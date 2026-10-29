package com.xlollx.songport.ytmbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update check's decisions and the parsing of GitHub's answer. Plain JVM, no network. */
class UpdatesTest {

    private val url = "https://github.com/xlollx/Trackhop-Bridge/releases/tag/v1.4.2"
    private fun release(code: Int? = 36, name: String = "1.4.2", minTrackhop: Int = 87, minName: String? = "1.2.4") =
        BridgeRelease(name, url, code?.let { BridgeMeta(it, name, minTrackhop, minName) })

    /** This build is 1.4.1 (35) unless a test says otherwise. */
    private fun decide(release: BridgeRelease?, trackhop: Long? = 87, notifiedCode: Int = 0, notifiedName: String? = null) =
        Updates.decide(release, 35, "1.4.1", trackhop, notifiedCode, notifiedName)

    @Test fun noReleaseMeansNothing() {
        assertEquals(UpdateDecision.None, decide(null))
    }

    @Test fun sameOrOlderReleaseIsNotAnUpdate() {
        assertEquals(UpdateDecision.None, decide(release(code = 35, name = "1.4.1")))
        assertEquals(UpdateDecision.None, decide(release(code = 34, name = "1.4.0")))
    }

    @Test fun newerCompatibleReleaseIsAnnounced() {
        assertEquals(UpdateDecision.Available(release(), notify = true), decide(release(), trackhop = 87))
        assertEquals(UpdateDecision.Available(release(), notify = true), decide(release(), trackhop = 90))
    }

    @Test fun versionCodeDecidesWhenKnown() {
        // A name that compares lower does not hide a higher versionCode.
        assertTrue(decide(release(code = 36, name = "1.4.1")) is UpdateDecision.Available)
    }

    @Test fun tooOldTrackhopGetsTheCardNotTheNotification() {
        assertEquals(UpdateDecision.NeedsTrackhop(release(minTrackhop = 90), 90, "1.2.4"), decide(release(minTrackhop = 90), trackhop = 87))
        val unnamed = release(minTrackhop = 90, minName = null)
        assertEquals(UpdateDecision.NeedsTrackhop(unnamed, 90, null), decide(unnamed, trackhop = 89))
    }

    @Test fun missingTrackhopCountsAsCompatible() {
        assertEquals(UpdateDecision.Available(release(minTrackhop = 999), notify = true), decide(release(minTrackhop = 999), trackhop = null))
    }

    @Test fun missingBridgeJsonCountsAsCompatibleAndComparesNames() {
        val old = release(code = null, name = "1.4.2")
        assertEquals(UpdateDecision.Available(old, notify = true), decide(old, trackhop = 1))
        assertEquals(UpdateDecision.None, decide(release(code = null, name = "1.4.1")))
        assertEquals(UpdateDecision.None, decide(release(code = null, name = "1.3.21")))
        assertEquals(UpdateDecision.Available(old, notify = false), decide(old, notifiedName = "1.4.2"))
    }

    @Test fun announcedOncePerVersion() {
        assertEquals(UpdateDecision.Available(release(), notify = false), decide(release(), notifiedCode = 36))
        assertEquals(UpdateDecision.Available(release(), notify = true), decide(release(), notifiedCode = 35))
        assertEquals(UpdateDecision.Available(release(code = 37), notify = true), decide(release(code = 37), notifiedCode = 36))
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(Updates.compareVersions("1.4.10", "1.4.9") > 0)
        assertTrue(Updates.compareVersions("1.4", "1.4.1") < 0)
        assertEquals(0, Updates.compareVersions("1.4.0", "1.4"))
    }

    @Test fun parsesGitHubsAnswer() {
        val json = """{"tag_name":"v1.4.2","html_url":"$url","draft":false,"prerelease":false,
            "body":"<!-- bridge.json {\"versionCode\":36,\"versionName\":\"1.4.2\",\"minTrackhop\":87,\"minTrackhopName\":\"1.2.4\"} -->\n**Full Changelog**: ...",
            "assets":[{"name":"TrackhopBridge-v1.4.2.apk"},{"name":"bridge.json"}]}"""
        assertEquals(release(), Updates.parseRelease(json))
    }

    @Test fun releaseWithoutBridgeJsonHasNoMeta() {
        val json = """{"tag_name":"v1.4.0","html_url":"https://github.com/xlollx/Trackhop-Bridge/releases/tag/v1.4.0","body":"**Full Changelog**"}"""
        assertEquals(BridgeRelease("1.4.0", "https://github.com/xlollx/Trackhop-Bridge/releases/tag/v1.4.0", null), Updates.parseRelease(json))
        assertNull(Updates.parseMeta("<!-- bridge.json {\"versionName\":\"1.4.2\"} -->"))
        assertNull(Updates.parseMeta(null))
    }

    @Test fun foreignLinksAndBadAnswersAreRefused() {
        val json = """{"tag_name":"v1.4.2","html_url":"https://example.com/evil","body":null}"""
        assertEquals(Updates.RELEASES_URL, Updates.parseRelease(json)?.htmlUrl)
        assertNull(Updates.parseRelease("""{"message":"API rate limit exceeded"}"""))
        assertNull(Updates.parseRelease("""{"tag_name":"v1.5.0","draft":true}"""))
        assertNull(Updates.parseRelease("not json"))
    }
}
