package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// PlaybackResume.kt
//
// Persists the last playback position for movies so the user can resume where
// they stopped. Uses SharedPreferences (simple key/value, no extra dependencies).
//
// Rules:
//  • Only for MovieTarget — not for live channels, events, or highlights.
//  • Position is saved every few seconds while playing (called from the 250ms tick).
//  • Position is cleared when the movie finishes (within NEAR_END_THRESHOLD_MS of
//    the end) so the next play starts from the beginning — not mid-credits.
//  • A saved position < MIN_RESUME_MS is ignored (treat as "not started").
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context

object PlaybackResume {

    private const val PREFS_NAME         = "playback_resume"
    private const val PREFIX             = "pos_"

    /** Minimum position to bother resuming from (5 seconds). */
    private const val MIN_RESUME_MS      = 5_000L

    /**
     * If the saved position is within this many ms of the duration, treat the
     * movie as "finished" and clear it (next play starts from the beginning).
     * 120 seconds = 2 minutes — skips credits but handles imprecise durations.
     */
    private const val NEAR_END_MS        = 120_000L

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Save the current position for [movieId].
     * Call this from the 250ms position polling loop while the movie is playing.
     * [durationMs] is used to detect "near end" — pass [androidx.media3.common.C.TIME_UNSET]
     * if duration is not yet known.
     */
    fun save(context: Context, movieId: String, positionMs: Long, durationMs: Long) {
        if (positionMs < MIN_RESUME_MS) return // too close to start — don't save
        // If near the end, treat as finished: clear instead of save
        if (durationMs > 0 && durationMs != androidx.media3.common.C.TIME_UNSET &&
            positionMs >= durationMs - NEAR_END_MS) {
            clear(context, movieId)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREFIX + movieId, positionMs)
            .apply()
    }

    /**
     * Mark the movie as fully watched — clears the saved position so next
     * play starts from the beginning.
     */
    fun markFinished(context: Context, movieId: String) {
        clear(context, movieId)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the saved position in milliseconds, or 0 if none (first play or
     * movie was fully watched).
     */
    fun get(context: Context, movieId: String): Long {
        val pos = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREFIX + movieId, 0L)
        return if (pos >= MIN_RESUME_MS) pos else 0L
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun clear(context: Context, movieId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX + movieId)
            .apply()
    }
}
