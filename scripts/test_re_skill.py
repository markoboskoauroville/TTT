#!/usr/bin/env python3
"""
Test 1 for RE Magic (MaReSkill, 9.11.2026): AssemblyAI only, the skill read fresh, the icon and the editor
in settings, the learning loop that only reads.

    python3 scripts/test_re_skill.py
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


skill = code(SRC / "dictate/MaReSkill.kt")
rp = code(SRC / "dictate/MaReprompt.kt")
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
ime = code(SRC / "FlorisImeService.kt")
acc = code(SRC / "dictate/overlay/DictateAccessibilityService.kt")
order = code(SRC / "app/settings/MaSettingsOrder.kt")
order_screen = code(SRC / "app/settings/MaSettingsOrderScreen.kt")
routes = code(SRC / "app/Routes.kt")
prov = code(SRC / "dictate/MaProviders.kt")
screen = code(SRC / "app/settings/dictate/MaReScreen.kt")
icon = code(SRC / "app/settings/MaReIcon.kt")

# AssemblyAI only
check("RE records with AssemblyAI forced", "reprompt = true, engine = MaProviders.ENGINE_ASSEMBLYAI" in row)
check("no on-device fallback for a forced AssemblyAI", "if (engine == ENGINE_ASSEMBLYAI) return null" in prov)
# The skill is read fresh and goes in front of both prompts
check("the instruction carries the skill", 'MaReSkill.preamble() + "Carry out this' in rp)
check("the write mode carries the skill", 'MaReSkill.preamble() + "Write the message the user asks for' in rp)
check("read from disk each time", "fun read(): String" in skill and "readText()" in skill)
check("in the TTTmini Documents folder", "File(MaVault.dir(), SKILL_FILE)" in skill and 'SKILL_FILE = "re_ttt_skill.md"' in skill)
check("not shipped in the app", not list((ROOT / "app/src/main").rglob("re_ttt_skill*")))
# Settings
check("settings entry", 'RE_MAGIC("re_magic", "RE Magic"' in order and "MaSettingsEntry.RE_MAGIC," in order)
check("same icon as the key: r, e, sparkle", "AutoAwesome" in icon and "M3.5,20.5" in icon and "MaReIcon" in order_screen)
check("route and screen", "MaRe::class" in routes and "MaSettingsEntry.RE_MAGIC -> Routes.Settings.MaRe" in order_screen)
check("editor: save, reload, reset, switch", all(w in screen for w in ('Text("Save")', 'Text("Reload")', 'Text("Reset rules")', "maReLearn")))
# The loop: reads only
check("accessibility hook only reads", "noteSendClickForSkill" in acc and "MaReSkill.onSendClicked" in acc)
hook = acc[acc.find("private fun noteSendClickForSkill"):acc.find("private fun focusedEditableNode")]
check("the hook never clicks or acts",
      all(w not in hook for w in ("performAction", "performClick", "performEnter", "dispatchGesture")), hook[:200])
check("IME watches only while a draft waits", "MaReSkill.pending()" in ime and "noteFieldText" in ime)
check("the draft is kept when RE commits", "MaReSkill.onComposed(" in ctrl)
check("learning asks a model through one path", "suspend fun askModel" in ctrl and "DictateController.askModel" in skill)
check("a switch, on by default", 'val maReLearn = boolean(key = "dictate__ma_re_learn", default = true)' in code(SRC / "app/AppPrefs.kt"))
check("only the newest draft, only its app", "d.pkg != pkg" in skill and "draft = null" in skill)
check("no key shape in the code", not re.search(r"(sk-ant|AIza|[0-9a-f]{32})", skill + screen))


# Port of the pure rules
def key(r):
    return "".join(c for c in r.lower() if c.isalnum())


def merge(rules, rule):
    r = rule.strip().removeprefix("- ").strip()
    if not r or r.upper() == "NONE":
        return rules
    k = key(r)
    if any(key(x) == k or (len(k) > 20 and k in key(x)) for x in rules):
        return rules
    return rules + [r]


SEND = re.compile(r"^(send|sent|pošalji|posalji|pošalji poruku|send message|submit|post|reply|odgovori|objavi|pošalji odgovor|enviar|senden|envoyer|invia)$", re.I)
check("merge adds", merge([], "Cut 'Dear'") == ["Cut 'Dear'"])
check("merge dedupes", merge(["Cut Dear"], "- cut dear") == ["Cut Dear"])
check("NONE adds nothing", merge(["a"], "none") == ["a"])
check("send words", all(SEND.match(w) for w in ("Send", "Pošalji", "SEND")) and not SEND.match("Send to everyone"))
mac = Path.home() / "Downloads/API/re_ttt_skill.md"
check("the Mac skill has section 7", (not mac.exists()) or "## 7. Corrections log → rules" in mac.read_text())

print(f"re skill, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
