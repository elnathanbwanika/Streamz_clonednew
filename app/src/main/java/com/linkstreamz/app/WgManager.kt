package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgManager.kt
//
// Manages the per-app WireGuard VPN tunnel using GoBackend directly.
//
// Architecture:
//   • The wireguard-android library bundles its own VpnService implementation
//     (GoBackend$VpnService) declared in the AAR's AndroidManifest.xml, which
//     Gradle merges automatically.  We do NOT need a custom VpnService.
//   • GoBackend.setState(UP) internally starts GoBackend$VpnService, builds
//     the TUN interface, and starts the wireguard-go runtime.
//   • We hold the GoBackend instance here and tear it down on stop().
//
// Server integration:
//   The VPN config lives on the SAME server as the rest of the app content
//   (CONTENT_BASE_URL in NetworkConfig.kt — the GitHub Pages repo).
//   No separate URL constant is needed.
//
//   The app tries the following lookup order:
//     1. content.json "vpn" key — { "active": true, "configUrl": "…/wg0.conf" }
//        OR  { "active": true, "config": "[Interface]\n…" }  (inline .conf)
//     2. $CONTENT_BASE_URL/vpn/wg0.conf  (raw .conf file at a fixed path)
//
//   If the "vpn" section is absent, active=false, or all fetches fail,
//   the app routes traffic normally (and any device-level VPN the user
//   has connected continues to work without interference).
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Tunnel connection states observed by the UI (optional). */
enum class WgTunnelState { STOPPED, CONNECTING, CONNECTED, ERROR }

object WgManager {

    private const val TAG = "WgManager"

    /** Request code used in MainActivity.onActivityResult for VPN permission. */
    const val VPN_PERMISSION_REQUEST_CODE = 0xA55

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Plain OkHttp — no proxy — just for fetching the config file.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _tunnelState = MutableStateFlow(WgTunnelState.STOPPED)
    val tunnelState: StateFlow<WgTunnelState> = _tunnelState.asStateFlow()

    @Volatile private var pendingConfigText: String? = null
    @Volatile private var appContext: Context?        = null

