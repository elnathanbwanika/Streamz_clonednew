package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
