package com.autovideo.app

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PlaylistRepository(private val resolver: ContentResolver) {
    suspend fun readLocal(uri: Uri): M3uParseResult = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use(::readLimitedUtf8)
            ?: throw IOException("Не удалось открыть выбранный плейлист")
        PlaylistParser.parse(text)
    }

    suspend fun readRemote(rawUrl: String): M3uParseResult = withContext(Dispatchers.IO) {
        downloadRemote(rawUrl)
    }

    private suspend fun downloadRemote(rawUrl: String): M3uParseResult {
        var currentUrl = OnlineUrlValidator.normalizeSecureUrl(rawUrl)
            ?: throw IOException("Разрешены только корректные HTTPS-адреса")
        var redirectCount = 0

        while (redirectCount <= MAX_REDIRECTS) {
            currentCoroutineContext().ensureActive()
            val connection = URL(currentUrl).openConnection() as? HttpsURLConnection
                ?: throw IOException("Требуется защищённое HTTPS-соединение")
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", PLAYLIST_ACCEPT)
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connect()

                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    currentUrl = OnlineUrlValidator.resolveSecureUrl(currentUrl, location.orEmpty())
                        ?: throw IOException("Сервер перенаправил на небезопасный адрес")
                    redirectCount++
                    continue
                }
                if (code !in 200..299) throw IOException("Сервер вернул ошибку $code")
                if (connection.contentLengthLong > MAX_BYTES) throw IOException("Плейлист слишком большой")

                val text = connection.inputStream.use(::readLimitedUtf8)
                return PlaylistParser.parse(text, currentUrl)
            } finally {
                connection.disconnect()
            }
        }

        throw IOException("Слишком много перенаправлений")
    }

    private fun readLimitedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) throw IOException("Плейлист слишком большой")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 5L * 1024L * 1024L
        const val USER_AGENT = "VideoHeadUnit/0.8"
        const val PLAYLIST_ACCEPT = "application/vnd.apple.mpegurl, audio/x-mpegurl, text/plain, */*"
    }
}
