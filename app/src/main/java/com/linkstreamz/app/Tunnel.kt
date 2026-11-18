package com.linkstreamz.app

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import linktunnel.Linktunnel
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

// ─────────────────────────────────────────────────────────────────────────────
// Tunnel.kt
//
// In-process userspace WireGuard tunnel (wireguard-go + gvisor netstack) plus
// the loopback authenticated HTTP proxy that fronts it.
//
// Nothing here uses the Android system VPN service: no TUN device is created, no other
// app is affected and no key icon is shown. Only traffic explicitly sent to the
// local proxy is tunnelled.
//
// The tunnel is only published as UP after a real WireGuard handshake and a
// health probe through the netstack. Until then the app fails open (direct).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * When true the app refuses to fall back to a direct connection while it is
 * supposed to be tunnelled. Default is fail-open so the player never stalls on
 * a broken tunnel. This is the single switch for that behaviour.
 */
const val FAIL_CLOSED = false

/** How long the first content/event request may wait for the tunnel to come up. */
const val TUNNEL_WAIT_MS = 4_000L

data class TunnelProxyEndpoint(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
)

/** Connection phases reported to the rest of the app. */
enum class TunnelPhase { DOWN, CONNECTING, UP }

/**
 * Snapshot of wireguard-go's first peer, parsed from the UAPI. Contains no
 * secrets or config.
 */
data class TunnelStatus(
    val up: Boolean,
    val handshakeSec: Long,
    val handshakeNsec: Long,
    val txBytes: Long,
    val rxBytes: Long,
    val endpoint: String,
    val listenPort: Int,
) {
    /** Age of the last handshake, or null when no handshake has happened yet. */
    fun handshakeAgeMs(nowMs: Long = System.currentTimeMillis()): Long? {
        if (handshakeSec <= 0L) return null
        return nowMs - handshakeSec * 1000L
    }

    fun hasFreshHandshake(
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = HANDSHAKE_MAX_AGE_MS,
    ): Boolean {
        val age = handshakeAgeMs(nowMs) ?: return false
        // Tolerate small clock skew; reject absurdly future timestamps too.
        return age <= maxAgeMs && age >= -CLOCK_SKEW_MS
    }

    companion object {
        const val HANDSHAKE_MAX_AGE_MS = 3 * 60 * 1000L
        private const val CLOCK_SKEW_MS = 60 * 1000L
    }
}

/** Swappable tunnel backend so the engine can be replaced in one place. */
interface TunnelEngine {
    fun start(config: WgConfig, mtu: Int): Boolean
    fun stop()
    val isUp: Boolean
    fun proxy(): TunnelProxyEndpoint?
    fun status(): TunnelStatus? = null
    fun probe(url: String, timeoutMs: Int): Int = -1
    fun rebind(): Boolean = false
    fun httpGet(url: String, timeoutMs: Int): String = ""
}

/**
 * Userspace WireGuard backend. [WgConfig] is parsed and its endpoint resolved
 * on the normal network before the tunnel starts; the wireguard-go UAPI text and
 * netstack addresses are then handed to the Go library.
 *
 * Endpoint host names are always resolved with the system resolver (never
 * through our own tunnel). The last good IP is cached so a transient DNS
 * failure while switching networks does not tear the tunnel down.
 */
class GoTunnelEngine : TunnelEngine {

    private data class CachedEndpoint(val host: String, val endpoint: WgEndpoint)

    @Volatile private var cachedEndpoint: CachedEndpoint? = null

