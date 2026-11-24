package com.linkstreamz.app

// OkHttp-backed Downloader implementation for NewPipeExtractor v0.27.1.
// Replaces NewValve to avoid the Kotlin 2.4.0 binary metadata incompatibility.
// Uses the same OkHttp client that's already in the project.

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    companion object {
        // Only used when NewPipe does not supply a UA (it normally does).
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }

    override fun execute(request: NpRequest): NpResponse {
        // NewPipe's contract says its headers OVERRIDE downloader defaults.
        // Sending our UA *in addition* to NewPipe's produced two User-Agent
        // lines; YouTube then saw a client/UA mismatch and returned a player
        // response with no streams. Normalise keys to lower case so NewPipe's
        // value replaces ours instead of being appended to it.
        val headers = LinkedHashMap<String, MutableList<String>>()
        headers["user-agent"] = mutableListOf(DEFAULT_USER_AGENT)
        for (entry in request.headers().entries) {
            headers[entry.key.lowercase()] = entry.value.toMutableList()
        }

        val requestBuilder = Request.Builder().url(request.url())
        for (entry in headers.entries) {
            for (value in entry.value) {
                requestBuilder.addHeader(entry.key, value)
            }
        }

        val body = request.dataToSend()
        val okBody: RequestBody? = if (body != null) {
            RequestBody.create(null, body)
        } else {
            null
        }

        val method = request.httpMethod()
        val built = when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(okBody ?: RequestBody.create(null, ByteArray(0)))
            else -> requestBuilder.method(method, okBody)
        }.build()

        val response = client.newCall(built).execute()
        val responseBody = response.body?.string() ?: ""

        // OkHttp's Headers is an Iterable<Pair<String, String>>, not a Map.
        val responseHeaders = LinkedHashMap<String, MutableList<String>>()
        for (pair in response.headers) {
            responseHeaders.getOrPut(pair.first) { mutableListOf() }.add(pair.second)
        }

        return NpResponse(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            request.url()
        )
    }
}
