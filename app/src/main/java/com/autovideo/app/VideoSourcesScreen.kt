package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class VideoSourceRoute { SOURCES, LOCAL }

@Composable
fun VideoSourcesScreen(
    localCount: Int,
    sourceName: String,
    onOpenLocal: () -> Unit,
    onOpenRutube: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF10091F), Color(0xFF0B1728))))
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Видео", color = AutoText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Локальные файлы и RUTUBE", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeadUnitActionButton(
                text = "Локальные · $localCount",
                icon = Icons.Rounded.VideoLibrary,
                onClick = onOpenLocal,
                backgroundColor = AutoGreen.copy(alpha = 0.72f),
            )
            HeadUnitActionButton(
                text = "RUTUBE",
                icon = Icons.Rounded.VideoLibrary,
                onClick = onOpenRutube,
                backgroundColor = AutoPurple,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(sourceName, color = AutoMuted, fontSize = 13.sp)
    }
}

@Composable
fun VideoExperienceScreen(
    state: LibraryUiState,
    playbackStore: PlaybackStore,
    favorites: FavoritesState,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
    onOpenRutube: () -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    var routeName by rememberSaveable { mutableStateOf(VideoSourceRoute.SOURCES.name) }
    val route = runCatching { VideoSourceRoute.valueOf(routeName) }
        .getOrDefault(VideoSourceRoute.SOURCES)

    when (route) {
        VideoSourceRoute.SOURCES -> VideoSourcesScreen(
            localCount = state.videoFiles.size,
            sourceName = state.sources.firstOrNull().safeDisplayName(),
            onOpenLocal = { routeName = VideoSourceRoute.LOCAL.name },
            onOpenRutube = onOpenRutube,
        )
        VideoSourceRoute.LOCAL -> VideoFoldersScreen(
            state = state,
            playbackStore = playbackStore,
            favorites = favorites,
            onOpenFolder = onOpenFolder,
            onPlay = onPlay,
            onToggleFolderFavorite = onToggleFolderFavorite,
            onToggleFileFavorite = onToggleFileFavorite,
        )
    }
}
