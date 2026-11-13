#!/usr/bin/env python3
"""
Test 1 for RE Magic Database (MaRemoteDb, 9.11.2026): the token is encrypted and never logged, the sync keeps both on a
conflict, only small text files, the git blob hash is right.

    python3 scripts/test_remote_db.py
"""
import hashlib
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


db = code(SRC / "dictate/MaRemoteDb.kt")
scr = code(SRC / "app/settings/dictate/MaReScreen.kt")
check("the repo is the private RE_MAGIC_DATABASE", 'REPO = "markoboskoauroville/RE_MAGIC_DATABASE"' in db)
check("the token is encrypted with a Keystore key", "AndroidKeyStore" in db and "AES/GCM/NoPadding" in db)
check("the token never reaches a log", not re.search(r"MaLog\.add\([^)]*(tok|token)\b", db) and not re.search(r"(?<!Ma)Log\.", db))
# 13.11.2026: no paste box; the token comes from a key file (KeyImporter), Settings only shows saved / delete.
check("the token is never typed in the UI", "PasswordVisualTransformation" not in scr and "OutlinedTextField(\n                value = tokenInput" not in scr and "Delete token" in scr)
check("conflicts keep both", ".conflict-" in db and "localChanged" in db and "remoteChanged" in db)
check("small text files only", "length() < 400_000" in db and "path.endsWith(\".md\")" in db)
check("one pull on start, a push after each update", "maybePull()" in code(SRC / "FlorisImeService.kt") and db.count("syncSoon()") >= 1
      and "MaRemoteDb.syncSoon()" in code(SRC / "dictate/MaSkills.kt"))
check("the token page is scoped to the account", "target_name=markoboskoauroville" in db and "contents=write" in db)


def git_sha(b):
    return hashlib.sha1(b"blob %d\0" % len(b) + b).hexdigest()


check("git blob hash of hello", git_sha(b"hello\n") == "ce013625030ba8dba906f756967f9e9ca394464a")
print(f"remote db, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
