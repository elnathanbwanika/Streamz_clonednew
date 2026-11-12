package com.linkstreamz.app

import android.os.Build
import linktunnel.Linktunnel
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
// ─────────────────────────────────────────────────────────────────────────────

/**
 * When true the app refuses to fall back to a direct connection while it is
 * supposed to be tunnelled. Default is fail-open so the player never stalls on
 * a broken tunnel. This is the single switch for that behaviour.
 */
const val FAIL_CLOSED = false

data class TunnelProxyEndpoint(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
)

/** Swappable tunnel backend so the engine can be replaced in one place. */
interface TunnelEngine {
    fun start(config: WgConfig): Boolean
    fun stop()
    val isUp: Boolean
    fun proxy(): TunnelProxyEndpoint?
}

/**
 * Userspace WireGuard backend. [WgConfig] is parsed and its endpoint resolved
 * on the normal network before the tunnel starts; the wireguard-go UAPI text and
 * netstack addresses are then handed to the Go library.
 */
class GoTunnelEngine : TunnelEngine {

    // The AAR ships arm64-v8a and armeabi-v7a only. On other ABIs (e.g. x86
    // emulators) the tunnel simply stays down and the app fails open.
    private val abiSupported: Boolean by lazy {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }
    }

    override fun start(config: WgConfig): Boolean {
        if (!abiSupported) return false
        return try {
            val endpoint = WgEndpoint.parse(config.peer.endpoint)?.resolveHost() ?: return false
            val ipc = config.toIpc(endpoint)
            val dns = config.dnsCsv().ifBlank { "1.1.1.1" }
            Linktunnel.stop()
            Linktunnel.start(ipc, config.interfaceAddressesCsv(), dns, config.iface.mtu.toLong())
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
}

/** Owns the process-wide tunnel and publishes state changes to HTTP clients. */
object TunnelManager {
    @Volatile
    private var engine: TunnelEngine = GoTunnelEngine()

    @Volatile
    private var proxy: TunnelProxyEndpoint? = null

    @Volatile
    private var up: Boolean = false

    fun isUp(): Boolean = up

    fun currentProxy(): TunnelProxyEndpoint? = if (up) proxy else null

    /** Returns true when the tunnel is up. Never throws. */
    fun start(config: WgConfig): Boolean {
        stopInternal(notify = false)
        val ok = try {
            engine.start(config)
        } catch (_: Throwable) {
            false
        }
        up = ok
        proxy = if (ok) engine.proxy() else null
        if (!ok) {
            try {
                engine.stop()
            } catch (_: Throwable) {
            }
        }
        HttpClients.onTunnelStateChanged()
        if (BuildConfig.DEBUG) {
            AppLogger.d("Tunnel", if (ok) "tunnel up" else "fail-open")
            if (ok) proxy?.let { AppLogger.d("Tunnel", "proxy listening") }
        }
        return ok
    }

    fun stop() {
        stopInternal(notify = true)
    }

    private fun stopInternal(notify: Boolean) {
        try {
            engine.stop()
        } catch (_: Throwable) {
        }
        val wasUp = up
        up = false
        proxy = null
        if (notify) {
            HttpClients.onTunnelStateChanged()
            if (wasUp && BuildConfig.DEBUG) AppLogger.d("Tunnel", "tunnel down")
        }
    }

    /** Test seam so the engine can be swapped without touching callers. */
    internal fun setEngineForTest(newEngine: TunnelEngine) {
        engine = newEngine
        up = newEngine.isUp
        proxy = newEngine.proxy()
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
            return listOf(
                Proxy(Proxy.Type.HTTP, InetSocketAddress(endpoint.host, endpoint.port))
            )
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
