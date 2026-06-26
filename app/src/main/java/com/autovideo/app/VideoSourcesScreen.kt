package com.autovideo.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Usb
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class VideoSourceRoute { SOURCES, LOCAL, RUTUBE, IPTV, LINK, PLAYER }

@Composable
fun VideoSourcesScreen(
    localCount: Int,
    sourceName: String?,
    onOpenLocal: () -> Unit,
    onOpenUsb: () -> Unit,
    onOpenRutube: () -> Unit,
    onOpenIptv: () -> Unit,
    onOpenLink: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF10091F), Color(0xFF0B1728))))
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Видео", color = AutoText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Локальные и онлайн-источники", color = AutoMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HeadUnitActionButton("Локальные · $localCount", Icons.Rounded.VideoLibrary, onOpenLocal)
            HeadUnitActionButton(sourceName ?: "USB", Icons.Rounded.Usb, onOpenUsb, backgroundColor = AutoCyan)
            HeadUnitActionButton("RUTUBE", Icons.Rounded.Language, onOpenRutube, backgroundColor = AutoBlue)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HeadUnitActionButton("IPTV и M3U", Icons.Rounded.LiveTv, onOpenIptv, backgroundColor = AutoPink)
            HeadUnitActionButton("Открыть ссылку", Icons.Rounded.Link, onOpenLink, backgroundColor = AutoGreen)
        }
    }
}

@Composable
fun VideoExperienceScreen(
    state: LibraryUiState,
    playbackStore: PlaybackStore,
    favorites: FavoritesState,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlay: (MediaFile) -> Unit,
    onToggleFolderFavorite: (MediaFolder) -> Unit,
    onToggleFileFavorite: (MediaFile) -> Unit,
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel()
    val streamingViewModel: StreamingViewModel = viewModel()
    val streamingState by streamingViewModel.uiState.collectAsStateWithLifecycle()
    var routeName by rememberSaveable { mutableStateOf(VideoSourceRoute.SOURCES.name) }
    var playerReturnRoute by rememberSaveable { mutableStateOf(VideoSourceRoute.SOURCES.name) }
    var streamUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var streamTitle by rememberSaveable { mutableStateOf("") }
    val route = runCatching { VideoSourceRoute.valueOf(routeName) }.getOrDefault(VideoSourceRoute.SOURCES)

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onSuccess { mainViewModel.addSource(uri) }
        }
    }
    val playlistPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) streamingViewModel.importPlaylist(uri)
    }

    fun openStream(stream: OnlineStream, returnRoute: VideoSourceRoute) {
        streamUrl = stream.url
        streamTitle = stream.title
        playerReturnRoute = returnRoute.name
        routeName = VideoSourceRoute.PLAYER.name
    }

    when (route) {
        VideoSourceRoute.SOURCES -> VideoSourcesScreen(
            localCount = state.videoFiles.size,
            sourceName = state.sources.firstOrNull()?.name,
            onOpenLocal = { routeName = VideoSourceRoute.LOCAL.name },
            onOpenUsb = { treePicker.launch(null) },
            onOpenRutube = { routeName = VideoSourceRoute.RUTUBE.name },
            onOpenIptv = { routeName = VideoSourceRoute.IPTV.name },
            onOpenLink = { routeName = VideoSourceRoute.LINK.name },
        )
        VideoSourceRoute.LOCAL -> VideoFoldersScreen(
            state, playbackStore, favorites, onOpenFolder, onPlay,
            onToggleFolderFavorite, onToggleFileFavorite,
        )
        VideoSourceRoute.RUTUBE -> RutubeScreen { routeName = VideoSourceRoute.SOURCES.name }
        VideoSourceRoute.IPTV -> IptvScreen(
            state = streamingState,
            onBack = { routeName = VideoSourceRoute.SOURCES.name },
            onImportPlaylist = {
                playlistPicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain"))
            },
            onLoadRemote = streamingViewModel::loadRemotePlaylist,
            onQueryChange = streamingViewModel::updateQuery,
            onGroupChange = streamingViewModel::selectGroup,
            onToggleFavorite = streamingViewModel::toggleFavorite,
            onOpenChannel = { channel ->
                streamingViewModel.recordOpened(channel)
                openStream(
                    OnlineStream(channel.streamUrl, channel.name),
                    VideoSourceRoute.IPTV,
                )
            },
        )
        VideoSourceRoute.LINK -> SecureStreamEntry(
            onBack = { routeName = VideoSourceRoute.SOURCES.name },
            onOpen = { stream -> openStream(stream, VideoSourceRoute.LINK) },
        )
        VideoSourceRoute.PLAYER -> {
            val url = streamUrl
            if (url != null) {
                Media3StreamScreen(
                    stream = OnlineStream(url, streamTitle.ifBlank { "Онлайн-видео" }),
                    onBack = { routeName = playerReturnRoute },
                )
            } else {
                VideoSourcesScreen(
                    state.videoFiles.size,
                    state.sources.firstOrNull()?.name,
                    { routeName = VideoSourceRoute.LOCAL.name },
                    { treePicker.launch(null) },
                    { routeName = VideoSourceRoute.RUTUBE.name },
                    { routeName = VideoSourceRoute.IPTV.name },
                    { routeName = VideoSourceRoute.LINK.name },
                )
            }
        }
    }
}
