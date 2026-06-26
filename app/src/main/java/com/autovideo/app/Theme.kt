package com.autovideo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AutoBackground = Color(0xFF05040A)
val AutoSurface = Color(0xFF0E0B17)
val AutoSurfaceHigh = Color(0xFF171126)
val AutoPurple = Color(0xFF8B5CF6)
val AutoPink = Color(0xFFEC4899)
val AutoBlue = Color(0xFF38BDF8)
val AutoGreen = Color(0xFF34D399)
val AutoText = Color(0xFFF8FAFC)
val AutoMuted = Color(0xFFA6A0B5)

private val AutoVideoColors = darkColorScheme(
    primary = AutoPurple,
    secondary = AutoPink,
    tertiary = AutoBlue,
    background = AutoBackground,
    surface = AutoSurface,
    surfaceVariant = AutoSurfaceHigh,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AutoText,
    onSurface = AutoText,
    onSurfaceVariant = AutoMuted,
)

@Composable
fun AutoVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutoVideoColors,
        content = content,
    )
}
