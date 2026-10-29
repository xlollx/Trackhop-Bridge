package com.xlollx.songport.ytmbridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xlollx.songport.ytmbridge.app.BuildConfig
import com.xlollx.songport.ytmbridge.app.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The daily update check: one plain GET to GitHub's API for the latest release, no account, no
 * identifiers, no other host. A newer release that works with the installed Trackhop is announced
 * once by notification; one that needs a newer Trackhop is only shown on the main screen.
 */
object UpdateCheck {
    private const val API = "https://api.github.com/repos/xlollx/Trackhop-Bridge/releases/latest"
    /** Trackhop's package id, unchanged since it was called Songport. */
    private const val TRACKHOP = "com.xlollx.songport"
    private const val WORK = "update-check"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION = 1

    private val scheduled = AtomicBoolean(false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("update_check", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("enabled", on).apply()
        val wm = WorkManager.getInstance(ctx)
        if (on) schedule(ctx, wm) else wm.cancelUniqueWork(WORK)
    }

    /** Schedules the daily work once per process (it survives reboots and updates by itself). */
    fun ensureScheduled(ctx: Context) {
        if (!enabled(ctx) || !scheduled.compareAndSet(false, true)) return
        try {
            schedule(ctx, WorkManager.getInstance(ctx))
        } catch (e: Exception) {
            scheduled.set(false)
        }
    }

    private fun schedule(ctx: Context, wm: WorkManager) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Whether the user has been asked for the notification permission already (asked once only). */
    fun askedPermission(ctx: Context): Boolean = prefs(ctx).getBoolean("asked_permission", false)
    fun markAskedPermission(ctx: Context) = prefs(ctx).edit().putBoolean("asked_permission", true).apply()

    /** The installed Trackhop's versionCode, or null when it is not installed. */
    fun trackhopVersion(ctx: Context): Long? = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(TRACKHOP, 0))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** What the last successful check found, judged against the Trackhop installed now. */
    fun current(ctx: Context): UpdateDecision = decide(ctx, stored(ctx))

    /**
     * Asks GitHub for the latest release and stores it. With [notify] a compatible release not
     * announced yet gets its notification. Returns null when the check failed (offline, rate limit,
     * unexpected answer); failures are otherwise silent.
     */
    fun run(ctx: Context, notify: Boolean): UpdateDecision? {
        val json = fetch() ?: return null
        val release = Updates.parseRelease(json) ?: return null
        val meta = release.meta
        prefs(ctx).edit()
            .putString("release_name", release.versionName)
            .putString("release_url", release.htmlUrl)
            .putInt("release_code", meta?.versionCode ?: 0)
            .putString("release_meta_name", meta?.versionName)
            .putInt("release_min_trackhop", meta?.minTrackhop ?: 0)
            .putString("release_min_trackhop_name", meta?.minTrackhopName)
            .apply()
        val decision = decide(ctx, release)
        if (notify && decision is UpdateDecision.Available && decision.notify && post(ctx, decision.release)) {
            prefs(ctx).edit().putInt("notified_code", decision.release.meta?.versionCode ?: 0)
                .putString("notified_name", decision.release.versionName).apply()
        }
        return decision
    }

    private fun stored(ctx: Context): BridgeRelease? {
        val p = prefs(ctx)
        val name = p.getString("release_name", null) ?: return null
        val code = p.getInt("release_code", 0)
        val meta = if (code > 0) BridgeMeta(
            code, p.getString("release_meta_name", null) ?: name,
            p.getInt("release_min_trackhop", 0), p.getString("release_min_trackhop_name", null),
        ) else null
        return BridgeRelease(name, p.getString("release_url", null) ?: Updates.RELEASES_URL, meta)
    }

    private fun decide(ctx: Context, release: BridgeRelease?): UpdateDecision {
        val p = prefs(ctx)
        return Updates.decide(
            release, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, trackhopVersion(ctx),
            p.getInt("notified_code", 0), p.getString("notified_name", null),
        )
    }

    /** The plain GET: no cookies, no account, a fixed User-Agent instead of the device's. */
    private fun fetch(): String? {
        val conn = URL(API).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Trackhop-Bridge")
            // 403/429 are GitHub's rate limits: try again tomorrow.
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Posts "Trackhop Bridge X.Y.Z is available"; tapping it opens the release page. False when not allowed. */
    private fun post(ctx: Context, release: BridgeRelease): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val res = AppLocale.wrap(ctx)
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, res.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle(res.getString(R.string.update_notification_title, release.name))
            .setContentText(res.getString(R.string.update_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            nm.notify(NOTIFICATION, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

/** The daily run. Errors never fail the work: the next run simply tries again. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        if (UpdateCheck.enabled(applicationContext)) UpdateCheck.run(applicationContext, notify = true)
        return Result.success()
    }
}
