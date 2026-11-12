package com.linkstreamz.app

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// HttpClients.kt
//
// Single OkHttp client factory for all app traffic. The proxy selector and
// authenticator consult TunnelManager dynamically, so the same client instance
// is direct before/after the tunnel and proxied only while it is up.
//
// Clients created with [newBuilder] share the same connection pool and proxy
// configuration; [onTunnelStateChanged] evicts that pool so stale direct
// connections are never reused through the tunnel (and vice versa).
// ─────────────────────────────────────────────────────────────────────────────

/** Adds per-session Basic proxy credentials when the proxy answers 407. */
class TunnelProxyAuthenticator(
    private val stateProvider: () -> TunnelProxyEndpoint?,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 407) return null
        if (response.request.header("Proxy-Authorization") != null) return null

        val endpoint = stateProvider() ?: return null
        val proxy: Proxy = route?.proxy ?: return null
        val address = proxy.address() as? InetSocketAddress ?: return null
        if (address.port != endpoint.port) return null
        if (!TunnelProxySelector.isLoopback(address.hostString)) return null

        return response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(endpoint.user, endpoint.password))
            .build()
    }
}

object HttpClients {
    private val pool = ConnectionPool(
        maxIdleConnections = 8,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES,
    )

    private val proxySelector = TunnelProxySelector(
        stateProvider = { TunnelManager.currentProxy() },
        failClosed = FAIL_CLOSED,
    )

    private val proxyAuthenticator = TunnelProxyAuthenticator(
        stateProvider = { TunnelManager.currentProxy() },
    )

    /** Tunnel-aware client used by every app request. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .proxySelector(proxySelector)
        .proxyAuthenticator(proxyAuthenticator)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Client that always connects directly. Used for tunnel bootstrap work such
     * as fetching the VPN config itself.
     */
    val direct: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(FETCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .build()

    fun newBuilder(): OkHttpClient.Builder = client.newBuilder()

    /** Called on every tunnel up/down/config change. */
    fun onTunnelStateChanged() {
        pool.evictAll()
    }
}
