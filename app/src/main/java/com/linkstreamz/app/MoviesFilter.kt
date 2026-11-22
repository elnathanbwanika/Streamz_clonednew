package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Filter model + pure helpers
// ─────────────────────────────────────────────────────────────────────────────

enum class TranslatedFilter(val label: String) {
    TRANSLATED("Translated"),
    NON_TRANSLATED("Original"),
}

enum class MediaContentType(val label: String) {
    MOVIES("Movies"),
    SERIES("Series"),
}

data class MoviesFilterState(
    val translatedFilter: TranslatedFilter = TranslatedFilter.TRANSLATED,
    val contentType: MediaContentType = MediaContentType.MOVIES,
    val vjFilter: String? = null,
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
)

private data class FilterDescriptor(
    val genreTokens: Set<String>,
    val year: Int,
    val vj: String?,
    val translated: Boolean,
)

private fun genreTokens(value: String): Set<String> =
    value.split(',', '|', '/')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("NA", ignoreCase = true) }
        .toSet()

private fun descriptorOf(item: Any): FilterDescriptor? = when (item) {
    is Movie -> FilterDescriptor(genreTokens(item.genre), item.year, item.vj, item.translated)
    is Series -> FilterDescriptor(genreTokens(item.genre), item.year, item.vj, item.translated)
    else -> null
}

/** Pure predicate shared by the catalogue and the sheet's live count. */
fun matchesMoviesFilter(item: Any, state: MoviesFilterState): Boolean {
    val d = descriptorOf(item) ?: return false
    val matchTranslated = when (state.translatedFilter) {
        TranslatedFilter.TRANSLATED -> d.translated
        TranslatedFilter.NON_TRANSLATED -> !d.translated
    }
    val matchVj = state.vjFilter == null || d.vj == state.vjFilter
    val matchGenre = state.genres.isEmpty() ||
        state.genres.any { selected -> d.genreTokens.any { it.equals(selected, ignoreCase = true) } }
    val matchYear = state.years.isEmpty() || d.year in state.years
    return matchTranslated && matchVj && matchGenre && matchYear
}

fun filterCatalogue(
    movies: List<Movie>,
    series: List<Series>,
    state: MoviesFilterState,
): Pair<List<Movie>, List<Series>> =
    movies.filter { matchesMoviesFilter(it, state) } to
        series.filter { matchesMoviesFilter(it, state) }

/** Number of non-default selections, shown as a badge on the trigger button. */
fun activeFilterCount(state: MoviesFilterState): Int {
    var count = 0
    if (state.contentType != MediaContentType.MOVIES) count++
    if (state.translatedFilter != TranslatedFilter.TRANSLATED) count++
    if (state.vjFilter != null) count++
    count += state.genres.size
    count += state.years.size
    return count
}

fun availableGenres(movies: List<Movie>, series: List<Series>): List<String> =
    (movies.map { it.genre } + series.map { it.genre })
        .flatMap { genreTokens(it) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

fun availableYears(movies: List<Movie>, series: List<Series>): List<Int> =
    (movies.map { it.year } + series.map { it.year })
        .distinct()
        .sortedDescending()

fun availableVJs(movies: List<Movie>, series: List<Series>): List<String> =
    (movies.mapNotNull { it.vj } + series.mapNotNull { it.vj })
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("NA", ignoreCase = true) && !it.equals("N/A", ignoreCase = true) && it != "-" }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

