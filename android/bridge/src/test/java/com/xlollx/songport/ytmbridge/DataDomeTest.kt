package com.xlollx.songport.ytmbridge

import com.xlollx.songport.ytmbridge.DataDome.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the sign-in screen and the SoundCloud client make of DataDome's pages and answers, and the
 * client id taken from the web app's own requests. Pure functions: no Android, no network.
 */
class DataDomeTest {
    private val cid = "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6"

    @Test fun theBlockFrameIsBlocked() {
        assertEquals(Kind.BLOCKED, DataDome.kindOf(
            "https://geo.captcha-delivery.com/captcha/?initialCid=AHrlqAAA&hash=05B30BD9055986BD2EE8F5A199D973&cid=abc~def&t=bv&referer=https%3A%2F%2Fsoundcloud.com%2Fsignin&s=33264&e=x&dm=cd"))
    }

    @Test fun aPuzzleIsACaptchaAndTheBrowserCheckACheck() {
        assertEquals(Kind.CAPTCHA, DataDome.kindOf("https://geo.captcha-delivery.com/captcha/?initialCid=x&cid=y&t=fe&referer=https%3A%2F%2Fsoundcloud.com%2F"))
        assertEquals(Kind.CHECK, DataDome.kindOf("https://geo.captcha-delivery.com/interstitial/?initialCid=x&hash=y&cid=z&referer=https%3A%2F%2Fsoundcloud.com%2F"))
    }

    @Test fun anythingElseIsNothing() {
        // DataDome's own scripts and images, and ordinary pages, say nothing about a block.
        assertNull(DataDome.kindOf("https://js.datadome.co/tags.js"))
        assertNull(DataDome.kindOf("https://ct.captcha-delivery.com/c.js"))
        assertNull(DataDome.kindOf("https://geo.captcha-delivery.com/captcha/img/slider.png"))
        assertNull(DataDome.kindOf("https://soundcloud.com/signin?t=bv"))
        assertNull(DataDome.kindOf("https://evilcaptcha-delivery.com/captcha/?t=bv"))
        assertNull(DataDome.kindOf("not a url"))
    }

    @Test fun refusedApiCallsAreReadFromTheirBody() {
        assertEquals(Kind.BLOCKED, DataDome.kindOfBody(
            """{"url":"https://geo.captcha-delivery.com/captcha/?initialCid=AHrl&hash=05B3&cid=k~x&t=bv&referer=https%3A%2F%2Fapi-v2.soundcloud.com%2Fme&s=33264"}"""))
        assertEquals(Kind.BLOCKED, DataDome.kindOfBody(
            """{"url":"https:\/\/geo.captcha-delivery.com\/captcha\/?initialCid=AHrl&cid=k&t=bv&s=1"}"""))
        assertEquals(Kind.CAPTCHA, DataDome.kindOfBody(
            """{"url":"https://geo.captcha-delivery.com/captcha/?initialCid=AHrl&hash=05B3&cid=k~x&t=fe&referer=x"}"""))
        // The HTML page DataDome serves instead of a page.
        assertEquals(Kind.BLOCKED, DataDome.kindOfBody(
            """<html><script>var dd={'rt':'c','cid':'AHrl','hsh':'05B3','t':'bv','s':33264,'e':'x','host':'geo.captcha-delivery.com','cookie':'k~x'}</script><script src="https://ct.captcha-delivery.com/c.js"></script></html>"""))
        assertEquals(Kind.CAPTCHA, DataDome.kindOfBody(
            """<html><script>var dd={'rt':'c','cid':'AHrl','hsh':'05B3','t':'fe','host':'geo.captcha-delivery.com'}</script></html>"""))
    }

    @Test fun anOrdinaryRefusalIsNotDataDome() {
        assertNull(DataDome.kindOfBody("""{"error":"forbidden"}"""))
        assertNull(DataDome.kindOfBody(""))
    }

    @Test fun theClientIdComesFromTheWebAppsOwnRequests() {
        assertEquals(cid, SoundCloudBridge.clientIdOf("https://api-v2.soundcloud.com/me?client_id=$cid&app_version=1761&app_locale=en"))
        assertEquals(cid, SoundCloudBridge.clientIdOf("https://api-v2.soundcloud.com/announcements?app_locale=en&client_id=$cid"))
        assertNull(SoundCloudBridge.clientIdOf("https://api-v2.soundcloud.com/me?client_id=short&x=1"))
        assertNull(SoundCloudBridge.clientIdOf("https://api-v2.soundcloud.com/me?client_id=${cid}X"))
        assertNull(SoundCloudBridge.clientIdOf("https://api-v2.soundcloud.com/me?my_client_id=$cid"))
        assertNull(SoundCloudBridge.clientIdOf("https://api-v2.soundcloud.com/me"))
    }

    @Test fun theDataDomeCookieIsReadFromTheJar() {
        assertEquals("k~x_y", SoundCloudBridge.dataDomeCookie("oauth_token=2-1; datadome=k~x_y; sc_anonymous_id=z"))
        assertNull(SoundCloudBridge.dataDomeCookie("oauth_token=2-1"))
    }
}
