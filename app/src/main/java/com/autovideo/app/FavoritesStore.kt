package com.autovideo.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavoritesState(
    val fileUris: Set<String> = emptySet(),
    val folderIds: Set<String> = emptySet(),
)

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_favorites", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readState())

    val state: StateFlow<FavoritesState> = mutableState.asStateFlow()

    fun isFavorite(file: MediaFile): Boolean = file.uriString in mutableState.value.fileUris

    fun isFavorite(folder: MediaFolder): Boolean = folder.id in mutableState.value.folderIds

    fun toggle(file: MediaFile) {
        val next = mutableState.value.fileUris.toMutableSet().apply {
            if (!add(file.uriString)) remove(file.uriString)
        }
        prefs.edit().putStringSet(KEY_FILES, next).apply()
        mutableState.value = mutableState.value.copy(fileUris = next)
    }

    fun toggle(folder: MediaFolder) {
        val next = mutableState.value.folderIds.toMutableSet().apply {
            if (!add(folder.id)) remove(folder.id)
        }
        prefs.edit().putStringSet(KEY_FOLDERS, next).apply()
        mutableState.value = mutableState.value.copy(folderIds = next)
    }

    private fun readState() = FavoritesState(
        fileUris = prefs.getStringSet(KEY_FILES, emptySet()).orEmpty().toSet(),
        folderIds = prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty().toSet(),
    )

    private companion object {
        const val KEY_FILES = "favorite_files"
        const val KEY_FOLDERS = "favorite_folders"
    }
}
