package com.linkstreamz.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Main Movies Home (rows + filter)
// ─────────────────────────────────────────────────────────────────────────────

private val NEWLY_ADDED_IDS = listOf("mv-1", "sr-1", "mv-4", "sr-2", "mv-2", "sr-3", "mv-9")
private val TRENDING_MOVIE_IDS = listOf("mv-1", "mv-4", "mv-2", "mv-6", "mv-5", "mv-3", "mv-7", "mv-8", "mv-10", "mv-11")

@Composable
fun MoviesScreen(
    movies: List<Movie>,
    series: List<Series>,
    onWatch: (Any) -> Unit,
    onDownload: (Movie) -> Unit = {},
    focusLimited: Boolean = false,
    onFocusConsumed: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(MoviesFilterState()) }
    var viewAllRow by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(ServerClock.now()) }

    // Recompute the "limited" ordering once a minute, not every second. The
    // sort key only changes when a window opens/closes, so a 1 s tick rebuilt
    // the whole grid 60x more often than needed for no visible difference.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = ServerClock.now()
        }
    }

    // Ended time-limited movies are dead content: hide them from every rail,
    // grid, filter and search result. A downloaded copy is a local file, so it
    // stays reachable from the Downloads screen.
    val visibleMovies = remember(movies, nowMs) { movies.visibleCatalogue(nowMs) }

    val limitedMovies = remember(visibleMovies, nowMs) {
        visibleMovies.filter { it.timeLimited != null && !it.isTimeLimitedEnded(nowMs) }
            .sortedWith(
                compareBy<Movie> { it.effectiveTimeLimitedState(nowMs) != TimeLimitedState.LIVE }
                    .thenBy { it.timeLimited?.endAtMs ?: Long.MAX_VALUE }
            )
    }

    val listState = rememberLazyListState()
    LaunchedEffect(focusLimited, limitedMovies.size, movies.size) {
        if (focusLimited && limitedMovies.isNotEmpty()) {
            searchQuery = ""
            listState.animateScrollToItem(1)
            onFocusConsumed()
        } else if (focusLimited && movies.isNotEmpty()) {
            onFocusConsumed()
        }
    }

    fun byId(id: String): Any? = visibleMovies.find { it.id == id } ?: series.find { it.id == id }

    val (filteredMovies, filteredSeries) = remember(visibleMovies, series, filterState) {
        filterCatalogue(visibleMovies, series, filterState)
    }

    val newlyAdded = remember(filteredMovies, filteredSeries, filterState) {
        val list = if (filterState.contentType == MediaContentType.MOVIES) filteredMovies else filteredSeries
        list.takeLast(10).reversed()
    }
    val trendingMovies = remember(filteredMovies) { filteredMovies.take(10) }
    val trendingSeries = remember(filteredSeries) { filteredSeries.take(10) }

    if (viewAllRow != null) {
        val (rowTitle, rowItems) = when (viewAllRow) {
            "newly" -> "Newly Added" to newlyAdded
            "trendingMovies" -> "Trending Movies" to trendingMovies
            "trendingSeries" -> "Trending Series" to trendingSeries
            else -> "" to emptyList()
        }
        ViewAllScreen(title = rowTitle, items = rowItems, onBack = { viewAllRow = null }, onWatch = onWatch)
        return
    }

    val allItems: List<Any> = visibleMovies + series
    val searchResults = remember(searchQuery, allItems) {
        if (searchQuery.length < 2) emptyList()
        else allItems.filter { item ->
            when (item) {
                is Movie -> listOf(item.title, item.genre, item.vj.orEmpty(), item.year.toString()).any { it.contains(searchQuery, true) }
                is Series -> listOf(item.title, item.genre, item.vj.orEmpty(), item.year.toString()).any { it.contains(searchQuery, true) }
                else -> false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Action row (search + filter)
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search bar
                    Box(
                        Modifier.weight(1f)
                            .heightIn(min = 48.dp)
                            .background(LinkColors.CardBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = LinkColors.MutedText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Box {
                                if (searchQuery.isEmpty()) Text("Search movies, series or VJs", color = LinkColors.MutedText, fontSize = 13.sp)
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = TextStyle(color = LinkColors.PrimaryText, fontSize = 13.sp, fontFamily = BodyFont),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    // Filter trigger — count badge makes active refinements obvious
                    val activeCount = activeFilterCount(filterState)
                    AppButton(
                        label = "Filters${if (activeCount > 0) " · $activeCount" else ""}",
                        onClick = { showFilter = true },
                        variant = if (activeCount > 0) AppButtonVariant.Primary else AppButtonVariant.Secondary,
                        contentDescription = "Filters",
                    )
                }
            }

            // ── Time-limited rail (always near the top)
            if (limitedMovies.isNotEmpty() && searchQuery.length < 2) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Limited Time", color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.background(LinkColors.OrangePrimary.copy(alpha = 0.14f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("LEAVING SOON", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(limitedMovies, key = { "limited-${it.id}" }) { movie ->
                            TimeLimitedCard(
                                movie = movie,
                                nowMs = ServerClock.now(),
                                onWatch = { onWatch(movie) },
                                onDownload = { onDownload(movie) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ── Search results
            if (searchQuery.length >= 2) {
                item {
                    Text("${searchResults.size} results for \"$searchQuery\"", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(searchResults) { item -> MediaCard(item = item, onClick = { onWatch(item) }) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                // ── Newly Added row
                if (newlyAdded.isNotEmpty()) {
                    item {
                        MoviesRowHeader(title = "Newly Added", onViewAll = { viewAllRow = "newly" })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(newlyAdded) { item -> MediaCard(item = item, onClick = { onWatch(item) }) }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // ── Trending Movies row
                if (trendingMovies.isNotEmpty() && filterState.contentType == MediaContentType.MOVIES) {
                    item {
                        MoviesRowHeader(title = "Trending Movies", onViewAll = { viewAllRow = "trendingMovies" })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(trendingMovies) { item -> MediaCard(item = item, onClick = { onWatch(item) }) }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // ── Trending Series row
                if (trendingSeries.isNotEmpty() && filterState.contentType == MediaContentType.SERIES) {
                    item {
                        MoviesRowHeader(title = "Trending Series", onViewAll = { viewAllRow = "trendingSeries" })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(trendingSeries) { item -> MediaCard(item = item, onClick = { onWatch(item) }) }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        if (showFilter) {
            MoviesFilterSheet(
                state = filterState,
                movies = visibleMovies,
                series = series,
                onDismiss = { showFilter = false },
                onApply = { newState -> filterState = newState; showFilter = false }
            )
        }
    }
}

@Composable
private fun TimeLimitedCard(
    movie: Movie,
    nowMs: Long = ServerClock.now(),
    onWatch: () -> Unit,
    onDownload: () -> Unit,
) {
    val tl = movie.timeLimited ?: return
    val state = movie.effectiveTimeLimitedState(nowMs) ?: return
    val context = LocalContext.current
    Column(
        Modifier.width(178.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LinkColors.CardBg)
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF16110E))) {
            AsyncImage(
                model = movie.thumbUrl,
                contentDescription = null,
                imageLoader = AppImages.direct(context),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.TopStart).padding(7.dp)
                    .background(LinkColors.OrangePrimary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    when (state) {
                        TimeLimitedState.LIVE -> "LIMITED"
                        TimeLimitedState.UPCOMING -> "UPCOMING"
                        TimeLimitedState.ENDED -> "ENDED"
                    },
                    color = Color.Black, fontFamily = MonoFont, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(Modifier.padding(9.dp)) {
            Text(movie.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (state) {
                    TimeLimitedState.LIVE -> timeLimitedRemainingText(tl, nowMs)
                    TimeLimitedState.UPCOMING -> "Starts in ${timeLimitedStartCountdown(tl, nowMs)}"
                    TimeLimitedState.ENDED -> "Window ended"
                },
                color = LinkColors.OrangePrimary,
                fontFamily = MonoFont,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(AppControlSpec.ChipGap)) {
                Box(Modifier.weight(1f)) {
                    AppButton(
                        label = if (state == TimeLimitedState.LIVE) "Watch" else "Details",
                        onClick = onWatch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (DownloadSupport.canDownload(movie, nowMs)) {
                    AppIconButton(Icons.Default.Download, "Download", onDownload)
                }
            }
        }
    }
}

@Composable
private fun MoviesRowHeader(title: String, onViewAll: () -> Unit) {    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "View all",
            color = LinkColors.OrangePrimary,
            fontFamily = MonoFont,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onViewAll)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// View All page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ViewAllScreen(title: String, items: List<Any>, onBack: () -> Unit, onWatch: (Any) -> Unit) {
    Column(Modifier.fillMaxSize().background(LinkColors.ScreenBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(Icons.Default.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text(title, color = LinkColors.PrimaryText, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("${items.size} items", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 11.sp)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                MediaCard(item = item, onClick = { onWatch(item) }, fullWidth = true)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared media card (Movie or Series)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns a normalised VJ display string, or null when the item is not translated /
 * the VJ field is blank / placeholder ("NA", "N/A", "-").
 */
private fun resolveVjLabel(vj: String?, translated: Boolean): String? {
    if (!translated) return null
    val raw = vj?.trim() ?: return null
    if (raw.isBlank() || raw.equals("NA", ignoreCase = true) ||
        raw.equals("N/A", ignoreCase = true) || raw == "-") return null
    return if (raw.startsWith("VJ ", ignoreCase = true)) raw else "VJ $raw"
}

@Composable
fun MediaCard(item: Any, onClick: () -> Unit, fullWidth: Boolean = false) {
    val context = LocalContext.current
    val (id, title, genre, year, thumb, tag, vjLabel, tvBadge) = when (item) {
        is Movie  -> CardData(
            id      = item.id,
            title   = item.title,
            genre   = item.genre,
            year    = item.year,
            thumb   = item.thumbUrl,
            tag     = "MOVIE",
            vjLabel = resolveVjLabel(item.vj, item.translated),
            tvBadge = item.tvBadge,
        )
        is Series -> CardData(
            id      = item.id,
            title   = item.title,
            genre   = item.genre,
            year    = item.year,
            thumb   = item.thumbUrl,
            tag     = "SERIES",
            vjLabel = resolveVjLabel(item.vj, item.translated),
            tvBadge = item.tvBadge,
        )
        else -> return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "mediaCardPressScale",
    )

    Column(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(116.dp))
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(LsShape.Poster)
            .background(LsColor.Card)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
    ) {
        // ── Thumbnail: portrait 3:4, full card width ──────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color(0xFF16110E))
        ) {
            AsyncImage(
                model            = thumb,
                contentDescription = null,
                imageLoader      = AppImages.direct(context),
                contentScale     = ContentScale.Crop,
                modifier         = Modifier.fillMaxSize(),
            )
            // MOVIE / SERIES badge — top-left corner
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        if (tag == "SERIES") Color(0xFF0A0A0A).copy(alpha = 0.85f)
                        else LinkColors.OrangePrimary.copy(alpha = 0.85f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = tag,
                    color      = if (tag == "SERIES") LinkColors.MutedText else Color.Black,
                    fontFamily = MonoFont,
                    fontSize   = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            TvBadgeOverlay(
                placement = tvBadge?.card,
                modifier = Modifier.matchParentSize(),
            )
            (item as? Movie)?.timeLimited?.let { tl ->
                val state = tl.state(ServerClock.now())
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(
                            if (state == TimeLimitedState.LIVE) LinkColors.OrangePrimary else Color(0xFF0A0A0A).copy(alpha = 0.88f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        when (state) {
                            TimeLimitedState.LIVE -> timeLimitedRemainingText(tl)
                            TimeLimitedState.UPCOMING -> "Soon"
                            TimeLimitedState.ENDED -> "Ended"
                        },
                        color = if (state == TimeLimitedState.LIVE) Color.Black else LinkColors.OrangePrimary,
                        fontFamily = MonoFont,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── Info area: fixed 58dp height so every card in a row is the same ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            // Title — 1 line, ellipsis
            Text(
                text       = title,
                color      = LinkColors.PrimaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            // GENRE · YEAR — 1 line, monospace, muted
            Text(
                text          = "$genre · $year",
                color         = LinkColors.MutedText,
                fontFamily    = MonoFont,
                fontSize      = 9.sp,
                maxLines      = 1,
                overflow      = TextOverflow.Ellipsis,
                letterSpacing = 0.05.sp,
            )
            // VJ name — only for translated movies; space always reserved
            Text(
                text       = vjLabel ?: "",
                color      = LinkColors.OrangePrimary,
                fontFamily = MonoFont,
                fontSize   = 9.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class CardData(
    val id:      String,
    val title:   String,
    val genre:   String,
    val year:    Int,
    val thumb:   String,
    val tag:     String,
    val vjLabel: String?,   // null = not translated / no VJ
    val tvBadge: TvBadge?,
)

// Keep top-level references for backward-compat
@Composable
fun SectionTitle(title: String) {
    Text(text = title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LinkColors.PrimaryText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}
