package com.linkstreamz.app

/**
 * Headers used for player requests.
 *
 * Only the server's configured headers are applied. Origin and Referer are
 * never synthesised — token-protected CDNs commonly reject requests that carry
 * a different Origin than the one that minted the token. A configured
 * `User-Agent: vlc` is expanded to the exact VLC identity used by VLC desktop.
 */
object PlayerRequestHeaders {
    const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/114.0.0.0 Mobile Safari/537.36"

    fun build(configured: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, value) in configured) {
            if (name.equals("User-Agent", ignoreCase = true) &&
                value.trim().equals("vlc", ignoreCase = true)
            ) {
                out[canonical(name)] = VLC_USER_AGENT
            } else {
                out[canonical(name)] = value
            }
        }
        if (out.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            out["User-Agent"] = DEFAULT_USER_AGENT
        }
        return out
    }

    /** Same as [build] but with any configured Origin/Referer removed. */
    fun withoutOriginReferer(configured: Map<String, String>): Map<String, String> =
        build(configured).filterKeys {
            !it.equals("Origin", ignoreCase = true) && !it.equals("Referer", ignoreCase = true)
        }

    private fun canonical(name: String): String = when {
        name.equals("User-Agent", ignoreCase = true) -> "User-Agent"
        name.equals("Origin", ignoreCase = true) -> "Origin"
        name.equals("Referer", ignoreCase = true) -> "Referer"
        else -> name
    }
}
