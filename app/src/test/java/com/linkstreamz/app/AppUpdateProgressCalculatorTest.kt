package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateProgressCalculatorTest {
    @Test
    fun `speed is a moving average over the last three seconds`() {
        val tracker = AppUpdateSpeedTracker()
        assertEquals(0L, tracker.record(0L, 0L))
        assertEquals(1_000L, tracker.record(1_000L, 1_000L))
        // 1,500 bytes in 1,250 ms across the window: 1500 * 1000 / 1250.
        assertEquals(1_200L, tracker.record(1_250L, 1_500L))
        // At 4 s the first sample has aged out: (4500 - 1000) / 3 s.
        assertEquals(1_166L, tracker.record(4_000L, 4_500L))
    }

    @Test
    fun `eta uses remaining bytes and current speed`() {
        assertEquals(10L, AppUpdateProgressCalculator.etaSeconds(1_000L, 100L))
        assertEquals(1L, AppUpdateProgressCalculator.etaSeconds(1L, 100L))
        assertEquals(0L, AppUpdateProgressCalculator.etaSeconds(0L, 100L))
        assertNull(AppUpdateProgressCalculator.etaSeconds(1_000L, 0L))
    }

    @Test
    fun `unknown total is indeterminate and has no eta`() {
        val progress = AppUpdateProgressCalculator.snapshot(1_048_576L, null, 2_097_152L)
        assertNull(progress.fraction)
        assertNull(progress.totalBytes)
        assertNull(progress.etaSeconds)
        assertTrue(progress.isIndeterminate)
        assertEquals("1.0 MB downloaded", AppUpdateProgressText.size(progress))
        assertEquals("2.0 MB/s · Estimating time", AppUpdateProgressText.detail(progress))
    }

    @Test
    fun `tiny and huge files clamp the fraction and format sizes`() {
        assertEquals(0.5f, AppUpdateProgressCalculator.fraction(1L, 2L) ?: -1f, 0.0001f)
        assertEquals(1f, AppUpdateProgressCalculator.fraction(5L, 5L) ?: -1f, 0.0001f)
        assertEquals(1f, AppUpdateProgressCalculator.fraction(9L, 5L) ?: -1f, 0.0001f)
        assertEquals(
            0.5f,
            AppUpdateProgressCalculator.fraction(1L shl 40, 1L shl 41) ?: -1f,
            0.0001f,
        )
        assertEquals("0 MB", AppUpdateProgressText.bytes(0L))
        assertEquals("1 KB", AppUpdateProgressText.bytes(1_024L))
        assertEquals("1.0 MB", AppUpdateProgressText.bytes(1_048_576L))
        assertEquals("1.0 GB", AppUpdateProgressText.bytes(1_073_741_824L))
        assertEquals("2.0 GB", AppUpdateProgressText.bytes(2L * 1_073_741_824L))
    }

    @Test
    fun `stalled download decays to zero speed and an unknown eta`() {
        val tracker = AppUpdateSpeedTracker()
        tracker.record(0L, 0L)
        assertEquals(1_000L, tracker.record(1_000L, 1_000L))
        // No new bytes: querying later ages the window out without new samples.
        assertEquals(0L, tracker.current(5_000L))
        assertEquals(0L, tracker.record(4_000L, 1_000L))
        assertEquals(0L, tracker.record(7_000L, 1_000L))

        val progress = AppUpdateProgressCalculator.snapshot(1_000L, 10_000L, 0L)
        assertEquals(0.1f, progress.fraction ?: -1f, 0.0001f)
        assertNull(progress.etaSeconds)
        assertEquals("--/s · Estimating time", AppUpdateProgressText.detail(progress))
        assertFalse(progress.isIndeterminate)
    }

    @Test
    fun `eta text uses seconds and minutes with spaces`() {
        assertEquals("Estimating time", AppUpdateProgressText.eta(null))
        assertEquals("Almost done", AppUpdateProgressText.eta(0L))
        assertEquals("10 s left", AppUpdateProgressText.eta(10L))
        assertEquals("2 min 5 s left", AppUpdateProgressText.eta(125L))
    }
}
