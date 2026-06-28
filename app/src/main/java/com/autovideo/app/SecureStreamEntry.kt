package com.autovideo.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SecureStreamEntry(
    onBack: () -> Unit,
    onOpen: (OnlineStream) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeadUnitIconButton(Icons.Rounded.ArrowBack, "Назад", onBack)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Открыть ссылку", color = AutoText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Только защищённые адреса", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название") },
            singleLine = true,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.take(4_096); error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ссылка") },
            singleLine = true,
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
        )
        Spacer(Modifier.height(18.dp))
        HeadUnitActionButton(
            "Воспроизвести",
            Icons.Rounded.PlayArrow,
            onClick = {
                val value = OnlineUrlValidator.normalizeSecureUrl(url)
                if (value == null) error = "Введите корректный адрес"
                else onOpen(OnlineStream(value, title.ifBlank { "Онлайн-видео" }))
            },
        )
    }
}
