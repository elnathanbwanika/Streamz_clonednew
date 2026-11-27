package com.linkstreamz.app

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Helpers for direct bucket files (`.mp4`, `.mkv`, long names, percent-encoded
 * spaces/brackets/parentheses).
 *
 * The URL that arrives from the admin console is already correct. We validate it
 * with OkHttp, but return the ORIGINAL string unchanged: decoding it and encoding
 * it again would turn `%20` into `+` or `%2520`, and `%5B` into `%255B`, which
 * points at a different object.
 */
object DirectFileUrls {
    /** Returns the URL unchanged after checking that it is a valid HTTP(S) URL. */
    fun exact(url: String): String {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull()
            ?: throw IllegalArgumentException("Invalid HTTP(S) URL")
        if (parsed.protocol !in setOf("http", "https")) {
            throw IllegalArgumentException("Only HTTP(S) URLs are supported")
        }
        return url
    }

    /** Encoded path as seen on the wire, without decoding percent escapes. */
    fun encodedPath(url: String): String = url.toHttpUrl().encodedPath

    /** True for a plain HTTP(S) URL with no fragment that we can pass through safely. */
    fun isSafePassthrough(url: String): Boolean = runCatching {
        val parsed = exact(url).toHttpUrl()
        parsed.fragment.isEmpty()
    }.getOrDefault(false)
}
