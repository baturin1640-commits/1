package com.autovideo.app

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MediaScanner(private val context: Context) {
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts",
        "mpg", "mpeg", "3gp", "3g2", "flv", "wmv", "vob", "ogv"
    )
    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "wave", "ogg", "oga", "opus",
        "amr", "ac3", "eac3", "dts", "wma", "ape", "alac", "aiff", "aif"
    )

    fun scan(uris: List<Uri>): Pair<List<RemovableSource>, List<MediaFolder>> {
        val sources = mutableListOf<RemovableSource>()
        val folders = mutableListOf<MediaFolder>()

        uris.forEach { treeUri ->
            val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            val sourceName = root?.name?.takeIf(String::isNotBlank)
                ?: treeUri.lastPathSegment?.substringAfterLast(':')?.takeIf(String::isNotBlank)
                ?: "Съёмный носитель"
            val connected = root?.exists() == true && root.canRead()
            sources += RemovableSource(treeUri.toString(), sourceName, connected)

            if (connected && root != null) {
                scanDirectory(
                    directory = root,
                    sourceName = sourceName,
                    sourceUriString = treeUri.toString(),
                    output = folders,
                    visited = mutableSetOf(),
                    depth = 0,
                )
            }
        }

        val sortedSources = sources.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val sortedFolders = folders.sortedWith(
            compareBy<MediaFolder> { it.sourceName.lowercase(Locale.getDefault()) }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
        return sortedSources to sortedFolders
    }

    private fun scanDirectory(
        directory: DocumentFile,
        sourceName: String,
        sourceUriString: String,
        output: MutableList<MediaFolder>,
        visited: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > 32 || !visited.add(directory.uri.toString())) return

        val children = runCatching { directory.listFiles() }.getOrDefault(emptyArray())
        val folderName = directory.name?.takeIf(String::isNotBlank) ?: "Без названия"
        val mediaFiles = children
            .asSequence()
            .filter { it.isFile }
            .mapNotNull { toMediaFile(it, folderName, sourceName) }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
            .toList()

        if (mediaFiles.isNotEmpty()) {
            output += MediaFolder(
                id = "${sourceUriString}#${directory.uri}",
                name = folderName,
                sourceName = sourceName,
                sourceUriString = sourceUriString,
                files = mediaFiles,
            )
        }

        children.asSequence()
            .filter { it.isDirectory && it.canRead() }
            .forEach {
                scanDirectory(
                    directory = it,
                    sourceName = sourceName,
                    sourceUriString = sourceUriString,
                    output = output,
                    visited = visited,
                    depth = depth + 1,
                )
            }
    }

    private fun toMediaFile(
        file: DocumentFile,
        folderName: String,
        sourceName: String,
    ): MediaFile? {
        val name = file.name ?: return null
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val declaredMime = file.type
        val inferredMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val mime = declaredMime ?: inferredMime
        val isVideo = mime?.startsWith("video/") == true || extension in videoExtensions
        val isAudio = mime?.startsWith("audio/") == true || extension in audioExtensions
        if (!isVideo && !isAudio) return null

        return MediaFile(
            uriString = file.uri.toString(),
            name = name,
            mimeType = mime,
            sizeBytes = file.length(),
            isVideo = isVideo,
            folderName = folderName,
            sourceName = sourceName,
        )
    }
}
