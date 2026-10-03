package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen.kt
//
// All Settings UI for Link Streamz.
//
// Public composables (reusable building blocks):
//   SettingsScreen            – top-level entry; wires ViewModel → stateless content
//   SettingsScreenContent     – stateless, accepts all callbacks; hoists no state
//   SettingsSectionCard       – collapsible card: label, summary, animated expand
//   SettingRow                – icon-tile + title/subtitle + optional trailing slot
//   BrandToggle               – custom 50×30 dp animated pill toggle
//   BrandSegmentedControl     – full-width 4-segment orange control
//   BrandUrlField             – branded URL text input with focus glow
//   BrandPrimaryButton        – filled gradient "Play now" button
//   BrandOutlinedButton       – transparent orange-bordered button
//
// Color tokens  → LinkColors  (object, no hard-coded hex anywhere else)
// Dimension tokens → LinkDimens (object, no hard-coded dp anywhere else)
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Color tokens ──────────────────────────────────────────────────────────────

object LinkColors {
    // Brand orange
    val OrangePrimary   = Color(0xFFFF6600)
    val OrangeLight     = Color(0xFFFF8A33)   // gradient top
    val OrangeHighlight = Color(0xFFFFA35C)   // toggle-on border

    // Layout
    val ScreenBg        = Color(0xFF000000)
    val CardBg          = Color(0xFF0F0F0F)
    val LineAlpha       = Color(0xFFFF6600).copy(alpha = 0.16f)  // dividers + default border

    // Text
    val PrimaryText     = Color(0xFFF4E9DF)   // warm cream
    val MutedText       = Color(0xFF8F7F71)   // tan-brown

    // Icon tile
    val IconTileBg      = Color(0xFFFF6600).copy(alpha = 0.14f)

    // Toggle – off state
    val ToggleOffTrack  = Color(0xFF1A1108)
    val ToggleOffBorder = Color(0xFFFF6600).copy(alpha = 0.35f)
    val ToggleOffKnob   = Color(0xFF7A5A40)

    // Toggle – on state
    val ToggleOnKnob    = Color(0xFFFFF6EE)   // bright cream; track = OrangePrimary

    // Segmented control
    val SegmentBg       = Color(0xFF080808)
    val SegmentBorder   = Color(0xFFFF6600).copy(alpha = 0.40f)
    val SegmentDivider  = Color(0xFFFF6600).copy(alpha = 0.25f)
    val SegmentUnsel    = Color(0xFFC9A98F)   // unselected label

    // Input
    val InputBg         = Color(0xFF000000)
    val InputBorder     = Color(0xFFFF6600).copy(alpha = 0.40f)
    val InputPlaceholder = Color(0xFF5E4C3E)

    // Footer
    val FooterText      = Color(0xFF5E4C3E)

    // Player Overlay
    val OverlayBtnBg    = Color(0xFF0A0806)
    val MenuBg          = Color(0xFF141414)
}

// ── Dimension tokens ──────────────────────────────────────────────────────────

