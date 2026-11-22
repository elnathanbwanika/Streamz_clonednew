package com.linkstreamz.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Shared inner video content: the video surface, brightness overlay, scrim, loading spinner,
 * overlay controls, seek bubble, and error overlay.
 *
 * This is rendered inside both the portrait frame Box and the fullscreen overlay Box in PlayerScreen.
 * The outer container (shape, size, border) is the caller's responsibility.
 */
@Composable
fun PlayerVideoContent(
    exoPlayer: ExoPlayer,
    isFloating: Boolean,
    onHideFloat: () -> Unit,
    playerBrightness: Float,
    controlsVisible: Boolean,
    isExtracting: Boolean,
    isBuffering: Boolean,
    isSyncing: Boolean,
    playing: Boolean,
    settings: SettingsUiState,
    seekMessage: String?,
    error: String?,
    controlsState: PlayerControlsState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onServerSelect: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onFullscreenToggle: () -> Unit,
    onFloatWindow: () -> Unit,
    onInteraction: () -> Unit,
    onHideControls: () -> Unit,
    onPlayerBrightnessChange: (Float) -> Unit,
    onRetry: () -> Unit,
    onSwitchServer: () -> Unit,
    hasMultipleServers: Boolean,
    isFullscreen: Boolean = false,
    tvBadge: TvBadgePlacement? = null,
    isInPip: Boolean = false,
    onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    onVideoAspectRatio: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Video Surface or Float Placeholder
        if (isFloating) {
            FloatingWindowPlaceholder(onTap = onHideFloat)
        } else {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = exoPlayer
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        subtitleView?.visibility = android.view.View.GONE
                        // Hand the view to the host for the PiP source-rect hint.
                        (context.findActivity() as? MainActivity)?.setPipVideoView(this)
                        // Clip to inner radius so video corners match the frame.
                        // Not in PiP: the window must be a plain, full-bleed video.
                        if (!isInPip) {
                            clipToOutline = true
                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                    val radius = LinkDimens.PlayerInnerRadius.value * view.resources.displayMetrics.density
                                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                                }
                            }
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    // PiP: the window ratio is set from the real video size, so FIT
                    // shows the complete frame with no cropping and no letterbox.
                    // FILL is only for the in-app fullscreen stretch.
                    playerView.resizeMode = when {
                        isInPip -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFullscreen -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    // Corner clipping only for the rounded portrait frame; never in PiP.
                    playerView.clipToOutline = !isFullscreen && !isInPip
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    (view.context.findActivity() as? MainActivity)?.setPipVideoView(null)
                    view.player = null
                },
            )
        }

        // 1.1. Report the real video size so the host can set the PiP aspect ratio.
        DisposableEffect(exoPlayer) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        onVideoSizeChanged(videoSize.width, videoSize.height)
                        // The coded size alone is wrong for anamorphic movie
                        // files: Media3 applies pixelWidthHeightRatio on screen,
                        // so PiP must use the same display ratio.
                        onVideoAspectRatio(
                            PipAspectRatio.display(
                                width = videoSize.width,
                                height = videoSize.height,
                                pixelRatio = videoSize.pixelWidthHeightRatio,
                                rotationDegrees = videoSize.unappliedRotationDegrees,
                            )
                        )
                    }
                }
            }
            exoPlayer.addListener(listener)
            val current = exoPlayer.videoSize
            if (current.width > 0 && current.height > 0) {
                onVideoSizeChanged(current.width, current.height)
                onVideoAspectRatio(
                    PipAspectRatio.display(
                        width = current.width,
                        height = current.height,
                        pixelRatio = current.pixelWidthHeightRatio,
                        rotationDegrees = current.unappliedRotationDegrees,
                    )
                )
            }
            onDispose { exoPlayer.removeListener(listener) }
        }

        // 1.2. Brightness overlay — hidden in PiP: the window must show only video.
        if (!isInPip) {
            when {
                playerBrightness < 100f -> {
                    val dimAlpha = 1f - (playerBrightness / 100f).coerceIn(0f, 1f)
                    if (dimAlpha > 0f)
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dimAlpha)))
                }
                playerBrightness > 100f -> {
                    val boostAlpha = ((playerBrightness - 100f) / 200f).coerceIn(0f, 1f) * 0.75f
                    if (boostAlpha > 0f)
                        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = boostAlpha)))
                }
            }
        }

        // 1.5. Scrim — hidden in PiP.
        AnimatedVisibility(
            visible = controlsVisible && !isInPip,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(600)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.62f),
                            0.28f to Color.Black.copy(alpha = 0.18f),
                            0.52f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.22f),
                            1.00f to Color.Black.copy(alpha = 0.78f),
                        )
                    )
            )
        }

        // 1.6. Channel badge, above the video and below the controls
        TvBadgeOverlay(
            placement = tvBadge,
            modifier = Modifier.matchParentSize(),
        )

        // 1.7. Loading / buffering / syncing overlay — hidden in PiP (video only).
        AnimatedVisibility(
            visible = !isInPip && (isExtracting || isBuffering || (isSyncing && !playing)),
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        color = LinkColors.OrangePrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                    )
                    val delayVal = settings.streamDelaySeconds
                    when {
                        isExtracting -> Text(
                            "Extracting YouTube stream...",
                            color = LinkColors.MutedText,
                            fontSize = 12.sp,
                            fontFamily = MonoFont,
                        )
                        isSyncing && delayVal > 0 -> Text(
                            "Syncing to stay ${delayVal}s behind live...",
                            color = LinkColors.MutedText,
                            fontSize = 12.sp,
                            fontFamily = MonoFont,
                        )
                    }
                }
            }
        }

        // 2. Overlay Controls — never drawn in PiP: the window must show only video.
        if (!isInPip) {
            PlayerOverlay(
                state = controlsState,
                visible = controlsVisible || error != null,
                onPlayPause = onPlayPause,
                onRewind = onRewind,
                onForward = onForward,
                onSeek = onSeek,
                onServerSelect = onServerSelect,
                onVolumeChange = onVolumeChange,
                onFullscreenToggle = onFullscreenToggle,
                onFloatWindow = onFloatWindow,
                onInteraction = onInteraction,
                playerBrightness = playerBrightness,
                onPlayerBrightnessChange = onPlayerBrightnessChange,
                modifier = Modifier.matchParentSize(),
                onBackgroundTap = onHideControls,
            )
        }

        // Seek-feedback bubble — never in PiP (no text over the video).
        var bubbleMessage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(seekMessage) {
            if (seekMessage != null) {
                bubbleMessage = seekMessage
                delay(450)
                bubbleMessage = null
            }
        }
        if (!isInPip) bubbleMessage?.let { message ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 15.dp, vertical = 9.dp)
            ) {
                Text(message, color = LinkColors.PrimaryText, fontFamily = MonoFont, fontSize = 12.sp)
            }
        }

        // Error overlay — never in PiP: the window shows the video only, and a
        // dead stream is handled by the activity (it exits PiP / stops playback).
        if (error != null && !isInPip) {
            Column(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = .9f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "STREAM UNAVAILABLE",
                    color = LinkColors.PrimaryText,
                    fontFamily = DisplayFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    error,
                    color = LinkColors.MutedText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(10.dp, 11.dp),
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRetry,
                        shape = LsShape.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LsColor.Accent,
                            contentColor = LsColor.OnAccent,
                        ),
                    ) { Text("Try again", fontWeight = FontWeight.Bold) }
                    if (hasMultipleServers) Button(
                        onClick = onSwitchServer,
                        shape = LsShape.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LsColor.FillSubtle,
                            contentColor = LsColor.Text,
                        ),
                    ) { Text("Switch server", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
