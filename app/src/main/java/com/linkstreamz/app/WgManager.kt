package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgManager.kt
//
// Lifecycle for the in-process userspace WireGuard tunnel.
//
// Config source: PUBLIC VPN repository (tracerdasu/link-streamz-vpn, branch main).
// Fetches vpn/index.json, finds the active non-expired wireguard entry,
// downloads its .conf, verifies integrity (SHA-256 or size), persists it
// to app-private storage, then hands it to TunnelManager (wireguard-go +
// netstack). No system VPN service / TUN device / permission dialog is involved.
//
// Rules:
//   - Start when a valid active config exists.
//   - Restart when the active id or sha256 changes.
//   - Defer swaps while playback is active (except expiry/deactivation, which
//     stop immediately).
//   - Stop when the active config is null or expired.
//   - Fail-open: a broken tunnel never blocks the player.
//   - Config fetching always uses a direct client, never the tunnel.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

/** Tunnel connection states. */
enum class WgTunnelState { STOPPED, CONNECTING, CONNECTED, ERROR }

object WgManager {

    private const val TAG = "WgManager"

    /** Allow 10 minutes of clock skew when checking expiry. */
    private const val EXP_SKEW_MS = 10 * 60 * 1000L

    /** Network callbacks arrive in bursts; rebuild the device once per burst. */
    private const val NETWORK_DEBOUNCE_MS = 500L

    /** App-private storage location for the verified config. */
    private const val CONFIG_SUBDIR = "vpn"
    private const val CONFIG_FNAME  = "active.conf"
    private const val WORK_NAME     = "vpn-config-refresh"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tunnelState = MutableStateFlow(WgTunnelState.STOPPED)
    val tunnelState: StateFlow<WgTunnelState> = _tunnelState.asStateFlow()

    private data class VerifiedConfig(val id: String, val sha256: String, val text: String) {
        val key: String get() = "$id:$sha256"
    }

    private sealed class FetchResult {
        data class Ok(val config: VerifiedConfig) : FetchResult()
        /** Manifest says there is no usable active config (or it expired). */
        object NoActive : FetchResult()
        /** Network/parse problem: keep the last good config and retry later. */
        object Transient : FetchResult()
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var applied: VerifiedConfig? = null
    @Volatile private var pending: VerifiedConfig? = null
    @Volatile private var playbackActive: Boolean = false
    @Volatile private var callbackRegistered: Boolean = false
    @Volatile private var lastNetwork: Network? = null
    @Volatile private var networkChangeJob: Job? = null
    @Volatile private var lastNetworkSignature: String? = null
    @Volatile private var phaseObserved: Boolean = false

    // ── Public API ────────────────────────────────────────────────────────────

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        observeTunnelPhase()
        registerNetworkCallback(app)
        schedulePeriodicRefresh(app)
        scope.launch(Dispatchers.IO) {
            // Cold start: let Android publish the default network (including an
            // external VPN) before deciding whether an in-app config is active.
            NetworkBootstrap.awaitDefaultNetwork()
            refreshConfig(app)
        }
    }

    /** True when an in-app WireGuard config is currently applied. */
    fun isConfigured(): Boolean = applied != null

    /** Publishes the real connection phase (handshake/probe gated) to the UI. */
    private fun observeTunnelPhase() {
        if (phaseObserved) return
        phaseObserved = true
        scope.launch {
            TunnelManager.phase.collect { phase ->
                _tunnelState.value = when (phase) {
                    TunnelPhase.UP -> WgTunnelState.CONNECTED
                    TunnelPhase.CONNECTING -> WgTunnelState.CONNECTING
                    TunnelPhase.DOWN -> if (applied == null) WgTunnelState.STOPPED else WgTunnelState.ERROR
                }
            }
        }
    }

    fun refresh(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        scope.launch(Dispatchers.IO) { refreshConfig(ctx.applicationContext) }
    }

