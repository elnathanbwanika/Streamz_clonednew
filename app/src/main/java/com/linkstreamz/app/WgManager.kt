package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgManager.kt
//
// Manages the per-app WireGuard VPN tunnel.
//
// Config source: PRIVATE admin GitHub repo only.
// Fetches vpn/index.json via the GitHub Contents API, finds the active,
// non-expired wireguard entry, downloads its .conf, verifies integrity,
// persists it to app-private storage, then applies it via GoBackend.
//
// The public content repo (CONTENT_BASE_URL) is never consulted for VPN.
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

    // ── Public API ────────────────────────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch(Dispatchers.IO) { refreshConfig(context) }
    }

    fun onVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val ctx = appContext ?: return
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

    // ── Refresh ───────────────────────────────────────────────────────────────

    private fun refreshConfig(context: Context) {
        try {
            val configText = fetchAndVerifyConfig(context.applicationContext)
            if (configText == null) {
                deleteStoredConfig(context.applicationContext)
                tearDownTunnel()
                return
            }
            pendingConfigText = configText
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                requestPermissionThenStart(context)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "refresh error: ${e.javaClass.simpleName}: ${e.message}")
            Log.w(TAG, "VPN refresh failed", e)
        }
    }

    // ── Permission + start ────────────────────────────────────────────────────

    private fun requestPermissionThenStart(context: Context) {
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
            _tunnelState.value = WgTunnelState.CONNECTING
            try {
                val wgCfg = buildWgConfig(context, configText)
                if (wgCfg == null) {
                    AppLogger.e(TAG, "config parse failed — VPN not started")
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
                AppLogger.e(TAG, "tunnel start failed: ${e.javaClass.simpleName}: ${e.message}")
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
     * Fetches vpn/index.json from the PRIVATE admin repo via GitHub Contents API.
     * Validates active entry, expiry, and file integrity.
     * Returns raw .conf text or null if no valid active config exists.
     */
    private fun fetchAndVerifyConfig(context: Context): String? {
        // Guard: token placeholder not replaced yet.
        if (ADMIN_GITHUB_TOKEN == "REPLACE_WITH_GITHUB_PAT") {
            AppLogger.e(TAG, "ADMIN_GITHUB_TOKEN not set — VPN disabled")
            return null
        }

        // ── 1. Fetch vpn/index.json ────────────────────────────────────────────
        val manifestUrl = "$GITHUB_API_BASE/repos/$ADMIN_REPO_OWNER/$ADMIN_REPO_NAME/contents/vpn/index.json"
        val manifestBody = githubGetText(manifestUrl, label = "manifest") ?: return null

        // ── 2. Parse ───────────────────────────────────────────────────────────
        val root = try { JSONObject(manifestBody) } catch (e: Exception) {
            AppLogger.e(TAG, "manifest JSON error: ${e.message}"); return null
        }
        val activeId = root.optString("active", "")
            .takeIf { it.isNotBlank() && it != "null" }
        if (activeId == null) {
            AppLogger.d(TAG, "manifest active=null — no VPN")
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
            AppLogger.d(TAG, "manifest active=$activeId not found or wrong type — no VPN")
            return null
        }

        // ── 4. Expiry check ────────────────────────────────────────────────────
        val expStr = entry.optString("exp", "").trim()
        if (expStr.isNotBlank() && expStr != "null") {
            val expEpochMs = try {
                Instant.parse(expStr).toEpochMilli()
            } catch (e: Exception) {
                AppLogger.e(TAG, "file $activeId bad exp '$expStr': ${e.message}"); return null
            }
            if (System.currentTimeMillis() > expEpochMs + EXP_SKEW_MS) {
                AppLogger.d(TAG, "file $activeId expired — no VPN")
                return null
            }
        }

        val filePath     = entry.optString("path", "").trim()
        val expectedSha  = entry.optString("sha256", "").trim()
        val expectedSize = entry.optLong("size", -1L)

        if (filePath.isBlank()) {
            AppLogger.e(TAG, "file $activeId missing path"); return null
        }

        // ── 5. Download config bytes ───────────────────────────────────────────
        val fileUrl = "$GITHUB_API_BASE/repos/$ADMIN_REPO_OWNER/$ADMIN_REPO_NAME/contents/$filePath"
        val fileBytes = githubGetBytes(fileUrl, label = "file $activeId") ?: return null

        // ── 6. Integrity check ─────────────────────────────────────────────────
        if (expectedSha.isNotBlank() && expectedSha != "null") {
            val actual = sha256Hex(fileBytes)
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                AppLogger.e(TAG, "hash mismatch for $activeId — keeping previous config")
                return loadStoredConfig(context)   // fall back to last good config
            }
        } else if (expectedSize >= 0 && fileBytes.size.toLong() != expectedSize) {
            AppLogger.e(TAG, "size mismatch for $activeId (got ${fileBytes.size}, want $expectedSize) — keeping previous")
            return loadStoredConfig(context)
        }

        // ── 7. Validate it's a real WireGuard config ───────────────────────────
        val confText = fileBytes.toString(Charsets.UTF_8)
        if (!confText.contains("[Interface]", ignoreCase = true)) {
            AppLogger.e(TAG, "file $activeId not a valid WireGuard conf — keeping previous")
            return loadStoredConfig(context)
        }

        // ── 8. Persist and return ──────────────────────────────────────────────
        saveStoredConfig(context, fileBytes)
        AppLogger.d(TAG, "config applied (id=$activeId)")
        return confText
    }

    // ── GitHub Contents API ───────────────────────────────────────────────────

    /** Fetches a URL with GitHub raw-file headers, returns body as String or null. */
    private fun githubGetText(url: String, label: String): String? {
        return try {
            http.newCall(githubRequest(url)).execute().use { resp ->
                AppLogger.d(TAG, "$label HTTP ${resp.code}")
                if (resp.code == 200) resp.body?.string() else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "$label fetch error: ${e.message}"); null
        }
    }

    /** Fetches a URL with GitHub raw-file headers, returns raw bytes or null. */
    private fun githubGetBytes(url: String, label: String): ByteArray? {
        return try {
            http.newCall(githubRequest(url)).execute().use { resp ->
                AppLogger.d(TAG, "$label HTTP ${resp.code}")
                if (resp.code == 200) resp.body?.bytes() else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "$label fetch error: ${e.message}"); null
        }
    }

    private fun githubRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $ADMIN_GITHUB_TOKEN")
            .header("Accept", "application/vnd.github.v3.raw")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

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
                ?: run { AppLogger.e(TAG, "WgConfig.parse returned null"); return null }

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
            AppLogger.e(TAG, "config build error: ${e.message}")
            Log.e(TAG, "config build error", e)
            null
        }
    }
}
