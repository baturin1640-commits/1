package com.autovideo.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .padding(horizontal = 30.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Главная", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (state.sources.any(RemovableSource::connected)) {
                        "Видео во внутренней памяти и на подключённых носителях"
                    } else {
                        "Разрешите доступ к памяти или подключите накопитель"
                    },
                    color = AutoMuted,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            state.sources.filter(RemovableSource::connected).take(2).forEach { source ->
                SourceChip(source)
                Spacer(Modifier.width(10.dp))
            }
            HeadUnitIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Обновить",
                onClick = onRefresh,
                size = 62.dp,
                iconSize = 32.dp,
                backgroundColor = Color(0xFF171126),
                tint = AutoMuted,
            )
            Spacer(Modifier.width(10.dp))
            HeadUnitIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "Поиск",
                onClick = { },
                size = 62.dp,
                iconSize = 32.dp,
                backgroundColor = Color(0xFF171126),
                tint = AutoMuted,
            )
            Spacer(Modifier.width(16.dp))
            Text(time, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF8A9A), fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
            }
            return@Column
        }

        if (state.sources.isEmpty()) {
            NoMediaSource(onAddSource)
            return@Column
        }

        if (continued.isNotEmpty()) {
            SectionHeader("Продолжить просмотр", "${continued.size} файлов")
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(continued, key = MediaFile::uriString) { file ->
                    ContinueCard(
                        file = file,
                        progress = playbackStore.progress(file),
                        onClick = { onPlay(file) },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        SectionHeader(
            title = "Папки с видео",
            subtitle = "${state.videoFolders.size} папок · ${state.videoFiles.size} видео",
        )
        Spacer(Modifier.height(12.dp))

        if (state.videoFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(AutoSurfaceHigh, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = AutoPurple,
                        modifier = Modifier.size(88.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Папки с видео не найдены", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("Проверьте разрешения, память и подключённый носитель", color = AutoMuted)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.videoFolders, key = MediaFolder::id) { folder ->
                    FolderCard(folder = folder, onClick = { onOpenFolder(folder) })
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: RemovableSource) {
    Row(
        modifier = Modifier
            .background(Color(0xFF171126), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (
                source.uriString == INTERNAL_SOURCE_URI ||
                source.name.equals("Внутренняя память", ignoreCase = true)
            ) {
                Icons.Rounded.PhoneAndroid
            } else {
                Icons.Rounded.Usb
            },
            contentDescription = null,
            tint = AutoGreen,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(source.name, color = AutoMuted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(subtitle, color = AutoMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ContinueCard(file: MediaFile, progress: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(290.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(22.dp))
            .background(AutoSurfaceHigh),
    ) {
        VideoThumbnail(
            file = file,
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp),
            showPlayButton = true,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = AutoPurple,
            trackColor = Color(0xFF292238),
        )
        Text(
            file.name,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            fontSize = 15.sp,
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
            .height(205.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF211635), Color(0xFF11101A), Color(0xFF151123))
                )
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(106.dp)
                .background(Color(0x332C1C48), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(80.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            folder.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${folder.videoCount} видео · ${folder.sourceName}",
            color = AutoMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoMediaSource(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(84.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text("Медиатека пока пуста", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Разрешите доступ к памяти или выберите папку/накопитель", color = AutoMuted)
            Spacer(Modifier.height(22.dp))
            HeadUnitActionButton(
                text = "Выбрать папку или носитель",
                icon = Icons.Rounded.Add,
                onClick = onAddSource,
            )
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
