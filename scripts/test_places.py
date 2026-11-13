#!/usr/bin/env python3
"""MaPlaces (13.11.2026): streets, towns, post codes offered offline; contacts' names bring their emails."""
import os, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = os.path.join(R, "app/src/main/kotlin/dev/patrickgold/florisboard")
def read(p): return open(os.path.join(K, p), encoding="utf-8").read()
fail = []
def check(ok, msg):
    if not ok: fail.append(msg)

rows = {}
for line in open(os.path.join(R, "app/src/main/assets/dictate/places.tsv"), encoding="utf-8"):
    if line.startswith("#"): continue
    k, n, e, w = line.rstrip("\n").split("\t")
    rows.setdefault((k, n), e)
check(rows.get(("c", "Zagreb")) == "10000", "Zagreb with 10000 in the place book")
check(rows.get(("c", "Split")) == "21000", "Split with 21000")
check("Zagreb" in rows.get(("s", "Ilica"), ""), "Ilica is in Zagreb")
check(("w", "London") in rows, "world cities shipped")

places = read("dictate/nlp/MaPlaces.kt")
check("http" not in places.replace("http://www.apache.org", ""), "MaPlaces never goes online (nothing typed leaves the phone)")
check("opensSentence" in places, "a capital that opens a sentence does not count as a place")
nlp = read("ime/nlp/NlpManager.kt")
check("MaPlaces.suggest(" in nlp and "POSTAL_ADDRESS" in nlp, "NlpManager offers places, address fields get more")
ed = read("ime/editor/EditorInstance.kt")
check("place.isOffered(text)" in ed and "place.spanToReplace(text)" in ed, "a picked place replaces what was typed of it")
book = read("dictate/nlp/MaAddressBook.kt")
check("ofContact(token, isEmailField)" in book and "contactValues" in book, "a contact's name offers the contact's email")
check("MaPlaces.initialize" in read("FlorisApplication.kt"), "MaPlaces loads at start")
if fail:
    print("\n".join("FAIL " + f for f in fail)); sys.exit(1)
print("places ok")
