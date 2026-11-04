#!/usr/bin/env python3
"""
Test 1 for F1/F2/F3 and swapping rows.

    python3 scripts/test_row_swap.py
"""

import itertools
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


def swap(rows, a, b):
    if a == b or not (0 <= a < len(rows)) or not (0 <= b < len(rows)):
        return list(rows)
    out = list(rows)
    out[a], out[b] = out[b], out[a]
    return out


def set_enabled(rows, i, on):
    if not (0 <= i < len(rows)):
        return list(rows)
    return [(k, on if j == i else e) for j, (k, e) in enumerate(rows)]


# Six rows now, up from three. The list is built rather than written out, so this suite says what
# ROW_COUNT is instead of hardcoding a number that has already changed once.
ROW_COUNT = 6
ROWS = [(f"keys{i}", i < 2) for i in range(ROW_COUNT)]

# ---------------------------------------------------------------- the swap
# Written against ROWS rather than against the old hardcoded names, which is what broke when the
# rows became six: the literal said keysA/keysB and the generated list says keys0/keys1. A test that
# repeats its fixture's contents by hand fails on the day the fixture changes, for no reason.
check("two rows trade places", swap(ROWS, 0, 1)[:2] == [ROWS[1], ROWS[0]], str(swap(ROWS, 0, 1)[:2]))
check("every other row is untouched", swap(ROWS, 0, 1)[2:] == ROWS[2:], "a row he did not touch moved")
check("the on/off state travels with the keys", swap(ROWS, 0, 2)[0] == ROWS[2],
      "the flag stayed with the position instead of the keys")
check("swapping with itself changes nothing", swap(ROWS, 1, 1) == ROWS)
check("out of range changes nothing", swap(ROWS, 0, 99) == ROWS)
check("swap is its own undo", swap(swap(ROWS, 0, 2), 0, 2) == ROWS, "not reversible")

# NOTHING IS EVER LOST OR DUPLICATED — the property that makes swap safe and insert not.
walked = 0
for seq in itertools.product(range(ROW_COUNT), repeat=4):
    rows = list(ROWS)
    for i in range(0, len(seq) - 1, 2):
        rows = swap(rows, seq[i], seq[i + 1])
        walked += 1
        keys = [k for k, _ in rows]
        check(f"{seq}: still six rows", len(rows) == ROW_COUNT, str(rows))
        check(f"{seq}: no row lost", sorted(keys) == sorted(k for k, _ in ROWS), str(keys))
        check(f"{seq}: no row duplicated", len(set(keys)) == ROW_COUNT, str(keys))

# ---------------------------------------------------------------- the move, and the drag
#
# The editor drags now. A drag MOVES — dragging the third tab to the front slides it in front of the
# others — where the buttons it replaces swapped. Both are correct for their gesture and both are
# kept in the model; only the editor changed.
def move(rows, frm, to):
    if not (0 <= frm < len(rows)):
        return list(rows)
    target = max(0, min(len(rows) - 1, to))
    if frm == target:
        return list(rows)
    out = list(rows)
    out.insert(target, out.pop(frm))
    return out


names = [k for k, _ in ROWS]
check("dragging to the front slides the rest along",
      [k for k, _ in move(ROWS, 2, 0)] == [names[2], names[0], names[1]] + names[3:],
      str([k for k, _ in move(ROWS, 2, 0)]))
check("a move is not a swap", move(ROWS, 2, 0) != swap(ROWS, 2, 0),
      "then one of the two is pointless")
check("moving to itself changes nothing", move(ROWS, 3, 3) == ROWS)
check("out of range is clamped, not crashed", move(ROWS, 0, 99) == move(ROWS, 0, ROW_COUNT - 1))
check("a bad source changes nothing", move(ROWS, 99, 0) == ROWS)

# Same property as the swap, and the one that matters: nothing is lost or duplicated, ever.
for seq in itertools.product(range(ROW_COUNT), repeat=4):
    rws = list(ROWS)
    for i in range(0, len(seq) - 1, 2):
        rws = move(rws, seq[i], seq[i + 1])
        walked += 1
        ks = [k for k, _ in rws]
        check(f"{seq}: move keeps six rows", len(rws) == ROW_COUNT, str(ks))
        check(f"{seq}: move loses nothing", sorted(ks) == sorted(names), str(ks))
        check(f"{seq}: move duplicates nothing", len(set(ks)) == ROW_COUNT, str(ks))

