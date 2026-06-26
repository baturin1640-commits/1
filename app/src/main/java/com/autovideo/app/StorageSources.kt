package com.autovideo.app

import android.content.Context
import android.net.Uri

class StorageSources(context: Context) {
    private val prefs = context.getSharedPreferences("selected_media_sources", Context.MODE_PRIVATE)

    fun all(): List<Uri> = prefs.getStringSet("uris", emptySet())
        .orEmpty()
        .toList()
        .sorted()
        .map(Uri::parse)

    fun add(uri: Uri) {
        val values = prefs.getStringSet("uris", emptySet()).orEmpty().toMutableSet()
        values.add(uri.toString())
        prefs.edit().putStringSet("uris", values).apply()
    }

    fun remove(uriString: String) {
        val values = prefs.getStringSet("uris", emptySet()).orEmpty().toMutableSet()
        values.remove(uriString)
        prefs.edit().putStringSet("uris", values).apply()
    }
}
