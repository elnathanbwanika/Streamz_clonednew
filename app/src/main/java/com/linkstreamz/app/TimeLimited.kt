package com.linkstreamz.app

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

enum class TimeLimitedState { UPCOMING, LIVE, ENDED }

data class TimeLimited(
    val startAtMs: Long,
    val durationHours: Int,
    val allowDownload: Boolean,
) {
    val endAtMs: Long get() = startAtMs + durationHours * 3_600_000L

    fun state(nowMs: Long = ServerClock.now()): TimeLimitedState = when {
        nowMs < startAtMs -> TimeLimitedState.UPCOMING
        nowMs < endAtMs -> TimeLimitedState.LIVE
        else -> TimeLimitedState.ENDED
    }
}

object TimeLimitedParser {
    private val eat = ZoneOffset.ofHours(3)
    private const val DEFAULT_DURATION_HOURS = 168

    fun parse(element: JsonElement?): TimeLimited? {
        val obj = element as? JsonObject ?: return null
        val startAt = parseInstantMs((obj["startAt"] as? JsonPrimitive)?.contentOrNull) ?: return null
        val duration = (obj["durationHours"] as? JsonPrimitive)?.contentOrNull
            ?.trim()?.toFloatOrNull()?.toInt()?.takeIf { it > 0 } ?: DEFAULT_DURATION_HOURS
        val allowDownload = (obj["allowDownload"] as? JsonPrimitive)?.booleanOrNull == true
        return TimeLimited(startAt, duration.coerceAtMost(24 * 365), allowDownload)
    }

    fun parseInstantMs(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value).toInstant(eat).toEpochMilli()
            }.getOrNull()
    }
}

object ServerClock {
    @Volatile private var offsetMs: Long = 0L

    fun now(): Long = System.currentTimeMillis() + offsetMs

    fun updateFromDateHeader(value: String?) {
        val serverMs = parseHttpDate(value) ?: return
        offsetMs = serverMs - System.currentTimeMillis()
    }

    fun updateFromServerMillis(serverMs: Long?) {
        if (serverMs != null && serverMs > 0) offsetMs = serverMs - System.currentTimeMillis()
    }

    fun offsetMillis(): Long = offsetMs

    fun resetForTests() { offsetMs = 0L }

    private fun parseHttpDate(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            java.time.ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull() ?: TimeLimitedParser.parseInstantMs(raw)
    }
}

fun Movie.effectiveTimeLimitedState(nowMs: Long = ServerClock.now()): TimeLimitedState? =
    timeLimited?.state(nowMs)

fun Movie.isTimeLimitedEnded(nowMs: Long = ServerClock.now()): Boolean =
    effectiveTimeLimitedState(nowMs) == TimeLimitedState.ENDED

fun List<Movie>.visibleTimeLimited(nowMs: Long = ServerClock.now()): List<Movie> =
    filter { it.timeLimited != null && !it.isTimeLimitedEnded(nowMs) }

fun timeLimitedAvailabilityText(movie: Movie): String {
    val tl = movie.timeLimited ?: return ""
    return "Available ${formatEatDateTime(tl.startAtMs)} EAT for ${formatDuration(tl.durationHours)}"
}

fun timeLimitedRemainingText(tl: TimeLimited, nowMs: Long = ServerClock.now()): String {
    val remaining = max(0L, tl.endAtMs - nowMs)
    val totalSeconds = remaining / 1000
    return when {
        totalSeconds >= 86_400 -> {
            val days = totalSeconds / 86_400
            val hours = (totalSeconds % 86_400) / 3_600
            "${days}d ${hours}h left"
        }
        totalSeconds >= 3_600 -> {
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds % 3_600) / 60
            "${hours}h ${minutes}m left"
        }
        else -> {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            "${minutes}m ${seconds}s left"
        }
    }
}

fun timeLimitedStartCountdown(tl: TimeLimited, nowMs: Long = ServerClock.now()): String {
    val remaining = max(0L, tl.startAtMs - nowMs)
    val s = remaining / 1000
    return when {
        s >= 86_400 -> "${s / 86_400}d ${(s % 86_400) / 3_600}h"
        s >= 3_600 -> "${s / 3_600}h ${(s % 3_600) / 60}m"
        s >= 60 -> "${s / 60}m ${s % 60}s"
        else -> "${s}s"
    }
}

fun formatEatDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atOffset(ZoneOffset.ofHours(3))
        .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.US))

