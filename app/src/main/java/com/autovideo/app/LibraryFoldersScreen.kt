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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoFoldersScreen(
    folders: List<MediaFolder>,
    loading: Boolean,
    onOpenFolder: (MediaFolder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Text("Видео", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "${folders.size} папок · ${folders.sumOf(MediaFolder::videoCount)} видео",
            color = AutoMuted,
        )
        Spacer(Modifier.height(22.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AutoPurple)
            }

            folders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = AutoPurple,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Папки с видео не найдены", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Проверьте разрешения и подключённые накопители", color = AutoMuted)
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(folders, key = MediaFolder::id) { folder ->
                    VideoFolderCard(folder = folder, onClick = { onOpenFolder(folder) })
                }
            }
        }
    }
}

@Composable
private fun VideoFolderCard(folder: MediaFolder, onClick: () -> Unit) {
    val preview = folder.files.firstOrNull(MediaFile::isVideo)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AutoSurfaceHigh)
            .clickable(onClick = onClick),
    ) {
        if (preview != null) {
            VideoThumbnail(
                file = preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp),
                showPlayButton = false,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(Color(0xFF171126)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = AutoPurple)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${folder.videoCount} видео · ${folder.sourceName}",
                    color = AutoMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${folder.videoCount} видео · ${folder.audioCount} аудио · ${folder.sourceName}",
                    color = AutoMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(folder.files, key = MediaFile::uriString) { file ->
                MediaFileCard(file = file, onClick = { onPlay(file) })
            }
        }
    }
}
