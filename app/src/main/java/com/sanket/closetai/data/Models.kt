package com.sanket.closetai.data

import org.json.JSONArray
import org.json.JSONObject

val GROUPS: LinkedHashMap<String, List<String>> = linkedMapOf(
    "Tops" to listOf("T-Shirt", "Polo", "Shirt", "Kurta", "Tank Top", "Top / Blouse"),
    "Layers" to listOf("Sweater", "Hoodie", "Sweatshirt", "Jacket", "Blazer", "Overshirt", "Coat"),
    "Bottoms" to listOf("Jeans", "Trousers", "Chinos", "Joggers", "Shorts", "Track Pants", "Skirt", "Pyjama / Salwar"),
    "One-piece" to listOf("Dress", "Suit", "Ethnic Set"),
    "Footwear" to listOf("Sneakers", "Formal Shoes", "Loafers", "Boots", "Sandals / Slides", "Juttis"),
    "Accessories" to listOf("Watch", "Belt", "Cap / Hat", "Sunglasses", "Bag", "Scarf / Stole", "Jewellery", "Other"),
)
val CATEGORIES: List<String> = GROUPS.values.flatten()
fun groupOf(category: String): String =
    GROUPS.entries.firstOrNull { category in it.value }?.key ?: "Accessories"

val FORMALITY_LABELS = listOf("Lounge", "Casual", "Smart casual", "Business", "Formal")

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

data class Item(
    val id: String,
    val imagePath: String,
    val category: String,
    val name: String,
    val color: String = "",
    val colorHex: String = "",
    val secondaryColor: String = "",
    val pattern: String = "",
    val material: String = "",
    val fit: String = "",
    val formality: Int = 3,
    val seasons: List<String> = emptyList(),
    val notes: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val wornCount: Int = 0,
    val lastWorn: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("imagePath", imagePath).put("category", category).put("name", name)
        .put("color", color).put("colorHex", colorHex).put("secondaryColor", secondaryColor)
        .put("pattern", pattern).put("material", material).put("fit", fit).put("formality", formality)
        .put("seasons", JSONArray(seasons)).put("notes", notes).put("addedAt", addedAt)
        .put("wornCount", wornCount).put("lastWorn", lastWorn)

    companion object {
        fun from(o: JSONObject) = Item(
            id = o.str("id"), imagePath = o.str("imagePath"), category = o.str("category").ifBlank { "Other" },
            name = o.str("name"), color = o.str("color"), colorHex = o.str("colorHex"),
            secondaryColor = o.str("secondaryColor"), pattern = o.str("pattern"), material = o.str("material"),
            fit = o.str("fit"), formality = o.optInt("formality", 3), seasons = o.optJSONArray("seasons").strings(),
            notes = o.str("notes"), addedAt = o.optLong("addedAt"), wornCount = o.optInt("wornCount"),
            lastWorn = o.optLong("lastWorn"),
        )
    }
}

data class Swatch(val name: String, val hex: String)

data class Analysis(
    val summary: String,
    val build: String,
    val proportions: String,
    val skinTone: String,
    val undertone: String,
    val bestColors: List<Swatch>,
    val avoidColors: List<Swatch>,
    val bestFits: List<String>,
    val rules: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("summary", summary).put("build", build).put("proportions", proportions)
        .put("skinTone", skinTone).put("undertone", undertone)
        .put("bestColors", swatchArray(bestColors)).put("avoidColors", swatchArray(avoidColors))
        .put("bestFits", JSONArray(bestFits)).put("rules", JSONArray(rules))

    companion object {
        private fun swatchArray(l: List<Swatch>) = JSONArray().apply {
            l.forEach { put(JSONObject().put("name", it.name).put("hex", it.hex)) }
        }
        private fun swatches(a: JSONArray?): List<Swatch> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                when (val v = a.opt(i)) {
                    is JSONObject -> Swatch(v.str("name"), v.str("hex"))
                    is String -> Swatch(v, "")
                    else -> null
                }
            }
        }
        fun from(o: JSONObject) = Analysis(
            summary = o.str("summary"), build = o.str("build"), proportions = o.str("proportions"),
            skinTone = o.str("skinTone"), undertone = o.str("undertone"),
            bestColors = swatches(o.optJSONArray("bestColors")), avoidColors = swatches(o.optJSONArray("avoidColors")),
            bestFits = o.optJSONArray("bestFits").strings(), rules = o.optJSONArray("rules").strings(),
        )
    }
}

data class Profile(
    val photoPath: String = "",
    val dressingFor: String = "Menswear",
    val height: String = "",
    val city: String = "",
    val style: String = "Smart casual",
    val notes: String = "",
    val analysis: Analysis? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("photoPath", photoPath).put("dressingFor", dressingFor).put("height", height)
        .put("city", city).put("style", style).put("notes", notes)
        .apply { analysis?.let { put("analysis", it.toJson()) } }

    companion object {
        fun from(o: JSONObject?) = if (o == null) Profile() else Profile(
            photoPath = o.str("photoPath"), dressingFor = o.str("dressingFor").ifBlank { "Menswear" },
            height = o.str("height"), city = o.str("city"), style = o.str("style").ifBlank { "Smart casual" },
            notes = o.str("notes"), analysis = o.optJSONObject("analysis")?.let { Analysis.from(it) },
        )
    }
}

data class Look(
    val title: String,
    val itemIds: List<String>,
    val why: String,
    val tips: List<String>,
    val addOn: String,
    val score: Double = 0.0,
) {
    val key: String get() = itemIds.sorted().joinToString(",")

    fun toJson(): JSONObject = JSONObject().put("title", title).put("itemIds", JSONArray(itemIds))
        .put("why", why).put("tips", JSONArray(tips)).put("addOn", addOn).put("score", score)

    companion object {
        fun from(o: JSONObject) = Look(
            title = o.str("title").ifBlank { "Outfit" }, itemIds = o.optJSONArray("itemIds").strings(),
            why = o.str("why"), tips = o.optJSONArray("tips").strings(),
            addOn = o.str("addOn").let { if (it == "null") "" else it },
            score = o.optDouble("score", 0.0).let { if (it.isNaN()) 0.0 else it },
        )
    }
}

data class WearLog(val title: String, val itemIds: List<String>, val at: Long) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("itemIds", JSONArray(itemIds)).put("at", at)

    companion object {
        fun from(o: JSONObject) = WearLog(o.str("title"), o.optJSONArray("itemIds").strings(), o.optLong("at"))
    }
}

data class Gaps(val verdict: String, val buys: List<Pair<String, String>>, val unused: String)
