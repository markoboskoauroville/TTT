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
check("it is a dictation key", "MaFeatureKey.RECORD_EN_HR,\n        MaFeatureKey.SEND," in order)
check("drawn with the record keys (red dot)", "MaFeatureKey.RECORD_HR, MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_EN_HR ->" in row)
check("its letters are E>H", 'translate -> "E>H"' in row)
check("in the rows editor too", 'MaFeatureKey.RECORD_EN_HR -> letters("\\u25CF E>H")' in rows_screen)
check("it records English", "if (button.key == MaFeatureKey.RECORD_HR) MaLanguage.HR else MaLanguage.EN" in row)
check("and asks for the translation", "DictateController.onMicClick(context, translate = translate)" in row)
check("only the key that started it rings", "DictateController.isTranslating() == translate" in row)

# The pipeline
check("set at every start", "translateRecording = translate\n                startRecording(context)" in ctrl)
check("translated after transcription", "if (translateRecording && !alreadyFormatted) translateToCroatian(rawText)" in ctrl)
check("instead of style and spell check, not on top",
      re.search(r"translated != null -> translated\s*!alreadyFormatted -> phoneStyle", ctrl) is not None)
check("with the chosen model and the loaded key", "requestReword(MaTranslate.instruction()" in ctrl and "MaTranslate.model()" in ctrl)
check("a failure keeps the English", "E>H failed: the English was written instead" in ctrl)
check("a reply that is not a translation is refused", "MaTranslate.accept(text, translated)" in ctrl)

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
    t = translated.strip()
    if not t:
        return False
    return 0.33 <= len(t) / max(1, len(english.strip())) <= 2.5


check("a translation of similar length passes", accept("I will bring the guitar tomorrow.", "Sutra ću donijeti gitaru."))
check("an empty reply fails", not accept("Hello there", "  "))
check("a model talking fails", not accept("ok", "Here is the translation of your message into Croatian: U redu."))

print(f"translate key, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
