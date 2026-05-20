package com.prayerwidget

/**
 * PrayerTimes
 * ───────────
 * Holds the six daily prayer times plus display metadata for one calendar day.
 *
 * The `dhuhr` field is stored using the traditional transliteration internally,
 * but the widget displays it as "Zuhr" (the ICC / London Central Mosque convention).
 *
 * `nextPrayer` is one of "Fajr", "Zuhr", "Asr", "Maghrib", "Isha", or "" (after Isha).
 */
data class PrayerTimes(
    val fajr:          String,
    val sunrise:       String,
    val dhuhr:         String,   // displayed on widget as "Zuhr"
    val asr:           String,
    val maghrib:       String,
    val isha:          String,
    val gregorianDate: String,   // e.g. "13 May 2026"
    val hijriDate:     String,   // e.g. "" — not in ICC timetable
    val city:          String,
    val nextPrayer:    String
)
