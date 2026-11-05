package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgManager.kt
//
// Singleton that manages the lifecycle of the per-app WireGuard VPN.
//
// Flow:
//   1. MainActivity calls WgManager.init(context) in onCreate().
//   2. WgManager fetches StreamConfig.VPN_CONFIG_URL from the admin server.
//   3. The server can respond in two ways:
//        a) Raw .conf text (starts with "[Interface]") — used directly.
//        b) JSON manifest:
//             {"active": true, "configUrl": "https://…/wg0.conf"}
//           If active=false (or missing) OR fetch fails, VPN is skipped entirely.
//   4. If a valid active config is found:
//        a) VPN permission is requested (Android shows one-time dialog).
//        b) WgVpnService is started with the config text.
//   5. If no config or inactive: app routes traffic normally, external VPNs
//      connected on the device continue to work (OkHttp uses system routing).
//
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WgManager {

    private const val TAG = "WgManager"

    // VPN permission request code — handled in MainActivity.onActivityResult
    const val VPN_PERMISSION_REQUEST_CODE = 0xA55

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Simple OkHttp client just for fetching the config — no proxy, plain system routing.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Current tunnel state — observe in UI if needed. */
    val tunnelState: StateFlow<WgTunnelState> = WgVpnService.state

    // The raw config text fetched from the server, kept so we can pass it to
    // the service after the user grants VPN permission.
    @Volatile private var pendingConfigText: String? = null
    @Volatile private var appContext: Context? = null

    /**
     * Call this from [MainActivity.onCreate] BEFORE setContent.
     * Fetches the VPN config from the server and starts the tunnel if active.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            try {
                val configText = fetchConfig() ?: return@launch
                pendingConfigText = configText
                // VPN permission must be requested on the main thread from an Activity
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    requestPermissionThenStart(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "VPN init failed — falling back to normal routing", e)
            }
        }
    }

    /**
     * Call this from [MainActivity.onActivityResult] with the VPN permission result.
     */
    fun onVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val ctx = appContext ?: return
            val cfg = pendingConfigText ?: return
            WgVpnService.start(ctx, cfg)
        } else {
            Log.i(TAG, "VPN permission denied — normal routing")
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Fetch and parse the VPN config manifest from the admin server.
     * Returns the raw WireGuard .conf text, or null if VPN should be skipped.
     */
    private fun fetchConfig(): String? {
        val manifestUrl = StreamConfig.VPN_CONFIG_URL
        if (manifestUrl.isBlank() || manifestUrl.contains("YOUR_SERVER")) {
            Log.d(TAG, "VPN_CONFIG_URL not configured — skipping VPN")
            return null
        }

        val manifestText = try {
            val req = Request.Builder().url(manifestUrl).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "VPN config fetch returned ${resp.code} — skipping VPN")
                    return null
                }
                resp.body?.string() ?: return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "VPN config fetch failed: ${e.message} — skipping VPN")
            return null
        }

        val trimmed = manifestText.trim()

        // Case A: raw .conf file
        if (trimmed.startsWith("[Interface]", ignoreCase = true)) {
            Log.d(TAG, "Got raw WireGuard config")
            return trimmed
        }

        // Case B: JSON manifest
        return try {
            val json = JSONObject(trimmed)
            val active = json.optBoolean("active", false)
            if (!active) {
                Log.d(TAG, "VPN manifest says active=false — skipping VPN")
                return null
            }
            val configUrl = json.optString("configUrl", "")
            if (configUrl.isBlank()) {
                Log.w(TAG, "VPN manifest has no configUrl")
                return null
            }
            // Fetch the actual .conf file from the URL in the manifest
            val confReq = Request.Builder().url(configUrl).build()
            http.newCall(confReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Config file fetch returned ${resp.code}")
                    null
                } else {
                    resp.body?.string()?.trim()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VPN manifest: ${e.message}")
            null
        }
    }

    private fun requestPermissionThenStart(context: Context) {
        val permIntent = VpnService.prepare(context)
        if (permIntent == null) {
            // Permission already granted — start immediately
            val cfg = pendingConfigText ?: return
            WgVpnService.start(context.applicationContext, cfg)
        } else {
            // Need to show the system VPN permission dialog from an Activity
            if (context is Activity) {
                context.startActivityForResult(permIntent, VPN_PERMISSION_REQUEST_CODE)
            } else {
                // If we don't have an Activity, add FLAG_ACTIVITY_NEW_TASK
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(permIntent)
                // We can't get the result — WgManager.onVpnPermissionResult won't be called.
                // The user will see the dialog and need to re-open the app. Acceptable UX.
            }
        }
    }
}
