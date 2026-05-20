package com.prayerwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity
 * ────────────
 * Sole setup point for the widget. The widget no longer uses a per-instance
 * config activity (for Samsung LockStar / Good Lock compatibility), so all
 * setup happens here:
 *
 *   1. City name (display label on the widget)
 *   2. Import the annual timetable JSON
 *   3. Optionally force-refresh all widgets right now
 *
 * After importing the JSON, every widget instance — home screen, edge panel,
 * LockStar lock screen — picks it up automatically on the next refresh.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etCity:    EditText
    private lateinit var tvStatus:  TextView
    private lateinit var btnImport: Button
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStatus()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val gap = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF1A1A2E.toInt())
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Prayer Times Widget"
            setTextColor(Color.parseColor("#4CAF9F"))
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "ICC London / Islam21c 18° methodology"
            setTextColor(Color.parseColor("#AAAACC"))
            textSize = 12f
            setPadding(0, 0, 0, gap)
        })

        // Step 1 — city
        root.addView(stepLabel("Step 1 — City name (widget display label)"))
        etCity = EditText(this).apply {
            hint = "e.g. London"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666688"))
            setText(getSharedPreferences(PrayerTimesWidget.PREFS, MODE_PRIVATE)
                .getString(PrayerTimesWidget.KEY_CITY, ""))
        }
        root.addView(etCity, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = gap })

        // Step 2 — JSON import
        root.addView(stepLabel("Step 2 — Import annual timetable JSON"))
        root.addView(hint("Generate it from the ICC PDF using icc_pdf_to_json.py on your PC, then copy the file to your phone."))
        btnImport = Button(this).apply {
            text = "Import JSON file"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A4A5E"))
            setOnClickListener { launchFilePicker() }
        }
        root.addView(btnImport, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = gap / 2 })

        tvStatus = TextView(this).apply {
            text = "No timetable loaded"
            setTextColor(Color.parseColor("#FFCC88"))
            textSize = 12f
            setPadding(0, 0, 0, gap)
        }
        root.addView(tvStatus)

        // Step 3 — save & refresh
        root.addView(stepLabel("Step 3 — Save & refresh all widgets"))
        btnRefresh = Button(this).apply {
            text = "Save and refresh widgets"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF9F"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener { saveAndRefresh() }
        }
        root.addView(btnRefresh, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (52 * resources.displayMetrics.density).toInt()
        ).apply { bottomMargin = gap })

        // How to add the widget
        root.addView(stepLabel("How to add the widget"))
        root.addView(hint(
            "Home screen: long-press → Widgets → Prayer Times → add.

" +
            "Lock screen (Samsung): install Good Lock from Galaxy Store → LockStar module → " +
            "edit lock screen → add Widget → pick Prayer Times.

" +
            "Edge Panel: Settings → Display → Edge Panels → Apps & Widgets → " +
            "Edit → drag Prayer Times in."
        ))

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun stepLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#CCCCDD"))
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, (4 * resources.displayMetrics.density).toInt())
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#888899"))
        textSize = 11f
        setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
    }

    // ── File picker ───────────────────────────────────────────────────────────

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_JSON)
    }

    @Deprecated("Legacy onActivityResult for broad API compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_JSON && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            importJson(uri)
        }
    }

    private fun importJson(uri: Uri) {
        btnImport.isEnabled = false
        tvStatus.text = "Importing…"
        Thread {
            val count = try {
                val raw = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (raw.isBlank()) -1
                else TimetableStorage.loadFromRawJson(this, raw)
            } catch (e: Exception) { -1 }

            runOnUiThread {
                btnImport.isEnabled = true
                if (count <= 0) {
                    tvStatus.text = "⚠ Could not read file. Check that it is the JSON produced by icc_pdf_to_json.py."
                    Toast.makeText(this, "Import failed", Toast.LENGTH_LONG).show()
                } else {
                    refreshStatus()
                    refreshAllWidgets()
                    Toast.makeText(this, "✓ $count days imported", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── Save city + force refresh every widget instance ───────────────────────

    private fun saveAndRefresh() {
        val city = etCity.text.toString().trim()
        getSharedPreferences(PrayerTimesWidget.PREFS, MODE_PRIVATE)
            .edit().putString(PrayerTimesWidget.KEY_CITY, city).apply()
        refreshAllWidgets()
        Toast.makeText(this, "✓ Widgets refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAllWidgets() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, PrayerTimesWidget::class.java))
        for (id in ids) PrayerTimesWidget.refreshWidget(this, mgr, id)
    }

    private fun refreshStatus() {
        tvStatus.text = TimetableStorage.summary(this)
    }

    companion object {
        private const val REQUEST_JSON = 2001
    }
}
