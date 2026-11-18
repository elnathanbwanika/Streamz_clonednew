package com.linkstreamz.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Ordering used by the player and all app HTTP clients. When an external VPN is
 * the active network we ask for IPv4 addresses first: some IP-bound stream
 * tokens are only valid for the IPv4 exit and Android otherwise prefers IPv6.
 */
object DnsOrdering {
    fun order(addresses: List<InetAddress>, preferIpv4: Boolean): List<InetAddress> {
        if (!preferIpv4) return addresses
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        if (ipv4.isEmpty()) return addresses
        val ipv6 = addresses.filterIsInstance<Inet6Address>()
        val other = addresses.filter { it !is Inet4Address && it !is Inet6Address }
        return ipv4 + ipv6 + other
    }
}

class AdaptiveDns(
    private val preferIpv4: () -> Boolean,
    private val upstream: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        DnsOrdering.order(upstream.lookup(hostname), preferIpv4())
}

/**
 * Waits for Android to publish a default network before the first stream
 * request. On a cold start the process can be alive before the external VPN's
 * network is selected; a request made at that moment can fail or poison the
 * resolver cache until the app is backgrounded and resumed.
 */
object NetworkBootstrap {
    private const val COLD_START_WAIT_MS = 1_500L
    private const val TAG = "Network"

    private val lock = Any()
    private val ready = CompletableDeferred<Unit>()

    @Volatile private var appContext: Context? = null
    @Volatile private var defaultNetwork: Network? = null
    @Volatile private var externalVpn = false
    @Volatile private var callbackRegistered = false

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        HttpClients.init(app)
        if (callbackRegistered) return

        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        cm.activeNetwork?.let { network ->
            val caps = cm.getNetworkCapabilities(network)
            synchronized(lock) {
                defaultNetwork = network
                externalVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                synchronized(lock) {
                    defaultNetwork = network
                    externalVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    if (!ready.isCompleted) ready.complete(Unit)
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                synchronized(lock) {
                    defaultNetwork = network
                    externalVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    if (!ready.isCompleted) ready.complete(Unit)
                }
            }

            override fun onLost(network: Network) {
                synchronized(lock) {
                    if (defaultNetwork == network) {
                        defaultNetwork = null
                        externalVpn = false
                    }
                }
            }
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
            callbackRegistered = true
        } catch (_: Exception) {
            // No callback available: callers still fall back to activeNetwork checks.
        }
    }

    fun hasDefaultNetwork(context: Context? = null): Boolean {
        val ctx = context ?: appContext ?: return false
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun awaitDefaultNetwork(timeoutMs: Long = COLD_START_WAIT_MS): Boolean {
        if (hasDefaultNetwork()) return true
        val signalled = withTimeoutOrNull(timeoutMs) {
            ready.await()
            true
        } ?: false
        return signalled && hasDefaultNetwork()
    }

    fun isExternalVpnActive(): Boolean {
        val ctx = appContext ?: return externalVpn
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return externalVpn
        val network = cm.activeNetwork ?: return externalVpn
        return cm.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    /**
     * Run before creating the player's HTTP client and data source factory.
     * Waits for the default network, evicts stale pooled sockets, clears any
     * app-owned HTTP cache and logs the tunnel state that will be used.
     */
    suspend fun prepareForStream(context: Context, bypassTunnel: Boolean): Boolean {
        if (appContext == null) init(context)

        val networkReady = awaitDefaultNetwork()
        val vpn = isExternalVpnActive()
        val configured = WgManager.isConfigured()
        val phase = TunnelManager.phase.value
        val tunnelLabel = when {
            !configured -> "not configured"
            phase != TunnelPhase.UP -> "not up ($phase)"
            else -> "up"
        }
        AppLogger.d(
            TAG,
            "stream start: network=${if (networkReady) "ready" else "timeout"} " +
                "externalVpn=$vpn bypassTunnel=$bypassTunnel tunnel=$tunnelLabel",
        )

        HttpClients.onNetworkChanged()
        clearHttpCache(context)
        return networkReady
    }

    fun clearHttpCache(context: Context) {
        val dirs = listOf("http", "okhttp", "HttpCache")
        for (name in dirs) {
            runCatching { File(context.cacheDir, name).deleteRecursively() }
        }
        AppLogger.d(TAG, "http cache cleared")
    }
}
