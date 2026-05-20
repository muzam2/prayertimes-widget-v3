#!/usr/bin/env python3
"""
icc_pdf_to_json.py
──────────────────
Converts the ICC (London Central Mosque) annual prayer calendar PDF into a
JSON timetable that can be imported into the Prayer Times Widget app.

The ICC calendar is fully rasterised (image-only pages) so text extraction
libraries return nothing. This script renders each page as a high-resolution
image and uses Tesseract OCR to read the prayer times.

Usage
─────
    python icc_pdf_to_json.py ICC_Calendar_2027.pdf
    python icc_pdf_to_json.py ICC_Calendar_2027.pdf --out icc_2027_final.json
    python icc_pdf_to_json.py ICC_Calendar_2027.pdf --tesseract "C:\\Program Files\\Tesseract-OCR\\tesseract.exe"

Output JSON format
──────────────────
{
  "2027-01-01": { "fajr":"06:26", "sunrise":"08:03", "zuhr":"12:09",
                  "asr":"13:46", "maghrib":"16:05", "isha":"17:42" },
  ...
}
All times are 24-hour HH:MM.  The app accepts "zuhr" and "dhuhr" interchangeably.

Requirements
────────────
    pip install pymupdf pytesseract pillow

Tesseract OCR must also be installed separately:
    Windows  → https://github.com/UB-Mannheim/tesseract/wiki
               Tick "Add to PATH" during install, or pass --tesseract
    macOS    → brew install tesseract
    Linux    → sudo apt install tesseract-ocr
"""

import argparse
import calendar
import json
import os
import re
import sys

# ── Third-party imports with helpful error messages ───────────────────────────

def _require(package, pip_name=None):
    try:
        return __import__(package)
    except ImportError:
        pip_name = pip_name or package
        sys.exit(f"\nMissing package: {package}\n"
                 f"Run:  pip install {pip_name}\n")

fitz       = _require("fitz", "pymupdf")
pytess     = _require("pytesseract")
Image      = _require("PIL.Image", "pillow")
ImageFilter = _require("PIL.ImageFilter", "pillow")

# Re-import properly after the check
import fitz                              # noqa: F811
import pytesseract
from PIL import Image, ImageFilter

# ── Constants ─────────────────────────────────────────────────────────────────

MONTH_ABBR = {
    "jan": 1, "feb": 2, "mar": 3, "apr": 4, "may": 5, "jun": 6,
    "jul": 7, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12,
}

PRAYER_KEYS = ["fajr", "sunrise", "zuhr", "asr", "maghrib", "isha"]

# Fuzzy patterns to match OCR-garbled prayer names
PRAYER_PATTERNS = [
    ("fajr",    re.compile(r"\b(?:fajr|faj[rl]?|fait|far)\b",        re.I)),
    ("sunrise", re.compile(r"\b(?:sunrise|sunris[ec]?)\b",            re.I)),
    ("zuhr",    re.compile(r"\b(?:zuhr|zubr|zehr|zuht|zulr)\b",       re.I)),
    ("asr",     re.compile(r"\b(?:asr|ast)\b",                        re.I)),
    ("maghrib", re.compile(r"\b(?:maghrib|maghr[ib]+)\b",              re.I)),
    ("isha",    re.compile(r"\b(?:isha|i[s§$]ha|icha)\b",             re.I)),
]

# Matches "Jan 1", "Feb 23", "Jan1" etc. — also "Jan 117" (OCR garble for "Jan 11")
DATE_TOKEN_RE = re.compile(
    r"\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\s*(\d{1,3})\b",
    re.I
)

# ── Helpers ───────────────────────────────────────────────────────────────────

def fix_day(n: int) -> int | None:
    """Map OCR-garbled day numbers back to 1–31.  E.g. 117 → 11."""
    if 1 <= n <= 31:
        return n
    s = str(n)
    if len(s) >= 2 and 1 <= int(s[:2]) <= 31:
        return int(s[:2])
    if len(s) >= 1 and 1 <= int(s[0]) <= 31:
        return int(s[0])
    return None


