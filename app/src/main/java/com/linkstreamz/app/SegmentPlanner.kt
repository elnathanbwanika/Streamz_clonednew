package com.linkstreamz.app

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class DownloadSegment(
    val index: Int,
    val start: Long,
    val end: Long,
    var downloaded: Long = 0L,
) {
    val length: Long get() = end - start + 1L
    val remaining: Long get() = max(0L, length - downloaded)
    val nextByte: Long get() = start + downloaded
    val isComplete: Boolean get() = remaining == 0L
}

object SegmentPlanner {
    const val MIN_SEGMENT_BYTES = 256L * 1024L

    fun adaptiveConnections(totalBytes: Long): Int = when {
        totalBytes <= 0L -> 1
        totalBytes < 8L * 1024 * 1024 -> 4
        totalBytes < 64L * 1024 * 1024 -> 6
        else -> 8
    }

    fun plan(
        totalBytes: Long,
        requestedConnections: Int = adaptiveConnections(totalBytes),
        minSegmentBytes: Long = MIN_SEGMENT_BYTES,
    ): List<DownloadSegment> {
        if (totalBytes <= 0L) return emptyList()
        val maxBySize = max(1, ceil(totalBytes.toDouble() / max(1L, minSegmentBytes)).toInt())
        val count = requestedConnections.coerceIn(4, 8).coerceAtMost(maxBySize)
        val base = totalBytes / count
        val remainder = totalBytes % count
        var cursor = 0L
        return (0 until count).map { i ->
            val length = base + if (i < remainder) 1 else 0
            val segment = DownloadSegment(i, cursor, cursor + length - 1)
            cursor += length
            segment
        }
    }

    fun totalDownloaded(segments: List<DownloadSegment>): Long =
        segments.sumOf { min(it.downloaded, it.length) }

    fun progress(segments: List<DownloadSegment>, totalBytes: Long): Float =
        if (totalBytes > 0L) (totalDownloaded(segments).toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    fun mergeCompleted(existing: List<DownloadSegment>, fresh: List<DownloadSegment>): List<DownloadSegment> {
        if (existing.size != fresh.size || existing.any { it.start != fresh[it.index].start || it.end != fresh[it.index].end }) {
            return fresh
        }
        return fresh.map { seg ->
            val done = existing.getOrNull(seg.index)?.downloaded ?: 0L
            seg.copy(downloaded = done.coerceIn(0L, seg.length))
        }
    }

    fun rangeHeader(segment: DownloadSegment): String =
        "bytes=${segment.nextByte}-${segment.end}"
}
