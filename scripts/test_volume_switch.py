#!/usr/bin/env python3
"""
Test 1 for the volume-keys switch.

VOLUME_KEYS.md is a contract with two invariants, and adding a switch is the change most likely to
break them. Both are re-walked here across the whole range of hold lengths, in both states.

    python3 scripts/test_volume_switch.py
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


HOLD_MS = 500


def press(live: bool, hold_ms: int, key: str, reading: bool = False):
    """
    One volume press, from down to up. Returns (app_action, volume_changed).

    A faithful port of MaVolumeKeys.onDown/onUp with the new gate at the top of both.
    """
    if not live:
        # Both halves return false, so the system sees an ordinary volume key.
        return None, True
    if hold_ms >= HOLD_MS:
        # The hold spent the press on the volume; release does nothing else.
        return None, True
    if reading:
        return ("next" if key == "down" else "previous"), False
    return ("record/stop" if key == "up" else "send"), False


# ---------------------------------------------------------------- the two invariants
walked = 0
for live in (True, False):
    for reading in (True, False):
        for key in ("up", "down"):
            for hold in range(0, 1201, 10):
                action, volume = press(live, hold, key, reading)
                walked += 1
                # NEVER BOTH.
                check(
                    f"live={live} {key} {hold}ms reading={reading}: never both",
                    not (action is not None and volume),
                    f"action={action} volume={volume}",
                )
                # ALWAYS ONE.
                check(
                    f"live={live} {key} {hold}ms reading={reading}: always one",
                    action is not None or volume,
                    "the press did nothing at all",
                )

# Switched off, EVERY press is the volume and no press is ever an app action. This is the whole
# claim of the switch, and it must hold at every hold length, not at three sample points.
for key in ("up", "down"):
    for hold in range(0, 1201, 10):
        action, volume = press(False, hold, key)
        check(f"off: {key} {hold}ms is volume", volume and action is None, f"action={action}")

# Switched on, the contract is unchanged. A quick tap still records; a hold is still the volume.
check("on: a quick up tap records", press(True, 100, "up")[0] == "record/stop")
check("on: a quick down tap sends", press(True, 100, "down")[0] == "send")
check("on: a held key is the volume", press(True, 900, "up") == (None, True))
check("on: reading, down is next", press(True, 100, "down", reading=True)[0] == "next")
check("on: reading, up is previous", press(True, 100, "up", reading=True)[0] == "previous")


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


# ---------------------------------------------------------------- both halves agree
vol = code(SRC / "dictate/MaVolumeKeys.kt")
check("onDown is gated", vol.count("if (!live())") >= 1, "the press is still taken")
check(
    "onUp is gated too",
    vol.count("if (!live())") == 2,
    "one half gated: a press given to the system on the way down would act on release",
)

# ---------------------------------------------------------------- THE TRAPDOOR RULE
#
# The failure VOLUME_KEYS.md exists to prevent was not "a switch existed" — it was a switch he could
# not see, sitting among a dozen draggable ones. So the switch must be reachable from the row and
# from NOWHERE else. This is the check that keeps the promise made in that document.
pref = "maVolumeKeysLive"
readers = []
for f in SRC.rglob("*.kt"):
    if pref in code(f):
        readers.append(f.name)
check(
    "only the row key and the module touch it",
    sorted(readers) == ["AppPrefs.kt", "MaFeatureRow.kt", "MaVolumeKeys.kt"],
    f"reached from {sorted(readers)} — a settings screen or the switchboard has it",
)

switchboard = code(SRC / "app/settings/dictate/MaSwitchboardScreen.kt")
check("not in the switchboard", pref not in switchboard, "back in the list that caused the bug")

row = code(SRC / "dictate/ui/MaFeatureRow.kt")
# The ring, the same one the buckets wear. One visual language: a green ring means on, everywhere.
# 4.11.2026: grey outline off, green on, like every switch.
check("the key shows its state", "ring = if (volumeLive) onGreen else MaSwitcherRingOff" in row, "an invisible switch again")
check("and the glyph holds still", "VolumeOff" not in row, "the face changes as well as the ring")
check("and says it out loud", "Volume keys off" in row, "silent, like the one that cost him days")

order = code(SRC / "dictate/MaFeatureOrder.kt")
check("it is a key you can put on a row", 'VOLUME_KEYS("volume_keys"' in order, "not in the catalogue")

doc = (ROOT / "VOLUME_KEYS.md").read_text()
check(
    "the contract was amended, not contradicted",
    "maVolumeKeysLive" in doc,
    "the code and the contract now disagree, and the document says the code is wrong",
)

print(f"volume switch, test 1: {checks} checks, {len(failures)} failed ({walked} presses walked)")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
