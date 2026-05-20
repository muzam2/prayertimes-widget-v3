package com.prayerwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * MainActivity - sole setup point for the widget + notification.
 * Wrapped in try/catch so a crash here cannot kill the launcher icon.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etCity:        EditText
    private lateinit var tvStatus:      TextView
    private lateinit var btnImport:     Button
    private lateinit var swNotif:       Switch
    private lateinit var tvNotifStatus: TextView
    private lateinit var btnRefresh:    Button

    private val openJson = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importJson(it) } }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        try {
            if (granted) {
                PrayerNotification.setEnabled(this, true)
            } else {
                swNotif.isChecked = false
                PrayerNotification.setEnabled(this, false)
                Toast.makeText(
                    this,
                    "Notifications blocked. Enable in Settings to show prayer times on the lock screen.",
                    Toast.LENGTH_LONG
                ).show()
            }
            updateNotifStatus()
        } catch (t: Throwable) {
            Log.e(TAG, "permission callback failed", t)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(buildUi())
            refreshStatus()
            updateNotifStatus()
        } catch (t: Throwable) {
            Log.e(TAG, "onCreate failed", t)
            // Show a fallback text view rather than crashing the activity
            val tv = TextView(this).apply {
                text = "Prayer Times - setup screen failed to load.\n\n" + (t.message ?: "")
                setPadding(48, 48, 48, 48)
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF1A1A2E.toInt())
            }
            setContentView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        try { updateNotifStatus() } catch (t: Throwable) { Log.e(TAG, "onResume failed", t) }
    }

    // ------- UI -----------------------------------------------------------

    private fun buildUi(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val gap = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF1A1A2E.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Prayer Times"
            setTextColor(Color.parseColor("#4CAF9F"))
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "ICC London / Islam21c 18 degree methodology"
            setTextColor(Color.parseColor("#AAAACC"))
            textSize = 12f
            setPadding(0, 0, 0, gap)
        })

        // Step 1: city
        root.addView(stepLabel("Step 1: City name (display label)"))
        etCity = EditText(this).apply {
            hint = "e.g. London"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666688"))
            setText(getSharedPreferences(PrayerTimesWidget.PREFS, MODE_PRIVATE)
                .getString(PrayerTimesWidget.KEY_CITY, ""))
        }
        root.addView(etCity, lp().apply { bottomMargin = gap })

        // Step 2: JSON import
        root.addView(stepLabel("Step 2: Import annual timetable JSON"))
        root.addView(hint("Generate from the ICC PDF using icc_pdf_to_json.py on your PC, then copy the file to your phone."))
        btnImport = Button(this).apply {
            text = "Import JSON file"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A4A5E"))
            setOnClickListener {
                try { openJson.launch(arrayOf("application/json", "*/*")) }
                catch (t: Throwable) {
                    Log.e(TAG, "openJson.launch failed", t)
                    Toast.makeText(this@MainActivity, "Could not open file picker.", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(btnImport, lp().apply { bottomMargin = gap / 2 })

        tvStatus = TextView(this).apply {
            text = "No timetable loaded"
            setTextColor(Color.parseColor("#FFCC88"))
            textSize = 12f
            setPadding(0, 0, 0, gap)
        }
        root.addView(tvStatus)

        // Step 3: notification toggle
        root.addView(stepLabel("Step 3: Show on lock screen"))
        root.addView(hint("Posts a persistent notification with all prayer times. This is the most reliable lock-screen surface - widgets via LockStar are flaky."))

        val notifRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        swNotif = Switch(this).apply {
            text = "Lock-screen notification"
            setTextColor(Color.WHITE)
            isChecked = PrayerNotification.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                try {
                    if (isChecked) requestNotificationPermissionIfNeeded()
                    else {
                        PrayerNotification.setEnabled(this@MainActivity, false)
                        updateNotifStatus()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "swNotif listener failed", t)
                }
            }
        }
        notifRow.addView(swNotif, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(notifRow)

        tvNotifStatus = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#888899"))
            textSize = 11f
            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, gap)
        }
        root.addView(tvNotifStatus)

        // Step 4: save + refresh everything
        root.addView(stepLabel("Step 4: Save and refresh"))
        btnRefresh = Button(this).apply {
            text = "Save and refresh"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF9F"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener {
                try { saveAndRefresh() }
                catch (t: Throwable) { Log.e(TAG, "saveAndRefresh failed", t) }
            }
        }
        root.addView(btnRefresh, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (52 * resources.displayMetrics.density).toInt()
        ).apply { bottomMargin = gap })

        root.addView(stepLabel("Where the widget appears"))
        root.addView(hint("Home screen: long-press > Widgets > Prayer Times > add. Lock screen: enable the notification above. LockStar widget hosting is unreliable for third-party widgets."))

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

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

    // ------- Notification permission --------------------------------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PrayerNotification.setEnabled(this, true)
            updateNotifStatus()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            PrayerNotification.setEnabled(this, true)
            updateNotifStatus()
        } else {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateNotifStatus() {
        val enabled = PrayerNotification.isEnabled(this)
        val permGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        tvNotifStatus.text = when {
            !enabled                 -> "Off"
            enabled && !permGranted  -> "On (but system permission denied - enable in Settings)"
            else                     -> "On - check your lock screen"
        }
        if (enabled && !permGranted) swNotif.isChecked = false
    }

    // ------- File import --------------------------------------------------

    private fun importJson(uri: Uri) {
        btnImport.isEnabled = false
        tvStatus.text = "Importing..."
        Thread {
            val count = try {
                val raw = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (raw.isBlank()) -1
                else TimetableStorage.loadFromRawJson(this, raw)
            } catch (e: Exception) { Log.e(TAG, "importJson failed", e); -1 }

            runOnUiThread {
                try {
                    btnImport.isEnabled = true
                    if (count <= 0) {
                        tvStatus.text = "Could not read file. Check that it is the JSON produced by icc_pdf_to_json.py."
                        Toast.makeText(this, "Import failed", Toast.LENGTH_LONG).show()
                    } else {
                        refreshStatus()
                        refreshAllWidgets()
                        PrayerNotification.refresh(this)
                        Toast.makeText(this, count.toString() + " days imported", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "import UI update failed", t)
                }
            }
        }.start()
    }

    // ------- Save + refresh -----------------------------------------------

    private fun saveAndRefresh() {
        val city = etCity.text.toString().trim()
        getSharedPreferences(PrayerTimesWidget.PREFS, MODE_PRIVATE)
            .edit().putString(PrayerTimesWidget.KEY_CITY, city).apply()
        refreshAllWidgets()
        try { PrayerNotification.refresh(this) }
        catch (t: Throwable) { Log.e(TAG, "saveAndRefresh.refresh failed", t) }
        Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAllWidgets() {
        try {
            val mgr = AppWidgetManager.getInstance(this)
            val ids = mgr.getAppWidgetIds(ComponentName(this, PrayerTimesWidget::class.java))
            for (id in ids) {
                try { PrayerTimesWidget.refreshWidget(this, mgr, id) }
                catch (t: Throwable) { Log.e(TAG, "refreshWidget($id) failed", t) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "refreshAllWidgets failed", t)
        }
    }

    private fun refreshStatus() {
        tvStatus.text = TimetableStorage.summary(this)
    }

    companion object { private const val TAG = "MainActivity" }
}
