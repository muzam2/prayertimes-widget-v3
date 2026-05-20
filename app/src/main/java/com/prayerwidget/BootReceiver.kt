package com.prayerwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver
 * ────────────
 * AlarmManager alarms are cleared on reboot. This receiver re-arms the
 * midnight update alarm as soon as the device finishes booting.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PrayerTimesWidget.scheduleMidnightAlarm(context)
        }
    }
}
