package com.linkstreamz.app

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Route
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/**
 * Global proxy selector for routing all app traffic through VPN when available.
 * This should be registered once when the app starts.
 */
object ProxySelectorManager {
    private var currentVpnManager: VpnManager? = null
    private val defaultProxySelector: ProxySelector = ProxySelector.getDefault()

    /**
     * Set the VPN manager to use for determining proxy routing.
     */
    fun setVpnManager(vpnManager: VpnManager) {
        currentVpnManager = vpnManager
    }

    /**
     * Get the proxy selector that respects VPN state.
     * This is used by OkHttp clients and other networking libraries.
     */
    fun getProxySelector(): ProxySelector {
        return object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val vpnEndpoint = currentVpnManager?.getProxyEndpoint()

                return if (vpnEndpoint != null) {
                    try {
                        val parts = vpnEndpoint.split(":")
                        if (parts.size == 2) {
                            val host = parts[0]
                            val port = parts[1].toInt()
                            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                        } else {
                            defaultProxySelector.select(uri) ?: listOf(Proxy.NO_PROXY)
                        }
                    } catch (e: Exception) {
                        defaultProxySelector.select(uri) ?: listOf(Proxy.NO_PROXY)
                    }
                } else {
                    defaultProxySelector.select(uri) ?: listOf(Proxy.NO_PROXY)
                }
            }

            override fun connectFailed(
                uri: URI?,
                sockAddr: java.net.SocketAddress?,
                fail: java.io.IOException?
            ) {
                defaultProxySelector.connectFailed(uri, sockAddr, fail)
            }
        }
    }
}

/**
 * Global proxy authenticator for the VPN.
 * OkHttp's Authenticator must return a new Request with credentials added, or null to give up.
 */
object ProxyAuthenticator {
    private var currentCredentials: String? = null

    /**
     * Generates a new random credential for this session.
     */
    fun generateRandomCredential(): String {
        val randomValue = (0..1_000_000).random().toString()
        currentCredentials = "user:$randomValue"
        return currentCredentials ?: ""
    }

    /**
     * Returns an OkHttp Authenticator that injects Basic proxy credentials when challenged.
     */
    fun getAuthenticator(): Authenticator {
        val credential = currentCredentials?.let {
            val parts = it.split(":")
            if (parts.size == 2) Credentials.basic(parts[0], parts[1]) else null
        }
        return Authenticator { _: Route?, response: Response ->
            if (credential == null) return@Authenticator null
            // Avoid infinite retry loops
            if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }
}
