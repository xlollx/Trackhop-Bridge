package com.xlollx.songport.ytmbridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** What a release's bridge.json says: its version and the oldest Trackhop it works with. */
data class BridgeMeta(val versionCode: Int, val versionName: String, val minTrackhop: Int, val minTrackhopName: String?)

/** The latest Bridge release on GitHub. [meta] is null for releases published without bridge.json (before 1.4.1). */
data class BridgeRelease(val versionName: String, val htmlUrl: String, val meta: BridgeMeta?) {
    val name: String get() = meta?.versionName ?: versionName
}

/** What to do about the latest release. */
sealed interface UpdateDecision {
    /** Nothing newer than the installed Bridge. */
    data object None : UpdateDecision
    /** Newer and usable with the installed Trackhop (or without one); [notify] when not announced yet. */
    data class Available(val release: BridgeRelease, val notify: Boolean) : UpdateDecision
    /** Newer, but the installed Trackhop is older than the release needs: no notification. */
    data class NeedsTrackhop(val release: BridgeRelease, val minTrackhop: Int, val minTrackhopName: String?) : UpdateDecision
}

/** The update check's logic, free of Android so the tests can run it directly. */
object Updates {
    const val RELEASES_URL = "https://github.com/xlollx/Trackhop-Bridge/releases"

    /**
     * [installedCode]/[installedName] are this Bridge's, [trackhopCode] the installed Trackhop's
     * versionCode (null when it is not installed), [lastNotifiedCode]/[lastNotifiedName] the last
     * release announced (the name only matters for releases without bridge.json).
     */
    fun decide(
        release: BridgeRelease?, installedCode: Int, installedName: String, trackhopCode: Long?,
        lastNotifiedCode: Int = 0, lastNotifiedName: String? = null,
    ): UpdateDecision {
        if (release == null) return UpdateDecision.None
        val meta = release.meta
        val newer = if (meta != null) meta.versionCode > installedCode else compareVersions(release.versionName, installedName) > 0
        if (!newer) return UpdateDecision.None
        // No bridge.json (old releases) or no Trackhop: nothing to be incompatible with.
        if (meta != null && trackhopCode != null && trackhopCode < meta.minTrackhop) {
            return UpdateDecision.NeedsTrackhop(release, meta.minTrackhop, meta.minTrackhopName)
        }
        val notified = if (meta != null) meta.versionCode <= lastNotifiedCode else release.versionName == lastNotifiedName
        return UpdateDecision.Available(release, notify = !notified)
    }

    /** The answer of GitHub's releases/latest, or null when it is not a usable release. */
    fun parseRelease(json: String): BridgeRelease? = try {
        val o = Json.parseToJsonElement(json).jsonObject
        val tag = o.str("tag_name")?.removePrefix("v")?.takeIf { it.isNotBlank() }
        if (tag == null || o.str("draft") == "true" || o.str("prerelease") == "true") null
        else BridgeRelease(tag, safeUrl(o.str("html_url")), parseMeta(o.str("body")))
    } catch (e: Exception) {
        null
    }

    /**
     * The bridge.json the release workflow also writes into the release notes, as a hidden comment
     * (`<!-- bridge.json {...} -->`): reading the asset itself would mean a second host, GitHub's
     * download server. Null when the comment is absent or malformed.
     */
    fun parseMeta(body: String?): BridgeMeta? {
        val raw = body?.let { META.find(it)?.groupValues?.get(1) } ?: return null
        return try {
            val o = Json.parseToJsonElement(raw).jsonObject
            BridgeMeta(
                versionCode = o.int("versionCode") ?: return null,
                versionName = o.str("versionName") ?: return null,
                minTrackhop = o.int("minTrackhop") ?: return null,
                minTrackhopName = o.str("minTrackhopName")?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Dotted versions compared number by number ("1.4.10" > "1.4.9"); missing parts count as 0. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** Only this repository's pages are opened; anything else falls back to the releases page. */
    private fun safeUrl(url: String?): String =
        url?.takeIf { it.startsWith("$RELEASES_URL/", ignoreCase = true) } ?: RELEASES_URL

    private val META = Regex("""<!--\s*bridge\.json\s*(\{[^}]*\})\s*-->""")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}
