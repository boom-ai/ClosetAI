package com.sanket.closetai.ai

import com.sanket.closetai.data.FORMALITY_LABELS
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Weather
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Hue 0-360, saturation and lightness 0-1. known=false when the colour could not be resolved. */
internal data class Hsl(val h: Double, val s: Double, val l: Double, val known: Boolean)

/** What today's temperature says about a layer over the base top. */
internal data class LayerRule(val forbid: Boolean, val required: Boolean, val lo: Int, val hi: Int, val maxLayers: Int)

/** One scored outfit. All scores are 0..1. */
internal data class Combo(
    val top: Item?,
    val layers: List<Item>,
    val bottom: Item?,
    val shoes: Item?,
    val onePiece: Item?,
    val accessories: List<Item>,
    val score: Double,
    val colour: Double,
    val weather: Double,
    val formality: Double,
    val compromise: Boolean,
) {
    /** Top / one-piece, layers, bottom (no shoes, no accessories). */
    val core: List<Item> get() = listOfNotNull(top ?: onePiece) + layers + listOfNotNull(bottom)
    /** Display order: top, layers, bottom, shoes, accessories. */
    val pieces: List<Item> get() = core + listOfNotNull(shoes) + accessories
    val ids: List<String> get() = pieces.map { it.id }.distinct()
    val coreIds: Set<String> get() = core.map { it.id }.toSet()
    val key: String get() = (core + listOfNotNull(shoes)).map { it.id }.sorted().joinToString(",")
}

/** Everything about "today" that scoring needs, computed once per request. */
internal class StyleCtx(
    val req: StyleRequest,
    val t: Double,
    val rainy: Boolean,
    val uvHigh: Boolean,
    val humid: Boolean,
    val swing: Boolean,
    val rule: LayerRule,
    val occKind: String,
    val occLo: Int,
    val occHi: Int,
    val occTarget: Int,
    val available: List<Item>,
    val byId: Map<String, Item>,
    val ownsShoes: Boolean,
    val now: Long,
    val lastWorn: Map<String, Long>,
    val recentCombos: Set<String>,
)

internal data class Shortlist(val ctx: StyleCtx, val combos: List<Combo>)

/**
 * Deterministic outfit engine: weather, occasion, colour, rotation and taste scoring.
 * The LLM only picks and polishes from what this produces; with no AI it is the whole stylist.
 */
object Scorer {

    private const val DAY_MS: Double = 86_400_000.0

    // ------------------------------------------------------------------ weather

    /** Temperature the outfit should be built for (C). Unknown weather = mild 24. */
    fun effectiveTemp(w: Weather?, hour: Int, bias: Double): Double {
        val b = if (bias.isNaN()) 0.0 else bias.coerceIn(-6.0, 6.0)
        if (w == null) return 24.0 + b
        val feels = if (w.feels.isNaN()) (if (w.temp.isNaN()) 24.0 else w.temp) else w.feels
        val hi = if (w.tMax.isNaN()) feels else w.tMax
        val lo = if (w.tMin.isNaN()) feels else w.tMin
        var t = when {
            hour < 12 -> (feels + hi) / 2.0
            hour >= 17 -> (feels + lo) / 2.0
            else -> feels
        }
        if (!w.wind.isNaN() && w.wind > 20.0 && t < 18.0) t -= 2.0
        return t + b
    }

    internal fun layerRule(t: Double, hour: Int, rainy: Boolean): LayerRule = when {
        t >= 27.0 -> LayerRule(true, false, 0, 0, 0)
        t >= 24.0 -> if (rainy) LayerRule(true, false, 0, 0, 0) else LayerRule(false, false, 1, 3, 1)
        t >= 18.0 -> LayerRule(false, t < 20.0 && (hour < 10 || hour >= 18), 2, 4, 1)
        t >= 12.0 -> LayerRule(false, true, 3, 4, 1)
        else -> LayerRule(false, true, 4, 5, 2)
    }

    // ------------------------------------------------------------------ item attributes

    /** Detected warmth 1-5, or a sensible default for the category. */
    fun warmthOf(item: Item): Int {
        if (item.warmth in 1..5) return item.warmth
        val base = when (item.category) {
            "T-Shirt", "Polo", "Tank Top", "Top / Blouse" -> 1
            "Shirt", "Kurta" -> 2
            "Overshirt", "Blazer" -> 3
            "Sweater", "Hoodie", "Sweatshirt", "Jacket" -> 4
            "Coat" -> 5
            "Shorts", "Pyjama / Salwar" -> 1
            "Chinos", "Trousers", "Joggers", "Track Pants", "Skirt" -> 2
            "Jeans" -> 3
            "Dress", "Ethnic Set" -> 2
            "Suit" -> 3
            "Sandals / Slides" -> 1
            "Sneakers", "Loafers", "Formal Shoes", "Juttis" -> 2
            "Boots" -> 4
            else -> 1
        }
        val m = item.material.lowercase()
        return when {
            m.contains("linen") -> max(1, base - 1)
            m.contains("wool") || m.contains("fleece") -> min(5, base + 1)
            else -> base
        }
    }

    internal fun sleeveOf(item: Item): String {
        val s = item.sleeve.lowercase()
        if (s == "none" || s == "short" || s == "long") return s
        return when (item.category) {
            "T-Shirt", "Polo" -> "short"
            "Tank Top" -> "none"
            "Shirt", "Kurta", "Top / Blouse" -> "long"
            else -> if (item.group == "Layers") "long" else "n/a"
        }
    }

    internal fun roleOf(item: Item): String {
        if (item.group == "Layers") {
            val r = item.layerRole.lowercase()
            if (r == "mid" || r == "outer") return r
            return if (item.category == "Jacket" || item.category == "Coat" || item.category == "Blazer") "outer" else "mid"
        }
        return if (item.group == "Tops") "base" else "none"
    }

    private fun isKnitTop(item: Item): Boolean =
        item.category == "Sweater" || item.category == "Hoodie" || item.category == "Sweatshirt"

    private fun rainOk(item: Item): Boolean =
        item.rainSafe && item.category != "Juttis" && !item.material.lowercase().contains("suede")

    private fun hasTag(item: Item, tag: String): Boolean = item.styleTags.any { it.equals(tag, true) }

    private fun isEthnic(item: Item): Boolean =
        item.category == "Kurta" || item.category == "Ethnic Set" || item.category == "Juttis" ||
            item.category == "Pyjama / Salwar" || hasTag(item, "ethnic") || hasTag(item, "festive")

    private fun isPatterned(item: Item): Boolean {
        val p = item.pattern.lowercase()
        return p == "striped" || p == "checked" || p == "printed" || p == "graphic"
    }

    // ------------------------------------------------------------------ colour

