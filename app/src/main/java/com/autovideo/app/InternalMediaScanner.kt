package com.autovideo.app

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.Locale

const val INTERNAL_SOURCE_URI = "internal://media-store"
private const val INTERNAL_SOURCE_NAME = "Внутренняя память"

class InternalMediaScanner(private val context: Context) {
    private val resolver = context.contentResolver
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts",
        "mpg", "mpeg", "3gp", "3g2", "flv", "wmv", "vob", "ogv"
    )
    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "wave", "ogg", "oga", "opus",
        "amr", "ac3", "eac3", "dts", "wma", "ape", "alac", "aiff", "aif"
    )

    fun scan(access: MediaReadAccess): Pair<List<RemovableSource>, List<MediaFolder>> {
        if (!access.any) return emptyList<RemovableSource>() to emptyList()

        val volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        } else {
            "external"
        }
        val grouped = linkedMapOf<String, MutableList<MediaFile>>()
        queryVolume(volumeName, access, grouped)

        val directories = grouped.map { (path, files) -> IndexedMediaDirectory(path, files) }
        return listOf(RemovableSource(INTERNAL_SOURCE_URI, INTERNAL_SOURCE_NAME, true)) to
            MediaHierarchy.build(INTERNAL_SOURCE_NAME, INTERNAL_SOURCE_URI, directories)
    }

    private fun queryVolume(
        volumeName: String,
        access: MediaReadAccess,
        groupedFiles: MutableMap<String, MutableList<MediaFile>>,
    ) {
        val collection = MediaStore.Files.getContentUri(volumeName)
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            pathColumn,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val allowedTypes = buildList {
            if (access.video) add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            if (access.audio) add(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
        }
        if (allowedTypes.isEmpty()) return
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${allowedTypes.joinToString(",")})"

        runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull()?.use { cursor ->
            readCursor(cursor, collection, pathColumn, access, groupedFiles)
        }
    }

    private fun readCursor(
        cursor: Cursor,
        collection: Uri,
        pathColumn: String,
        access: MediaReadAccess,
        groupedFiles: MutableMap<String, MutableList<MediaFile>>,
    ) {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val pathIndex = cursor.getColumnIndex(pathColumn)
        val mediaTypeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: continue
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
            val mediaType = if (mediaTypeIndex >= 0) cursor.getInt(mediaTypeIndex) else 0
            val video = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ||
                mime?.startsWith("video/") == true || extension in videoExtensions
            val audio = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ||
                mime?.startsWith("audio/") == true || extension in audioExtensions
            val isVideo = video && access.video
            val isAudio = !isVideo && audio && access.audio
            if (!isVideo && !isAudio) continue

            val rawPath = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
            val folderPath = normalizeFolderPath(rawPath, isVideo)
            val folderName = folderPath.substringAfterLast('/').ifBlank {
                if (isVideo) "Видео" else "Музыка"
            }
            groupedFiles.getOrPut(folderPath) { mutableListOf() }.add(
                MediaFile(
                    uriString = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString(),
                    name = name,
                    mimeType = mime,
                    sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                    isVideo = isVideo,
                    folderName = folderName,
                    sourceName = INTERNAL_SOURCE_NAME,
                )
            )
        }
    }

    private fun normalizeFolderPath(rawPath: String, isVideo: Boolean): String {
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rawPath.trim().trim('/')
        } else {
            File(rawPath).parent.orEmpty().trim().trim('/')
        }
        return path.ifBlank { if (isVideo) "Movies" else "Music" }
    }
}
