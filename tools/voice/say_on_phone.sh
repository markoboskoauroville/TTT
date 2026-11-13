#!/bin/bash
# Speak to Marko on his Nothing while debugging (TTT skill rule 18).
# First choice: TTT's Reader voice, Beatrice (his cloned voice on Speechify, key kept inside TTT), with Voice Engine
# subtitles: adb broadcast dev.ttt.engines.SAY (build 641+). Fallback when that build is not installed: espeak-ng
# made locally, played by Voice Engine v3 (POST /play on 127.0.0.1:8720 through an adb forward).
D="-s 127.0.0.1:15555"
S=${SCRATCH:-/tmp}
TEXT="$*"
V=$(adb $D shell dumpsys package com.mantraproductions.tttlight | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2)
if [ "${V:-0}" -ge 1641 ]; then
  adb $D shell am broadcast -a dev.ttt.engines.SAY --es t "'$(printf %s "$TEXT" | sed "s/'/ /g")'" \
    -n com.mantraproductions.tttlight/dev.patrickgold.florisboard.dictate.engines.EngineDebugReceiver >/dev/null
  echo "beatrice"; exit 0
fi
adb $D forward tcp:18720 tcp:8720 >/dev/null 2>&1
espeak-ng -v en-gb+m3 -s 150 -w $S/say.wav "$TEXT" 2>/dev/null
ffmpeg -y -loglevel error -i $S/say.wav -ac 1 -ar 22050 -b:a 64k $S/say.mp3
DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 $S/say.mp3)
python3 - "$TEXT" "$S/say.mp3" "$DUR" <<'PY'
import base64,json,sys,urllib.request,time
text,mp3,dur=sys.argv[1],sys.argv[2],float(sys.argv[3])
b=json.dumps({"id":"cc-%d"%time.time(),"text":text,"audio":base64.b64encode(open(mp3,'rb').read()).decode(),"words":[],"dur":dur}).encode()
r=urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:18720/play",data=b,headers={"Content-Type":"application/json"}),timeout=10)
print("espeak", r.status)
PY
