package com.autovideo.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun Media3StreamScreen(
    stream: OnlineStream,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loading by remember(stream.url) { mutableStateOf(true) }
    var error by remember(stream.url) { mutableStateOf<String?>(null) }

    val player = remember(stream.url) {
        buildOnlinePlayer(context.applicationContext, stream)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                loading = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                loading = false
                error = "Поток не воспроизводится: ${playbackException.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xB0000000)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeadUnitIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack,
                size = 62.dp,
                iconSize = 34.dp,
                backgroundColor = Color(0x99000000),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stream.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(stream.kind.name, color = AutoMuted, fontSize = 12.sp)
            }
            AppClock(compact = true)
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AutoPurple,
            )
        }

        error?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center).background(Color(0xE513101B)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.padding(5.dp))
                HeadUnitActionButton(
                    text = "Повторить",
                    icon = Icons.Rounded.Refresh,
                    onClick = {
                        error = null
                        loading = true
                        player.prepare()
                        player.play()
                    },
                )
            }
        }
    }
}

private fun buildOnlinePlayer(context: Context, stream: OnlineStream): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("VideoHeadUnit/0.8")
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(false)

    val mediaItem = MediaItem.Builder()
        .setUri(stream.url)
        .apply {
            when (stream.kind) {
                StreamKind.HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
                StreamKind.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
                StreamKind.PROGRESSIVE -> Unit
            }
        }
        .build()

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            setMediaItem(mediaItem)
            prepare()
        }
}
