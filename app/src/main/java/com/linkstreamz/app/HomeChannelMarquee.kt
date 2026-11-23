package com.linkstreamz.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A label-free, endlessly scrolling strip of playable channel names.
 *
 * Two identical strips sit side by side; translating by exactly one strip width
 * and restarting the animation makes the loop seamless. No thumbnails, no header.
 */
object ChannelMarqueeSpec {
    /** Each channel gets roughly this much screen time, so speed stays constant as the list grows. */
    const val MS_PER_CHANNEL = 1_800
    const val MIN_LOOP_MS = 12_000
    val Height = 48.dp
    val Gap = 20.dp
}

@Composable
fun HomeChannelMarquee(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(channels) { channels.distinctBy { it.id } }
    if (items.isEmpty()) return

    var stripWidth by remember { mutableFloatStateOf(0f) }
    val loopMs = remember(items.size) {
        (items.size * ChannelMarqueeSpec.MS_PER_CHANNEL)
            .coerceAtLeast(ChannelMarqueeSpec.MIN_LOOP_MS)
    }
    val transition = rememberInfiniteTransition(label = "channel-marquee")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(loopMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "channel-marquee-progress",
    )

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = ChannelMarqueeSpec.Height)
            .clip(AppControlShape)
            .background(LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), AppControlShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .graphicsLayer { translationX = -progress * stripWidth },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarqueeStrip(
                items = items,
                onPlay = onPlay,
                onWidth = { stripWidth = it },
            )
            MarqueeStrip(items = items, onPlay = onPlay, onWidth = null)
        }
    }
}

@Composable
private fun MarqueeStrip(
    items: List<Channel>,
    onPlay: (Channel) -> Unit,
    onWidth: ((Float) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .then(
                onWidth?.let { callback ->
                    Modifier.onSizeChanged { callback(it.width.toFloat()) }
                } ?: Modifier
            )
            .padding(horizontal = ChannelMarqueeSpec.Gap / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChannelMarqueeSpec.Gap),
    ) {
        items.forEach { channel ->
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable { onPlay(channel) }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(LinkColors.OrangePrimary),
                )
                androidx.compose.material3.Text(
                    text = channel.title,
                    color = LinkColors.PrimaryText,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
