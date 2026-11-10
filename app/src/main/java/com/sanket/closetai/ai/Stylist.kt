package com.sanket.closetai.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sanket.closetai.data.Analysis
import com.sanket.closetai.data.CATEGORIES
import com.sanket.closetai.data.Gaps
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Look
import com.sanket.closetai.data.Profile
import com.sanket.closetai.data.Weather
import com.sanket.closetai.data.WearLog
import com.sanket.closetai.data.str
import com.sanket.closetai.data.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

data class Detected(
    val category: String,
    val name: String,
    val color: String,
    val colorHex: String,
    val secondaryColor: String,
    val pattern: String,
    val material: String,
    val fit: String,
    val formality: Int,
    val seasons: List<String>,
    val notes: String,
    val sleeve: String = "",
    val weight: String = "",
    val warmth: Int = 0,
    val layerRole: String = "",
    val length: String = "",
    val styleTags: List<String> = emptyList(),
    val boldness: Int = 1,
    val rainSafe: Boolean = true,
    val confidence: Double = 1.0,
) {
    /**
     * Copies detected attributes onto an item: sets attrV=2, detectFailed=false; keeps id, imagePath,
     * addedAt, wornCount, lastWorn, inLaundry. When keepNameAndCategory is true keeps item.name and item.category.
     */
    fun applyTo(item: Item, keepNameAndCategory: Boolean = false): Item = item.copy(
        category = if (keepNameAndCategory) item.category else category,
        name = if (keepNameAndCategory) item.name else name,
        color = color,
        colorHex = colorHex,
        secondaryColor = secondaryColor,
        pattern = pattern,
        material = material,
        fit = fit,
        formality = formality,
        seasons = seasons,
        notes = notes,
        sleeve = sleeve,
        weight = weight,
        warmth = warmth,
        layerRole = layerRole,
        length = length,
        styleTags = styleTags,
        boldness = boldness,
        rainSafe = rainSafe,
        confidence = confidence,
        attrV = 2,
        detectFailed = false,
    )
}

data class StyleRequest(
    val items: List<Item>,
    val profile: Profile,
    val occasion: String,
    val vibe: String,
    val extra: String,
    val anchor: Item?,
    val weather: Weather?,
    val taste: Map<String, Double>,
    val tempBias: Double,
    val history: List<WearLog>,
    val hour: Int,
    val useAi: Boolean,
)

data class StyleResult(val looks: List<Look>, val offline: Boolean, val note: String)

/** All prompts and response parsing for the stylist. Scoring lives in [Scorer]. */
class Stylist(private val ai: AiClient) {

    // ------------------------------------------------------------------ detection

    private val styleTagSet: List<String> = listOf("minimal", "smart", "street", "sporty", "ethnic", "classic", "rugged", "festive")

    private fun oneOf(raw: String, allowed: List<String>, fallback: String): String {
        val v = raw.trim().lowercase()
        return allowed.firstOrNull { it == v } ?: fallback
    }

