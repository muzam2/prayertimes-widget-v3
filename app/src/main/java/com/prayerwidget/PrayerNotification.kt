package com.prayerwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * PrayerNotification
 * ──────────────────
 * Builds and posts a single ongoing notification that lists today's prayer
 * times. Because it's marked ongoing and posted with VISIBILITY_PUBLIC, Samsung
 * One UI shows it on the lock screen automatically — no LockStar, no widget
 * host quirks, no third-party hosting at all. It's just an ordinary Android
 * notification.
 *
 * The body uses BigTextStyle so all six prayers fit when the notification is
 * expanded. The collapsed view still shows the next prayer's name and time.
 *
 * Refresh is triggered from:
 *   - PrayerTimesWidget.refreshWidget (every widget update tick)
 *   - MainActivity.saveAndRefresh
 *   - BootReceiver (so the notification reappears after a reboot)
 *   - The midnight alarm in PrayerTimesWidget
 */
object PrayerNotification {

    private const val CHANNEL_ID    = "prayer_times_persistent_v1"
    private const val CHANNEL_NAME  = "Prayer times (lock screen)"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS_KEY_ENABLED = "notification_enabled"

    /**
     * Whether the persistent notification is enabled by the user.
     * Defaults to true on first run.
     */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREFS_KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PREFS_KEY_ENABLED, enabled).apply()
        if (enabled) refresh(context) else cancel(context)
    }

    /**
     * Build and post the notification using whatever data is currently in
     * TimetableStorage. Safe to call any number of times; replaces the
     * existing notification by ID.
     */
    fun refresh(context: Context) {
        if (!isEnabled(context)) return

        ensureChannel(context)

        val prefs = context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
        val city  = prefs.getString(PrayerTimesWidget.KEY_CITY, "") ?: ""
        val times = PrayerTimesRepository.getTodaysTimes(context)

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
                displayCity + " — " + times.gregorianDate

            collapsed = displayCity + " — " + times.gregorianDate

            // Build the expanded body. Marker on the next prayer so it stands
            // out even though notifications don't support per-line colour.
            val rows = listOf(
                "Fajr"    to times.fajr,
                "Sunrise" to times.sunrise,
                "Zuhr"    to times.dhuhr,
                "Asr"     to times.asr,
                "Maghrib" to times.maghrib,
                "Isha"    to times.isha
            )
            val sb = StringBuilder()
            sb.append(displayCity).append(" — ").append(times.gregorianDate).append('\n')
            for ((label, time) in rows) {
                val marker = if (label == next) "  ►  " else "     "
                sb.append(marker).append(label.padEnd(8)).append(time).append('\n')
            }
            expanded = sb.toString().trimEnd()
        }

        // Tap action: launch MainActivity
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
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

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+ — silently skip.
            // MainActivity prompts for it on first open.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
    }
}
