package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// PlaybackResume.kt
//
// Persists the last playback position so the user can resume where they stopped.
// Uses SharedPreferences (simple key/value, no extra dependencies).
//
// WHICH CONTENT RESUMES
//  • Every non-live video: movies, series episodes, highlights, downloads
//    (single movie, episode, whole season) and custom streams.
//  • Never: live TV channels and live events — those have no meaningful
//    position, so they always start at the live edge.
//
// The decision lives in exactly one place, [keyFor], so a new target type can
// never silently opt in or out. Each target gets a stable, collision-free key.
//
// RULES
//  • Position is saved every ~2 s while playing (from the 250ms tick) and on
//    every lifecycle transition / stream teardown.
//  • A saved position within NEAR_END_MS of the end is cleared, so a finished
//    video starts from the beginning instead of mid-credits.
//  • A saved position < MIN_RESUME_MS is ignored (treat as "not started").
//  • Duration 0 / TIME_UNSET means "not known yet" (live HLS, still probing);
//    those saves are ignored so an unknown duration cannot wipe a good position.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import androidx.media3.common.C

object PlaybackResume {

    private const val PREFS_NAME         = "playback_resume"

    /**
     * Prefixes are per content kind, so ids from different sources can never
     * collide (a movie "12" and a highlight "12" are different videos).
     */
    private const val PREFIX_MOVIE       = "movie_"
    private const val PREFIX_EPISODE     = "episode_"
    private const val PREFIX_HIGHLIGHT   = "highlight_"
    private const val PREFIX_DOWNLOAD    = "download_"
    private const val PREFIX_CUSTOM      = "custom_"

    /** Minimum position to bother resuming from (5 seconds). */
    private const val MIN_RESUME_MS      = 5_000L

    /**
     * If the saved position is within this many ms of the duration, treat the
     * video as finished and clear it (next play starts from the beginning).
     * 120 seconds = 2 minutes — skips credits but handles imprecise durations.
     */
    private const val NEAR_END_MS        = 120_000L

    // ── Keys ──────────────────────────────────────────────────────────────────

    /**
     * The stable resume key for [target], or null when it must not resume.
     *
     * Live TV channels and live events are the only exclusions. Highlights are
     * recorded VOD clips and do resume, as do downloads and custom streams.
     */
    fun keyFor(target: PlayerTarget): String? = when (target) {
        // Live — never resume.
        is ChannelTarget, is EventTarget -> null

        is MovieTarget -> PREFIX_MOVIE + target.movie.id
        is HighlightTarget -> PREFIX_HIGHLIGHT + target.highlight.id
        is EpisodeTarget -> PREFIX_EPISODE + target.series.id + "_" +
            target.season.number + "_" + target.episode.n

        // Downloads: key on the local file URI, not the title. Two downloads can
        // share a title (e.g. the same movie re-downloaded), and the URI is what
        // actually identifies the bytes being played.
        is DownloadedTarget -> PREFIX_DOWNLOAD + target.localUri

        // Custom streams: the URL is the only identity we have. A server with no
        // url (should not happen) gets no key rather than a shared "custom_null".
        is CustomTarget -> target.server.url?.let { PREFIX_CUSTOM + it }
    }

    /** True when [target] is allowed to resume from a saved position. */
    fun isResumable(target: PlayerTarget): Boolean = keyFor(target) != null

    // ── Write ──────────────────────────────────────────────────────────────────

    /**
     * Save the current position for [key].
     * Call from the 250ms position tick and before playback is torn down.
     * [durationMs] is used to detect "near end"; pass [C.TIME_UNSET] if unknown.
     */
    fun save(context: Context, key: String, positionMs: Long, durationMs: Long) {
        if (positionMs < MIN_RESUME_MS) return // too close to start — don't save
        // Duration not known yet (live HLS, still probing): keep the old value
        // rather than treat the video as finished and wipe it.
        if (durationMs <= 0L || durationMs == C.TIME_UNSET) return
        // Near the end: treat as finished, clear instead of save.
        if (positionMs >= durationMs - NEAR_END_MS) {
            clear(context, key)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, positionMs)
            .apply()
    }

    /**
     * Mark the video as fully watched — clears the saved position so the next
     * play starts from the beginning.
     */
    fun markFinished(context: Context, key: String) {
        clear(context, key)
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /**
     * Returns the saved position in milliseconds, or 0 if none (first play or
     * the video was fully watched).
     */
    fun get(context: Context, key: String): Long {
        val pos = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(key, 0L)
        return if (pos >= MIN_RESUME_MS) pos else 0L
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun clear(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }
}
