package com.xlollx.songport.ytmbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtmAuthTest {
    @Test fun sapisidOrItsTwin() {
        assertEquals("a", YtmAuth.sapisid("X=1; SAPISID=a; __Secure-3PAPISID=b"))
        assertEquals("b", YtmAuth.sapisid("X=1; __Secure-3PAPISID=b"))
        assertNull(YtmAuth.sapisid("X=1"))
    }

    /** The web player's own form: SAPISIDHASH <ts>_<sha1("<ts> <SAPISID> https://music.youtube.com")>. */
    @Test fun authorizationAsThePlayerSendsIt() {
        assertEquals(
            "SAPISIDHASH 1700000000_2a51c431c9056f051275282329baf9f425825f28",
            YtmAuth.authorization("SAPISID=SAPI-abc_123; SID=x", nowSeconds = 1700000000),
        )
    }

    @Test(expected = BridgeException::class) fun noSapisidNoAuthorization() {
        YtmAuth.authorization("SID=x")
    }
}
