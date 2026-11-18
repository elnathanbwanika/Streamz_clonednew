package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelMtuTest {

    @Test
    fun `without external vpn the configured mtu is used`() {
        assertEquals(1420, TunnelMtu.choose(1420, 1500, externalVpn = false))
        assertEquals(1280, TunnelMtu.choose(1280, 1500, externalVpn = false))
        assertEquals(900, TunnelMtu.choose(900, 1500, externalVpn = false))
    }

    @Test
    fun `external vpn caps at 1280 even on a large link`() {
        assertEquals(1280, TunnelMtu.choose(1420, 1500, externalVpn = true))
        assertEquals(1280, TunnelMtu.choose(1420, null, externalVpn = true))
    }

    @Test
    fun `external vpn reserves overhead below the underlying link mtu`() {
        assertEquals(1220, TunnelMtu.choose(1420, 1300, externalVpn = true))
        assertEquals(1170, TunnelMtu.choose(1420, 1250, externalVpn = true))
    }

    @Test
    fun `external vpn never raises a smaller configured mtu`() {
        assertEquals(1200, TunnelMtu.choose(1200, 1500, externalVpn = true))
        assertEquals(1000, TunnelMtu.choose(1000, 1300, externalVpn = true))
    }

    @Test
    fun `mtu is clamped to the minimum`() {
        assertEquals(576, TunnelMtu.choose(1420, 600, externalVpn = true))
        assertEquals(1420, TunnelMtu.choose(300, 1500, externalVpn = false))
    }

    @Test
    fun `invalid configured mtu falls back to the default`() {
        assertEquals(1420, TunnelMtu.choose(0, 1500, externalVpn = false))
        assertEquals(1280, TunnelMtu.choose(0, 1500, externalVpn = true))
    }
}
