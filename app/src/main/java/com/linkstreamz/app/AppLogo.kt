package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Link Streamz brand mark, defined once so the welcome screen and every
 * Android icon surface stay in sync.
 *
 * Geometry (in a 48x48 design grid): a soft orange rounded square, an "L"
 * drawn as two strokes with rounded caps, and a single dot above the corner.
 * The same paths are mirrored in res/drawable/ic_app_logo.xml for the launcher
 * and notification icons — keep both in step.
 */
object AppLogo {
    const val DESIGN_SIZE = 48f
    val TileCornerFraction = 0.26f
    val Accent: Color get() = LsColor.Accent
    val Ink: Color get() = LsColor.OnAccent

    /** Draws only the glyph (L + dot) into a 48x48 coordinate space. */
    fun drawGlyph(scope: DrawScope, tint: Color) {
        val stem = Path().apply {
            moveTo(16f, 13f)
            lineTo(16f, 33f)
            lineTo(32f, 33f)
        }
        scope.drawPath(
            path = stem,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 5.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        scope.drawCircle(center = Offset(32f, 16f), radius = 3.5f, color = tint)
    }
}

/** The orange tile with the L glyph. Use this wherever the app logo is needed in Compose. */
@Composable
fun AppLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    showTile: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showTile) {
                    Modifier
                        .clip(RoundedCornerShape(AppLogo.TileCornerFraction * 100))
                        .background(AppLogo.Accent)
                } else Modifier
            )
            .drawBehind {
                val scale = size.minDimension / AppLogo.DESIGN_SIZE
                val dx = (size.width - AppLogo.DESIGN_SIZE * scale) / 2f
                val dy = (size.height - AppLogo.DESIGN_SIZE * scale) / 2f
                val tinted = if (showTile) AppLogo.Ink else AppLogo.Accent

                val stem = Path().apply {
                    moveTo(dx + 16f * scale, dy + 13f * scale)
                    lineTo(dx + 16f * scale, dy + 33f * scale)
                    lineTo(dx + 32f * scale, dy + 33f * scale)
                }
                drawPath(
                    path = stem,
                    color = tinted,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 5.5f * scale,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
                drawCircle(
                    center = Offset(dx + 32f * scale, dy + 16f * scale),
                    radius = 3.5f * scale,
                    color = tinted,
                )
            },
    )
}