    // The AAR ships arm64-v8a and armeabi-v7a only. On other ABIs (e.g. x86
    // emulators) the tunnel simply stays down and the app fails open.
    private val abiSupported: Boolean by lazy {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }
    }

    override fun start(config: WgConfig, mtu: Int): Boolean {
        if (!abiSupported) return false
        return try {
            val parsed = WgEndpoint.parse(config.peer.endpoint) ?: return false
            val resolved = parsed.resolveHost()
                ?: cachedEndpoint
                    ?.takeIf { it.host.equals(parsed.host, ignoreCase = true) && it.endpoint.port == parsed.port }
                    ?.endpoint
                ?: return false
            cachedEndpoint = CachedEndpoint(parsed.host, resolved)

            val safeMtu = mtu.coerceIn(MIN_MTU, MAX_MTU)
            val ipc = config.toIpc(resolved)
            val dns = config.dnsCsv().ifBlank { "1.1.1.1" }

            if (BuildConfig.DEBUG) {
                AppLogger.d("Tunnel", "endpoint ${resolved.format()} mtu $safeMtu")
            }
            Linktunnel.stop()
            Linktunnel.start(ipc, config.interfaceAddressesCsv(), dns, safeMtu.toLong())
            Linktunnel.isUp()
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) AppLogger.e("Tunnel", "start failed", e)
            try {
                Linktunnel.stop()
            } catch (_: Throwable) {
            }
            false
        }
    }

    override fun stop() {
        try {
            Linktunnel.stop()
        } catch (_: Throwable) {
        }
    }

    override val isUp: Boolean
        get() = try {
            Linktunnel.isUp()
        } catch (_: Throwable) {
            false
        }

    override fun proxy(): TunnelProxyEndpoint? {
        if (!isUp) return null
        val port = Linktunnel.proxyPort().toInt()
        if (port <= 0) return null
        return TunnelProxyEndpoint(
            host = Linktunnel.proxyHost(),
            port = port,
            user = Linktunnel.proxyUser(),
            password = Linktunnel.proxyPassword(),
        )
    }

    override fun status(): TunnelStatus? = try {
        val o = JSONObject(Linktunnel.status())
        TunnelStatus(
            up = o.optBoolean("up", false),
            handshakeSec = o.optLong("handshakeSec", 0L),
            handshakeNsec = o.optLong("handshakeNsec", 0L),
            txBytes = o.optLong("txBytes", 0L),
            rxBytes = o.optLong("rxBytes", 0L),
            endpoint = o.optString("endpoint", ""),
            listenPort = o.optInt("listenPort", 0),
        )
    } catch (_: Throwable) {
        null
    }

    override fun probe(url: String, timeoutMs: Int): Int = try {
        Linktunnel.probe(url, timeoutMs.toLong()).toInt()
    } catch (_: Throwable) {
        -1
    }

    override fun rebind(): Boolean = try {
        Linktunnel.rebind()
        true
    } catch (_: Throwable) {
        false
    }

    override fun httpGet(url: String, timeoutMs: Int): String = try {
        Linktunnel.httpGet(url, timeoutMs.toLong())
    } catch (_: Throwable) {
        ""
    }

    private companion object {
        const val MIN_MTU = 576
        const val MAX_MTU = 65535
    }
}

/**
 * Owns the process-wide tunnel and publishes state changes to HTTP clients.
 *
 * State machine:
 *   DOWN -> CONNECTING (device created) -> UP (fresh handshake + probe) -> DOWN
 *
 * A failed handshake or health probe moves back to DOWN, hides the proxy (so
 * traffic fails open to direct) and reconnects with exponential backoff.
 */
object TunnelManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var engine: TunnelEngine = GoTunnelEngine()

    @Volatile
    private var proxy: TunnelProxyEndpoint? = null

    private val _phase = MutableStateFlow(TunnelPhase.DOWN)
    val phase: StateFlow<TunnelPhase> = _phase.asStateFlow()

    @Volatile private var activeConfig: WgConfig? = null
    @Volatile private var activeMtu: Int = 0
    @Volatile private var generation: Int = 0
    @Volatile private var retryAttempt: Int = 0
    @Volatile private var externalVpnActive: Boolean = false

    private var monitorJob: Job? = null
    private var retryJob: Job? = null

    // Tunables are internal so unit tests can use short timeouts.
    @Volatile internal var connectTimeoutMs = 12_000L
    @Volatile internal var probeRetryMs = 500L
    @Volatile internal var healthIntervalMs = 30_000L
    @Volatile internal var retryBaseMs = 2_000L
    @Volatile internal var maxRetryDelayMs = 60_000L
    @Volatile internal var probeTimeoutMs = 5_000
    @Volatile internal var probeUrl = "https://1.1.1.1/cdn-cgi/trace"
    @Volatile internal var egressDiagnostics = true

    private const val EGRESS_URL = "https://api.ipify.org"
    private const val TAG = "Tunnel"

    fun isUp(): Boolean = _phase.value == TunnelPhase.UP && proxy != null

    fun currentProxy(): TunnelProxyEndpoint? = if (isUp()) proxy else null

    /**
     * Starts (or restarts) the device. Returns true when the device was
     * created; the tunnel only becomes UP later, after handshake + probe.
     */
    @Synchronized
    fun start(config: WgConfig, mtu: Int, externalVpn: Boolean = false): Boolean {
        retryAttempt = 0
        externalVpnActive = externalVpn
        return startInternal(config, mtu)
    }

    @Synchronized
    private fun startInternal(config: WgConfig, mtu: Int): Boolean {
        generation++
        val gen = generation

        retryJob?.cancel()
        retryJob = null
        monitorJob?.cancel()
        monitorJob = null

        try {
            engine.stop()
        } catch (_: Throwable) {
        }
        proxy = null
        activeConfig = config
        activeMtu = mtu
        setPhase(TunnelPhase.CONNECTING, "starting")

        val ok = try {
            engine.start(config, mtu)
        } catch (_: Throwable) {
            false
        }
        if (!ok) {
            activeConfig = null
            setPhase(TunnelPhase.DOWN, "engine start failed")
            if (BuildConfig.DEBUG) AppLogger.d(TAG, "fail-open")
            return false
        }

        proxy = engine.proxy()
        if (proxy == null) {
            activeConfig = null
            try {
                engine.stop()
            } catch (_: Throwable) {
            }
            setPhase(TunnelPhase.DOWN, "proxy unavailable")
            return false
        }

        if (BuildConfig.DEBUG) AppLogger.d(TAG, "proxy listening")
        launchMonitor(gen)
        return true
    }

    @Synchronized
    fun stop() {
        generation++
        retryAttempt = 0
        retryJob?.cancel()
        retryJob = null
        monitorJob?.cancel()
        monitorJob = null
        activeConfig = null
        activeMtu = 0
        try {
            engine.stop()
        } catch (_: Throwable) {
        }
        setPhase(TunnelPhase.DOWN, "stopped")
    }

    /**
     * Called on any network change (Wi-Fi/cellular/VPN/MTU/capabilities).
     * Re-opens the UDP bind, re-resolves the endpoint and forces a fresh
     * handshake by restarting the device with the new MTU.
     */
    @Synchronized
    fun onNetworkChanged(mtu: Int, externalVpn: Boolean) {
        externalVpnActive = externalVpn
        val config = activeConfig ?: return
        if (BuildConfig.DEBUG) {
            AppLogger.d(TAG, "network changed (vpn=$externalVpn mtu=$mtu)")
        }
        // Best effort: re-open sockets before the full restart below.
        try {
            engine.rebind()
        } catch (_: Throwable) {
        }
        retryAttempt = 0
        activeMtu = mtu
        startInternal(config, mtu)
    }

    /** Waits until the tunnel is UP or [timeoutMs] elapses. */
    suspend fun awaitUp(timeoutMs: Long = TUNNEL_WAIT_MS): Boolean {
        if (isUp()) return true
        if (timeoutMs <= 0) return false
        return withTimeoutOrNull(timeoutMs) {
            phase.first { it == TunnelPhase.UP }
            true
        } ?: false
    }

    private fun launchMonitor(gen: Int) {
        monitorJob = scope.launch {
            val started = System.currentTimeMillis()
            var udpLogged = false

            // ── Handshake gating ─────────────────────────────────────────
            while (isActive && gen == generation && _phase.value == TunnelPhase.CONNECTING) {
                val status = engine.status()
                val latency = engine.probe(probeUrl, probeTimeoutMs)
                val age = status?.handshakeAgeMs()

                if (BuildConfig.DEBUG) {
                    AppLogger.d(
                        TAG,
                        "probe=${latency}ms handshake=${age?.let { "${it / 1000}s" } ?: "none"} " +
                            "tx=${status?.txBytes ?: -1} rx=${status?.rxBytes ?: -1}",
                    )
                }

                if (latency >= 0 && status != null && status.hasFreshHandshake()) {
                    promoteUp(gen, status)
                    break
                }

                val elapsed = System.currentTimeMillis() - started
                if (!udpLogged && externalVpnActive && status != null && age == null && elapsed > 3_000L) {
                    udpLogged = true
                    if (BuildConfig.DEBUG) {
                        AppLogger.d(TAG, "udp-blocked-suspected (external VPN active, no handshake)")
                    }
                }

                if (elapsed >= connectTimeoutMs) {
                    failAndRetry(gen, "handshake timeout")
                    return@launch
                }
                delay(probeRetryMs)
            }

            if (!isActive || gen != generation || _phase.value != TunnelPhase.UP) return@launch

            // ── Health loop ──────────────────────────────────────────────
            while (isActive && gen == generation) {
                delay(healthIntervalMs)
                if (gen != generation) return@launch

                val status = engine.status()
                val latency = engine.probe(probeUrl, probeTimeoutMs)
                if (latency < 0 || status == null || !status.hasFreshHandshake()) {
                    if (BuildConfig.DEBUG) {
                        AppLogger.d(
                            TAG,
                            "health probe failed (probe=$latency handshake=${status?.handshakeAgeMs() ?: "none"})",
                        )
                    }
                    failAndRetry(gen, "health probe failed")
                    return@launch
                }
            }
        }
    }

    private fun promoteUp(gen: Int, status: TunnelStatus) {
        if (gen != generation) return
        val endpoint = engine.proxy()
        if (endpoint == null) {
            failAndRetry(gen, "proxy unavailable")
            return
        }
        proxy = endpoint
        retryAttempt = 0
        setPhase(TunnelPhase.UP, "handshake+probe ok")
        if (BuildConfig.DEBUG) {
            AppLogger.d(
                TAG,
                "tunnel up endpoint=${status.endpoint.ifBlank { "?" }} " +
                    "tx=${status.txBytes} rx=${status.rxBytes}",
            )
        }
        logEgress()
    }

    private fun logEgress() {
        if (!BuildConfig.DEBUG || !egressDiagnostics) return
        scope.launch {
            val throughTunnel = engine.httpGet(EGRESS_URL, probeTimeoutMs)
            val direct = try {
                val req = okhttp3.Request.Builder().url(EGRESS_URL).build()
                HttpClients.direct.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string()?.trim() else null
                }
            } catch (_: Throwable) {
                null
            }
            AppLogger.d(
                TAG,
                "egress tunnel=${throughTunnel.ifBlank { "?" }} direct=${direct ?: "?"}",
            )
        }
    }

    @Synchronized
    private fun failAndRetry(gen: Int, reason: String) {
        if (gen != generation) return
        setPhase(TunnelPhase.DOWN, reason)
        scheduleRetry(gen)
    }

    private fun scheduleRetry(gen: Int) {
        if (gen != generation) return
        retryJob?.cancel()
        retryAttempt += 1
        val shift = (retryAttempt - 1).coerceIn(0, 5)
        val delayMs = (retryBaseMs shl shift).coerceAtMost(maxRetryDelayMs)
        if (BuildConfig.DEBUG) AppLogger.d(TAG, "retry in ${delayMs}ms")
        retryJob = scope.launch {
            delay(delayMs)
            if (gen != generation) return@launch
            val config = activeConfig ?: return@launch
            retryJob = null
            startInternal(config, activeMtu)
        }
    }

    private fun setPhase(newPhase: TunnelPhase, reason: String) {
        if (newPhase != TunnelPhase.UP) proxy = null
        val old = _phase.value
        if (old == newPhase) return
        _phase.value = newPhase
        HttpClients.onTunnelStateChanged()
        if (BuildConfig.DEBUG) AppLogger.d(TAG, "state $old -> $newPhase ($reason)")
    }

    /** Test seam so the engine can be swapped without touching callers. */
    internal fun setEngineForTest(newEngine: TunnelEngine) {
        generation++
        retryJob?.cancel()
        retryJob = null
        monitorJob?.cancel()
        monitorJob = null
        engine = newEngine
        proxy = newEngine.proxy()
        activeConfig = null
        activeMtu = 0
        retryAttempt = 0
        _phase.value = if (newEngine.isUp) TunnelPhase.UP else TunnelPhase.DOWN
    }
}

