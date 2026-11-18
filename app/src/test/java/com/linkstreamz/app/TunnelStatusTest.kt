package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelStatusTest {

    private val now = 1_700_000_000_000L

    private fun status(handshakeSec: Long) = TunnelStatus(
        up = true,
        handshakeSec = handshakeSec,
        handshakeNsec = 0,
        txBytes = 10,
        rxBytes = 20,
        endpoint = "198.51.100.7:51820",
        listenPort = 12345,
    )

    @Test
    fun `no handshake has no age and is not fresh`() {
        val s = status(0)
        assertNull(s.handshakeAgeMs(now))
        assertFalse(s.hasFreshHandshake(now))
    }

    @Test
    fun `recent handshake is fresh`() {
        val s = status((now - 45_000L) / 1000L)
        assertEquals(45_000L, s.handshakeAgeMs(now))
        assertTrue(s.hasFreshHandshake(now))
    }

    @Test
    fun `stale handshake is rejected`() {
        val s = status((now - 5 * 60_000L) / 1000L)
        assertFalse(s.hasFreshHandshake(now))
    }

    @Test
    fun `small future clock skew is tolerated`() {
        val s = status((now + 30_000L) / 1000L)
        assertTrue(s.hasFreshHandshake(now))
    }

    @Test
    fun `large future timestamp is rejected`() {
        val s = status((now + 5 * 60_000L) / 1000L)
        assertFalse(s.hasFreshHandshake(now))
    }

    @Test
    fun `custom max age is honoured`() {
        val s = status((now - 10_000L) / 1000L)
        assertTrue(s.hasFreshHandshake(now, maxAgeMs = 20_000L))
        assertFalse(s.hasFreshHandshake(now, maxAgeMs = 5_000L))
    }
}
