package com.autovideo.app

import java.util.Locale

data class IndexedMediaDirectory(
    val path: String,
    val files: List<MediaFile>,
)

object MediaHierarchy {
    fun build(
        sourceName: String,
        sourceUri: String,
        directories: List<IndexedMediaDirectory>,
    ): List<MediaFolder> {
        val directFiles = linkedMapOf<String, MutableList<MediaFile>>()
        val allPaths = linkedSetOf<String>()

        directories.forEach { directory ->
            val normalized = normalize(directory.path)
            val targetPath = normalized.ifBlank { ROOT_PATH }
            directFiles.getOrPut(targetPath) { mutableListOf() }.addAll(directory.files)

            if (targetPath == ROOT_PATH) {
                allPaths += ROOT_PATH
            } else {
                val segments = targetPath.split('/').filter(String::isNotBlank)
                for (index in segments.indices) {
                    allPaths += segments.take(index + 1).joinToString("/")
                }
            }
        }

        return allPaths
            .sortedWith(compareBy<String> { it.count { char -> char == '/' } }.thenBy { it.lowercase(Locale.getDefault()) })
            .map { path ->
                val parentPath = when {
                    path == ROOT_PATH -> null
                    '/' !in path -> null
                    else -> path.substringBeforeLast('/')
                }
                MediaFolder(
                    id = id(sourceUri, path),
                    name = if (path == ROOT_PATH) "Корень накопителя" else path.substringAfterLast('/'),
                    sourceName = sourceName,
                    sourceUriString = sourceUri,
                    path = path,
                    parentId = parentPath?.let { id(sourceUri, it) },
                    files = directFiles[path]
                        .orEmpty()
                        .distinctBy(MediaFile::uriString)
                        .sortedBy { it.name.lowercase(Locale.getDefault()) },
                )
            }
    }

    private fun id(sourceUri: String, path: String): String = "$sourceUri#$path"

    private fun normalize(value: String): String = value
        .replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() && it != "." }
        .joinToString("/")

    private const val ROOT_PATH = "__root__"
}
