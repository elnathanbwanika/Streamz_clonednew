package com.linkstreamz.app

import android.content.Context

/**
 * Application-level singleton that wires together the browser-identity token stack.
 *
 * Call [init] once in MainActivity.onCreate before setContent.
 * After that, [profile], [cookies], [minter], and [repo] are safe to read from any thread.
 */
object TokenEngine {
    @Volatile private var _profile: BrowserProfile? = null
    @Volatile private var _cookies: CookieStore?    = null
    @Volatile private var _minter:  TokenMinter?    = null
    @Volatile private var _repo:    TokenRepository? = null

    val profile: BrowserProfile  get() = _profile ?: error("TokenEngine.init() not called")
    val cookies: CookieStore     get() = _cookies ?: error("TokenEngine.init() not called")
    val minter:  TokenMinter     get() = _minter  ?: error("TokenEngine.init() not called")
    val repo:    TokenRepository get() = _repo    ?: error("TokenEngine.init() not called")

    fun init(context: Context) {
        if (_profile != null) return          // already initialised
        val p = BrowserProfile.get(context)
        val c = CookieStore(context)
        val m = TokenMinter(context, p, c)
        val r = TokenRepository(m)
        _profile = p
        _cookies = c
        _minter  = m
        _repo    = r
        AppLogger.d("TokenEngine", "Initialised. UA=${p.userAgent}")
    }
}
