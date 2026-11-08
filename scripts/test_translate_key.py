#!/usr/bin/env python3
"""
Test 1 for the E>H key (MaTranslate, DEVELOPMENT.md §229).

    python3 scripts/test_translate_key.py

Marko, 2.11.2026: dictate in English, Claude translates it into Croatian in his style, and that is
pasted. A red dot, "E>H". This pins the key, the pipeline step, its prompt on the Prompts screen and
the one-time placement next to his E key.
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


order = code(SRC / "dictate/MaFeatureOrder.kt")
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
rows_screen = code(SRC / "app/settings/dictate/MaRowsScreen.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
tr = code(SRC / "dictate/MaTranslate.kt")
prompts = code(SRC / "app/settings/dictate/MaPromptsScreen.kt")
mig = code(SRC / "dictate/data/prefs/DictateLegacyMigrator.kt")
rows = code(SRC / "dictate/MaRows.kt")
app = code(SRC / "FlorisApplication.kt")

# The key
check("the key exists", 'RECORD_EN_HR("recordenhr"' in order)
check("it is a dictation key", "MaFeatureKey.RECORD_EN_HR,\n        MaFeatureKey.RECORD_EN_NET," in order)   # WA follows RE since 7.11.2026
check("drawn with the record keys (red dot)", "MaFeatureKey.RECORD_HR, MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_EN_HR," in row)
check("its letters are E>H", 'translate -> "E>H"' in row)
check("in the rows editor too", 'MaFeatureKey.RECORD_EN_HR -> letters("\\u25CF E>H")' in rows_screen)
check("it records English", "val language = if (button.key == MaFeatureKey.RECORD_HR ||" in row)
check("and asks for the translation", "DictateController.onMicClick(context, translate = translate, engine = engine)" in row)
check("only the key that started it rings", "DictateController.isTranslating() == translate" in row)

# The pipeline
check("set at every start", "translateRecording = translate && !reprompt" in ctrl)
check("translated after transcription", "if (translateRecording) translateToCroatian(rawText)" in ctrl)
check("instead of style and spell check, not on top",
      re.search(r"translated != null -> translated\s*!alreadyFormatted -> phoneStyle", ctrl) is not None)
check("with the chosen model and the loaded key", "requestReword(MaTranslate.instruction()" in ctrl and "MaTranslate.model()" in ctrl)
check("a failure keeps the English and says so", "E|H failed: the English was written instead" in ctrl)
check("a reply that is not a translation is refused", "MaTranslate.accept(english, reply)" in ctrl)

# The prompt
check("it translates into Croatian", "Translate \" +\n            \"it into Croatian" in tr or "Translate it into Croatian" in tr.replace('" +\n            "', ""))
check("in his style", "the way Marko writes" in tr.replace('" +\n            "', ""))
check("editable on Prompts, with its model", "builtInName = MaTranslate.NAME" in prompts and "modelPref = prefs.dictate.maTranslateModel" in prompts)

# On his keyboard
check("placed next to his E key, once", "fun addTranslateKeyIfNeeded()" in mig and "maTranslateKeyAdded.set(true)" in mig)
check("after E, else after H", "listOf(MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_HR)" in mig)
check("runs at start", "DictateLegacyMigrator.addTranslateKeyIfNeeded()" in app)
check("never twice in a row", "if (row.entries.any { it.button == Button.Builtin(key) }) return@map row" in rows)


def accept(english: str, translated: str) -> bool:  # MaTranslate.accept
    t = len(translated.strip())
    if t == 0:
        return False
    e = max(1, len(english.strip()))
    too_long = t > e * 2.5 and t > e + 60
    too_short = t < e * 0.33 and t < e - 30
    return not too_long and not too_short


check("a translation of similar length passes", accept("I will bring the guitar tomorrow.", "Sutra ću donijeti gitaru."))
check("an empty reply fails", not accept("Hello there", "  "))
check("the phone's case fails: 701 chars for 38", not accept("x" * 38, "y" * 701))
check("a short line may grow a little: Ok -> U redu", accept("Ok", "U redu"))
check("a short line translated longer still passes", accept("Give me an hour.", "Daj mi sat vremena, molim te."))

# 3.11.2026: the dictation is fenced, so a short line is translated, not answered; one retry.
check("the dictation is fenced in tags", "<english>" in tr and "fun message(instruction: String, english: String)" in tr)
check("a refused reply gets one stricter try", "MaTranslate.strictMessage(english)" in ctrl)
check("sent as the fenced message", "ask(MaTranslate.message(MaTranslate.instruction(), english))" in ctrl)
check("no empty line between its paragraphs", "a single line break between them, never an empty line" in tr.replace('" +\n            "', ""))

print(f"translate key, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
