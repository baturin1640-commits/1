package com.autovideo.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SafMediaScanner(private val context: Context) {
    private val video = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "divx", "mov", "ts", "m2ts",
        "mts", "mpg", "mpeg", "3gp", "flv", "f4v", "wmv", "vob", "ogv", "mxf"
    )
    private val audio = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "ac3",
        "eac3", "dts", "wma", "ape", "alac", "aiff", "mka"
    )

    suspend fun scan(treeUri: Uri): Pair<List<RemovableSource>, List<MediaFolder>> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val name = root?.name?.takeIf(String::isNotBlank) ?: "Накопитель"
        val readable = root?.exists() == true && root.canRead()
        val source = RemovableSource(treeUri.toString(), name, readable)
        if (!readable || root == null) return listOf(source) to emptyList()

        val queue = ArrayDeque<Node>()
        val visited = hashSetOf<String>()
        val directories = mutableListOf<IndexedMediaDirectory>()
        queue.add(Node(root, "", 0))

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val node = queue.removeFirst()
            if (node.depth > 64 || !visited.add(node.directory.uri.toString())) continue
            val children = runCatching { node.directory.listFiles() }.getOrDefault(emptyArray())
            val folderName = node.directory.name?.takeIf(String::isNotBlank) ?: name
            val files = children.asSequence()
                .filter(DocumentFile::isFile)
                .mapNotNull { file -> toMedia(file, folderName, name) }
                .distinctBy(MediaFile::uriString)
                .toList()
            if (files.isNotEmpty()) directories += IndexedMediaDirectory(node.path, files)

            children.asSequence()
                .filter(DocumentFile::isDirectory)
                .forEach { child ->
                    val childName = child.name?.takeIf(String::isNotBlank) ?: return@forEach
                    val path = listOf(node.path, childName).filter(String::isNotBlank).joinToString("/")
                    queue.add(Node(child, path, node.depth + 1))
                }
        }

        return listOf(source) to MediaHierarchy.build(name, treeUri.toString(), directories)
    }

    private fun toMedia(file: DocumentFile, folder: String, source: String): MediaFile? {
        val name = file.name?.takeIf(String::isNotBlank) ?: return null
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = file.type?.lowercase(Locale.ROOT)
        val isVideo = mime?.startsWith("video/") == true || extension in video
        val isAudio = mime?.startsWith("audio/") == true || extension in audio
        if (!isVideo && !isAudio) return null
        return MediaFile(
            uriString = file.uri.toString(),
            name = name,
            mimeType = mime,
            sizeBytes = runCatching { file.length() }.getOrDefault(0L),
            isVideo = isVideo,
            folderName = folder,
            sourceName = source,
        )
    }

    private data class Node(val directory: DocumentFile, val path: String, val depth: Int)
}
