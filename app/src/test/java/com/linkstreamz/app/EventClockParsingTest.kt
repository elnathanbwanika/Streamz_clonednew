package com.linkstreamz.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventClockParsingTest {
    private val repo = EventsRepository(preview = true)
    private val json = Json { isLenient = true }

    @Test
    fun minuteAcceptsNumbersAndNumericStrings() {
        assertEquals(37, repo.parseTolerantMinute(JsonPrimitive(37)))
        assertEquals(37, repo.parseTolerantMinute(JsonPrimitive("37")))
        assertEquals(0, repo.parseTolerantMinute(JsonPrimitive("0")))
        assertEquals(45, repo.parseTolerantMinute(JsonPrimitive(45.9)))
    }

    @Test
    fun minuteRejectsGarbageWithoutThrowing() {
        assertNull(repo.parseTolerantMinute(null))
        assertNull(repo.parseTolerantMinute(JsonPrimitive("45+3")))
        assertNull(repo.parseTolerantMinute(JsonPrimitive("garbage")))
        assertNull(repo.parseTolerantMinute(json.parseToJsonElement("{}")))
    }

    @Test
    fun addedTimesUseDefaultsAndClamp() {
        assertEquals(5, repo.parseClampedAdded(null, 5, 0, 5))
        assertEquals(5, repo.parseClampedAdded(JsonPrimitive(99), 5, 0, 5))
        assertEquals(0, repo.parseClampedAdded(JsonPrimitive(-3), 5, 0, 5))
        assertEquals(10, repo.parseClampedAdded(JsonPrimitive("12"), 10, 0, 10))
        assertEquals(3, repo.parseClampedAdded(JsonPrimitive("3"), 10, 0, 10))
    }

    @Test
    fun minuteTextIsPreservedVerbatim() {
        val text = json.parseToJsonElement("\"45+2\"") as JsonPrimitive
        assertEquals("45+2", text.content)
    }
}
