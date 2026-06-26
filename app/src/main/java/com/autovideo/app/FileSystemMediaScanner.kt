package com.autovideo.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
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
    private val commonInternalFolders = setOf(
        "movies", "movie", "video", "videos", "download", "downloads", "dcim", "music"
    )

    fun signature(): String = discoverRoots()
        .joinToString("|") { root ->
            "${root.directory.absolutePath}:${root.directory.lastModified()}:${root.primary}"
        }

    fun scan(): Pair<List<RemovableSource>, List<MediaFolder>> {
        if (!FullStorageAccess.isGranted(context)) return emptyList<RemovableSource>() to emptyList()

        val roots = discoverRoots()
        val sources = roots.map { root ->
            RemovableSource(
                uriString = Uri.fromFile(root.directory).toString(),
                name = root.name,
                connected = root.directory.isDirectory && root.directory.canRead(),
            )
        }
        val folders = mutableListOf<MediaFolder>()
        val deadlineNanos = System.nanoTime() + 8_000_000_000L
        var visitedDirectories = 0

        roots.forEach { root ->
            if (System.nanoTime() >= deadlineNanos || visitedDirectories >= 6_000) return@forEach
            if (!root.directory.isDirectory || !root.directory.canRead()) return@forEach

            val sourceUri = Uri.fromFile(root.directory).toString()
            val queue = ArrayDeque<DirectoryNode>()
            val rootChildren = safeChildren(root.directory)

            collectFolderFiles(root.directory, root.name, sourceUri, rootChildren, folders)

            rootChildren
                .asSequence()
                .filter(File::isDirectory)
                .filter(::isReadableDirectory)
                .filter { !root.primary || it.name.lowercase(Locale.ROOT) in commonInternalFolders }
                .sortedBy(::directoryPriority)
                .forEach { queue.add(DirectoryNode(it, 1)) }

            val seen = hashSetOf<String>()
            while (queue.isNotEmpty() && System.nanoTime() < deadlineNanos && visitedDirectories < 6_000) {
                val node = queue.removeFirst()
                if (node.depth > 16) continue
                val path = node.directory.absolutePath
                if (!seen.add(path)) continue
                visitedDirectories++

                val children = safeChildren(node.directory)
                collectFolderFiles(node.directory, root.name, sourceUri, children, folders)
                children
                    .asSequence()
                    .filter(File::isDirectory)
                    .filter(::isReadableDirectory)
                    .sortedBy(::directoryPriority)
                    .forEach { queue.add(DirectoryNode(it, node.depth + 1)) }
            }
        }

        return sources.distinctBy(RemovableSource::uriString) to folders
    }

    private fun collectFolderFiles(
        directory: File,
        sourceName: String,
        sourceUri: String,
        children: Array<File>,
        output: MutableList<MediaFolder>,
    ) {
        val folderName = directory.name.ifBlank { sourceName }
        val mediaFiles = children
            .asSequence()
            .filter(File::isFile)
            .mapNotNull { toMediaFile(it, folderName, sourceName) }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
            .toList()

        if (mediaFiles.isNotEmpty()) {
            output += MediaFolder(
                id = "$sourceUri#${directory.absolutePath}",
                name = folderName,
                sourceName = sourceName,
                sourceUriString = sourceUri,
                files = mediaFiles,
            )
        }
    }

    private fun discoverRoots(): List<ScanRoot> {
        val roots = linkedMapOf<String, ScanRoot>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(StorageManager::class.java)
                ?.storageVolumes
                .orEmpty()
                .forEach { volume ->
                    val directory = volume.directory ?: return@forEach
                    val path = directory.absolutePath
                    roots[path] = ScanRoot(
                        name = if (volume.isPrimary) {
                            "Внутренняя память"
                        } else {
                            volume.getDescription(context).ifBlank { "Съёмный носитель" }
                        },
                        directory = directory,
                        primary = volume.isPrimary,
                    )
                }
        }

        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        roots.putIfAbsent(
            primary.absolutePath,
            ScanRoot("Внутренняя память", primary, true),
        )

        context.getExternalFilesDirs(null).filterNotNull().forEach { appDirectory ->
            deriveVolumeRoot(appDirectory)?.let { root ->
                val isPrimary = root.absolutePath == primary.absolutePath
                roots.putIfAbsent(
                    root.absolutePath,
                    ScanRoot(
                        name = if (isPrimary) "Внутренняя память" else "Съёмный носитель",
                        directory = root,
                        primary = isPrimary,
                    ),
                )
            }
        }

        return roots.values.toList()
    }

    private fun deriveVolumeRoot(appDirectory: File): File? {
        val marker = "/Android/data/${context.packageName}/files"
        val index = appDirectory.absolutePath.indexOf(marker)
        return if (index > 0) File(appDirectory.absolutePath.substring(0, index)) else null
    }

    private fun safeChildren(directory: File): Array<File> =
        runCatching { directory.listFiles() }.getOrNull() ?: emptyArray()

    private fun isReadableDirectory(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        return file.canRead() && !file.isHidden && name !in skippedNames
    }

    private fun directoryPriority(file: File): Int = when (file.name.lowercase(Locale.ROOT)) {
        "movies", "movie", "video", "videos" -> 0
        "download", "downloads" -> 1
        "dcim", "music" -> 2
        else -> 10
    }

    private fun toMediaFile(file: File, folderName: String, sourceName: String): MediaFile? {
        val extension = file.extension.lowercase(Locale.ROOT)
        val isVideo = extension in videoExtensions
        val isAudio = extension in audioExtensions
        if (!isVideo && !isAudio) return null

        return MediaFile(
            uriString = Uri.fromFile(file).toString(),
            name = file.name,
            mimeType = null,
            sizeBytes = file.length(),
            isVideo = isVideo,
            folderName = folderName,
            sourceName = sourceName,
        )
    }

    private data class ScanRoot(
        val name: String,
        val directory: File,
        val primary: Boolean,
    )

    private data class DirectoryNode(
        val directory: File,
        val depth: Int,
    )
}
