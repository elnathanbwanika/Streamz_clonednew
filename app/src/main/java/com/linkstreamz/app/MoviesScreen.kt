package com.linkstreamz.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────────────────
// Main Movies Home (rows + filter)
// ─────────────────────────────────────────────────────────────────────────────

private val NEWLY_ADDED_IDS = listOf("mv-1", "sr-1", "mv-4", "sr-2", "mv-2", "sr-3", "mv-9")
private val TRENDING_MOVIE_IDS = listOf("mv-1", "mv-4", "mv-2", "mv-6", "mv-5", "mv-3", "mv-7", "mv-8", "mv-10", "mv-11")

enum class TranslatedFilter(val label: String) { TRANSLATED("Translated"), NON_TRANSLATED("Non-translated") }
enum class MediaContentType(val label: String) { MOVIES("Movies"), SERIES("Series") }

data class MoviesFilterState(
    val translatedFilter: TranslatedFilter = TranslatedFilter.TRANSLATED,
    val contentType: MediaContentType = MediaContentType.MOVIES,
    val vjFilter: String? = null
)

@Composable
fun MoviesScreen(movies: List<Movie>, series: List<Series>, onWatch: (Any) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(MoviesFilterState()) }
    var viewAllRow by remember { mutableStateOf<String?>(null) }

    fun byId(id: String): Any? = movies.find { it.id == id } ?: series.find { it.id == id }

    val filteredMovies = remember(movies, filterState) {
        movies.filter { item ->
            val matchTranslated = when (filterState.translatedFilter) {
                TranslatedFilter.TRANSLATED -> item.translated
                TranslatedFilter.NON_TRANSLATED -> !item.translated
            }
            val matchVj = filterState.vjFilter == null || item.vj == filterState.vjFilter
            matchTranslated && matchVj
        }
    }

    val filteredSeries = remember(series, filterState) {
        series.filter { item ->
            val matchTranslated = when (filterState.translatedFilter) {
                TranslatedFilter.TRANSLATED -> item.translated
                TranslatedFilter.NON_TRANSLATED -> !item.translated
            }
            val matchVj = filterState.vjFilter == null || item.vj == filterState.vjFilter
            matchTranslated && matchVj
        }
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

    val allItems: List<Any> = movies + series
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
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Action row (search + filter)
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search bar
                    Box(
                        Modifier.weight(1f)
                            .background(LinkColors.CardBg, RoundedCornerShape(8.dp))
                            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
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
                    // Filter button
                    val activeFilters = buildList {
                        if (filterState.contentType == MediaContentType.SERIES) add("Series")
                        if (filterState.vjFilter != null) add(filterState.vjFilter!!.replace("VJ ", ""))
                    }
                    Box(
                        Modifier
                            .background(
                                if (activeFilters.isNotEmpty()) LinkColors.OrangePrimary else LinkColors.CardBg,
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { showFilter = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            if (activeFilters.isNotEmpty()) activeFilters.joinToString(" · ") else "Filter",
                            color = if (activeFilters.isNotEmpty()) Color.Black else LinkColors.PrimaryText,
                            fontSize = 13.sp, fontFamily = MonoFont, fontWeight = FontWeight.Medium
                        )
                    }
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
            MoviesFilterDialog(
                state = filterState,
                allVJs = (movies.mapNotNull { it.vj } + series.mapNotNull { it.vj }).distinct().sorted(),
                onDismiss = { showFilter = false },
                onApply = { newState -> filterState = newState; showFilter = false }
            )
        }
    }
}

@Composable
private fun MoviesRowHeader(title: String, onViewAll: () -> Unit) {
    Row(
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
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LinkColors.CardBg)
                    .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.28f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = LinkColors.OrangePrimary, fontSize = 18.sp)
            }
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
    val (id, title, genre, year, thumb, tag, vjLabel) = when (item) {
        is Movie  -> CardData(
            id      = item.id,
            title   = item.title,
            genre   = item.genre,
            year    = item.year,
            thumb   = item.thumbUrl,
            tag     = "MOVIE",
            vjLabel = resolveVjLabel(item.vj, item.translated),
        )
        is Series -> CardData(
            id      = item.id,
            title   = item.title,
            genre   = item.genre,
            year    = item.year,
            thumb   = item.thumbUrl,
            tag     = "SERIES",
            vjLabel = resolveVjLabel(item.vj, item.translated),
        )
        else -> return
    }

    Column(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(116.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(LinkColors.CardBg)
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
            // Fallback play icon shown while image loads / if no thumb
            // (AsyncImage shows nothing on failure — place icon under it so it
            //  only appears when the image is absent)
            Text(
                text     = "▶",
                color    = Color(0xFF3A2A20),
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Center),
            )
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
)

// ─────────────────────────────────────────────────────────────────────────────
// Filter Dialog (centered, not bottom sheet)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MoviesFilterDialog(
    state: MoviesFilterState,
    allVJs: List<String>,
    onDismiss: () -> Unit,
    onApply: (MoviesFilterState) -> Unit
) {
    var draft by remember { mutableStateOf(state) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF111111))
                .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", color = LinkColors.PrimaryText, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = LinkColors.MutedText, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // Translated / Non-translated segmented control
            Text("Content", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            ) {
                TranslatedFilter.entries.forEach { tab ->
                    val selected = draft.translatedFilter == tab
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) LinkColors.OrangePrimary else Color.Transparent)
                            .clickable { draft = draft.copy(translatedFilter = tab) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tab.label, color = if (selected) Color.Black else LinkColors.MutedText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Type (Movies / Series)
            Text("Type", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            ) {
                MediaContentType.entries.forEach { type ->
                    val selected = draft.contentType == type
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) LinkColors.OrangePrimary else Color.Transparent)
                            .clickable { draft = draft.copy(contentType = type) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(type.label, color = if (selected) Color.Black else LinkColors.MutedText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            // VJ filter (only when Translated)
            AnimatedVisibility(visible = draft.translatedFilter == TranslatedFilter.TRANSLATED) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text("VJ", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            VJChip(label = "All", selected = draft.vjFilter == null) { draft = draft.copy(vjFilter = null) }
                        }
                        items(allVJs) { vj ->
                            VJChip(label = vj.replace("VJ ", ""), selected = draft.vjFilter == vj) { draft = draft.copy(vjFilter = vj) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { draft = MoviesFilterState(); onApply(MoviesFilterState()) },
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.4f))
                ) { Text("Reset", color = LinkColors.OrangePrimary) }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LinkColors.OrangePrimary)
                ) { Text("Apply", color = Color.Black, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun VJChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .shadow(if (selected) 4.dp else 0.dp, RoundedCornerShape(999.dp), ambientColor = LinkColors.OrangePrimary.copy(0.3f))
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) LinkColors.OrangePrimary else Color(0xFF0A0A0A))
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = if (selected) 1f else 0.28f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Color.Black else LinkColors.MutedText, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// Keep top-level references for backward-compat
@Composable
fun SectionTitle(title: String) {
    Text(text = title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LinkColors.PrimaryText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}
