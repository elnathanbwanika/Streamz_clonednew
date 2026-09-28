package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// PlayerControls.kt
//
// Reusable composables for the video-player overlay.
// Only UI + state wiring – playback logic lives in PlayerScreen.kt.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// State holder
// ─────────────────────────────────────────────────────────────────────────────

/** Which dropdown (if any) is currently open. */
enum class OpenMenu { None, Servers, Settings }

/**
 * Hoisted state for the player overlay controls.
 *
 * Instantiate with [rememberPlayerControlsState] inside PlayerScreen so it
 * survives recomposition, and pass it down to [PlayerOverlay].
 */
class PlayerControlsState(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    volume: Float,
    isFullscreen: Boolean,
    servers: List<StreamServer>,
    selectedServerIndex: Int,
    openMenu: OpenMenu,
) {
    var isPlaying by mutableStateOf(isPlaying)
    var position by mutableLongStateOf(position)
    var duration by mutableLongStateOf(duration)
    var volume by mutableFloatStateOf(volume)
    var isFullscreen by mutableStateOf(isFullscreen)
    var servers by mutableStateOf(servers)
    var selectedServerIndex by mutableIntStateOf(selectedServerIndex)
    var openMenu by mutableStateOf(openMenu)

    fun toggleMenu(menu: OpenMenu) {
        openMenu = if (openMenu == menu) OpenMenu.None else menu
    }

    fun closeMenus() { openMenu = OpenMenu.None }
}

