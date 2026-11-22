package com.linkstreamz.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private sealed interface DownloadsDestination {
    data object Root : DownloadsDestination
    data class SeriesPage(val series: String) : DownloadsDestination
    data class SeasonPage(val series: String, val season: Int) : DownloadsDestination
}

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (DownloadItem) -> Unit,
) {
    val context = LocalContext.current
    val items by DownloadManager.items.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var tab by remember { mutableStateOf(0) }
    var stack by remember {
        mutableStateOf<List<DownloadsDestination>>(listOf(DownloadsDestination.Root))
    }
    val destination = stack.last()

    val movies = remember(items) { downloadMovies(items) }
    val series = remember(items) { downloadSeries(items) }
    val seasonOwner = when (val page = destination) {
        is DownloadsDestination.SeriesPage -> page.series
        is DownloadsDestination.SeasonPage -> page.series
        else -> null
    }
    val seasons = remember(items, seasonOwner) { downloadSeasons(items, seasonOwner ?: "") }
    val episodes = remember(items, destination) {
        val page = destination as? DownloadsDestination.SeasonPage
        if (page != null) downloadEpisodes(items, page.series, page.season) else emptyList()
    }

    fun stepBack() {
        if (stack.size > 1) stack = stack.dropLast(1) else onBack()
    }
    BackHandler { stepBack() }

    // If the last item of an opened series/season is deleted, fall back up.
    LaunchedEffect(items, destination) {
        when (val page = destination) {
            is DownloadsDestination.SeriesPage ->
                if (series.none { it.title == page.series }) stack = listOf(DownloadsDestination.Root)
            is DownloadsDestination.SeasonPage ->
                if (seasons.none { it.number == page.season }) {
                    stack = listOf(DownloadsDestination.Root, DownloadsDestination.SeriesPage(page.series))
                }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(LinkColors.ScreenBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(Icons.Default.ArrowBack, "Back", { stepBack() })
            Spacer(Modifier.width(12.dp))
            val heading = when (val page = destination) {
                DownloadsDestination.Root -> "Downloads" to "${items.size} item${if (items.size == 1) "" else "s"} saved"
                is DownloadsDestination.SeriesPage -> page.series to
                    "${seasons.size} season${if (seasons.size == 1) "" else "s"} · ${series.firstOrNull { it.title == page.series }?.episodeCount ?: 0} episodes"
                is DownloadsDestination.SeasonPage ->
                    (if (page.season > 0) "Season ${page.season}" else "Episodes") to
                        "${episodes.size} episode${if (episodes.size == 1) "" else "s"} · ${page.series}"
            }
            Column {
                Text(heading.first, color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(heading.second, color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp)
            }
        }

        if (destination == DownloadsDestination.Root) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = LsColor.Card,
                contentColor = LinkColors.OrangePrimary,
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Movies  ${movies.size}") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Series  ${series.size}") },
                )
            }
        }

        when (val page = destination) {
            DownloadsDestination.Root -> {
                if (tab == 0) {
                    if (movies.isEmpty()) {
                        DownloadsEmpty("No downloaded movies", "Movies you save appear here.")
                    } else {
                        DownloadItemList(
                            rows = movies,
                            onPlay = onPlay,
                            onPause = { DownloadManager.pause(context, it.id) },
                            onResume = { DownloadManager.resume(context, it.id) },
                            onDelete = { confirmDelete = it },
                        )
                    }
                } else if (series.isEmpty()) {
                    DownloadsEmpty("No downloaded series", "Episodes you save are grouped by series here.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(series, key = { it.title }) { entry ->
                            DownloadSeriesCard(entry) {
                                stack = stack + DownloadsDestination.SeriesPage(entry.title)
                            }
                        }
                    }
                }
            }
            is DownloadsDestination.SeriesPage -> {
                if (seasons.isEmpty()) {
                    DownloadsEmpty("No seasons", "This series has no saved episodes.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(seasons, key = { it.number }) { season ->
                            DownloadSeasonCard(season) {
                                stack = stack + DownloadsDestination.SeasonPage(page.series, season.number)
                            }
                        }
                    }
                }
            }
            is DownloadsDestination.SeasonPage -> {
                if (episodes.isEmpty()) {
                    DownloadsEmpty("No episodes", "Nothing saved in this season.")
                } else {
                    DownloadItemList(
                        rows = episodes,
                        onPlay = onPlay,
                        onPause = { DownloadManager.pause(context, it.id) },
                        onResume = { DownloadManager.resume(context, it.id) },
                        onDelete = { confirmDelete = it },
                    )
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = LsColor.Card,
            title = { Text("Remove download?", color = LinkColors.PrimaryText) },
            text = {
                Text(
                    if (item.isActive) "The download will be cancelled and its partial file removed."
                    else "The saved file will be deleted from this device.",
                    color = LinkColors.MutedText,
                )
            },
            confirmButton = {
                AppButton("Remove", {
                    DownloadManager.remove(context, item)
                    confirmDelete = null
                })
            },
            dismissButton = {
                AppButton("Cancel", { confirmDelete = null }, variant = AppButtonVariant.Secondary)
            },
        )
    }
}

