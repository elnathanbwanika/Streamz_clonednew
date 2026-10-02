package com.linkstreamz.app

import android.net.Uri

object YouTubeUrl {

    fun isYouTubeUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            host == "youtube.com" ||
                host == "www.youtube.com" ||
                host == "m.youtube.com" ||
                host == "youtu.be" ||
                host == "music.youtube.com"
        } catch (e: Exception) {
            false
        }
    }

    fun youtubeVideoId(url: String): String? {
        if (!isYouTubeUrl(url)) return null
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null

            if (host == "youtu.be") {
                return uri.pathSegments.firstOrNull()?.takeIf { it.length == 11 }
            }

            val path = uri.path ?: ""
            
            if (path.startsWith("/watch")) {
                return uri.getQueryParameter("v")?.takeIf { it.length == 11 }
            }

            if (path.startsWith("/shorts/")) {
                return uri.pathSegments.getOrNull(1)?.takeIf { it.length == 11 }
            }

            if (path.startsWith("/embed/")) {
                return uri.pathSegments.getOrNull(1)?.takeIf { it.length == 11 }
            }
            
            if (path.startsWith("/live/")) {
                return uri.pathSegments.getOrNull(1)?.takeIf { it.length == 11 }
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}
