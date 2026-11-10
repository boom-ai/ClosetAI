package com.sanket.closetai.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanket.closetai.ai.AiClient
import com.sanket.closetai.ai.AiSettings
import com.sanket.closetai.ai.Detected
import com.sanket.closetai.ai.Scorer
import com.sanket.closetai.ai.StyleRequest
import com.sanket.closetai.ai.Stylist
import com.sanket.closetai.data.Gaps
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Look
import com.sanket.closetai.data.OCCASIONS
import com.sanket.closetai.data.Profile
import com.sanket.closetai.data.Repo
import com.sanket.closetai.data.StyledSet
import com.sanket.closetai.data.VIBES
import com.sanket.closetai.data.WearLog
import com.sanket.closetai.data.Weather
import com.sanket.closetai.data.WeatherRepo
import com.sanket.closetai.data.groupOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

enum class Tab { TODAY, CLOSET, LOOKS, ME }

/** The four stages shown while styling (index + 1 == AppViewModel.styleStep). */
val STYLE_STAGES = listOf("Reading the weather", "Shortlisting your pieces", "Composing looks", "Final check")

/** Reasons offered after a thumbs-down; they teach the stylist what to change. */
val DISLIKE_REASONS = listOf("Colours", "Too formal", "Too casual", "Too warm", "Too cold", "Just not me")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    private val prefs = app.getSharedPreferences("state", Context.MODE_PRIVATE)
    val ai = AiClient(app.getSharedPreferences("settings", Context.MODE_PRIVATE))
    private val stylist = Stylist(ai)
    private val weatherRepo = WeatherRepo(app, prefs)
    private val saveLock = Mutex()

    // ------------------------------------------------------------------ state
    /** False until the saved wardrobe has been read from disk. */
    var ready by mutableStateOf(false)
    /** First-run onboarding finished (or skipped). */
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))

    val items = mutableStateListOf<Item>()
    var profile by mutableStateOf(Profile())
    val saved = mutableStateListOf<Look>()
    val history = mutableStateListOf<WearLog>()
    /** occasion -> cached looks styled today */
    val today = mutableStateMapOf<String, StyledSet>()
    /** look.key -> generated try-on image path */
    val tryOns = mutableStateMapOf<String, String>()
    /** look keys whose try-on is generating */
    val tryOnBusy = mutableStateListOf<String>()
    /** look.key -> +1 / -1 (this session) */
    val ratings = mutableStateMapOf<String, Int>()

    var weather by mutableStateOf<Weather?>(null)
    var weatherBusy by mutableStateOf(false)

    var tab by mutableStateOf(Tab.TODAY)
    var occasion by mutableStateOf(prefs.getString("occasion", OCCASIONS[0]) ?: OCCASIONS[0])
    var vibe by mutableStateOf(VIBES[0])
    var extra by mutableStateOf("")
    var anchorId by mutableStateOf<String?>(null)
    /** Which look of the current set is shown as the hero (0..2). */
    var heroIndex by mutableIntStateOf(0)

    var styling by mutableStateOf(false)
    /** 0 = idle, 1..4 = STYLE_STAGES index + 1 */
    var styleStep by mutableIntStateOf(0)
    /** Extra progress note, e.g. "AI busy, retrying (1/3)..." */
    var styleNote by mutableStateOf("")
    var styleError by mutableStateOf<String?>(null)

    /** Bulk add / re-detect progress: detectDone of detectTotal (0 total = idle). */
    var detectTotal by mutableIntStateOf(0)
    var detectDone by mutableIntStateOf(0)

    var analysing by mutableStateOf(false)
    var gapsBusy by mutableStateOf(false)
    var gaps by mutableStateOf<Gaps?>(null)

    /** One-shot snackbar text. */
    var message by mutableStateOf<String?>(null)
    var aiSettings by mutableStateOf(ai.settings())
    /** True right after "Wear this": an Undo is available. */
    var canUndoWear by mutableStateOf(false)
    /** Bumps whenever taste changes (so the Me screen recomposes). */
    var tasteVersion by mutableIntStateOf(0)

    private var taste: Map<String, Double> = emptyMap()
    private var oldLiked: List<String> = emptyList()
    private var oldDisliked: List<String> = emptyList()
    private var loaded = false
    private var saveSeq = 0L
    private var savedSeq = 0L
    private var undoWear: (() -> Unit)? = null
    private var autoStyledDay: String = prefs.getString("autoStyledDay", "") ?: ""

    var tempBias: Double
        get() = prefs.getFloat("tempBias", 0f).toDouble()
        private set(v) { prefs.edit().putFloat("tempBias", v.coerceIn(-4.0, 4.0).toFloat()).apply() }

    /** Uri handed to the camera app; kept in prefs so it survives the app being killed while the camera is open. */
    var captureUri: Uri?
        get() = prefs.getString("captureUri", null)?.let { Uri.parse(it) }
        set(v) { prefs.edit().putString("captureUri", v?.toString()).apply() }
    var captureForProfile: Boolean
        get() = prefs.getBoolean("captureForProfile", false)
        set(v) { prefs.edit().putBoolean("captureForProfile", v).apply() }

    init {
        ai.onRetry = { msg -> styleNote = msg }
        weather = weatherRepo.cached()
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { repo.load() }
            if (s == null) {
                message = "Couldn't read your saved wardrobe. A backup copy was kept and saving is paused to protect it."
                ready = true
                return@launch
            }
            items.addAll(s.items)
            profile = s.profile
            saved.addAll(s.saved)
            history.addAll(s.history)
            tryOns.putAll(s.tryOns)
            s.today.filterValues { isToday(it.styledAt) }.forEach { (k, v) -> today[k] = v }
            taste = s.taste
            gaps = s.gaps
            oldLiked = s.liked
            oldDisliked = s.disliked
            loaded = true
            if (items.isNotEmpty() && !onboarded) finishOnboarding()
            ready = true
            refreshWeather(force = false)
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun dayKey(t: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    fun isToday(t: Long): Boolean = t > 0 && dayKey(t) == dayKey()

    fun item(id: String): Item? = items.firstOrNull { it.id == id }

    fun piecesOf(look: Look): List<Item> = look.itemIds.mapNotNull { item(it) }

    /** Items the stylist may use right now. */
    fun available(): List<Item> = items.filter { !it.inLaundry }

    /** Which basics are missing before an outfit can be built: any of "Top", "Bottom", "Shoes". */
    fun missingBasics(): List<String> {
        val groups = available().map { it.group }.toSet()
        val out = mutableListOf<String>()
        val onePiece = "One-piece" in groups
        if (!onePiece && "Tops" !in groups && "Layers" !in groups) out.add("Top")
        if (!onePiece && "Bottoms" !in groups) out.add("Bottom")
        if ("Footwear" !in groups) out.add("Shoes")
        return out
    }

    /** Enough to style (shoes are nice-to-have, not required). */
    fun canStyle(): Boolean = missingBasics().none { it == "Top" || it == "Bottom" }

    /** Today's cached looks for the selected occasion, if any. */
    val currentSet: StyledSet?
        get() = today[occasion]?.takeIf { isToday(it.styledAt) && it.looks.isNotEmpty() }

    /** True when the weather has moved enough since styling that a restyle is worth offering. */
    fun isStale(set: StyledSet): Boolean {
        val w = weather ?: return false
        if (set.temp > 900) return false
        return abs(w.feels - set.temp) >= 6.0 || w.isRainy != set.rainy
    }

    fun failedCount(): Int = items.count { it.detectFailed }
    fun laundryCount(): Int = items.count { it.inLaundry }
    fun hasLocationPermission(): Boolean = weatherRepo.hasPermission()
    fun tasteSummary(): Pair<List<String>, List<String>> {
        val real = taste.filterKeys { !it.startsWith("combo:") }
        val likes = real.filterValues { it >= 0.8 }.entries.sortedByDescending { it.value }.take(6).map { prettyFeature(it.key) }
        val dislikes = real.filterValues { it <= -0.8 }.entries.sortedBy { it.value }.take(6).map { prettyFeature(it.key) }
        return likes to dislikes
    }

    private fun prettyFeature(k: String): String {
        val v = k.substringAfter(":")
        return when (k.substringBefore(":")) {
            "pair" -> v.replace(">", " over ")
            "depth" -> v.replace(">", " over ") + " tones"
            "dir" -> "$v looks"
            "form" -> "formality $v"
            "tag" -> "$v style"
            "layer" -> if (v == "none") "no layer" else "layered"
            else -> v
        }
    }

    private fun persist() {
        if (!loaded) return
        val snap = Repo.Snapshot(
            items = items.toList(), profile = profile, saved = saved.toList(), history = history.toList(),
            tryOns = tryOns.toMap(), liked = oldLiked, disliked = oldDisliked,
            today = today.toMap(), taste = taste, gaps = gaps,
        )
        val seq = ++saveSeq
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock {
                if (seq > savedSeq) { runCatching { repo.save(snap) }; savedSeq = seq }
            }
        }
    }

    // ------------------------------------------------------------------ settings / onboarding
    fun saveSettings(s: AiSettings) {
        ai.save(s)
        aiSettings = ai.settings()
        message = "Settings saved"
    }

    fun finishOnboarding() {
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun newCaptureUri(forProfile: Boolean): Uri {
        captureForProfile = forProfile
        return repo.newCaptureUri().also { captureUri = it }
    }

    // ------------------------------------------------------------------ weather
    /** Fetches live weather (device location if allowed, else the profile city). Free: never restyles by itself. */
    fun refreshWeather(force: Boolean) {
        if (weatherBusy) return
        weatherBusy = true
        viewModelScope.launch {
            val w = runCatching { weatherRepo.refresh(profile.city, force) }.getOrNull()
            if (w != null) weather = w
            else if (force) message =
                if (profile.city.isBlank() && !weatherRepo.hasPermission()) "Allow location or add your city in Me to get live weather"
                else "Couldn't reach the weather service — check your connection"
            weatherBusy = false
            autoStyleIfNeeded()
        }
    }

    /** Styles once per day automatically, only when nothing is cached for today's occasion. */
    private fun autoStyleIfNeeded() {
        if (!loaded || !onboarded || styling) return
        if (autoStyledDay == dayKey() || currentSet != null || !canStyle()) return
        autoStyledDay = dayKey()
        prefs.edit().putString("autoStyledDay", autoStyledDay).apply()
        styleMe()
    }

    // ------------------------------------------------------------------ styling
    fun selectOccasion(o: String) {
        if (styling) return
        occasion = o
        heroIndex = 0
        styleError = null
        prefs.edit().putString("occasion", o).apply()
    }

    /** Runs the stylist for the selected occasion and caches the result. Existing looks stay until it succeeds. */
    fun styleMe() {
        if (styling) return
        if (!canStyle()) { styleError = "Add at least a top and a bottom to your closet first."; return }
        styling = true
        styleError = null
        styleStep = 1
        styleNote = ""
        val occ = occasion
        viewModelScope.launch {
            try {
                val req = StyleRequest(
                    items = items.toList(), profile = profile, occasion = occ, vibe = vibe, extra = extra.trim(),
                    anchor = anchorId?.let { item(it) }, weather = weather, taste = taste, tempBias = tempBias,
                    history = history.toList(), hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    useAi = ai.isConfigured,
                )
                val res = stylist.outfits(
                    req,
                    readImage = { path -> runCatching { File(path).readBytes() }.getOrNull() },
                    onStage = { st -> styleStep = (STYLE_STAGES.indexOf(st) + 1).coerceAtLeast(styleStep); styleNote = "" },
                )
                if (res.looks.isEmpty()) {
                    styleError = res.note.ifBlank { "Couldn't build a complete outfit from the clean clothes in your closet. Add more pieces or bring some back from laundry." }
                } else {
                    val w = weather
                    today[occ] = StyledSet(
                        occasion = occ, looks = res.looks.take(3), styledAt = System.currentTimeMillis(),
                        temp = w?.feels ?: 999.0, rainy = w?.isRainy ?: false, offline = res.offline,
                    )
                    if (occasion == occ) heroIndex = 0
                    persist()
                    if (res.offline && ai.isConfigured && res.note.isNotBlank())
                        message = "AI was unavailable, so this was styled on-device. ${res.note}"
                }
            } catch (e: Exception) {
                styleError = e.message ?: "Styling failed"
            } finally {
                styling = false
                styleStep = 0
                styleNote = ""
            }
        }
    }

    fun promote(index: Int) {
        val n = currentSet?.looks?.size ?: 0
        if (index in 0 until n) heroIndex = index
    }

    fun styleAround(id: String) {
        anchorId = id
        tab = Tab.TODAY
        styleMe()
    }

    fun clearAnchor() { anchorId = null }

    /** Closet pieces that could replace [itemId] in a look: same group, clean, closest formality first. */
    fun swapOptions(look: Look, itemId: String): List<Item> {
        val old = item(itemId) ?: return emptyList()
        return available()
            .filter { it.group == old.group && it.id !in look.itemIds && !it.detectFailed }
            .sortedBy { abs(it.formality - old.formality) }
    }

    /** Replaces one piece of a look locally (no AI call) and marks it edited. */
    fun swap(look: Look, oldId: String, newId: String) {
        val newLook = look.copy(itemIds = look.itemIds.map { if (it == oldId) newId else it }, edited = true, score = 0.0)
        var changed = false
        today.toMap().forEach { (occ, set) ->
            val i = set.looks.indexOfFirst { it.key == look.key }
            if (i >= 0) {
                today[occ] = set.copy(looks = set.looks.toMutableList().also { it[i] = newLook })
                changed = true
            }
        }
        val si = saved.indexOfFirst { it.key == look.key }
        if (si >= 0) { saved[si] = newLook; changed = true }
        if (changed) persist()
    }

    // ------------------------------------------------------------------ wear / save / rate
    fun isWornToday(look: Look): Boolean =
        history.any { isToday(it.at) && it.itemIds.sorted() == look.itemIds.sorted() }

    fun wear(look: Look) {
        if (isWornToday(look)) return
        val now = System.currentTimeMillis()
        val before = look.itemIds.mapNotNull { item(it) }
        val log = WearLog(look.title, look.itemIds, now)
        history.add(0, log)
        while (history.size > 60) history.removeAt(history.lastIndex)
        before.forEach { old ->
            val i = items.indexOfFirst { it.id == old.id }
            if (i >= 0) items[i] = items[i].copy(wornCount = items[i].wornCount + 1, lastWorn = now)
        }
        val tasteBefore = taste
        taste = Scorer.updateTaste(taste, Scorer.tasteFeatures(before, look.direction), 0.5)
        undoWear = {
            history.remove(log)
            before.forEach { old ->
                val i = items.indexOfFirst { it.id == old.id }
                if (i >= 0) items[i] = items[i].copy(wornCount = old.wornCount, lastWorn = old.lastWorn)
            }
            taste = tasteBefore
        }
        canUndoWear = true
        tasteVersion++
        persist()
    }

    fun undoLastWear() {
        undoWear?.invoke()
        undoWear = null
        canUndoWear = false
        persist()
    }

    fun isSaved(look: Look): Boolean = saved.any { it.key == look.key }

    fun toggleSave(look: Look) {
        val existing = saved.firstOrNull { it.key == look.key }
        if (existing != null) {
            saved.remove(existing)
        } else {
            saved.add(0, look)
            taste = Scorer.updateTaste(taste, Scorer.tasteFeatures(piecesOf(look), look.direction), 0.5)
            tasteVersion++
        }
        while (saved.size > 60) saved.removeAt(saved.lastIndex)
        persist()
    }

    /** Thumbs up / down. [reason] is one of DISLIKE_REASONS (or blank) and sharpens what the stylist learns. */
    fun rate(look: Look, up: Boolean, reason: String = "") {
        val pieces = piecesOf(look)
        val current = ratings[look.key]
        if (up && current == 1) return
        ratings[look.key] = if (up) 1 else -1
        var feats = Scorer.tasteFeatures(pieces, look.direction)
        if (!up) {
            when (reason) {
                "Colours" -> feats = feats.filter { it.startsWith("pair:") || it.startsWith("depth:") }
                "Too formal", "Too casual" -> feats = feats.filter { it.startsWith("form:") || it.startsWith("dir:") }
                "Too warm" -> { tempBias = tempBias + 1.0; feats = feats.filter { it.startsWith("layer:") } }
                "Too cold" -> { tempBias = tempBias - 1.0; feats = feats.filter { it.startsWith("layer:") } }
            }
        }
        taste = Scorer.updateTaste(taste, feats, if (up) 1.0 else -1.0)
        if (!up) {
            val coreIds = pieces.filter { it.group != "Accessories" }.map { it.id }
            taste = Scorer.updateTaste(taste, listOf(Scorer.comboKey(coreIds)), -3.5)
        }
        tasteVersion++
        persist()
        message = if (up) "Noted — more like this" else "Noted — you won't see this combination again"
    }

    fun resetTaste() {
        taste = emptyMap()
        tempBias = 0.0
        ratings.clear()
        tasteVersion++
        persist()
        message = "Taste memory cleared"
    }

    // ------------------------------------------------------------------ try-on
    fun tryOn(look: Look) {
        val key = look.key
        if (key in tryOnBusy) return
        val photo = profile.photoPath
        if (photo.isBlank() || !File(photo).exists()) {
            message = "Add your full-length photo in Me first"; tab = Tab.ME; return
        }
        if (ai.settings().tryOnKey.isBlank()) { message = "Try-on needs a Gemini key — add it in Settings"; return }
        val order = listOf("Tops", "One-piece", "Layers", "Bottoms", "Footwear")
        val pieces = piecesOf(look).filter { it.group != "Accessories" }.sortedBy { order.indexOf(it.group) }.take(5)
        if (pieces.isEmpty()) return
        tryOnBusy.add(key)
        viewModelScope.launch {
            try {
                val (person, withBytes) = withContext(Dispatchers.IO) {
                    File(photo).readBytes() to pieces.map { it to File(it.imagePath).readBytes() }
                }
                val img = stylist.tryOn(person, profile, look, withBytes)
                val f = withContext(Dispatchers.IO) { repo.saveTryOn(img) }
                val old = tryOns[key]
                tryOns[key] = f.absolutePath
                if (old != null) withContext(Dispatchers.IO) { repo.deleteFile(old) }
                persist()
            } catch (e: Exception) {
                message = e.message ?: "Try-on failed"
            } finally {
                tryOnBusy.remove(key)
            }
        }
    }

    /** content:// Uri of the look's try-on image for sharing, or null when there is none. */
    fun shareUri(look: Look): Uri? =
        tryOns[look.key]?.takeIf { File(it).exists() }?.let { runCatching { repo.shareUri(it) }.getOrNull() }

    // ------------------------------------------------------------------ closet
    /** Imports photos, saves them immediately as "needs details" items, then detects them in batches. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { repo.importImage(it, 1024, "item") }.getOrNull() }
                    .also { repo.clearCaptures() }
            }
            if (files.size < uris.size) message = "Couldn't read ${uris.size - files.size} photo(s)"
            if (files.isEmpty()) return@launch
            val fresh = files.map { f ->
                Item(
                    id = UUID.randomUUID().toString().take(8), imagePath = f.absolutePath,
                    category = "Other", name = "New item", detectFailed = true,
                )
            }
            items.addAll(fresh)
            persist()
            if (ai.isConfigured) runDetection(fresh.map { it.id }, keepNames = false)
            else message = "Saved. Add an AI key in Settings to detect them automatically."
        }
    }

    /** Re-runs AI detection on the given items (defaults to every item that still needs details). */
    fun redetect(ids: List<String> = items.filter { it.detectFailed }.map { it.id }) {
        if (ids.isEmpty() || detectTotal > 0) return
        if (!ai.isConfigured) { message = "Add an AI key in Settings first"; return }
        viewModelScope.launch { runDetection(ids, keepNames = false) }
    }

    private suspend fun runDetection(ids: List<String>, keepNames: Boolean) {
        detectTotal += ids.size
        var ok = 0
        var lastError: String? = null
        for (chunk in ids.chunked(5)) {
            val targets = chunk.mapNotNull { item(it) }
            val detected: List<Detected?> = try {
                val bytes = withContext(Dispatchers.IO) { targets.map { File(it.imagePath).readBytes() } }
                stylist.detect(bytes)
            } catch (e: Exception) {
                lastError = e.message
                emptyList()
            }
            targets.forEachIndexed { i, t ->
                val d = detected.getOrNull(i) ?: return@forEachIndexed
                val idx = items.indexOfFirst { it.id == t.id }
                if (idx >= 0) { items[idx] = d.applyTo(items[idx], keepNames); ok++ }
            }
            detectDone += chunk.size
            persist()
        }
        if (detectDone >= detectTotal) { detectTotal = 0; detectDone = 0 }
        message = when {
            ok == ids.size -> "Added $ok item${if (ok == 1) "" else "s"} to your closet"
            ok > 0 -> "$ok detected, ${ids.size - ok} need details — tap Retry in Closet"
            else -> lastError ?: "Detection failed — tap Retry in Closet"
        }
    }

    fun updateItem(updated: Item) {
        val i = items.indexOfFirst { it.id == updated.id }
        if (i >= 0) { items[i] = updated; persist() }
    }

    fun setLaundry(id: String, inLaundry: Boolean) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(inLaundry = inLaundry); persist() }
    }

    fun allClean() {
        for (i in items.indices) if (items[i].inLaundry) items[i] = items[i].copy(inLaundry = false)
        persist()
        message = "Everything is back in the closet"
    }

    fun deleteItem(id: String) {
        val target = item(id) ?: return
        items.remove(target)
        if (anchorId == id) anchorId = null
        for (i in saved.indices.reversed()) {
            val l = saved[i]
            if (id in l.itemIds) {
                val rest = l.itemIds - id
                if (rest.size < 2) saved.removeAt(i) else saved[i] = l.copy(itemIds = rest)
            }
        }
        for (i in history.indices.reversed()) {
            val h = history[i]
            if (id in h.itemIds) history[i] = h.copy(itemIds = h.itemIds - id)
        }
        today.toMap().forEach { (occ, set) ->
            if (set.looks.any { id in it.itemIds }) {
                val rest = set.looks.filter { id !in it.itemIds }
                if (rest.isEmpty()) today.remove(occ) else today[occ] = set.copy(looks = rest)
            }
        }
        heroIndex = 0
        val stale = tryOns.filterKeys { k -> id in k.split(",") }
        stale.keys.forEach { tryOns.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteFile(target.imagePath)
            stale.values.forEach { repo.deleteFile(it) }
        }
        persist()
    }

    // ------------------------------------------------------------------ profile
    fun setProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching { repo.importImage(uri, 1280, "profile") }.getOrNull().also { repo.clearCaptures() }
            }
            if (f == null) { message = "Couldn't read that photo"; return@launch }
            val old = profile.photoPath
            profile = profile.copy(photoPath = f.absolutePath)
            persist()
            withContext(Dispatchers.IO) { repo.deleteFile(old) }
            if (ai.isConfigured) analyse()
        }
    }

    fun updateProfile(p: Profile) {
        val cityChanged = !p.city.equals(profile.city, ignoreCase = true)
        profile = p
        persist()
        if (cityChanged && p.city.isNotBlank()) refreshWeather(force = true)
    }

    fun analyse() {
        val p = profile
        if (analysing) return
        if (p.photoPath.isBlank() || !File(p.photoPath).exists()) { message = "Add a full-length photo first"; return }
        if (!ai.isConfigured) { message = "Add an AI key in Settings first"; return }
        analysing = true
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { File(p.photoPath).readBytes() }
                val a = stylist.analyse(bytes, p)
                profile = profile.copy(analysis = a)
                persist()
                message = "Your style profile is ready"
            } catch (e: Exception) {
                message = e.message ?: "Analysis failed"
            } finally {
                analysing = false
            }
        }
    }

    // ------------------------------------------------------------------ gaps
    fun findGaps() {
        if (gapsBusy) return
        if (items.size < 3) { message = "Add a few more clothes first"; return }
        if (!ai.isConfigured) { message = "Add an AI key in Settings first"; return }
        gapsBusy = true
        viewModelScope.launch {
            try {
                gaps = stylist.gaps(items.filter { !it.detectFailed }, profile, weather)
                persist()
            } catch (e: Exception) {
                message = e.message ?: "Analysis failed"
            } finally {
                gapsBusy = false
            }
        }
    }
}
