package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchClockTest {
    private val kickoff = 1_700_000_000_000L

    private fun at(minutes: Double) = kickoff + (minutes * 60_000).toLong()

    private fun event(
        status: String = "live",
        kickoffMs: Long? = kickoff,
        minute: Int? = null,
        minuteText: String? = null,
        addedFirst: Int = 5,
        addedSecond: Int = 10,
    ) = Event(
        id = "1", sport = "Football", league = "Test", status = status,
        startMs = null, at = "", home = Team("A", "b1", "A"),
        away = Team("B", "b2", "B"), servers = emptyList(),
        minute = minute, minuteText = minuteText, kickoffMs = kickoffMs,
        hasKickoff = kickoffMs != null,
        addedFirst = addedFirst, addedSecond = addedSecond,
    )

    @Test
    fun referenceClockValues() {
        val cases = listOf(
            -1.0 to "0'", 0.0 to "1'", 44.9 to "45'",
            45.0 to "45+1'", 49.9 to "45+5'",
            50.0 to "HT", 64.9 to "HT",
            65.0 to "46'", 109.9 to "90'",
            110.0 to "90+1'", 119.9 to "90+10'", 120.0 to "FT",
        )
        for ((minutes, expected) in cases) {
            assertEquals("at K${minutes}", expected, MatchClock.label(kickoff, 5, 10, at(minutes)))
        }
    }

    @Test
    fun zeroAddedTimes() {
        assertEquals("45'", MatchClock.label(kickoff, 0, 0, at(44.9)))
        assertEquals("HT", MatchClock.label(kickoff, 0, 0, at(45.0)))
        assertEquals("HT", MatchClock.label(kickoff, 0, 0, at(59.9)))
        assertEquals("46'", MatchClock.label(kickoff, 0, 0, at(60.0)))
        assertEquals("90'", MatchClock.label(kickoff, 0, 0, at(104.9)))
        assertEquals("FT", MatchClock.label(kickoff, 0, 0, at(105.0)))
    }

    @Test
    fun statusesFollowClock() {
        assertEquals("upcoming", MatchClock.status(event(), at(-1.0)))
        assertEquals("live", MatchClock.status(event(), at(49.9)))
        assertEquals("live", MatchClock.status(event(), at(55.0))) // HT stays live
        assertEquals("live", MatchClock.status(event(), at(119.9)))
        assertEquals("finished", MatchClock.status(event(), at(120.0))) // FT moves
        assertEquals("finished", MatchClock.status(event(status = "finished"), at(10.0)))
    }

    @Test
    fun adminMinuteWithoutKickoff() {
        assertEquals("45+2", MatchClock.eventLabel(event(kickoffMs = null, minuteText = "45+2")))
        assertEquals("37'", MatchClock.eventLabel(event(kickoffMs = null, minute = 37)))
        assertEquals("0'", MatchClock.eventLabel(event(kickoffMs = null, minute = 0)))
        assertEquals(null, MatchClock.eventLabel(event(kickoffMs = null)))
        assertEquals("live", MatchClock.status(event(kickoffMs = null)))
    }

    @Test
    fun tolerantMinuteParsing() {
        // Mirrors EventsRepository.parseTolerantMinute via the public model.
        assertEquals(37, event(kickoffMs = null, minute = 37).minute)
        assertEquals(0, event(kickoffMs = null, minute = 0).minute)
        assertEquals(null, event(kickoffMs = null).minute)
        assertEquals("45+3", event(kickoffMs = null, minuteText = "45+3").minuteText)
    }

    @Test
    fun addedTimesAreClamped() {
        assertEquals("45+5'", MatchClock.label(kickoff, 99, 99, at(49.9)))
        assertEquals("45+1'", MatchClock.label(kickoff, -5, -5, at(45.0)))
        assertEquals("FT", MatchClock.label(kickoff, 99, 99, at(120.0)))
    }

    @Test
    fun nextTickIsAdaptive() {
        assertEquals(1_000L, MatchClock.nextTickMs(kickoff, 5, 10, at(44.99)))
        val midHalf = MatchClock.nextTickMs(kickoff, 5, 10, at(20.0))
        assertEquals(true, midHalf in 1_000L..60_000L)
    }
}
