package com.linkstreamz.app

/**
 * Removes URLs, query strings and token-shaped values from anything written to
 * the in-app log. Only the host is kept for a URL so failures remain useful
 * without leaking a signed path.
 */
object LogRedactor {
    private val URL = Regex("""(?i)\b(https?)://([^/\s?#"']+)(?:[/?#][^\s"']*)?""")
    private val TOKEN_PARAM = Regex(
        """(?i)\b(token|access_token|sig|signature|expires|exp|key|auth|jwt|policy)=([^&\s"']+)""",
    )
    private val JWT = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = URL.replace(text) { match ->
            "${match.groupValues[1]}://${match.groupValues[2]}/<redacted>"
        }
        out = TOKEN_PARAM.replace(out) { match -> "${match.groupValues[1]}=<redacted>" }
        out = JWT.replace(out, "<redacted-token>")
        return out
    }

    /** Host-only form used by the stream matrix; never includes path or query. */
    fun redirectHost(location: String?): String? {
        val value = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { java.net.URI(value).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
