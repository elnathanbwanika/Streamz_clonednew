package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts the PiP "video only" guarantee.
 *
 * The PiP window is the whole activity shrunk to a thumbnail, so *anything* the
 * player screen composes is visible inside it. These tests pin the layout
 * policy down so a future screen change cannot silently re-introduce the
 * header, the rounded frame or the "More movies" list into the floating window.
 */
class PipLayoutSpecTest {

    // ── Only the video surface is visible in PiP ──────────────────────────────

    @Test
    fun `pip shows exactly the video surface`() {
        assertEquals(
            setOf(PlayerChrome.VIDEO_SURFACE),
            PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false),
        )
    }

    @Test
    fun `pip shows the video surface even when the in-app fullscreen flag is set`() {
        assertEquals(
            setOf(PlayerChrome.VIDEO_SURFACE),
            PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = true),
        )
    }

    @Test
    fun `top bar with back title and download button is hidden in pip`() {
        assertFalse(
            PlayerChrome.TOP_BAR in
                PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false),
        )
    }

    @Test
    fun `rounded frame and orange border are hidden in pip`() {
        assertFalse(
            PlayerChrome.VIDEO_FRAME_DECORATION in
                PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false),
        )
    }

    @Test
    fun `related content list is hidden in pip`() {
        assertFalse(
            PlayerChrome.RELATED_CONTENT in
                PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false),
        )
    }

    @Test
    fun `app drawn controls and overlays are hidden in pip`() {
        val visible = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        listOf(
            PlayerChrome.PLAYER_OVERLAY,
            PlayerChrome.LOADING_OVERLAY,
            PlayerChrome.TV_BADGE,
            PlayerChrome.BRIGHTNESS_SCRIM,
            PlayerChrome.SEEK_BUBBLE,
            PlayerChrome.ERROR_OVERLAY,
        ).forEach { chrome ->
            assertFalse("$chrome must not be drawn in PiP", chrome in visible)
        }
    }

    @Test
    fun `every non video chrome is reported hidden in pip`() {
        val hidden = PipLayoutSpec.chromeHiddenInPip()
        assertEquals(PlayerChrome.entries.size - 1, hidden.size)
        assertFalse(PlayerChrome.VIDEO_SURFACE in hidden)
        PlayerChrome.entries
            .filter { it != PlayerChrome.VIDEO_SURFACE }
            .forEach { assertTrue("$it should be hidden in PiP", it in hidden) }
    }

    // ── Normal (non-PiP) layout keeps all its chrome ──────────────────────────

    @Test
    fun `normal portrait layout keeps the full chrome`() {
        assertEquals(
            PipLayoutSpec.ALL_CHROME,
            PipLayoutSpec.visibleChrome(isInPip = false, isFullscreen = false),
        )
    }

    @Test
    fun `normal layout keeps the top bar and related content`() {
        val visible = PipLayoutSpec.visibleChrome(isInPip = false, isFullscreen = false)
        assertTrue(PlayerChrome.TOP_BAR in visible)
        assertTrue(PlayerChrome.RELATED_CONTENT in visible)
        assertTrue(PlayerChrome.VIDEO_SURFACE in visible)
    }

    // ── Resize mode: FIT in PiP so the whole frame is visible ─────────────────

    @Test
    fun `pip uses fit so the complete video is visible`() {
        assertEquals(
            PipResizeMode.FIT,
            PipLayoutSpec.resizeMode(isInPip = true, isFullscreen = false),
        )
    }

    @Test
    fun `pip uses fit even if the fullscreen flag is set`() {
        assertEquals(
            PipResizeMode.FIT,
            PipLayoutSpec.resizeMode(isInPip = true, isFullscreen = true),
        )
    }

    @Test
    fun `in app fullscreen still uses fill`() {
        assertEquals(
            PipResizeMode.FILL,
            PipLayoutSpec.resizeMode(isInPip = false, isFullscreen = true),
        )
    }

    @Test
    fun `portrait player uses fit`() {
        assertEquals(
            PipResizeMode.FIT,
            PipLayoutSpec.resizeMode(isInPip = false, isFullscreen = false),
        )
    }

    @Test
    fun `pip never crops the video`() {
        PlayerChrome.entries.forEach { chrome ->
            assertEquals(
                "only the video surface may be visible in PiP, found $chrome",
                chrome == PlayerChrome.VIDEO_SURFACE,
                PipLayoutSpec.isAllowedInPip(chrome),
            )
        }
    }
}
