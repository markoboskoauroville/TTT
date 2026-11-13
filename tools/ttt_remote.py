#!/usr/bin/env python3
"""
TTT REMOTE: drive TTT on Marko's phone from a distance (Marko, 13.11.2026: "make Python API so you can do anything from
the distance. You can even talk through my keyboard").

Everything goes over adb (on the cloud: adb over Tailscale, tools/voice/adb_tailscale_fwd.py). The phone side is TTT's
debug receiver: only adb can send to it (android.permission.DUMP), answers land in files/remote/<id>.json. No key value
ever leaves the phone through this; status shows counts only.

    python3 tools/ttt_remote.py connect                 # tunnel + adb connect (cloud), or plain adb (USB / LAN)
    python3 tools/ttt_remote.py say "text"              # Beatrice (TTT Reader voice, Speechify) + Voice Engine subtitle
    python3 tools/ttt_remote.py vsay "text"             # Voice Engine v4 /say (its own Speechify key)
    python3 tools/ttt_remote.py status                  # version, RE engine, H/E engines, key counts per ring, state
    python3 tools/ttt_remote.py type "text"             # TTT types into the focused field
    python3 tools/ttt_remote.py re "reply to Ana ..." [--engine qwen|opus|sonnet|anthropic_auto]
    python3 tools/ttt_remote.py engine qwen             # set the RE engine
    python3 tools/ttt_remote.py compare [--engines qwen,opus] [--only hr_late,en_fix]   # MaReCompare, prints a table
    python3 tools/ttt_remote.py shot out.png            # screenshot
    python3 tools/ttt_remote.py ui                      # texts on screen with tap centres
    python3 tools/ttt_remote.py tap X Y | key BACK | text "abc"
    python3 tools/ttt_remote.py install 642             # release build-642 from GitHub, adb install -r (never uninstall)
    python3 tools/ttt_remote.py log                     # files/engines_log.txt
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

PKG = "com.mantraproductions.tttlight"
RCV = PKG + "/dev.patrickgold.florisboard.dictate.engines.EngineDebugReceiver"
FILES = f"/sdcard/Android/data/{PKG}/files"
DEV = os.environ.get("TTT_DEVICE", "127.0.0.1:15555")
HERE = os.path.dirname(os.path.abspath(__file__))


def adb(*args, check=False, text=True):
    r = subprocess.run(["adb", "-s", DEV, *args], capture_output=True, text=text)
    if check and r.returncode != 0:
        sys.exit(r.stderr.strip() if text else r.stderr)
    return r.stdout if text else r.stdout


def q(s: str) -> str:
    """One shell-quoted argument for `adb shell` (the device shell parses it again)."""
    return "'" + s.replace("'", "'\\''") + "'"


def broadcast(action, **extras):
    cmd = ["shell", "am", "broadcast", "-a", action, "-n", RCV]
    for k, v in extras.items():
        if v is None:
            continue
        cmd += ["--es", k, q(str(v))]
    return adb(*cmd)


def result(rid, timeout=120):
    path = f"{FILES}/remote/{rid}.json"
    t0 = time.time()
    while time.time() - t0 < timeout:
        out = adb("shell", "cat", path)
        if out.strip().startswith("{"):
            adb("shell", "rm", "-f", path)
            return json.loads(out)
        time.sleep(1)
    sys.exit(f"no answer in {timeout}s ({path})")


def call(action, timeout=120, **extras):
    rid = f"r{int(time.time() * 1000)}"
    broadcast(action, id=rid, **extras)
    return result(rid, timeout)


def connect():
    if DEV.startswith("127.0.0.1:"):
        port = DEV.split(":")[1]
        running = subprocess.run(["pgrep", "-f", f"adb_tailscale_fwd.py {port}"], capture_output=True).returncode == 0
        if not running:
            subprocess.Popen([sys.executable, os.path.join(HERE, "voice", "adb_tailscale_fwd.py"), port,
                              os.environ.get("TTT_PHONE_IP", "100.67.149.40"), "5555"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
            time.sleep(1)
        print(subprocess.run(["adb", "connect", DEV], capture_output=True, text=True).stdout.strip())
    print(adb("get-state").strip() or "not connected")


def ui():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = adb("shell", "cat", "/sdcard/ui.xml")
    for m in re.finditer(r'(?:text|content-desc)="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        a, b, c, d = map(int, m.groups()[1:])
        print(m.group(1)[:90], (a + c) // 2, (b + d) // 2)


def main(argv):
    if not argv:
        print(__doc__)
        return
    cmd, rest = argv[0], argv[1:]
    opt = {}
    pos = []
    i = 0
    while i < len(rest):
        if rest[i].startswith("--"):
            opt[rest[i][2:]] = rest[i + 1]
            i += 2
        else:
            pos.append(rest[i])
            i += 1
    if cmd == "connect":
        connect()
    elif cmd == "say":
        broadcast("dev.ttt.engines.SAY", t=" ".join(pos))
    elif cmd == "vsay":
        adb("forward", "tcp:18720", "tcp:8720")
        b = json.dumps({"text": " ".join(pos)}).encode()
        try:
            r = urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:18720/say", data=b,
                                                              headers={"Content-Type": "application/json"}), timeout=10)
            print(r.status, r.read().decode()[:200])
        except urllib.error.HTTPError as e:
            print(e.code, e.read().decode()[:200])
    elif cmd == "status":
        print(json.dumps(call("dev.ttt.remote.STATUS", 20), indent=1))
    elif cmd == "type":
        print(call("dev.ttt.remote.TYPE", 20, t=" ".join(pos)))
    elif cmd == "engine":
        print(call("dev.ttt.remote.RE_ENGINE", 20, e=pos[0]))
    elif cmd == "re":
        r = call("dev.ttt.remote.RE_ASK", 180, t=" ".join(pos), e=opt.get("engine"))
        print(f"[{r.get('model') or opt.get('engine', '')}] {r.get('ms')} ms" + (f" ERROR {r['error']}" if r.get("error") else ""))
        print(r.get("answer", ""))
    elif cmd == "compare":
        adb("shell", "rm", "-f", f"{FILES}/re_compare.json", f"{FILES}/engines_log.txt")
        broadcast("dev.ttt.engines.RE_COMPARE", e=opt.get("engines", "qwen,sonnet,opus,anthropic_auto"), only=opt.get("only"))
        while "compare done" not in adb("shell", "cat", f"{FILES}/engines_log.txt"):
            time.sleep(3)
        rows = json.loads(adb("shell", "cat", f"{FILES}/re_compare.json"))
        for r in rows:
            flag = "" if (r["hasDraft"] == r["wantDraft"] and r["lang"] == r["wantLang"] and not r["think"]) else "  <-- check"
            print(f"{r['scenario']:13} {r['engine']:15} {r['ms']:6} ms {r['answer'][:110].replace(chr(10), ' ')}{flag}")
        out = opt.get("out")
        if out:
            json.dump(rows, open(out, "w"), indent=1, ensure_ascii=False)
    elif cmd == "shot":
        png = adb("exec-out", "screencap", "-p", text=False)
        open(pos[0] if pos else "shot.png", "wb").write(png)
        print(pos[0] if pos else "shot.png")
    elif cmd == "ui":
        ui()
    elif cmd == "tap":
        adb("shell", "input", "tap", pos[0], pos[1])
    elif cmd == "key":
        adb("shell", "input", "keyevent", pos[0])
    elif cmd == "text":
        adb("shell", "input", "text", q(" ".join(pos).replace(" ", "%s")))
    elif cmd == "install":
        n = pos[0]
        apk = f"/tmp/ttt-build-{n}.apk"
        urllib.request.urlretrieve(f"https://github.com/markoboskoauroville/TTT/releases/download/build-{n}/ttt-build-{n}.apk", apk)
        print(subprocess.run(["adb", "-s", DEV, "install", "-r", apk], capture_output=True, text=True).stdout.strip())
    elif cmd == "log":
        print(adb("shell", "cat", f"{FILES}/engines_log.txt"))
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
