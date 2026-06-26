package com.autovideo.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StreamingUiState(
    val loading: Boolean = false,
    val channels: List<IptvChannel> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val selectedGroup: String? = null,
    val favorites: Set<String> = emptySet(),
    val history: List<IptvChannel> = emptyList(),
) {
    val groups: List<String>
        get() = channels.mapNotNull(IptvChannel::group).distinct().sorted()

    val visibleChannels: List<IptvChannel>
        get() = channels.filter { channel ->
            (selectedGroup == null || channel.group == selectedGroup) &&
                (query.isBlank() ||
                    channel.name.contains(query, ignoreCase = true) ||
                    channel.group.orEmpty().contains(query, ignoreCase = true))
        }
}

class StreamingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository(application.contentResolver)
    private val mutableState = MutableStateFlow(StreamingUiState())
    private var currentLoad: Job? = null

    val uiState: StateFlow<StreamingUiState> = mutableState.asStateFlow()

    fun importPlaylist(uri: Uri) = load { repository.readLocal(uri) }

    fun loadRemotePlaylist(url: String) = load { repository.readRemote(url) }

    fun updateQuery(value: String) {
        mutableState.update { it.copy(query = value.take(120)) }
    }

    fun selectGroup(group: String?) {
        mutableState.update { it.copy(selectedGroup = group) }
    }

    fun toggleFavorite(channel: IptvChannel) {
        mutableState.update { state ->
            val values = state.favorites.toMutableSet().apply {
                if (!add(channel.streamUrl)) remove(channel.streamUrl)
            }
            state.copy(favorites = values)
        }
    }

    fun recordOpened(channel: IptvChannel) {
        mutableState.update { state ->
            state.copy(
                history = (listOf(channel) + state.history)
                    .distinctBy(IptvChannel::streamUrl)
                    .take(30),
            )
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun load(block: suspend () -> M3uParseResult) {
        currentLoad?.cancel()
        currentLoad = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null, warnings = emptyList()) }
            try {
                val result = block()
                mutableState.update { state ->
                    state.copy(
                        loading = false,
                        channels = result.channels,
                        warnings = result.warnings,
                        error = if (result.channels.isEmpty()) {
                            "В плейлисте нет доступных HTTPS-каналов"
                        } else {
                            null
                        },
                        query = "",
                        selectedGroup = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(loading = false, error = AppErrorMapper.userMessage(error))
                }
            }
        }
    }
}
