#!/usr/bin/env python3
"""
Transcription model setting (Marko, 7.11.2026):

    python3 scripts/test_transcription_model.py

"Transcription model is the settings entry ... either AssemblyAI, either Whisper or Whisper as a fallback if
AssemblyAI gets rejected or is not happening for some reason." Then, compared with Whisper Turbo: "Don't change
anything, parakeet should be fine." So the on-device engine is Parakeet.
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


catalog = (SRC / "dictate/provider/LocalModelCatalog.kt").read_text()
all_block = catalog.split("val all: List<LocalModelSpec> = listOf(")[1].split(")")[0]
check("on-device model is Parakeet (Marko: parakeet should be fine)", "PARAKEET_TDT_V3" in all_block)
check("Parakeet is not deleted from the phone", "RETIRED_IDS" not in (SRC / "FlorisApplication.kt").read_text())
reg = (ROOT / "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/ProviderRegistry.kt").read_text()
check("local provider defaults to Parakeet", 'defaultTranscriptionModel = "parakeet-tdt-0.6b-v3"' in reg)

prefs = (SRC / "app/AppPrefs.kt").read_text()
check("engine preference exists", "dictate__ma_transcription_engine" in prefs)

prov = (SRC / "dictate/MaProviders.kt").read_text()
for v in ('"assemblyai"', '"local"', '"fallback"'):
    check(f"engine value {v}", v in prov)
check("on-phone choice routes every transcription to Parakeet", "engine == ENGINE_LOCAL" in prov and "return localTranscriptionAccount()" in prov)
fb = prov.split("fun localFallbackProvider")[1]
check("fallback takes any failure, not only offline", "anyFailure" in fb and "!anyFailure &&" in fb)
check("AssemblyAI-only never falls back", "engine == ENGINE_ASSEMBLYAI) return null" in fb)

order = (SRC / "app/settings/MaSettingsOrder.kt").read_text()
check("settings entry is called Transcription model", '"Transcription model"' in order and "MaSettingsEntry.TRANSCRIPTION_MODEL," in order)
screen = (SRC / "app/settings/dictate/MaTranscriptionScreen.kt").read_text()
for t in ('"AssemblyAI"', '"Parakeet (on the phone)"', '"AssemblyAI, Parakeet as fallback"'):
    check(f"screen offers {t}", t in screen)
routes = (SRC / "app/Routes.kt").read_text()
check("screen is routed", "MaTranscriptionScreen()" in routes)

if failures:
    print("FAIL")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print(f"OK {checks} checks")
