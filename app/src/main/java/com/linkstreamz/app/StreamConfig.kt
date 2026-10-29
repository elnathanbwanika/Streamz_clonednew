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

    fun playerUrl(channelId: String): String =
        PLAYER_PAGE_TEMPLATE.replace("{CHANNEL}", channelId)
}
