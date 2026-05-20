package com.prayerwidget

import android.content.Context
import android.util.Log

/**
 * PrayerTimesRepository
 * ─────────────────────
 * All prayer times come from the locally stored timetable (TimetableStorage).
 * No network calls. No API key. Fully offline.
 *
 * Returns null if the timetable hasn't been imported yet, or if today's date
 * is not found in it (e.g. a new year and the JSON hasn't been updated yet).
 * The widget shows an "Import timetable" prompt in that case.
 */
object PrayerTimesRepository {

    private const val TAG = "PrayerTimesRepo"

    fun getTodaysTimes(context: Context): PrayerTimes? {
        val prefs = context.getSharedPreferences(PrayerTimesWidget.PREFS, Context.MODE_PRIVATE)
        val city  = prefs.getString(PrayerTimesWidget.KEY_CITY, "") ?: ""
        val times = TimetableStorage.todaysTimes(context, city)
        if (times == null) Log.w(TAG, "No times for today — timetable missing or out of date")
        return times
    }
}