object LinkDimens {
    val ScreenPadH    = 12.dp
    val HeaderRadius  = 14.dp   // gear tile corner radius
    val CardRadius    = 18.dp   // section card corner radius
    val IconTileSize  = 34.dp
    val IconTileR     = 11.dp   // icon tile corner radius
    val InputRadius   = 14.dp
    val BtnRadius     = 14.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-level entry – wires ViewModel; keeps composables stateless below
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRefresh: suspend () -> Unit,
    onCustomStream: (String, String) -> Unit,
) {
    SettingsScreenContent(
        uiState              = uiState,
        onToggleAutoplay     = viewModel::setAutoplay,
        onToggleStartMuted   = viewModel::setStartMuted,
        onSetSkipLength      = viewModel::setSkipLength,
        onSetHideControls    = viewModel::setHideControlsSeconds,
        onToggleKeepScreenOn = viewModel::setKeepScreenOn,
        onSetStreamDelay     = viewModel::setStreamDelaySeconds,
        onRefresh            = onRefresh,
        onReset              = viewModel::resetSettings,
        onCustomStream       = onCustomStream,
        onCustomUrlChange    = viewModel::setCustomUrl,
        onCustomAudioUrlChange = viewModel::setCustomAudioUrl,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen content – stateless; all state is hoisted above this composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param initialExpandedSection Used by previews to control which section starts open.
 *   In production it defaults to "PLAYBACK" per spec.
 */
@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    onToggleAutoplay: (Boolean) -> Unit,
    onToggleStartMuted: (Boolean) -> Unit,
    onSetSkipLength: (Int) -> Unit,
    onSetHideControls: (Int) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSetStreamDelay: (Int) -> Unit,
    onRefresh: suspend () -> Unit,
    onReset: () -> Unit,
    onCustomStream: (String, String) -> Unit,
    onCustomUrlChange: (String) -> Unit,
    onCustomAudioUrlChange: (String) -> Unit,
    initialExpandedSection: String? = "PLAYBACK",
) {
    var expandedSection by remember { mutableStateOf(initialExpandedSection) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LinkColors.ScreenBg)
            .drawBehind {
                // Soft radial orange glow anchored at the very top of the screen.
                // Orange at ~13 % alpha fading to transparent over ~70 % of the radius.
                val r = size.width * 1.5f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to LinkColors.OrangePrimary.copy(alpha = 0.13f),
                            0.70f to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(size.width / 2f, 0f),
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            contentPadding        = PaddingValues(
                horizontal = LinkDimens.ScreenPadH,
                vertical   = 12.dp,
            ),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
        ) {

            // ── Header ───────────────────────────────────────────────────────
            item {
                SettingsHeader()
                Spacer(Modifier.height(14.dp))
            }

            // ── PLAYBACK ─────────────────────────────────────────────────────
            item {
                val skipOptions  = listOf(5, 10, 15, 30)
                val autoTxt      = if (uiState.autoplay) "on" else "off"
                val muteTxt      = if (uiState.startMuted) "Muted" else "Sound on"
                val playSummary  = "Autoplay $autoTxt · $muteTxt · Skip ${uiState.skipLength}s"

                SettingsSectionCard(
                    label          = "PLAYBACK",
                    summary        = playSummary,
                    isExpanded     = expandedSection == "PLAYBACK",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "PLAYBACK") null else "PLAYBACK"
                    },
                ) {
                    SettingRow(
                        icon     = Icons.Default.PlayArrow,
                        title    = "Autoplay",
                        subtitle = "Start playing as soon as a match opens",
                    ) {
                        BrandToggle(
                            checked            = uiState.autoplay,
                            onCheckedChange    = onToggleAutoplay,
                            contentDescription = "Autoplay",
                        )
                    }
                    SettingDivider()
                    SettingRow(
                        icon     = Icons.Default.VolumeOff,
                        title    = "Start muted",
                        subtitle = "Open every stream with sound off",
                    ) {
                        BrandToggle(
                            checked            = uiState.startMuted,
                            onCheckedChange    = onToggleStartMuted,
                            contentDescription = "Start muted",
                        )
                    }
                    SettingDivider()
                    SettingRow(
                        icon     = Icons.Default.Sync,
                        title    = "Skip length",
                        subtitle = "Used by the rewind and forward buttons and double-tap",
                    )
                    BrandSegmentedControl(
                        options                  = skipOptions.map { "${it}s" },
                        selectedIndex            = skipOptions.indexOf(uiState.skipLength)
                            .coerceAtLeast(0),
                        onOptionSelected         = { onSetSkipLength(skipOptions[it]) },
                        contentDescriptionPrefix = "Skip length",
                    )
                    SettingDivider()
                    SettingRow(
                        icon     = Icons.Default.Timer,
                        title    = "Stream delay",
                        subtitle = "Stay behind live for smoother playback",
                    )
                    val delayOptions = listOf(0, 10, 20, 30)
                    val delayLabels = listOf("Off", "10s", "20s", "30s")
                    BrandSegmentedControl(
                        options                  = delayLabels,
                        selectedIndex            = delayOptions.indexOf(uiState.streamDelaySeconds)
                            .coerceAtLeast(0),
                        onOptionSelected         = { onSetStreamDelay(delayOptions[it]) },
                        contentDescriptionPrefix = "Stream delay",
                    )
                }
            }

            // ── DISPLAY ──────────────────────────────────────────────────────
            item {
                val hideOptions    = listOf(3, 5, 8, 12)
                val screenSleepTxt = if (uiState.keepScreenOn) "Screen stays on"
                                     else "Screen can sleep"
                val dispSummary    = "Hide after ${uiState.hideControlsSeconds}s · $screenSleepTxt"

                SettingsSectionCard(
                    label          = "DISPLAY",
                    summary        = dispSummary,
                    isExpanded     = expandedSection == "DISPLAY",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "DISPLAY") null else "DISPLAY"
                    },
                ) {
                    SettingRow(
                        icon     = Icons.Default.Tv,
                        title    = "Hide controls",
                        subtitle = "Overlay fades out after this idle time",
                    )
                    BrandSegmentedControl(
                        options                  = hideOptions.map { "${it}s" },
                        selectedIndex            = hideOptions.indexOf(uiState.hideControlsSeconds)
                            .coerceAtLeast(0),
                        onOptionSelected         = { onSetHideControls(hideOptions[it]) },
                        contentDescriptionPrefix = "Hide controls after",
                    )
                    SettingDivider()
                    SettingRow(
                        icon     = Icons.Default.Smartphone,
                        title    = "Keep screen on",
                        subtitle = "Stop the display sleeping while playing",
                    ) {
                        BrandToggle(
                            checked            = uiState.keepScreenOn,
                            onCheckedChange    = onToggleKeepScreenOn,
                            contentDescription = "Keep screen on",
                        )
                    }
                }
            }

            // ── CUSTOM STREAM ─────────────────────────────────────────────────
            item {
                val focusRequester = remember { FocusRequester() }
                val audioFocusRequester = remember { FocusRequester() }

                SettingsSectionCard(
                    label          = "CUSTOM STREAM",
                    summary        = "Play your own HLS or MP4 link",
                    isExpanded     = expandedSection == "CUSTOM STREAM",
                    onToggleExpand = {
                        expandedSection =
                            if (expandedSection == "CUSTOM STREAM") null else "CUSTOM STREAM"
                    },
                ) {
                    SettingRow(
                        icon     = Icons.Default.Link,
                        title    = "Play your own link",
                        subtitle = "Paste an HLS/MP4 URL. Optionally add an audio URL for split streams. " +
                                   "Plays instantly and saves locally.",
                    )
                    Spacer(Modifier.height(10.dp))
                    BrandUrlField(
                        value          = uiState.customUrl,
                        onValueChange  = onCustomUrlChange,
                        focusRequester = focusRequester,
                        onGo           = {
                            if (uiState.customUrl.isBlank() && uiState.customAudioUrl.isBlank()) focusRequester.requestFocus()
                            else onCustomStream(uiState.customUrl.trim(), uiState.customAudioUrl.trim())
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Separate audio link (optional)",
                        color = LinkColors.MutedText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                    )
                    BrandUrlField(
                        value          = uiState.customAudioUrl,
                        onValueChange  = onCustomAudioUrlChange,
                        focusRequester = audioFocusRequester,
                        onGo           = {
                            if (uiState.customUrl.isBlank() && uiState.customAudioUrl.isBlank()) focusRequester.requestFocus()
                            else onCustomStream(uiState.customUrl.trim(), uiState.customAudioUrl.trim())
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandPrimaryButton(
                            text    = "Play now",
                            icon    = Icons.Default.PlayArrow,
                            onClick = {
                                if (uiState.customUrl.isBlank() && uiState.customAudioUrl.isBlank()) focusRequester.requestFocus()
                                else onCustomStream(uiState.customUrl.trim(), uiState.customAudioUrl.trim())
                            },
                            modifier = Modifier.weight(1f),
                        )
                        BrandOutlinedButton(
                            text    = "Clear",
                            onClick = {
                                onCustomUrlChange("")
                                onCustomAudioUrlChange("")
                            },
                        )
                    }
                }
            }

            // ── MAINTENANCE ───────────────────────────────────────────────────
            item {
                var refreshLabel by remember { mutableStateOf("Refresh") }

                SettingsSectionCard(
                    label          = "MAINTENANCE",
                    summary        = "Refresh content or reset options",
                    isExpanded     = expandedSection == "MAINTENANCE",
                    onToggleExpand = {
                        expandedSection =
                            if (expandedSection == "MAINTENANCE") null else "MAINTENANCE"
                    },
                ) {
                    SettingRow(
                        icon     = Icons.Default.Refresh,
                        title    = "Refresh content",
                        subtitle = "Fetch the latest matches and highlights",
                    ) {
                        BrandOutlinedButton(
                            text    = refreshLabel,
                            onClick = {
                                if (refreshLabel == "Refresh") {
                                    scope.launch {
                                        refreshLabel = "..."
                                        onRefresh()
                                        refreshLabel = "Updated"
                                        delay(1_200)
                                        refreshLabel = "Refresh"
                                    }
                                }
                            },
                        )
                    }
                    SettingDivider()
                    SettingRow(
                        icon     = Icons.Default.Restore,
                        title    = "Reset settings",
                        subtitle = "Restore every option to its default",
                    ) {
                        BrandOutlinedButton(
                            text    = "Reset",
                            onClick = onReset,
                        )
                    }
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    text          = "LINK STREAMZ · v${BuildConfig.VERSION_NAME}",
                    color         = LinkColors.FooterText,
                    fontFamily    = MonoFont,
                    fontSize      = 11.sp,
                    letterSpacing = 2.2.sp,           // 0.20 em × 11
                    textAlign     = TextAlign.Center,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 44 dp rounded gear tile
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = LinkColors.OrangePrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(LinkDimens.HeaderRadius),
                )
                .border(
                    width = 1.dp,
                    color = LinkColors.OrangePrimary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(LinkDimens.HeaderRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Settings,
                contentDescription = null,
                tint               = LinkColors.OrangePrimary,
                modifier           = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text          = "SETTINGS",
                color         = LinkColors.PrimaryText,
                fontFamily    = DisplayFont,
                fontSize      = 16.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 2.24.sp,              // 0.14 em × 16
            )
            Text(
                text          = "LINK STREAMZ",
                color         = LinkColors.OrangePrimary,
                fontFamily    = MonoFont,
                fontSize      = 11.sp,
                letterSpacing = 2.2.sp,               // 0.20 em × 11
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingsSectionCard – collapsible card with animated border / chevron
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsSectionCard(
    label: String,
    summary: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Animate border opacity: 16 % collapsed → 55 % expanded
    val borderColor by animateColorAsState(
        targetValue  = LinkColors.OrangePrimary.copy(alpha = if (isExpanded) 0.55f else 0.16f),
        animationSpec = tween(280),
        label        = "sectionBorder",
    )
    val chevronAngle by animateFloatAsState(
        targetValue   = if (isExpanded) 180f else 0f,
        animationSpec = tween(280),
        label         = "chevronRotation",
    )
    val chevronTint by animateColorAsState(
        targetValue   = if (isExpanded) LinkColors.OrangePrimary else LinkColors.MutedText,
        animationSpec = tween(280),
        label         = "chevronTint",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Outer glow when expanded: draw a wide semi-transparent orange stroke
            // BEFORE the background so it bleeds outside the card bounds.
            .drawBehind {
                if (isExpanded) {
                    drawRoundRect(
                        color        = LinkColors.OrangePrimary.copy(alpha = 0.08f),
                        size         = size,
                        cornerRadius = CornerRadius(LinkDimens.CardRadius.toPx()),
                        style        = Stroke(width = 8.dp.toPx()),
                    )
                }
            }
            .background(LinkColors.CardBg, RoundedCornerShape(LinkDimens.CardRadius))
            .border(1.dp, borderColor, RoundedCornerShape(LinkDimens.CardRadius))
            .clip(RoundedCornerShape(LinkDimens.CardRadius))
            .animateContentSize(animationSpec = tween(280)),
    ) {
        // ── Card header row ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onToggleExpand,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Glowing 7 dp orange dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .drawBehind {
                        drawCircle(
                            color  = LinkColors.OrangePrimary.copy(alpha = 0.40f),
                            radius = size.minDimension * 1.8f,
                        )
                    }
                    .background(LinkColors.OrangePrimary, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text          = label,
                    color         = LinkColors.OrangePrimary,
                    fontFamily    = MonoFont,
                    fontSize      = 11.sp,
                    letterSpacing = 2.42.sp,          // 0.22 em × 11
                )
                Text(
                    text      = summary,
                    color     = LinkColors.MutedText,
                    fontSize  = 12.sp,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector        = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse $label" else "Expand $label",
                tint               = chevronTint,
                modifier           = Modifier
                    .size(18.dp)
                    .rotate(chevronAngle),
            )
        }

        // ── Expanded content ──────────────────────────────────────────────
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
            ) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingRow – icon tile + text + optional trailing composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 34 dp icon tile, 11 dp radius, orange 14 % alpha background
        Box(
            modifier = Modifier
                .size(LinkDimens.IconTileSize)
                .background(
                    color = LinkColors.IconTileBg,
                    shape = RoundedCornerShape(LinkDimens.IconTileR),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = LinkColors.OrangePrimary,
                modifier           = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                color      = LinkColors.PrimaryText,
                fontSize   = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text       = subtitle,
                color      = LinkColors.MutedText,
                fontSize   = 12.sp,
                lineHeight = 16.2.sp,              // 1.35 × 12
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingDivider – 1 dp line in line color
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LinkColors.LineAlpha),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// BrandToggle – custom animated 50 × 30 dp pill switch
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BrandToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
) {
    val trackColor  by animateColorAsState(
        targetValue   = if (checked) LinkColors.OrangePrimary else LinkColors.ToggleOffTrack,
        animationSpec = tween(280),
        label         = "toggleTrack",
    )
    val borderColor by animateColorAsState(
        targetValue   = if (checked) LinkColors.OrangeHighlight else LinkColors.ToggleOffBorder,
        animationSpec = tween(280),
        label         = "toggleBorder",
    )
    val knobColor   by animateColorAsState(
        targetValue   = if (checked) LinkColors.ToggleOnKnob else LinkColors.ToggleOffKnob,
        animationSpec = tween(280),
        label         = "toggleKnob",
    )
    // 0 dp (off) → 20 dp (on)
    val knobOffset  by animateDpAsState(
        targetValue   = if (checked) 20.dp else 0.dp,
        animationSpec = tween(280),
        label         = "toggleKnobOffset",
    )

    Box(
        modifier = Modifier
            .semantics {
                this.contentDescription = contentDescription
                this.role               = Role.Switch
                this.stateDescription   = if (checked) "On" else "Off"
            }
            .minimumInteractiveComponentSize()        // ≥ 48 × 48 dp touch target
            .wrapContentSize(Alignment.Center)
            .size(width = 50.dp, height = 30.dp)
            // Orange glow ring when on – drawn BEFORE background so it bleeds outward
            .drawBehind {
                if (checked) {
                    drawRoundRect(
                        color        = LinkColors.OrangePrimary.copy(alpha = 0.22f),
                        size         = size,
                        cornerRadius = CornerRadius(15.dp.toPx()),
                        style        = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            .background(trackColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = { onCheckedChange(!checked) },
            ),
    ) {
        // 21 dp knob with 3 dp inset; shadow when on
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(21.dp)
                .offset(x = knobOffset)
                .let { if (checked) it.shadow(2.dp, CircleShape) else it }
                .background(knobColor, CircleShape),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BrandSegmentedControl – full-width 4-segment control
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BrandSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    contentDescriptionPrefix: String,
) {
    // The Row itself has NO padding – padding lives inside each segment Box.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LinkColors.SegmentBg, RoundedCornerShape(14.dp))
            .border(1.dp, LinkColors.SegmentBorder, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        this.contentDescription = "$contentDescriptionPrefix $option"
                        this.role               = Role.RadioButton
                        this.stateDescription   = if (isSelected) "Selected" else "Not selected"
                    }
                    .minimumInteractiveComponentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = { onOptionSelected(index) },
                    )
                    .background(
                        color = if (isSelected) LinkColors.OrangePrimary else Color.Transparent,
                    )
                    .padding(vertical = 11.dp),      // ← internal to each segment
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = null,
                            tint               = Color.Black,
                            modifier           = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text       = option,
                        color      = if (isSelected) Color.Black else LinkColors.SegmentUnsel,
                        fontFamily = MonoFont,
                        fontSize   = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            // 1 dp vertical divider between segments
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(LinkColors.SegmentDivider),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BrandUrlField – branded URL text input with animated focus glow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BrandUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onGo: () -> Unit,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (textFieldValue.text != value) {
        textFieldValue = textFieldValue.copy(text = value)
    }

    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue   = if (isFocused) LinkColors.OrangePrimary else LinkColors.InputBorder,
        animationSpec = tween(200),
        label         = "urlFieldBorder",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Focus glow ring – drawn before background so it bleeds outward
            .drawBehind {
                if (isFocused) {
                    drawRoundRect(
                        color        = LinkColors.OrangePrimary.copy(alpha = 0.18f),
                        size         = size,
                        cornerRadius = CornerRadius(LinkDimens.InputRadius.toPx()),
                        style        = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            .background(LinkColors.InputBg, RoundedCornerShape(LinkDimens.InputRadius))
            .border(1.dp, borderColor, RoundedCornerShape(LinkDimens.InputRadius)),
    ) {
        BasicTextField(
            value           = textFieldValue,
            onValueChange   = {
                textFieldValue = it
                onValueChange(it.text)
            },
            textStyle       = TextStyle(
                color      = LinkColors.PrimaryText,
                fontSize   = 13.sp,
                fontFamily = MonoFont,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction    = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            singleLine      = true,
            modifier        = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox   = { innerTextField ->
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text       = "https://your-server/stream.m3u8",
                        color      = LinkColors.InputPlaceholder,
                        fontSize   = 13.sp,
                        fontFamily = MonoFont,
                    )
                }
                innerTextField()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BrandPrimaryButton – filled gradient "Play now" style button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BrandPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Box(
        modifier = modifier
            // Orange tinted elevation shadow
            .shadow(
                elevation    = 6.dp,
                shape        = RoundedCornerShape(LinkDimens.BtnRadius),
                ambientColor = LinkColors.OrangePrimary.copy(alpha = 0.40f),
                spotColor    = LinkColors.OrangePrimary.copy(alpha = 0.60f),
            )
            // Vertical gradient: OrangeLight (#FF8A33) → OrangePrimary (#FF6600)
            .background(
                brush = Brush.verticalGradient(
                    listOf(LinkColors.OrangeLight, LinkColors.OrangePrimary),
                ),
                shape = RoundedCornerShape(LinkDimens.BtnRadius),
            )
            .clip(RoundedCornerShape(LinkDimens.BtnRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.Black,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = text,
                color      = Color.Black,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BrandOutlinedButton – transparent fill, orange border (Refresh / Reset)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BrandOutlinedButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 84.dp)
            .border(
                width = 1.dp,
                color = LinkColors.OrangePrimary.copy(alpha = 0.60f),
                shape = RoundedCornerShape(LinkDimens.BtnRadius),
            )
            .clip(RoundedCornerShape(LinkDimens.BtnRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = text,
            color      = LinkColors.OrangePrimary,
            fontSize   = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(
    name            = "Settings – PLAYBACK expanded (default)",
    showBackground  = true,
    backgroundColor = 0xFF000000,
    widthDp         = 390,
    heightDp        = 900,
)
@androidx.compose.runtime.Composable
fun PreviewSettingsPlaybackOpen() {
    MaterialTheme {
        SettingsScreenContent(
            uiState                = SettingsUiState(
                autoplay            = true,
                startMuted          = false,
                skipLength          = 5,
                hideControlsSeconds = 5,
                keepScreenOn        = true,
            ),
            onToggleAutoplay       = {},
            onToggleStartMuted     = {},
            onSetSkipLength        = {},
            onSetHideControls      = {},
            onToggleKeepScreenOn   = {},
            onSetStreamDelay       = {},
            onRefresh              = {},
            onReset                = {},
            onCustomStream         = { _, _ -> },
            onCustomUrlChange      = {},
            onCustomAudioUrlChange = {},
            initialExpandedSection = "PLAYBACK",
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name            = "Settings – all sections collapsed",
    showBackground  = true,
    backgroundColor = 0xFF000000,
    widthDp         = 390,
    heightDp        = 900,
)
@androidx.compose.runtime.Composable
fun PreviewSettingsAllCollapsed() {
    MaterialTheme {
        SettingsScreenContent(
            uiState                = SettingsUiState(),
            onToggleAutoplay       = {},
            onToggleStartMuted     = {},
            onSetSkipLength        = {},
            onSetHideControls      = {},
            onToggleKeepScreenOn   = {},
            onSetStreamDelay       = {},
            onRefresh              = {},
            onReset                = {},
            onCustomStream         = { _, _ -> },
            onCustomUrlChange      = {},
            onCustomAudioUrlChange = {},
            initialExpandedSection = null,            // all collapsed
        )
    }
}
