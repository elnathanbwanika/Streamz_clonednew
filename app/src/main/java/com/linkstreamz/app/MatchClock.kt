package com.linkstreamz.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
// Match clock — one pure source of truth for live minute labels and status.
// All times are milliseconds; callers pass ServerClock.now() so the clock
// follows the server-adjusted time learned from HTTP Date headers.
// ─────────────────────────────────────────────────────────────────────────────

object MatchClock {
    const val DEFAULT_ADDED_FIRST = 5
    const val DEFAULT_ADDED_SECOND = 10
    const val HALF_TIME_MINUTES = 15
    const val FIRST_HALF_MINUTES = 45
    const val SECOND_HALF_MINUTES = 45

    /** Status derived from a kickoff, clamped added times, and explicit admin status. */
    fun phase(
        kickoffMs: Long?,
        addedFirst: Int = DEFAULT_ADDED_FIRST,
        addedSecond: Int = DEFAULT_ADDED_SECOND,
        nowMs: Long = ServerClock.now(),
    ): MatchPhase {
        if (kickoffMs == null) return MatchPhase.LIVE
        val t = (nowMs - kickoffMs) / 60_000.0
        val a1 = addedFirst.coerceIn(0, 5)
        val a2 = addedSecond.coerceIn(0, 10)
        return when {
            t < 0 -> MatchPhase.UPCOMING
            t < FIRST_HALF_MINUTES + a1 -> MatchPhase.LIVE
            t < FIRST_HALF_MINUTES + a1 + HALF_TIME_MINUTES -> MatchPhase.HALF_TIME
            t < FIRST_HALF_MINUTES + a1 + HALF_TIME_MINUTES + SECOND_HALF_MINUTES + a2 -> MatchPhase.LIVE
            else -> MatchPhase.FULL_TIME
        }
    }

    /** Label for a kickoff-driven match. Null means the caller should use its fallback. */
    fun label(
        kickoffMs: Long?,
        addedFirst: Int = DEFAULT_ADDED_FIRST,
        addedSecond: Int = DEFAULT_ADDED_SECOND,
        nowMs: Long = ServerClock.now(),
    ): String? {
        if (kickoffMs == null) return null
        val t = (nowMs - kickoffMs) / 60_000.0
        val a1 = addedFirst.coerceIn(0, 5)
        val a2 = addedSecond.coerceIn(0, 10)
        return when {
            t < 0 -> "0'"
            t < FIRST_HALF_MINUTES -> "${floor(t).toInt() + 1}'"
            t < FIRST_HALF_MINUTES + a1 ->
                "45+${floor(t - FIRST_HALF_MINUTES).toInt() + 1}'"
            t < FIRST_HALF_MINUTES + a1 + HALF_TIME_MINUTES -> "HT"
            else -> {
                val u = t - (FIRST_HALF_MINUTES + a1 + HALF_TIME_MINUTES)
                when {
                    u < SECOND_HALF_MINUTES -> "${FIRST_HALF_MINUTES + 1 + floor(u).toInt()}'"
                    u < SECOND_HALF_MINUTES + a2 ->
                        "90+${floor(u - SECOND_HALF_MINUTES).toInt() + 1}'"
                    else -> "FT"
                }
            }
        }
    }

    /**
     * Label for any event. Kickoff events use the computed clock; otherwise the
     * admin's minute is shown verbatim (minuteText, or `37'`), or null for LIVE.
     */
    fun kickoffOf(event: Event): Long? =
        if (event.hasKickoff) event.kickoffMs else event.startMs

    fun eventLabel(event: Event, nowMs: Long = ServerClock.now()): String? {
        val kickoff = kickoffOf(event)
        if (kickoff != null) {
            return label(kickoff, event.addedFirst, event.addedSecond, nowMs)
        }
        val text = event.minuteText?.trim()?.takeIf { it.isNotBlank() }
        if (text != null) return text
        val minute = event.minute ?: return null
        return "$minute'"
    }

