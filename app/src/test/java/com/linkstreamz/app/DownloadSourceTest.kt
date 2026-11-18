package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceTest {

    private fun movie(
        videoUrl: String = "https://cdn.example.com/movie.mp4",
        servers: List<StreamServer> = emptyList(),
        allowDownload: Boolean = true,
    ) = Movie(
        id = "mv-1",
        title = "Movie",
        genre = "Action",
        year = 2026,
        vj = null,
        translated = false,
        thumbUrl = "https://cdn.example.com/poster.jpg",
        videoUrl = videoUrl,
        duration = "2h",
        allowDownload = allowDownload,
        servers = servers,
    )

    private fun series(
        allowDownload: Boolean = true,
        servers: List<StreamServer> = emptyList(),
    ) = Series(
        id = "sr-1",
        title = "Series",
        genre = "Drama",
        year = 2026,
        vj = null,
        translated = false,
        thumbUrl = "https://cdn.example.com/poster.jpg",
        seasons = emptyList(),
        allowDownload = allowDownload,
        servers = servers,
    )

    private fun episode(
        videoUrl: String = "https://cdn.example.com/ep.mp4",
        servers: List<StreamServer> = emptyList(),
        allowDownload: Boolean = true,
    ) = Episode(
        n = 1,
        title = "Pilot",
        thumbUrl = "https://cdn.example.com/ep.jpg",
        videoUrl = videoUrl,
        duration = "40m",
        allowDownload = allowDownload,
        servers = servers,
    )

    @Test
    fun normalMovieUsesItsDirectVideoUrl() {
        val value = movie()
        assertTrue(DownloadSupport.canDownload(value))
        assertEquals("https://cdn.example.com/movie.mp4", DownloadSupport.downloadUrl(value))
    }

    @Test
    fun movieWithoutPermissionCannotBeDownloaded() {
        val value = movie(allowDownload = false)
        assertFalse(DownloadSupport.canDownload(value))
        assertNull(DownloadSupport.downloadUrl(value))
    }

    @Test
    fun hlsOnlyMovieHasNoDownloadSource() {
        val value = movie(videoUrl = "https://cdn.example.com/master.m3u8")
        assertFalse(DownloadSupport.canDownload(value))
        assertNull(DownloadSupport.downloadUrl(value))
    }

    @Test
    fun recommendedDirectServerWinsOverEarlierDirectServer() {
        val value = movie(
            servers = listOf(
                StreamServer("Server 1", url = "https://cdn.example.com/first.mp4"),
                StreamServer("Server 2", url = "https://cdn.example.com/recommended.mp4", recommended = true),
            ),
        )
        assertEquals("https://cdn.example.com/recommended.mp4", DownloadSupport.downloadUrl(value))
    }

    @Test
    fun recommendedHlsServerIsSkippedForFirstDirectServer() {
        val value = movie(
            videoUrl = "https://cdn.example.com/fallback.mp4",
            servers = listOf(
                StreamServer("HLS", url = "https://cdn.example.com/master.m3u8", recommended = true),
                StreamServer("Direct", url = "https://cdn.example.com/direct.mkv"),
            ),
        )
        assertEquals("https://cdn.example.com/direct.mkv", DownloadSupport.downloadUrl(value))
    }

    @Test
    fun hlsOnlyServersDoNotFallBackToAnHlsVideoUrl() {
        val value = movie(
            videoUrl = "https://cdn.example.com/master.m3u8",
            servers = listOf(StreamServer("HLS", url = "https://cdn.example.com/alt.m3u8", recommended = true)),
        )
        assertFalse(DownloadSupport.canDownload(value))
        assertNull(DownloadSupport.downloadUrl(value))
    }

    @Test
    fun episodeRespectsSeriesAndEpisodePermissions() {
        val ep = episode()
        assertTrue(DownloadSupport.canDownloadEpisode(series(), ep))
        assertFalse(DownloadSupport.canDownloadEpisode(series(allowDownload = false), ep))
        assertFalse(DownloadSupport.canDownloadEpisode(series(), ep.copy(allowDownload = false)))
    }

    @Test
    fun episodeUsesRecommendedServerThenFallsBackToSeriesServers() {
        val direct = episode(
            servers = listOf(
                StreamServer("Server 1", url = "https://cdn.example.com/ep1.mp4"),
                StreamServer("Server 2", url = "https://cdn.example.com/ep2.mp4", recommended = true),
            ),
        )
        assertEquals("https://cdn.example.com/ep2.mp4", DownloadSupport.downloadUrl(series(), direct))

        val noEpisodeServers = episode(videoUrl = "")
        val withSeriesServers = series(
            servers = listOf(StreamServer("Series", url = "https://cdn.example.com/series.mp4", recommended = true)),
        )
        assertEquals(
            "https://cdn.example.com/series.mp4",
            DownloadSupport.downloadUrl(withSeriesServers, noEpisodeServers),
        )
    }
}