@Composable
private fun DownloadItemList(
    rows: List<DownloadItem>,
    onPlay: (DownloadItem) -> Unit,
    onPause: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rows, key = { it.id }) { item ->
            DownloadRow(
                item = item,
                onOpen = { onPlay(item) },
                onPause = { onPause(item) },
                onResume = { onResume(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun DownloadsEmpty(title: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Download, null, tint = LinkColors.MutedText, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = LinkColors.MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DownloadSeriesCard(entry: DownloadSeries, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(LsShape.Card)
            .background(LsColor.Card)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(64.dp).height(84.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF16110E)),
        ) {
            if (entry.thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = entry.thumbUrl,
                    contentDescription = null,
                    imageLoader = AppImages.direct(LocalContext.current),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                "${entry.seasonCount} season${if (entry.seasonCount == 1) "" else "s"} · ${entry.episodeCount} episode${if (entry.episodeCount == 1) "" else "s"}",
                color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.5.sp,
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = LinkColors.MutedText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DownloadSeasonCard(season: DownloadSeason, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(LsShape.Card)
            .background(LsColor.Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LinkColors.OrangePrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (season.number > 0) season.number.toString() else "–",
                color = LinkColors.OrangePrimary,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (season.number > 0) "Season ${season.number}" else "Unassigned episodes",
                color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
            Text(
                "${season.episodeCount} episode${if (season.episodeCount == 1) "" else "s"} downloaded",
                color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.5.sp,
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = LinkColors.MutedText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(LsShape.Card)
            .background(LsColor.Card)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(64.dp).height(84.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF16110E)),
        ) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                imageLoader = AppImages.direct(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            TvBadgeOverlay(
                placement = item.tvBadge?.card,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = when {
                item.kind == "episode" ->
                    buildList {
                        if (item.season != null && item.season > 0) add("Season ${item.season}")
                        if (item.episode != null) add("Episode ${item.episode}")
                        if (!item.episodeTitle.isNullOrBlank()) add(item.episodeTitle)
                    }.joinToString(" · ").ifBlank { item.seriesTitle ?: item.title }
                else -> "Movie"
            }
            Text(subtitle, color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            if (item.status == DownloadStatus.COMPLETED) {
                Text("${formatBytes(item.downloadedBytes)} · Downloaded", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 9.5.sp)
            } else {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                    color = LinkColors.OrangePrimary,
                    trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    statusText(item),
                    color = if (item.status == DownloadStatus.FAILED) Color(0xFFE57373) else LinkColors.MutedText,
                    fontFamily = MonoFont,
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                item.status == DownloadStatus.COMPLETED && item.fileUri != null -> RowAction(Icons.Default.PlayArrow, "Play", onOpen)
                item.isActive -> RowAction(Icons.Default.Pause, "Pause", onPause)
                else -> RowAction(Icons.Default.Refresh, "Resume", onResume)
            }
            RowAction(Icons.Default.Delete, "Remove", onDelete)
        }
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(LinkColors.OrangePrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = LinkColors.OrangePrimary, modifier = Modifier.size(16.dp))
    }
}

private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Queued · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
    DownloadStatus.DOWNLOADING -> buildString {
        append("${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}")
        if (item.speedBytesPerSecond > 0L) {
            append(" · ${formatTransferSpeed(item.speedBytesPerSecond)}")
            if (item.etaSeconds >= 0L) append(" · ${formatTransferEta(item.etaSeconds)}")
        }
    }
    DownloadStatus.PAUSED -> "Paused · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
    DownloadStatus.FAILED -> UserFacingError.fromStored(item.error)
    DownloadStatus.COMPLETED -> "${formatBytes(item.downloadedBytes)} · Downloaded"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(java.util.Locale.US, "%.2f GB", mb / 1024) else String.format(java.util.Locale.US, "%.1f MB", mb)
}