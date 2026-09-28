package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// PlayerControls.kt
//
// Reusable composables for the video-player overlay.
// Only UI + state wiring – playback logic lives in PlayerScreen.kt.
//
// Key changes (v2):
//   • ServersMenu and SettingsMenu are now rendered inside a Popup so they
//     float above every other composable in the window, escape the player's
//     clip boundary, and can extend over the "More events" list below.
//   • MenuBg is fully opaque (#141414 / Card colour) – no alpha.
//   • A custom PopupPositionProvider anchors each menu 8 dp below its button,
//     left-aligned for Servers and right-aligned for Settings, with
//     screen-edge clamping in both portrait and landscape.
//   • The server list is wrapped in a verticalScroll so 12+ servers never
//     overflow the screen.  Height is capped at 60 % of the display height.
//   • One-menu-at-a-time and outside-tap-dismiss are enforced through the
//     existing openMenu state; the Popup's onDismissRequest also calls
//     closeMenus() so the hardware Back key works.
//   • While a menu is open, the auto-hide timer is not restarted (callers
//     should check state.openMenu != None before calling touchControls()).
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

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
// Dropdown appearance constants
// ─────────────────────────────────────────────────────────────────────────────

/** Fully opaque #141414 – matches the Card surface so there is no bleed-through. */
private val MenuBg = Color(0xFF141414)
private val MenuWidth = 190.dp
private val MenuCorner = 12.dp
private val MenuBorderColor = Color.White.copy(alpha = 0.10f)
private val HairlineDivider = Color.White.copy(alpha = 0.08f)
/** Vertical gap between the anchor button's bottom and the menu top. */
private val MenuGapDp = 8.dp

// ─────────────────────────────────────────────────────────────────────────────
// Popup position providers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Positions the popup below the anchor button, left-aligned to the anchor's
 * left edge.  If the menu would extend past the right or bottom of the screen
 * it is shifted left or flipped upward accordingly.
 */
private class LeftAlignedBelowProvider(
    private val anchorBounds: IntRect,
    private val gapPx: Int,
    private val menuWidthPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bounds = this.anchorBounds

        // Try to place below the anchor
        var x = bounds.left
        var y = bounds.bottom + gapPx

        // Clamp to right edge
        if (x + popupContentSize.width > windowSize.width) {
            x = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        }
        // Clamp to left edge
        x = x.coerceAtLeast(0)

        // If the menu would go off the bottom, flip it above the anchor
        if (y + popupContentSize.height > windowSize.height) {
            y = (bounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }

        return IntOffset(x, y)
    }
}

/**
 * Positions the popup below the anchor button, right-aligned to the anchor's
 * right edge.  Applies the same screen-edge guards.
 */
