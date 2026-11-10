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
fun Onboarding(vm: AppViewModel, actions: Actions) {
    var step by remember { mutableIntStateOf(0) }
    var key by remember { mutableStateOf(vm.aiSettings.apiKey) }
    var height by remember { mutableStateOf(vm.profile.height) }
    var city by remember { mutableStateOf(vm.profile.city) }
    val uri = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 32.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..2) {
                    Box(Modifier.width(if (i == step) 28.dp else 10.dp).height(4.dp).clip(RPill)
                        .background(if (i <= step) Accent else Surface2))
                }
            }
            TextAction("Skip", color = Ink2) { vm.finishOnboarding() }
        }

        if (step == 0) {
            Overline("Closet AI", Modifier.padding(top = 40.dp), color = Accent)
            Text("Meet your stylist", style = TDisplay.copy(fontSize = 38.sp, lineHeight = 44.sp), modifier = Modifier.padding(top = 8.dp))
            listOf(
                "Photograph your clothes once. They sort themselves.",
                "Every morning it checks the weather where you are.",
                "Then it tells you exactly what to wear, from what you own.",
            ).forEach { line ->
                Row(Modifier.padding(top = 14.dp)) {
                    Text("—", color = Accent, modifier = Modifier.width(28.dp))
                    Text(line, style = TBody, color = Ink2)
                }
            }
            Overline("Your Gemini key", Modifier.padding(top = 36.dp, bottom = 6.dp))
            SecretField(key, { key = it }, placeholder = "Paste your key")
            TextAction("Get a free Gemini key  →") { runCatching { uri.openUri(Provider.GEMINI.keyUrl) } }
            Text("The key stays on this phone. It powers clothing detection, styling and try-on.", style = TSmall, color = Ink3)
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Continue") {
                if (key.isNotBlank()) {
                    vm.saveSettings(vm.aiSettings.copy(provider = Provider.GEMINI, apiKey = key.trim(), model = Provider.GEMINI.defaultModel))
                }
                step = 1
            }
            TextAction("I'll add it later", Modifier.fillMaxWidth(), color = Ink2) { step = 1 }
        } else if (step == 1) {
            Overline("Step 2 of 3", Modifier.padding(top = 40.dp), color = Accent)
            Text("You", style = TDisplay.copy(fontSize = 38.sp, lineHeight = 44.sp), modifier = Modifier.padding(top = 8.dp))
            Text("One full-length photo lets the stylist read your build, proportions and colouring. Daylight, phone at chest height, fitted clothes.",
                style = TBody, color = Ink2, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(120.dp).aspectRatio(0.75f).clip(RTile).background(Surface2), contentAlignment = Alignment.Center) {
                    if (vm.profile.photoPath.isNotBlank()) {
                        Photo(vm.profile.photoPath, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, background = Surface2)
                    } else {
                        Text("No photo yet", style = TSmall, color = Ink3)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Take photo", Modifier.fillMaxWidth()) { actions.profileFromCamera() }
                    SecondaryButton("Choose photo", Modifier.fillMaxWidth()) { actions.profileFromGallery() }
                    if (vm.analysing) Text("Reading your look…", style = TSmall, color = Accent)
                }
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Overline("Height", Modifier.padding(bottom = 6.dp))
                    AppTextField(height, { height = it }, placeholder = "5'7\"")
                }
                Column(Modifier.weight(1f)) {
                    Overline("City", Modifier.padding(bottom = 6.dp))
                    AppTextField(city, { city = it }, placeholder = "Jalandhar")
                }
            }
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                if (vm.hasLocationPermission()) "Location is on for live weather  ✓" else "Use my location for live weather",
                Modifier.fillMaxWidth(),
            ) { actions.requestLocation() }
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Continue") {
                val cur = vm.profile
                if (cur.height != height.trim() || cur.city != city.trim()) {
                    vm.updateProfile(cur.copy(height = height.trim(), city = city.trim()))
                }
                step = 2
            }
        } else {
            val missing = vm.missingBasics()
            Overline("Step 3 of 3", Modifier.padding(top = 40.dp), color = Accent)
            Text("Your first clothes", style = TDisplay.copy(fontSize = 38.sp, lineHeight = 44.sp), modifier = Modifier.padding(top = 8.dp))
            Text("One piece per photo, laid flat or on a hanger against a plain background. Start with a few favourites; you can add the rest any time.",
                style = TBody, color = Ink2, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(20.dp))
            SecondaryButton("Choose from gallery (up to 30)", Modifier.fillMaxWidth()) { actions.addFromGallery() }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Take a photo", Modifier.fillMaxWidth()) { actions.addFromCamera() }
            Spacer(Modifier.height(20.dp))
            listOf("Top", "Bottom", "Shoes").forEach { b ->
                val have = b !in missing
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (have) "✓" else "○", color = if (have) Success else Ink3, fontSize = 16.sp,
                        modifier = Modifier.width(28.dp))
                    Text(b, style = TBody, color = if (have) Ink else Ink2)
                }
            }
            Text(
                if (vm.detectTotal > 0) "Detecting ${vm.detectDone} of ${vm.detectTotal}…"
                else "${vm.items.size} piece${if (vm.items.size == 1) "" else "s"} in your closet",
                style = TSmall, color = if (vm.detectTotal > 0) Accent else Ink3, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            PrimaryButton(if (vm.items.isEmpty()) "Finish" else "Finish — style me") { vm.finishOnboarding() }
        }
    }
}