fun formatDuration(hours: Int): String {
    val days = hours / 24
    return when {
        hours % 24 == 0 && days > 0 -> if (days == 1) "1 day" else "$days days"
        hours == 1 -> "1 hour"
        else -> "$hours hours"
    }
}

fun formatShortDuration(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atOffset(ZoneOffset.ofHours(3))
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US))

object TimeLimitedBannerSelector {
    fun select(movies: List<Movie>, nowMs: Long = ServerClock.now()): Movie? {
        val active = movies.filter { it.timeLimited != null && !it.isTimeLimitedEnded(nowMs) }
        return active.filter { it.effectiveTimeLimitedState(nowMs) == TimeLimitedState.LIVE }
            .minByOrNull { it.timeLimited!!.endAtMs }
            ?: active.filter { it.effectiveTimeLimitedState(nowMs) == TimeLimitedState.UPCOMING }
                .minByOrNull { it.timeLimited!!.startAtMs }
    }

    fun newest(movies: List<Movie>, nowMs: Long = ServerClock.now(), limit: Int = 10): List<Movie> =
        movies.visibleTimeLimited(nowMs)
            .sortedByDescending { it.timeLimited!!.startAtMs }
            .take(limit)
}

object DownloadSupport {
    private val plainExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "ts", "mpg", "mpeg")

    fun isPlainFile(url: String): Boolean {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull() ?: return false
        val path = parsed.encodedPath.lowercase(Locale.US)
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return false
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        if (ext.isBlank()) return !path.contains("youtube", true) && !path.contains("googlevideo", true)
        return ext in plainExtensions
    }

    /**
     * A normal movie is downloadable when the title allows it and at least one
     * direct-file source exists. Time-limited movies keep their own
     * `allowDownload` flag and must still be inside the live window.
     */
    fun canDownload(movie: Movie, nowMs: Long = ServerClock.now()): Boolean {
        if (!movie.allowDownload) return false
        val limited = movie.timeLimited
        if (limited != null) {
            if (!limited.allowDownload) return false
            if (limited.state(nowMs) != TimeLimitedState.LIVE) return false
        }
        return downloadUrl(movie) != null
    }

    fun canDownloadEpisode(series: Series, episode: Episode): Boolean {
        if (!series.allowDownload || !episode.allowDownload) return false
        return downloadUrl(series, episode) != null
    }

    /**
     * Picks the best downloadable URL: a recommended direct-file server first,
     * then the first direct-file server, then the item's own video URL.
     */
    fun downloadUrl(movie: Movie): String? =
        if (!movie.allowDownload) null
        else bestDownloadUrl(movie.servers, movie.videoUrl)

    fun downloadUrl(series: Series, episode: Episode): String? =
        if (!series.allowDownload || !episode.allowDownload) null
        else bestDownloadUrl(episode.servers.ifEmpty { series.servers }, episode.videoUrl)

    private fun bestDownloadUrl(servers: List<StreamServer>, fallback: String): String? {
        val direct = servers.mapNotNull { server ->
            (server.url ?: server.videoUrl)?.takeIf(::canDownloadUrl)
        }
        val recommended = servers.filter { it.recommended }
            .mapNotNull { (it.url ?: it.videoUrl)?.takeIf(::canDownloadUrl) }
            .firstOrNull()
        return recommended
            ?: direct.firstOrNull()
            ?: fallback.takeIf(::canDownloadUrl)
    }

    fun canDownloadUrl(url: String): Boolean = url.isNotBlank() && isPlainFile(url)

    fun extensionFor(url: String): String {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull()
        val path = parsed?.encodedPath ?: url.substringBefore('?')
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.US)
        return ext.takeIf { it in plainExtensions } ?: "mp4"
    }
}

object FileNames {
    private val illegal = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val spaces = Regex("\\s+")

    fun sanitize(value: String, maxLength: Int = 120): String {
        val cleaned = value
            .replace(illegal, "_")
            .replace(spaces, " ")
            .trim()
            .trim('.', ' ')
            .ifBlank { "download" }
        return cleaned.take(maxLength.coerceAtLeast(1)).trim().ifBlank { "download" }
    }

    fun singleMovie(title: String, extension: String): String =
        "${sanitize(title)}.${sanitize(extension).lowercase()}"

    fun episode(series: String, season: Int, episode: Int, title: String, extension: String): String =
        "S%02dE%02d - %s.%s".format(season, episode, sanitize(title), sanitize(extension).lowercase())
}