private class RightAlignedBelowProvider(
    private val anchorBounds: IntRect,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bounds = this.anchorBounds

        // Right-align: place so the menu's right edge is at the anchor's right edge
        var x = bounds.right - popupContentSize.width
        var y = bounds.bottom + gapPx

        // Clamp to left edge
        x = x.coerceAtLeast(0)
        // Clamp to right edge
        if (x + popupContentSize.width > windowSize.width) {
            x = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        }

        // Flip above if off-screen bottom
        if (y + popupContentSize.height > windowSize.height) {
            y = (bounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }

        return IntOffset(x, y)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerMenuPopup  – base popup shell
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders [content] inside a [Popup] window layer that escapes the player
 * Box's clip and sits above every other composable.
 *
 * @param visible         Drives the animated show/hide.
 * @param positionProvider Positions the popup window on screen.
 * @param onDismiss       Called when the user taps outside or presses Back.
 * @param maxHeightFraction Maximum fraction of screen height for the content (default 0.60).
 */
@Composable
private fun PlayerMenuPopup(
    visible: Boolean,
    positionProvider: PopupPositionProvider,
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.60f,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return   // Don't compose the Popup at all when hidden (avoids stale offsets)

    val screenHeightPx = LocalView.current.height
    val density = LocalDensity.current
    val maxHeightDp = with(density) { (screenHeightPx * maxHeightFraction).toDp() }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,          // capture Back key presses
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,   // allow popup to escape screen boundaries
        ),
    ) {
        AnimatedVisibility(
            visible = true,            // always true inside the popup; outer `if (!visible)` guards
            enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { -it / 3 },
            exit  = fadeOut(tween(110)) + slideOutVertically(tween(110)) { -it / 3 },
        ) {
            Column(
                modifier = Modifier
                    .width(MenuWidth)
                    .heightIn(max = maxHeightDp)
                    .background(MenuBg, RoundedCornerShape(MenuCorner))
                    .border(0.5.dp, MenuBorderColor, RoundedCornerShape(MenuCorner))
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Servers dropdown  (top-left)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Popup dropdown that lists available servers.
 * Rendered outside the player Box so it can extend past the player boundary.
 * The active server has an orange dot indicator.
 *
 * @param anchorBoundsInWindow The [IntRect] of the anchor button in window coordinates,
 *   obtained from [Modifier.onGloballyPositioned].
 */
@Composable
fun ServersMenuPopup(
    visible: Boolean,
    servers: List<StreamServer>,
    selectedIndex: Int,
    anchorBoundsInWindow: IntRect,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { MenuGapDp.roundToPx() }
    val menuWidthPx = with(density) { MenuWidth.roundToPx() }

    val positionProvider = remember(anchorBoundsInWindow, gapPx, menuWidthPx) {
        LeftAlignedBelowProvider(anchorBoundsInWindow, gapPx, menuWidthPx)
    }

    PlayerMenuPopup(
        visible = visible,
        positionProvider = positionProvider,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings dropdown  (top-right)  — volume slider + fullscreen toggle
// ─────────────────────────────────────────────────────────────────────────────

/** Single row inside a settings popup with an optional leading icon and a label. */
@Composable
fun PlayerMenuRow(
    icon: ImageVector,
    iconDescription: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: @Composable RowScope.() -> Unit,
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

/**
 * Popup dropdown for settings: volume slider and fullscreen toggle.
 * Rendered outside the player Box, right-aligned below the gear button.
 */
@Composable
fun SettingsMenuPopup(
    visible: Boolean,
    volume: Float,
    isFullscreen: Boolean,
    anchorBoundsInWindow: IntRect,
    onVolumeChange: (Float) -> Unit,
    onFullscreenToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { MenuGapDp.roundToPx() }

    val positionProvider = remember(anchorBoundsInWindow, gapPx) {
        RightAlignedBelowProvider(anchorBoundsInWindow, gapPx)
    }

    PlayerMenuPopup(
        visible = visible,
        positionProvider = positionProvider,
        onDismiss = onDismiss,
    ) {
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
 * - Top-left  → Servers button + [ServersMenuPopup]
 * - Top-right → Settings button + [SettingsMenuPopup]
 * - Center    → [PlayerCenterControls]
 * - Bottom    → [PlayerSeekBar]
 *
 * The two dropdown menus are rendered in their own Popup windows, which means
 * they escape the player Box's clip boundary and render above everything else
 * on screen (including the "More events" list below the player).
 *
 * Visibility is driven by [visible].  The overlay stays visible while a menu
 * is open or the user is dragging the seek bar; callers should honour
 * [onInteraction] to reset their own auto-hide timer, but must NOT restart
 * the timer while a menu is open (PlayerScreen already handles this via the
 * forceVisible / openMenu check).
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

    // Track pixel bounds of each anchor button inside the window for popup positioning
    var serverBtnBounds by remember { mutableStateOf(IntRect.Zero) }
    var settingsBtnBounds by remember { mutableStateOf(IntRect.Zero) }
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(220)),
        modifier = modifier.fillMaxSize(),
    ) {
        // Intercept taps on the translucent scrim to close open menus.
        // Use pointerInput-level consumption so the tap that dismisses a menu
        // does NOT fall through to the play/pause button beneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (state.openMenu != OpenMenu.None) {
                        state.closeMenus()
                        // Do NOT call onInteraction here – closing a menu should
                        // not restart the hide timer; that restarts after the next
                        // real user interaction.
                    } else {
                        onInteraction()
                    }
                },
        ) {

            // ── Top-left: Server button ───────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 10.dp, start = 10.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        val sizePx = coords.size
                        serverBtnBounds = IntRect(
                            left   = pos.x.toInt(),
                            top    = pos.y.toInt(),
                            right  = (pos.x + sizePx.width).toInt(),
                            bottom = (pos.y + sizePx.height).toInt(),
                        )
                    },
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
            }

            // ── Top-right: Settings button ────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        val sizePx = coords.size
                        settingsBtnBounds = IntRect(
                            left   = pos.x.toInt(),
                            top    = pos.y.toInt(),
                            right  = (pos.x + sizePx.width).toInt(),
                            bottom = (pos.y + sizePx.height).toInt(),
                        )
                    },
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
            }

            // ── Center: Rewind / Play-Pause / Forward ─────────────────────
            PlayerCenterControls(
                isPlaying = state.isPlaying,
                onRewind = { onRewind(); onInteraction() },
                onPlayPause = { onPlayPause(); onInteraction() },
                onForward = { onForward(); onInteraction() },
                modifier = Modifier.align(Alignment.Center),
            )

            // ── Bottom: Seek bar ──────────────────────────────────────────
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

    // ── Popup menus (rendered OUTSIDE the overlay Box, in a new window layer) ──

    // Servers popup – below the top-left button, left-aligned
    if (serverBtnBounds != IntRect.Zero) {
        ServersMenuPopup(
            visible = state.openMenu == OpenMenu.Servers,
            servers = state.servers,
            selectedIndex = state.selectedServerIndex,
            anchorBoundsInWindow = serverBtnBounds,
            onSelect = { idx ->
                onServerSelect(idx)
                state.closeMenus()
                onInteraction()
            },
            onDismiss = { state.closeMenus() },
        )
    }

    // Settings popup – below the top-right button, right-aligned
    if (settingsBtnBounds != IntRect.Zero) {
        SettingsMenuPopup(
            visible = state.openMenu == OpenMenu.Settings,
            volume = state.volume,
            isFullscreen = state.isFullscreen,
            anchorBoundsInWindow = settingsBtnBounds,
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
            onDismiss = { state.closeMenus() },
        )
    }
}
