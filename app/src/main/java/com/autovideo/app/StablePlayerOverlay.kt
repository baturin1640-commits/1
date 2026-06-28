package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class PlayerDisplayMode { FULLSCREEN, WINDOWED }

@Composable
fun StablePlayerOverlay(
    file: MediaFile,
    queue: List<MediaFile>,
    playbackStore: PlaybackStore,
    displayMode: PlayerDisplayMode,
    onDisplayModeChange: (PlayerDisplayMode) -> Unit,
    onSelectFile: (MediaFile) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowed = displayMode == PlayerDisplayMode.WINDOWED
    Box(
        modifier = modifier
            .clip(if (windowed) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp))
            .background(Color.Black),
    ) {
        VlcPlayerScreen(
            file = file,
            queue = queue,
            playbackStore = playbackStore,
            onSelectFile = onSelectFile,
            onBack = { if (windowed) onClose() else onDisplayModeChange(PlayerDisplayMode.WINDOWED) },
        )
        if (windowed) {
            HeadUnitIconButton(
                icon = Icons.Rounded.Fullscreen,
                contentDescription = "На весь экран",
                onClick = { onDisplayModeChange(PlayerDisplayMode.FULLSCREEN) },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 78.dp),
                size = 56.dp,
                iconSize = 30.dp,
                backgroundColor = Color(0xB0000000),
            )
            HeadUnitIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Закрыть плеер",
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                size = 56.dp,
                iconSize = 30.dp,
                backgroundColor = Color(0xB0000000),
            )
        }
    }
}
