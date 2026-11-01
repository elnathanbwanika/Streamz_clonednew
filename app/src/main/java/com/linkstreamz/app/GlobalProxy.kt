package com.linkstreamz.app

import android.content.Context
import android.util.Log
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Route
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// GlobalProxy.kt
//
// Single source of truth for the app-wide SOCKS5 / HTTP proxy that routes ALL
// network traffic through your WireGuard server.
//
// How to use:
//   1. Your WireGuard server runs a SOCKS5 proxy (e.g. microsocks on port 1080).
//   2. In Settings → VPN / Proxy, enter Host, Port, and optional credentials.
//   3. Tap Apply → GlobalProxy.save() persists config and activates it.
//   4. Restart the app — all OkHttpClient instances are then built via
//      GlobalProxy.buildClient(), routing through the SOCKS5 proxy.
//   5. WebView (TokenMinter) is reconfigured immediately via Android ProxyController.
//
// All streaming, API, and token-minting traffic appears to originate from
// your WireGuard server's IP address.
// ─────────────────────────────────────────────────────────────────────────────

/** Immutable snapshot of the proxy configuration. */
data class AppProxyConfig(
    val host: String,
    val port: Int,
    val username: String  = "",
    val password: String  = "",
    val type: Proxy.Type  = Proxy.Type.SOCKS,   // SOCKS5 by default
)

object GlobalProxy {

    private const val TAG      = "GlobalProxy"
    private const val PREFS    = "global_proxy_prefs"
    private const val KEY_HOST = "proxy_host"
    private const val KEY_PORT = "proxy_port"
    private const val KEY_USER = "proxy_user"
    private const val KEY_PASS = "proxy_pass"
    private const val KEY_TYPE = "proxy_type"   // "SOCKS" or "HTTP"

    @Volatile private var _config: AppProxyConfig? = null

    /** Current active config. Null = direct connection (no proxy). */
    val config: AppProxyConfig? get() = _config

    /** True when a proxy is configured and active. */
    val isEnabled: Boolean get() = _config != null

    // ── Persistence ───────────────────────────────────────────────────────────

    /**
     * Load persisted proxy config from SharedPrefs.
     * Call once from MainActivity.onCreate() BEFORE any network call.
     */
    fun load(context: Context) {
        val p    = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = p.getString(KEY_HOST, "")?.trim() ?: ""
        val port = p.getInt(KEY_PORT, 1080)
        val user = p.getString(KEY_USER, "") ?: ""
        val pass = p.getString(KEY_PASS, "") ?: ""
        val type = if (p.getString(KEY_TYPE, "SOCKS") == "HTTP") Proxy.Type.HTTP else Proxy.Type.SOCKS
        _config  = if (host.isNotBlank()) AppProxyConfig(host, port, user, pass, type) else null
        Log.d(TAG, if (_config != null) "Loaded proxy: $host:$port ($type)" else "No proxy configured")
    }

    /**
     * Save and immediately apply a new proxy config.
     * Pass null to disable (direct connection).
     */
    fun save(context: Context, cfg: AppProxyConfig?) {
        _config = cfg
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (cfg != null) {
            ed.putString(KEY_HOST, cfg.host)
            ed.putInt(KEY_PORT, cfg.port)
            ed.putString(KEY_USER, cfg.username)
            ed.putString(KEY_PASS, cfg.password)
            ed.putString(KEY_TYPE, cfg.type.name)
            Log.d(TAG, "Saved proxy: ${cfg.host}:${cfg.port} (${cfg.type})")
        } else {
            ed.remove(KEY_HOST).remove(KEY_PORT).remove(KEY_USER).remove(KEY_PASS).remove(KEY_TYPE)
            Log.d(TAG, "Proxy cleared — direct connection")
        }
        ed.apply()
    }

    // ── OkHttp integration ────────────────────────────────────────────────────

    /**
     * Build an OkHttpClient routed through the current proxy.
     * Use this everywhere an OkHttpClient is created (PlayerEngine, EventsRepository,
     * NewPipe). Returns a direct client when no proxy is configured.
     */
    fun buildClient(
        connectTimeoutSec: Long = 15,
        readTimeoutSec:    Long = 30,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)

        val cfg = _config
        if (cfg != null) {
            builder.proxy(Proxy(cfg.type, InetSocketAddress(cfg.host, cfg.port)))
            if (cfg.username.isNotBlank() || cfg.password.isNotBlank()) {
                val credential = Credentials.basic(cfg.username, cfg.password)
                builder.proxyAuthenticator { _: Route?, response: Response ->
                    if (response.request.header("Proxy-Authorization") != null) null
                    else response.request.newBuilder().header("Proxy-Authorization", credential).build()
                }
            }
            Log.d(TAG, "OkHttpClient: proxy ${cfg.host}:${cfg.port}")
        } else {
            Log.d(TAG, "OkHttpClient: direct (no proxy)")
        }

        return builder.build()
    }

    // ── WebView proxy (TokenMinter) ───────────────────────────────────────────

    /**
     * Returns an [androidx.webkit.ProxyConfig] for the current proxy, or null if
     * no proxy is configured.
     *
     * Apply using [androidx.webkit.ProxyController] immediately after [save].
     */
    fun buildWebViewProxyConfig(): androidx.webkit.ProxyConfig? {
        val cfg = _config ?: return null
        val scheme = if (cfg.type == Proxy.Type.SOCKS) "socks5" else "http"
        val auth   = if (cfg.username.isNotBlank()) "${cfg.username}:${cfg.password}@" else ""
        val url    = "$scheme://$auth${cfg.host}:${cfg.port}"
        return androidx.webkit.ProxyConfig.Builder().addProxyRule(url).build()
    }
}
