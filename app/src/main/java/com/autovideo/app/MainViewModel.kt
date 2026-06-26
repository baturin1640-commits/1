package com.autovideo.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sourceStore = StorageSources(application)
    private val selectedFolderScanner = MediaScanner(application)
    private val mediaStoreScanner = InternalMediaScanner(application)
    private val fileSystemScanner = FileSystemMediaScanner(application)
    private val mutableState = MutableStateFlow(LibraryUiState())
    private var currentScan: Job? = null

    val uiState: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun addSource(uri: Uri) {
        sourceStore.add(uri)
        refresh()
    }

    fun removeSource(uriString: String) {
        if (uriString == INTERNAL_SOURCE_URI || uriString.startsWith("file:")) return
        sourceStore.remove(uriString)
        refresh()
    }

    fun refresh() {
        currentScan?.cancel()
        currentScan = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    val selectedFolders = selectedFolderScanner.scan(sourceStore.all())
                    val automatic = if (FullStorageAccess.isGranted(getApplication())) {
                        fileSystemScanner.scan()
                    } else {
                        mediaStoreScanner.scan(MediaPermissions.access(getApplication()))
                    }

                    val sources = (automatic.first + selectedFolders.first)
                        .distinctBy(RemovableSource::uriString)
                        .sortedBy { it.name.lowercase(Locale.getDefault()) }

                    val folders = deduplicateFolders(automatic.second + selectedFolders.second)
                        .sortedWith(
                            compareBy<MediaFolder> {
                                it.sourceName.lowercase(Locale.getDefault())
                            }.thenBy {
                                it.name.lowercase(Locale.getDefault())
                            },
                        )
                    sources to folders
                }

                mutableState.value = LibraryUiState(
                    loading = false,
                    sources = result.first,
                    folders = result.second,
                )
            } catch (throwable: Throwable) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = throwable.message ?: "Не удалось прочитать медиатеку",
                )
            }
        }
    }

    private fun deduplicateFolders(folders: List<MediaFolder>): List<MediaFolder> {
        val unique = linkedMapOf<String, MediaFolder>()
        folders.forEach { folder ->
            val fileSignature = folder.files
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .joinToString("|") { "${it.name.lowercase(Locale.getDefault())}:${it.sizeBytes}" }
            val key = "${folder.name.lowercase(Locale.getDefault())}|$fileSignature"
            unique.putIfAbsent(key, folder)
        }
        return unique.values.toList()
    }
}
