package com.sanket.closetai.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanket.closetai.ai.AiClient
import com.sanket.closetai.ai.AiSettings
import com.sanket.closetai.ai.Detected
import com.sanket.closetai.ai.Stylist
import com.sanket.closetai.data.Gaps
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Look
import com.sanket.closetai.data.Profile
import com.sanket.closetai.data.Repo
import com.sanket.closetai.data.WearLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class Tab { WARDROBE, STYLE, PROFILE }

val OCCASIONS = listOf(
    "College / casual day", "Office", "Interview", "Date", "Party / night out",
    "Wedding / festive", "Family function", "Travel", "Gym / sports", "Religious visit",
)
val WEATHER = listOf("Hot (32°C+)", "Warm", "Pleasant", "Cold", "Very cold", "Rainy")
val VIBES = listOf("Surprise me", "Clean & minimal", "Sharp", "Relaxed", "Bold", "Classic")
val STYLES = listOf("Smart casual", "Minimal / clean", "Streetwear", "Classic / formal", "Ethnic / Indo-western", "Sporty", "Mixed")
val DRESSING_FOR = listOf("Menswear", "Womenswear", "Unisex")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    val ai = AiClient(app.getSharedPreferences("settings", Context.MODE_PRIVATE))
    private val stylist = Stylist(ai)
    private val saveLock = Mutex()

    // ---- state ----
    val items = mutableStateListOf<Item>()
    var pending by mutableIntStateOf(0)
    var profile by mutableStateOf(Profile())
    val saved = mutableStateListOf<Look>()
    val history = mutableStateListOf<WearLog>()
    val looks = mutableStateListOf<Look>()

    var tab by mutableStateOf(Tab.WARDROBE)
    var filter by mutableStateOf("All")
    var occasion by mutableStateOf(OCCASIONS[0])
    var weather by mutableStateOf(WEATHER[2])
    var vibe by mutableStateOf(VIBES[0])
    var anchorId by mutableStateOf<String?>(null)
    var extra by mutableStateOf("")

    var styling by mutableStateOf(false)
    var analysing by mutableStateOf(false)
    var gapsBusy by mutableStateOf(false)
    var gaps by mutableStateOf<Gaps?>(null)

    var message by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)
    var aiSettings by mutableStateOf(ai.settings())

    /** Uri handed to the camera app; kept here so it survives recomposition. */
    var captureUri: Uri? = null
    var captureForProfile = false

    init {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { repo.load() }
            items.addAll(s.items)
            profile = s.profile
            saved.addAll(s.saved)
            history.addAll(s.history)
            if (!ai.isConfigured) message = "Tip: add your free AI key in Settings to turn on auto-detection."
        }
    }

    private fun persist() {
        val snap = Repo.Snapshot(items.toList(), profile, saved.toList(), history.toList())
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { runCatching { repo.save(snap) } }
        }
    }

    fun newCaptureUri(forProfile: Boolean): Uri {
        captureForProfile = forProfile
        return repo.newCaptureUri().also { captureUri = it }
    }

    fun saveSettings(s: AiSettings) {
        ai.save(s)
        aiSettings = ai.settings()
        showSettings = false
        message = "Settings saved"
    }

    // ---- wardrobe ----
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        tab = Tab.WARDROBE
        filter = "All"
        viewModelScope.launch {
            pending += uris.size
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { repo.importImage(it, 1024, "item") }.getOrNull() }
                    .also { repo.clearCaptures() }
            }
            pending -= uris.size - files.size
            if (files.size < uris.size) message = "Couldn't read ${uris.size - files.size} photo(s)"
            var added = 0
            for (chunk in files.chunked(6)) {
                val detected: List<Detected?> = if (ai.isConfigured) {
                    try {
                        val bytes = withContext(Dispatchers.IO) { chunk.map { it.readBytes() } }
                        stylist.detect(bytes)
                    } catch (e: Exception) {
                        message = e.message ?: "Detection failed"
                        emptyList()
                    }
                } else emptyList()
                chunk.forEachIndexed { i, f ->
                    val d = detected.getOrNull(i)
                    items.add(
                        Item(
                            id = UUID.randomUUID().toString().take(8),
                            imagePath = f.absolutePath,
                            category = d?.category ?: "Other",
                            name = d?.name ?: "New item",
                            color = d?.color ?: "",
                            colorHex = d?.colorHex ?: "",
                            secondaryColor = d?.secondaryColor ?: "",
                            pattern = d?.pattern ?: "",
                            material = d?.material ?: "",
                            fit = d?.fit ?: "",
                            formality = d?.formality ?: 3,
                            seasons = d?.seasons ?: emptyList(),
                            notes = d?.notes ?: "",
                        ),
                    )
                    if (d != null) added++
                }
                pending = (pending - chunk.size).coerceAtLeast(0)
                persist()
            }
            if (!ai.isConfigured) message = "Saved. Add an AI key in Settings to auto-detect categories."
            else if (added > 0) message = "Added $added item${if (added > 1) "s" else ""}"
        }
    }

    fun updateItem(item: Item) {
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) { items[i] = item; persist() }
    }

    fun deleteItem(id: String) {
        val target = items.firstOrNull { x -> x.id == id } ?: return
        items.remove(target)
        repo.deleteFile(target.imagePath)
        if (anchorId == id) anchorId = null
        persist()
    }

    fun styleAround(id: String) {
        anchorId = id
        tab = Tab.STYLE
    }

    // ---- profile ----
    fun setProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching { repo.importImage(uri, 1280, "profile") }.getOrNull().also { repo.clearCaptures() }
            }
            if (f == null) { message = "Couldn't read that photo"; return@launch }
            repo.deleteFile(profile.photoPath)
            profile = profile.copy(photoPath = f.absolutePath)
            persist()
            message = "Photo added — now tap Analyse my look"
        }
    }

    fun updateProfile(p: Profile) { profile = p; persist() }

    fun analyse() {
        val p = profile
        if (p.photoPath.isBlank() || !File(p.photoPath).exists()) { message = "Add a full-length photo first"; return }
        if (!ai.isConfigured) { showSettings = true; return }
        analysing = true
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { File(p.photoPath).readBytes() }
                val a = stylist.analyse(bytes, p)
                profile = profile.copy(analysis = a)
                persist()
                message = "Style profile ready"
            } catch (e: Exception) {
                message = e.message ?: "Analysis failed"
            } finally {
                analysing = false
            }
        }
    }

    // ---- styling ----
    fun styleMe() {
        if (items.size < 2) { message = "Add a few clothes first"; tab = Tab.WARDROBE; return }
        if (!ai.isConfigured) { showSettings = true; return }
        styling = true
        looks.clear()
        viewModelScope.launch {
            try {
                val res = stylist.outfits(
                    items.toList(), profile, occasion, weather, vibe,
                    items.firstOrNull { it.id == anchorId }, extra,
                )
                if (res.isEmpty()) message = "Couldn't build a full look — try another occasion or add more pieces."
                looks.addAll(res)
            } catch (e: Exception) {
                message = e.message ?: "Styling failed"
            } finally {
                styling = false
            }
        }
    }

    fun toggleSave(look: Look) {
        val existing = saved.firstOrNull { it.key == look.key }
        if (existing != null) saved.remove(existing) else saved.add(0, look)
        while (saved.size > 40) saved.removeAt(saved.lastIndex)
        persist()
    }

    fun isSaved(look: Look) = saved.any { it.key == look.key }

    fun wear(look: Look) {
        val now = System.currentTimeMillis()
        history.add(0, WearLog(look.title, look.itemIds, now))
        while (history.size > 30) history.removeAt(history.lastIndex)
        look.itemIds.forEach { id ->
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) items[i] = items[i].copy(wornCount = items[i].wornCount + 1, lastWorn = now)
        }
        persist()
        message = "Logged — enjoy the fit ✨"
    }

    fun findGaps() {
        if (items.size < 3) { message = "Add a few more clothes first"; return }
        if (!ai.isConfigured) { showSettings = true; return }
        gapsBusy = true
        viewModelScope.launch {
            try {
                gaps = stylist.gaps(items.toList(), profile)
            } catch (e: Exception) {
                message = e.message ?: "Analysis failed"
            } finally {
                gapsBusy = false
            }
        }
    }
}
