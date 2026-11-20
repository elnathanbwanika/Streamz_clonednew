package com.linkstreamz.app

/**
 * Pure, framework-free logic for the picture-in-picture player.
 *
 * Everything here is deliberately free of Android imports so it can be unit
 * tested on the JVM without Robolectric or an emulator (see PipSpecTest).
 * The Android plumbing in [PipController] only *consumes* these values.
 */

/**
 * Android's documented limits for `PictureInPictureParams.Builder.setAspectRatio`.
 *
 * - `setAspectRatio` throws `IllegalArgumentException` when the ratio is outside
 *   [1/2.39, 2.39] (i.e. wider than 2.39:1 or taller than 1:2.39).
 * - The system also enforces a minimum window size; a ratio whose resulting
 *   window would be narrower than [MIN_WIDTH_DP] dp is not useful for video.
 *
 * We clamp to the ratio range only — the system handles the physical minimum
 * size itself, and clamping to it here would distort the video.
 */
object PipAspectRatio {
    /** Widest allowed ratio (2.39:1, cinema scope). */
    const val MAX_RATIO: Float = 2.39f

    /** Tallest allowed ratio (1:2.39, e.g. a tall vertical clip). */
    const val MIN_RATIO: Float = 1f / 2.39f

    /** Fallback for 16:9 when the video size is not known yet. */
    const val DEFAULT_RATIO: Float = 16f / 9f

    /**
     * Clamp a raw width/height ratio into the range Android accepts.
     *
     * Returns [DEFAULT_RATIO] when the input is unusable (zero, negative, NaN
     * or infinite) rather than propagating a bad value into the platform
     * builder, where it would throw.
     */
    fun clamp(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return DEFAULT_RATIO
        val raw = width.toFloat() / height.toFloat()
        if (raw.isNaN() || raw.isInfinite()) return DEFAULT_RATIO
        return raw.coerceIn(MIN_RATIO, MAX_RATIO)
    }

    /** Clamp an already-computed ratio (e.g. from a Float video-size source). */
    fun clamp(ratio: Float): Float {
        if (ratio.isNaN() || ratio.isInfinite() || ratio <= 0f) return DEFAULT_RATIO
        return ratio.coerceIn(MIN_RATIO, MAX_RATIO)
    }

    /** True when Android will accept this ratio without throwing. */
    fun isSupported(ratio: Float): Boolean =
        !ratio.isNaN() && !ratio.isInfinite() && ratio >= MIN_RATIO && ratio <= MAX_RATIO
}

/** Which transport buttons the PiP window shows. */
enum class PipAction { REWIND_10, PLAY_PAUSE, FORWARD_10 }

/**
 * What the player screen is allowed to draw.
 *
 * The PiP window is the whole activity shrunk to a thumbnail, so anything the
 * screen composes is visible inside it. This enum is the single source of truth
 * for "video only" and is unit-tested ([PipLayoutSpecTest]) so a future screen
 * change cannot silently re-introduce chrome into the floating window.
 */
enum class PlayerChrome {
    /** The video surface itself — the only thing PiP may show. */
    VIDEO_SURFACE,

    /** Header bar: back arrow, title, download / downloads buttons. */
    TOP_BAR,

    /** Rounded frame, orange border and gap padding around the video. */
    VIDEO_FRAME_DECORATION,

    /** App-drawn transport controls (play, rewind, forward, seek bar). */
    PLAYER_OVERLAY,

    /** Buffering / extracting spinners and the "syncing" caption. */
    LOADING_OVERLAY,

    /** The "Link Streamz TV" badge overlay. */
    TV_BADGE,

    /** Dim/brighten scrim from the brightness gesture. */
    BRIGHTNESS_SCRIM,

    /** Seek feedback bubble text. */
    SEEK_BUBBLE,

    /** Error card with retry / switch-server buttons. */
    ERROR_OVERLAY,

    /** "More movies" / related content list below the player. */
    RELATED_CONTENT,
}

/**
 * Layout policy for the player screen.
 *
 * Kept framework-free so the PiP guarantee is a plain unit test rather than an
 * instrumented one: when the window is in PiP, [visibleChrome] must be exactly
 * the video surface.
 */
object PipLayoutSpec {

    /** Everything the normal (non-PiP) player screen draws. */
    val ALL_CHROME: Set<PlayerChrome> = PlayerChrome.entries.toSet()

    /**
     * The set of views that may be drawn for the given mode.
     *
     * @param isInPip true while the activity is hosted in a PiP window.
     * @param isFullscreen true when the in-app fullscreen overlay is showing.
     */
    fun visibleChrome(isInPip: Boolean, isFullscreen: Boolean): Set<PlayerChrome> =
        if (isInPip) {
            // Video only: full-bleed, no header, no frame, no lists, no overlays.
            setOf(PlayerChrome.VIDEO_SURFACE)
        } else {
            // Normal portrait / fullscreen: the video plus the app chrome.
            ALL_CHROME
        }

    /** True when [chrome] may be drawn in PiP. Only the video surface may. */
    fun isAllowedInPip(chrome: PlayerChrome): Boolean =
        chrome == PlayerChrome.VIDEO_SURFACE

    /**
     * Everything that must be hidden in PiP — i.e. every non-video view.
     * Used by the test to assert nothing decorative leaks into the window.
     */
    fun chromeHiddenInPip(): Set<PlayerChrome> =
        ALL_CHROME.filterNot { isAllowedInPip(it) }.toSet()

