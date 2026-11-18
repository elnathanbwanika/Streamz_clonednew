package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {

    @Test
    fun smallFileUsesSingleSegment() {
        val plan = SegmentPlanner.plan(totalBytes = 1_000L, requestedConnections = 8)
        assertEquals(1, plan.size)
        assertEquals(0L, plan.first().start)
        assertEquals(999L, plan.first().end)
        assertEquals("bytes=0-999", SegmentPlanner.rangeHeader(plan.first()))
    }

    @Test
    fun largeFileSplitsIntoSegmentsAndCoversEveryByte() {
        val total = 100L * 1024 * 1024
        val plan = SegmentPlanner.plan(totalBytes = total, requestedConnections = 8)
        assertEquals(8, plan.size)
        assertEquals(0L, plan.first().start)
        assertEquals(total - 1, plan.last().end)
        plan.zipWithNext().forEach { (a, b) ->
            assertEquals(a.end + 1, b.start)
        }
        assertEquals(total, plan.sumOf { it.length })
    }

    @Test
    fun connectionsAreClampedToSafeRange() {
        val total = 500L * 1024 * 1024
        assertEquals(4, SegmentPlanner.plan(total, requestedConnections = 1).size)
        assertEquals(8, SegmentPlanner.plan(total, requestedConnections = 99).size)
    }

    @Test
    fun progressAndCompletionTrackSegments() {
        val plan = SegmentPlanner.plan(totalBytes = 10L * 1024 * 1024, requestedConnections = 4)
        assertEquals(0L, SegmentPlanner.totalDownloaded(plan))
        assertEquals(0f, SegmentPlanner.progress(plan, 10L * 1024 * 1024))
        plan.forEachIndexed { index, segment ->
            segment.downloaded = if (index == 0) segment.length else 0L
        }
        val first = plan.first()
        assertEquals(first.length, SegmentPlanner.totalDownloaded(plan))
        assertTrue(plan.first().isComplete)
        assertFalse(plan.last().isComplete)
        assertEquals(first.end + 1, first.nextByte)
        assertEquals(
            (first.length.toFloat() / (10L * 1024 * 1024)).coerceIn(0f, 1f),
            SegmentPlanner.progress(plan, 10L * 1024 * 1024),
            0.0001f,
        )
    }
}
