#!/usr/bin/env python3
"""
Test 1 for the copy row: the mechanism, alone, before any build.

The claim being tested is not "it compiles" and not "it looks right". It is the thing Marko asked
for in one sentence:

    THERE IS ONE COPY ROW IN THE WHOLE APP, IT APPEARS IN BOTH VIEWS, AND IT HAS ONE SWITCH.

That is a structural claim, so it can be checked without a phone and without CI. Each check below
would have caught the bug that was shipped: two rows from two preferences and two key vocabularies,
both called the copy row, ending in different keys.

    python3 scripts/test_copy_row.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"

FEATURE_ROW = SRC / "dictate/ui/MaFeatureRow.kt"
KEYBOARD_VIEW = SRC / "ime/text/TextInputLayout.kt"
DICTATE_VIEW = SRC / "dictate/ui/LegacyDictateLayout.kt"
PREFS = SRC / "app/AppPrefs.kt"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def code(path: Path) -> str:
    """The file with comments stripped, so a comment mentioning a symbol is not read as using it.

    This matters here more than usual: every removal in this change left a comment saying what was
    removed and why, and a naive grep would report all of them as survivors.
    """
    text = path.read_text()
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"^\s*//.*$", "", text, flags=re.M)
    return text


# 1. Exactly one place in the app reads the copy row preference.
#    Two readers is how the two rows drifted; the second one read a different preference entirely,
#    but the shape of the failure is the same and this is the check that sees it.
#
#    Whole word, because the first version of this check looked for "maCopyRow." and missed the
#    declaration in AppPrefs, which is written "maCopyRow = string(". It reported a real file as
#    absent — the failure was in the test, not in the code.
COPY_ROW_PREF = re.compile(r"\bmaCopyRow\b")
readers = [p for p in SRC.rglob("*.kt") if COPY_ROW_PREF.search(code(p))]
reader_names = sorted(p.name for p in readers)
check(
    "one reader of maCopyRow in the keyboard",
    reader_names == ["AppPrefs.kt", "MaCopyRowScreen.kt", "MaFeatureRow.kt", "MaUiConfig.kt"],
    f"expected AppPrefs (declares), MaCopyRowScreen (arranges), MaFeatureRow (draws), MaUiConfig (exports and applies the shipped default UI); got {reader_names}",
)

# 2. Both views ask the same composable for it, with copyRowOnly.
kb = code(KEYBOARD_VIEW)
dv = code(DICTATE_VIEW)
check("keyboard view draws the copy row", "copyRowOnly = true" in kb, "no copyRowOnly call site")
check("transcription view draws the copy row", "copyRowOnly = true" in dv, "no copyRowOnly call site")

# 3. Nothing draws the old strip any more.
for path in SRC.rglob("*.kt"):
    check(f"no LegacyEditRow call in {path.name}", "LegacyEditRow(" not in code(path), "still called")

# 4. The row appended to the feature rows is gone, so there is one way in and not two.
fr = code(FEATURE_ROW)
check(
    "copy row is never appended to the feature rows",
    "+ listOf(copyButtons)" not in fr,
    "the appended second copy is still there",
)

# 5. One switch. The two placement preferences are gone from declaration and from every reader.
prefs = code(PREFS)
check("maCopyRowOnKeyboard is gone", "maCopyRowOnKeyboard" not in prefs, "still declared")
check("maCopyRowOnDictate is gone", "maCopyRowOnDictate" not in prefs, "still declared")
for path in SRC.rglob("*.kt"):
    body = code(path)
    check(
        f"no reader of the dead switches in {path.name}",
        "maCopyRowOnKeyboard" not in body and "maCopyRowOnDictate" not in body,
        "reads a preference that no longer exists",
    )

# 6. The keyboard's switch is maEditRow — the key Marko already presses — and the transcription
#    view has no switch at all. A `wanted` variable standing between the two would mean the second
#    switch had grown back.
check("keyboard copy row is switched by maEditRow", "maEditRow" in kb, "no switch on the keyboard")
check(
    "transcription copy row is not switched",
    "maEditRow" not in dv.split("copyRowOnly = true")[0][-600:],
    "a switch has appeared in front of the fixed row",
)

# 7. The second instance must not redraw the chrome, or the keyboard shows two wand bars, two
#    magic rows and two dashboards. maDashboardOpen is file-level state, so this is not cosmetic.
check("keyboard copy row draws no chrome", "drawChrome = false" in kb, "chrome would be duplicated")
check(
    "chrome is gated on the flag",
    fr.count("drawChrome &&") == 2 and "if (drawChrome) MaWandBar(" in fr,
    "the wand bar, magic row or dashboard is not gated",
)

print(f"copy row, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
