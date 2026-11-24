package com.linkstreamz.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

// ─────────────────────────────────────────────────────────────────────────────
// Compact match cards.
//
// Every card is three stacked lines:
//   1. SPORT | LEAGUE                       (small, uppercase)
//   2. [crest] HOME   centre status   AWAY [crest]
//   3. footer: live minute / date + countdown / duration
//
// Only layout, spacing and typography are restyled here. All colours come from
// LinkColors / LsColor and all fonts from MonoFont / DisplayFont / BodyFont —
// no new palette values are introduced.
// ─────────────────────────────────────────────────────────────────────────────

private val MatchCardRadius = 14.dp
private val MatchCardPadding = 12.dp
private val MatchLeagueSize = 11.5.sp
private val MatchFooterSize = 11.sp

@Composable
fun EventCard(
    event: Event,
    nowMs: Long? = null,
    onClick: () -> Unit,
) {
    // Self-ticking only while this card is composed (on screen). The delay is
    // adaptive: minute boundaries normally, 1s checks only close to a change.
    val lifecycle = LocalLifecycleOwner.current
    val visible by lifecycle.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val isVisible = visible == Lifecycle.State.STARTED || visible == Lifecycle.State.RESUMED
    val tick by produceState(
        initialValue = nowMs ?: ServerClock.now(),
        event.id,
        event.kickoffMs,
        event.addedFirst,
        event.addedSecond,
        isVisible,
    ) {
        if (nowMs != null || !isVisible) return@produceState
        while (true) {
            val current = ServerClock.now()
            value = current
            delay(MatchClock.nextTickMs(MatchClock.kickoffOf(event), event.addedFirst, event.addedSecond, current))
        }
    }
    val effectiveNow = nowMs ?: tick
    val status = MatchClock.status(event, effectiveNow)

    CardFrame(onClick = onClick) {
        CardLeague(
            icon = { SportIcon(event.sport, status) },
            label = listOf(event.sport, event.league).filter { it.isNotBlank() }.joinToString(" | "),
        )
        TeamRow(
            home = event.home,
            away = event.away,
            status = { MatchCenter(event, status, effectiveNow) },
        )
        CardFooter(
            content = {
                when (status) {
                    "live" -> {
                        val label = MatchClock.eventLabel(event, effectiveNow) ?: "LIVE"
                        if (label == "HT" || label.equals("Half time", ignoreCase = true)) {
                            Text(
                                "Half time",
                                color = LinkColors.OrangePrimary.copy(alpha = 0.7f),
                                fontFamily = MonoFont,
                                fontSize = MatchFooterSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        } else {
                            Text(
                                label,
                                color = LinkColors.OrangePrimary,
                                fontFamily = MonoFont,
                                fontSize = MatchFooterSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    "upcoming" -> {
                        val date = displayWhen(event.startMs)
                        val countdown = countdownText(event.startMs, effectiveNow)
                        if (date.isNotBlank()) {
                            Text(
                                date,
                                color = LinkColors.OrangePrimary,
                                fontFamily = MonoFont,
                                fontSize = MatchFooterSize,
                                maxLines = 1,
                            )
                        }
                        if (!countdown.isNullOrBlank()) {
                            Text(
                                "Starts in $countdown",
                                color = LinkColors.PrimaryText,
                                fontFamily = MonoFont,
                                fontSize = MatchFooterSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    else -> Text(
                        "Full time",
                        color = LinkColors.MutedText,
                        fontFamily = MonoFont,
                        fontSize = MatchFooterSize,
                        maxLines = 1,
                    )
                }
            },
        )
    }
}

@Composable
fun HighlightCard(
    highlight: Highlight,
    onClick: () -> Unit,
) {
    CardFrame(onClick = onClick) {
        CardLeague(
            icon = {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    tint = LinkColors.OrangePrimary,
                    modifier = Modifier.size(13.dp),
                )
            },
            label = highlight.title,
            trailing = highlight.duration.takeIf { it.isNotBlank() }?.let { { QualityChip(it) } },
        )
        TeamRow(
            home = highlight.home,
            away = highlight.away,
            status = {
                Box(
                    Modifier.size(34.dp)
                        .background(
                            Brush.verticalGradient(listOf(LinkColors.OrangeLight, LinkColors.OrangePrimary)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            },
        )
        CardFooter {
            Text(
                highlight.duration.takeIf { it.isNotBlank() } ?: "Watch now",
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = MatchFooterSize,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SingleItemCard(
    categoryLine: String,
    team: Team,
    statusText: String?,
    isLive: Boolean,
    onClick: () -> Unit,
    imageLoader: coil.ImageLoader? = null,
) {
    CardFrame(onClick = onClick) {
        CardLeague(
            icon = {
                if (isLive) {
                    Box(Modifier.size(7.dp).background(Color(0xFFFF3B30), CircleShape))
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = LinkColors.OrangePrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            },
            label = categoryLine,
        )
        TeamRowSingle(
            team = team,
            status = statusText?.let {
                {
                    Text(
                        it,
                        color = if (isLive) Color(0xFFFF3B30) else LinkColors.MutedText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            imageLoader = imageLoader,
        )
        if (!statusText.isNullOrBlank()) {
            CardFooter {
                Text(
                    statusText,
                    color = if (isLive) Color(0xFFFF3B30) else LinkColors.MutedText,
                    fontFamily = MonoFont,
                    fontSize = MatchFooterSize,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CardFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.12f else 0.08f,
        label = "borderAlpha",
    )
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.99f else 1f, label = "scale")

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(MatchCardRadius))
            .background(LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = borderAlpha), RoundedCornerShape(MatchCardRadius))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(MatchCardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun CardLeague(
    icon: @Composable () -> Unit,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        Text(
            text = label.ifBlank { "Sports" }.uppercase(),
            color = Color(0xFFE3D3C4),
            fontSize = MatchLeagueSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = .4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Centred footer line; its content is supplied by each card type. */
@Composable
private fun CardFooter(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun Crest(
    team: Team,
    size: androidx.compose.ui.unit.Dp = TeamBadgeSize,
    imageLoader: coil.ImageLoader? = null,
) {
    val fallbackText = team.initials.take(3).uppercase().takeIf { it.isNotBlank() } ?: "?"

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!team.logo.isNullOrBlank()) {
            val modelData = remember(team.logo) {
                if (team.logo.startsWith("data:image")) {
                    try {
                        val base64String = team.logo.substringAfter(",")
                        android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        team.logo
                    }
                } else {
                    team.logo
                }
            }

            SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(modelData)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader ?: coil.Coil.imageLoader(LocalContext.current),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> BadgeFallback(fallbackText, size)
                }
            }
        } else {
            BadgeFallback(fallbackText, size)
        }
    }
}

@Composable
private fun BadgeFallback(text: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = LinkColors.MutedText,
            fontSize = if (size < 32.dp) 9.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MatchCenter(event: Event, status: String, nowMs: Long) {
    when (status) {
        "live" -> {
            val label = MatchClock.eventLabel(event, nowMs) ?: "LIVE"
            val halfTime = label == "HT" || label.equals("Half time", ignoreCase = true)
            if (halfTime) {
                Text(
                    text = "HT",
                    color = LinkColors.OrangePrimary.copy(alpha = 0.7f),
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            } else {
                LiveIndicatorRadar(text = label)
            }
        }
        "upcoming" -> Text(
            displayWhen(event.startMs).ifBlank { "Soon" },
            color = LinkColors.MutedText,
            fontFamily = MonoFont,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text("FT", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun LiveIndicatorRadar(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "radar")

        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Restart),
            label = "radar_scale",
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Restart),
            label = "radar_alpha",
        )

        Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(LinkColors.OrangePrimary, CircleShape),
            )
            Box(
                Modifier.matchParentSize().background(LinkColors.OrangePrimary, CircleShape),
            )
        }

        Text(
            text = text.uppercase(),
            color = LinkColors.OrangePrimary,
            fontFamily = MonoFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun QualityChip(text: String) {
    Text(
        text,
        color = LinkColors.OrangePrimary,
        fontFamily = MonoFont,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(LinkColors.OrangePrimary.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SportIcon(sport: String, status: String = "") {
    val isLive = status == "live"
    val isUpcoming = status == "upcoming"

    val icon = if (isUpcoming) {
        Icons.Default.RadioButtonUnchecked
    } else {
        when (sport.lowercase()) {
            "basketball" -> Icons.Default.SportsBasketball
            "cricket" -> Icons.Default.CatchingPokemon
            else -> Icons.Default.Circle
        }
    }

    if (isLive) {
        val infiniteTransition = rememberInfiniteTransition(label = "sport_radar")

        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Restart),
            label = "radar_scale",
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Restart),
            label = "radar_alpha",
        )

        Box(Modifier.size(13.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(LinkColors.OrangePrimary, CircleShape),
            )
            Icon(icon, null, tint = LinkColors.OrangePrimary, modifier = Modifier.matchParentSize())
        }
    } else {
        Icon(icon, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(13.dp))
    }
}

@Composable
fun MiniCard(
    title: String,
    subtitle: String,
    home: Team?,
    away: Team?,
    current: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderAlpha by animateFloatAsState(targetValue = if (isPressed) 0.12f else 0.08f, label = "border")
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.99f else 1f, label = "scale")

    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(MatchCardRadius))
            .background(if (current) LinkColors.OrangePrimary.copy(alpha = 0.14f) else LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = borderAlpha), RoundedCornerShape(MatchCardRadius))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(10.dp, 10.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TeamRow(
            home = home,
            away = away,
            status = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        color = LinkColors.PrimaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        color = LinkColors.MutedText,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    trailing()
                }
            },
        )
    }
}
