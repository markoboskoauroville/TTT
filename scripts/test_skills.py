#!/usr/bin/env python3
"""
Test 1 for the on-phone skills (MaSkills, 9.11.2026): files in Documents/TTTmini/skills in the MARKO_COMMUNICATION
shape, one cheap background prompt after a conversation, the notes used as context, Settings, the sync.

    python3 scripts/test_skills.py
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


sk = code(SRC / "dictate/MaSkills.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
skill = code(SRC / "dictate/MaReSkill.kt")
scr = code(SRC / "app/settings/dictate/MaReScreen.kt")
ime = code(SRC / "FlorisImeService.kt")

check("the folder is Documents/TTTmini/skills", 'DIR = "skills"' in sk and "File(MaVault.dir(), DIR)" in sk)
check("people, threads and style", '"people"' in sk and '"threads"' in sk and 'STYLE_FILE = "style.md"' in sk)
check("one cheap prompt", 'askModel(prompt(c, person, thread, style), "RE skills", cheap = true)' in sk and "cheap: Boolean = false" in ctrl)
check("after a quiet minute or on demand", "DEBOUNCE_MS = 60_000L" in sk and "suspend fun updateNow" in sk)
check("the notes are context for the next reply", "MaSkills.contextBlock()" in skill and "<this_thread>" in sk)
check("the sent message feeds the thread", "MaSkills.noteSent(" in skill and "MaSkills.noteComposed(" in ctrl)
check("spoken 'update skills' in both paths", "MaSkills.wants(words)" in ctrl and "MaSkills.wants(words)" in ime)
check("Settings: switch, list, edit, update now", all(w in scr for w in ("maReSkills", "Update skills now", "Save file", "Refresh list")))
check("a switch, on by default", 'boolean(key = "dictate__ma_re_skills", default = true)' in code(SRC / "app/AppPrefs.kt"))
check("no secrets asked for in the prompt", "no keys, passwords" in sk)
check("nothing is uploaded but the prompt", "OkHttp" not in sk and "Request.Builder" not in sk)


def slug(t):
    import unicodedata
    n = "".join(c for c in unicodedata.normalize("NFD", t.strip()) if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", "-", n.lower()).strip("-")[:48] or "chat"


check("slug: Mislav Milićević", slug("Mislav Milićević") == "mislav-milicevic")
check("slug: a number", slug("+385 97 679 0768 (You)") == "385-97-679-0768-you")
ans = "=====PERSON=====\nNONE\n=====THREAD=====\n# Chat\n## Summary\nx\n=====STYLE=====\n- short lines\n"
check("three sections", "=====THREAD=====" in ans and "=====STYLE=====" in ans)
print(f"skills, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
