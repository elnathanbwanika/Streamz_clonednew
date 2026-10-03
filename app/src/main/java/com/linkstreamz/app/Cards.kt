package com.linkstreamz.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

@Composable
fun EventCard(
    event: Event,
    nowMs: Long,
    onClick: () -> Unit,
) {
    val status = effectiveStatus(event.startMs, event.status, nowMs)
    CardFrame(onClick = onClick) {
        CardLeague(
            icon = { SportIcon(event.sport, status) },
            label = listOf(event.sport, event.league).filter { it.isNotBlank() }.joinToString(" | "),
        )
        MatchRow(
            home = event.home,
            away = event.away,
            center = { MatchCenter(event, status, nowMs) },
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
            icon = { Icon(Icons.Default.PlayArrow, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(13.dp)) },
            label = highlight.title,
            trailing = highlight.duration.takeIf { it.isNotBlank() }?.let {
                { QualityChip(it) }
            },
        )
        MatchRow(
            home = highlight.home,
            away = highlight.away,
            center = {
                Box(
                    Modifier.size(34.dp).shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = LinkColors.OrangePrimary.copy(alpha = 0.35f),
                        spotColor = LinkColors.OrangePrimary.copy(alpha = 0.35f)
                    ).background(
                        Brush.verticalGradient(listOf(LinkColors.OrangeLight, LinkColors.OrangePrimary)), 
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            },
        )
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
    
    val borderAlpha by animateFloatAsState(targetValue = if (isPressed) 0.50f else 0.16f, label = "borderAlpha")
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.99f else 1f, label = "scale")

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = borderAlpha), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource, 
                indication = LocalIndication.current, 
                onClick = onClick
            )
            .then(
                Modifier.padding(11.dp, 11.dp, 13.dp, 13.dp)
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
        modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        Text(
            text = label.ifBlank { "Sports" }.uppercase(),
            color = Color(0xFFE3D3C4),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun MatchRow(
    home: Team?,
    away: Team?,
    center: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamSide(home, right = false, Modifier.weight(1f))
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) { center() }
        TeamSide(away, right = true, Modifier.weight(1f))
    }
}

@Composable
private fun TeamSide(team: Team?, right: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (right) Arrangement.End else Arrangement.Start,
    ) {
        if (!right) {
            team?.let { Crest(it) }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            team?.name ?: "Highlight",
            color = LinkColors.PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (right) TextAlign.End else TextAlign.Start,
        )
        if (right) {
            Spacer(Modifier.width(8.dp))
            team?.let { Crest(it) }
        }
    }
}

