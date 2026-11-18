package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// TunnelNetwork.kt
//
// MTU policy for the in-process tunnel.
//
// Without an external VPN app the configured interface MTU is used. When
// another VPN is active its TUN device sits below our UDP sockets and shrinks
// the usable path, so we stay below the underlying link MTU minus tunnel
// overhead and never exceed 1280 bytes.
// ─────────────────────────────────────────────────────────────────────────────

object TunnelMtu {
    /** Outer WireGuard/IP/UDP overhead reserved when an external VPN is active. */
    const val EXTERNAL_VPN_OVERHEAD = 80

    /** Safe maximum for paths that may be re-fragmented by another tunnel. */
    const val EXTERNAL_VPN_MAX = 1280

    /** Smallest MTU we are willing to hand to netstack. */
    const val MIN_MTU = 576

    const val DEFAULT_CONFIG_MTU = 1420

    /**
     * @param configMtu MTU from the WireGuard config (default 1420).
     * @param underlyingMtu MTU of the active network's LinkProperties, if known.
     * @param externalVpn true when the active network has TRANSPORT_VPN.
     */
    fun choose(configMtu: Int, underlyingMtu: Int?, externalVpn: Boolean): Int {
        val configured = if (configMtu in MIN_MTU..65535) configMtu else DEFAULT_CONFIG_MTU

        if (!externalVpn) return configured

        val linkLimited = if (underlyingMtu != null && underlyingMtu > 0) {
            (underlyingMtu - EXTERNAL_VPN_OVERHEAD).coerceAtLeast(MIN_MTU)
        } else {
            configured
        }
        return minOf(configured, linkLimited, EXTERNAL_VPN_MAX).coerceAtLeast(MIN_MTU)
    }
}
