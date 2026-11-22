package com.linkstreamz.app

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a low-level exception into a short, safe message for the UI.
 *
 * Exception messages from OkHttp/HttpUrlConnection contain the full request URL,
 * including signed bucket paths and tokens. None of that may ever reach a label,
 * dialog or toast — only a generic, actionable description is shown.
 */
object UserFacingError {
    private const val NETWORK = "Network issue. Check your connection and try again."
    private const val GENERIC = "Something went wrong. Please try again."

    fun message(error: Throwable?): String = when {
        error == null -> GENERIC
        error is UnknownHostException ||
            error is ConnectException ||
            error is SocketTimeoutException ||
            error is java.io.InterruptedIOException ||
            error is java.net.NoRouteToHostException -> NETWORK
        error is IOException && looksLikeNetwork(error.message) -> NETWORK
        error is IOException && looksLikeNoSpace(error.message) ->
            "Not enough storage space. Free up space and try again."
        else -> GENERIC
    }

    /** Sanitizes an error already stored in the database from an older build. */
    fun fromStored(raw: String?): String {
        if (raw.isNullOrBlank()) return "Download failed. Please try again."
        return when {
            looksLikeNetwork(raw) -> NETWORK
            looksLikeNoSpace(raw) -> "Not enough storage space. Free up space and try again."
            else -> GENERIC
        }
    }

    private fun looksLikeNetwork(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return listOf(
            "failed to resolve host", "unknownhost", "unable to resolve host",
            "connection reset", "connection refused", "connection closed",
            "timed out", "timeout", "no route to host", "network is unreachable",
            "failed to connect", "software caused connection abort",
            "broken pipe", "eof", "connection failure", "ssl", "handshake",
            "unable to connect", "could not reach", "could not connect",
        ).any { m.contains(it) }
    }

    private fun looksLikeNoSpace(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return m.contains("enospc") || m.contains("no space") || m.contains("insufficient storage")
    }
}