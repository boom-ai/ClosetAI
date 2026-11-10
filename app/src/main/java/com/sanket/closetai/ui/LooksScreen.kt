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

private fun dayLabel(t: Long): String {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
    val day = fmt.format(Date(t))
    val today = fmt.format(Date())
    val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
    return when (day) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(t))
    }
}

@Composable
fun LooksScreen(vm: AppViewModel, pad: PaddingValues, actions: Actions) {
    var worn by remember { mutableStateOf(false) }
    val top = pad.calculateTopPadding() + 12.dp
    val bottom = pad.calculateBottomPadding() + 28.dp

    Column(Modifier.fillMaxSize().padding(top = top)) {
        Column(Modifier.padding(horizontal = Gutter)) {
            Text("Looks", style = TH1)
            Row(Modifier.padding(top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Saved  ${vm.saved.size}", selected = !worn) { worn = false }
                Chip("Worn  ${vm.history.size}", selected = worn) { worn = true }
            }
        }
        if (!worn) {
            val looks = vm.saved.filter { vm.piecesOf(it).isNotEmpty() }
            if (looks.isEmpty()) {
                EmptyState(
                    "No saved looks yet",
                    "Tap the heart on any look you like and it will live here.",
                    Modifier.padding(horizontal = Gutter),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    gridItems(looks, key = { "saved-${it.key}" }) { look ->
                        val tryPath = vm.tryOns[look.key]
                        Column(Modifier.clickable { actions.openLook(look) }) {
                            if (tryPath != null) {
                                Photo(tryPath, Modifier.fillMaxWidth().aspectRatio(0.8f), contentScale = ContentScale.Crop)
                            } else {
                                OutfitBoard(vm.piecesOf(look), Modifier.fillMaxWidth().aspectRatio(0.8f), shape = RTile)
                            }
                            Text(look.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            if (look.occasion.isNotBlank()) Text(look.occasion, fontSize = 11.sp, color = Ink3, maxLines = 1)
                        }
                    }
                }
            }
        } else {
            val logs = vm.history.toList()
            if (logs.isEmpty()) {
                EmptyState(
                    "Nothing worn yet",
                    "Tap “Wear this today” on a look. Your stylist uses this to keep outfits in rotation.",
                    Modifier.padding(horizontal = Gutter),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom)) {
                    itemsIndexed(logs, key = { i, l -> "worn-$i-${l.at}" }) { i, log ->
                        val label = dayLabel(log.at)
                        val pieces = log.itemIds.mapNotNull { vm.item(it) }
                        Column {
                            if (i == 0 || dayLabel(logs[i - 1].at) != label) {
                                Overline(label, Modifier.padding(top = if (i == 0) 0.dp else 18.dp, bottom = 10.dp))
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                                    actions.openLook(
                                        Look(title = log.title, itemIds = log.itemIds, why = "", tips = emptyList(), addOn = ""),
                                    )
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (pieces.isNotEmpty()) {
                                    OutfitBoard(pieces, Modifier.width(72.dp).aspectRatio(0.8f), shape = RField)
                                } else {
                                    Box(Modifier.width(72.dp).aspectRatio(0.8f).clip(RField).background(Surface2))
                                }
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(log.title.ifBlank { "Outfit" }, style = TTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pieces.joinToString(", ") { it.name }.ifBlank { "Pieces no longer in closet" },
                                        style = TSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
