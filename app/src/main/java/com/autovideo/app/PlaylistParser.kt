package com.autovideo.app

object PlaylistParser {
    fun parse(text: String, baseUrl: String? = null): M3uParseResult =
        M3uParseResult(emptyList(), emptyList(), false)
}
