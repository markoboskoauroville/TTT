#!/bin/bash
# Speak to Marko on his Nothing through Voice Engine v3 (POST /play: it plays the mp3 and shows the subtitle).
# Local speech (espeak-ng), nothing leaves this machine except to his phone over adb.
S=${SCRATCH:-/tmp}
TEXT="$*"
adb -s 127.0.0.1:15555 forward tcp:18720 tcp:8720 >/dev/null 2>&1
espeak-ng -v en-gb+m3 -s 150 -w $S/say.wav "$TEXT" 2>/dev/null
ffmpeg -y -loglevel error -i $S/say.wav -ac 1 -ar 22050 -b:a 64k $S/say.mp3
DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 $S/say.mp3)
python3 - "$TEXT" "$S/say.mp3" "$DUR" <<'PY'
import base64,json,sys,urllib.request,time
text,mp3,dur=sys.argv[1],sys.argv[2],float(sys.argv[3])
b=json.dumps({"id":"cc-%d"%time.time(),"text":text,"audio":base64.b64encode(open(mp3,'rb').read()).decode(),"words":[],"dur":dur}).encode()
r=urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:18720/play",data=b,headers={"Content-Type":"application/json"}),timeout=10)
print(r.status, r.read()[:120].decode())
PY
