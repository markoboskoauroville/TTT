#!/usr/bin/env python3
"""
Test 1 for the version line at the top of the settings.

Mantra Manifest rule (10.10.2026): tapping the app's version opens the latest release on GitHub, so
updating is tap, download, install. Holding it still opens App info, which a tap used to.

    python3 scripts/test_version_link.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HOME = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt"
URLS = ROOT / "app/src/main/res/values/strings_dont_translate.xml"

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


urls = URLS.read_text()
_m = re.search(r'name="ma__latest_release_url"[^>]*>([^<]+)<', urls)
check("the latest-release URL exists", _m is not None, "the tap has nowhere to go")
check("it is THIS repository's latest release",
      _m is not None and _m.group(1) == "https://github.com/markoboskoauroville/TTT/releases/latest",
      _m.group(1) if _m else "missing")

home = code(HOME)
# The version line is the Text whose text names the app and the build number.
_start = home.find("R.string.app_name_full")
_block = home[_start:_start + 2500]
check("the version line is found", _start >= 0, "the test is not looking at the right line")
_tap = re.search(r"onClick\s*=\s*\{([^}]*)\}", _block)
check("a tap opens the latest release",
      _tap is not None and "launchUrl(R.string.ma__latest_release_url)" in _tap.group(1),
      _tap.group(1) if _tap else "no onClick")
check("a hold still opens App info",
      "onLongClick" in _block and "ACTION_APPLICATION_DETAILS_SETTINGS" in _block.split("onLongClick", 1)[-1],
      "the door to permissions and force stop was removed instead of moved")
check("launchUrl is imported", "import dev.patrickgold.florisboard.lib.util.launchUrl" in home)
check("combinedClickable is imported", "import androidx.compose.foundation.combinedClickable" in home)

print(f"version link, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
