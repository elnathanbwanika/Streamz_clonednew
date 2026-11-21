package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the resume policy in PlaybackResume.keyFor.
 *
 * The rule the user cares about: EVERY non-live video resumes where it left
 * off, and only live TV / live events start at the live edge. These tests pin
 * that down per target type so a new target cannot silently opt out (or a live
 * one opt in) without failing here.
 *
 * Only the key/eligibility logic is tested — the SharedPreferences read/write
 * needs an Android Context and is covered by instrumentation on device.
 */
class PlaybackResumeTest {

    private fun server(url: String = "https://example.test/v.m3u8") transportStream() =
        StreamServer(name = "Auto", url = url)

    private fun movie(id: String) = Movie(
        id = id,
        title = "Movie $id",
        genre = "Action",
        year = 2024,
        vj = null,
        translated = false,
        thumbUrl = "",
        videoUrl = "https://example.test/$id.m3u8",
        duration = "1:30:00",
    )

    private fun series(id: String) = Series(
        id = id,
        title = "Series $id",
        genre = "Drama",
        year = 2024,
        vj = null,
        translated = false,
        thumbUrl = "",
        seasons = listOf(
            Season(
                number = 2,
                episodes = listOf(
                    Episode(
                        n = 5,
                        title = "Ep 5",
                        thumbUrl = "",
                        videoUrl = "https://example.test/ep.m3u8",
                        duration = "0:45:00",
                    ),
                ),
            ),
        ),
    )

    private fun channel(id: String) = Channel(
        id = id,
        title = "Channel $id",
        category = "Sports",
        country = "KE",
        initials = "CH",
        color1 = "#000000",
        color2 = "#111111",
        program = "Live",
        videoUrl = "https://example.test/live.m3u8",
    )

    private fun event(id: String) = Event(
        id = id,
        sport = "Football",
        league = "EPL",
        status = "LIVE",
        startMs = 0L,
        at = "",
        home = Team(name = "Home", crestClass = "", initials = "H"),
        away = Team(name = "Away", crestClass = "", initials = "A"),
        servers = listOf(server()),
    )

    private fun highlight(id: String) = Highlight(
        id = id,
        eventId = null,
        title = "Highlight $id",
        duration = "0:05:00",
        dateMs = 0L,
        thumb = null,
        home = null,
        away = null,
        servers = listOf(server()),
    )

    // ── Live content must NOT resume ──────────────────────────────────────────

    @Test
    fun liveChannelDoesNotResume() {
        assertNull(PlaybackResume.keyFor(ChannelTarget(channel("c1"))))
        assertFalse(PlaybackResume.isResumable(ChannelTarget(channel("c1"))))
    }

    @Test
    fun liveEventDoesNotResume() {
        assertNull(PlaybackResume.keyFor(EventTarget(event("e1"))))
        assertFalse(PlaybackResume.isResumable(EventTarget(event("e1"))))
    }

    // ── Everything else resumes ───────────────────────────────────────────────

    @Test
    fun movieResumes() {
        assertTrue(PlaybackResume.isResumable(MovieTarget(movie("m1"))))
    }

    @Test
    fun episodeResumes() {
        val s = series("s1")
        assertTrue(
            PlaybackResume.isResumable(
                EpisodeTarget(series = s, season = s.seasons.first(), episode = s.seasons.first().episodes.first()),
            ),
        )
    }

    @Test
    fun highlightResumes() {
        assertTrue(PlaybackResume.isResumable(HighlightTarget(highlight("h1"))))
    }

    @Test
    fun singleMovieDownloadResumes() {
        assertTrue(
            PlaybackResume.isResumable(
                DownloadedTarget(localUri = "content://dl/1", title = "Movie", subtitle = "Downloaded"),
            ),
        )
    }

    @Test
    fun episodeDownloadResumes() {
        assertTrue(
            PlaybackResume.isResumable(
                DownloadedTarget(localUri = "content://dl/2", title = "Series", subtitle = "S1 E3"),
            ),
        )
    }

    @Test
    fun customStreamResumes() {
        assertTrue(PlaybackResume.isResumable(CustomTarget(server("https://example.test/custom.m3u8"))))
    }

    // ── Keys are stable and collision-free ────────────────────────────────────

    @Test
    fun sameMovieGivesSameKey() {
        assertEquals(
            PlaybackResume.keyFor(MovieTarget(movie("m1"))),
            PlaybackResume.keyFor(MovieTarget(movie("m1"))),
        )
    }

    @Test
    fun differentMoviesGiveDifferentKeys() {
        assertNotEquals(
            PlaybackResume.keyFor(MovieTarget(movie("m1"))),
            PlaybackResume.keyFor(MovieTarget(movie("m2"))),
        )
    }

    @Test
    fun movieAndHighlightWithSameIdDoNotCollide() {
        // An id from the movies feed and the same id from the highlights feed are
        // different videos; the kind prefix keeps their positions apart.
        assertNotEquals(
            PlaybackResume.keyFor(MovieTarget(movie("12"))),
            PlaybackResume.keyFor(HighlightTarget(highlight("12"))),
        )
    }

    @Test
    fun differentEpisodesOfSameSeriesGiveDifferentKeys() {
        val s = series("s1")
        val season = s.seasons.first()
        val a = EpisodeTarget(series = s, season = season, episode = season.episodes.first())
        val b = EpisodeTarget(
            series = s,
            season = season,
            episode = season.episodes.first().copy(n = 6),
        )
        assertNotEquals(PlaybackResume.keyFor(a), PlaybackResume.keyFor(b))
    }

    @Test
    fun downloadsWithSameTitleButDifferentFileDoNotCollide() {
        // Two downloads can share a title (same movie, different quality/file);
        // the local URI is what identifies the bytes, so it must be the key.
        assertNotEquals(
            PlaybackResume.keyFor(DownloadedTarget("content://dl/a", "Movie", "Downloaded")),
            PlaybackResume.keyFor(DownloadedTarget("content://dl/b", "Movie", "Downloaded")),
        )
    }

    @Test
    fun differentCustomStreamsGiveDifferentKeys() {
        assertNotEquals(
            PlaybackResume.keyFor(CustomTarget(server("https://example.test/one.m3u8"))),
            PlaybackResume.keyFor(CustomTarget(server("https://example.test/two.m3u8"))),
        )
    }
}
