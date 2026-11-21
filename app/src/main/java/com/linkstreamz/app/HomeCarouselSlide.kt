package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// UI-side slide model for the Home featured carousel.
//
// Kept separate from HomeCarousel.kt (the pure builder) so the builder stays
// unit-testable without Compose or Android framework types.
// ─────────────────────────────────────────────────────────────────────────────

/** One rendered slide: the builder's [CarouselSlide] plus the data it displays. */
sealed interface HomeCarouselSlide {
    val key: String

    /** Live or upcoming sports event. */
    data class Sports(val slide: CarouselSlide, val event: Event) : HomeCarouselSlide {
        override val key: String get() = slide.key
        val isLive: Boolean get() = slide is CarouselSlide.LiveEvent
    }

    /** Time-limited movie (LIVE shows time left, UPCOMING shows "Starts in ..."). */
    data class Limited(val movie: CarouselMovie, val limits: MovieLimits) : HomeCarouselSlide {
        override val key: String get() = "limited-movie-${movie.id}"
    }

    /** Recently added movie. */
    data class NewMovie(val movie: CarouselMovie) : HomeCarouselSlide {
        override val key: String get() = "new-movie-${movie.id}"
    }
}

/** The movie plus everything a slide renders, resolved once by the caller. */
data class CarouselMovie(
    val id: String,
    val title: String,
    val genre: String,
    val year: Int,
    val vj: String?,
    val thumbUrl: String,
    val videoUrl: String = "",
    val timeLimited: TimeLimited?,
    val allowDownload: Boolean,
    val servers: List<StreamServer> = emptyList(),
)

/** Rebuilds the domain [Movie] for the existing play/download/details paths. */
fun CarouselMovie.toMovie(): Movie = Movie(
    id = id,
    title = title,
    genre = genre,
    year = year,
    vj = vj,
    translated = false,
    thumbUrl = thumbUrl,
    videoUrl = videoUrl,
    duration = "",
    timeLimited = timeLimited,
    allowDownload = allowDownload,
    servers = servers,
)

/** Resolves the builder's slides against the movie list into renderable slides. */
object HomeCarouselSlides {
    fun resolve(
        slides: List<CarouselSlide>,
        movies: List<CarouselMovie>,
    ): List<HomeCarouselSlide> {
        val byId = movies.associateBy { it.id }
        return slides.mapNotNull { slide ->
            when (slide) {
                is CarouselSlide.LiveEvent -> HomeCarouselSlide.Sports(slide, slide.event)
                is CarouselSlide.UpcomingEvent -> HomeCarouselSlide.Sports(slide, slide.event)
                is CarouselSlide.TimeLimitedMovie -> byId[slide.movieId]
                    ?.let { HomeCarouselSlide.Limited(it, slide.limits) }
                is CarouselSlide.NewMovie -> byId[slide.movieId]
                    ?.let { HomeCarouselSlide.NewMovie(it) }
            }
        }
    }
}
