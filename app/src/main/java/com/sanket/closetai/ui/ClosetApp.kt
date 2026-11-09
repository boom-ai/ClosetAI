@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sanket.closetai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanket.closetai.ai.AiSettings
import com.sanket.closetai.ai.DEFAULT_IMAGE_MODEL
import com.sanket.closetai.ai.Provider
import com.sanket.closetai.data.CATEGORIES
import com.sanket.closetai.data.FORMALITY_LABELS
import com.sanket.closetai.data.GROUPS
import com.sanket.closetai.data.Item
import com.sanket.closetai.data.Look
import com.sanket.closetai.data.groupOf
import java.io.File

private val R14 = RoundedCornerShape(14.dp)
private val R18 = RoundedCornerShape(18.dp)

private fun hexColor(h: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrNull()

@Composable
fun ClosetApp(vm: AppViewModel) {
    val snack = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var openItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris: List<Uri> ->
        vm.addPhotos(uris)
    }
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.setProfilePhoto(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val u = vm.captureUri
        if (ok && u != null) {
            if (vm.captureForProfile) vm.setProfilePhoto(u) else vm.addPhotos(listOf(u))
        }
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Closet AI", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (vm.items.isEmpty()) "Your wardrobe, styled" else "${vm.items.size} pieces logged",
                        color = Mute, fontSize = 13.sp,
                    )
                }
                IconButton(onClick = { vm.showSettings = true }) {
                    Icon(Icons.Filled.Settings, "Settings", tint = if (vm.ai.isConfigured) Mute else Gold)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Bg, indicatorColor = Gold, selectedTextColor = Gold,
                    unselectedIconColor = Mute, unselectedTextColor = Mute,
                )
                NavigationBarItem(vm.tab == Tab.WARDROBE, { vm.tab = Tab.WARDROBE },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Wardrobe") }, colors = colors)
                NavigationBarItem(vm.tab == Tab.STYLE, { vm.tab = Tab.STYLE },
                    icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Style me") }, colors = colors)
                NavigationBarItem(vm.tab == Tab.PROFILE, { vm.tab = Tab.PROFILE },
                    icon = { Icon(Icons.Filled.Person, null) }, label = { Text("Profile") }, colors = colors)
            }
        },
        floatingActionButton = {
            if (vm.tab == Tab.WARDROBE) {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = Gold, contentColor = Bg, shape = CircleShape) {
                    Icon(Icons.Filled.Add, "Add clothes")
                }
            }
        },
    ) { pad ->
        when (vm.tab) {
            Tab.WARDROBE -> WardrobeScreen(vm, pad, onItem = { openItemId = it }, onAdd = { showAdd = true })
            Tab.STYLE -> StyleScreen(vm, pad)
            Tab.PROFILE -> ProfileScreen(
                vm, pad,
                onGallery = { profilePicker.launch(imagesOnly) },
                onCamera = { camera.launch(vm.newCaptureUri(forProfile = true)) },
            )
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = Panel,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
                Text("Add clothes", style = MaterialTheme.typography.titleLarge)
                Text(
                    "One item per photo. Lay it flat or hang it against a plain wall — the AI detects type, colour, fit and formality.",
                    color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                SheetOption("📷  Take a photo", "Use your camera") {
                    showAdd = false; camera.launch(vm.newCaptureUri(forProfile = false))
                }
                SheetOption("🖼️  Choose from gallery", "Pick up to 30 at once for bulk logging") {
                    showAdd = false; gallery.launch(imagesOnly)
                }
            }
        }
    }

    openItemId?.let { id ->
        val item = vm.items.firstOrNull { it.id == id }
        if (item == null) openItemId = null else ItemSheet(vm, item, onClose = { openItemId = null })
    }

    if (vm.showSettings) SettingsDialog(vm)
}

@Composable
private fun SheetOption(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(R14).background(CardC).border(1.dp, Line, R14)
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(sub, color = Mute, fontSize = 12.sp)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text, fontSize = 13.sp) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CardC, labelColor = Ink, selectedContainerColor = Ink, selectedLabelColor = Bg,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = Line, selectedBorderColor = Ink,
        ),
    )
}

