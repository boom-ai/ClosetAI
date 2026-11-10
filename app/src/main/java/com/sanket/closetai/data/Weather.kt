package com.sanket.closetai.data

import org.json.JSONObject
import kotlin.math.roundToInt

/** Live weather for the user's location (Open-Meteo). All temperatures in Celsius, wind in km/h. */
data class Weather(
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val wind: Double,
    val precip: Double,
    val code: Int,
    val isDay: Boolean,
    val tMax: Double,
    val tMin: Double,
    /** Max chance of rain today, 0-100 */
    val rainProb: Int,
    val uv: Double,
    val place: String,
    val lat: Double,
    val lon: Double,
    val fetchedAt: Long,
    /** "gps" (device location) or "city" (profile city) */
    val source: String,
) {
    val condition: String get() = wmo(code, isDay).first
    val emoji: String get() = wmo(code, isDay).second
    val isRainy: Boolean get() = code in 51..67 || code in 80..82 || code in 95..99 || rainProb >= 60
    val ageMinutes: Long get() = (System.currentTimeMillis() - fetchedAt) / 60_000L

    /** Coarse label used in prompts and UI. */
    fun bucket(): String = when {
        isRainy -> "Rainy"
        feels >= 32 -> "Hot"
        feels >= 26 -> "Warm"
        feels >= 18 -> "Pleasant"
        feels >= 10 -> "Cold"
        else -> "Very cold"
    }

    /** One friendly sentence about what the day means for dressing. */
    fun headline(): String {
        val swing = tMax - tMin
        return when {
            isRainy && feels >= 24 -> "Warm and wet \u2014 quick-dry fabrics, shoes that can take a puddle."
            isRainy -> "Rain around \u2014 darker bottoms and a layer that handles a shower."
            feels >= 36 -> "Seriously hot \u2014 the lightest, loosest cotton or linen you own."
            feels >= 30 -> "Hot day \u2014 light colours, breathable fabrics, no layers."
            swing >= 12 && tMin < 16 -> "Cool start, warm afternoon \u2014 wear a layer you can take off."
            feels >= 24 -> "Warm and easy \u2014 short sleeves or a light shirt."
            feels >= 18 -> "Pleasant \u2014 a light layer is optional."
            feels >= 12 -> "Cool \u2014 add a mid layer over your top."
            feels >= 6 -> "Cold \u2014 a proper sweater or jacket, closed shoes."
            else -> "Very cold \u2014 layer up: base, knit and outerwear."
        }
    }

    /** Compact, complete description for the AI prompt. */
    fun promptLine(): String =
        "${temp.roundToInt()}\u00B0C now, feels like ${feels.roundToInt()}\u00B0C, today ${tMin.roundToInt()}-${tMax.roundToInt()}\u00B0C, " +
            "$condition, rain chance $rainProb%, humidity $humidity%, wind ${wind.roundToInt()} km/h, UV ${uv.roundToInt()}" +
            (if (place.isNotBlank()) ", in $place" else "")

    fun toJson(): JSONObject = JSONObject()
        .put("temp", temp).put("feels", feels).put("humidity", humidity).put("wind", wind).put("precip", precip)
        .put("code", code).put("isDay", isDay).put("tMax", tMax).put("tMin", tMin).put("rainProb", rainProb)
        .put("uv", uv).put("place", place).put("lat", lat).put("lon", lon).put("fetchedAt", fetchedAt)
        .put("source", source)

    companion object {
        fun from(o: JSONObject): Weather? = runCatching {
            Weather(
                temp = o.getDouble("temp"), feels = o.getDouble("feels"), humidity = o.optInt("humidity"),
                wind = o.optDouble("wind", 0.0), precip = o.optDouble("precip", 0.0), code = o.optInt("code", -1),
                isDay = o.optBoolean("isDay", true), tMax = o.getDouble("tMax"), tMin = o.getDouble("tMin"),
                rainProb = o.optInt("rainProb"), uv = o.optDouble("uv", 0.0), place = o.optString("place", ""),
                lat = o.optDouble("lat", 0.0), lon = o.optDouble("lon", 0.0), fetchedAt = o.optLong("fetchedAt"),
                source = o.optString("source", "city"),
            )
        }.getOrNull()

        fun wmo(code: Int, day: Boolean): Pair<String, String> = when (code) {
            0 -> "Clear" to if (day) "\u2600\uFE0F" else "\uD83C\uDF19"
            1 -> "Mainly clear" to if (day) "\uD83C\uDF24\uFE0F" else "\uD83C\uDF19"
            2 -> "Partly cloudy" to "\u26C5"
            3 -> "Overcast" to "\u2601\uFE0F"
            45, 48 -> "Fog" to "\uD83C\uDF2B\uFE0F"
            51, 53, 55 -> "Drizzle" to "\uD83C\uDF26\uFE0F"
            56, 57, 66, 67 -> "Freezing rain" to "\uD83C\uDF27\uFE0F"
            61, 63, 65 -> "Rain" to "\uD83C\uDF27\uFE0F"
            71, 73, 75, 77 -> "Snow" to "\u2744\uFE0F"
            80, 81, 82 -> "Showers" to "\uD83C\uDF26\uFE0F"
            85, 86 -> "Snow showers" to "\uD83C\uDF28\uFE0F"
            95, 96, 97, 99 -> "Thunderstorm" to "\u26C8\uFE0F"
            else -> "Weather" to "\uD83C\uDF21\uFE0F"
        }
    }
}
