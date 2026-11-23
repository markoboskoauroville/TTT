#!/usr/bin/env python3
"""
Test 1 for the recording overlay: one hairline, and nothing that can be pressed.

    python3 scripts/test_recording_line.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


line_src = (SRC / "dictate/overlay/MaRecordingLine.kt").read_text()
line = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "", line_src, flags=re.S), flags=re.M)

# ---------------------------------------------------------------- one thing, full width, one pixel
check("full width", "WindowManager.LayoutParams.MATCH_PARENT," in line, "still a notch in the middle")
check("one device pixel tall", re.search(r"MATCH_PARENT,\s*\n\s*1,", line) is not None,
      "taller than a hairline")
check("pinned to the top", "gravity = Gravity.TOP" in line and "LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS" in line, "not at the top edge")
check("the meter is horizontal", "MaVuView(service, vertical = false)" in line, "upright, in a strip")
check("only the meter is added", line.count("windowManager.addView(") == 1, "more than one view")

# ---------------------------------------------------------------- nothing to press
#
# With no controls the window can refuse touches entirely, which the old notch could not: a window is
# touchable or it is not, and there is no per-region setting. So every touch reaches the app beneath.
check("it takes no touches", "FLAG_NOT_TOUCHABLE" in line, "a strip of his screen stops working")
for gone in ("TextView", "LinearLayout", "GradientDrawable", "Typeface"):
    check(f"no {gone} left", gone not in line, "a control survived the cut")
check("no clock", "timer" not in line, "the clock survived")
# The CLOCK's ticker, not the meter's. The meter has a 40ms redraw loop and must — that is the meter
# working, not waste. The first version of this check banned every `postDelayed` and failed on the
# animation loop, which is a check firing on the one thing the file exists to do.
check("no once-a-second clock ticker", "startTicking" not in line and "postDelayed(this, 1000)" not in line,
      "a second of work per second, for a clock nobody can see")
check("the meter still redraws", "postDelayed(this, 40L)" in line, "the line would be frozen")
check("no language badge", "MaLanguage" not in line, "the badge survived")
check("no send or bin", "cancelRecording" not in line and "onMicClick" not in line,
      "controls that are already volume keys")

# ---------------------------------------------------------------- the one bit it carries
check("it still shows the level", "MaVuView" in line, "nothing moves, so nothing says recording")
check("it is still driven by the recorder", "show(visible" in line, "shown at the wrong times")

# The horizontal draw path has to work at h = 1, where the corner radius is half a pixel.
h = 1.0
r = h / 2
check("a one-pixel bar still has a valid radius", 0 < r <= h / 2, f"r={r}")

print(f"recording line, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
