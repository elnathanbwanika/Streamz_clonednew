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
