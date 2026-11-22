package com.linkstreamz.app

/** A downloaded series as shown on the Series tab. */
data class DownloadSeries(
    val title: String,
    val thumbUrl: String,
    val seasonCount: Int,
    val episodeCount: Int,
    val latestAt: Long,
)

/** A season inside a downloaded series. */
data class DownloadSeason(
    val number: Int,
    val episodeCount: Int,
)

/** Standalone movies, newest first. */
fun downloadMovies(items: List<DownloadItem>): List<DownloadItem> =
    items.filter { it.kind != "episode" }.sortedByDescending { it.createdAt }

/** Downloaded series, newest activity first. */
fun downloadSeries(items: List<DownloadItem>): List<DownloadSeries> =
    items.filter { it.kind == "episode" }
        .groupBy { it.seriesTitle?.takeIf { t -> t.isNotBlank() } ?: it.title }
        .map { (title, episodes) ->
            val newest = episodes.maxByOrNull { it.createdAt }!!
            DownloadSeries(
                title = title,
                thumbUrl = episodes.asSequence()
                    .map { it.thumbUrl }
                    .filter { it.isNotBlank() }
                    .firstOrNull().orEmpty(),
                seasonCount = episodes.map { it.season ?: 0 }.distinct().size,
                episodeCount = episodes.size,
                latestAt = newest.createdAt,
            )
        }
        .sortedWith(compareByDescending<DownloadSeries> { it.latestAt }
            .thenBy { it.title.lowercase() })

/** Seasons available for one series, in ascending order (0 = unassigned). */
fun downloadSeasons(items: List<DownloadItem>, series: String): List<DownloadSeason> =
    items.filter { it.kind == "episode" && (it.seriesTitle?.takeIf { t -> t.isNotBlank() } ?: it.title) == series }
        .groupBy { it.season ?: 0 }
        .toList()
        .sortedBy { it.first }
        .map { (number, episodes) -> DownloadSeason(number, episodes.size) }

/** Episodes of one season, ordered by episode number. */
fun downloadEpisodes(items: List<DownloadItem>, series: String, season: Int): List<DownloadItem> =
    items.filter {
        it.kind == "episode" &&
            (it.seriesTitle?.takeIf { t -> t.isNotBlank() } ?: it.title) == series &&
            (it.season ?: 0) == season
    }.sortedWith(compareBy<DownloadItem> { it.episode ?: Int.MAX_VALUE }
        .thenBy { it.createdAt })
