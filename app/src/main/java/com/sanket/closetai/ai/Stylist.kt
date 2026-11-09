package com.sanket.closetai.ai

import com.sanket.closetai.data.Analysis
import com.sanket.closetai.data.CATEGORIES
import com.sanket.closetai.data.Gaps
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Look
import com.sanket.closetai.data.Profile
import com.sanket.closetai.data.str
import com.sanket.closetai.data.strings
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
)

/** All prompts and response parsing for the stylist. */
class Stylist(private val ai: AiClient) {

    suspend fun detect(images: List<ByteArray>): List<Detected?> {
        val n = images.size
        val prompt = """
You are an expert fashion cataloguer. You will receive $n photo(s); each shows ONE clothing item or accessory, in the order given (image 1 first).
Reply with ONLY a JSON array of $n objects, same order, each:
{"category": one of ${CATEGORIES.joinToString(", ", "[", "]") { "\"" + it + "\"" }},
 "name": short descriptive name, e.g. "Navy slim-fit oxford shirt",
 "color": main colour name, "colorHex": "#rrggbb" of the main colour, "secondaryColor": string or null,
 "pattern": "solid"|"striped"|"checked"|"printed"|"graphic"|"textured"|"denim"|"other",
 "material": best guess e.g. "cotton", "linen", "denim", "knit", "leather", "synthetic",
 "fit": "slim"|"regular"|"relaxed"|"oversized"|"n/a",
 "formality": integer 1-5 (1 gym/lounge, 2 casual, 3 smart casual, 4 business, 5 formal/festive),
 "seasons": array from ["summer","monsoon","winter","all-season"],
 "notes": one short practical styling note}
Category rules: a collared knit short-sleeve with a placket is "Polo"; a woven button-up is "Shirt"; a collarless knit tee is "T-Shirt"; a tailored jacket is "Blazer"; casual cotton twill trousers are "Chinos"; dress pants are "Trousers". If a photo shows no garment, use "Other" with name "Unclear photo".
""".trimIndent()
        val v = ai.askJson(prompt, images)
        val arr: JSONArray = when (v) {
            is JSONArray -> v
            is JSONObject -> v.optJSONArray("items") ?: JSONArray().put(v)
            else -> JSONArray()
        }
        return (0 until n).map { i ->
            arr.optJSONObject(i)?.let { o ->
                val cat = o.str("category").let { c -> CATEGORIES.firstOrNull { it.equals(c, true) } ?: "Other" }
                val hex = o.str("colorHex").takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: ""
                Detected(
                    category = cat,
                    name = o.str("name").ifBlank { cat }.take(80),
                    color = o.str("color"),
                    colorHex = hex,
                    secondaryColor = o.str("secondaryColor"),
                    pattern = o.str("pattern"),
                    material = o.str("material"),
                    fit = o.str("fit"),
                    formality = o.optInt("formality", 3).coerceIn(1, 5),
                    seasons = o.optJSONArray("seasons").strings().take(4),
                    notes = o.str("notes").take(200),
                )
            }
        }
    }

    suspend fun analyse(photo: ByteArray, p: Profile): Analysis {
        val prompt = """
You are a warm, honest, expert personal stylist. The photo shows the client (full length). Give practical styling guidance for the clothes they will wear — no judgement of attractiveness, and only neutral build/proportion terms.
Client info: dressing for ${p.dressingFor}; height ${p.height.ifBlank { "unknown" }}; city/climate ${p.city.ifBlank { "unknown" }}; usual style ${p.style}; notes: ${p.notes.ifBlank { "none" }}.
Reply with ONLY JSON:
{"summary": 2 sentences on their overall look and what to lean into,
 "build": short neutral description (e.g. "lean, athletic shoulders"),
 "proportions": short (e.g. "longer legs, shorter torso"),
 "skinTone": e.g. "medium wheatish", "undertone": "warm"|"cool"|"neutral"|"olive",
 "bestColors": 8 objects {"name","hex"}, "avoidColors": 3-4 objects {"name","hex"},
 "bestFits": 4 specific fit tips (tops, bottoms, length, rise),
 "rules": 5 short personal style rules}
""".trimIndent()
        val v = ai.askJson(prompt, listOf(photo))
        val o = v as? JSONObject ?: throw AiException("The AI reply wasn't readable — please try again.")
        return Analysis.from(o)
    }

