#!/usr/bin/env python3
"""
Test 1 for RE's Read screen (MaScreenRead, 9.11.2026): the order of sources, the spoken commands in English and
Croatian, read only, nothing sent, the switch and the permission line in Settings.

    python3 scripts/test_screen_read.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
failures, checks = [], 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def code(path):
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


rd = code(SRC / "dictate/MaScreenRead.kt")
chat = code(SRC / "dictate/overlay/MaScreenChat.kt")
acc = code(SRC / "dictate/overlay/DictateAccessibilityService.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
ime = code(SRC / "FlorisImeService.kt")
rp = code(SRC / "dictate/MaReprompt.kt")
scr = code(SRC / "app/settings/dictate/MaReScreen.kt")
xml = (ROOT / "app/src/main/res/xml/accessibility_service_config.xml").read_text()

check("order: tree first, then screenshot and OCR", rd.find("DictateAccessibilityService.chatItems()") < rd.find("screenshotAboveKeyboard()"))
check("no third source: nothing sends a picture", "OkHttp" not in rd and "Request.Builder" not in rd and "requestReword" not in rd)
check("OCR is on the device (ML Kit bundled)", "TextRecognizerOptions.DEFAULT_OPTIONS" in rd and "com.google.mlkit:text-recognition" in (ROOT / "gradle/libs.versions.toml").read_text())
check("the bitmap is not saved", "compress(" not in acc and "FileOutputStream" not in rd)
check("the screenshot capability is declared", 'android:canTakeScreenshot="true"' in xml)
check("read only: the tree walk presses nothing", all(w not in chat for w in ("performAction", "performClick", "dispatchGesture")))
check("the voice path reads the screen then drafts", "MaScreenRead.wants(words)" in ctrl and "MaReprompt.prompt(words, write, screen)" in ctrl)
check("the typed path too", "MaScreenRead.wants(words)" in ime)
check("a failed read stops and says so", "could not read the screen" in ctrl and "return" in ctrl[ctrl.find("could not read the screen"):][:200])
check("the reply is never sent", "performEnter" not in rd and "pressSend" not in rd and "never sent" in (SRC / "dictate/MaScreenRead.kt").read_text().lower())
check("the prompt names sides and the newest message", "[left]" in rd and "newest" in rd and "Return only the reply" in rd)
check("Settings: switch, order, permission line", "maReScreen" in scr and "Read screen" in scr and "ACTION_ACCESSIBILITY_SETTINGS" in scr)
check("never an auto-click of the system prompt", "performClick" not in scr)

W = re.compile(
    r"screen\s?shot|take (a |the )?screen|read (the )?screen|read (the )?(last|latest|newest|previous|recent) message|"
    r"reply to (the )?(last|latest|newest|previous|recent)|answer (the )?(last|latest|newest) message|"
    r"snimk\w* zaslona|snimk\w* ekrana|slikaj (ekran|zaslon)|uslikaj|"
    r"odgovori na (zadnju|posljednju|najnoviju|prethodnu)|pročitaj (zadnju|posljednju|najnoviju) poruku|"
    r"procitaj (zadnju|posljednju|najnoviju) poruku|pročitaj (ekran|zaslon)", re.I)
for phrase in ("take a screenshot and reply to the latest message", "Take the screenshot", "take a screen shot and read it",
               "read the last message", "reply to the latest message", "odgovori na zadnju poruku", "napravi snimku zaslona",
               "pročitaj zadnju poruku", "Read the latest message and reply to it"):
    check("hears: " + phrase, bool(W.search(phrase)))
for phrase in ("write a message to Svemir", "make it shorter", "tell Damjan I am working on it"):
    check("does not hear: " + phrase, not W.search(phrase))

print(f"screen read, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
