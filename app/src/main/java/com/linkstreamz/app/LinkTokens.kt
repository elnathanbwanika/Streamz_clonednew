package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// LinkTokens.kt
//
// The single visual-polish token layer for Link Streamz.
//
// PURELY PRESENTATIONAL. Every value here is a Color, Dp, TextUnit, Shape, or a
// @Composable style helper. Nothing in this file:
//   - reads or writes app state
//   - touches ViewModels, repositories, networking, ExoPlayer, VPN, downloads
//   - changes navigation, click handlers, view ids, labels, or data models
//
// Screens opt in by importing these tokens. The pre-existing palette
// (LinkColors / LinkDimens / DisplayFont / BodyFont / MonoFont) is left
// untouched so any screen not yet restyled keeps compiling and behaving
// exactly as before.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Colour tokens
// ─────────────────────────────────────────────────────────────────────────────

object LsColor {
    /** Page background. */
    val Bg = Color(0xFF000000)

    /** Chrome, pill containers, sheets. */
    val Surface = Color(0xFF0D0D0D)

    /** Cards and tiles. */
    val Card = Color(0xFF141414)

    /** Brand orange. */
    val Accent = Color(0xFFFF6600)

    /** Deep orange wash for icon squares and accent fills. */
    val AccentDim = Color(0xFF2A1608)

    /** Warm base for the limited-time hero card. */
    val HeroBase = Color(0xFF170B04)

    /** Primary text. */
    val Text = Color(0xFFF5F5F5)

    /** Secondary / supporting text. */
    val TextDim = Color(0xFF9A9A9A)

    /** Text and glyphs that sit on an orange fill. */
    val OnAccent = Color(0xFF000000)

    /** Secondary button fill - white at 14 %. */
    val FillSubtle = Color(0x24FFFFFF)

    /** Pressed-state overlay. */
    val Pressed = Color(0x1FFFFFFF)

    /** Dialog scrim - 60 % black. */
    val Scrim = Color(0x99000000)

    /** Error text. */
    val Danger = Color(0xFFFF8A80)
}

// ─────────────────────────────────────────────────────────────────────────────
// Spacing tokens
// ─────────────────────────────────────────────────────────────────────────────

object LsSpace {
    /** Horizontal screen padding. */
    val Screen: Dp = 20.dp

    /** Gap between siblings in a list or row. */
    val Item: Dp = 12.dp

    /** Gap between distinct sections. */
    val Section: Dp = 24.dp

    /** Minimum touch target. */
    val Touch: Dp = 48.dp

    /** Round icon-only button diameter. */
    val IconButton: Dp = 44.dp

    /** Primary button height. */
    val Button: Dp = 48.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Shape tokens (no outlines or strokes anywhere - tone separation only)
// ─────────────────────────────────────────────────────────────────────────────

object LsShape {
    val Chip: RoundedCornerShape = RoundedCornerShape(percent = 50)
    val Button: RoundedCornerShape = RoundedCornerShape(16.dp)
    val Card: RoundedCornerShape = RoundedCornerShape(14.dp)
    val Hero: RoundedCornerShape = RoundedCornerShape(28.dp)
    val Poster: RoundedCornerShape = RoundedCornerShape(12.dp)
    val Dialog: RoundedCornerShape = RoundedCornerShape(28.dp)
    val Sheet: RoundedCornerShape = RoundedCornerShape(28.dp)
    val Pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
}

// ─────────────────────────────────────────────────────────────────────
// Type tokens
//
// Syncopate Bold - section headings + the wordmark (uppercase, 0.14 em)
// JetBrains Mono - data and labels (countdowns, times, badges, league names)
// Inter          - body copy and buttons
// ─────────────────────────────────────────────────────────────────────

object LsType {
    /** Letter-spacing used by section headings and the wordmark: 0.14 em. */
    const val HEADING_TRACKING_EM = 0.14f

    /** Letter-spacing used by small uppercase badges: 0.10 em. */
    const val BADGE_TRACKING_EM = 0.10f
}

/** 12 sp Syncopate Bold, uppercase, 0.14 em tracking. Section headings + wordmark. */
@Composable
@ReadOnlyComposable
fun lsHeadingStyle(color: Color = LsColor.Text): TextStyle = TextStyle(
    fontFamily = DisplayFont,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    letterSpacing = (12 * LsType.HEADING_TRACKING_EM).sp,
    color = color,
)

/** JetBrains Mono at [size] sp with [trackingEm] em tracking. Data and labels. */
@Composable
@ReadOnlyComposable
fun lsMonoStyle(
    size: TextUnit = 11.sp,
    color: Color = LsColor.TextDim,
    weight: FontWeight = FontWeight.Normal,
    trackingEm: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = MonoFont,
    fontWeight = weight,
    fontSize = size,
    letterSpacing = (size.value * trackingEm).sp,
    color = color,
)

/** Inter body text. */
@Composable
@ReadOnlyComposable
fun lsBodyStyle(
    size: TextUnit = 14.sp,
    color: Color = LsColor.Text,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = (size.value * 1.45f).sp,
): TextStyle = TextStyle(
    fontFamily = BodyFont,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    color = color,
)

/** Inter button label. */
@Composable
@ReadOnlyComposable
fun lsButtonStyle(color: Color = LsColor.OnAccent): TextStyle = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    color = color,
)

// ─────────────────────────────────────────
// Small text helpers - they only apply a style, never change the string
// ─────────────────────────────────────────

/** Section heading: Syncopate Bold, uppercase, 0.14 em. */
@Composable
fun LsSectionHeading(text: String, modifier: Modifier = Modifier, color: Color = LsColor.Text) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = lsHeadingStyle(color),
        maxLines = 1,
    )
}

/** Small uppercase monospace badge, e.g. MOVIE / LIMITED / time left. */
@Composable
fun LsBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LsColor.TextDim,
    size: TextUnit = 9.sp,
    background: Color? = null,
) {
    val base = lsMonoStyle(size = size, color = color, weight = FontWeight.Bold, trackingEm = LsType.BADGE_TRACKING_EM)
    Text(
        text = text.uppercase(),
        modifier = if (background != null) {
            modifier
                .background(background, LsShape.Pill)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        } else {
            modifier
        },
        style = base,
        maxLines = 1,
    )
}

/** Monospace metadata line: genre / year, counts, durations. */
@Composable
fun LsMeta(text: String, modifier: Modifier = Modifier, color: Color = LsColor.TextDim, size: TextUnit = 10.sp) {
    Text(
        text = text,
        modifier = modifier,
        style = lsMonoStyle(size = size, color = color),
        maxLines = 1,
    )
}

/** Dialog body copy. */
@Composable
fun LsDialogBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = lsBodyStyle(size = 14.sp, color = LsColor.TextDim),
        textAlign = TextAlign.Start,
    )
}
