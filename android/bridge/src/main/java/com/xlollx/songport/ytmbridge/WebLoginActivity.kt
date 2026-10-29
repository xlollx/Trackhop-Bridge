package com.xlollx.songport.ytmbridge

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import kotlin.concurrent.thread

/**
 * Shared sign-in screen for the web sessions (Spotify, Apple Music, Deezer, SoundCloud). The user signs in on the
 * service's own page; the activity polls the cookie jar of the service's domain and, as soon as the
 * session cookie is there, stores the cookies encrypted and closes. Trackhop starts it with the
 * actions declared in the manifest.
 */
class WebLoginActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }
    private lateinit var web: WebView
    private lateinit var frame: android.widget.FrameLayout
    private val popups = ArrayList<WebView>()

    private fun closePopup(w: WebView) {
        popups.remove(w)
        frame.removeView(w)
        runCatching { w.destroy() }
        check()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var done = false
    private lateinit var service: String
    private lateinit var notice: android.widget.TextView
    private var seen: DataDome.Kind? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = when (intent.action) {
            ACTION_SPOTIFY -> SPOTIFY
            ACTION_APPLE -> APPLE
            ACTION_DEEZER -> DEEZER
            ACTION_SOUNDCLOUD -> SOUNDCLOUD
            else -> intent.getStringExtra(EXTRA_SERVICE) ?: SPOTIFY
        }
        // Deezer and SoundCloud came with a notice that names them: an acceptance of the older one does not cover them.
        Disclaimer.require(this, if (service == DEEZER || service == SOUNDCLOUD) Disclaimer.VERSION else 1) { setup() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        // A "Done" button next to the page: a service that answers something unexpected must never
        // leave the user trapped in this screen.
        val root = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val bar = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(24, 12, 24, 12) }
        bar.addView(android.widget.TextView(this).apply {
            text = getString(R.string.bridge_login_hint)
            textSize = 12f
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(android.widget.Button(this).apply {
            text = getString(R.string.bridge_login_done)
            setOnClickListener {
                if (!check()) android.widget.Toast.makeText(this@WebLoginActivity, R.string.bridge_login_not_signed_in, android.widget.Toast.LENGTH_LONG).show()
            }
        })
        // Said in words when the service's bot protection refuses the sign-in: its own page can be a
        // dead end ("You have been blocked") with nothing to tap.
        notice = android.widget.TextView(this).apply {
            textSize = 14f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0x33FF5252)
            visibility = android.view.View.GONE
        }
        // The page sits in a frame so a sign-in popup (Facebook, Google, Apple ID) can open above it.
        frame = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        web = WebView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        }
        frame.addView(web)
        root.addView(bar); root.addView(notice); root.addView(frame)
        setContentView(root)
        // Edge to edge: the bar would otherwise sit under the status bar, and the keyboard over the fields.
        BridgeInsets.fit(root)
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Spotify refuses its web player to anything whose user agent says "wv" (a WebView) and
            // shows "Playback disabled - incompatible browser". A desktop identity avoids that page;
            // Apple's pages work with the default one, and SoundCloud needs it (see [identity]).
        }
        identity(web)
        if (service == SOUNDCLOUD) {
            // A DataDome cookie from an earlier attempt (as desktop Chrome, or already flagged) would
            // tie this one to it: the sign-in starts as a new visitor. The account's cookies stay.
            val dropped = dropCookie(SoundCloudBridge.SITE, "datadome")
            BridgeNet.log("soundcloud", "sign-in started as the phone's WebView (${BridgeUa.describe()}; ${if (dropped) "old DataDome cookie removed" else "no DataDome cookie"})")
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https") return false
                // SoundCloud's phone-sized site may try to hand over to its app by itself: only a tap does that.
                if (scheme == "intent" && (service != SOUNDCLOUD || request.hasGesture())) {
                    runCatching {
                        Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null
                        }.let(::startActivity)
                    }
                }
                return true
            }
            // The player sends Apple's developer token as the bearer of every API call it makes: that
            // is where the Bridge takes it from, instead of guessing which script bundle holds it.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (service == APPLE && request.url.host?.endsWith("music.apple.com") == true) {
                    request.requestHeaders?.get("Authorization")?.let { AppleBridge.rememberDeveloperToken(applicationContext, it) }
                }
                // SoundCloud's web app names itself with a client id on every API call: kept for ours.
                if (service == SOUNDCLOUD && request.url.host == "api-v2.soundcloud.com") {
                    SoundCloudBridge.rememberClientId(applicationContext, request.url.toString())
                }
                watchBotCheck(request.url.toString())
                // Only watched: every request goes on as the page made it.
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                // A new page is a new chance: its own block, if any, shows the notice again.
                seen = null
                notice.visibility = android.view.View.GONE
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (service == APPLE) view.evaluateJavascript(
                    "(function(){try{return MusicKit.getInstance().developerToken||''}catch(e){return ''}})()"
                ) { v -> AppleBridge.rememberDeveloperToken(applicationContext, v?.trim('"')) }
                check()
            }
        }
        web.settings.apply { setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically = true }
        web.webChromeClient = object : WebChromeClient() {
            // "Continue with Facebook" and the like open a popup, sign in there, and hand the result
            // back to the page that opened it: a real second view, sharing the cookie jar, keeps that
            // handshake intact. Without it the sign-in ran in the main page and never came back.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popup = WebView(this@WebLoginActivity).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.apply {
                        javaScriptEnabled = true; domStorageEnabled = true
                        setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically = true
                    }
                    identity(this)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            watchBotCheck(request.url.toString())
                            return null
                        }
                        override fun onPageFinished(view: WebView, url: String) { check() }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView) { closePopup(window) }
                    }
                }
                frame.addView(popup)
                popups += popup
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            val top = popups.lastOrNull()
            when {
                top != null -> if (top.canGoBack()) top.goBack() else closePopup(top)
                web.canGoBack() -> web.goBack()
                else -> { setResult(RESULT_CANCELED); finish() }
            }
        }
        web.loadUrl(when (service) {
            APPLE -> AppleBridge.HOME
            DEEZER -> DeezerBridge.LOGIN_URL
            SOUNDCLOUD -> SoundCloudBridge.LOGIN_URL
            else -> SpotifyBridge.LOGIN_URL
        })
        // Sign-in overlays (Apple ID) do not navigate: poll the cookie jar while the screen is open.
        handler.postDelayed(object : Runnable {
            override fun run() { if (!done && !isFinishing) { check(); handler.postDelayed(this, 1500) } }
        }, 1500)
    }

    /**
     * The browser identity of the page and its popups.
     * - Apple: the WebView's own, as it always was.
     * - SoundCloud: the WebView's own as well, without the app header and without any script.
     *   soundcloud.com sits behind DataDome, which reads the browser from inside the page (touch,
     *   screen, platform, how navigator's properties are defined) and blocked a WebView whose user
     *   agent, client hints and patched navigator.userAgentData said desktop Chrome on Windows while
     *   everything else said Android. A plain mobile browser has nothing to disagree with.
     * - Spotify and Deezer: desktop Chrome, as before (Spotify refuses a "wv" user agent).
     */
    private fun identity(w: WebView) {
        when (service) {
            APPLE -> Unit
            SOUNDCLOUD -> BridgeUa.dropAppHeader(w)
            else -> BridgeUa.apply(this, w)
        }
    }

    /** Called from WebView threads with every address a page loads; only reads it. */
    private fun watchBotCheck(url: String) {
        val kind = DataDome.kindOf(url) ?: return
        handler.post { botCheck(kind) }
    }

    private fun botCheck(kind: DataDome.Kind) {
        if (done || isFinishing || kind == seen) return
        seen = kind
        BridgeNet.log(service, when (kind) {
            DataDome.Kind.BLOCKED -> "sign-in: the bot protection (DataDome) blocked the page"
            DataDome.Kind.CAPTCHA -> "sign-in: the bot protection (DataDome) shows a puzzle"
            DataDome.Kind.CHECK -> "sign-in: the bot protection (DataDome) checks the browser"
        })
        // A puzzle or the browser check works in the page itself: nothing is put in its way.
        if (kind != DataDome.Kind.BLOCKED) return
        notice.text = getString(R.string.bridge_login_blocked, serviceName())
        notice.visibility = android.view.View.VISIBLE
    }

    private fun serviceName() = when (service) {
        APPLE -> "Apple Music"
        DEEZER -> "Deezer"
        SOUNDCLOUD -> "SoundCloud"
        else -> "Spotify"
    }

    /** Removes one cookie of [site]'s domain; true when there was one. */
    private fun dropCookie(site: String, name: String): Boolean = runCatching {
        val cm = CookieManager.getInstance()
        if (WebSession.cookieValue(cm.getCookie(site), name) == null) return@runCatching false
        val domain = site.substringAfter("://").substringBefore('/')
        cm.setCookie(site, "$name=; Max-Age=0; Path=/")
        cm.setCookie(site, "$name=; Max-Age=0; Domain=.$domain; Path=/")
        cm.flush()
        true
    }.getOrDefault(false)

    /** @return true when a session was captured (the screen then closes). */
    private fun check(): Boolean {
        if (done) return true
        val origin = when (service) {
            APPLE -> "https://music.apple.com"
            DEEZER -> DeezerBridge.ORIGIN
            SOUNDCLOUD -> SoundCloudBridge.SITE
            else -> "https://open.spotify.com"
        }
        val cookies = CookieManager.getInstance().getCookie(origin) ?: return false
        val ok = when (service) {
            APPLE -> AppleBridge.signedIn(cookies)
            DEEZER -> DeezerBridge.signedIn(cookies)
            SOUNDCLOUD -> SoundCloudBridge.signedIn(cookies)
            else -> SpotifyBridge.signedIn(cookies)
        }
        if (!ok) return false
        done = true
        val sess = when (service) {
            APPLE -> AppleBridge.session
            DEEZER -> DeezerBridge.session
            SOUNDCLOUD -> SoundCloudBridge.session
            else -> SpotifyBridge.session
        }
        // A new sign-in may be another account: nothing cached from the previous one is reused.
        if (service == DEEZER) { sess.put(applicationContext, "sid", null); DeezerWebClient.forget() }
        if (service == SOUNDCLOUD) {
            sess.put(applicationContext, "userId", null)
            SoundCloudBridge.rememberUserAgent(applicationContext, web.settings.userAgentString)
        }
        val app = applicationContext
        sess.save(app, cookies, null)
        // Close at once: the sign-in is done. The account name needs a token or an API call, which can
        // take a while, so it is fetched afterwards and shown when the screen refreshes.
        setResult(RESULT_OK)
        finish()
        thread {
            val account = runCatching {
                when (service) {
                    APPLE -> AppleBridge.storefront(app)?.let { "Apple Music (${it.uppercase()})" }
                    DEEZER -> DeezerBridge.accountName(app)
                    SOUNDCLOUD -> SoundCloudBridge.accountName(app)
                    else -> SpotifyBridge.accountInfo(app).let { (id, name) -> id?.let { sess.put(app, "userId", it) }; name }
                }
            }.getOrNull()
            if (account != null) sess.save(app, cookies, account)
        }
        return true
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        popups.forEach { runCatching { it.destroy() } }
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SPOTIFY = "com.xlollx.songport.ytmbridge.SPOTIFY_LOGIN"
        const val ACTION_APPLE = "com.xlollx.songport.ytmbridge.APPLE_LOGIN"
        const val ACTION_DEEZER = "com.xlollx.songport.ytmbridge.DEEZER_LOGIN"
        const val ACTION_SOUNDCLOUD = "com.xlollx.songport.ytmbridge.SOUNDCLOUD_LOGIN"
        const val EXTRA_SERVICE = "service"
        const val SPOTIFY = "spotify"
        const val APPLE = "apple"
        const val DEEZER = "deezer"
        const val SOUNDCLOUD = "soundcloud"

        fun intent(ctx: android.content.Context, service: String): Intent =
            Intent(ctx, WebLoginActivity::class.java).setAction(when (service) {
                APPLE -> ACTION_APPLE
                DEEZER -> ACTION_DEEZER
                SOUNDCLOUD -> ACTION_SOUNDCLOUD
                else -> ACTION_SPOTIFY
            })
    }
}
