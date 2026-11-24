// CHANGED: Complete replacement of the data-loading strategy.
//
// BEFORE: The app used two separate Retrofit endpoints (/events and /highlights)
//         and fell back to a hardcoded demoFeed() when SERVER_URL contained
//         "your-server.example.com". All hardcoded events, teams, highlights,
//         and mux.dev test-stream URLs have been removed.
//
// AFTER:  A single OkHttp GET fetches content.json from GitHub Pages once and
//         parses all six sections (settings, sports, leagues, teams, events,
//         highlights) in one pass. The team code-map is built at parse time so
//         every event/highlight gets its full Team object (with badge URL) from
//         the JSON, not from a local lookup table. There is no demo mode, no
//         fallback data, and no hardcoded content anywhere.
//
// FEED: Every point where the loader or mapper touches the codebase is marked
//       with a "// FEED:" comment for easy auditing.

package com.linkstreamz.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// ─────────────────────────────────────────────────────────────────────────────
// Data models — unchanged from before except Team gains a teamCode field
// so the code-map can be built from the JSON teams[] array.
// ─────────────────────────────────────────────────────────────────────────────

data class StreamServer(
    val name: String,
    // Muxed source (video+audio together). Non-null in muxed mode; null in split/audio-only mode.
    val url: String? = null,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val useProxy: Boolean = false,
    val delaySeconds: Int? = null,
    // Explicit video source for split mode. Non-null when server carries separate video+audio.
    val videoUrl: String? = null,
    // Explicit audio source. Non-null in split mode and audio-only mode.
    val audioUrl: String? = null,
    // When false, the player hides the item-level TV badge for this server only.
    // Missing, null or non-boolean values keep the badge visible.
    val showTvBadge: Boolean = true,
    // Marks the preferred source. Downloads try a recommended direct-file
    // server before falling back to the first direct-file server.
    val recommended: Boolean = false,
)

internal fun parseServerTvBadge(element: JsonElement?): Boolean {
    val primitive = element as? JsonPrimitive ?: return true
    if (primitive.isString) return true
    return primitive.booleanOrNull != false
}

/**
 * Returns the playback mode for a normalised [StreamServer].
 *
 * "muxed"      – single URL that contains both video and audio (existing behaviour).
 * "split"      – separate video URL + separate audio URL that must be played together.
 * "audio-only" – only an audio track is available; show a placeholder in the video surface.
 * "invalid"    – not enough information to play anything; should be filtered out.
 */
fun serverMode(server: StreamServer): String = when {
    server.videoUrl != null && server.audioUrl != null -> "split"
    server.url != null                                  -> "muxed"
    server.audioUrl != null                             -> "audio-only"
    else                                                -> "invalid"
}

data class Team(val name: String, val crestClass: String, val initials: String, val logo: String? = null)

data class Event(
    val id: String,
    val sport: String,
    val league: String,
    val status: String,
    val startMs: Long?,
    val at: String,
    val home: Team,
    val away: Team,
    val servers: List<StreamServer>,
    val tvBadge: TvBadge? = null,
    // Admin live-match fields. Parsed tolerantly; all nullable/defaulted so a
    // bad value never drops the event. The clock uses kickoffMs when present.
    val minute: Int? = null,
    val minuteText: String? = null,
    val kickoffMs: Long? = null,
    // True when the feed supplied a kickoff. startMs alone is kept for legacy
    // callers whose fixtures only set startMs; they still get a computed clock.
    val hasKickoff: Boolean = false,
    val addedFirst: Int = MatchClock.DEFAULT_ADDED_FIRST,
    val addedSecond: Int = MatchClock.DEFAULT_ADDED_SECOND,
)

data class Highlight(
    val id: String,
    val eventId: String?,
    val title: String,
    val duration: String,
    val dateMs: Long?,
    val thumb: String?,
    val home: Team?,
    val away: Team?,
    val servers: List<StreamServer>,
    val tvBadge: TvBadge? = null,
    val sport: String = "",
)

// FEED: Feed now also carries the reference lists parsed from content.json.
// These are exposed so UI pickers (sports, leagues) can use live data.
data class Feed(
    val events: List<Event>,
    val highlights: List<Highlight>,
    // FEED: Top-level arrays from content.json — empty lists when absent.
    val sports: List<String> = emptyList(),
    val leagues: List<LeagueEntry> = emptyList(),
)

// FEED: Represents one entry in the JSON leagues[] array.
data class LeagueEntry(val name: String, val sport: String)

