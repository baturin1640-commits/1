package com.autovideo.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sourceStore = StorageSources(application)
    private val removableScanner = MediaScanner(application)
    private val internalScanner = InternalMediaScanner(application)
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
        if (uriString == InternalMediaScanner.INTERNAL_SOURCE_URI) return
        sourceStore.remove(uriString)
        refresh()
    }

    fun refresh() {
        currentScan?.cancel()
        currentScan = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val videoAccess = canReadInternalVideo()
                val audioAccess = canReadInternalAudio()
                val result = withContext(Dispatchers.IO) {
                    val removable = removableScanner.scan(sourceStore.all())
                    val internalFolders = internalScanner.scan(
                        includeVideo = videoAccess,
                        includeAudio = audioAccess,
                    )
                    Triple(removable.first, removable.second, internalFolders)
                }

                val internalSource = RemovableSource(
                    uriString = InternalMediaScanner.INTERNAL_SOURCE_URI,
                    name = InternalMediaScanner.INTERNAL_SOURCE_NAME,
                    connected = videoAccess || audioAccess,
                    isRemovable = false,
                )

                mutableState.value = LibraryUiState(
                    loading = false,
                    sources = listOf(internalSource) + result.first,
                    folders = (result.third + result.second).distinctBy(MediaFolder::id),
                    internalVideoAccess = videoAccess,
                    internalAudioAccess = audioAccess,
                )
            } catch (throwable: Throwable) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = throwable.message ?: "Не удалось прочитать медиатеку",
                )
            }
        }
    }

    private fun canReadInternalVideo(): Boolean {
        val context = getApplication<Application>()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                    hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                hasPermission(Manifest.permission.READ_MEDIA_VIDEO)

            else -> hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun canReadInternalAudio(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
