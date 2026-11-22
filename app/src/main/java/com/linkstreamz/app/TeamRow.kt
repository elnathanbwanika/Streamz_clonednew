package com.linkstreamz.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Fixed badge slot. Never weighted, never wrap-content, never shrinkable. */
val TeamBadgeSize: Dp = 36.dp

/** Fixed, non-growing status column. */
val TeamStatusWidth: Dp = 60.dp

private val TeamNameMaxSize = 14.sp
private val TeamNameMinSize = 11.sp
private val TeamNameMaxLines = 2

/**
 * The single source of truth for how a match is drawn:
 *
 *   [badge 36dp][8dp][home name, weight 1][status 60dp][away name, weight 1][8dp][badge 36dp]
 *
 * Badges sit OUTSIDE the weighted name slots, so their size can never depend on
 * the length of a team name. Names share the remaining width equally.
 */
@Composable
fun TeamRow(
    home: Team?,
    away: Team?,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: coil.ImageLoader? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(home, right = false, imageLoader = imageLoader)
        Spacer(Modifier.width(8.dp))
        AutoSizeTeamName(home?.name.orEmpty(), right = false, Modifier.weight(1f))
        StatusSlot(status)
        AutoSizeTeamName(away?.name.orEmpty(), right = true, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TeamBadge(away, right = true, imageLoader = imageLoader)
    }
}

/** A one-sided variant for cards that show only one team (channels, movies). */
@Composable
fun TeamRowSingle(
    team: Team?,
    status: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    imageLoader: coil.ImageLoader? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(team, right = false, imageLoader = imageLoader)
        Spacer(Modifier.width(8.dp))
        AutoSizeTeamName(team?.name.orEmpty(), right = false, Modifier.weight(1f))
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            StatusSlot(status)
        }
    }
}

@Composable
private fun TeamBadge(team: Team?, right: Boolean, imageLoader: coil.ImageLoader?) {
    // A fixed slot is reserved even when there is no team, so every row aligns.
    if (team != null) Crest(team, size = TeamBadgeSize, imageLoader = imageLoader)
    else Spacer(Modifier.width(TeamBadgeSize))
}

@Composable
private fun StatusSlot(content: @Composable () -> Unit) {
    Row(
        Modifier.defaultMinSize(minWidth = TeamStatusWidth),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * Autosizes from 14sp down to 11sp before wrapping, capped at 2 lines with
 * ellipsis. Uses [SubcomposeLayout] to measure the real text width at each size
 * with the exact font scale in effect, so it behaves like TextAutoSize without
 * requiring a newer Compose version.
 */
@Composable
private fun AutoSizeTeamName(text: String, right: Boolean, modifier: Modifier) {
    val align = if (right) TextAlign.End else TextAlign.Start
    SubcomposeLayout(modifier) { constraints ->
        val steps = listOf(14, 13, 12, 11)
        val maxWidth = constraints.maxWidth
        val measureConstraints = constraints.copy(minWidth = 0, maxWidth = maxWidth)
        var chosen = steps[0]
        var placeable = subcompose("name-${steps[0]}") { TeamNameText(text, chosen.sp, align) }
            .first().measure(measureConstraints)

        if (placeable.height > lineHeight(chosen) * TeamNameMaxLines && chosen > 11) {
            for (size in steps.drop(1)) {
                val candidate = subcompose("name-$size") { TeamNameText(text, size.sp, align) }
                    .first().measure(measureConstraints)
                placeable = candidate
                chosen = size
                if (placeable.height <= lineHeight(chosen) * TeamNameMaxLines) break
            }
        }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private fun lineHeight(sizeSp: Int): Int = (sizeSp * 1.3f).toInt()

@Composable
private fun TeamNameText(text: String, size: TextUnit, align: TextAlign) {
    Text(
        text = text,
        color = LinkColors.PrimaryText,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        maxLines = TeamNameMaxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

/**
 * Pure helper for tests and for callers that need a preview of the fit.
 * Returns the largest size (14..11sp) whose wrapped line count fits in 2 lines,
 * using an average glyph width. Compose does the exact measurement at runtime.
 */
fun fittedTeamNameSize(text: String, availableWidthDp: Float, fontScale: Float = 1f): Int {
    for (size in listOf(14, 13, 12, 11)) {
        val charWidth = size * 0.52f * fontScale
        val charsPerLine = max(1, (availableWidthDp / charWidth).toInt())
        val lines = max(1, (text.length + charsPerLine - 1) / charsPerLine)
        if (lines <= TeamNameMaxLines) return size
    }
    return TeamNameMinSize.value.toInt()
}
