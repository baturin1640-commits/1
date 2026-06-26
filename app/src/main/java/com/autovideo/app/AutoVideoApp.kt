package com.autovideo.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class AppSection {
    HOME,
    VIDEO,
    AUDIO,
    SETTINGS,
}

@Composable
fun AutoVideoApp(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playbackStore = remember { PlaybackStore(context.applicationContext) }
    val favoritesStore = remember { FavoritesStore(context.applicationContext) }
    val favorites by favoritesStore.state.collectAsStateWithLifecycle()
    val soundPlayer = remember { UiSoundPlayer(context.applicationContext) }

    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.release() }
    }

    CompositionLocalProvider(LocalUiSoundPlayer provides soundPlayer) {
        var section by remember { mutableStateOf(AppSection.HOME) }
        var selectedFolder by remember { mutableStateOf<MediaFolder?>(null) }
        var playingFile by remember { mutableStateOf<MediaFile?>(null) }
        var playingQueue by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
        var lastExitBackMs by remember { mutableLongStateOf(0L) }

        val latestVideo = playbackStore.latestVideo(state.videoFiles)

        fun startPlayback(file: MediaFile, queue: List<MediaFile>) {
            val normalized = queue.distinctBy(MediaFile::uriString)
            playingQueue = if (normalized.any { it.uriString == file.uriString }) normalized else listOf(file)
            playingFile = file
        }

        fun closeCurrentFolderLevel() {
            val current = selectedFolder ?: return
            selectedFolder = current.parentId?.let { parentId ->
                state.folders.firstOrNull { it.id == parentId }
            }
        }

        BackHandler(enabled = true) {
            when {
                playingFile != null -> playingFile = null
                selectedFolder != null -> closeCurrentFolderLevel()
                section != AppSection.HOME -> section = AppSection.HOME
                else -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastExitBackMs <= 2_000L) {
                        activity?.finish()
                    } else {
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

        val destination = when {
            playingFile != null -> "player:${playingFile?.uriString}"
            selectedFolder != null -> "folder:${selectedFolder?.id}"
            else -> "library"
        }

        Box(modifier = Modifier.fillMaxSize().background(AutoBackground)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val enter = slideInHorizontally(
                        animationSpec = tween(380, easing = FastOutSlowInEasing),
                        initialOffsetX = { it / 11 },
                    ) + fadeIn(tween(300))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        targetOffsetX = { -it / 14 },
                    ) + fadeOut(tween(220))
                    enter.togetherWith(exit)
                },
                label = "mainPageTransition",
            ) { target ->
                when {
                    target.startsWith("player:") && playingFile != null -> VlcPlayerScreen(
                        file = checkNotNull(playingFile),
                        queue = playingQueue,
                        playbackStore = playbackStore,
                        onSelectFile = { playingFile = it },
                        onBack = { playingFile = null },
                    )

                    target.startsWith("folder:") && selectedFolder != null -> FolderBrowserScreen(
                        state = state,
                        folder = checkNotNull(selectedFolder),
                        favorites = favorites,
                        onOpenFolder = { selectedFolder = it },
                        onBack = ::closeCurrentFolderLevel,
                        onPlay = { file ->
                            startPlayback(file, state.filesInTree(checkNotNull(selectedFolder)))
                        },
                        onToggleFolderFavorite = favoritesStore::toggle,
                        onToggleFileFavorite = favoritesStore::toggle,
                    )

                    else -> MainLibraryShell(
                        state = state,
                        section = section,
                        playbackStore = playbackStore,
                        favorites = favorites,
                        latestVideo = latestVideo,
                        onSelectSection = { section = it },
                        onResumeLatest = {
                            latestVideo?.let { startPlayback(it, state.videoFiles) }
                        },
                        onAddSource = onAddSource,
                        onRefresh = onRefresh,
                        onRemoveSource = onRemoveSource,
                        onOpenFolder = { selectedFolder = it },
                        onPlay = { file, queue -> startPlayback(file, queue) },
                        onToggleFolderFavorite = favoritesStore::toggle,
                        onToggleFileFavorite = favoritesStore::toggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainLibraryShell(
    state: LibraryUiState,
    section: AppSection,
    playbackStore: PlaybackStore,
    favorites: FavoritesState,
    latestVideo: MediaFile?,
    onSelectSection: (AppSection) -> Unit,
    onResumeLatest: () -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile, List<MediaFile>) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize().background(AutoBackground)) {
        SideNavigation(
            selected = section,
            latestVideo = latestVideo,
            onSelect = onSelectSection,
            onResumeLatest = onResumeLatest,
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = tween(430, easing = FastOutSlowInEasing),
                        initialOffsetX = { width -> if (forward) width / 9 else -width / 9 },
                    ) + fadeIn(tween(340))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(360, easing = FastOutSlowInEasing),
                        targetOffsetX = { width -> if (forward) -width / 12 else width / 12 },
                    ) + fadeOut(tween(250))
                    enter.togetherWith(exit)
                },
                label = "sectionTransition",
            ) { current ->
                when (current) {
                    AppSection.HOME -> HomeScreen(
                        state = state,
                        favorites = favorites,
                        onAddSource = onAddSource,
                        onRefresh = onRefresh,
                        onOpenFolder = onOpenFolder,
                        onPlay = { onPlay(it, if (it.isVideo) state.videoFiles else state.audioFiles) },
                        onToggleFolderFavorite = onToggleFolderFavorite,
                        onToggleFileFavorite = onToggleFileFavorite,
                    )

                    AppSection.VIDEO -> VideoFoldersScreen(
                        state = state,
                        playbackStore = playbackStore,
                        favorites = favorites,
                        onOpenFolder = onOpenFolder,
                        onPlay = { onPlay(it, state.videoFiles) },
                        onToggleFolderFavorite = onToggleFolderFavorite,
                        onToggleFileFavorite = onToggleFileFavorite,
                    )

                    AppSection.AUDIO -> AudioFoldersScreen(
                        state = state,
                        favorites = favorites,
                        onOpenFolder = onOpenFolder,
                        onToggleFolderFavorite = onToggleFolderFavorite,
                    )

                    AppSection.SETTINGS -> SourcesScreen(
                        state = state,
                        onAddSource = onAddSource,
                        onRefresh = onRefresh,
                        onRemoveSource = onRemoveSource,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideNavigation(
    selected: AppSection,
    latestVideo: MediaFile?,
    onSelect: (AppSection) -> Unit,
    onResumeLatest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080711), Color(0xFF100B21), Color(0xFF07101C))
                )
            )
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("V", color = AutoText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        NavigationItem("Главная", Icons.Rounded.Home, AppSection.HOME, selected, onSelect)
        NavigationItem("Видео", Icons.Rounded.VideoLibrary, AppSection.VIDEO, selected, onSelect)
        NavigationItem("Музыка", Icons.Rounded.Audiotrack, AppSection.AUDIO, selected, onSelect)
        NavigationItem("Настройки", Icons.Rounded.Settings, AppSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HeadUnitIconButton(
                icon = Icons.Rounded.PlayCircle,
                contentDescription = "Продолжить с места остановки",
                onClick = onResumeLatest,
                enabled = latestVideo != null,
                size = 82.dp,
                iconSize = 48.dp,
                backgroundColor = if (latestVideo != null) AutoPurple else AutoSurfaceHigh,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (latestVideo != null) "Продолжить" else "Нет истории",
                color = if (latestVideo != null) AutoText else AutoMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NavigationItem(
    label: String,
    icon: ImageVector,
    section: AppSection,
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
) {
    val active = section == selected
    val scale by animateFloatAsState(
        targetValue = if (active) 1.035f else 1f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "navigationScale",
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(82.dp)
                .headUnitPressable(
                    onClick = { onSelect(section) },
                    shape = RoundedCornerShape(24.dp),
                )
                .background(
                    if (active) {
                        Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))
                    } else {
                        Brush.linearGradient(listOf(AutoSurfaceHigh, AutoSurfaceHigh))
                    },
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Color.White else AutoMuted,
                modifier = Modifier.size(45.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) AutoText else AutoMuted,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SourcesScreen(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    val context = LocalContext.current
    val source = state.sources.firstOrNull()
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { onRefresh() }

    fun openPermissions() {
        settingsLauncher.launch(
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AutoBackground, Color(0xFF100A20), Color(0xFF0A1424))
                )
            )
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Настройки", color = AutoText, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("Один активный накопитель и системные разрешения", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            HeadUnitIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Обновить",
                onClick = onRefresh,
                size = 64.dp,
                iconSize = 34.dp,
            )
            Spacer(Modifier.width(10.dp))
            AppClock(compact = true)
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeadUnitActionButton(
                text = "Сменить накопитель",
                icon = Icons.Rounded.Storage,
                onClick = onAddSource,
                backgroundColor = AutoPurple,
            )
            HeadUnitActionButton(
                text = "Все разрешения",
                icon = Icons.Rounded.AdminPanelSettings,
                onClick = ::openPermissions,
                backgroundColor = Color(0xFF3E295F),
            )
            if (source != null && source.uriString != INTERNAL_SOURCE_URI) {
                HeadUnitActionButton(
                    text = "Внутренняя память",
                    icon = Icons.Rounded.PhoneAndroid,
                    onClick = { onRemoveSource(source.uriString) },
                    backgroundColor = Color(0xFF1B4560),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Активный накопитель", color = AutoText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        when {
            state.loading && source == null -> CircularProgressIndicator(color = AutoPurple)
            source == null -> EmptySourceCard(onAddSource)
            else -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(AutoSurfaceBright, AutoSurfaceHigh, Color(0xFF13243B))
                        ),
                        RoundedCornerShape(22.dp),
                    )
                    .padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (source.uriString == INTERNAL_SOURCE_URI) {
                        Icons.Rounded.PhoneAndroid
                    } else {
                        Icons.Rounded.Usb
                    },
                    contentDescription = null,
                    tint = if (source.connected) AutoGreen else AutoMuted,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        source.name,
                        color = AutoText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (source.connected) "Подключён · найдено ${state.videoFiles.size} видео и ${state.audioFiles.size} аудио" else "Накопитель недоступен",
                        color = if (source.connected) AutoGreen else AutoMuted,
                        fontSize = 14.sp,
                    )
                }
                if (source.uriString != INTERNAL_SOURCE_URI) {
                    HeadUnitIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Отключить накопитель",
                        onClick = { onRemoveSource(source.uriString) },
                        size = 64.dp,
                        iconSize = 34.dp,
                        tint = AutoMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoSurfaceHigh, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                contentDescription = null,
                tint = AutoPink,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Доступ к файлам", color = AutoText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Разрешите видео, музыку и управление памятью. Для USB выберите корень самого накопителя.",
                    color = AutoMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptySourceCard(onAddSource: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AutoSurfaceHigh, RoundedCornerShape(22.dp))
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Storage,
            contentDescription = null,
            tint = AutoPurple,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Накопитель не выбран", color = AutoText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        HeadUnitActionButton(
            text = "Выбрать накопитель",
            icon = Icons.Rounded.Add,
            onClick = onAddSource,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