    /**
     * The resize mode to use for the video surface.
     *
     * PiP sets the window aspect ratio from the real video size, so FIT shows
     * the complete frame with no cropping and no letterboxing. FILL is only
     * wanted for the deliberate in-app fullscreen stretch.
     */
    fun resizeMode(isInPip: Boolean, isFullscreen: Boolean): PipResizeMode = when {
        isInPip -> PipResizeMode.FIT
        isFullscreen -> PipResizeMode.FILL
        else -> PipResizeMode.FIT
    }

    /**
     * Resolve the layout mode from the platform PiP state.
     *
     * This is the single decision point, and it is deliberately a pure function
     * of the PiP flags only: playback state, touches, controller visibility,
     * lifecycle events and window size are all ignored. That is what makes the
     * "paused in PiP shows the normal page" class of bug impossible to reintroduce
     * — there is no input through which playback or a lifecycle event could
     * reach the decision.
     *
     * @param isInPictureInPictureMode `Activity.isInPictureInPictureMode`
     * @param isTransitioningToPip `PictureInPictureUiState.isTransitioningToPip`
     *   (Android 13+); true means the window is about to shrink, so switch early.
     */
    @Suppress("UNUSED_PARAMETER")
    fun layoutMode(
        isInPictureInPictureMode: Boolean,
        isTransitioningToPip: Boolean,
        playbackState: PlaybackState? = null,
        lifecycleEvent: LifecycleEvent? = null,
        controlsVisible: Boolean = false,
        windowResized: Boolean = false,
    ): PipLayoutMode {
        // Every parameter after the two PiP flags is deliberately IGNORED. They
        // exist so the decision point is explicit and so the tests can pass real
        // values for them and prove they cannot influence the outcome: no
        // playback state, no lifecycle event, no touch/controller visibility and
        // no window resize may bring app chrome back into the floating window.
        if (isInPictureInPictureMode || isTransitioningToPip) {
            return PipLayoutMode.VIDEO_ONLY
        }
        return PipLayoutMode.NORMAL
    }
}

/** The two player-screen layouts. */
enum class PipLayoutMode { NORMAL, VIDEO_ONLY }

/**
 * Every playback state the player can be in.
 *
 * Exists only so the PiP layout tests can enumerate them and prove that the
 * layout mode is identical for each — playback state must never influence it.
 */
enum class PlaybackState { PLAYING, PAUSED, BUFFERING, ENDED, ERROR }

/**
 * Every lifecycle callback / state change that could otherwise restore the
 * normal page inside the floating window.
 */
enum class LifecycleEvent {
    ON_RESUME,
    ON_PAUSE,
    ON_START,
    ON_STOP,
    ON_CONFIGURATION_CHANGED,
    ON_NEW_INTENT,
    ON_PICTURE_IN_PICTURE_MODE_CHANGED,
    ON_PICTURE_IN_PICTURE_UI_STATE_CHANGED,
    ON_USER_LEAVE_HINT,
    ON_RECOMPOSITION,
}

/** Video scaling modes, mirroring Media3's `AspectRatioFrameLayout`. */
enum class PipResizeMode { FIT, FILL, ZOOM }

/**
 * Decides the PiP action set from the content type.
 *
 * Live content (events, channels) cannot meaningfully skip back or forward:
 * seeking a live stream jumps to whatever is currently being broadcast, so
 * the two skip buttons are misleading. Live therefore gets play/pause only.
 */
object PipActions {
    /** Default skip distance in seconds, used for the 10s buttons. */
    const val SKIP_SECONDS: Int = 10

    fun forContent(isLive: Boolean): List<PipAction> =
        if (isLive) {
            listOf(PipAction.PLAY_PAUSE)
        } else {
            listOf(PipAction.REWIND_10, PipAction.PLAY_PAUSE, PipAction.FORWARD_10)
        }

    /** Map a [PlayerTarget] to live/non-live for action purposes. */
    fun isLiveTarget(target: PlayerTarget): Boolean = when (target) {
        is EventTarget, is ChannelTarget, is HighlightTarget -> true
        is MovieTarget, is EpisodeTarget, is DownloadedTarget, is CustomTarget -> false
    }

    fun forTarget(target: PlayerTarget): List<PipAction> = forContent(isLiveTarget(target))
}

/**
 * Stable request codes for the PiP action PendingIntents.
 *
 * These must never change between builds: Android reuses a PendingIntent when
 * the request code, intent and flags match, so a shifting code would silently
 * deliver a tap to the wrong action.
 */
object PipRequestCodes {
    const val ACTION_REWIND = 7_100
    const val ACTION_PLAY_PAUSE = 7_101
    const val ACTION_FORWARD = 7_102
    const val ACTION_SEEK = 7_103
    const val ACTION_CLOSE_REQUEST = 7_104

    /** Intent action strings handled by [PipActionReceiver]. */
    const val ACTION_REWIND_10 = "com.linkstreamz.app.pip.REWIND_10"
    const val ACTION_PLAY_PAUSE_TOGGLE = "com.linkstreamz.app.pip.PLAY_PAUSE"
    const val ACTION_FORWARD_10 = "com.linkstreamz.app.pip.FORWARD_10"
    const val ACTION_SEEK_TO = "com.linkstreamz.app.pip.SEEK_TO"

    /** Fired by the system's own close (X) button on Android 12+. */
    const val ACTION_CLOSE = "com.linkstreamz.app.pip.CLOSE"

    /** Extra key carrying the seek target position in milliseconds. */
    const val EXTRA_POSITION_MS = "com.linkstreamz.app.pip.EXTRA_POSITION_MS"
}
