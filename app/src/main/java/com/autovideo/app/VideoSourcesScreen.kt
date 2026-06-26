package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoSourcesScreen(
    localCount: Int,
    sourceName: String?,
    onOpenLocal: () -> Unit,
    onOpenUsb: () -> Unit,
    onOpenRutube: () -> Unit,
    onOpenIptv: () -> Unit,
    onOpenLink: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF10091F), Color(0xFF0B1728))))
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Видео", color = AutoText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Локальные и онлайн-источники", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HeadUnitActionButton("Локальные · $localCount", Icons.Rounded.VideoLibrary, onOpenLocal)
            HeadUnitActionButton(sourceName ?: "USB", Icons.Rounded.Usb, onOpenUsb, backgroundColor = AutoCyan)
            HeadUnitActionButton("RUTUBE", Icons.Rounded.Language, onOpenRutube, backgroundColor = AutoBlue)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HeadUnitActionButton("IPTV и M3U", Icons.Rounded.LiveTv, onOpenIptv, backgroundColor = AutoPink)
            HeadUnitActionButton("Открыть ссылку", Icons.Rounded.Link, onOpenLink, backgroundColor = AutoGreen)
        }
    }
}
