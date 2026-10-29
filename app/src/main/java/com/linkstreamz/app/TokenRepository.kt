package com.linkstreamz.app

import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * P4/P5 – In-memory token cache with per-channel single-flight, expiry-aware
 * staleness, and on-demand / scheduled invalidation.
 *
 * Staleness rule: stale if `now >= exp - 90s`, or (malformed token) if minted > 2 min ago.
 * Single-flight: one [Mutex] per channel; concurrent callers wait for the same mint.
 * Jitter: 300–1200 ms delay before each mint to avoid stampeding.
 */
class TokenRepository(
    private val minter: TokenMinter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cache      = ConcurrentHashMap<String, TokenRecord>()
    private val locks      = ConcurrentHashMap<String, Mutex>()
    private val playerUrls = ConcurrentHashMap<String, String>()

    /** The channel currently shown in the player — used as a fallback key for segment URIs. */
    @Volatile var activeChannel: String? = null

    /** Register a channel id → player page URL mapping before calling [token] or [mint]. */
    fun register(channel: String, playerUrl: String) {
        playerUrls[channel] = playerUrl
    }

    fun peek(channel: String): TokenRecord? = cache[channel]

    /** Return a valid (non-stale) [TokenRecord], minting if necessary. */
    suspend fun token(channel: String): TokenRecord {
        cache[channel]?.let { if (!stale(it)) return it }
        return mint(channel)
    }

    /**
     * Blocking variant for call sites that cannot suspend (e.g. [TokenResolver]
     * inside ExoPlayer's loading thread).
     */
    fun tokenBlocking(channel: String, timeoutMs: Long = 16_000L): TokenRecord =
        runBlocking { withTimeout(timeoutMs) { token(channel) } }

    /**
     * Force a fresh mint regardless of cache, with single-flight guarantee.
     * Throws [TokenMintException] after 2 failed attempts.
     */
    suspend fun mint(channel: String): TokenRecord = lock(channel).withLock {
        // Re-check after acquiring the lock — another coroutine may have just minted
        cache[channel]?.let { if (!stale(it)) return@withLock it }

        delay(Random.nextLong(300, 1_200))   // anti-stampede jitter

        val playerUrl = playerUrls[channel]
            ?: error("No player URL registered for '$channel'. Call register() first.")

        var lastErr: Throwable? = null
        repeat(2) { attempt ->
            runCatching { minter.mint(channel, playerUrl) }
                .onSuccess { rec -> cache[channel] = rec; return@withLock rec }
                .onFailure { lastErr = it }
            if (attempt == 0) delay(700)
        }
        throw TokenMintException("mint failed for '$channel' after 2 attempts", lastErr)
    }

    fun invalidate(channel: String) {
        cache.remove(channel)
        AppLogger.d("TokenRepository", "Invalidated token for $channel")
    }

    fun invalidateByUri(uri: Uri) = channelOf(uri)?.let { invalidate(it) }

    /**
     * Map a stream [uri] to a channel ID.
     * Checks the first path segment (e.g. "SPT1" from "/SPT1/index.m3u8") first,
     * then falls back to [activeChannel].
     */
    fun channelOf(uri: Uri): String? {
        val seg = uri.pathSegments.firstOrNull()
        return if (seg != null && playerUrls.containsKey(seg)) seg else activeChannel
    }

    // ── private ──────────────────────────────────────────────────────────────

    private fun lock(ch: String) = locks.getOrPut(ch) { Mutex() }

    private fun stale(rec: TokenRecord): Boolean {
        val exp = rec.expEpochSec
        return if (exp != null) clock() / 1000 >= exp - 90
        else clock() - rec.mintedAtMs >= 120_000L
    }
}
