package com.autovideo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AutoBackground = Color(0xFF07070A)
val AutoSurface = Color(0xFF101015)
val AutoSurfaceHigh = Color(0xFF19191F)
val AutoPurple = Color(0xFF8B5CF6)
val AutoPink = Color(0xFFEC4899)
val AutoBlue = Color(0xFF38BDF8)
val AutoGreen = Color(0xFF34D399)
val AutoText = Color(0xFFF8FAFC)
val AutoMuted = Color(0xFFB1ADBA)

private val AutoVideoColors = darkColorScheme(
    primary = AutoPurple,
    secondary = AutoPink,
    tertiary = AutoBlue,
    background = AutoBackground,
    surface = AutoSurface,
    surfaceVariant = AutoSurfaceHigh,
    primaryContainer = Color(0xFF4A2B76),
    secondaryContainer = Color(0xFF5A2342),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onPrimaryContainer = Color.White,
    onSecondaryContainer = Color.White,
    onTertiaryContainer = Color.White,
    onBackground = AutoText,
    onSurface = AutoText,
    onSurfaceVariant = AutoText,
    outline = Color(0xFF4A4752),
)

@Composable
fun AutoVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutoVideoColors,
        content = content,
    )
}
