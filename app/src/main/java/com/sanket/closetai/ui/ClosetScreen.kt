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

private const val LAUNDRY = "In laundry"

@Composable
fun ClosetScreen(vm: AppViewModel, pad: PaddingValues, actions: Actions) {
    var filter by remember { mutableStateOf("All") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }

    val laundry = vm.laundryCount()
    val failed = vm.failedCount()
    val groups = GROUPS.keys.filter { g -> vm.items.any { it.group == g } }
    val active = if (filter == "All" || filter in groups || (filter == LAUNDRY && laundry > 0)) filter else "All"
    val q = query.trim().lowercase()
    val shown = vm.items.filter { i ->
        (active == "All" || (active == LAUNDRY && i.inLaundry) || i.group == active) &&
            (q.isEmpty() || i.name.lowercase().contains(q) || i.color.lowercase().contains(q) || i.category.lowercase().contains(q))
    }.sortedWith(compareBy<Item> { GROUPS.keys.indexOf(it.group) }.thenByDescending { it.addedAt })

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Gutter, end = Gutter,
                top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Closet", style = TH1)
                            Text(
                                "${vm.items.size} piece${if (vm.items.size == 1) "" else "s"}" +
                                    if (laundry > 0) "  ·  $laundry in laundry" else "",
                                style = TSmall,
                            )
                        }
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Search", tint = Ink2)
                        }
                    }
                    if (searching) {
                        AppTextField(query, { query = it }, Modifier.padding(top = 10.dp), placeholder = "Search name, colour or type")
                    }
                    if (vm.detectTotal > 0) {
                        Column(Modifier.padding(top = 14.dp)) {
                            Text("Detecting ${vm.detectDone} of ${vm.detectTotal}…", style = TSmall, color = Accent)
                            LinearProgressIndicator(
                                progress = { if (vm.detectTotal == 0) 0f else vm.detectDone.toFloat() / vm.detectTotal.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RPill),
                                color = Accent, trackColor = Surface2,
                            )
                        }
                    } else if (failed > 0) {
                        Row(
                            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RField).background(AccentSoft)
                                .padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$failed item${if (failed == 1) " needs" else "s need"} details", style = TSmall, color = Ink,
                                modifier = Modifier.weight(1f))
                            TextAction("Retry detection") { vm.redetect() }
                        }
                    }
                    if (vm.items.isNotEmpty()) {
                        LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "f-all") { Chip("All", selected = active == "All") { filter = "All" } }
                            items(groups, key = { "f-$it" }) { g -> Chip(g, selected = active == g) { filter = g } }
                            if (laundry > 0) {
                                item(key = "f-laundry") {
                                    Chip("🧺 $laundry", selected = active == LAUNDRY) { filter = LAUNDRY }
                                }
                            }
                        }
                    }
                    if (active == LAUNDRY) {
                        TextAction("Everything is clean — bring it all back") { vm.allClean(); filter = "All" }
                    }
                }
            }

            if (vm.items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        "Your closet is empty",
                        "Photograph your clothes, one piece per photo. Pick many from your gallery at once and they sort themselves.",
                        actionLabel = "Add clothes", onAction = { actions.openAddSheet() },
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "nomatch", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nothing matches.", style = TBody, color = Ink2, modifier = Modifier.padding(vertical = 24.dp))
                }
            }

            gridItems(shown, key = { "item-${it.id}" }) { piece ->
                Column(
                    Modifier.alpha(if (piece.inLaundry) 0.45f else 1f).combinedClickable(
                        onClick = { openId = piece.id },
                        onLongClick = {
                            vm.setLaundry(piece.id, !piece.inLaundry)
                            vm.message = if (piece.inLaundry) "Back in the closet" else "In laundry — won't be suggested"
                        },
                    ),
                ) {
                    Box {
                        Photo(piece.imagePath, Modifier.fillMaxWidth().aspectRatio(0.75f))
                        if (piece.inLaundry) {
                            Text("🧺", fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                        }
                        val dot = hexColor(piece.colorHex)
                        if (dot != null && !piece.detectFailed) {
                            Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(12.dp).clip(CircleShape)
                                .background(dot).border(1.5.dp, Color.White, CircleShape))
                        }
                    }
                    if (piece.detectFailed) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Warn))
                            Text("  Needs details", fontSize = 13.sp, color = Warn)
                        }
                    } else {
                        Text(piece.name, fontSize = 13.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(piece.category, fontSize = 11.sp, color = Ink3, maxLines = 1)
                }
            }

            if (vm.items.size >= 3) {
                item(key = "gaps", span = { GridItemSpan(maxLineSpan) }) { GapsPanel(vm) }
            }
        }

        Row(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = Gutter, bottom = pad.calculateBottomPadding() + 20.dp)
                .height(52.dp).clip(RPill).background(Accent).clickable { actions.openAddSheet() }
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+  Add", style = TTitle, color = OnAccent)
        }
    }

    val open = openId?.let { vm.item(it) }
    if (open != null) {
        ItemSheet(vm, open) { openId = null }
    }
}

