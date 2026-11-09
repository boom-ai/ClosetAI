package com.sanket.closetai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Stores everything locally on the phone: one JSON file + JPEGs in app storage. */
class Repo(private val ctx: Context) {
    private val dataFile = File(ctx.filesDir, "closet.json")
    private val imgDir = File(ctx.filesDir, "images").apply { mkdirs() }
    private val captureDir = File(ctx.filesDir, "captures").apply { mkdirs() }

    data class Snapshot(
        val items: List<Item>,
        val profile: Profile,
        val saved: List<Look>,
        val history: List<WearLog>,
        val tryOns: Map<String, String> = emptyMap(),
    )

    fun load(): Snapshot {
        if (!dataFile.exists()) return Snapshot(emptyList(), Profile(), emptyList(), emptyList())
        return try {
            val o = JSONObject(dataFile.readText())
            fun <T> list(key: String, f: (JSONObject) -> T): List<T> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(f) }
            }
            Snapshot(
                items = list("items") { Item.from(it) }.filter { File(it.imagePath).exists() },
                profile = Profile.from(o.optJSONObject("profile")),
                saved = list("saved") { Look.from(it) },
                history = list("history") { WearLog.from(it) },
                tryOns = o.optJSONObject("tryOns")?.let { t ->
                    t.keys().asSequence().associateWith { k -> t.optString(k) }.filterValues { File(it).exists() }
                } ?: emptyMap(),
            )
        } catch (e: Exception) {
            Snapshot(emptyList(), Profile(), emptyList(), emptyList())
        }
    }

    @Synchronized
    fun save(s: Snapshot) {
        val o = JSONObject()
            .put("items", JSONArray().apply { s.items.forEach { put(it.toJson()) } })
            .put("profile", s.profile.toJson())
            .put("saved", JSONArray().apply { s.saved.forEach { put(it.toJson()) } })
            .put("history", JSONArray().apply { s.history.forEach { put(it.toJson()) } })
            .put("tryOns", JSONObject().apply { s.tryOns.forEach { (k, v) -> put(k, v) } })
        val tmp = File(ctx.filesDir, "closet.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(o.toString()); tmp.delete()
        }
    }

    /** A fresh content:// Uri the camera app can write a photo into. */
    fun newCaptureUri(): Uri {
        val f = File(captureDir, "cap_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    }

    fun clearCaptures() {
        captureDir.listFiles()?.forEach { it.delete() }
    }

    /** Decode (with EXIF rotation), downscale, flatten onto white and save as JPEG. */
    fun importImage(uri: Uri, maxPx: Int, prefix: String): File {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h).toFloat())
            decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        val file = File(imgDir, "${prefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        decoded.recycle()
        out.recycle()
        return file
    }

    /** Saves generated try-on image bytes as a file. */
    fun saveTryOn(bytes: ByteArray): File {
        val dir = File(ctx.filesDir, "tryon").apply { mkdirs() }
        val f = File(dir, "tryon_${System.currentTimeMillis()}.jpg")
        f.writeBytes(bytes)
        return f
    }

    fun deleteFile(path: String) {
        if (path.isNotBlank()) File(path).delete()
    }
}
