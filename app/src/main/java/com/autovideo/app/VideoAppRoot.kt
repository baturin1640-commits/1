package com.autovideo.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val playbackStore = remember { PlaybackStore(context.applicationContext) }
    val favoritesStore = remember { FavoritesStore(context.applicationContext) }
    val favorites by favoritesStore.state.collectAsStateWithLifecycle()
    val sounds = remember { UiSoundPlayer(context.applicationContext) }

    DisposableEffect(sounds) {
        onDispose { sounds.release() }
    }

    CompositionLocalProvider(LocalUiSoundPlayer provides sounds) {
        var sectionName by rememberSaveable { mutableStateOf(AppSection.HOME.name) }
        var folderId by rememberSaveable { mutableStateOf<String?>(null) }
        var playingUri by rememberSaveable { mutableStateOf<String?>(null) }
        var modeName by rememberSaveable { mutableStateOf(PlayerDisplayMode.FULLSCREEN.name) }
        var queueUris by remember { mutableStateOf<List<String>>(emptyList()) }

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
            }
        }

        Box(Modifier.fillMaxSize().background(AutoBackground)) {
            Row(Modifier.fillMaxSize()) {
                RootSideNavigation(
                    selected = section,
                    latestVideo = latest,
                    onSelect = {
                        folderId = null
                        sectionName = it.name
                    },
                    onResumeLatest = { latest?.let { file -> play(file, state.videoFiles) } },
                )

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val destination = folderId ?: section.name
                    Crossfade(targetState = destination, animationSpec = tween(260), label = "root") {
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
                            section == AppSection.VIDEO -> VideoFoldersScreen(
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
                            else -> RootSettingsScreen(state, onAddSource, onRefresh, onRemoveSource)
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

@Composable
private fun RootSideNavigation(
    selected: AppSection,
    latestVideo: MediaFile?,
    onSelect: (AppSection) -> Unit,
    onResumeLatest: () -> Unit,
) {
    Column(
        modifier = Modifier.width(140.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xFF080711), Color(0xFF100B21))))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("V", color = AutoText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        RootNavigationItem("Главная", Icons.Rounded.Home, AppSection.HOME, selected, onSelect)
        RootNavigationItem("Видео", Icons.Rounded.VideoLibrary, AppSection.VIDEO, selected, onSelect)
        RootNavigationItem("Музыка", Icons.Rounded.Audiotrack, AppSection.AUDIO, selected, onSelect)
        RootNavigationItem("Настройки", Icons.Rounded.Settings, AppSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))
        HeadUnitIconButton(
            icon = Icons.Rounded.PlayCircle,
            contentDescription = "Продолжить",
            onClick = onResumeLatest,
            enabled = latestVideo != null,
            size = 82.dp,
            iconSize = 48.dp,
            backgroundColor = if (latestVideo != null) AutoPurple else AutoSurfaceHigh,
        )
        Text(
            if (latestVideo != null) "Продолжить" else "Нет истории",
            color = AutoMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun RootNavigationItem(
    label: String,
    icon: ImageVector,
    section: AppSection,
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
) {
    val active = section == selected
    val scale by animateFloatAsState(if (active) 1.035f else 1f, label = "rootNav")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.scale(scale).size(82.dp)
                .headUnitPressable({ onSelect(section) }, shape = RoundedCornerShape(24.dp))
                .background(
                    if (active) Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))
                    else Brush.linearGradient(listOf(AutoSurfaceHigh, AutoSurfaceHigh)),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (active) Color.White else AutoMuted, modifier = Modifier.size(45.dp))
        }
        Text(label, color = if (active) AutoText else AutoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun RootSettingsScreen(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    val context = LocalContext.current
    val source = state.sources.firstOrNull()
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF100A20))))
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Настройки", color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("Накопитель и разрешения", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            HeadUnitActionButton("Сменить накопитель", Icons.Rounded.Storage, onAddSource)
            HeadUnitActionButton("Обновить", Icons.Rounded.Refresh, onRefresh, backgroundColor = AutoBlue)
            HeadUnitActionButton(
                "Разрешения",
                Icons.Rounded.AdminPanelSettings,
                onClick = {
                    context.startActivity(
                        Intent(
                            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                },
                backgroundColor = Color(0xFF3E295F),
            )
            if (source != null && source.uriString != INTERNAL_SOURCE_URI) {
                HeadUnitActionButton(
                    "Внутренняя память",
                    Icons.Rounded.PhoneAndroid,
                    onClick = { onRemoveSource(source.uriString) },
                    backgroundColor = Color(0xFF1B4560),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().background(AutoSurfaceHigh, RoundedCornerShape(22.dp)).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (source?.uriString == INTERNAL_SOURCE_URI) Icons.Rounded.PhoneAndroid else Icons.Rounded.Usb,
                contentDescription = null,
                tint = if (source?.connected == true) AutoGreen else AutoMuted,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    source?.name ?: "Накопитель не выбран",
                    color = AutoText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.loading) "Чтение всех папок…"
                    else "${state.videoFiles.size} видео · ${state.audioFiles.size} аудио",
                    color = AutoMuted,
                    fontSize = 14.sp,
                )
            }
        }
        state.error?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = Color(0xFFFF9AAA), fontSize = 15.sp)
        }
    }
}
