package com.autovideo.app

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
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val playbackStore = remember { PlaybackStore(context.applicationContext) }
    var section by remember { mutableStateOf(AppSection.HOME) }
    var selectedFolder by remember { mutableStateOf<MediaFolder?>(null) }
    var playingFile by remember { mutableStateOf<MediaFile?>(null) }

    Surface(color = AutoBackground, modifier = Modifier.fillMaxSize()) {
        when {
            playingFile != null -> PlayerScreen(
                file = checkNotNull(playingFile),
                playbackStore = playbackStore,
                onBack = { playingFile = null },
            )

            selectedFolder != null -> FolderScreen(
                folder = checkNotNull(selectedFolder),
                onBack = { selectedFolder = null },
                onPlay = { playingFile = it },
            )

            else -> Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AutoBackground, Color(0xFF130B24), AutoBackground),
                        )
                    )
            ) {
                SideNavigation(
                    selected = section,
                    connected = state.sources.any(RemovableSource::connected),
                    onSelect = { section = it },
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (section) {
                        AppSection.HOME -> HomeScreen(
                            state = state,
                            playbackStore = playbackStore,
                            onAddSource = onAddSource,
                            onRefresh = onRefresh,
                            onOpenFolder = { selectedFolder = it },
                            onPlay = { playingFile = it },
                        )

                        AppSection.VIDEO -> MediaLibraryScreen(
                            title = "Все видео",
                            files = state.videoFiles,
                            loading = state.loading,
                            emptyMessage = "Видео на подключённых носителях не найдено",
                            onPlay = { playingFile = it },
                        )

                        AppSection.AUDIO -> MediaLibraryScreen(
                            title = "Аудио",
                            files = state.audioFiles,
                            loading = state.loading,
                            emptyMessage = "Аудиофайлы на подключённых носителях не найдены",
                            onPlay = { playingFile = it },
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
}

@Composable
private fun SideNavigation(
    selected: AppSection,
    connected: Boolean,
    onSelect: (AppSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(Color(0xDD090710))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AV",
            color = AutoText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(22.dp))
        NavigationItem("Главная", Icons.Rounded.Home, AppSection.HOME, selected, onSelect)
        NavigationItem("Видео", Icons.Rounded.VideoLibrary, AppSection.VIDEO, selected, onSelect)
        NavigationItem("Аудио", Icons.Rounded.Audiotrack, AppSection.AUDIO, selected, onSelect)
        NavigationItem("Настройки", Icons.Rounded.Settings, AppSection.SETTINGS, selected, onSelect)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (connected) AutoGreen else Color(0xFF6B6478))
            )
            Spacer(Modifier.width(5.dp))
            Text(if (connected) "USB" else "Нет", color = AutoMuted, fontSize = 10.sp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = { onSelect(section) },
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) AutoPurple else Color.Transparent),
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Color.White else AutoMuted)
        }
        Text(label, color = if (active) AutoText else AutoMuted, fontSize = 9.sp)
    }
}

@Composable
private fun SourcesScreen(
    state: LibraryUiState,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Носители", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Подключайте флешки и внешние диски", color = AutoMuted)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onAddSource,
                colors = ButtonDefaults.buttonColors(containerColor = AutoPurple),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить носитель")
            }
        }

        Spacer(Modifier.height(28.dp))
        if (state.loading) {
            CircularProgressIndicator(color = AutoPurple)
        } else if (state.sources.isEmpty()) {
            EmptySourcesCard(onAddSource)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.sources, key = { it.uriString }) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(AutoSurfaceHigh)
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Usb,
                            contentDescription = null,
                            tint = if (source.connected) AutoGreen else AutoMuted,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(source.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Text(
                                if (source.connected) "Подключён и доступен" else "Носитель сейчас недоступен",
                                color = if (source.connected) AutoGreen else AutoMuted,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onRemoveSource(source.uriString) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", tint = AutoMuted)
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
            .clip(RoundedCornerShape(22.dp))
            .background(AutoSurfaceHigh)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Usb, contentDescription = null, tint = AutoPurple, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        Text("Носитель ещё не выбран", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Выберите корневую папку флешки или внешнего диска", color = AutoMuted)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onAddSource, colors = ButtonDefaults.buttonColors(containerColor = AutoPurple)) {
            Text("Выбрать носитель")
        }
    }
}
