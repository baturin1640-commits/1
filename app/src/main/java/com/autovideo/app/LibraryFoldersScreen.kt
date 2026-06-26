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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
            .padding(horizontal = 30.dp, vertical = 22.dp),
    ) {
        Text("Видео", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "${folders.size} папок · ${folders.sumOf(MediaFolder::videoCount)} видео",
            color = AutoMuted,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(24.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
            }

            folders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = AutoPurple,
                        modifier = Modifier.size(92.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Папки с видео не найдены", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Проверьте разрешения и подключённые накопители", color = AutoMuted)
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF211635), Color(0xFF11101A), Color(0xFF151123))
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(118.dp)
                .background(Color(0x332C1C48), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(88.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            folder.name,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${folder.videoCount} видео · ${folder.sourceName}",
            color = AutoMuted,
            fontSize = 13.sp,
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
