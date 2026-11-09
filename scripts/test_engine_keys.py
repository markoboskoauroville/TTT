#!/usr/bin/env python3
"""
Talk to AssemblyAI / Talk to Parakeet keys (Marko, 8.11.2026):

    python3 scripts/test_engine_keys.py

"build in for my feature row new buttons: talk to assemblyai talk to, then the name of the local model"
"button for both languages ... the same, only different color, so I know do I speak local or I speak to the network."
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
failures, checks = [], 0


def check(name, ok):
    global checks
    checks += 1
    if not ok:
        failures.append(name)


order = (SRC / "dictate/MaFeatureOrder.kt").read_text()
row = (SRC / "dictate/ui/MaFeatureRow.kt").read_text()
prov = (SRC / "dictate/MaProviders.kt").read_text()
ctrl = (SRC / "dictate/DictateController.kt").read_text()
mig = (SRC / "dictate/data/prefs/DictateLegacyMigrator.kt").read_text()
app = (SRC / "FlorisApplication.kt").read_text()
rows = (SRC / "app/settings/dictate/MaRowsScreen.kt").read_text()

for k in ("RECORD_EN_NET", "RECORD_HR_NET", "RECORD_EN_LOCAL", "RECORD_HR_LOCAL"):
    check(f"{k} is a key", f"    {k}(" in order)
    check(f"{k} drawn on the row", f"MaFeatureKey.{k}" in row)
    check(f"{k} shown in the Rows editor", f"MaFeatureKey.{k} ->" in rows)
check("network keys go to AssemblyAI", "MaFeatureKey.RECORD_EN_NET, MaFeatureKey.RECORD_HR_NET -> MaProviders.ENGINE_ASSEMBLYAI" in row)
check("local keys go to Parakeet", "MaFeatureKey.RECORD_EN_LOCAL, MaFeatureKey.RECORD_HR_LOCAL -> MaProviders.ENGINE_LOCAL" in row)
check("AssemblyAI dot is red, not blue (9.11.2026)", "Color(0xFFE53935)" in row and "4FC3F7" not in row)
check("same key, the dot's colour differs", "MaEngineNetRed" in row and "MaEngineLocalAmber" in row and ".background(dot)" in row)
check("engine override beats the setting", "engineOverride ?: prefs.dictate.maTranscriptionEngine.get()" in prov)
check("every start takes and clears the override",
      "MaProviders.engineOverride = nextRecordingEngine\n        nextRecordingEngine = null" in ctrl)
check("keys reach his rows once", "fun addEngineKeysIfNeeded()" in mig and "addEngineKeysIfNeeded()" in app)

if failures:
    for f in failures:
        print("FAIL", f)
    print(f"{len(failures)} failed of {checks}")
    sys.exit(1)
print(f"engine keys: {checks} checks OK")
