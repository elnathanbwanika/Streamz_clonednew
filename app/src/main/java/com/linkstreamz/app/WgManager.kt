package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgManager.kt
//
// Manages the per-app WireGuard VPN tunnel.
//
// Config source: PUBLIC VPN repository (tracerdasu/link-streamz-vpn, branch main).
// Fetches vpn/index.json, finds the active non-expired wireguard entry,
// downloads its .conf, verifies integrity (SHA-256 or size), persists it
// to app-private storage, then applies it via GoBackend.
//
// Critical rules:
//   1. NEVER disconnect an already connected external VPN. If the device
//      already has an active VPN (TRANSPORT_VPN / tun interface), the app
//      preserves it, skips starting its own tunnel, and routes traffic through
//      the existing external VPN.
//   2. If the internal tunnel is running and an external VPN connects, the
//      internal tunnel yields immediately to avoid any conflict.
//   3. When active, tunnel is strictly scoped to this app (includeApplication).
//   4. No credentials or tokens required.
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Tunnel connection states. */
enum class WgTunnelState { STOPPED, CONNECTING, CONNECTED, ERROR }

object WgManager {

    private const val TAG = "WgManager"

    const val VPN_PERMISSION_REQUEST_CODE = 0xA55

    /** Allow 10 minutes of clock skew when checking expiry. */
    private const val EXP_SKEW_MS = 10 * 60 * 1000L

    /** App-private storage location for the verified config. */
    private const val CONFIG_SUBDIR = "vpn"
    private const val CONFIG_FNAME  = "active.conf"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Plain OkHttp — requests MUST go over the normal network, not the tunnel.
    private val http = OkHttpClient.Builder()
        .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _tunnelState = MutableStateFlow(WgTunnelState.STOPPED)
    val tunnelState: StateFlow<WgTunnelState> = _tunnelState.asStateFlow()

    @Volatile private var pendingConfigText: String? = null
    @Volatile private var appContext: Context?        = null
    @Volatile private var goBackend: GoBackend?       = null
    @Volatile private var activeTunnel: Tunnel?       = null
    @Volatile private var isCallbackRegistered        = false

