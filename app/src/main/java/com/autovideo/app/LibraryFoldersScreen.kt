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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoFoldersScreen(
    state: LibraryUiState,
    playbackStore: PlaybackStore,
    favorites: FavoritesState,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    val continued = remember(state.videoFiles) {
        state.videoFiles
            .filter { playbackStore.position(it) > 0L }
            .sortedByDescending(playbackStore::updatedAt)
            .take(6)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AutoBackground, Color(0xFF11091D), Color(0xFF12162E))
                )
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        LibraryHeader(
            title = "Видео",
            subtitle = "${state.videoRootFolders.size} папок · ${state.videoFiles.size} видео",
        )
        Spacer(Modifier.height(12.dp))

        if (continued.isNotEmpty()) {
            Text(
                "Продолжить просмотр",
                color = AutoText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(continued, key = MediaFile::uriString) { file ->
                    ContinueVideoCard(
                        file = file,
                        progress = playbackStore.progress(file),
                        favorite = file.uriString in favorites.fileUris,
                        onClick = { onPlay(file) },
                        onToggleFavorite = { onToggleFileFavorite(file) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text("Папки", color = AutoText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        RootFolderGrid(
            state = state,
            folders = state.videoRootFolders,
            favorites = favorites,
            emptyTitle = "Папки с видео не найдены",
            onOpenFolder = onOpenFolder,
            onToggleFolderFavorite = onToggleFolderFavorite,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun AudioFoldersScreen(
    state: LibraryUiState,
    favorites: FavoritesState,
    onOpenFolder: (MediaFolder) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AutoBackground, Color(0xFF091424), Color(0xFF150B25))
                )
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        LibraryHeader(
            title = "Музыка",
            subtitle = "${state.audioRootFolders.size} папок · ${state.audioFiles.size} аудиофайлов",
        )
        Spacer(Modifier.height(16.dp))
        RootFolderGrid(
            state = state,
            folders = state.audioRootFolders,
            favorites = favorites,
            emptyTitle = "Папки с музыкой не найдены",
            onOpenFolder = onOpenFolder,
            onToggleFolderFavorite = onToggleFolderFavorite,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LibraryHeader(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AutoMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        AppClock(compact = true)
    }
}

@Composable
private fun RootFolderGrid(
    state: LibraryUiState,
    folders: List<MediaFolder>,
    favorites: FavoritesState,
    emptyTitle: String,
    onOpenFolder: (MediaFolder) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading && folders.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(56.dp))
        }

        folders.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = AutoPurple,
                    modifier = Modifier.size(82.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(emptyTitle, color = AutoText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Проверьте выбранный накопитель и разрешения", color = AutoMuted)
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(folders, key = MediaFolder::id) { folder ->
                MediaFolderTile(
                    folder = folder,
                    countText = "${state.filesInTree(folder).size} файлов",
                    favorite = folder.id in favorites.folderIds,
                    onClick = { onOpenFolder(folder) },
                    onToggleFavorite = { onToggleFolderFavorite(folder) },
                )
            }
        }
    }
}

@Composable
private fun ContinueVideoCard(
    file: MediaFile,
    progress: Float,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(218.dp)
            .height(108.dp)
            .headUnitPressable(
                onClick = onClick,
                shape = RoundedCornerShape(18.dp),
                sound = UiSound.FOLDER,
            )
            .background(AutoSurfaceHigh, RoundedCornerShape(18.dp)),
    ) {
        VideoThumbnail(
            file = file,
            modifier = Modifier.fillMaxSize(),
            showPlayButton = true,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xDF080710)),
        ) {
            Text(
                file.name,
                color = AutoText,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = AutoPink,
                trackColor = Color(0xFF312A46),
            )
        }
        FavoriteButton(
            favorite = favorite,
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

@Composable
fun FolderBrowserScreen(
    state: LibraryUiState,
    folder: MediaFolder,
    favorites: FavoritesState,
    onOpenFolder: (MediaFolder) -> Unit,
    onBack: () -> Unit,
    onPlay: (MediaFile) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    val childFolders = state.childrenOf(folder.id)
    val directFiles = folder.files

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AutoBackground, Color(0xFF100A20), Color(0xFF10142A))
                )
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeadUnitIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack,
                size = 68.dp,
                iconSize = 36.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    color = AutoText,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${childFolders.size} подпапок · ${directFiles.size} файлов · ${folder.sourceName}",
                    color = AutoMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FavoriteButton(
                favorite = folder.id in favorites.folderIds,
                onClick = { onToggleFolderFavorite(folder) },
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            AppClock(compact = true)
        }

        Spacer(Modifier.height(14.dp))
        if (childFolders.isEmpty() && directFiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Папка пуста", color = AutoMuted, fontSize = 20.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(childFolders, key = { "folder:${it.id}" }) { child ->
                    MediaFolderTile(
                        folder = child,
                        countText = "${state.filesInTree(child).size} файлов",
                        favorite = child.id in favorites.folderIds,
                        onClick = { onOpenFolder(child) },
                        onToggleFavorite = { onToggleFolderFavorite(child) },
                    )
                }
                items(directFiles, key = { "file:${it.uriString}" }) { file ->
                    MediaAssetTile(
                        file = file,
                        favorite = file.uriString in favorites.fileUris,
                        onClick = { onPlay(file) },
                        onToggleFavorite = { onToggleFileFavorite(file) },
                    )
                }
            }
        }
    }
}
