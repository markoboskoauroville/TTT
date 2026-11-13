#!/bin/bash
# Speak to Marko on his Nothing while debugging (TTT skill rule 18). TEXT ONLY (Marko, 13.11.2026: "No audio anymore"):
# Voice Engine v4 (VOICE_ENGINE_ANDROID) speaks it through Speechify in Beatrice and shows the subtitle.
D="-s 127.0.0.1:15555"
adb $D forward tcp:18720 tcp:8720 >/dev/null 2>&1
python3 - "$*" <<'PY'
import json,sys,urllib.request
b=json.dumps({"text":sys.argv[1]}).encode()
try:
    r=urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:18720/say",data=b,headers={"Content-Type":"application/json"}),timeout=10)
    print(r.status, r.read()[:160].decode())
except urllib.error.HTTPError as e:
    print(e.code, e.read()[:160].decode())
PY
