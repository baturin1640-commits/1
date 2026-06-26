package com.autovideo.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VideoAppRoot(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val playbackStore = remember { PlaybackStore(context.applicationContext) }
    val favoritesStore = remember { FavoritesStore(context.applicationContext) }
    val favorites by favoritesStore.state.collectAsStateWithLifecycle()
    val sounds = remember { UiSoundPlayer(context.applicationContext) }

    DisposableEffect(sounds) { onDispose { sounds.release() } }

    CompositionLocalProvider(LocalUiSoundPlayer provides sounds) {
        var sectionName by rememberSaveable { mutableStateOf(AppSection.HOME.name) }
        var folderId by rememberSaveable { mutableStateOf<String?>(null) }
        var playingUri by rememberSaveable { mutableStateOf<String?>(null) }
        var modeName by rememberSaveable { mutableStateOf(PlayerDisplayMode.FULLSCREEN.name) }
        var queueUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var lastExitBackMs by remember { mutableLongStateOf(0L) }

        val section = runCatching { AppSection.valueOf(sectionName) }.getOrDefault(AppSection.HOME)
        val folder = state.folders.firstOrNull { it.id == folderId }
        val allFiles = state.folders.flatMap(MediaFolder::files).distinctBy(MediaFile::uriString)
        val playingFile = allFiles.firstOrNull { it.uriString == playingUri }
        val mode = runCatching { PlayerDisplayMode.valueOf(modeName) }
            .getOrDefault(PlayerDisplayMode.FULLSCREEN)
        val queue = queueUris.mapNotNull { uri -> allFiles.firstOrNull { it.uriString == uri } }
            .ifEmpty { playingFile?.let { if (it.isVideo) state.videoFiles else state.audioFiles }.orEmpty() }
        val latest = playbackStore.latestVideo(state.videoFiles)

        LaunchedEffect(playingUri, state.loading, allFiles.size) {
            if (!state.loading && playingUri != null && playingFile == null) playingUri = null
        }

        fun play(file: MediaFile, files: List<MediaFile>) {
            queueUris = files.distinctBy(MediaFile::uriString).map(MediaFile::uriString)
            playingUri = file.uriString
            modeName = PlayerDisplayMode.FULLSCREEN.name
        }

        fun backFolder() {
            folderId = folder?.parentId
        }

        BackHandler {
            when {
                playingFile != null && mode == PlayerDisplayMode.FULLSCREEN -> {
                    modeName = PlayerDisplayMode.WINDOWED.name
                }
                folder != null -> backFolder()
                section != AppSection.HOME -> sectionName = AppSection.HOME.name
                playingFile != null -> playingUri = null
                else -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastExitBackMs <= 2_000L) activity?.finish()
                    else {
                        lastExitBackMs = now
                        Toast.makeText(
                            context,
                            "Проведите назад ещё раз, чтобы закрыть приложение",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(AutoBackground)) {
            Row(Modifier.fillMaxSize()) {
                CarSideNavigation(
                    selected = section,
                    latestVideo = latest,
                    onSelect = {
                        folderId = null
                        sectionName = it.name
                    },
                    onResumeLatest = { latest?.let { file -> play(file, state.videoFiles) } },
                )

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Crossfade(
                        targetState = folderId ?: section.name,
                        animationSpec = tween(230),
                        label = "rootContent",
                    ) {
                        when {
                            folder != null -> FolderBrowserScreen(
                                state = state,
                                folder = folder,
                                favorites = favorites,
                                onOpenFolder = { folderId = it.id },
                                onBack = ::backFolder,
                                onPlay = { file -> play(file, state.filesInTree(folder)) },
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )
                            section == AppSection.HOME -> HomeScreen(
                                state = state,
                                favorites = favorites,
                                onAddSource = onAddSource,
                                onRefresh = onRefresh,
                                onOpenFolder = { folderId = it.id },
                                onPlay = { file -> play(file, if (file.isVideo) state.videoFiles else state.audioFiles) },
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )
                            section == AppSection.VIDEO -> VideoExperienceScreen(
                                state = state,
                                playbackStore = playbackStore,
                                favorites = favorites,
                                onOpenFolder = { folderId = it.id },
                                onPlay = { play(it, state.videoFiles) },
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )
                            section == AppSection.AUDIO -> AudioFoldersScreen(
                                state = state,
                                favorites = favorites,
                                onOpenFolder = { folderId = it.id },
                                onToggleFolderFavorite = favoritesStore::toggle,
                            )
                            else -> StorageSettingsScreen(state, onAddSource, onRefresh, onRemoveSource)
                        }
                    }
                }
            }

            if (playingFile != null) {
                StablePlayerOverlay(
                    file = playingFile,
                    queue = queue,
                    playbackStore = playbackStore,
                    displayMode = mode,
                    onDisplayModeChange = { modeName = it.name },
                    onSelectFile = { playingUri = it.uriString },
                    onClose = { playingUri = null },
                    modifier = if (mode == PlayerDisplayMode.FULLSCREEN) {
                        Modifier.fillMaxSize().zIndex(20f)
                    } else {
                        Modifier.align(Alignment.BottomEnd).padding(18.dp)
                            .fillMaxHeight(0.45f).aspectRatio(16f / 9f).zIndex(20f)
                    },
                )
            }
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
