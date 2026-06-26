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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoFoldersScreen(
    folders: List<MediaFolder>,
    loading: Boolean,
    playbackStore: PlaybackStore,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
) {
    val continued = remember(folders) {
        folders
            .flatMap(MediaFolder::files)
            .filter(MediaFile::isVideo)
            .filter { playbackStore.position(it) > 0L }
            .sortedByDescending(playbackStore::updatedAt)
            .take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 22.dp),
    ) {
        ScreenHeader(
            title = "Видео",
            subtitle = "${folders.size} папок · ${folders.sumOf(MediaFolder::videoCount)} видео",
        )
        Spacer(Modifier.height(22.dp))

        if (continued.isNotEmpty()) {
            Text(
                "Продолжить просмотр",
                color = AutoText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(continued, key = MediaFile::uriString) { file ->
                    ContinueVideoCard(
                        file = file,
                        progress = playbackStore.progress(file),
                        onClick = { onPlay(file) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text(
            "Папки",
            color = AutoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        FolderGrid(
            folders = folders,
            loading = loading,
            emptyText = "Папки с видео не найдены",
            icon = Icons.Rounded.Folder,
            countText = { "${it.videoCount} видео · ${it.sourceName}" },
            onOpenFolder = onOpenFolder,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun AudioFoldersScreen(
    folders: List<MediaFolder>,
    loading: Boolean,
    onOpenFolder: (MediaFolder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 22.dp),
    ) {
        ScreenHeader(
            title = "Музыка",
            subtitle = "${folders.size} папок · ${folders.sumOf(MediaFolder::audioCount)} аудиофайлов",
        )
        Spacer(Modifier.height(24.dp))
        FolderGrid(
            folders = folders,
            loading = loading,
            emptyText = "Папки с музыкой не найдены",
            icon = Icons.Rounded.LibraryMusic,
            countText = { "${it.audioCount} аудио · ${it.sourceName}" },
            onOpenFolder = onOpenFolder,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, color = AutoText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AutoMuted, fontSize = 15.sp)
        }
        Spacer(Modifier.weight(1f))
        AppClock()
    }
}

@Composable
private fun FolderGrid(
    folders: List<MediaFolder>,
    loading: Boolean,
    emptyText: String,
    icon: ImageVector,
    countText: (MediaFolder) -> String,
    onOpenFolder: (MediaFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading && folders.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
        }

        folders.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = AutoPurple, modifier = Modifier.size(92.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    emptyText,
                    color = AutoText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Проверьте разрешения и подключённые накопители", color = AutoMuted)
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(folders, key = MediaFolder::id) { folder ->
                LibraryFolderCard(
                    folder = folder,
                    icon = icon,
                    count = countText(folder),
                    onClick = { onOpenFolder(folder) },
                )
            }
        }
    }
}

@Composable
private fun LibraryFolderCard(
    folder: MediaFolder,
    icon: ImageVector,
    count: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .background(AutoSurfaceHigh)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AutoPurple,
            modifier = Modifier.size(94.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            folder.name,
            color = AutoText,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            count,
            color = AutoMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContinueVideoCard(file: MediaFile, progress: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(22.dp))
            .background(AutoSurfaceHigh),
    ) {
        VideoThumbnail(
            file = file,
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp),
            showPlayButton = true,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = AutoPurple,
            trackColor = Color(0xFF292832),
        )
        Text(
            file.name,
            color = AutoText,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FolderBrowserScreen(
    folder: MediaFolder,
    onBack: () -> Unit,
    onPlay: (MediaFile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeadUnitIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack,
                size = 68.dp,
                iconSize = 36.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    color = AutoText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${folder.videoCount} видео · ${folder.audioCount} аудио · ${folder.sourceName}",
                    color = AutoMuted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppClock()
        }

        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(folder.files, key = MediaFile::uriString) { file ->
                MediaFileCard(file = file, onClick = { onPlay(file) })
            }
        }
    }
}
