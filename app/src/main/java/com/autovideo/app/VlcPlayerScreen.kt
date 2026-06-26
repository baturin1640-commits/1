package com.autovideo.app

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Icon
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

@Composable
fun VlcPlayerScreen(
    file: MediaFile,
    queue: List<MediaFile>,
    playbackStore: PlaybackStore,
    onSelectFile: (MediaFile) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val libVlc = remember(file.uriString) {
        LibVLC(
            context,
            arrayListOf(
                "--audio-time-stretch",
                "--avcodec-hw=any",
                "--no-spdif",
                "--file-caching=1200",
                "--drop-late-frames",
                "--skip-frames",
            ),
        )
    }
    val player = remember(file.uriString) { MediaPlayer(libVlc) }

    val playlist = remember(queue, file.uriString) {
        val distinct = queue.distinctBy(MediaFile::uriString)
        if (distinct.any { it.uriString == file.uriString }) distinct else listOf(file)
    }
    val currentIndex = playlist.indexOfFirst { it.uriString == file.uriString }.coerceAtLeast(0)
    val previousFile = playlist.getOrNull(currentIndex - 1)
    val nextFile = playlist.getOrNull(currentIndex + 1)

    var videoLayout by remember(file.uriString) { mutableStateOf<VLCVideoLayout?>(null) }
    var playbackStarted by remember(file.uriString) { mutableStateOf(false) }
    var positionMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var durationMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var isPlaying by remember(file.uriString) { mutableStateOf(false) }
    var controlsVisible by remember(file.uriString) { mutableStateOf(true) }
    var feedback by remember(file.uriString) { mutableStateOf<String?>(null) }
    var errorMessage by remember(file.uriString) { mutableStateOf<String?>(null) }
    var sliderValue by remember(file.uriString) { mutableFloatStateOf(0f) }
    var scrubbing by remember(file.uriString) { mutableStateOf(false) }
    var dragStartPosition by remember(file.uriString) { mutableLongStateOf(0L) }
    var dragDistancePx by remember(file.uriString) { mutableFloatStateOf(0f) }
    var resumeAfterSeek by remember(file.uriString) { mutableStateOf(false) }
    var audioTrackConfigured by remember(file.uriString) { mutableStateOf(false) }

    fun currentDuration(): Long = player.length.coerceAtLeast(0L)

    fun saveProgress() {
        playbackStore.save(file, player.time.coerceAtLeast(0L), currentDuration())
    }

    fun seekTo(targetMs: Long, message: String? = null) {
        val maximum = currentDuration().takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = targetMs.coerceIn(0L, maximum)
        player.time = target
        positionMs = target
        sliderValue = target.toFloat()
        feedback = message
        controlsVisible = true
    }

    fun seekBy(deltaMs: Long) {
        seekTo(
            player.time.coerceAtLeast(0L) + deltaMs,
            if (deltaMs < 0L) "−10 сек" else "+10 сек",
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

    val audioFocusListener = remember(player) {
        AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (player.isPlaying) player.pause()
                AudioManager.AUDIOFOCUS_GAIN -> player.setVolume(100)
            }
        }
    }

    DisposableEffect(player, nextFile?.uriString) {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    player.setVolume(100)
                    if (!audioTrackConfigured) {
                        player.setAudioDigitalOutputEnabled(false)
                        player.setAudioOutputDevice("stereo")
                        if (player.audioTrack == -1) {
                            player.audioTracks
                                ?.firstOrNull { it.id >= 0 }
                                ?.let { player.setAudioTrack(it.id) }
                        }
                        audioTrackConfigured = true
                    }
                }

                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped -> isPlaying = false

                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    saveProgress()
                    nextFile?.let(onSelectFile)
                }

                MediaPlayer.Event.EncounteredError -> {
                    errorMessage = "Не удалось воспроизвести файл или его аудиодорожку"
                    controlsVisible = true
                }
            }
        }

        onDispose {
            saveProgress()
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
            runCatching { audioManager.abandonAudioFocus(audioFocusListener) }
        }
    }

    LaunchedEffect(player, file.uriString, videoLayout) {
        if (playbackStarted) return@LaunchedEffect
        if (file.isVideo && videoLayout == null) return@LaunchedEffect
        playbackStarted = true

        runCatching {
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        player.setAudioOutput("android_audiotrack")
        player.setAudioDigitalOutputEnabled(false)
        player.setAudioOutputDevice("stereo")
        player.setVolume(100)

        val media = Media(libVlc, file.uri).apply {
            setHWDecoderEnabled(true, false)
            addOption(":no-spdif")
            addOption(":audio-track=-1")
            addOption(":file-caching=1200")
        }
        player.media = media
        media.release()
        player.play()

        val resumeAt = playbackStore.position(file)
        if (resumeAt > 0L) {
            for (attempt in 0 until 30) {
                delay(100L)
                if (player.length > 0L) {
                    player.time = resumeAt
                    positionMs = resumeAt
                    sliderValue = resumeAt.toFloat()
                    break
                }
            }
        }

        while (isActive) {
            positionMs = player.time.coerceAtLeast(0L)
            durationMs = currentDuration()
            isPlaying = player.isPlaying
            if (!scrubbing) sliderValue = positionMs.toFloat()
            delay(250L)
        }
    }

    DisposableEffect(lifecycleOwner, player, videoLayout) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && file.isVideo) {
                videoLayout?.let { layout ->
                    runCatching {
                        player.detachViews()
                        player.attachViews(layout, null, true, false)
                        layout.requestLayout()
                        layout.invalidate()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(feedback, scrubbing) {
        if (feedback != null && !scrubbing) {
            delay(900L)
            feedback = null
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
                        dragStartPosition = player.time.coerceAtLeast(0L)
                        dragDistancePx = 0f
                        resumeAfterSeek = player.isPlaying
                        if (resumeAfterSeek) player.pause()
                        scrubbing = true
                        controlsVisible = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragDistancePx += dragAmount
                        val maximum = currentDuration()
                        if (maximum > 0L && size.width > 0) {
                            val delta = (dragDistancePx / size.width.toFloat() * maximum).toLong()
                            val target = (dragStartPosition + delta).coerceIn(0L, maximum)
                            positionMs = target
                            sliderValue = target.toFloat()
                            feedback = (if (delta >= 0L) "+" else "−") +
                                formatVlcTime(abs(delta)) + " · " + formatVlcTime(target)
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
        if (file.isVideo) {
            AndroidView(
                factory = { viewContext ->
                    VLCVideoLayout(viewContext).also { layout ->
                        layout.keepScreenOn = true
                        videoLayout = layout
                        player.attachViews(layout, null, true, false)
                    }
                },
                update = { layout ->
                    videoLayout = layout
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF552184), Color(0xFF172B55)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(92.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(file.name, color = AutoText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(file.folderName, color = AutoMuted, fontSize = 16.sp)
            }
        }

        feedback?.let { value ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xE005040A))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeadUnitIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    onClick = {
                        saveProgress()
                        onBack()
                    },
                    size = 64.dp,
                    iconSize = 34.dp,
                    backgroundColor = Color(0x99000000),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(file.sourceName, color = Color(0xFFB6B0C3), fontSize = 13.sp)
                }
                AppClock(compact = true)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000))))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                errorMessage?.let {
                    Text(it, color = Color(0xFFFF8A9A), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                val maximum = durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = sliderValue.coerceIn(0f, maximum),
                    onValueChange = {
                        if (!scrubbing) {
                            resumeAfterSeek = player.isPlaying
                            if (resumeAfterSeek) player.pause()
                        }
                        scrubbing = true
                        sliderValue = it
                        positionMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        seekTo(sliderValue.toLong())
                        scrubbing = false
                        if (resumeAfterSeek) player.play()
                    },
                    valueRange = 0f..maximum,
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
                    Text(formatVlcTime(positionMs), color = Color.White, fontSize = 14.sp)
                    Text(" / ${formatVlcTime(durationMs)}", color = AutoMuted, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeadUnitIconButton(
                            Icons.Rounded.SkipPrevious,
                            "Предыдущий файл",
                            onClick = { switchTo(previousFile) },
                            size = 68.dp,
                            iconSize = 38.dp,
                            backgroundColor = Color(0xA5171320),
                            enabled = previousFile != null,
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.Replay10,
                            "Назад на 10 секунд",
                            onClick = { seekBy(-10_000L) },
                            size = 68.dp,
                            iconSize = 38.dp,
                            backgroundColor = Color(0xA5171320),
                        )
                        HeadUnitIconButton(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Пауза" else "Воспроизвести",
                            onClick = ::togglePlayback,
                            size = 82.dp,
                            iconSize = 46.dp,
                            backgroundColor = AutoPurple,
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.Forward10,
                            "Вперёд на 10 секунд",
                            onClick = { seekBy(10_000L) },
                            size = 68.dp,
                            iconSize = 38.dp,
                            backgroundColor = Color(0xA5171320),
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.SkipNext,
                            "Следующий файл",
                            onClick = { switchTo(nextFile) },
                            size = 68.dp,
                            iconSize = 38.dp,
                            backgroundColor = Color(0xA5171320),
                            enabled = nextFile != null,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Свайп по видео — перемотка", color = AutoMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatVlcTime(valueMs: Long): String {
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
