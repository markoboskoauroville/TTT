#!/usr/bin/env python3
"""
Test 1 for AI words while typing (MaAiPredict.live, DEVELOPMENT.md §235).

    python3 scripts/test_live_ai_words.py

Marko, 4.11.2026: "always do parallel to word prediction using Anthropic Haiku, parallel to local
engine, both displayed at same time".
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


row = code(SRC / "ime/smartbar/CandidatesRow.kt")
ai = code(SRC / "dictate/nlp/MaAiPredict.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
prefs = code(SRC / "app/AppPrefs.kt")
screen = code(SRC / "app/settings/dictate/MaPredictionsScreen.kt")

# Always Haiku, on by default.
check("on unless he turns it off",
      re.search(r'maAiLivePredict = boolean\(\s*key = "dictate__ma_ai_live_predict",\s*default = true', prefs) is not None)
check("always the newest Haiku", "modelOverride = MaClaudeModels.DEFAULT, purpose = \"Live words\"" in ctrl)
check("the switch is on Auto prediction", "prefs.dictate.maAiLivePredict," in screen)

# In parallel, after a pause, never stale.
check("asked after a pause, not on every letter", "delay(MaAiPredict.LIVE_PAUSE_MS)" in row and "LIVE_PAUSE_MS = 450L" in ai)
check("a newer text cancels the question", ".collectLatest { (before, current) ->" in row)
check("old words go the moment the text changes", "if (MaAiPredict.liveFor != before) MaAiPredict.clearLive()" in row)
check("three words", "LIVE_WANTED = 3" in ai and "wanted = MaAiPredict.LIVE_WANTED" in row)
check("in the language being typed", "MaTypingLanguage.of(before) == MaLanguage.HR" in row)

# Never where it must not.
check("not in incognito", "keyboardManager.activeState.isIncognitoMode ||" in row)
check("not in password fields", "maIsSecretField(editorInstance.activeInfo.base.inputType)" in row and "TYPE_TEXT_VARIATION_PASSWORD" in row)
check("not without a key", "if (MaProviders.rewordingApiKey().isBlank()) return@collectLatest" in row)
check("not while the AI key's own words show", "MaAiPredict.words.value.isNotEmpty()) return@collectLatest" in row)

# Both at once.
check("after the local words, never instead", "} + liveCandidates" in row)
check("no word twice", "liveWords.filter { it.lowercase() !in local }" in row)
check("a sparkle where Haiku's words begin", "if (n == firstLive && liveCandidates.isNotEmpty())" in row and "Icons.Default.AutoAwesome" in row)
check("shown even when the local engine has nothing", "if (candidates.isNotEmpty() || liveCandidates.isNotEmpty())" in row)
check("a picked Haiku word teaches the local model", "if (aiWords.isNotEmpty() || n >= firstLive)" in row)

# Haiku teaches the local engine as we go (4.11.2026), and is asked less as it does.
ngram = code(SRC / "dictate/nlp/MaNgram.kt")
check("each answer is kept for the local engine", "MaNgram.rememberTeacher(before, current, words," in row)
check("the local engine offers it, after his own words", "teacherFor(language).words(two, one, word)" in ngram
      and "WHAT HAIKU TAUGHT" in (SRC / "dictate/nlp/MaNgram.kt").read_text())
check("a spot it already knows is not asked again",
      "if (MaNgram.teacherWords(before, current).size >= MaAiPredict.LIVE_WANTED) return@collectLatest" in row)
check("kept on disk, per language", "ma_teacher_$language.tsv" in ngram)
check("forgotten with the rest", ngram.count("teacherFor(language).clear()") >= 2)
check("never his counts: a guess is not a word he wrote", "teacherFor(language).remember(two, one, words)" in ngram
      and "modelFor(language).learn(" not in ngram[ngram.find("fun rememberTeacher"):ngram.find("fun teacherWords")])

# 4.11.2026: tapping a word typed an older one ("since since since"): the gesture kept its first lambda.
item = row[row.find("private fun CandidateItem"):]
check("the tap calls the word on screen now", "val currentOnClick by rememberUpdatedState(onClick)" in item
      and "currentOnClick()" in item and "\n                        onClick()" not in item)
check("the long press too", "val currentOnLongPress by rememberUpdatedState(onLongPress)" in item and "if (currentOnLongPress())" in item)

print(f"live AI words, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
