package com.autovideo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AutoBackground = Color(0xFF060611)
val AutoSurface = Color(0xFF0E0D20)
val AutoSurfaceHigh = Color(0xFF17152B)
val AutoSurfaceBright = Color(0xFF221A42)
val AutoPurple = Color(0xFF8B4DFF)
val AutoPink = Color(0xFFFF3CB4)
val AutoBlue = Color(0xFF5965FF)
val AutoCyan = Color(0xFF2CC8F5)
val AutoRed = Color(0xFFFF4F68)
val AutoGreen = Color(0xFF43E0C0)
val AutoText = Color(0xFFF9F8FF)
val AutoMuted = Color(0xFFB5AFC7)

private val AutoVideoColors = darkColorScheme(
    primary = AutoPurple,
    secondary = AutoPink,
    tertiary = AutoCyan,
    background = AutoBackground,
    surface = AutoSurface,
    surfaceVariant = AutoSurfaceHigh,
    primaryContainer = Color(0xFF422474),
    secondaryContainer = Color(0xFF65254D),
    tertiaryContainer = Color(0xFF173F57),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onPrimaryContainer = Color.White,
    onSecondaryContainer = Color.White,
    onTertiaryContainer = Color.White,
    onBackground = AutoText,
    onSurface = AutoText,
    onSurfaceVariant = AutoText,
    outline = Color(0xFF51486D),
)

@Composable
fun AutoVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutoVideoColors,
        content = content,
    )
}
