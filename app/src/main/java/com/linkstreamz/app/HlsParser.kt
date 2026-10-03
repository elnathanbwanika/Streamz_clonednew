package com.linkstreamz.app

import java.net.URI

object HlsParser {
    fun rewritePlaylist(
        playlistContent: String,
        baseUrl: String,
        localPrefix: String
    ): String {
        val lines = playlistContent.lines()
        val result = StringBuilder()
        
        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF:") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")) {
                result.append(line).append("\n")
            } else if (line.startsWith("#EXT-X-MEDIA:")) {
                // Rewrite URI="..."
                val rewritten = line.replace(Regex("""URI="([^"]+)"""")) { matchResult ->
                    val uri = matchResult.groupValues[1]
                    val absolute = resolveUri(baseUrl, uri)
                    """URI="$localPrefix?url=${encode(absolute)}""""
                }
                result.append(rewritten).append("\n")
            } else if (line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-MAP:")) {
                val rewritten = line.replace(Regex("""URI="([^"]+)"""")) { matchResult ->
                    val uri = matchResult.groupValues[1]
                    val absolute = resolveUri(baseUrl, uri)
                    """URI="$localPrefix?url=${encode(absolute)}""""
                }
                result.append(rewritten).append("\n")
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val absolute = resolveUri(baseUrl, line)
                result.append("$localPrefix?url=${encode(absolute)}\n")
            } else {
                result.append(line).append("\n")
            }
        }
        return result.toString()
    }

    private fun resolveUri(base: String, relative: String): String {
        val baseUri = URI(base)
        var resolved = baseUri.resolve(relative)
        
        if (baseUri.query != null) {
            val baseQuery = baseUri.query
            val newQuery = if (resolved.query == null) baseQuery else "${resolved.query}&$baseQuery"
            resolved = URI(resolved.scheme, resolved.userInfo, resolved.host, resolved.port, resolved.path, newQuery, resolved.fragment)
        }
        return resolved.toString()
    }
    
    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
