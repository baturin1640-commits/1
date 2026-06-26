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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardHomeScreen(
    state: LibraryUiState,
    favorites: FavoritesState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    val source = state.sources.firstOrNull()
    val favoriteFiles = (state.videoFiles + state.audioFiles)
        .distinctBy(MediaFile::uriString)
        .filter { it.uriString in favorites.fileUris }
        .take(12)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AutoBackground, Color(0xFF0C1023), Color(0xFF120A22))
                )
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Главная", color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (source?.uriString == INTERNAL_SOURCE_URI) {
                            Icons.Rounded.PhoneAndroid
                        } else {
                            Icons.Rounded.Usb
                        },
                        contentDescription = null,
                        tint = if (source?.connected == true) AutoGreen else AutoMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        source?.name ?: "Накопитель не выбран",
                        color = AutoMuted,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            HeadUnitIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Обновить медиатеку",
                onClick = onRefresh,
                size = 64.dp,
                iconSize = 34.dp,
                backgroundColor = AutoSurfaceBright,
            )
            Spacer(Modifier.width(10.dp))
            AppClock(compact = true)
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.loading && state.folders.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
            }

            state.sources.isEmpty() -> EmptyDashboardSource(onAddSource)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    DashboardSummaryCard(
                        videoCount = state.videoFiles.size,
                        audioCount = state.audioFiles.size,
                        connected = source?.connected == true,
                    )
                }

                item {
                    DashboardSectionHeader(
                        title = "Видео",
                        subtitle = "${state.videoFiles.size} файлов · ${state.videoRootFolders.size} папок",
                        icon = Icons.Rounded.VideoLibrary,
                        accent = AutoGreen,
                    )
                }

                item {
                    if (state.videoRootFolders.isEmpty()) {
                        DashboardEmptySection("Папки с видео не найдены", AutoGreen)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.videoRootFolders, key = MediaFolder::id) { folder ->
                                DashboardFolderCard(
                                    folder = folder,
                                    count = "${state.videoCount(folder)} видео",
                                    accent = AutoGreen,
                                    favorite = folder.id in favorites.folderIds,
                                    onClick = { onOpenFolder(folder) },
                                    onToggleFavorite = { onToggleFolderFavorite(folder) },
                                )
                            }
                        }
                    }
                }

                item {
                    DashboardSectionHeader(
                        title = "Музыка",
                        subtitle = "${state.audioFiles.size} треков · ${state.audioRootFolders.size} папок",
                        icon = Icons.Rounded.Audiotrack,
                        accent = AutoPurple,
                    )
                }

                item {
                    if (state.audioRootFolders.isEmpty()) {
                        DashboardEmptySection("Папки с музыкой не найдены", AutoPurple)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.audioRootFolders, key = MediaFolder::id) { folder ->
                                DashboardFolderCard(
                                    folder = folder,
                                    count = "${state.audioCount(folder)} треков",
                                    accent = AutoPurple,
                                    favorite = folder.id in favorites.folderIds,
                                    onClick = { onOpenFolder(folder) },
                                    onToggleFavorite = { onToggleFolderFavorite(folder) },
                                )
                            }
                        }
                    }
                }

                if (favoriteFiles.isNotEmpty()) {
                    item {
                        DashboardSectionHeader(
                            title = "Избранное",
                            subtitle = "Быстрый доступ к сохранённым файлам",
                            icon = Icons.Rounded.PlayArrow,
                            accent = AutoPink,
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(favoriteFiles, key = MediaFile::uriString) { file ->
                                MediaAssetTile(
                                    file = file,
                                    favorite = true,
                                    onClick = { onPlay(file) },
                                    onToggleFavorite = { onToggleFileFavorite(file) },
                                )
                            }
                        }
                    }
                }

                state.error?.let { message ->
                    item {
                        Text(message, color = Color(0xFFFF9AAA), fontSize = 14.sp)
                    }
                }

                item { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

@Composable
private fun DashboardSummaryCard(
    videoCount: Int,
    audioCount: Int,
    connected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF15253A), Color(0xFF21133E), Color(0xFF26102F))
                ),
                RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (connected) "Медиатека готова" else "Накопитель недоступен",
                color = if (connected) AutoGreen else AutoRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Видео $videoCount · Музыка $audioCount",
                color = AutoMuted,
                fontSize = 13.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(AutoGreen.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Usb,
                contentDescription = null,
                tint = AutoGreen,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(accent.copy(alpha = 0.17f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = AutoText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AutoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DashboardFolderCard(
    folder: MediaFolder,
    count: String,
    accent: Color,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(138.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(22.dp), sound = UiSound.FOLDER)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.12f), AutoSurfaceHigh)
                ),
                RoundedCornerShape(22.dp),
            )
            .padding(15.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(50.dp),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                folder.name,
                color = AutoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(count, color = AutoMuted, fontSize = 12.sp)
        }
        FavoriteButton(
            favorite = favorite,
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun DashboardEmptySection(message: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AutoSurfaceHigh, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, color = AutoMuted, fontSize = 15.sp)
    }
}

@Composable
private fun EmptyDashboardSource(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Usb,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(82.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("Выберите накопитель", color = AutoText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            HeadUnitActionButton(
                text = "Открыть накопитель",
                icon = Icons.Rounded.Usb,
                onClick = onAddSource,
            )
        }
    }
}
