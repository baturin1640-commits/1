package com.autovideo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistParserTest {
    @Test
    fun parsesExtendedPlaylistWithBomAndWindowsLines() {
        val text = "\uFEFF#EXTM3U\r\n" +
            "#EXTINF:-1 tvg-id=\"one\" tvg-name=\"Первый\" group-title=\"Новости\",Канал\r\n" +
            "https://example.com/one.m3u8\r\n"

        val result = PlaylistParser.parse(text)

        assertTrue(result.extended)
        assertEquals(1, result.channels.size)
        assertEquals("one", result.channels.single().id)
        assertEquals("Первый", result.channels.single().name)
        assertEquals("Новости", result.channels.single().group)
    }

    @Test
    fun ignoresCommentsMalformedLinesAndDuplicates() {
        val text = "#EXTM3U\n# comment\nnot-a-url\n" +
            "#EXTINF:-1,First\nhttps://example.com/live.m3u8\n" +
            "#EXTINF:-1,Duplicate\nhttps://example.com/live.m3u8\n"

        val result = PlaylistParser.parse(text)

        assertEquals(1, result.channels.size)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun resolvesSecureRelativeStream() {
        val result = PlaylistParser.parse(
            "#EXTM3U\n#EXTINF:-1,Channel\n../live/channel.m3u8",
            "https://example.com/lists/index.m3u",
        )

        assertEquals("https://example.com/live/channel.m3u8", result.channels.single().streamUrl)
    }

    @Test
    fun emptyPlaylistDoesNotCrash() {
        val result = PlaylistParser.parse("\uFEFF\r\n")
        assertTrue(result.channels.isEmpty())
    }
}
