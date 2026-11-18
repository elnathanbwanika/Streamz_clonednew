package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class TimeLimitedTest {

    private fun movie(
        id: String = "mv-1",
        videoUrl: String = "https://example.com/movie.mp4",
        timeLimited: TimeLimited? = null,
        allowDownload: Boolean = true,
    ) = Movie(
        id = id,
        title = "Movie $id",
        genre = "Action",
        year = 2026,
        vj = null,
        translated = false,
        thumbUrl = "https://example.com/poster.jpg",
        videoUrl = videoUrl,
        duration = "2h",
        timeLimited = timeLimited,
        allowDownload = allowDownload,
    )

    @Test
    fun parsesStartAtWithOffsetAndDuration() {
        val tl = TimeLimitedParser.parse(
            Json.parseToJsonElement(
                """{"startAt":"2026-11-14T18:00:00+03:00","durationHours":168,"allowDownload":true}"""
            )
        )
        requireNotNull(tl)
        assertEquals(168, tl.durationHours)
        assertTrue(tl.allowDownload)
        assertEquals(7L * 24 * 3_600_000, tl.endAtMs - tl.startAtMs)
    }

    @Test
    fun invalidOrMissingTimeLimitedMeansNormalMovie() {
        assertNull(TimeLimitedParser.parse(null))
        assertNull(TimeLimitedParser.parse(Json.parseToJsonElement("\"not an object\"")))
        assertNull(TimeLimitedParser.parse(Json.parseToJsonElement("""{"durationHours":24}""")))
        assertNull(TimeLimitedParser.parse(Json.parseToJsonElement("""{"startAt":"nonsense"}""")))
    }

    @Test
    fun defaultDurationIsOneWeek() {
        val tl = TimeLimitedParser.parse(Json.parseToJsonElement("""{"startAt":"2026-11-14T18:00:00+03:00"}"""))
        requireNotNull(tl)
        assertEquals(168, tl.durationHours)
        assertFalse(tl.allowDownload)
    }

    @Test
    fun statesFollowTheWindow() {
        val start = 1_000_000L
        val tl = TimeLimited(start, 1, true)
        assertEquals(TimeLimitedState.UPCOMING, tl.state(start - 1))
        assertEquals(TimeLimitedState.LIVE, tl.state(start))
        assertEquals(TimeLimitedState.LIVE, tl.state(tl.endAtMs - 1))
        assertEquals(TimeLimitedState.ENDED, tl.state(tl.endAtMs))
    }

    @Test
    fun endedMoviesAreHiddenButUpcomingAreVisible() {
        val now = 2_000_000L
        val live = movie("live", timeLimited = TimeLimited(now - 1_000, 2, true))
        val upcoming = movie("upcoming", timeLimited = TimeLimited(now + 60_000, 2, true))
        val ended = movie("ended", timeLimited = TimeLimited(now - 10_000_000, 1, true))
        val normal = movie("normal")
        val visible = listOf(live, upcoming, ended, normal).visibleTimeLimited(now)
        assertEquals(listOf("live", "upcoming"), visible.map { it.id })
    }

    @Test
    fun blankVideoUrlIsAllowedOnlyForTimeLimitedMovies() {
        val tl = movie(videoUrl = "", timeLimited = TimeLimited(0, 1, true))
        assertEquals("", tl.videoUrl)
        assertTrue(tl.isTimeLimitedEnded(Long.MAX_VALUE))
    }

    @Test
    fun downloadRequiresLiveWindowAndPlainFileAndPermission() {
        val now = 1_000_000L
        val live = movie(timeLimited = TimeLimited(now - 1, 2, true))
        assertTrue(DownloadSupport.canDownload(live, now))

        assertFalse(DownloadSupport.canDownload(live.copy(timeLimited = TimeLimited(now - 1, 2, false)), now))
        assertFalse(DownloadSupport.canDownload(live.copy(timeLimited = TimeLimited(now + 5_000, 2, true)), now))
        assertFalse(DownloadSupport.canDownload(live.copy(videoUrl = "https://example.com/master.m3u8"), now))
        assertFalse(DownloadSupport.canDownload(live.copy(videoUrl = "https://example.com/live.mpd"), now))
        assertFalse(DownloadSupport.canDownload(live.copy(videoUrl = ""), now))
        assertFalse(DownloadSupport.canDownload(movie(allowDownload = false), now))
        assertTrue(DownloadSupport.canDownload(movie(), now))
    }

    @Test
    fun plainFileDetectionAndExtension() {
        assertTrue(DownloadSupport.isPlainFile("https://cdn.example.com/a/b/movie.mp4?token=1"))
        assertTrue(DownloadSupport.isPlainFile("https://cdn.example.com/movie.MKV"))
        assertFalse(DownloadSupport.isPlainFile("https://cdn.example.com/master.m3u8"))
        assertFalse(DownloadSupport.isPlainFile("not a url"))
        assertEquals("mkv", DownloadSupport.extensionFor("https://cdn.example.com/movie.mkv"))
        assertEquals("mp4", DownloadSupport.extensionFor("https://cdn.example.com/play"))
    }

    @Test
    fun bannerPrefersLiveOverUpcomingAndEarliestEnding() {
        val now = 5_000_000L
        val longLive = movie("long", timeLimited = TimeLimited(now - 10_000, 48, true))
        val shortLive = movie("short", timeLimited = TimeLimited(now - 10_000, 2, true))
        val upcoming = movie("soon", timeLimited = TimeLimited(now + 60_000, 24, true))
        val ended = movie("ended", timeLimited = TimeLimited(now - 10_000_000, 1, true))

        val selected = TimeLimitedBannerSelector.select(listOf(longLive, upcoming, shortLive, ended), now)
        assertEquals("short", selected?.id)

        val onlyUpcoming = TimeLimitedBannerSelector.select(listOf(upcoming, ended), now)
        assertEquals("soon", onlyUpcoming?.id)

        assertNull(TimeLimitedBannerSelector.select(listOf(ended), now))
    }

    @Test
    fun serverClockOffsetAppliesToNow() {
        ServerClock.resetForTests()
        assertEquals(0L, ServerClock.offsetMillis())
        ServerClock.resetForTests()
    }

    @Test
    fun countdownAndRemainingText() {
        val tl = TimeLimited(0, 168, true)
        assertEquals("7d 0h left", timeLimitedRemainingText(tl, 0))
        assertTrue(timeLimitedStartCountdown(tl.copy(startAtMs = 3_600_000), 0).contains("h"))
    }

    @Test
    fun fileNamesAreSafeAndStructured() {
        assertEquals("Movie_ A _ B (2026).mp4", FileNames.singleMovie("Movie: A / B (2026)", "mp4"))
        assertEquals("S01E02 - Pilot.mp4", FileNames.episode("Series", 1, 2, "Pilot", "mp4"))
    }
}
