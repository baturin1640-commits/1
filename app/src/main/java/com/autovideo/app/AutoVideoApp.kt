package com.autovideo.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Settings
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    val destinationKey = when {
        playingFile != null -> "player:${playingFile?.uriString}"
        selectedFolder != null -> "folder:${selectedFolder?.id}"
        else -> "section:${section.name}"
    }

    Box(modifier = Modifier.fillMaxSize().background(AutoBackground)) {
        AnimatedContent(
            targetState = destinationKey,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(300), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 1.02f))
            },
            label = "pageTransition",
        ) { target ->
            when {
                target.startsWith("player:") && playingFile != null -> PlayerScreen(
                    file = checkNotNull(playingFile),
                    queue = playingQueue,
                    playbackStore = playbackStore,
                    onSelectFile = { next -> playingFile = next },
                    onBack = { playingFile = null },
                )

                target.startsWith("folder:") && selectedFolder != null -> FolderBrowserScreen(
                    folder = checkNotNull(selectedFolder),
                    onBack = { selectedFolder = null },
                    onPlay = { file ->
                        startPlayback(file, checkNotNull(selectedFolder).files)
                    },
                )

                else -> MainLibraryShell(
                    state = state,
                    section = section,
                    playbackStore = playbackStore,
                    onSelectSection = { section = it },
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
    onSelectSection: (AppSection) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile, List<MediaFile>) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(AutoBackground, Color(0xFF130B24), AutoBackground),
                )
            ),
    ) {
        SideNavigation(
            selected = section,
            connected = state.sources.any(RemovableSource::connected),
            onSelect = onSelectSection,
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f))
                        .togetherWith(fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 1.01f))
                },
                label = "sectionTransition",
            ) { currentSection ->
                when (currentSection) {
                    AppSection.HOME -> HomeScreen(
                        state = state,
                        playbackStore = playbackStore,
                        onAddSource = onAddSource,
                        onRefresh = onRefresh,
                        onOpenFolder = onOpenFolder,
                        onPlay = { file -> onPlay(file, state.folders.flatMap(MediaFolder::files)) },
                    )

                    AppSection.VIDEO -> VideoFoldersScreen(
                        folders = state.videoFolders,
                        loading = state.loading,
                        onOpenFolder = onOpenFolder,
                    )

                    AppSection.AUDIO -> MediaLibraryScreen(
                        title = "Аудио",
                        files = state.audioFiles,
                        loading = state.loading,
                        emptyMessage = "Аудиофайлы во внутренней памяти и на носителях не найдены",
                        onPlay = { file -> onPlay(file, state.audioFiles) },
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
    connected: Boolean,
    onSelect: (AppSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .fillMaxHeight()
            .background(Color(0xED090710))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AV",
            color = AutoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(20.dp))
        NavigationItem("Главная", Icons.Rounded.Home, AppSection.HOME, selected, onSelect)
        NavigationItem("Видео", Icons.Rounded.VideoLibrary, AppSection.VIDEO, selected, onSelect)
        NavigationItem("Аудио", Icons.Rounded.Audiotrack, AppSection.AUDIO, selected, onSelect)
        NavigationItem("Настройки", Icons.Rounded.Settings, AppSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(11.dp)
                    .background(
                        if (connected) AutoGreen else Color(0xFF6B6478),
                        RoundedCornerShape(50),
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(if (connected) "Медиа" else "Нет", color = AutoMuted, fontSize = 12.sp)
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
        targetValue = if (active) 1.04f else 1f,
        animationSpec = tween(220),
        label = "navActiveScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
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
                .background(if (active) AutoPurple else Color(0xFF171320)),
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
    ) {
        onRefresh()
    }

    fun openPermissions() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        settingsLauncher.launch(intent)
    }

    Column(Modifier.fillMaxSize().padding(30.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Настройки", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Память, накопители и системные разрешения", color = AutoMuted, fontSize = 15.sp)
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
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF171320), RoundedCornerShape(20.dp))
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
                Text("Выдать все разрешения", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Кнопка открывает системную страницу приложения. Разрешите файлы, видео, аудио и управление памятью.",
                    color = AutoMuted,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Источники медиа", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        if (state.loading) {
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
                            Text(source.name, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            Text(
                                if (source.connected) "Подключён и доступен" else "Источник сейчас недоступен",
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
        Text("Медиафайлы пока не найдены", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
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
