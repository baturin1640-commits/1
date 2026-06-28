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
    private val selectedFolderScanner = SafMediaScanner(application)
    private val mediaStoreScanner = InternalMediaScanner(application)
    private val libraryCache = MediaLibraryCache(application)
    private val mutableState = MutableStateFlow(LibraryUiState(loading = true))

    private var currentScan: Job? = null
    private var refreshRequestedWhileScanning = false

    val uiState: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init {
        initializeLibrary()
    }

    fun addSource(uri: Uri) {
        sourceStore.select(uri)
        libraryCache.clear()
        mutableState.value = LibraryUiState(loading = true)
        refresh()
    }

    fun removeSource(uriString: String) {
        sourceStore.remove(uriString)
        libraryCache.clear()
        mutableState.value = LibraryUiState(loading = true)
        refresh()
    }

    fun refresh() {
        startScan(forceVisibleProgress = mutableState.value.folders.isEmpty())
    }

    private fun initializeLibrary() {
        viewModelScope.launch {
            val fingerprint = withContext(Dispatchers.IO) { currentFingerprint() }
            val cached = withContext(Dispatchers.IO) { libraryCache.load() }
            val cacheMatches = cached != null && cached.fingerprint == fingerprint

            if (cacheMatches) {
                mutableState.value = LibraryUiState(
                    loading = false,
                    sources = cached!!.sources,
                    folders = cached.folders,
                )
                val age = System.currentTimeMillis() - cached.savedAtMs
                if (age > CACHE_MAX_AGE_MS) startScan(forceVisibleProgress = false)
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
        mutableState.value = mutableState.value.copy(
            loading = showVisibleProgress && mutableState.value.folders.isEmpty(),
            error = null,
        )

        try {
            val result = withContext(Dispatchers.IO) {
                val selected = sourceStore.selected()
                val scanResult = if (selected != null) {
                    runCatching { selectedFolderScanner.scan(selected) }
                } else {
                    runCatching {
                        mediaStoreScanner.scan(MediaPermissions.access(getApplication()))
                    }
                }

                val data = scanResult.getOrDefault(emptyScanResult())
                val sources = data.first.take(1)
                val folders = data.second
                    .distinctBy(MediaFolder::id)
                    .map { it.copy(files = it.files.distinctBy(MediaFile::uriString)) }
                    .sortedWith(
                        compareBy<MediaFolder> { it.path.count { char -> char == '/' } }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )
                val sourceUnavailable = selected != null && sources.firstOrNull()?.connected == false
                val error = when {
                    sourceUnavailable -> "Накопитель отключён или доступ к нему потерян"
                    scanResult.isFailure -> AppErrorMapper.userMessage(scanResult.exceptionOrNull())
                    else -> null
                }
                val fingerprint = currentFingerprint()

                if (scanResult.isSuccess) {
                    libraryCache.save(sources, folders, fingerprint)
                }
                ScanResult(sources, folders, error)
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
                error = AppErrorMapper.userMessage(throwable),
            )
        }
    }

    private fun currentFingerprint(): String = sourceStore.selected()?.let {
        "selected::$it::complete-scan-v1"
    } ?: MediaPermissions.access(getApplication<Application>()).let { access ->
        "internal::video=${access.video};audio=${access.audio}"
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
