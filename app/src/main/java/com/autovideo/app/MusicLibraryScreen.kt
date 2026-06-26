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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun MusicLibraryScreen(
    state: LibraryUiState,
    favorites: FavoritesState,
    playlists: List<AudioPlaylist>,
    playlistStore: AudioPlaylistStore,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlayPlaylist: (List<MediaFile>) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF160A27), Color(0xFF071B24))))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Музыка", color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${state.audioFiles.size} треков · ${playlists.size} плейлистов",
                    color = AutoMuted,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            HeadUnitActionButton(
                text = "Создать плейлист",
                icon = Icons.Rounded.Add,
                onClick = { showCreateDialog = true },
                backgroundColor = AutoPurple,
            )
            Spacer(Modifier.width(10.dp))
            AppClock(compact = true)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            item {
                MusicSectionTitle(
                    title = "Папки с музыкой",
                    subtitle = "Открывайте папку и выбирайте любой трек",
                    icon = Icons.Rounded.Folder,
                    accent = AutoPurple,
                )
            }

            item {
                if (state.audioRootFolders.isEmpty()) {
                    MusicEmptyCard("Папки с музыкой не найдены")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.audioRootFolders, key = MediaFolder::id) { folder ->
                            MusicFolderCard(
                                folder = folder,
                                countText = "${state.audioCount(folder)} треков",
                                favorite = folder.id in favorites.folderIds,
                                onOpen = { onOpenFolder(folder) },
                                onFavorite = { onToggleFolderFavorite(folder) },
                            )
                        }
                    }
                }
            }

            item {
                MusicSectionTitle(
                    title = "Мои плейлисты",
                    subtitle = "Сохраняются после перезапуска приложения",
                    icon = Icons.Rounded.QueueMusic,
                    accent = AutoPink,
                )
            }

            item {
                if (playlists.isEmpty()) {
                    MusicEmptyCard("Плейлистов пока нет. Нажмите «Создать плейлист».")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists, key = AudioPlaylist::id) { playlist ->
                            val files = playlist.trackUris.mapNotNull { uri ->
                                state.audioFiles.firstOrNull { it.uriString == uri }
                            }
                            PlaylistCard(
                                playlist = playlist,
                                playableCount = files.size,
                                onPlay = { if (files.isNotEmpty()) onPlayPlaylist(files) },
                                onDelete = { playlistStore.delete(playlist.id) },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onCreate = { name ->
                playlistStore.create(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun MusicSectionTitle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(accent.copy(alpha = 0.18f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = AutoText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AutoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MusicFolderCard(
    folder: MediaFolder,
    countText: String,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(138.dp)
            .headUnitPressable(onOpen, shape = RoundedCornerShape(22.dp), sound = UiSound.FOLDER)
            .background(
                Brush.linearGradient(
                    listOf(AutoPurple.copy(alpha = 0.42f), AutoPink.copy(alpha = 0.17f), AutoSurfaceHigh)
                ),
                RoundedCornerShape(22.dp),
            )
            .padding(15.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Rounded.Folder, null, tint = AutoPink, modifier = Modifier.size(49.dp))
            Spacer(Modifier.height(7.dp))
            Text(
                folder.name,
                color = AutoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(countText, color = AutoMuted, fontSize = 12.sp)
        }
        FavoriteButton(favorite, onFavorite, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun PlaylistCard(
    playlist: AudioPlaylist,
    playableCount: Int,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(250.dp)
            .height(138.dp)
            .headUnitPressable(
                onClick = onPlay,
                enabled = playableCount > 0,
                shape = RoundedCornerShape(22.dp),
            )
            .background(
                Brush.linearGradient(
                    listOf(AutoPink.copy(alpha = 0.34f), AutoPurple.copy(alpha = 0.22f), AutoSurfaceHigh)
                ),
                RoundedCornerShape(22.dp),
            )
            .padding(15.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(
                if (playableCount > 0) Icons.Rounded.PlayArrow else Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = AutoPink,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                playlist.name,
                color = AutoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$playableCount треков", color = AutoMuted, fontSize = 12.sp)
        }
        HeadUnitIconButton(
            icon = Icons.Rounded.Delete,
            contentDescription = "Удалить плейлист",
            onClick = onDelete,
            size = 38.dp,
            iconSize = 21.dp,
            backgroundColor = Color(0xCC43172A),
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun MusicEmptyCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AutoSurfaceHigh, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Audiotrack, null, tint = AutoPurple, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, color = AutoMuted, fontSize = 15.sp)
    }
}

@Composable
private fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый плейлист", color = AutoText) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onCreate(name.trim()) },
            ) {
                Text("Создать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = AutoSurface,
    )
}