@Composable
private fun GapsPanel(vm: AppViewModel) {
    val g = vm.gaps
    Panel(Modifier.padding(top = 8.dp)) {
        Overline("Wardrobe gaps", color = Accent)
        Text("What to buy next", style = TH2, modifier = Modifier.padding(top = 4.dp))
        if (g == null) {
            Text("Find the few pieces that would unlock the most new outfits from what you already own.",
                style = TSmall, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text(g.verdict, style = TBody, color = Ink2, modifier = Modifier.padding(top = 8.dp))
            g.buys.forEachIndexed { i, buy ->
                Row(Modifier.padding(top = 12.dp)) {
                    Text("${i + 1}", style = TH2, color = Accent, modifier = Modifier.width(30.dp))
                    Column {
                        Text(buy.first, style = TTitle)
                        Text(buy.second, style = TSmall)
                    }
                }
            }
            if (g.unused.isNotBlank()) Text(g.unused, style = TSmall, modifier = Modifier.padding(top = 12.dp))
            if (g.at > 0) Text("Checked ${ago(g.at)}", style = TSmall, color = Ink3, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(if (g == null) "Analyse my wardrobe" else "Refresh", enabled = !vm.gapsBusy) { vm.findGaps() }
            if (vm.gapsBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ItemSheet(vm: AppViewModel, item: Item, onClose: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var formality by remember(item.id) { mutableIntStateOf(item.formality) }
    var color by remember(item.id) { mutableStateOf(item.color) }
    var fit by remember(item.id) { mutableStateOf(item.fit) }
    var catOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppSheet(onDismiss = onClose) {
        Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState())) {
            Photo(item.imagePath, Modifier.fillMaxWidth().height(280.dp))
            Overline("Name", Modifier.padding(top = 16.dp, bottom = 6.dp))
            AppTextField(name, { name = it })

            Overline("Category", Modifier.padding(top = 14.dp, bottom = 6.dp))
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RField).border(1.dp, Hairline, RField).clickable { catOpen = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(category, style = TBody, modifier = Modifier.weight(1f))
                    Text("Change", fontSize = 13.sp, color = Accent)
                }
                DropdownMenu(expanded = catOpen, onDismissRequest = { catOpen = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                    GROUPS.forEach { (g, cats) ->
                        Text(g.uppercase(), style = TOverline, color = Accent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        cats.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catOpen = false })
                        }
                    }
                }
            }

            Overline("Formality", Modifier.padding(top = 14.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FORMALITY_LABELS.forEachIndexed { i, l -> Chip(l, selected = formality == i + 1) { formality = i + 1 } }
            }

            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Overline("Colour", Modifier.padding(bottom = 6.dp))
                    AppTextField(color, { color = it })
                }
                Column(Modifier.weight(1f)) {
                    Overline("Fit", Modifier.padding(bottom = 6.dp))
                    AppTextField(fit, { fit = it })
                }
            }

            val tags = (listOf(item.pattern, item.material, item.sleeve.takeIf { it != "n/a" } ?: "", item.weight) +
                item.seasons + item.styleTags).filter { it.isNotBlank() }.distinct()
            if (tags.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t ->
                        Text(t, fontSize = 12.sp, color = Ink2,
                            modifier = Modifier.clip(RPill).background(Surface2).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Text(
                "Worn ${item.wornCount}×" + if (item.lastWorn > 0) "  ·  last ${ago(item.lastWorn)}" else "",
                style = TSmall, color = Ink3, modifier = Modifier.padding(top = 12.dp),
            )
            if (item.notes.isNotBlank()) Text(item.notes, style = TSmall, modifier = Modifier.padding(top = 6.dp))

            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("In laundry", style = TTitle)
                    Text("Not suggested until it's clean", style = TSmall)
                }
                Switch(
                    checked = item.inLaundry, onCheckedChange = { vm.setLaundry(item.id, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent,
                        uncheckedTrackColor = Surface2, uncheckedThumbColor = Ink3, uncheckedBorderColor = Hairline),
                )
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton("Style a look around this") { vm.styleAround(item.id); onClose() }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Save changes", Modifier.weight(1f)) {
                    vm.updateItem(
                        item.copy(
                            name = name.trim().ifBlank { item.name }, category = category, formality = formality,
                            color = color.trim(), fit = fit.trim(),
                            detectFailed = item.detectFailed && category == "Other" && name.trim() == item.name,
                        ),
                    )
                    onClose()
                }
                SecondaryButton("Re-detect", Modifier.weight(1f)) { vm.redetect(listOf(item.id)); onClose() }
            }
            TextAction("Delete this item", Modifier.padding(top = 4.dp), color = Danger) { confirmDelete = true }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this item?",
            text = "${item.name} will be removed from your closet and from saved looks.",
            confirmLabel = "Delete", danger = true,
            onConfirm = { confirmDelete = false; vm.deleteItem(item.id); onClose() },
            onDismiss = { confirmDelete = false },
        )
    }
}
