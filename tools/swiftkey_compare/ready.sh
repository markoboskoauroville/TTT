#!/bin/bash
# ready.sh <build> [fresh]: TTT build on Pixel 2, page reloaded, text box focused, letters on; verified by typing.
S=SCRATCH; cd $S; P2="adb -s 127.0.0.1:5556"; N=$1
(cd apk && gh release download build-$N -R markoboskoauroville/TTT -p '*.apk' --clobber) || exit 2
$P2 install -r apk/ttt-build-$N.apk | tail -1
[ "$2" = fresh ] && $P2 shell pm clear com.mantraproductions.tttlight >/dev/null && sleep 15
$P2 shell ime enable com.mantraproductions.tttlight/dev.patrickgold.florisboard.FlorisImeService >/dev/null
$P2 shell ime set com.mantraproductions.tttlight/dev.patrickgold.florisboard.FlorisImeService >/dev/null
for attempt in 1 2 3; do
  $P2 shell am start -a android.intent.action.VIEW -n org.chromium.webview_shell/.WebViewBrowserActivity -d "http://172.17.0.1:8765/pad.html?r$N$attempt" >/dev/null
  sleep 6; $P2 shell input tap 380 190; sleep 2; $P2 shell input keyevent 66; sleep 8
  $P2 shell input tap 500 310; sleep 2; $P2 shell input tap 540 700; sleep 4
  for t in 1 2; do
    python3 -I kb.py ttt "ok" rd >/dev/null
    box=$(convert shots/rd.png -crop 1000x80+20+455 -resize 200% -threshold 60% - | tesseract - - --psm 7 2>/dev/null)
    if echo "$box" | grep -qi "ok"; then python3 -I -c "
import sys; sys.path.insert(0,'.'); from kb import clear; clear('ttt')"; echo "READY build $N"; exit 0; fi
    $P2 shell input tap 162 2217; sleep 3      # letters were off
  done
done
echo "NOT READY"; exit 1
