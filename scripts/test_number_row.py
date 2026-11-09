#!/usr/bin/env python3
"""
Test 1 for the number row's second characters.

The editor existed for weeks inside a screen with no settings entry, so the only way to reach it was
a deep link nobody types. **A feature that compiles, passes its tests and cannot be opened is not a
feature** — this is the third time this month.

    python3 scripts/test_number_row.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"

failures: list[str] = []
checks = 0
SEP = "\u001f"


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def sanitize(v):
    """The port: any text, no separator, no carriage return, capped at 2000 (10.10.2026)."""
    return v.replace("\r", "").replace(SEP, "")[:2000]


def hint(v):
    """The port of MaNumericSecondary.hint: the first non-blank code point."""
    for c in v:
        if not c.isspace():
            return c
    return None


# ---------------------------------------------------------------- anything, like a hotstring
#
# He replaced his own earlier rule: a slot is a snippet now, not one punctuation mark.
for good in ("/", "_", "7", "k", "\u0161", "Hello, world.", "ssh marko@host -p 22",
             "line one\nline two", "\U0001F600 done"):
    check(f"{good!r} is kept whole", sanitize(good) == good, "part of the snippet is lost")
check("a sentence is not cut at 16", len(sanitize("x" * 300)) == 300, "a snippet would be truncated")
check("a pasted novel is capped", len(sanitize("x" * 5000)) == 2000)

# The separator must never survive: one in a value would split a slot in two and shift every key
# after it.
check("the separator is stripped", sanitize(f"/{SEP}(") == "/(", "one field would become two")
check("a Windows line break is one break", sanitize("a\r\nb") == "a\nb")
check("empty stays empty", sanitize("") == "", "a key with nothing behind it is a choice")

# The corner shows the first character only.
check("the hint is the first character", hint("Hello there") == "H")
check("a leading space does not blank the hint", hint("  /usr/bin") == "/")
check("an emoji hint is whole", hint("\U0001F600 hi") == "\U0001F600")
check("a blank slot has no hint", hint("   ") is None)

# Ten slots, always ten, whatever is stored.
def parse(raw):
    parts = raw.split(SEP)
    return [sanitize(parts[i]) if i < len(parts) else "" for i in range(10)]


check("a short string still gives ten", len(parse("/")) == 10, "a key would have no slot at all")
check("a long string still gives ten", len(parse(SEP.join("!" * 20))) == 10)
check("a round trip survives", parse(SEP.join(["/", "(", ")"] + [""] * 7))[:3] == ["/", "(", ")"])


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


core = code(SRC / "dictate/MaNumericSecondary.kt")
order = code(SRC / "app/settings/MaSettingsOrder.kt")
screen = code(SRC / "app/settings/MaSettingsOrderScreen.kt")
routes = code(SRC / "app/Routes.kt")
editor = code(SRC / "app/settings/dictate/MaNumericSecondarySetting.kt")

check("letters and digits are no longer filtered", "isLetterOrDigit" not in core,
      "a snippet would lose its words")
check("the cap is the long one", "const val MAX_LENGTH = 2000" in core and "take(MAX_LENGTH)" in core,
      "a sentence would be cut short")
check("the hint takes a whole code point", "offsetByCodePoints(start, 1)" in core,
      "an emoji in the corner would be half a surrogate pair")
row = code(SRC / "dictate/ui/MaExtraRow.kt")
check("the hold types the slot in one commit", "editorInstance.commitText(text)" in row,
      "one key event per character, which autocorrect can rewrite word by word")
# 21.10.2026: the corner mark is gone; the whole command is printed under the digit instead.
check("the whole command is printed under the digit", "MaNumericSecondary.preview(it)" in row
      and "MaNumericSecondary.hint(" not in row, "a sentence crammed into a corner")

# ---------------------------------------------------------------- special keys (10.10.2026)
#
# Holding the digit in the editor offers keys that cannot be typed. Every token it offers must be one
# MaMacroSyntax resolves, or picking it inserts text the key then types literally.
macro = code(SRC / "dictate/MaMacroSyntax.kt")
_named = set(re.findall(r'put\("([a-z0-9]+)", KeyEvent', macro)) | {f"f{i}" for i in range(1, 13)} \
    | {chr(c) for c in range(ord("a"), ord("z") + 1)}
_mods = set(re.findall(r'"([a-z]+)" to \(KeyEvent\.META', macro))
_special = re.findall(r'SpecialKey\("\{([^}]+)\}", "([^"]+)", "([^"]+)"\)', core)
check("the menu offers the keys he named", {t for t, _, _ in _special} >= {"Enter", "Tab", "Left", "Right", "Up", "Down"},
      str([t for t, _, _ in _special]))
for _t, _name, _glyph in _special:
    *_ms, _k = _t.lower().split("+")
    check(f"{{{_t}}} is a key the macro runner knows", _k in _named and all(m in _mods for m in _ms),
          "picking it would type the braces instead of pressing the key")
check("every special key has a glyph", all(g for _, _, g in _special))

check("the digit opens the menu", "DropdownMenu(expanded = menuOpen" in editor and "onLongClick = { menuOpen = true }" in editor,
      "no way to insert a key that cannot be typed")
check("picking inserts at the cursor", "field.selection.min" in editor, "the token would land at the end")
check("the editor says how to reach them", "Hold a digit here" in editor)
check("a slot with a real key runs as a macro",
      "MaMacroSyntax.parse(text).any { it is MaMacroSyntax.Step.Key }" in row and "MaMacroSyntax.run(text" in row,
      "{Enter} would be typed as seven characters")
check("plain text still goes in as one commit", "editorInstance.commitText(text)" in row)
check("a slot starting with a key shows its glyph", "TOKEN_AT_START" in core and "it.glyph" in core,
      "the corner would show a brace")

# ---------------------------------------------------------------- it can actually be opened
check("there is a settings entry", 'NUMBER_ROW("number_row"' in order, "unreachable, as before")
check("it is in DEFAULT", "MaSettingsEntry.NUMBER_ROW," in order,
      "it would never appear for anyone who has saved an order")
check("it has an icon", "MaSettingsEntry.NUMBER_ROW ->" in screen)
check("it has a route", "MaSettingsEntry.NUMBER_ROW -> Routes.Settings.MaNumberRow" in screen)
_m = re.search(r"((?:\s*@\w+(?:\([^)]*\))?\s*)*)\bobject MaNumberRow\b", routes)
check("the route carries @Deeplink", _m is not None and "@Deeplink" in _m.group(1),
      "the app would not start — this exact shape crashed build 369")
# A second door, on the row's own switch, because the settings entry alone was not found.
switchboard = code(SRC / "app/settings/dictate/MaSwitchboardScreen.kt")
check("the Switchboard's Number row opens the editor",
      re.search(r'"Number row",[^\n]*Routes\.Settings\.MaNumberRow', switchboard) is not None,
      "the switch is a dead end and the editor has one door again")
# A saved order must not bury it. Read DEFAULT from the source and walk the same insertion parse()
# does: an order saved before NUMBER_ROW existed puts it straight after READER, not at the bottom.
check("parse inserts a new entry second from the top (Marko 9.11.2026)", "all.add(minOf(1, all.size), entry)" in order,
      "a new entry is appended under everything he arranged, where he did not find it")
_default = re.findall(r"MaSettingsEntry\.([A-Z_0-9]+)",
                      re.search(r"val DEFAULT: List<MaSettingsEntry> = listOf\((.*?)\n    \)", order, re.S).group(1))


def parse_order(wanted):
    out = list(dict.fromkeys(wanted))
    for i, e in enumerate(_default):
        if e in out:
            continue
        anchor = next((d for d in reversed(_default[:i]) if d in out), None)
        out.insert(0 if anchor is None else out.index(anchor) + 1, e)
    return out


_old = list(reversed([e for e in _default if e != "NUMBER_ROW"]))
_got = parse_order(_old)
check("an old saved order gets Number row after Reader",
      _got.index("NUMBER_ROW") == _got.index("READER") + 1, str(_got))
check("his order is otherwise untouched", [e for e in _got if e != "NUMBER_ROW"] == _old, str(_got))
check("an empty order is DEFAULT", parse_order([]) == _default, str(parse_order([])))
check("the route is registered", "composableWithDeepLink(Settings.MaNumberRow::class)" in routes)
check("the screen hosts the editor", "MaNumericSecondarySetting()" in
      code(SRC / "app/settings/dictate/MaNumberRowScreen.kt"), "an empty screen")
check("the editor says the hold types the whole box", "types the whole box" in editor,
      "he would not know the hold types the whole snippet")

print(f"number row, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