@Composable
private fun Thumb(path: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Paper)) {
        AsyncImage(
            model = File(path), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !busy, shape = R14,
        modifier = modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Bg, strokeWidth = 2.dp)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(R18).background(Panel).border(1.dp, Line, R18).padding(16.dp),
    ) { content() }
}

@Composable
private fun FieldLabel(t: String) =
    Text(t.uppercase(), color = Mute, fontSize = 11.sp, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

// ===================== WARDROBE =====================

@Composable
private fun WardrobeScreen(vm: AppViewModel, pad: PaddingValues, onItem: (String) -> Unit, onAdd: () -> Unit) {
    val counts = vm.items.groupingBy { it.category }.eachCount()
    val present = CATEGORIES.filter { (counts[it] ?: 0) > 0 }
    val filter = if (vm.filter == "All" || vm.filter in present) vm.filter else "All"
    val list = vm.items.filter { filter == "All" || it.category == filter }
        .sortedWith(compareBy<Item> { CATEGORIES.indexOf(it.category) }.thenByDescending { it.addedAt })
    val tops = vm.items.count { groupOf(it.category) in listOf("Tops", "Layers") }
    val bottoms = vm.items.count { groupOf(it.category) == "Bottoms" }
    val worn = vm.items.count { it.wornCount > 0 }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 90.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                Stat("${vm.items.size}", "Items", Modifier.weight(1f))
                Stat("$tops×$bottoms", "Tops × bottoms", Modifier.weight(1f))
                Stat("${if (vm.items.isEmpty()) 0 else worn * 100 / vm.items.size}%", "Worn", Modifier.weight(1f))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All  ${vm.items.size}", filter == "All") { vm.filter = "All" } }
                items(present) { c -> Chip("$c  ${counts[c]}", filter == c) { vm.filter = c } }
            }
        }
        if (list.isEmpty() && vm.pending == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).border(1.5.dp, Line, R18).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Start your closet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tap + to photograph your clothes. Select many from your gallery at once — each is sorted into shirts, t-shirts, polos, pants and more automatically.",
                        color = Mute, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) {
                        Text("Add clothes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (filter == "All") {
            items(vm.pending) {
                Column(Modifier.clip(R14).background(CardC).border(1.dp, Line, R14)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(Paper), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Copper, strokeWidth = 3.dp)
                    }
                    Text("DETECTING…", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(8.dp))
                }
            }
        }
        items(list, key = { it.id }) { piece ->
            Column(Modifier.clip(R14).background(CardC).border(1.dp, Line, R14).clickable { onItem(piece.id) }) {
                Box {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(Paper)) {
                        AsyncImage(model = File(piece.imagePath), contentDescription = piece.name,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    hexColor(piece.colorHex)?.let { c ->
                        Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(14.dp).clip(CircleShape)
                            .background(c).border(2.dp, Color.White, CircleShape))
                    }
                }
                Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                    Text(piece.category.uppercase(), color = Gold, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(piece.name, fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(R14).background(Panel).border(1.dp, Line, R14).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label.uppercase(), color = Mute, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ItemSheet(vm: AppViewModel, item: Item, onClose: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var formality by remember(item.id) { mutableStateOf(item.formality) }
    var color by remember(item.id) { mutableStateOf(item.color) }
    var fit by remember(item.id) { mutableStateOf(item.fit) }
    var catOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Thumb(item.imagePath, Modifier.fillMaxWidth().height(260.dp))
            FieldLabel("Name")
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FieldLabel("Category")
            Box {
                OutlinedButton(onClick = { catOpen = true }, shape = R14, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Line)) {
                    Text("$category  ·  ${groupOf(category)}", color = Ink)
                }
                DropdownMenu(expanded = catOpen, onDismissRequest = { catOpen = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                    GROUPS.forEach { (g, cats) ->
                        Text(g.uppercase(), color = Gold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        cats.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catOpen = false })
                        }
                    }
                }
            }
            FieldLabel("Formality")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FORMALITY_LABELS.forEachIndexed { i, l -> Chip("${i + 1} $l", formality == i + 1) { formality = i + 1 } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Colour"); OutlinedTextField(color, { color = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Fit"); OutlinedTextField(fit, { fit = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            val tags = listOf(item.pattern, item.material) + item.seasons + "Worn ${item.wornCount}×"
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.filter { it.isNotBlank() }.forEach {
                    Text(it, fontSize = 11.sp, color = Mute,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(CardC).border(1.dp, Line, RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            if (item.notes.isNotBlank()) Text("💡 ${item.notes}", color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(16.dp))
            PrimaryButton("✦ Style outfits around this") { vm.styleAround(item.id); onClose() }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { confirmDelete = true }, shape = R14, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Line)) { Text("Delete", color = Bad) }
                OutlinedButton(
                    onClick = {
                        vm.updateItem(item.copy(name = name.trim().ifBlank { item.name }, category = category,
                            formality = formality, color = color.trim(), fit = fit.trim()))
                        vm.message = "Saved"; onClose()
                    },
                    shape = R14, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Line),
                ) { Text("Save changes", color = Ink) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = { Text(item.name) },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteItem(item.id); onClose() }) { Text("Delete", color = Bad) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
            containerColor = Panel,
        )
    }
}

// ===================== STYLE =====================

@Composable
private fun StyleScreen(vm: AppViewModel, pad: PaddingValues) {
    val anchor = vm.items.firstOrNull { it.id == vm.anchorId }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 6.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card {
                Text("What should I wear?", style = MaterialTheme.typography.titleLarge)
                FieldLabel("Occasion")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OCCASIONS.forEach { Chip(it, vm.occasion == it) { vm.occasion = it } }
                }
                FieldLabel("Weather")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEATHER.forEach { Chip(it, vm.weather == it) { vm.weather = it } }
                }
                FieldLabel("Vibe")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VIBES.forEach { Chip(it, vm.vibe == it) { vm.vibe = it } }
                }
                if (anchor != null) {
                    FieldLabel("Built around")
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(R14).background(CardC).padding(6.dp)) {
                        Thumb(anchor.imagePath, Modifier.size(44.dp))
                        Text(anchor.name, Modifier.padding(horizontal = 10.dp).weight(1f, fill = false), fontSize = 13.sp)
                        IconButton({ vm.anchorId = null }) { Icon(Icons.Filled.Close, "Clear", tint = Mute) }
                    }
                }
                FieldLabel("Anything else? (optional)")
                OutlinedTextField(vm.extra, { vm.extra = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. meeting her parents, want to look taller", color = Mute) })
                Spacer(Modifier.height(14.dp))
                PrimaryButton("✦ Style me", busy = vm.styling) { vm.styleMe() }
            }
        }
        if (vm.styling) item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
                Text("  ${vm.styleStage.ifBlank { "Putting looks together…" }}  (30–90 s)", color = Mute)
            }
        }
        items(vm.looks) { LookCard(vm, it) }

        item { SectionHeader("Saved looks", if (vm.saved.isEmpty()) "" else "${vm.saved.size}") }
        if (vm.saved.isEmpty()) item { Text("Tap ♡ on any outfit to keep it here.", color = Mute, fontSize = 13.sp) }
        items(vm.saved.toList()) { LookCard(vm, it) }

        item { SectionHeader("Recently worn", "") }
        item {
            if (vm.history.isEmpty()) Text("Tap “Wearing this” so the stylist avoids repeats.", color = Mute, fontSize = 13.sp)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.history.take(14)) { h ->
                    val its = h.itemIds.mapNotNull { id -> vm.items.firstOrNull { it.id == id } }.take(4)
                    val days = ((System.currentTimeMillis() - h.at) / 86_400_000L).toInt()
                    Column(Modifier.width(150.dp).clip(R14).background(Panel).border(1.dp, Line, R14).padding(8.dp)) {
                        FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            its.forEach { Thumb(it.imagePath, Modifier.size(63.dp)) }
                        }
                        Text(h.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(when (days) { 0 -> "Today"; 1 -> "Yesterday"; else -> "$days days ago" }, color = Mute, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Card {
                Text("Wardrobe gaps", style = MaterialTheme.typography.titleMedium)
                Text("Find the few pieces that would unlock the most new outfits.", color = Mute, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                PrimaryButton("Analyse my wardrobe", busy = vm.gapsBusy) { vm.findGaps() }
                vm.gaps?.let { g ->
                    Text(g.verdict, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp), lineHeight = 20.sp)
                    g.buys.forEachIndexed { i, (what, why) ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(R14).background(CardC).padding(12.dp)) {
                            Text("${i + 1}. $what", fontWeight = FontWeight.SemiBold)
                            Text(why, color = Mute, fontSize = 12.sp)
                        }
                    }
                    if (g.unused.isNotBlank()) Text(g.unused, color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, aside: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (aside.isNotBlank()) Text(aside, color = Mute, fontSize = 13.sp)
    }
}

@Composable
private fun LookCard(vm: AppViewModel, look: Look) {
    val its = look.itemIds.mapNotNull { id -> vm.items.firstOrNull { it.id == id } }
    if (its.isEmpty()) return
    val saved = vm.isSaved(look)
    val tryPath = vm.tryOns[look.key]
    val trying = look.key in vm.tryOnBusy
    Column(Modifier.fillMaxWidth().clip(R18).background(Panel).border(1.dp, Line, R18)) {
        if (tryPath != null || trying) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(Paper), contentAlignment = Alignment.Center) {
                if (tryPath != null) AsyncImage(model = File(tryPath), contentDescription = "You wearing ${look.title}",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (trying) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(R14).background(Color(0xCC14110F)).padding(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Gold, strokeWidth = 3.dp)
                    Text("Dressing you up… (20–60 s)", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().background(Paper),
            contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(its) { i ->
                Box(Modifier.width(92.dp).aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
                    AsyncImage(model = File(i.imagePath), contentDescription = i.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize())
                    Text(i.category, color = Color.White, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xBB000000)).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(look.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (look.score > 0) {
                    Text(
                        String.format(java.util.Locale.US, "%.1f/10", look.score),
                        color = Bg, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
            Text(look.why, color = Color(0xFFE2D8CC), lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            look.tips.forEach { Text("•  $it", color = Color(0xFFE2D8CC), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (look.addOn.isNotBlank()) Text("🛍️ Would level up with: ${look.addOn}", color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Button(
                onClick = { vm.tryOn(look) }, enabled = !trying, shape = R14,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardC, contentColor = Gold),
                border = BorderStroke(1.dp, Gold),
            ) {
                Text(if (tryPath == null) "✨ See it on me" else "↻ Regenerate try-on", fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.wear(look) }, shape = R14,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) {
                    Text("👕 Wear", fontWeight = FontWeight.SemiBold)
                }
                val r = vm.ratings[look.key]
                OutlinedButton(onClick = { vm.rate(look, true) }, shape = R14, border = BorderStroke(1.dp, if (r == 1) Gold else Line),
                    contentPadding = PaddingValues(horizontal = 12.dp)) { Text("👍") }
                OutlinedButton(onClick = { vm.rate(look, false) }, shape = R14, border = BorderStroke(1.dp, if (r == -1) Bad else Line),
                    contentPadding = PaddingValues(horizontal = 12.dp)) { Text("👎") }
                OutlinedButton(onClick = { vm.toggleSave(look) }, shape = R14, border = BorderStroke(1.dp, Line),
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (saved) Copper else Ink,
                        modifier = Modifier.size(18.dp))
                    Text(if (saved) " Saved" else " Save", color = Ink)
                }
            }
        }
    }
}

// ===================== PROFILE =====================

@Composable
private fun ProfileScreen(vm: AppViewModel, pad: PaddingValues, onGallery: () -> Unit, onCamera: () -> Unit) {
    val p = vm.profile
    var height by remember(p.height) { mutableStateOf(p.height) }
    var city by remember(p.city) { mutableStateOf(p.city) }
    var notes by remember(p.notes) { mutableStateOf(p.notes) }
    fun commit() = vm.updateProfile(vm.profile.copy(height = height.trim(), city = city.trim(), notes = notes.trim()))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 6.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.width(112.dp).aspectRatio(0.75f).clip(R14).background(CardC).border(1.dp, Line, R14).clickable { onGallery() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.photoPath.isNotBlank()) AsyncImage(model = File(p.photoPath), contentDescription = "You",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("Tap to add a full-length photo", color = Mute, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Your style profile", style = MaterialTheme.typography.titleLarge)
                    Text("One clear, full-length photo in good light. The AI reads your build, skin undertone and proportions to personalise every outfit.",
                        color = Mute, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onGallery) { Text("Gallery") }
                        TextButton(onCamera) { Text("Camera") }
                    }
                }
            }
            FieldLabel("Dressing for")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DRESSING_FOR.forEach { Chip(it, p.dressingFor == it) { commit(); vm.updateProfile(vm.profile.copy(dressingFor = it)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Height")
                    OutlinedTextField(height, { height = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("5'10\"", color = Mute) })
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("City / climate")
                    OutlinedTextField(city, { city = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Jalandhar", color = Mute) })
                }
            }
            FieldLabel("Usual style")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STYLES.forEach { Chip(it, p.style == it) { commit(); vm.updateProfile(vm.profile.copy(style = it)) } }
            }
            FieldLabel("Anything to know (optional)")
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. want to look leaner, prefer earthy colours", color = Mute) })
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Analyse my look", busy = vm.analysing) { commit(); vm.analyse() }
        }

        p.analysis?.let { a ->
            Card {
                Text("Your style read", style = MaterialTheme.typography.titleLarge)
                Text(a.summary, color = Color(0xFFE2D8CC), lineHeight = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
                KV("Build", a.build); KV("Proportions", a.proportions); KV("Skin tone", "${a.skinTone} · ${a.undertone} undertone")
                FieldLabel("Your best colours"); Swatches(a.bestColors.map { it.name to it.hex })
                FieldLabel("Go easy on"); Swatches(a.avoidColors.map { it.name to it.hex })
                FieldLabel("Fits that work for you"); a.bestFits.forEach { Text("•  $it", fontSize = 14.sp, lineHeight = 20.sp) }
                FieldLabel("Your style rules"); a.rules.forEach { Text("•  $it", fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    if (v.isBlank()) return
    Row(Modifier.padding(top = 2.dp)) {
        Text(k, color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, modifier = Modifier.width(96.dp))
        Text(v, fontSize = 13.5.sp)
    }
}

@Composable
private fun Swatches(list: List<Pair<String, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { (name, hex) ->
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(CardC).border(1.dp, Line, RoundedCornerShape(50))
                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(hexColor(hex) ?: Mute))
                Text("  $name", fontSize = 12.sp)
            }
        }
    }
}

// ===================== SETTINGS =====================

@Composable
private fun SettingsDialog(vm: AppViewModel) {
    val cur = vm.aiSettings
    var provider by remember { mutableStateOf(cur.provider) }
    var key by remember { mutableStateOf(cur.apiKey) }
    var model by remember { mutableStateOf(cur.model) }
    var imageModel by remember { mutableStateOf(cur.imageModel) }
    var geminiKey by remember { mutableStateOf(cur.geminiKey) }
    val uri = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = { vm.showSettings = false },
        containerColor = Panel,
        title = { Text("AI settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Closet AI calls the AI directly from your phone with your own key. Gemini keys from Google AI Studio have a free tier.",
                    color = Mute, fontSize = 13.sp, lineHeight = 18.sp)
                FieldLabel("Provider")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Provider.values().forEach { pv ->
                        Chip(pv.label, provider == pv) {
                            if (provider != pv) { provider = pv; model = pv.defaultModel; if (cur.provider != pv) key = "" else key = cur.apiKey }
                        }
                    }
                }
                FieldLabel("API key")
                OutlinedTextField(key, { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                TextButton({ uri.openUri(provider.keyUrl) }) { Text("Get a ${provider.label} key →") }
                FieldLabel("Model")
                OutlinedTextField(model, { model = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Leave the default unless you want a different model.", color = Mute, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
                FieldLabel("Try-on image model (Gemini)")
                OutlinedTextField(imageModel, { imageModel = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (provider != Provider.GEMINI) {
                    FieldLabel("Gemini key for try-on")
                    OutlinedTextField(geminiKey, { geminiKey = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
                Text("“See it on me” uses Gemini's image model with your Gemini key. Image generation may not be on the free tier.",
                    color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton({ vm.saveSettings(AiSettings(provider, key, model.ifBlank { provider.defaultModel },
                imageModel.ifBlank { DEFAULT_IMAGE_MODEL }, geminiKey)) }) { Text("Save") }
        },
        dismissButton = { TextButton({ vm.showSettings = false }) { Text("Cancel") } },
    )
}
