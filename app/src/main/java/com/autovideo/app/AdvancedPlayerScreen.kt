package com.autovideo.app

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun AdvancedPlayerScreen(
    file: MediaFile,
    queue: List<MediaFile>,
    playbackStore: PlaybackStore,
    onSelectFile: (MediaFile) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(file.uriString) {
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderers).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            volume = 1f
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }
    }

    val playlist = remember(queue, file.uriString) {
        val distinct = queue.distinctBy(MediaFile::uriString)
        if (distinct.any { it.uriString == file.uriString }) distinct else listOf(file)
    }
    val index = playlist.indexOfFirst { it.uriString == file.uriString }.coerceAtLeast(0)
    val previousFile = playlist.getOrNull(index - 1)
    val nextFile = playlist.getOrNull(index + 1)

    var positionMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var durationMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var isPlaying by remember(file.uriString) { mutableStateOf(false) }
    var controlsVisible by remember(file.uriString) { mutableStateOf(true) }
    var seekFeedback by remember(file.uriString) { mutableStateOf<String?>(null) }
    var errorMessage by remember(file.uriString) { mutableStateOf<String?>(null) }
    var sliderValue by remember(file.uriString) { mutableFloatStateOf(0f) }
    var scrubbing by remember(file.uriString) { mutableStateOf(false) }
    var dragStartPosition by remember(file.uriString) { mutableLongStateOf(0L) }
    var dragDistance by remember(file.uriString) { mutableFloatStateOf(0f) }
    var resumeAfterSeek by remember(file.uriString) { mutableStateOf(false) }

    fun duration(): Long = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

    fun saveProgress() {
        playbackStore.save(file, player.currentPosition, duration())
    }

    fun seekTo(value: Long, message: String? = null) {
        val maximum = duration().takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = value.coerceIn(0L, maximum)
        player.seekTo(target)
        positionMs = target
        sliderValue = target.toFloat()
        seekFeedback = message
        controlsVisible = true
    }

    fun seekBy(delta: Long) {
        seekTo(
            player.currentPosition + delta,
            if (delta < 0L) "−10 сек" else "+10 сек",
        )
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        controlsVisible = true
    }

    fun switchTo(target: MediaFile?) {
        if (target == null) return
        saveProgress()
        onSelectFile(target)
    }

    LaunchedEffect(player, file.uriString) {
        player.setMediaItem(MediaItem.fromUri(file.uri))
        player.prepare()
        val resume = playbackStore.position(file)
        if (resume > 0L) player.seekTo(resume)
        player.playWhenReady = true

        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = duration()
            isPlaying = player.isPlaying
            if (!scrubbing) sliderValue = positionMs.toFloat()
            delay(200L)
        }
    }

    DisposableEffect(player, nextFile?.uriString) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    nextFile?.let(onSelectFile)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Не удалось воспроизвести файл или его аудиодорожку"
                controlsVisible = true
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(seekFeedback, scrubbing) {
        if (seekFeedback != null && !scrubbing) {
            delay(850L)
            seekFeedback = null
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, scrubbing) {
        if (controlsVisible && isPlaying && !scrubbing) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(player, durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragStartPosition = player.currentPosition.coerceAtLeast(0L)
                        dragDistance = 0f
                        resumeAfterSeek = player.isPlaying
                        if (resumeAfterSeek) player.pause()
                        scrubbing = true
                        controlsVisible = true
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount
                        val maximum = duration()
                        if (maximum > 0L && size.width > 0) {
                            val delta = (dragDistance / size.width.toFloat() * maximum).toLong()
                            val target = (dragStartPosition + delta).coerceIn(0L, maximum)
                            positionMs = target
                            sliderValue = target.toFloat()
                            seekFeedback = (if (delta >= 0L) "+" else "−") +
                                formatPlayerTime(abs(delta)) + " · " + formatPlayerTime(target)
                        }
                    },
                    onDragEnd = {
                        seekTo(sliderValue.toLong())
                        scrubbing = false
                        if (resumeAfterSeek) player.play()
                    },
                    onDragCancel = {
                        seekTo(dragStartPosition)
                        scrubbing = false
                        if (resumeAfterSeek) player.play()
                    },
                )
            }
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { point ->
                        val centerX = point.x in size.width * 0.25f..size.width * 0.75f
                        val centerY = point.y in size.height * 0.20f..size.height * 0.80f
                        if (centerX && centerY) togglePlayback()
                        else controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { point ->
                        if (point.x < size.width / 2f) seekBy(-10_000L) else seekBy(10_000L)
                    },
                )
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (file.isAudio) {
            AudioPlaceholder(file)
        }

        seekFeedback?.let { feedback ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD905040A))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(feedback, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(controlsVisible, Modifier.align(Alignment.TopCenter)) {
            PlayerTopBar(file = file, onBack = {
                saveProgress()
                onBack()
            })
        }

        AnimatedVisibility(controlsVisible, Modifier.align(Alignment.BottomCenter)) {
            PlayerBottomBar(
                positionMs = positionMs,
                durationMs = durationMs,
                sliderValue = sliderValue,
                isPlaying = isPlaying,
                errorMessage = errorMessage,
                previousEnabled = previousFile != null,
                nextEnabled = nextFile != null,
                onSliderChange = {
                    if (!scrubbing) {
                        resumeAfterSeek = player.isPlaying
                        if (resumeAfterSeek) player.pause()
                    }
                    scrubbing = true
                    sliderValue = it
                    positionMs = it.toLong()
                },
                onSliderFinished = {
                    seekTo(sliderValue.toLong())
                    scrubbing = false
                    if (resumeAfterSeek) player.play()
                },
                onPrevious = { switchTo(previousFile) },
                onReplay10 = { seekBy(-10_000L) },
                onToggle = ::togglePlayback,
                onForward10 = { seekBy(10_000L) },
                onNext = { switchTo(nextFile) },
            )
        }
    }
}

