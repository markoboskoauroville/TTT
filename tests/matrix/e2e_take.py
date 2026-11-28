#!/usr/bin/env python3
"""One end-to-end take through the REAL recorder and keyboard (28.11.2026): the clip goes in through the Mac-microphone
link (fake_mac_mic.py stands in for Screen Copy), the H or E key is pressed with adb (an injected press, so TTT asks the
link), and the field's text is read back with uiautomator. Nothing is played aloud.

    python3 tests/matrix/e2e_take.py <serial> <clip> <key x> <key y> <out.png> [--wait 25]

Needs: a text field focused with TTT showing (e.g. a new Keep note), fake_mac_mic.py running on 18879, and
`adb reverse tcp:8879 tcp:18879`. Writes the clip path into /tmp-free scratch control file next to this script.
"""
import argparse
import os
import re
import subprocess
import time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
HERE = os.path.dirname(os.path.abspath(__file__))


def adb(s, *a, text=True):
    return subprocess.run([ADB, "-s", s, *a], capture_output=True, text=text).stdout


def field_text(s):
    adb(s, "shell", "uiautomator", "dump", "/sdcard/ui_e2e.xml")
    x = adb(s, "shell", "cat", "/sdcard/ui_e2e.xml")
    adb(s, "shell", "rm", "-f", "/sdcard/ui_e2e.xml")
    edits = re.findall(r'<node[^>]*class="android.widget.EditText"[^>]*>', x)
    out = []
    for e in edits:
        m = re.search(r' text="([^"]*)"', e)
        if m:
            out.append(m.group(1))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("serial")
    ap.add_argument("clip")
    ap.add_argument("x", type=int)
    ap.add_argument("y", type=int)
    ap.add_argument("png")
    ap.add_argument("--wait", type=float, default=25)
    ap.add_argument("--control", default=os.path.join(HERE, ".fake_mic_clip"))
    a = ap.parse_args()
    open(a.control, "w").write(os.path.join(HERE, "clips", a.clip + ".wav"))
    before = field_text(a.serial)
    t0 = time.time()
    adb(a.serial, "shell", "input", "tap", str(a.x), str(a.y))
    time.sleep(a.wait)
    png = adb(a.serial, "exec-out", "screencap", "-p", text=False)
    open(a.png, "wb").write(png)
    after = field_text(a.serial)
    print("before:", before)
    print("after: ", after)
    print(f"elapsed {time.time() - t0:.1f}s, screenshot {a.png}")


if __name__ == "__main__":
    main()
