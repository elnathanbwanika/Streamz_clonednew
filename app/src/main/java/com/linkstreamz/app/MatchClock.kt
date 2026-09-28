package com.linkstreamz.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

fun currentMinute(startMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
    if (startMs == null) return null
    val mins = floor((nowMs - startMs) / 60_000.0).toInt()
    if (mins < 0) return null
    return when {
        mins < 1 -> "1'"
        mins <= 90 -> "$mins'"
        else -> "90+${mins - 90}'"
    }
}

fun countdownText(startMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
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

fun effectiveStatus(
    startMs: Long?,
    serverStatus: String,
    nowMs: Long = System.currentTimeMillis(),
): String {
    if (startMs == null) return serverStatus
    val minutes = (nowMs - startMs) / 60_000.0
    return when {
        minutes < 0 -> "upcoming"
        minutes < MATCH_MINUTES -> "live"
        else -> "finished"
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
    val minutes = ((System.currentTimeMillis() - dateMs) / 60_000).toInt()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${maxOf(1, minutes / 60)}h ago"
        minutes < 10_080 -> "${maxOf(1, minutes / 1_440)}d ago"
        else -> displayWhen(dateMs)
    }
}