    suspend fun detect(images: List<ByteArray>): List<Detected?> {
        val n = images.size
        if (n == 0) return emptyList()
        val prompt = """
You are an expert fashion cataloguer for a wardrobe app. You will receive $n photo(s); each shows ONE clothing item or accessory, in the order given (image 1 first). Describe the garment only - ignore hangers, hands, floors and backgrounds.
Reply with ONLY a JSON array of exactly $n objects, same order, each:
{"category": one of ${CATEGORIES.joinToString(", ", "[", "]") { "\"" + it + "\"" }},
 "name": short descriptive name, e.g. "Navy slim-fit oxford shirt",
 "color": main colour name, "colorHex": "#rrggbb" of the fabric's main colour in even light (not the shadow, not the background), "secondaryColor": string or null,
 "pattern": "solid"|"striped"|"checked"|"printed"|"graphic"|"textured"|"denim"|"other",
 "material": best guess: "cotton"|"linen"|"denim"|"knit"|"wool"|"fleece"|"polyester"|"leather"|"suede"|"canvas"|"synthetic"|other,
 "fit": "slim"|"regular"|"relaxed"|"oversized"|"n/a",
 "formality": integer 1-5 (1 gym/lounge, 2 casual, 3 smart casual, 4 business, 5 formal/festive),
 "seasons": array from ["summer","monsoon","winter","all-season"],
 "sleeve": "none"|"short"|"long"|"n/a" (n/a for bottoms, shoes, accessories),
 "weight": "light"|"mid"|"heavy" (fabric weight),
 "warmth": integer 1-5 (1 tee/linen/shorts/sandals, 2 shirt/chinos/sneakers, 3 jeans/overshirt/blazer, 4 sweater/hoodie/jacket/boots, 5 coat/puffer),
 "layerRole": "base" (worn next to skin: tees, polos, shirts, kurtas) | "mid" (sweater, hoodie, sweatshirt, overshirt) | "outer" (jacket, blazer, coat) | "none" (bottoms, shoes, accessories),
 "length": "shorts"|"cropped"|"full"|"n/a" (bottoms only, else n/a),
 "styleTags": 1-3 from ["minimal","smart","street","sporty","ethnic","classic","rugged","festive"],
 "boldness": 1 quiet/plain | 2 noticeable | 3 statement (loud colour, big graphic or print),
 "rainSafe": false for suede, juttis, white canvas, leather-soled shoes and delicate fabrics, else true,
 "confidence": 0.0-1.0 how sure you are about category and colour,
 "notes": one short practical styling note}
Category rules: a collared knit short-sleeve with a placket is "Polo"; a woven button-up is "Shirt"; a heavy shirt worn open as a layer is "Overshirt"; a collarless knit tee is "T-Shirt"; a tailored jacket is "Blazer"; casual cotton twill trousers and cargos are "Chinos"; dress pants are "Trousers". If a photo shows no garment, use "Other" with name "Unclear photo" and confidence 0.
""".trimIndent()
        val v = ai.askJson(prompt, images)
        val arr: JSONArray = when (v) {
            is JSONArray -> v
            is JSONObject -> v.optJSONArray("items") ?: JSONArray().put(v)
            else -> throw AiException("The AI reply wasn't readable — please try again.")
        }
        return (0 until n).map { i -> arr.optJSONObject(i)?.let { parseDetected(it) } }
    }

    private fun parseDetected(o: JSONObject): Detected? {
        val rawName = o.str("name")
        if (rawName.lowercase().contains("unclear")) return null
        val cat = o.str("category").let { c -> CATEGORIES.firstOrNull { it.equals(c.trim(), true) } ?: "Other" }
        val hex = o.str("colorHex").trim().takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: ""
        val conf = o.optDouble("confidence", 0.8).let { if (it.isNaN()) 0.8 else it.coerceIn(0.0, 1.0) }
        val seasons = o.optJSONArray("seasons").strings().map { it.lowercase() }
            .filter { it in setOf("summer", "monsoon", "winter", "all-season") }.distinct().take(4)
        val tags = o.optJSONArray("styleTags").strings().map { it.trim().lowercase() }
            .filter { it in styleTagSet }.distinct().take(3)
        return Detected(
            category = cat,
            name = rawName.ifBlank { cat }.take(80),
            color = o.str("color").take(40),
            colorHex = hex,
            secondaryColor = o.str("secondaryColor").takeUnless {
                it.trim().lowercase() in setOf("none", "null", "n/a", "na", "-", "no")
            }?.take(40) ?: "",
            pattern = o.str("pattern").trim().lowercase().take(20),
            material = o.str("material").trim().lowercase().take(30),
            fit = oneOf(o.str("fit"), listOf("slim", "regular", "relaxed", "oversized", "n/a"), ""),
            formality = o.optInt("formality", 3).coerceIn(1, 5),
            seasons = seasons,
            notes = o.str("notes").take(200),
            sleeve = oneOf(o.str("sleeve"), listOf("none", "short", "long", "n/a"), ""),
            weight = oneOf(o.str("weight"), listOf("light", "mid", "heavy"), ""),
            warmth = o.optInt("warmth", 0).let { if (it in 1..5) it else 0 },
            layerRole = oneOf(o.str("layerRole"), listOf("base", "mid", "outer", "none"), ""),
            length = oneOf(o.str("length"), listOf("shorts", "cropped", "full", "n/a"), ""),
            styleTags = tags,
            boldness = o.optInt("boldness", 1).coerceIn(1, 3),
            rainSafe = o.optBoolean("rainSafe", true),
            confidence = conf,
        )
    }

