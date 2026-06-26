package com.autovideo.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbnailCache {
    private const val CACHE_SIZE_KB = 12 * 1024
    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) cache.put(key, bitmap)
    }
}

@Composable
fun VideoThumbnail(
    file: MediaFile,
    modifier: Modifier = Modifier,
    showPlayButton: Boolean = true,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = ThumbnailCache.get(file.uriString),
        key1 = file.uriString,
    ) {
        if (!file.isVideo || value != null) return@produceState

        value = withContext(Dispatchers.IO) {
            ThumbnailCache.get(file.uriString) ?: runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, file.uri)
                    val durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L
                    val frameTimeUs = when {
                        durationMs <= 0L -> 1_000_000L
                        durationMs < 8_000L -> durationMs * 300L
                        else -> minOf(durationMs / 4L, 10_000L) * 1_000L
                    }
                    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            frameTimeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            640,
                            360,
                        )
                    } else {
                        retriever.getFrameAtTime(
                            frameTimeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        )?.let(::scaleThumbnail)
                    }
                    frame?.also { ThumbnailCache.put(file.uriString, it) }
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                if (file.isVideo) {
                    listOf(Color(0xFF32185F), Color(0xFF0D2135))
                } else {
                    listOf(Color(0xFF4B173C), Color(0xFF161126))
                }
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))
                ),
            )
        } ?: Icon(
            imageVector = if (file.isVideo) Icons.Rounded.VideoFile else Icons.Rounded.Audiotrack,
            contentDescription = null,
            tint = if (file.isVideo) AutoPurple else AutoPink,
            modifier = Modifier.size(42.dp),
        )

        if (showPlayButton) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xB805040A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun scaleThumbnail(source: Bitmap): Bitmap {
    if (source.width <= 640 && source.height <= 360) return source
    val scale = minOf(640f / source.width.toFloat(), 360f / source.height.toFloat())
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    if (scaled !== source) source.recycle()
    return scaled
}