    private fun daysAgo(t: Long): Long? = if (t <= 0) null else (System.currentTimeMillis() - t) / 86_400_000L

    private fun catalog(items: List<Item>) = items.joinToString("\n") { i ->
        val d = daysAgo(i.lastWorn)
        val col = i.color + if (i.secondaryColor.isNotBlank()) "/" + i.secondaryColor else ""
        "${i.id} | ${i.category} | ${i.name} | colour: $col | ${i.pattern} | ${i.material} | fit ${i.fit} | " +
            "formality ${i.formality} | ${i.seasons.joinToString("/")} | ${if (d == null) "never worn" else "worn ${d}d ago"}"
    }

    private fun profileText(p: Profile): String {
        val sb = StringBuilder(
            "Dressing for: ${p.dressingFor}. Height: ${p.height.ifBlank { "?" }}. City: ${p.city.ifBlank { "?" }}. " +
                "Usual style: ${p.style}. Notes: ${p.notes.ifBlank { "none" }}.",
        )
        p.analysis?.let { a ->
            sb.append("\nStylist read: ${a.summary} Build: ${a.build}. Proportions: ${a.proportions}. ")
            sb.append("Skin: ${a.skinTone}, ${a.undertone} undertone. ")
            sb.append("Best colours: ${a.bestColors.joinToString { it.name }}. Avoid: ${a.avoidColors.joinToString { it.name }}. ")
            sb.append("Fit tips: ${a.bestFits.joinToString("; ")}. Rules: ${a.rules.joinToString("; ")}.")
        }
        return sb.toString()
    }

    /** Taste memory from the client's 👍 / 👎 on past looks. */
    data class Taste(val liked: List<String>, val disliked: List<String>)

    private val rulebook = """
STYLING RULEBOOK (apply like a top menswear/womenswear stylist)
Colour
- Build every outfit on at least one neutral (navy, charcoal, grey, white, off-white, beige, khaki, olive, brown, black). Max 3 colours total incl. shoes; accessories echo one of them.
- Use proven pairings: navy+white/grey/khaki/brown; olive+white/navy/beige/tan; grey+black/white/burgundy/navy; beige/khaki+navy/white/olive/brown; earth tones together for warm undertones; jewel tones (bottle green, maroon, navy) with neutrals.
- Tonal / monochrome looks are allowed only with clear shade or texture variation.
- Avoid: two competing saturated colours, a top and bottom of nearly the same non-neutral shade, black with dark navy/brown when they read as a mismatch, brown shoes with black trousers in formal settings.
- Favour the client's best colours near the face (the top layer); colours they should avoid go to the bottom half or not at all.
Contrast & proportion
- Match outfit contrast to the client's own colouring (darker hair/beard + medium skin → medium-high contrast works).
- Lean/slim builds: add structure and visual width up top (polos, oxford/overshirts, layers, crew sweaters); avoid baggy, sloppy oversize and very skinny together.
- Shorter or average height (under ~5'9"): keep one continuous colour from trouser to shoe or tonal bottom half, minimise horizontal breaks below the waist, avoid long untucked hems past the mid-fly, prefer slim-straight or tapered trousers without heavy stacking.
Formality, pattern, fabric
- All pieces within ±1 formality level of each other and of the occasion. Never: cargo/joggers with blazer or formal shoes; formal shoes with shorts; gym wear outside gym/very casual.
- Max one bold pattern or graphic per outfit; pair stripes/checks with solids; logos small.
- Weather: hot → light colours, cotton/linen, short sleeves, no heavy layers; rainy → dark quick-dry bottoms, no suede; cold → layer (base + mid + outer), heavier textures, closed shoes.
- Belt matches shoe colour family; sneakers clean and minimal for smart casual; ethnic: kurta with slim jeans/chinos/churidar + juttis/loafers/clean sneakers.
Structure
- Each outfit: a top (or one-piece) + a bottom + footwear if any is owned; add a layer/accessory only when it improves the look or the weather needs it.
- The 3 final looks should take different directions: one safe & sharp, one elevated, one with personality — all appropriate.
""".trimIndent()

