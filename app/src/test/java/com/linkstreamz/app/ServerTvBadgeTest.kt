package com.linkstreamz.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTvBadgeTest {

    private fun parse(json: String) = parseServerTvBadge(Json.parseToJsonElement(json))

    @Test
    fun falseHidesBadge() {
        assertFalse(parse("false"))
    }

    @Test
    fun trueShowsBadge() {
        assertTrue(parse("true"))
    }

    @Test
    fun missingShowsBadge() {
        assertTrue(parseServerTvBadge(null))
    }

    @Test
    fun nullShowsBadge() {
        assertTrue(parseServerTvBadge(JsonNull))
    }

    @Test
    fun nonBooleanValuesShowBadge() {
        assertTrue(parse("\"false\""))
        assertTrue(parse("0"))
        assertTrue(parse("{}"))
        assertTrue(parse("[]"))
    }

    @Test
    fun serverDefaultsToShowingBadge() {
        assertTrue(StreamServer(name = "Server 1", url = "https://example.com/live.m3u8").showTvBadge)
    }
}
