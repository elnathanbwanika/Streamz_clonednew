package com.linkstreamz.app

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackSelection
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Retries a failed media request once or twice on a NEW connection.
 *
 * IP-bound tokens can transiently fail on the first cold-start connection
 * (stale DNS, no default network yet, or a socket opened before an external VPN
 * was selected). A pooled retry is useless, so every retry evicts the pool.
 */
class PlayerRetryPolicy : LoadErrorHandlingPolicy {

    override fun getFallbackSelectionFor(
        fallbackOptions: FallbackOptions,
        info: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): FallbackSelection? = null

    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val responseCode =
            (info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val timedOut = info.exception is SocketTimeoutException ||
            info.exception is InterruptedIOException ||
            info.exception is ConnectException
        val retryableStatus = responseCode == 401 || responseCode == 403 || responseCode == 404

        if ((retryableStatus || timedOut) && info.errorCount <= 2) {
            val delayMs = if (info.errorCount == 1) 1_000L else 3_000L
            HttpClients.evictConnections()
            val reason = when {
                responseCode != null -> "HTTP $responseCode"
                else -> info.exception?.javaClass?.simpleName ?: "connection error"
            }
            AppLogger.d(
                "Retry",
                "stream attempt ${info.errorCount + 1} after $reason — " +
                    "retrying in ${delayMs}ms on a new connection",
            )
            return delayMs
        }

        return if (info.errorCount < 3) 1_000L * info.errorCount else C.TIME_UNSET
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 4
}
