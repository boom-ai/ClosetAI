package com.sanket.closetai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF14110F)
val Panel = Color(0xFF1D1916)
val CardC = Color(0xFF25201C)
val Line = Color(0xFF352E28)
val Ink = Color(0xFFF4EDE4)
val Mute = Color(0xFFA89D90)
val Gold = Color(0xFFE8B86B)
val Copper = Color(0xFFC9784A)
val Paper = Color(0xFFECE6DD)
val Bad = Color(0xFFE07A6A)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A120B),
    secondary = Copper,
    onSecondary = Color(0xFF1A120B),
    background = Bg,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = CardC,
    onSurfaceVariant = Mute,
    surfaceContainer = Panel,
    surfaceContainerLow = Panel,
    surfaceContainerHigh = CardC,
    secondaryContainer = Ink,
    onSecondaryContainer = Bg,
    outline = Line,
    outlineVariant = Line,
    error = Bad,
)

val Serif = FontFamily.Serif

private val type = Typography(
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp),
)

@Composable
fun ClosetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
