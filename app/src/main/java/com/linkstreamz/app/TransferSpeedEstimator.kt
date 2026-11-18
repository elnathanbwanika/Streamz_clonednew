package com.linkstreamz.app

import java.util.Locale

class TransferSpeedEstimator {
    private var lastBytes: Long = 0L
    private var lastAt: Long = 0L
    private var speed: Long = 0L

    fun update(downloadedBytes: Long, nowMs: Long): Long {
        if (lastAt == 0L) {
            lastBytes = downloadedBytes
            lastAt = nowMs
            return speed
        }
        val elapsed = nowMs - lastAt
        if (elapsed <= 0L) return speed
        if (elapsed < 250L) return speed
        val delta = (downloadedBytes - lastBytes).coerceAtLeast(0L)
        val instantaneous = delta * 1_000L / elapsed
        speed = when {
            speed <= 0L -> instantaneous
            instantaneous <= 0L -> speed / 3L
            else -> (speed * 2L + instantaneous) / 3L
        }
        lastBytes = downloadedBytes
        lastAt = nowMs
        return speed
    }

    fun reset() {
        lastBytes = 0L
        lastAt = 0L
        speed = 0L
    }

    fun etaSeconds(downloadedBytes: Long, totalBytes: Long, currentSpeed: Long = speed): Long {
        if (totalBytes <= 0L || currentSpeed <= 0L) return -1L
        val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0L)
        return (remaining + currentSpeed - 1L) / currentSpeed
    }
}

fun formatTransferSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "--/s"
    bytesPerSecond >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB/s", bytesPerSecond / 1_073_741_824.0)
    bytesPerSecond >= 1_048_576L -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / 1_048_576.0)
    bytesPerSecond >= 1_024L -> String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
    else -> String.format(Locale.US, "%d B/s", bytesPerSecond)
}

fun formatTransferEta(seconds: Long): String = when {
    seconds < 0L -> "Estimating time"
    seconds == 0L -> "Almost done"
    seconds < 60L -> "${seconds}s left"
    else -> "${seconds / 60L}m ${seconds % 60L}s left"
}

fun formatTransferProgressLine(downloadedBytes: Long, totalBytes: Long, speed: Long, etaSeconds: Long): String {
    val speedText = formatTransferSpeed(speed)
    val etaText = formatTransferEta(etaSeconds)
    return if (totalBytes > 0L) {
        "${formatDownloadSize(downloadedBytes)} of ${formatDownloadSize(totalBytes)} · $speedText · $etaText"
    } else {
        "${formatDownloadSize(downloadedBytes)} · $speedText · $etaText"
    }
}

fun formatDownloadSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
}
