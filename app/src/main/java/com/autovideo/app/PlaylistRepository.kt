package com.autovideo.app

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PlaylistRepository(private val resolver: ContentResolver) {
    suspend fun readLocal(uri: Uri): M3uParseResult = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use(::readLimitedUtf8)
            ?: throw IOException("Не удалось открыть выбранный плейлист")
        PlaylistParser.parse(text)
    }

    suspend fun readRemote(rawUrl: String): M3uParseResult = withContext(Dispatchers.IO) {
        var currentUrl = OnlineUrlValidator.normalizeSecureUrl(rawUrl)
            ?: throw IOException("Разрешены только корректные HTTPS-адреса")
        var redirectCount = 0

        while (true) {
            ensureActive()
            val connection = (URL(currentUrl).openConnection() as? HttpsURLConnection)
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
                    if (redirectCount >= MAX_REDIRECTS) throw IOException("Слишком много перенаправлений")
                    val location = connection.getHeaderField("Location")
                    currentUrl = OnlineUrlValidator.resolveSecureUrl(currentUrl, location.orEmpty())
                        ?: throw IOException("Сервер перенаправил на небезопасный адрес")
                    redirectCount++
                    continue
                }
                if (code !in 200..299) throw IOException("Сервер вернул ошибку $code")
                val declaredLength = connection.contentLengthLong
                if (declaredLength > MAX_BYTES) throw IOException("Плейлист слишком большой")

                val text = connection.inputStream.use(::readLimitedUtf8)
                return@withContext PlaylistParser.parse(text, currentUrl)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readLimitedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
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
