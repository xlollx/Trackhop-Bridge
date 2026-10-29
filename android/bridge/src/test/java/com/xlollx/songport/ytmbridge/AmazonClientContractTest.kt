package com.xlollx.songport.ytmbridge

import android.app.Application
import kotlinx.serialization.json.JsonElement
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Amazon Music's web player GraphQL endpoint, against a local mock server. Unofficial and
 * undocumented: the shapes are the ones the client reads (captured from the player, per its own
 * comments), so these tests pin the client's behaviour, not Amazon's. Refusals (401/403, auth
 * error codes) make the client run the real player in a WebView, which a JVM test cannot do: those
 * paths are left to the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AmazonClientContractTest {
    companion object { init { BridgeMock.install() } }

    private lateinit var app: Application
    private lateinit var amazon: AmazonClient
    private val cookies = "session-id=262-1234567-7654321; ubid-main=133-1; at-main=Atza|" + "x".repeat(40)

    @Before fun setUp() {
        BridgeMock.start()
        BridgeMock.fixed = { r ->
            if (r.getHeader(BridgeMock.ORIGINAL_HOST) == "music.amazon.it" && r.requestUrl!!.encodedPath == "/") MockResponse().setBody("""<html><script>
                window.amznMusic = window.amznMusic || {}; var x = "amznMusic.appConfig is set below";
                amznMusic.appConfig = {"accessToken":"Atna|tok","customerId":"A1CUSTOMER","deviceId":"13700000000000000","deviceType":"A16ZV8BU3SN1N3",
                  "csrf":{"token":"gCSRF+tok=","ts":1700000000,"rnd":"424242"},"displayName":"Ñandú {braces} \"q\""};
                </script></html>""") else null
        }
        app = RuntimeEnvironment.getApplication()
        AmazonSession.save(app, cookies, "music.amazon.it", null)
        AmazonClient.forgetConfig()
        amazon = AmazonClient(app)
    }

    @After fun tearDown() { BridgeMock.stop() }

    private fun call(i: Int): RecordedRequest = BridgeMock.calls[i]
    private fun RecordedRequest.json(): JsonElement = parseJson(body.clone().readUtf8())
    private fun answer(body: String, code: Int = 200) = BridgeMock.answer(body, code)

    private fun node(i: Int, title: String = "Song $i") = """{"__typename":"Track","id":"B0ASIN${i.toString().padStart(4, '0')}","title":${q("$title (Remastered)")},"shortTitle":${q(title)},
        "duration":${200 + i},"album":{"title":"Album (Deluxe)","shortTitle":"Album"},
        "contributingArtists":{"edges":[{"role":"FEATURED","node":{"name":"Guest"}},{"role":"PRIMARY","node":{"name":"Main $i"}}]},"parentalSettings":{"hasExplicitLanguage":${i % 2 == 0}}}"""
    private fun page(range: IntRange, next: String?) = """{"data":{"playlist":{"id":"PL1","title":"T","tracks":{"edges":[${range.joinToString(",") { """{"cursor":"$it:uuid-$it","node":${node(it)}}""" }}],
        "pageInfo":{"hasNextPage":${next != null},"endCursor":${next?.let { q(it) } ?: "null"}}}}}}"""

    @Test fun playlistReadByCursorWithThePlayersHeaders() {
        answer(page(0..99, "99:uuid-99"))
        answer(page(100..100, null))
        val ts = amazon.playlistTracks("PL1")
        assertEquals(101, ts.size)
        assertEquals("100:uuid-100", ts[100].setVideoId)
        assertEquals(listOf("Main 0", "Guest"), ts[0].artists)
        assertEquals("Song 0", ts[0].title)
        assertEquals(200_000L, ts[0].durationMs)
        assertEquals(true, ts[0].explicit)
        val r = call(0)
        assertEquals("POST", r.method)
        assertEquals("gql.music.amazon.it", r.getHeader(BridgeMock.ORIGINAL_HOST))
        assertEquals("amzn1.application.4ff5579ca2e3407aba989a1f5dbdaf69", r.getHeader("x-api-key"))
        assertEquals("gCSRF+tok=", r.getHeader("csrf-token"))
        assertEquals("1700000000", r.getHeader("csrf-ts"))
        assertEquals("424242", r.getHeader("csrf-rnd"))
        assertEquals("262-1234567-7654321", r.getHeader("x-amzn-session-id"))
        assertEquals("IT", r.getHeader("music-territory"))
        assertEquals("it-IT", r.getHeader("content-language"))
        assertEquals("https://music.amazon.it", r.getHeader("Origin"))
        assertTrue(r.getHeader("Cookie")!!.contains("at-main="))
        val b = r.json()
        assertEquals("playlistDetail", b["operationName"].str)
        assertEquals(64, b["extensions"]["persistedQuery"]["sha256Hash"].str!!.length)
        assertEquals("PL1", b["variables"]["id"].str)
        assertEquals("99:uuid-99", call(1).json()["variables"]["afterCursor"].str)
        assertEquals(2, BridgeMock.calls.size)
    }

    @Test fun duplicateIsNotAFailureButAnotherRefusalIs() {
        answer("""{"data":{"appendTracks":null},"errors":[{"message":"Track already in playlist","extensions":{"code":"DUPLICATE_TRACK"}}]}""", 294)
        amazon.addTrack("PL1", "B0ASIN0001")
        answer("""{"data":{"appendTracks":null},"errors":[{"message":"Playlist is full","extensions":{"code":"PLAYLIST_LIMIT_EXCEEDED"}}]}""", 294)
        try { amazon.addTrack("PL1", "B0ASIN0002"); fail() } catch (e: BridgeException) { assertTrue(e.message!!.contains("PLAYLIST_LIMIT_EXCEEDED")) }
        val v = call(0).json()["variables"]
        assertEquals("B0ASIN0001", v["trackId"].str)
        assertEquals("true", v["rejectDuplicateTracks"].toString())
    }

    @Test fun createPrivateRemoveEntryAndDelete() {
        answer("""{"data":{"createPlaylist":{"id":"PLNEW","title":"Ñandú 🎧"}}}""")
        answer("""{"data":{"removeTracks":{"id":"PLNEW"}}}""")
        answer("""{"data":{"deletePlaylist":{"isDeleted":true}}}""")
        assertEquals("PLNEW", amazon.createPlaylist("Ñandú 🎧").id)
        amazon.removeTrack("PLNEW", "3:uuid-3")
        amazon.removePlaylist("PLNEW")
        assertEquals("PRIVATE", call(0).json()["variables"]["visibility"].str)
        assertEquals("Ñandú 🎧", call(0).json()["variables"]["title"].str)
        assertEquals("3:uuid-3", call(1).json()["variables"]["cursor"].str)
        assertEquals("deletePlaylist", call(2).json()["operationName"].str)
    }

    @Test fun deleteNotConfirmedIsAnError() {
        answer("""{"data":{"deletePlaylist":{"isDeleted":false}}}""")
        try { amazon.removePlaylist("X"); fail() } catch (e: BridgeException) { }
    }

    @Test fun unknownPersistedQueryAsksForAnUpdate() {
        answer("""{"errors":[{"message":"PersistedQueryNotFound","extensions":{"code":"PERSISTED_QUERY_NOT_FOUND"}}]}""")
        try { amazon.libraryPlaylists(); fail() } catch (e: BridgeException) { assertTrue(e.message!!.contains("needs an update")) }
        assertEquals(1, BridgeMock.calls.size)
    }

    @Test fun serverErrorIsAClearErrorWithoutRetrying() {
        answer("<html>Internal Server Error</html>", 500)
        try { amazon.libraryPlaylists(); fail() } catch (e: BridgeException) { assertTrue(e.message, e.message!!.startsWith("Amazon Music 500 on AddToPlaylistModalUserPlaylists")) }
        assertEquals(1, BridgeMock.calls.size)
    }

    @Test fun libraryPlaylistsAndTheAccountName() {
        answer("""{"data":{"user":{"playlists":{"edges":[{"node":{"id":"P1","title":"Uno","trackCount":12}},{"node":{"id":"P2","title":null}},{"node":{}}]}}}}""")
        val ls = amazon.libraryPlaylists()
        assertEquals(listOf("P1" to 12, "P2" to -1), ls.map { it.id to it.count })
        assertEquals("P2", ls[1].name)
        assertEquals(500L, call(0).json()["variables"]["firstLimit"].long)
        assertEquals("Ñandú {braces} \"q\"", amazon.accountName())
    }

    private fun q(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()
}
