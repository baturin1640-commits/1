package com.autovideo.app

import androidx.compose.runtime.Composable

@Composable
fun PlayerScreen(
    file: MediaFile,
    queue: List<MediaFile>,
    playbackStore: PlaybackStore,
    onSelectFile: (MediaFile) -> Unit,
    onBack: () -> Unit,
) {
    AdvancedPlayerScreen(
        file = file,
        queue = queue,
        playbackStore = playbackStore,
        onSelectFile = onSelectFile,
        onBack = onBack,
    )
}
