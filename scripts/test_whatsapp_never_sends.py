#!/usr/bin/env python3
"""
Test 1 for the WA key (MaWhatsApp, 7.11.2026).

    python3 scripts/test_whatsapp_never_sends.py

Marko, 7.11.2026: "a WhatsApp control ... write the message and never send the message automatically."
This pins what makes it safe: Sonnet has no send action, taps on anything that sends or calls are refused
in code, typing is only into text fields, actions only while WhatsApp is in front; and the key itself.
Also the address book guards (build 473-477 crashed on every letter from inside it).
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


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


wa = code(SRC / "dictate/MaWhatsApp.kt")
tools = re.findall(r'tool\(\s*"([a-z_]+)"', wa)
check("Sonnet's tools are exactly these", sorted(tools) == sorted(["tap", "type", "scroll", "back", "open_whatsapp", "finish"]), str(tools))
check("no send tool", not any("send" in t for t in tools), str(tools))
check("no ACTION_CLICK outside the guarded tap", wa.count("ACTION_CLICK") == 1)
check("a tap is refused on send / call", 'if (isForbidden(n)) return@withContext "refused' in wa)
check("the clickable parent is checked too", "n = n.parent ?: break\n                        if (isForbidden(n))" in wa)
forbidden = re.search(r'FORBIDDEN = Regex\(\s*"""(.*?)"""', wa, re.S)
check("the refusal list exists", forbidden is not None)
if forbidden:
    rx = re.compile(forbidden.group(1).replace("(?i)", ""), re.I)
    for probe in ["send", "Send", "Pošalji", "voice_note_btn", "Voice call", "Video call", "videopoziv"]:
        check(f"refuses {probe!r}", rx.search(probe) is not None)
    for probe in ["Search", "Ana Horvat", "entry", "menuitem_search"]:
        check(f"allows {probe!r}", rx.search(probe) is None)
check("typing only into a text field", 'if (!n.isEditable) return@withContext "refused' in wa)
check("acts only while WhatsApp is in front", wa.count('if (screen.packageName !in PACKAGES) return@withContext "refused') == 2)
check("no global action but back", re.findall(r"GLOBAL_ACTION_\w+", wa) == ["GLOBAL_ACTION_BACK"])
check("the system prompt says never send", "NEVER send anything" in (SRC / "dictate/MaWhatsApp.kt").read_text())

order = code(SRC / "dictate/MaFeatureOrder.kt")
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
check("the key exists", 'WHATSAPP("whatsapp"' in order)
check("the key records for WhatsApp", "DictateController.onMicClick(context, whatsapp = true)" in row)
check("a long press stops it", "onLongClick = { MaWhatsApp.stop() }" in row)
check("the transcript goes to MaWhatsApp", "MaWhatsApp.run(appContext, rawText.trim())" in ctrl)

ed = code(SRC / "ime/editor/EditorInstance.kt")
nlp = code(SRC / "ime/nlp/NlpManager.kt")
book = code(SRC / "dictate/nlp/MaAddressBook.kt")
check("the address book observer is guarded", "runCatching {\n            dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.observe(" in ed)
check("the address book suggestions are guarded", "emptyList() else runCatching {\n                dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.suggest(" in nlp)
check("trailingPhone never reads past the end", "if (i >= textBefore.length) return \"\"" in book)

print(f"whatsapp never sends, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
