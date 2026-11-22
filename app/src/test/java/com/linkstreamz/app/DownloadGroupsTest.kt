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

    @Test fun emptyListGivesNoGroups() {
        assertTrue(groupDownloads(emptyList()).isEmpty())
    }

    @Test fun moviesAreGroupedSeparatelyFromEpisodes() {
        val groups = groupDownloads(
            listOf(item("m1"), item("e1", "episode", "Show", 1, 1), item("m2"))
        )
        assertEquals("movies", groups.first().key)
        assertEquals(2, groups.first().items.size)
        assertTrue(groups.any { it.key == "series:Show" })
    }

    @Test fun episodesAreGroupedBySeriesAndSeason() {
        val groups = groupDownloads(
            listOf(
                item("e2", "episode", "Show", 1, 2),
                item("e1", "episode", "Show", 1, 1),
                item("e3", "episode", "Show", 2, 1),
            )
        )
        val s1 = groups.first { it.key == "series:Show:1" }
        val s2 = groups.first { it.key == "series:Show:2" }
        assertEquals("Season 1", s1.title)
        assertEquals("Season 2", s2.title)
        assertEquals(listOf("e1", "e2"), s1.items.map { it.id })
        assertEquals(1, s2.items.size)
    }

    @Test fun multipleSeriesGetTheirOwnHeaders() {
        val groups = groupDownloads(
            listOf(item("a", "episode", "Show A", 1, 1), item("b", "episode", "Show B", 1, 1))
        )
        assertTrue(groups.any { it.key == "series:Show A" })
        assertTrue(groups.any { it.key == "series:Show B" })
    }

    @Test fun missingSeasonUsesEpisodesLabel() {
        val groups = groupDownloads(listOf(item("e", "episode", "Show", null, 3)))
        assertEquals("Episodes", groups.first { it.key == "series:Show:0" }.title)
    }

    @Test fun groupKeysAreUniqueForManyEpisodesInOneSeason() {
        // Regression: grouping by (series, season, episode) produced one
        // "Season 2" group per episode, all with the same key, which crashed
        // LazyColumn in DownloadsScreen.
        val groups = groupDownloads(
            (1..6).map { item("e$it", "episode", "Mobland", 2, it) }
        )
        val keys = groups.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(1, keys.count { it == "series:Mobland:2" })
        assertEquals(6, groups.first { it.key == "series:Mobland:2" }.items.size)
    }

    @Test fun allGroupKeysAreUniqueAcrossMoviesAndSeries() {
        val groups = groupDownloads(
            listOf(
                item("m1"), item("m2"),
                item("e1", "episode", "Mobland", 1, 1),
                item("e2", "episode", "Mobland", 1, 2),
                item("e3", "episode", "Mobland", 2, 1),
                item("e4", "episode", "Other", 1, 1),
            )
        )
        val keys = groups.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}