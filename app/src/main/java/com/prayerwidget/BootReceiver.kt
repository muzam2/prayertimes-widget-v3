package com.prayerwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        try { PrayerTimesWidget.scheduleMidnightAlarm(context) }
        catch (t: Throwable) { Log.e(TAG, "scheduleMidnightAlarm failed", t) }

        try {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerTimesWidget::class.java))
            for (id in ids) {
                try { PrayerTimesWidget.refreshWidget(context, mgr, id) }
                catch (t: Throwable) { Log.e(TAG, "refreshWidget($id) failed", t) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getAppWidgetIds failed", t)
        }

        try { PrayerNotification.refresh(context) }
        catch (t: Throwable) { Log.e(TAG, "PrayerNotification.refresh failed", t) }
    }

    companion object { private const val TAG = "BootReceiver" }
}
