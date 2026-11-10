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
fun ClosetApp(vm: AppViewModel) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var openLook by remember { mutableStateOf<Look?>(null) }

    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snack.showSnackbar(msg)
            vm.message = null
        }
    }

    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
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
    val locationPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean ->
        vm.refreshWeather(true)
    }

    val actions = Actions(
        openAddSheet = { showAdd = true },
        addFromCamera = { runCatching { camera.launch(vm.newCaptureUri(false)) } },
        addFromGallery = { runCatching { gallery.launch(imagesOnly) } },
        profileFromCamera = { runCatching { camera.launch(vm.newCaptureUri(true)) } },
        profileFromGallery = { runCatching { profilePicker.launch(imagesOnly) } },
        requestLocation = {
            if (vm.hasLocationPermission()) vm.refreshWeather(true)
            else runCatching { locationPerm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
        },
        openSettings = { showSettings = true },
        openLook = { look -> openLook = look },
        shareLook = { look ->
            val uri = vm.shareUri(look)
            if (uri == null) {
                vm.message = "Generate a try-on first, then you can share it"
            } else {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share look"))
                }
            }
        },
    )

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!vm.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else if (!vm.onboarded) {
            Onboarding(vm, actions)
        } else if (showSettings) {
            SettingsPage(vm) { showSettings = false }
        } else {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = Hairline)
                        NavigationBar(containerColor = Bg, tonalElevation = 0.dp) {
                            val colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = AccentSoft,
                                unselectedIconColor = Ink3, unselectedTextColor = Ink3,
                            )
                            NavigationBarItem(
                                selected = vm.tab == Tab.TODAY, onClick = { vm.tab = Tab.TODAY },
                                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                label = { Text("Today") }, colors = colors,
                            )
                            NavigationBarItem(
                                selected = vm.tab == Tab.CLOSET, onClick = { vm.tab = Tab.CLOSET },
                                icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                                label = { Text("Closet") }, colors = colors,
                            )
                            NavigationBarItem(
                                selected = vm.tab == Tab.LOOKS, onClick = { vm.tab = Tab.LOOKS },
                                icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                                label = { Text("Looks") }, colors = colors,
                            )
                            NavigationBarItem(
                                selected = vm.tab == Tab.ME, onClick = { vm.tab = Tab.ME },
                                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                label = { Text("Me") }, colors = colors,
                            )
                        }
                    }
                },
            ) { pad ->
                when (vm.tab) {
                    Tab.TODAY -> TodayScreen(vm, pad, actions)
                    Tab.CLOSET -> ClosetScreen(vm, pad, actions)
                    Tab.LOOKS -> LooksScreen(vm, pad, actions)
                    Tab.ME -> MeScreen(vm, pad, actions)
                }
            }
        }
        SnackbarHost(
            hostState = snack,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }

    if (showAdd) {
        AppSheet(onDismiss = { showAdd = false }) {
            Text("Add clothes", style = TH2)
            Text(
                "One item per photo, laid flat or on a hanger against a plain background. " +
                    "The stylist reads the type, colour, fabric, warmth and formality by itself.",
                style = TSmall, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            PrimaryButton("Choose from gallery") { showAdd = false; actions.addFromGallery() }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Take a photo", Modifier.fillMaxWidth()) { showAdd = false; actions.addFromCamera() }
            Text("Tip: pick up to 30 photos at once from the gallery.", style = TSmall, color = Ink3,
                modifier = Modifier.padding(top = 12.dp))
        }
    }

    val detail = openLook
    if (detail != null) {
        LookDetailSheet(vm, detail, actions) { openLook = null }
    }
}
