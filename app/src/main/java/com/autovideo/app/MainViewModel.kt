package com.autovideo.app

import android.app.Application
import android.net.Uri
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
    private val mediaScanner = MediaScanner(application)
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
        sourceStore.remove(uriString)
        refresh()
    }

    fun refresh() {
        currentScan?.cancel()
        currentScan = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    mediaScanner.scan(sourceStore.all())
                }
                mutableState.value = LibraryUiState(
                    loading = false,
                    sources = result.first,
                    folders = result.second,
                )
            } catch (throwable: Throwable) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = throwable.message ?: "Не удалось прочитать носитель",
                )
            }
        }
    }
}
