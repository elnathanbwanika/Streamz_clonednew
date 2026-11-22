package com.linkstreamz.app

data class DownloadGroup(
    val key: String,
    val title: String,
    val subtitle: String?,
    val items: List<DownloadItem>,
)

/**
 * Groups saved downloads into Movies and Series, with one group per
 * series/season. Uses only metadata already stored on [DownloadItem].
 */
fun groupDownloads(items: List<DownloadItem>): List<DownloadGroup> {
    if (items.isEmpty()) return emptyList()
    val result = mutableListOf<DownloadGroup>()

    val movies = items.filter { it.kind != "episode" }
        .sortedByDescending { it.createdAt }
    if (movies.isNotEmpty()) {
        result += DownloadGroup(
            key = "movies",
            title = "Movies",
            subtitle = "${movies.size} item${if (movies.size == 1) "" else "s"}",
            items = movies,
        )
    }

    val episodeGroups = items.filter { it.kind == "episode" }
        .groupBy { Triple(it.seriesTitle ?: it.title, it.season ?: 0, it.episode ?: 0) }
        .toList()
        .sortedWith(
            compareByDescending<Pair<Triple<String, Int, Int>, List<DownloadItem>>> { pair ->
                pair.second.maxOf { it.createdAt }
            }.thenBy { it.first.first.lowercase() }
                .thenBy { it.first.second }
                .thenBy { it.first.third }
        )

    episodeGroups.groupBy { it.first.first }.forEach { (series, seasons) ->
        val seasonGroups = seasons.map { (key, episodes) ->
            val season = key.second
            DownloadGroup(
                key = "series:$series:$season",
                title = if (season > 0) "Season $season" else "Episodes",
                subtitle = "${episodes.size} episode${if (episodes.size == 1) "" else "s"}",
                items = episodes.sortedBy { it.episode ?: 0 },
            )
        }
        result += DownloadGroup(
            key = "series:$series",
            title = series,
            subtitle = "${seasonGroups.sumOf { it.items.size }} episodes",
            items = emptyList(),
        )
        result.addAll(seasonGroups)
    }
    return result
}