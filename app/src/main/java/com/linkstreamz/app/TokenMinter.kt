package com.linkstreamz.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * P3 – Hidden Chromium WebView that loads the provider player page and extracts
 * the minted stream token from outgoing .m3u8 requests.
 *
 * Design invariants:
 * - One WebView instance is reused across all mints; no per-mint allocation.
 * - The WebViewClient is replaced (not appended) on each mint — no listener leak.
 * - View is 1dp / alpha=0 (not GONE) so layout is performed; some engines need it.
 * - [CookieStore.syncFromWebView] runs BEFORE [finish] so the jar is populated
 *   before the first OkHttp request fires.
 */
class TokenMinter(
    private val context: Context,
    private val profile: BrowserProfile,
    private val cookies: CookieStore,
) {
    private var webView: WebView? = null

    /**
     * Load [playerUrl] in the hidden WebView, intercept the .m3u8 request, validate
     * that the token's channel field matches [channel], sync cookies, and return a
     * [TokenRecord]. Suspends on the main thread; times out after 15 seconds.
     *
     * @throws TokenMintException on timeout or missing valid token.
     */
    suspend fun mint(channel: String, playerUrl: String): TokenRecord =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wv = obtainWebView()
                val streamHost = Uri.parse(playerUrl).host.orEmpty()
                var settled = false
                val mainHandler = Handler(Looper.getMainLooper())
                val probeRunnables = mutableListOf<Runnable>()

                fun buildRecord(token: String) = TokenRecord(
                    channel     = channel,
                    token       = token,
                    expEpochSec = parseExp(token),
                    mintedAtMs  = System.currentTimeMillis(),
                    userAgent   = profile.userAgent,
                    headers     = mapOf(
                        "User-Agent"      to profile.userAgent,
                        "Referer"         to StreamConfig.REFERER,
                        "Origin"          to StreamConfig.ORIGIN,
                        "Accept"          to "*/*",
                        "Accept-Language" to profile.acceptLanguage,
                    ),
                )

                fun finish(record: TokenRecord?, error: String?) {
                    if (settled) return
                    settled = true
                    // Cancel all pending probe callbacks to prevent post-settlement callbacks
                    probeRunnables.forEach { mainHandler.removeCallbacks(it) }
                    probeRunnables.clear()
                    wv.stopLoading()
                    if (record != null) cont.resume(record)
                    else cont.resumeWithException(TokenMintException(error ?: "mint failed"))
                }

                fun harvest(rawUrl: String) {
                    if (settled) return
                    val token = Uri.parse(rawUrl).getQueryParameter("token") ?: return
                    if (token.isBlank()) return
                    if (payloadChannel(token) != channel) return
                    // syncFromWebView BEFORE finish — cookies must be in the jar first
                    cookies.syncFromWebView(streamHost, rawUrl)
                    CookieManager.getInstance().flush()
                    AppLogger.d("TokenMinter", "Harvested token for $channel exp=${parseExp(token)}")
                    mainHandler.post { finish(buildRecord(token), null) }
                }

                // Replace the client on each mint — never accumulate listeners
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        v: WebView, req: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = req.url.toString()
                        if (url.contains(".m3u8") && url.contains("token=")) harvest(url)
                        return null
                    }

                    override fun onPageFinished(v: WebView, url: String) {
                        v.evaluateJavascript(DISMISS_JS, null)
                        // Poll for late-attached video sources (up to 12 s)
                        repeat(12) { i ->
                            val r = Runnable {
                                if (settled) return@Runnable
                                v.evaluateJavascript(PROBE_JS) { raw ->
                                    val probe = raw?.trim('"')
                                        ?.replace("\\/", "/")
                                        .orEmpty()
                                    if (probe.contains(".m3u8") && probe.contains("token="))
                                        harvest(probe)
                                }
                            }
                            probeRunnables += r
                            mainHandler.postDelayed(r, 1_000L * (i + 1))
                        }
                    }

                    override fun onReceivedHttpError(
                        v: WebView, req: WebResourceRequest, resp: WebResourceResponse,
                    ) = Unit  // ignore — let probe / timeout handle failures
                }

                mainHandler.postDelayed(
                    { finish(null, "mint timeout for $channel") }, 15_000,
                )
                cont.invokeOnCancellation { settled = true; wv.stopLoading() }

                AppLogger.d("TokenMinter", "Loading player page for $channel: $playerUrl")
                wv.loadUrl(playerUrl)
            }
        }

    /** Attach the hidden WebView to [host] so it participates in layout. */
    fun attach(host: FrameLayout) {
        val wv = obtainWebView()
        if (wv.parent == null) host.addView(wv)
    }

    fun destroy() {
        webView?.destroy()
        webView = null
    }

    // ── private ──────────────────────────────────────────────────────────────

    private fun obtainWebView(): WebView = webView ?: WebView(context).apply {
        settings.javaScriptEnabled                = true
        settings.domStorageEnabled                = true
        @Suppress("DEPRECATION") settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        @Suppress("DEPRECATION")
        settings.mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.loadsImagesAutomatically         = false
        settings.userAgentString                  = profile.userAgent
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        CookieManager.getInstance().setAcceptCookie(true)
        layoutParams = ViewGroup.LayoutParams(1, 1)  // 1 dp — not GONE
        alpha        = 0f                            // invisible but laid out
        WebView.setWebContentsDebuggingEnabled(true)
        webView = this
    }

    private fun payloadChannel(token: String): String? =
        decodeToken(token)?.split('|')?.firstOrNull()

    private fun parseExp(token: String): Long? =
        decodeToken(token)?.split('|')?.lastOrNull()?.toLongOrNull()

    private fun decodeToken(token: String): String? {
        val padded = token.padEnd((token.length + 3) / 4 * 4, '=')
        for (flags in intArrayOf(
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.DEFAULT  or Base64.NO_WRAP,
        )) {
            runCatching { return String(Base64.decode(padded, flags)) }
        }
        return null
    }

    companion object {
        private const val PROBE_JS = """
(function(){try{
  var v=document.querySelector('video');
  if(v&&v.src&&v.src.indexOf('.m3u8')>-1&&v.src.indexOf('token=')>-1)return v.src;
  var r=performance.getEntriesByType('resource').map(function(e){return e.name})
    .filter(function(n){return n.indexOf('.m3u8')>-1&&n.indexOf('token=')>-1});
  return r.length?r[r.length-1]:'';
}catch(e){return ''}})()"""

        private const val DISMISS_JS = """
(function(){try{
  document.querySelectorAll('[class*=close],[id*=close],.close,button').forEach(function(e){
    try{e.click()}catch(_){}});
}catch(_){}})()"""
    }
}
