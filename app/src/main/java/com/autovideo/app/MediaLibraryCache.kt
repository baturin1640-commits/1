package com.autovideo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedMediaLibrary(
    val sources: List<RemovableSource>,
    val folders: List<MediaFolder>,
    val savedAtMs: Long,
    val fingerprint: String,
)

class MediaLibraryCache(context: Context) {
    private val file = File(context.filesDir, "media_library_cache.json")

    fun load(): CachedMediaLibrary? = runCatching {
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        if (root.optInt("version") != 1) return null
        CachedMediaLibrary(
            sources = decodeSources(root.optJSONArray("sources")),
            folders = decodeFolders(root.optJSONArray("folders")),
            savedAtMs = root.optLong("savedAtMs"),
            fingerprint = root.optString("fingerprint"),
        )
    }.getOrNull()

    fun save(sources: List<RemovableSource>, folders: List<MediaFolder>, fingerprint: String) {
        runCatching {
            val root = JSONObject()
                .put("version", 1)
                .put("savedAtMs", System.currentTimeMillis())
                .put("fingerprint", fingerprint)
                .put("sources", encodeSources(sources))
                .put("folders", encodeFolders(folders))
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(root.toString())
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                file.writeText(root.toString())
                temp.delete()
            }
        }
    }

    fun clear() {
        file.delete()
    }

    private fun encodeSources(items: List<RemovableSource>) = JSONArray().apply {
        items.forEach { source ->
            put(JSONObject()
                .put("uri", source.uriString)
                .put("name", source.name)
                .put("connected", source.connected))
        }
    }

    private fun encodeFolders(items: List<MediaFolder>) = JSONArray().apply {
        items.forEach { folder ->
            val files = JSONArray()
            folder.files.forEach { media ->
                files.put(JSONObject()
                    .put("uri", media.uriString)
                    .put("name", media.name)
                    .put("mime", media.mimeType.orEmpty())
                    .put("size", media.sizeBytes)
                    .put("video", media.isVideo)
                    .put("folder", media.folderName)
                    .put("source", media.sourceName))
            }
            put(JSONObject()
                .put("id", folder.id)
                .put("name", folder.name)
                .put("source", folder.sourceName)
                .put("sourceUri", folder.sourceUriString)
                .put("files", files))
        }
    }

    private fun decodeSources(array: JSONArray?): List<RemovableSource> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uri = item.optString("uri")
            if (uri.isBlank()) continue
            add(RemovableSource(uri, item.optString("name", "Носитель"), item.optBoolean("connected", true)))
        }
    }

    private fun decodeFolders(array: JSONArray?): List<MediaFolder> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val fileArray = item.optJSONArray("files") ?: continue
            val files = buildList {
                for (fileIndex in 0 until fileArray.length()) {
                    val media = fileArray.optJSONObject(fileIndex) ?: continue
                    val uri = media.optString("uri")
                    val name = media.optString("name")
                    if (uri.isBlank() || name.isBlank()) continue
                    add(MediaFile(
                        uriString = uri,
                        name = name,
                        mimeType = media.optString("mime").ifBlank { null },
                        sizeBytes = media.optLong("size"),
                        isVideo = media.optBoolean("video", true),
                        folderName = media.optString("folder", "Медиа"),
                        sourceName = media.optString("source", "Память"),
                    ))
                }
            }
            if (files.isEmpty()) continue
            add(MediaFolder(
                id = item.optString("id"),
                name = item.optString("name", "Медиа"),
                sourceName = item.optString("source", "Память"),
                sourceUriString = item.optString("sourceUri"),
                files = files,
            ))
        }
    }
}
