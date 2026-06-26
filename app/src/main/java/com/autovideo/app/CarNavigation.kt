package com.autovideo.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RootSection {
    HOME,
    VIDEO,
    RUTUBE,
    AUDIO,
    FAVORITES,
    SETTINGS,
}

@Composable
fun CarSideNavigation(
    selected: RootSection,
    latestVideo: MediaFile?,
    onSelect: (RootSection) -> Unit,
    onResumeLatest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xFF080711), Color(0xFF100B21))))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("V", color = AutoText, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.size(3.dp))
        CarNavItem("Главная", Icons.Rounded.Home, RootSection.HOME, selected, onSelect)
        CarNavItem("Видео", Icons.Rounded.VideoLibrary, RootSection.VIDEO, selected, onSelect)
        CarNavItem(
            label = "RUTUBE",
            section = RootSection.RUTUBE,
            selected = selected,
            onSelect = onSelect,
            customIcon = { RutubeLogoIcon(Modifier.size(37.dp)) },
        )
        CarNavItem("Музыка", Icons.Rounded.Audiotrack, RootSection.AUDIO, selected, onSelect)
        CarNavItem("Избранное", Icons.Rounded.Favorite, RootSection.FAVORITES, selected, onSelect)
        CarNavItem("Настройки", Icons.Rounded.Settings, RootSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))
        HeadUnitIconButton(
            icon = Icons.Rounded.PlayCircle,
            contentDescription = "Продолжить просмотр",
            onClick = onResumeLatest,
            enabled = latestVideo != null,
            size = 64.dp,
            iconSize = 38.dp,
            backgroundColor = if (latestVideo != null) AutoPurple else AutoSurfaceHigh,
        )
        Text(
            if (latestVideo != null) "Продолжить" else "Нет истории",
            color = AutoMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CarNavItem(
    label: String,
    icon: ImageVector? = null,
    section: RootSection,
    selected: RootSection,
    onSelect: (RootSection) -> Unit,
    customIcon: (@Composable () -> Unit)? = null,
) {
    val active = section == selected
    val scale by animateFloatAsState(if (active) 1.035f else 1f, label = "navScale")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(66.dp)
                .headUnitPressable({ onSelect(section) }, shape = RoundedCornerShape(20.dp))
                .background(
                    if (active) Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))
                    else Brush.linearGradient(listOf(AutoSurfaceHigh, AutoSurfaceHigh)),
                    RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                customIcon != null -> customIcon()
                icon != null -> Icon(
                    icon,
                    label,
                    tint = if (active) Color.White else AutoMuted,
                    modifier = Modifier.size(37.dp),
                )
            }
        }
        Text(
            label,
            color = if (active) AutoText else AutoMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun RutubeLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.22f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFF2DAA), Color(0xFF8C4DFF))),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
        val inset = size.minDimension * 0.18f
        drawRoundRect(
            color = Color(0xFF0B0912),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(radius * 0.62f, radius * 0.62f),
        )
        val path = Path().apply {
            moveTo(size.width * 0.43f, size.height * 0.34f)
            lineTo(size.width * 0.70f, size.height * 0.50f)
            lineTo(size.width * 0.43f, size.height * 0.66f)
            close()
        }
        drawPath(path, Color.White)
    }
}