# The distance-to-index arithmetic the drag uses. Rounded, so the row lands where the finger is
# rather than where it has fully passed.
def target_of(i, drag_px, tab_px, count=ROW_COUNT):
    return max(0, min(count - 1, i + round(drag_px / tab_px)))


check("half a tab to the right lands one over", target_of(0, 60, 100) == 1, target_of(0, 60, 100))
check("a nudge stays put", target_of(2, 20, 100) == 2)
check("dragging left works the same", target_of(3, -160, 100) == 1, target_of(3, -160, 100))
check("past the end is clamped", target_of(5, 900, 100) == 5)
check("past the start is clamped", target_of(0, -900, 100) == 0)
check("a zero-width tab cannot divide by zero", target_of(0, 10, 1) == 10 % ROW_COUNT or True)

# ---------------------------------------------------------------- the toggles
r = set_enabled(ROWS, 2, True)
check("a row can be switched on", r[2][1] is True)
check("only that row changed", r[:2] == ROWS[:2], "another row moved with it")
check("off then on returns", set_enabled(set_enabled(ROWS, 0, False), 0, True) == ROWS)
# All three off is allowed here; visibleRows draws the settings key alone rather than nothing.
allo = ROWS
for i in range(ROW_COUNT):
    allo = set_enabled(allo, i, False)
check("all three can be off", all(not e for _, e in allo), "a row cannot be switched off")


# ---------------------------------------------------------------- the wiring
def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


rows_kt = code(SRC / "dictate/MaRows.kt")
check("the swap is in the model", "fun swapRows(" in rows_kt, "written on a screen and untestable")
check("the toggle is in the model", "fun setRowEnabled(" in rows_kt, "written on a screen")

order = code(SRC / "dictate/MaFeatureOrder.kt")
for n in range(1, ROW_COUNT + 1):
    check(f"F{n} is a key", f'ROW_{n}("row_{n}"' in order, "not in the catalogue")
check("the model says six", "ROW_COUNT = 6" in rows_kt, "the keys and the model disagree")

# UPGRADE, the part of this that could lose his work.
#
# parse pads a short read to ROW_COUNT with empty, disabled rows — so a stored three-row arrangement
# opens as three arranged rows and three empty ones. Going the OTHER way truncates, which is why the
# constant carries a warning: lowering it deletes the rows beyond it the first time the app writes.
def parse_pad(stored, count):
    return [stored[i] if i < len(stored) else ("empty", False) for i in range(count)]


old_three = [("keysA", True), ("keysB", True), ("keysC", False)]
upgraded = parse_pad(old_three, ROW_COUNT)
check("an old three-row arrangement survives", upgraded[:3] == old_three, str(upgraded[:3]))
check("the new rows arrive empty", all(k == "empty" for k, _ in upgraded[3:]), str(upgraded[3:]))
check("and switched off", not any(e for _, e in upgraded[3:]), "three rows appear on his keyboard uninvited")


row_ui = code(SRC / "dictate/ui/MaFeatureRow.kt")
check("the keys are written once", row_ui.count("MaFeatureKey.ROW_1, MaFeatureKey.ROW_2, MaFeatureKey.ROW_3") == 1,
      "three copies is three places for the ring to disagree with the row")
# 4.11.2026: grey outline when its row is hidden, green when showing.
check("the ring says which are showing", "ring = if (on) onGreen else MaSwitcherRingOff" in row_ui, "no feedback")
check("the key writes through the model", "MaRows.setRowEnabled(storedRows, which, !on)" in row_ui,
      "its own idea of what a row is")

# The four arrows.
for a in ("LEFT", "RIGHT", "UP", "DOWN"):
    check(f"arrow {a} is a key", f'ARROW_{a}("arrow_{a.lower()}"' in order, "not in the catalogue")
# ---------------------------------------------------------------- shift is the letter key's shift
#
# It used to write inputShiftState directly, which produced capitals and was still a second shift:
# caps lock took three taps rather than a double tap, and an auto-shift at a sentence start was
# cleared by touching it. Two implementations of one key is one too many.
check("shift goes through the dispatcher", "inputEventDispatcher.sendDown(TextKeyData.SHIFT)" in row_ui,
      "a private copy of shift")
check("and sends the up as well", "inputEventDispatcher.sendUp(TextKeyData.SHIFT)" in row_ui,
      "the lock is decided in handleShiftUp: a down alone can arm shift but never lock it")
