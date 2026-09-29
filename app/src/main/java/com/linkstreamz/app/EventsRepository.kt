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
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url
import java.time.Instant

data class StreamServer(
    val name: String,
    val url: String,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val useProxy: Boolean = false,
    val delaySeconds: Int? = null
)
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
)
data class Feed(val events: List<Event>, val highlights: List<Highlight>)
sealed class FeedState {
    data object Loading : FeedState()
    data class Ready(val feed: Feed) : FeedState()
    data object Error : FeedState()
}

private interface EventsApi {
    @GET suspend fun getEvents(@Url url: String, @HeaderMap headers: Map<String, String>): JsonElement
    @GET suspend fun getHighlights(@Url url: String, @HeaderMap headers: Map<String, String>): JsonElement
}

class EventsRepository(private val preview: Boolean = false) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val api: EventsApi
    private val demo = preview || SERVER_URL.contains("your-server.example.com")
    private val _state = MutableStateFlow<FeedState>(
        if (preview) FeedState.Ready(demoFeed()) else FeedState.Loading,
    )
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        val headersInterceptor = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().build())
        }
        val client = OkHttpClient.Builder().addInterceptor(headersInterceptor).build()
        api = Retrofit.Builder()
            .baseUrl("${SERVER_URL.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EventsApi::class.java)
    }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                load(silent = _state.value is FeedState.Ready)
                delay(REFRESH_MS)
            }
        }
    }

    suspend fun load(silent: Boolean = false) {
        if (!silent) _state.value = FeedState.Loading
        try {
            val feed = if (demo) demoFeed() else {
                val base = SERVER_URL.trimEnd('/')
                val eventJson = api.getEvents("$base/events", REQUEST_HEADERS)
                val highlightJson = api.getHighlights("$base/highlights", REQUEST_HEADERS)
                normalize(eventJson, highlightJson)
            }
            _state.value = FeedState.Ready(feed)
        } catch (_: Exception) {
            if (!silent || _state.value !is FeedState.Ready) _state.value = FeedState.Error
        }
    }

    private fun normalize(eventsJson: JsonElement, highlightsJson: JsonElement): Feed {
        val eventArray = asList(eventsJson, "events")
        val events = eventArray.mapIndexed { index, item -> normalizeEvent(item, index) }
        val eventById = events.associateBy { it.id }
        val highlights = asList(highlightsJson, "highlights").mapIndexed { index, item ->
            normalizeHighlight(item, index, eventById)
        }
        return Feed(events, highlights)
    }

    private fun asList(value: JsonElement, key: String): List<JsonElement> {
        if (value is JsonArray) return value
        val obj = value as? JsonObject ?: return emptyList()
        return (obj[key] ?: obj["data"] ?: obj["items"] ?: obj["results"])
            ?.let { it as? JsonArray } ?: emptyList()
    }

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

    private fun team(value: JsonElement?): Team {
        val obj = value as? JsonObject
        val name = when {
            value is JsonPrimitive -> value.content
            else -> obj?.pick("name", "n", "title")?.stringValue() ?: "TBD"
        }
        val initials = obj?.pick("short", "code", "abbr")?.stringValue()
            ?: name.filter { it.isLetterOrDigit() }.split(" ").filter { it.isNotBlank() }
                .joinToString("") { it.first().uppercase() }.take(3)
        val hue = (name.hashCode().absoluteValue % 4) + 1
        return Team(
            name = name,
            crestClass = obj?.pick("c")?.stringValue() ?: "b$hue",
            initials = initials,
            logo = obj?.pick("logo", "logoUrl", "badge", "image", "icon")?.stringValue(),
        )
    }

    private fun streams(obj: JsonObject): List<StreamServer> {
        val raw = obj.pick("servers", "streams", "links", "sources")
            ?: obj.pick("stream", "url", "src", "link")
            ?: return emptyList()
        val values = if (raw is JsonArray) raw else JsonArray(listOf(raw))
        return values.mapIndexedNotNull { index, element ->
            val server = element as? JsonObject
            val url = server?.pick("url", "src", "link", "stream", "m3u8")?.stringValue()
                ?: element.stringValue()
            url?.let {
                val headersObj = server?.pick("headers") as? JsonObject
                val headers = headersObj?.mapValues { it.value.stringValue() ?: "" } ?: emptyMap()
                val useProxyRaw = server?.pick("useProxy")
                val useProxy = useProxyRaw?.stringValue()?.toBooleanStrictOrNull() ?: (useProxyRaw as? JsonPrimitive)?.booleanOrNull ?: false
                val delaySecondsRaw = server?.pick("delaySeconds", "delay")
                val delaySeconds = delaySecondsRaw?.longValue()?.toInt()
                
                StreamServer(
                    name = server?.pick("name", "label", "title", "server")?.stringValue()
                        ?: "Server ${index + 1}",
                    url = it,
                    quality = server?.pick("quality", "q", "resolution")?.stringValue(),
                    headers = headers,
                    useProxy = useProxy,
                    delaySeconds = delaySeconds
                )
            }
        }
    }

    private fun normalizeEvent(value: JsonElement, index: Int): Event {
        val obj = value as? JsonObject ?: JsonObject(emptyMap())
        val start = obj.pick("start", "startTime", "kickoff", "startsAt", "date")
        val status = normalizeStatus(obj.pick("status", "state")?.stringValue())
        return Event(
            id = obj.pick("id", "_id", "eventId")?.stringValue() ?: index.toString(),
            sport = obj.pick("sport", "category")?.stringValue() ?: "Sports",
            league = obj.pick("league", "competition", "tournament")?.stringValue().orEmpty(),
            status = status,
            startMs = parseDate(start),
            at = obj.pick("at")?.stringValue() ?: displayWhen(parseDate(start)),
            home = team(obj.pick("home", "homeTeam", "team1")),
            away = team(obj.pick("away", "awayTeam", "team2")),
            servers = streams(obj),
        )
    }

    private fun normalizeHighlight(value: JsonElement, index: Int, events: Map<String, Event>): Highlight {
        val obj = value as? JsonObject ?: JsonObject(emptyMap())
        val eventId = obj.pick("eventId", "event", "matchId")?.stringValue()
        val event = eventId?.let(events::get)
        val home = obj.pick("home", "homeTeam")?.let(::team) ?: event?.home
        val away = obj.pick("away", "awayTeam")?.let(::team) ?: event?.away
        val durationRaw = obj.pick("duration", "dur", "length")
        val duration = durationRaw?.stringValue() ?: durationRaw?.longValue()?.let(::formatDuration).orEmpty()
        return Highlight(
            id = obj.pick("id", "_id")?.stringValue() ?: index.toString(),
            eventId = eventId,
            title = obj.pick("title", "name")?.stringValue() ?: "Highlight",
            duration = duration,
            dateMs = parseDate(obj.pick("date", "publishedAt", "createdAt")),
            thumb = obj.pick("thumbnail", "thumb", "image", "poster")?.stringValue(),
            home = home,
            away = away,
            servers = streams(obj),
        )
    }

    private fun normalizeStatus(value: String?): String = when (value?.lowercase()?.replace(Regex("[\\s_-]"), "")) {
        "live", "inplay", "inprogress", "ongoing" -> "live"
        "upcoming", "scheduled", "pending", "notstarted" -> "upcoming"
        "finished", "ended", "completed", "ft", "past" -> "finished"
        else -> "upcoming"
    }

    private fun formatDuration(seconds: Long): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)

    private fun demoFeed(): Feed {
        val now = System.currentTimeMillis()
        fun team(name: String, initials: String, crest: String) = Team(name, crest, initials)
        fun event(id: Int, league: String, minutes: Int, home: Team, away: Team, count: Int): Event =
            Event(id.toString(), "Football", league, "live", now - minutes * 60_000L, "", home, away,
                (1..count).map { StreamServer("Server $it", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", if (it == 1) "HD" else "SD") })
        val events = listOf(
            event(1, "MLS", 25, team("Columbus", "CC", "b1"), team("Inter Miami", "IM", "b2"), 3),
            event(2, "CONCACAF Nations League", 67, team("Haiti", "HT", "f-ht"), team("Costa Rica", "CR", "f-cr"), 2),
            event(3, "WNBA", 12, team("Dream", "ATL", "b3"), team("Mystics", "WAS", "b4"), 1),
            event(4, "CONCACAF Nations League", 88, team("Curaçao", "CW", "f-cw"), team("Nicaragua", "NI", "f-ni"), 1),
            event(5, "CONCACAF Nations League", 43, team("Dominica", "DM", "f-dm"), team("Puerto Rico", "PR", "f-pr"), 2),
            event(6, "Asian Games", 5, team("India", "IN", "f-in"), team("Nepal", "NP", "f-np"), 1),
            Event("7", "Football", "MLS", "upcoming", now + 3 * 3_600_000L, "", team("LA Galaxy", "LA", "b4"), team("Seattle", "SEA", "b1"), emptyList()),
            Event("8", "Basketball", "WNBA", "upcoming", now + 9 * 3_600_000L, "", team("Liberty", "NY", "b2"), team("Aces", "LV", "b3"), emptyList()),
            Event("9", "Football", "CONCACAF Nations League", "finished", now - 26 * 3_600_000L, "", team("Canada", "CAN", "b3"), team("Jamaica", "JAM", "b1"), listOf(StreamServer("Server 1", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "HD"))),
        )
        val highlights = events.take(6).mapIndexed { index, event ->
            Highlight((index + 1).toString(), event.id, listOf("Full match highlights", "Key moments", "Top plays", "Best of the first half", "Opening overs recap", "Match recap")[index], "0${8 - index}:42", now - (index + 1) * 3_600_000L, null, event.home, event.away, event.servers.take(minOf(2, event.servers.size)))
        }
        return Feed(events, highlights)
    }
}

private val Int.absoluteValue: Int get() = if (this == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(this)