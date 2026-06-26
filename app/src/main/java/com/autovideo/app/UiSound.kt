package com.autovideo.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

enum class UiSound {
    BUTTON,
    FOLDER,
    FAVORITE,
}

class UiSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val sounds = mutableMapOf<UiSound, Int>()

    init {
        sounds[UiSound.BUTTON] = load("ui_button.wav", 430.0, 560.0, 42)
        sounds[UiSound.FOLDER] = load("ui_folder.wav", 330.0, 455.0, 58)
        sounds[UiSound.FAVORITE] = load("ui_favorite.wav", 620.0, 820.0, 72)
    }

    fun play(sound: UiSound) {
        sounds[sound]?.takeIf { it != 0 }?.let { id ->
            soundPool.play(id, 0.16f, 0.16f, 1, 0, 1f)
        }
    }

    fun release() = soundPool.release()

    private fun load(name: String, firstHz: Double, secondHz: Double, durationMs: Int): Int {
        val directory = File(appContext.cacheDir, "ui_sounds").apply { mkdirs() }
        val file = File(directory, name)
        if (!file.isFile) writeSoftTone(file, firstHz, secondHz, durationMs)
        return runCatching { soundPool.load(file.absolutePath, 1) }.getOrDefault(0)
    }

    private fun writeSoftTone(file: File, firstHz: Double, secondHz: Double, durationMs: Int) {
        val sampleRate = 22_050
        val sampleCount = sampleRate * durationMs / 1_000
        val pcm = ShortArray(sampleCount) { index ->
            val progress = index.toDouble() / sampleCount.coerceAtLeast(1)
            val envelope = sin(PI * progress).coerceAtLeast(0.0)
            val sample = (
                sin(2.0 * PI * firstHz * index / sampleRate) * 0.68 +
                    sin(2.0 * PI * secondHz * index / sampleRate) * 0.32
                ) * envelope * 0.28
            (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        DataOutputStream(FileOutputStream(file)).use { output ->
            val dataSize = pcm.size * 2
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(sampleRate * 2)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianShort(16)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            pcm.forEach { output.writeLittleEndianShort(it.toInt()) }
        }
    }
}

val LocalUiSoundPlayer = staticCompositionLocalOf<UiSoundPlayer?> { null }

private fun DataOutputStream.writeLittleEndianInt(value: Int) {
    writeByte(value and 0xFF)
    writeByte(value ushr 8 and 0xFF)
    writeByte(value ushr 16 and 0xFF)
    writeByte(value ushr 24 and 0xFF)
}

private fun DataOutputStream.writeLittleEndianShort(value: Int) {
    writeByte(value and 0xFF)
    writeByte(value ushr 8 and 0xFF)
}