    private fun tasteText(t: Taste): String {
        if (t.liked.isEmpty() && t.disliked.isEmpty()) return "No feedback yet."
        return "Looks the client LIKED: ${t.liked.takeLast(12).joinToString("; ").ifBlank { "none" }}.\n" +
            "Looks the client DISLIKED (avoid similar): ${t.disliked.takeLast(12).joinToString("; ").ifBlank { "none" }}."
    }

    private fun parseLooks(v: Any, ids: Set<String>): List<Look> {
        val arr = when (v) {
            is JSONObject -> v.optJSONArray("outfits") ?: JSONArray()
            is JSONArray -> v
            else -> JSONArray()
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Look.from(it) }
                ?.let { l -> l.copy(itemIds = l.itemIds.filter { it in ids }.distinct(), tips = l.tips.take(5)) }
                ?.takeIf { it.itemIds.size >= 2 }
        }
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

    /**
     * Two-pass styling:
     *  1) shortlist 6 candidate outfits from the text catalogue using the rulebook;
     *  2) a visual review that looks at the actual garment photos (and the client photo),
     *     fixes clashes, scores each look and returns the best 3.
     */
    suspend fun outfits(
        items: List<Item>, profile: Profile, occasion: String, weather: String, vibe: String,
        anchor: Item?, extra: String, taste: Taste,
        readImage: (String) -> ByteArray?,
        onStage: (String) -> Unit = {},
    ): List<Look> {
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ENGLISH).format(Date())
        val ids = items.map { it.id }.toSet()
        val request = "Occasion: $occasion. Weather: $weather. Vibe: $vibe. " +
            (anchor?.let { "Every outfit MUST include item ${it.id} (${it.name}). " } ?: "") +
            "Extra wishes: ${extra.ifBlank { "none" }}. Today: $today."

        onStage("Shortlisting combinations…")
        val prompt1 = """
You are an elite personal stylist building outfits ONLY from the client's own wardrobe.
CLIENT
${profileText(profile)}
TASTE
${tasteText(taste)}
REQUEST
$request
WARDROBE (id | category | name | details | last worn)
${catalog(items)}
$rulebook
TASK
Think through the colour, formality, weather and proportion rules, then propose 6 strong, DIFFERENT candidate outfits. Use only ids from the list. Avoid items worn in the last 3 days unless essential.
Reply with ONLY JSON: {"outfits":[{"title":"3-5 word name","itemIds":["id",...],"why":"why it works for this client","tips":["3-4 concrete tips: tuck, sleeves, buttons, hem/cuff, accessories, grooming"],"addOn":string|null}]}
""".trimIndent()
        val candidates = parseLooks(ai.askJson(prompt1), ids)
        if (candidates.isEmpty()) return emptyList()

