#!/usr/bin/env python3
"""
Test 1 for the Croatian spell check, the per-prompt model and the Claude cost log.

    python3 scripts/test_spellcheck_costs.py

MaClaudeCost's prices, file round trip and remaining-balance arithmetic were compiled with kotlinc
and run against 9 cases when written. This suite checks the wiring.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
LIB = ROOT / "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


ctrl = code(APP / "dictate/DictateController.kt")
sc = code(APP / "dictate/MaSpellcheck.kt")
cost = code(APP / "dictate/MaClaudeCost.kt")
client = code(LIB / "OpenAiCompatibleClient.kt")
app = code(APP / "FlorisApplication.kt")
prefs = code(APP / "app/AppPrefs.kt")
prompts = code(APP / "app/settings/dictate/MaPromptsScreen.kt")
km = code(APP / "ime/keyboard/KeyboardManager.kt")
order = code(APP / "app/settings/MaSettingsOrder.kt")
order_screen = code(APP / "app/settings/MaSettingsOrderScreen.kt")
routes = code(APP / "app/Routes.kt")

# ---------------------------------------------------------------- spell check
check("on by default", re.search(r'maSpellcheckEnabled = boolean\(\s*key = "[^"]+",\s*default = true', prefs) is not None,
      "he called it mandatory")
check("runs on the dictation path", "croatianSpellcheck(appContext, local)" in ctrl)
check("before the rest of post-processing", ctrl.find("croatianSpellcheck(appContext, local)")
      < ctrl.find("postProcessTranscript(appContext, checked)"))
check("Croatian only", "MaLanguage.active() != MaLanguage.HR" in ctrl)
check("not gated on the rewording switch", "if (text.isBlank() || !MaSpellcheck.enabled()) return text" in ctrl)
check("a bad reply keeps the transcript", "MaSpellcheck.accept(text, corrected)" in ctrl)
check("the guard is a length ratio", "ratio in 0.5..1.5" in sc)
check("still counts as a pure transcript", "finalText == spellchecked" in ctrl,
      "paragraph splitting would stop for every Croatian dictation")
check("never translates", "Never translate." in sc)

# ---------------------------------------------------------------- models per prompt
check("Ctrl+P passes its model", "MaProofread.prompt(), model = MaProofread.model()" in km)
check("Ctrl+F passes its model", "MaFlow.prompt(), model = MaFlow.model()" in km)
check("the override only for Anthropic", "modelOverride != null && account.providerId == MaRoles.REWORDING" in ctrl,
      "a Claude alias sent to another provider would fail")
check("each prompt has a chooser", prompts.count("modelPref = prefs.dictate.") == 5)  # + E>H, 2.11.2026
check("spell check wording is editable", "pref = prefs.dictate.maSpellcheckPrompt" in prompts)

# ---------------------------------------------------------------- switches first (Mantra §1.9)
# Marko, 21.10.2026: on/off switches at the top of every settings screen, fine adjustments under.
_first_switch = prompts.find("FeatureSwitch(")
_first_detail = min(i for i in (prompts.find("MaPhoneModelSection()"), prompts.find("PromptCategory(\n")) if i >= 0)
check("the switches come before any model or wording", 0 <= _first_switch < _first_detail,
      "a switch buried under lists of models (build 399)")
check("both switches are in the top block", prompts.count("FeatureSwitch(\n") == 2
      and prompts.find("pref = prefs.dictate.maSpellcheckEnabled") < _first_detail
      and prompts.find("pref = prefs.dictate.maPhoneStyleEnabled") < _first_detail)

# ---------------------------------------------------------------- costs
check("every answer reports its usage", "claudeUsageListener?.invoke(" in client)
check("the app records it", "MaClaudeCost.record(model, input, output)" in app)
check("attached at start", "MaClaudeCost.attach(this)" in app)
check("each request says what it was for", "MaClaudeCost.purpose = purpose" in ctrl)
check("haiku 5.5 priced", '"claude-haiku-5-5" to Price(0.10, 0.50)' in cost)
check("a newer model is never free", "TIER_FALLBACK[tier]" in cost)
check("no text in the log", "userContent" not in cost)
check("costs screen listed", "MaSettingsEntry.CLAUDE_COSTS," in order)
check("costs screen icon", "MaSettingsEntry.CLAUDE_COSTS -> Icons.Default.History" in order_screen)
check("costs screen route", "MaSettingsEntry.CLAUDE_COSTS -> Routes.Settings.MaClaudeCosts" in order_screen
      and "Settings.MaClaudeCosts::class) { MaClaudeCostScreen() }" in routes)

print(f"spell check and costs, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
