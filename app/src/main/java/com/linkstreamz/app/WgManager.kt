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
// Flow:
//   1. MainActivity calls WgManager.init(context) in onCreate().
//   2. WgManager fetches StreamConfig.VPN_CONFIG_URL from the server.
//      Server can respond with:
//        a) Raw .conf text starting with "[Interface]" — used directly.
//        b) JSON: {"active": true, "configUrl": "https://…/wg0.conf"}
//           If active=false (or absent) the VPN is skipped entirely.
//   3. Android VPN permission dialog is shown (once ever, stored by OS).
//   4. GoBackend.setState(tunnel, UP, config) is called on a background thread.
//      GoBackend starts its own VpnService, sets up the TUN, and routes traffic.
//   5. If no active config is found the app routes traffic normally, and any
//      device-level VPN the user has running continues to work.
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
     * Fetches the VPN config from the server and starts the tunnel if active.
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
                AppLogger.d(TAG, "VPN config fetched (${configText.length} bytes), requesting permission")
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
        scope.launch {
            tearDownTunnel()
        }
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
     * Starts the WireGuard tunnel on a background thread.
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
                // Blocking call — GoBackend starts its own VpnService and
                // hands off to the wireguard-go runtime.
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
     * Fetch and return the raw WireGuard .conf text, or null if VPN is disabled
     * or the fetch fails.
     */
    private fun fetchConfig(): String? {
        val manifestUrl = StreamConfig.VPN_CONFIG_URL
        if (manifestUrl.isBlank() || manifestUrl.contains("YOUR_SERVER")) {
            Log.d(TAG, "VPN_CONFIG_URL not configured — skipping VPN")
            return null
        }
        AppLogger.d(TAG, "Fetching VPN config from: $manifestUrl")

        val body = try {
            http.newCall(Request.Builder().url(manifestUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.d(TAG, "Config fetch returned HTTP ${resp.code} — skipping VPN")
                    return null
                }
                resp.body?.string() ?: return null
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "Config fetch failed: ${e.message} — skipping VPN")
            return null
        }

        val trimmed = body.trim()

        // Case A: raw .conf
        if (trimmed.startsWith("[Interface]", ignoreCase = true)) {
            AppLogger.d(TAG, "Received raw WireGuard config")
            return trimmed
        }

        // Case B: JSON manifest { "active": bool, "configUrl": "…" }
        return try {
            val json = JSONObject(trimmed)
            if (!json.optBoolean("active", false)) {
                AppLogger.d(TAG, "VPN manifest active=false — skipping VPN")
                return null
            }
            val configUrl = json.optString("configUrl", "")
            if (configUrl.isBlank()) {
                AppLogger.e(TAG, "VPN manifest has no configUrl")
                return null
            }
            AppLogger.d(TAG, "Fetching .conf from: $configUrl")
            http.newCall(Request.Builder().url(configUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.e(TAG, "Conf file fetch returned HTTP ${resp.code}")
                    null
                } else {
                    resp.body?.string()?.trim()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse VPN manifest: ${e.message}")
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
            if (parsed.iface.mtu > 0) {
                ifaceBuilder.parseMtu(parsed.iface.mtu.toString())
            }
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
