package com.autovideo.app

internal data class PlaylistEntryMeta(
    val name: String = "",
    val id: String? = null,
    val logo: String? = null,
    val group: String? = null,
)

internal object PlaylistLineParser {
    fun metadata(line: String): PlaylistEntryMeta {
        val payload = line.substringAfter(':', "")
        val title = payload.substringAfter(',', "").trim()
        val attributes = payload.substringBefore(',')
        return PlaylistEntryMeta(
            name = attribute(attributes, "tvg-name").ifBlank { title },
            id = attribute(attributes, "tvg-id").takeIf(String::isNotBlank),
            logo = attribute(attributes, "tvg-logo").takeIf(String::isNotBlank),
            group = attribute(attributes, "group-title").takeIf(String::isNotBlank),
        )
    }

    private fun attribute(source: String, key: String): String {
        val marker = "$key=\""
        val start = source.indexOf(marker, ignoreCase = true)
        if (start < 0) return ""
        val valueStart = start + marker.length
        val end = source.indexOf('"', valueStart)
        return if (end > valueStart) source.substring(valueStart, end).trim() else ""
    }
}
