package com.autovideo.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
            .width(244.dp)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xFF080711), Color(0xFF100B21))))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CarNavItem("Главная", Icons.Rounded.Home, RootSection.HOME, selected, onSelect)
            CarNavItem("Видео", Icons.Rounded.VideoLibrary, RootSection.VIDEO, selected, onSelect)
            CarNavItem(
                label = "RUTUBE",
                section = RootSection.RUTUBE,
                selected = selected,
                onSelect = onSelect,
                customIcon = { RutubeLogoIcon(Modifier.size(60.dp)) },
            )
            CarNavItem("Музыка", Icons.Rounded.Audiotrack, RootSection.AUDIO, selected, onSelect)
            CarNavItem("Избранное", Icons.Rounded.Favorite, RootSection.FAVORITES, selected, onSelect)
            CarNavItem("Настройки", Icons.Rounded.Settings, RootSection.SETTINGS, selected, onSelect)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .headUnitPressable(
                    onClick = onResumeLatest,
                    enabled = latestVideo != null,
                    shape = RoundedCornerShape(30.dp),
                )
                .background(
                    if (latestVideo != null) AutoPurple else AutoSurfaceHigh,
                    RoundedCornerShape(30.dp),
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = "Продолжить просмотр",
                tint = if (latestVideo != null) Color.White else AutoMuted,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (latestVideo != null) "Продолжить" else "Нет истории",
                color = if (latestVideo != null) Color.White else AutoMuted,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
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
    val scale by animateFloatAsState(if (active) 1.025f else 1f, label = "navScale")

    Row(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .height(110.dp)
            .headUnitPressable(
                onClick = { onSelect(section) },
                shape = RoundedCornerShape(30.dp),
            )
            .background(
                if (active) Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))
                else Brush.linearGradient(listOf(AutoSurfaceHigh, AutoSurfaceHigh)),
                RoundedCornerShape(30.dp),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                customIcon != null -> customIcon()
                icon != null -> Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) Color.White else AutoMuted,
                    modifier = Modifier.size(60.dp),
                )
            }
        }
        Spacer(Modifier.width(17.dp))
        Text(
            label,
            color = if (active) Color.White else AutoText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
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
