package com.prayerwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * PrayerNotification
 * ──────────────────
 * Builds and posts a single ongoing notification with today's prayer times.
 * Samsung One UI shows it on the lock screen automatically (VISIBILITY_PUBLIC
 * + ongoing + CATEGORY_STATUS), so we don't need LockStar or any widget host.
 *
 * EVERY public entry point swallows exceptions and logs them — a bug here
 * must never bring down the widget receiver that calls into us, otherwise
 * Android shows "couldn't add widget" when the user tries to drop the widget.
 */
object PrayerNotification {

    private const val TAG               = "PrayerNotification"
    private const val CHANNEL_ID        = "prayer_times_persistent_v1"
    private const val CHANNEL_NAME      = "Prayer times (lock screen)"
    private const val NOTIFICATION_ID   = 1001
    private const val PREFS_KEY_ENABLED = "notification_enabled"

    fun isEnabled(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREFS_KEY_ENABLED, true)
        } catch (t: Throwable) {
            Log.e(TAG, "isEnabled failed", t)
            false
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        try {
            context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFS_KEY_ENABLED, enabled).apply()
            if (enabled) refresh(context) else cancel(context)
        } catch (t: Throwable) {
            Log.e(TAG, "setEnabled failed", t)
        }
    }

    fun refresh(context: Context) {
        try {
            if (!isEnabled(context)) return
            ensureChannel(context)
            val notif = buildNotification(context) ?: return
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        } catch (t: Throwable) {
            // SecurityException (no POST_NOTIFICATIONS), RemoteException, or any
            // other Samsung quirk — log it and move on. We must never let this
            // propagate to the widget receiver.
            Log.e(TAG, "refresh failed", t)
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (t: Throwable) {
            Log.e(TAG, "cancel failed", t)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun buildNotification(context: Context): android.app.Notification? {
        val prefs = context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
        val city  = prefs.getString(PrayerTimesWidget.KEY_CITY, "") ?: ""
        val times = try {
            PrayerTimesRepository.getTodaysTimes(context)
        } catch (t: Throwable) {
            Log.e(TAG, "getTodaysTimes failed", t); null
        }

        val title: String
        val collapsed: String
        val expanded: String

        if (times == null) {
            title     = "Prayer Times"
            collapsed = "Open app to import timetable"
            expanded  = "No timetable loaded. Open Prayer Times and import the annual JSON to enable notifications."
        } else {
            val displayCity = times.city.ifEmpty { city }.ifEmpty { "Prayer Times" }
            val next        = times.nextPrayer
            val nextTime    = when (next) {
                "Fajr"    -> times.fajr
                "Zuhr"    -> times.dhuhr
                "Asr"     -> times.asr
                "Maghrib" -> times.maghrib
                "Isha"    -> times.isha
                else      -> ""
            }

            title = if (next.isNotEmpty())
                "Next: " + next + " at " + nextTime
            else
                displayCity + " - " + times.gregorianDate

            collapsed = displayCity + " - " + times.gregorianDate

            val rows = listOf(
                "Fajr"    to times.fajr,
                "Sunrise" to times.sunrise,
                "Zuhr"    to times.dhuhr,
                "Asr"     to times.asr,
                "Maghrib" to times.maghrib,
                "Isha"    to times.isha
            )
            val sb = StringBuilder()
            sb.append(displayCity).append(" - ").append(times.gregorianDate).append('\n')
            for ((label, time) in rows) {
                val marker = if (label == next) "  >  " else "     "
                sb.append(marker).append(label.padEnd(8)).append(time).append('\n')
            }
            expanded = sb.toString().trimEnd()
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(collapsed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPi)
            .setColor(0xFF4CAF9F.toInt())
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description       = "Persistent prayer-times display on the lock screen"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            mgr.createNotificationChannel(ch)
        } catch (t: Throwable) {
            Log.e(TAG, "ensureChannel failed", t)
        }
    }
}
