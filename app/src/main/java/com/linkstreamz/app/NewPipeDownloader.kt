package com.linkstreamz.app

// YT-EXTRACT: OkHttp-backed Downloader implementation for NewPipeExtractor v0.26.5.
// Replaces NewValve to avoid the Kotlin 2.4.0 binary metadata incompatibility.
// Uses the same OkHttp client that's already in the project.

import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val requestBuilder = Request.Builder()
            .url(request.url())
            .addHeader("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")

        request.headers().forEach { (key, values) ->
            values.forEach { v -> requestBuilder.addHeader(key, v) }
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

        val headers = mutableMapOf<String, MutableList<String>>()
        response.headers.forEach { (k, v) ->
            headers.getOrPut(k) { mutableListOf() }.add(v)
        }

        return NpResponse(
            response.code,
            response.message,
            headers,
            responseBody,
            request.url()
        )
    }
}