    // ------------------------------------------------------------------ profile analysis

    suspend fun analyse(photo: ByteArray, p: Profile): Analysis {
        val prompt = """
You are a warm, honest, expert personal stylist. The photo shows the client (full length). Give practical styling guidance for the clothes they will wear - no judgement of attractiveness, and only neutral build/proportion terms.
Client info: dressing for ${p.dressingFor}; height ${p.height.ifBlank { "unknown" }}; city/climate ${p.city.ifBlank { "unknown" }}; usual style ${p.style}; notes: ${p.notes.ifBlank { "none" }}.
Reply with ONLY JSON:
{"summary": 2 sentences on their overall look and what to lean into,
 "build": short neutral description (e.g. "lean, athletic shoulders"),
 "proportions": short (e.g. "longer legs, shorter torso"),
 "skinTone": e.g. "medium wheatish", "undertone": "warm"|"cool"|"neutral"|"olive",
 "contrast": "low"|"medium"|"high" (difference between hair/beard and skin),
 "bestColors": 8 objects {"name","hex"} that flatter this skin tone near the face,
 "avoidColors": 3-4 objects {"name","hex"} that wash this skin tone out,
 "bestNeutrals": 4 neutral colour names from ["navy","charcoal","grey","black","white","off-white","beige","khaki","olive","brown"] that suit them best, best first,
 "bestFits": 4 specific fit tips (tops, bottoms, length, rise),
 "rules": 5 short personal style rules}
""".trimIndent()
        val v = ai.askJson(prompt, listOf(photo))
        val o = v as? JSONObject ?: throw AiException("The AI reply wasn't readable — please try again.")
        return Analysis.from(o)
    }

    // ------------------------------------------------------------------ shared prompt text

    private fun daysAgo(t: Long): Long? = if (t <= 0) null else (System.currentTimeMillis() - t) / 86_400_000L

    private fun itemLine(i: Item): String {
        val col = i.color.ifBlank { Scorer.familyOf(i) } + "(" + Scorer.depthOf(i) + ")" +
            (if (i.secondaryColor.isNotBlank()) "/" + i.secondaryColor else "")
        val d = daysAgo(i.lastWorn)
        return i.id + " | " + i.category + " | " + i.name + " | " + col + " | " + i.pattern.ifBlank { "?" } + " | " +
            i.material.ifBlank { "?" } + " | fit " + i.fit.ifBlank { "?" } + " | sleeve " + Scorer.sleeveOf(i) +
            " | F" + i.formality + " | W" + Scorer.warmthOf(i) +
            (if (i.styleTags.isNotEmpty()) " | " + i.styleTags.joinToString("/") else "") +
            " | " + (if (d == null) "never worn" else "worn ${d}d ago")
    }

