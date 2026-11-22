package com.linkstreamz.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Carousel slide cards.
//
// Every slide uses the same outer frame (HomeCarouselFrame): the same FIXED
// height, the same corner radius, the same padding, and the same three-zone
// layout (header / middle / footer with SpaceBetween). Only the content differs,
// so neither changing slide nor a ticking countdown can shift the layout.
// A fixed height (rather than heightIn) is what keeps the pager from resizing
// between slides, which would otherwise make the whole rail jump.
// ─────────────────────────────────────────────────────────────────────────────

/** Fixed card height. Tall enough for the widest slide (limited movie). */
private val CarouselCardHeight = 190.dp

/** Shared shell: one height, one radius, one padding, one background wash. */
@Composable
private fun HomeCarouselFrame(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .height(CarouselCardHeight)
            .clip(LsShape.Hero)
            .background(LsColor.HeroBase)
            .drawBehind {
                drawCircle(
                    color = LsColor.Accent.copy(alpha = 0.16f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width, 0f),
                )
            }
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

/** Small uppercase tag pill. */
@Composable
private fun CarouselTag(text: String, background: Color = LinkColors.OrangePrimary) {
    val onOrange = background == LinkColors.OrangePrimary
    Box(
        Modifier
            .background(background, LsShape.Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text.uppercase(),
            color = if (onOrange) LsColor.OnAccent else LsColor.Text,
            fontFamily = MonoFont,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
    }
}

/** Muted mono line beside a tag (time left, "Starts in ..."). */
@Composable
private fun CarouselMeta(text: String) {
    Text(
        text,
        color = LinkColors.OrangeLight,
        fontFamily = MonoFont,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Title that always reserves two lines, so a shorter title cannot shift the card. */
@Composable
private fun CarouselTitle(text: String, size: Int = 20) {
    Text(
        text,
        color = LsColor.Text,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(min = (size * 1.15f * 2).dp),
    )
}

/** Body line that always reserves its two lines. */
@Composable
private fun CarouselBody(text: String) {
    Text(
        text,
        color = LsColor.TextDim,
        fontFamily = MonoFont,
        fontSize = 10.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(min = 26.dp),
    )
}

/** Pulsing dot used by the LIVE tag. */
@Composable
private fun PulsingDot(color: Color = LsColor.OnAccent) {
    val transition = rememberInfiniteTransition(label = "pip-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pip-dot-alpha",
    )
    Box(
        Modifier
            .size(5.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape)
    )
}

/** Live tag: orange pill with a pulsing dot. */
@Composable
private fun LiveTag() {
    Row(
        Modifier
            .background(LinkColors.OrangePrimary, LsShape.Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PulsingDot()
        Text(
            "LIVE",
            color = LsColor.OnAccent,
            fontFamily = MonoFont,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

/** Orange primary button, borderless. */
@Composable
private fun CarouselButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = LinkColors.OrangePrimary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        shape = LsShape.Button,
        modifier = Modifier.height(LsSpace.Button),
    ) {
        Text(
            text,
            color = LsColor.OnAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Subtle icon button used for the download action. */
@Composable
private fun CarouselIconButton(description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(LsSpace.Button)
            .clip(LsShape.Button)
            .background(LsColor.FillSubtle)
            .semantics { contentDescription = description },
    ) {
        Icon(Icons.Default.Download, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(18.dp))
    }
}

/** "Starts in 2h 15m" inside 24 h, otherwise the kickoff day and time in EAT. */
internal fun startsInText(startMs: Long?, nowMs: Long): String {
    if (startMs == null) return "Starting soon"
    val remaining = startMs - nowMs
    if (remaining <= 0L) return "Starting now"
    if (remaining > 24L * 3_600_000L) {
        val eat = Instant.ofEpochMilli(startMs).atOffset(ZoneOffset.ofHours(3))
        return eat.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.US)) + " EAT"
    }
    val totalMinutes = remaining / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "Starts in ${hours}h ${minutes}m" else "Starts in ${minutes}m"
}

/** Renders one slide. All layouts share [HomeCarouselFrame]. */
@Composable
fun HomeCarouselCard(
    slide: HomeCarouselSlide,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDownload: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (slide) {
        is HomeCarouselSlide.Sports -> SportsSlideCard(
            event = slide.event,
            isLive = slide.isLive,
            onClick = onClick,
            onPlay = onPlay,
            modifier = modifier,
        )
        is HomeCarouselSlide.Limited -> LimitedSlideCard(
            movie = slide.movie,
            limits = slide.limits,
            onClick = onClick,
            onPlay = onPlay,
            onDownload = { onDownload(slide.movie.toMovie()) },
            modifier = modifier,
        )
        is HomeCarouselSlide.NewMovie -> NewMovieSlideCard(
            movie = slide.movie,
            onClick = onClick,
            onPlay = onPlay,
            modifier = modifier,
        )
    }
}

/** LIVE / UPCOMING event: tag, league, both crests with names, countdown, CTA. */
@Composable
private fun SportsSlideCard(
    event: Event,
    isLive: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A minute is plenty for an event countdown, and only the composed slide runs it.
    val nowMs by produceState(ServerClock.now(), event.id) {
        while (true) {
            delay(60_000L)
            value = ServerClock.now()
        }
    }
    val league = listOf(event.sport, event.league).filter { it.isNotBlank() }.joinToString(" | ")
    val teams = "${event.home.name} vs ${event.away.name}"
    val when_ = startsInText(event.startMs, nowMs)
    HomeCarouselFrame(
        onClick = onClick,
        contentDescription = if (isLive) {
            "Live now, $league, $teams. Watch live."
        } else {
            "Upcoming, $league, $teams, $when_"
        },
        modifier = modifier,
    ) {
        // Zone 1 - tag + league
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLive) LiveTag() else CarouselTag("UPCOMING", background = LsColor.FillSubtle)
            Spacer(Modifier.width(8.dp))
            Text(
                league,
                color = LinkColors.OrangeLight,
                fontFamily = MonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Zone 2 - shared match row: fixed 36dp badges, flexible names, fixed status.
        TeamRow(
            home = event.home,
            away = event.away,
            status = {
                Text(
                    if (isLive) "LIVE" else when_,
                    color = LsColor.TextDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        // Zone 3 - countdown, plus the CTA for live events only
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (isLive) "Playing now" else when_,
                color = LsColor.TextDim,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLive) {
                Spacer(Modifier.width(12.dp))
                CarouselButton("Watch live", onPlay)
            }
        }
    }
}

/** Time-limited movie: tag with time left, title, availability, Watch + download. */
@Composable
private fun LimitedSlideCard(
    movie: CarouselMovie,
    limits: MovieLimits,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tl = movie.timeLimited ?: return
    // Derived from the limits the builder sorted this slide by, so the tag can
    // never disagree with the bucket it came from.
    val state = when {
        ServerClock.now() < limits.startAtMs -> TimeLimitedState.UPCOMING
        ServerClock.now() < limits.endAtMs -> TimeLimitedState.LIVE
        else -> TimeLimitedState.ENDED
    }
    val nowMs by produceState(ServerClock.now(), movie.id) {
        while (true) {
            // Under an hour left the countdown shows seconds, so tick every second.
            // Otherwise a minute is plenty, which keeps recomposition cheap.
            val remaining = tl.endAtMs - ServerClock.now()
            delay(if (remaining in 1..3_600_000L) 1_000L else 60_000L)
            value = ServerClock.now()
        }
    }
    val meta = when (state) {
        TimeLimitedState.LIVE -> timeLimitedRemainingText(tl, nowMs)
        TimeLimitedState.UPCOMING -> "Starts in ${timeLimitedStartCountdown(tl, nowMs)}"
        TimeLimitedState.ENDED -> ""
    }
    HomeCarouselFrame(
        onClick = onClick,
        contentDescription = "Limited time movie, ${movie.title}, $meta",
        modifier = modifier,
    ) {
        // Zone 1 - tag + time left / starts in
        Row(verticalAlignment = Alignment.CenterVertically) {
            CarouselTag("LIMITED TIME")
            Spacer(Modifier.width(8.dp))
            CarouselMeta(meta)
        }
        // Zone 2 - title + availability
        Column {
            CarouselTitle(movie.title)
            CarouselBody(timeLimitedAvailabilityText(movie.toMovie()))
        }
        // Zone 3 - Watch / Details + download when allowed
        Row(verticalAlignment = Alignment.CenterVertically) {
            CarouselButton(if (state == TimeLimitedState.LIVE) "Watch" else "Details", onPlay)
            if (DownloadSupport.canDownload(movie.toMovie(), nowMs)) {
                Spacer(Modifier.width(12.dp))
                CarouselIconButton("Download", onDownload)
            }
        }
    }
}

/** Recently added movie: NEW tag, poster thumb, title, "GENRE · year", VJ, Play. */
@Composable
private fun NewMovieSlideCard(
    movie: CarouselMovie,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = listOf(movie.genre.uppercase(), movie.year.toString())
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    HomeCarouselFrame(
        onClick = onClick,
        contentDescription = "New movie, ${movie.title}, $meta. Play.",
        modifier = modifier,
    ) {
        // Zone 1 - tag
        CarouselTag("NEW")
        // Zone 2 - poster + text
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = movie.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 46.dp, height = 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LsColor.Card),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                CarouselTitle(movie.title, size = 17)
                Text(
                    meta,
                    color = LsColor.TextDim,
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!movie.vj.isNullOrBlank()) {
                    Text(
                        movie.vj,
                        color = LinkColors.OrangeLight,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Zone 3 - Play
        Row(verticalAlignment = Alignment.CenterVertically) {
            CarouselButton("Play", onPlay)
        }
    }
}
