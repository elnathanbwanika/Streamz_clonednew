package com.linkstreamz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Basketball
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.BoxScope

@Composable
fun EventCard(event: Event, nowMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = effectiveStatus(event.startMs, event.status, nowMs)
    CardFrame(status == "live", onClick, modifier) {
        CardLeague(
            icon = { SportIcon(event.sport) },
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
fun HighlightCard(highlight: Highlight, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardFrame(false, onClick, modifier) {
        CardLeague(
            icon = { Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(13.dp)) },
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
                    Modifier.size(34.dp).shadow(8.dp, CircleShape)
                        .background(Brush.linearGradient(listOf(Accent2, Accent)), CircleShape),
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
    live: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // SHADOW: card:hover box-shadow: 0 2px 8px rgba(0,0,0,.35)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Card2, Card)))
            .clickable(onClick = onClick)
            .then(
                Modifier.padding(11.dp, 11.dp, 13.dp, 13.dp),
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
            color = Color(0xFFDCDCDC),
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
            color = Text,
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
    Box(
        Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (team.logo != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(team.logo).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Inside,
                modifier = Modifier.padding(3.dp).matchParentSize(),
            )
        } else {
            Text(team.initials, color = Color.White, fontSize = if (size < 32.dp) 10.5.sp else 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MatchCenter(event: Event, status: String, nowMs: Long) {
    when (status) {
        "live" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Circle, null, tint = Accent, modifier = Modifier.size(13.dp))
            Text(currentMinute(event.startMs, nowMs) ?: "Live", color = Accent, fontFamily = MonoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        "upcoming" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STARTS IN", color = Accent.copy(alpha = .85f), fontFamily = MonoFont, fontSize = 9.sp, letterSpacing = .4.sp)
            Text(countdownText(event.startMs, nowMs) ?: event.at.ifBlank { "Soon" }, color = Text, fontFamily = MonoFont, fontSize = 10.5.sp)
        }
        else -> Text("Ended", color = Muted, fontFamily = MonoFont, fontSize = 10.sp)
    }
}

@Composable
private fun QualityChip(text: String) {
    Text(
        text,
        color = Accent,
        fontFamily = MonoFont,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(AccentSoft, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SportIcon(sport: String) {
    val icon = when (sport.lowercase()) {
        "basketball" -> Icons.Default.Basketball
        "cricket" -> Icons.Default.CatchingPokemon
        else -> Icons.Default.Circle
    }
    Icon(icon, null, tint = Accent, modifier = Modifier.size(13.dp))
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
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) Brush.horizontalGradient(listOf(AccentSoft, Card)) else Brush.verticalGradient(listOf(Card2, Card)))
            .clickable(onClick = onClick)
            .padding(10.dp, 10.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        home?.let { Crest(it, 30.dp) }
        away?.let { Crest(it, 30.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontFamily = MonoFont, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}