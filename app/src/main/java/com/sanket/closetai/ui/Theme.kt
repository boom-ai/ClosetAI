package com.sanket.closetai.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- colour tokens (dark, warm editorial) ----
val Bg = Color(0xFF0E0D0C)
val Surface1 = Color(0xFF171513)
val Surface2 = Color(0xFF211E1B)
val Hairline = Color(0xFF2E2A26)
val Paper = Color(0xFFEDE6DA)
val Ink = Color(0xFFF5F0E8)
val Ink2 = Color(0xFFB8AEA1)
val Ink3 = Color(0xFF7D746A)
val Accent = Color(0xFFD9A45B)
val OnAccent = Color(0xFF17110A)
val AccentSoft = Color(0xFF2B2114)
val Success = Color(0xFF7FB58A)
val Warn = Color(0xFFE0B15A)
val Danger = Color(0xFFE5766A)
val DangerSoft = Color(0xFF2C1815)

// ---- shapes ----
val RHero = RoundedCornerShape(28.dp)
val RCard = RoundedCornerShape(20.dp)
val RTile = RoundedCornerShape(16.dp)
val RField = RoundedCornerShape(12.dp)
val RPill = RoundedCornerShape(50)

/** Screen side gutter. */
val Gutter = 20.dp

val Serif = FontFamily.Serif

// ---- type ----
val TDisplay = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 48.sp, color = Ink)
val TH1 = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, color = Ink)
val TH2 = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, color = Ink)
val TTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = Ink)
val TBody = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
val TSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Ink2)
val TOverline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp, color = Ink3)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Accent,
    onSecondary = OnAccent,
    background = Bg,
    onBackground = Ink,
    surface = Surface1,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Ink2,
    surfaceContainer = Surface1,
    surfaceContainerLow = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = Accent,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Danger,
    inverseSurface = Ink,
    inverseOnSurface = Bg,
)

private val type = Typography(
    headlineMedium = TH1,
    titleLarge = TH2,
    titleMedium = TTitle,
    bodyLarge = TBody,
    bodyMedium = TBody,
    bodySmall = TSmall,
)

@Composable
fun ClosetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
