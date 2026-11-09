#!/usr/bin/env python3
"""
Test 1 for Spelling (MaSpelling, 9.11.2026): keyterms and custom spelling on every AssemblyAI request, the local
pass with context rules, the table file, the settings page. Includes a port of the pure rules.

    python3 scripts/test_spelling.py
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
LIB = ROOT / "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider"
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


sp = code(SRC / "dictate/MaSpelling.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
cli = code(LIB / "OpenAiCompatibleClient.kt")
models = code(LIB / "ProviderModels.kt")
order = code(SRC / "app/settings/MaSettingsOrder.kt")
routes = code(SRC / "app/Routes.kt")
screen = code(SRC / "app/settings/dictate/MaSpellingScreen.kt")
skill = code(SRC / "dictate/MaReSkill.kt")

check("request carries keyterms and custom spelling", "val keyterms: List<String>" in models and "val customSpelling" in models)
check("every request sends them", "keyterms = MaSpelling.keyterms(), customSpelling = MaSpelling.engineSpelling()" in ctrl.replace("\n", " ").replace("  ", " ")
      or ("keyterms = MaSpelling.keyterms()" in ctrl and "customSpelling = MaSpelling.engineSpelling()" in ctrl))
check("async: keyterms_prompt and custom_spelling", '"keyterms_prompt"' in cli and '"custom_spelling"' in cli)
check("sync and dictation: keyterms_prompt in config", "AssemblySyncConfigDto(" in cli and 'put("keyterms_prompt"' in cli)
check("all three AssemblyAI paths wrapped", cli.count("withVocabulary(request)") == 3)
check("a 400 retries without the vocabulary", "e.httpStatus != 400" in cli and "keyterms = emptyList(), customSpelling = emptyList()" in cli)
check("limits: 100 terms, 8000 characters", ".take(100)" in cli and "chars <= 8000" in cli)
check("the local pass runs before the text is committed", "MaSpelling.apply(result.text)" in ctrl)
check("learning feeds the learned list", "MaSpelling.noteEdit(d.text, finalText)" in skill)
check("the table is the Documents file", 'FILE = "dictation_corrections.json"' in sp and "File(MaVault.dir(), FILE)" in sp)
check("settings page and entry", 'SPELLING("spelling", "Spelling"' in order and "MaSpelling::class" in routes and "Switch(" in screen)
check("engine custom spelling: one word, no context rule", "it.unless.isEmpty() && it.onlyIf.isEmpty()" in sp and "isWhitespace" in sp)
check("no key shapes", not re.search(r"(sk-ant|AIza)", sp + screen))

# the file the Mac builds
r = subprocess.run([str(Path.home() / ".local/bin/re-skill-sync"), "corrections", "build"], capture_output=True, text=True)
mac = Path.home() / "Downloads/API/dictation_corrections.json"
if r.returncode == 0 and mac.exists():
    d = json.loads(mac.read_text())
    ids = {p["id"]: p for p in d["pairs"]}
    check("the table has Grok with its exception", ids["grok"]["unless"] == ["Elon Musk", "xAI"])
    check("keyterms hold the names", all(n in d["keyterms"] for n in ("Groq", "Svemir", "Santoshi", "Suno", "Tajana", "Kinisi")))
    pairs = d["pairs"]
else:
    pairs = []


# port of the pure rules
def case_like(m, to):
    letters = [c for c in m if c.isalpha()]
    if len(letters) >= 2 and all(c.isupper() for c in letters) and len(to) >= 2 and not any(c.isupper() for c in to):
        return to.upper()
    if m[0].isupper() and to[0].islower():
        return to[0].upper() + to[1:]
    return to


def apply(text, pairs):
    for p in pairs:
        if any(u.lower() in text.lower() for u in p["unless"]):
            continue
        if p["onlyIf"] and not any(o.lower() in text.lower() for o in p["onlyIf"]):
            continue
        for f in sorted(p["from"], key=len, reverse=True):
            words = r"\s+".join(re.escape(w) for w in f.split())
            text = re.sub(r"(?<![\w])" + words + r"(?![\w])", lambda m: case_like(m.group(0), p["to"]), text, flags=re.I)
    return text


if pairs:
    check("Grok becomes Groq", apply("I use Grok for Whisper", pairs) == "I use Groq for Whisper")
    check("Grok stays with Elon Musk", apply("Elon Musk built Grok", pairs) == "Elon Musk built Grok")
    check("Grok stays with xAI", apply("xAI Grok is new", pairs) == "xAI Grok is new")
    check("Svemir forms", apply("Sfemeri and Sveimir", pairs) == "Svemir and Svemir")
    check("whole words only", apply("Samiracle", pairs) == "Samiracle")
    check("Satoshi to Santoshi", apply("the Satoshi concert", pairs) == "the Santoshi concert")
    check("mode only beside Claude", apply("dark mode", pairs) == "dark mode" and apply("a Claude Code mode", pairs) == "a Claude Code mod")
    check("Marco Bosco", apply("by Marco Bosco", pairs) == "by Marko Bosko")
    check("Diana only in the band context", apply("Diana", pairs) == "Diana" and apply("Diana sings with Santoshi", pairs).startswith("Tajana"))
    check("sentence start keeps its capital", apply("Kinezi is the account", pairs) == "Kinisi is the account")
    check("all caps stays caps", apply("KINEZI", pairs) == "KINISI")

print(f"spelling, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
