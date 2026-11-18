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
    
    // We create a custom client for the proxy with redirects enabled, and default browser headers
    private val proxyClient = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun start() {
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
        
        if (path == "master.m3u8") {
            val request = buildRequest(sourceUrl, null)
            val response = executeRequest(request)
            if (response == null) {
                call.respond(HttpStatusCode.BadGateway, "Failed to fetch master")
                return
            }
            if (!response.isSuccessful) {
                // Forward the real HTTP error code — don't wrap in 502.
                // This lets ExoPlayer / the retry logic see the actual error (403, 404, etc.).
                val realStatus = HttpStatusCode.fromValue(response.code)
                val body = try { response.body?.string() ?: "" } finally { response.close() }
                AppLogger.e("Proxy", "Master fetch failed: ${response.code} from $sourceUrl")
                call.respond(realStatus, body)
                return
            }
            val body = response.body?.string() ?: ""
            if (body.contains("#EXT-X-STREAM-INF")) {
                val rewritten = HlsParser.rewritePlaylist(
                    body, sourceUrl, "http://127.0.0.1:$localPort/$sessionToken/variant.m3u8"
                )
                call.respondText(rewritten, ContentType.parse("application/vnd.apple.mpegurl"))
            } else {
                handleVariant(call, sourceUrl)
            }
        } else if (path == "variant.m3u8" && urlParam != null) {
            handleVariant(call, urlParam)
        } else if (urlParam != null) {
            val range = call.request.headers[HttpHeaders.Range]
            val request = buildRequest(urlParam, range)
            val response = executeRequest(request)
            if (response == null) {
                call.respond(HttpStatusCode.BadGateway, "Segment failed")
                return
            }

            AppLogger.d("Proxy", "Streaming segment: $urlParam (Status: ${response.code})")

            val bytes = response.body?.bytes() ?: ByteArray(0)
            val ct = response.body?.contentType()
                ?.let { ContentType.parse(it.toString()) }
                ?: ContentType.Application.OctetStream
            val statusCode = HttpStatusCode.fromValue(response.code)

            // Forward Content-Range and Accept-Ranges headers if present (for 206 support)
            response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
            response.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }
            response.close()

            call.respondBytes(bytes, ct, statusCode)
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing url parameter")
        }
    }
    
    private fun buildRequest(url: String, range: String?): Request {
        val requestBuilder = Request.Builder().url(url)
        val requestHeaders = PlayerRequestHeaders.build(headers).toMutableMap()
        requestHeaders["Accept"] = "*/*"
        requestHeaders["Accept-Encoding"] = "identity"
        if (range != null) {
            requestHeaders["Range"] = range
        }
        requestBuilder.headers(requestHeaders.toHeaders())
        return requestBuilder.build()
    }

    private suspend fun handleVariant(call: ApplicationCall, variantUrl: String) {
        val timeline = timelines.getOrPut(variantUrl) {
            HlsTimeline(proxyClient, variantUrl, headers, delaySeconds).apply {
                start()
                scope.launch {
                    syncing.collect { isSyncing.value = it }
                }
            }
        }
        
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
                val call = proxyClient.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        AppLogger.e("Proxy", "Request failed: ${request.url}", e)
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        AppLogger.d("Proxy", "Response: ${response.code} from ${request.url}")
                        if (!response.isSuccessful && response.code !in 200..299) {
                            AppLogger.e("Proxy", "Bad response: ${response.code}")
                        }
                        if (cont.isActive) cont.resume(response)
                    }
                })
            }
            if (response != null && (response.isSuccessful || response.code in 400..499)) {
                return response
            }
            response?.close()
            attempt++
            if (attempt <= maxRetries) {
                AppLogger.d("Proxy", "Retrying ($attempt/$maxRetries) in ${delayMs}ms: ${request.url}")
                kotlinx.coroutines.delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(8_000L)
            }
        }
        return null
    }

    companion object {
        const val MAX_SEGMENT_RETRIES = 3
    }
}