def extract_times(line: str) -> list[str]:
    """Return all HH:MM / H:MM tokens from a line, normalising dash→colon."""
    line = re.sub(r"(\d)[-–](\d{2})\b", r"\1:\2", line)
    return re.findall(r"\b\d{1,2}:\d{2}\b", line)   # duplicates allowed


def to_24h(raw_times: list[str]) -> list[str | None]:
    """
    Convert a list of H:MM strings (12-hour, no AM/PM) to HH:MM (24-hour).
    Works by ensuring each prayer time is strictly after the previous one;
    if it isn't, adds 12 hours (720 minutes).
    """
    result, prev = [], 0
    for t in raw_times:
        m = re.match(r"^(\d{1,2}):(\d{2})$", t.strip()) if t else None
        if not m:
            result.append(None)
            continue
        total = int(m.group(1)) * 60 + int(m.group(2))
        if total < prev:
            total += 720
        result.append(f"{total // 60:02d}:{total % 60:02d}")
        prev = total
    return result


def identify_prayer(line: str) -> str | None:
    for name, pat in PRAYER_PATTERNS:
        if pat.search(line):
            return name
    return None


def ocr_page(page, scale: float = 4.0) -> str:
    """Render a PDF page at `scale`× and return Tesseract OCR text."""
    pix = page.get_pixmap(matrix=fitz.Matrix(scale, scale))
    img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    img = img.filter(ImageFilter.SHARPEN)
    return pytesseract.image_to_string(img, config="--psm 6 --oem 3")


# ── Month parser ──────────────────────────────────────────────────────────────

