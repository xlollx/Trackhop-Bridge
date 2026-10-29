package com.xlollx.songport.ytmbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the SoundCloud sign-in finds the session: the Authorization header the web app sends, the
 * client id on its API addresses (api-v2 and the mobile site's api-mobi), the cookie of any
 * SoundCloud host, and a localStorage dump. Pure functions: no Android, no network.
 */
class SoundCloudSessionTest {
    private val cid = "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6"
    private val tok = "2-294035-46212435-AbCdEfGhIjKlMn"

    // ---- the Authorization header

    @Test fun theTokenOfAnOAuthHeader() {
        assertEquals(tok, SoundCloudBridge.tokenOfAuthorization("OAuth $tok"))
        assertEquals(tok, SoundCloudBridge.tokenOfAuthorization("oauth  $tok "))
        assertEquals("AbCdEfGh.1j-Kl_Mn~", SoundCloudBridge.tokenOfAuthorization("OAuth AbCdEfGh.1j-Kl_Mn~"))
    }

    @Test fun noTokenFromAnotherSchemeOrAnEmptyOne() {
        assertNull(SoundCloudBridge.tokenOfAuthorization(null))
        assertNull(SoundCloudBridge.tokenOfAuthorization(""))
        assertNull(SoundCloudBridge.tokenOfAuthorization("OAuth"))
        assertNull(SoundCloudBridge.tokenOfAuthorization("OAuth "))
        assertNull(SoundCloudBridge.tokenOfAuthorization("OAuth undefined"))
        assertNull(SoundCloudBridge.tokenOfAuthorization("OAuth null"))
        assertNull(SoundCloudBridge.tokenOfAuthorization("OAuth abcdefghijkl"))
        assertNull(SoundCloudBridge.tokenOfAuthorization("Bearer $tok"))
        assertNull(SoundCloudBridge.tokenOfAuthorization("Basic dXNlcjpwYXNz"))
        assertNull(SoundCloudBridge.tokenOfAuthorization("OAuth two words"))
    }

    @Test fun theSchemeIsAllTheDiagnosticsSee() {
        assertEquals("OAuth", SoundCloudBridge.schemeOf("OAuth $tok"))
        assertEquals("Bearer", SoundCloudBridge.schemeOf("Bearer xyz"))
        assertEquals("other", SoundCloudBridge.schemeOf(tok))
        assertNull(SoundCloudBridge.schemeOf(null))
        assertFalse(SoundCloudBridge.Found(tok, "header:api-mobi.soundcloud.com").toString().contains(tok))
    }

    @Test fun onlySoundCloudsApiHosts() {
        assertTrue(SoundCloudBridge.isApiHost("api-v2.soundcloud.com"))
        assertTrue(SoundCloudBridge.isApiHost("api-mobi.soundcloud.com"))
        assertTrue(SoundCloudBridge.isApiHost("api.soundcloud.com"))
        assertFalse(SoundCloudBridge.isApiHost("m.soundcloud.com"))
        assertFalse(SoundCloudBridge.isApiHost("api-auth.soundcloud.com"))
        assertFalse(SoundCloudBridge.isApiHost("api-mobi.soundcloud.com.evil.example"))
        assertFalse(SoundCloudBridge.isApiHost(null))
    }

    // ---- the client id

    @Test fun theClientIdOfTheMobileAndDesktopApis() {
        assertEquals(cid, SoundCloudBridge.apiClientId("https://api-mobi.soundcloud.com/mixed-selections?client_id=$cid&limit=10&app_locale=en"))
        assertEquals(cid, SoundCloudBridge.apiClientId("https://api-mobi.soundcloud.com/me?app_version=1761&client_id=$cid"))
        assertEquals(cid, SoundCloudBridge.apiClientId("https://api-v2.soundcloud.com/me?client_id=$cid&app_version=1761"))
        assertEquals(cid, SoundCloudBridge.apiClientId("https://api.soundcloud.com/resolve?url=x&client_id=$cid"))
    }

    @Test fun noClientIdFromOtherHostsOrShapes() {
        assertNull(SoundCloudBridge.apiClientId("https://m.soundcloud.com/?client_id=$cid"))
        assertNull(SoundCloudBridge.apiClientId("https://example.com/api-mobi.soundcloud.com?client_id=$cid"))
        assertNull(SoundCloudBridge.apiClientId("https://api-mobi.soundcloud.com/me?client_id=short"))
        assertNull(SoundCloudBridge.apiClientId("https://api-mobi.soundcloud.com/me"))
        assertNull(SoundCloudBridge.apiClientId("not a url"))
    }

    // ---- cookies of every host, and the order of the sources

    @Test fun theCookieOfAnySoundCloudHost() {
        val jars = mapOf(
            SoundCloudBridge.SITE to "sc_anonymous_id=x; datadome=k",
            SoundCloudBridge.MOBILE_SITE to "sc_anonymous_id=x; oauth_token=$tok",
            "https://secure.soundcloud.com" to null,
        )
        val f = SoundCloudBridge.pick(null, jars, null)!!
        assertEquals(tok, f.token)
        assertEquals("cookie:m.soundcloud.com", f.source)
    }

    @Test fun theHeaderFirstThenCookiesThenLocalStorage() {
        val header = SoundCloudBridge.Found("2-1-1-HeaderTok", "header:api-mobi.soundcloud.com")
        val storage = SoundCloudBridge.Found("2-1-1-StoredTok", "localStorage:auth")
        val withCookie = mapOf("https://secure.soundcloud.com" to "oauth_token=2-1-1-CookieTok")
        assertEquals("header:api-mobi.soundcloud.com", SoundCloudBridge.pick(header, withCookie, storage)!!.source)
        assertEquals("cookie:secure.soundcloud.com", SoundCloudBridge.pick(null, withCookie, storage)!!.source)
        assertEquals("localStorage:auth", SoundCloudBridge.pick(null, mapOf(SoundCloudBridge.SITE to "a=1"), storage)!!.source)
        assertNull(SoundCloudBridge.pick(null, emptyMap(), null))
    }

    @Test fun theJarsAreKeptAsOneAndTheirNamesAloneAreLogged() {
        val merged = SoundCloudBridge.mergeCookies(listOf("a=1; datadome=k", null, "a=2; oauth_token=$tok", ""))
        assertEquals("a=1; datadome=k; oauth_token=$tok", merged)
        assertEquals("k", SoundCloudBridge.dataDomeCookie(merged))
        assertEquals(listOf("a", "datadome", "oauth_token"), SoundCloudBridge.cookieNames(merged))
        assertEquals(emptyList<String>(), SoundCloudBridge.cookieNames(null))
    }

    // ---- localStorage

    /** As evaluateJavascript hands over the script's string: JSON-encoded once more. */
    private fun asEvaluated(json: String) = kotlinx.serialization.json.JsonPrimitive(json).toString()

    @Test fun aTokenUnderAKeyNamingIt() {
        val dump = """{"V2::local::oauth_token":"$tok","sc_anonymous_id":null}"""
        val f = SoundCloudBridge.tokenFromStorage(asEvaluated(dump))!!
        assertEquals(tok, f.token)
        assertEquals("localStorage:V2::local::oauth_token", f.source)
        // The raw object is read the same.
        assertEquals(tok, SoundCloudBridge.tokenFromStorage(dump)!!.token)
    }

    @Test fun aTokenInsideAStoredObject() {
        val stored = """{"user":{"id":46212435,"username":"xlollx"},"session":{"accessToken":"$tok","expiresAt":1790000000}}"""
        val dump = """{"persist:auth":${kotlinx.serialization.json.JsonPrimitive(stored)},"theme":null}"""
        val f = SoundCloudBridge.tokenFromStorage(asEvaluated(dump))!!
        assertEquals(tok, f.token)
        assertEquals("localStorage:persist:auth", f.source)
    }

    @Test fun aTokenOfAnotherShapeOnlyUnderAnOAuthField() {
        val dump = """{"auth":${kotlinx.serialization.json.JsonPrimitive("""{"oauth_token":"Zx9Yw8Vu7Ts6Rq5P"}""")},"csrf_token":"abcdefghijklmnop"}"""
        assertEquals("Zx9Yw8Vu7Ts6Rq5P", SoundCloudBridge.tokenFromStorage(asEvaluated(dump))!!.token)
        assertEquals("Zx9Yw8Vu7Ts6Rq5P", SoundCloudBridge.tokenFromStorage("""{"oauth_token":"\"Zx9Yw8Vu7Ts6Rq5P\""}""")!!.token)
        // A token-like value of another kind (a CSRF or analytics token) is not the account's.
        assertNull(SoundCloudBridge.tokenFromStorage("""{"csrf_token":"abcdefghijklmnop","ddToken":"1234567890abcdef"}"""))
    }

    @Test fun theSoundCloudShapeWinsOverALooseOne() {
        val dump = """{"access_token":"Loose1234567890","auth":${kotlinx.serialization.json.JsonPrimitive("""{"token":"$tok"}""")}}"""
        assertEquals(tok, SoundCloudBridge.tokenFromStorage(dump)!!.token)
    }

    @Test fun nothingInAnEmptyOrBrokenDump() {
        assertNull(SoundCloudBridge.tokenFromStorage(null))
        assertNull(SoundCloudBridge.tokenFromStorage(""))
        assertNull(SoundCloudBridge.tokenFromStorage("\"\""))
        assertNull(SoundCloudBridge.tokenFromStorage("null"))
        assertNull(SoundCloudBridge.tokenFromStorage("{not json"))
        assertNull(SoundCloudBridge.tokenFromStorage(asEvaluated("""{"sc_anonymous_id":null,"volume":"0.8"}""")))
    }

    @Test fun theKeysAreNamesOnly() {
        val dump = asEvaluated("""{"V2::local::oauth_token":"$tok","volume":null}""")
        assertEquals(listOf("V2::local::oauth_token", "volume"), SoundCloudBridge.storageKeys(dump))
        assertEquals(emptyList<String>(), SoundCloudBridge.storageKeys("garbage"))
    }

    // ---- when the page is signed in, and what stays on the page

    @Test fun signedInOnlyOffTheSignInForm() {
        assertTrue(SoundCloudBridge.looksSignedIn("https://m.soundcloud.com/"))
        assertTrue(SoundCloudBridge.looksSignedIn("https://m.soundcloud.com/discover"))
        assertTrue(SoundCloudBridge.looksSignedIn("https://soundcloud.com/you/library"))
        assertFalse(SoundCloudBridge.looksSignedIn("https://soundcloud.com/signin"))
        assertFalse(SoundCloudBridge.looksSignedIn("https://m.soundcloud.com/signin?redirect=1"))
        assertFalse(SoundCloudBridge.looksSignedIn("https://secure.soundcloud.com/web-auth?client_id=x"))
        assertFalse(SoundCloudBridge.looksSignedIn("https://accounts.google.com/o/oauth2/auth"))
        assertFalse(SoundCloudBridge.looksSignedIn(null))
        assertFalse(SoundCloudBridge.looksSignedIn("about:blank"))
    }

    @Test fun theAppAndTheStoresAreHandoffs() {
        assertTrue(SoundCloudBridge.isAppHandoff("intent://open?url=x#Intent;scheme=soundcloud;package=com.soundcloud.android;end"))
        assertTrue(SoundCloudBridge.isAppHandoff("soundcloud://discover"))
        assertTrue(SoundCloudBridge.isAppHandoff("market://details?id=com.soundcloud.android"))
        assertTrue(SoundCloudBridge.isAppHandoff("https://play.google.com/store/apps/details?id=com.soundcloud.android"))
        assertTrue(SoundCloudBridge.isAppHandoff("https://soundcloud.app.goo.gl/abc"))
        assertTrue(SoundCloudBridge.isAppHandoff("https://soundcloud.page.link/abc"))
        assertTrue(SoundCloudBridge.isAppHandoff("https://soundcloud.app.link/abc"))
        assertTrue(SoundCloudBridge.isAppHandoff("https://apps.apple.com/app/id336353151"))
        assertFalse(SoundCloudBridge.isAppHandoff("https://m.soundcloud.com/"))
        assertFalse(SoundCloudBridge.isAppHandoff("https://secure.soundcloud.com/web-auth"))
        assertFalse(SoundCloudBridge.isAppHandoff("https://accounts.google.com/o/oauth2/auth"))
        assertFalse(SoundCloudBridge.isAppHandoff("https://www.facebook.com/login"))
    }
}
