#!/usr/bin/env python3
"""
RE flexibility scenarios (Marko, 9.11.2026: "test the flexibility of the system").

    python3 scripts/test_re_flex.py                 offline (CI): the scenarios are well formed, the rule checker works
    python3 scripts/test_re_flex.py --device SERIAL runs the scenarios on the phone through the real pop-up (needs adb,
                                                    the TTT keyboard and WhatsApp Business open in a chat box;
                                                    SELF-CHAT ONLY; nothing is sent)

The scenarios live in scripts/re_flex_scenarios.json, shared by the app's tests and this runner. The live model run
happens on the device with the keys the app already holds: no key is read here.
"""
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCEN = json.loads((HERE / "re_flex_scenarios.json").read_text())
failures, checks = [], 0

META = re.compile(r"^(here is|here's|sure|of course|certainly|i've|i have|evo |naravno|ok,|okay,|as requested)", re.I)
LIST = re.compile(r"^\s*([-•*]|\d+[.)])\s+", re.M)
HR_HINT = re.compile(r"[čćšžđ]|\b(sam|je|se|ću|bih|što|ako|vidimo|srijedu|poruku|kasnim|javi)\b", re.I)
EN_HINT = re.compile(r"\b(the|you|is|are|please|file|tomorrow|send|we|see)\b", re.I)


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def judge(s, box, out, is_note):
    """Returns a list of rule violations for one scenario's output."""
    bad = []
    exp = s.get("expect", "draft")
    if exp == "note":
        if not is_note:
            bad.append("expected a note (not a message)")
        return bad
    if is_note:
        return bad if exp == "either" else bad + ["got a note, expected a message"]
    low = out.lower()
    if META.search(out.strip()):
        bad.append("meta text at the start")
    if s.get("no_list") and LIST.search(out):
        bad.append("a list where none was asked")
    if s.get("expect_list") and not LIST.search(out):
        bad.append("no list although one was asked")
    for w in s.get("must_contain", []):
        if s.get("any_of"):
            continue
        if w.lower() not in low:
            bad.append(f"missing '{w}'")
    if s.get("any_of") and not any(w.lower() in low for w in s.get("must_contain", [])):
        bad.append("none of " + str(s["must_contain"]))
    for w in s.get("must_not_contain", []):
        if w.lower() in low:
            bad.append(f"still has '{w}'")
    if s.get("must_match") and not re.search(s["must_match"], out.strip(), re.M):
        bad.append("pattern not found " + s["must_match"])
    if s.get("must_not_match") and re.search(s["must_not_match"], out):
        bad.append("forbidden pattern " + s["must_not_match"])
    if box.strip():
        r = len(out) / max(1, len(box))
        if "max_len_ratio" in s and r > s["max_len_ratio"]:
            bad.append(f"not shorter enough ({r:.2f})")
    lang = s.get("language")
    if lang == "hr" and not HR_HINT.search(out):
        bad.append("not Croatian")
    if lang == "en" and (HR_HINT.search(out) and not EN_HINT.search(out)):
        bad.append("not English")
    if s.get("link_own_line") and not any(l.strip().startswith(("http", "markobosko")) for l in out.splitlines()):
        bad.append("link not on its own line")
    return bad


# ------------------------------------------------------------------ offline checks
check("at least 25 scenarios", len(SCEN) >= 25, str(len(SCEN)))
check("ids unique", len({s["id"] for s in SCEN}) == len(SCEN))
check("English and Croatian both there", sum(s["lang"] == "hr" for s in SCEN) >= 6 and sum(s["lang"] != "hr" for s in SCEN) >= 15)
for s in SCEN:
    check("well formed " + s["id"], all(k in s for k in ("id", "lang", "box", "command")))
check("rule: meta text caught", judge({"id": "x"}, "a", "Sure, here you go", False))
check("rule: list caught", judge({"id": "x", "no_list": True}, "a", "- one\n- two", False))
check("rule: removal caught", judge({"id": "x", "must_not_contain": ["really"]}, "a", "I really mean it", False))
check("rule: a good answer passes", judge(SCEN[0], B := SCEN[0]["box"], "Hi Mislav, free on Tuesday for a coffee?", False) == [])
check("rule: note allowed only when expected", judge({"id": "q", "expect": "note"}, "a", "x", True) == [] and judge({"id": "q"}, "a", "x", True))

# ------------------------------------------------------------------ device run
if "--device" in sys.argv:
    N = sys.argv[sys.argv.index("--device") + 1]

    def adb(*a, t=60):
        return subprocess.run(["adb", "-s", N, *a], capture_output=True, text=True, timeout=t).stdout

    def dump():
        adb("shell", "uiautomator", "dump", "/sdcard/re_w.xml")
        return ET.fromstring(adb("shell", "cat", "/sdcard/re_w.xml") or "<hierarchy/>")

    def texts(root):
        return [(n.get("text") or "", n.get("bounds") or "") for n in root.iter("node") if n.get("text")]

    def tap_text(root, label):
        for t, b in texts(root):
            if t.strip().lower() == label.lower():
                m = re.findall(r"\d+", b)
                x, y = (int(m[0]) + int(m[2])) // 2, (int(m[1]) + int(m[3])) // 2
                adb("shell", "input", "tap", str(x), str(y))
                return True
        return False

    def ascii_text(t):
        return t.replace(" ", "%s").replace("\n", "%s").replace("'", "").replace('"', "")

    only = [a for a in sys.argv if a.startswith("--only=")]
    todo = [s for s in SCEN if not only or s["id"] in only[0][7:].split(",")]
    rows = []
    for s in todo:
        adb("shell", "input", "tap", "450", "2150")
        adb("shell", "input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A")
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
        if s["box"]:
            adb("shell", "input", "text", ascii_text(s["box"]))
        adb("shell", "input", "swipe", "990", "2231", "990", "2231", "900")
        time.sleep(2)
        adb("shell", "input", "text", ascii_text(s["command"]))
        adb("shell", "input", "keyevent", "KEYCODE_ENTER")
        out, note, t0 = None, False, time.time()
        while time.time() - t0 < 60:
            time.sleep(3)
            root = dump()
            tx = texts(root)
            head = next(((t, i) for i, (t, _) in enumerate(tx) if t.startswith("RE draft") or t.startswith("RE says")), None)
            if head:
                note = head[0].startswith("RE says")
                body = [t for t, _ in tx[head[1] + 1:] if t not in ("Cancel", "Edit", "OK", "Close")]
                out = "\n".join(body)
                tap_text(root, "Close" if note else "Cancel")
                break
        bad = ["no pop-up within 60 s (spinner?)"] if out is None else judge(s, s["box"], out, note)
        rows.append((s["id"], "PASS" if not bad else "FAIL", "; ".join(bad), (out or "")[:90].replace("\n", " / ")))
        time.sleep(1)
    for r in rows:
        print(f"{r[1]}  {r[0]:16} {r[2]}   | {r[3]}")
    print(f"{sum(r[1]=='PASS' for r in rows)}/{len(rows)} passed on the device")
    sys.exit(0)

print(f"re flex, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
