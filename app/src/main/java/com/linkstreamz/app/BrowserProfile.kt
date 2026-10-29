package com.linkstreamz.app

import android.content.Context
import android.webkit.WebSettings
import java.util.Locale

/**
 * P1 – Single source of truth for browser identity.
 *
 * The same [userAgent] is used by both the hidden WebView (TokenMinter) and the
 * OkHttp client (TokenDataSourceFactory). One character of difference causes 403s.
 *
 * The UA is derived from the platform WebView default and has the "; wv" marker
 * stripped so it looks like stock Chrome to the server fingerprint check.
 */
class BrowserProfile private constructor(
    val userAgent: String,
    val acceptLanguage: String,
) {
    val baseHeaders: Map<String, String> = mapOf(
        "Accept"          to "*/*",
        "Accept-Language" to acceptLanguage,
        "Accept-Encoding" to "gzip, deflate",
    )

    companion object {
        @Volatile private var instance: BrowserProfile? = null

        fun get(context: Context): BrowserProfile =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): BrowserProfile {
            val raw = WebSettings.getDefaultUserAgent(context)
            val ua  = raw.replace("; wv)", ")").replace(" wv", "")
            val loc = Locale.getDefault()
            return BrowserProfile(ua, "${loc.language}-${loc.country}")
        }
    }
}
