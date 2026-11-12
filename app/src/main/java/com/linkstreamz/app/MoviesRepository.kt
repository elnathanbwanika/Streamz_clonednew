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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// Remote feed state
// ─────────────────────────────────────────────────────────────────────────────

sealed class MediaFeedState {
    data object Loading : MediaFeedState()
    data class Ready(val movies: List<Movie>, val series: List<Series>) : MediaFeedState()
    data object Error : MediaFeedState()
}

sealed class ChannelFeedState {
    data object Loading : ChannelFeedState()
    data class Ready(val channels: List<Channel>) : ChannelFeedState()
    data object Error : ChannelFeedState()
}

// ─────────────────────────────────────────────────────────────────────────────
// MoviesRepository – fetches movies + series from the server.
//
// Primary source:  $CONTENT_BASE_URL/movies.json  (dedicated endpoint)
// Fallback source: $CONTENT_JSON_URL              (root content.json "movies" + "series" keys)
//
// Falls back to bundled sample data when the server is unreachable so the UI
// is never empty on first launch.
// ─────────────────────────────────────────────────────────────────────────────

class MoviesRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val httpClient = HttpClients.newBuilder()
        .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<MediaFeedState>(MediaFeedState.Loading)
    val state: StateFlow<MediaFeedState> = _state.asStateFlow()

    // Empty until the first successful fetch from the server
    val movies: List<Movie> get() = (_state.value as? MediaFeedState.Ready)?.movies ?: emptyList()
    val series: List<Series> get() = (_state.value as? MediaFeedState.Ready)?.series ?: emptyList()

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                load(silent = _state.value is MediaFeedState.Ready)
                delay(REFRESH_MS)
            }
        }
    }

    suspend fun load(silent: Boolean = false) {
        if (!silent) _state.value = MediaFeedState.Loading
        try {
            val (movies, series) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchMoviesAndSeries() }
            _state.value = MediaFeedState.Ready(movies, series)
        } catch (e: Exception) {
            if (!silent || _state.value !is MediaFeedState.Ready) {
                _state.value = MediaFeedState.Error
            }
        }
    }

    private fun fetchMoviesAndSeries(): Pair<List<Movie>, List<Series>> {
        // Try dedicated movies.json first
        try {
            val req = okhttp3.Request.Builder().url("$CONTENT_BASE_URL/movies.json").get().build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: throw IllegalStateException("Empty body")
                val root = json.parseToJsonElement(body) as? JsonObject
                    ?: throw IllegalStateException("Not a JSON object")
                return parseMoviesFromRoot(root)
            }
        } catch (_: Exception) { /* fall through to root content.json */ }

        // Fallback: parse movies/series sections from root content.json
        val req = okhttp3.Request.Builder().url(CONTENT_JSON_URL).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
        val body = resp.body?.string() ?: throw IllegalStateException("Empty body")
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw IllegalStateException("Not a JSON object")
        return parseMoviesFromRoot(root)
    }

    private fun parseMoviesFromRoot(root: JsonObject): Pair<List<Movie>, List<Series>> {
        val moviesArray = root["movies"] as? JsonArray ?: JsonArray(emptyList())
        val seriesArray = root["series"] as? JsonArray ?: JsonArray(emptyList())

        val movies = moviesArray.mapIndexedNotNull { i, el -> parseMovie(el as? JsonObject ?: return@mapIndexedNotNull null, i) }
        val series = seriesArray.mapIndexedNotNull { i, el -> parseSeries(el as? JsonObject ?: return@mapIndexedNotNull null, i) }
        return movies to series
    }

    private fun parseMovie(obj: JsonObject, index: Int): Movie? {
        val videoUrl = obj.str("video", "videoUrl", "url", "stream") ?: return null
        return Movie(
            id    = obj.str("id") ?: "mv-${index + 1}",
            title = obj.str("title", "name") ?: return null,
            genre = obj.str("genre", "category") ?: "",
            year  = obj.int("year") ?: 2024,
            vj    = obj.str("vj")?.takeIf { it.isNotBlank() },
            translated = obj.bool("translated") ?: false,
            thumbUrl   = obj.str("thumb", "thumbnail", "poster", "image") ?: "https://picsum.photos/seed/${index}/400/250",
            videoUrl   = videoUrl,
            duration   = obj.str("dur", "duration") ?: "",
        )
    }

    private fun parseSeries(obj: JsonObject, index: Int): Series? {
        val seasonsArray = obj["seasons"] as? JsonArray ?: return null
        val seasons = seasonsArray.mapIndexedNotNull { si, sel ->
            val sObj = sel as? JsonObject ?: return@mapIndexedNotNull null
            val eps = (sObj["episodes"] as? JsonArray)?.mapIndexedNotNull { ei, eel ->
                val eObj = eel as? JsonObject ?: return@mapIndexedNotNull null
                Episode(
                    n        = eObj.int("n", "number", "episode") ?: (ei + 1),
                    title    = eObj.str("title", "name") ?: "Episode ${ei + 1}",
                    thumbUrl = eObj.str("thumb", "thumbnail", "poster") ?: "https://picsum.photos/seed/${index}_${si}_$ei/200/120",
                    videoUrl = eObj.str("video", "videoUrl", "url", "stream") ?: "",
                    duration = eObj.str("dur", "duration") ?: "",
                )
            } ?: emptyList()
            Season(number = sObj.int("number", "season") ?: (si + 1), episodes = eps)
        }
        return Series(
            id         = obj.str("id") ?: "sr-${index + 1}",
            title      = obj.str("title", "name") ?: return null,
            genre      = obj.str("genre", "category") ?: "",
            year       = obj.int("year") ?: 2024,
            vj         = obj.str("vj")?.takeIf { it.isNotBlank() },
            translated = obj.bool("translated") ?: false,
            thumbUrl   = obj.str("thumb", "thumbnail", "poster", "image") ?: "https://picsum.photos/seed/sr${index}/600/340",
            seasons    = seasons,
        )
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────
    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
    private fun JsonObject.int(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
    private fun JsonObject.bool(vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.let {
            it.contentOrNull?.toBooleanStrictOrNull() ?: it.contentOrNull?.let { v -> v == "1" || v == "true" }
        }}
}

// ─────────────────────────────────────────────────────────────────────────────
// ChannelsRepository – fetches channels from the server.
//
// Primary source:  $CONTENT_BASE_URL/channels.json
// Fallback source: $CONTENT_JSON_URL "channels" key
//
// Falls back to bundled sample data when the server is unreachable.
// ─────────────────────────────────────────────────────────────────────────────

class ChannelsRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val httpClient = HttpClients.newBuilder()
        .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<ChannelFeedState>(ChannelFeedState.Loading)
    val state: StateFlow<ChannelFeedState> = _state.asStateFlow()

    // Empty until the first successful fetch from the server
    val channels: List<Channel> get() = (_state.value as? ChannelFeedState.Ready)?.channels ?: emptyList()

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                load(silent = _state.value is ChannelFeedState.Ready)
                delay(REFRESH_MS)
            }
        }
    }

    suspend fun load(silent: Boolean = false) {
        if (!silent) _state.value = ChannelFeedState.Loading
        try {
            val channels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchChannels() }
            _state.value = ChannelFeedState.Ready(channels)
        } catch (e: Exception) {
            if (!silent || _state.value !is ChannelFeedState.Ready) {
                _state.value = ChannelFeedState.Error
            }
        }
    }

    private fun fetchChannels(): List<Channel> {
        // Try dedicated channels.json first
        try {
            val req = okhttp3.Request.Builder().url("$CONTENT_BASE_URL/channels.json").get().build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: throw IllegalStateException("Empty body")
                val root = json.parseToJsonElement(body)
                return parseChannelsElement(root)
            }
        } catch (_: Exception) { /* fall through */ }

        // Fallback: parse channels section from root content.json
        val req = okhttp3.Request.Builder().url(CONTENT_JSON_URL).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
        val body = resp.body?.string() ?: throw IllegalStateException("Empty body")
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw IllegalStateException("Not a JSON object")
        return parseChannelsElement(root["channels"] ?: JsonArray(emptyList()))
    }

    private fun parseChannelsElement(element: JsonElement): List<Channel> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { i, el -> parseChannel(el as? JsonObject ?: return@mapIndexedNotNull null, i) }
    }

    private fun parseChannel(obj: JsonObject, index: Int): Channel? {
        val videoUrl = obj.str("video", "videoUrl", "url", "stream") ?: return null
        return Channel(
            id       = obj.str("id") ?: "ch-${index + 1}",
            title    = obj.str("title", "name", "channel") ?: return null,
            category = obj.str("cat", "category", "type") ?: "General",
            country  = obj.str("country") ?: "Uganda",
            initials = obj.str("initials", "abbr", "code")
                ?: (obj.str("title", "name") ?: "??").split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
            logo     = obj.str("logo", "logoUrl", "logo_url", "channelLogo", "thumbnail", "thumb", "thumbnailUrl", "image", "imageUrl", "icon", "poster", "badge"),
            color1   = obj.str("c1", "color1", "primaryColor") ?: "#1A1A2E",
            color2   = obj.str("c2", "color2", "secondaryColor") ?: "#16213E",
            program  = obj.str("program", "currentShow", "now", "schedule") ?: "Now Live",
            videoUrl = videoUrl,
        )
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────
    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
}
