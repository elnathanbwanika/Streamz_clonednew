package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoviesFilterTest {

    private fun movie(
        id: String,
        genre: String = "Action",
        year: Int = 2024,
        vj: String? = null,
        translated: Boolean = true,
    ) = Movie(
        id = id,
        title = id,
        genre = genre,
        year = year,
        vj = vj,
        translated = translated,
        thumbUrl = "https://example.com/$id.jpg",
        videoUrl = "https://example.com/$id.mp4",
        duration = "1:30",
    )

    private fun series(id: String, genre: String = "Drama", year: Int = 2023) = Series(
        id = id,
        title = id,
        genre = genre,
        year = year,
        vj = null,
        translated = false,
        thumbUrl = "https://example.com/$id.jpg",
        seasons = emptyList(),
    )

    @Test
    fun languageFilterSeparatesTranslatedAndOriginal() {
        val translated = movie("t", translated = true)
        val original = movie("o", translated = false)

        assertTrue(matchesMoviesFilter(translated, MoviesFilterState()))
        assertFalse(matchesMoviesFilter(original, MoviesFilterState()))
        assertTrue(
            matchesMoviesFilter(
                original,
                MoviesFilterState(translatedFilter = TranslatedFilter.NON_TRANSLATED)
            )
        )
    }

    @Test
    fun genreFilterHandlesMultiValueGenreStrings() {
        val item = movie("g", genre = "Action, Thriller")

        assertTrue(matchesMoviesFilter(item, MoviesFilterState(genres = setOf("Action"))))
        assertTrue(matchesMoviesFilter(item, MoviesFilterState(genres = setOf("thriller"))))
        assertTrue(matchesMoviesFilter(item, MoviesFilterState(genres = setOf("Drama", "Thriller"))))
        assertFalse(matchesMoviesFilter(item, MoviesFilterState(genres = setOf("Drama"))))
    }

    @Test
    fun yearAndVjFiltersAreCombined() {
        val item = movie("v", year = 2022, vj = "VJ Ace")

        assertTrue(matchesMoviesFilter(item, MoviesFilterState(years = setOf(2022))))
        assertFalse(matchesMoviesFilter(item, MoviesFilterState(years = setOf(2023))))
        assertTrue(matchesMoviesFilter(item, MoviesFilterState(vjFilter = "VJ Ace")))
        assertFalse(matchesMoviesFilter(item, MoviesFilterState(vjFilter = "VJ Bee")))
        assertTrue(
            matchesMoviesFilter(
                item,
                MoviesFilterState(years = setOf(2022), vjFilter = "VJ Ace")
            )
        )
    }

    @Test
    fun filterCatalogueReturnsBothTypes() {
        val movies = listOf(movie("a"), movie("b", translated = false))
        val all = listOf(series("s"))

        val (filteredMovies, filteredSeries) = filterCatalogue(movies, all, MoviesFilterState())
        assertEquals(listOf("a"), filteredMovies.map { it.id })
        assertEquals(emptyList<Series>(), filteredSeries)

        val (_, originalSeries) = filterCatalogue(
            movies,
            all,
            MoviesFilterState(translatedFilter = TranslatedFilter.NON_TRANSLATED)
        )
        assertEquals(listOf("s"), originalSeries.map { it.id })
    }

    @Test
    fun activeCountTracksEveryNonDefaultSelection() {
        assertEquals(0, activeFilterCount(MoviesFilterState()))
        assertEquals(
            4,
            activeFilterCount(
                MoviesFilterState(
                    contentType = MediaContentType.SERIES,
                    translatedFilter = TranslatedFilter.NON_TRANSLATED,
                    genres = setOf("Action"),
                    years = setOf(2024),
                )
            )
        )
    }

    @Test
    fun facetsAreDistinctAndSorted() {
        val movies = listOf(movie("a", genre = "Action, Drama", year = 2020))
        val all = listOf(series("s", genre = "drama", year = 2024))

        assertEquals(listOf("Action", "Drama"), availableGenres(movies, all))
        assertEquals(listOf(2024, 2020), availableYears(movies, all))
    }

    @Test
    fun contentTypeSelectsWhichCatalogueIsCounted() {
        val movies = listOf(movie("a"), movie("b"))
        val all = listOf(series("s"), series("t"), series("u"))

        assertEquals(2, resultCount(movies, all, MoviesFilterState()))
        assertEquals(
            3,
            resultCount(
                movies,
                all,
                MoviesFilterState(
                    contentType = MediaContentType.SERIES,
                    translatedFilter = TranslatedFilter.NON_TRANSLATED,
                )
            )
        )
    }

    @Test
    fun liveCountTracksEveryDraftChange() {
        val movies = listOf(
            movie("a", genre = "Action", year = 2024, vj = "VJ Ace"),
            movie("b", genre = "Drama", year = 2023, vj = "VJ Bee"),
            movie("c", genre = "Action", year = 2024, vj = "VJ Ace"),
        )
        assertEquals(3, resultCount(movies, emptyList(), MoviesFilterState()))
        assertEquals(
            2,
            resultCount(movies, emptyList(), MoviesFilterState(genres = setOf("Action")))
        )
        assertEquals(
            2,
            resultCount(movies, emptyList(), MoviesFilterState(years = setOf(2024)))
        )
        assertEquals(
            1,
            resultCount(
                movies,
                emptyList(),
                MoviesFilterState(genres = setOf("Action"), vjFilter = "VJ Bee")
            )
        )
        assertEquals(
            0,
            resultCount(movies, emptyList(), MoviesFilterState(vjFilter = "VJ Nobody"))
        )
    }

    @Test
    fun vjSectionIsOnlyVisibleForTranslatedTitles() {
        assertTrue(isVjSectionVisible(MoviesFilterState()))
        assertFalse(
            isVjSectionVisible(
                MoviesFilterState(translatedFilter = TranslatedFilter.NON_TRANSLATED)
            )
        )
    }

    @Test
    fun activeChipsAndBadgeTrackEverySelection() {
        val state = MoviesFilterState(
            contentType = MediaContentType.SERIES,
            translatedFilter = TranslatedFilter.NON_TRANSLATED,
            genres = setOf("Action", "Drama"),
            years = setOf(2024),
            vjFilter = "VJ Ace",
        )
        val chips = activeFilterChips(state)

        assertEquals(6, chips.size)
        assertEquals(6, activeFilterCount(state))
        assertEquals("Series", chips[0].label)
        assertEquals("Original", chips[1].label)
        assertEquals(listOf("Action", "Drama"), chips.subList(2, 4).map { it.label })
        assertEquals("2024", chips[4].label)
        assertEquals("VJ Ace", chips[5].label)
    }

    @Test
    fun removingChipsRestoresDefaultsOneAtATime() {
        var state = MoviesFilterState(
            contentType = MediaContentType.SERIES,
            translatedFilter = TranslatedFilter.NON_TRANSLATED,
            genres = setOf("Action", "Drama"),
            years = setOf(2024),
            vjFilter = "VJ Ace",
        )
        for (chip in activeFilterChips(state)) {
            state = removeActiveFilter(state, chip.id)
        }
        assertEquals(MoviesFilterState(), state)
        assertEquals(0, activeFilterCount(state))
        assertTrue(activeFilterChips(state).isEmpty())
    }

    @Test
    fun removingLanguageAlsoClearsStaleVj() {
        val state = MoviesFilterState(
            translatedFilter = TranslatedFilter.NON_TRANSLATED,
            vjFilter = "VJ Ace",
        )
        assertEquals(
            MoviesFilterState(),
            removeActiveFilter(state, activeFilterChips(state).first { it.id == "language" }.id)
        )
    }

    @Test
    fun stateSaverRoundTripsSelections() {
        val state = MoviesFilterState(
            contentType = MediaContentType.SERIES,
            translatedFilter = TranslatedFilter.NON_TRANSLATED,
            genres = setOf("Action", "Drama"),
            years = setOf(2020, 2024),
            vjFilter = "VJ Ace",
        )
        val saved = MoviesFilterStateSaver.save(state)
        assertNotNull(saved)
        assertEquals(state, MoviesFilterStateSaver.restore(saved!!))
    }
}
