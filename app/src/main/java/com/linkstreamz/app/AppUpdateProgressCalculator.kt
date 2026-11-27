package com.linkstreamz.app

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Immutable view of one progress tick. A null total means the server did not
 * report a length, so the bar must be indeterminate. A null ETA means no speed
 * sample exists yet, so the UI shows a dash instead of a guessed time.
 */
data class UpdateProgressSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etaSeconds: Long?,
) {
    val fraction: Float? get() = AppUpdateProgressCalculator.fraction(downloadedBytes, totalBytes)
    val percent: Int get() = ((fraction ?: 0f) * 100f).roundToInt().coerceIn(0, 100)
    val isIndeterminate: Boolean get() = fraction == null
    val remainingBytes: Long? get() = totalBytes?.let { (it - downloadedBytes).coerceAtLeast(0L) }
}

object AppUpdateProgressCalculator {
    const val SPEED_WINDOW_MS = 3_000L

    fun fraction(downloadedBytes: Long, totalBytes: Long?): Float? {
        val total = totalBytes ?: return null
        if (total <= 0L) return null
        return (downloadedBytes.coerceAtLeast(0L).toDouble() / total.toDouble())
            .coerceIn(0.0, 1.0).toFloat()
    }

    fun etaSeconds(remainingBytes: Long, bytesPerSecond: Long): Long? {
        if (remainingBytes <= 0L) return 0L
        if (bytesPerSecond <= 0L) return null
        return Math.ceil(remainingBytes.toDouble() / bytesPerSecond.toDouble()).toLong()
    }

    fun snapshot(
        downloadedBytes: Long,
        totalBytes: Long?,
        bytesPerSecond: Long,
    ): UpdateProgressSnapshot {
        val done = downloadedBytes.coerceAtLeast(0L)
        val total = totalBytes?.takeIf { it > 0L }
        val speed = bytesPerSecond.coerceAtLeast(0L)
        return UpdateProgressSnapshot(
            downloadedBytes = done,
            totalBytes = total,
            bytesPerSecond = speed,
            etaSeconds = total?.let { etaSeconds((it - done).coerceAtLeast(0L), speed) },
        )
    }
}

/**
 * Moving average over a sliding time window. Samples are recorded by the
 * download engine every 250 ms; querying [current] also ages the window, so a
 * stalled download decays to zero speed even if no new bytes arrive.
 */
class AppUpdateSpeedTracker(
    private val windowMs: Long = AppUpdateProgressCalculator.SPEED_WINDOW_MS,
) {
    private data class Sample(val atMs: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()

    fun record(nowMs: Long, bytes: Long): Long {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (samples.isEmpty() || nowMs > samples.last().atMs) {
            samples.addLast(Sample(nowMs, safeBytes))
        } else if (nowMs == samples.last().atMs) {
            samples.removeLast()
            samples.addLast(Sample(nowMs, safeBytes))
        }
        return compute(nowMs)
    }

    fun current(nowMs: Long): Long = compute(nowMs)

    fun reset() {
        samples.clear()
    }

    private fun compute(nowMs: Long): Long {
        if (samples.isEmpty()) return 0L
        val cutoff = nowMs - windowMs
        // Keep one sample at/before the cutoff as the baseline for the window.
        while (samples.size >= 2 && samples[1].atMs <= cutoff) samples.removeFirst()
        val first = samples.first()
        val last = samples.last()
        val elapsed = maxOf(nowMs, last.atMs) - first.atMs
        if (elapsed <= 0L) return 0L
        val delta = (last.bytes - first.bytes).coerceAtLeast(0L)
        if (delta <= 0L) return 0L
        return (delta.toDouble() * 1000.0 / elapsed.toDouble()).toLong()
    }
}

object AppUpdateProgressText {
    fun bytes(value: Long): String = when {
        value <= 0L -> "0 MB"
        value >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", value / 1_073_741_824.0)
        value >= 1_048_576L -> String.format(Locale.US, "%.1f MB", value / 1_048_576.0)
        else -> String.format(Locale.US, "%.0f KB", value / 1024.0)
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0L) "--/s" else "${bytes(bytesPerSecond)}/s"

    fun eta(seconds: Long?): String = when {
        seconds == null -> "Estimating time"
        seconds <= 0L -> "Almost done"
        seconds < 60L -> "$seconds s left"
        else -> "${seconds / 60L} min ${seconds % 60L} s left"
    }

    fun size(progress: UpdateProgressSnapshot): String = when {
        progress.totalBytes != null ->
            "${bytes(progress.downloadedBytes)} / ${bytes(progress.totalBytes)}"
        else -> "${bytes(progress.downloadedBytes)} downloaded"
    }

    fun detail(progress: UpdateProgressSnapshot): String =
        "${speed(progress.bytesPerSecond)} · ${eta(progress.etaSeconds)}"
}
