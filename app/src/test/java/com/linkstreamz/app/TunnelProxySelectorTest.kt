package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class TunnelProxySelectorTest {

    private val endpoint = TunnelProxyEndpoint(
        host = "127.0.0.1",
        port = 45678,
        user = "user",
        password = "pass",
    )

    @Test
    fun `routes http and https through proxy while tunnel up`() {
        val selector = TunnelProxySelector({ endpoint }, failClosed = false)

        for (url in listOf("https://example.com/x", "http://example.com/x")) {
            val proxies = selector.select(URI(url))
            assertEquals(1, proxies.size)
            assertEquals(Proxy.Type.HTTP, proxies[0].type())
            val address = proxies[0].address() as InetSocketAddress
            assertEquals("127.0.0.1", address.hostString)
            assertEquals(45678, address.port)
        }
    }

    @Test
    fun `uses direct connection while tunnel down with fail-open`() {
        val selector = TunnelProxySelector({ null }, failClosed = false)
        assertEquals(TunnelProxySelector.DIRECT, selector.select(URI("https://example.com")))
    }

    @Test
    fun `uses dead proxy while tunnel down with fail-closed`() {
        val selector = TunnelProxySelector({ null }, failClosed = true)
        val proxies = selector.select(URI("https://example.com"))
        assertEquals(1, proxies.size)
        val address = proxies[0].address() as InetSocketAddress
        assertEquals(9, address.port)
    }

    @Test
    fun `loopback traffic is always direct`() {
        val selector = TunnelProxySelector({ endpoint }, failClosed = true)
        assertEquals(TunnelProxySelector.DIRECT, selector.select(URI("http://127.0.0.1:1234/x")))
        assertEquals(TunnelProxySelector.DIRECT, selector.select(URI("http://localhost:1234/x")))
        assertEquals(TunnelProxySelector.DIRECT, selector.select(URI("http://[::1]:1234/x")))
    }

    @Test
    fun `non-http schemes are direct`() {
        val selector = TunnelProxySelector({ endpoint }, failClosed = true)
        assertTrue(selector.select(URI("ftp://example.com")).first() == Proxy.NO_PROXY)
    }

    @Test
    fun `default fail-open constant is false`() {
        assertEquals(false, FAIL_CLOSED)
    }
}
