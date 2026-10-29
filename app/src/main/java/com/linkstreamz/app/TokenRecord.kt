package com.linkstreamz.app

/**
 * Immutable snapshot of a freshly minted token and the browser identity that produced it.
 *
 * [expEpochSec] is parsed from the base64 payload (third pipe-delimited field).
 * If the token is malformed it is null; staleness check falls back to 2-minute lifetime.
 *
 * Tokens are NEVER written to disk or exported. Each device mints its own.
 */
data class TokenRecord(
    val channel: String,
    val token: String,
    val expEpochSec: Long?,
    val mintedAtMs: Long,
    val userAgent: String,
    val headers: Map<String, String>,
)

/** Thrown (not swallowed) when the WebView mint times out or finds no valid token. */
class TokenMintException(message: String, cause: Throwable? = null) : Exception(message, cause)