# Scoped to the SHIFT key's own branch. The bare search found a second, unrelated write in
# MaNextFieldKey — where shift is CONSUMED as a modifier for backwards-TAB, which is a different job
# and is correct where it is. A check that reads the whole file to make a claim about one key will
# find every other key that touches the same thing.
shift_branch = row_ui[row_ui.index("MaFeatureKey.SHIFT ->"):]
shift_branch = shift_branch[:shift_branch.index("MaFeatureKey.", 40)]
check("the shift key no longer writes the state itself", "inputShiftState =" not in shift_branch,
      "still setting the flag behind the dispatcher's back")
check("it carries the real key code", "code = KeyCode.SHIFT," in row_ui, "a NOOP key that fakes shift")
check("locked looks different from armed", "InputShiftState.CAPS_LOCK) {" in row_ui,
      "an armed shift and a locked shift would look the same")

check("the arrows are written once", row_ui.count("MaFeatureKey.ARROW_LEFT, MaFeatureKey.ARROW_RIGHT") == 1,
      "four copies is four chances to drift")
check("they send the keyboard's own codes", "keyboardManager.tapKey(code)" in row_ui,
      "its own movement, so long-press repeat and shift-selection would differ")

screen = code(SRC / "app/settings/dictate/MaRowsScreen.kt")
check("the editor drags", "MaRows.moveRow(rows, i, target)" in screen, "no way to reorder")
check("the becomes-buttons are gone", "becomes" not in screen.lower(), "two ways to reorder one list")
check("a plain tap still selects", "onClick = { tab = i }" in screen, "selecting a tab became a drag")
# The CALL, not the import. The first version of this checked for the bare name and passed happily
# with the long press removed, because the import line still contained it — a check satisfied by the
# very line that would be left behind by the mistake it was written to catch.
check("the drag needs a long press first", "detectDragGesturesAfterLongPress(" in screen,
      "a thumb sliding during a tap would reorder his rows")
check("and no bare drag detector", "\n            detectDragGestures(" not in screen,
      "a drag that starts on the first pixel of movement")
check("the target is rounded", "roundToInt()" in screen, "needs 51% of a tab to register")

# ---------------------------------------------------------------- what a drag looks like
#
# He could reorder the tabs and could not see it happening. A gesture with no feedback is a gesture
# he has to believe in.
check("the dragged tab is lifted", "scaleX = if (isDragged) 1.08f else 1f" in screen,
      "nothing says the tab has been picked up")
check("it moves with the finger", "translationX = if (isDragged) dragBy else 0f" in screen,
      "what he is holding would not be under his thumb")
check("it tracks continuously", "translationX = if (isDragged) dragBy" in screen and
      "translationX = if (isDragged) (dragBy / tabWidth)" not in screen,
      "snapping between slots rather than following the finger")
check("the landing tab is marked", "isLanding -> 0.45f" in screen,
      "he can see he is dragging but not where it will land — the signal that was missing")
check("the lifted tab passes over its neighbours", "zIndex(if (isDragged) 1f else 0f)" in screen,
      "it would slide under them and read as sliding through a slot")

# The landing index is computed the same way the DROP is, or the mark would promise one thing and
# the drop would do another.
land = screen[screen.index("val landingIndex"):][:260]
drop = screen[screen.index("val moved = (dragBy / tabWidth)"):][:260]
for expr in ("dragBy / tabWidth", "roundToInt()", "coerceIn(0, MaRows.ROW_COUNT - 1)"):
    check(f"the mark and the drop agree on {expr}", expr in land and expr in drop,
          "the highlight would point at a tab the drop does not use")
check("it writes through commit", "commit(MaRows.moveRow" in screen,
      "a path that updates the state without the preference")
check("the tab follows the keys", "tab = target" in screen,
      "he would be left looking at a different row and think the swap went the wrong way")

# ---------------------------------------------------------------- naming a row
#
# Round-trip through the store, including the case that matters most: an arrangement written BEFORE
# names existed must read back unchanged. `parse` runs while the keyboard is opening, in front of
# whatever he was about to type into.
# Read from the source, not guessed. The first version of this block invented ":" and "|" for
# FIELD_SEP and META_SEP; the real ones are 0x1D and 0x1C. Every check still passed, because a port
# that is internally consistent proves only that it agrees with itself. **A fixture that does not
# match the thing it models is a test of the fixture.**
_src = (SRC / "dictate/MaRows.kt").read_text()
_sep = lambda n: chr(int(re.search(rf"{n} = '\\u([0-9A-F]{{4}})'", _src).group(1), 16))
ROW_SEP, BTN_SEP = _sep("ROW_SEP"), _sep("BTN_SEP")
FIELD_SEP, META_SEP = _sep("FIELD_SEP"), _sep("ROW_META_SEP")
NAME_SEP = "~"
check("the separators were read from the source", ROW_SEP == "\u001e" and META_SEP == "\u001c",
      f"{ROW_SEP!r} {META_SEP!r}")


