package com.prayerwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * MainActivity - sole setup point for the widget + notification.
 * The widget no longer uses a per-instance config activity (for Samsung
 * LockStar / Good Lock compatibility). All setup happens here:
 *   1. City name (display label)
 *   2. Import the annual timetable JSON
 *   3. Toggle the lock-screen notification on/off
 *   4. Save + force-refresh all widgets and the notification
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStatus()
        updateNotifStatus()
    }

    override fun onResume() {
        super.onResume()
        // User may have changed system notification settings while away
        updateNotifStatus()
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
            setOnClickListener { openJson.launch(arrayOf("application/json", "*/*")) }
        }
        root.addView(btnImport, lp().apply { bottomMargin = gap / 2 })

        tvStatus = TextView(this).apply {
            text = "No timetable loaded"
            setTextColor(Color.parseColor("#FFCC88"))
            textSize = 12f
            setPadding(0, 0, 0, gap)
        }
        root.addView(tvStatus)

        // Step 3: notification toggle (the real lock-screen surface)
        root.addView(stepLabel("Step 3: Show on lock screen"))
        root.addView(hint("Posts a persistent notification with all prayer times. Visible on the lock screen by default - this is the most reliable way to show prayer times when the phone is locked."))

        val notifRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        swNotif = Switch(this).apply {
            text = "Lock-screen notification"
            setTextColor(Color.WHITE)
            isChecked = PrayerNotification.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) requestNotificationPermissionIfNeeded()
                else {
                    PrayerNotification.setEnabled(this@MainActivity, false)
                    updateNotifStatus()
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
            setOnClickListener { saveAndRefresh() }
        }
        root.addView(btnRefresh, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (52 * resources.displayMetrics.density).toInt()
        ).apply { bottomMargin = gap })

        // Where to find the widget
        root.addView(stepLabel("Where the widget appears"))
        root.addView(hint("Home screen: long-press > Widgets > Prayer Times > add. Lock screen: enable the notification above - it shows automatically. Good Lock / LockStar widget hosting is unreliable for third-party widgets, so the notification is the recommended path."))

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
            // Pre-Android 13: permission is granted at install time
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
        // Keep switch state honest if user revoked permission in Settings
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
            } catch (e: Exception) { -1 }

            runOnUiThread {
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
            }
        }.start()
    }

    // ------- Save + refresh -----------------------------------------------

    private fun saveAndRefresh() {
        val city = etCity.text.toString().trim()
        getSharedPreferences(PrayerTimesWidget.PREFS, MODE_PRIVATE)
            .edit().putString(PrayerTimesWidget.KEY_CITY, city).apply()
        refreshAllWidgets()
        PrayerNotification.refresh(this)
        Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAllWidgets() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, PrayerTimesWidget::class.java))
        for (id in ids) PrayerTimesWidget.refreshWidget(this, mgr, id)
    }

    private fun refreshStatus() {
        tvStatus.text = TimetableStorage.summary(this)
    }
}
