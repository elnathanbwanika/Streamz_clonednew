package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Home featured carousel slide builder.
 *
 * The builder is pure, so these pin the ordering and filtering rules without
 * Compose, Android or a network.
 */
class HomeCarouselTest {

    private val NOW = 1_700_000_000_000L
    private val HOUR = 3_600_000L

    private fun event(
        id: String,
        status: String,
        startOffsetMs: Long,
    ) = Event(
        id = id,
        sport = "Football",
        league = "EPL",
        status = status,
        startMs = NOW + startOffsetMs,
        at = "",
        home = Team("Home", "", "H"),
        away = Team("Away", "", "A"),
        servers = emptyList(),
    )

    private fun liveEvent(id: String) = event(id, "live", -30 * 60_000L)

    private fun upcomingEvent(id: String, inMs: Long) = event(id, "upcoming", inMs)

    private fun movie(
        id: String,
        limits: TimeLimited? = null,
    ) = CarouselMovie(
        id = id,
        title = "Title $id",
        genre = "Drama",
        year = 2024,
        vj = "VJ Test",
        thumbUrl = "",
        timeLimited = limits,
        allowDownload = true,
    )

    private fun liveLimit(id: String, endsInMs: Long = HOUR) = TimeLimited(
        startAtMs = NOW - HOUR,
        durationHours = ((endsInMs + HOUR) / HOUR).toInt(),
        allowDownload = true,
    )

    private fun upcomingLimit(id: String, startsInMs: Long = HOUR) = TimeLimited(
        startAtMs = NOW + startsInMs,
        durationHours = 2,
        allowDownload = true,
    )

    private fun endedLimit(id: String) = TimeLimited(
        startAtMs = NOW - 10 * HOUR,
        durationHours = 2,
        allowDownload = true,
    )

    @Test
    fun `round robin order is live event, limited movie, upcoming event, new movie`() {
        val slides = HomeCarousel.build(
            movies = listOf(movie("m1"), movie("m2", liveLimit("m2"))),
            events = listOf(liveEvent("e1"), upcomingEvent("e2", HOUR)),
            nowMs = NOW,
        )
        assertEquals(
            listOf("live-event-e1", "limited-movie-m2", "upcoming-event-e2", "new-movie-m1"),
            slides.map { it.key },
        )
    }

    @Test
    fun `round robin repeats with the next item of each kind`() {
        val slides = HomeCarousel.build(
            movies = listOf(movie("m1"), movie("m2", liveLimit("m2")), movie("m3", liveLimit("m3"))),
            events = listOf(liveEvent("e1"), liveEvent("e2"), upcomingEvent("u1", HOUR)),
            nowMs = NOW,
        )
        assertEquals(
            listOf(
                "live-event-e1", "limited-movie-m2", "upcoming-event-u1", "new-movie-m1",
                "live-event-e2", "limited-movie-m3",
            ),
            slides.map { it.key },
        )
    }

    @Test
    fun `caps at ten slides`() {
        val events = (1..8).map { liveEvent("e$it") }
        val movies = (1..8).map { movie("m$it", liveLimit("m$it")) }
        val slides = HomeCarousel.build(movies = movies, events = events, nowMs = NOW)
        assertEquals(HomeCarousel.MAX_SLIDES, slides.size)
    }

    @Test
    fun `new movies are capped at five`() {
        val movies = (1..12).map { movie("m$it") }
        val slides = HomeCarousel.build(movies = movies, events = emptyList(), nowMs = NOW)
        assertEquals(HomeCarousel.MAX_NEW_MOVIES, slides.size)
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5"), slides.map { (it as CarouselSlide.NewMovie).movieId })
    }

    @Test
    fun `deduplicates by key`() {
        val movies = listOf(movie("m1"), movie("m1"), movie("m1"))
        val slides = HomeCarousel.build(movies = movies, events = emptyList(), nowMs = NOW)
        assertEquals(1, slides.size)
    }

    @Test
    fun `ended time limited movies are excluded everywhere`() {
        val ended = movie("mEnded", endedLimit("mEnded"))
        val slides = HomeCarousel.build(movies = listOf(ended), events = emptyList(), nowMs = NOW)
        assertTrue(slides.isEmpty())
    }

    @Test
    fun `ended time limited movie is not offered as a new movie`() {
        val ended = movie("mEnded", endedLimit("mEnded"))
        val fresh = movie("mFresh")
        val slides = HomeCarousel.build(movies = listOf(ended, fresh), events = emptyList(), nowMs = NOW)
        assertEquals(listOf("new-movie-mFresh"), slides.map { it.key })
    }

    @Test
    fun `finished events are excluded`() {
        val finished = event("eFin", "finished", -5 * HOUR)
        val slides = HomeCarousel.build(movies = emptyList(), events = listOf(finished), nowMs = NOW)
        assertTrue(slides.isEmpty())
    }

    @Test
    fun `upcoming events are soonest first`() {
        val slides = HomeCarousel.build(
            movies = emptyList(),
            events = listOf(
                upcomingEvent("late", 6 * HOUR),
                upcomingEvent("soon", HOUR),
                upcomingEvent("mid", 3 * HOUR),
            ),
            nowMs = NOW,
        )
        assertEquals(
            listOf("upcoming-event-soon", "upcoming-event-mid", "upcoming-event-late"),
            slides.map { it.key },
        )
    }

    @Test
    fun `time limited movies are live first then upcoming`() {
        val slides = HomeCarousel.build(
            movies = listOf(
                movie("up", upcomingLimit("up")),
                movie("live", liveLimit("live")),
            ),
            events = emptyList(),
            nowMs = NOW,
        )
        assertEquals(listOf("limited-movie-live", "limited-movie-up"), slides.map { it.key })
    }

    @Test
    fun `empty kinds are hidden`() {
        val slides = HomeCarousel.build(
            movies = listOf(movie("m1")),
            events = emptyList(),
            nowMs = NOW,
        )
        assertEquals(listOf("new-movie-m1"), slides.map { it.key })
    }

    @Test
    fun `single slide case returns exactly one`() {
        val slides = HomeCarousel.build(
            movies = emptyList(),
            events = listOf(liveEvent("only")),
            nowMs = NOW,
        )
        assertEquals(1, slides.size)
        assertEquals("live-event-only", slides.single().key)
    }

    @Test
    fun `empty case returns no slides`() {
        val slides = HomeCarousel.build(
            movies = emptyList(),
            events = emptyList(),
            nowMs = NOW,
        )
        assertTrue(slides.isEmpty())
    }

    @Test
    fun `live events come before upcoming in their own bucket`() {
        val slides = HomeCarousel.build(
            movies = emptyList(),
            events = listOf(upcomingEvent("u1", HOUR), liveEvent("l1")),
            nowMs = NOW,
        )
        assertEquals(listOf("live-event-l1", "upcoming-event-u1"), slides.map { it.key })
    }

    @Test
    fun `non time limited movies are never treated as limited`() {
        val slides = HomeCarousel.build(
            movies = listOf(movie("plain")),
            events = emptyList(),
            nowMs = NOW,
        )
        assertFalse(slides.any { it is CarouselSlide.TimeLimitedMovie })
        assertEquals(listOf("new-movie-plain"), slides.map { it.key })
    }
}