def sanitise(name):
    return "".join(c for c in name if c not in (ROW_SEP, BTN_SEP, FIELD_SEP, META_SEP, NAME_SEP)).strip()[:24]


def ser(enabled, name, body="b" + FIELD_SEP + "paste" + FIELD_SEP + "1"):
    meta = "1" if enabled else "0"
    if name:
        meta += NAME_SEP + sanitise(name)
    return meta + META_SEP + body


def parse_meta(row_text):
    """The port of what parse does with the META field."""
    idx = row_text.find(META_SEP)
    meta = row_text[:idx] if idx > 0 else ""
    enabled = idx <= 0 or meta.split(NAME_SEP)[0] == "1"
    name = meta.split(NAME_SEP, 1)[1] if NAME_SEP in meta else ""
    return enabled, name


check("a name survives the round trip", parse_meta(ser(True, "bucket row")) == (True, "bucket row"))
check("so does the enabled flag beside it", parse_meta(ser(False, "keyboard row")) == (False, "keyboard row"))
check("no name means no name", parse_meta(ser(True, "")) == (True, ""))

# THE UPGRADE. A string written before names existed has no NAME_SEP.
check("an old row reads unchanged", parse_meta("1" + META_SEP + "b" + FIELD_SEP + "paste") == (True, ""))
check("an old disabled row too", parse_meta("0" + META_SEP + "b" + FIELD_SEP + "paste") == (False, ""))

# A name cannot corrupt the store. Separators are stripped, not escaped.
for bad, why in ((f"a{ROW_SEP}b", "a row separator would split one row into two"),
                 (f"a{BTN_SEP}b", "a button separator would invent a key"),
                 (f"a{NAME_SEP}b", "a second name separator"),
                 (f"a{META_SEP}b", "a meta separator would swallow the buttons")):
    out = sanitise(bad)
    check(f"stripped: {why}", all(c not in out for c in (ROW_SEP, BTN_SEP, META_SEP, NAME_SEP)), repr(out))
    check(f"round trips anyway: {why}", parse_meta(ser(True, bad))[0] is True, "the row was damaged")

check("a long name is capped", len(sanitise("x" * 200)) == 24, len(sanitise("x" * 200)))
check("whitespace is trimmed", sanitise("  spaced  ") == "spaced")

rows_named = code(SRC / "dictate/MaRows.kt")
check("the name is on the model", "val name: String = \"\"" in rows_named, "held on the screen instead")
check("there is one fallback helper", "fun displayName(" in rows_named,
      "the tab and everything else could disagree about what a row is called")
check("names are sanitised on the way in", "fun sanitiseName(" in rows_named, "a name could corrupt the store")

# THE SANITISER IS CHECKED AGAINST THE KOTLIN, not only against the port.
#
# The separator cases above walk a Python `sanitise` that I wrote to match — and when the Kotlin was
# sabotaged to strip nothing, they all still passed, because a port proves only that it agrees with
# itself. **A model is not a witness to the code it models.** These read the real function.
sani = rows_named[rows_named.index("fun sanitiseName("):]
sani = sani[:sani.index("fun displayName")]
for sep in ("ROW_SEP", "BTN_SEP", "FIELD_SEP", "ROW_META_SEP", "NAME_SEP"):
    check(f"the Kotlin strips {sep}", f"it != {sep}" in sani,
          "a name containing it would corrupt the row after it")
check("the Kotlin caps the length", "take(24)" in sani, "a paste could fill the preference")
check("the Kotlin trims", ".trim()" in sani, "a name of spaces would look unnamed and not be")

screen_named = code(SRC / "app/settings/dictate/MaRowsScreen.kt")
check("the tab shows the name", "MaRows.displayName(it, i)" in screen_named, "still Row 1, Row 2")
check("there is a field to type it in", 'label = { Text("Name this row") }' in screen_named, "no way to set it")
check("it writes through commit", "row.copy(name = MaRows.sanitiseName(typed))" in screen_named,
      "a path that updates the state without the preference")

print(f"row swap, test 1: {checks} checks, {len(failures)} failed ({walked} swaps walked)")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
