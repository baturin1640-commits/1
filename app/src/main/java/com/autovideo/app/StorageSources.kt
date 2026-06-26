package com.autovideo.app

import android.content.Context
import android.net.Uri

class StorageSources(context: Context) {
    private val prefs = context.getSharedPreferences("selected_media_sources", Context.MODE_PRIVATE)

    fun selected(): Uri? = prefs.getString(KEY_SELECTED_URI, null)?.let(Uri::parse)

    fun all(): List<Uri> = selected()?.let(::listOf).orEmpty()

    fun select(uri: Uri) {
        prefs.edit()
            .putString(KEY_SELECTED_URI, uri.toString())
            .remove(LEGACY_URIS)
            .apply()
    }

    fun add(uri: Uri) = select(uri)

    fun remove(uriString: String) {
        if (selected()?.toString() == uriString) {
            prefs.edit().remove(KEY_SELECTED_URI).remove(LEGACY_URIS).apply()
        }
    }

    private companion object {
        const val KEY_SELECTED_URI = "selected_uri"
        const val LEGACY_URIS = "uris"
    }
}
