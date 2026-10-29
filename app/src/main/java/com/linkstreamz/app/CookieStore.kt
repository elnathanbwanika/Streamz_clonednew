package com.linkstreamz.app

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * P2 – Persistent per-host cookie storage backed by SharedPreferences.
 *
 * Cookies set by the WebView (via [syncFromWebView]) and cookies from OkHttp
 * responses (via [WebCookieJar.saveFromResponse]) live in the same store, so
 * both stacks share a single session identity.
 */
class CookieStore(context: Context) {
    private val prefs = context.getSharedPreferences("stream_cookies", MODE_PRIVATE)

    @Synchronized
    fun put(url: HttpUrl, cookie: Cookie) {
        val existing = loadRaw(url).toMutableList()
        existing.removeAll { it.name == cookie.name }
        existing.add(cookie)
        prefs.edit()
            .putString(url.host, existing.joinToString("\n") { it.toString() })
            .apply()
    }

    @Synchronized
    fun get(url: HttpUrl): List<Cookie> = loadRaw(url)

    /**
     * Pull cookies the WebView obtained for [pageUrl] into the native OkHttp store.
     * Must be called BEFORE [finish] so cookies are in the jar before the first
     * OkHttp request fires.
     */
    fun syncFromWebView(host: String, pageUrl: String) {
        val header  = CookieManager.getInstance().getCookie(pageUrl) ?: return
        val httpUrl = pageUrl.toHttpUrlOrNull() ?: return
        header.split(';').map { it.trim() }.forEach { pair ->
            val name  = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (name.isNotEmpty()) {
                Cookie.parse(httpUrl, "$name=$value; Domain=$host; Path=/")
                    ?.let { put(httpUrl, it) }
            }
        }
    }

    private fun loadRaw(url: HttpUrl): List<Cookie> =
        (prefs.getString(url.host, "") ?: "")
            .lines()
            .filter { it.isNotBlank() }
            .mapNotNull { Cookie.parse(url, it) }
}

/** OkHttp [CookieJar] backed by [CookieStore]. */
class WebCookieJar(private val store: CookieStore) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
        cookies.forEach { store.put(url, it) }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.get(url)
}