    private val colourNames: List<Pair<String, String>> = listOf(
        "off-white" to "#f2efe6", "off white" to "#f2efe6", "cream" to "#f3ead3", "ivory" to "#f4f0e0",
        "navy" to "#1f2a44", "indigo" to "#27375e", "charcoal" to "#36393f", "black" to "#111111",
        "white" to "#fafafa", "grey" to "#8d9096", "gray" to "#8d9096", "silver" to "#c0c2c6",
        "beige" to "#d8c7a4", "khaki" to "#bfa87a", "tan" to "#b98f5e", "camel" to "#b98f5e", "sand" to "#d6c3a1",
        "brown" to "#6b4a32", "coffee" to "#5a3e2b", "olive" to "#5c6238", "maroon" to "#6d1f2c",
        "burgundy" to "#6d1f2c", "wine" to "#6d1f2c", "mustard" to "#c99a2e", "rust" to "#a8502a",
        "teal" to "#1f7a78", "turquoise" to "#2aa7a1", "mint" to "#a9dcc4", "sky" to "#8fbfe6",
        "light blue" to "#9cc3e8", "denim" to "#3f5f8a", "blue" to "#2f5fb3", "green" to "#2f7d4a",
        "red" to "#c0392b", "pink" to "#e79ab3", "lavender" to "#b9a7dc", "purple" to "#6c3f9c",
        "violet" to "#6c3f9c", "yellow" to "#e8c93a", "orange" to "#e07b2a", "peach" to "#f2b796",
    )

