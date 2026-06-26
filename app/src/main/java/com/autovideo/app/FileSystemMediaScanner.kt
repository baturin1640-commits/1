package com.autovideo.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

class FileSystemMediaScanner(private val context: Context) {
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
    private val commonFolders = setOf(
        "movies", "movie", "video", "videos", "download", "downloads", "dcim", "music"
    )

    @Suppress("DEPRECATION")
    private val primaryRoot: File get() = Environment.getExternalStorageDirectory()

    fun signature(): String = with(primaryRoot) {
        "$absolutePath:${lastModified()}:${usableSpace}"
    }

    fun scan(): Pair<List<RemovableSource>, List<MediaFolder>> {
        if (!FullStorageAccess.isGranted(context)) return emptyList<RemovableSource>() to emptyList()
        val root = primaryRoot
        val sourceUri = Uri.fromFile(root).toString()
        val sourceName = "Внутренняя память"
        val source = RemovableSource(sourceUri, sourceName, root.isDirectory && root.canRead())
        if (!source.connected) return listOf(source) to emptyList()

        val directories = mutableListOf<IndexedMediaDirectory>()
        val queue = ArrayDeque<DirectoryNode>()
        val rootChildren = safeChildren(root)
        collectFiles(rootChildren, "", sourceName)?.let(directories::add)
        rootChildren
            .asSequence()
            .filter(File::isDirectory)
            .filter(::isReadableDirectory)
            .filter { it.name.lowercase(Locale.ROOT) in commonFolders }
            .sortedBy(::priority)
            .forEach { queue.add(DirectoryNode(it, it.name, 1)) }

        val seen = hashSetOf<String>()
        val deadline = System.nanoTime() + 5_000_000_000L
        var visited = 0
        while (queue.isNotEmpty() && System.nanoTime() < deadline && visited < 3_500) {
            val node = queue.removeFirst()
            if (node.depth > 16 || !seen.add(node.directory.absolutePath)) continue
            visited++
            val children = safeChildren(node.directory)
            collectFiles(children, node.path, sourceName)?.let(directories::add)
            children
                .asSequence()
                .filter(File::isDirectory)
                .filter(::isReadableDirectory)
                .sortedBy(::priority)
                .forEach { child ->
                    queue.add(DirectoryNode(child, "${node.path}/${child.name}", node.depth + 1))
                }
        }

        return listOf(source) to MediaHierarchy.build(sourceName, sourceUri, directories)
    }

    private fun collectFiles(
        children: Array<File>,
        path: String,
        sourceName: String,
    ): IndexedMediaDirectory? {
        val folderName = path.substringAfterLast('/').ifBlank { sourceName }
        val files = children
            .asSequence()
            .filter(File::isFile)
            .mapNotNull { file ->
                val extension = file.extension.lowercase(Locale.ROOT)
                val isVideo = extension in videoExtensions
                val isAudio = extension in audioExtensions
                if (!isVideo && !isAudio) return@mapNotNull null
                MediaFile(
                    uriString = Uri.fromFile(file).toString(),
                    name = file.name,
                    mimeType = null,
                    sizeBytes = file.length(),
                    isVideo = isVideo,
                    folderName = folderName,
                    sourceName = sourceName,
                )
            }
            .toList()
        return files.takeIf(List<MediaFile>::isNotEmpty)?.let { IndexedMediaDirectory(path, it) }
    }

    private fun safeChildren(directory: File): Array<File> =
        runCatching { directory.listFiles() }.getOrNull() ?: emptyArray()

    private fun isReadableDirectory(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        return file.canRead() && !file.isHidden && name !in skippedNames
    }

    private fun priority(file: File): Int = when (file.name.lowercase(Locale.ROOT)) {
        "movies", "movie", "video", "videos" -> 0
        "download", "downloads" -> 1
        "dcim", "music" -> 2
        else -> 10
    }

    private data class DirectoryNode(
        val directory: File,
        val path: String,
        val depth: Int,
    )
}
