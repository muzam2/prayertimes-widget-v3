package com.prayerwidget

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity — shown when the user taps the app icon.
 * The real UI is the widget; this screen just shows setup instructions.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = if (TimetableStorage.isLoaded(this))
            TimetableStorage.summary(this)
        else
            "No timetable loaded yet — see Step 2 below."

        val tv = TextView(this).apply {
            text = """Prayer Times Widget  (v3.0 — JSON edition)

Timetable status:
$status

──────────────────────────────────

Step 1 — Generate the JSON timetable (once per year, on your PC):

  • Run icc_pdf_to_json.py on the ICC annual PDF calendar
    (download from iccuk.org each January)
  
  • The script produces  icc_YYYY_final.json
    A pre-built 2026 file is included in your project folder.

Step 2 — Import the JSON onto your phone:

  • Copy the .json file to your phone via USB or cloud storage
  
  • Long-press your home screen → Widgets → Prayer Times → Add
  
  • In the setup screen, enter your city name and tap
    "Import JSON file", navigate to the file and select it
  
  • Tap "Add Widget to Lock Screen"

The widget refreshes automatically at midnight each day.
Tap the ↻ button to force a refresh at any time.
"""
            setPadding(48, 64, 48, 64)
            textSize = 14f
            setTextColor(0xFFDDDDFF.toInt())
        }

        val root = android.widget.ScrollView(this).apply {
            setBackgroundColor(0xFF1A1A2E.toInt())
            addView(tv)
        }
        setContentView(root)
    }
}