sealed class FeedState {
    data object Loading : FeedState()
    data class Ready(val feed: Feed) : FeedState()
    data object Error : FeedState()
}

// ─────────────────────────────────────────────────────────────────────────────
// Repository
// ─────────────────────────────────────────────────────────────────────────────

class EventsRepository(private val preview: Boolean = false) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // FEED: shared tunnel-aware client (direct while the tunnel is down).
    private val httpClient: OkHttpClient = HttpClients.newBuilder()
        .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()
    private val tunnelWaitDone = AtomicBoolean(false)

    // FEED: Starts the background polling loop. Called once from MainActivity.
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                // FEED: silent=true means "keep existing data on failure".
                load(silent = _state.value is FeedState.Ready)
                delay(REFRESH_MS)
            }
        }
    }

    // FEED: Main loader. Called by: start(), ErrorState Retry button, Settings Refresh.
    // Never throws. Always resolves to Loading → Ready or Loading → Error.
    // If silent=true and a previous Ready state exists, failure keeps the old data.
    suspend fun load(silent: Boolean = false) {
        if (tunnelWaitDone.compareAndSet(false, true)) {
            TunnelManager.awaitUp(TUNNEL_WAIT_MS)
        }
        if (!silent) _state.value = FeedState.Loading
        try {
            // FEED: Single GET to content.json — the only remote call in the app.
            val feed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchAndParse() }
            _state.value = FeedState.Ready(feed)
        } catch (_: Exception) {
            // FEED: On any error, only switch to Error state when there is no
            // previous data to fall back on.
            if (!silent || _state.value !is FeedState.Ready) {
                _state.value = FeedState.Error
            }
            // When silent=true and Ready data exists, we stay in Ready
            // and the caller (HomeScreen) shows a toast via the existing toast mechanism.
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network + parse
    // ─────────────────────────────────────────────────────────────────────────

    // FEED: Performs the GET, validates HTTP status, and delegates to parseFeed().
    // Throws on network error, HTTP error (non-200), or JSON parse error.
    private fun fetchAndParse(): Feed {
        // FEED: Plain GET — no auth headers, no query params.
        val request = okhttp3.Request.Builder()
            .url(CONTENT_JSON_URL)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            ServerClock.updateFromDateHeader(resp.header("Date"))
            // FEED: Non-2xx → treat as network/server error; do not attempt to parse body.
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw IllegalStateException("Empty response body")

            // FEED: Parse may throw JsonParseException — caller catches it as data error.
            val root = json.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("Root element is not a JSON object")

            return parseFeed(root)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top-level parser
    // ─────────────────────────────────────────────────────────────────────────

    // FEED: Parses all six sections from the root JSON object in one pass.
    private fun parseFeed(root: JsonObject): Feed {
        AppUpdateStore.ingest(root["appUpdate"])
        InAppMessageStore.ingest(root)

        // FEED: Build team code-map from teams[] so home/away codes resolve to full Team objects.
        val teamMap: Map<String, Team> = parseTeams(root)

        // FEED: Parse top-level sports[] — empty list when absent.
        val sports: List<String> = (root["sports"] as? JsonArray)
            ?.mapNotNull { it.stringValue() }
            ?: emptyList()

        // FEED: Parse top-level leagues[] — empty list when absent.
        val leagues: List<LeagueEntry> = (root["leagues"] as? JsonArray)
            ?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.stringValue() ?: return@mapNotNull null
                val sport = obj["sport"]?.stringValue() ?: ""
                LeagueEntry(name, sport)
            }
            ?: emptyList()

        // FEED: Parse events[] — empty list when absent or malformed.
        val eventArray = (root["events"] as? JsonArray) ?: JsonArray(emptyList())
        val events = eventArray.mapIndexed { index, item ->
            normalizeEvent(item, index, teamMap)
        }.sortedByDescending { it.id.toLongOrNull() ?: 0L } // FEED: sort newest first
        val eventById = events.associateBy { it.id }

        // FEED: Parse highlights[] — empty list when absent or malformed.
        val highlightArray = (root["highlights"] as? JsonArray) ?: JsonArray(emptyList())
        val highlights = highlightArray.mapIndexed { index, item ->
            normalizeHighlight(item, index, eventById, teamMap)
        }.sortedByDescending { it.id.toLongOrNull() ?: 0L } // FEED: sort newest first

        return Feed(events, highlights, sports, leagues)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Team code-map builder
    // ─────────────────────────────────────────────────────────────────────────

    // FEED: Reads teams[] from the root object and builds a code → Team map.
    // The "badge" field from the JSON becomes the Team.logo.
    private fun parseTeams(root: JsonObject): Map<String, Team> {
        val teamsArray = root["teams"] as? JsonArray ?: return emptyMap()
        return teamsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val code = obj["code"]?.stringValue()?.trim()?.uppercase()
                ?: return@mapNotNull null
            val name = obj["name"]?.stringValue() ?: code
            val badge = obj["badge"]?.stringValue()?.takeIf { it.isNotBlank() }
            val initials = code.take(3)
            val hue = (name.hashCode().absoluteValue % 4) + 1
            code to Team(name = name, crestClass = "b$hue", initials = initials, logo = badge)
        }.toMap()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event mapper
    // ─────────────────────────────────────────────────────────────────────────

    // FEED: Maps one JSON event object to the internal Event model.
    // teamMap is the code → Team lookup built from teams[].
    private fun normalizeEvent(value: JsonElement, index: Int, teamMap: Map<String, Team>): Event {
        val obj = value as? JsonObject ?: JsonObject(emptyMap())
        val start = obj.pick("start", "startTime", "kickoff", "startsAt", "date")
        val kickoff = obj.pick("kickoff", "start", "startTime", "startsAt", "date")
        val kickoffMs = parseDate(kickoff)
        val rawMinute = obj.pick("minute", "min", "matchMinute")
        val minute = parseTolerantMinute(rawMinute)
        val minuteText = obj.pick("minuteText", "minute_text", "clock", "matchClock")
            ?.stringValue()?.trim()?.takeIf { it.isNotBlank() }
        val addedFirst = parseClampedAdded(obj.pick("addedFirst", "added_first", "stoppageFirst"), 5, 0, 5)
        val addedSecond = parseClampedAdded(obj.pick("addedSecond", "added_second", "stoppageSecond"), 10, 0, 10)
        AppLogger.d(
            "EventClock",
            "${obj.pick("id", "_id", "eventId")?.stringValue() ?: index}: " +
                "minute=$minute text=$minuteText kickoff=$kickoffMs " +
                "a1=$addedFirst a2=$addedSecond",
        )
        val status = normalizeStatus(obj.pick("status", "state")?.stringValue())
        return Event(
            // FEED: id field from JSON (int or string); fall back to list index as string.
            id = obj.pick("id", "_id", "eventId")?.stringValue() ?: index.toString(),
            // FEED: sport field; default to empty string if absent (never "Sports" fallback).
            sport = obj.pick("sport", "category")?.stringValue().orEmpty(),
            // FEED: league field.
            league = obj.pick("league", "competition", "tournament")?.stringValue().orEmpty(),
            status = status,
            startMs = parseDate(start),
            at = obj.pick("at")?.stringValue() ?: displayWhen(parseDate(start)),
            // FEED: home/away are short codes resolved via teamMap; fall back to raw parsing.
            home = resolveTeam(obj.pick("home", "homeTeam", "team1"), teamMap),
            away = resolveTeam(obj.pick("away", "awayTeam", "team2"), teamMap),
            servers = streams(obj),
            tvBadge = TvBadgeParser.parse(obj["tvBadge"]),
            minute = minute,
            minuteText = minuteText,
            kickoffMs = kickoffMs,
            hasKickoff = kickoffMs != null,
            addedFirst = addedFirst,
            addedSecond = addedSecond,
        )
    }

    // FEED: minute may be a JSON number, a numeric string ("37", "0"), or
    // absent/null/garbage. "45+3" is left to minuteText. Never throws.
    internal fun parseTolerantMinute(value: JsonElement?): Int? {
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.longOrNull?.toInt()
            ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }

    // FEED: added-time values are numbers, clamped to their legal ranges.
    internal fun parseClampedAdded(
        value: JsonElement?,
        default: Int,
        min: Int,
        max: Int,
    ): Int {
        val primitive = value as? JsonPrimitive ?: return default
        val parsed = primitive.longOrNull?.toInt()
            ?: primitive.contentOrNull?.trim()?.toIntOrNull()
            ?: return default
        return parsed.coerceIn(min, max)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Highlight mapper
    // ─────────────────────────────────────────────────────────────────────────

    // FEED: Maps one JSON highlight object to the internal Highlight model.
    private fun normalizeHighlight(
        value: JsonElement,
        index: Int,
        events: Map<String, Event>,
        teamMap: Map<String, Team>,
    ): Highlight {
        val obj = value as? JsonObject ?: JsonObject(emptyMap())
        val eventId = obj.pick("eventId", "event", "matchId")?.stringValue()
        val event = eventId?.let(events::get)
        // FEED: home/away from highlight JSON take priority; fall back to parent event's teams.
        val home = obj.pick("home", "homeTeam")?.let { resolveTeam(it, teamMap) } ?: event?.home
        val away = obj.pick("away", "awayTeam")?.let { resolveTeam(it, teamMap) } ?: event?.away
        val durationRaw = obj.pick("duration", "dur", "length")
        val duration = durationRaw?.stringValue()
            ?: durationRaw?.longValue()?.let(::formatDuration).orEmpty()
        return Highlight(
            id = obj.pick("id", "_id")?.stringValue() ?: index.toString(),
            eventId = eventId,
            // FEED: "title" field from JSON; never substituted with a fake label.
            title = obj.pick("title", "name")?.stringValue().orEmpty(),
            duration = duration,
            dateMs = parseDate(obj.pick("date", "publishedAt", "createdAt")),
            thumb = obj.pick("thumbnail", "thumb", "image", "poster")?.stringValue(),
            home = home,
            away = away,
            servers = streams(obj),
            tvBadge = TvBadgeParser.parse(obj["tvBadge"]),
            sport = obj.pick("sport", "category")?.stringValue()
                ?.takeIf { it.isNotBlank() }
                ?: event?.sport.orEmpty(),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Team resolution
    // ─────────────────────────────────────────────────────────────────────────

    // FEED: Resolves a home/away field to a Team.
    // Priority:  1. code lookup in teamMap  2. inline object parse  3. bare string
    // Never returns null for events (returns Team("TBD",...) as last resort).
    private fun resolveTeam(value: JsonElement?, teamMap: Map<String, Team>): Team {
        if (value == null) return unknownTeam()

        // FEED: If the value is a plain string, treat it as a team code first.
        if (value is JsonPrimitive) {
            val code = value.content.trim().uppercase()
            teamMap[code]?.let { return it }
            // Not in map — return a minimal Team built from the raw string.
            return Team(
                name = value.content,
                crestClass = "b${(value.content.hashCode().absoluteValue % 4) + 1}",
                initials = value.content.take(3).uppercase(),
                logo = null,
            )
        }

        val obj = value as? JsonObject ?: return unknownTeam()

        // FEED: If the object has a "code" field, prefer the teamMap entry.
        val code = obj.pick("code", "short", "abbr")?.stringValue()?.trim()?.uppercase()
        if (code != null) {
            teamMap[code]?.let { return it }
        }

        // FEED: Fall back to inline parsing (supports rich team objects inline in events).
        val name = obj.pick("name", "n", "title")?.stringValue() ?: code ?: "TBD"
        val initials = code
            ?: name.filter { it.isLetterOrDigit() }.split(" ")
                .filter { it.isNotBlank() }
                .joinToString("") { it.first().uppercase() }
                .take(3)
        val hue = (name.hashCode().absoluteValue % 4) + 1
        return Team(
            name = name,
            crestClass = obj.pick("c")?.stringValue() ?: "b$hue",
            initials = initials,
            // FEED: badge / logo from inline object.
            logo = obj.pick("logo", "logoUrl", "badge", "image", "icon")?.stringValue()
                ?.takeIf { it.isNotBlank() },
        )
    }

    private fun unknownTeam(): Team = Team("TBD", "b1", "TBD", null)

    // ─────────────────────────────────────────────────────────────────────────
    // Server / stream mapper (unchanged logic, kept as-is)
    // ─────────────────────────────────────────────────────────────────────────

    private fun streams(obj: JsonObject): List<StreamServer> {
        val raw = obj.pick("servers", "streams", "links", "sources")
            ?: obj.pick("stream", "url", "src", "link")
            ?: return emptyList()
        val values = if (raw is JsonArray) raw else JsonArray(listOf(raw))
        return values.mapIndexedNotNull { index, element ->
            normalizeServerEntry(element, index)
        }
    }

    /**
     * Normalizes a single raw server/stream entry (either a JSON object or a plain URL string)
     * into a [StreamServer].
     *
     * Accepted video field names : url, video, src, link, stream, file, m3u8
     * Accepted audio field names : audio, audioUrl, audioSrc, audio_url, audioLink, track, soundtrack
     *
     * Rules:
     *   • Only a video-ish field present  → muxed mode  (url = it, videoUrl = it, audioUrl = null)
     *   • Both video-ish AND audio-ish    → split mode   (url = null, videoUrl = video, audioUrl = audio)
     *   • Only an audio-ish field present → audio-only  (url = null, videoUrl = null, audioUrl = audio)
     *   • Neither present                 → invalid → returns null (filtered out)
     */
    private fun normalizeServerEntry(element: JsonElement, index: Int): StreamServer? {
        val server = element as? JsonObject

        // ── Video field resolution ────────────────────────────────────────────
        val rawVideoUrl: String? = server
            ?.pick("url", "video", "src", "link", "stream", "file", "m3u8")
            ?.stringValue()
            ?: element.stringValue() // bare string element is treated as a video URL

        // ── Audio field resolution ────────────────────────────────────────────
        val rawAudioUrl: String? = server
            ?.pick("audio", "audioUrl", "audioSrc", "audio_url", "audioLink", "track", "soundtrack")
            ?.stringValue()

        // ── Shared metadata ───────────────────────────────────────────────────
        val headersObj = server?.pick("headers") as? JsonObject
        // FEED: headers are passed through opaquely — including masked "••••••" values.
        val baseHeaders = headersObj?.mapValues { it.value.stringValue() ?: "" }?.toMutableMap() ?: mutableMapOf()
        // Optional top-level referer / origin fields on the server object merge into headers.
        // If the admin panel sets these, they override whatever is in the headers object.
        server?.pick("referer", "Referer")?.stringValue()?.takeIf { it.isNotBlank() }
            ?.let { baseHeaders["Referer"] = it }
        server?.pick("origin", "Origin")?.stringValue()?.takeIf { it.isNotBlank() }
            ?.let { baseHeaders["Origin"] = it }
        val headers: Map<String, String> = baseHeaders
        val useProxyRaw = server?.pick("useProxy")
        val useProxy = useProxyRaw?.stringValue()?.toBooleanStrictOrNull()
            ?: (useProxyRaw as? JsonPrimitive)?.booleanOrNull ?: false
        val delaySecondsRaw = server?.pick("delaySeconds", "delay")
        val delaySeconds = delaySecondsRaw?.longValue()?.toInt()
        val name = server?.pick("name", "label", "title", "server")?.stringValue()
            ?: "Server ${index + 1}"
        val quality = server?.pick("quality", "q", "resolution")?.stringValue()
        val showTvBadge = parseServerTvBadge(server?.pick("tvBadge"))

        // ── Apply normalization rules ─────────────────────────────────────────
        return when {
            rawVideoUrl != null && rawAudioUrl != null -> StreamServer(
                name = name,
                url = null,
                videoUrl = rawVideoUrl,
                audioUrl = rawAudioUrl,
                quality = quality,
                headers = headers,
                useProxy = useProxy,
                delaySeconds = delaySeconds,
                showTvBadge = showTvBadge,
            )
            rawVideoUrl != null -> StreamServer(
                name = name,
                url = rawVideoUrl,
                videoUrl = rawVideoUrl,
                audioUrl = null,
                quality = quality,
                headers = headers,
                useProxy = useProxy,
                delaySeconds = delaySeconds,
                showTvBadge = showTvBadge,
            )
            rawAudioUrl != null -> StreamServer(
                name = name,
                url = null,
                videoUrl = null,
                audioUrl = rawAudioUrl,
                quality = quality,
                headers = headers,
                useProxy = useProxy,
                delaySeconds = delaySeconds,
                showTvBadge = showTvBadge,
            )
            else -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private fun normalizeStatus(value: String?): String = when (value?.lowercase()?.replace(Regex("[\\s_-]"), "")) {
        "live", "inplay", "inprogress", "ongoing" -> "live"
        "upcoming", "scheduled", "pending", "notstarted" -> "upcoming"
        "finished", "ended", "completed", "ft", "past" -> "finished"
        else -> "upcoming"
    }

    private fun formatDuration(seconds: Long): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)

    private fun JsonObject.pick(vararg names: String): JsonElement? =
        names.firstNotNullOfOrNull { name -> this[name]?.takeUnless { it is JsonPrimitive && it.content == "" } }

    private fun JsonElement.stringValue(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.longValue(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        primitive.longOrNull?.let { return it }
        primitive.doubleOrNull?.let { return it.toLong() }
        return primitive.contentOrNull?.toLongOrNull()
    }

    private fun parseDate(element: JsonElement?): Long? {
        val raw = element?.stringValue() ?: element?.longValue()?.toString() ?: return null
        raw.toLongOrNull()?.let { return if (it < 10_000_000_000) it * 1000 else it }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }
}

private val Int.absoluteValue: Int get() = if (this == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(this)