    /** Called by the player so config swaps can be deferred while streaming. */
    fun setPlaybackActive(active: Boolean) {
        playbackActive = active
        if (active) return
        val queued = pending ?: return
        pending = null
        scope.launch(Dispatchers.IO) { applyConfig(queued) }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) { tearDownTunnel() }
    }

    /**
     * Synchronous fetch+persist used by the periodic worker. Does not start the
     * tunnel: the worker may run with no foreground process. Returns true when
     * a valid active config is available or already stored.
     */
    fun refreshBlocking(context: Context): Boolean {
        val app = context.applicationContext
        return try {
            when (fetchAndVerifyConfig(app)) {
                is FetchResult.Ok -> true
                is FetchResult.NoActive -> {
                    deleteStoredConfig(app)
                    true
                }
                is FetchResult.Transient -> false
            }
        } catch (e: Exception) {
            logd("refresh failed")
            false
        }
    }

    // ── Network changes (reconnect) ───────────────────────────────────────────

    private fun registerNetworkCallback(context: Context) {
        if (callbackRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                lastNetwork = network
                scheduleNetworkChange(context)
            }

            override fun onLost(network: Network) {
                if (lastNetwork == network) lastNetwork = null
                scheduleNetworkChange(context)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scheduleNetworkChange(context)
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                scheduleNetworkChange(context)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            callbackRegistered = true
        } catch (_: Exception) {
        }
    }

    /**
     * Network callbacks arrive in bursts (VPN enable/disable, MTU changes).
     * Debounce them so the device is rebuilt at most once per burst.
     */
    private fun scheduleNetworkChange(context: Context) {
        networkChangeJob?.cancel()
        networkChangeJob = scope.launch(Dispatchers.IO) {
            delay(NETWORK_DEBOUNCE_MS)
            applyNetworkChange(context.applicationContext)
        }
    }

    private fun applyNetworkChange(context: Context) {
        if (TunnelManager.isUp().not() && applied == null) return
        val config = applied?.let { WgConfig.parse(it.text) } ?: return
        val external = isExternalVpnActive(context)
        val linkMtu = activeLinkMtu(context)
        val mtu = TunnelMtu.choose(config.iface.mtu, linkMtu, external)
        val signature = networkSignature(context, external, linkMtu, mtu)
        if (signature == lastNetworkSignature && TunnelManager.isUp()) {
            // Same path: re-probe happens in the tunnel health loop.
            return
        }
        lastNetworkSignature = signature

        if (BuildConfig.DEBUG) {
            AppLogger.d(
                TAG,
                "network vpn=$external transports=${activeTransports(context)} " +
                    "linkMtu=${linkMtu ?: "?"} chosenMtu=$mtu",
            )
        }
        TunnelManager.onNetworkChanged(mtu, external)
    }

    private fun networkSignature(
        context: Context,
        externalVpn: Boolean,
        linkMtu: Int?,
        mtu: Int,
    ): String {
        val network = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?.activeNetwork?.toString() ?: "none"
        return "$network:${activeTransports(context)}:$externalVpn:$linkMtu:$mtu"
    }

    private fun isExternalVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun activeLinkMtu(context: Context): Int? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        return cm.getLinkProperties(network)?.mtu?.takeIf { it > 0 }
    }

    private fun activeTransports(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "?"
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "?"
        return buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cell")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
        }.joinToString(",").ifBlank { "other" }
    }

    // ── Refresh / apply ───────────────────────────────────────────────────────

    private fun refreshConfig(context: Context, forceRestart: Boolean = false) {
        try {
            val result = fetchAndVerifyConfig(context)
            val verified = when (result) {
                is FetchResult.Ok -> result.config
                is FetchResult.NoActive -> {
                    // Deactivated or expired: stop immediately, even while streaming.
                    applied = null
                    pending = null
                    deleteStoredConfig(context)
                    tearDownTunnel()
                    return
                }
                is FetchResult.Transient -> {
                    logd("config fetch failed (keeping last good)")
                    // Keep a running tunnel as-is; if none is running, fall back
                    // to the last good config on disk.
                    if (applied != null) return
                    storedVerified(context) ?: return
                }
            }

            val appliedKey = applied?.key
            if (!forceRestart && verified.key == appliedKey &&
                TunnelManager.phase.value != TunnelPhase.DOWN
            ) {
                pending = null
                return
            }

            if (playbackActive && applied != null && verified.key != appliedKey) {
                pending = verified
                logd("config swap deferred (playback)")
                return
            }

            applyConfig(verified)
        } catch (e: Exception) {
            logd("refresh failed")
        }
    }

    private fun applyConfig(verified: VerifiedConfig) {
        _tunnelState.value = WgTunnelState.CONNECTING
        val cfg = WgConfig.parse(verified.text)
        if (cfg == null) {
            applied = null
            tearDownTunnel()
            return
        }

        val ctx = appContext
        val external = ctx?.let { isExternalVpnActive(it) } ?: false
        val linkMtu = ctx?.let { activeLinkMtu(it) }
        val mtu = TunnelMtu.choose(cfg.iface.mtu, linkMtu, external)
        if (ctx != null) {
            lastNetworkSignature = networkSignature(ctx, external, linkMtu, mtu)
            logd(
                "vpn=$external transports=${activeTransports(ctx)} " +
                    "linkMtu=${linkMtu ?: "?"} chosenMtu=$mtu",
            )
        }

        val ok = TunnelManager.start(cfg, mtu, external)
        if (ok) {
            applied = verified
            pending = null
            // CONNECTED is published by observeTunnelPhase() only after the
            // handshake + probe gate passes.
            logd("config applied (connecting)")
        } else {
            applied = null
            _tunnelState.value = WgTunnelState.ERROR
            logd("fail-open")
        }
    }

    private fun tearDownTunnel() {
        applied = null
        pending = null
        lastNetworkSignature = null
        TunnelManager.stop()
        _tunnelState.value = WgTunnelState.STOPPED
    }

    private fun schedulePeriodicRefresh(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<TunnelConfigWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } catch (_: Exception) {
        }
    }

    // ── Manifest fetch + validation ───────────────────────────────────────────

    /**
     * Fetches vpn/index.json from the public VPN repo.
     * Validates active entry, expiry, and file integrity.
     * Returns a verified config or null if no valid active config exists.
     */
    private fun fetchAndVerifyConfig(context: Context): FetchResult {
        // ── 1. Fetch vpn/index.json with cache-busting query parameter ────────
        val manifestUrl = "$VPN_MANIFEST_URL?t=${System.currentTimeMillis()}"
        val (manifestCode, manifestBody) = httpGetText(manifestUrl)

        if (manifestCode != 200 || manifestBody == null) {
            logd("manifest HTTP $manifestCode (transient)")
            return FetchResult.Transient
        }

        // ── 2. Parse ───────────────────────────────────────────────────────────
        val root = try { JSONObject(manifestBody) } catch (_: Exception) {
            logd("manifest parse failed (transient)")
            return FetchResult.Transient
        }
        val activeId = root.optString("active", "")
            .takeIf { it.isNotBlank() && it != "null" }

        logd("manifest HTTP $manifestCode active=${activeId ?: "null"}")

        if (activeId == null) {
            return FetchResult.NoActive
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
            return FetchResult.NoActive
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
                logd("file expired")
                return FetchResult.NoActive
            }
        }

        val filePath     = entry.optString("path", "").trim()
        val expectedSha  = entry.optString("sha256", "").trim()
        val expectedSize = entry.optLong("size", -1L)

        if (filePath.isBlank()) {
            return FetchResult.NoActive
        }

        // ── 5. Download config bytes with cache-busting query parameter ───────
        val configUrl = "$VPN_BASE_URL/${filePath.trimStart('/')}?t=${System.currentTimeMillis()}"
        val fileBytes = httpGetBytes(configUrl)
        if (fileBytes == null) {
            // Temporary network issue: keep previous good config.
            return storedVerified(context)?.let { FetchResult.Ok(it) }
                ?: FetchResult.Transient
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
            logd("hash mismatch")
            return storedVerified(context)?.let { FetchResult.Ok(it) }
                ?: FetchResult.Transient
        }

        // ── 7. Validate WireGuard config format ────────────────────────────────
        val confText = fileBytes.toString(Charsets.UTF_8)
        if (!confText.contains("[Interface]", ignoreCase = true)) {
            logd("hash mismatch")
            return storedVerified(context)?.let { FetchResult.Ok(it) }
                ?: FetchResult.Transient
        }

        // ── 8. Persist and return ──────────────────────────────────────────────
        saveStoredConfig(context, fileBytes)
        return FetchResult.Ok(VerifiedConfig(activeId, expectedSha, confText))
    }

    private fun storedVerified(context: Context): VerifiedConfig? {
        val text = loadStoredConfig(context) ?: return null
        val sha = sha256Hex(text.toByteArray(Charsets.UTF_8))
        return VerifiedConfig("stored", sha, text)
    }

    // ── HTTP helpers (always direct) ──────────────────────────────────────────

    /** Fetches a URL, returns Pair of HTTP status code and body string or null. */
    private fun httpGetText(url: String): Pair<Int, String?> {
        return try {
            val req = Request.Builder().url(url).build()
            HttpClients.direct.newCall(req).execute().use { resp ->
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
            HttpClients.direct.newCall(req).execute().use { resp ->
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

    private fun logd(msg: String) {
        if (BuildConfig.DEBUG) AppLogger.d(TAG, msg)
    }
}
