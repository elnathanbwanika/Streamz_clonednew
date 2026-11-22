package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGroupsTest {
    private fun item(
        id: String,
        kind: String = "movie",
        series: String? = null,
        season: Int? = null,
        episode: Int? = null,
        created: Long = 0L,
    ) = DownloadItem(
        id = id, movieId = id, title = series ?: "Movie $id", kind = kind,
        seriesTitle = series, season = season, episode = episode,
        episodeTitle = "Episode $episode", tvBadge = null,
        thumbUrl = "", videoUrl = "", fileUri = null, fileName = "$id.mp4",
        relativePath = "", mimeType = "video/mp4", totalBytes = 100L,
        downloadedBytes = 100L, status = DownloadStatus.COMPLETED,
        error = null, createdAt = created, updatedAt = created,
    )

    @Test fun moviesAreSeparateAndNewestFirst() {
        val movies = downloadMovies(listOf(
            item("old", created = 1L),
            item("e", "episode", "Show", 1, 1),
            item("new", created = 2L),
        ))
        assertEquals(listOf("new", "old"), movies.map { it.id })
    }

    @Test fun seriesListHasOneEntryPerSeriesWithCounts() {
        val items = listOf(
            item("a1", "episode", "Mobland", 1, 1, 1L),
            item("a2", "episode", "Mobland", 1, 2, 2L),
            item("a3", "episode", "Mobland", 2, 1, 3L),
            item("b1", "episode", "Other", 1, 1, 4L),
            item("m"),
        )
        val series = downloadSeries(items)
        assertEquals(2, series.size)
        assertEquals("Other", series.first().title)
        val mobland = series.first { it.title == "Mobland" }
        assertEquals(2, mobland.seasonCount)
        assertEquals(3, mobland.episodeCount)
    }

    @Test fun seasonsAreUniqueAndSortedByNumber() {
        val items = (1..6).map { item("e$it", "episode", "Mobland", 2, it) } +
            listOf(item("s1", "episode", "Mobland", 1, 1), item("u", "episode", "Mobland", null, 9))
        val seasons = downloadSeasons(items, "Mobland")
        assertEquals(listOf(0, 1, 2), seasons.map { it.number })
        assertEquals(6, seasons.first { it.number == 2 }.episodeCount)
        assertEquals(seasons.size, seasons.map { it.number }.toSet().size)
    }

    @Test fun episodesAreOrderedByEpisodeNumber() {
        val items = listOf(
            item("e3", "episode", "Mobland", 2, 3),
            item("e1", "episode", "Mobland", 2, 1),
            item("e2", "episode", "Mobland", 2, 2),
            item("other", "episode", "Mobland", 1, 1),
        )
        assertEquals(
            listOf("e1", "e2", "e3"),
            downloadEpisodes(items, "Mobland", 2).map { it.id },
        )
        assertTrue(downloadEpisodes(items, "Missing", 2).isEmpty())
    }
}