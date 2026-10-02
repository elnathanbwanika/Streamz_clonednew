package com.linkstreamz.app

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class StreamProxyServer(
    private val client: OkHttpClient,
    private val sourceUrl: String,
    private val headers: Map<String, String>,
    private val delaySeconds: Int
) {
    private val sessionToken = UUID.randomUUID().toString()
    private var server: ApplicationEngine? = null
    var localPort: Int = 0
        private set
    
    val localMasterUrl: String
        get() = "http://127.0.0.1:$localPort/$sessionToken/master.m3u8"

    val isSyncing = MutableStateFlow(false)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val timelines = ConcurrentHashMap<String, HlsTimeline>()

    fun start() {
        // Pre-allocate a free port so we know it before Ktor starts.
        // Called from withContext(Dispatchers.IO) in PlayerEngine — already on IO thread.
        val freePort = java.net.ServerSocket(0).use { it.localPort }
        localPort = freePort
        server = embeddedServer(CIO, port = freePort, host = "127.0.0.1") {
            routing {
                get("/{token}/{path...}") {
                    val token = call.parameters["token"]
                    if (token != sessionToken) {
                        call.respond(HttpStatusCode.Forbidden, "Invalid token")
                        return@get
                    }
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    handleRequest(call, path)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        scope.cancel()
        timelines.values.forEach { it.stop() }
        server?.stop(0, 0)
    }

    private suspend fun handleRequest(call: ApplicationCall, path: String) {
        val urlParam = call.request.queryParameters["url"]
        AppLogger.d("Proxy", "handleRequest: path=$path urlParam=$urlParam")
        
        if (path == "master.m3u8") {
            // Fetch master playlist
            val request = Request.Builder().url(sourceUrl).headers(headers.toHeaders()).build()
            val response = executeRequest(request)
            if (response == null || !response.isSuccessful) {
                call.respond(HttpStatusCode.BadGateway, "Failed to fetch master")
                return
            }
            val body = response.body?.string() ?: ""
            if (body.contains("#EXT-X-STREAM-INF")) {
                // It's a master playlist. Rewrite variants.
                val rewritten = HlsParser.rewritePlaylist(
                    body, sourceUrl, "http://127.0.0.1:$localPort/$sessionToken/variant.m3u8"
                )
                call.respondText(rewritten, ContentType.parse("application/vnd.apple.mpegurl"))
            } else {
                // It's a media playlist directly
                handleVariant(call, sourceUrl)
            }
        } else if (path == "variant.m3u8" && urlParam != null) {
            handleVariant(call, urlParam)
        } else if (urlParam != null) {
            // Segment or key
            val range = call.request.headers[HttpHeaders.Range]
            val requestBuilder = Request.Builder().url(urlParam).headers(headers.toHeaders())
            if (range != null) {
                requestBuilder.header("Range", range)
            }
            val request = requestBuilder.build()
            val response = executeRequest(request)
            if (response == null) {
                call.respond(HttpStatusCode.BadGateway, "Segment failed")
                return
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val code = HttpStatusCode.fromValue(response.code)
            call.respondBytes(bytes, status = code)
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing url parameter")
        }
    }

    private suspend fun handleVariant(call: ApplicationCall, variantUrl: String) {
        val timeline = timelines.getOrPut(variantUrl) {
            HlsTimeline(client, variantUrl, headers, delaySeconds).apply {
                start()
                // Mirror the syncing state
                scope.launch {
                    syncing.collect { isSyncing.value = it }
                }
            }
        }
        
        // Wait if still syncing and it's a live stream
        if (timeline.syncing.value && delaySeconds > 0) {
            delay(1000)
        }
        
        val playlist = timeline.generateDelayedPlaylist("http://127.0.0.1:$localPort/$sessionToken/segment")
        call.respondText(playlist, ContentType.parse("application/vnd.apple.mpegurl"))
    }

    private suspend fun executeRequest(request: Request, maxRetries: Int = MAX_SEGMENT_RETRIES): Response? {
        var attempt = 0
        var delayMs = 1_000L
        while (attempt <= maxRetries) {
            val response = suspendCancellableCoroutine<Response?> { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) cont.resume(response)
                    }
                })
            }
            // Treat a null (network error) or server-error response as retryable
            if (response != null && (response.isSuccessful || response.code in 400..499)) {
                // 4xx errors are the server's definitive answer — don't retry those
                return response
            }
            attempt++
            if (attempt <= maxRetries) {
                AppLogger.d("Proxy", "Request failed (attempt $attempt/$maxRetries), retrying in ${delayMs}ms: ${request.url}")
                kotlinx.coroutines.delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(8_000L)
            }
        }
        AppLogger.e("Proxy", "All $maxRetries retries exhausted for ${request.url}")
        return null
    }

    companion object {
        /** Retry each segment/playlist fetch up to this many times before giving up. */
        const val MAX_SEGMENT_RETRIES = 3
    }
}
