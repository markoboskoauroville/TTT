#!/usr/bin/env python3
"""
Test 1 for Marko's standing rules, enforced by the build instead of by memory (13.11.2026: "where is the hole in the
system? Why it's not constantly improving?"). The rules were written in the skill and still broken; now a push that
breaks them fails CI.

  1. No key / token / password text field anywhere in the app: keys enter only through "Import keys from file".
  2. The new TTT screens use the host look (.claude/skills/ttt-design): MaHost colours and keys, no Material default
     buttons, no title bar, no DropdownMenu; the feature row's long-press panels are MaKeyPanel.
  3. A recorder outside the keyboard reuses the keyboard's readout (MaRecorderReadout).

    python3 scripts/test_host_rules.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
failures, checks = [], 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def code(path):
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


# 1. no key fields
KEYWORDS = re.compile(r"(api[ _-]?key|key_label|token|password|paste the|secret)", re.I)
fields = 0
for f in SRC.rglob("*.kt"):
    t = code(f)
    for m in re.finditer(r"\b(Outlined)?TextField\s*\(", t):
        fields += 1
        block = t[m.start():m.start() + 900]
        lab = re.search(r"label\s*=\s*\{[^}]*\}", block)
        bad = (lab and KEYWORDS.search(lab.group(0))) or "PasswordVisualTransformation()" in block.split("\n\n")[0]
        if "isSecret" in block:
            bad = False  # the generic settings field; checked below by its callers
        check(f"no key field in {f.relative_to(SRC)}", not bad, (lab.group(0) if lab else "password field")[:80])
callers = [f for f in SRC.rglob("*.kt") if re.search(r"isSecret\s*=\s*true", code(f))]
check("no caller asks for a secret field", not callers, ", ".join(str(c.relative_to(SRC)) for c in callers))

# 2. host look on the new screens
HOST = ["dictate/MaReChatActivity.kt", "dictate/MaHistoryActivity.kt", "dictate/ui/MaKeyPanel.kt"]
for rel in HOST:
    t = code(SRC / rel)
    for bad in ["FilledTonalButton(", "FloatingActionButton(", "DropdownMenu(", "TopAppBar(", "Brush.linearGradient"]:
        check(f"{rel} has no {bad}", bad not in t)
for rel in ["dictate/MaReChatActivity.kt", "dictate/MaHistoryActivity.kt"]:
    t = code(SRC / rel)
    check(f"{rel} uses MaHost", "MaHost." in t)
    stray = [c for c in re.findall(r"Color\(0x[0-9A-Fa-f]{8}\)", t) if c not in ("Color(0x33E8B15C)", "Color(0x1FFFFFFF)")]
    check(f"{rel} has no own colours", not stray, ", ".join(sorted(set(stray))))
chat = code(SRC / "dictate/MaReChatActivity.kt")
check("RE chat has no title bar", "\"RE Magic\"" not in chat)
check("RE chat recorder is the keyboard readout", "MaRecorderReadout(" in chat)
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
i = row.find("MaFeatureKey.REPROMPT ->")
j = row.find("MaFeatureKey.WHATSAPP ->", i)
check("RE long press is the key panel", "MaKeyPanel(" in row[i:j] and "DropdownMenu" not in row[i:j])

# 3. the GitHub token and the GIF key come from files
imp = code(SRC / "dictate/engines/KeyImporter.kt")
check("key picker takes the GitHub token", "github_pat_" in imp and "MaRemoteDb.saveToken" in imp)
check("key picker takes the KLIPY key", "klipyApiKey.set" in imp)
db = code(SRC / "dictate/MaRemoteDb.kt")
check("RE memory files are synced", '"re/"' in db)

for f in failures:
    print("FAIL", f)
print(f"host rules, test 1: {checks} checks, {len(failures)} failed ({fields} text fields examined)")
sys.exit(1 if failures else 0)
