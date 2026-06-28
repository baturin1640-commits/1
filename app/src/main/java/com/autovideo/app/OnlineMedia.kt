package com.autovideo.app

import java.net.URI
import java.util.Locale

enum class StreamKind {
    HLS,
    DASH,
    PROGRESSIVE,
}

data class OnlineStream(
    val url: String,
    val title: String,
    val kind: StreamKind = OnlineUrlValidator.detectStreamKind(url),
)

data class IptvChannel(
    val id: String?,
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val streamUrl: String,
)

data class M3uParseResult(
    val channels: List<IptvChannel>,
    val warnings: List<String>,
    val extended: Boolean,
)

object OnlineUrlValidator {
    private const val MAX_URL_LENGTH = 4_096

    fun normalizeSecureUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_URL_LENGTH) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null

        return runCatching {
            URI(
                "https",
                null,
                uri.host.lowercase(Locale.ROOT),
                uri.port,
                uri.rawPath.ifBlank { "/" },
                uri.rawQuery,
                null,
            ).toASCIIString()
        }.getOrNull()
    }

    fun resolveSecureUrl(baseUrl: String?, candidate: String): String? {
        normalizeSecureUrl(candidate)?.let { return it }
        val base = baseUrl?.let(::normalizeSecureUrl) ?: return null
        return runCatching { URI(base).resolve(candidate.trim()).toString() }
            .getOrNull()
            ?.let(::normalizeSecureUrl)
    }

    fun normalizeRutubeUrl(raw: String): String? {
        val candidate = raw.trim().let { value ->
            if (value.startsWith("rutube.ru", ignoreCase = true) ||
                value.startsWith("www.rutube.ru", ignoreCase = true)
            ) {
                "https://$value"
            } else {
                value
            }
        }
        val normalized = normalizeSecureUrl(candidate) ?: return null
        val host = runCatching { URI(normalized).host.lowercase(Locale.ROOT) }.getOrNull() ?: return null
        return normalized.takeIf { host == "rutube.ru" || host.endsWith(".rutube.ru") }
    }

    fun isRutubeUrl(raw: String): Boolean = normalizeRutubeUrl(raw) != null

    fun detectStreamKind(url: String, contentType: String? = null): StreamKind {
        val path = runCatching { URI(url).path.lowercase(Locale.ROOT) }.getOrDefault("")
        val type = contentType.orEmpty().lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") || "mpegurl" in type -> StreamKind.HLS
            path.endsWith(".mpd") || "dash+xml" in type -> StreamKind.DASH
            else -> StreamKind.PROGRESSIVE
        }
    }
}
