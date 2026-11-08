#!/usr/bin/env python3
"""
Test 1 for one line break between paragraphs and for E|H staying Croatian (DEVELOPMENT.md §230).

    python3 scripts/test_paragraphs_translate.py

Marko, 3.11.2026: reflow and the style pass put an empty line between paragraphs ("only new line
without any space in between"), and E|H pasted English.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


par = code(SRC / "dictate/MaParagraphs.kt")
ctrl = code(SRC / "dictate/DictateController.kt")

BLANK = re.compile("\n[ \t ]*(?:\n[ \t ]*)+")


def single(t: str) -> str:  # MaParagraphs.single
    return BLANK.sub("\n", t.replace("\r\n", "\n"))


check("the rule is the same", "private val BLANK_LINES = Regex(" in par and "u00A0]*(?:" in par)
check("an empty line goes", single("Dobro jutro, Damjan.\n\nRadim na tome.") == "Dobro jutro, Damjan.\nRadim na tome.")
check("several go", single("a\n\n\n\nb") == "a\nb")
check("spaces on the empty line too", single("a\n  \n\t\nb") == "a\nb")
check("CRLF", single("a\r\n\r\nb") == "a\nb")
check("a single break stays", single("a\nb") == "a\nb")
check("no break, no change", single("one line") == "one line")

check("dictation is written with single breaks", "MaParagraphs.single(MaSpokenEmail.apply(MaSpokenFormat.apply(text)))" in ctrl)
check("Ctrl+P and Ctrl+F too", "sink.commitText(MaParagraphs.single(text))" in ctrl)
check("the realtime path too", "val outputText = MaParagraphs.single(" in ctrl)

check("E|H runs on single-call audio too", "val translated = if (translateRecording) translateToCroatian(rawText) else null" in ctrl)
check("a translation skips the formatting pass that turned it back into English",
      "if (alreadyFormatted || translated != null) checked else postProcessTranscript(appContext, checked)" in ctrl)

print(f"paragraphs and translate, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
