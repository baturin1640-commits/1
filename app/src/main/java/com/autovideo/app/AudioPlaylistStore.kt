package com.autovideo.app

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AudioPlaylist(
    val id: String,
    val name: String,
    val trackUris: List<String>,
    val createdAtMs: Long,
)

class AudioPlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readPlaylists())

    val state: StateFlow<List<AudioPlaylist>> = mutableState.asStateFlow()

    fun create(name: String): AudioPlaylist? {
        val cleanName = name.trim().take(60)
        if (cleanName.isBlank()) return null
        val playlist = AudioPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            trackUris = emptyList(),
            createdAtMs = System.currentTimeMillis(),
        )
        save(mutableState.value + playlist)
        return playlist
    }

    fun rename(id: String, name: String) {
        val cleanName = name.trim().take(60)
        if (cleanName.isBlank()) return
        save(mutableState.value.map { playlist ->
            if (playlist.id == id) playlist.copy(name = cleanName) else playlist
        })
    }

    fun delete(id: String) {
        save(mutableState.value.filterNot { it.id == id })
    }

    fun addTrack(id: String, file: MediaFile) {
        save(mutableState.value.map { playlist ->
            if (playlist.id == id && file.uriString !in playlist.trackUris) {
                playlist.copy(trackUris = playlist.trackUris + file.uriString)
            } else {
                playlist
            }
        })
    }

    fun removeTrack(id: String, uriString: String) {
        save(mutableState.value.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(trackUris = playlist.trackUris.filterNot { it == uriString })
            } else {
                playlist
            }
        })
    }

    fun replaceTracks(id: String, files: List<MediaFile>) {
        save(mutableState.value.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(trackUris = files.distinctBy(MediaFile::uriString).map(MediaFile::uriString))
            } else {
                playlist
            }
        })
    }

    private fun save(playlists: List<AudioPlaylist>) {
        val normalized = playlists
            .distinctBy(AudioPlaylist::id)
            .sortedBy(AudioPlaylist::createdAtMs)
        val array = JSONArray()
        normalized.forEach { playlist ->
            array.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("createdAtMs", playlist.createdAtMs)
                    put("tracks", JSONArray(playlist.trackUris))
                }
            )
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
        mutableState.value = normalized
    }

    private fun readPlaylists(): List<AudioPlaylist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val tracksJson = item.optJSONArray("tracks") ?: JSONArray()
                    val tracks = buildList {
                        for (trackIndex in 0 until tracksJson.length()) {
                            tracksJson.optString(trackIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val name = item.optString("name").takeIf(String::isNotBlank) ?: "Плейлист"
                    add(
                        AudioPlaylist(
                            id = id,
                            name = name,
                            trackUris = tracks.distinct(),
                            createdAtMs = item.optLong("createdAtMs", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "audio_playlists"
        const val KEY_PLAYLISTS = "playlists_json"
    }
}
