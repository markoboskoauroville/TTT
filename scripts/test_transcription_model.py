#!/usr/bin/env python3
"""
Transcription model setting (Marko, 7.11.2026):

    python3 scripts/test_transcription_model.py

"use offline transcription services using Whisper Turbo Model ... remove the old model ... Transcription model
is the settings entry. Inside I have AssemblyAI and Whisper ... either AssemblyAI, either Whisper or Whisper
as a fallback if AssemblyAI gets rejected or is not happening for some reason."
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
check("catalog offers Whisper Turbo", "WHISPER_TURBO" in all_block)
check("catalog no longer offers Parakeet", "PARAKEET" not in all_block and "val PARAKEET_TDT_V3" not in catalog)
check("Turbo encoder is the int8 export", "turbo-encoder.int8.onnx" in catalog and "674_716_297" in catalog)
check("Turbo decoder is the int8 export", "turbo-decoder.int8.onnx" in catalog)
check("every Turbo file has a checksum", catalog.count('"b02dcdf5') == 1 and "20accd02" in catalog and "b34b360d" in catalog)
check("Parakeet is deleted from the phone", '"parakeet-tdt-0.6b-v3"' in catalog and "RETIRED_IDS" in (SRC / "FlorisApplication.kt").read_text())

prefs = (SRC / "app/AppPrefs.kt").read_text()
check("engine preference exists", "dictate__ma_transcription_engine" in prefs)

prov = (SRC / "dictate/MaProviders.kt").read_text()
for v in ('"assemblyai"', '"whisper"', '"fallback"'):
    check(f"engine value {v}", v in prov)
check("Whisper choice routes every transcription to the phone", "engine == ENGINE_WHISPER" in prov and "return localTranscriptionAccount()" in prov)
fb = prov.split("fun localFallbackProvider")[1]
check("fallback takes any failure, not only offline", "anyFailure" in fb and "!anyFailure &&" in fb)
check("AssemblyAI-only never falls back", "engine == ENGINE_ASSEMBLYAI) return null" in fb)

order = (SRC / "app/settings/MaSettingsOrder.kt").read_text()
check("settings entry is called Transcription model", '"Transcription model"' in order and "MaSettingsEntry.TRANSCRIPTION_MODEL," in order)
screen = (SRC / "app/settings/dictate/MaTranscriptionScreen.kt").read_text()
for t in ('"AssemblyAI"', '"Whisper"', '"AssemblyAI, Whisper as fallback"'):
    check(f"screen offers {t}", t in screen)
routes = (SRC / "app/Routes.kt").read_text()
check("screen is routed", "MaTranscriptionScreen()" in routes)

if failures:
    print("FAIL")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print(f"OK {checks} checks")
