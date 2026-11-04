#!/usr/bin/env python3
"""
Test 1 for the hidden code words (MaCodeWords, DEVELOPMENT.md §233).

    python3 scripts/test_code_words.py

Marko, 4.11.2026: "when I refer to Ashai Afterman or something similar, if there is misspelling, it
will recognize from the code." The first code word is the Yshai Afterman style.
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


cw = (SRC / "dictate/MaCodeWords.kt").read_text()
rp = (SRC / "dictate/MaReprompt.kt").read_text()

# The same rules as the Kotlin, walked here.
REAL = {"aftermath", "aftermaths", "shah", "shai"}
KEYS = [["yshai", "afterman"], ["afterman"], ["yshai"]]
TR = str.maketrans({"č": "c", "ć": "c", "ž": "z", "š": "s", "đ": "d"})


def words(t):
    return [w for w in re.split(r"[^a-z0-9]+", t.lower().translate(TR)) if w]


def dist(a, b):
    d = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i in range(len(a) + 1): d[i][0] = i
    for j in range(len(b) + 1): d[0][j] = j
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            c = 0 if a[i - 1] == b[j - 1] else 1
            d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
            if i > 1 and j > 1 and a[i - 1] == b[j - 2] and a[i - 2] == b[j - 1]:
                d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
    return d[len(a)][len(b)]


def close(h, k):
    if h == k: return True
    if h in REAL: return False
    if len(k) <= 3 or len(h) <= 3: return False
    allowed = 2 if len(k) >= 6 else 1
    if abs(len(h) - len(k)) > allowed: return False
    return dist(h, k) <= allowed


def mentions(t):
    w = words(t)
    joined = [a + b for a, b in zip(w, w[1:]) if b in ("man", "men")]
    for key in KEYS:
        if len(key) == 1:
            if any(close(x, key[0]) for x in w) or any(close(x, key[0]) for x in joined): return True
        else:
            for i in range(len(w) - len(key) + 1):
                if all(close(w[i + k], key[k]) for k in range(len(key))): return True
    return False


for said in ["make it Yshai Afterman style", "Ashai Afterman style please", "in the style of Ishai Afterman",
             "rewrite like Jišai Aftermen", "Afterman style", "aftreman", "like Yshai", "use the after man style",
             "Make it like Ashai After man"]:
    check(f"recognised: {said!r}", mentions(said))
for said in ["make it shorter", "the aftermath of the storm", "translate to Croatian", "after the man left",
             "a man after my own heart", "shah of Iran", "Shai Hulud"]:
    check(f"not a code word: {said!r}", not mentions(said))

# The code says the same.
check("same real-word exceptions", 'setOf("aftermath", "aftermaths", "shah", "shai")' in cw)
check("same keys", 'listOf("yshai", "afterman")' in cw and 'listOf("afterman")' in cw and 'listOf("yshai")' in cw)
check("two edits for long words, one for short", "if (key.length >= 6) 2 else 1" in cw)
check("'after man' joined, only with man/men second", 'if (b == "man" || b == "men") a + b else ""' in cw)

# The style, as he asked: normal capitals, the manifest's rules.
desc = cw.replace('" +\n                "', "")
for rule in ["Short paragraphs of one or two sentences", "honest counterweight", "Witness instead of asserting",
             "One image, carried through", "The principle before the request", "Practical facts bare",
             "taking a worry away", "one person as \\\"you\\\"", "Normal sentence capitalisation",
             "Never all lowercase", "No exclamation marks, no emoji, no bullet points, no dashes"]:
    check(f"style holds: {rule}", rule in desc)

check("Reprompt sends the expansion", "MaCodeWords.expand(words.trim())" in rp)

print(f"code words, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