def parse_month(text: str, month_num: int, year: int = 2026) -> dict:
    """
    Parse Tesseract OCR text for one monthly page.
    Returns a dict of 'YYYY-MM-DD' → {fajr, sunrise, zuhr, asr, maghrib, isha}.
    All times are 24-hour HH:MM strings.
    """
    results: dict = {}
    days_in_month = calendar.monthrange(year, month_num)[1]
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    current_days: list[int] = []
    current_prayers: dict[str, list[str]] = {}

    def flush():
        if not current_days:
            return
        n_times = max((len(v) for v in current_prayers.values()), default=0)
        n_days  = len(current_days)

        # Gap-fill: if prayer lines have more values than day-headers detected,
        # prepend the missing leading day numbers.
        if n_times > n_days:
            gap  = n_times - n_days
            lead = [current_days[0] - gap + j for j in range(gap)]
            lead = [d for d in lead if 1 <= d <= days_in_month]
            all_days = (lead + current_days) if len(lead) == gap else current_days
        else:
            all_days = current_days

        for i, day in enumerate(all_days):
            if not (1 <= day <= days_in_month):
                continue
            raw = [
                current_prayers.get(p, [])[i]
                if i < len(current_prayers.get(p, []))
                else None
                for p in PRAYER_KEYS
            ]
            if sum(1 for r in raw if r is None) > 1:
                continue   # too many missing values — skip this day
            conv = to_24h(raw)
            if any(c is None for c in conv):
                continue
            results[f"{year}-{month_num:02d}-{day:02d}"] = dict(
                zip(PRAYER_KEYS, conv)
            )

    for line in lines:
        date_matches = DATE_TOKEN_RE.findall(line)
        if date_matches:
            flush()
            current_days, current_prayers = [], {}
            for abbr, ds in date_matches:
                if MONTH_ABBR.get(abbr.lower()) == month_num:
                    d = fix_day(int(ds))
                    if d and d not in current_days:
                        current_days.append(d)
            current_days.sort()
            continue

        prayer = identify_prayer(line)
        if prayer and current_days:
            times = extract_times(line)
            if times:
                current_prayers[prayer] = times

    flush()
    return results


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(
        description="Convert ICC annual prayer calendar PDF → widget JSON"
    )
    ap.add_argument("pdf", help="Path to ICC_Calendar_YYYY.pdf")
    ap.add_argument("--out", default=None,
                    help="Output JSON path (default: icc_YYYY_final.json)")
    ap.add_argument("--year", type=int, default=None,
                    help="Calendar year (auto-detected from filename if omitted)")
    ap.add_argument("--tesseract", default=None,
                    help="Full path to tesseract executable "
                         r'(e.g. "C:\Program Files\Tesseract-OCR\tesseract.exe")')
    ap.add_argument("--scale", type=float, default=4.0,
                    help="Render scale factor (default 4.0; higher = slower but more accurate)")
    args = ap.parse_args()

    # Configure Tesseract path (needed on Windows if not in PATH)
    if args.tesseract:
        pytesseract.pytesseract.tesseract_cmd = args.tesseract

    # Verify Tesseract is accessible
    try:
        pytesseract.get_tesseract_version()
    except pytesseract.TesseractNotFoundError:
        sys.exit(
            "\nTesseract not found.\n\n"
            "Windows: Download from https://github.com/UB-Mannheim/tesseract/wiki\n"
            "         Tick 'Add to PATH' during install, then restart your terminal.\n"
            "         Or pass --tesseract \"C:\\Program Files\\Tesseract-OCR\\tesseract.exe\"\n\n"
            "macOS:   brew install tesseract\n"
            "Linux:   sudo apt install tesseract-ocr\n"
        )

    # Detect year from filename
    year = args.year
    if year is None:
        m = re.search(r"(20\d{2})", os.path.basename(args.pdf))
        year = int(m.group(1)) if m else 2026
        print(f"Detected year: {year}  (use --year YYYY to override)")

    out_path = args.out or f"icc_{year}_final.json"

    # Open PDF
    try:
        doc = fitz.open(args.pdf)
    except Exception as e:
        sys.exit(f"Could not open PDF: {e}")

    print(f"PDF pages: {doc.page_count}")
    print(f"Rendering at {args.scale}× — this takes about 30–60 s on a modern PC…\n")

    # ICC calendar layout: pages 0–1 = cover, 2–13 = Jan–Dec, 14–15 = back matter
    # (adjust page_start/page_end if a future ICC PDF has a different layout)
    page_start = 2
    page_end   = min(14, doc.page_count)   # exclusive

    if doc.page_count < 14:
        print(f"Warning: expected at least 14 pages, got {doc.page_count}.")
        print("Check that you have the full ICC annual calendar PDF.\n")
        page_end = doc.page_count

    all_entries: dict = {}
    months = list(range(page_start, page_end))

    for idx, page_idx in enumerate(months):
        month_num = page_idx - page_start + 1
        month_name = list(MONTH_ABBR.keys())[month_num - 1].capitalize()
        days_expected = calendar.monthrange(year, month_num)[1]

        print(f"  OCR page {page_idx + 1}/{doc.page_count}  ({month_name})...", end="", flush=True)
        text    = ocr_page(doc[page_idx], scale=args.scale)
        entries = parse_month(text, month_num, year=year)
        all_entries.update(entries)
        ok = "✓" if len(entries) == days_expected else f"⚠ {len(entries)}/{days_expected}"
        print(f"  {ok}")

    total = len(all_entries)
    expected = sum(calendar.monthrange(year, m)[1] for m in range(1, 13))
    print(f"\nTotal: {total}/{expected} days")

    if total < expected:
        import datetime
        all_dates = {
            (datetime.date(year, 1, 1) + datetime.timedelta(i)).strftime("%Y-%m-%d")
            for i in range(expected)
        }
        missing = sorted(all_dates - set(all_entries))
        print(f"\nMissing {len(missing)} dates: {missing}")
        print("\nThese usually affect dark-background (Friday/highlighted) cards.")
        print("You can add them manually by editing the JSON before importing.\n")

    # Save
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(all_entries, f, sort_keys=True, indent=2, ensure_ascii=False)

    print(f"\nSaved → {out_path}")
    print("Copy this file to your phone and import it in the Prayer Times Widget app.")


if __name__ == "__main__":
    main()
