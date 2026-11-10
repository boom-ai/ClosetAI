package com.sanket.closetai.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Live weather with no API key (Open-Meteo).
 * Location chain: device location (if permission granted) -> geocoded profile city -> last known coordinates.
 */
class WeatherRepo(private val ctx: Context, private val prefs: SharedPreferences) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** Last weather we fetched (may be old), shown instantly on launch. */
    fun cached(): Weather? = prefs.getString("wx_json", null)?.let { s ->
        runCatching { Weather.from(JSONObject(s)) }.getOrNull()
    }

    /**
     * Returns fresh weather, or the cached one when it is younger than 45 min (unless [force]),
     * or null if nothing could be fetched and nothing is cached.
     */
    suspend fun refresh(city: String, force: Boolean): Weather? = withContext(Dispatchers.IO) {
        val old = cached()
        val cityChanged = city.isNotBlank() && !city.equals(prefs.getString("wx_city", ""), ignoreCase = true)
        if (!force && !cityChanged && old != null && old.ageMinutes < 45) return@withContext old

        var lat = Double.NaN
        var lon = Double.NaN
        var place = ""
        var source = "city"

        val loc = runCatching { deviceLocation() }.getOrNull()
        if (loc != null) {
            lat = loc.latitude; lon = loc.longitude; source = "gps"
            place = placeName(lat, lon) ?: ""
        }
        if (lat.isNaN() && city.isNotBlank()) {
            runCatching { geocode(city) }.getOrNull()?.let { g ->
                lat = g.first; lon = g.second; place = g.third; source = "city"
            }
        }
        if (lat.isNaN() && old != null) {
            lat = old.lat; lon = old.lon; place = old.place; source = old.source
        }
        if (lat.isNaN()) return@withContext old

        val fresh = runCatching { fetch(lat, lon, place.ifBlank { city }, source) }.getOrNull()
        if (fresh != null) {
            prefs.edit().putString("wx_json", fresh.toJson().toString()).putString("wx_city", city).apply()
            fresh
        } else old
    }

    private fun get(url: String): String? =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }

    private fun fetch(lat: Double, lon: Double, place: String, source: String): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", String.format(Locale.US, "%.4f", lat))
            .addQueryParameter("longitude", String.format(Locale.US, "%.4f", lon))
            .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,precipitation,weather_code,is_day")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "1")
            .build().toString()
        val body = get(url) ?: return null
        val o = JSONObject(body)
        val c = o.optJSONObject("current") ?: return null
        val d = o.optJSONObject("daily")
        fun d0(k: String): Double = d?.optJSONArray(k)?.optDouble(0, Double.NaN) ?: Double.NaN
        fun ok(v: Double, fallback: Double): Double = if (v.isNaN() || v.isInfinite()) fallback else v
        val temp = c.optDouble("temperature_2m", Double.NaN)
        if (temp.isNaN()) return null
        return Weather(
            temp = temp,
            feels = ok(c.optDouble("apparent_temperature", Double.NaN), temp),
            humidity = c.optInt("relative_humidity_2m", 0),
            wind = ok(c.optDouble("wind_speed_10m", Double.NaN), 0.0),
            precip = ok(c.optDouble("precipitation", Double.NaN), 0.0),
            code = c.optInt("weather_code", -1),
            isDay = c.optInt("is_day", 1) == 1,
            tMax = ok(d0("temperature_2m_max"), temp),
            tMin = ok(d0("temperature_2m_min"), temp),
            rainProb = ok(d0("precipitation_probability_max"), 0.0).toInt(),
            uv = ok(d0("uv_index_max"), 0.0),
            place = place,
            lat = lat, lon = lon,
            fetchedAt = System.currentTimeMillis(),
            source = source,
        )
    }

    /** City name -> (lat, lon, display name). */
    private fun geocode(city: String): Triple<Double, Double, String>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city.trim())
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build().toString()
        val body = get(url) ?: return null
        val r = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
        val lat = r.optDouble("latitude", Double.NaN)
        val lon = r.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return Triple(lat, lon, r.optString("name", city))
    }

    @Suppress("DEPRECATION")
    private fun placeName(lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) null
        else Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun deviceLocation(): Location? {
        if (!hasPermission()) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers: List<String> = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last: Location? = providers
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 2 * 60 * 60_000L) return last
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && providers.contains(LocationManager.NETWORK_PROVIDER)) {
            val fresh: Location? = withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, ctx.mainExecutor) { loc: Location? ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fresh != null) return fresh
        }
        return last
    }
}
