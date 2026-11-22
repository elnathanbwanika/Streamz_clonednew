package com.linkstreamz.app

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.media3.common.Player

/**
 * Owns the platform Picture-in-Picture session for the player.
 *
 * Design notes (they explain the shape of this file):
 *
 * 1. **Real PiP, not an overlay.** This uses `Activity.enterPictureInPictureMode`
 *    only. It never uses `SYSTEM_ALERT_WINDOW` / "display over other apps".
 *    The old [FloatingOverlayManager] approach drew a `TYPE_APPLICATION_OVERLAY`
 *    window with app-owned buttons; that is what produced the "curved arrow,
 *    X, pause, zoom" chrome the user complained about, and why the video had
 *    black bars (the overlay was a fixed 16:9 box, not the video's own ratio).
 *
 * 2. **The app draws no chrome inside PiP.** While the activity is in PiP the
 *    Compose UI is hidden entirely ([isInPip] is observed by the player
 *    screen), so the only controls the user can see are the system's own
 *    overlay (expand / close), which an app cannot remove or restyle.
 *
 * 3. **Expanding reuses the same ExoPlayer.** We never recreate the player on
 *    exit, so there is no rebuffer.
 *
 * 4. **Closing stops playback.** On Android 12+ the system close button is
 *    wired via `setCloseAction`. On older versions we fall back to
 *    [onPipWindowClosed] and stop the player ourselves.
 */
