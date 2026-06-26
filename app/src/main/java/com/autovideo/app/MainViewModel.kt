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
    private val libraryCache = MediaLibraryCache(application)
    private val mutableState = MutableStateFlow(LibraryUiState(loading = true))

    private var currentScan: Job? = null
    private var refreshRequestedWhileScanning = false

    val uiState: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init {
        initializeLibrary()
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
        startScan(forceVisibleProgress = mutableState.value.folders.isEmpty())
    }

    private fun initializeLibrary() {
        viewModelScope.launch {
            val fingerprint = withContext(Dispatchers.IO) { currentFingerprint() }
            val cached = withContext(Dispatchers.IO) { libraryCache.load() }

            if (cached != null && cached.folders.isNotEmpty()) {
                mutableState.value = LibraryUiState(
                    loading = false,
                    sources = cached.sources,
                    folders = cached.folders,
                )
                val age = System.currentTimeMillis() - cached.savedAtMs
                val stale = age > CACHE_MAX_AGE_MS || cached.fingerprint != fingerprint
                if (stale) startScan(forceVisibleProgress = false)
            } else {
                startScan(forceVisibleProgress = true)
            }
        }
    }

    private fun startScan(forceVisibleProgress: Boolean) {
        if (currentScan?.isActive == true) {
            refreshRequestedWhileScanning = true
            return
        }

        currentScan = viewModelScope.launch {
            var showProgress = forceVisibleProgress
            do {
                refreshRequestedWhileScanning = false
                performScan(showProgress)
                showProgress = false
            } while (refreshRequestedWhileScanning)
        }
    }

    private suspend fun performScan(showVisibleProgress: Boolean) {
        if (showVisibleProgress) {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
        } else {
            mutableState.value = mutableState.value.copy(error = null)
        }

        try {
            val result = withContext(Dispatchers.IO) {
                val selectedUris = sourceStore.all()
                val selectedFoldersResult = if (selectedUris.isEmpty()) {
                    Result.success(emptyScanResult())
                } else {
                    runCatching { selectedFolderScanner.scan(selectedUris) }
                }
                val mediaStoreResult = runCatching {
                    mediaStoreScanner.scan(MediaPermissions.access(getApplication()))
                }
                val fileSystemResult = runCatching { fileSystemScanner.scan() }

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
                val fingerprint = currentFingerprint()

                if (folders.isNotEmpty() || errors.isEmpty()) {
                    libraryCache.save(sources, folders, fingerprint)
                }

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

            val previous = mutableState.value
            if (result.folders.isEmpty() && previous.folders.isNotEmpty() && result.error != null) {
                mutableState.value = previous.copy(loading = false, error = result.error)
            } else {
                mutableState.value = LibraryUiState(
                    loading = false,
                    sources = result.sources,
                    folders = result.folders,
                    error = result.error,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                error = throwable.message ?: "Не удалось прочитать медиатеку",
            )
        }
    }

    private fun currentFingerprint(): String {
        val selected = sourceStore.all().joinToString("|") { it.toString() }
        return "$selected::${fileSystemScanner.signature()}"
    }

    private fun deduplicateSources(sources: List<RemovableSource>): List<RemovableSource> {
        val unique = linkedMapOf<String, RemovableSource>()
        sources.forEach { source ->
            val key = if (source.name.equals("Внутренняя память", ignoreCase = true)) {
                "internal-storage"
            } else {
                source.uriString
            }
            val existing = unique[key]
            if (existing == null || (!existing.connected && source.connected)) unique[key] = source
        }
        return unique.values.toList()
    }

    private fun deduplicateFolders(folders: List<MediaFolder>): List<MediaFolder> {
        val unique = linkedMapOf<String, MediaFolder>()
        folders.forEach { folder ->
            val files = folder.files.distinctBy(MediaFile::uriString)
            val key = files.joinToString("|") { it.uriString }
                .ifBlank { "${folder.sourceName}:${folder.name}" }
            unique.putIfAbsent(key, folder.copy(files = files))
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

    private companion object {
        const val CACHE_MAX_AGE_MS = 12L * 60L * 60L * 1_000L
    }
}
