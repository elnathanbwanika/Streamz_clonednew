package com.linkstreamz.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.syncopate_bold, androidx.compose.ui.text.font.FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.syncopate_regular, androidx.compose.ui.text.font.FontWeight.Normal)
)
val BodyFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.inter_regular, androidx.compose.ui.text.font.FontWeight.Normal)
)
val MonoFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.jetbrains_mono, androidx.compose.ui.text.font.FontWeight.Normal)
)

private val LinkColorScheme = darkColorScheme(
    background = LinkColors.ScreenBg,
    surface = LinkColors.CardBg,
    primary = LinkColors.OrangePrimary,
    onPrimary = Color.Black,
    onBackground = LinkColors.PrimaryText,
    onSurface = LinkColors.PrimaryText,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LinkStreamzTheme(content: @Composable () -> Unit) {
    val rippleConfig = RippleConfiguration(color = LinkColors.OrangePrimary)
    val overscrollConfig = OverscrollConfiguration(glowColor = LinkColors.OrangePrimary.copy(alpha = 0.3f))
    
    MaterialTheme(colorScheme = LinkColorScheme, typography = LinkTypography) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides rippleConfig,
            LocalOverscrollConfiguration provides overscrollConfig,
            content = content
        )
    }
}

private val LinkTypography = androidx.compose.material3.Typography(
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = BodyFont, color = LinkColors.PrimaryText),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = BodyFont, color = LinkColors.PrimaryText),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont, color = LinkColors.MutedText),
)

fun Modifier.appBackground(): Modifier = this
    .background(LinkColors.ScreenBg)
    .drawBehind {
        val r = size.width * 1.5f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to LinkColors.OrangePrimary.copy(alpha = 0.16f),
                    0.70f to Color.Transparent,
                ),
                center = Offset(size.width / 2f, 0f),
                radius = r,
            ),
            radius = r,
            center = Offset(size.width / 2f, 0f),
        )
    }

@Composable
fun AppSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().appBackground()) { content() }
}