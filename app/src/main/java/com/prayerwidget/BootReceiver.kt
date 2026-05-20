package com.prayerwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * BootReceiver
 * ────────────
 * AlarmManager alarms and the lock-screen notification are cleared on reboot.
 * This receiver re-arms everything as soon as the device finishes booting.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PrayerTimesWidget.scheduleMidnightAlarm(context)

            // Re-render any home-screen widget instances
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerTimesWidget::class.java))
            for (id in ids) PrayerTimesWidget.refreshWidget(context, mgr, id)

            // Repost the lock-screen notification
            PrayerNotification.refresh(context)
        }
    }
}
