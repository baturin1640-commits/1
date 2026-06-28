package com.autovideo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineUrlValidatorTest {
    @Test
    fun blocksUnsupportedSchemes() {
        assertNull(OnlineUrlValidator.normalizeSecureUrl("http://example.com/live.m3u8"))
        assertNull(OnlineUrlValidator.normalizeSecureUrl("file:///video.mp4"))
    }

    @Test
    fun detectsStreamKinds() {
        assertEquals(StreamKind.HLS, OnlineUrlValidator.detectStreamKind("https://example.com/live.m3u8"))
        assertEquals(StreamKind.DASH, OnlineUrlValidator.detectStreamKind("https://example.com/manifest.mpd"))
        assertEquals(StreamKind.PROGRESSIVE, OnlineUrlValidator.detectStreamKind("https://example.com/movie.mp4"))
    }

    @Test
    fun acceptsOnlyRutubeDomains() {
        assertTrue(OnlineUrlValidator.isRutubeUrl("rutube.ru/video/abc"))
        assertTrue(OnlineUrlValidator.isRutubeUrl("https://video.rutube.ru/video/abc"))
        assertFalse(OnlineUrlValidator.isRutubeUrl("https://example.com/video/abc"))
    }
}
