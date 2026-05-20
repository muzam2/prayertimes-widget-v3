package com.prayerwidget

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * TimetableStorage
 * ─────────────────
 * Persists the annual timetable to a JSON file in internal storage.
 * Lookup is O(1) — just key on today's date.
 *
 * File location: <filesDir>/prayer_timetable.json
 *
 * Internal file schema:
 * {
 *   "meta": { "importedAt": "2026-05-13", "entryCount": 365 },
 *   "times": {
 *     "2026-01-01": { "fajr":"06:26", "sunrise":"08:03", "dhuhr":"12:09",
 *                     "asr":"13:46",  "maghrib":"16:05", "isha":"17:42",
 *                     "gregorianDate":"1 Jan 2026" },
 *     ...
 *   }
 * }
 *
 * Import (raw) schema — produced by the icc_pdf_to_json.py script:
 * {
 *   "2026-01-01": { "fajr":"06:26", "sunrise":"08:03", "zuhr":"12:09",
 *                   "asr":"13:46",  "maghrib":"16:05", "isha":"17:42" },
 *   ...
 * }
 * The "zuhr" key is mapped to "dhuhr" during import.
 */
object TimetableStorage {

    private const val TAG      = "TimetableStorage"
    private const val FILENAME = "prayer_timetable.json"

    // ─────────────────────────────────────────────────────────────────────────
    // Primary import — reads the flat JSON from icc_pdf_to_json.py
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse the raw JSON string produced by the Python extraction script,
     * convert to internal format, and persist to disk.
     *
     * Accepts both "zuhr" (ICC / icc_pdf_to_json.py output) and "dhuhr"
     * (legacy internal key) — whichever is present.
     *
     * @return number of entries imported, or -1 on error.
     */
    fun loadFromRawJson(context: Context, rawJson: String): Int {
        return try {
            val flat   = JSONObject(rawJson)
            val inSdf  = SimpleDateFormat("yyyy-MM-dd",  Locale.US)
            val outSdf = SimpleDateFormat("d MMM yyyy",  Locale.US)
            val entries = mutableMapOf<String, PrayerTimes>()

            flat.keys().forEach { dateKey ->
                try {
                    val obj     = flat.getJSONObject(dateKey)
                    val fajr    = obj.getString("fajr")
                    val sunrise = obj.getString("sunrise")
                    // Accept either "zuhr" (Python script) or "dhuhr" (legacy)
                    val dhuhr   = when {
                        obj.has("zuhr")  -> obj.getString("zuhr")
                        obj.has("dhuhr") -> obj.getString("dhuhr")
                        else             -> throw IllegalArgumentException("No Zuhr/Dhuhr key")
                    }
                    val asr     = obj.getString("asr")
                    val maghrib = obj.getString("maghrib")
                    val isha    = obj.getString("isha")

                    val gregDisplay = try { outSdf.format(inSdf.parse(dateKey)!!) }
                                      catch (e: Exception) { dateKey }

                    entries[dateKey] = PrayerTimes(
                        fajr          = fajr,
                        sunrise       = sunrise,
                        dhuhr         = dhuhr,
                        asr           = asr,
                        maghrib       = maghrib,
                        isha          = isha,
                        gregorianDate = gregDisplay,
                        hijriDate     = "",
                        city          = "",
                        nextPrayer    = nextPrayer(fajr, dhuhr, asr, maghrib, isha)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping entry $dateKey: ${e.message}")
                }
            }

            if (entries.isEmpty()) return -1
            save(context, entries)
            Log.i(TAG, "Imported ${entries.size} entries from raw JSON")
            entries.size
        } catch (e: Exception) {
            Log.e(TAG, "loadFromRawJson failed", e)
            -1
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal save / load
    // ─────────────────────────────────────────────────────────────────────────

    fun save(context: Context, entries: Map<String, PrayerTimes>) {
        try {
            val timesJson = JSONObject()
            for ((date, pt) in entries) {
                timesJson.put(date, JSONObject().apply {
                    put("fajr",          pt.fajr)
                    put("sunrise",       pt.sunrise)
                    put("dhuhr",         pt.dhuhr)
                    put("asr",           pt.asr)
                    put("maghrib",       pt.maghrib)
                    put("isha",          pt.isha)
                    put("gregorianDate", pt.gregorianDate)
                })
            }

            val root = JSONObject().apply {
                put("meta", JSONObject().apply {
                    put("importedAt",  today())
                    put("entryCount", entries.size)
                })
                put("times", timesJson)
            }

            file(context).writeText(root.toString())
            Log.i(TAG, "Saved ${entries.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "save() failed", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read
    // ─────────────────────────────────────────────────────────────────────────

    fun todaysTimes(context: Context, city: String): PrayerTimes? =
        getByDate(context, today(), city)

    fun getByDate(context: Context, dateKey: String, city: String): PrayerTimes? {
        val f = file(context)
        if (!f.exists()) { Log.d(TAG, "No timetable file"); return null }
        return try {
            val root  = JSONObject(f.readText())
            val times = root.getJSONObject("times")
            if (!times.has(dateKey)) {
                Log.w(TAG, "Date $dateKey not in timetable"); return null
            }
            val obj     = times.getJSONObject(dateKey)
            val fajr    = obj.getString("fajr")
            val sunrise = obj.getString("sunrise")
            val dhuhr   = obj.getString("dhuhr")
            val asr     = obj.getString("asr")
            val maghrib = obj.getString("maghrib")
            val isha    = obj.getString("isha")

            PrayerTimes(
                fajr          = fajr,
                sunrise       = sunrise,
                dhuhr         = dhuhr,
                asr           = asr,
                maghrib       = maghrib,
                isha          = isha,
                gregorianDate = obj.optString("gregorianDate", dateKey),
                hijriDate     = "",
                city          = city,
                nextPrayer    = nextPrayer(fajr, dhuhr, asr, maghrib, isha)
            )
        } catch (e: Exception) {
            Log.e(TAG, "getByDate($dateKey) failed", e); null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata
    // ─────────────────────────────────────────────────────────────────────────

    fun isLoaded(context: Context) = file(context).exists()

    fun summary(context: Context): String {
        val f = file(context)
        if (!f.exists()) return "No timetable loaded"
        return try {
            val meta  = JSONObject(f.readText()).getJSONObject("meta")
            val count = meta.optInt("entryCount", 0)
            val date  = meta.optString("importedAt", "?")
            "$count days loaded  •  imported $date"
        } catch (e: Exception) {
            "Timetable loaded (details unavailable)"
        }
    }

    fun clear(context: Context) { file(context).delete(); Log.i(TAG, "Timetable cleared") }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun file(context: Context) = File(context.filesDir, FILENAME)

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Determine which prayer comes next.
     * Returns one of: "Fajr", "Zuhr", "Asr", "Maghrib", "Isha", or "" (after Isha).
     * Note: we match "Zuhr" here to align with the widget label and highlightNext().
     */
    fun nextPrayer(fajr: String, dhuhr: String, asr: String,
                   maghrib: String, isha: String): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val now = fmt.parse(SimpleDateFormat("HH:mm", Locale.US).format(Date())) ?: return "Fajr"
        val prayers = listOf(
            "Fajr"    to fajr,
            "Zuhr"    to dhuhr,   // display name is "Zuhr" (ICC convention)
            "Asr"     to asr,
            "Maghrib" to maghrib,
            "Isha"    to isha
        )
        for ((name, time) in prayers) {
            val t = fmt.parse(time) ?: continue
            if (now.before(t)) return name
        }
        return ""
    }
}
