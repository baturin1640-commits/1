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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    BackHandler(enabled = true) {
        when {
            playingFile != null -> playingFile = null
            selectedFolder != null -> selectedFolder = null
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
                    initialOffsetX = { it / 10 },
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
                target.startsWith("player:") && playingFile != null -> PlayerScreen(
                    file = checkNotNull(playingFile),
                    queue = playingQueue,
                    playbackStore = playbackStore,
                    onSelectFile = { playingFile = it },
                    onBack = { playingFile = null },
                )

                target.startsWith("folder:") && selectedFolder != null -> FolderBrowserScreen(
                    folder = checkNotNull(selectedFolder),
                    onBack = { selectedFolder = null },
                    onPlay = { file -> startPlayback(file, checkNotNull(selectedFolder).files) },
                )

                else -> MainLibraryShell(
                    state = state,
                    section = section,
                    playbackStore = playbackStore,
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
                )
            }
        }
    }
}

@Composable
private fun MainLibraryShell(
    state: LibraryUiState,
    section: AppSection,
    playbackStore: PlaybackStore,
    latestVideo: MediaFile?,
    onSelectSection: (AppSection) -> Unit,
    onResumeLatest: () -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile, List<MediaFile>) -> Unit,
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
                        onAddSource = onAddSource,
                        onRefresh = onRefresh,
                        onOpenFolder = onOpenFolder,
                    )

                    AppSection.VIDEO -> VideoFoldersScreen(
                        folders = state.videoFolders,
                        loading = state.loading,
                        playbackStore = playbackStore,
                        onOpenFolder = onOpenFolder,
                        onPlay = { file -> onPlay(file, state.videoFiles) },
                    )

                    AppSection.AUDIO -> AudioFoldersScreen(
                        folders = state.audioFolders,
                        loading = state.loading,
                        onOpenFolder = onOpenFolder,
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
            .width(124.dp)
            .fillMaxHeight()
            .background(Color(0xFF09080D))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AV", color = AutoText, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        NavigationItem("Главная", Icons.Rounded.Home, AppSection.HOME, selected, onSelect)
        NavigationItem("Видео", Icons.Rounded.VideoLibrary, AppSection.VIDEO, selected, onSelect)
        NavigationItem("Музыка", Icons.Rounded.Audiotrack, AppSection.AUDIO, selected, onSelect)
        NavigationItem("Настройки", Icons.Rounded.Settings, AppSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HeadUnitIconButton(
                icon = Icons.Rounded.PlayCircle,
                contentDescription = "Продолжить просмотр",
                onClick = onResumeLatest,
                enabled = latestVideo != null,
                size = 72.dp,
                iconSize = 42.dp,
                backgroundColor = if (latestVideo != null) AutoPurple else AutoSurfaceHigh,
            )
            Spacer(Modifier.height(7.dp))
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(74.dp)
                .headUnitPressable(
                    onClick = { onSelect(section) },
                    shape = RoundedCornerShape(22.dp),
                )
                .background(if (active) AutoPurple else AutoSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Color.White else AutoMuted,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
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

    Column(Modifier.fillMaxSize().padding(30.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Настройки", color = AutoText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Память, накопители и системные разрешения",
                    color = AutoMuted,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            HeadUnitIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Обновить",
                onClick = onRefresh,
                size = 64.dp,
                iconSize = 34.dp,
            )
            Spacer(Modifier.width(12.dp))
            HeadUnitActionButton(
                text = "Все разрешения",
                icon = Icons.Rounded.AdminPanelSettings,
                onClick = ::openPermissions,
                backgroundColor = Color(0xFF4D2A78),
            )
            Spacer(Modifier.width(12.dp))
            HeadUnitActionButton(
                text = "Добавить носитель",
                icon = Icons.Rounded.Add,
                onClick = onAddSource,
            )
            Spacer(Modifier.width(12.dp))
            AppClock()
        }

        Spacer(Modifier.height(20.dp))
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
                tint = AutoPurple,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Выдать все разрешения",
                    color = AutoText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Откройте системные настройки и разрешите файлы, видео, аудио и управление памятью.",
                    color = AutoMuted,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Источники медиа", color = AutoText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        if (state.loading && state.sources.isEmpty()) {
            CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
        } else if (state.sources.isEmpty()) {
            EmptySourcesCard(onAddSource)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.sources, key = RemovableSource::uriString) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AutoSurfaceHigh, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val internal = source.uriString == INTERNAL_SOURCE_URI ||
                            source.name.equals("Внутренняя память", ignoreCase = true)
                        Icon(
                            imageVector = if (internal) Icons.Rounded.PhoneAndroid else Icons.Rounded.Usb,
                            contentDescription = null,
                            tint = if (source.connected) AutoGreen else AutoMuted,
                            modifier = Modifier.size(46.dp),
                        )
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text(
                                source.name,
                                color = AutoText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                            )
                            Text(
                                if (source.connected) "Подключён и доступен" else "Источник недоступен",
                                color = if (source.connected) AutoGreen else AutoMuted,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (!internal && !source.uriString.startsWith("file:")) {
                            HeadUnitIconButton(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить",
                                onClick = { onRemoveSource(source.uriString) },
                                size = 62.dp,
                                iconSize = 32.dp,
                                tint = AutoMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySourcesCard(onAddSource: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AutoSurfaceHigh, RoundedCornerShape(22.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = AutoPurple,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Медиафайлы пока не найдены",
            color = AutoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Разрешите доступ к памяти или выберите отдельную папку, флешку либо диск",
            color = AutoMuted,
        )
        Spacer(Modifier.height(20.dp))
        HeadUnitActionButton(
            text = "Выбрать папку или носитель",
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
