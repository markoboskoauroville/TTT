#!/usr/bin/env python3
"""
Test 1 for the Reprompt key (MaReprompt, DEVELOPMENT.md §232).

    python3 scripts/test_reprompt.py

Marko, 4.11.2026: a key with "re" and a small magic symbol; it opens a window for an instruction to
Claude, and the text in the field is changed directly.
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


rp = code(SRC / "dictate/MaReprompt.kt")
act = code(SRC / "dictate/MaRepromptActivity.kt")
order = code(SRC / "dictate/MaFeatureOrder.kt")
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
rows_screen = code(SRC / "app/settings/dictate/MaRowsScreen.kt")
ime = code(SRC / "FlorisImeService.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
mig = code(SRC / "dictate/data/prefs/DictateLegacyMigrator.kt")
app = code(SRC / "FlorisApplication.kt")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

# The key
check("the key exists", 'REPROMPT("reprompt", "Reprompt")' in order)
check("drawn as re + a small sparkle", 'text = "re"' in row and "Icons.Default.AutoAwesome" in row)
check("in the rows editor too", 'MaFeatureKey.REPROMPT -> letters("re\\u2726")' in rows_screen)
check("it remembers the field and opens the window", "MaReprompt.open(context, pkg, info.base.fieldId" in row)
# 4.11.2026: an empty field is the prompt mode, not a refusal.
check("an empty field opens the prompt mode", "MaReprompt.open(context, pkg, info.base.fieldId, write = empty)" in row
      and '"Reprompt: write something first"' not in row)
check("the field decides when it is back", "val write = content.text.isBlank() && content.selectedText.isBlank()" in ime
      and "MaReprompt.prompt(words, write, screen)" in ime)
check("write mode inserts, reshape mode replaces", "requiresSelection = !write," in rp)
check("write mode: the request fenced, its language named",
      "<request>" in rp and "MaRewordPrompt.languageOf(w)" in rp)
check("the window asks the right question", "The field is empty: what message should Claude write?" in act)

# The window
check("the window is declared", "dev.patrickgold.florisboard.dictate.MaRepromptActivity" in manifest)
check("with the keyboard open on it", "stateAlwaysVisible" in manifest)
check("its own task, so closing returns to his app", 'android:taskAffinity=""' in manifest)
check("Apply and Enter submit", "onDone = { apply(text) }" in act and 'Text("Apply")' in act)
check("his last instructions are one tap", "clickable { apply(r) }" in act)
check("Back or Cancel rewrites nothing", "if (!submitted) MaReprompt.cancel()" in act)

# Back in the field
check("runs when the field comes back", "MaReprompt.takeFor(info.packageName, info.fieldId)" in ime)
check("through the Ctrl+P / Ctrl+F path, with Ctrl+F's model, sent bare",
      "MaReprompt.prompt(words, write, screen), model = MaFlow.model(), allowGrowth = true, bare = true" in ime)
# Mac emulator, build 440: "make it shorter" came back only proofread. Sent bare, no proofreading system prompt.
check("bare skips the rewording system prompt", "requestRewordRaw(MaReprompt.message(raw, input), modelOverride = model" in ctrl)
check("his text fenced, its language kept", "<text>" in rp and "MaRewordPrompt.languageOf(text)" in rp)
check("told not to just proofread", "Do not just proofread." in rp)
check("buttons before the list, above the navigation bar",
      act.find('Text("Apply")') < act.find("for (r in recent)") and "navigationBarsPadding()" in act)
check("the length guard is off only for it", "val grew = !allowGrowth && text.length >" in ctrl)
check("never its own window's field", "packageName == ownPackage) return null" in rp)
check("only the same field, only within a minute",
      "t.packageName != packageName || t.fieldId != fieldId" in rp and "WAIT_MS = 60_000L" in rp)

# On his keyboard
check("put on his rows once, after E|H", "fun addRepromptKeyIfNeeded()" in mig and
      "listOf(MaFeatureKey.RECORD_EN_HR, MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_HR)" in mig)
check("runs at start", "DictateLegacyMigrator.addRepromptKeyIfNeeded()" in app)


# The memory of instructions (MaReprompt.withRecent)
def with_recent(lst, w, mx=6):
    return ([w] + [x for x in lst if x.lower() != w.lower()])[:mx]


check("newest first, no duplicates", with_recent(["a", "b"], "B") == ["B", "a"])
check("at most six", len(with_recent([str(i) for i in range(10)], "x")) == 6)
check("same rule in the code", "(listOf(words) + list.filter { !it.equals(words, ignoreCase = true) }).take(RECENT_MAX)" in rp)


# takeFor, walked
class R:
    def __init__(self):
        self.target = None; self.instr = None; self.own = "com.mantraproductions.tttlight"

    def take(self, pkg, fid, now):
        if self.target is None or self.instr is None: return None
        if pkg is None or pkg == self.own: return None
        t, i = self.target, self.instr
        self.target = self.instr = None
        if now - t[2] > 60000: return None
        if t[0] != pkg or t[1] != fid: return None
        return i


r = R(); r.target = ("com.whatsapp", 7, 0); r.instr = "shorter"
check("own window first: waits", r.take(r.own, 1, 10) is None and r.instr == "shorter")
check("his field back: runs", r.take("com.whatsapp", 7, 20) == "shorter")
r.target = ("com.whatsapp", 7, 0); r.instr = "shorter"
check("another app first: dropped", r.take("com.android.chrome", 3, 20) is None and r.instr is None)
r.target = ("com.whatsapp", 7, 0); r.instr = "shorter"
check("too late: dropped", r.take("com.whatsapp", 7, 70000) is None)

# 5.11.2026: a press records the instruction in English; the window is the long press.
check("a press records, in English", "if (rec == null) MaLanguage.set(context, MaLanguage.EN)" in row
      and "DictateController.onMicClick(context, reprompt = true, engine = MaProviders.ENGINE_ASSEMBLYAI)" in row)
check("the long press opens the typing window", "onLongClick = {" in row[row.find("MaFeatureKey.REPROMPT -> {"):row.find("MaFeatureKey.RECORD -> {")])
check("set at every start", "repromptRecording = reprompt" in ctrl)
check("the transcript is an instruction, not typed", "if (repromptRecording && !live) {" in ctrl
      and "MaReprompt.prompt(words, write, screen), model = MaFlow.model()," in ctrl)
check("empty field writes, text is reshaped", "val write = outSink.fullText().isBlank() && outSink.selectedText().isBlank()" in ctrl)
check("a streamed preview is taken back", "outSink.clearDictationPreview(realtimeShown.toString())" in ctrl)
check("kept in Reprompt's list", "MaReprompt.rememberWords(words)" in ctrl)
check("the record outline, light red while it records", "ring = if (recordingThis) MaRecordOutlineLive else MaRecordOutline," in row)
check("the E key stays dark while Reprompt records", "!DictateController.isRepromptRecording()" in row)

print(f"reprompt, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
