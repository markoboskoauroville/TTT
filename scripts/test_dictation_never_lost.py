#!/usr/bin/env python3
"""
Test 1 for the matrix fixes of 28.11.2026: a dictation is never lost, never answered by another engine, and the styling
switch is OFF / ANTHROPIC / GROQ in one place.

    python3 scripts/test_dictation_never_lost.py
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


def code(path: str) -> str:
    s = (SRC / path).read_text()
    return re.sub(r"^\s*//.*$", "", re.sub(r"/\*.*?\*/", "", s, flags=re.S), flags=re.M)


chain = code("dictate/engines/EngineChain.kt")
fra = code("dictate/engines/FrankfurtRelay.kt")
box = code("dictate/MaStyleBox.kt")
eng = code("dictate/MaReEngine.kt")
dc = code("dictate/DictateController.kt")
panel = code("dictate/ui/MaFeatureRow.kt")
res = code("dictate/MaReResult.kt")

# ---------------------------------------------------------------- never another engine
m = re.search(r"if \(first != null && \(first in ids \|\| fraFirst\)\) ids = listOf\(first\)\s*else if \(first != null\) \{(.*?)\n\s*\}", chain, re.S)
check("an unusable chosen engine stops the take", m is not None and "throw EngineGaveUp" in m.group(1),
      "the first usable engine of the order answers instead of the one he chose")

check("an empty answer is a failure, not a dictation", chain.count("if (text.isBlank())") == 2 and "if (t is EngineGaveUp) throw t" in chain,
      "an engine answering \"\" typed nothing and said nothing")
adapters = code("dictate/engines/EngineAdapters.kt")
check("a missing recognizer language is said at once", "ERROR_LANGUAGE_UNAVAILABLE" in adapters and "FailKind.NOT_CONFIGURED" in adapters,
      "10 retries x 3 s for a language the phone does not have")

check("the attempt deadline covers the upload", "else 10_000L + (sec * 1000).toLong()" in chain, "a 45 s take timed out and was sent twice")
check("a blocked network is named", "fun netBlocked" in chain and "if (netBlocked(c)) BLOCKED" in chain and "EngineChain.BLOCKED" in fra,
      "a bare 'no internet' while Android blocks TTT in the background")

# ---------------------------------------------------------------- Frankfurt keys: additive, resent when missing
check("keys go to Frankfurt additively", '.put("replace", false)' in fra and '.put("replace", true)' not in fra,
      "one phone's copy wipes the other phone's rings in Frankfurt")
check("missing keys in Frankfurt are sent again once", "class KeysMissing" in fra and "syncKeys(c, force = true)" in fra
      and fra.count("transcribeOnce(c, id, wav, lang, names, onStatus)") == 2, "a stale hash keeps Frankfurt keyless forever")

check("Frankfurt wakes Tailscale when it is off", "fun wakeTailscale" in fra and "com.tailscale.ipn.CONNECT_VPN" in fra
      and "if (!woke && !vpnUp(c))" in fra, "70 s of retries against a tunnel that is down")
check("Tailscale is visible to TTT", '<package android:name="com.tailscale.ipn"/>' in (ROOT / "app/src/main/AndroidManifest.xml").read_text(),
      "the explicit broadcast may be filtered on Android 11+")

# ---------------------------------------------------------------- the box: never holds the words hostage
check("style has a hard timeout", "withTimeout(STYLE_TIMEOUT_MS)" in box, "a hanging model leaves the box writing forever")
fail = re.search(r"private fun failed\(who: String, why: String\) \{(.*?)\n    \}", box, re.S)
check("a failed style shows the Original", fail is not None and "tab = ORIGINAL" in fail.group(1) and "Original shown" in fail.group(1),
      "the dictation sits behind an empty Style tab")
check("type is delivered without waiting for the keyboard", "deliverSoon(t)" in box and "maDeliverReNow()" in box,
      "the text waited for an onStartInputView that may not come")
check("type falls back to the clipboard", "setPrimaryClip" in box and "MaReResult.pending()" in box and "fun pending()" in res,
      "an undelivered Type is lost after 60 s")
check("the box records that it opened", "MaStyleBox.openedAt = System.currentTimeMillis()" in box, "no proof the window came")
check("a box that never opened types the words", "MaStyleBox.openedAt < askedAt" in dc and "sink(context).commitText(typed)" in dc,
      "a refused window swallows the dictation")

# ---------------------------------------------------------------- STYLING: OFF / ANTHROPIC / GROQ
for v in ('const val OFF = "off"', 'const val ANTHROPIC = "anthropic"', 'const val GROQ = "groq"'):
    check("provider " + v, v in box, "missing")
check("the badge cycles the three", "MaStyleBox.setProvider(context, MaStyleBox.nextProvider(pv))" in box, "a tap does not switch")
check("the badge names the provider", '"STYLE off"' in box and '"✓ STYLE Groq"' in box and '"✓ STYLE " + MaStyleBox.modelLabel' in box,
      "the status line does not say which styling is on")
check("the H/E panel has the STYLE row", '"STYLE"' in panel and "MaStyleBox.setProvider(context, pv)" in panel
      and '"ANTHROPIC"' in panel and '"GROQ"' in panel, "no switch where he chooses engines")
check("the panel counts the STYLE row", "val rows = ids.size + 2 + mics.size" in panel, "rows overflow the panel")
check("Groq chosen never answers with Sonnet", re.search(r"if \(MaReEngine\.isQwen\(\)\) \{\s*return MaReEngine\.groqAsk", dc) is not None,
      "Groq without an RE key fell through to Anthropic")
check("Groq uses RE's ring or TTT's Groq keys", 'fun qwenRing(c: Context): String = if (EngineKeys.ownLines(c, RING).isNotEmpty()) RING else "groq"' in eng,
      "GROQ cannot work on a phone without a separate RE key")

if failures:
    print("\n".join("FAIL " + f for f in failures))
print(f"dictation never lost, test 1: {checks} checks, {len(failures)} failed")
sys.exit(1 if failures else 0)