    // Active backend — kept so we can tear down the tunnel.
    @Volatile private var goBackend: GoBackend?  = null
    @Volatile private var activeTunnel: Tunnel?  = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call from [MainActivity.onCreate].
     * Fetches the VPN config from the same server as the rest of the app and
     * starts the tunnel if one is active.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            try {
                AppLogger.d(TAG, "Initialising WireGuard VPN manager")
                val configText = fetchConfig() ?: run {
                    AppLogger.d(TAG, "No active VPN config — using normal routing")
                    return@launch
                }
                pendingConfigText = configText
                AppLogger.d(TAG, "VPN config ready (${configText.length} bytes), requesting permission")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    requestPermissionThenStart(context)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "VPN init error — falling back to normal routing: ${e.message}")
                Log.w(TAG, "VPN init failed", e)
            }
        }
    }

    /**
     * Call from [MainActivity.onActivityResult] with the VPN permission result.
     */
    fun onVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val ctx = appContext ?: return
            val cfg = pendingConfigText ?: return
            AppLogger.d(TAG, "VPN permission granted — starting tunnel")
            startTunnel(ctx, cfg)
        } else {
            AppLogger.d(TAG, "VPN permission denied — normal routing")
        }
    }

    /** Explicitly stop the tunnel (e.g. from settings or on app exit). */
    fun stop() {
        scope.launch { tearDownTunnel() }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun requestPermissionThenStart(context: Context) {
        val permIntent = VpnService.prepare(context)
        if (permIntent == null) {
            // Permission already granted — start immediately.
            val cfg = pendingConfigText ?: return
            startTunnel(context.applicationContext, cfg)
        } else {
            if (context is Activity) {
                context.startActivityForResult(permIntent, VPN_PERMISSION_REQUEST_CODE)
            } else {
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(permIntent)
            }
        }
    }

    /**
     * Starts the WireGuard tunnel on a coroutine (IO dispatcher).
     * GoBackend internally starts GoBackend$VpnService (declared in the AAR
     * manifest), builds the TUN interface, and routes traffic through it.
     */
    private fun startTunnel(context: Context, configText: String) {
        scope.launch {
            _tunnelState.value = WgTunnelState.CONNECTING
            try {
                val wgCfg = buildWgConfig(context, configText)
                if (wgCfg == null) {
                    AppLogger.e(TAG, "Failed to parse WireGuard config — VPN not started")
                    _tunnelState.value = WgTunnelState.ERROR
                    return@launch
                }

                // GoBackend takes the applicationContext; it uses it to call
                // context.startService(Intent(context, GoBackend$VpnService::class.java))
                // and to load the native wg-go shared library.
                val backend = GoBackend(context.applicationContext)
                goBackend = backend

                val tunnel = object : Tunnel {
                    override fun getName() = "streamz"
                    override fun onStateChange(newState: Tunnel.State) {
                        Log.d(TAG, "Tunnel state → $newState")
                        _tunnelState.value = when (newState) {
                            Tunnel.State.UP   -> {
                                AppLogger.d(TAG, "WireGuard tunnel UP — VPN connected ✓")
                                WgTunnelState.CONNECTED
                            }
                            Tunnel.State.DOWN -> {
                                AppLogger.d(TAG, "WireGuard tunnel DOWN")
                                WgTunnelState.STOPPED
                            }
                            else -> WgTunnelState.CONNECTING
                        }
                    }
                }
                activeTunnel = tunnel

                AppLogger.d(TAG, "Calling GoBackend.setState(UP)…")
                backend.setState(tunnel, Tunnel.State.UP, wgCfg)
                AppLogger.d(TAG, "GoBackend.setState(UP) returned")

            } catch (e: Exception) {
                AppLogger.e(TAG, "WireGuard tunnel failed: ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "WireGuard tunnel failed", e)
                _tunnelState.value = WgTunnelState.ERROR
            }
        }
    }

    private fun tearDownTunnel() {
        try {
            val t = activeTunnel
            val b = goBackend
            if (t != null && b != null) {
                b.setState(t, Tunnel.State.DOWN, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error tearing down tunnel: ${e.message}")
        } finally {
            activeTunnel = null
            goBackend    = null
            _tunnelState.value = WgTunnelState.STOPPED
        }
    }

    // ── Config fetch ──────────────────────────────────────────────────────────

    /**
     * Fetches the WireGuard config from the app's existing content server.
     *
     * Lookup order:
     *  1. content.json  "vpn" key   — same file the rest of the app uses.
     *       { "active": true, "configUrl": "https://…/wg0.conf" }
     *       { "active": true, "config": "[Interface]\n…"  }   (inline)
     *  2. $CONTENT_BASE_URL/vpn/wg0.conf  — fixed side-file path.
     *
     * Returns the raw .conf text, or null if VPN is disabled / unavailable.
     */
    private fun fetchConfig(): String? {
        // ── Step 1: check content.json "vpn" key ──────────────────────────────
        AppLogger.d(TAG, "Checking content.json for VPN config (url: $CONTENT_JSON_URL)")
        val contentJsonText = try {
            http.newCall(Request.Builder().url(CONTENT_JSON_URL).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "content.json fetch failed: ${e.message}")
            null
        }

        if (contentJsonText != null) {
            val confFromJson = parseVpnFromContentJson(contentJsonText)
            if (confFromJson != null) return confFromJson
        }

        // ── Step 2: try fixed side-file path ──────────────────────────────────
        val sideFileUrl = "$CONTENT_BASE_URL/vpn/wg0.conf"
        AppLogger.d(TAG, "Trying side-file: $sideFileUrl")
        return try {
            http.newCall(Request.Builder().url(sideFileUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.d(TAG, "Side-file returned HTTP ${resp.code} — no VPN config found")
                    null
                } else {
                    val body = resp.body?.string()?.trim()
                    if (body != null && body.startsWith("[Interface]", ignoreCase = true)) {
                        AppLogger.d(TAG, "Found raw WireGuard config at side-file path")
                        body
                    } else {
                        AppLogger.d(TAG, "Side-file exists but is not a valid .conf — skipping VPN")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "Side-file fetch failed: ${e.message} — skipping VPN")
            null
        }
    }

    /**
     * Parses the "vpn" key out of the content.json text.
     *
     * Supported shapes:
     *   { "vpn": { "active": true, "configUrl": "https://…/wg0.conf" } }
     *   { "vpn": { "active": true, "config": "[Interface]\n…"        } }
     *
     * Returns the raw .conf text, or null if absent / inactive.
     */
    private fun parseVpnFromContentJson(contentJsonText: String): String? {
        return try {
            val root = JSONObject(contentJsonText)
            if (!root.has("vpn")) {
                AppLogger.d(TAG, "content.json has no \"vpn\" key")
                return null
            }
            val vpn = root.getJSONObject("vpn")
            if (!vpn.optBoolean("active", false)) {
                AppLogger.d(TAG, "content.json vpn.active=false — VPN disabled")
                return null
            }

            // Inline config takes priority over configUrl
            val inline = vpn.optString("config", "").trim()
            if (inline.startsWith("[Interface]", ignoreCase = true)) {
                AppLogger.d(TAG, "Using inline WireGuard config from content.json")
                return inline
            }

            // configUrl — fetch the actual .conf
            val configUrl = vpn.optString("configUrl", "").trim()
            if (configUrl.isBlank()) {
                AppLogger.e(TAG, "content.json vpn has no configUrl and no inline config")
                return null
            }
            AppLogger.d(TAG, "Fetching .conf from configUrl: $configUrl")
            http.newCall(Request.Builder().url(configUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.e(TAG, "configUrl fetch returned HTTP ${resp.code}")
                    null
                } else {
                    val body = resp.body?.string()?.trim()
                    if (body != null && body.startsWith("[Interface]", ignoreCase = true)) {
                        AppLogger.d(TAG, "WireGuard config fetched from configUrl ✓")
                        body
                    } else {
                        AppLogger.e(TAG, "configUrl response is not a valid WireGuard .conf")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse vpn section: ${e.message}")
            null
        }
    }

    // ── Config builder ────────────────────────────────────────────────────────

    /**
     * Parse the raw .conf text into a [com.wireguard.config.Config].
     * Sets includeApplication so only THIS app's traffic goes through the tunnel.
     */
    private fun buildWgConfig(context: Context, configText: String): com.wireguard.config.Config? {
        return try {
            val parsed = WgConfig.parse(configText) ?: run {
                AppLogger.e(TAG, "WgConfig.parse returned null — invalid .conf?")
                return null
            }

            val ifaceBuilder = com.wireguard.config.Interface.Builder()
            ifaceBuilder.parsePrivateKey(parsed.iface.privateKey)
            for (addr in parsed.iface.addresses) ifaceBuilder.parseAddresses(addr)
            for (d   in parsed.iface.dns)       ifaceBuilder.parseDnsServers(d)
            if (parsed.iface.mtu > 0) ifaceBuilder.parseMtu(parsed.iface.mtu.toString())
            // Per-app routing: only THIS app's traffic is tunnelled.
            ifaceBuilder.includeApplication(context.packageName)

            val peerBuilder = com.wireguard.config.Peer.Builder()
            peerBuilder.parsePublicKey(parsed.peer.publicKey)
            parsed.peer.presharedKey?.let { peerBuilder.parsePreSharedKey(it) }
            peerBuilder.parseEndpoint(parsed.peer.endpoint)
            for (ip in parsed.peer.allowedIps) peerBuilder.parseAllowedIPs(ip)
            if (parsed.peer.persistentKeepalive > 0) {
                peerBuilder.parsePersistentKeepalive(parsed.peer.persistentKeepalive.toString())
            }

            com.wireguard.config.Config.Builder()
                .setInterface(ifaceBuilder.build())
                .addPeer(peerBuilder.build())
                .build()

        } catch (e: Exception) {
            AppLogger.e(TAG, "Config build error: ${e.message}")
            Log.e(TAG, "Config build error", e)
            null
        }
    }
}
