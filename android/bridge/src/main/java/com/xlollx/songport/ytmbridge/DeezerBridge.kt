package com.xlollx.songport.ytmbridge

import android.content.Context
import android.webkit.CookieManager

/**
 * Deezer through the web player's session, for accounts that cannot use the official API: Deezer
 * stopped registering new developer apps, so the route with a key of one's own is closed to most.
 *
 * The user signs in once on deezer.com's own page inside a WebView; the session cookies (`arl`
 * is the one that names the account) are kept encrypted in [session], never handed to Trackhop.
 * [DeezerWebClient] then talks to the web player's gateway the way deezer.com does.
 */
object DeezerBridge {
    val session = WebSession("deezer", listOf("www.deezer.com", "deezer.com", "account.deezer.com", "connect.deezer.com"))
    const val ORIGIN = DeezerWebClient.ORIGIN
    const val LOGIN_URL = "https://www.deezer.com/login"

    /** `arl` is the long-lived cookie of a signed-in browser; without it the gateway answers as a visitor. */
    fun signedIn(cookies: String?): Boolean = WebSession.cookieValue(cookies, "arl") != null

    /** The app's side of [DeezerWebClient]: the WebView sign-in in [session]. */
    fun host(ctx: Context): WebHost = object : WebHost {
        /** The WebView's jar while it still holds the sign-in, the copy saved at sign-in otherwise. */
        override fun cookies(): String? {
            val stored = session.cookies(ctx) ?: return null
            val live = runCatching { CookieManager.getInstance().getCookie(ORIGIN) }.getOrNull()
            return if (signedIn(live)) live!! else stored
        }
        override fun account(): String? = session.account(ctx)
        override fun get(key: String): String? = session.get(ctx, key)
        override fun put(key: String, value: String?) = session.put(ctx, key, value)
        override val userAgent: String get() = BridgeUa.desktop(ctx)
    }

    /** The account's display name, read once after sign-in. Blocking; never on the main thread. */
    fun accountName(ctx: Context): String? = DeezerWebClient(ctx.applicationContext).user(refresh = true).name
}

/** The client as the app has always built it, from a Context. */
fun DeezerWebClient(ctx: Context): DeezerWebClient = DeezerWebClient(DeezerBridge.host(ctx))