    // ── Public API ────────────────────────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        registerNetworkCallback(context.applicationContext)
        scope.launch(Dispatchers.IO) { refreshConfig(context) }
    }

    fun refresh(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        scope.launch(Dispatchers.IO) { refreshConfig(ctx) }
    }

    fun onVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val ctx = appContext ?: return
            if (isExternalVpnActive(ctx)) {
                AppLogger.d(TAG, "external VPN connected — preserving external connection")
                return
            }
            val cfg = pendingConfigText ?: return
            AppLogger.d(TAG, "VPN permission granted — starting tunnel")
            startTunnel(ctx, cfg)
        } else {
            AppLogger.d(TAG, "VPN permission denied")
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) { tearDownTunnel() }
    }

    // ── External VPN Detection ────────────────────────────────────────────────

    /**
     * Returns true if the device is currently connected to an external VPN.
     * When true, this app MUST NOT start its own tunnel to avoid disconnecting
     * or interrupting the user's existing VPN connection.
     */
    fun isExternalVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val isOurRunning = (activeTunnel != null && _tunnelState.value == WgTunnelState.CONNECTED)

        // 1. Check all networks for TRANSPORT_VPN
        try {
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (!isOurRunning) return true
                }
            }
        } catch (_: Exception) {}

        // 2. Check active default network
        try {
            val activeNet = cm.activeNetwork
            if (activeNet != null) {
                val caps = cm.getNetworkCapabilities(activeNet)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    if (!isOurRunning) return true
                }
            }
        } catch (_: Exception) {}

        // 3. Check network interfaces for active tun/ppp/vpn
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (iface in interfaces) {
                    if (iface.isUp && !iface.isLoopback) {
                        val name = iface.name.lowercase()
                        if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("p2p") || name.contains("vpn")) {
                            if (!isOurRunning) return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun registerNetworkCallback(context: Context) {
        if (isCallbackRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val isOurRunning = (activeTunnel != null && _tunnelState.value == WgTunnelState.CONNECTED)
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !isOurRunning) {
                    // An external VPN has connected — immediately tear down our internal tunnel
                    scope.launch(Dispatchers.IO) {
                        if (activeTunnel != null) {
                            AppLogger.d(TAG, "external VPN connected — tearing down internal tunnel")
                            tearDownTunnel()
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                // If a network was lost, check if external VPN was disconnected, and restore internal tunnel if needed
                scope.launch(Dispatchers.IO) {
                    delay(1000)
                    if (!isExternalVpnActive(context) && activeTunnel == null && pendingConfigText != null) {
                        refreshConfig(context)
                    }
                }
            }
        }

        try {
            val req = NetworkRequest.Builder().build()
            cm.registerNetworkCallback(req, cb)
            isCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private fun refreshConfig(context: Context) {
        try {
            // Guard: Never start internal VPN if external VPN is already active
            if (isExternalVpnActive(context)) {
                AppLogger.d(TAG, "external VPN connected — preserving external connection")
                if (activeTunnel != null) {
                    tearDownTunnel()
                }
                return
            }

            val configText = fetchAndVerifyConfig(context.applicationContext)
            if (configText == null) {
                deleteStoredConfig(context.applicationContext)
                tearDownTunnel()
                return
            }

            // Re-check right before starting to avoid race conditions
            if (isExternalVpnActive(context)) {
                AppLogger.d(TAG, "external VPN connected — preserving external connection")
                return
            }

            pendingConfigText = configText
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                requestPermissionThenStart(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "VPN refresh failed", e)
        }
    }

    // ── Permission + start ────────────────────────────────────────────────────

    private fun requestPermissionThenStart(context: Context) {
        if (isExternalVpnActive(context)) {
            AppLogger.d(TAG, "external VPN connected — preserving external connection")
            return
        }

        val permIntent = VpnService.prepare(context)
        if (permIntent == null) {
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

    private fun startTunnel(context: Context, configText: String) {
        scope.launch(Dispatchers.IO) {
            if (isExternalVpnActive(context)) {
                AppLogger.d(TAG, "external VPN connected — preserving external connection")
                return@launch
            }

            _tunnelState.value = WgTunnelState.CONNECTING
            try {
                val wgCfg = buildWgConfig(context, configText)
                if (wgCfg == null) {
                    _tunnelState.value = WgTunnelState.ERROR
                    return@launch
                }
                val backend = GoBackend(context.applicationContext)
                goBackend = backend
                val tunnel = object : Tunnel {
                    override fun getName() = "streamz"
                    override fun onStateChange(newState: Tunnel.State) {
                        _tunnelState.value = when (newState) {
                            Tunnel.State.UP   -> { AppLogger.d(TAG, "tunnel UP"); WgTunnelState.CONNECTED }
                            Tunnel.State.DOWN -> { AppLogger.d(TAG, "tunnel DOWN"); WgTunnelState.STOPPED }
                            else              -> WgTunnelState.CONNECTING
                        }
                    }
                }
                activeTunnel = tunnel
                backend.setState(tunnel, Tunnel.State.UP, wgCfg)
            } catch (e: Exception) {
                Log.e(TAG, "tunnel start failed", e)
                _tunnelState.value = WgTunnelState.ERROR
            }
        }
    }

    private fun tearDownTunnel() {
        try {
            val t = activeTunnel; val b = goBackend
            if (t != null && b != null) b.setState(t, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            Log.w(TAG, "teardown: ${e.message}")
        } finally {
            activeTunnel = null; goBackend = null
            _tunnelState.value = WgTunnelState.STOPPED
        }
    }

    // ── Manifest fetch + validation ───────────────────────────────────────────

    /**
     * Fetches vpn/index.json from the public VPN repo.
     * Validates active entry, expiry, and file integrity.
     * Returns raw .conf text or null if no valid active config exists.
     */
    private fun fetchAndVerifyConfig(context: Context): String? {
        // ── 1. Fetch vpn/index.json with cache-busting query parameter ────────
        val manifestUrl = "$VPN_MANIFEST_URL?t=${System.currentTimeMillis()}"
        val (manifestCode, manifestBody) = httpGetText(manifestUrl)

        if (manifestCode != 200 || manifestBody == null) {
            AppLogger.d(TAG, "manifest HTTP $manifestCode active=null")
            return null
        }

        // ── 2. Parse ───────────────────────────────────────────────────────────
        val root = try { JSONObject(manifestBody) } catch (_: Exception) {
            AppLogger.d(TAG, "manifest HTTP $manifestCode active=null")
            return null
        }
        val activeId = root.optString("active", "")
            .takeIf { it.isNotBlank() && it != "null" }

        AppLogger.d(TAG, "manifest HTTP $manifestCode active=${activeId ?: "null"}")

        if (activeId == null) {
            return null
        }

        // ── 3. Find matching entry ─────────────────────────────────────────────
        val files = root.optJSONArray("files")
        var entry: JSONObject? = null
        if (files != null) {
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                if (f.optString("id") == activeId
                    && f.optString("type").equals("wireguard", ignoreCase = true)) {
                    entry = f; break
                }
            }
        }
        if (entry == null) {
            return null
        }

        // ── 4. Expiry check (allow 10 minutes of clock skew) ───────────────────
        val expStr = entry.optString("exp", "").trim()
        if (expStr.isNotBlank() && expStr != "null") {
            val expEpochMs = try {
                Instant.parse(expStr).toEpochMilli()
            } catch (_: Exception) {
                null
            }
            if (expEpochMs == null || System.currentTimeMillis() > expEpochMs + EXP_SKEW_MS) {
                AppLogger.d(TAG, "file expired")
                return null
            }
        }

        val filePath     = entry.optString("path", "").trim()
        val expectedSha  = entry.optString("sha256", "").trim()
        val expectedSize = entry.optLong("size", -1L)

        if (filePath.isBlank()) {
            return null
        }

        // ── 5. Download config bytes with cache-busting query parameter ───────
        val configUrl = "$VPN_BASE_URL/${filePath.trimStart('/')}?t=${System.currentTimeMillis()}"
        val fileBytes = httpGetBytes(configUrl)
        if (fileBytes == null) {
            // Treat temporary network issue as normal retry later: keep previous good config
            return loadStoredConfig(context)
        }

        // ── 6. Integrity check (SHA-256 or size) ───────────────────────────────
        val isHashValid = if (expectedSha.isNotBlank() && expectedSha != "null") {
            val actual = sha256Hex(fileBytes)
            actual.equals(expectedSha, ignoreCase = true)
        } else if (expectedSize >= 0) {
            fileBytes.size.toLong() == expectedSize
        } else {
            true
        }

        if (!isHashValid) {
            AppLogger.d(TAG, "hash mismatch")
            return loadStoredConfig(context)
        }

        // ── 7. Validate WireGuard config format ────────────────────────────────
        val confText = fileBytes.toString(Charsets.UTF_8)
        if (!confText.contains("[Interface]", ignoreCase = true)) {
            AppLogger.d(TAG, "hash mismatch")
            return loadStoredConfig(context)
        }

        // ── 8. Persist and return ──────────────────────────────────────────────
        saveStoredConfig(context, fileBytes)
        AppLogger.d(TAG, "config applied")
        return confText
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /** Fetches a URL, returns Pair of HTTP status code and body string or null. */
    private fun httpGetText(url: String): Pair<Int, String?> {
        return try {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                Pair(resp.code, if (resp.code == 200) resp.body?.string() else null)
            }
        } catch (_: Exception) {
            Pair(0, null)
        }
    }

    /** Fetches a URL, returns raw bytes or null on failure. */
    private fun httpGetBytes(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 200) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── App-private storage ───────────────────────────────────────────────────

    private fun configFile(context: Context) =
        File(context.filesDir, "$CONFIG_SUBDIR/$CONFIG_FNAME")

    private fun saveStoredConfig(context: Context, bytes: ByteArray) {
        val f = configFile(context)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    private fun loadStoredConfig(context: Context): String? =
        configFile(context).takeIf { it.exists() }?.readText()

    private fun deleteStoredConfig(context: Context) {
        configFile(context).delete()
    }

    // ── SHA-256 ───────────────────────────────────────────────────────────────

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // ── WireGuard config builder ──────────────────────────────────────────────

    private fun buildWgConfig(context: Context, configText: String): com.wireguard.config.Config? {
        return try {
            val parsed = WgConfig.parse(configText)
                ?: return null

            val ifaceBuilder = com.wireguard.config.Interface.Builder()
            ifaceBuilder.parsePrivateKey(parsed.iface.privateKey)
            for (addr in parsed.iface.addresses) ifaceBuilder.parseAddresses(addr)
            for (d in parsed.iface.dns)           ifaceBuilder.parseDnsServers(d)
            if (parsed.iface.mtu > 0) ifaceBuilder.parseMtu(parsed.iface.mtu.toString())
            ifaceBuilder.includeApplication(context.packageName)

            val peerBuilder = com.wireguard.config.Peer.Builder()
            peerBuilder.parsePublicKey(parsed.peer.publicKey)
            parsed.peer.presharedKey?.let { peerBuilder.parsePreSharedKey(it) }
            peerBuilder.parseEndpoint(parsed.peer.endpoint)
            for (ip in parsed.peer.allowedIps) peerBuilder.parseAllowedIPs(ip)
            if (parsed.peer.persistentKeepalive > 0)
                peerBuilder.parsePersistentKeepalive(parsed.peer.persistentKeepalive.toString())

            com.wireguard.config.Config.Builder()
                .setInterface(ifaceBuilder.build())
                .addPeer(peerBuilder.build())
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "config build error", e)
            null
        }
    }
}
