package com.autovideo.app

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    file: MediaFile,
    playbackStore: PlaybackStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(file.uriString) {
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderers).build()
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }

    fun currentDuration(): Long {
        val value = player.duration
        return if (value == C.TIME_UNSET || value < 0L) 0L else value
    }

    fun saveProgress() {
        playbackStore.save(file, player.currentPosition, currentDuration())
    }

    fun seekBy(deltaMs: Long) {
        val maximum = currentDuration().takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, maximum)
        player.seekTo(target)
        positionMs = target
        seekFeedback = if (deltaMs < 0L) "−10 сек" else "+10 сек"
        controlsVisible = true
    }

    LaunchedEffect(player, file.uriString) {
        val resumeAt = playbackStore.position(file)
        player.setMediaItem(MediaItem.fromUri(file.uri))
        player.prepare()
        if (resumeAt > 0L) player.seekTo(resumeAt)
        player.playWhenReady = true

        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = currentDuration()
            isPlaying = player.isPlaying
            if (!scrubbing) sliderValue = positionMs.toFloat()
            delay(250L)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage ?: "Формат или кодек не поддерживается устройством"
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

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650L)
            seekFeedback = null
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) seekBy(-10_000L) else seekBy(10_000L)
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
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (file.isAudio) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF552184), Color(0xFF172B55)))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(76.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(file.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(file.folderName, color = AutoMuted, fontSize = 14.sp)
            }
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

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent))
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    saveProgress()
                    onBack()
                }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад", tint = Color.White)
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

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))
                    )
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                errorMessage?.let {
                    Text(it, color = Color(0xFFFF8A9A), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }

                val sliderMaximum = durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = sliderValue.coerceIn(0f, sliderMaximum),
                    onValueChange = {
                        scrubbing = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        player.seekTo(sliderValue.toLong())
                        positionMs = sliderValue.toLong()
                        scrubbing = false
                    },
                    valueRange = 0f..sliderMaximum,
                    colors = SliderDefaults.colors(
                        thumbColor = AutoPurple,
                        activeTrackColor = AutoPurple,
                        inactiveTrackColor = Color(0xFF4B4653),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatPlayerTime(positionMs), color = Color.White, fontSize = 12.sp)
                    Text(" / ${formatPlayerTime(durationMs)}", color = AutoMuted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { seekBy(-10_000L) }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Rounded.Replay10,
                                contentDescription = "Назад на 10 секунд",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                                controlsVisible = true
                            },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        IconButton(onClick = { seekBy(10_000L) }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Rounded.Forward10,
                                contentDescription = "Вперёд на 10 секунд",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Двойное касание: −10 / +10 сек", color = AutoMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatPlayerTime(valueMs: Long): String {
    if (valueMs <= 0L) return "00:00"
    val totalSeconds = valueMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
