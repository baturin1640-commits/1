package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: LibraryUiState,
    playbackStore: PlaybackStore,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
) {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(30_000L)
        }
    }

    val continued = remember(state.folders) {
        state.folders
            .flatMap(MediaFolder::files)
            .filter { playbackStore.progress(it) > 0.01f }
            .sortedByDescending(playbackStore::updatedAt)
            .take(6)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Главная", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (state.sources.any(RemovableSource::connected)) {
                        "Папки с подключённых носителей"
                    } else {
                        "Подключите флешку или внешний диск"
                    },
                    color = AutoMuted,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            state.sources.filter(RemovableSource::connected).take(3).forEach { source ->
                SourceChip(source.name)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = AutoMuted)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Rounded.Search, contentDescription = "Поиск", tint = AutoMuted)
            }
            Text(time, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(state.error, color = Color(0xFFFF8A9A), fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AutoPurple)
            }
            return@Column
        }

        if (state.sources.isEmpty()) {
            NoMediaSource(onAddSource)
            return@Column
        }

        if (continued.isNotEmpty()) {
            SectionHeader("Продолжить просмотр", "${continued.size} файлов")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(continued, key = { it.uriString }) { file ->
                    ContinueCard(
                        file = file,
                        progress = playbackStore.progress(file),
                        onClick = { onPlay(file) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        SectionHeader(
            title = "Папки с видео",
            subtitle = "${state.videoFolders.size} папок · ${state.videoFiles.size} видео",
        )
        Spacer(Modifier.height(10.dp))

        if (state.videoFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AutoSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = AutoPurple, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Папки с видео не найдены", fontWeight = FontWeight.SemiBold)
                    Text("Проверьте носитель или обновите медиатеку", color = AutoMuted)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(245.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.videoFolders, key = MediaFolder::id) { folder ->
                    FolderCard(folder = folder, onClick = { onOpenFolder(folder) })
                }
            }
        }
    }
}

@Composable
private fun SourceChip(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF171126))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Usb, contentDescription = null, tint = AutoGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, color = AutoMuted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(subtitle, color = AutoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ContinueCard(file: MediaFile, progress: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AutoSurfaceHigh)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF351B68), Color(0xFF141027), Color(0xFF0C2440)),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC05040A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Воспроизвести", tint = Color.White)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = AutoPurple,
            trackColor = Color(0xFF292238),
        )
        Text(
            file.name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderCard(folder: MediaFolder, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2B1654), Color(0xFF171126), Color(0xFF0D1827)),
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0x558B5CF6)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = AutoPurple)
            }
            Spacer(Modifier.weight(1f))
            Text(folder.sourceName, color = AutoMuted, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text(
            folder.name,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${folder.videoCount} видео" + if (folder.audioCount > 0) " · ${folder.audioCount} аудио" else "",
            color = AutoMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NoMediaSource(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Usb, contentDescription = null, tint = AutoPurple, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Подключите съёмный носитель", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Выберите корневую папку флешки или внешнего диска", color = AutoMuted)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAddSource,
                colors = ButtonDefaults.buttonColors(containerColor = AutoPurple),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать носитель")
            }
        }
    }
}

private fun currentTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