// ─────────────────────────────────────────────────────────────────────────────
// Filter sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesFilterSheet(
    state: MoviesFilterState,
    movies: List<Movie>,
    series: List<Series>,
    onDismiss: () -> Unit,
    onApply: (MoviesFilterState) -> Unit,
) {
    var draft by remember(state) { mutableStateOf(state) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val genres = remember(movies, series) { availableGenres(movies, series) }
    val years = remember(movies, series) { availableYears(movies, series) }
    val vjs = remember(movies, series) { availableVJs(movies, series) }

    val resultCount = remember(draft, movies, series) {
        val (m, s) = filterCatalogue(movies, series, draft)
        if (draft.contentType == MediaContentType.MOVIES) m.size else s.size
    }
    val resultLabel = if (draft.contentType == MediaContentType.MOVIES) "movies" else "series"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LsColor.Surface,
        contentColor = LsColor.Text,
        shape = LsShape.Sheet,
        scrimColor = LsColor.Scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(LsShape.Pill)
                    .background(LsColor.FillSubtle)
            )
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FILTERS",
                        color = LsColor.Text,
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        "Refine your catalogue",
                        color = LsColor.TextDim,
                        fontFamily = BodyFont,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    Modifier.size(36.dp)
                        .clip(LsShape.Chip)
                        .background(LsColor.FillSubtle)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, "Close filters", tint = LsColor.Text, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Hairline()

            // ── Scrollable facets
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                FilterSection("SHOW") {
                    Segment(
                        options = MediaContentType.entries.toList(),
                        selected = draft.contentType,
                        label = { it.label },
                    ) { draft = draft.copy(contentType = it) }
                }

                FilterSection("LANGUAGE") {
                    Segment(
                        options = TranslatedFilter.entries.toList(),
                        selected = draft.translatedFilter,
                        label = { it.label },
                    ) { language ->
                        // VJs only exist on translated titles — drop a stale VJ
                        // selection so it cannot silently empty the results.
                        draft = if (language == TranslatedFilter.NON_TRANSLATED) {
                            draft.copy(translatedFilter = language, vjFilter = null)
                        } else {
                            draft.copy(translatedFilter = language)
                        }
                    }
                }

                if (genres.isNotEmpty()) {
                    FilterSection("GENRE", "Pick one or more") {
                        ChipRow(genres) { genre ->
                            val selected = genre in draft.genres
                            FilterChoiceChip(
                                label = genre,
                                selected = selected,
                                onClick = {
                                    draft = draft.copy(
                                        genres = if (selected) draft.genres - genre else draft.genres + genre
                                    )
                                },
                            )
                        }
                    }
                }

                if (years.isNotEmpty()) {
                    FilterSection("RELEASE YEAR") {
                        ChipRow(years) { year ->
                            val selected = year in draft.years
                            FilterChoiceChip(
                                label = year.toString(),
                                selected = selected,
                                onClick = {
                                    draft = draft.copy(
                                        years = if (selected) draft.years - year else draft.years + year
                                    )
                                },
                            )
                        }
                    }
                }

                if (draft.translatedFilter == TranslatedFilter.TRANSLATED && vjs.isNotEmpty()) {
                    FilterSection("VJ", "Translated titles only") {
                        ChipRow(listOf(null) + vjs) { vj ->
                            FilterChoiceChip(
                                label = vj ?: "All",
                                selected = draft.vjFilter == vj,
                            ) { draft = draft.copy(vjFilter = vj) }
                        }
                    }
                }
            }

            Hairline()

            // ── Sticky footer
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .height(48.dp)
                        .clip(LsShape.Button)
                        .border(1.dp, LsColor.FillSubtle, LsShape.Button)
                        .clickable { onApply(MoviesFilterState()) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reset", color = LsColor.Text, fontFamily = BodyFont, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(LsShape.Button)
                        .background(LsColor.Accent)
                        .clickable { onApply(draft) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Show $resultCount $resultLabel",
                        color = LsColor.OnAccent,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LsColor.FillSubtle))
}

@Composable
private fun FilterSection(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                color = LsColor.TextDim,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            if (hint != null) {
                Text(hint, color = LsColor.TextDim.copy(alpha = 0.6f), fontFamily = BodyFont, fontSize = 10.sp)
            }
        }
        content()
    }
}

@Composable
private fun <T> Segment(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(LsShape.Chip)
            .background(LsColor.Bg)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(LsShape.Chip)
                    .background(if (isSelected) LsColor.Accent else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) LsColor.OnAccent else LsColor.TextDim,
                    fontFamily = BodyFont,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, chip: @Composable (T) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(items) { item -> chip(item) }
    }
}

@Composable
private fun FilterChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(LsShape.Chip)
            .background(if (selected) LsColor.Accent else LsColor.Card)
            .border(1.dp, if (selected) LsColor.Accent else LsColor.FillSubtle, LsShape.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            color = if (selected) LsColor.OnAccent else LsColor.Text,
            fontFamily = BodyFont,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                null,
                tint = LsColor.OnAccent,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
