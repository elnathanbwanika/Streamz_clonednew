package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgVpnService.kt
//
// Android VpnService that establishes a per-app WireGuard tunnel.
//
// Key design choices:
//  • Interface.Builder.includeApplication(packageName) restricts the tunnel so
//    only THIS app's traffic goes through WireGuard.  All other apps (including
//    other VPN clients the user may have running) continue unaffected.
//  • GoBackend from com.wireguard.android:tunnel handles the crypto/routing
//    using wireguard-go native code bundled in the library AAR.
//  • When no server config is available, WgManager never starts this service,
//    so the app routes traffic normally through the system network stack
//    (which respects any external device-level VPN the user has connected).
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** States of the per-app WireGuard tunnel. */
enum class WgTunnelState { STOPPED, CONNECTING, CONNECTED, ERROR }

class WgVpnService : android.net.VpnService() {

    companion object {
        private const val TAG          = "WgVpnService"
        private const val CHANNEL_ID   = "wg_vpn_channel"
        private const val NOTIF_ID     = 7001
        const val ACTION_START         = "com.linkstreamz.app.WG_START"
        const val ACTION_STOP          = "com.linkstreamz.app.WG_STOP"
        private const val EXTRA_CONFIG = "wg_config_text"

        // Shared tunnel state — observed by WgManager
        private val _state = MutableStateFlow(WgTunnelState.STOPPED)
        val state: StateFlow<WgTunnelState> = _state.asStateFlow()

        // Active backend/tunnel kept for disconnect
        @Volatile private var goBackend: GoBackend? = null
        @Volatile private var activeTunnel: Tunnel?  = null

        fun start(context: Context, configText: String) {
            val i = Intent(context, WgVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, configText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WgVpnService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("WireGuard VPN connecting…"))

        when (intent?.action) {
            ACTION_START -> {
                val configText = intent.getStringExtra(EXTRA_CONFIG)
                if (configText.isNullOrBlank()) {
                    Log.w(TAG, "ACTION_START received with no config — stopping")
                    stopSelf()
                } else {
                    Thread { connectWireGuard(configText) }.start()
                }
            }
            ACTION_STOP  -> disconnect()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // User revoked VPN permission from system settings
        disconnect()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    // ── WireGuard connection ──────────────────────────────────────────────────

    private fun connectWireGuard(configText: String) {
        _state.value = WgTunnelState.CONNECTING
        try {
            val wgCfg = buildWgConfig(configText)
            if (wgCfg == null) {
                Log.e(TAG, "Failed to build WireGuard config from text")
                _state.value = WgTunnelState.ERROR
                stopSelf()
                return
            }

            val backend = GoBackend(applicationContext)
            goBackend = backend

            val tunnel = object : Tunnel {
                override fun getName() = "streamz"
                override fun onStateChange(newState: Tunnel.State) {
                    Log.d(TAG, "Tunnel state changed: $newState")
                    _state.value = when (newState) {
                        Tunnel.State.UP   -> WgTunnelState.CONNECTED
                        Tunnel.State.DOWN -> WgTunnelState.STOPPED
                        else              -> WgTunnelState.CONNECTING
                    }
                    if (newState == Tunnel.State.UP) {
                        updateNotification("WireGuard VPN connected")
                    }
                }
            }
            activeTunnel = tunnel

            // This is blocking — wireguard-go takes over the TUN fd.
            backend.setState(tunnel, Tunnel.State.UP, wgCfg)

        } catch (e: Exception) {
            Log.e(TAG, "WireGuard connection failed", e)
            _state.value = WgTunnelState.ERROR
            stopSelf()
        }
    }

    /**
     * Build a [com.wireguard.config.Config] from raw .conf text using the
     * WireGuard Android library's own builder API.
     *
     * We also set [includeApplication] so only this app's traffic is tunnelled.
     */
    private fun buildWgConfig(configText: String): com.wireguard.config.Config? {
        return try {
            val parsed = WgConfig.parse(configText) ?: return null

            val ifaceBuilder = com.wireguard.config.Interface.Builder()
            ifaceBuilder.parsePrivateKey(parsed.iface.privateKey)
            for (addr in parsed.iface.addresses) {
                ifaceBuilder.parseAddresses(addr)
            }
            for (d in parsed.iface.dns) {
                ifaceBuilder.parseDnsServers(d)
            }
            ifaceBuilder.parseMtu(parsed.iface.mtu.toString())
            // ← Per-app routing: only THIS app's sockets go through the tunnel
            ifaceBuilder.includeApplication(packageName)

            val peerBuilder = com.wireguard.config.Peer.Builder()
            peerBuilder.parsePublicKey(parsed.peer.publicKey)
            parsed.peer.presharedKey?.let { peerBuilder.parsePreSharedKey(it) }
            peerBuilder.parseEndpoint(parsed.peer.endpoint)
            for (ip in parsed.peer.allowedIps) {
                peerBuilder.parseAllowedIPs(ip)
            }
            peerBuilder.parsePersistentKeepalive(parsed.peer.persistentKeepalive.toString())

            com.wireguard.config.Config.Builder()
                .setInterface(ifaceBuilder.build())
                .addPeer(peerBuilder.build())
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Config build error: ${e.message}", e)
            null
        }
    }

    private fun disconnect() {
        try {
            val t = activeTunnel
            val b = goBackend
            if (t != null && b != null) {
                Thread {
                    try { b.setState(t, Tunnel.State.DOWN, null) }
                    catch (e: Exception) { Log.w(TAG, "Error stopping tunnel: ${e.message}") }
                }.start()
            }
        } finally {
            activeTunnel = null
            goBackend    = null
            _state.value  = WgTunnelState.STOPPED
            stopForeground(true)
            stopSelf()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(CHANNEL_ID) == null) {
                mgr?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "VPN Status",
                        NotificationManager.IMPORTANCE_LOW,
                    ).also { it.description = "Shows when the in-app VPN tunnel is active" }
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Link Streamz")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIF_ID, buildNotification(text))
    }
}
