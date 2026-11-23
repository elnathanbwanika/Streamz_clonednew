package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// Home event spotlight
//
// Replaces the old "Recently downloaded" summary strip. One event card at a
// time, rotating automatically every five seconds. Ranking is a pure helper so
// it can be unit-tested without Compose; the composable only owns the timer and
// the current page index.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Pure ranking + timing rules for the spotlight strip. */
object EventSpotlight {
    /** How long one card stays visible before the next one fades in. */
    const val ROTATE_MS = 5_000L

    /** Cap so the dots stay readable and the timer never walks a huge list. */
    const val MAX_EVENTS = 8

    /** Minimum horizontal travel (px) that counts as a deliberate swipe. */
    const val SWIPE_THRESHOLD_PX = 40f

    /**
     * Live first (soonest kickoff first), then upcoming (soonest first), then
     * finished (newest first). De-duplicates by id and caps the result.
     */
    fun rank(events: List<Event>, nowMs: Long = ServerClock.now()): List<Event> {
        fun bucket(event: Event): Int = when (effectiveStatus(event.startMs, event.status, nowMs)) {
            "live" -> 0
            "upcoming" -> 1
            else -> 2
        }
        return events
            .distinctBy { it.id }
            .sortedWith(
                compareBy<Event> { bucket(it) }
                    .thenBy { event ->
                        val start = event.startMs ?: Long.MAX_VALUE
                        if (bucket(event) == 2) -start else start
                    }
            )
            .take(MAX_EVENTS)
    }
}

@Composable
fun HomeEventSpotlightCard(
    events: List<Event>,
    onClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranked = remember(events) { EventSpotlight.rank(events) }
    var page by remember(ranked) { mutableIntStateOf(0) }
    var manualChange by remember { mutableIntStateOf(0) }

    // A feed refresh or a manual swipe restarts the timer, so a card the user
    // picked always gets the full five seconds before auto-rotation resumes.
    LaunchedEffect(ranked, manualChange) {
        if (ranked.size < 2) return@LaunchedEffect
        while (true) {
            delay(EventSpotlight.ROTATE_MS)
            page = (page + 1) % ranked.size
        }
    }

    if (ranked.isEmpty()) return

    Crossfade(
        targetState = page % ranked.size,
        animationSpec = tween(300),
        label = "event-spotlight",
    ) { index ->
        val event = ranked[index]
        val status = effectiveStatus(event.startMs, event.status, ServerClock.now())
        val accent = if (status == "live") Color(0xFFFF3B30) else LinkColors.OrangePrimary
        val headline = when (status) {
            "live" -> "LIVE NOW"
            "upcoming" -> "UPCOMING"
            else -> "FINISHED"
        }
        val league = listOf(event.sport, event.league)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        val detail = when (status) {
            "live" -> "In progress"
            "upcoming" -> displayWhen(event.startMs)
            else -> "Full time"
        }

        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LinkColors.CardBg)
                .pointerInput(ranked.size) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            if (abs(dragAmount) > 1f) change.consume()
                        },
                        onDragEnd = {
                            if (ranked.size > 1 && abs(totalDrag) >= EventSpotlight.SWIPE_THRESHOLD_PX) {
                                val step = if (totalDrag < 0) 1 else -1
                                page = (page + step + ranked.size) % ranked.size
                                manualChange++
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                    )
                }
                .clickable { onClick(event) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LinkColors.OrangePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (status == "live") {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = LinkColors.OrangePrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = listOf(headline, league).filter { it.isNotBlank() }.joinToString(" · "),
                    color = accent,
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${event.home.name} vs ${event.away.name}",
                    color = LinkColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    color = LinkColors.MutedText,
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Watch",
                    color = LinkColors.OrangePrimary,
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                )
                if (ranked.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(ranked.size) { dot ->
                            Box(
                                Modifier
                                    .size(if (dot == index) 10.dp else 4.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (dot == index) LinkColors.OrangePrimary
                                        else LinkColors.MutedText.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
