package com.linkstreamz.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The stream currently open in the player, used by the Logs overlay test. */
data class ActiveStream(
    val label: String,
    val url: String,
    val headers: Map<String, String>,
    val bypassTunnel: Boolean,
)

object StreamDiagnostics {
    val active = MutableStateFlow<ActiveStream?>(null)

    fun set(target: ActiveStream?) {
        active.value = target
    }
}

/**
 * Sequential cold-start stream probe. All output goes through AppLogger and is
 * already redacted. URLs, query strings, tokens and header values are never
 * logged; only status, timing, resolved IP/family, redirect hosts and a
 * first-bytes classification.
 */
object StreamTestRunner {
    private const val TAG = "StreamTest"
    private const val TIMEOUT_MS = 15_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val autoRan = AtomicBoolean(false)
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    fun runAutoOnce(context: Context, stream: ActiveStream?) {
        val value = stream ?: return
        if (!isHttp(value.url)) return
        if (!autoRan.compareAndSet(false, true)) return
        scope.launch {
            NetworkBootstrap.awaitDefaultNetwork()
            mutex.withLock { execute(context, value, "cold-start auto") }
        }
    }

    fun runManual(context: Context, stream: ActiveStream?) {
        val value = stream ?: return
        if (!isHttp(value.url)) {
            AppLogger.d(TAG, "manual test skipped: current stream is local")
            return
        }
        scope.launch {
            if (mutex.tryLock()) {
                try {
                    execute(context, value, "manual")
                } finally {
                    mutex.unlock()
                }
            } else {
                AppLogger.d(TAG, "manual test skipped: another test is running")
            }
        }
    }

    private suspend fun execute(context: Context, stream: ActiveStream, reason: String) {
        _running.value = true
        try {
            AppLogger.d(TAG, "start ($reason) server=${stream.label.ifBlank { "unnamed" }}")
            HttpClients.evictConnections()
            val baseHeaders = PlayerRequestHeaders.build(stream.headers)
            val v1 = buildV1(stream, baseHeaders)
            probe("V1", stream.url, v1.client, baseHeaders, v1.dns)
            probe("V4", stream.url, v1.client, PlayerRequestHeaders.withoutOriginReferer(stream.headers), v1.dns)

            val v2dns = CapturingDns(Dns.SYSTEM)
            val v2 = OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .dns(v2dns)
                .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            probe(
                "V2",
                stream.url,
                v2,
                mapOf("User-Agent" to PlayerRequestHeaders.VLC_USER_AGENT),
                v2dns,
            )

            val v3dns = CapturingDns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        Dns.SYSTEM.lookup(hostname).filterIsInstance<Inet4Address>()
                }
            )
            val v3 = v1.builder.dns(v3dns).build()
            // V3 must resolve and connect again; otherwise it would reuse V1's pool.
            HttpClients.evictConnections()
            probe("V3", stream.url, v3, baseHeaders, v3dns)

            probeHttpUrlConnection(stream.url, baseHeaders)

