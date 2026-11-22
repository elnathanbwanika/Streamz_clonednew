package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the framework-free PiP logic in PipSpec.kt.
 *
 * These cover the two things most likely to break silently on a real device:
 * an aspect ratio Android rejects (which throws and crashes the enter call),
 * and the wrong action set for live content (misleading skip buttons).
 */
class PipSpecTest {

    // ── Aspect ratio clamping ────────────────────────────────────────────────

    @Test
    fun sixteenByNineIsPreserved() {
        assertEquals(16f / 9f, PipAspectRatio.clamp(1920, 1080), 0.0001f)
    }

    @Test
    fun portraitNineBySixteenIsPreserved() {
        assertEquals(9f / 16f, PipAspectRatio.clamp(1080, 1920), 0.0001f)
    }

    @Test
    fun squareVideoIsPreserved() {
        assertEquals(1f, PipAspectRatio.clamp(1080, 1080), 0.0001f)
    }

    @Test
    fun anamorphicMovieUsesDisplayRatioNotCodedSize() {
        // 720x576 with SAR 1.42 displays as 16:9. Using 720/576 (1.25) would
        // make the floating window too narrow; ignoring SAR entirely and using
        // the raw 5:4 also mismatches the video surface.
        assertEquals(16f / 9f, PipAspectRatio.display(720, 576, 1.42f, 0), 0.01f)
    }

    @Test
    fun squarePixelStreamKeepsCodedRatio() {
        assertEquals(16f / 9f, PipAspectRatio.display(1920, 1080, 1f, 0), 0.0001f)
    }

    @Test
    fun rotatedAnamorphicFrameSwapsDimensions() {
        val ratio = PipAspectRatio.display(720, 576, 1.42f, 90)
        assertEquals(576f / (720f * 1.42f), ratio, 0.0001f)
    }

    @Test
    fun invalidPixelRatioFallsBackToOne() {
        assertEquals(16f / 9f, PipAspectRatio.display(1920, 1080, Float.NaN, 0), 0.0001f)
        assertEquals(16f / 9f, PipAspectRatio.display(1920, 1080, 0f, 0), 0.0001f)
    }

    @Test
    fun ultraWideIsClampedToMaximum() {
        // 3840x1080 = 3.55, wider than Android's 2.39 limit.
        assertEquals(PipAspectRatio.MAX_RATIO, PipAspectRatio.clamp(3840, 1080), 0.0001f)
    }

    @Test
    fun veryTallVideoIsClampedToMinimum() {
        // 1080x3840 = 0.28, taller than Android's 1:2.39 limit.
        assertEquals(PipAspectRatio.MIN_RATIO, PipAspectRatio.clamp(1080, 3840), 0.0001f)
    }

    @Test
    fun cinemaScopeIsAcceptedExactly() {
        assertEquals(2.39f, PipAspectRatio.clamp(2390, 1000), 0.0001f)
        assertTrue(PipAspectRatio.isSupported(2.39f))
    }

    @Test
    fun unknownSizeFallsBackToSixteenByNine() {
        assertEquals(PipAspectRatio.DEFAULT_RATIO, PipAspectRatio.clamp(0, 0), 0.0001f)
        assertEquals(PipAspectRatio.DEFAULT_RATIO, PipAspectRatio.clamp(-1, 1080), 0.0001f)
        assertEquals(PipAspectRatio.DEFAULT_RATIO, PipAspectRatio.clamp(1920, 0), 0.0001f)
    }

    @Test
    fun floatOverloadClampsAndRejectsBadValues() {
        assertEquals(PipAspectRatio.MAX_RATIO, PipAspectRatio.clamp(9f), 0.0001f)
        assertEquals(PipAspectRatio.MIN_RATIO, PipAspectRatio.clamp(0.01f), 0.0001f)
        assertEquals(PipAspectRatio.DEFAULT_RATIO, PipAspectRatio.clamp(Float.NaN), 0.0001f)
        assertEquals(
            PipAspectRatio.DEFAULT_RATIO,
            PipAspectRatio.clamp(Float.POSITIVE_INFINITY),
            0.0001f,
        )
        assertEquals(PipAspectRatio.DEFAULT_RATIO, PipAspectRatio.clamp(0f), 0.0001f)
        assertEquals(PipAspectRatio.DEFAULT_RATIO, PipAspectRatio.clamp(-2f), 0.0001f)
    }

    @Test
    fun clampedOutputIsAlwaysAcceptedByThePlatform() {
        val widths = listOf(0, 1, 16, 480, 720, 1080, 1920, 3840, 7680, 100_000)
        val heights = listOf(0, 1, 9, 480, 720, 1080, 1920, 2160, 4320, 100_000)
        for (w in widths) {
            for (h in heights) {
                val ratio = PipAspectRatio.clamp(w, h)
                assertTrue(
                    "ratio $ratio for ${w}x$h must be supported",
                    PipAspectRatio.isSupported(ratio),
                )
            }
        }
    }

    // ── Action list: movie vs live ────────────────────────────────────────────

    @Test
    fun moviesGetRewindPlayPauseAndForward() {
        assertEquals(
            listOf(PipAction.REWIND_10, PipAction.PLAY_PAUSE, PipAction.FORWARD_10),
            PipActions.forContent(isLive = false),
        )
    }

    @Test
    fun liveContentGetsPlayPauseOnly() {
        assertEquals(listOf(PipAction.PLAY_PAUSE), PipActions.forContent(isLive = true))
    }

    @Test
    fun liveActionListNeverContainsASkipButton() {
        val live = PipActions.forContent(isLive = true)
        assertFalse(live.contains(PipAction.REWIND_10))
        assertFalse(live.contains(PipAction.FORWARD_10))
        assertTrue(live.contains(PipAction.PLAY_PAUSE))
    }

    @Test
    fun skipDistanceIsTenSeconds() {
        assertEquals(10, PipActions.SKIP_SECONDS)
    }

    // ── Target classification ─────────────────────────────────────────────────

    private fun movie() = Movie(
        id = "mv-1",
        title = "Test Movie",
        genre = "Action",
        year = 2024,
        vj = null,
        translated = false,
        thumbUrl = "",
        videoUrl = "https://example.com/v.m3u8",
        duration = "1:30:00",
    )

    private fun channel() = Channel(
        id = "ch-1",
        title = "Test Channel",
        category = "News",
        country = "UG",
        initials = "TC",
        logo = "",
        color1 = "#000000",
        color2 = "#111111",
        program = "News",
        videoUrl = "https://example.com/c.m3u8",
    )

    @Test
    fun movieTargetIsTreatedAsNonLive() {
        assertFalse(PipActions.isLiveTarget(MovieTarget(movie())))
    }

    @Test
    fun channelTargetIsTreatedAsLive() {
        assertTrue(PipActions.isLiveTarget(ChannelTarget(channel())))
    }

    @Test
    fun movieTargetGetsThreeActionsAndChannelTargetGetsOne() {
        assertEquals(3, PipActions.forTarget(MovieTarget(movie())).size)
        assertEquals(1, PipActions.forTarget(ChannelTarget(channel())).size)
    }
}