@Composable
fun Crest(team: Team, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val background = when (team.crestClass) {
        "f-ht" -> Brush.verticalGradient(listOf(Color(0xFF00209F), Color(0xFFD21034)))
        "f-cr" -> Brush.verticalGradient(listOf(Color(0xFF002B7F), Color.White, Color(0xFFCE1126), Color.White, Color(0xFF002B7F)))
        "f-cw" -> Brush.verticalGradient(listOf(Color(0xFF002B7F), Color(0xFFF9E814), Color(0xFF002B7F)))
        "f-ni" -> Brush.verticalGradient(listOf(Color(0xFF0067C6), Color.White, Color(0xFF0067C6)))
        "f-dm" -> Brush.verticalGradient(listOf(Color(0xFF0A6E2A), Color(0xFFF5D600), Color(0xFF111111)))
        "f-pr" -> Brush.verticalGradient(listOf(Color(0xFFD0202F), Color.White, Color(0xFFD0202F)))
        "f-in" -> Brush.verticalGradient(listOf(Color(0xFFFF9933), Color.White, Color(0xFF138808)))
        "f-np" -> Brush.linearGradient(listOf(Color(0xFFDC143C), Color(0xFF003893)))
        "b1" -> Brush.linearGradient(listOf(Color(0xFFF5C400), Color(0xFF8A6D00)))
        "b2" -> Brush.linearGradient(listOf(Color(0xFFF78FB3), Color(0xFFC2185B)))
        "b3" -> Brush.linearGradient(listOf(Color(0xFFE2443A), Color(0xFF7A1010)))
        else -> Brush.linearGradient(listOf(Color(0xFF5A6CFF), Color(0xFF1A2A86)))
    }

    val fallbackText = team.initials.take(3).uppercase().takeIf { it.isNotBlank() } ?: "?"

    Box(
        Modifier
            .size(size)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, Color.White.copy(alpha = 0.06f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val textStyle = TextStyle(
            color = Color.White,
            fontSize = if (size < 32.dp) 10.5.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.7f),
                offset = Offset(0f, 2f),
                blurRadius = 4f
            )
        )

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

            coil.compose.SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(modelData)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Inside,
                modifier = Modifier.padding(3.dp).matchParentSize()
            ) {
                when (val state = painter.state) {
                    is coil.compose.AsyncImagePainter.State.Loading -> {
                        Text(fallbackText, style = textStyle)
                    }
                    is coil.compose.AsyncImagePainter.State.Error -> {
                        android.util.Log.e("Crest", "Badge load failed for ${team.initials}: ${state.result.throwable.message}", state.result.throwable)
                        Text(fallbackText, style = textStyle)
                    }
                    is coil.compose.AsyncImagePainter.State.Success -> {
                        android.util.Log.d("Crest", "Rendering badge for ${team.initials}: ${team.logo}")
                        SubcomposeAsyncImageContent()
                    }
                    else -> {
                        Text(fallbackText, style = textStyle)
                    }
                }
            }
        } else {
            android.util.Log.d("Crest", "Falling back to text for ${team.initials}")
            Text(fallbackText, style = textStyle)
        }
    }
}

@Composable
private fun MatchCenter(event: Event, status: String, nowMs: Long) {
    when (status) {
        "live" -> {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    Modifier.size(13.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .drawBehind {
                            drawCircle(
                                color = LinkColors.OrangePrimary.copy(alpha = 0.4f * alpha),
                                radius = size.minDimension * 1.5f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Circle, null, tint = LinkColors.OrangePrimary, modifier = Modifier.matchParentSize())
                }
                Text(currentMinute(event.startMs, nowMs) ?: "Live", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        "upcoming" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STARTS IN", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 9.sp, letterSpacing = .4.sp)
            Text(countdownText(event.startMs, nowMs) ?: event.at.ifBlank { "Soon" }, color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 10.5.sp)
        }
        else -> Text("Ended", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp)
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
        modifier = Modifier.background(LinkColors.OrangePrimary.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SportIcon(sport: String, status: String = "") {
    val isLive = status == "live"
    val isUpcoming = status == "upcoming"
    
    val alpha = if (isLive) {
        val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        ).value
    } else 1f

    val modifier = Modifier
        .size(13.dp)
        .graphicsLayer { this.alpha = alpha }
        .drawBehind {
            if (isLive) {
                drawCircle(
                    color = LinkColors.OrangePrimary.copy(alpha = 0.4f * alpha),
                    radius = size.minDimension * 1.5f
                )
            }
        }

    val icon = if (isUpcoming) {
        Icons.Default.RadioButtonUnchecked
    } else {
        when (sport.lowercase()) {
            "basketball" -> Icons.Default.SportsBasketball
            "cricket" -> Icons.Default.CatchingPokemon
            else -> Icons.Default.Circle
        }
    }
    Icon(icon, null, tint = LinkColors.OrangePrimary, modifier = modifier)
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
    
    val borderAlpha by animateFloatAsState(targetValue = if (isPressed) 0.50f else 0.16f, label = "border")
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.99f else 1f, label = "scale")

    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) LinkColors.OrangePrimary.copy(alpha = 0.14f) else LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = borderAlpha), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(10.dp, 10.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        home?.let { Crest(it, 30.dp) }
        away?.let { Crest(it, 30.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = LinkColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}