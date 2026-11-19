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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

@Composable
fun EventCard(
    event: Event,
    nowMs: Long = ServerClock.now(),
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
                if (isLive) Box(Modifier.size(7.dp).background(Color(0xFFFF3B30), CircleShape)) 
                else Icon(Icons.Default.PlayArrow, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(13.dp))
            },
            label = categoryLine,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TeamSide(team, right = false, Modifier.weight(1f), imageLoader = imageLoader)
            if (statusText != null) {
                Text(
                    statusText,
                    color = if (isLive) Color(0xFFFF3B30) else LinkColors.MutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
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
    
    val borderAlpha by animateFloatAsState(targetValue = if (isPressed) 0.12f else 0.08f, label = "borderAlpha")
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
private fun TeamSide(
    team: Team?,
    right: Boolean,
    modifier: Modifier = Modifier,
    imageLoader: coil.ImageLoader? = null,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (right) Arrangement.End else Arrangement.Start,
    ) {
        if (!right) {
            team?.let { Crest(it, imageLoader = imageLoader) }
            Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.width(12.dp))
            team?.let { Crest(it, imageLoader = imageLoader) }
        }
    }
}

@Composable
fun Crest(
    team: Team,
    size: androidx.compose.ui.unit.Dp = 44.dp,
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

            coil.compose.SubcomposeAsyncImage(
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
            LiveIndicatorRadar(text = currentMinute(event.startMs, nowMs) ?: "LIVE")
        }
        "upcoming" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STARTS IN", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 6.5.sp, letterSpacing = 0.3.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            Text(countdownText(event.startMs, nowMs) ?: event.at.ifBlank { "Soon" }, color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 10.5.sp)
        }
        else -> Text("Ended", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp)
    }
}

@Composable
fun LiveIndicatorRadar(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "radar")
        
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_scale"
        )
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_alpha"
        )

        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Expanding outer ring
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(LinkColors.OrangePrimary, CircleShape)
            )
            // Static central dot
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(LinkColors.OrangePrimary, CircleShape)
            )
        }
        
        Text(
            text = text.uppercase(),
            color = LinkColors.OrangePrimary,
            fontFamily = MonoFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
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
        modifier = Modifier.background(LinkColors.OrangePrimary.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
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
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_scale"
        )
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_alpha"
        )

        Box(
            modifier = Modifier.size(13.dp),
            contentAlignment = Alignment.Center
        ) {
            // Expanding outer ring
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(LinkColors.OrangePrimary, CircleShape)
            )
            // Static central sport icon
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) LinkColors.OrangePrimary.copy(alpha = 0.14f) else LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = borderAlpha), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(10.dp, 10.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        home?.let { Crest(it) }
        away?.let { Crest(it) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = LinkColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}