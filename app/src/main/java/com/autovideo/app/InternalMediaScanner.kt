package com.autovideo.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.Locale

class InternalMediaScanner(private val context: Context) {
    fun scan(includeVideo: Boolean, includeAudio: Boolean): List<MediaFolder> {
        val files = buildList {
            if (includeVideo) addAll(scanCollection(isVideo = true))
            if (includeAudio) addAll(scanCollection(isVideo = false))
        }

        return files
            .groupBy { "${it.sourceName}#${it.folderName}" }
            .map { (id, items) ->
                MediaFolder(
                    id = id,
                    name = items.first().folderName,
                    sourceName = INTERNAL_SOURCE_NAME,
                    sourceUriString = INTERNAL_SOURCE_URI,
                    files = items.sortedBy { it.name.lowercase(Locale.getDefault()) },
                )
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun scanCollection(isVideo: Boolean): List<MediaFile> {
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isVideo ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()

        val output = mutableListOf<MediaFile>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.SIZE} > 0",
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val mime = mimeIndex.takeIf { it >= 0 }?.let(cursor::getString)
                val size = sizeIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
                val rawPath = pathIndex.takeIf { it >= 0 }?.let(cursor::getString)
                val folderName = folderName(rawPath)
                val uri: Uri = ContentUris.withAppendedId(collection, id)

                output += MediaFile(
                    uriString = uri.toString(),
                    name = name,
                    mimeType = mime,
                    sizeBytes = size,
                    isVideo = isVideo,
                    folderName = folderName,
                    sourceName = INTERNAL_SOURCE_NAME,
                )
            }
        }
        return output
    }

    private fun folderName(path: String?): String {
        if (path.isNullOrBlank()) return "Без папки"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            path.trimEnd('/').substringAfterLast('/').ifBlank { "Без папки" }
        } else {
            File(path).parentFile?.name?.ifBlank { "Без папки" } ?: "Без папки"
        }
    }

    companion object {
        const val INTERNAL_SOURCE_URI = "mediastore://internal"
        const val INTERNAL_SOURCE_NAME = "Внутренняя память"
    }
}
