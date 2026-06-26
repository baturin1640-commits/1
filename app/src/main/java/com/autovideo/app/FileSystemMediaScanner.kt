package com.autovideo.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
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
    private val skippedDirectoryNames = setOf(
        "Android", ".thumbnails", "LOST.DIR", "System Volume Information", "$RECYCLE.BIN"
    )

    fun scan(): Pair<List<RemovableSource>, List<MediaFolder>> {
        if (!FullStorageAccess.isGranted(context)) return emptyList<RemovableSource>() to emptyList()

        val roots = discoverRoots()
        val sources = mutableListOf<RemovableSource>()
        val folders = mutableListOf<MediaFolder>()

        roots.forEach { root ->
            val sourceUri = Uri.fromFile(root.directory).toString()
            sources += RemovableSource(
                uriString = sourceUri,
                name = root.name,
                connected = root.directory.exists() && root.directory.canRead(),
            )

            if (root.directory.exists() && root.directory.canRead()) {
                scanDirectory(
                    directory = root.directory,
                    sourceName = root.name,
                    sourceUriString = sourceUri,
                    output = folders,
                    visited = mutableSetOf(),
                    depth = 0,
                )
            }
        }

        return sources.distinctBy(RemovableSource::uriString) to folders
    }

    private fun discoverRoots(): List<ScanRoot> {
        val roots = linkedMapOf<String, ScanRoot>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes.orEmpty().forEach { volume ->
                val directory = volume.directory ?: return@forEach
                val canonical = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
                val name = if (volume.isPrimary) {
                    "Внутренняя память"
                } else {
                    volume.getDescription(context).ifBlank { "Съёмный носитель" }
                }
                roots[canonical] = ScanRoot(name, directory)
            }
        }

        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null) {
            val canonical = runCatching { primary.canonicalPath }.getOrDefault(primary.absolutePath)
            roots.putIfAbsent(canonical, ScanRoot("Внутренняя память", primary))
        }

        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            deriveVolumeRoot(appDir)?.let { root ->
                val canonical = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
                val name = if (canonical == runCatching { primary.canonicalPath }.getOrNull()) {
                    "Внутренняя память"
                } else {
                    "Съёмный носитель"
                }
                roots.putIfAbsent(canonical, ScanRoot(name, root))
            }
        }

        return roots.values.toList()
    }

    private fun deriveVolumeRoot(appDirectory: File): File? {
        val marker = "/Android/data/${context.packageName}/files"
        val path = appDirectory.absolutePath
        val index = path.indexOf(marker)
        if (index <= 0) return null
        return File(path.substring(0, index))
    }

    private fun scanDirectory(
        directory: File,
        sourceName: String,
        sourceUriString: String,
        output: MutableList<MediaFolder>,
        visited: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > 40) return
        val canonicalPath = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
        if (!visited.add(canonicalPath)) return

        val children = runCatching { directory.listFiles() }.getOrNull() ?: return
        val folderName = directory.name.ifBlank { sourceName }
        val mediaFiles = children
            .asSequence()
            .filter { it.isFile }
            .mapNotNull { toMediaFile(it, folderName, sourceName) }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
            .toList()

        if (mediaFiles.isNotEmpty()) {
            output += MediaFolder(
                id = "$sourceUriString#$canonicalPath",
                name = folderName,
                sourceName = sourceName,
                sourceUriString = sourceUriString,
                files = mediaFiles,
            )
        }

        children.asSequence()
            .filter { it.isDirectory && it.canRead() }
            .filterNot { it.name in skippedDirectoryNames }
            .forEach { child ->
                scanDirectory(
                    directory = child,
                    sourceName = sourceName,
                    sourceUriString = sourceUriString,
                    output = output,
                    visited = visited,
                    depth = depth + 1,
                )
            }
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
    )
}
