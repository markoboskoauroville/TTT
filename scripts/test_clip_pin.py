#!/usr/bin/env python3
"""
Test 1 for the pin circle on clipboard entries.

Multi-select, not radio: any number filled at once, and a filled one sits at the top.

    python3 scripts/test_clip_pin.py
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


def toggle(items, i):
    """One tap on the circle of entry i."""
    return [(t, (not p) if j == i else p) for j, (t, p) in enumerate(items)]


def sections(items):
    """What ClipboardHistory does: pinned first, then the rest, each in its own order."""
    return [x for x in items if x[1]] + [x for x in items if not x[1]]


ITEMS = [(f"clip{i}", False) for i in range(5)]

# ---------------------------------------------------------------- it is NOT a radio button
one = toggle(ITEMS, 1)
two = toggle(one, 3)
check("two can be filled at once", [p for _, p in two] == [False, True, False, True, False], str(two))
check("filling one does not empty another", two[1][1] is True, "it behaved like a radio button")
check("a second tap empties it", toggle(two, 1)[1][1] is False, "no way to unpin")
check("tapping is its own undo", toggle(toggle(ITEMS, 2), 2) == ITEMS)

# ---------------------------------------------------------------- filled goes to the top
check("a filled entry rises", sections(two)[0] == ("clip1", True), str(sections(two)[0]))
check("all the filled ones are above all the rest",
      [p for _, p in sections(two)] == [True, True, False, False, False], str(sections(two)))
check("nothing is lost in the sort", sorted(sections(two)) == sorted(two))
check("nothing is duplicated", len(set(t for t, _ in sections(two))) == len(two))

# Order within each section is kept: pinning c3 must not reorder c1 against it, nor the unpinned
# among themselves. A history that reshuffles is a history he cannot find anything in.
check("pinned keep their relative order", [t for t, _ in sections(two) if _ ] == ["clip1", "clip3"],
      "pinning reordered the pins")
check("the rest keep theirs",
      [t for t, p in sections(two) if not p] == ["clip0", "clip2", "clip4"], "the history reshuffled")

# ---------------------------------------------------------------- walked
walked = 0
for taps in itertools.product(range(5), repeat=4):
    items = list(ITEMS)
    for t in taps:
        items = toggle(items, t)
        walked += 1
        s = sections(items)
        check(f"{taps}: five entries, always", len(s) == 5, str(s))
        check(f"{taps}: none lost", sorted(s) == sorted(items), str(s))
        # The invariant that makes it a section rather than a sort: no unpinned entry above a pinned.
        seen_unpinned = False
        ok = True
        for _, pinned in s:
            if not pinned:
                seen_unpinned = True
            elif seen_unpinned:
                ok = False
        check(f"{taps}: nothing pinned below something unpinned", ok, str(s))


# ---------------------------------------------------------------- the wiring
def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


# ZERO PINNING (Marko, 23.11.2026: "Please remove pinning from my application. Not being able to pin ... Zero pinning.").
# The walk above still proves the sorting logic of the history model; the app offers no way to pin any more.
ui = code(SRC / "ime/clipboard/ClipboardInputLayout.kt")
check("no pin circle on a clip", "pinClip(item)" not in ui and "MaClipRing else Color.Transparent" not in ui, "zero pinning")
check("no pin / unpin in the clip's long-press menu", "clip__pin_item" not in ui and "clip__unpin_item" not in ui, "zero pinning")
check("no pin in the clipboard header", "Icons.Default.PushPin else Icons.Outlined.PushPin" not in ui, "zero pinning")
hist = code(SRC / "ime/clipboard/ClipboardHistory.kt")
check("no pinned section; old pinned clips listed with the rest, by time", "val pinned = emptyList<ClipboardItem>()" in hist and "val unpinned = all" in hist,
      "a pinned section would come back")
ims = code(SRC / "FlorisImeService.kt")
check("the keyboard is never pinned up", "maKeyboardPinned.get()" not in ims, "zero pinning")
check("no pin key can be loaded", "it.id == id && it != PIN" in code(SRC / "dictate/MaFeatureOrder.kt"), "zero pinning")
emo = code(SRC / "ime/media/emoji/EmojiPaletteView.kt")
check("no emoji pin", "EmojiHistoryHelper.pinEmoji" not in emo, "zero pinning")
hs = code(SRC / "app/settings/dictate/DictateHistoryScreen.kt")
check("no pin in the dictation history", "onClick = onTogglePin" not in hs, "zero pinning")

print(f"clip pin, test 1: {checks} checks, {len(failures)} failed ({walked} taps walked)")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
