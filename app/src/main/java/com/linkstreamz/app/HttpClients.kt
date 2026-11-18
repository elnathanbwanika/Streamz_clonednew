package com.linkstreamz.app

import android.content.Context
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
// DNS is IPv4-first whenever the active network is an external VPN, because
// IP-bound stream tokens frequently only match the IPv4 exit.
//
// Clients created with [newBuilder] share the same connection pool and proxy
// configuration; [onTunnelStateChanged] / [onNetworkChanged] evict that pool so
// stale direct connections are never reused through a changed network.
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
    @Volatile private var appContext: Context? = null

    private val adaptiveDns = AdaptiveDns(preferIpv4 = { NetworkBootstrap.isExternalVpnActive() })

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

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Tunnel-aware client used by every app request. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .proxySelector(proxySelector)
        .proxyAuthenticator(proxyAuthenticator)
        .dns(adaptiveDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Client that always connects directly. Used for tunnel bootstrap work such
     * as fetching the VPN config itself.
     */
    val direct: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .proxy(Proxy.NO_PROXY)
        .dns(adaptiveDns)
        .connectTimeout(FETCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun newBuilder(): OkHttpClient.Builder = client.newBuilder()

    fun newDirectBuilder(): OkHttpClient.Builder = direct.newBuilder()

    /**
     * Builder for one playback session.
     *
     * The proxy decision is frozen at load time: when the in-app tunnel is not
     * UP (or this stream bypasses it), the player gets a NO_PROXY client and
     * stays direct even if the tunnel comes up seconds later. This prevents an
     * IP-bound token from being invalidated mid-playback by a route change.
     */
    fun streamBuilder(direct: Boolean): OkHttpClient.Builder =
        if (direct || !TunnelManager.isUp()) newDirectBuilder() else newBuilder()

    /** Fresh player client built after [NetworkBootstrap.prepareForStream]. */
    fun streamClient(direct: Boolean): OkHttpClient =
        streamBuilder(direct)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

    /** Evict pooled sockets after a network or tunnel change. */
    fun onNetworkChanged() {
        pool.evictAll()
    }

    /** Called on every tunnel up/down/config change. */
    fun onTunnelStateChanged() {
        pool.evictAll()
    }

    /** Drops pooled sockets before an explicit retry on a new connection. */
    fun evictConnections() {
        pool.evictAll()
    }

    /** The context is kept for callers that need cache diagnostics. */
    fun context(): Context? = appContext
}
