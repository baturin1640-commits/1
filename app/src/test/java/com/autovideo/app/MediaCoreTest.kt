package com.autovideo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCoreTest {
    @Test
    fun secureUrlValidatorRejectsUnsafeSchemesAndCredentials() {
        assertNull(OnlineUrlValidator.normalizeSecureUrl("http://example.com/video.m3u8"))
        assertNull(OnlineUrlValidator.normalizeSecureUrl("file:///storage/video.mp4"))
        assertNull(OnlineUrlValidator.normalizeSecureUrl("javascript:alert(1)"))
        assertNull(OnlineUrlValidator.normalizeSecureUrl("https://user:pass@example.com/list.m3u"))
        assertEquals(
            "https://example.com/video.m3u8",
            OnlineUrlValidator.normalizeSecureUrl(" HTTPS://EXAMPLE.COM/video.m3u8 "),
        )
    }

    @Test
    fun streamTypeDetectionSupportsHlsDashAndProgressive() {
        assertEquals(StreamKind.HLS, OnlineUrlValidator.detectStreamKind("https://host/live/index.m3u8"))
        assertEquals(StreamKind.DASH, OnlineUrlValidator.detectStreamKind("https://host/live/manifest.mpd"))
        assertEquals(StreamKind.HLS, OnlineUrlValidator.detectStreamKind("https://host/live", "application/vnd.apple.mpegurl"))
        assertEquals(StreamKind.PROGRESSIVE, OnlineUrlValidator.detectStreamKind("https://host/video.mp4"))
    }

    @Test
    fun rutubeValidationAllowsOnlyRutubeHosts() {
        assertTrue(OnlineUrlValidator.isRutubeUrl("rutube.ru/video/123"))
        assertTrue(OnlineUrlValidator.isRutubeUrl("https://embed.rutube.ru/play/embed/123"))
        assertFalse(OnlineUrlValidator.isRutubeUrl("https://rutube.ru.example.com/video/123"))
        assertFalse(OnlineUrlValidator.isRutubeUrl("javascript:alert(1)"))
    }

    @Test
    fun extendedM3uParsesBomCrLfAttributesAndDeduplicates() {
        val text = "\uFEFF#EXTM3U\r\n" +
            "#EXTINF:-1 tvg-id=\"one\" tvg-name=\"Первый\" tvg-logo=\"https://img.example/logo.png\" group-title=\"Новости\",Запасное имя\r\n" +
            "https://stream.example/live/one.m3u8\r\n" +
            "#EXTINF:-1,Дубликат\r\n" +
            "https://stream.example/live/one.m3u8\r\n"

        val result = PlaylistParser.parse(text)

        assertTrue(result.extended)
        assertEquals(1, result.channels.size)
        assertEquals("one", result.channels.single().id)
        assertEquals("Первый", result.channels.single().name)
        assertEquals("Новости", result.channels.single().group)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun damagedAndEmptyPlaylistsDoNotCrash() {
        assertTrue(PlaylistParser.parse("").channels.isEmpty())
        val damaged = PlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Broken\nnot a url\n#comment")
        assertTrue(damaged.channels.isEmpty())
        assertTrue(damaged.warnings.isNotEmpty())
    }

    @Test
    fun relativePlaylistLinksResolveAgainstSecureBase() {
        val result = PlaylistParser.parse(
            "#EXTM3U\n#EXTINF:-1,Channel\nstreams/live.m3u8",
            "https://example.com/lists/main.m3u",
        )
        assertEquals("https://example.com/lists/streams/live.m3u8", result.channels.single().streamUrl)
    }

    @Test
    fun hierarchyCreatesOnlyDirectChildrenAtEachLevel() {
        val media = MediaFile(
            uriString = "content://drive/movies/trips/day1.mp4",
            name = "day1.mp4",
            mimeType = "video/mp4",
            sizeBytes = 100,
            isVideo = true,
            folderName = "Trips",
            sourceName = "USB DISK",
        )
        val folders = MediaHierarchy.build(
            sourceName = "USB DISK",
            sourceUri = "content://drive",
            directories = listOf(IndexedMediaDirectory("Movies/Trips", listOf(media))),
        )
        val state = LibraryUiState(folders = folders)
        val movies = state.childrenOf(null).single()
        val trips = state.childrenOf(movies.id).single()

        assertEquals("Movies", movies.name)
        assertEquals("Trips", trips.name)
        assertTrue(movies.files.isEmpty())
        assertEquals(listOf(media), trips.files)
        assertEquals(1, state.videoCount(movies))
    }
}
