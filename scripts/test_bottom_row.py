#!/usr/bin/env python3
"""
Test 1 for the bottom row, copied from SwiftKey.

MEASURED from Microsoft's own documentation, 26.8.2026: the `123` key sits in the LOWER LEFT,
directly below shift, and `abc` returns from the symbol and emoji layouts **in that same corner**.
The emoji key is lower right; symbols are two panels deep.

The corner is the whole point. A layout switcher that moves between panels is a switcher he has to
find twice, and finding it twice is what makes a keyboard feel foreign.

    python3 scripts/test_bottom_row.py
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAYOUTS = ROOT / "app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def bottom(mod: str):
    rows = json.loads((LAYOUTS / mod / "default.json").read_text())
    return [k.get("label") for k in rows[-1]]


def labels_all(mod: str):
    rows = json.loads((LAYOUTS / mod / "default.json").read_text())
    return [k.get("label") for row in rows for k in row]


# ---------------------------------------------------------------- the ring is gone (13.11.2026)
#
# The one-key ring and the long-press list of views were replaced by Marko's own design: two fixed keys next to
# ctrl, each going to one place, and nothing on a hold. Checked further down (EXPECT).
targets = {"view_characters", "view_symbols", "view_symbols2"}
for mod in ("charactersMod", "symbolsMod", "symbols2Mod"):
    row = bottom(mod)
    check(f"{mod}: ctrl is far left", row[0] == "ctrl", str(row))
reach = set()
for mod in ("charactersMod", "symbolsMod", "symbols2Mod"):
    reach |= {k for k in bottom(mod) if k.startswith("view_")}
check("every view is reachable", reach == targets, str(reach))
for mod, own in (("charactersMod", "view_characters"), ("symbolsMod", "view_symbols"), ("symbols2Mod", "view_symbols2")):
    check(f"{mod}: no key goes where it already is", own not in bottom(mod), str(bottom(mod)))

# ---------------------------------------------------------------- nothing was lost
#
# A reorder that drops a key is a reorder that deletes a feature, and the keys here include ctrl,
# which he uses for Ctrl+P and Ctrl+F.
for mod, expected in (
    # Updated for SwiftKey's row: language_switch is gone on purpose and view_symbols2 moved to the
    # 123 popup. What must still be present is everything he presses.
    # The comma key became the Symbols 2 key (10.10.2026); the comma itself is on the period's
    # hold, checked below.
    ("charactersMod", {"ctrl", "space", ".", "enter", "view_symbols", "view_symbols2", "shift", "delete"}),
    # The switcher on each panel is now the NEXT view, not always "back to letters" — so what must be
    # present is "a switcher", checked above, rather than one particular destination. Letters remain
    # reachable from every panel: by cycling, and immediately from the long-press menu.
    # 13.11.2026: next to ctrl two layout keys and nothing else; the comma is the period's hold here too.
    ("symbolsMod", {"ctrl", "space", ".", "enter", "view_characters", "view_symbols2"}),
    ("symbols2Mod", {"ctrl", "space", ".", "enter", "view_symbols", "view_characters"}),
):
    got = set(l for l in labels_all(mod) if l)
    missing = expected - got
    check(f"{mod}: no key was dropped", not missing, f"missing {missing}")

# ---------------------------------------------------------------- SwiftKey's row, applied
#
# From his screenshot: 123 | , | space | . | enter, with a DEDICATED COMMA left of space and the
# period carrying ,!? on its popup. Five keys where his had seven.
#
# **This is a removal, so it is the part to be careful about.** `language_switch` went and should
# have: the language is chosen by the record and send keys now, so a key that switches it is the
# mode this app spent three builds deleting. `view_symbols2` went from the ROW and is still on the
# 123 key's popup — moved, not removed.
#
# `ctrl` STAYED, against the screenshot. SwiftKey has no Ctrl+P and no Ctrl+F; he uses both hourly,
# and copying a layout is not a reason to delete a feature that layout never had.
# 13.11.2026, Marko: "next to the left control there are two SY buttons. SY1 shows one symbol layout, SY2 the
# second, and that's all they do. In their symbol layout the text changes to text, so I can quickly switch to my text
# layout. No other functions: no dot, no comma, nothing." So: letters sy1 sy2, symbols 1 text sy2, symbols 2 sy1
# text; no popups on them; the comma is the period's hold on every panel.
EXPECT = {"charactersMod": ["view_symbols", "view_symbols2"], "symbolsMod": ["view_characters", "view_symbols2"],
          "symbols2Mod": ["view_symbols", "view_characters"]}
for mod, pair in EXPECT.items():
    row = bottom(mod)
    check(f"{mod}: six keys", len(row) == 6, str(row))
    check(f"{mod}: the two layout keys next to ctrl", row[0] == "ctrl" and row[1:3] == pair, str(row))
    check(f"{mod}: no comma key on the row", "," not in row, str(row))
    _rows = json.loads((LAYOUTS / mod / "default.json").read_text())
    check(f"{mod}: the layout keys do nothing else", all("popup" not in k for k in _rows[-1][1:3]), "a hold on sy does something")
    _p = next(k for k in _rows[-1] if k.get("label") == ".")
    check(f"{mod}: the period's hold types the comma", (_p.get("popup", {}).get("main") or {}).get("code") == 44, "no comma on this panel")
    check(f"{mod}: no language switcher", "language_switch" not in row, str(row))
    check(f"{mod}: enter is last", row[-1] == "enter", str(row))

# ---------------------------------------------------------------- Symbols 2 in one press
#
# 10.10.2026, his ask: reaching Symbols 2 from the letters took two presses (sy1, then sy2) and that
# is one too many. The comma key right of sy1 became a Symbols 2 key, so from the letters each
# symbol panel is one press away: sy1 on the left, sy2 beside it. The comma moved to the period's
# hold, which the period already advertised with a "," in its corner.
row = bottom("charactersMod")
check("charactersMod: six keys", len(row) == 6, str(row))
check("charactersMod: sy1 then sy2, side by side", row[1:3] == ["view_symbols", "view_symbols2"], str(row))
check("charactersMod: no comma key on the row", "," not in row, str(row))
check("charactersMod: no language switcher", "language_switch" not in row, str(row))
check("charactersMod: enter is last", row[-1] == "enter", str(row))
_chars = json.loads((LAYOUTS / "charactersMod" / "default.json").read_text())
_period = next(k for k in _chars[-1] if k.get("label") == ".")
check("the period's hold types the comma", (_period.get("popup", {}).get("main") or {}).get("code") == 44,
      "the comma would be unreachable from the letters")

# What the removed keys offered must still be reachable, or the tidy-up deleted a panel.
chars_rows = json.loads((LAYOUTS / "charactersMod" / "default.json").read_text())
check("the second symbol panel is one press away", "view_symbols2" in bottom("charactersMod"), str(bottom("charactersMod")))

# Ctrl survives, and it is the one whose loss he would feel first.
for mod in ("charactersMod", "symbolsMod", "symbols2Mod"):
    check(f"{mod}: ctrl is still there", "ctrl" in labels_all(mod), "Ctrl+P and Ctrl+F would be gone")

# The comma stays reachable from the period's popup, which is where it has always been.
chars = json.loads((LAYOUTS / "charactersMod" / "default.json").read_text())
period = next(k for row in chars for k in row if k.get("label") == ".")
popup = [p.get("label") for p in period.get("popup", {}).get("relevant", [])]
# The comma has its own key now, so the period's popup carries ! and ? — which is what SwiftKey
# prints above that key as ",!?".
check("the period offers ! and ?", "!" in popup and "?" in popup, str(popup))

# ---------------------------------------------------------------- the files still parse
for mod in ("charactersMod", "symbolsMod", "symbols2Mod"):
    rows = json.loads((LAYOUTS / mod / "default.json").read_text())
    check(f"{mod}: parses as rows of keys", isinstance(rows, list) and all(isinstance(r, list) for r in rows))
    for row in rows:
        for k in row:
            check(f"{mod}: every key has a code", "code" in k, str(k))

print(f"bottom row, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
