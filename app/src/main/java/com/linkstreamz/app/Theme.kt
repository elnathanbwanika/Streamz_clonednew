package com.linkstreamz.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

// CSS :root tokens copied from the source HTML.
val Bg = Color(0xFF000000)
val Bg2 = Color(0xFF0D0D0D)
val Card = Color(0xFF141414)
val Card2 = Color(0xFF191919)
val Line = Color(0x14FFFFFF)
val Line2 = Color(0x24FFFFFF)
val Text = Color(0xFFF5F5F5)
val Muted = Color(0xFF8A8A8A)
val Accent = Color(0xFFFF6600)
val Accent2 = Color(0xFFFF8533)
val AccentSoft = Color(0x24FF6600)
val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
val DisplayFont = androidx.compose.ui.text.font.FontFamily.SansSerif // APPROXIMATION: bundle Syncopate in res/font for exact parity.
val MonoFont = androidx.compose.ui.text.font.FontFamily.Monospace // APPROXIMATION: bundle JetBrains Mono in res/font for exact parity.

private val LinkColors = darkColorScheme(
    background = Bg,
    surface = Card,
    primary = Accent,
    onPrimary = Color.Black,
    onBackground = Text,
    onSurface = Text,
)

@Composable
fun LinkStreamzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LinkColors, typography = LinkTypography, content = content)
}

// APPROXIMATION: platform font weights remain explicit until the required TTF bundle is added.
private val LinkTypography = androidx.compose.material3.Typography(
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, color = Text),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, color = Text),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont, color = Muted),
)

fun Modifier.appBackground(): Modifier = this
    // GRADIENT: radial-gradient(120% 34% at 50% -4%, rgba(255,102,0,.18), transparent 68%), linear-gradient(180deg,#050505,#000 40%)
    .drawBehind {
        drawRect(Brush.linearGradient(listOf(Color(0xFF050505), Bg), start = Offset.Zero, end = Offset(0f, size.height * .4f)))
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x2EFF6600), Color.Transparent), radius = size.width * 1.2f),
            radius = size.width * 1.2f,
            center = Offset(size.width / 2f, -size.height * .04f),
        )
    }

@Composable
fun AppSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg).appBackground()) { content() }
}