        // ---- pass 2: visual review ----
        onStage("Checking colours & fit visually…")
        val used = candidates.flatMap { it.itemIds }.distinct().take(16)
        val byId = items.associateBy { it.id }
        val images = mutableListOf<ByteArray>()
        val labels = StringBuilder()
        withContext(Dispatchers.Default) {
        profile.photoPath.takeIf { it.isNotBlank() }?.let { readImage(it) }?.let { smallJpeg(it, 640) }?.let {
            images.add(it); labels.append("Image 1: the client.\n")
        }
        used.forEach { id ->
            val item = byId[id] ?: return@forEach
            readImage(item.imagePath)?.let { smallJpeg(it, 384) }?.let {
                images.add(it)
                labels.append("Image ${images.size}: item $id — ${item.category}, ${item.name}\n")
            }
        }
        }
        val candText = candidates.mapIndexed { i, l ->
            "${i + 1}. ${l.title}: " + l.itemIds.joinToString(" + ") { id -> "$id (${byId[id]?.name ?: "?"})" }
        }.joinToString("\n")
        val prompt2 = """
You are reviewing outfit candidates for a client as a senior stylist. You can SEE the actual garments.
CLIENT
${profileText(profile)}
TASTE
${tasteText(taste)}
REQUEST
$request
IMAGES
$labels
CANDIDATE OUTFITS
$candText
FULL WARDROBE (you may swap in any of these ids to fix a problem)
${catalog(items)}
$rulebook
TASK
1. Look at the real colours, shades, textures and fits in the photos (labels from the catalogue can be wrong).
2. For each candidate, fix anything weak: a clashing shade, mismatched formality, bad shoe choice, wrong fabric for the weather, unflattering proportions for this client. Swap items if needed.
3. Score each (fixed) outfit 1-10 on: colour harmony, suits the client's colouring & build, occasion/weather fit, overall style. Be strict — 8+ only for looks a stylist would be proud of.
4. Return the best 3 distinct outfits, highest score first (one safe & sharp, one elevated, one with personality where possible).
Reply with ONLY JSON: {"outfits":[{"title":"3-5 word name","itemIds":["id",...],"score":8.5,"why":"1-2 sentences naming the colour/proportion logic that makes it work on THIS client","tips":["3-4 concrete tips"],"addOn":"one specific piece that would elevate it, or null"}]}
""".trimIndent()
        return try {
            val reviewed = parseLooks(ai.askJson(prompt2, images), ids)
                .sortedByDescending { it.score }
                .distinctBy { it.key }
                .take(3)
            reviewed.ifEmpty { candidates.take(3) }
        } catch (e: Exception) {
            candidates.take(3)
        }
    }

    /** Virtual try-on: image 1 = the person, following images = the outfit's items. */
    suspend fun tryOn(person: ByteArray, profile: Profile, items: List<Pair<Item, ByteArray>>): ByteArray {
        val list = items.mapIndexed { i, pair ->
            val piece = pair.first
            "Image ${i + 2}: ${piece.category} — ${piece.name}${if (piece.color.isNotBlank()) " (${piece.color})" else ""}"
        }.joinToString("\n")
        val prompt = """
Virtual try-on. Image 1 is a photo of a real person. The other images are clothing items from their own wardrobe:
$list
Generate ONE photorealistic, full-length (head to toe) photo of the SAME person from image 1 wearing exactly these items together as one outfit.
- Keep the person's identity unchanged: same face, facial hair, hairstyle, skin tone, body build and proportions (height ${profile.height.ifBlank { "as in the photo" }}).
- Reproduce each garment faithfully: same colour, pattern, fabric texture, logos and fit. Tops tucked or untucked as looks natural for the outfit.
- Natural relaxed standing pose facing the camera, camera at chest height, soft natural daylight, plain light-grey studio background, portrait 3:4 framing.
- No extra accessories or clothes that are not listed.
""".trimIndent()
        return ai.generateImage(prompt, listOf(person) + items.map { it.second })
    }

    suspend fun gaps(items: List<Item>, profile: Profile): Gaps {
        val prompt = """
You are a stylist auditing a client's wardrobe.
CLIENT
${profileText(profile)}
WARDROBE
${catalog(items)}
Reply with ONLY JSON: {"verdict":"one sentence on the wardrobe's strengths","buys":[{"item":"specific piece, colour and fit","why":"what gap it fills / how many new outfits it unlocks"}],"unused":"one sentence on pieces to wear more or let go"} with 4-5 buys ordered by impact.
""".trimIndent()
        val o = ai.askJson(prompt) as? JSONObject ?: throw AiException("The AI reply wasn't readable — please try again.")
        val b = o.optJSONArray("buys") ?: JSONArray()
        val buys = (0 until b.length()).mapNotNull { i -> b.optJSONObject(i)?.let { it.str("item") to it.str("why") } }
        return Gaps(o.str("verdict"), buys, o.str("unused"))
    }
}
