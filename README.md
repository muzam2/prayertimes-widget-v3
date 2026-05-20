# Prayer Times Widget — v3.0 (JSON Edition)

Lock screen widget for Galaxy S23 Ultra (Android 16 / One UI 8) showing daily
prayer times from the **London Central Mosque (ICC UK)** timetable.

---

## What's in this folder

```
PrayerTimesWidget_v3/      ← Android Studio project (open this)
icc_pdf_to_json.py         ← Python script — convert ICC PDF → JSON each January
icc_2026_final.json        ← Ready-to-use 2026 timetable (365 days, pre-built)
README.md                  ← This file
```

---

## First-time setup

### Step 1 — Import the 2026 timetable onto your phone

1. Copy `icc_2026_final.json` to your phone (USB, Google Drive, WhatsApp, etc.)
2. Build and install the app from Android Studio
3. Long-press your lock screen or home screen → **Widgets** → **Prayer Times** → Add
4. In the setup screen:
   - Enter your city name (e.g. `London`)
   - Tap **Import JSON file** and select `icc_2026_final.json`
   - Tap **Add Widget to Lock Screen**

Done. The widget refreshes automatically at midnight and highlights the next prayer in teal.

---

## Each January — generate a new timetable

When ICC publishes the 2027 (or later) annual calendar:

1. Download the PDF from **iccuk.org**
2. Run the Python script on your PC (see instructions below)
3. Copy the output JSON to your phone
4. Open the app → tap the widget → **Reconfigure** → **Import JSON file**

---

## Running `icc_pdf_to_json.py` on Windows

### One-time prerequisites (≈ 5 minutes)

**1. Install Python 3**

Download from https://www.python.org/downloads/  
During install, tick **"Add Python to PATH"** ✓

**2. Install Tesseract OCR**

Download the Windows installer from:  
https://github.com/UB-Mannheim/tesseract/wiki

Recommended: `tesseract-ocr-w64-setup-5.x.x.exe`  
During install, tick **"Add to PATH"** ✓ (makes step 4 simpler)

**3. Install Python packages**

Open **Command Prompt** or **PowerShell** and run:

```
pip install pymupdf pytesseract pillow
```

### Running the script

```
python icc_pdf_to_json.py ICC_Calendar_2027.pdf
```

That's it. It will:
- OCR all 12 monthly pages (takes ~30–60 seconds)
- Output `icc_2027_final.json` in the same folder
- Print a summary showing how many days were found per month

**If Tesseract isn't on PATH** (e.g. you skipped the PATH option during install):

```
python icc_pdf_to_json.py ICC_Calendar_2027.pdf --tesseract "C:\Program Files\Tesseract-OCR\tesseract.exe"
```

**Custom output filename:**

```
python icc_pdf_to_json.py ICC_Calendar_2027.pdf --out my_timetable_2027.json
```

### Common issues on Windows

| Problem | Fix |
|---------|-----|
| `'python' is not recognised` | Re-install Python with "Add to PATH" ticked |
| `TesseractNotFoundError` | Pass `--tesseract "C:\Program Files\Tesseract-OCR\tesseract.exe"` |
| `ModuleNotFoundError: fitz` | Run `pip install pymupdf` |
| `ModuleNotFoundError: pytesseract` | Run `pip install pytesseract` |
| Some days missing from output | Normal for dark-background cards (~5%); add manually if needed |

---

## About the timetable

- Source: **London Central Mosque (ICC UK)** — iccuk.org
- The ICC annual PDF is fully rasterised (image pages) — no text layer
- Times use ICC's **18° methodology** (same as Islam21c)
- The PDF uses **12-hour format without AM/PM** — the script converts to 24h
- The ICC uses **"Zuhr"** (not "Dhuhr") — the widget label matches
- Dec 31 2026: the ICC PDF labels this card "Oct 31" (apparent typo) — the 2026 JSON corrects this

---

## App overview

| File | Purpose |
|------|---------|
| `PrayerTimes.kt` | Data class |
| `TimetableStorage.kt` | Reads/writes timetable; imports flat JSON |
| `PrayerTimesWidget.kt` | Widget renderer, midnight alarm, refresh |
| `WidgetConfigActivity.kt` | Setup screen with JSON file picker |
| `PrayerTimesRepository.kt` | Reads today's times from storage |
| `BootReceiver.kt` | Re-arms midnight alarm after reboot |
| `MainActivity.kt` | Launcher screen with setup instructions |

No internet permission. No API key. Fully private — all data stays on device.
