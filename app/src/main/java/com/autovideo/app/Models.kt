package com.autovideo.app

import android.net.Uri

data class RemovableSource(
    val uriString: String,
    val name: String,
    val connected: Boolean,
)

data class MediaFile(
    val uriString: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val folderName: String,
    val sourceName: String,
) {
    val uri: Uri get() = Uri.parse(uriString)
    val isAudio: Boolean get() = !isVideo
}

data class MediaFolder(
    val id: String,
    val name: String,
    val sourceName: String,
    val sourceUriString: String,
    val path: String,
    val parentId: String?,
    val files: List<MediaFile>,
) {
    val directVideoCount: Int get() = files.count(MediaFile::isVideo)
    val directAudioCount: Int get() = files.size - directVideoCount
}

data class LibraryUiState(
    val loading: Boolean = false,
    val sources: List<RemovableSource> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val error: String? = null,
) {
    val videoFiles: List<MediaFile> get() = folders.flatMap(MediaFolder::files).filter(MediaFile::isVideo)
    val audioFiles: List<MediaFile> get() = folders.flatMap(MediaFolder::files).filter(MediaFile::isAudio)

    fun childrenOf(parentId: String?): List<MediaFolder> = folders
        .filter { it.parentId == parentId }
        .sortedBy { it.name.lowercase() }

    fun descendantsOf(folderId: String): List<MediaFolder> {
        val result = mutableListOf<MediaFolder>()
        val queue = ArrayDeque(childrenOf(folderId))
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            result += folder
            queue.addAll(childrenOf(folder.id))
        }
        return result
    }

    fun filesInTree(folder: MediaFolder): List<MediaFile> =
        (listOf(folder) + descendantsOf(folder.id)).flatMap(MediaFolder::files)

    fun videoCount(folder: MediaFolder): Int = filesInTree(folder).count(MediaFile::isVideo)

    fun audioCount(folder: MediaFolder): Int = filesInTree(folder).count(MediaFile::isAudio)

    val videoRootFolders: List<MediaFolder>
        get() = childrenOf(null).filter { videoCount(it) > 0 }

    val audioRootFolders: List<MediaFolder>
        get() = childrenOf(null).filter { audioCount(it) > 0 }
}