@Composable
fun rememberPlayerControlsState(
    isPlaying: Boolean = false,
    position: Long = 0L,
    duration: Long = 0L,
    volume: Float = 1f,
    isFullscreen: Boolean = false,
    servers: List<StreamServer> = emptyList(),
    selectedServerIndex: Int = 0,
): PlayerControlsState = remember {
    PlayerControlsState(isPlaying, position, duration, volume, isFullscreen, servers, selectedServerIndex, OpenMenu.None)
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerIconButton  (40 dp circular, black 55% α, white icon)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Circular overlay icon button used for the top-left and top-right anchors.
 *
 * @param tintOverride  Pass [Accent] when the button's menu is open so it turns orange.
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tintOverride: Color = Color.White,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintOverride,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dropdown container  (#141414 95% α, 12 dp corners, hairline dividers)
// ─────────────────────────────────────────────────────────────────────────────

private val MenuBg = Color(0xFF141414).copy(alpha = 0.95f)
private val MenuWidth = 190.dp
private val MenuCorner = 12.dp
private val HairlineDivider = Color.White.copy(alpha = 0.08f)

/**
 * Animated dropdown shell used by both top buttons.
 * Place inside a [Box] that is positioned under the anchor button.
 */
@Composable
fun PlayerMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { -it / 3 },
        exit = fadeOut(tween(110)) + slideOutVertically(tween(110)) { -it / 3 },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .width(MenuWidth)
                .background(MenuBg, RoundedCornerShape(MenuCorner))
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}

/** Single row inside a [PlayerMenu] with optional leading icon and a label. */
@Composable
fun PlayerMenuRow(
    icon: ImageVector,
    iconDescription: String,
    label: @Composable RowScope.() -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = Muted,
            modifier = Modifier.size(18.dp),
        )
        label()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Servers dropdown  (top-left)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dropdown that lists available servers.
 * The active server has an orange dot indicator.
 */
@Composable
fun ServersMenu(
    visible: Boolean,
    servers: List<StreamServer>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    PlayerMenu(visible = visible) {
        servers.forEachIndexed { index, server ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = HairlineDivider,
                    thickness = 0.5.dp,
                )
            }
            val isActive = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Active indicator dot
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            if (isActive) Accent else Color.Transparent,
                            CircleShape,
                        )
                        .then(
                            if (!isActive) Modifier.background(
                                Color.White.copy(alpha = 0.18f),
                                CircleShape,
                            ) else Modifier
                        ),
                )
                Text(
                    text = server.name,
                    color = if (isActive) Accent else Text,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                server.quality?.let {
                    Text(
                        text = it,
                        color = Muted,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings dropdown  (top-right)  — volume slider + fullscreen toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsMenu(
    visible: Boolean,
    volume: Float,
    isFullscreen: Boolean,
    onVolumeChange: (Float) -> Unit,
    onFullscreenToggle: () -> Unit,
) {
    PlayerMenu(visible = visible) {
        // Volume row
        PlayerMenuRow(
            icon = if (volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            iconDescription = "Volume",
        ) {
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 10.dp),
            color = HairlineDivider,
            thickness = 0.5.dp,
        )

        // Fullscreen row
        PlayerMenuRow(
            icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            iconDescription = "Fullscreen",
            onClick = onFullscreenToggle,
        ) {
            Text(
                text = "Fullscreen",
                color = Text,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerCenterControls  (rewind / play-pause / forward)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rewind 10s
        PlayerIconButton(
            icon = Icons.Default.Replay10,
            contentDescription = "Rewind 10 seconds",
            onClick = onRewind,
            size = 48.dp,
            iconSize = 26.dp,
        )

        // Play / Pause  (largest)
        PlayerIconButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            size = 64.dp,
            iconSize = 34.dp,
        )

        // Forward 10s
        PlayerIconButton(
            icon = Icons.Default.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = onForward,
            size = 48.dp,
            iconSize = 26.dp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerSeekBar  (time left | track | duration)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom seek bar with mm:ss time labels on both sides.
 *
 * @param onDragStart  Called when the user starts dragging – use to pause auto-hide.
 * @param onDragEnd    Called when the user releases the thumb.
 */
@Composable
fun PlayerSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val isLive = duration <= 0L
    val sliderValue = if (!isLive) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f
    val valueRange = 0f..if (!isLive) duration.toFloat().coerceAtLeast(1f) else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatOverlayTime(position),
            color = Color.White,
            fontFamily = MonoFont,
            fontSize = 11.sp,
        )

        Slider(
            value = sliderValue,
            onValueChange = { if (!isLive) onSeek(it) },
            onValueChangeFinished = onDragEnd,
            valueRange = valueRange,
            enabled = !isLive,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                disabledThumbColor = Color.White.copy(alpha = 0.40f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.40f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.20f),
            ),
            // Notify drag start via interactionSource observation isn't trivial here;
            // the caller can treat every onSeek call as "user is dragging."
        )

        Text(
            text = if (!isLive) formatOverlayTime(duration) else "LIVE",
            color = Color.White,
            fontFamily = MonoFont,
            fontSize = 11.sp,
        )
    }
}

// mm:ss (or h:mm:ss for long-form content)
private fun formatOverlayTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerOverlay  (the full overlay Box — wire everything together)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-size overlay that sits on top of the video surface.
 *
 * - Top-left  → Servers button + [ServersMenu]
 * - Top-right → Settings button + [SettingsMenu]
 * - Center    → [PlayerCenterControls]
 * - Bottom    → [PlayerSeekBar]
 *
 * Visibility is driven by [visible].  The overlay stays visible while a menu
 * is open or the user is dragging the seek bar; callers should honour
 * [onInteraction] to reset their own auto-hide timer.
 */
@Composable
fun PlayerOverlay(
    state: PlayerControlsState,
    visible: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onServerSelect: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onFullscreenToggle: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep overlay showing while any menu is open
    val forceVisible = state.openMenu != OpenMenu.None
    val shouldShow = visible || forceVisible

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(220)),
        modifier = modifier.fillMaxSize(),
    ) {
        // Intercept taps on the translucent scrim to close open menus
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (state.openMenu != OpenMenu.None) {
                        state.closeMenus()
                    }
                    onInteraction()
                },
        ) {

            // ── Top-left: Server button + dropdown ───────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 10.dp, start = 10.dp),
            ) {
                PlayerIconButton(
                    icon = Icons.Default.Dns,
                    contentDescription = "Choose server",
                    tintOverride = if (state.openMenu == OpenMenu.Servers) Accent else Color.White,
                    onClick = {
                        state.toggleMenu(OpenMenu.Servers)
                        onInteraction()
                    },
                )

                ServersMenu(
                    visible = state.openMenu == OpenMenu.Servers,
                    servers = state.servers,
                    selectedIndex = state.selectedServerIndex,
                    onSelect = { idx ->
                        onServerSelect(idx)
                        state.closeMenus()
                        onInteraction()
                    },
                    modifier = Modifier.padding(top = 46.dp), // anchor just below the button
                )
            }

            // ── Top-right: Settings button + dropdown ────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp),
            ) {
                PlayerIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tintOverride = if (state.openMenu == OpenMenu.Settings) Accent else Color.White,
                    onClick = {
                        state.toggleMenu(OpenMenu.Settings)
                        onInteraction()
                    },
                )

                SettingsMenu(
                    visible = state.openMenu == OpenMenu.Settings,
                    volume = state.volume,
                    isFullscreen = state.isFullscreen,
                    onVolumeChange = { v ->
                        state.volume = v
                        onVolumeChange(v)
                        onInteraction()
                    },
                    onFullscreenToggle = {
                        state.isFullscreen = !state.isFullscreen
                        onFullscreenToggle()
                        state.closeMenus()
                        onInteraction()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 46.dp), // anchor just below the button
                )
            }

            // ── Center: Rewind / Play-Pause / Forward ────────────────────
            PlayerCenterControls(
                isPlaying = state.isPlaying,
                onRewind = { onRewind(); onInteraction() },
                onPlayPause = { onPlayPause(); onInteraction() },
                onForward = { onForward(); onInteraction() },
                modifier = Modifier.align(Alignment.Center),
            )

            // ── Bottom: Seek bar ─────────────────────────────────────────
            PlayerSeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = { v ->
                    onSeek(v)
                    onInteraction()
                },
                onDragStart = onInteraction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
