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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private enum class HomeMode {
    FOLDERS,
    FAVORITES,
}

@Composable
fun HomeScreen(
    state: LibraryUiState,
    favorites: FavoritesState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    var mode by remember { mutableStateOf(HomeMode.FOLDERS) }
    val source = state.sources.firstOrNull()
    val favoriteFolders = state.folders.filter { it.id in favorites.folderIds }
    val favoriteFiles = (state.videoFiles + state.audioFiles)
        .distinctBy(MediaFile::uriString)
        .filter { it.uriString in favorites.fileUris }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AutoBackground, Color(0xFF0E0920), Color(0xFF11142D))
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
                        text = source?.name ?: "Накопитель не выбран",
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTab(
                title = "Папки",
                selected = mode == HomeMode.FOLDERS,
                onClick = { mode = HomeMode.FOLDERS },
            )
            HomeTab(
                title = "Избранное",
                selected = mode == HomeMode.FAVORITES,
                onClick = { mode = HomeMode.FAVORITES },
            )
        }
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            Text(it, color = Color(0xFFFF8A9A), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.loading && state.folders.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
            }

            state.sources.isEmpty() -> EmptyLibrary(onAddSource)

            mode == HomeMode.FOLDERS -> {
                if (state.videoRootFolders.isEmpty()) {
                    EmptySection(
                        icon = Icons.Rounded.Folder,
                        title = "Папки с видео не найдены",
                        subtitle = "Выберите другой накопитель или обновите медиатеку",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.videoRootFolders, key = MediaFolder::id) { folder ->
                            MediaFolderTile(
                                folder = folder,
                                countText = "${state.videoCount(folder)} видео",
                                favorite = folder.id in favorites.folderIds,
                                onClick = { onOpenFolder(folder) },
                                onToggleFavorite = { onToggleFolderFavorite(folder) },
                            )
                        }
                    }
                }
            }

            favoriteFolders.isEmpty() && favoriteFiles.isEmpty() -> EmptySection(
                icon = Icons.Rounded.FavoriteBorder,
                title = "Избранное пока пусто",
                subtitle = "Добавляйте сюда папки, видео и аудиотреки кнопкой с сердцем",
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(favoriteFolders, key = { "folder:${it.id}" }) { folder ->
                    MediaFolderTile(
                        folder = folder,
                        countText = "${state.filesInTree(folder).size} файлов",
                        favorite = true,
                        onClick = { onOpenFolder(folder) },
                        onToggleFavorite = { onToggleFolderFavorite(folder) },
                    )
                }
                items(favoriteFiles, key = { "file:${it.uriString}" }) { file ->
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
}

@Composable
private fun HomeTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(AutoPink, AutoPurple))
                } else {
                    Brush.horizontalGradient(listOf(AutoSurfaceHigh, AutoSurfaceHigh))
                },
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = AutoText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun MediaFolderTile(
    folder: MediaFolder,
    countText: String,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val accent = accentFor(folder.id)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .headUnitPressable(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                sound = UiSound.FOLDER,
            )
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.24f), AutoSurfaceHigh, Color(0xFF100E20))
                ),
                RoundedCornerShape(20.dp),
            )
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                folder.name,
                color = AutoText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                countText,
                color = AutoMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FavoriteButton(
            favorite = favorite,
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
fun MediaAssetTile(
    file: MediaFile,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .headUnitPressable(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                sound = UiSound.FOLDER,
            )
            .background(AutoSurfaceHigh, RoundedCornerShape(20.dp)),
    ) {
        if (file.isVideo) {
            VideoThumbnail(
                file = file,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                showPlayButton = true,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xD9080711))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    file.name,
                    color = AutoText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Audiotrack,
                    contentDescription = null,
                    tint = AutoCyan,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    file.name,
                    color = AutoText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FavoriteButton(
            favorite = favorite,
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
        )
    }
}

@Composable
fun FavoriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .headUnitPressable(
                onClick = onClick,
                shape = CircleShape,
                sound = UiSound.FAVORITE,
            )
            .background(Color(0xCC090713), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (favorite) "Удалить из избранного" else "Добавить в избранное",
            tint = if (favorite) AutoPink else AutoText,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun EmptyLibrary(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(82.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("Медиатека пока пуста", color = AutoText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Выберите внутреннюю память, флешку или жёсткий диск", color = AutoMuted)
            Spacer(Modifier.height(18.dp))
            HeadUnitActionButton(
                text = "Выбрать накопитель",
                icon = Icons.Rounded.Add,
                onClick = onAddSource,
            )
        }
    }
}

@Composable
private fun EmptySection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = AutoPurple, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, color = AutoText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AutoMuted, fontSize = 14.sp)
        }
    }
}

private fun accentFor(value: String): Color {
    val colors = listOf(AutoPurple, AutoPink, AutoBlue, AutoCyan, AutoRed)
    return colors[(value.hashCode() and Int.MAX_VALUE) % colors.size]
}
