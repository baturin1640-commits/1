package com.autovideo.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.roundToInt

private enum class PlayerDragMode {
    NONE,
    SEEK,
    VOLUME,
    BRIGHTNESS,
}

@Composable
fun ReliablePlayerOverlay(
    file: MediaFile,
    queue: List<MediaFile>,
    playbackStore: PlaybackStore,
    displayMode: PlayerDisplayMode,
    onDisplayModeChange: (PlayerDisplayMode) -> Unit,
    onSelectFile: (MediaFile) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostContext = LocalContext.current
    val context = hostContext.applicationContext
    val activity = remember(hostContext) { hostContext.findPlayerActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val libVlc = remember(file.uriString) {
        LibVLC(
            context,
            arrayListOf(
                "--audio-time-stretch",
                "--no-spdif",
                "--file-caching=3000",
                "--network-caching=3000",
                "--drop-late-frames",
                "--skip-frames",
                "--avcodec-fast",
                "--no-video-title-show",
            ),
        )
    }
    val player = remember(file.uriString) { MediaPlayer(libVlc) }
    val playlist = remember(queue, file.uriString) {
        queue.filter(MediaFile::isVideo)
            .distinctBy(MediaFile::uriString)
            .let { items -> if (items.any { it.uriString == file.uriString }) items else listOf(file) }
    }
    val currentIndex = playlist.indexOfFirst { it.uriString == file.uriString }.coerceAtLeast(0)
    val previousFile = playlist.getOrNull(currentIndex - 1)
    val nextFile = playlist.getOrNull(currentIndex + 1)

    var videoLayout by remember(file.uriString) { mutableStateOf<VLCVideoLayout?>(null) }
    var openedDescriptor by remember(file.uriString) { mutableStateOf<AssetFileDescriptor?>(null) }
    var softwareDecoder by remember(file.uriString) { mutableStateOf(false) }
    var fallbackAttempted by remember(file.uriString) { mutableStateOf(false) }
    var restartToken by remember(file.uriString) { mutableIntStateOf(0) }
    var requestedPosition by remember(file.uriString) {
        mutableLongStateOf(playbackStore.position(file))
    }
    var positionMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var durationMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var pendingSeekMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var sliderFraction by remember(file.uriString) { mutableFloatStateOf(0f) }
    var isPlaying by remember(file.uriString) { mutableStateOf(false) }
    var controlsVisible by remember(file.uriString) { mutableStateOf(true) }
    var scrubbing by remember(file.uriString) { mutableStateOf(false) }
    var resumeAfterSeek by remember(file.uriString) { mutableStateOf(false) }
    var dragMode by remember(file.uriString) { mutableStateOf(PlayerDragMode.NONE) }
    var dragStartX by remember(file.uriString) { mutableFloatStateOf(0f) }
    var dragDistanceX by remember(file.uriString) { mutableFloatStateOf(0f) }
    var dragDistanceY by remember(file.uriString) { mutableFloatStateOf(0f) }
    var dragStartMs by remember(file.uriString) { mutableLongStateOf(0L) }
    var volumeStart by remember(file.uriString) { mutableIntStateOf(0) }
    var brightnessStart by remember(file.uriString) { mutableFloatStateOf(0.5f) }
    var message by remember(file.uriString) { mutableStateOf<String?>(null) }
    var error by remember(file.uriString) { mutableStateOf<String?>(null) }

    val latestSelect by rememberUpdatedState(onSelectFile)
    val latestNext by rememberUpdatedState(nextFile)
    val latestSoftware by rememberUpdatedState(softwareDecoder)
    val latestFallback by rememberUpdatedState(fallbackAttempted)

    fun duration(): Long = player.length.coerceAtLeast(0L)

    fun updateProgress(position: Long, total: Long) {
        positionMs = position.coerceAtLeast(0L)
        durationMs = total.coerceAtLeast(0L)
        sliderFraction = if (total > 0L) {
            (position.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    fun savePosition() {
        playbackStore.save(file, player.time.coerceAtLeast(0L), duration())
    }

    fun seekTo(targetMs: Long, feedback: String? = null) {
        val total = duration()
        if (total <= 0L) return
        val target = targetMs.coerceIn(0L, total)
        player.time = target
        pendingSeekMs = target
        updateProgress(target, total)
        message = feedback
        controlsVisible = true
    }

    fun switchFile(target: MediaFile?) {
        if (target == null) return
        savePosition()
        latestSelect(target)
    }

    fun restartInSoftwareMode() {
        requestedPosition = player.time.coerceAtLeast(playbackStore.position(file))
        softwareDecoder = true
        fallbackAttempted = true
        error = null
        message = "Включены встроенные программные кодеки"
        restartToken++
    }

    fun currentWindowBrightness(): Float {
        val explicit = activity?.window?.attributes?.screenBrightness ?: -1f
        if (explicit >= 0f) return explicit.coerceIn(0.05f, 1f)
        val systemValue = runCatching {
            Settings.System.getInt(hostContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        return (systemValue / 255f).coerceIn(0.05f, 1f)
    }

    fun setWindowBrightness(value: Float) {
        val target = value.coerceIn(0.05f, 1f)
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = target
        }
    }

    val focusListener = remember(player) {
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
                        error = null
                        player.setVolume(100)
                    }
                    MediaPlayer.Event.Paused,
                    MediaPlayer.Event.Stopped -> isPlaying = false
                    MediaPlayer.Event.EndReached -> {
                        isPlaying = false
                        savePosition()
                        latestNext?.let(latestSelect)
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        if (!latestSoftware && !latestFallback) {
                            requestedPosition = player.time.coerceAtLeast(playbackStore.position(file))
                            softwareDecoder = true
                            fallbackAttempted = true
                            message = "Аппаратный декодер недоступен · переключение"
                            restartToken++
                        } else {
                            error = "Файл не удалось открыть. Нажмите «Повторить» — плеер заново подключит накопитель и встроенные кодеки."
                            controlsVisible = true
                        }
                    }
                }
            }
        }

        onDispose {
            savePosition()
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { openedDescriptor?.close() }
            openedDescriptor = null
            runCatching { player.release() }
            runCatching { libVlc.release() }
            runCatching { audioManager.abandonAudioFocus(focusListener) }
        }
    }

    LaunchedEffect(player, videoLayout, restartToken, file.uriString) {
        if (videoLayout == null) return@LaunchedEffect

        runCatching {
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        error = null
        runCatching { player.stop() }
        runCatching { openedDescriptor?.close() }
        openedDescriptor = null
        player.setAudioOutput("android_audiotrack")
        player.setAudioDigitalOutputEnabled(false)
        player.setVolume(100)

        val media = runCatching {
            openVlcMedia(context, libVlc, file).also { opened ->
                openedDescriptor = opened.descriptor
            }.media.apply {
                setHWDecoderEnabled(!softwareDecoder, false)
                if (softwareDecoder) addOption(":avcodec-hw=none")
                addOption(":no-spdif")
                addOption(":file-caching=3000")
                addOption(":network-caching=3000")
            }
        }.getOrElse {
            error = "Не удалось получить доступ к файлу. Переподключите накопитель или выберите его заново."
            return@LaunchedEffect
        }

        player.media = media
        media.release()
        player.play()

        val resumeAt = requestedPosition.coerceAtLeast(0L)
        if (resumeAt > 0L) {
            for (attempt in 0 until 60) {
                delay(100L)
                val total = duration()
                if (total > 0L) {
                    val target = resumeAt.coerceIn(0L, total)
                    player.time = target
                    pendingSeekMs = target
                    updateProgress(target, total)
                    requestedPosition = 0L
                    break
                }
            }
        }

        while (isActive) {
            val total = duration()
            val current = player.time.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            if (!scrubbing) {
                pendingSeekMs = current
                updateProgress(current, total)
            } else {
                durationMs = total
            }
            delay(250L)
        }
    }

    LaunchedEffect(message, scrubbing) {
        if (message != null && !scrubbing) {
            delay(1_300L)
            message = null
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, scrubbing, displayMode) {
        if (displayMode == PlayerDisplayMode.FULLSCREEN && controlsVisible && isPlaying && !scrubbing) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    val windowed = displayMode == PlayerDisplayMode.WINDOWED
    val shape: Shape = if (windowed) RoundedCornerShape(24.dp) else RectangleShape
    val showControls = windowed || controlsVisible || error != null
    val smallButton = if (windowed) 42.dp else 78.dp
    val smallIcon = if (windowed) 24.dp else 43.dp
    val playButton = if (windowed) 50.dp else 96.dp
    val playIcon = if (windowed) 30.dp else 56.dp

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .pointerInput(player, durationMs, displayMode) {
                detectDragGestures(
                    onDragStart = { offset: Offset ->
                        dragMode = PlayerDragMode.NONE
                        dragStartX = offset.x
                        dragDistanceX = 0f
                        dragDistanceY = 0f
                        dragStartMs = player.time.coerceAtLeast(0L)
                        pendingSeekMs = dragStartMs
                        volumeStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        brightnessStart = currentWindowBrightness()
                        controlsVisible = true
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistanceX += amount.x
                        dragDistanceY += amount.y

                        if (dragMode == PlayerDragMode.NONE &&
                            (abs(dragDistanceX) > 14f || abs(dragDistanceY) > 14f)
                        ) {
                            val vertical = abs(dragDistanceY) > abs(dragDistanceX) * 1.15f
                            dragMode = when {
                                !windowed && vertical && dragStartX <= size.width * 0.35f -> PlayerDragMode.VOLUME
                                !windowed && vertical && dragStartX >= size.width * 0.65f -> PlayerDragMode.BRIGHTNESS
                                else -> PlayerDragMode.SEEK
                            }
                            if (dragMode == PlayerDragMode.SEEK) {
                                resumeAfterSeek = player.isPlaying
                                if (resumeAfterSeek) player.pause()
                                scrubbing = true
                            }
                        }

                        when (dragMode) {
                            PlayerDragMode.SEEK -> {
                                val total = duration()
                                if (total > 0L && size.width > 0) {
                                    val delta = (dragDistanceX / size.width.toFloat() * total).toLong()
                                    val target = (dragStartMs + delta).coerceIn(0L, total)
                                    pendingSeekMs = target
                                    updateProgress(target, total)
                                    message = (if (delta >= 0L) "+" else "−") + formatVideoTime(abs(delta))
                                }
                            }
                            PlayerDragMode.VOLUME -> {
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val delta = (-dragDistanceY / size.height.toFloat() * maxVolume).roundToInt()
                                val target = (volumeStart + delta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                message = "Громкость ${(target * 100f / maxVolume).roundToInt()}%"
                            }
                            PlayerDragMode.BRIGHTNESS -> {
                                val target = (brightnessStart - dragDistanceY / size.height.toFloat()).coerceIn(0.05f, 1f)
                                setWindowBrightness(target)
                                message = "Яркость ${(target * 100f).roundToInt()}%"
                            }
                            PlayerDragMode.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (dragMode == PlayerDragMode.SEEK) {
                            seekTo(pendingSeekMs)
                            scrubbing = false
                            if (resumeAfterSeek) player.play()
                        }
                        dragMode = PlayerDragMode.NONE
                    },
                    onDragCancel = {
                        if (dragMode == PlayerDragMode.SEEK) {
                            seekTo(dragStartMs)
                            scrubbing = false
                            if (resumeAfterSeek) player.play()
                        }
                        dragMode = PlayerDragMode.NONE
                    },
                )
            }
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { point ->
                        val center = point.x in size.width * 0.25f..size.width * 0.75f &&
                            point.y in size.height * 0.20f..size.height * 0.80f
                        if (center) {
                            if (player.isPlaying) player.pause() else player.play()
                            controlsVisible = true
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { point ->
                        val delta = if (point.x < size.width / 2f) -10_000L else 10_000L
                        seekTo(
                            player.time.coerceAtLeast(0L) + delta,
                            if (delta < 0L) "−10 сек" else "+10 сек",
                        )
                    },
                )
            },
    ) {
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
                runCatching { player.updateVideoSurfaces() }
            },
            modifier = Modifier.fillMaxSize(),
        )

        message?.let { value ->
            Text(
                value,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE5070610))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White,
                fontSize = if (windowed) 12.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        AnimatedVisibility(
            visible = showControls,
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
                            if (softwareDecoder) {
                                "Встроенные программные кодеки"
                            } else {
                                "Аппаратный декодер · автоматический резерв"
                            },
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
                    size = if (windowed) 40.dp else 62.dp,
                    iconSize = if (windowed) 24.dp else 35.dp,
                    backgroundColor = Color(0xA31A1724),
                )
                Spacer(Modifier.width(if (windowed) 5.dp else 8.dp))
                HeadUnitIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Закрыть видео",
                    onClick = {
                        savePosition()
                        onClose()
                    },
                    size = if (windowed) 40.dp else 62.dp,
                    iconSize = if (windowed) 24.dp else 35.dp,
                    backgroundColor = Color(0xB33A1721),
                )
            }
        }

        error?.let { value ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEB120D1A))
                    .padding(if (windowed) 12.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    value,
                    color = Color(0xFFFFB0BB),
                    fontSize = if (windowed) 11.sp else 15.sp,
                    maxLines = if (windowed) 3 else 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                HeadUnitActionButton(
                    text = "Повторить",
                    icon = Icons.Rounded.Refresh,
                    onClick = ::restartInSoftwareMode,
                    backgroundColor = AutoPurple,
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF4000000))))
                    .padding(horizontal = if (windowed) 10.dp else 20.dp, vertical = if (windowed) 8.dp else 16.dp),
            ) {
                Slider(
                    value = sliderFraction.coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        if (durationMs <= 0L) return@Slider
                        if (!scrubbing) {
                            resumeAfterSeek = player.isPlaying
                            if (resumeAfterSeek) player.pause()
                        }
                        scrubbing = true
                        sliderFraction = fraction.coerceIn(0f, 1f)
                        pendingSeekMs = (durationMs.toDouble() * sliderFraction.toDouble()).toLong()
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
                    modifier = Modifier.height(if (windowed) 26.dp else 42.dp),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (windowed) 5.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeadUnitIconButton(
                            Icons.Rounded.SkipPrevious,
                            "Предыдущий файл",
                            onClick = { switchFile(previousFile) },
                            enabled = previousFile != null,
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.Replay10,
                            "Назад на 10 секунд",
                            onClick = { seekTo(player.time.coerceAtLeast(0L) - 10_000L, "−10 сек") },
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                        HeadUnitIconButton(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Пауза" else "Воспроизвести",
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                                controlsVisible = true
                            },
                            size = playButton,
                            iconSize = playIcon,
                            backgroundColor = AutoPurple,
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.Forward10,
                            "Вперёд на 10 секунд",
                            onClick = { seekTo(player.time.coerceAtLeast(0L) + 10_000L, "+10 сек") },
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                        HeadUnitIconButton(
                            Icons.Rounded.SkipNext,
                            "Следующий файл",
                            onClick = { switchFile(nextFile) },
                            enabled = nextFile != null,
                            size = smallButton,
                            iconSize = smallIcon,
                            backgroundColor = Color(0xB3181521),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!windowed) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Слева: громкость · Справа: яркость",
                                color = AutoMuted,
                                fontSize = 11.sp,
                            )
                            Text(
                                "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs)}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class OpenVlcMedia(
    val media: Media,
    val descriptor: AssetFileDescriptor?,
)

private fun openVlcMedia(context: Context, libVlc: LibVLC, file: MediaFile): OpenVlcMedia {
    val uri = file.uri
    return if (uri.scheme == "content") {
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: error("Content provider returned no file descriptor")
        try {
            OpenVlcMedia(Media(libVlc, descriptor), descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
    } else {
        OpenVlcMedia(Media(libVlc, normalizeLocalUri(uri)), null)
    }
}

private fun normalizeLocalUri(uri: Uri): Uri = when (uri.scheme) {
    null -> Uri.fromFile(java.io.File(uri.toString()))
    else -> uri
}

private fun formatVideoTime(valueMs: Long): String {
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

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}
