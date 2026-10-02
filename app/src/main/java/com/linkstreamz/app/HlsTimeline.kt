package com.linkstreamz.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class HlsSegment(
    val uri: String,
    val duration: Double,
    val title: String,
    val discontinuity: Boolean,
    val sequence: Long,
    val extras: List<String> // keys, maps, etc before the segment
)

class HlsTimeline(
    private val client: OkHttpClient,
    private val playlistUrl: String,
    private val headers: Map<String, String>,
    private val delaySeconds: Int
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isLive = true
    private var targetDuration = 10.0
    private var mediaSequence = 0L
    private val segments = mutableListOf<HlsSegment>()
    private val globalExtras = mutableListOf<String>()
    
    val syncing = MutableStateFlow(true)
    private var stopped = false

    companion object {
        /** Retry playlist fetches this many times before skipping a poll cycle. */
        const val PLAYLIST_FETCH_RETRIES = 3
        /** Live window: how many segments we expose to ExoPlayer at once. */
        const val LIVE_WINDOW_SEGMENTS = 8
    }

    fun start() {
        scope.launch {
            while (!stopped && isLive) {
                fetchAndMerge()
                if (!isLive) break
                delay((targetDuration * 1000 / 2).toLong().coerceAtLeast(1000L))
            }
        }
    }

    fun stop() {
        stopped = true
        scope.cancel()
    }

    private suspend fun fetchAndMerge() {
        val request = Request.Builder()
            .url(playlistUrl)
            .headers(headers.toHeaders())
            .build()

        var attempt = 0
        var delayMs = 500L
        var body: String? = null
        while (attempt <= PLAYLIST_FETCH_RETRIES) {
            body = suspendCancellableCoroutine { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) {
                            cont.resume(if (response.isSuccessful) response.body?.string() else null)
                        }
                    }
                })
            }
            if (body != null) break
            attempt++
            if (attempt <= PLAYLIST_FETCH_RETRIES) {
                AppLogger.d("Timeline", "Playlist fetch failed (attempt $attempt/$PLAYLIST_FETCH_RETRIES), retry in ${delayMs}ms")
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(4_000L)
            }
        }
        if (body == null) {
            AppLogger.e("Timeline", "All playlist fetch retries exhausted for $playlistUrl")
            return
        }

        parsePlaylist(body)
    }

    /** Exposed for unit tests — feeds raw playlist text directly into the timeline. */
    @Synchronized
    internal fun parsePlaylistForTest(body: String) = parsePlaylist(body)

    @Synchronized
    internal fun parsePlaylist(body: String) {
        val lines = body.lines()
        var currentDuration = 0.0
        var currentTitle = ""
        var isDiscontinuity = false
        var currentExtras = mutableListOf<String>()
        
        var seq = 0L
        val newSegments = mutableListOf<HlsSegment>()
        
        var hasEndList = false

        for (line in lines) {
            if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                targetDuration = line.substringAfter(":").toDoubleOrNull() ?: targetDuration
            } else if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                seq = line.substringAfter(":").toLongOrNull() ?: seq
            } else if (line.startsWith("#EXTINF:")) {
                val parts = line.substringAfter(":").split(",", limit = 2)
                currentDuration = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                currentTitle = parts.getOrNull(1) ?: ""
            } else if (line.startsWith("#EXT-X-DISCONTINUITY")) {
                isDiscontinuity = true
                currentExtras.add(line)
            } else if (line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-MAP:") || line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) {
                currentExtras.add(line)
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                hasEndList = true
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val absoluteUri = URI(playlistUrl).resolve(line).toString()
                newSegments.add(
                    HlsSegment(
                        absoluteUri, currentDuration, currentTitle, isDiscontinuity, seq, currentExtras
                    )
                )
                seq++
                currentExtras = mutableListOf()
                isDiscontinuity = false
            } else if (line.startsWith("#EXT-X-") && !line.startsWith("#EXT-X-VERSION")) {
                if (newSegments.isEmpty() && segments.isEmpty()) {
                    globalExtras.add(line)
                }
            }
        }

        if (hasEndList) {
            isLive = false
        }

        // Merge new segments
        for (seg in newSegments) {
            if (segments.none { it.sequence == seg.sequence }) {
                segments.add(seg)
            }
        }

        // Bounded to ~3 minutes of history
        val maxHistory = (180 / targetDuration).toInt().coerceAtLeast(10)
        while (segments.size > maxHistory) {
            segments.removeAt(0)
        }

        // Check if we have enough delay
        if (isLive) {
            val totalDur = segments.sumOf { it.duration }
            if (totalDur >= delaySeconds) {
                syncing.value = false
            }
        } else {
            syncing.value = false
        }
    }

    @Synchronized
    fun generateDelayedPlaylist(localPrefix: String): String {
        if (segments.isEmpty()) return ""

        val result = java.lang.StringBuilder()
        result.append("#EXTM3U\n")
        result.append("#EXT-X-VERSION:3\n")
        result.append("#EXT-X-TARGETDURATION:${targetDuration.toInt()}\n")
        
        for (ext in globalExtras) {
            result.append(ext).append("\n")
        }

        val eligibleSegments = if (isLive && delaySeconds > 0) {
            var acc = 0.0
            val eligible = mutableListOf<HlsSegment>()
            for (i in segments.indices.reversed()) {
                acc += segments[i].duration
                if (acc > delaySeconds) {
                    // include from 0 to i
                    eligible.addAll(segments.take(i + 1))
                    break
                }
            }
            eligible
        } else {
            segments.toList()
        }

        if (eligibleSegments.isEmpty()) {
            val seq = segments.firstOrNull()?.sequence ?: 0L
            val emptyResult = java.lang.StringBuilder()
            emptyResult.append("#EXTM3U\n")
            emptyResult.append("#EXT-X-VERSION:3\n")
            emptyResult.append("#EXT-X-TARGETDURATION:${targetDuration.toInt()}\n")
            for (ext in globalExtras) {
                emptyResult.append(ext).append("\n")
            }
            emptyResult.append("#EXT-X-MEDIA-SEQUENCE:$seq\n")
            return emptyResult.toString()
        }

        // Keep last LIVE_WINDOW_SEGMENTS segments for live (8 = ~80s at 10s/seg for HD runway)
        val window = if (isLive) eligibleSegments.takeLast(LIVE_WINDOW_SEGMENTS) else eligibleSegments
        
        result.append("#EXT-X-MEDIA-SEQUENCE:${window.first().sequence}\n")
        
        for (seg in window) {
            for (ext in seg.extras) {
                if (ext.startsWith("#EXT-X-KEY:") || ext.startsWith("#EXT-X-MAP:")) {
                    val rewritten = ext.replace(Regex("""URI="([^"]+)"""")) { matchResult ->
                        val uri = matchResult.groupValues[1]
                        val absolute = URI(playlistUrl).resolve(uri).toString()
                        """URI="$localPrefix?url=${java.net.URLEncoder.encode(absolute, "UTF-8")}""""
                    }
                    result.append(rewritten).append("\n")
                } else {
                    result.append(ext).append("\n")
                }
            }
            result.append("#EXTINF:${seg.duration},${seg.title}\n")
            result.append("$localPrefix?url=${java.net.URLEncoder.encode(seg.uri, "UTF-8")}\n")
        }

        if (!isLive) {
            result.append("#EXT-X-ENDLIST\n")
        }

        return result.toString()
    }
}
