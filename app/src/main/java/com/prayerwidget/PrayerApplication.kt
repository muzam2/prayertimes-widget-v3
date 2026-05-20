package com.prayerwidget

import android.app.Application

/**
 * PrayerApplication
 * ─────────────────
 * Application subclass — kept for future use.
 * (PDFBox was removed in v3.0; timetable is now a pre-built JSON file.)
 */
class PrayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Nothing to initialise — all processing happens on the desktop
        // via icc_pdf_to_json.py before the JSON is copied to the phone.
    }
}
