@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.autovideo.app

import android.content.Context
import android.media.audiofx.Equalizer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private data class AudioEqPreset(
    val name: String,
    val levelsDb: List<Float>,
)

private val audioEqPresets = listOf(
    AudioEqPreset("Ровный", listOf(0f, 0f, 0f, 0f, 0f, 0f)),
    AudioEqPreset("Бас", listOf(8f, 6f, 3f, 0f, -1f, -2f)),
    AudioEqPreset("Рок", listOf(5f, 3f, -1f, 2f, 5f, 6f)),
    AudioEqPreset("Поп", listOf(-1f, 2f, 5f, 5f, 2f, -1f)),
    AudioEqPreset("Вокал", listOf(-3f, -1f, 3f, 7f, 6f, 2f)),
    AudioEqPreset("Джаз", listOf(4f, 2f, 1f, 3f, 5f, 4f)),
    AudioEqPreset("Классика", listOf(3f, 2f, 0f, 1f, 3f, 5f)),
    AudioEqPreset("Ночь", listOf(2f, 1f, 0f, 0f, -2f, -4f)),
)

private val audioEqLabels = listOf("60 Гц", "170 Гц", "310 Гц", "1 кГц", "3 кГц", "10 кГц")
private val audioEqFrequencies = intArrayOf(60, 170, 310, 1_000, 3_000, 10_000)

