package com.linkstreamz.app

/**
 * Single source of truth for provider-specific URL templates.
 *
 * NOTE: The example values below match the current provider (wideiptv.top / bluetier.top).
 * Change ONLY this object when the provider changes their URL shape — never hardcode these
 * strings at call sites.
 *
 * Channel IDs are the first path segment of the stream URL, e.g. "SPT1" from
 * "https://ds169.bluetier.top/SPT1/index.m3u8".
 */
object StreamConfig {
    /** Player page URL. {CHANNEL} is replaced with the channel ID at runtime. */
    const val PLAYER_PAGE_TEMPLATE = "https://wideiptv.top/player/{CHANNEL}"

    /** Referer header value sent with every HLS request. */
    const val REFERER = "https://wideiptv.top/"

    /** Origin header value sent with every HLS request. */
    const val ORIGIN = "https://wideiptv.top"

    /**
     * URL of the WireGuard VPN config file (or JSON manifest) on the admin server.
     *
     * Your server can respond with EITHER:
     *   1. A raw WireGuard .conf file (content starts with "[Interface]")
     *   2. A JSON manifest:  {"active": true, "configUrl": "https://…/wg0.conf"}
     *      Set active=false to disable the VPN without deleting the file.
     *
     * ⚠ REPLACE THIS URL with the actual endpoint on your server.
     *   e.g. "https://api.yourserver.com/vpn/config.conf"
     *
     * Leave blank (or keep "YOUR_SERVER") to disable in-app VPN entirely —
     * the app will route traffic normally and respect any external VPN the user has connected.
     */
    const val VPN_CONFIG_URL = "https://YOUR_SERVER/vpn/wg0.conf"

    fun playerUrl(channelId: String): String =
        PLAYER_PAGE_TEMPLATE.replace("{CHANNEL}", channelId)

    /**
     * Returns true if the URL hostname belongs to a known token-protected CDN.
     *
     * This is used as a heuristic when the JSON feed does not explicitly set
     * `useTokenAuth`. Add extra hostnames here as new token-protected providers
     * are onboarded.
     */
    fun requiresTokenAuth(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            TOKEN_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    /** Hostnames (and their subdomains) that require browser-token authentication. */
    private val TOKEN_AUTH_HOSTS = setOf(
        "bluetier.top",
        "wideiptv.top",
    )
}
