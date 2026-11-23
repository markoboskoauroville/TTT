#!/bin/bash
# Emulator smoke test (Marko, 23.11.2026: "install it in emulator ... and test"). Run inside android-emulator-runner.
# Installs the APK given as $1, makes TTT the keyboard, opens the new screens and the box, screenshots each into shots/,
# and fails when TTT crashed. No keys on the emulator: AI answers are not expected, only that every screen opens.
set -u
APK="$1"; PKG=com.mantraproductions.tttlight; mkdir -p shots
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }
adb install -r "$APK" || exit 1
adb logcat -c
adb shell ime list -a -s
IME=$(adb shell ime list -a -s | tr -d '\r' | grep "$PKG" | head -1)
echo "IME: $IME"
adb shell ime enable "$IME"; adb shell ime set "$IME"; sleep 2
echo "default IME now: $(adb shell settings get secure default_input_method)"
adb shell pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -n $PKG/dev.patrickgold.florisboard.app.FlorisAppActivity; shot 01_first_start 8
# First start shows the welcome steps: "Skip setup, go to Settings" (found by its text, tapped at its centre).
tap_text() {
  adb shell uiautomator dump /sdcard/t.xml >/dev/null 2>&1; adb pull /sdcard/t.xml t.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import re,sys,subprocess
x=open('t.xml',encoding='utf-8',errors='replace').read()
m=re.search(r'text="[^"]*'+re.escape(sys.argv[1])+r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x)
if m:
    a=list(map(int,m.groups())); subprocess.run(['adb','shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2)]); print('tapped',sys.argv[1])
else: print('not found:',sys.argv[1])
PY
}
tap_text "Skip setup"; shot 01_settings_home 5
adb shell input swipe 500 1600 500 700 300; shot 01b_settings_home_scrolled 2
tap_text "API"; shot 02a_api_by_tap 4; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/dictate/api" $PKG; shot 02_api 5
adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/dictate/auto" $PKG; shot 03_auto_correction_and_ai 5
adb shell input swipe 500 1500 500 600 300; shot 03b_auto_scrolled 2
adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/dictate/settings-sync" $PKG; shot 04_settings_sync 5
adb root; sleep 3
adb shell am start -n $PKG/dev.patrickgold.florisboard.dictate.MaStyleBoxActivity; shot 05_box 5
# The Reprompt tab (third of four across the width), then New message, then Original.
W=$(adb shell wm size | tr -d '\r' | sed 's/.*: //;s/x.*//'); H=$(adb shell wm size | tr -d '\r' | sed 's/.*x//')
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml shots/05_box_ui.xml >/dev/null 2>&1
adb shell input keyevent KEYCODE_BACK; sleep 1
# The keyboard itself, in a real text field (the system's search box in Settings).
adb shell am start -a android.settings.SETTINGS; sleep 4
adb shell am start -a android.intent.action.SENDTO -d "sms:5551234"; sleep 5
adb shell input tap $((W/2)) $((H*92/100)); shot 06_keyboard_in_messages 5
adb shell uiautomator dump /sdcard/kb.xml >/dev/null 2>&1; adb pull /sdcard/kb.xml shots/06_keyboard_ui.xml >/dev/null 2>&1
adb logcat -d > shots/logcat.txt
if grep -A12 "FATAL EXCEPTION" shots/logcat.txt | grep -q "$PKG\|patrickgold"; then
  echo "TTT CRASHED:"; grep -A25 "FATAL EXCEPTION" shots/logcat.txt | head -60; exit 1
fi
echo "emulator smoke: no TTT crash; screenshots in shots/"