    private fun profileText(p: Profile): String {
        val sb = StringBuilder(
            "Dressing for: ${p.dressingFor}. Height: ${p.height.ifBlank { "?" }}. City: ${p.city.ifBlank { "?" }}. " +
                "Usual style: ${p.style}. Notes: ${p.notes.ifBlank { "none" }}.",
        )
        val a = p.analysis
        if (a != null) {
            sb.append("\nBuild: ${a.build}. Proportions: ${a.proportions}. Skin: ${a.skinTone}, ${a.undertone} undertone")
            if (a.contrast.isNotBlank()) sb.append(", ${a.contrast} contrast")
            sb.append(". Best colours near the face: ${a.bestColors.joinToString { it.name }}. Avoid: ${a.avoidColors.joinToString { it.name }}.")
            if (a.bestNeutrals.isNotEmpty()) sb.append(" Best neutrals: ${a.bestNeutrals.joinToString()}.")
            if (a.bestFits.isNotEmpty()) sb.append(" Fit: ${a.bestFits.take(4).joinToString("; ")}.")
            if (a.rules.isNotEmpty()) sb.append(" Rules: ${a.rules.take(3).joinToString("; ")}.")
        }
        return sb.toString()
    }

    /** Small JPEG of an image file for visual review (keeps requests light). */
    private fun smallJpeg(bytes: ByteArray, maxPx: Int): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        out.toByteArray()
    } catch (e: Exception) { null }

    // ------------------------------------------------------------------ outfits

    private class Polished(val combo: Combo, val look: Look, val rank: Double)

    private fun layerSentence(ctx: StyleCtx): String = when {
        ctx.rule.forbid -> "too warm for any layer - do not add one"
        ctx.rule.required -> "a layer over the top is required" + (if (ctx.rule.maxLayers >= 2) " (knit + outerwear if cold)" else "")
        else -> "a light layer is optional"
    }

    private fun buildLook(ctx: StyleCtx, c: Combo, direction: String, score10: Double, title: String, why: String,
                          tips: List<String>, note: String, addOn: String): Look {
        val cleanTips = tips.map { it.trim() }.filter { it.isNotBlank() }.map { it.take(160) }.distinct().toMutableList()
        if (cleanTips.size < 2) {
            for (t in Scorer.tipsOf(ctx, c)) if (cleanTips.size < 3 && t !in cleanTips) cleanTips.add(t)
        }
        return Look(
            title = title.trim().take(48).ifBlank { Scorer.titleOf(c) },
            itemIds = c.ids,
            why = why.trim().take(400).ifBlank { Scorer.whyOf(ctx, c) },
            tips = cleanTips.take(4),
            addOn = addOn.trim().let { if (it.equals("null", true) || it.equals("none", true)) "" else it.take(140) },
            score = Scorer.round1(score10.coerceIn(0.0, 10.0)),
            direction = direction,
            occasion = ctx.req.occasion,
            weatherNote = note.trim().take(180).ifBlank { Scorer.weatherNoteOf(ctx, c) },
        )
    }

    private fun offlineLooks(short: Shortlist, exclude: List<Combo>, n: Int): List<Pair<Combo, Look>> {
        val ctx = short.ctx
        val taken = exclude.map { it.key }.toSet()
        val ranked = short.combos.filter { it.key !in taken }
        // keep already chosen looks in the diversity check by seeding them first
        val seeded = exclude + ranked
        val picked = Scorer.pickDiverse(seeded, exclude.size + n, Scorer.sharedLimit(ctx), { it.coreIds }, { it.key })
            .filter { it.key !in taken }
            .take(n)
            .map { it.copy(accessories = Scorer.autoAccessories(ctx, it)) }
        val dirs = if (exclude.isEmpty()) Scorer.directions(picked) else picked.map { "" }
        return picked.mapIndexed { i, c ->
            c to buildLook(ctx, c, dirs.getOrElse(i) { "" }, c.score * 10.0, "", "", emptyList(), "", "")
        }
    }

    private fun orderLooks(looks: List<Look>): List<Look> {
        val order = listOf("safe", "elevated", "personality")
        return looks.sortedBy { l -> order.indexOf(l.direction).let { if (it < 0) 9 else it } }
    }

    /**
     * Pre-scores the wardrobe on-device (weather, occasion, colour, rotation, taste), then makes ONE AI call
     * that picks and polishes the best 3 from those candidates. Never throws for AI problems: it falls back
     * to the on-device picks.
     */
    suspend fun outfits(req: StyleRequest, readImage: (String) -> ByteArray?, onStage: (String) -> Unit = {}): StyleResult {
        onStage("Reading the weather")
        onStage("Shortlisting your pieces")
        val short: Shortlist = withContext(Dispatchers.Default) { Scorer.shortlist(req) }
        val ctx = short.ctx
        if (short.combos.isEmpty()) {
            return StyleResult(emptyList(), !req.useAi, "Not enough clean pieces for a full outfit (top + bottom" +
                (if (ctx.ownsShoes) " + shoes" else "") + ").")
        }
        onStage("Composing looks")
        if (!req.useAi) {
            val looks = withContext(Dispatchers.Default) { offlineLooks(short, emptyList(), 3).map { it.second } }
            onStage("Final check")
            return StyleResult(orderLooks(looks), true, "On-device styling (no AI key)")
        }

        // ---- the one AI call: candidates + facts + small photos of the pieces involved ----
        val candidates = short.combos
        val images = ArrayList<ByteArray>()
        val labels = StringBuilder()
        withContext(Dispatchers.IO) {
            val wanted = candidates.flatMap { c -> c.core + listOfNotNull(c.shoes) }.distinctBy { it.id }.take(10)
            for (item in wanted) {
                val small = try { readImage(item.imagePath)?.let { smallJpeg(it, 256) } } catch (e: Exception) { null }
                if (small != null) {
                    images.add(small)
                    labels.append("Image ").append(images.size).append(": item ").append(item.id).append(" (").append(item.name).append(")\n")
                }
            }
        }
        val prompt = outfitPrompt(ctx, candidates, labels.toString())
        val reply: Any = try {
            ai.askJson(prompt, images)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val looks = withContext(Dispatchers.Default) { offlineLooks(short, emptyList(), 3).map { it.second } }
            onStage("Final check")
            return StyleResult(orderLooks(looks), true, (e.message ?: "AI unavailable").take(160))
        }

        onStage("Final check")
        return withContext(Dispatchers.Default) {
            val polished = parsePolished(ctx, candidates, reply)
            if (polished.isEmpty()) {
                StyleResult(orderLooks(offlineLooks(short, emptyList(), 3).map { it.second }), true,
                    "The AI reply wasn't usable — showing on-device picks")
            } else {
                val best = Scorer.pickDiverse(polished.sortedByDescending { it.rank }, 3, Scorer.sharedLimit(ctx),
                    { it.combo.coreIds }, { it.combo.key })
                val looks = ArrayList<Look>(best.map { it.look })
                if (looks.size < 3) {
                    // the AI returned fewer than 3 usable looks: top up from the pre-scored list
                    for (extra in offlineLooks(short, best.map { it.combo }, 3 - looks.size)) looks.add(extra.second)
                }
                StyleResult(orderLooks(looks.take(3)), false, "")
            }
        }
    }

    private fun outfitPrompt(ctx: StyleCtx, candidates: List<Combo>, imageLabels: String): String {
        val req = ctx.req
        val w = req.weather
        val used = candidates.flatMap { it.core + listOfNotNull(it.shoes) }.distinctBy { it.id }
        val usedIds = used.map { it.id }.toSet()
        val spare = ctx.available.filter { it.id !in usedIds && it.group != "Accessories" }.take(30 - used.size.coerceAtMost(30))
        val accessories = ctx.available.filter { it.group == "Accessories" && it.category != "Other" }.take(8)
        val candText = candidates.mapIndexed { i, c ->
            "C" + (i + 1) + " [" + (c.core + listOfNotNull(c.shoes)).joinToString(", ") { it.id } + "] pre=" +
                Scorer.round1(c.score * 10.0) + " " + Scorer.facts(ctx, c)
        }.joinToString("\n")
        val anchor = req.anchor
        val weatherLine = if (w != null) w.promptLine() else "unknown - assume a mild 24°C day with no rain"
        val sb = StringBuilder()
        sb.append("You are an elite personal stylist. Pick the 3 best outfits for this client today from CANDIDATES, which were pre-checked for weather, occasion and colour.\n")
        sb.append("CLIENT\n").append(profileText(req.profile)).append("\n")
        sb.append("TODAY\nOccasion: ").append(req.occasion).append(". Vibe: ").append(req.vibe.ifBlank { "Surprise me" })
            .append(". Extra wishes: ").append(req.extra.trim().take(200).ifBlank { "none" }).append(".\n")
        sb.append("Weather: ").append(weatherLine).append(". Local hour ").append(req.hour.coerceIn(0, 23)).append(":00. Dress for about ")
            .append(ctx.t.roundToInt()).append("°C; ").append(layerSentence(ctx)).append(if (ctx.rainy) "; rain is likely." else ".").append("\n")
        if (anchor != null) sb.append("Every outfit MUST include item ").append(anchor.id).append(" (").append(anchor.name).append(").\n")
        sb.append("TASTE (learned from feedback): ").append(Scorer.tasteSummary(req.taste)).append("\n")
        sb.append("ITEMS (id | category | name | colour(depth) | pattern | material | fit | sleeve | F=formality 1-5 | W=warmth 1-5 | tags | last worn)\n")
        sb.append(used.joinToString("\n") { itemLine(it) }).append("\n")
        if (spare.isNotEmpty()) sb.append("SPARE ITEMS (allowed only as a single swap)\n").append(spare.joinToString("\n") { itemLine(it) }).append("\n")
        if (accessories.isNotEmpty()) {
            sb.append("ACCESSORIES\n").append(accessories.joinToString("\n") { it.id + " | " + it.category + " | " + it.name + " | " + it.color }).append("\n")
        }
        sb.append("CANDIDATES (pre = on-device score out of 10)\n").append(candText).append("\n")
        if (imageLabels.isNotBlank()) {
            sb.append("IMAGES (real photos of the pieces - trust what you see over the text labels)\n").append(imageLabels)
        }
        sb.append(
            """
RULES
- Choose only from CANDIDATES. In each pick you may swap at most ONE piece for another item id of the same category group (top for top, bottom for bottom, shoes for shoes) when it clearly improves colour or fit, and add up to 2 ACCESSORIES ids. Never drop the top, bottom or shoes. Never invent ids.
- The 3 picks must differ in main top AND bottom wherever the candidates allow.
- Judge like a stylist: colour harmony and light/dark balance, colours that flatter this client's skin near the face, proportions for their height and build, and how right it is for the occasion and for today's weather. Respect TASTE.
- Give each pick a direction: "safe" (sharp and reliable), "elevated" (a level up), "personality" (more expressive but still appropriate). Use each once if you can.
- Be strict with scores: 8+ only for a look a stylist would be proud of; 6 or less if it is merely acceptable.
- Tips must be concrete actions for THESE pieces: tuck, sleeves, buttons, hem or cuff, which accessory, grooming.
- weatherNote: one short line tying the look to today's actual numbers.
Reply with ONLY JSON, fields in exactly this order:
{"outfits":[{"candidate":"C3","check":"max 20 words: colour, proportion and weather verdict","title":"3-5 word name","direction":"safe|elevated|personality","itemIds":["id","id"],"score":7.5,"why":"1-2 sentences naming the colour and proportion logic for THIS client","tips":["tip","tip","tip"],"weatherNote":"one line","addOn":"one specific piece worth buying that would lift it, or null"}]}
""".trimEnd(),
        )
        return sb.toString()
    }

    private fun parsePolished(ctx: StyleCtx, candidates: List<Combo>, reply: Any): List<Polished> {
        val arr: JSONArray = when (reply) {
            is JSONObject -> reply.optJSONArray("outfits") ?: reply.optJSONArray("looks") ?: JSONArray()
            is JSONArray -> reply
            else -> JSONArray()
        }
        val out = ArrayList<Polished>()
        val dirs = setOf("safe", "elevated", "personality")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val idx = o.str("candidate").filter { it.isDigit() }.take(3).toIntOrNull()
            val base: Combo? = if (idx != null) candidates.getOrNull(idx - 1) else null
            val ids = o.optJSONArray("itemIds").strings()
            // the AI's version when valid (same outfit or one swap, plus accessories); otherwise the original candidate
            val combo: Combo = Scorer.comboFromIds(ctx, ids, base) ?: base ?: continue
            if (out.any { it.combo.key == combo.key }) continue
            val pre10 = combo.score * 10.0
            val ai10 = o.optDouble("score", pre10).let { if (it.isNaN()) pre10 else it.coerceIn(1.0, 10.0) }
            val rank = 0.5 * pre10 + 0.5 * ai10
            val dir = o.str("direction").trim().lowercase().let { if (it in dirs) it else "" }
            val look = buildLook(
                ctx, combo, dir, rank, o.str("title"), o.str("why"), o.optJSONArray("tips").strings(),
                o.str("weatherNote"), o.str("addOn"),
            )
            out.add(Polished(combo, look, rank))
        }
        return out
    }

    // ------------------------------------------------------------------ try-on

    private fun wearHint(i: Item, look: Look, hasLayer: Boolean): String {
        val bits = ArrayList<String>()
        if (i.fit.isNotBlank() && i.fit != "n/a") bits.add(i.fit + " fit")
        when (i.group) {
            "Tops" -> {
                val s = Scorer.sleeveOf(i)
                if (s == "short" || s == "long") bits.add("$s sleeves")
                val tipsText = look.tips.joinToString(" ").lowercase()
                bits.add(
                    when {
                        tipsText.contains("untuck") -> "worn untucked"
                        tipsText.contains("tuck") -> "tucked in"
                        i.category == "Shirt" && i.formality >= 4 -> "tucked in"
                        else -> "worn untucked"
                    },
                )
                if (s == "long" && tipsText.contains("roll") && !hasLayer) bits.add("sleeves rolled to below the elbow")
            }
            "Layers" -> bits.add(if (i.category == "Sweater" || i.category == "Sweatshirt" || i.category == "Hoodie") "worn over the top" else "worn open over the top")
            "Bottoms" -> {
                val len = i.length.lowercase()
                bits.add(if (i.category == "Shorts" || len == "shorts") "ending just above the knee" else if (len == "cropped") "cropped at the ankle" else "full length with a clean break at the shoe")
            }
            else -> {}
        }
        return bits.joinToString(", ")
    }

    /** Virtual try-on: image 1 = the person, following images = the outfit's items. */
    suspend fun tryOn(person: ByteArray, profile: Profile, look: Look, items: List<Pair<Item, ByteArray>>): ByteArray {
        val rank = listOf("Tops", "One-piece", "Layers", "Bottoms", "Footwear", "Accessories")
        val ordered = items.sortedBy { rank.indexOf(it.first.group).let { r -> if (r < 0) 9 else r } }
        val garments = ordered.filter { it.first.group != "Accessories" }.take(5)
        val shownAcc = if (garments.size < 5) ordered.filter { it.first.group == "Accessories" }.take(5 - garments.size) else emptyList()
        val shown = garments + shownAcc
        val textOnly = ordered.filter { p -> shown.none { it.first.id == p.first.id } }
        val hasLayer = garments.any { it.first.group == "Layers" }
        val hasShoes = garments.any { it.first.group == "Footwear" }
        val images = ArrayList<ByteArray>()
        images.add(person)
        val list = StringBuilder()
        withContext(Dispatchers.Default) {
            for (p in shown) {
                val piece = p.first
                images.add(smallJpeg(p.second, 768) ?: p.second)
                list.append("Image ").append(images.size).append(": ").append(piece.category).append(" - ").append(piece.name)
                if (piece.color.isNotBlank()) list.append(" (").append(piece.color).append(")")
                val hint = wearHint(piece, look, hasLayer)
                if (hint.isNotBlank()) list.append("; ").append(hint)
                list.append("\n")
            }
        }
        val extras = if (textOnly.isEmpty()) "" else
            "Also add, described in words only: " + textOnly.joinToString("; ") { it.first.name + (if (it.first.color.isNotBlank()) " (" + it.first.color + ")" else "") } + ".\n"
        val prompt = """
Virtual try-on by EDITING image 1. Image 1 is a photo of a real person. Change ONLY their clothing so they are wearing the outfit below; everything else about the person stays exactly as in image 1.
OUTFIT (each following image shows one garment from their own wardrobe)
$list$extras
PERSON - do not change:
- Same face, expression, hair, beard, skin tone, body build and proportions (height ${profile.height.ifBlank { "as in the photo" }}). Do not slim, bulk up, lengthen or retouch the body or face.
GARMENTS:
- Use only the garment from each garment photo. Ignore hangers, hands, mannequins, other people, floors and backgrounds in those photos.
- Reproduce every garment faithfully: exact colour and shade, pattern, fabric texture, collar, buttons, pockets, logos and prints. Do not restyle or recolour anything.
- Fit the clothes naturally on this person's body with realistic drape and folds, following the wearing notes after each item. Layers go over the top; the top's collar or hem may show.
- ${if (hasShoes) "Show the listed shoes clearly." else "No shoes are listed: keep the footwear from image 1, or plain neutral shoes if the feet are not visible."}
- Add no clothes, accessories, bags or jewellery that are not listed.
PHOTO:
- One photorealistic full-length image, head to toe including the shoes; if image 1 is cropped, extend it naturally.
- Relaxed standing pose facing the camera, camera at chest height, soft even daylight, plain light-grey studio background, portrait 3:4 framing. No text, borders or collage.
""".trimIndent()
        return ai.generateImage(prompt, images)
    }

    // ------------------------------------------------------------------ wardrobe gaps

    suspend fun gaps(items: List<Item>, profile: Profile, weather: Weather?): Gaps {
        val usable = items.filter { !(it.detectFailed && it.category == "Other") }
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val monthName = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September",
            "October", "November", "December").getOrElse(month) { "" }
        val climate = if (weather != null) {
            "It is $monthName. Right now: ${weather.promptLine()} (${weather.bucket().lowercase()}). Weigh what this season and the next one need in this climate."
        } else {
            "It is $monthName" + (if (profile.city.isNotBlank()) " in ${profile.city}" else "") + ". Weigh what this season and the next one need in this climate."
        }
        val prompt = """
You are a stylist auditing a client's wardrobe.
CLIENT
${profileText(profile)}
CLIMATE
$climate
WARDROBE (id | category | name | colour(depth) | pattern | material | fit | sleeve | F=formality 1-5 | W=warmth 1-5 | tags | last worn)
${usable.joinToString("\n") { itemLine(it) }}
Recommend buys that unlock the most new outfits with what they already own: check missing neutrals, missing layers or footwear for the coming weather, and formality levels they cannot dress for yet. Name colours that suit this client's skin tone.
Reply with ONLY JSON: {"verdict":"one sentence on the wardrobe's strengths","buys":[{"item":"specific piece, colour and fit","why":"what gap it fills / how many new outfits it unlocks"}],"unused":"one sentence on pieces to wear more or let go"} with 4-5 buys ordered by impact.
""".trimIndent()
        val o = ai.askJson(prompt) as? JSONObject ?: throw AiException("The AI reply wasn't readable — please try again.")
        val b = o.optJSONArray("buys") ?: JSONArray()
        val buys = (0 until b.length()).mapNotNull { i -> b.optJSONObject(i)?.let { it.str("item") to it.str("why") } }
            .filter { it.first.isNotBlank() }
        return Gaps(o.str("verdict"), buys, o.str("unused"), System.currentTimeMillis())
    }
}
