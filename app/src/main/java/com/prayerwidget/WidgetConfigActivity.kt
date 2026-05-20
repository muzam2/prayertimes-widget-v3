package com.prayerwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * WidgetConfigActivity
 * ─────────────────────
 * Shown when the user first adds the widget (or reconfigures it).
 * Two jobs:
 *  1. City name input  — used as the display label on the widget
 *  2. JSON import      — pick the annual timetable JSON produced by icc_pdf_to_json.py
 */
class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var etCity:       EditText
    private lateinit var tvStatus:     TextView
    private lateinit var btnImport:    Button
    private lateinit var btnAddWidget: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContentView(R.layout.activity_widget_config)

        etCity       = findViewById(R.id.et_city)
        tvStatus     = findViewById(R.id.tv_timetable_status)
        btnImport    = findViewById(R.id.btn_import_json)
        btnAddWidget = findViewById(R.id.btn_add_widget)

        // Pre-fill saved city name
        val prefs = getSharedPreferences(PrayerTimesWidget.PREFS, MODE_PRIVATE)
        etCity.setText(prefs.getString(PrayerTimesWidget.KEY_CITY, ""))

        refreshStatus()

        btnImport.setOnClickListener { launchFilePicker() }

        btnAddWidget.setOnClickListener {
            val city = etCity.text.toString().trim()
            if (city.isEmpty()) {
                Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!TimetableStorage.isLoaded(this)) {
                Toast.makeText(this, "Please import a timetable JSON first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString(PrayerTimesWidget.KEY_CITY, city).apply()

            val mgr = AppWidgetManager.getInstance(this)
            PrayerTimesWidget.refreshWidget(this, mgr, appWidgetId)

            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    // ── File picker ───────────────────────────────────────────────────────────

    private fun launchFilePicker() {
        // Use */* so the system file picker shows all files — some devices label
        // .json files with odd MIME types. The user just navigates to the file.
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

    // ── JSON import ───────────────────────────────────────────────────────────

    private fun importJson(uri: Uri) {
        btnImport.isEnabled    = false
        btnAddWidget.isEnabled = false
        tvStatus.text          = "Importing…"

        Thread {
            val count = try {
                val rawJson = contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""
                if (rawJson.isBlank()) -1
                else TimetableStorage.loadFromRawJson(this, rawJson)
            } catch (e: Exception) {
                -1
            }

            runOnUiThread {
                btnImport.isEnabled    = true
                btnAddWidget.isEnabled = true

                if (count <= 0) {
                    tvStatus.text = "⚠ Could not read file — make sure you selected the correct JSON"
                    Toast.makeText(this, "Import failed. Select the icc_2026_final.json file.",
                        Toast.LENGTH_LONG).show()
                } else {
                    refreshStatus()
                    Toast.makeText(this, "✓ $count days imported", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun refreshStatus() {
        tvStatus.text = TimetableStorage.summary(this)
    }

    companion object {
        private const val REQUEST_JSON = 1002
    }
}
