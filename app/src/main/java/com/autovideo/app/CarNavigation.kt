package com.autovideo.app

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CarSideNavigation(
    selected: AppSection,
    latestVideo: MediaFile?,
    onSelect: (AppSection) -> Unit,
    onResumeLatest: () -> Unit,
) {
    Column(
        modifier = Modifier.width(140.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xFF080711), Color(0xFF100B21))))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("V", color = AutoText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.size(8.dp))
        CarNavItem("Главная", Icons.Rounded.Home, AppSection.HOME, selected, onSelect)
        CarNavItem("Видео", Icons.Rounded.VideoLibrary, AppSection.VIDEO, selected, onSelect)
        CarNavItem("Музыка", Icons.Rounded.Audiotrack, AppSection.AUDIO, selected, onSelect)
        CarNavItem("Настройки", Icons.Rounded.Settings, AppSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))
        HeadUnitIconButton(
            icon = Icons.Rounded.PlayCircle,
            contentDescription = "Продолжить просмотр",
            onClick = onResumeLatest,
            enabled = latestVideo != null,
            size = 82.dp,
            iconSize = 48.dp,
            backgroundColor = if (latestVideo != null) AutoPurple else AutoSurfaceHigh,
        )
        Text(
            if (latestVideo != null) "Продолжить" else "Нет истории",
            color = AutoMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CarNavItem(
    label: String,
    icon: ImageVector,
    section: AppSection,
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
) {
    val active = section == selected
    val scale by animateFloatAsState(if (active) 1.035f else 1f, label = "navScale")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.scale(scale).size(82.dp)
                .headUnitPressable({ onSelect(section) }, shape = RoundedCornerShape(24.dp))
                .background(
                    if (active) Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))
                    else Brush.linearGradient(listOf(AutoSurfaceHigh, AutoSurfaceHigh)),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (active) Color.White else AutoMuted, modifier = Modifier.size(45.dp))
        }
        Text(label, color = if (active) AutoText else AutoMuted, fontSize = 12.sp)
    }
}
