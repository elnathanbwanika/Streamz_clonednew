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
 * - View is 1×1 px / alpha=0 (not GONE) so layout is performed and JS executes.
 * - All WebView API calls are marshalled to the main thread via mainHandler.
 * - shouldInterceptRequest fires on a background thread; cookie sync is posted
 *   to main thread before finish() is called.
 * - [CookieStore.syncFromWebView] runs BEFORE [finish] so the jar is populated
 *   before the first OkHttp request fires.
 */
class TokenMinter(
    private val context: Context,
    private val profile: BrowserProfile,
    private val cookies: CookieStore,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    /**
     * Load [playerUrl] in the hidden WebView, intercept the .m3u8 request,
     * sync cookies, and return a [TokenRecord].
     * Suspends; times out after 20 seconds.
     *
     * @throws TokenMintException on timeout or no token found.
     */
    suspend fun mint(channel: String, playerUrl: String): TokenRecord =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wv = obtainWebView()
                var settled = false
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

                // Always called on main thread
                fun finish(record: TokenRecord?, error: String?) {
                    if (settled) return
                    settled = true
                    probeRunnables.forEach { mainHandler.removeCallbacks(it) }
                    probeRunnables.clear()
                    wv.stopLoading()
                    AppLogger.d("TokenMinter", if (record != null) "mint OK for $channel" else "mint FAIL: $error")
                    if (record != null) cont.resume(record)
                    else cont.resumeWithException(TokenMintException(error ?: "mint failed"))
                }

                // Called from ANY thread (shouldInterceptRequest = background thread)
                // Posts cookie sync + finish to main thread safely.
                fun harvestFromAnyThread(rawUrl: String) {
                    if (settled) return
                    val token = Uri.parse(rawUrl).getQueryParameter("token") ?: return
                    if (token.isBlank()) return
                    // Accept any non-blank token — skip strict channel-field check
                    // because some providers encode the channel differently (uppercase,
                    // short alias, etc.). The token is valid if the server issued it.
                    AppLogger.d("TokenMinter", "Intercepted token for $channel from $rawUrl")
                    // Cookie sync must run on main thread
                    mainHandler.post {
                        if (settled) return@post
                        runCatching {
                            CookieManager.getInstance().flush()
                            cookies.syncFromWebView(
                                Uri.parse(playerUrl).host.orEmpty(),
                                playerUrl
                            )
                        }
                        finish(buildRecord(token), null)
                    }
                }

                // Replace the client on each mint — never accumulate listeners
                wv.webViewClient = object : WebViewClient() {

                    // Fired on a background thread by WebView
                    override fun shouldInterceptRequest(
                        v: WebView, req: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = req.url.toString()
                        if (url.contains(".m3u8") && url.contains("token="))
                            harvestFromAnyThread(url)
                        return null  // let WebView fetch it normally
                    }

                    // Fired on main thread
                    override fun onPageFinished(v: WebView, url: String) {
                        v.evaluateJavascript(DISMISS_JS, null)
                        // Poll for late-attached or JS-injected video sources (up to 15 s)
                        repeat(15) { i ->
                            val r = Runnable {
                                if (settled) return@Runnable
                                v.evaluateJavascript(PROBE_JS) { raw ->
                                    val probe = raw?.trim('"')
                                        ?.replace("\\/", "/")
                                        .orEmpty()
                                    if (probe.contains(".m3u8") && probe.contains("token="))
                                        harvestFromAnyThread(probe)
                                }
                            }
                            probeRunnables += r
                            mainHandler.postDelayed(r, 1_000L * (i + 1))
                        }
                    }

                    override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        AppLogger.d("TokenMinter", "Page started: $url")
                    }

                    override fun onReceivedError(
                        v: WebView, req: WebResourceRequest,
                        err: android.webkit.WebResourceError,
                    ) {
                        // Only log main-frame errors; sub-resource failures are common and expected
                        if (req.isForMainFrame)
                            AppLogger.e("TokenMinter", "Main frame error ${err.errorCode}: ${err.description}")
                    }

                    override fun onReceivedHttpError(
                        v: WebView, req: WebResourceRequest, resp: WebResourceResponse,
                    ) = Unit  // sub-resource HTTP errors are normal; let probe/timeout handle it
                }

                // 20-second hard timeout
                val timeoutRunnable = Runnable { finish(null, "mint timeout for $channel after 20s") }
                mainHandler.postDelayed(timeoutRunnable, 20_000)

                cont.invokeOnCancellation {
                    mainHandler.post {
                        settled = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        probeRunnables.forEach { mainHandler.removeCallbacks(it) }
                        probeRunnables.clear()
                        wv.stopLoading()
                    }
                }

                AppLogger.d("TokenMinter", "Loading player page for $channel: $playerUrl")
                wv.loadUrl(playerUrl)
            }
        }

    /** Attach the hidden WebView to [host] so it participates in layout. */
    fun attach(host: FrameLayout) {
        // Must be called on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val wv = obtainWebView()
            if (wv.parent == null) host.addView(wv)
        } else {
            mainHandler.post {
                val wv = obtainWebView()
                if (wv.parent == null) host.addView(wv)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
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
        layoutParams = ViewGroup.LayoutParams(1, 1)  // 1 px — not GONE
        alpha        = 0f                            // invisible but laid out
        WebView.setWebContentsDebuggingEnabled(true)
        webView = this
    }

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
        /** Probe the live DOM for an HLS src with a token parameter. */
        private const val PROBE_JS = """
(function(){try{
  var v=document.querySelector('video');
  if(v&&v.src&&v.src.indexOf('.m3u8')>-1&&v.src.indexOf('token=')>-1)return v.src;
  var sources=document.querySelectorAll('source');
  for(var i=0;i<sources.length;i++){
    var s=sources[i].src||sources[i].getAttribute('src')||'';
    if(s.indexOf('.m3u8')>-1&&s.indexOf('token=')>-1)return s;
  }
  var r=performance.getEntriesByType('resource').map(function(e){return e.name})
    .filter(function(n){return n.indexOf('.m3u8')>-1&&n.indexOf('token=')>-1});
  return r.length?r[r.length-1]:'';
}catch(e){return ''}})()\"""

        /** Dismiss common overlays/cookie banners that block the player from loading. */
        private const val DISMISS_JS = """
(function(){try{
  document.querySelectorAll('[class*=close],[id*=close],.close,[class*=modal],[id*=modal]').forEach(function(e){
    try{e.click()}catch(_){}});
  var btns=document.querySelectorAll('button');
  for(var i=0;i<btns.length;i++){
    var t=(btns[i].textContent||'').toLowerCase();
    if(t.indexOf('accept')>-1||t.indexOf('close')>-1||t.indexOf('dismiss')>-1||t.indexOf('agree')>-1)
      try{btns[i].click()}catch(_){}
  }
}catch(_){}})()\"""
    }
}