            val v1Egress = egress(v1.client)
            val v2Egress = egress(v2)
            AppLogger.d(TAG, "egress V1 ipv4=${v1Egress.first ?: "failed"} ipv6=${v1Egress.second ?: "failed"}")
            AppLogger.d(TAG, "egress V2 ipv4=${v2Egress.first ?: "failed"} ipv6=${v2Egress.second ?: "failed"}")
            AppLogger.d(TAG, "done ($reason)")
        } finally {
            _running.value = false
        }
    }

    private data class V1(
        val client: OkHttpClient,
        val builder: OkHttpClient.Builder,
        val dns: CapturingDns,
    )

    private fun buildV1(stream: ActiveStream, headers: Map<String, String>): V1 {
        val dns = CapturingDns(AdaptiveDns(preferIpv4 = { NetworkBootstrap.isExternalVpnActive() }))
        val builder = HttpClients.streamBuilder(stream.bypassTunnel)
            .dns(dns)
            .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        return V1(builder.build(), builder, dns)
    }

    private fun probe(
        variant: String,
        url: String,
        client: OkHttpClient,
        headers: Map<String, String>,
        dns: CapturingDns,
    ) {
        val started = System.currentTimeMillis()
        try {
            dns.clear()
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - started
                val redirects = redirectHosts(response)
                val prefix = readPrefix(response)
                val address = dns.first()
                logResult(
                    variant,
                    response.code,
                    elapsed,
                    address,
                    redirects,
                    response.header("Content-Type"),
                    prefix,
                )
            }
        } catch (e: Exception) {
            logFailure(variant, started, dns.first(), e)
        }
    }

    private fun probeHttpUrlConnection(url: String, headers: Map<String, String>) {
        val started = System.currentTimeMillis()
        val redirects = mutableListOf<String>()
        var current = url
        try {
            var responseCode = -1
            var contentType: String? = null
            var prefix = "empty"
            var count = 0
            while (count < 6) {
                count++
                val connection = URL(current).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.connect()
                responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    val host = LogRedactor.redirectHost(location)
                    if (host != null) redirects += host
                    if (location.isNullOrBlank()) {
                        connection.disconnect()
                        break
                    }
                    current = URL(URL(current), location).toString()
                    connection.disconnect()
                    continue
                }
                contentType = connection.contentType
                val stream = if (responseCode >= 400) connection.errorStream else connection.inputStream
                val bytes = stream?.use { input ->
                    val buffer = ByteArray(32)
                    val read = input.read(buffer)
                    if (read > 0) buffer.copyOf(read) else null
                }
                prefix = classifyPrefix(bytes)
                connection.disconnect()
                break
            }
            val address = runCatching { InetAddress.getAllByName(URL(current).host).firstOrNull() }.getOrNull()
            logResult("V5", responseCode, System.currentTimeMillis() - started, address, redirects, contentType, prefix)
        } catch (e: Exception) {
            logFailure("V5", started, null, e)
        }
    }

    private fun egress(client: OkHttpClient): Pair<String?, String?> {
        fun fetch(url: String): String? = try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()?.take(80)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
        return fetch("https://api.ipify.org") to fetch("https://api64.ipify.org")
    }

    private fun redirectHosts(response: okhttp3.Response): List<String> {
        val hosts = mutableListOf<String>()
        var prior = response.priorResponse
        while (prior != null) {
            prior.request.url.host.let { if (it.isNotBlank()) hosts += it }
            prior = prior.priorResponse
        }
        return hosts.asReversed()
    }

    private fun readPrefix(response: okhttp3.Response): String {
        val body = response.body ?: return "empty"
        if (body.contentLength() == 0L) return "empty"
        return try {
            body.byteStream().use { input ->
                val buffer = ByteArray(32)
                val read = input.read(buffer)
                if (read <= 0) "empty" else classifyPrefix(buffer.copyOf(read))
            }
        } catch (_: Exception) {
            "unreadable"
        }
    }

    private fun classifyPrefix(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return "empty"
        val text = bytes.toString(Charsets.ISO_8859_1)
        return when {
            text.startsWith("#EXTM3U") -> "hls-playlist"
            text.trimStart().startsWith("<") -> "markup"
            bytes.all { it == 0.toByte() } -> "binary"
            bytes.all { b -> b.toInt() in 32..126 || b.toInt() in 9..13 } -> "text"
            else -> "binary"
        }
    }

    private fun logResult(
        variant: String,
        code: Int,
        elapsedMs: Long,
        address: InetAddress?,
        redirects: List<String>,
        contentType: String?,
        prefix: String,
    ) {
        AppLogger.d(
            TAG,
            "$variant status=$code time=${elapsedMs}ms " +
                "ip=${address?.hostAddress ?: "unknown"} family=${family(address)} " +
                "redirects=${if (redirects.isEmpty()) "none" else redirects.joinToString(",")} " +
                "contentType=${contentType ?: "unknown"} firstBytes=$prefix",
        )
    }

    private fun logFailure(variant: String, started: Long, address: InetAddress?, error: Throwable) {
        val message = LogRedactor.redact(
            "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
        ).take(160)
        AppLogger.d(
            TAG,
            "$variant error after ${System.currentTimeMillis() - started}ms " +
                "ip=${address?.hostAddress ?: "unknown"} family=${family(address)} error=$message",
        )
    }

    private fun family(address: InetAddress?): String = when (address) {
        is Inet4Address -> "IPv4"
        is Inet6Address -> "IPv6"
        else -> "unknown"
    }

    private fun isHttp(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private class CapturingDns(private val upstream: Dns) : Dns {
        private val addresses = mutableListOf<InetAddress>()

        override fun lookup(hostname: String): List<InetAddress> {
            val result = upstream.lookup(hostname)
            synchronized(addresses) {
                addresses.clear()
                addresses.addAll(result)
            }
            return result
        }

        fun clear() {
            synchronized(addresses) { addresses.clear() }
        }

        fun first(): InetAddress? = synchronized(addresses) { addresses.firstOrNull() }
    }
}
