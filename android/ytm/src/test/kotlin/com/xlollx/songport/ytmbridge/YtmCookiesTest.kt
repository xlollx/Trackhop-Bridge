package com.xlollx.songport.ytmbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtmCookiesTest {
    @Test fun headerValue() {
        assertEquals("SID=s1; SAPISID=abc; HSID=h", YtmCookies.parse("SID=s1; SAPISID=abc; HSID=h"))
    }

    @Test fun wholeHeaderLineAnyCase() {
        assertEquals("SAPISID=abc; PREF=f6=40000000", YtmCookies.parse("cookie: SAPISID=abc; PREF=f6=40000000\n"))
        assertEquals("SAPISID=abc", YtmCookies.parse("Cookie: SAPISID=abc"))
    }

    @Test fun secureTwinIsEnough() {
        assertEquals("__Secure-3PAPISID=x", YtmCookies.parse("__Secure-3PAPISID=x"))
    }

    /** The same reading for another site (the server's Spotify and Deezer connectors): no SAPISID needed. */
    @Test fun otherSitesKeepTheirOwnCookies() {
        val js = """[{"domain":".spotify.com","name":"sp_dc","value":"d"},{"domain":".youtube.com","name":"SAPISID","value":"abc"},{"domain":"open.spotify.com","name":"sp_key","value":"k"}]"""
        assertEquals("sp_dc=d; sp_key=k", YtmCookies.header(js, listOf("spotify.com")))
        assertNull(YtmCookies.parse(js.replace("SAPISID", "PREF")))
        assertEquals("arl=a; sid=s", YtmCookies.header("cookie: arl=a; sid=s", listOf("deezer.com")))
        assertNull(YtmCookies.header("  ", listOf("deezer.com")))
    }

    @Test fun signedOutIsRefused() {
        assertNull(YtmCookies.parse("VISITOR_INFO1_LIVE=v; YSC=y"))
        assertNull(YtmCookies.parse("   "))
    }

    @Test fun netscapeExportKeepsOnlyGoogleAndYouTube() {
        val txt = """
            # Netscape HTTP Cookie File
            # This is a generated file! Do not edit.

            .youtube.com	TRUE	/	TRUE	1893456000	SAPISID	abc
            #HttpOnly_.youtube.com	TRUE	/	TRUE	1893456000	__Secure-3PSID	sid3
            .google.com	TRUE	/	TRUE	1893456000	SID	g1
            .example.com	TRUE	/	FALSE	1893456000	tracker	nope
        """.trimIndent()
        assertEquals("SAPISID=abc; __Secure-3PSID=sid3; SID=g1", YtmCookies.parse(txt))
    }

    @Test fun jsonExport() {
        val js = """[{"domain":".youtube.com","name":"SAPISID","value":"abc"},{"domain":"music.youtube.com","name":"YSC","value":"y"},{"domain":".other.org","name":"x","value":"1"}]"""
        assertEquals("SAPISID=abc; YSC=y", YtmCookies.parse(js))
    }

    @Test fun laterCopyWins() {
        assertEquals("SAPISID=new", YtmCookies.parse("SAPISID=old; SAPISID=new"))
    }
}
