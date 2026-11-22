package com.linkstreamz.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class InAppMessageTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun root(vararg entries: String): JsonObject =
        json.parseToJsonElement("{\"messages\":[${entries.joinToString(",")}]}") as JsonObject

    private fun one(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }

    private fun base(vararg extra: Pair<String, String>) = one(
        *(listOf(
            "id" to "100",
            "title" to "\"New feature\"",
            "body" to "\"We added downloads.\"",
            "active" to "true",
        ) + extra).toMap().toList().toTypedArray()
    )

    private class FakeSeen(
        private val once: Set<Long> = emptySet(),
        private val daily: Map<Long, Long> = emptyMap(),
    ) : InAppMessageSeenState {
        override fun seenOnce(id: Long): Boolean = id in once
        override fun lastDailyDay(id: Long): Long? = daily[id]
    }

    private fun msg(
        id: Long = 1L,
        frequency: InAppMessageFrequency = InAppMessageFrequency.ONCE,
        start: String? = null,
        end: String? = null,
    ) = InAppMessage(
        id = id,
        title = "t$id",
        body = "b$id",
        style = InAppMessageStyle.INFO,
        image = null,
        buttonLabel = null,
        action = InAppMessageAction.None,
        frequency = frequency,
        startAtMs = start?.let { TimeLimitedParser.parseInstantMs(it) },
        endAtMs = end?.let { TimeLimitedParser.parseInstantMs(it) },
        active = true,
    )

    @Test
    fun parsesEveryField() {
        val parsed = InAppMessageParser.parse(
            root(
                one(
                    "id" to "1795324975331",
                    "title" to "\"New feature\"",
                    "body" to "\"Line one\\nLine two\"",
                    "style" to "\"warn\"",
                    "image" to "\"https://example.com/a.png\"",
                    "button" to "{\"label\":\"Open\",\"action\":{\"type\":\"screen\",\"value\":\"movies\"}}",
                    "frequency" to "\"daily\"",
                    "startAt" to "\"2026-11-20T18:00:00+03:00\"",
                    "endAt" to "\"2026-11-27T18:00:00+03:00\"",
                    "active" to "true",
                )
            )
        ).single()

        assertEquals(1795324975331L, parsed.id)
        assertEquals("New feature", parsed.title)
        assertEquals("Line one\nLine two", parsed.body)
        assertEquals(InAppMessageStyle.WARN, parsed.style)
        assertEquals("https://example.com/a.png", parsed.image)
        assertEquals("Open", parsed.buttonLabel)
        assertEquals(InAppMessageAction.Screen("movies"), parsed.action)
        assertEquals(InAppMessageFrequency.DAILY, parsed.frequency)
        assertEquals(TimeLimitedParser.parseInstantMs("2026-11-20T18:00:00+03:00"), parsed.startAtMs)
        assertEquals(TimeLimitedParser.parseInstantMs("2026-11-27T18:00:00+03:00"), parsed.endAtMs)
        assertTrue(parsed.active)
    }

    @Test
    fun missingOptionalFieldsGetSafeDefaults() {
        val parsed = InAppMessageParser.parse(root(base())).single()
        assertNull(parsed.image)
        assertNull(parsed.buttonLabel)
        assertEquals(InAppMessageAction.None, parsed.action)
        assertEquals(InAppMessageStyle.INFO, parsed.style)
        assertEquals(InAppMessageFrequency.ONCE, parsed.frequency)
        assertNull(parsed.startAtMs)
        assertNull(parsed.endAtMs)
    }

    @Test
    fun malformedAndInactiveEntriesAreIgnored() {
        val parsed = InAppMessageParser.parse(
            root(
                "\"not an object\"",
                "null",
                base("id" to "\"not-a-number\""),
                base("title" to "null"),
                base("body" to "\"   \""),
                base("active" to "false"),
                base("active" to "null"),
                base("startAt" to "\"garbage\""),
            )
        )
        assertEquals(1, parsed.size)
        assertEquals(100L, parsed.single().id)
        assertNull(parsed.single().startAtMs)
    }

    @Test
    fun absentMessagesArrayIsEmpty() {
        assertTrue(InAppMessageParser.parse(json.parseToJsonElement("{}") as JsonObject).isEmpty())
        assertTrue(InAppMessageParser.parseArrayElement(null).isEmpty())
    }

    @Test
    fun actionValidationRejectsBadLinksAndScreens() {
        val parsed = InAppMessageParser.parse(
            root(
                base("button" to "{\"label\":\"A\",\"action\":{\"type\":\"link\",\"value\":\"http://evil.test\"}}"),
                base("id" to "101", "button" to "{\"label\":\"B\",\"action\":{\"type\":\"screen\",\"value\":\"settings\"}}"),
                base("id" to "102", "button" to "{\"label\":\"C\",\"action\":{\"type\":\"link\",\"value\":\"https://safe.test/x\"}}"),
                base("id" to "103", "button" to "{\"label\":\"D\",\"action\":{\"type\":\"none\"}}"),
            )
        )
        assertEquals(InAppMessageAction.None, parsed[0].action)
        assertEquals(InAppMessageAction.None, parsed[1].action)
        assertEquals(InAppMessageAction.Link("https://safe.test/x"), parsed[2].action)
        assertEquals(InAppMessageAction.None, parsed[3].action)
    }

    @Test
    fun eligibilityRespectsEatWindowBoundaries() {
        val start = "2026-11-20T18:00:00+03:00"
        val end = "2026-11-27T18:00:00+03:00"
        val m = msg(start = start, end = end)
        val at = { s: String -> TimeLimitedParser.parseInstantMs(s)!! }

        assertFalse(m.isEligible(at("2026-11-20T17:59:59+03:00")))
        assertTrue(m.isEligible(at("2026-11-20T18:00:00+03:00")))
        assertTrue(m.isEligible(at("2026-11-26T20:00:00+00:00"))) // 23:00 EAT
        assertFalse(m.isEligible(at("2026-11-27T18:00:00+03:00")))
    }

    @Test
    fun eligibilityUsesTheSuppliedAdjustedClock() {
        val m = msg(start = "2026-11-20T18:00:00+03:00")
        val realStart = Instant.parse("2026-11-20T15:00:00Z").toEpochMilli()
        // Device is one hour behind the server; eligibility follows the clock
        // passed in (the server-adjusted one in app), not System.currentTimeMillis().
        assertFalse(m.isEligible(realStart - 3_600_000L))
        assertTrue(m.isEligible(realStart + 1_000L))
    }

    @Test
    fun eatDayRollsAtMidnightEat() {
        val before = TimeLimitedParser.parseInstantMs("2026-11-20T23:59:00+03:00")!!
        val after = TimeLimitedParser.parseInstantMs("2026-11-21T00:01:00+03:00")!!
        val same = TimeLimitedParser.parseInstantMs("2026-11-20T20:00:00+00:00")!! // 23:00 EAT
        assertEquals(InAppMessageTime.eatDay(before), InAppMessageTime.eatDay(same))
        assertTrue(InAppMessageTime.eatDay(after) > InAppMessageTime.eatDay(before))
    }

    @Test
    fun onceShowsOnlyUntilSeen() {
        val m = msg(frequency = InAppMessageFrequency.ONCE)
        assertTrue(shouldShowByFrequency(m, FakeSeen(), 0L))
        assertFalse(shouldShowByFrequency(m, FakeSeen(once = setOf(m.id)), 0L))
    }

    @Test
    fun dailyShowsOncePerEatDay() {
        val m = msg(frequency = InAppMessageFrequency.DAILY)
        val day = InAppMessageTime.eatDay(1_000L)
        assertFalse(shouldShowByFrequency(m, FakeSeen(daily = mapOf(m.id to day)), 1_000L))
        assertTrue(shouldShowByFrequency(m, FakeSeen(daily = mapOf(m.id to day - 1)), 1_000L))
    }

    @Test
    fun alwaysShowsEveryLaunch() {
        val m = msg(frequency = InAppMessageFrequency.ALWAYS)
        val seen = FakeSeen(once = setOf(m.id), daily = mapOf(m.id to InAppMessageTime.eatDay(1_000L)))
        assertTrue(shouldShowByFrequency(m, seen, 1_000L))
    }

    @Test
    fun changingIdMakesItANewMessage() {
        val old = msg(id = 10L, frequency = InAppMessageFrequency.ONCE)
        val seen = FakeSeen(once = setOf(old.id))
        assertFalse(shouldShowByFrequency(old, seen, 0L))
        val new = msg(id = 11L, frequency = InAppMessageFrequency.ONCE)
        assertTrue(shouldShowByFrequency(new, seen, 0L))
    }

    @Test
    fun queueIsNewestFirstAndSkipsShownMessages() {
        val messages = listOf(msg(1L), msg(3L), msg(2L))
        assertEquals(
            3L,
            InAppMessageQueue.next(messages, FakeSeen(), 0L, emptySet())?.id
        )
        assertEquals(
            2L,
            InAppMessageQueue.next(messages, FakeSeen(), 0L, setOf(3L))?.id
        )
    }

    @Test
    fun queueStopsAfterThreePerSession() {
        val messages = (1L..6L).map { msg(it) }
        assertEquals(6L, InAppMessageQueue.next(messages, FakeSeen(), 0L, emptySet())?.id)
        assertNull(InAppMessageQueue.next(messages, FakeSeen(), 0L, setOf(6L, 5L, 4L)))
        assertEquals(3L, InAppMessageQueue.next(messages, FakeSeen(), 0L, setOf(6L, 5L))?.id)
    }

    @Test
    fun queueSkipsIneligibleAndAlreadySeenMessages() {
        val future = msg(id = 9L, start = "2099-01-01T00:00:00+03:00")
        val seenOnce = msg(id = 8L, frequency = InAppMessageFrequency.ONCE)
        val next = InAppMessageQueue.next(
            listOf(future, seenOnce, msg(7L)),
            FakeSeen(once = setOf(8L)),
            0L,
            emptySet(),
        )
        assertEquals(7L, next?.id)
    }
}
