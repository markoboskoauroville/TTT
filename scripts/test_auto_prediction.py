#!/usr/bin/env python3
"""
Test 1 for Settings → Auto prediction (DEVELOPMENT.md §223).

    python3 scripts/test_auto_prediction.py

Marko, 27.10.2026: everything about word prediction under one menu entry, the AI model included.
This pins that every prediction setting is on MaPredictionsScreen and on no other settings screen,
that the AI key asks the model chosen there, and that the choice is a radio list of real names.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
SETTINGS = SRC / "app/settings"

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


screen = code(SETTINGS / "dictate/MaPredictionsScreen.kt")

# Every prediction setting is on the one screen ...
PREFS = [
    "prefs.suggestion.nextWordPrediction", "prefs.suggestion.autoCorrect",
    "prefs.suggestion.multilingualTyping", "prefs.suggestion.api30InlineSuggestionsEnabled",
    "prefs.suggestion.displayMode", "prefs.dictate.maNgramEnabled", "prefs.dictate.maAiPredictKey",
    "prefs.dictate.maPredictModel", "prefs.dictate.maCorrectionsEnabled",
]
for p in PREFS:
    check(f"{p} on Auto prediction", p in screen)
check("what was learned is shown there", "MaNgramSetting(showSwitch = false)" in screen)
check("teaching from dictations is reached from there", "Routes.Settings.MaVocabulary" in screen)

# ... and on no other settings screen. The Switchboard's one-tap row for the suggestion row is a
# shortcut that opens this screen, so it is allowed.
ALLOWED = {"dictate/MaPredictionsScreen.kt", "dictate/MaSwitchboardScreen.kt", "dictate/MaNgramSetting.kt",
           "dictate/MaAutoScreen.kt"}  # AUTO lists every automatic change as a switch (9.11.2026)
for path in SETTINGS.rglob("*.kt"):
    rel = str(path.relative_to(SETTINGS))
    if rel in ALLOWED or "/search/" in str(path):
        continue
    t = code(path)
    for p in PREFS:
        check(f"{p} not on {rel}", p not in t, "a prediction setting outside Auto prediction")
    check(f"MaNgramSetting not on {rel}", "MaNgramSetting(" not in t)

# ---------------------------------------------------------------- never hidden (27.10.2026)
sw = code(SETTINGS / "dictate/MaSwitchboardScreen.kt")
check("no switch for the row on the switchboard", "prefs.suggestion.enabled" not in sw)
check("the switchboard does not list it",
      "filterNot { it == Entry.SUGGESTIONS }" in code(SETTINGS / "dictate/MaSwitchboardOrder.kt"))
check("no switch for the row anywhere in settings",
      all("prefs.suggestion.enabled," not in code(f) and "prefs.smartbar.enabled," not in code(f)
          for f in SETTINGS.rglob("*.kt") if "/search/" not in str(f)))
vis = code(SRC / "ime/smartbar/MaSmartbarVisibility.kt")
check("the row keeps its place unless he turns auto-hide on", "if (!autoHide) return true" in vis)
check("auto-hide is off unless he turns it on",
      re.search(r'maPredictRowAutoHide = boolean\(\s*key = "dictate__ma_predict_row_auto_hide",\s*default = false', code(SRC / "app/AppPrefs.kt")) is not None)
check("right after the model: auto-hide, then the language",
      0 <= screen.find("PredictionModelChooser(enabled") < screen.find("prefs.dictate.maPredictRowAutoHide")
      < screen.find("prefs.dictate.maDetectTypingLanguage") < screen.find("prefs.dictate.maAiPredictKey,"))
nlp = code(SRC / "ime/nlp/NlpManager.kt")
check("suggestions use the typing language", "val typing = MaTypingLanguage.of(content.textBeforeSelection)" in nlp)
check("the shipped dictionary of that language", "getSuggestionProvider(shippedSubtype ?: subtype)" in nlp)
check("both dictionaries preloaded", "all.forEach { preload(it) }" in nlp)
check("detection is on unless he turns it off",
      re.search(r'maDetectTypingLanguage = boolean\(\s*key = "dictate__ma_detect_typing_language",\s*default = true', code(SRC / "app/AppPrefs.kt")) is not None)
bar = code(SRC / "ime/smartbar/Smartbar.kt")
check("the curtain is gone", "val maSuggestionsShown = true" in bar)
check("the Smartbar is always drawn", "val smartbarEnabled = true" in bar)
siz = code(SRC / "ime/keyboard/FlorisImeSizing.kt")
check("its height always counted", "prefs.smartbar.enabled" not in siz and siz.count("val smartbarEnabled = true") == 3)
km = code(SRC / "ime/keyboard/KeyboardManager.kt")
check("the toggle key cannot hide it", "prefs.smartbar.enabled.let { it.set(!it.get()) }" not in km)
check("the badge hold cannot hide it",
      "maSuggestionsShown.set(!prefs.dictate.maSuggestionsShown.get())" not in code(SRC / "dictate/MaLanguage.kt"))
mig = code(SRC / "dictate/data/prefs/DictateLegacyMigrator.kt")
check("turned back on at every start", "fun keepPredictionRowOn()" in mig and
      "DictateLegacyMigrator.keepPredictionRowOn()" in code(SRC / "FlorisApplication.kt"))

# ---------------------------------------------------------------- the model first (27.10.2026)
check("My corrections first, then the model (28.10.2026)",
      0 <= screen.find("Routes.Settings.MaDictationCorrections") < screen.find("PredictionModelChooser(enabled"))
check("the model chooser comes before every switch",
      0 <= screen.find("PredictionModelChooser(enabled") < screen.find("SwitchPreference("))

# ---------------------------------------------------------------- the AI words go with the text
row = code(SRC / "ime/smartbar/CandidatesRow.kt")
ai = code(SRC / "dictate/nlp/MaAiPredict.kt")
check("the words know what they were asked for", "fun stillFor(before: String)" in ai)
check("they clear when the text changes", "!MaAiPredict.stillFor(before)) MaAiPredict.clear()" in row)
check("a late answer for gone text is dropped", "if (!MaAiPredict.stillFor(editorInstance.activeContent" in row)

# The entry: named as he names it, the only one about prediction.
order = code(SETTINGS / "MaSettingsOrder.kt")
check("the entry is called Auto prediction", 'PREDICTIONS("predictions", "Auto prediction"' in order)
check("Learn my words has no entry of its own", "MaSettingsEntry.VOCABULARY" in order and "val hidden" in order)
check("My corrections has no entry of its own", "MaSettingsEntry.CORRECTIONS" in order and "val hidden" in order)
check("My corrections is reached from it", "Routes.Settings.MaDictationCorrections" in screen)
check("the big A icon", "MaSettingsEntry.PREDICTIONS -> Icons.Default.FontDownload" in code(SETTINGS / "MaSettingsOrderScreen.kt"))
check("beside Prompts and its costs",
      re.search(r"MaSettingsEntry\.CLAUDE_COSTS,\s*MaSettingsEntry\.PREDICTIONS,", order[order.find("val DEFAULT"):]) is not None)
strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
check("the title string", '<string name="settings__ma_predictions__title">Auto prediction</string>' in strings)
check("the screen uses it", "R.string.settings__ma_predictions__title" in screen)

# The model: a radio list of real names, Claude only, paid with the loaded Anthropic key.
check("a radio list", "RadioButton(" in screen)
check("each row shows the real name", "MaClaudeModels.displayName(MaClaudeModels.resolve(alias, cached))" in screen)
check("the three Claude tiers", 'listOf("haiku", "sonnet", "opus")' in screen)
check("no 'Default' on the screen", "Default" not in screen)
check("the key's state is said, never the key", "hasKey" in screen and "apiKey}" not in screen)

ctrl = code(SRC / "dictate/DictateController.kt")
row = code(SRC / "ime/smartbar/CandidatesRow.kt")
ngram = code(SRC / "dictate/nlp/MaNgram.kt")
check("askPredictionModel uses the chosen model", "modelOverride = prefs.dictate.maPredictModel.get()" in ctrl)
check("the override reaches the cheap path", "if (modelOverride != null && account.providerId == MaRoles.REWORDING)" in ctrl)
check("the AI key asks it", "DictateController.askPredictionModel(" in row)
check("sorting new words into a language asks it", "DictateController.askPredictionModel(" in ngram)

# Search finds the moved settings on their new screen.
index = (SETTINGS / "search/SettingsSearchIndex.kt").read_text()
for key in ["pref__ma_auto_correct__label", "pref__ma_multilingual__label", "pref__ma_next_word__label",
            "pref__ma_inline__label", "pref__suggestion__display_mode__label", "settings__ma_predict_model__title",
            "pref__ma_ai_predict_key__label", "pref__ma_ngram_enabled__label", "pref__ma_corrections_enabled__label"]:
    check(f"search: {key} -> Auto prediction",
          f"SettingsSearchEntry(R.string.{key}, R.string.settings__ma_predictions__title, Routes.Settings.MaPredictions" in index)
check("search: My corrections", "SettingsSearchEntry(R.string.settings__ma_corrections__title, R.string.settings__ma_predictions__title, Routes.Settings.MaDictationCorrections)" in index)

# The one-time move up beside Prompts (he could not find it near the bottom of his stored order).
check("the move exists", "fun withPredictionsBesidePrompts(raw: String): String" in order)
mig = code(SRC / "dictate/data/prefs/DictateLegacyMigrator.kt")
check("it runs once", "maPredictionsMovedV1.get()) return" in mig and "maPredictionsMovedV1.set(true)" in mig)
check("it runs at start", "DictateLegacyMigrator.movePredictionsBesidePromptsIfNeeded()" in code(SRC / "FlorisApplication.kt"))


def move(raw: str) -> str:  # the same steps as withPredictionsBesidePrompts
    ids = [i.strip() for i in raw.split(",") if i.strip() and i.strip() not in ("predictions", "corrections")]
    anchor = next((a for a in ("claude_costs", "prompts") if a in ids), None)
    if anchor is None:
        return raw
    ids.insert(ids.index(anchor) + 1, "predictions")
    return ",".join(ids)


check("after Claude costs", move("a,prompts,claude_costs,b,vocabulary,predictions") == "a,prompts,claude_costs,predictions,b,vocabulary")
check("after Prompts without costs", move("prompts,x,predictions") == "prompts,predictions,x")
check("corrections folds in", move("prompts,claude_costs,corrections,x,predictions") == "prompts,claude_costs,predictions,x")
check("nothing to anchor: unchanged", move("x,y,predictions") == "x,y,predictions")

# ---------------------------------------------------------------- the copied line (28.10.2026)
strip = code(SRC / "dictate/ui/MaBucketStrip.kt")
cp = strip[strip.find('val full = clip?.stringRepresentation()'):strip.find('text = "copied"')]
check("the copied line fills the row's height, so it is centred", ".fillMaxHeight()" in cp)
check("a tap pastes it at the cursor", "currentInputConnection()?.commitText(full, 1)" in cp)
check("never selects the field away", "selectAll" not in cp)
check("pasted, it leaves the row", "stamp != pasted" in strip and "MaCopiedLine.pasted.value = stamp" in strip)
check("the row scrolls back to its first word", "LaunchedEffect(candidates) { rowScroll.scrollTo(0) }" in row
      and "florisHorizontalScroll(state = rowScroll" in row)

print(f"auto prediction, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
