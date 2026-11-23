package com.linkstreamz.app

import androidx.compose.runtime.saveable.Saver

// ─────────────────────────────────────────────────────────────────────────────
// Filter model + pure helpers
//
// The popover keeps a draft of this state; the screen owns the applied state.
// Every count, chip and result is derived here so none of them can drift.
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

/** Pure predicate shared by the catalogue and the popover's live count. */
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

/** Live "Show N" count for the selected content type. */
fun resultCount(movies: List<Movie>, series: List<Series>, state: MoviesFilterState): Int {
    val (m, s) = filterCatalogue(movies, series, state)
    return if (state.contentType == MediaContentType.MOVIES) m.size else s.size
}

/** VJs only exist on translated titles, so the facet is hidden otherwise. */
fun isVjSectionVisible(state: MoviesFilterState): Boolean =
    state.translatedFilter == TranslatedFilter.TRANSLATED

/** Number of non-default selections, shown as a badge on the trigger button. */
fun activeFilterCount(state: MoviesFilterState): Int = activeFilterChips(state).size

/** One removable toolbar chip per active selection. */
data class ActiveFilterChip(val id: String, val label: String)

fun activeFilterChips(state: MoviesFilterState): List<ActiveFilterChip> {
    val chips = mutableListOf<ActiveFilterChip>()
    if (state.contentType != MediaContentType.MOVIES) {
        chips += ActiveFilterChip(TYPE_ID, state.contentType.label)
    }
    if (state.translatedFilter != TranslatedFilter.TRANSLATED) {
        chips += ActiveFilterChip(LANGUAGE_ID, state.translatedFilter.label)
    }
    state.genres.sortedBy { it.lowercase() }.forEach { chips += ActiveFilterChip("$GENRE_PREFIX$it", it) }
    state.years.sortedDescending().forEach { chips += ActiveFilterChip("$YEAR_PREFIX$it", it.toString()) }
    state.vjFilter?.let { chips += ActiveFilterChip(VJ_ID, it) }
    return chips
}

private const val TYPE_ID = "type"
private const val LANGUAGE_ID = "language"
private const val VJ_ID = "vj"
private const val GENRE_PREFIX = "genre:"
private const val YEAR_PREFIX = "year:"

/** Removes one active selection by its chip id. */
fun removeActiveFilter(state: MoviesFilterState, id: String): MoviesFilterState = when {
    id == TYPE_ID -> state.copy(contentType = MediaContentType.MOVIES)
    id == LANGUAGE_ID -> state.copy(translatedFilter = TranslatedFilter.TRANSLATED, vjFilter = null)
    id == VJ_ID -> state.copy(vjFilter = null)
    id.startsWith(GENRE_PREFIX) ->
        state.copy(genres = state.genres - id.removePrefix(GENRE_PREFIX))
    id.startsWith(YEAR_PREFIX) -> {
        val year = id.removePrefix(YEAR_PREFIX).toIntOrNull()
        if (year == null) state else state.copy(years = state.years - year)
    }
    else -> state
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

/** Keeps filter selections across tab switches without a Parcelable model. */
val MoviesFilterStateSaver = Saver<MoviesFilterState, List<String>>(
    save = { state ->
        listOf(
            state.translatedFilter.name,
            state.contentType.name,
            state.vjFilter.orEmpty(),
            state.genres.sorted().joinToString("\u001f"),
            state.years.sorted().joinToString(","),
        )
    },
    restore = { parts ->
        if (parts.size != 5) {
            null
        } else {
            runCatching {
                MoviesFilterState(
                    translatedFilter = runCatching { TranslatedFilter.valueOf(parts[0]) }
                        .getOrDefault(TranslatedFilter.TRANSLATED),
                    contentType = runCatching { MediaContentType.valueOf(parts[1]) }
                        .getOrDefault(MediaContentType.MOVIES),
                    vjFilter = parts[2].ifEmpty { null },
                    genres = if (parts[3].isEmpty()) emptySet() else parts[3].split('\u001f').toSet(),
                    years = if (parts[4].isEmpty()) emptySet() else parts[4].split(',')
                        .mapNotNull { it.toIntOrNull() }.toSet(),
                )
            }.getOrNull()
        }
    },
)
