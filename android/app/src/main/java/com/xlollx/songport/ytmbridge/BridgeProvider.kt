package com.xlollx.songport.ytmbridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.xlollx.songport.ytmbridge.app.BuildConfig
import java.security.MessageDigest

/**
 * The interface Trackhop talks to, through `ContentResolver.call()`.
 *
 * Every call checks who is asking: only the Trackhop package, signed with one of the known
 * certificates, gets an answer. Anything else receives an error and no data. The methods are
 * Trackhop's own [BridgeCore], the code its GitHub and F-Droid build runs in-process, so both
 * builds see the same connectors. The session cookies themselves are never returned: callers only
 * get playlists, tracks and short-lived tokens.
 */
class BridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return err("no context")
        if (!Allowed.callerOk(ctx, callingPackage)) return err("caller not allowed")
        // Most users never open the Bridge: its daily update check starts with Trackhop's first call.
        UpdateCheck.ensureScheduled(ctx.applicationContext)
        val out = BridgeCore.call(ctx.applicationContext, method, arg, extras)
        // The status calls report this app's version, not the built-in connectors' marker.
        if (method == "status" || method.endsWith(".status")) out.putInt("version", BuildConfig.VERSION_CODE)
        return out
    }

    private fun err(message: String) = Bundle().apply { putString("error", message) }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/** Who may call the provider. Package name and signing certificate must both match. */
object Allowed {
    /** Trackhop's package id, unchanged since it was called Songport. */
    private const val TRACKHOP = "com.xlollx.songport"

    /**
     * SHA-256 of the signing certificates of official Trackhop builds (upper-case hex, no colons):
     * the release upload key, the debug key used by the CI builds, and, once the app is on Google
     * Play, the Play App Signing key. A fork that rebuilds Trackhop with its own key must also
     * rebuild the Bridge with its own list.
     */
    private val SHA256 = setOf(
        "21EADF268C72F85565961A6045BF4BE5ABAF256D680D1E0D808D93ED22D35032", // release upload key
        "8BEC806768B3789A40E741BBFCEC462C71950E32A7A1DA15665E95BB7A0AE754", // CI debug key
    )

    fun callerOk(ctx: Context, callingPackage: String?): Boolean {
        val pkg = callingPackage ?: return false
        if (pkg == ctx.packageName) return true
        if (pkg != TRACKHOP) return false
        val pm = ctx.packageManager
        val signers = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } catch (e: Exception) {
            null
        } ?: return false
        return signers.any { sha256(it.toByteArray()) in SHA256 }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
}
