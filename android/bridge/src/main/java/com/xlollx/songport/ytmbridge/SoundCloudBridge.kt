package com.xlollx.songport.ytmbridge

import android.content.Context

/**
 * SoundCloud through the web app's session. The user signs in on soundcloud.com inside a WebView;
 * what is kept (encrypted in [session], never handed to Trackhop) is the cookie jar, whose
 * `oauth_token` is the bearer the web app itself sends to `api-v2.soundcloud.com`, and the web app's
 * `client_id`, seen on its own requests while the sign-in page loads.
 */
object SoundCloudBridge {
    val session = WebSession("soundcloud", listOf("soundcloud.com", "secure.soundcloud.com", "api-auth.soundcloud.com", "m.soundcloud.com"))
    const val SITE = "https://soundcloud.com"
    const val LOGIN_URL = "https://soundcloud.com/signin"

    fun oauthToken(cookies: String?): String? = WebSession.cookieValue(cookies, "oauth_token")
    fun signedIn(cookies: String?): Boolean = oauthToken(cookies) != null

    private val CLIENT_ID_PARAM = Regex("""[?&]client_id=([A-Za-z0-9]{32})(?:&|#|$)""")

    /** The client id an api-v2 address carries, or null. */
    fun clientIdOf(url: String): String? = CLIENT_ID_PARAM.find(url)?.groupValues?.get(1)

    /** Keeps the client id of a request the web app made (from the sign-in WebView). */
    fun rememberClientId(ctx: Context, url: String) {
        val id = clientIdOf(url) ?: return
        if (session.get(ctx, "clientId") != id) session.put(ctx, "clientId", id)
    }

    /**
     * The sign-in runs as the WebView itself (its own user agent, no desktop disguise, no scripts):
     * soundcloud.com sits behind DataDome, which blocked the disguised WebView, whose user agent and
     * client hints said desktop Chrome while everything else about it said Android. The API calls
     * afterwards carry the user agent the sign-in ran with, so the two never disagree.
     */
    fun rememberUserAgent(ctx: Context, ua: String?) { session.put(ctx, "userAgent", ua?.takeIf { it.isNotBlank() }) }

    /** The user agent of the sign-in; a session from before it was kept ran as desktop Chrome. */
    fun userAgent(ctx: Context): String = session.get(ctx, "userAgent") ?: BridgeUa.desktop(ctx)

    /** DataDome's cookie from the sign-in, which says this browser passed its checks. */
    fun dataDomeCookie(cookies: String?): String? = WebSession.cookieValue(cookies, "datadome")

    /** What the person reads when DataDome refuses: no app named, the same text in every build. */
    const val BLOCKED = "SoundCloud blocked these requests as unusual activity: wait a while (an hour or more), then sign out of SoundCloud and sign in again, preferably on Wi-Fi"

    /** The account's name, read once after sign-in. Blocking; never on the main thread. */
    fun accountName(ctx: Context): String? = SoundCloudWebClient(ctx.applicationContext).me(refresh = true).second
}
