#!/usr/bin/env python3
"""
Spoken email addresses and the speech engine (Marko, 8.11.2026):

    python3 scripts/test_spoken_email.py

"Fix the engine and spoken email addresses be corrected to match the address book."
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
failures: list[str] = []
checks = 0


def check(name: str, ok: bool) -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(name)


email = (SRC / "dictate/nlp/MaSpokenEmail.kt").read_text()
book = (SRC / "dictate/nlp/MaAddressBook.kt").read_text()
ctrl = (SRC / "dictate/DictateController.kt").read_text()
fetch = (ROOT / "tools/fetch-sherpa-onnx.sh").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
gate = (SRC / "dictate/audio/SpeechGate.kt").read_text()

check("address book lists its saved emails", "fun savedEmails(): List<String>" in book)
check("every finished dictation passes the email snapper",
      "MaSpokenTime.apply(MaSpokenEmail.apply(MaNameSpelling.apply(MaCyrillic.toLatin(MaSpokenFormat.apply(text)))))" in ctrl)
check("spoken 'at' words", '"at", "et", "@", "majmun"' in email)
check("spoken dot in Croatian", '"točka"' in email)
check("pronouns are never made into an address", "local in PRONOUNS" in email)
check("only known mail domains without a saved match", "domain in MAIL_DOMAINS" in email)
check("a short name must match exactly", "sLocal.length / 4" in email)
check("written addresses snap too", "WRITTEN.replace" in email)
check("a failure leaves the text as dictated", "getOrDefault(text)" in email)

check("sherpa-onnx 2.0.1 fetched", 'VERSION="2.0.1"' in fetch)
check("sherpa-onnx 2.0.1 AAR checksum",
      "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7" in fetch)
check("ORT bridge retagged to the runtime sherpa ships", "retag_elf_verneed.py" in fetch and 'VERS_1.28.2' in fetch)
check("gradle uses the 2.0.1 jar", gradle.count("sherpa-onnx-2.0.1.jar") == 2 and "sherpa-onnx-1.13.3" not in gradle)
check("VAD segment start is Long since 2.0", "seg.start.toInt()" in gate)
check("retag tool present", (ROOT / "tools/retag_elf_verneed.py").exists())

cyr = (SRC / "dictate/nlp/MaCyrillic.kt").read_text()
local = (SRC / "dictate/provider/LocalTranscriptionProvider.kt").read_text()
check("Parakeet Cyrillic is written in Croatian Latin", local.count("MaCyrillic.toLatin(") == 2)
check("Serbian letters map to Gaj Latin", "'ж' to \"ž\"" in cyr and "'љ' to \"lj\"" in cyr and "'џ' to \"dž\"" in cyr)

names = (SRC / "dictate/nlp/MaNameSpelling.kt").read_text()
check("Marco -> Marko, Bosco -> Bosko in every dictation", '"marco" to "marko"' in names and '"bosco" to "bosko"' in names)

tm = (SRC / "dictate/nlp/MaSpokenTime.kt").read_text()
check("'at 330' is written 3:30, minutes in fives only", "minutes % 5 == 0" in tm)
check("his name keeps its capital at a fragment's start", "MaNameSpelling.isName(lower)" in ctrl and "isContactNameWord(first)" in ctrl)

fix = (SRC / "dictate/nlp/MaCroatianFix.kt").read_text()
check("Croatian corrected offline before the model passes", "MaCroatianFix.apply(appContext, latin)" in ctrl)
check("from the keyboard's own dictionary, pairs and triples",
      all(a in fix for a in ('"ime/dict/hr.json"', '"ime/dict/hr_bigrams.txt"', '"ime/dict/hr_trigrams.txt"')))
check("English words and names inside a sentence are left alone", "l in en" in fix and "!sentenceStart" in fix)
check("off the main thread", "withContext(Dispatchers.Default)" in ctrl)

if failures:
    for f in failures:
        print("FAIL", f)
    print(f"{len(failures)} failed of {checks}")
    sys.exit(1)
print(f"spoken email + engine: {checks} checks OK")
