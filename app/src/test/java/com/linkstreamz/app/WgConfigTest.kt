package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class WgConfigTest {

    private val privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val publicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    private val presharedKey = Base64.getEncoder().encodeToString(ByteArray(32) { 3 })

    @Test
    fun `parses hostname endpoint and applies optional defaults`() {
        val text = """
            [Interface]
            PrivateKey = $privateKey
            Address = 10.8.0.2/24, fd00::2/64

            [Peer]
            PublicKey = $publicKey
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()

        val cfg = WgConfig.parse(text)
        assertNotNull(cfg)
        requireNotNull(cfg)
        assertEquals("vpn.example.com:51820", cfg.peer.endpoint)
        assertEquals(1420, cfg.iface.mtu)
        assertEquals(25, cfg.peer.persistentKeepalive)
        assertNull(cfg.peer.presharedKey)
        assertTrue(cfg.iface.dns.isEmpty())

        val endpoint = WgEndpoint.parse(cfg.peer.endpoint)
        assertNotNull(endpoint)
        assertEquals("vpn.example.com", endpoint!!.host)
        assertEquals(51820, endpoint.port)
        assertFalse(endpoint.isIpLiteral())
    }

    @Test
    fun `parses ipv6 endpoint with brackets`() {
        val endpoint = WgEndpoint.parse("[2001:db8::1]:51820")
        assertNotNull(endpoint)
        assertEquals("2001:db8::1", endpoint!!.host)
        assertEquals(51820, endpoint.port)
        assertTrue(endpoint.isIpLiteral())
        assertEquals("[2001:db8::1]:51820", endpoint.format())
    }

    @Test
    fun `parses ipv4 endpoint without brackets`() {
        val endpoint = WgEndpoint.parse("192.0.2.10:1234")
        assertNotNull(endpoint)
        assertEquals("192.0.2.10", endpoint!!.host)
        assertEquals(1234, endpoint.port)
        assertTrue(endpoint.isIpLiteral())
        assertEquals("192.0.2.10:1234", endpoint.format())
    }

    @Test
    fun `rejects malformed endpoints`() {
        assertNull(WgEndpoint.parse(""))
        assertNull(WgEndpoint.parse("hostonly"))
        assertNull(WgEndpoint.parse("host:"))
        assertNull(WgEndpoint.parse("host:0"))
        assertNull(WgEndpoint.parse("host:70000"))
        assertNull(WgEndpoint.parse("[::1]"))
        assertNull(WgEndpoint.parse("[]:80"))
    }

    @Test
    fun `base64 key converts to lowercase hex`() {
        val zeroBase64 = Base64.getEncoder().encodeToString(ByteArray(32))
        assertEquals("0".repeat(64), WgKeys.base64ToHex(zeroBase64))
        assertEquals("0".repeat(64), WgKeys.base64ToHex("0".repeat(64)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects wrong length key`() {
        WgKeys.base64ToHex(Base64.getEncoder().encodeToString(ByteArray(16)))
    }

    @Test
    fun `ipc uses hex keys resolved endpoint and normalized allowed ips`() {
        val text = """
            [Interface]
            PrivateKey = $privateKey
            Address = 10.8.0.2/24
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1280

            [Peer]
            PublicKey = $publicKey
            PresharedKey = $presharedKey
            Endpoint = 198.51.100.7:51820
            AllowedIPs = 10.0.0.0/8, 2001:db8::1
            PersistentKeepalive = 15
        """.trimIndent()

        val cfg = requireNotNull(WgConfig.parse(text))
        val endpoint = requireNotNull(WgEndpoint.parse(cfg.peer.endpoint))
        val ipc = cfg.toIpc(endpoint)

        assertTrue(ipc.contains("private_key=${WgKeys.base64ToHex(privateKey)}"))
        assertTrue(ipc.contains("public_key=${WgKeys.base64ToHex(publicKey)}"))
        assertTrue(ipc.contains("preshared_key=${WgKeys.base64ToHex(presharedKey)}"))
        assertTrue(ipc.contains("endpoint=198.51.100.7:51820"))
        assertTrue(ipc.contains("persistent_keepalive_interval=15"))
        assertTrue(ipc.contains("allowed_ip=10.0.0.0/8"))
        assertTrue(ipc.contains("allowed_ip=2001:db8::1/128"))
        assertEquals("10.8.0.2", cfg.interfaceAddressesCsv())
        assertEquals("1.1.1.1,8.8.8.8", cfg.dnsCsv())
    }

    @Test
    fun `rejects config missing required fields`() {
        assertNull(WgConfig.parse(""))
        assertNull(
            WgConfig.parse(
                """
                [Interface]
                Address = 10.0.0.2/24
                [Peer]
                PublicKey = $publicKey
                Endpoint = 1.2.3.4:51820
                """.trimIndent()
            )
        )
        assertNull(
            WgConfig.parse(
                """
                [Interface]
                PrivateKey = $privateKey
                [Peer]
                PublicKey = $publicKey
                Endpoint = 1.2.3.4:51820
                """.trimIndent()
            )
        )
        assertNull(
            WgConfig.parse(
                """
                [Interface]
                PrivateKey = $privateKey
                Address = 10.0.0.2/24
                [Peer]
                PublicKey = $publicKey
                """.trimIndent()
            )
        )
    }
}
