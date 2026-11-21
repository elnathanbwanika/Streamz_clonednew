package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// Home carousel model
//
// Pure data + pure builder. No Compose, no Android framework, no I/O: the
// slide list is a function of the already-loaded feeds and a clock value, so it
// can be unit-tested and a refresh can never trigger a network call.
// ─────────────────────────────────────────────────────────────────────────────

/** One carousel slide. [key] is stable across rebuilds so the pager can keep it. */
sealed interface CarouselSlide {
    val key: String

    data class LiveEvent(val event: Event) : CarouselSlide {
        override val key: String get() = "live-event-${event.id}"
    }

    data class TimeLimitedMovie(val movieId: String, val limits: MovieLimits) : CarouselSlide {
        override val key: String get() = "limited-movie-$movieId"
    }

    data class UpcomingEvent(val event: Event) : CarouselSlide {
        override val key: String get() = "upcoming-event-${event.id}"
    }

    data class NewMovie(val movieId: String) : CarouselSlide {
        override val key: String get() = "new-movie-$movieId"
    }
}

/**
 * The slice of a time-limited movie the builder needs, flattened so the builder
 * stays free of the domain model and easy to test.
 */
data class MovieLimits(
    val startAtMs: Long,
    val endAtMs: Long,
)

object HomeCarousel {

    /** How long each slide stays before sliding on. */
    const val AUTO_SLIDE_MS = 2000L

    /** The horizontal slide animation (ease-in-out). */
    const val SLIDE_ANIM_MS = 450

    /** Pause after the user lets go of the carousel. */
    const val RESUME_AFTER_TOUCH_MS = 4000L

    /** Hard cap on the number of slides. */
    const val MAX_SLIDES = 10

    /** How many of the newest movies the NEW_MOVIE kind may contribute. */
    const val MAX_NEW_MOVIES = 5

    /**
     * Builds the carousel slide list.
     *
     * Inputs are already-loaded feeds; [nowMs] decides which of them are still
     * alive. Order is round-robin over the four kinds in priority order, so the
     * first four slides are one of each kind, then the next item of each kind,
     * and so on. De-duplicates by slide key, drops ended/finished items, drops
     * kinds with no data, and caps the result at [maxSlides].
     *
     * @param movies newest-first, as the feed delivers them.
     * @param events the sports feed.
     */
    fun build(
        movies: List<CarouselMovie>,
        events: List<Event>,
        nowMs: Long = ServerClock.now(),
        maxSlides: Int = MAX_SLIDES,
    ): List<CarouselSlide> {
        if (maxSlides <= 0) return emptyList()

        val liveEvents = events
            .filter { effectiveStatus(it.startMs, it.status, nowMs) == "live" }
            .sortedBy { it.startMs ?: Long.MAX_VALUE }
            .map { CarouselSlide.LiveEvent(it) }

        // Time-limited movies: LIVE first (soonest to end), then UPCOMING
        // (soonest to start). ENDED windows are never shown.
        val limited = movies.filter { it.timeLimited != null }
        val liveLimited = limited
            .filter { it.timeLimited!!.state(nowMs) == TimeLimitedState.LIVE }
            .sortedBy { it.timeLimited!!.endAtMs }
            .map { movie ->
                val tl = movie.timeLimited!!
                CarouselSlide.TimeLimitedMovie(movie.id, MovieLimits(tl.startAtMs, tl.endAtMs))
            }
        val upcomingLimited = limited
            .filter { it.timeLimited!!.state(nowMs) == TimeLimitedState.UPCOMING }
            .sortedBy { it.timeLimited!!.startAtMs }
            .map { movie ->
                val tl = movie.timeLimited!!
                CarouselSlide.TimeLimitedMovie(movie.id, MovieLimits(tl.startAtMs, tl.endAtMs))
            }
        val timeLimited = liveLimited + upcomingLimited

        val upcomingEvents = events
            .filter { effectiveStatus(it.startMs, it.status, nowMs) == "upcoming" }
            .sortedBy { it.startMs ?: Long.MAX_VALUE }
            .map { CarouselSlide.UpcomingEvent(it) }

        // Newest 5 movies, excluding time-limited ones already shown and any
        // movie whose window has ended.
        val shownMovieIds = timeLimited.mapTo(mutableSetOf()) { it.movieId }
        val newMovies = movies
            .filter { it.id !in shownMovieIds }
            .filter { movie ->
                val tl = movie.timeLimited
                tl == null || tl.state(nowMs) != TimeLimitedState.ENDED
            }
            .take(MAX_NEW_MOVIES)
            .map { CarouselSlide.NewMovie(it.id) }

        val buckets = listOf(liveEvents, timeLimited, upcomingEvents, newMovies)
            .filter { it.isNotEmpty() }
        if (buckets.isEmpty()) return emptyList()

        val out = ArrayList<CarouselSlide>(maxSlides)
        val seen = HashSet<String>()
        var index = 0
        while (out.size < maxSlides) {
            var added = false
            for (bucket in buckets) {
                if (index < bucket.size) {
                    val slide = bucket[index]
                    if (seen.add(slide.key)) out.add(slide)
                    added = true
                    if (out.size >= maxSlides) break
                }
            }
            if (!added) break
            index++
        }
        return out
    }
}