    private fun hexToHsl(hex: String): Hsl? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        val v = h.toIntOrNull(16) ?: return null
        val r = ((v shr 16) and 255) / 255.0
        val g = ((v shr 8) and 255) / 255.0
        val b = (v and 255) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2.0
        val d = mx - mn
        if (d < 0.0001) return Hsl(0.0, 0.0, l, true)
        val s = if (l > 0.5) d / (2.0 - mx - mn) else d / (mx + mn)
        var hue = when (mx) {
            r -> (g - b) / d + (if (g < b) 6.0 else 0.0)
            g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        }
        hue *= 60.0
        return Hsl(hue, s, l, true)
    }

    internal fun hslOf(item: Item): Hsl {
        hexToHsl(item.colorHex)?.let { return it }
        val name = item.color.lowercase()
        if (name.isNotBlank()) {
            for (p in colourNames) {
                if (name.contains(p.first)) {
                    hexToHsl(p.second)?.let { return it }
                }
            }
        }
        return Hsl(0.0, 0.0, 0.5, false)
    }

    private fun hueDist(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Colour family used for rules, taste features and descriptions. */
    internal fun familyOf(item: Item): String {
        val c = hslOf(item)
        if (!c.known) return "neutral"
        val h = c.h
        return when {
            c.l < 0.12 || (c.s < 0.15 && c.l < 0.2) -> "black"
            c.l > 0.9 || (c.s < 0.15 && c.l > 0.82) -> "white"
            c.s < 0.15 -> "grey"
            h in 205.0..250.0 && c.l < 0.28 -> "navy"
            h in 20.0..50.0 && c.s < 0.45 && c.l >= 0.6 -> "beige"
            h in 15.0..50.0 && c.l < 0.45 && c.s < 0.7 -> "brown"
            h in 20.0..50.0 && c.s < 0.45 -> "khaki"
            h in 60.0..100.0 && c.s < 0.45 && c.l < 0.45 -> "olive"
            (h < 15.0 || h >= 345.0) && c.l < 0.3 -> "maroon"
            h < 15.0 || h >= 345.0 -> "red"
            h < 40.0 -> "orange"
            h < 65.0 -> "yellow"
            h < 165.0 -> "green"
            h < 195.0 -> "teal"
            h < 255.0 -> "blue"
            h < 290.0 -> "purple"
            else -> "pink"
        }
    }

    private val neutralFamilies: Set<String> = setOf("black", "white", "grey", "navy", "beige", "brown", "khaki", "olive", "neutral")

    internal fun isNeutral(item: Item): Boolean {
        if (familyOf(item) in neutralFamilies) return true
        // blue denim behaves as a neutral
        val denim = item.category == "Jeans" || item.pattern.equals("denim", true) || item.material.lowercase().contains("denim")
        val c = hslOf(item)
        return denim && c.h in 195.0..255.0
    }

    internal fun depthOf(item: Item): String {
        val l = hslOf(item).l
        return if (l >= 0.65) "light" else if (l < 0.35) "dark" else "mid"
    }

    /** The rule table: how well two garments sit next to each other (0..1). */
    internal fun pairScore(a: Item, b: Item): Double {
        val ca = hslOf(a)
        val cb = hslOf(b)
        val na = isNeutral(a)
        val nb = isNeutral(b)
        val dl = abs(ca.l - cb.l)
        if (na && nb) {
            val fa = familyOf(a)
            val fb = familyOf(b)
            if ((fa == "black" && fb == "navy") || (fa == "navy" && fb == "black")) return 0.35
            if ((fa == "black" && fb == "brown" && cb.l < 0.35) || (fb == "black" && fa == "brown" && ca.l < 0.35)) return 0.4
            if (dl >= 0.25) return 1.0
            if (dl >= 0.12) return 0.7
            val textureDiffers = (a.material.isNotBlank() && b.material.isNotBlank() && !a.material.equals(b.material, true)) ||
                (a.pattern.isNotBlank() && b.pattern.isNotBlank() && !a.pattern.equals(b.pattern, true))
            return if (textureDiffers) 0.75 else 0.45
        }
        if (na || nb) return min(1.0, 0.9 + (if (dl >= 0.2) 0.1 else 0.0))
        val dh = hueDist(ca.h, cb.h)
        val minS = min(ca.s, cb.s)
        return when {
            dh <= 15.0 -> if (dl >= 0.25) 0.75 else 0.3
            dh <= 45.0 -> if (minS < 0.5) 0.7 else 0.55
            dh <= 150.0 -> if (minS < 0.35) 0.5 else 0.3
            else -> if (minS < 0.4 || min(ca.l, cb.l) < 0.3) 0.65 else 0.35
        }
    }

    private fun shoeScore(ctx: StyleCtx, shoes: Item, bottom: Item?, others: List<Item>): Double {
        val cs = hslOf(shoes)
        var s = 1.0
        if (!isNeutral(shoes)) {
            val echo = others.any { !isNeutral(it) && hueDist(hslOf(it).h, cs.h) <= 30.0 }
            s = if (echo) 0.9 else 0.5
        }
        if (bottom != null) {
            if (ctx.occTarget >= 4 && familyOf(shoes) == "brown" && familyOf(bottom) == "black") s = min(s, 0.4)
            // one continuous line from trouser to shoe flatters average height
            if (abs(hslOf(bottom).l - cs.l) < 0.25) s += 0.05
        }
        return s.coerceIn(0.0, 1.0)
    }

    private fun colourScore(ctx: StyleCtx, top: Item?, layers: List<Item>, bottom: Item?, shoes: Item?, onePiece: Item?): Double {
        val face: Item? = top ?: onePiece
        var base = if (top != null && bottom != null) pairScore(top, bottom) else 0.85
        if (layers.isNotEmpty() && face != null) {
            var sum = 0.0
            for (l in layers) {
                var p = pairScore(l, face)
                if (bottom != null) {
                    var pb = pairScore(l, bottom)
                    val sameAsBottom = familyOf(l) == familyOf(bottom) && abs(hslOf(l).l - hslOf(bottom).l) < 0.12 &&
                        bottom.category != "Jeans"
                    if (sameAsBottom) pb = min(pb, 0.5)
                    p = (p + pb) / 2.0
                }
                sum += p
            }
            base = base * 0.65 + (sum / layers.size) * 0.35
        }
        val core = listOfNotNull(face) + layers + listOfNotNull(bottom)
        var score = base
        if (shoes != null) score = base * 0.78 + shoeScore(ctx, shoes, bottom, core) * 0.22
        val all = core + listOfNotNull(shoes)
        val hues = all.filter { !isNeutral(it) }.map { familyOf(it) }.distinct().size
        if (hues > 2) score *= 0.5
        if (core.count { isPatterned(it) } >= 2) score *= 0.5
        if (all.count { it.boldness >= 3 } >= 2) score *= 0.6

        // colour next to the face
        val a = ctx.req.profile.analysis
        if (face != null) {
            val cf = hslOf(face)
            if (a != null && cf.known && !isNeutral(face)) {
                if (a.bestColors.any { sw -> nearSwatch(cf, sw.hex) }) score += 0.1
                if (a.avoidColors.any { sw -> nearSwatch(cf, sw.hex) }) score -= 0.15
            }
            if (a != null && isNeutral(face) && a.bestNeutrals.any { n -> n.lowercase().contains(familyOf(face)) }) score += 0.04
            val skin = (a?.skinTone ?: "").lowercase()
            val extreme = skin.contains("fair") || skin.contains("light") || skin.contains("pale") ||
                skin.contains("dark") || skin.contains("deep")
            // skin-like beige/sand right under the face washes out medium skin
            if (!extreme && cf.known && cf.h in 25.0..55.0 && cf.l in 0.5..0.75 && cf.s < 0.35) score -= 0.1
        }
        // lean frames drown in baggy-on-baggy
        if (top != null && bottom != null) {
            val ft = top.fit.lowercase()
            val fb = bottom.fit.lowercase()
            if (ft == "oversized" && (fb == "relaxed" || fb == "oversized")) score -= 0.1
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun nearSwatch(c: Hsl, hex: String): Boolean {
        val s = hexToHsl(hex) ?: return false
        if (s.s < 0.15) return false
        return hueDist(c.h, s.h) <= 25.0 && abs(c.l - s.l) < 0.25
    }

    // ------------------------------------------------------------------ per-item scores

    private fun rangeScore(v: Int, lo: Int, hi: Int): Double {
        val d = if (v < lo) lo - v else if (v > hi) v - hi else 0
        return when (d) {
            0 -> 1.0
            1 -> 0.5
            else -> 0.0
        }
    }

    private fun band(t: Double): Int = when {
        t >= 30.0 -> 0
        t >= 24.0 -> 1
        t >= 18.0 -> 2
        t >= 12.0 -> 3
        t >= 6.0 -> 4
        else -> 5
    }

    private val topLo = intArrayOf(1, 1, 1, 1, 2, 2)
    private val topHi = intArrayOf(1, 2, 2, 3, 3, 3)
    private val botLo = intArrayOf(1, 1, 2, 2, 3, 3)
    private val botHi = intArrayOf(2, 3, 3, 4, 4, 5)
    private val shoeLo = intArrayOf(1, 1, 2, 2, 3, 3)
    private val shoeHi = intArrayOf(2, 3, 3, 4, 5, 5)

    /** slot: "top" | "layer" | "bottom" | "shoes". Returns 0..1; 0 means wrong for today. */
    internal fun weatherItem(ctx: StyleCtx, item: Item, slot: String): Double {
        val t = ctx.t
        val b = band(t)
        val w = warmthOf(item)
        val m = item.material.lowercase()
        val c = hslOf(item)
        val asLayer = slot == "layer" || item.group == "Layers"
        var s = when {
            asLayer -> if (ctx.rule.forbid) 0.0 else rangeScore(w, ctx.rule.lo, ctx.rule.hi)
            slot == "bottom" -> rangeScore(w, botLo[b], botHi[b])
            slot == "shoes" -> rangeScore(w, shoeLo[b], shoeHi[b])
            else -> rangeScore(w, topLo[b], topHi[b])
        }
        if (slot == "shoes" && t < 14.0 && item.category == "Sandals / Slides") s = 0.0
        if (slot == "bottom" && t < 14.0 && (item.category == "Shorts" || item.length.equals("shorts", true))) s = 0.0

        // old detections only know seasons
        if (item.warmth !in 1..5 && item.seasons.isNotEmpty()) {
            val se = item.seasons.map { it.lowercase() }
            if (t >= 27.0 && se.all { it == "winter" }) s *= 0.4
            if (t < 12.0 && se.all { it == "summer" }) s *= 0.5
        }
        val synthetic = m.contains("polyester") || m.contains("synthetic") || m.contains("nylon")
        val breathable = m.contains("cotton") || m.contains("linen")
        if (t >= 30.0) {
            val heavy = m.contains("wool") || m.contains("fleece") || m.contains("leather") || m.contains("corduroy")
            if (heavy && slot != "shoes") s = 0.0
            if (item.weight.equals("heavy", true) && slot != "shoes") s *= 0.5
            val gymKit = ctx.occKind == "gym" && hasTag(item, "sporty")
            if (synthetic && slot != "shoes" && !gymKit) s *= 0.6
            if (item.category == "Jeans" && t >= 36.0) s *= 0.8
            if (slot == "top" && sleeveOf(item) == "long" && !m.contains("linen") && !item.weight.equals("light", true)) s *= 0.85
            if (item.category == "Kurta" && breathable) s += 0.05
            if (item.fit.equals("relaxed", true) && slot != "shoes") s *= 1.05
        }
        if (ctx.rainy) {
            if (!rainOk(item)) s *= 0.3
            if (m.contains("linen")) s *= 0.8
            if (slot == "bottom") {
                if (c.known && c.l > 0.75) s *= 0.6
                if (c.known && c.l < 0.35) s += 0.1
            }
            if (slot == "layer" && (item.category == "Blazer" || item.category == "Coat") && m.contains("wool")) s *= 0.3
            if (slot == "layer" && (synthetic || (c.known && c.l < 0.35))) s += 0.05
        }
        if (ctx.uvHigh && slot == "top" && c.known && c.l < 0.25) s *= 0.8
        if (ctx.humid && slot != "shoes") {
            if (synthetic) s *= 0.7
            if (breathable) s *= 1.1
        }
        return s.coerceIn(0.0, 1.0)
    }

    private fun gymOk(item: Item): Boolean = hasTag(item, "sporty") || item.category in setOf(
        "T-Shirt", "Tank Top", "Joggers", "Shorts", "Track Pants", "Sneakers", "Hoodie", "Sweatshirt",
    )

    /** How well one piece fits the occasion's formality range (0..1). */
    internal fun formalityItem(ctx: StyleCtx, item: Item): Double {
        if (ctx.occKind == "gym" && !gymOk(item)) return 0.0
        if (ctx.occKind == "religious" && (item.category == "Shorts" || item.category == "Tank Top")) return 0.0
        val f = item.formality.coerceIn(1, 5)
        var s = rangeScore(f, ctx.occLo, ctx.occHi)
        // inside the range, the occasion's sweet spot still wins
        if (s >= 1.0 && f != ctx.occTarget) s = 0.9
        // ethnic pieces belong to festive, family and religious days; elsewhere they are a stretch
        val ethnicPiece = item.category == "Kurta" || item.category == "Ethnic Set" || item.category == "Juttis" ||
            item.category == "Pyjama / Salwar"
        if (ethnicPiece) {
            if (ctx.occKind == "interview") return 0.0
            if (ctx.occKind == "office" || ctx.occKind == "date" || ctx.occKind == "party" || ctx.occKind == "travel") s = min(s, 0.5)
        }
        return s
    }

    private fun daysSince(ctx: StyleCtx, item: Item): Double? {
        val lw = ctx.lastWorn[item.id] ?: 0L
        return if (lw <= 0L) null else (ctx.now - lw) / DAY_MS
    }

    /** Rotation adjustment in score units: negative = worn too recently, positive = due an outing. */
    private fun rotationAdj(ctx: StyleCtx, item: Item): Double {
        if (item.group == "Footwear" || item.group == "Accessories") return 0.0
        val d = daysSince(ctx, item) ?: return 0.05
        val raw = when {
            d < 2.0 -> -0.25
            d < 3.0 -> -0.15
            d < 7.0 -> 0.0
            d < 21.0 -> 0.05
            else -> 0.1
        }
        return if (item.group == "Tops" || item.group == "One-piece" || raw > 0.0) raw else raw / 2.0
    }

    private fun wornToday(ctx: StyleCtx, item: Item): Boolean {
        if (item.group != "Tops") return false
        val d = daysSince(ctx, item) ?: return false
        return d < 1.0
    }

    private fun tasteOf(taste: Map<String, Double>, features: List<String>): Double {
        var sum = 0.0
        var n = 0
        for (f in features) {
            val w = taste[f]
            if (w != null && !w.isNaN()) { sum += w; n++ }
        }
        if (n == 0) return 0.5
        return (0.5 + 0.08 * sum / sqrt(n.toDouble())).coerceIn(0.0, 1.0)
    }

    private fun slotName(item: Item): String = when (item.group) {
        "Bottoms" -> "bottom"
        "Footwear" -> "shoe"
        "Layers" -> "layer"
        else -> "top"
    }

    private fun itemScore(ctx: StyleCtx, item: Item, slot: String): Double {
        val rot = (0.7 + 3.0 * rotationAdj(ctx, item)).coerceIn(0.0, 1.0)
        val taste = tasteOf(ctx.req.taste, listOf(slotName(item) + ":" + item.category, "fam:" + familyOf(item)))
        return 0.45 * weatherItem(ctx, item, slot) + 0.35 * formalityItem(ctx, item) + 0.10 * rot + 0.10 * taste
    }

    /** level 0 strict, 1 ignores "worn today", 2 ignores occasion mismatch, 3 ignores weather mismatch. */
    private fun allowed(ctx: StyleCtx, item: Item, slot: String, level: Int): Boolean {
        if (item.id == ctx.req.anchor?.id) return true
        if (level < 1 && wornToday(ctx, item)) return false
        if (level < 2 && formalityItem(ctx, item) <= 0.0) return false
        if (level < 3 && weatherItem(ctx, item, slot) <= 0.0) return false
        return true
    }

    // ------------------------------------------------------------------ taste

    /** Attribute-level description of an outfit so feedback generalises beyond the exact pieces. */
    fun tasteFeatures(pieces: List<Item>, direction: String): List<String> {
        val out = ArrayList<String>()
        val layers = pieces.filter { it.group == "Layers" }
        val top: Item? = pieces.firstOrNull { it.group == "Tops" } ?: pieces.firstOrNull { it.group == "One-piece" }
            ?: layers.firstOrNull()
        val bottom: Item? = pieces.firstOrNull { it.group == "Bottoms" }
        val shoes: Item? = pieces.firstOrNull { it.group == "Footwear" }
        val layer: Item? = layers.firstOrNull { it.id != top?.id }
        if (top != null) {
            out.add("top:" + top.category)
            out.add("fam:" + familyOf(top))
            out.add("pattern:" + top.pattern.lowercase().ifBlank { "solid" })
            if (top.fit.isNotBlank()) out.add("fitTop:" + top.fit.lowercase())
        }
        if (bottom != null) {
            out.add("bottom:" + bottom.category)
            if (bottom.fit.isNotBlank()) out.add("fitBot:" + bottom.fit.lowercase())
        }
        if (top != null && bottom != null) {
            out.add("pair:" + familyOf(top) + ">" + familyOf(bottom))
            out.add("depth:" + depthOf(top) + ">" + depthOf(bottom))
        }
        if (shoes != null) out.add("shoe:" + shoes.category)
        out.add("layer:" + (layer?.category ?: "none"))
        val core = pieces.filter { it.group != "Accessories" }
        core.flatMap { it.styleTags }.map { it.lowercase() }.distinct().take(4).forEach { out.add("tag:$it") }
        if (core.isNotEmpty()) out.add("form:" + (core.sumOf { it.formality }.toDouble() / core.size).roundToInt())
        val d = direction.trim().lowercase()
        if (d.isNotBlank()) out.add("dir:$d")
        return out.distinct()
    }

    /** Decays every weight by 0.97, then adds delta to the given features; weights stay within +-5. */
    fun updateTaste(taste: Map<String, Double>, features: List<String>, delta: Double): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (e in taste.entries) {
            val v = e.value
            if (!v.isNaN()) out[e.key] = v * 0.97
        }
        val d = if (delta.isNaN()) 0.0 else delta
        for (f in features.distinct()) {
            if (f.isBlank()) continue
            out[f] = ((out[f] ?: 0.0) + d).coerceIn(-5.0, 5.0)
        }
        return out.filterValues { abs(it) >= 0.01 }
    }

    fun comboKey(itemIds: List<String>): String = "combo:" + itemIds.sorted().joinToString(",")

    // ------------------------------------------------------------------ outfit evaluation

    private val casualBottoms: Set<String> = setOf("Shorts", "Joggers", "Track Pants")

    private fun breaksHardRule(ctx: StyleCtx, top: Item?, layers: List<Item>, bottom: Item?, shoes: Item?, onePiece: Item?): Boolean {
        val bc = bottom?.category ?: ""
        val tc = top?.category ?: ""
        val upper = listOfNotNull(top) + layers
        val blazer = upper.any { it.category == "Blazer" }
        if (tc == "Kurta") {
            if (bc in casualBottoms) return true
            if (layers.any { it.category == "Hoodie" || it.category == "Sweatshirt" || it.category == "Overshirt" || it.category == "Blazer" }) return true
        }
        if (blazer && (bc in casualBottoms || tc == "Tank Top" || upper.any { it.category == "Hoodie" })) return true
        if (shoes != null) {
            val sc = shoes.category
            if (sc == "Formal Shoes" && bc in casualBottoms) return true
            if (sc == "Juttis" && !(tc == "Kurta" || onePiece?.category == "Ethnic Set")) return true
            if (sc == "Sandals / Slides" && (blazer || ctx.occTarget >= 4 || onePiece?.category == "Suit")) return true
        }
        if (ctx.occKind == "religious" && (bc == "Shorts" || tc == "Tank Top")) return true
        if (onePiece?.category == "Suit" && layers.isNotEmpty()) return true
        return false
    }

    private fun vibeBonus(ctx: StyleCtx, core: List<Item>): Double {
        if (core.isEmpty()) return 0.0
        val v = ctx.req.vibe.lowercase()
        val meanF = core.sumOf { it.formality }.toDouble() / core.size
        val allSolidNeutral = core.all { !isPatterned(it) && isNeutral(it) }
        return when {
            v.contains("minimal") -> if (core.none { isPatterned(it) } && core.count { !isNeutral(it) } <= 1) 0.04 else 0.0
            v.contains("sharp") -> if (meanF >= ctx.occTarget) 0.04 else 0.0
            v.contains("relaxed") -> if (core.any { it.fit.equals("relaxed", true) } || meanF <= ctx.occTarget) 0.03 else 0.0
            v.contains("bold") -> if (!allSolidNeutral) 0.04 else -0.02
            v.contains("classic") -> if (core.any { hasTag(it, "classic") } || allSolidNeutral) 0.03 else 0.0
            else -> 0.0
        }
    }

    /** Scores one outfit; null when it breaks a hard styling rule or is a banned combo. */
    internal fun evaluate(ctx: StyleCtx, top: Item?, layers: List<Item>, bottom: Item?, shoes: Item?, onePiece: Item?): Combo? {
        if (top == null && onePiece == null) return null
        if (onePiece == null && bottom == null) return null
        if (breaksHardRule(ctx, top, layers, bottom, shoes, onePiece)) return null
        val core = listOfNotNull(top ?: onePiece) + layers + listOfNotNull(bottom)
        val wearables = core + listOfNotNull(shoes)
        val forms = wearables.map { it.formality.coerceIn(1, 5) }
        val spread = (forms.maxOrNull() ?: 3) - (forms.minOrNull() ?: 3)
        if (spread > 2) return null
        val ids = wearables.map { it.id }
        val banned = ctx.req.taste[comboKey(ids)]
        if (banned != null && banned <= -3.0) return null

        val colour = colourScore(ctx, top, layers, bottom, shoes, onePiece)

        // weather: each piece + whether the whole upper body is warm enough
        var wSum = 0.0
        val face: Item = top ?: onePiece ?: return null
        wSum += weatherItem(ctx, face, "top")
        for (l in layers) wSum += weatherItem(ctx, l, "layer")
        if (bottom != null) wSum += weatherItem(ctx, bottom, "bottom")
        if (shoes != null) wSum += weatherItem(ctx, shoes, "shoes")
        val wMean = wSum / wearables.size
        val upper = warmthOf(face) + layers.sumOf { warmthOf(it) }
        val target = (1.0 + (28.0 - ctx.t) / 2.5).coerceIn(1.0, 10.0)
        val warmFit = 1.0 - min(1.0, max(0.0, abs(upper - target) - 1.0) / 3.0)
        var weather = 0.6 * wMean + 0.4 * warmFit
        if (ctx.swing && layers.isNotEmpty() && !ctx.rule.forbid) weather += 0.05
        weather = weather.coerceIn(0.0, 1.0)

        // occasion
        var formality = wearables.sumOf { formalityItem(ctx, it) } / wearables.size
        if (spread == 2) formality *= 0.5
        val ethnic = core.any { isEthnic(it) }
        if (ethnic) formality += when (ctx.occKind) {
            "wedding" -> 0.15
            "family", "religious" -> 0.1
            else -> 0.0
        }
        if (ctx.occKind == "party" && (layers + listOf(face)).any { hslOf(it).known && hslOf(it).l < 0.35 }) formality += 0.05
        formality = formality.coerceIn(0.0, 1.0)

        val taste = tasteOf(ctx.req.taste, tasteFeatures(wearables, ""))
        val rot = (0.7 + 3.0 * (core.sumOf { rotationAdj(ctx, it) } / core.size)).coerceIn(0.0, 1.0)
        val tagsA = (top ?: onePiece)?.styleTags?.map { it.lowercase() } ?: emptyList()
        val tagsB = bottom?.styleTags?.map { it.lowercase() } ?: emptyList()
        val tag = if (tagsA.isEmpty() || tagsB.isEmpty()) 0.5 else if (tagsA.any { it in tagsB }) 1.0 else 0.3

        var score = 0.30 * colour + 0.25 * weather + 0.20 * formality + 0.10 * taste + 0.10 * rot + 0.05 * tag
        score += vibeBonus(ctx, core)
        if (comboKeyPlain(ids) in ctx.recentCombos) score -= 0.3
        val conf = wearables.sumOf { if (it.confidence.isNaN()) 1.0 else it.confidence.coerceIn(0.0, 1.0) } / wearables.size
        if (conf < 0.6) score *= 0.95

        var compromise = !allowed(ctx, face, "top", 1)
        for (l in layers) if (!allowed(ctx, l, "layer", 1)) compromise = true
        if (bottom != null && !allowed(ctx, bottom, "bottom", 1)) compromise = true
        if (shoes != null && !allowed(ctx, shoes, "shoes", 1)) compromise = true
        val hasLayer = layers.isNotEmpty() || face.group == "Layers" || onePiece?.category == "Suit"
        if (ctx.rule.required && !hasLayer) { compromise = true; score *= 0.9 }
        if (compromise) score *= 0.92
        if (score.isNaN()) score = 0.0
        return Combo(top, layers, bottom, shoes, onePiece, emptyList(), score.coerceIn(0.0, 1.0), colour, weather, formality, compromise)
    }

    private fun comboKeyPlain(ids: List<String>): String = ids.sorted().joinToString(",")

    // ------------------------------------------------------------------ shortlist

    private fun occasionRange(occasion: String): Pair<String, IntArray> {
        val o = occasion.lowercase()
        return when {
            o.contains("interview") -> "interview" to intArrayOf(4, 5, 4)
            o.contains("wedding") || o.contains("festive") -> "wedding" to intArrayOf(4, 5, 5)
            o.contains("office") || o.contains("work") -> "office" to intArrayOf(3, 4, 3)
            o.contains("date") -> "date" to intArrayOf(3, 4, 3)
            o.contains("party") || o.contains("night") -> "party" to intArrayOf(3, 4, 3)
            o.contains("family") -> "family" to intArrayOf(3, 4, 3)
            o.contains("travel") -> "travel" to intArrayOf(1, 3, 2)
            o.contains("gym") || o.contains("sport") -> "gym" to intArrayOf(1, 2, 1)
            o.contains("religious") || o.contains("temple") || o.contains("gurdwara") -> "religious" to intArrayOf(2, 4, 3)
            o.contains("college") || o.contains("casual") -> "college" to intArrayOf(2, 3, 2)
            else -> "other" to intArrayOf(2, 4, 3)
        }
    }

    internal fun context(req: StyleRequest): StyleCtx {
        val w = req.weather
        val hour = req.hour.coerceIn(0, 23)
        val t = effectiveTemp(w, hour, req.tempBias)
        val rainy = w != null && (w.isRainy || w.rainProb >= 50)
        val uvHigh = w != null && !w.uv.isNaN() && w.uv >= 8.0 && hour in 10..16
        val humid = w != null && w.humidity >= 70 && t >= 28.0
        val swing = w != null && !w.tMax.isNaN() && !w.tMin.isNaN() && (w.tMax - w.tMin) >= 12.0 && w.tMin < 16.0
        val occ = occasionRange(req.occasion)
        val anchorId = req.anchor?.id
        val available = req.items.filter { i ->
            i.id == anchorId || (!i.inLaundry && !(i.detectFailed && i.category == "Other"))
        }.distinctBy { it.id }
        val now = System.currentTimeMillis()
        val last = HashMap<String, Long>()
        for (i in available) last[i.id] = i.lastWorn
        val recent = HashSet<String>()
        for (h in req.history) {
            for (id in h.itemIds) {
                val cur = last[id]
                if (cur != null && h.at > cur) last[id] = h.at
            }
            if (h.at > 0L && (now - h.at) / DAY_MS <= 14.0) {
                val wearable = h.itemIds.filter { id -> available.any { it.id == id && it.group != "Accessories" } }
                if (wearable.size >= 2) recent.add(comboKeyPlain(wearable))
            }
        }
        return StyleCtx(
            req = req, t = t, rainy = rainy, uvHigh = uvHigh, humid = humid, swing = swing,
            rule = layerRule(t, hour, rainy), occKind = occ.first, occLo = occ.second[0], occHi = occ.second[1],
            occTarget = occ.second[2], available = available, byId = available.associateBy { it.id },
            ownsShoes = available.any { it.group == "Footwear" }, now = now, lastWorn = last, recentCombos = recent,
        )
    }

    private fun bestOf(ctx: StyleCtx, items: List<Item>, slot: String, n: Int, level: Int): List<Item> {
        val anchorId = ctx.req.anchor?.id
        val ok = items.filter { allowed(ctx, it, slot, level) }.sortedByDescending { itemScore(ctx, it, slot) }
        val head = ok.take(n)
        val anchor = ok.firstOrNull { it.id == anchorId }
        return if (anchor != null && anchor !in head) head + anchor else head
    }

    private fun enumerate(ctx: StyleCtx, level: Int): List<Combo> {
        val anchor = ctx.req.anchor
        val anchorId = anchor?.id
        val av = ctx.available
        val tops = bestOf(ctx, av.filter { it.group == "Tops" }, "top", 8, level)
        val layersAll = av.filter { it.group == "Layers" }
        val knitTops = bestOf(ctx, layersAll.filter { isKnitTop(it) }, "layer", 3, level)
        val bottoms = bestOf(ctx, av.filter { it.group == "Bottoms" }, "bottom", 6, level)
        val shoesList = bestOf(ctx, av.filter { it.group == "Footwear" }, "shoes", 4, level)
        val onePieces = bestOf(ctx, av.filter { it.group == "One-piece" }, "top", 3, level)
        var layerPool = bestOf(ctx, layersAll, "layer", 4, level)
        // a layer is never a good compromise when it is too hot for one - only an explicit anchor overrides
        if (ctx.rule.forbid) layerPool = layerPool.filter { it.id == anchorId }
        val shoeOpts: List<Item?> = if (ctx.ownsShoes) shoesList else listOf<Item?>(null)
        val out = ArrayList<Combo>()

        fun layerOptions(top: Item?, suit: Boolean): List<List<Item>> {
            val topIsLayer = top != null && top.group == "Layers"
            val singles = if (suit) emptyList() else layerPool.filter { it.id != top?.id && (!topIsLayer || roleOf(it) == "outer") }
            val needs = ctx.rule.required && !topIsLayer && !suit
            val opts = ArrayList<List<Item>>()
            if (!needs || singles.isEmpty() || level >= 3) opts.add(emptyList())
            for (s in singles) opts.add(listOf(s))
            if (ctx.rule.maxLayers >= 2 && !topIsLayer) {
                for (m in singles) {
                    if (roleOf(m) != "mid") continue
                    for (o in singles) if (roleOf(o) == "outer") opts.add(listOf(m, o))
                }
            }
            return opts
        }

        val topSlot = tops + (if (ctx.rule.forbid) knitTops.filter { it.id == anchorId } else knitTops)
        for (t in topSlot) {
            val lo = layerOptions(t, false)
            for (b in bottoms) for (s in shoeOpts) for (ls in lo) {
                evaluate(ctx, t, ls, b, s, null)?.let { out.add(it) }
            }
        }
        for (op in onePieces) {
            val lo = layerOptions(null, op.category == "Suit")
            for (s in shoeOpts) for (ls in lo) {
                evaluate(ctx, null, ls, null, s, op)?.let { out.add(it) }
            }
        }
        val anchored = if (anchor == null || anchor.group == "Accessories") out
        else out.filter { c -> c.core.any { it.id == anchorId } || c.shoes?.id == anchorId }
        return anchored.sortedByDescending { it.score }
    }

    /** Greedy pick of the best candidates that still differ from each other. */
    private fun diverse(ctx: StyleCtx, sorted: List<Combo>, limit: Int): List<Combo> {
        val anchorId = ctx.req.anchor?.id
        val picked = ArrayList<Combo>()
        val keys = HashSet<String>()
        val seenCore = HashSet<Set<String>>()
        val topUse = HashMap<String, Int>()
        val botUse = HashMap<String, Int>()
        for (pass in 0..1) {
            val maxTop = if (pass == 0) 2 else 4
            val maxBot = if (pass == 0) 3 else 6
            for (c in sorted) {
                if (picked.size >= limit) break
                if (c.key in keys) continue
                // pass 0: one shoe choice per core outfit
                if (pass == 0 && c.coreIds in seenCore) continue
                val tid = (c.top ?: c.onePiece)?.id ?: ""
                val bid = c.bottom?.id ?: ""
                if (tid != anchorId && (topUse[tid] ?: 0) >= maxTop) continue
                if (bid.isNotBlank() && bid != anchorId && (botUse[bid] ?: 0) >= maxBot) continue
                picked.add(c)
                keys.add(c.key)
                seenCore.add(c.coreIds)
                topUse[tid] = (topUse[tid] ?: 0) + 1
                if (bid.isNotBlank()) botUse[bid] = (botUse[bid] ?: 0) + 1
            }
        }
        return picked.sortedByDescending { it.score }
    }

    /** Pre-scores the wardrobe for this request and returns the candidates worth showing to the AI. */
    internal fun shortlist(req: StyleRequest): Shortlist {
        val ctx = context(req)
        val limit = if (ctx.available.size < 10) 6 else 10
        var best: List<Combo> = emptyList()
        for (level in 0..3) {
            val found = diverse(ctx, enumerate(ctx, level), limit)
            if (found.size > best.size || best.isEmpty()) best = found
            val distinctCores = best.map { it.coreIds }.distinct().size
            // weather is only relaxed when almost nothing else can be built
            if (distinctCores >= 3 || (level == 2 && distinctCores >= 2)) break
        }
        val anchor = req.anchor
        if (anchor != null && anchor.group == "Accessories") best = best.map { it.copy(accessories = listOf(anchor)) }
        return Shortlist(ctx, best)
    }

    /**
     * Rebuilds a scored outfit from ids the AI returned. Null when the ids do not form a valid outfit,
     * or differ from [base] by more than one swapped piece.
     */
    internal fun comboFromIds(ctx: StyleCtx, ids: List<String>, base: Combo?): Combo? {
        val pieces = ids.distinct().mapNotNull { ctx.byId[it] }
        val anchor = ctx.req.anchor
        val topsG = pieces.filter { it.group == "Tops" }
        val layersG = pieces.filter { it.group == "Layers" }
        val bottoms = pieces.filter { it.group == "Bottoms" }
        val shoesG = pieces.filter { it.group == "Footwear" }
        val ones = pieces.filter { it.group == "One-piece" }
        if (topsG.size > 1 || bottoms.size > 1 || shoesG.size > 1 || ones.size > 1) return null
        val onePiece: Item? = ones.firstOrNull()
        if (onePiece != null && (bottoms.isNotEmpty() || topsG.isNotEmpty())) return null
        val top: Item? = if (onePiece != null) null
        else topsG.firstOrNull() ?: layersG.firstOrNull { isKnitTop(it) } ?: layersG.firstOrNull()
        val layers = layersG.filter { it.id != top?.id }.sortedBy { if (roleOf(it) == "outer") 1 else 0 }
        if (layers.size > 2) return null
        val bottom: Item? = bottoms.firstOrNull()
        val shoes: Item? = shoesG.firstOrNull()
        if (ctx.ownsShoes && shoes == null) return null
        if (ctx.rule.forbid && (layers.any { it.id != anchor?.id } || (top != null && top.group == "Layers" && top.id != anchor?.id))) return null
        val scored = evaluate(ctx, top, layers, bottom, shoes, onePiece) ?: return null
        if (anchor != null && anchor.group != "Accessories" && scored.ids.none { it == anchor.id }) return null
        if (base != null) {
            val a = (base.core + listOfNotNull(base.shoes)).map { it.id }.toSet()
            val b = (scored.core + listOfNotNull(scored.shoes)).map { it.id }.toSet()
            if ((b - a).size > 1 || (a - b).size > 1) return null
        }
        val acc = ArrayList<Item>()
        if (anchor != null && anchor.group == "Accessories") acc.add(anchor)
        for (p in pieces) {
            if (p.group == "Accessories" && p.category != "Other" && acc.none { it.id == p.id || it.category == p.category } && acc.size < 3) acc.add(p)
        }
        return scored.copy(accessories = acc)
    }

    /** Final 3: different core pieces where the wardrobe allows, overlapping only when it is tiny. */
    internal fun <T> pickDiverse(ranked: List<T>, n: Int, sharedLimit: Int, coreOf: (T) -> Set<String>, keyOf: (T) -> String): List<T> {
        val picked = ArrayList<T>()
        val keys = HashSet<String>()
        for (x in ranked) {
            if (picked.size >= n) break
            if (keyOf(x) in keys) continue
            val c = coreOf(x)
            if (picked.all { p -> coreOf(p).intersect(c).size <= sharedLimit }) { picked.add(x); keys.add(keyOf(x)) }
        }
        for (x in ranked) {
            if (picked.size >= n) break
            val c = coreOf(x)
            if (keyOf(x) !in keys && picked.none { p -> coreOf(p) == c }) { picked.add(x); keys.add(keyOf(x)) }
        }
        for (x in ranked) {
            if (picked.size >= n) break
            if (keyOf(x) !in keys) { picked.add(x); keys.add(keyOf(x)) }
        }
        return picked
    }

    internal fun sharedLimit(ctx: StyleCtx): Int {
        val a = ctx.req.anchor
        return if (a != null && a.group != "Accessories" && a.group != "Footwear") 2 else 1
    }

    /** safe / elevated / personality for up to 3 looks, from what the looks actually are. */
    internal fun directions(combos: List<Combo>): List<String> {
        if (combos.isEmpty()) return emptyList()
        if (combos.size == 1) return listOf("safe")
        fun meanF(c: Combo): Double = c.core.sumOf { it.formality }.toDouble() / max(1, c.core.size)
        fun flair(c: Combo): Int = c.core.count { !isNeutral(it) } * 2 + c.core.count { isPatterned(it) } * 2 +
            c.core.sumOf { it.boldness.coerceIn(1, 3) - 1 }
        val out = MutableList(combos.size) { "safe" }
        val pers = combos.indices.maxByOrNull { flair(combos[it]) } ?: 0
        if (flair(combos[pers]) > 0) out[pers] = "personality"
        val rest = combos.indices.filter { out[it] == "safe" }
        if (rest.size >= 2) {
            val elev = rest.maxByOrNull { meanF(combos[it]) } ?: rest[0]
            val lowest = rest.minOf { meanF(combos[it]) }
            if (meanF(combos[elev]) > lowest || out.none { it == "personality" }) out[elev] = "elevated"
        }
        return out
    }

    /** Accessories the on-device stylist adds by itself: belt, watch, sun protection. */
    internal fun autoAccessories(ctx: StyleCtx, c: Combo): List<Item> {
        val acc = ArrayList<Item>(c.accessories)
        val owned = ctx.available.filter { it.group == "Accessories" && it.category != "Other" }
        fun add(i: Item?) { if (i != null && acc.size < 3 && acc.none { it.id == i.id || it.category == i.category }) acc.add(i) }
        val tucked = c.top != null && c.top.category == "Shirt" && ctx.occTarget >= 3
        if (tucked && c.bottom != null && c.bottom.category != "Joggers" && c.bottom.category != "Track Pants") {
            val shoeL = c.shoes?.let { hslOf(it).l } ?: 0.3
            add(owned.filter { it.category == "Belt" }.minByOrNull { abs(hslOf(it).l - shoeL) })
        }
        if (ctx.occKind != "gym") add(owned.firstOrNull { it.category == "Watch" })
        if (ctx.uvHigh && ctx.occTarget <= 3) add(owned.firstOrNull { it.category == "Sunglasses" } ?: owned.firstOrNull { it.category == "Cap / Hat" })
        return acc
    }

    // ------------------------------------------------------------------ words (prompt facts + offline text)

    private fun colourWord(i: Item): String {
        val c = i.color.trim()
        val w = if (c.isNotBlank()) c else familyOf(i).let { if (it == "neutral") "" else it }
        return w.take(18).replaceFirstChar { it.uppercase() }
    }

    private fun shortCat(i: Item): String = when (i.category) {
        "T-Shirt" -> "tee"
        "Sandals / Slides" -> "slides"
        "Top / Blouse" -> "top"
        "Pyjama / Salwar" -> "pyjama"
        "Formal Shoes" -> "dress shoes"
        "Track Pants" -> "track pants"
        else -> i.category.lowercase()
    }

    private fun label(i: Item): String = (colourWord(i) + " " + shortCat(i)).trim()

    internal fun warmthVerdict(ctx: StyleCtx, c: Combo): String {
        val face: Item = c.top ?: c.onePiece ?: return "ok"
        val upper = warmthOf(face) + c.layers.sumOf { warmthOf(it) }
        val target = (1.0 + (28.0 - ctx.t) / 2.5).coerceIn(1.0, 10.0)
        return when {
            upper - target > 1.5 -> "runs warm"
            target - upper > 1.5 -> "runs cool"
            else -> "ok"
        }
    }

    /** One compact line of pre-computed facts about a candidate for the AI. */
    internal fun facts(ctx: StyleCtx, c: Combo): String {
        val face: Item? = c.top ?: c.onePiece
        val sb = StringBuilder()
        if (face != null) sb.append(familyOf(face)).append("(").append(depthOf(face)).append(") ").append(shortCat(face))
        for (l in c.layers) sb.append(" under ").append(familyOf(l)).append("(").append(depthOf(l)).append(") ").append(shortCat(l))
        val b = c.bottom
        if (b != null) sb.append(" over ").append(familyOf(b)).append("(").append(depthOf(b)).append(") ").append(shortCat(b))
        val s = c.shoes
        if (s != null) sb.append(", ").append(familyOf(s)).append(" ").append(shortCat(s))
        val f = c.core.sumOf { it.formality }.toDouble() / max(1, c.core.size)
        return "colour=\"" + sb.toString() + "\" warmth=" + warmthVerdict(ctx, c) + " formality=" + round1(f) +
            " flags=" + (if (c.compromise) "compromise" else "-")
    }

    internal fun round1(v: Double): Double = if (v.isNaN()) 0.0 else Math.round(v * 10.0) / 10.0

    internal fun titleOf(c: Combo): String {
        val lead: Item? = c.layers.lastOrNull() ?: c.top ?: c.onePiece
        val b = c.bottom
        val a = if (lead != null) label(lead) else "Outfit"
        return if (b != null) a + ", " + label(b).lowercase() else a
    }

    internal fun whyOf(ctx: StyleCtx, c: Combo): String {
        val face: Item = c.top ?: c.onePiece ?: return "A balanced look from your wardrobe."
        val b = c.bottom
        val sb = StringBuilder()
        if (b != null) {
            val nf = isNeutral(face)
            val nb = isNeutral(b)
            val dl = abs(hslOf(face).l - hslOf(b).l)
            sb.append(label(face)).append(" with ").append(label(b).lowercase()).append(": ")
            sb.append(
                when {
                    nf && nb && dl >= 0.25 -> "two neutrals with a clear light-dark contrast, which suits your colouring."
                    nf && nb -> "a quiet tonal pairing that reads clean and put-together."
                    nf || nb -> "one colour doing the talking, grounded by a neutral."
                    else -> "two colours that sit comfortably together without competing."
                },
            )
        } else {
            sb.append(label(face)).append(" carries the whole look, so the rest stays simple.")
        }
        if (c.layers.isNotEmpty()) sb.append(" The ").append(shortCat(c.layers.last())).append(" adds structure up top.")
        val f = (c.core.sumOf { it.formality }.toDouble() / max(1, c.core.size)).roundToInt().coerceIn(1, 5)
        sb.append(" ").append(FORMALITY_LABELS[f - 1]).append(" level, right for ").append(ctx.req.occasion.lowercase()).append(".")
        if (c.compromise) sb.append(" Closest match from what is available today.")
        return sb.toString()
    }

    internal fun tipsOf(ctx: StyleCtx, c: Combo): List<String> {
        val tips = ArrayList<String>()
        val top = c.top
        val b = c.bottom
        val s = c.shoes
        if (top != null) {
            when (top.category) {
                "Shirt" -> tips.add(
                    if (ctx.occTarget >= 3) "Tuck the shirt in and keep the placket in line with your belt buckle."
                    else "Wear the shirt untucked only if the hem ends by mid-fly; otherwise a loose front tuck.",
                )
                "Polo" -> tips.add("Polo untucked, top button open, sleeves sitting mid-bicep.")
                "T-Shirt" -> tips.add("The tee should end around mid-fly; a longer hem shortens your legs.")
                "Kurta" -> tips.add("Keep the kurta crisp and the trousers slim so the line stays long.")
                else -> {}
            }
            if (sleeveOf(top) == "long" && ctx.t >= 24.0 && c.layers.isEmpty() && top.category != "Kurta") {
                tips.add("Roll the sleeves twice to just below the elbow.")
            }
        }
        if (c.layers.isNotEmpty()) {
            val l = c.layers.last()
            tips.add(
                if (ctx.t >= 18.0) "Wear the " + shortCat(l) + " open so the top shows and you stay cool."
                else "Keep the " + shortCat(l) + " on; let the collar or hem of the top peek out.",
            )
        }
        if (b != null) {
            when {
                b.category == "Jeans" && s?.category == "Sneakers" -> tips.add("One neat cuff or a clean break at the sneaker, no stacking.")
                b.category == "Chinos" || b.category == "Trousers" -> tips.add("Hem should just touch the shoe with no bunching.")
                b.category == "Shorts" -> tips.add("Shorts ending just above the knee look sharpest.")
                else -> {}
            }
        }
        if (s != null && ctx.rainy && s.category == "Sneakers") tips.add("Wet roads today: pick the pair you do not mind wiping down.")
        if (c.accessories.any { it.category == "Watch" }) tips.add("Add your watch; skip anything else on the wrist.")
        if (ctx.uvHigh && tips.size < 4) tips.add("Strong sun: sunglasses or a cap for the walk outside.")
        if (tips.size < 2) tips.add("Steam or iron the top; crisp fabric lifts simple pieces.")
        if (tips.size < 2) tips.add("Keep grooming tidy: a trimmed beard line sharpens any outfit.")
        return tips.take(4)
    }

    internal fun weatherNoteOf(ctx: StyleCtx, c: Combo): String {
        val w = ctx.req.weather
        val deg = "°"
        val t = ctx.t.roundToInt()
        if (w == null) return "No live weather; styled for a mild day around 24$deg."
        val layer = c.layers.lastOrNull()?.let { shortCat(it) }
        val base = when {
            ctx.rainy && ctx.t >= 24.0 -> "Warm and wet (rain ${w.rainProb}%): quick-drying pieces, shoes that survive a puddle."
            ctx.rainy -> "Rain likely (${w.rainProb}%) at $t$deg: darker bottoms" + (if (layer != null) ", keep the $layer handy." else ", carry an umbrella.")
            ctx.t >= 36.0 -> "Feels like $t$deg: lightest cotton, loose fits, no layers."
            ctx.t >= 30.0 -> "Hot at $t$deg: breathable fabric and no layer."
            ctx.swing && layer != null -> "Cool start, warm afternoon (${w.tMin.roundToInt()}-${w.tMax.roundToInt()}$deg): take the $layer off by midday."
            ctx.t >= 24.0 -> "Warm $t$deg: short or rolled sleeves are enough."
            ctx.t >= 18.0 -> "Pleasant $t$deg: " + (if (layer != null) "the $layer is optional, wear it open." else "no layer needed through the day.")
            ctx.t >= 12.0 -> "Cool $t$deg: " + (if (layer != null) "keep the $layer on." else "add a layer if you have one clean.")
            else -> "Cold $t$deg: " + (if (layer != null) "layer up with the $layer and closed shoes." else "wear your warmest pieces and closed shoes.")
        }
        return if (ctx.uvHigh && !ctx.rainy) "$base UV ${w.uv.roundToInt()}: sunglasses help." else base
    }

    /** Short plain-language summary of the strongest learned likes and dislikes for the prompt. */
    internal fun tasteSummary(taste: Map<String, Double>): String {
        val usable = taste.entries.filter { !it.key.startsWith("combo:") && !it.value.isNaN() }
        val likes = usable.filter { it.value >= 0.8 }.sortedByDescending { it.value }.take(5).map { it.key }
        val dislikes = usable.filter { it.value <= -0.8 }.sortedBy { it.value }.take(5).map { it.key }
        if (likes.isEmpty() && dislikes.isEmpty()) return "no feedback yet"
        return "likes " + likes.joinToString(", ").ifBlank { "-" } + "; dislikes " + dislikes.joinToString(", ").ifBlank { "-" }
    }
}
