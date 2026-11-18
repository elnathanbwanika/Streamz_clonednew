package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `removes query strings and paths from urls`() {
        val redacted = LogRedactor.redact(
            "GET https://cdn.example.com/live/master.m3u8?token=signed-secret&expires=123 failed",
        )
        assertFalse(redacted.contains("signed-secret"))
        assertFalse(redacted.contains("master.m3u8"))
        assertTrue(redacted.contains("cdn.example.com"))
    }

    @Test
    fun `redacts token shaped parameters`() {
        val redacted = LogRedactor.redact("token=abc123 expires=99 jwt=eyJhbGciOiJIUzI1NiJ9.payload.sig")
        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
    }

    @Test
    fun `keeps redirect host only`() {
        assertEquals(
            "edge.example.com",
            LogRedactor.redirectHost("https://edge.example.com/path/segment.ts?token=secret"),
        )
    }
}
