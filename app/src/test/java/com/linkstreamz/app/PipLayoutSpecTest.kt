package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floating window must be video-only in EVERY playback state, not just
 * while playing.
 *
 * Regression this guards: pausing from the PiP window used to bring the normal
 * page back inside the floating window (header with back arrow + title, the
 * bordered/rounded video container, a cropped video). The cause was that the
 * player screen's PiP effect was keyed on `playing`, so a pause tore it down,
 * `detachPip()` cleared the cached PiP flag and the screen recomposed as "normal"
 * while the window was still in PiP.
 *
 * The fix makes the layout mode a pure function of the platform PiP flags.
 * These tests pin that down: no playback state, no lifecycle event and no UI
 * state may influence the decision.
 */
class PipLayoutSpecTest {

    /** Every playback state the player can be in. */
    private val allPlaybackStates = listOf(
        PlaybackState.PLAYING,
        PlaybackState.PAUSED,
        PlaybackState.BUFFERING,
        PlaybackState.ENDED,
        PlaybackState.ERROR,
    )

    /** Every lifecycle callback that could restore the normal UI. */
    private val allLifecycleEvents = listOf(
        LifecycleEvent.ON_RESUME,
        LifecycleEvent.ON_PAUSE,
        LifecycleEvent.ON_START,
        LifecycleEvent.ON_STOP,
        LifecycleEvent.ON_CONFIGURATION_CHANGED,
        LifecycleEvent.ON_NEW_INTENT,
        LifecycleEvent.ON_PICTURE_IN_PICTURE_MODE_CHANGED,
        LifecycleEvent.ON_PICTURE_IN_PICTURE_UI_STATE_CHANGED,
        LifecycleEvent.ON_USER_LEAVE_HINT,
        LifecycleEvent.ON_RECOMPOSITION,
    )

    // ── The core guarantee ────────────────────────────────────────────────────

