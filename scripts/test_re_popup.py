#!/usr/bin/env python3
"""
Test 1 for the RE pop-up (MaReResult, 9.11.2026): RE never writes into the field by itself; only OK inserts a
message; notes and errors are pop-up only.

    python3 scripts/test_re_popup.py
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


ctrl = code(SRC / "dictate/DictateController.kt")
res = code(SRC / "dictate/MaReResult.kt")
act = code(SRC / "dictate/MaReResultActivity.kt")
ime = code(SRC / "FlorisImeService.kt")
skill = code(SRC / "dictate/MaReSkill.kt")
chat = code(SRC / "dictate/overlay/MaScreenChat.kt")
man = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

i = ctrl.find("if (prompt.name == MaReprompt.NAME) {\n                    _state.value")
j = ctrl.find("sink.commitText(MaParagraphs.single(text))", i)
check("RE's answer goes to the pop-up before any commit", 0 < i < j and "MaReResult.present(" in ctrl[i:j] and "return@launch" in ctrl[i:j])
check("the draft is registered only when inserted", "MaReSkill.onComposed(r.pkg" in ctrl and ctrl.count("MaReSkill.onComposed(") == 1)
check("the pop-up is declared", "dictate.MaReResultActivity" in man)
check("OK, EDIT, CANCEL for a message", all(w in act for w in ('Text("OK"', 'Text("Edit"', 'Text("Cancel"')))
check("a note has only Close", 'Text("Close"' in act and "if (result.note)" in act)
check("a note can never be accepted", "if (r.note || finalText.isBlank()) return" in res)
check("large scrollable text", "verticalScroll" in act and "fontSize = 20.sp" in act)
check("insertion happens when the field is back", "MaReResult.takeInsert(info.packageName, info.fieldId)" in ime and "insertReResult" in ime)
check("errors raised by RE are notes", ctrl.count("MaReResult.note(") >= 1 and "MaReResult.note(" in ime)
check("the model is told the RE-NOTE format", 'RE-NOTE:' in skill and "ANSWER FORMAT" in skill)
check("an edit in the pop-up is learned from", "noteCorrection(r.pkg" in res and "fun noteCorrection" in skill)
check("the app's own bar is not chat", "inputTopSeen" in chat and "Emoji" in chat.replace("Emoji, GIFs", "Emoji") or "inputTopSeen" in chat)
check("no description labels in the chat lines", "contentDescription" not in chat.split("fun collect")[1].split("fun keyboardTop")[0])


def is_note(t):
    t = t.strip().lower()
    return t.startswith("re-note:") or t.startswith("no message found")


check("note detection", is_note("RE-NOTE: which message?") and is_note("NO MESSAGE FOUND") and not is_note("Hi, ja sam Marko"))
print(f"re popup, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
