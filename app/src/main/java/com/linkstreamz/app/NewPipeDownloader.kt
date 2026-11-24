package com.linkstreamz.app

// OkHttp-backed Downloader implementation for NewPipeExtractor v0.27.1.
// Replaces NewValve to avoid the Kotlin 2.4.0 binary metadata incompatibility.
// Uses the same OkHttp client that's already in the project.

import okhttp3.OkHttpClient
import okhttp3.Request
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
        // response with no streams. Build a case-insensitive map so NewPipe's
        // value replaces ours instead of being appended to it.
        val headers = java.util.LinkedHashMap<String, MutableList<String>>(
            String.CASE_INSENSITIVE_ORDER
        )
        headers["User-Agent"] = mutableListOf(DEFAULT_USER_AGENT)
        request.headers().forEach { (key, values) ->
            headers[key] = values.toMutableList()
        }

        val requestBuilder = Request.Builder().url(request.url())
        headers.forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        val body = request.dataToSend()
        val okBody: okhttp3.RequestBody? = if (body != null) {
            okhttp3.RequestBody.create(null, body)
        } else null

        val method = request.httpMethod()
        val built = when (method) {
            "GET"  -> requestBuilder.get()
            "POST" -> requestBuilder.post(okBody ?: okhttp3.RequestBody.create(null, ByteArray(0)))
            else   -> requestBuilder.method(method, okBody)
        }.build()

        val response = client.newCall(built).execute()
        val responseBody = response.body?.string() ?: ""

        val responseHeaders = mutableMapOf<String, MutableList<String>>()
        response.headers.forEach { (k, v) ->
            responseHeaders.getOrPut(k) { mutableListOf() }.add(v)
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
