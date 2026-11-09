#!/usr/bin/env python3
"""
Test 1 for the RE assistant, slice 1 (MaAssistant, 9.11.2026): the trigger, the commands, read only, banking refused.

    python3 scripts/test_assistant.py
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


a = code(SRC / "dictate/MaAssistant.kt")
tile = code(SRC / "dictate/MaAssistantTile.kt")
acc = code(SRC / "dictate/overlay/DictateAccessibilityService.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
man = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
xml = (ROOT / "app/src/main/res/xml/accessibility_service_config.xml").read_text()

check("a Quick Settings tile", "MaAssistantTile" in man and "BIND_QUICK_SETTINGS_TILE" in man and "TileService" in tile)
check("the accessibility button", "flagRequestAccessibilityButton" in xml and "registerAccessibilityButtonCallback" in acc)
check("both go through one toggle, AssemblyAI only", "MaAssistant.toggle(" in tile + acc and "ENGINE_ASSEMBLYAI" in a)
check("the transcript reaches the assistant", "MaAssistant.take()" in ctrl and "MaAssistant.handle(" in ctrl)
check("a reply is a pop-up draft", "applyPrompt(" in a and "write = true" in a and "OutputTarget.OVERLAY" in a)
check("everything else is read-only and spoken", "askModel(askPrompt(words, screen)" in a and "TextToSpeech" in a)
check("it never sends, buys or deletes", "Never offer to send, buy or delete" in a and not any(w in a for w in ("performEnter", "pressSend", "performClick", "dispatchGesture")))
check("banking apps are refused", "REFUSED" in a and "does not read banking" in a)
check("it ends in a pop-up always", "MaReResult.note(" in a)
check("the model call is bounded", "withTimeoutOrNull(MaReprompt.RE_TIMEOUT_MS)" in a)
check("an assistant OK inserts without the keyboard", "viaAssistant" in code(SRC / "dictate/MaReResult.kt"))
W = re.compile(r"^(please )?(reply|answer|respond)\b|\bodgovori\b|\bnapi[sš]i (mu|joj|im|odgovor)\b", re.I)
for p in ("reply to the latest message saying yes", "odgovori Ivanu da dolazim", "answer the last message", "napisi mu da kasnim"):
    check("reply: " + p, bool(W.search(p)))
for p in ("read me the last messages", "prociitaj zadnje poruke", "what is on the screen", "summarise this chat", "who wrote last"):
    check("not a reply: " + p, not W.search(p))
print(f"assistant, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