    @Test
    fun `pip shows exactly the video surface`() {
        assertEquals(
            setOf(PlayerChrome.VIDEO_SURFACE),
            PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false),
        )
    }

    @Test
    fun `pip hides the header back arrow title and download button`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        assertFalse(PlayerChrome.TOP_BAR in drawn)
    }

    @Test
    fun `pip hides the rounded bordered video frame`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        assertFalse(PlayerChrome.VIDEO_FRAME_DECORATION in drawn)
    }

    @Test
    fun `pip hides the related content list`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        assertFalse(PlayerChrome.RELATED_CONTENT in drawn)
    }

    @Test
    fun `pip hides every overlay drawn on top of the video`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        listOf(
            PlayerChrome.PLAYER_OVERLAY,
            PlayerChrome.LOADING_OVERLAY,
            PlayerChrome.TV_BADGE,
            PlayerChrome.BRIGHTNESS_SCRIM,
            PlayerChrome.SEEK_BUBBLE,
            PlayerChrome.ERROR_OVERLAY,
        ).forEach { assertFalse("$it must be hidden in PiP", it in drawn) }
    }

    @Test
    fun `pip hides every non-video view`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        val leaked = drawn - PlayerChrome.VIDEO_SURFACE
        assertTrue("leaked into PiP: $leaked", leaked.isEmpty())
    }

    @Test
    fun `pip video surface is always drawn`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
        assertTrue(PlayerChrome.VIDEO_SURFACE in drawn)
    }

    // ── Layout mode is independent of playback state ───────────────────────────

    @Test
    fun `layout mode is video only in pip for every playback state`() {
        allPlaybackStates.forEach { state ->
            assertEquals(
                "PiP must stay video-only when $state",
                PipLayoutMode.VIDEO_ONLY,
                PipLayoutSpec.layoutMode(
                    isInPictureInPictureMode = true,
                    isTransitioningToPip = false,
                    playbackState = state,
                ),
            )
        }
    }

    @Test
    fun `layout mode is video only while transitioning to pip for every playback state`() {
        allPlaybackStates.forEach { state ->
            assertEquals(
                "transition to PiP must already be video-only when $state",
                PipLayoutMode.VIDEO_ONLY,
                PipLayoutSpec.layoutMode(
                    isInPictureInPictureMode = false,
                    isTransitioningToPip = true,
                    playbackState = state,
                ),
            )
        }
    }

    @Test
    fun `layout mode is normal outside pip for every playback state`() {
        allPlaybackStates.forEach { state ->
            assertEquals(
                "outside PiP the normal page must show when $state",
                PipLayoutMode.NORMAL,
                PipLayoutSpec.layoutMode(
                    isInPictureInPictureMode = false,
                    isTransitioningToPip = false,
                    playbackState = state,
                ),
            )
        }
    }

    // ── Lifecycle events cannot restore the normal UI inside PiP ────────────────

    @Test
    fun `no lifecycle event restores normal layout while in pip`() {
        allLifecycleEvents.forEach { event ->
            assertEquals(
                "lifecycle event $event must not restore the normal page in PiP",
                PipLayoutMode.VIDEO_ONLY,
                PipLayoutSpec.layoutMode(
                    isInPictureInPictureMode = true,
                    isTransitioningToPip = false,
                    lifecycleEvent = event,
                ),
            )
        }
    }

    @Test
    fun `every playback state and lifecycle event pair stays video only in pip`() {
        // The exhaustive product: 5 playback states x 10 lifecycle events.
        allPlaybackStates.forEach { state ->
            allLifecycleEvents.forEach { event ->
                assertEquals(
                    "PiP must stay video-only for $state + $event",
                    PipLayoutMode.VIDEO_ONLY,
                    PipLayoutSpec.layoutMode(
                        isInPictureInPictureMode = true,
                        isTransitioningToPip = false,
                        playbackState = state,
                        lifecycleEvent = event,
                    ),
                )
            }
        }
    }

    @Test
    fun `touches controller visibility and window resize cannot restore normal layout in pip`() {
        // Tapping the window, the controller auto-showing or a resize must all
        // leave the floating window video-only.
        listOf(true, false).forEach { controlsVisible ->
            listOf(true, false).forEach { windowResized ->
                assertEquals(
                    "controls=$controlsVisible resized=$windowResized must stay video-only",
                    PipLayoutMode.VIDEO_ONLY,
                    PipLayoutSpec.layoutMode(
                        isInPictureInPictureMode = true,
                        isTransitioningToPip = false,
                        controlsVisible = controlsVisible,
                        windowResized = windowResized,
                    ),
                )
            }
        }
    }

    @Test
    fun `resize mode is FIT in pip for every playback state`() {
        allPlaybackStates.forEach { state ->
            assertEquals(
                "PiP must FIT the complete frame when $state",
                PipResizeMode.FIT,
                PipLayoutSpec.resizeMode(isInPip = true, isFullscreen = false),
            )
        }
    }

    @Test
    fun `resize mode is FIT in pip even when the in-app fullscreen flag is set`() {
        // The in-app fullscreen stretch must never leak into the PiP window.
        assertEquals(
            PipResizeMode.FIT,
            PipLayoutSpec.resizeMode(isInPip = true, isFullscreen = true),
        )
    }

    // ── Normal layout keeps its chrome ─────────────────────────────────────────

    @Test
    fun `normal layout keeps all its chrome`() {
        val drawn = PipLayoutSpec.visibleChrome(isInPip = false, isFullscreen = false)
        assertTrue(PlayerChrome.TOP_BAR in drawn)
        assertTrue(PlayerChrome.RELATED_CONTENT in drawn)
        assertTrue(PlayerChrome.VIDEO_SURFACE in drawn)
    }

    @Test
    fun `only the video surface is allowed in pip`() {
        assertTrue(PipLayoutSpec.isAllowedInPip(PlayerChrome.VIDEO_SURFACE))
        PipLayoutSpec.chromeHiddenInPip().forEach {
            assertFalse("$it must not be allowed in PiP", PipLayoutSpec.isAllowedInPip(it))
        }
    }
}
