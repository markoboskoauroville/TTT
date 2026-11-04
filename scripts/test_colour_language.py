#!/usr/bin/env python3
"""
Test 1 for the feature-row colour language (DEVELOPMENT.md §239).

    python3 scripts/test_colour_language.py

Marko, 4.11.2026: record keys dark red, light red while recording; keys that show other rows grey,
green when their row is showing; F2 had no outline.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


row = (UI / "MaFeatureRow.kt").read_text()
meter = (UI / "MaRecordMeter.kt").read_text()

check("light red exists", "val MaRecordOutlineLive = Color(0xFFE8776C)" in meter)
check("the outline is the dark record red", "val MaRecordOutline = MaRecordRed" in meter and "val MaRecordRed = Color(0xFF9B3B33)" in meter)
check("E, H, E|H: dark red, light red while recording", ") MaRecordOutlineLive else MaRecordOutline," in row)
check("Record: dark red, light red while recording", "ring = if (recording) MaRecordOutlineLive else MaRecordOutline," in row)
check("no record key turns green any more", "onGreen else MaRecordOutline" not in row)
for what, expr in [("F1-F6", "ring = if (on) onGreen else MaSwitcherRingOff"),
                   ("number row (#)", "ring = if (zone1) onGreen else MaSwitcherRingOff"),
                   ("the keys", "ring = if (zone2) onGreen else MaSwitcherRingOff"),
                   ("zone 3", "ring = if (zone3) onGreen else MaSwitcherRingOff"),
                   ("reader row", "ring = if (readerShown) onGreen else MaSwitcherRingOff"),
                   ("bucket row", "ring = if (bucketShown) onGreen else MaSwitcherRingOff"),
                   ("volume keys switch", "ring = if (volumeLive) onGreen else MaSwitcherRingOff")]:
    check(f"{what}: grey off, green on", expr in row)
check("no row or switch key without an outline when off",
      re.search(r"ring = if \((on|zone\d|readerShown|bucketShown|volumeLive)\) onGreen else null", row) is None)

print(f"colour language, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
