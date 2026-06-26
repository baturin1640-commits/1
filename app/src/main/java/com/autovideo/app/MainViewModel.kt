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
    private val removableMediaScanner = MediaScanner(application)
    private val internalMediaScanner = InternalMediaScanner(application)
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
        if (uriString == INTERNAL_SOURCE_URI) return
        sourceStore.remove(uriString)
        refresh()
    }

    fun refresh() {
        currentScan?.cancel()
        currentScan = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    val removable = removableMediaScanner.scan(sourceStore.all())
                    val internal = internalMediaScanner.scan(
                        MediaPermissions.access(getApplication()),
                    )

                    val sources = buildList {
                        internal.first?.let(::add)
                        addAll(removable.first)
                    }
                    val folders = (internal.second + removable.second).sortedWith(
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
}
