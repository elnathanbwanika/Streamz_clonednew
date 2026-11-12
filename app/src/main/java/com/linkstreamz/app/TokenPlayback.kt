package com.linkstreamz.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackSelection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// ── P4 – Token resolver ───────────────────────────────────────────────────────

/**
 * [ResolvingDataSource.Resolver] that stamps the fresh token and identity headers
 * onto EVERY DataSpec: master playlist, media playlists, segments, key URIs, init
 * segments, and redirect hops.
 *
 * This is the only place URLs are rewritten. Uri.resolve() must never be used to
 * build segment URLs because it drops the query string and with it the token.
 */
class TokenResolver(private val repo: TokenRepository) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val channel = repo.channelOf(dataSpec.uri) ?: return dataSpec
        val rec = repo.tokenBlocking(channel)
        return dataSpec.buildUpon()
            .setUri(dataSpec.uri.withToken(rec.token))
            .setHttpRequestHeaders(rec.headers)
            .build()
    }

    override fun resolveReportedUri(uri: Uri): Uri = uri
}

/** Replace (or add) the `token` query parameter without disturbing other params. */
private fun Uri.withToken(token: String): Uri {
    val keep = queryParameterNames.filter { it != "token" }
        .map { it to getQueryParameter(it) }
    val b = buildUpon().encodedQuery(null)
    keep.forEach { (k, v) -> b.appendQueryParameter(k, v) }
    b.appendQueryParameter("token", token)
    return b.build()
}

// ── P4 – Data source factory ──────────────────────────────────────────────────

/**
 * [DataSource.Factory] used by ExoPlayer for all HLS requests on token-protected channels.
 *
 * Wraps OkHttp with:
 * - [WebCookieJar] so session cookies reach every request.
 * - [BrowserProfile] User-Agent — the exact same string as the hidden WebView.
 * - [ResolvingDataSource] on top so every byte passes through [TokenResolver].
 */
class TokenDataSourceFactory(
    private val repo: TokenRepository,
    private val cookies: CookieStore,
    private val profile: BrowserProfile,
) : DataSource.Factory {

    private val client: OkHttpClient = HttpClients.newBuilder()
        .cookieJar(WebCookieJar(cookies))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun createDataSource(): DataSource {
        val base = OkHttpDataSource.Factory(client)
            .setUserAgent(profile.userAgent)
            .setDefaultRequestProperties(profile.baseHeaders)
        return ResolvingDataSource.Factory(base, TokenResolver(repo)).createDataSource()
    }
}

// ── P5 – Error policy ─────────────────────────────────────────────────────────

/**
 * [LoadErrorHandlingPolicy] that reacts to 403/401 by invalidating the cached token
 * and minting a fresh one before ExoPlayer fires the retry.
 *
 * Uses Media3 1.5.1 API: LoadErrorInfo.loadEventInfo.dataSpec, .errorCount.
 */
class TokenErrorPolicy(private val repo: TokenRepository) : LoadErrorHandlingPolicy {

    /** No location/track fallback — token refresh is handled inline by [getRetryDelayMsFor]. */
    override fun getFallbackSelectionFor(
        fallbackOptions: FallbackOptions,
        info: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): FallbackSelection? = null

    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val uri = info.loadEventInfo.dataSpec.uri
        val code = (info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        if (code == 403 || code == 401) {
            repo.invalidateByUri(uri)
            val ch = repo.channelOf(uri)
            if (ch != null) {
                AppLogger.d("TokenErrorPolicy", "HTTP $code — reminting for $ch (attempt ${info.errorCount})")
                runBlocking { withTimeoutOrNull(16_000) { repo.mint(ch) } }
            }
            return 800L + Random.nextLong(0, 600)
        }
        return if (info.errorCount < 3) 1_000L * info.errorCount else C.TIME_UNSET
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 4
}
