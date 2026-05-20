package com.prayerwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import java.util.*

class PrayerTimesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Render the widget(s) first - this is the contract the launcher needs satisfied.
        // Everything else is best-effort; failures must never propagate.
        for (id in ids) {
            try { refreshWidget(context, mgr, id) }
            catch (t: Throwable) { Log.e(TAG, "refreshWidget($id) failed", t) }
        }
        // Side effects come after, each isolated:
        try { scheduleMidnightAlarm(context) }
        catch (t: Throwable) { Log.e(TAG, "scheduleMidnightAlarm failed", t) }
        try { PrayerNotification.refresh(context) }
        catch (t: Throwable) { Log.e(TAG, "PrayerNotification.refresh failed", t) }
    }

    override fun onEnabled(context: Context) {
        try { scheduleMidnightAlarm(context) }
        catch (t: Throwable) { Log.e(TAG, "onEnabled.scheduleMidnightAlarm failed", t) }
        try { PrayerNotification.refresh(context) }
        catch (t: Throwable) { Log.e(TAG, "onEnabled.refresh failed", t) }
    }

    override fun onDisabled(context: Context) {
        try { cancelMidnightAlarm(context) }
        catch (t: Throwable) { Log.e(TAG, "cancelMidnightAlarm failed", t) }
        // Keep notification alive even if all widgets are removed - separate feature.
    }

    override fun onReceive(context: Context, intent: Intent) {
        try { super.onReceive(context, intent) }
        catch (t: Throwable) { Log.e(TAG, "super.onReceive failed", t) }

        try {
            when (intent.action) {
                ACTION_REFRESH, ACTION_MIDNIGHT -> {
                    val mgr = AppWidgetManager.getInstance(context)
                    val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerTimesWidget::class.java))
                    for (id in ids) {
                        try { refreshWidget(context, mgr, id) }
                        catch (t: Throwable) { Log.e(TAG, "onReceive refresh($id) failed", t) }
                    }
                    try { PrayerNotification.refresh(context) }
                    catch (t: Throwable) { Log.e(TAG, "onReceive notif failed", t) }
                    if (intent.action == ACTION_MIDNIGHT) {
                        try { scheduleMidnightAlarm(context) }
                        catch (t: Throwable) { Log.e(TAG, "onReceive alarm failed", t) }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive top-level failed", t)
        }
    }

    companion object {
        private const val TAG     = "PrayerTimesWidget"
        const val ACTION_REFRESH  = "com.prayerwidget.ACTION_REFRESH"
        const val ACTION_MIDNIGHT = "com.prayerwidget.ACTION_MIDNIGHT"
        const val PREFS           = "prayer_prefs"
        const val KEY_CITY        = "city"
        const val KEY_COUNTRY     = "country"   // unused; kept for prefs compatibility

        private val COLOR_NEXT   = Color.parseColor("#4CAF9F")
        private val COLOR_NORMAL = Color.WHITE
        private val COLOR_LABEL  = Color.parseColor("#AACCCC")

        fun refreshWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val city  = prefs.getString(KEY_CITY, "") ?: ""
            val times = try { PrayerTimesRepository.getTodaysTimes(context) }
                        catch (t: Throwable) { Log.e(TAG, "getTodaysTimes failed", t); null }
            mgr.updateAppWidget(widgetId, buildViews(context, times, city))
        }

        fun buildViews(context: Context, times: PrayerTimes?, city: String): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.prayer_widget)

            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, PrayerTimesWidget::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.btn_refresh, pi)

            if (times == null) {
                v.setTextViewText(R.id.tv_date,  "No timetable loaded")
                v.setTextViewText(R.id.tv_hijri, "Open app to import JSON")
                v.setTextViewText(R.id.tv_city,  city.ifEmpty { "-" })
                v.setTextViewText(R.id.tv_status, "")
                for (id in listOf(
                    R.id.tv_fajr_time, R.id.tv_sunrise_time, R.id.tv_zuhr_time,
                    R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time))
                    v.setTextViewText(id, "--:--")
                return v
            }

            v.setTextViewText(R.id.tv_status,       "")
            v.setTextViewText(R.id.tv_date,         times.gregorianDate)
            v.setTextViewText(R.id.tv_hijri,        times.hijriDate)
            v.setTextViewText(R.id.tv_city,         times.city.ifEmpty { city })
            v.setTextViewText(R.id.tv_fajr_time,    times.fajr)
            v.setTextViewText(R.id.tv_sunrise_time, times.sunrise)
            v.setTextViewText(R.id.tv_zuhr_time,    times.dhuhr)
            v.setTextViewText(R.id.tv_asr_time,     times.asr)
            v.setTextViewText(R.id.tv_maghrib_time, times.maghrib)
            v.setTextViewText(R.id.tv_isha_time,    times.isha)
            highlightNext(v, times.nextPrayer)
            return v
        }

        private fun highlightNext(v: RemoteViews, next: String) {
            data class Row(val timeId: Int, val labelId: Int, val name: String)
            listOf(
                Row(R.id.tv_fajr_time,    R.id.tv_fajr_label,    "Fajr"),
                Row(R.id.tv_sunrise_time, R.id.tv_sunrise_label, "Sunrise"),
                Row(R.id.tv_zuhr_time,    R.id.tv_zuhr_label,    "Zuhr"),
                Row(R.id.tv_asr_time,     R.id.tv_asr_label,     "Asr"),
                Row(R.id.tv_maghrib_time, R.id.tv_maghrib_label, "Maghrib"),
                Row(R.id.tv_isha_time,    R.id.tv_isha_label,    "Isha")
            ).forEach { row ->
                val isNext = row.name == next
                v.setTextColor(row.timeId,  if (isNext) COLOR_NEXT  else COLOR_NORMAL)
                v.setTextColor(row.labelId, if (isNext) COLOR_NEXT  else COLOR_LABEL)
            }
        }

        private fun midnightPi(context: Context) = PendingIntent.getBroadcast(
            context, 42,
            Intent(context, PrayerTimesWidget::class.java).apply { action = ACTION_MIDNIGHT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun scheduleMidnightAlarm(context: Context) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }
            // setExactAndAllowWhileIdle requires SCHEDULE_EXACT_ALARM on Android 12+.
            // Fall back to inexact set() if the exact one fails (no permission etc.)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = midnightPi(context)
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } catch (t: Throwable) {
                Log.w(TAG, "setExactAndAllowWhileIdle failed, falling back to set()", t)
                try { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
                catch (t2: Throwable) { Log.e(TAG, "set() also failed", t2) }
            }
        }

        private fun cancelMidnightAlarm(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(midnightPi(context))
        }
    }
}
