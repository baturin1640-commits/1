package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FavoritesScreen(
    state: LibraryUiState,
    favorites: FavoritesState,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    val folders = state.folders.filter { it.id in favorites.folderIds }
    val files = (state.videoFiles + state.audioFiles)
        .distinctBy(MediaFile::uriString)
        .filter { it.uriString in favorites.fileUris }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF170A22), Color(0xFF0A1821))))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Избранное", color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${folders.size} папок · ${files.size} файлов",
                    color = AutoMuted,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.padding(top = 8.dp))

        if (folders.isEmpty() && files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = AutoPink,
                        modifier = Modifier.padding(10.dp),
                    )
                    Text("Избранное пока пусто", color = AutoText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Отмечайте папки, видео и музыку кнопкой с сердцем", color = AutoMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(folders, key = { "folder:${it.id}" }) { folder ->
                    MediaFolderTile(
                        folder = folder,
                        countText = "${state.filesInTree(folder).size} файлов",
                        favorite = true,
                        onClick = { onOpenFolder(folder) },
                        onToggleFavorite = { onToggleFolderFavorite(folder) },
                    )
                }
                items(files, key = { "file:${it.uriString}" }) { file ->
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
