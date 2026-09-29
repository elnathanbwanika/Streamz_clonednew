package com.linkstreamz.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HlsParser.rewritePlaylist].
 *
 * These tests cover:
 *  - Relative segment URIs resolved against the base URL
 *  - Absolute segment URIs passed through unchanged (still wrapped in proxy URL)
 *  - Master playlist variant rewriting (EXT-X-STREAM-INF)
 *  - EXT-X-KEY URI rewriting
 *  - EXT-X-MAP URI rewriting
 *  - EXT-X-MEDIA subtitle/audio rendition URI rewriting (EXT-X-MEDIA)
 *  - Plain comment lines preserved unchanged
 *  - Empty lines handled gracefully
 */
class HlsPlaylistRewriterTest {

    private val baseUrl = "https://cdn.example.com/live/stream.m3u8"
    private val localPrefix = "http://127.0.0.1:12345/token123/seg"

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ── Relative segment URIs ─────────────────────────────────────────────────

    @Test
    fun `relative segment URIs are resolved against base URL`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:6.0,
            seg0.ts
            #EXTINF:6.0,
            seg1.ts
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(playlist, baseUrl, localPrefix)

        val expectedSeg0 = "https://cdn.example.com/live/seg0.ts"
        val expectedSeg1 = "https://cdn.example.com/live/seg1.ts"

        assertTrue(result.contains("$localPrefix?url=${encode(expectedSeg0)}"))
        assertTrue(result.contains("$localPrefix?url=${encode(expectedSeg1)}"))
    }

    @Test
    fun `absolute segment URIs are wrapped in proxy URL`() {
        val playlist = """
            #EXTM3U
            #EXTINF:6.0,
            https://other.cdn.net/segments/0001.ts
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(playlist, baseUrl, localPrefix)

        assertTrue(result.contains("$localPrefix?url=${encode("https://other.cdn.net/segments/0001.ts")}"))
    }

    // ── Master playlist variants ───────────────────────────────────────────────

    @Test
    fun `EXT-X-STREAM-INF tag is preserved and variant URI is rewritten`() {
        val masterBase = "https://cdn.example.com/master.m3u8"
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=854x480
            480p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720
            720p/index.m3u8
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(master, masterBase, localPrefix)

        assertTrue(result.contains("#EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=854x480"))
        assertTrue(result.contains("#EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720"))
        // Variant URIs should be rewritten
        val expected480 = "https://cdn.example.com/480p/index.m3u8"
        val expected720 = "https://cdn.example.com/720p/index.m3u8"
        assertTrue("480p variant should be rewritten", result.contains(encode(expected480)))
        assertTrue("720p variant should be rewritten", result.contains(encode(expected720)))
    }

    // ── EXT-X-KEY ─────────────────────────────────────────────────────────────

    @Test
    fun `EXT-X-KEY URI is rewritten to proxy URL`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="keys/enc.key",IV=0x00000000000000000000000000000001
            #EXTINF:6.0,
            seg0.ts
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(playlist, baseUrl, localPrefix)

        val expectedKeyUrl = "https://cdn.example.com/live/keys/enc.key"
        assertTrue(
            "EXT-X-KEY URI should be rewritten",
            result.contains("URI=\"$localPrefix?url=${encode(expectedKeyUrl)}\"")
        )
        // IV must be preserved
        assertTrue(result.contains("IV=0x00000000000000000000000000000001"))
    }

    @Test
    fun `EXT-X-KEY with absolute URI is still rewritten to proxy URL`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/enc.key"
            #EXTINF:6.0,
            seg0.ts
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(playlist, baseUrl, localPrefix)

        assertTrue(result.contains("URI=\"$localPrefix?url=${encode("https://keys.example.com/enc.key")}\""))
    }

    // ── EXT-X-MAP ─────────────────────────────────────────────────────────────

    @Test
    fun `EXT-X-MAP URI is rewritten to proxy URL`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg0.m4s
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(playlist, baseUrl, localPrefix)

        val expectedMapUrl = "https://cdn.example.com/live/init.mp4"
        assertTrue(
            "EXT-X-MAP URI should be rewritten",
            result.contains("URI=\"$localPrefix?url=${encode(expectedMapUrl)}\"")
        )
    }

    // ── EXT-X-MEDIA (audio / subtitle renditions) ─────────────────────────────

    @Test
    fun `EXT-X-MEDIA URI is rewritten to proxy URL`() {
        val masterBase = "https://cdn.example.com/master.m3u8"
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",DEFAULT=YES,URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,AUDIO="aac"
            video/480p.m3u8
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(master, masterBase, localPrefix)

        val expectedAudio = "https://cdn.example.com/audio/en.m3u8"
        assertTrue(
            "EXT-X-MEDIA URI should be rewritten",
            result.contains("URI=\"$localPrefix?url=${encode(expectedAudio)}\"")
        )
    }

    // ── Comment and non-URI tags preserved ───────────────────────────────────

    @Test
    fun `plain comment lines are preserved unchanged`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:42
        """.trimIndent()

        val result = HlsParser.rewritePlaylist(playlist, baseUrl, localPrefix)

        assertTrue(result.contains("#EXTM3U"))
        assertTrue(result.contains("#EXT-X-VERSION:3"))
        assertTrue(result.contains("#EXT-X-TARGETDURATION:6"))
        assertTrue(result.contains("#EXT-X-MEDIA-SEQUENCE:42"))
    }

    @Test
    fun `empty playlist returns empty-ish output without crashing`() {
        val result = HlsParser.rewritePlaylist("", baseUrl, localPrefix)
        assertNotNull(result)
    }
}
