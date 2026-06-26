package com.autovideo.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val playlistStore = remember { AudioPlaylistStore(context.applicationContext) }
    val favorites by favoritesStore.state.collectAsStateWithLifecycle()
    val playlists by playlistStore.state.collectAsStateWithLifecycle()
    val sounds = remember { UiSoundPlayer(context.applicationContext) }

    DisposableEffect(sounds) { onDispose { sounds.release() } }

    CompositionLocalProvider(LocalUiSoundPlayer provides sounds) {
        var sectionName by rememberSaveable { mutableStateOf(RootSection.HOME.name) }
        var folderId by rememberSaveable { mutableStateOf<String?>(null) }
        var playingUri by rememberSaveable { mutableStateOf<String?>(null) }
        var modeName by rememberSaveable { mutableStateOf(PlayerDisplayMode.FULLSCREEN.name) }
        var queueUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var rutubeFullscreen by rememberSaveable { mutableStateOf(false) }
        var lastExitBackMs by remember { mutableLongStateOf(0L) }

        val section = runCatching { RootSection.valueOf(sectionName) }.getOrDefault(RootSection.HOME)
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

        LaunchedEffect(section) {
            if (section != RootSection.RUTUBE) rutubeFullscreen = false
        }

        fun play(file: MediaFile, files: List<MediaFile>) {
            val keepWindowed = playingFile != null && mode == PlayerDisplayMode.WINDOWED
            queueUris = files.distinctBy(MediaFile::uriString).map(MediaFile::uriString)
            playingUri = file.uriString
            modeName = if (keepWindowed) {
                PlayerDisplayMode.WINDOWED.name
            } else {
                PlayerDisplayMode.FULLSCREEN.name
            }
        }

        fun playPlaylist(files: List<MediaFile>) {
            val audioFiles = files.filter(MediaFile::isAudio).distinctBy(MediaFile::uriString)
            audioFiles.firstOrNull()?.let { first -> play(first, audioFiles) }
        }

        fun openRutube() {
            folderId = null
            sectionName = RootSection.RUTUBE.name
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
                section != RootSection.HOME -> {
                    sectionName = RootSection.HOME.name
                    folderId = null
                    rutubeFullscreen = false
                }
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
                AnimatedVisibility(
                    visible = !rutubeFullscreen,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(280),
                    ) + fadeIn(animationSpec = tween(220)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(260),
                    ) + fadeOut(animationSpec = tween(180)),
                ) {
                    CarSideNavigation(
                        selected = section,
                        latestVideo = latest,
                        onSelect = {
                            folderId = null
                            rutubeFullscreen = false
                            sectionName = it.name
                        },
                        onResumeLatest = { latest?.let { file -> play(file, state.videoFiles) } },
                    )
                }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Crossfade(
                        targetState = folderId ?: section.name,
                        animationSpec = tween(230),
                        label = "rootContent",
                    ) { destination ->
                        val visibleFolder = state.folders.firstOrNull { it.id == destination }
                        when {
                            visibleFolder != null -> FolderBrowserScreen(
                                state = state,
                                folder = visibleFolder,
                                favorites = favorites,
                                onOpenFolder = { folderId = it.id },
                                onBack = { folderId = visibleFolder.parentId },
                                onPlay = { file -> play(file, state.filesInTree(visibleFolder)) },
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )

                            destination == RootSection.HOME.name -> DashboardHomeScreen(
                                state = state,
                                favorites = favorites,
                                playlists = playlists,
                                onAddSource = onAddSource,
                                onRefresh = onRefresh,
                                onOpenFolder = { folderId = it.id },
                                onPlay = { file ->
                                    play(file, if (file.isVideo) state.videoFiles else state.audioFiles)
                                },
                                onPlayPlaylist = ::playPlaylist,
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )

                            destination == RootSection.VIDEO.name -> VideoExperienceScreen(
                                state = state,
                                playbackStore = playbackStore,
                                favorites = favorites,
                                onOpenFolder = { folderId = it.id },
                                onPlay = { play(it, state.videoFiles) },
                                onOpenRutube = ::openRutube,
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )

                            destination == RootSection.RUTUBE.name -> RutubeScreen(
                                onBack = {
                                    rutubeFullscreen = false
                                    sectionName = RootSection.HOME.name
                                },
                                onFullscreenChanged = { rutubeFullscreen = it },
                            )

                            destination == RootSection.AUDIO.name -> MusicLibraryScreen(
                                state = state,
                                favorites = favorites,
                                playlists = playlists,
                                playlistStore = playlistStore,
                                onOpenFolder = { folderId = it.id },
                                onPlayPlaylist = ::playPlaylist,
                                onToggleFolderFavorite = favoritesStore::toggle,
                            )

                            destination == RootSection.FAVORITES.name -> FavoritesScreen(
                                state = state,
                                favorites = favorites,
                                onOpenFolder = { folderId = it.id },
                                onPlay = { file ->
                                    play(file, if (file.isVideo) state.videoFiles else state.audioFiles)
                                },
                                onToggleFolderFavorite = favoritesStore::toggle,
                                onToggleFileFavorite = favoritesStore::toggle,
                            )

                            else -> StorageSettingsScreen(state, onAddSource, onRefresh, onRemoveSource)
                        }
                    }
                }
            }

            if (playingFile != null && playingFile.isVideo) {
                ReliablePlayerOverlay(
                    file = playingFile,
                    queue = queue.filter(MediaFile::isVideo),
                    playbackStore = playbackStore,
                    displayMode = mode,
                    onDisplayModeChange = { modeName = it.name },
                    onSelectFile = { playingUri = it.uriString },
                    onClose = { playingUri = null },
                    modifier = if (mode == PlayerDisplayMode.FULLSCREEN) {
                        Modifier.fillMaxSize().zIndex(20f)
                    } else {
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(18.dp)
                            .fillMaxHeight(0.45f)
                            .aspectRatio(16f / 9f)
                            .zIndex(20f)
                    },
                )
            }

            if (playingFile != null && playingFile.isAudio) {
                AudioPlayerOverlay(
                    file = playingFile,
                    queue = queue.filter(MediaFile::isAudio),
                    allAudioFiles = state.audioFiles,
                    playbackStore = playbackStore,
                    playlistStore = playlistStore,
                    displayMode = mode,
                    onDisplayModeChange = { modeName = it.name },
                    onSelectFile = { playingUri = it.uriString },
                    onQueueChange = { files ->
                        queueUris = files.distinctBy(MediaFile::uriString).map(MediaFile::uriString)
                    },
                    onClose = { playingUri = null },
                    modifier = if (mode == PlayerDisplayMode.FULLSCREEN) {
                        Modifier.fillMaxSize().zIndex(20f)
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(18.dp)
                            .fillMaxWidth(0.76f)
                            .height(112.dp)
                            .zIndex(20f)
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
