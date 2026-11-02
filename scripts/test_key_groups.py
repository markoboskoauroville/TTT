#!/usr/bin/env python3
"""
Test 1 for the picker's grouping: the mechanism, alone.

Parses the Kotlin rather than trusting it, because the claims are about a list of forty-six buttons
and the failure mode is a key quietly missing from every section.

    python3 scripts/test_key_groups.py
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


order = (SRC / "dictate/MaFeatureOrder.kt").read_text()

# Every key, and every group, read from the source.
enum_body = order[: order.index("enum class MaFeatureGroup")]
keys = re.findall(r"^\s{4}([A-Z][A-Z_0-9]*)\(\"", enum_body, re.M)
groups = re.findall(r"^\s{4}([A-Z][A-Z_0-9]*)\(\"", order[order.index("enum class MaFeatureGroup"):], re.M)

# Not a fixed count. It was 30, then undo and redo were added and the check failed for the only
# reason a catalogue ever changes — a key was added. A number that has to be edited on every
# addition is a number that gets edited without being read. What matters is that there ARE keys and
# that every one of them has a section, which is asserted below.
check("keys were found at all", len(keys) >= 30, f"{len(keys)} keys")
check("nine sections", len(groups) == 9, f"{len(groups)}: {groups}")

# The mapping, parsed out of the exhaustive `when`.
mapping_src = order[order.index("val MaFeatureKey.group"):]
assigned: dict[str, str] = {}
current: list[str] = []
for line in mapping_src.splitlines():
    key = re.match(r"\s*MaFeatureKey\.([A-Z_0-9]+),\s*$", line)
    arrow = re.match(r"\s*->\s*MaFeatureGroup\.([A-Z_]+)\s*$", line)
    if key:
        current.append(key.group(1))
    elif arrow:
        for k in current:
            assigned[k] = arrow.group(1)
        current = []

# EVERY key has a section, and no key has two.
for k in keys:
    check(f"{k} has a section", k in assigned, "falls through into nothing")
check("no key is assigned twice", len(assigned) == len(set(assigned)), "duplicate branch")
check("nothing assigned that is not a key", set(assigned) <= set(keys), str(set(assigned) - set(keys)))

# Every section is used. A heading nobody belongs to is a promise with nothing under it.
used = set(assigned.values()) | {"BUCKETS", "MACROS"}
for g in groups:
    check(f"section {g} has keys", g in used, "empty heading")

# The thing he asked for, stated as a test: A is with the buckets.
check("A is in the buckets", assigned.get("AUTO_BUCKET") == "BUCKETS", assigned.get("AUTO_BUCKET", "nowhere"))
check("the bin is in the buckets", assigned.get("CLIP_CLEAR") == "BUCKETS", assigned.get("CLIP_CLEAR", "nowhere"))

# A few groupings that would be wrong if somebody moved them by feel later.
for key, group in (
    ("ALL_PASTE", "CLIPBOARD"),
    ("ALL_CLEAR", "CLIPBOARD"),
    ("ZONE_1", "KEYBOARD"),
    ("ZONE_3", "KEYBOARD"),
    ("MIC", "DICTATION"),
    ("READER", "READING"),
    ("NEXT_FIELD", "MOVING"),
    ("DUMP", "TOOLS"),
):
    check(f"{key} is in {group}", assigned.get(key) == group, assigned.get(key, "nowhere"))

# ---------------------------------------------------------------- the order within the buckets
rows = (SRC / "dictate/MaRows.kt").read_text()


def bucket_order(kind, slot=0):
    """The port of MaRows.bucketOrder."""
    if kind == "auto":
        return 0
    if kind == "clip":
        return slot
    if kind == "bin":
        return 11
    return 0


seq = [("auto", 0)] + [("clip", n) for n in range(1, 11)] + [("bin", 0)]
sorted_seq = sorted(seq, key=lambda x: bucket_order(*x))
check("A first, then C1 to C10, then the bin", sorted_seq == seq, str(sorted_seq))

check("the grouped catalogue exists", "fun catalogueGrouped()" in rows, "the screen has nothing to call")
check("empty sections are dropped", "if (buttons.isEmpty()) null" in rows, "a heading could stand alone")

screen = (SRC / "app/settings/dictate/MaRowsScreen.kt").read_text()
check("the picker uses it", "MaRows.catalogueGrouped()" in screen, "still grouping on the screen")
# Comments stripped first. The catch-all is named in the comment explaining why it is gone, and a
# check that reads comments is a check that fires on its own documentation.
screen_code = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "", screen, flags=re.S), flags=re.M)
check('the "Keys" catch-all is gone', '"Keys"' not in screen_code, "twenty-six things under one heading")

# ---------------------------------------------------------------- one face, one key
#
# The volume-keys key wore VolumeUp, which is the READER key's glyph. Two keys with one picture is
# the fault the design language names first, and he found it by looking at the row.
picker = (SRC / "app/settings/dictate/MaRowsScreen.kt").read_text()
reader_block = picker[picker.index("MaFeatureKey.READER ->"):][:200]
volume_block = picker[picker.index("MaFeatureKey.VOLUME_KEYS ->"):][:600]
check("the reader keeps the speaker", "VolumeUp" in reader_block, "the reader lost its own glyph")
check("the volume key does not wear a speaker", "VolumeUp" not in volume_block, "two keys, one face")
check("the volume key draws the rocker", "RoundedCornerShape(7.dp)" in volume_block, "no rocker in the picker")

row_kt = (SRC / "dictate/ui/MaFeatureRow.kt").read_text()
row_code_only = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "", row_kt, flags=re.S), flags=re.M)
check("the key draws the rocker too", "MaVolumeRocker(tint = fg)" in row_code_only, "picker and key disagree")
check("no speaker left on the volume key", "VolumeUp" not in row_code_only, "the speaker survived in the row")

# ---------------------------------------------------------------- every way of stopping stops all
#
# Three ways out now: the swipe, the dashboard's STOP, and the reader key. Two of them that stopped
# different amounts would be worse than one.
dash = re.sub(r"^\s*//.*$", "", (SRC / "dictate/ui/MaReaderDashboard.kt").read_text(), flags=re.M)
caption = re.sub(r"^\s*//.*$", "", (SRC / "dictate/ui/MaSubtitleRow.kt").read_text(), flags=re.M)
for name, body in (("the dashboard STOP", dash), ("the swipe", caption)):
    check(f"{name} stops the voice", "MaReader.stop()" in body, "leaves the reading running")
    check(f"{name} cancels a transcription", "DictateController.cancelTranscription()" in body,
          "leaves work in flight with nothing on screen to stop it")

# ---------------------------------------------------------------- the ring convention
#
# He photographed the three zone keys and said they were not following the convention: everywhere
# else in this app the RING carries the state and the GLYPH holds still. These three were written
# before that convention existed and had it backwards — monochrome ring, green glyph.
row_conv = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "", (SRC / "dictate/ui/MaFeatureRow.kt").read_text(), flags=re.S), flags=re.M)
for zone in ("zone1", "zone2", "zone3"):
    check(f"{zone}'s ring carries the state",
          f"ring = if ({zone}) onGreen else MaSwitcherRingOff" in row_conv, "still a monochrome ring")
    check(f"{zone}'s glyph holds still", f"tint = if ({zone}) onGreen else fg" not in row_conv,
          "the picture changes as well as the ring")
check("the off ring is a colour, not an absence", "val MaSwitcherRingOff" in
      (SRC / "dictate/ui/LegacyDictateLayout.kt").read_text(),
      "the border would appear and disappear, and the row would flicker")

# ---------------------------------------------------------------- the copy row he asked for
rows_src = (SRC / "dictate/MaRows.kt").read_text()
block = rows_src[rows_src.index("fun defaultCopyRow"):]
block = block[:block.index(").map")]
# NOT `order` — that name already holds MaFeatureOrder.kt further up this file, and reusing it here
# silently broke two checks about SEND and RECORD which then failed for a reason that had nothing to
# do with them. **A shadowed variable in a test file is a false failure pointing at innocent code.**
copy_order = [l.strip().rstrip(",").replace("MaFeatureKey.", "")
              for l in block.splitlines() if l.strip().startswith("MaFeatureKey.")]
check("the copy row reads as he asked",
      copy_order == ["SELECT_ALL", "PASTE", "COPY", "CUT", "CLIP_HISTORY",
                     "ALL_PASTE", "ALL_CLEAR", "PIN"], str(copy_order))
check("copy is on it", "COPY" in copy_order, "the one operation of the four that was missing")
check("the pin is last", copy_order[-1] == "PIN",
      "the key that stops Android collapsing the keyboard is one row away from the keys it enables")
check("transcription history is off the copy row", "HISTORY" not in copy_order,
      "two ideas of history one key apart")
check("but still in the catalogue", 'HISTORY("history"' in (SRC / "dictate/MaFeatureOrder.kt").read_text(),
      "removed from the app rather than from the row")

# ---------------------------------------------------------------- copy without a connection
#
# Selecting text in another app makes Android collapse the keyboard, and the input connection goes
# with it. Pinning keeps the keyboard on screen and does not give the connection back — so the copy
# key reported "either selection state is invalid or an error occurred within the input connection",
# which was accurate and useless. The selection is still on the screen; the accessibility service can
# ask the view that owns it to copy it.
editor = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "",
                (SRC / "ime/editor/EditorInstance.kt").read_text(), flags=re.S), flags=re.M)
svc = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "",
             (SRC / "dictate/overlay/DictateAccessibilityService.kt").read_text(), flags=re.S), flags=re.M)

check("the service can copy a screen selection", "fun copySelectionOnScreen" in svc, "no second route")
check("and cut one", "fun cutSelectionOnScreen" in svc, "cut still dies with the connection")
check("it uses the view's own action", "ACTION_COPY" in svc,
      "reading the text and slicing by index gets a formatted selection wrong")
check("a cursor is not a selection", "start == end) return false" in svc,
      "copying a cursor would look like success and leave the old clipboard")

check("copy falls back to it", "DictateAccessibilityService.copySelectionOnScreen()" in editor, "no fallback")
check("cut falls back to it", "DictateAccessibilityService.cutSelectionOnScreen()" in editor, "no fallback")
# find, not index. The sabotage run THREW here rather than failing — the count never printed and the
# other results were lost. Exactly the fault fixed in §162 and made again: **a check that raises is
# not a check that fails.** Guarded, and the guard is also the assertion that both are present.
conn_at = editor.find("getSelectedText(0)")
fallback_at = editor.find("copySelectionOnScreen()")
check("the connection is tried FIRST", conn_at >= 0 and fallback_at > conn_at,
      "the fallback is missing, or it runs before the fast route that needs no permission")
check("the old message is gone", "an error occurred within the input connection" not in editor,
      "a message describing an input connection to somebody holding a phone")

# ---------------------------------------------------------------- the history mark
#
# Two keys open a history: transcription and clipboard. They wore different pictures, and only one of
# them said "history" — the other was ContentPasteGo, whose arrow points RIGHT and means GO. The
# subject changes, the mark does not.
row_hist = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "",
                  (SRC / "dictate/ui/MaFeatureRow.kt").read_text(), flags=re.S), flags=re.M)
picker_hist = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "",
                     (SRC / "app/settings/dictate/MaRowsScreen.kt").read_text(), flags=re.S), flags=re.M)
check("the mark is composed once", "fun MaHistoryGlyph(" in row_hist, "each key draws its own badge")
check("the clipboard history key wears it",
      "MaHistoryGlyph(base = Icons.Default.ContentPaste" in row_hist, "still an arrow pointing right")
check("the picker shows the same glyph",
      "MaHistoryGlyph(base = Icons.Default.ContentPaste" in picker_hist,
      "the row and the keyboard would disagree")
check("the go-arrow is gone from both", "ContentPasteGo" not in row_hist and "ContentPasteGo" not in picker_hist,
      "an arrow meaning GO on a key that opens a past")
check("the mark is the History arrow", "Icons.Default.History" in row_hist,
      "a second drawing of the same idea")

# ---------------------------------------------------------------- the group is what it IS
#
# `Sg` landed under "Reading aloud" for one build. It reads as a reading key — "suggestion" is a
# word, the reader deals in words — and it is nothing of the kind: it shows and hides a row of the
# keyboard, exactly like n, k, c and F1 to F6. He found it there and said so.
#
# **The group answers what a key is a part of, not what its name sounds like.**
_order = (SRC / "dictate/MaFeatureOrder.kt").read_text()
_grp = _order[_order.index("val MaFeatureKey.group"):]


def group_of(key):
    """Which -> MaFeatureGroup.X branch a key falls into."""
    i = _grp.find(f"MaFeatureKey.{key},")
    if i < 0:
        return None
    m = re.search(r"-> MaFeatureGroup\.(\w+)", _grp[i:])
    return m.group(1) if m else None


check("Sg is a keyboard key", group_of("SUGGESTIONS") == "KEYBOARD", group_of("SUGGESTIONS"))
check("and sits with the other row switches", group_of("SUGGESTIONS") == group_of("ZONE_1"),
      "it shows and hides a row, exactly as the zone keys do")
check("F keys are there too", group_of("ROW_1") == "KEYBOARD", "the row switches would be split")
check("the reader row switch stays with reading", group_of("READER_ROW") == "READING",
      "that one IS the reader's, and its commands are beside it")

# NOTE, PAID FOR ONCE: this block was appended to the END of the file, after the exit, and reported
# a happy total having run none of it. The count went up by two for an unrelated reason and hid it.
# **Anything added to a test goes ABOVE the summary line**, and the summary is the last thing in the
# file.

# ---------------------------------------------------------------- the two new keys
#
# Send and Record, added 22.8.2026. Checked by name because both are keys that DO something on
# another app's screen, and the failure mode for those is silence.
row_src = (SRC / "dictate/ui/MaFeatureRow.kt").read_text()
row_code = re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "", row_src, flags=re.S), flags=re.M)

check("Send is in the catalogue", 'SEND("send"' in order, "not offered in the picker")
check("Record is in the catalogue", 'RECORD("record"' in order, "not offered in the picker")
check("both are dictation", assigned.get("SEND") == "DICTATION" and assigned.get("RECORD") == "DICTATION",
      f"SEND={assigned.get('SEND')} RECORD={assigned.get('RECORD')}")

# Send must go through the magic finger's own term, not a hardcoded word. Otherwise the key and the
# volume key would disagree the moment he changes the term.
check("Send uses the configured term", "MaMagicTargets.pressSend()" in row_code, "hardcoded, or its own path")
check("Send says when it finds nothing", "No Send button found" in row_src, "silent failure")

# Record must be the same call as the volume key and the bar's mic, or three routes drift apart.
check("Record uses the one entry point", "DictateController.onMicClick(context)" in row_code, "its own recording path")
check("Record wears the live ring", "ring = if (recording) onGreen else MaRecordRing" in row_code, "no sign it is running")
# The rule (2.11.2026): every key that records his voice is outlined in record red at rest.
check("Record keys outlined red at rest", row_code.count("else MaRecordRing") >= 2, "a record key without the red outline")

# One red, named once. Two reds mixed by eye are the same colour to whoever wrote them.
meter_src = (SRC / "dictate/ui/MaRecordMeter.kt").read_text()
check("the recording red is named once", "val MaRecordRed = Color(0xFF9B3B33)" in meter_src, "not shared")
check("the lamp uses it", "MaRecordLampOn = MaRecordRed" in meter_src, "a second red")
check("the key uses it", "MaRecordRed" in row_code, "the dot is some other red")

print(f"key groups, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
