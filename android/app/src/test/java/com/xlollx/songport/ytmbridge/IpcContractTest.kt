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
    )

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