/**
 * Routes only the app's own HTTP(S) traffic through the local proxy while the
 * tunnel is up. Loopback is always direct (the local HLS proxy lives there).
 */
class TunnelProxySelector(
    private val stateProvider: () -> TunnelProxyEndpoint?,
    private val failClosed: Boolean = FAIL_CLOSED,
) : ProxySelector() {

    override fun select(uri: URI): List<Proxy> {
        val host = uri.host ?: return DIRECT
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return DIRECT
        if (isLoopback(host)) return DIRECT

        val endpoint = stateProvider()
        if (endpoint != null) {
            if (BuildConfig.DEBUG) AppLogger.d("Tunnel", "route tunnel $host")
            return listOf(
                Proxy(Proxy.Type.HTTP, InetSocketAddress(endpoint.host, endpoint.port))
            )
        }
        if (BuildConfig.DEBUG) {
            AppLogger.d("Tunnel", if (failClosed) "route blocked $host" else "route direct $host")
        }
        return if (failClosed) FAILED else DIRECT
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        // Fail-open by design; nothing to do.
    }

    companion object {
        val DIRECT: List<Proxy> = listOf(Proxy.NO_PROXY)

        /** A guaranteed-dead proxy used when failing closed. */
        val FAILED: List<Proxy> =
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))

        fun isLoopback(host: String): Boolean {
            val h = host.lowercase().removePrefix("[").removeSuffix("]")
            return h == "localhost" || h == "::1" || h == "0.0.0.0" || h.startsWith("127.")
        }
    }
}
