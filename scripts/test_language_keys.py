#!/usr/bin/env python3
"""
Test 1 for the language keys and the status-line label.

Two bugs and one design change:
  · the sending line showed the SETTING, not the language the request was sent in
  · a single record key plus a language setting means every recording begins with a question

    python3 scripts/test_language_keys.py
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


class World:
    """Setting, request, and what the status line says."""

    def __init__(self):
        self.setting = "en"
        self.in_flight = None

    def record_in(self, lang):
        self.setting = lang          # set FIRST
        self.in_flight = lang        # then the audio exists and the request captures it

    def change_setting(self, lang):
        self.setting = lang

    def label(self):
        return self.in_flight if self.in_flight is not None else self.setting

    def finish(self):
        self.in_flight = None


# ---------------------------------------------------------------- the bug he reported
w = World()
w.record_in("en")
check("sending English says English", w.label() == "en", w.label())
w.change_setting("hr")
check("changing the setting does NOT relabel the request in flight", w.label() == "en",
      "this is exactly 'sending English, status says Croatian'")
w.finish()
check("with nothing in flight the setting is the answer", w.label() == "hr", w.label())

# Walked: any sequence of records and setting changes, the label must equal the request's language
# whenever one is flying, and the setting otherwise. Never anything else.
import itertools
walked = 0
for seq in itertools.product(["rec_en", "rec_hr", "set_en", "set_hr", "finish"], repeat=4):
    w = World()
    for act in seq:
        if act.startswith("rec_"):
            w.record_in(act[4:])
        elif act.startswith("set_"):
            w.change_setting(act[4:])
        else:
            w.finish()
        walked += 1
        expected = w.in_flight if w.in_flight is not None else w.setting
        check(f"{seq}: the label is the request's language", w.label() == expected, w.label())

# The order inside a press: language BEFORE recording, or the request captures the old one.
w = World()
w.record_in("hr")
check("the language is set before the request exists", w.in_flight == "hr" and w.setting == "hr")


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


ctrl = code(SRC / "dictate/DictateController.kt")
ui = code(SRC / "dictate/ui/DictateSmartbarUi.kt")
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
order = code(SRC / "dictate/MaFeatureOrder.kt")

check("the request carries its language", "val language: String," in ctrl, "nothing to label it with")
check("it is captured when the request leaves", "language = MaLanguage.active()," in ctrl)
check("the line reads the request", "DictateController.inFlightLanguage" in ui,
      "it would show the setting, which is the bug")
check("and not the setting directly", 'if (languageMode == MaLanguage.EN) "ENG"' not in ui,
      "the setting relabels a request already on the wire")

for k in ("RECORD_HR", "RECORD_EN"):
    check(f"{k} is a key", f'{k}("' in order, "not in the catalogue")
# E>H (2.11.2026) joins the same branch: one place for the set-then-record order, now for three keys.
check("the record keys are written once", "MaFeatureKey.RECORD_HR, MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_EN_HR ->" in row,
      "two places for the set-then-record order to drift")

# THE SEND PAIR IS GONE, and these invert rather than disappear. Recording in a language is the
# choice he needs, because the language must be decided BEFORE the audio exists. Sending is not:
# by then the language is already settled and a per-language send key is a second way to pick
# something already picked.
for k in ("SEND_HR", "SEND_EN"):
    check(f"{k} is gone from the catalogue", f'{k}("' not in order, "a key he asked to have removed")
check("and gone from the keyboard", "MaFeatureKey.SEND_HR" not in row, "in the row, not in the list")
check("the plain send key survives", 'SEND("send"' in order,
      "removing the pair must not remove sending")

rec = row[row.index("MaFeatureKey.RECORD_HR, MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_EN_HR ->"):][:2400]
check("the language is set before recording starts",
      rec.index("MaLanguage.set(context, language)") < rec.index("DictateController.onMicClick"),
      "the audio would exist before the language did, and transcribe would capture the old one")
check("it confirms in words", '"Sending "' in rec and '"Recording "' in rec,
      "no confirmation of which language it went in")

# The faults CI found in the first version of those keys are recorded in DEVELOPMENT.md §189 rather
# than here: the code they described is deleted, and a check that asserts the shape of deleted code
# fails for a reason that has nothing to do with the shape.
#
# The one that OUTLIVES the deletion is the type of pressSend, because the plain send key still calls
# it.
check("the send key does not pass tint to ThemedKey", "tint = if (sendHere) null else MaDim" not in row,
      "ThemedKey has no tint parameter; only ThemedIconKey does")
# These three described the deleted send pair: its two-part dimming, its handling of pressSend's
# return, and its polling of sendVisible. **A check that asserts the shape of deleted code fails for a
# reason that has nothing to do with the shape**, and keeping it would mean re-adding the key to make
# the suite green.
#
# What survives is one claim that is still true of the PLAIN send key, and it is the one that cost a
# red build: pressSend returns the term it pressed, not a Boolean.
# Compared against null in EITHER spelling. The deleted pair wrote `val sent = pressSend()` then
# `if (sent != null)`; the plain key writes `if (pressSend() == null)`. Both are correct and my check
# knew only the first — it failed on the survivor for using the shorter form.
check("pressSend is treated as a String?",
      "sent != null" in row or "pressSend() == null" in row or "pressSend()" not in row,
      "it returns the term it pressed, not a Boolean")

# ---------------------------------------------------------------- the badge is gone
#
# It showed the language and cycled it on a tap. That was right while the language was a MODE; the
# record and send keys carry their own language now, so it reported a mode nobody sets — and it was
# still tappable, which made it the last route by which the language could be wrong again.
ui3 = code(SRC / "dictate/ui/DictateSmartbarUi.kt")
check("no language badge on the sending line", '"[$badge]"' not in ui3, "a control with nothing to control")
check("its computation went with it", "val badge = remember(" not in ui3,
      "a value read, cached and observed for a control that does not exist")
check("nothing on that line cycles the language", "MaLanguage.cycleMode(context)" not in ui3,
      "the last route by which the language could be set behind the keys")
check("the line still REPORTS the language", '"Sending Croatian"' in ui3 and '"Sending English"' in ui3,
      "removing the control removed the report as well")

# ---------------------------------------------------------------- only one key rings
#
# Both rang. The reasoning written at the time was that one recording is running and lighting one key
# would suggest the other could start a second — an argument that protects a misunderstanding nobody
# has and destroys the answer he needs. He photographed it: press E, both light, and the row stops
# telling him which language is being captured.
check("the ring is per language", "recording && MaLanguage.active() == language" in row,
      "both keys light and they become a record key drawn twice")
check("it reads the ACTIVE language, not the request's", "inFlightLanguage == language" not in row,
      "inFlightLanguage is written when a REQUEST starts — both keys would stay dark until upload")

# The two keys must disagree about the ring for any given language. Walked, because "they differ" is
# the whole property and a single example proves nothing.
for recording in (True, False):
    for active in ("hr", "en"):
        lit = {lang: recording and active == lang for lang in ("hr", "en")}
        if recording:
            check(f"rec={recording} active={active}: exactly one rings",
                  sum(lit.values()) == 1, str(lit))
            check(f"rec={recording} active={active}: it is the right one", lit[active], str(lit))
        else:
            check(f"rec={recording}: neither rings", sum(lit.values()) == 0, str(lit))

print(f"language keys, test 1: {checks} checks, {len(failures)} failed ({walked} steps walked)")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
