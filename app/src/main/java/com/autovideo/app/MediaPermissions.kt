package com.autovideo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class MediaReadAccess(
    val video: Boolean,
    val audio: Boolean,
) {
    val any: Boolean get() = video || audio
}

object MediaPermissions {
    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        else -> emptyArray()
    }

    fun missingPermissions(context: Context): Array<String> = requiredPermissions()
        .filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        .toTypedArray()

    fun access(context: Context): MediaReadAccess {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return MediaReadAccess(video = true, audio = true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return MediaReadAccess(
                video = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO),
                audio = isGranted(context, Manifest.permission.READ_MEDIA_AUDIO),
            )
        }
        val granted = isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        return MediaReadAccess(video = granted, audio = granted)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
