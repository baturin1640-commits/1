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
    val files: List<MediaFile>,
) {
    val videoCount: Int get() = files.count(MediaFile::isVideo)
    val audioCount: Int get() = files.size - videoCount
    val containsVideo: Boolean get() = videoCount > 0
}

data class LibraryUiState(
    val loading: Boolean = false,
    val sources: List<RemovableSource> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val error: String? = null,
) {
    val videoFolders: List<MediaFolder> get() = folders.filter(MediaFolder::containsVideo)
    val videoFiles: List<MediaFile> get() = folders.flatMap(MediaFolder::files).filter(MediaFile::isVideo)
    val audioFiles: List<MediaFile> get() = folders.flatMap(MediaFolder::files).filter(MediaFile::isAudio)
}
