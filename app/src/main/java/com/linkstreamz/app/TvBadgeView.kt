package com.linkstreamz.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

private const val GRID_WIDTH = 340f

private val PlateColor = Color(0xFF120F0C)
private val OrangeColor = Color(0xFFFF6A00)
private val OrangeLight = Color(0xFFFF8A2A)
private val CreamColor = Color(0xFFF6EFE6)

@Composable
fun TvBadgeOverlay(placement: TvBadgePlacement?, modifier: Modifier = Modifier) {
    if (placement == null) return
    BoxWithConstraints(modifier) {
        val frameWidth = constraints.maxWidth.toFloat()
        val frameHeight = constraints.maxHeight.toFloat()
        if (frameWidth <= 0f || frameHeight <= 0f) return@BoxWithConstraints

        val rect = placement.resolve(frameWidth, frameHeight)
        if (rect.width <= 0f || rect.height <= 0f) return@BoxWithConstraints

        val density = LocalDensity.current
        TvBadgeArtwork(
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() },
                )
        )
    }
}

@Composable
private fun TvBadgeArtwork(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        if (widthPx <= 0f) return@BoxWithConstraints

        val scale = widthPx / GRID_WIDTH
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()

        val linkStreamz = remember(scale, density, measurer) {
            measurer.measure(
                text = AnnotatedString("LINK STREAMZ"),
                style = TextStyle(
                    color = CreamColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { (26f * scale).toSp() },
                    letterSpacing = with(density) { (0.5f * scale).toSp() },
                ),
            )
        }
        val tv = remember(scale, density, measurer) {
            measurer.measure(
                text = AnnotatedString("TV"),
                style = TextStyle(
                    color = PlateColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { (15f * scale).toSp() },
                ),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / GRID_WIDTH

            drawRoundRect(
                color = PlateColor,
                topLeft = Offset(3f * s, 3f * s),
                size = Size(334f * s, 90f * s),
                cornerRadius = CornerRadius(10f * s, 10f * s),
            )
            drawRoundRect(
                color = LinkColors.LineAlpha,
                topLeft = Offset(3f * s, 3f * s),
                size = Size(334f * s, 90f * s),
                cornerRadius = CornerRadius(10f * s, 10f * s),
                style = Stroke(width = 4f * s),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(OrangeLight, OrangeColor),
                    startY = 17f * s,
                    endY = 79f * s,
                ),
                topLeft = Offset(17f * s, 17f * s),
                size = Size(62f * s, 62f * s),
                cornerRadius = CornerRadius(8f * s, 8f * s),
            )

            val play = Path().apply {
                moveTo(40f * s, 34f * s)
                lineTo(63f * s, 48f * s)
                lineTo(40f * s, 62f * s)
                close()
            }
            drawPath(play, PlateColor)
            drawPath(play, PlateColor, style = Stroke(width = 3f * s, join = StrokeJoin.Round))

            drawText(
                textLayoutResult = linkStreamz,
                topLeft = Offset(90f * s, 46f * s - linkStreamz.firstBaseline),
            )

            drawRoundRect(
                color = OrangeColor,
                topLeft = Offset(90f * s, 56f * s),
                size = Size(48f * s, 24f * s),
                cornerRadius = CornerRadius(5f * s, 5f * s),
            )
            drawText(
                textLayoutResult = tv,
                topLeft = Offset(
                    x = 114f * s - tv.size.width / 2f,
                    y = 73.5f * s - tv.firstBaseline,
                ),
            )
        }
    }
}
