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

    fun scan(access: MediaReadAccess): Pair<RemovableSource?, List<MediaFolder>> {
        if (!access.any) return null to emptyList()

        val groupedFiles = linkedMapOf<String, MutableList<MediaFile>>()
        val folderNames = linkedMapOf<String, String>()

        if (access.video) {
            queryCollection(true, groupedFiles, folderNames)
        }
        if (access.audio) {
            queryCollection(false, groupedFiles, folderNames)
        }

        val folders = groupedFiles.map { (folderPath, files) ->
            MediaFolder(
                id = "$INTERNAL_SOURCE_URI#$folderPath",
                name = folderNames[folderPath].orEmpty().ifBlank { INTERNAL_SOURCE_NAME },
                sourceName = INTERNAL_SOURCE_NAME,
                sourceUriString = INTERNAL_SOURCE_URI,
                files = files.sortedBy { it.name.lowercase(Locale.getDefault()) },
            )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }

        return RemovableSource(
            uriString = INTERNAL_SOURCE_URI,
            name = INTERNAL_SOURCE_NAME,
            connected = true,
        ) to folders
    }

    private fun queryCollection(
        isVideo: Boolean,
        groupedFiles: MutableMap<String, MutableList<MediaFile>>,
        folderNames: MutableMap<String, String>,
    ) {
        val collection = collectionUri(isVideo)
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
        )

        runCatching {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull()?.use { cursor ->
            readCursor(cursor, collection, pathColumn, isVideo, groupedFiles, folderNames)
        }
    }

    private fun readCursor(
        cursor: Cursor,
        collection: Uri,
        pathColumn: String,
        isVideo: Boolean,
        groupedFiles: MutableMap<String, MutableList<MediaFile>>,
        folderNames: MutableMap<String, String>,
    ) {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val pathIndex = cursor.getColumnIndex(pathColumn)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
            val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            val rawPath = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
            val folderPath = normalizeFolderPath(rawPath, isVideo)
            val folderName = folderPath.substringAfterLast('/').ifBlank {
                if (isVideo) "Видео" else "Аудио"
            }
            val mediaUri = ContentUris.withAppendedId(collection, id)

            folderNames[folderPath] = folderName
            groupedFiles.getOrPut(folderPath) { mutableListOf() }.add(
                MediaFile(
                    uriString = mediaUri.toString(),
                    name = name,
                    mimeType = mime,
                    sizeBytes = size,
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

    private fun collectionUri(isVideo: Boolean): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        } else {
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }
}
