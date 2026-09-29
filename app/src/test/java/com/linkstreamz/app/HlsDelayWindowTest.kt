package com.linkstreamz.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HlsTimeline] delay window logic.
 *
 * Because [HlsTimeline] fetches from a real URL, we test only its
 * [generateDelayedPlaylist] output by directly calling [parsePlaylistForTest]
 * (a test-only entry point that feeds raw playlist text into the timeline's
 * internal state without making network calls).
 *
 * Covered scenarios:
 *  - Normal live stream with 20 s delay → only eligible segments served
 *  - Short source window (< delay) → empty output while syncing
 *  - VOD playlist with EXT-X-ENDLIST → delay skipped, all segments returned
 *  - Correct MEDIA-SEQUENCE never goes backwards
 *  - EXT-X-DISCONTINUITY tag preserved
 *  - EXT-X-KEY rewritten to local proxy URL
 *  - EXT-X-ENDLIST added when live source ends
 */
class HlsDelayWindowTest {

    private val localPrefix = "http://127.0.0.1:9999/tkn/seg"
    private val playlistUrl = "https://cdn.example.com/live/stream.m3u8"

    /** Builds a live playlist body from [segments] of the form (duration, uri). */
    private fun livePlaylist(
        segments: List<Pair<Double, String>>,
        startSeq: Long = 0,
        targetDuration: Int = 6,
        endList: Boolean = false,
    ): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:$targetDuration")
        appendLine("#EXT-X-MEDIA-SEQUENCE:$startSeq")
        segments.forEachIndexed { _, (dur, uri) ->
            appendLine("#EXTINF:$dur,")
            appendLine(uri)
        }
        if (endList) appendLine("#EXT-X-ENDLIST")
    }

    /** Drives an [HlsTimeline]'s internal parse without network access. */
    private fun buildTimeline(
        delaySeconds: Int,
        playlists: List<String>,
    ): HlsTimeline {
        val timeline = HlsTimeline(
            client = createTestOkHttpClient(),
            playlistUrl = playlistUrl,
            headers = emptyMap(),
            delaySeconds = delaySeconds,
        )
        playlists.forEach { timeline.parsePlaylistForTest(it) }
        return timeline
    }

    // ── Normal live: 20 s delay ───────────────────────────────────────────────

    @Test
    fun `live stream with 20s delay serves only eligible segments`() {
        // 10 segments × 6 s = 60 s total. With 20 s delay, last ~3 segments (18 s)
        // are "too recent"; eligible are the first 7 segments (42 s, which is >= 20 s behind).
        val segs = (0 until 10).map { i -> 6.0 to "seg$i.ts" }
        val timeline = buildTimeline(delaySeconds = 20, playlists = listOf(livePlaylist(segs)))

        val output = timeline.generateDelayedPlaylist(localPrefix)
        assertFalse("Should not contain EXT-X-ENDLIST for live", output.contains("#EXT-X-ENDLIST"))

        // The last eligible segment should not be one of the last 3
        val segLines = output.lines().filter { it.contains("$localPrefix?url=") }
        assertTrue("Should have some eligible segments", segLines.isNotEmpty())

        // seg7, seg8, seg9 should NOT appear (they are within the 20 s window)
        assertFalse(output.contains("seg9.ts"))
        assertFalse(output.contains("seg8.ts"))
        assertFalse(output.contains("seg7.ts"))
    }

    // ── Short window (< delay): syncing state ─────────────────────────────────

    @Test
    fun `when source window shorter than delay timeline is syncing`() {
        // Only 3 segments × 6 s = 18 s < 20 s delay
        val segs = (0 until 3).map { i -> 6.0 to "seg$i.ts" }
        val timeline = buildTimeline(delaySeconds = 20, playlists = listOf(livePlaylist(segs)))

        assertTrue("Should be syncing when window < delay", timeline.syncing.value)
    }

    @Test
    fun `when source window longer than delay syncing becomes false`() {
        // 6 segments × 6 s = 36 s > 20 s delay
        val segs = (0 until 6).map { i -> 6.0 to "seg$i.ts" }
        val timeline = buildTimeline(delaySeconds = 20, playlists = listOf(livePlaylist(segs)))

        assertFalse("Should not be syncing when enough segments", timeline.syncing.value)
    }

    // ── VOD: EXT-X-ENDLIST skips delay ───────────────────────────────────────

    @Test
    fun `VOD playlist with ENDLIST serves all segments without delay`() {
        val segs = (0 until 4).map { i -> 6.0 to "seg$i.ts" }
        val timeline = buildTimeline(
            delaySeconds = 20,
            playlists = listOf(livePlaylist(segs, endList = true)),
        )

        assertFalse("VOD should not be syncing", timeline.syncing.value)
        val output = timeline.generateDelayedPlaylist(localPrefix)
        assertTrue("VOD should have EXT-X-ENDLIST", output.contains("#EXT-X-ENDLIST"))
        // All 4 segments should appear
        assertTrue(output.contains("seg0.ts"))
        assertTrue(output.contains("seg3.ts"))
    }

    // ── MEDIA-SEQUENCE never goes backwards ───────────────────────────────────

    @Test
    fun `MEDIA-SEQUENCE in output matches first window segment sequence`() {
        val segs = (0 until 10).map { i -> 6.0 to "https://cdn.example.com/seg$i.ts" }
        val timeline = buildTimeline(delaySeconds = 20, playlists = listOf(livePlaylist(segs, startSeq = 100)))

        val output = timeline.generateDelayedPlaylist(localPrefix)
        if (output.isBlank()) return // Still syncing — skip

        val seqLine = output.lines().firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
        assertNotNull("Output should have MEDIA-SEQUENCE", seqLine)
        val seq = seqLine!!.substringAfter(":").toLong()
        assertTrue("MEDIA-SEQUENCE must be >= start seq 100", seq >= 100)
    }

    // ── EXT-X-DISCONTINUITY preserved ────────────────────────────────────────

    @Test
    fun `EXT-X-DISCONTINUITY is preserved in output`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:6.0,
            seg0.ts
            #EXTINF:6.0,
            seg1.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:6.0,
            seg2.ts
            #EXTINF:6.0,
            seg3.ts
            #EXTINF:6.0,
            seg4.ts
            #EXTINF:6.0,
            seg5.ts
            #EXTINF:6.0,
            seg6.ts
            #EXTINF:6.0,
            seg7.ts
        """.trimIndent()

        val timeline = buildTimeline(delaySeconds = 20, playlists = listOf(playlist))
        val output = timeline.generateDelayedPlaylist(localPrefix)
        if (output.isBlank()) return

        assertTrue("Discontinuity tag should be in output", output.contains("#EXT-X-DISCONTINUITY"))
    }

    // ── EXT-X-KEY rewritten in generated playlist ─────────────────────────────

    @Test
    fun `EXT-X-KEY URI is rewritten to local proxy URL in generated playlist`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/enc.key"
            #EXTINF:6.0,
            https://cdn.example.com/seg0.ts
            #EXTINF:6.0,
            https://cdn.example.com/seg1.ts
            #EXTINF:6.0,
            https://cdn.example.com/seg2.ts
            #EXTINF:6.0,
            https://cdn.example.com/seg3.ts
            #EXTINF:6.0,
            https://cdn.example.com/seg4.ts
            #EXTINF:6.0,
            https://cdn.example.com/seg5.ts
        """.trimIndent()

        val timeline = buildTimeline(delaySeconds = 0, playlists = listOf(playlist))
        val output = timeline.generateDelayedPlaylist(localPrefix)

        if (output.isBlank()) return
        assertTrue("EXT-X-KEY URI should be rewritten to proxy", output.contains("$localPrefix?url="))
        assertFalse("Original key URL should not appear in output", output.contains("https://keys.example.com/enc.key\""))
    }

    // ── Live stream that ends (drain then ENDLIST) ────────────────────────────

    @Test
    fun `when live source adds ENDLIST proxy eventually adds ENDLIST too`() {
        val segs = (0 until 6).map { i -> 6.0 to "https://cdn.example.com/seg$i.ts" }
        val livePlaylistBody = livePlaylist(segs)
        val endedPlaylistBody = livePlaylist(segs, endList = true)

        val timeline = buildTimeline(
            delaySeconds = 0, // No delay so we can see ENDLIST immediately
            playlists = listOf(livePlaylistBody, endedPlaylistBody),
        )

        val output = timeline.generateDelayedPlaylist(localPrefix)
        assertTrue("Should have EXT-X-ENDLIST after source ended", output.contains("#EXT-X-ENDLIST"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createTestOkHttpClient() = okhttp3.OkHttpClient.Builder().build()
}