    /**
     * Effective status for tabs/counts. Explicit admin "finished" always wins;
     * otherwise a kickoff-driven clock decides. HT is still live; FT is finished.
     */
    fun status(event: Event, nowMs: Long = ServerClock.now()): String {
        if (event.status.equals("finished", ignoreCase = true)) return "finished"
        val kickoff = kickoffOf(event)
        if (event.status.equals("upcoming", ignoreCase = true) && kickoff == null) {
            return "upcoming"
        }
        if (kickoff == null) return "live"
        return when (phase(kickoff, event.addedFirst, event.addedSecond, nowMs)) {
            MatchPhase.UPCOMING -> "upcoming"
            MatchPhase.FULL_TIME -> "finished"
            MatchPhase.HALF_TIME, MatchPhase.LIVE -> "live"
        }
    }

    /** Next time the visible label changes: minute boundary, or 1s near one. */
    fun nextTickMs(
        kickoffMs: Long?,
        addedFirst: Int = DEFAULT_ADDED_FIRST,
        addedSecond: Int = DEFAULT_ADDED_SECOND,
        nowMs: Long = ServerClock.now(),
    ): Long {
        if (kickoffMs == null) return 60_000L
        val a1 = addedFirst.coerceIn(0, 5)
        val a2 = addedSecond.coerceIn(0, 10)
        val boundaryAfterFirst = FIRST_HALF_MINUTES + a1
        val secondStart = boundaryAfterFirst + HALF_TIME_MINUTES
        val fullTime = secondStart + SECOND_HALF_MINUTES + a2
        val boundaries = listOf(
            0L,
            FIRST_HALF_MINUTES.toLong(),
            boundaryAfterFirst.toLong(),
            secondStart.toLong(),
            secondStart + SECOND_HALF_MINUTES.toLong(),
            fullTime.toLong(),
        )
        val t = (nowMs - kickoffMs) / 60_000.0
        // Near a boundary (within 2s) check every second so the label flips
        // promptly even with a little clock skew; otherwise sleep to the next
        // whole minute inside the current phase.
        val near = boundaries.any { kotlin.math.abs(t - it) < 0.05 }
        if (near) return 1_000L
        val nextWholeMinute = (floor(t) + 1) * 60_000L
        val nextBoundary = boundaries
            .map { it * 60_000L }
            .firstOrNull { it > (nowMs - kickoffMs) }
            ?: Long.MAX_VALUE
        return maxOf(1_000L, minOf(nextWholeMinute - (nowMs - kickoffMs), nextBoundary - (nowMs - kickoffMs)))
    }
}

enum class MatchPhase { UPCOMING, LIVE, HALF_TIME, FULL_TIME }

/** Backwards-compatible wrappers used by existing call sites. */
fun currentMinute(startMs: Long?, nowMs: Long = ServerClock.now()): String? =
    MatchClock.label(startMs, nowMs = nowMs)

fun effectiveStatus(
    startMs: Long?,
    serverStatus: String,
    nowMs: Long = ServerClock.now(),
): String {
    if (startMs == null) return serverStatus
    return MatchClock.status(
        Event(
            id = "", sport = "", league = "", status = serverStatus,
            startMs = startMs, at = "", home = Team("", "", ""),
            away = Team("", "", ""), servers = emptyList(),
            kickoffMs = startMs,
        ),
        nowMs,
    )
}

fun countdownText(startMs: Long?, nowMs: Long = ServerClock.now()): String? {
    if (startMs == null) return null
    val diff = startMs - nowMs
    if (diff <= 0) return null
    if (diff < 60_000) return "${maxOf(1, ceil(diff / 1000.0).toInt())}s"
    val totalMinutes = floor(diff / 60_000.0).toInt()
    val hours = totalMinutes / 60
    val days = hours / 24
    return when {
        days >= 2 -> "${days}d"
        days >= 1 -> "${days}d ${hours % 24}h"
        hours >= 1 -> "${hours}h ${totalMinutes % 60}m"
        else -> "${totalMinutes}m"
    }
}

fun displayWhen(startMs: Long?): String {
    if (startMs == null) return ""
    val instant = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    val label = when (instant.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> instant.format(DateTimeFormatter.ofPattern("d MMM"))
    }
    return "$label · ${instant.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

fun relativeTime(dateMs: Long?): String {
    if (dateMs == null) return ""
    val minutes = ((ServerClock.now() - dateMs) / 60_000).toInt()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${maxOf(1, minutes / 60)}h ago"
        minutes < 10_080 -> "${maxOf(1, minutes / 1_440)}d ago"
        else -> displayWhen(dateMs)
    }
}
