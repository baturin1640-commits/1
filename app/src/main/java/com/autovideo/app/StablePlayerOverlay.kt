package com.autovideo.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

enum class PlayerDisplayMode {
    FULLSCREEN,
    WINDOWED,
}

private enum class DecoderMode {
    HARDWARE,
    SOFTWARE,
}

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
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val audioManager = remember(applicationContext) {
        applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val libVlc = remember(file.uriString) {
        LibVLC(
            applicationContext,
            arrayListOf(
                "--audio-time-stretch",
                "--no-spdif",
                "--file-caching=1500",
                "--network-caching=1500",
                "--drop-late-frames",
                "--skip-frames",
                "--no-video-title-show",
            ),
        )
    }
    val player = remember(file.uriString) { MediaPlayer(libVlc) }
    val playlist = remember(queue, file.uriString) {
        queue.distinctBy(MediaFile::uriString).let { distinct ->
            if (distinct.any { it.uriString == file.uriString }) distinct else listOf(file)
        }
    }
    val currentIndex = playlist.indexOfFirst { it.uriString == file.uriString }.coerceAtLeast(0)
    val previousFile = playlist.getOrNull(currentIndex - 1)
    val nextFile = playlist.getOrNull(currentIndex + 1)

    var videoLayout by remember(file.uriString) { mutableStateOf<VLCVideoLayout?>(null) }
    var decoderMode by remember(file.uriString) { mutableStateOf(DecoderMode.HARDWARE) }
    var fallbackAttempted by remember(file.uriString) { mutableStateOf(false) }
    var restartGeneration by remember(file.uriString) { mutableIntStateOf(0) }
    var requestedResumeMs by remember(file.uriString) {
        mutableLongStateOf(playbackStore.position(file))
    }
    var positionMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var durationMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var isPlaying by remember(file.uriString) { mutableStateOf(false) }
    var controlsVisible by remember(file.uriString) { mutableStateOf(true) }
    var errorMessage by remember(file.uriString) { mutableStateOf<String?>(null) }
    var feedback by remember(file.uriString) { mutableStateOf<String?>(null) }
    var sliderFraction by remember(file.uriString) { mutableFloatStateOf(0f) }
    var scrubbing by remember(file.uriString) { mutableStateOf(false) }
    var pendingSeekMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var dragStartPosition by remember(file.uriString) { mutableLongStateOf(0L) }
    var dragDistancePx by remember(file.uriString) { mutableFloatStateOf(0f) }
    var resumeAfterSeek by remember(file.uriString) { mutableStateOf(false) }

    val latestDecoderMode by rememberUpdatedState(decoderMode)
    val latestNextFile by rememberUpdatedState(nextFile)
    val latestSelectFile by rememberUpdatedState(onSelectFile)

    fun currentDuration(): Long = player.length.coerceAtLeast(0L)

    fun updateProgress(position: Long, duration: Long) {
        positionMs = position.coerceAtLeast(0L)
        durationMs = duration.coerceAtLeast(0L)
        sliderFraction = if (duration > 0L) {
            (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    fun saveProgress() {
        playbackStore.save(file, player.time.coerceAtLeast(0L), currentDuration())
    }

    fun seekTo(targetMs: Long, message: String? = null) {
        val length = currentDuration()
        if (length <= 0L) return
        val target = targetMs.coerceIn(0L, length)
        player.time = target
        pendingSeekMs = target
        updateProgress(target, length)
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
        latestSelectFile(target)
    }

    fun retryWithSoftwareDecoder() {
        requestedResumeMs = player.time.coerceAtLeast(playbackStore.position(file))
        fallbackAttempted = true
        decoderMode = DecoderMode.SOFTWARE
        errorMessage = null
        feedback = "Программный декодер"
        restartGeneration++
    }

    val audioFocusListener = remember(player) {
        AudioManager.OnAudioFocusChangeListener { change ->
            mainHandler.post {
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (player.isPlaying) player.pause()
                    AudioManager.AUDIOFOCUS_GAIN -> player.setVolume(100)
                }
            }
        }
    }

    DisposableEffect(player) {
        player.setEventListener { event ->
            mainHandler.post {
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        isPlaying = true
                        errorMessage = null
                        player.setVolume(100)
                    }

                    MediaPlayer.Event.Paused,
                    MediaPlayer.Event.Stopped -> isPlaying = false

                    MediaPlayer.Event.EndReached -> {
                        isPlaying = false
                        saveProgress()
                        latestNextFile?.let(latestSelectFile)
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        if (latestDecoderMode == DecoderMode.HARDWARE && !fallbackAttempted) {
                            requestedResumeMs = player.time.coerceAtLeast(playbackStore.position(file))
                            fallbackAttempted = true
                            decoderMode = DecoderMode.SOFTWARE
                            feedback = "Аппаратный декодер недоступен · повтор"
                            restartGeneration++
                        } else {
                            errorMessage = "Файл не удалось декодировать. Повторите запуск или проверьте целостность файла."
                            controlsVisible = true
                        }
                    }
                }
            }
        }

        onDispose {
            saveProgress()
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
            runCatching { audioManager.abandonAudioFocus(audioFocusListener) }
        }
    }

    LaunchedEffect(player, file.uriString, videoLayout, restartGeneration) {
        if (file.isVideo && videoLayout == null) return@LaunchedEffect

        runCatching {
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        errorMessage = null
        runCatching { player.stop() }
        player.setAudioOutput("android_audiotrack")
        player.setAudioDigitalOutputEnabled(false)
        player.setVolume(100)

        val mediaResult = runCatching {
            Media(libVlc, file.uri).apply {
                val hardware = decoderMode == DecoderMode.HARDWARE
                setHWDecoderEnabled(hardware, false)
                if (!hardware) addOption(":avcodec-hw=none")
                addOption(":no-spdif")
                addOption(":file-caching=1500")
                addOption(":network-caching=1500")
            }
        }
        val media = mediaResult.getOrElse {
            errorMessage = "Не удалось открыть файл. Возможно, накопитель отключён."
            return@LaunchedEffect
        }

        player.media = media
        media.release()
        if (!player.play()) {
            errorMessage = "Плеер не смог начать воспроизведение"
            return@LaunchedEffect
        }

        val resumeAt = requestedResumeMs.coerceAtLeast(0L)
        if (resumeAt > 0L) {
            repeat(50) {
                delay(80L)
                val length = currentDuration()
                if (length > 0L) {
                    val target = resumeAt.coerceIn(0L, length)
                    player.time = target
                    pendingSeekMs = target
                    updateProgress(target, length)
                    requestedResumeMs = 0L
                    return@repeat
                }
            }
        }

        while (isActive) {
            val length = currentDuration()
            val position = player.time.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            if (!scrubbing) {
                pendingSeekMs = position
                updateProgress(position, length)
            } else {
                durationMs = length
            }
            delay(250L)
        }
    }

    LaunchedEffect(feedback, scrubbing) {
        if (feedback != null && !scrubbing) {
            delay(1_100L)
            feedback = null
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, scrubbing, displayMode) {
        if (displayMode == PlayerDisplayMode.FULLSCREEN && controlsVisible && isPlaying && !scrubbing) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    val windowed = displayMode == PlayerDisplayMode.WINDOWED
    val playerShape: Shape = if (windowed) RoundedCornerShape(24.dp) else RectangleShape
    val controlsShown = controlsVisible || windowed || errorMessage != null
    val smallButton = if (windowed) 42.dp else 68.dp
    val smallIcon = if (windowed) 24.dp else 37.dp
    val playButton = if (windowed) 50.dp else 82.dp
    val playIcon = if (windowed) 30.dp else 47.dp

    Box(
        modifier = modifier
            .clip(playerShape)
            .background(Color.Black)
            .pointerInput(player, durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragStartPosition = player.time.coerceAtLeast(0L)
                        pendingSeekMs = dragStartPosition
                        dragDistancePx = 0f
                        resumeAfterSeek = player.isPlaying
                        if (resumeAfterSeek) player.pause()
                        scrubbing = true
                        controlsVisible = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragDistancePx += dragAmount
                        val length = currentDuration()
                        if (length > 0L && size.width > 0) {
                            val delta = (dragDistancePx / size.width.toFloat() * length).toLong()
                            val target = (dragStartPosition + delta).coerceIn(0L, length)
                            pendingSeekMs = target
                            updateProgress(target, length)
                            feedback = (if (delta >= 0L) "+" else "−") +
                                formatStableTime(abs(delta))
                        }
                    },
                    onDragEnd = {
                        seekTo(pendingSeekMs)
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
                    onTap = { controlsVisible = !controlsVisible },
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
                    layout.requestLayout()
                    layout.invalidate()
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
                        .size(if (windowed) 78.dp else 180.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (windowed) 42.dp else 92.dp),
                    )
                }
            }
        }

        feedback?.let { value ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE5070610))
                    .padding(horizontal = if (windowed) 12.dp else 24.dp, vertical = 10.dp),
            ) {
                Text(
                    value,
                    color = Color.White,
                    fontSize = if (windowed) 12.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE8000000), Color.Transparent)))
                    .padding(horizontal = if (windowed) 8.dp else 16.dp, vertical = if (windowed) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        color = Color.White,
                        fontSize = if (windowed) 12.sp else 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!windowed) {
                        Text(
                            if (decoderMode == DecoderMode.HARDWARE) "Аппаратный декодер" else "Программный декодер",
                            color = AutoMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                HeadUnitIconButton(
                    icon = if (windowed) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                    contentDescription = if (windowed) "На весь экран" else "В оконный режим",
                    onClick = {
                        onDisplayModeChange(
                            if (windowed) PlayerDisplayMode.FULLSCREEN else PlayerDisplayMode.WINDOWED
                        )
                    },
                    size = if (windowed) 40.dp else 56.dp,
                    iconSize = if (windowed) 24.dp else 31.dp,
                    backgroundColor = Color(0xA31A1724),
                )
                Spacer(Modifier.width(if (windowed) 5.dp else 8.dp))
                HeadUnitIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Закрыть видео",
                    onClick = {
                        saveProgress()
                        onClose()
                    },
                    size = if (windowed) 40.dp else 56.dp,
                    iconSize = if (windowed) 24.dp else 31.dp,
                    backgroundColor = Color(0xB33A1721),
                )
            }
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEB120D1A))
                    .padding(if (windowed) 12.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    message,
                    color = Color(0xFFFFB0BB),
                    fontSize = if (windowed) 11.sp else 15.sp,
                    maxLines = if (windowed) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                HeadUnitActionButton(
                    text = "Повторить",
                    icon = Icons.Rounded.Refresh,
                    onClick = ::retryWithSoftwareDecoder,
                    backgroundColor = AutoPurple,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF4000000))))
                    .padding(
                        horizontal = if (windowed) 10.dp else 24.dp,
                        vertical = if (windowed) 8.dp else 18.dp,
                    ),
            ) {
                Slider(
                    value = sliderFraction.coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        val length = durationMs
                        if (length <= 0L) return@Slider
                        if (!scrubbing) {
                            resumeAfterSeek = player.isPlaying
                            if (resumeAfterSeek) player.pause()
                        }
                        scrubbing = true
                        sliderFraction = fraction.coerceIn(0f, 1f)
                        pendingSeekMs = (length.toDouble() * sliderFraction.toDouble()).toLong()
                        positionMs = pendingSeekMs
                    },
                    onValueChangeFinished = {
                        seekTo(pendingSeekMs)
                        scrubbing = false
                        if (resumeAfterSeek) player.play()
                    },
                    enabled = durationMs > 0L,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = AutoPink,
                        activeTrackColor = AutoPurple,
                        inactiveTrackColor = Color(0xFF4B4653),
                    ),
                    modifier = Modifier.height(if (windowed) 26.dp else 38.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!windowed) {
                        Text(
                            "${formatStableTime(positionMs)} / ${formatStableTime(durationMs)}",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    Row(
                        modifier = if (windowed) Modifier.fillMaxWidth() else Modifier,
                        horizontalArrangement = if (windowed) Arrangement.SpaceEvenly else Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeadUnitIconButton(
                            Icons.Rounded.SkipPrevious,
                            "Предыдущий файл",
                            onClick = { switchTo(previousFile) },
                            enabled = previousFile != null,
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.Replay10,
                            "Назад на 10 секунд",
                            onClick = { seekBy(-10_000L) },
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                        HeadUnitIconButton(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Пауза" else "Воспроизвести",
                            onClick = ::togglePlayback,
                            size = playButton,
                            iconSize = playIcon,
                            backgroundColor = AutoPurple,
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.Forward10,
                            "Вперёд на 10 секунд",
                            onClick = { seekBy(10_000L) },
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.SkipNext,
                            "Следующий файл",
                            onClick = { switchTo(nextFile) },
                            enabled = nextFile != null,
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                    }
                }
            }
        }
    }
}

private fun formatStableTime(valueMs: Long): String {
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
