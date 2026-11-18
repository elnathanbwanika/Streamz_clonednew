package com.linkstreamz.app

object ContentRouting {

    fun isLocalChannel(channel: Channel): Boolean =
        channel.country.trim().equals("Uganda", ignoreCase = true)

    fun bypassTunnel(target: PlayerTarget): Boolean = when (target) {
        is MovieTarget, is EpisodeTarget -> true
        is ChannelTarget -> isLocalChannel(target.channel)
        is DownloadedTarget -> true
        else -> false
    }
}
