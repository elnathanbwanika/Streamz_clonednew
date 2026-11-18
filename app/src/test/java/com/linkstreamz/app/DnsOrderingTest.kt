package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class DnsOrderingTest {

    private fun v4(vararg octets: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(octets[0].toByte(), octets[1].toByte(), octets[2].toByte(), octets[3].toByte()))

    private fun v6(): InetAddress =
        InetAddress.getByName("2606:4700:4700::1111")

    @Test
    fun `prefers ipv4 first when external vpn is active`() {
        val addresses = listOf(v6(), v4(1, 1, 1, 1), v4(8, 8, 8, 8))
        val ordered = DnsOrdering.order(addresses, preferIpv4 = true)
        assertTrue(ordered.first() is Inet4Address)
        assertEquals(ordered.size, addresses.size)
    }

    @Test
    fun `keeps system order when vpn is not active`() {
        val addresses = listOf(v6(), v4(1, 1, 1, 1))
        assertEquals(addresses, DnsOrdering.order(addresses, preferIpv4 = false))
    }

    @Test
    fun `keeps ipv6 when no ipv4 is available`() {
        val addresses = listOf(v6())
        assertEquals(addresses, DnsOrdering.order(addresses, preferIpv4 = true))
    }
}