@Composable
fun AudioPlayerOverlay(
    file: MediaFile,
    queue: List<MediaFile>,
    allAudioFiles: List<MediaFile>,
    playbackStore: PlaybackStore,
    playlistStore: AudioPlaylistStore,
    displayMode: PlayerDisplayMode,
    onDisplayModeChange: (PlayerDisplayMode) -> Unit,
    onSelectFile: (MediaFile) -> Unit,
    onQueueChange: (List<MediaFile>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences("audio_player_settings", Context.MODE_PRIVATE)
    }
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    val playlists by playlistStore.state.collectAsStateWithLifecycle()
    val audioQueue = remember(queue, file.uriString) {
        queue.filter(MediaFile::isAudio)
            .distinctBy(MediaFile::uriString)
            .let { items -> if (items.any { it.uriString == file.uriString }) items else listOf(file) }
    }
    val queueKey = remember(audioQueue) { audioQueue.joinToString("|") { it.uriString } }
    val latestSelectFile by rememberUpdatedState(onSelectFile)

    var loadedQueueKey by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(preferences.getBoolean("shuffle", false)) }
    var repeatMode by remember { mutableStateOf(preferences.getInt("repeat_mode", Player.REPEAT_MODE_OFF)) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var equalizerSessionId by remember { mutableStateOf(C.AUDIO_SESSION_ID_UNSET) }
    var eqLevels by remember {
        mutableStateOf(List(6) { index -> preferences.getFloat("eq_$index", 0f) })
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) playbackError = null
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                audioQueue.firstOrNull { it.uriString == mediaItem?.mediaId }?.let(latestSelectFile)
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                playbackError = "Трек не воспроизводится: ${error.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            val currentFile = audioQueue.firstOrNull { it.uriString == player.currentMediaItem?.mediaId }
            if (currentFile != null) {
                playbackStore.save(currentFile, player.currentPosition, player.duration.coerceAtLeast(0L))
            }
            runCatching { equalizer?.release() }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(queueKey, file.uriString) {
        if (audioQueue.isEmpty()) return@LaunchedEffect
        val targetIndex = audioQueue.indexOfFirst { it.uriString == file.uriString }.coerceAtLeast(0)
        if (loadedQueueKey != queueKey) {
            val items = audioQueue.map { track ->
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaId(track.uriString)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.name.substringBeforeLast('.'))
                            .setArtist(track.sourceName)
                            .setAlbumTitle(track.folderName)
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(items, targetIndex, playbackStore.position(file))
            player.shuffleModeEnabled = shuffleEnabled
            player.repeatMode = repeatMode
            player.prepare()
            player.play()
            loadedQueueKey = queueKey
        } else if (player.currentMediaItem?.mediaId != file.uriString) {
            player.seekTo(targetIndex, playbackStore.position(file))
            player.play()
        }
    }

    LaunchedEffect(player, queueKey) {
        var tick = 0
        while (isActive) {
            val total = player.duration.coerceAtLeast(0L)
            val current = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            isLoading = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE
            if (!scrubbing) {
                positionMs = current
                durationMs = total
                sliderValue = if (total > 0L) {
                    (current.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
            if (tick++ % 20 == 0 && total > 0L) {
                audioQueue.firstOrNull { it.uriString == player.currentMediaItem?.mediaId }?.let { currentFile ->
                    playbackStore.save(currentFile, current, total)
                }
            }
            delay(250L)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            val sessionId = player.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0 && sessionId != equalizerSessionId) {
                runCatching { equalizer?.release() }
                equalizer = runCatching { Equalizer(0, sessionId).apply { enabled = true } }.getOrNull()
                equalizerSessionId = sessionId
                applySixBandEqualizer(equalizer, eqLevels)
            }
            delay(500L)
        }
    }

    LaunchedEffect(eqLevels) {
        val editor = preferences.edit()
        eqLevels.forEachIndexed { index, value -> editor.putFloat("eq_$index", value) }
        editor.apply()
        applySixBandEqualizer(equalizer, eqLevels)
    }

    LaunchedEffect(shuffleEnabled, repeatMode) {
        player.shuffleModeEnabled = shuffleEnabled
        player.repeatMode = repeatMode
        preferences.edit()
            .putBoolean("shuffle", shuffleEnabled)
            .putInt("repeat_mode", repeatMode)
            .apply()
    }

    fun seekBy(deltaMs: Long) {
        val total = player.duration.coerceAtLeast(0L)
        if (total > 0L) player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, total))
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    if (displayMode == PlayerDisplayMode.WINDOWED) {
        AudioMiniPlayer(
            file = file,
            isPlaying = isPlaying,
            onPrevious = player::seekToPreviousMediaItem,
            onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
            onNext = player::seekToNextMediaItem,
            onExpand = { onDisplayModeChange(PlayerDisplayMode.FULLSCREEN) },
            onClose = onClose,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF050711), Color(0xFF17102E), Color(0xFF071A26))
                    )
                )
                .padding(22.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Музыка", color = AutoText, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        Text("${audioQueue.size} треков в очереди", color = AutoMuted, fontSize = 13.sp)
                    }
                    HeadUnitIconButton(
                        Icons.Rounded.QueueMusic,
                        "Очередь",
                        onClick = { showQueue = true },
                        size = 58.dp,
                        iconSize = 31.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeadUnitIconButton(
                        Icons.Rounded.Equalizer,
                        "Эквалайзер",
                        onClick = { showEqualizer = true },
                        size = 58.dp,
                        iconSize = 31.dp,
                        backgroundColor = AutoPurple,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeadUnitIconButton(
                        Icons.Rounded.PlaylistAdd,
                        "Плейлисты",
                        onClick = { showPlaylists = true },
                        size = 58.dp,
                        iconSize = 31.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeadUnitIconButton(
                        Icons.Rounded.ExpandMore,
                        "Свернуть плеер",
                        onClick = { onDisplayModeChange(PlayerDisplayMode.WINDOWED) },
                        size = 58.dp,
                        iconSize = 31.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeadUnitIconButton(
                        Icons.Rounded.Close,
                        "Закрыть плеер",
                        onClick = onClose,
                        size = 58.dp,
                        iconSize = 31.dp,
                        backgroundColor = Color(0xFF4A1B2B),
                    )
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(34.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(42.dp))
                            .background(Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue, AutoGreen))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(206.dp)
                                .clip(CircleShape)
                                .background(Color(0xC20A0913)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(112.dp),
                            )
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name.substringBeforeLast('.'),
                            color = AutoText,
                            fontSize = 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${file.folderName} · ${file.sourceName}",
                            color = AutoMuted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(24.dp))
                        Slider(
                            value = sliderValue.coerceIn(0f, 1f),
                            onValueChange = { value ->
                                scrubbing = true
                                sliderValue = value
                                positionMs = (durationMs * value).toLong()
                            },
                            onValueChangeFinished = {
                                if (durationMs > 0L) player.seekTo((durationMs * sliderValue).toLong())
                                scrubbing = false
                            },
                            enabled = durationMs > 0L,
                            colors = SliderDefaults.colors(
                                thumbColor = AutoPink,
                                activeTrackColor = AutoPurple,
                                inactiveTrackColor = AutoSurfaceBright,
                            ),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(formatAudioTime(positionMs), color = AutoMuted, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatAudioTime(durationMs), color = AutoMuted, fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HeadUnitIconButton(
                                Icons.Rounded.Shuffle,
                                "Перемешать",
                                onClick = { shuffleEnabled = !shuffleEnabled },
                                size = 60.dp,
                                iconSize = 31.dp,
                                backgroundColor = if (shuffleEnabled) AutoPurple else AutoSurfaceHigh,
                            )
                            Spacer(Modifier.width(10.dp))
                            HeadUnitIconButton(
                                Icons.Rounded.SkipPrevious,
                                "Предыдущий трек",
                                onClick = player::seekToPreviousMediaItem,
                                size = 68.dp,
                                iconSize = 38.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            HeadUnitIconButton(
                                Icons.Rounded.Replay10,
                                "Назад на 10 секунд",
                                onClick = { seekBy(-10_000L) },
                                size = 62.dp,
                                iconSize = 34.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            HeadUnitIconButton(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (isPlaying) "Пауза" else "Воспроизвести",
                                onClick = { if (player.isPlaying) player.pause() else player.play() },
                                size = 88.dp,
                                iconSize = 50.dp,
                                backgroundColor = AutoPink,
                            )
                            Spacer(Modifier.width(10.dp))
                            HeadUnitIconButton(
                                Icons.Rounded.Forward10,
                                "Вперёд на 10 секунд",
                                onClick = { seekBy(10_000L) },
                                size = 62.dp,
                                iconSize = 34.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            HeadUnitIconButton(
                                Icons.Rounded.SkipNext,
                                "Следующий трек",
                                onClick = player::seekToNextMediaItem,
                                size = 68.dp,
                                iconSize = 38.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            HeadUnitIconButton(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                "Повтор",
                                onClick = ::toggleRepeat,
                                size = 60.dp,
                                iconSize = 31.dp,
                                backgroundColor = if (repeatMode != Player.REPEAT_MODE_OFF) AutoBlue else AutoSurfaceHigh,
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        HeadUnitActionButton(
                            text = "Добавить в плейлист",
                            icon = Icons.Rounded.PlaylistAdd,
                            onClick = { showPlaylists = true },
                            backgroundColor = Color(0xFF2D5B55),
                        )

                        if (isLoading) {
                            Spacer(Modifier.height(12.dp))
                            Text("Загрузка трека…", color = AutoCyan, fontSize = 13.sp)
                        }
                        playbackError?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            Text(message, color = AutoRed, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showEqualizer) {
        AudioEqualizerDialog(
            levels = eqLevels,
            available = equalizer != null,
            onLevelsChange = { eqLevels = it },
            onDismiss = { showEqualizer = false },
        )
    }

    if (showPlaylists) {
        AudioPlaylistsDialog(
            currentFile = file,
            playlists = playlists,
            allAudioFiles = allAudioFiles,
            playlistStore = playlistStore,
            onPlayPlaylist = { files ->
                onQueueChange(files)
                files.firstOrNull()?.let(latestSelectFile)
                showPlaylists = false
            },
            onDismiss = { showPlaylists = false },
        )
    }

    if (showQueue) {
        AudioQueueDialog(
            queue = audioQueue,
            currentUri = player.currentMediaItem?.mediaId,
            onSelect = { selected ->
                val index = audioQueue.indexOfFirst { it.uriString == selected.uriString }
                if (index >= 0) {
                    player.seekToDefaultPosition(index)
                    player.play()
                }
                showQueue = false
            },
            onDismiss = { showQueue = false },
        )
    }
}

@Composable
private fun AudioMiniPlayer(
    file: MediaFile,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF2141029), Color(0xF2221342), Color(0xF20C2730))
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(Brush.linearGradient(listOf(AutoPink, AutoPurple, AutoBlue))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name.substringBeforeLast('.'),
                color = AutoText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(file.folderName, color = AutoMuted, fontSize = 12.sp, maxLines = 1)
        }
        HeadUnitIconButton(Icons.Rounded.SkipPrevious, "Предыдущий", onPrevious, size = 52.dp, iconSize = 29.dp)
        Spacer(Modifier.width(6.dp))
        HeadUnitIconButton(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (isPlaying) "Пауза" else "Воспроизвести",
            onPlayPause,
            size = 62.dp,
            iconSize = 36.dp,
            backgroundColor = AutoPink,
        )
        Spacer(Modifier.width(6.dp))
        HeadUnitIconButton(Icons.Rounded.SkipNext, "Следующий", onNext, size = 52.dp, iconSize = 29.dp)
        Spacer(Modifier.width(6.dp))
        HeadUnitIconButton(Icons.Rounded.ExpandLess, "Развернуть", onExpand, size = 52.dp, iconSize = 29.dp)
        Spacer(Modifier.width(6.dp))
        HeadUnitIconButton(
            Icons.Rounded.Close,
            "Закрыть",
            onClose,
            size = 52.dp,
            iconSize = 29.dp,
            backgroundColor = Color(0xFF4A1B2B),
        )
    }
}

@Composable
private fun AudioEqualizerDialog(
    levels: List<Float>,
    available: Boolean,
    onLevelsChange: (List<Float>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Эквалайзер · 6 полос", color = AutoText)
                Text(
                    if (available) "Изменения применяются сразу" else "Эквалайзер устройства недоступен",
                    color = if (available) AutoGreen else AutoRed,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(audioEqPresets, key = AudioEqPreset::name) { preset ->
                        Box(
                            modifier = Modifier
                                .headUnitPressable(
                                    onClick = { onLevelsChange(preset.levelsDb) },
                                    shape = RoundedCornerShape(15.dp),
                                )
                                .background(AutoSurfaceBright, RoundedCornerShape(15.dp))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(preset.name, color = AutoText, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                audioEqLabels.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = AutoText, fontSize = 12.sp, modifier = Modifier.width(58.dp))
                        Slider(
                            value = levels.getOrElse(index) { 0f },
                            onValueChange = { value ->
                                onLevelsChange(levels.toMutableList().apply { this[index] = value })
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = AutoPink,
                                activeTrackColor = AutoPurple,
                                inactiveTrackColor = AutoSurfaceBright,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${levels.getOrElse(index) { 0f }.roundToInt()} дБ",
                            color = AutoMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        containerColor = AutoSurface,
    )
}

@Composable
private fun AudioPlaylistsDialog(
    currentFile: MediaFile,
    playlists: List<AudioPlaylist>,
    allAudioFiles: List<MediaFile>,
    playlistStore: AudioPlaylistStore,
    onPlayPlaylist: (List<MediaFile>) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Плейлисты", color = AutoText) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(60) },
                        label = { Text("Название нового плейлиста") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    HeadUnitIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Создать плейлист",
                        onClick = {
                            playlistStore.create(newName)?.let { created ->
                                playlistStore.addTrack(created.id, currentFile)
                                newName = ""
                            }
                        },
                        enabled = newName.trim().isNotEmpty(),
                        size = 54.dp,
                        iconSize = 29.dp,
                        backgroundColor = AutoPurple,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Text("Введите название и нажмите кнопку +", color = AutoMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(0.58f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(playlists, key = AudioPlaylist::id) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AutoSurfaceHigh, RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.QueueMusic, null, tint = AutoPurple, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, color = AutoText, fontWeight = FontWeight.SemiBold)
                                    Text("${playlist.trackUris.size} треков", color = AutoMuted, fontSize = 11.sp)
                                }
                                HeadUnitIconButton(
                                    Icons.Rounded.Add,
                                    "Добавить текущий трек",
                                    onClick = { playlistStore.addTrack(playlist.id, currentFile) },
                                    size = 42.dp,
                                    iconSize = 23.dp,
                                )
                                Spacer(Modifier.width(5.dp))
                                HeadUnitIconButton(
                                    Icons.Rounded.PlayArrow,
                                    "Воспроизвести плейлист",
                                    onClick = {
                                        val files = playlist.trackUris.mapNotNull { uri ->
                                            allAudioFiles.firstOrNull { it.uriString == uri }
                                        }
                                        if (files.isNotEmpty()) onPlayPlaylist(files)
                                    },
                                    size = 42.dp,
                                    iconSize = 23.dp,
                                    backgroundColor = AutoGreen.copy(alpha = 0.35f),
                                )
                                Spacer(Modifier.width(5.dp))
                                HeadUnitIconButton(
                                    Icons.Rounded.Delete,
                                    "Удалить плейлист",
                                    onClick = { playlistStore.delete(playlist.id) },
                                    size = 42.dp,
                                    iconSize = 23.dp,
                                    backgroundColor = Color(0xFF4A1B2B),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        containerColor = AutoSurface,
    )
}

@Composable
private fun AudioQueueDialog(
    queue: List<MediaFile>,
    currentUri: String?,
    onSelect: (MediaFile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очередь воспроизведения", color = AutoText) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.68f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(queue, key = MediaFile::uriString) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .headUnitPressable(
                                onClick = { onSelect(track) },
                                shape = RoundedCornerShape(15.dp),
                            )
                            .background(
                                if (track.uriString == currentUri) AutoPurple.copy(alpha = 0.32f) else AutoSurfaceHigh,
                                RoundedCornerShape(15.dp),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = AutoCyan, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.name.substringBeforeLast('.'),
                                color = AutoText,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(track.folderName, color = AutoMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        containerColor = AutoSurface,
    )
}

private fun applySixBandEqualizer(effect: Equalizer?, levelsDb: List<Float>) {
    if (effect == null) return
    runCatching {
        val range = effect.bandLevelRange
        val assignments = mutableMapOf<Short, MutableList<Float>>()
        audioEqFrequencies.forEachIndexed { index, frequencyHz ->
            val band = effect.getBand(frequencyHz * 1_000)
            assignments.getOrPut(band) { mutableListOf() }
                .add(levelsDb.getOrElse(index) { 0f })
        }
        assignments.forEach { (band, values) ->
            val averageDb = values.average().toFloat()
            val levelMb = (averageDb * 100f)
                .roundToInt()
                .coerceIn(range[0].toInt(), range[1].toInt())
                .toShort()
            effect.setBandLevel(band, levelMb)
        }
        effect.enabled = true
    }
}

private fun formatAudioTime(valueMs: Long): String {
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
