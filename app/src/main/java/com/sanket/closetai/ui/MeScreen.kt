@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.sanket.closetai.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanket.closetai.ai.AiSettings
import com.sanket.closetai.ai.DEFAULT_IMAGE_MODEL
import com.sanket.closetai.ai.Provider
import com.sanket.closetai.data.DRESSING_FOR
import com.sanket.closetai.data.FORMALITY_LABELS
import com.sanket.closetai.data.GROUPS
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Look
import com.sanket.closetai.data.OCCASIONS
import com.sanket.closetai.data.STYLES
import com.sanket.closetai.data.StyledSet
import com.sanket.closetai.data.VIBES
import com.sanket.closetai.data.WearLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun KeyValue(k: String, v: String) {
    if (v.isNotBlank()) {
        Row(Modifier.padding(top = 6.dp)) {
            Text(k, fontSize = 13.sp, color = Ink3, modifier = Modifier.width(104.dp))
            Text(v, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Pill(text: String, dot: Color? = null) {
    Row(
        Modifier.clip(RPill).background(Surface2).padding(start = if (dot != null) 6.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(dot).border(1.dp, Hairline, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 12.sp, color = Ink)
    }
}

@Composable
fun MeScreen(vm: AppViewModel, pad: PaddingValues, actions: Actions) {
    val p = vm.profile
    var height by remember(p.height) { mutableStateOf(p.height) }
    var city by remember(p.city) { mutableStateOf(p.city) }
    var notes by remember(p.notes) { mutableStateOf(p.notes) }
    val commit = {
        val cur = vm.profile
        if (cur.height != height.trim() || cur.city != city.trim() || cur.notes != notes.trim()) {
            vm.updateProfile(cur.copy(height = height.trim(), city = city.trim(), notes = notes.trim()))
        }
    }
    val latestCommit by rememberUpdatedState(commit)
    DisposableEffect(Unit) { onDispose { latestCommit() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = Gutter, end = Gutter,
            top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Me", style = TH1)

        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.width(112.dp).aspectRatio(0.75f).clip(RTile).background(Surface2)
                        .clickable { actions.profileFromGallery() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.photoPath.isNotBlank()) {
                        Photo(p.photoPath, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, background = Surface2)
                    } else {
                        Text("Add a full-length photo", style = TSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Your photo", style = TTitle)
                    Text("Full length, daylight, phone at chest height, fitted clothes.", style = TSmall,
                        modifier = Modifier.padding(top = 4.dp))
                    Row {
                        TextAction("Gallery") { actions.profileFromGallery() }
                        TextAction("Camera") { actions.profileFromCamera() }
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Overline("Height", Modifier.padding(bottom = 6.dp))
                    AppTextField(height, { height = it }, placeholder = "5'7\"")
                }
                Column(Modifier.weight(1f)) {
                    Overline("City", Modifier.padding(bottom = 6.dp))
                    AppTextField(city, { city = it }, placeholder = "Jalandhar")
                }
            }
            Overline("Dressing for", Modifier.padding(top = 14.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DRESSING_FOR.forEach { d ->
                    Chip(d, selected = p.dressingFor == d) { commit(); vm.updateProfile(vm.profile.copy(dressingFor = d)) }
                }
            }
            Overline("Usual style", Modifier.padding(top = 14.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                STYLES.forEach { s ->
                    Chip(s, selected = p.style == s) { commit(); vm.updateProfile(vm.profile.copy(style = s)) }
                }
            }
            Overline("Anything to know", Modifier.padding(top = 14.dp, bottom = 6.dp))
            AppTextField(notes, { notes = it }, placeholder = "e.g. prefer earthy colours, nothing tight", singleLine = false)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (p.analysis == null) "Analyse my look" else "Re-analyse", busy = vm.analysing) {
                commit(); vm.analyse()
            }
        }

        val a = p.analysis
        if (a != null) {
            Panel {
                Overline("Your style read", color = Accent)
                Text(a.summary, style = TBody, color = Ink2, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                KeyValue("Build", a.build)
                KeyValue("Proportions", a.proportions)
                KeyValue("Skin tone", listOf(a.skinTone, if (a.undertone.isBlank()) "" else "${a.undertone} undertone").filter { it.isNotBlank() }.joinToString(" · "))
                KeyValue("Contrast", a.contrast)
                if (a.bestColors.isNotEmpty()) {
                    Overline("Your best colours", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        a.bestColors.forEach { c -> Pill(c.name, hexColor(c.hex) ?: Ink3) }
                    }
                }
                if (a.bestNeutrals.isNotEmpty()) {
                    Overline("Your neutrals", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        a.bestNeutrals.forEach { n -> Pill(n) }
                    }
                }
                if (a.avoidColors.isNotEmpty()) {
                    Overline("Go easy on", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        a.avoidColors.forEach { c -> Pill(c.name, hexColor(c.hex) ?: Ink3) }
                    }
                }
                if (a.bestFits.isNotEmpty()) {
                    Overline("Fits that work", Modifier.padding(top = 16.dp, bottom = 4.dp))
                    a.bestFits.forEach { f -> Text("•  $f", style = TBody, modifier = Modifier.padding(top = 4.dp)) }
                }
                if (a.rules.isNotEmpty()) {
                    Overline("Your rules", Modifier.padding(top = 16.dp, bottom = 4.dp))
                    a.rules.forEach { r -> Text("•  $r", style = TBody, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }

        Panel {
            val version = vm.tasteVersion
            val summary = remember(version) { vm.tasteSummary() }
            Overline("What your stylist has learned", color = Accent)
            if (summary.first.isEmpty() && summary.second.isEmpty()) {
                Text("Nothing yet. Like, save or wear looks and it will pick up your taste.", style = TSmall,
                    modifier = Modifier.padding(top = 8.dp))
            } else {
                if (summary.first.isNotEmpty()) {
                    Text("You like", style = TSmall, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.first.forEach { Pill(it) }
                    }
                }
                if (summary.second.isNotEmpty()) {
                    Text("Not for you", style = TSmall, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.second.forEach { Pill(it) }
                    }
                }
            }
            val bias = vm.tempBias
            if (version >= 0 && bias != 0.0) {
                Text(
                    "You run ${if (bias > 0) "warm" else "cold"} — outfits are adjusted by ${kotlin.math.abs(bias).roundToInt()}°.",
                    style = TSmall, modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (summary.first.isNotEmpty() || summary.second.isNotEmpty() || bias != 0.0) {
                TextAction("Reset what it learned") { vm.resetTaste() }
            }
        }

        Panel {
            val w = vm.weather
            Overline("Weather & location", color = Accent)
            Text(
                if (w == null) "No live weather yet."
                else "${w.place.ifBlank { "Your location" }} · " +
                    (if (w.source == "gps") "using this phone's location" else "using your profile city") +
                    " · updated ${ago(w.fetchedAt)}",
                style = TSmall, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!vm.hasLocationPermission()) SecondaryButton("Use my location") { actions.requestLocation() }
                SecondaryButton("Refresh") { commit(); vm.refreshWeather(true) }
                if (vm.weatherBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RCard).background(Surface1).border(1.dp, Hairline, RCard)
                .clickable { actions.openSettings() }.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Settings", style = TTitle)
                Text(if (vm.ai.isConfigured) "AI key and models" else "Add your AI key", style = TSmall,
                    color = if (vm.ai.isConfigured) Ink2 else Accent)
            }
            Text("›", fontSize = 22.sp, color = Ink3)
        }
        Text("Closet AI 2.0", style = TSmall, color = Ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsPage(vm: AppViewModel, onBack: () -> Unit) {
    val cur = vm.aiSettings
    var provider by remember { mutableStateOf(cur.provider) }
    var key by remember { mutableStateOf(cur.apiKey) }
    var model by remember { mutableStateOf(cur.model) }
    var imageModel by remember { mutableStateOf(cur.imageModel) }
    var geminiKey by remember { mutableStateOf(cur.geminiKey) }
    val uri = LocalUriHandler.current
    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 32.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Settings", style = TH2, modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            "Closet AI talks to the AI straight from this phone using your own key. The key is stored only here. " +
                "Gemini keys from Google AI Studio have a free tier. Without a key the app still styles you on-device, just less sharply.",
            style = TSmall, modifier = Modifier.padding(top = 12.dp),
        )

        Overline("Provider", Modifier.padding(top = 22.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Provider.values().forEach { pv ->
                Chip(pv.label, selected = provider == pv) {
                    if (provider != pv) {
                        provider = pv
                        model = if (cur.provider == pv) cur.model else pv.defaultModel
                        key = if (cur.provider == pv) cur.apiKey else ""
                    }
                }
            }
        }

        Overline("API key", Modifier.padding(top = 18.dp, bottom = 6.dp))
        SecretField(key, { key = it }, placeholder = "Paste your key")
        TextAction("Get a ${provider.label} key  →") { runCatching { uri.openUri(provider.keyUrl) } }

        Overline("Model", Modifier.padding(top = 10.dp, bottom = 6.dp))
        AppTextField(model, { model = it }, placeholder = provider.defaultModel)
        Text("Leave the default unless you know you want another model.", style = TSmall, color = Ink3,
            modifier = Modifier.padding(top = 6.dp))

        Overline("Try-on image model (Gemini)", Modifier.padding(top = 18.dp, bottom = 6.dp))
        AppTextField(imageModel, { imageModel = it }, placeholder = DEFAULT_IMAGE_MODEL)
        if (provider != Provider.GEMINI) {
            Overline("Gemini key for try-on", Modifier.padding(top = 18.dp, bottom = 6.dp))
            SecretField(geminiKey, { geminiKey = it }, placeholder = "Optional")
        }
        Text("“See it on me” uses Gemini's image model. Image generation may need billing enabled in AI Studio.",
            style = TSmall, color = Ink3, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(28.dp))
        PrimaryButton("Save") {
            vm.saveSettings(
                AiSettings(
                    provider, key, model.ifBlank { provider.defaultModel },
                    imageModel.ifBlank { DEFAULT_IMAGE_MODEL }, geminiKey,
                ),
            )
            onBack()
        }
    }
}
