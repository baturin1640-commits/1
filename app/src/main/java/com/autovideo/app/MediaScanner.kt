package com.autovideo.app

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque
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
    private val skippedNames = setOf(
        "android", ".thumbnails", "lost.dir", "system volume information", "\$recycle.bin"
    )

    fun scan(uris: List<Uri>): Pair<List<RemovableSource>, List<MediaFolder>> {
        val sources = mutableListOf<RemovableSource>()
        val folders = mutableListOf<MediaFolder>()
        val deadlineNanos = System.nanoTime() + 6_000_000_000L
        var visitedDirectories = 0

        uris.forEach { treeUri ->
            if (System.nanoTime() >= deadlineNanos || visitedDirectories >= 4_000) return@forEach
            val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            val sourceName = root?.name?.takeIf(String::isNotBlank)
                ?: treeUri.lastPathSegment?.substringAfterLast(':')?.takeIf(String::isNotBlank)
                ?: "Съёмный носитель"
            val connected = root?.exists() == true && root.canRead()
            sources += RemovableSource(treeUri.toString(), sourceName, connected)
            if (!connected || root == null) return@forEach

            val queue = ArrayDeque<DirectoryNode>()
            queue.add(DirectoryNode(root, 0))
            val seen = hashSetOf<String>()

            while (queue.isNotEmpty() && System.nanoTime() < deadlineNanos && visitedDirectories < 4_000) {
                val node = queue.removeFirst()
                if (node.depth > 16) continue
                val uriKey = node.directory.uri.toString()
                if (!seen.add(uriKey)) continue
                visitedDirectories++

                val children = runCatching { node.directory.listFiles() }.getOrDefault(emptyArray())
                val folderName = node.directory.name?.takeIf(String::isNotBlank) ?: "Без названия"
                val mediaFiles = children
                    .asSequence()
                    .filter(DocumentFile::isFile)
                    .mapNotNull { toMediaFile(it, folderName, sourceName) }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
                    .toList()

                if (mediaFiles.isNotEmpty()) {
                    folders += MediaFolder(
                        id = "${treeUri}#${node.directory.uri}",
                        name = folderName,
                        sourceName = sourceName,
                        sourceUriString = treeUri.toString(),
                        files = mediaFiles,
                    )
                }

                children
                    .asSequence()
                    .filter { it.isDirectory && it.canRead() }
                    .filter { child ->
                        val name = child.name.orEmpty().lowercase(Locale.ROOT)
                        name !in skippedNames && !name.startsWith('.')
                    }
                    .sortedBy { directoryPriority(it.name.orEmpty()) }
                    .forEach { queue.add(DirectoryNode(it, node.depth + 1)) }
            }
        }

        return sources.sortedBy { it.name.lowercase(Locale.getDefault()) } to
            folders.sortedWith(
                compareBy<MediaFolder> { it.sourceName.lowercase(Locale.getDefault()) }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
    }

    private fun directoryPriority(name: String): Int = when (name.lowercase(Locale.ROOT)) {
        "movies", "movie", "video", "videos" -> 0
        "download", "downloads" -> 1
        "dcim", "music" -> 2
        else -> 10
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

    private data class DirectoryNode(
        val directory: DocumentFile,
        val depth: Int,
    )
}
