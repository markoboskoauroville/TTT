#!/bin/bash
# smoke.sh <build-number>: install TTT build on Pixel 2 fresh, type, and fail on any crash.
S=SCRATCH; cd $S; P2="adb -s 127.0.0.1:5556"; N=$1
(cd apk && gh release download build-$N -R markoboskoauroville/TTT -p '*.apk' --clobber) || exit 2
$P2 install -r apk/ttt-build-$N.apk | tail -1
$P2 shell pm clear com.mantraproductions.tttlight >/dev/null
$P2 shell ime enable com.mantraproductions.tttlight/dev.patrickgold.florisboard.FlorisImeService >/dev/null
$P2 shell ime set com.mantraproductions.tttlight/dev.patrickgold.florisboard.FlorisImeService >/dev/null
$P2 shell am start -a android.intent.action.VIEW -n org.chromium.webview_shell/.WebViewBrowserActivity -d "http://172.17.0.1:8765/pad.html?s$N" >/dev/null
sleep 6; $P2 shell input tap 380 190; sleep 2; $P2 shell input keyevent 66; sleep 8   # reload: a fresh page sometimes takes no taps
$P2 shell input tap 500 310; sleep 2; $P2 shell input tap 540 700; sleep 5; $P2 shell input tap 162 2217; sleep 2; $P2 logcat -c
python3 -I kb.py ttt "hello how a" smoke1 >/dev/null; python3 -I ocr.py ttt shots/smoke1.png
python3 -I kb.py ttt "call 0915 " smoke2 >/dev/null
if $P2 logcat -d | grep -q "FATAL EXCEPTION"; then echo "SMOKE FAIL: crash"; $P2 logcat -d | grep -A6 "FATAL EXCEPTION" | head -8; exit 1; fi
python3 -I ocr.py ttt shots/smoke1.png | grep -q "'S'" && { echo "SMOKE FAIL: nothing typed (keyboard not up)"; exit 3; }
echo "SMOKE OK"
