package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRequestHeadersTest {

    @Test
    fun `maps configured vlc user agent to full VLC identity`() {
        val headers = PlayerRequestHeaders.build(mapOf("User-Agent" to "vlc"))
        assertEquals(PlayerRequestHeaders.VLC_USER_AGENT, headers["User-Agent"])
    }

    @Test
    fun `does not inject origin or referer`() {
        val headers = PlayerRequestHeaders.build(mapOf("X-Test" to "1"))
        assertFalse(headers.keys.any { it.equals("Origin", ignoreCase = true) })
        assertFalse(headers.keys.any { it.equals("Referer", ignoreCase = true) })
    }

    @Test
    fun `keeps explicitly configured origin and referer`() {
        val headers = PlayerRequestHeaders.build(
            mapOf("Origin" to "https://a.example", "Referer" to "https://a.example/"),
        )
        assertTrue(headers.containsKey("Origin"))
        assertTrue(headers.containsKey("Referer"))
        assertEquals("https://a.example", headers["Origin"])
    }
}
