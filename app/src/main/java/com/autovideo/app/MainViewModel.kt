package com.autovideo.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
    private var refreshRequestedWhileScanning = false

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
        if (currentScan?.isActive == true) {
            refreshRequestedWhileScanning = true
            return
        }

        currentScan = viewModelScope.launch {
            do {
                refreshRequestedWhileScanning = false
                performScan()
            } while (refreshRequestedWhileScanning)
        }
    }

    private suspend fun performScan() {
        mutableState.value = mutableState.value.copy(loading = true, error = null)

        try {
            val result = withContext(Dispatchers.IO) {
                val selectedFoldersResult = runCatching {
                    selectedFolderScanner.scan(sourceStore.all())
                }
                val mediaStoreResult = runCatching {
                    mediaStoreScanner.scan(MediaPermissions.access(getApplication()))
                }
                val fileSystemResult = runCatching {
                    fileSystemScanner.scan()
                }

                val selectedFolders = selectedFoldersResult.getOrDefault(emptyScanResult())
                val mediaStore = mediaStoreResult.getOrDefault(emptyScanResult())
                val fileSystem = fileSystemResult.getOrDefault(emptyScanResult())

                val sources = deduplicateSources(
                    fileSystem.first + mediaStore.first + selectedFolders.first,
                ).sortedBy { it.name.lowercase(Locale.getDefault()) }

                val folders = deduplicateFolders(
                    fileSystem.second + mediaStore.second + selectedFolders.second,
                ).sortedWith(
                    compareBy<MediaFolder> {
                        it.sourceName.lowercase(Locale.getDefault())
                    }.thenBy {
                        it.name.lowercase(Locale.getDefault())
                    },
                )

                val errors = listOfNotNull(
                    selectedFoldersResult.exceptionOrNull(),
                    mediaStoreResult.exceptionOrNull(),
                    fileSystemResult.exceptionOrNull(),
                )

                ScanResult(
                    sources = sources,
                    folders = folders,
                    error = if (sources.isEmpty() && folders.isEmpty() && errors.isNotEmpty()) {
                        errors.first().message ?: "Не удалось прочитать медиатеку"
                    } else {
                        null
                    },
                )
            }

            mutableState.value = LibraryUiState(
                loading = false,
                sources = result.sources,
                folders = result.folders,
                error = result.error,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                error = throwable.message ?: "Не удалось прочитать медиатеку",
            )
        }
    }

    private fun deduplicateSources(sources: List<RemovableSource>): List<RemovableSource> {
        val unique = linkedMapOf<String, RemovableSource>()
        sources.forEach { source ->
            val normalizedName = source.name.lowercase(Locale.getDefault())
            val key = if (normalizedName == "внутренняя память") {
                "internal-storage"
            } else {
                source.uriString
            }
            val existing = unique[key]
            if (existing == null || (!existing.connected && source.connected)) {
                unique[key] = source
            }
        }
        return unique.values.toList()
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

    private fun emptyScanResult(): Pair<List<RemovableSource>, List<MediaFolder>> =
        emptyList<RemovableSource>() to emptyList()

    private data class ScanResult(
        val sources: List<RemovableSource>,
        val folders: List<MediaFolder>,
        val error: String?,
    )
}
