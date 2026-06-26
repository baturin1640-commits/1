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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
) {
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
                Text(
                    "Главная",
                    color = AutoText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.sources.any(RemovableSource::connected)) {
                        "Папки с видео во внутренней памяти и на носителях"
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
                contentDescription = "Обновить медиатеку",
                onClick = onRefresh,
                size = 62.dp,
                iconSize = 32.dp,
                backgroundColor = AutoSurfaceHigh,
                tint = AutoText,
            )
            Spacer(Modifier.width(12.dp))
            AppClock()
        }

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF8A9A), fontSize = 14.sp)
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Папки с видео",
                color = AutoText,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${state.videoFolders.size} папок · ${state.videoFiles.size} видео",
                color = AutoMuted,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(14.dp))

        when {
            state.loading && state.videoFolders.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
            }

            state.sources.isEmpty() -> NoMediaSource(onAddSource)

            state.videoFolders.isEmpty() -> Box(
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
                    Text(
                        "Папки с видео не найдены",
                        color = AutoText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Проверьте разрешения, память и подключённый носитель",
                        color = AutoMuted,
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.videoFolders, key = MediaFolder::id) { folder ->
                    HomeFolderCard(folder = folder, onClick = { onOpenFolder(folder) })
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: RemovableSource) {
    Row(
        modifier = Modifier
            .background(AutoSurfaceHigh, RoundedCornerShape(50))
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
private fun HomeFolderCard(folder: MediaFolder, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .background(AutoSurfaceHigh)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Folder,
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
            Text(
                "Медиатека пока пуста",
                color = AutoText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Разрешите доступ к памяти или выберите папку/накопитель",
                color = AutoMuted,
            )
            Spacer(Modifier.height(22.dp))
            HeadUnitActionButton(
                text = "Выбрать папку или носитель",
                icon = Icons.Rounded.Add,
                onClick = onAddSource,
            )
        }
    }
}
