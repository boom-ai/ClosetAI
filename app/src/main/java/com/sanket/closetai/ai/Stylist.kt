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

    suspend fun outfits(
        items: List<Item>, profile: Profile, occasion: String, weather: String, vibe: String,
        anchor: Item?, extra: String,
    ): List<Look> {
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ENGLISH).format(Date())
        val prompt = """
You are an expert personal stylist building outfits ONLY from the client's own wardrobe below.
CLIENT
${profileText(profile)}
REQUEST
Occasion: $occasion. Weather: $weather. Vibe: $vibe. ${anchor?.let { "Every outfit MUST include item ${it.id} (${it.name})." } ?: ""} Extra: ${extra.ifBlank { "none" }}.
Today: $today.
WARDROBE (id | category | name | details | last worn)
${catalog(items)}
RULES
- Give 3 distinct outfits. Use only ids from the list. Each: a top (or one-piece), a bottom, and footwear if any is owned; add a layer/accessories when they genuinely help.
- Match formality to the occasion and fabrics to the weather. Prefer the client's best colours and fits. Avoid items worn in the last 3 days unless needed. Don't use the same main piece in all 3.
- Tips must be concrete: tuck or not, sleeve roll, buttons, cuff/hem, how to wear accessories, a grooming touch.
- If a piece is genuinely missing for this occasion, name it in addOn (one short item), else null.
Reply with ONLY JSON: {"outfits":[{"title":"3-5 word name","itemIds":["id",...],"why":"1-2 sentences on why it works for them","tips":["3-4 short tips"],"addOn":string|null}]}
""".trimIndent()
        val v = ai.askJson(prompt)
        val arr = when (v) {
            is JSONObject -> v.optJSONArray("outfits") ?: JSONArray()
            is JSONArray -> v
            else -> JSONArray()
        }
        val ids = items.map { it.id }.toSet()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Look.from(it) }
                ?.let { l -> l.copy(itemIds = l.itemIds.filter { it in ids }.distinct(), tips = l.tips.take(5)) }
                ?.takeIf { it.itemIds.isNotEmpty() }
        }
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
