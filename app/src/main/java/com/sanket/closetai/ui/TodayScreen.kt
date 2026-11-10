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

private fun directionLabel(look: Look): String {
    val d = when (look.direction) {
        "safe" -> "Safe & sharp"
        "elevated" -> "Elevated"
        "personality" -> "Personality"
        else -> "Today's pick"
    }
    return if (look.edited) "$d · edited" else d
}

private fun scoreText(score: Double): String = String.format(Locale.US, "%.1f", score)

@Composable
fun TodayScreen(vm: AppViewModel, pad: PaddingValues, actions: Actions) {
    var showTune by remember { mutableStateOf(false) }
    var swapId by remember { mutableStateOf<String?>(null) }
    var showDislike by remember { mutableStateOf(false) }
    var confirmRestyle by remember { mutableStateOf(false) }
    var confirmTryOn by remember { mutableStateOf(false) }

    val set = vm.currentSet
    val look: Look? = if (set == null) null else set.looks.getOrNull(vm.heroIndex) ?: set.looks.firstOrNull()
    val err = vm.styleError

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Gutter, end = Gutter,
            top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "weather") { WeatherHeader(vm, actions) }

        if (!vm.ai.isConfigured) {
            item(key = "nokey") {
                Text(
                    "On-device styling. Add an AI key for sharper looks  ›",
                    style = TSmall, color = Ink2,
                    modifier = Modifier.fillMaxWidth().clip(RField).background(Surface1)
                        .clickable { actions.openSettings() }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        item(key = "occasions") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Dressing for", Modifier.weight(1f))
                    TextAction("Tune") { showTune = true }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(OCCASIONS, key = { "occ-$it" }) { o ->
                        Chip(o, selected = vm.occasion == o) { vm.selectOccasion(o) }
                    }
                }
            }
        }

        if (err != null) {
            item(key = "error") {
                Column(
                    Modifier.fillMaxWidth().clip(RCard).background(DangerSoft).border(1.dp, Danger, RCard).padding(16.dp),
                ) {
                    Text("Couldn't style that", style = TTitle)
                    Text(err, style = TSmall, color = Ink, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Try again") { vm.styleMe() }
                        SecondaryButton("Settings") { actions.openSettings() }
                    }
                }
            }
        }

        if (!vm.canStyle()) {
            item(key = "basics") { BasicsCard(vm, actions) }
        } else if (set == null || look == null) {
            if (vm.styling) {
                item(key = "skeleton") { StylingSkeleton(vm) }
            } else {
                item(key = "invite") {
                    Column(
                        Modifier.fillMaxWidth().clip(RHero).background(Surface1).border(1.dp, Hairline, RHero).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Overline("Ready when you are", color = Accent)
                        Text("What should I wear?", style = TH1)
                        Text(
                            "I'll check today's weather, go through your clean clothes and put together three looks for ${vm.occasion.lowercase()}.",
                            style = TBody, color = Ink2,
                        )
                        Spacer(Modifier.height(6.dp))
                        PrimaryButton("Style me") { vm.styleMe() }
                    }
                }
            }
        } else {
            if (vm.styling) {
                item(key = "restyling") {
                    Column {
                        LinearProgressIndicator(
                            progress = { (vm.styleStep.coerceIn(1, 4)) / 4f },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RPill),
                            color = Accent, trackColor = Surface2,
                        )
                        Text(
                            vm.styleNote.ifBlank { STYLE_STAGES.getOrNull(vm.styleStep - 1) ?: "Styling" } + "…",
                            style = TSmall, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else if (vm.isStale(set)) {
                item(key = "stale") {
                    Row(
                        Modifier.fillMaxWidth().clip(RField).background(AccentSoft).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("The weather has changed since this was styled", style = TSmall, color = Ink, modifier = Modifier.weight(1f))
                        TextAction("Restyle") { vm.styleMe() }
                    }
                }
            }

            item(key = "hero") {
                HeroCard(vm, set, look, onPiece = { swapId = it.id }, onRestyle = { confirmRestyle = true })
            }
            item(key = "actions") {
                LookActions(
                    vm, look, actions,
                    onTryOn = { if (vm.tryOns[look.key] != null) confirmTryOn = true else vm.tryOn(look) },
                    onDislike = { showDislike = true },
                )
            }
            if (set.looks.size > 1) {
                item(key = "alternates") {
                    Column {
                        Overline(if (set.looks.size == 2) "One more way" else "Two more ways", Modifier.padding(top = 8.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(set.looks, key = { i, l -> "alt-$i-${l.key}" }) { i, alt ->
                                if (alt.key != look.key) {
                                    Column(Modifier.width(150.dp).clickable { vm.promote(i) }) {
                                        OutfitBoard(vm.piecesOf(alt), Modifier.fillMaxWidth().aspectRatio(0.8f), shape = RTile)
                                        Text(alt.title, style = TTitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 8.dp))
                                        Text(
                                            directionLabel(alt) + if (alt.score > 0) "  ·  ${scoreText(alt.score)}" else "",
                                            style = TSmall, color = Ink3, maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "hint") {
                Text("Tap any piece on the board to swap it.", style = TSmall, color = Ink3,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // ---------------- sheets & dialogs ----------------
    if (showTune) {
        AppSheet(onDismiss = { showTune = false }) {
            Text("Tune today's looks", style = TH2)
            Overline("Vibe", Modifier.padding(top = 16.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VIBES.forEach { v -> Chip(v, selected = vm.vibe == v) { vm.vibe = v } }
            }
            Overline("Anything else?", Modifier.padding(top = 16.dp, bottom = 8.dp))
            AppTextField(vm.extra, { vm.extra = it }, placeholder = "e.g. meeting her parents, lots of walking")
            val anchor = vm.anchorId?.let { vm.item(it) }
            if (anchor != null) {
                Overline("Built around", Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Photo(anchor.imagePath, Modifier.size(52.dp), shape = RField)
                    Text(anchor.name, style = TBody, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextAction("Clear") { vm.clearAnchor() }
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Restyle", enabled = vm.canStyle() && !vm.styling) { showTune = false; vm.styleMe() }
        }
    }

    val swapping = swapId
    if (swapping != null && look != null) {
        val current = vm.item(swapping)
        val options = vm.swapOptions(look, swapping)
        AppSheet(onDismiss = { swapId = null }) {
            Text("Swap this piece", style = TH2)
            if (current != null) Text(current.name, style = TSmall, modifier = Modifier.padding(top = 4.dp))
            if (options.isEmpty()) {
                Text("Nothing else clean in this category yet.", style = TBody, color = Ink2,
                    modifier = Modifier.padding(vertical = 20.dp))
            } else {
                LazyRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(options, key = { "swap-${it.id}" }) { o ->
                        Column(Modifier.width(116.dp).clickable { vm.swap(look, swapping, o.id); swapId = null }) {
                            Photo(o.imagePath, Modifier.fillMaxWidth().aspectRatio(0.75f))
                            Text(o.name, style = TSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SecondaryButton("It's in the laundry", Modifier.fillMaxWidth()) {
                vm.setLaundry(swapping, true)
                swapId = null
                vm.message = "Marked as in laundry — it won't be suggested until it's clean"
            }
        }
    }

    if (showDislike && look != null) {
        AppSheet(onDismiss = { showDislike = false }) {
            Text("What's off?", style = TH2)
            Text("This teaches your stylist. You won't see this exact combination again.", style = TSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DISLIKE_REASONS.forEach { r ->
                    Chip(r, selected = false) { vm.rate(look, false, r); showDislike = false }
                }
            }
        }
    }

    if (confirmRestyle) {
        ConfirmDialog(
            title = "Restyle?",
            text = "This replaces today's looks for ${vm.occasion.lowercase()} with new ones." +
                if (vm.ai.isConfigured) " It uses a little of your AI quota." else "",
            confirmLabel = "Restyle",
            onConfirm = { confirmRestyle = false; vm.styleMe() },
            onDismiss = { confirmRestyle = false },
        )
    }
    if (confirmTryOn && look != null) {
        ConfirmDialog(
            title = "Generate a new try-on?",
            text = "This replaces the current picture and uses your image quota again.",
            confirmLabel = "Generate",
            onConfirm = { confirmTryOn = false; vm.tryOn(look) },
            onDismiss = { confirmTryOn = false },
        )
    }
}

@Composable
private fun WeatherHeader(vm: AppViewModel, actions: Actions) {
    val w = vm.weather
    val date = remember { SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date()) }
    if (w == null) {
        Column {
            Overline(date)
            Panel(Modifier.padding(top = 8.dp)) {
                Text("Turn on live weather", style = TTitle)
                Text(
                    "Your looks adapt to the temperature, rain and sun where you are. Allow location, or add your city in Me.",
                    style = TSmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Use my location") { actions.requestLocation() }
                    if (vm.weatherBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().clickable(enabled = !vm.weatherBusy) { vm.refreshWeather(true) }) {
            Overline(date + "  ·  " + w.place.ifBlank { "Your location" })
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${w.temp.roundToInt()}°", style = TDisplay)
                Text(w.emoji, fontSize = 26.sp, modifier = Modifier.padding(start = 8.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("${w.condition} · feels ${w.feels.roundToInt()}°", style = TSmall, color = Ink)
                    Text(
                        "${w.tMin.roundToInt()}°–${w.tMax.roundToInt()}° · rain ${w.rainProb}% · UV ${w.uv.roundToInt()} · hum ${w.humidity}%",
                        style = TSmall, color = Ink3,
                    )
                }
                if (vm.weatherBusy) CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
            }
            Text(w.headline(), color = Accent, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            if (w.ageMinutes > 90) {
                Text("Updated ${ago(w.fetchedAt)} · tap to refresh", style = TSmall, color = Ink3,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun BasicsCard(vm: AppViewModel, actions: Actions) {
    val missing = vm.missingBasics()
    Column(
        Modifier.fillMaxWidth().clip(RHero).background(Surface1).border(1.dp, Hairline, RHero).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Overline("Let's build your closet", color = Accent)
        Text("Add a few clothes to get dressed", style = TH1)
        Text("Photograph what you own, one piece per photo. To style a look I need at least:", style = TBody, color = Ink2)
        listOf("Top", "Bottom", "Shoes").forEach { b ->
            val have = b !in missing
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (have) "✓" else "○", color = if (have) Success else Ink3, fontSize = 16.sp,
                    modifier = Modifier.width(28.dp))
                Text(if (b == "Shoes") "Shoes (optional, but better)" else "A ${b.lowercase()}", style = TBody,
                    color = if (have) Ink else Ink2)
            }
        }
        if (vm.detectTotal > 0) {
            Text("Detecting ${vm.detectDone} of ${vm.detectTotal}…", style = TSmall, color = Accent)
        }
        Spacer(Modifier.height(6.dp))
        PrimaryButton("Add clothes") { actions.openAddSheet() }
    }
}

@Composable
private fun StylingSkeleton(vm: AppViewModel) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "alpha",
    )
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).alpha(a).clip(RHero).background(Surface2))
        Spacer(Modifier.height(20.dp))
        STYLE_STAGES.forEachIndexed { i, stage ->
            val step = i + 1
            val mark = if (vm.styleStep > step) "✓" else if (vm.styleStep == step) "•" else "○"
            val color = if (vm.styleStep > step) Success else if (vm.styleStep == step) Accent else Ink3
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mark, color = color, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                Text(stage, style = TBody, color = if (vm.styleStep >= step) Ink else Ink3)
            }
        }
        if (vm.styleNote.isNotBlank()) Text(vm.styleNote, style = TSmall, color = Warn, modifier = Modifier.padding(top = 6.dp))
        Text("Usually 20–60 seconds. You can leave this screen.", style = TSmall, color = Ink3,
            modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun BoardOrTryOn(vm: AppViewModel, look: Look, onPiece: ((Item) -> Unit)?) {
    val pieces = vm.piecesOf(look)
    val tryPath = vm.tryOns[look.key]
    val trying = look.key in vm.tryOnBusy
    var onMe by remember(look.key, tryPath) { mutableStateOf(tryPath != null) }
    Box {
        Crossfade(targetState = onMe && tryPath != null, label = "boardOrTryOn") { showTry ->
            if (showTry && tryPath != null) {
                Photo(tryPath, Modifier.fillMaxWidth().aspectRatio(0.8f), shape = RHero, contentScale = ContentScale.Crop)
            } else {
                OutfitBoard(pieces, Modifier.fillMaxWidth().aspectRatio(0.8f), onPieceClick = onPiece)
            }
        }
        if (tryPath != null) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RPill).background(Color(0xD90E0D0C)).padding(4.dp),
            ) {
                ToggleLabel("Board", !onMe) { onMe = false }
                ToggleLabel("On me", onMe) { onMe = true }
            }
        }
        if (trying) {
            Column(
                Modifier.align(Alignment.Center).clip(RCard).background(Color(0xE60E0D0C)).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 2.dp)
                Text("Dressing you… 20–60 s", style = TSmall, color = Ink, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun ToggleLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) OnAccent else Ink2,
        modifier = Modifier.clip(RPill).background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun HeroCard(vm: AppViewModel, set: StyledSet, look: Look, onPiece: (Item) -> Unit, onRestyle: () -> Unit) {
    var expanded by remember(look.key) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        BoardOrTryOn(vm, look, onPiece)
        Overline(directionLabel(look), Modifier.padding(top = 18.dp), color = Accent)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
            Text(look.title, style = TH1, modifier = Modifier.weight(1f))
            if (look.score > 0) Text(scoreText(look.score), style = TH2, color = Accent, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        }
        if (look.weatherNote.isNotBlank()) {
            Text(look.weatherNote, color = Accent, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (look.why.isNotBlank()) {
            Text(look.why, style = TBody, color = Ink2, maxLines = if (expanded) 20 else 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (expanded) {
            look.tips.forEach { tip ->
                Row(Modifier.padding(top = 8.dp)) {
                    Text("•", color = Accent, modifier = Modifier.width(18.dp))
                    Text(tip, style = TBody, color = Ink)
                }
            }
            if (look.addOn.isNotBlank()) {
                Text("Would level up with: ${look.addOn}", style = TSmall, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (look.tips.isNotEmpty() || look.addOn.isNotBlank()) {
                TextAction(if (expanded) "Less" else "How to wear it") { expanded = !expanded }
            }
            Spacer(Modifier.weight(1f))
            Text("Styled ${ago(set.styledAt)}" + if (set.offline) " · on-device" else "", style = TSmall, color = Ink3)
            TextAction("Restyle", onClick = onRestyle)
        }
    }
}

@Composable
private fun LookActions(vm: AppViewModel, look: Look, actions: Actions, onTryOn: () -> Unit, onDislike: () -> Unit) {
    val worn = vm.isWornToday(look)
    val saved = vm.isSaved(look)
    val rating = vm.ratings[look.key]
    val hasTryOn = vm.tryOns[look.key] != null
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (worn) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RPill).background(Surface1).border(1.dp, Hairline, RPill)
                    .padding(start = 22.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wearing today  ✓", style = TTitle, color = Success, modifier = Modifier.weight(1f))
                if (vm.canUndoWear) TextAction("Undo") { vm.undoLastWear() }
            }
        } else {
            PrimaryButton("Wear this today") { vm.wear(look) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            ActionGlyph("✨", "On me", selected = hasTryOn, enabled = look.key !in vm.tryOnBusy, onClick = onTryOn)
            ActionGlyph(if (saved) "♥" else "♡", if (saved) "Saved" else "Save", selected = saved) { vm.toggleSave(look) }
            ActionGlyph("👍", "Like", selected = rating == 1) { vm.rate(look, true) }
            ActionGlyph("👎", "Not for me", selected = rating == -1, onClick = onDislike)
            if (hasTryOn) ActionGlyph("↗", "Share", selected = false) { actions.shareLook(look) }
        }
    }
}

@Composable
private fun ActionGlyph(glyph: String, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle(glyph, selected = selected, enabled = enabled, onClick = onClick)
        Text(label, fontSize = 11.sp, color = Ink3, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Detail sheet for any look (saved, worn or alternate), opened from the root. */
@Composable
fun LookDetailSheet(vm: AppViewModel, look: Look, actions: Actions, onClose: () -> Unit) {
    val pieces = vm.piecesOf(look)
    AppSheet(onDismiss = onClose) {
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            if (pieces.isEmpty()) {
                Text(look.title, style = TH2)
                Text("The pieces in this look are no longer in your closet.", style = TBody, color = Ink2,
                    modifier = Modifier.padding(vertical = 16.dp))
            } else {
                BoardOrTryOn(vm, look, null)
                Overline(look.occasion.ifBlank { directionLabel(look) }, Modifier.padding(top = 16.dp), color = Accent)
                Text(look.title, style = TH2, modifier = Modifier.padding(top = 4.dp))
                Text(pieces.joinToString("  ·  ") { it.name }, style = TSmall, modifier = Modifier.padding(top = 6.dp))
                if (look.why.isNotBlank()) Text(look.why, style = TBody, color = Ink2, modifier = Modifier.padding(top = 10.dp))
                look.tips.forEach { tip ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Text("•", color = Accent, modifier = Modifier.width(18.dp))
                        Text(tip, style = TBody, color = Ink)
                    }
                }
                Spacer(Modifier.height(18.dp))
                LookActions(
                    vm, look, actions,
                    onTryOn = { vm.tryOn(look) },
                    onDislike = { vm.rate(look, false); onClose() },
                )
            }
        }
    }
}
