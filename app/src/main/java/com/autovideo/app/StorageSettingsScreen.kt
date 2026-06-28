package com.autovideo.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StorageSettingsScreen(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    val context = LocalContext.current
    val source = state.sources.firstOrNull()
    val displayName = source.safeDisplayName()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF100A20))))
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Настройки", color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("Накопитель и разрешения", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.size(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeadUnitActionButton("Сменить накопитель", Icons.Rounded.Storage, onAddSource)
            HeadUnitActionButton("Обновить", Icons.Rounded.Refresh, onRefresh, backgroundColor = AutoBlue)
            HeadUnitActionButton(
                "Разрешения",
                Icons.Rounded.AdminPanelSettings,
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                },
                backgroundColor = Color(0xFF3E295F),
            )
            if (source != null && source.uriString != INTERNAL_SOURCE_URI) {
                HeadUnitActionButton(
                    "Внутренняя память",
                    Icons.Rounded.PhoneAndroid,
                    onClick = { onRemoveSource(source.uriString) },
                    backgroundColor = Color(0xFF1B4560),
                )
            }
        }
        Spacer(Modifier.size(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoSurfaceHigh, RoundedCornerShape(22.dp))
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (source?.uriString == INTERNAL_SOURCE_URI) Icons.Rounded.PhoneAndroid else Icons.Rounded.Usb,
                contentDescription = null,
                tint = if (source?.connected == true) AutoGreen else AutoMuted,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    displayName,
                    color = AutoText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    when {
                        state.loading -> "Чтение медиатеки…"
                        state.error != null -> state.error
                        else -> "${state.videoFiles.size} видео · ${state.audioFiles.size} аудио"
                    },
                    color = if (state.error == null) AutoMuted else AutoRed,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
