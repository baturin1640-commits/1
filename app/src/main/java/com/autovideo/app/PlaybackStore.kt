package com.autovideo.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
    private val mutableRevision = MutableStateFlow(0L)

    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun position(file: MediaFile): Long = prefs.getLong("${key(file)}_position", 0L)

    fun duration(file: MediaFile): Long = prefs.getLong("${key(file)}_duration", 0L)

    fun updatedAt(file: MediaFile): Long = prefs.getLong("${key(file)}_updated", 0L)

    fun progress(file: MediaFile): Float {
        val total = duration(file)
        if (total <= 0L) return 0f
        return (position(file).toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun latestVideo(files: List<MediaFile>): MediaFile? = files
        .asSequence()
        .filter(MediaFile::isVideo)
        .filter { position(it) > 0L }
        .maxByOrNull(::updatedAt)

    fun save(file: MediaFile, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val savedPosition = if (positionMs >= durationMs - 2_000L) 0L else positionMs.coerceAtLeast(0L)
        prefs.edit()
            .putLong("${key(file)}_position", savedPosition)
            .putLong("${key(file)}_duration", durationMs)
            .putLong("${key(file)}_updated", System.currentTimeMillis())
            .putString("last_media_uri", file.uriString)
            .apply()
        mutableRevision.value += 1L
    }

    private fun key(file: MediaFile): String = file.uriString.hashCode().toUInt().toString(16)
}
