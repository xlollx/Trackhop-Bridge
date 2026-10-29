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

    private val CLIENT_ID_PARAM = Regex("""[?&]client_id=([A-Za-z0-9]{32})(?:&|$)""")

    /** Keeps the client id of a request the web app made (from the sign-in WebView). */
    fun rememberClientId(ctx: Context, url: String) {
        val id = CLIENT_ID_PARAM.find(url)?.groupValues?.get(1) ?: return
        if (session.get(ctx, "clientId") != id) session.put(ctx, "clientId", id)
    }

    /** The account's name, read once after sign-in. Blocking; never on the main thread. */
    fun accountName(ctx: Context): String? = SoundCloudWebClient(ctx.applicationContext).me(refresh = true).second
}
