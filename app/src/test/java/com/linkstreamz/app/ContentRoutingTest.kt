package com.linkstreamz.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRoutingTest {

    private fun channel(country: String) = Channel(
        id = "ch-1",
        title = "Channel",
        category = "News",
        country = country,
        initials = "CH",
        color1 = "#000000",
        color2 = "#111111",
        program = "Now",
        videoUrl = "",
    )

    private fun movie() = Movie(
        id = "mv-1",
        title = "Movie",
        genre = "Action",
        year = 2024,
        vj = null,
        translated = false,
        thumbUrl = "",
        videoUrl = "https://example.com/movie.mp4",
        duration = "",
    )

    private fun series() = Series(
        id = "sr-1",
        title = "Series",
        genre = "Drama",
        year = 2024,
        vj = null,
        translated = false,
        thumbUrl = "",
        seasons = listOf(
            Season(
                number = 1,
                episodes = listOf(Episode(1, "Episode", "", "https://example.com/ep.mp4", "")),
            )
        ),
    )

    private fun event() = Event(
        id = "ev-1",
        sport = "Football",
        league = "League",
        status = "live",
        startMs = null,
        at = "",
        home = Team("Home", "", "H"),
        away = Team("Away", "", "A"),
        servers = listOf(StreamServer("Auto", "https://example.com/event.m3u8")),
    )

    @Test
    fun `local channel is matched case-insensitively with surrounding whitespace`() {
        assertTrue(ContentRouting.isLocalChannel(channel("Uganda")))
        assertTrue(ContentRouting.isLocalChannel(channel(" uganda ")))
        assertFalse(ContentRouting.isLocalChannel(channel("Kenya")))
    }

    @Test
    fun `movies and series bypass the tunnel`() {
        assertTrue(ContentRouting.bypassTunnel(MovieTarget(movie())))
        val series = series()
        assertTrue(ContentRouting.bypassTunnel(EpisodeTarget(series, series.seasons.first(), series.seasons.first().episodes.first())))
    }

    @Test
    fun `only local channels bypass the tunnel`() {
        assertTrue(ContentRouting.bypassTunnel(ChannelTarget(channel("Uganda"))))
        assertFalse(ContentRouting.bypassTunnel(ChannelTarget(channel("Kenya"))))
    }

    @Test
    fun `events highlights and custom streams use the tunnel`() {
        assertFalse(ContentRouting.bypassTunnel(EventTarget(event())))
        assertFalse(
            ContentRouting.bypassTunnel(
                HighlightTarget(
                    Highlight("hl-1", null, "Highlight", "", null, null, null, null, emptyList())
                )
            )
        )
        assertFalse(
            ContentRouting.bypassTunnel(
                CustomTarget(StreamServer("Custom", "https://example.com/custom.m3u8"))
            )
        )
    }
}
