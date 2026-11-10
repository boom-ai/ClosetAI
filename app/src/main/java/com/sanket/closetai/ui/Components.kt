package com.sanket.closetai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanket.closetai.data.Item
import java.io.File

/** "#rrggbb" -> Color, or null when it can't be parsed. */
fun hexColor(h: String): Color? = runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrNull()

/** "just now", "12 min ago", "3h ago", "yesterday", "5 days ago". */
fun ago(time: Long): String {
    if (time <= 0) return "never"
    val min = (System.currentTimeMillis() - time) / 60_000L
    return when {
        min < 2 -> "just now"
        min < 60 -> "$min min ago"
        min < 60 * 24 -> "${min / 60}h ago"
        min < 60 * 48 -> "yesterday"
        else -> "${min / (60 * 24)} days ago"
    }
}

/** Small uppercase label above a section or value. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Ink3) {
    Text(text.uppercase(), style = TOverline, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Full-width 56dp brass pill. The one main action on a screen. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !busy, shape = RPill,
        modifier = modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent, contentColor = OnAccent,
            disabledContainerColor = AccentSoft, disabledContentColor = Ink3,
        ),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
        else Text(text, style = TTitle, color = if (enabled) OnAccent else Ink3)
    }
}

/** 48dp outlined pill for secondary actions. Not full width unless the modifier says so. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RPill, modifier = modifier.height(48.dp),
        border = BorderStroke(1.dp, Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Danger else Ink),
    ) { Text(text, fontSize = 15.sp, color = if (danger) Danger else if (enabled) Ink else Ink3) }
}

/** Accent text button. */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, color: Color = Accent, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = color, fontSize = 14.sp) }
}

/** 48dp round button holding an emoji or short glyph. */
@Composable
fun IconCircle(glyph: String, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) AccentSoft else Surface2, label = "iconCircle")
    Box(
        modifier.size(48.dp).clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Accent else Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 18.sp, color = if (selected) Accent else Ink) }
}

/** 36dp pill chip: outlined when idle, ink-filled when selected. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Ink else Color.Transparent, label = "chipBg")
    val fg by animateColorAsState(if (selected) Bg else Ink2, label = "chipFg")
    Box(
        modifier.height(36.dp).clip(RPill).background(bg)
            .border(1.dp, if (selected) Ink else Hairline, RPill)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 13.sp, maxLines = 1) }
}

/** Surface card with hairline border and 16dp padding. */
@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Surface1, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RCard).background(color).border(1.dp, Hairline, RCard).padding(16.dp),
        content = content,
    )
}

/** A local image file on a paper tile. */
@Composable
fun Photo(
    path: String,
    modifier: Modifier = Modifier,
    shape: Shape = RTile,
    contentScale: ContentScale = ContentScale.Fit,
    background: Color = Paper,
) {
    Box(modifier.clip(shape).background(background)) {
        if (path.isNotBlank()) AsyncImage(
            model = File(path), contentDescription = null, contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Dark outlined text field in the app style. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine,
        modifier = modifier.fillMaxWidth(), shape = RField, textStyle = TBody,
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = Ink3, fontSize = 15.sp) },
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Hairline, cursorColor = Accent,
            focusedTextColor = Ink, unfocusedTextColor = Ink,
            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
        ),
    )
}

/** Centered empty / call-to-action block. Pass actionLabel = "" for no button. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String = "", onAction: () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().clip(RCard).border(1.dp, Hairline, RCard).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = TH2, textAlign = TextAlign.Center)
        Text(body, style = TSmall, textAlign = TextAlign.Center)
        if (actionLabel.isNotBlank()) PrimaryButton(actionLabel, Modifier.padding(top = 8.dp), onClick = onAction)
    }
}

/**
 * The outfit as a flat-lay board on paper: top/layer over bottom on the left, shoes and accessories on the right.
 * Give it a size via [modifier] (e.g. fillMaxWidth().aspectRatio(0.8f)). [onPieceClick] makes each garment tappable.
 */
@Composable
fun OutfitBoard(
    pieces: List<Item>,
    modifier: Modifier = Modifier,
    shape: Shape = RHero,
    onPieceClick: ((Item) -> Unit)? = null,
) {
    val order = listOf("Layers", "Tops", "One-piece", "Bottoms")
    val main = pieces.filter { it.group in order }.sortedBy { order.indexOf(it.group) }.take(3)
    val side = (pieces.filter { it.group == "Footwear" } + pieces.filter { it.group == "Accessories" }).take(3)
    Row(
        modifier.clip(shape).background(Paper).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (main.isNotEmpty()) Column(
            Modifier.weight(0.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            main.forEach { p -> BoardTile(p, Modifier.weight(1f).fillMaxWidth(), onPieceClick) }
        }
        if (side.isNotEmpty()) Column(
            Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            side.forEach { p -> BoardTile(p, Modifier.weight(1f).fillMaxWidth(), onPieceClick) }
        }
    }
}

@Composable
private fun BoardTile(piece: Item, modifier: Modifier, onPieceClick: ((Item) -> Unit)?) {
    val base = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White)
    Box(if (onPieceClick != null) base.clickable { onPieceClick(piece) } else base) {
        AsyncImage(
            model = File(piece.imagePath), contentDescription = piece.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(4.dp),
        )
    }
}

/**
 * App-level actions owned by the root (ClosetApp.kt) and passed to every screen:
 * things that need activity-result launchers, navigation or sheets hosted at the root.
 */
class Actions(
    /** Opens the "Add clothes" bottom sheet (camera / gallery choice). */
    val openAddSheet: () -> Unit,
    /** Launch the camera / gallery directly to add clothes. */
    val addFromCamera: () -> Unit,
    val addFromGallery: () -> Unit,
    /** Launch the camera / gallery to set the full-length profile photo. */
    val profileFromCamera: () -> Unit,
    val profileFromGallery: () -> Unit,
    /** Ask for coarse location (if not yet granted) and refresh the weather. */
    val requestLocation: () -> Unit,
    /** Open the full-screen Settings page. */
    val openSettings: () -> Unit,
    /** Open the look detail sheet for any look (saved, worn, alternate). */
    val openLook: (com.sanket.closetai.data.Look) -> Unit,
    /** Share the look's try-on image through the Android share sheet (no-op with a message if none). */
    val shareLook: (com.sanket.closetai.data.Look) -> Unit,
)

/** Bottom sheet in the app style; content is a padded column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 28.dp),
            content = content,
        )
    }
}

/** Yes / cancel dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title, style = TH2) },
        text = { Text(text, style = TBody, color = Ink2) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (danger) Danger else Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink2) } },
    )
}

/** Password-style field for API keys. */
@Composable
fun SecretField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        modifier = modifier.fillMaxWidth(), shape = RField, textStyle = TBody,
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = Ink3, fontSize = 15.sp) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Hairline, cursorColor = Accent,
            focusedTextColor = Ink, unfocusedTextColor = Ink,
            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
        ),
    )
}