@Composable
private fun AudioPlaceholder(file: MediaFile) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF552184), Color(0xFF172B55)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Audiotrack, null, tint = Color.White, modifier = Modifier.size(76.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(file.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(file.folderName, color = AutoMuted, fontSize = 14.sp)
    }
}

@Composable
private fun PlayerTopBar(file: MediaFile, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(file.sourceName, color = Color(0xFFB6B0C3), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerBottomBar(
    positionMs: Long,
    durationMs: Long,
    sliderValue: Float,
    isPlaying: Boolean,
    errorMessage: String?,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onPrevious: () -> Unit,
    onReplay10: () -> Unit,
    onToggle: () -> Unit,
    onForward10: () -> Unit,
    onNext: () -> Unit,
) {
    val maximum = durationMs.coerceAtLeast(1L).toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000))))
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        errorMessage?.let {
            Text(it, color = Color(0xFFFF8A9A), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        Slider(
            value = sliderValue.coerceIn(0f, maximum),
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            valueRange = 0f..maximum,
            colors = SliderDefaults.colors(
                thumbColor = AutoPurple,
                activeTrackColor = AutoPurple,
                inactiveTrackColor = Color(0xFF4B4653),
            ),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatPlayerTime(positionMs), color = Color.White, fontSize = 12.sp)
            Text(" / ${formatPlayerTime(durationMs)}", color = AutoMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(Icons.Rounded.SkipPrevious, "Предыдущий файл", previousEnabled, onPrevious)
                PlayerIconButton(Icons.Rounded.Replay10, "Назад на 10 секунд", true, onReplay10)
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(62.dp)) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (isPlaying) "Пауза" else "Воспроизвести",
                        modifier = Modifier.size(34.dp),
                    )
                }
                PlayerIconButton(Icons.Rounded.Forward10, "Вперёд на 10 секунд", true, onForward10)
                PlayerIconButton(Icons.Rounded.SkipNext, "Следующий файл", nextEnabled, onNext)
            }
            Spacer(Modifier.weight(1f))
            Text("Свайп по видео — перемотка", color = AutoMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(50.dp)) {
        Icon(
            icon,
            description,
            tint = if (enabled) Color.White else Color(0xFF635E69),
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun formatPlayerTime(valueMs: Long): String {
    if (valueMs <= 0L) return "00:00"
    val totalSeconds = valueMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
