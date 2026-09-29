package com.linkstreamz.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for server fallback ordering and logic.
 *
 * Because [sortServersForFallback] is a top-level function in PlayerScreen.kt
 * (same package), it is directly accessible here.
 *
 * Covered scenarios:
 *  - Index 0 (recommended) stays first
 *  - Remaining servers preserve original relative order
 *  - A single server stays as-is
 *  - Empty list handled without crash
 */
class ServerFallbackTest {

    private fun makeServer(name: String, url: String = "https://example.com/$name") =
        StreamServer(name = name, url = url)

    @Test
    fun `single server list is returned unchanged`() {
        val servers = listOf(makeServer("Server 1"))
        val result = sortServersForFallback(servers)
        assertEquals(1, result.size)
        assertEquals("Server 1", result[0].name)
    }

    @Test
    fun `empty list is handled without crash`() {
        val result = sortServersForFallback(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `index 0 server is first in fallback order`() {
        val servers = listOf(
            makeServer("Recommended"),
            makeServer("Second"),
            makeServer("Third"),
        )
        val result = sortServersForFallback(servers)
        assertEquals("Recommended", result[0].name)
    }

    @Test
    fun `remaining servers preserve original relative order`() {
        val servers = listOf(
            makeServer("First"),
            makeServer("Second"),
            makeServer("Third"),
            makeServer("Fourth"),
        )
        val result = sortServersForFallback(servers)
        assertEquals(4, result.size)
        assertEquals("First", result[0].name)
        assertEquals("Second", result[1].name)
        assertEquals("Third", result[2].name)
        assertEquals("Fourth", result[3].name)
    }

    @Test
    fun `fallback index advances through ordered list`() {
        val servers = listOf(
            makeServer("S1"),
            makeServer("S2"),
            makeServer("S3"),
        )
        val order = sortServersForFallback(servers)

        // Simulate one auto-fallback pass
        var idx = 0
        val visited = mutableListOf<String>()
        while (idx < order.size) {
            visited.add(order[idx].name)
            idx++
        }

        assertEquals(listOf("S1", "S2", "S3"), visited)
    }

    @Test
    fun `StreamServer default fields are backwards compatible`() {
        // Verify that a StreamServer with only name and url compiles and has sane defaults
        val s = StreamServer(name = "Test", url = "https://example.com/stream.m3u8")
        assertTrue(s.headers.isEmpty())
        assertFalse(s.useProxy)
        assertNull(s.delaySeconds)
        assertNull(s.quality)
    }
}
