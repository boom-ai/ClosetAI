package com.sanket.closetai.ai

import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class Provider(val label: String, val defaultModel: String, val keyUrl: String) {
    GEMINI("Google Gemini", "gemini-3.8-flash", "https://aistudio.google.com/apikey"),
    CLAUDE("Anthropic Claude", "claude-haiku-5-5", "https://console.anthropic.com/settings/keys"),
}

data class AiSettings(val provider: Provider, val apiKey: String, val model: String)

class AiException(message: String) : IOException(message)

/** Talks to Gemini or Claude directly from the phone with the user's own API key. */
class AiClient(private val prefs: SharedPreferences) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

    fun settings(): AiSettings {
        val p = runCatching { Provider.valueOf(prefs.getString("provider", Provider.GEMINI.name)!!) }
            .getOrDefault(Provider.GEMINI)
        return AiSettings(
            provider = p,
            apiKey = prefs.getString("apiKey", "") ?: "",
            model = (prefs.getString("model", "") ?: "").ifBlank { p.defaultModel },
        )
    }

    fun save(s: AiSettings) {
        prefs.edit()
            .putString("provider", s.provider.name)
            .putString("apiKey", s.apiKey.trim())
            .putString("model", s.model.trim())
            .apply()
    }

    val isConfigured: Boolean get() = settings().apiKey.isNotBlank()

    suspend fun ask(prompt: String, images: List<ByteArray> = emptyList()): String = withContext(Dispatchers.IO) {
        val s = settings()
        if (s.apiKey.isBlank()) throw AiException("Add your AI key in Settings first.")
        when (s.provider) {
            Provider.GEMINI -> gemini(s, prompt, images)
            Provider.CLAUDE -> claude(s, prompt, images)
        }
    }

    /** Ask and parse the reply as one JSON value (JSONObject or JSONArray). */
    suspend fun askJson(prompt: String, images: List<ByteArray> = emptyList()): Any {
        val text = ask(prompt, images)
        return extractJson(text) ?: throw AiException("The AI reply wasn't readable — please try again.")
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun gemini(s: AiSettings, prompt: String, images: List<ByteArray>): String {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        images.forEach {
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64(it))))
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${s.model}:generateContent")
            .header("x-goog-api-key", s.apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val o = execute(req)
        val cand = o.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException(o.optJSONObject("promptFeedback")?.optString("blockReason")
                ?.let { "Gemini blocked the request ($it)." } ?: "Gemini returned no answer.")
        val outParts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until outParts.length()) {
            val p = outParts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text", ""))
        }
        return sb.toString().ifBlank { throw AiException("Gemini returned an empty answer.") }
    }

    private fun claude(s: AiSettings, prompt: String, images: List<ByteArray>): String {
        val content = JSONArray()
        images.forEach {
            content.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64(it)),
                ),
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", s.model)
            .put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", s.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val o = execute(req)
        val arr = o.optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            if (c.optString("type") == "text") sb.append(c.optString("text"))
        }
        return sb.toString().ifBlank { throw AiException("Claude returned an empty answer.") }
    }

    /** Sends the request, retrying up to 4 times when the AI is overloaded (429/500/502/503/504/529). */
    private fun execute(req: Request): JSONObject {
        val waits = longArrayOf(3_000, 8_000, 15_000, 25_000)
        var attempt = 0
        while (true) {
            val (code, text) = http.newCall(req).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
            if (code in 200..299) return JSONObject(text)
            val retryable = code == 429 || code == 500 || code == 502 || code == 503 || code == 504 || code == 529
            if (retryable && attempt < waits.size) {
                Thread.sleep(waits[attempt])
                attempt++
                continue
            }
            val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
            throw AiException(
                when (code) {
                    400 -> "AI request rejected: ${msg ?: "check the model name in Settings"}"
                    401, 403 -> "Your API key was rejected — check it in Settings."
                    404 -> "Model not found — check the model name in Settings."
                    429 -> "AI rate limit hit — wait a minute and try again."
                    500, 502, 503, 504, 529 -> "The AI servers are busy right now. Wait a minute and try again, or switch model/provider in Settings."
                    else -> "AI error $code: ${msg ?: "try again"}"
                },
            )
        }
    }

    companion object {
        /** Tolerant JSON extraction: whole text, a ``` fence, or first {/[ to last }/]. */
        fun extractJson(raw: String): Any? {
            var t = raw.trim()
            val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(t)
            if (fence != null) t = fence.groupValues[1].trim()
            parse(t)?.let { return it }
            val start = t.indexOfFirst { it == '{' || it == '[' }
            val end = t.indexOfLast { it == '}' || it == ']' }
            if (start >= 0 && end > start) return parse(t.substring(start, end + 1))
            return null
        }

        private fun parse(t: String): Any? = try {
            val v = JSONTokener(t).nextValue()
            if (v is JSONObject || v is JSONArray) v else null
        } catch (e: Exception) {
            null
        }
    }
}
