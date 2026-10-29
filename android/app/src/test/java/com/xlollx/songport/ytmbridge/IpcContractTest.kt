package com.xlollx.songport.ytmbridge

import android.content.pm.ProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The interface Trackhop's Google Play build relies on: every method it calls must still be served
 * after a sync from Trackhop, and nobody else gets an answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class IpcContractTest {

    /** What Trackhop calls (its providers/...BridgeProvider.kt and data/Diagnostics.kt), plus what older versions called. */
    private val called = setOf(
        "status", "disconnect", "playlists", "playlistInfo", "tracks", "search", "stats", "create", "add", "remove",
        "rename", "delete", "visibility", "reorder",
        "amazon.status", "amazon.disconnect", "amazon.playlists", "amazon.tracks", "amazon.search", "amazon.create",
        "amazon.add", "amazon.remove", "amazon.rename", "amazon.delete", "amazon.visibility",
        "spotify.status", "spotify.disconnect", "spotify.token", "spotify.playlists", "spotify.version",
        "spotify.playlistInfo", "spotify.tracks", "spotify.search", "spotify.track", "spotify.create", "spotify.add",
        "spotify.remove", "spotify.rename", "spotify.delete", "spotify.visibility", "spotify.reorder",
        "apple.status", "apple.disconnect", "apple.tokens",
        "deezer.status", "deezer.disconnect", "deezer.playlists", "deezer.playlistInfo", "deezer.version", "deezer.tracks",
        "deezer.search", "deezer.track", "deezer.create", "deezer.add", "deezer.remove", "deezer.rename", "deezer.delete",
        "soundcloud.status", "soundcloud.disconnect", "soundcloud.playlists", "soundcloud.playlistInfo", "soundcloud.version",
        "soundcloud.tracks", "soundcloud.search", "soundcloud.create", "soundcloud.add", "soundcloud.remove", "soundcloud.reorder",
        "soundcloud.rename", "soundcloud.visibility", "soundcloud.delete",
    )

    /** The sign-in actions Trackhop starts (its providers' LOGIN_ACTION), each exported by this app's manifest. */
    private val loginActions = listOf("LOGIN", "AMAZON_LOGIN", "SPOTIFY_LOGIN", "APPLE_LOGIN", "DEEZER_LOGIN", "SOUNDCLOUD_LOGIN")
        .map { "com.xlollx.songport.ytmbridge.$it" }

    @Test fun everySignInActionIsExported() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        loginActions.forEach { a -> assertTrue("$a is declared", manifest.contains("<action android:name=\"$a\" />")) }
        // The web sign-ins (Spotify, Apple Music, Deezer, SoundCloud) share one exported activity.
        val web = Regex("""<activity\s+android:name="com\.xlollx\.songport\.ytmbridge\.WebLoginActivity"(.*?)</activity>""", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)?.groupValues?.get(1) ?: ""
        assertTrue(web.contains("android:exported=\"true\""))
        listOf("SPOTIFY_LOGIN", "APPLE_LOGIN", "DEEZER_LOGIN", "SOUNDCLOUD_LOGIN").forEach { assertTrue(it, web.contains("ytmbridge.$it")) }
    }

    /** The intents the shared sign-in screen builds use exactly the actions the manifest exports. */
    @Test fun signInIntentsUseTheExportedActions() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("com.xlollx.songport.ytmbridge.DEEZER_LOGIN", WebLoginActivity.intent(ctx, WebLoginActivity.DEEZER).action)
        assertEquals("com.xlollx.songport.ytmbridge.SOUNDCLOUD_LOGIN", WebLoginActivity.intent(ctx, WebLoginActivity.SOUNDCLOUD).action)
    }

    /** The new services answer their status in-process (not "unknown method"): what Trackhop's Play build checks. */
    @Test fun newServicesAnswerStatus() {
        val ctx = RuntimeEnvironment.getApplication()
        for (m in listOf("deezer.status", "soundcloud.status")) {
            val b = BridgeCore.call(ctx, m, null, null)
            assertNull(m, b.getString("error"))
            assertTrue(m, b.containsKey("connected"))
            assertFalse(m, b.getBoolean("connected"))
        }
    }

    @Test fun everyMethodTrackhopCallsIsServed() {
        val core = File("../bridge/src/main/java/com/xlollx/songport/ytmbridge/BridgeCore.kt").readText()
        val served = Regex("""^\s*"([A-Za-z.]+)"\s*->""", RegexOption.MULTILINE).findAll(core).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet<String>(), called - served)
    }

    @Test fun anotherAppGetsAnErrorAndNoData() {
        val ctx = RuntimeEnvironment.getApplication()
        val provider = BridgeProvider().apply { attachInfo(ctx, ProviderInfo().apply { authority = "com.xlollx.songport.ytmbridge.provider" }) }
        val answer = provider.call("playlists", null, null)
        assertEquals("caller not allowed", answer.getString("error"))
        assertNull(answer.getString("json"))
        assertFalse(Allowed.callerOk(ctx, "com.example.other"))
        assertFalse(Allowed.callerOk(ctx, null))
        // Trackhop's package id is not enough: it must also carry a known signature.
        assertFalse(Allowed.callerOk(ctx, "com.xlollx.songport"))
        assertTrue(Allowed.callerOk(ctx, ctx.packageName))
    }
}