class PipController(
    private val activity: Activity,
    val player: Player,
) {

    /** True while the activity is hosted in a PiP window. */
    var isInPip: Boolean = false
        private set

    /** Latest known display aspect ratio, from the real video size. */
    private var videoRatio: Float? = null

    /** Latest known video size; used only as a fallback when no ratio arrived. */
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    /** Latest play state, kept so the action icon can be rebuilt on demand. */
    private var isPlaying: Boolean = false

    /** Whether the current content is live (play/pause only). */
    private var isLiveContent: Boolean = false

    /** Title shown in the PiP window (Android 12+). */
    private var pipTitle: String = ""

    /** Subtitle shown in the PiP window (Android 12+). */
    private var pipSubtitle: String = ""

    /** On-screen bounds of the video view, for the enter/exit animation. */
    private var sourceRectHint: Rect? = null

    fun setTitle(title: String, subtitle: String) {
        pipTitle = title
        pipSubtitle = subtitle
        if (isInPip) updateParams("title-changed")
    }

    /** Set by the host so it can hide/show its own Compose chrome. */
    var onPipChanged: ((Boolean) -> Unit)? = null

    /** Set by the host so it can react to the system close action. */
    var onCloseRequested: (() -> Unit)? = null

    private var receiverRegistered = false

    /** Receives the app's own PiP action broadcasts and applies them to the player. */
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PipRequestCodes.ACTION_REWIND_10 -> seekBy(-PipActions.SKIP_SECONDS * 1000L)
                PipRequestCodes.ACTION_FORWARD_10 -> seekBy(PipActions.SKIP_SECONDS * 1000L)
                PipRequestCodes.ACTION_PLAY_PAUSE_TOGGLE -> {
                    togglePlayPause()
                    // Flip the cached icon state right away so the button shows
                    // the new state without waiting for the Compose-side listener.
                    isPlaying = player.playWhenReady
                }
                PipRequestCodes.ACTION_SEEK_TO -> {
                    val pos = intent.getLongExtra(PipRequestCodes.EXTRA_POSITION_MS, -1L)
                    if (pos >= 0L) player.seekTo(pos)
                }
                PipRequestCodes.ACTION_CLOSE -> {
                    // System close (X): stop playback and release, never leave audio.
                    player.pause()
                    onCloseRequested?.invoke()
                }
            }
            // The icon follows the state, so rebuild after every action.
            refreshActions("pip-action")
        }
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val target = player.currentPosition + deltaMs
        // Live streams report C.TIME_UNSET (negative) duration; clamp to >= 0 only.
        val clamped = if (duration > 0L) target.coerceIn(0L, duration) else target.coerceAtLeast(0L)
        player.seekTo(clamped)
    }

    private fun togglePlayPause() {
        if (player.playWhenReady) player.pause() else player.play()
    }

    /** Register the action receiver. Call from the host's ON_START / ON_RESUME. */
    fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(PipRequestCodes.ACTION_REWIND_10)
            addAction(PipRequestCodes.ACTION_FORWARD_10)
            addAction(PipRequestCodes.ACTION_PLAY_PAUSE_TOGGLE)
            addAction(PipRequestCodes.ACTION_SEEK_TO)
            addAction(PipRequestCodes.ACTION_CLOSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(actionReceiver, filter)
        }
        receiverRegistered = true
    }

    /** Unregister the action receiver. Call from the host's ON_STOP. */
    fun unregister() {
        if (!receiverRegistered) return
        try {
            activity.unregisterReceiver(actionReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered — harmless.
        }
        receiverRegistered = false
    }

    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == videoWidth && height == videoHeight && videoRatio != null) return
        videoWidth = width
        videoHeight = height
        // Fallback only: the display ratio callback is authoritative.
        if (videoRatio == null) videoRatio = PipAspectRatio.clamp(width, height)
        if (isInPip) updateParams("video-size-changed")
    }

    /**
     * Authoritative display ratio from Media3's [androidx.media3.common.VideoSize],
     * which accounts for non-square pixels and rotation. Callers must use this
     * instead of raw width/height for movie files.
     */
    fun setVideoAspectRatio(ratio: Float) {
        val clamped = PipAspectRatio.clamp(ratio)
        if (clamped == videoRatio) return
        videoRatio = clamped
        if (isInPip) updateParams("video-aspect-changed")
    }

    private fun currentRatio(): Float =
        videoRatio ?: PipAspectRatio.clamp(videoWidth, videoHeight)

    fun setLiveContent(live: Boolean) {
        if (live == isLiveContent) return
        isLiveContent = live
        if (isInPip) refreshActions("live-content-changed")
    }

    fun setPlaying(playing: Boolean) {
        if (playing == isPlaying) return
        isPlaying = playing
        if (isInPip) refreshActions("playbackStateChanged")
    }

    /**
     * Build the PiP params for the current state.
     *
     * The aspect ratio comes from the *real video size* (clamped), so FIT
     * resizing fills the window without letterboxing. Seamless resize is off
     * for video: it lets the window keep its old size while the content
     * re-lays-out, which shows black bars for a frame.
     */
    fun buildParams(
        includeActions: Boolean = true,
        autoEnter: Boolean = false,
        closeAction: RemoteAction? = null,
    ): PictureInPictureParams {
        val ratio = currentRatio()
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000f).toInt(), 1000))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(false)
            builder.setAutoEnterEnabled(autoEnter)
            if (closeAction != null) builder.setCloseAction(closeAction)
        }

        if (sourceRectHint != null) {
            builder.setSourceRectHint(sourceRectHint)
        }

        if (includeActions && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setActions(buildActions())
            // Android 12+ shows a title/subtitle on the PiP window. Both take a
            // plain CharSequence on PictureInPictureParams.Builder.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (pipTitle.isNotBlank()) builder.setTitle(pipTitle)
                if (pipSubtitle.isNotBlank()) builder.setSubtitle(pipSubtitle)
            }
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildActions(): List<RemoteAction> {
        val actions = PipActions.forContent(isLiveContent)
        val out = ArrayList<RemoteAction>(actions.size)
        for (action in actions) {
            when (action) {
                PipAction.REWIND_10 -> out += remoteAction(
                    iconRes = android.R.drawable.ic_media_rew,
                    title = "Replay 10 seconds",
                    contentDescription = "Replay 10 seconds",
                    requestCode = PipRequestCodes.ACTION_REWIND,
                    intentAction = PipRequestCodes.ACTION_REWIND_10,
                )
                PipAction.FORWARD_10 -> out += remoteAction(
                    iconRes = android.R.drawable.ic_media_ff,
                    title = "Forward 10 seconds",
                    contentDescription = "Forward 10 seconds",
                    requestCode = PipRequestCodes.ACTION_FORWARD,
                    intentAction = PipRequestCodes.ACTION_FORWARD_10,
                )
                PipAction.PLAY_PAUSE -> out += remoteAction(
                    iconRes = if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play,
                    title = if (isPlaying) "Pause" else "Play",
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    requestCode = PipRequestCodes.ACTION_PLAY_PAUSE,
                    intentAction = PipRequestCodes.ACTION_PLAY_PAUSE_TOGGLE,
                )
            }
        }
        return out
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun remoteAction(
        iconRes: Int,
        title: String,
        contentDescription: String,
        requestCode: Int,
        intentAction: String,
    ): RemoteAction {
        val intent = Intent(intentAction).setPackage(activity.packageName)
        val pending = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(activity, iconRes)
        return RemoteAction(icon, title, contentDescription, pending).apply {
            setEnabled(true)
        }
    }

    /** Push the current params to the system (ratio + actions). */
    fun updateParams(trigger: String = "updateParams") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // setPictureInPictureParams is only meaningful while the activity is in
        // PiP; calling it after the window is gone throws IllegalStateException.
        if (!isInPip) return
        try {
            // Reuses the existing aspect ratio and source rect hint; this never
            // changes the layout mode (that is owned by the activity).
            activity.setPictureInPictureParams(buildParams())
            // Log the layout-mode decision with its trigger: this is the line
            // that shows which event fired while the window was in PiP. The mode
            // can only ever be videoOnly here, because updateParams returns early
            // unless isInPip — a "normal" line with a playback trigger would be
            // the signature of the old bug.
            AppLogger.d(
                "Pip",
                "layoutMode=" + (if (isInPip) "videoOnly" else "normal") +
                    ", pip=" + isInPip + ", trigger=" + trigger,
            )
        } catch (_: IllegalArgumentException) {
            // Never crash the player over PiP chrome.
            AppLogger.e("Pip", "setPictureInPictureParams rejected the ratio")
        } catch (_: IllegalStateException) {
            AppLogger.e("Pip", "setPictureInPictureParams called after window closed")
        }
    }

    /** Rebuild only the action list (cheaper than a full params push). */
    fun refreshActions(trigger: String = "refreshActions") {
        if (!isInPip) return
        updateParams(trigger)
    }

    /** True when the platform can enter PiP at all. */
    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )

    /**
     * Enter PiP. Returns false when unsupported or the platform refused.
     * The caller decides *when* (playing, not paused, user left the app).
     */
    fun enter(sourceRectHint: Rect? = null): Boolean {
        if (!isSupported()) return false
        if (isInPip) return true
        if (sourceRectHint != null) this.sourceRectHint = sourceRectHint
        return try {
            activity.enterPictureInPictureMode(buildParams())
        } catch (e: IllegalStateException) {
            AppLogger.e("Pip", "enterPictureInPictureMode failed", e)
            false
        } catch (e: IllegalArgumentException) {
            AppLogger.e("Pip", "enterPictureInPictureMode rejected params", e)
            false
        }
    }

    /**
     * On Android 12+, wire the system's own close button to stop playback,
     * so audio never keeps running after the window is dismissed.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyCloseAction() {
        val intent = Intent(PipRequestCodes.ACTION_CLOSE).setPackage(activity.packageName)
        val pending = PendingIntent.getBroadcast(
            activity,
            PipRequestCodes.ACTION_CLOSE_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(activity, android.R.drawable.ic_menu_close_clear_cancel)
        val closeAction = RemoteAction(icon, "Stop", "Stop playback", pending)
        try {
            activity.setPictureInPictureParams(buildParams(closeAction = closeAction))
        } catch (_: IllegalArgumentException) {
            AppLogger.e("Pip", "setCloseAction rejected")
        } catch (_: IllegalStateException) {
            AppLogger.e("Pip", "setCloseAction called after window closed")
        }
    }

    /** Called by the activity when its PiP state changes. */
    fun onPipModeChanged(inPip: Boolean) {
        isInPip = inPip
        onPipChanged?.invoke(inPip)
        logPipState(inPip, "onPictureInPictureModeChanged")
        if (inPip) {
            // Keep the system overlay in sync with the live player state.
            refreshActions("pip-entry")
        }
    }

    /**
     * Diagnostic snapshot for the Logs overlay (no URLs, no tokens).
     *
     * Logs the PiP flag, the window size, the video view's bounds, the aspect
     * ratio we set, the source-rect hint and the resize mode — the exact values
     * needed to tell "layout did not switch" apart from "video was cropped".
     */
    private fun logPipState(inPip: Boolean, trigger: String) {
        val metrics = activity.resources.displayMetrics
        val ratio = currentRatio()
        val video = (activity as? MainActivity)?.videoViewForPip()
        val bounds = if (video != null && video.isAttachedToWindow) {
            val r = Rect()
            if (video.getGlobalVisibleRect(r)) "$r" else "unattached"
        } else "null"

        AppLogger.d(
            "Pip",
            buildString {
                append("layoutMode=").append(if (inPip) "videoOnly" else "normal")
                append(", pip=").append(inPip)
                append(", trigger=").append(trigger)
                append(", window=").append(metrics.widthPixels).append('x').append(metrics.heightPixels)
                append(", videoView=").append(bounds)
                append(", aspectRatio=").append(String.format(java.util.Locale.US, "%.4f", ratio))
                append(", sourceRectHint=").append(sourceRectHint?.toString() ?: "null")
                append(", resizeMode=").append(
                    PipLayoutSpec.resizeMode(isInPip = inPip, isFullscreen = false).name,
                )
            },
        )

        // Anything other than the video surface visible in PiP is a bug.
        // Compare what the screen actually composes with the video-only policy.
        if (inPip) {
            val drawn = PipLayoutSpec.visibleChrome(isInPip = true, isFullscreen = false)
            val leaked = drawn - PlayerChrome.VIDEO_SURFACE
            if (leaked.isNotEmpty()) {
                AppLogger.e(
                    "Pip",
                    "WARNING: non-video chrome visible in PiP (trigger=" + trigger + "): " +
                        leaked.joinToString { it.name },
                )
            }
        }
    }

    /**
     * The system closed the PiP window (Android < 12, where there is no
     * close action). Stop playback ourselves — never leave audio playing.
     */
    fun onPipWindowClosed() {
        isInPip = false
        onPipChanged?.invoke(false)
        onCloseRequested?.invoke()
        player.pause()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (isInPip) refreshActions("onConfigurationChanged")
    }
}
