#!/usr/bin/env python3
"""MaReTraining (13.11.2026): Opus supervises Qwen on everything RE does; on by default on every phone."""
import os, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = os.path.join(R, "app/src/main/kotlin/dev/patrickgold/florisboard")
def read(p): return open(os.path.join(K, p), encoding="utf-8").read()
fail = []
def check(ok, msg):
    if not ok: fail.append(msg)
t = read("dictate/MaReTraining.kt")
check("OPUS_MODEL" in t and "groqAsk(" in t, "Opus supervises, Qwen works")
check("addSkill(v1.skill)" in t and "QWEN_SKILLS.md" in t, "a failed answer becomes a Qwen skill at once")
check("v2.better.ifBlank" in t, "still failing: Marko gets Opus's corrected answer")
check("TRAINING_LOG.md" in t, "every round logged")
check("MaReTraining.skills()" in read("dictate/MaReEngine.kt"), "Qwen reads its skills on every call")
# 22.11.2026 (Marko: "RE works only with API from Anthropic model Sonnet ... Groq is only fallback"): training is off for good.
eng = read("dictate/MaReEngine.kt")
# 23.11.2026: Sonnet is the default; Opus, Haiku or Groq by his choice; Groq is the backup.
check("else -> SONNET" in eng and "else -> SONNET_MODEL" in eng and "HAIKU -> JUDGE_MODEL" in eng, "Sonnet is the default engine; Opus, Haiku, Groq by choice")
check("fun active(c: Context): Boolean = false" in t, "training mode is off for good")
check("MaReTraining.run(" not in read("dictate/MaReChat.kt") and "reChatAnthropic(context, p, status = status)" in read("dictate/MaReChat.kt"), "RE chat asks Sonnet")
dc = read("dictate/DictateController.kt")
check("MaReTraining.run(" in dc and "!MaReTraining.supervising" in dc, "RE instructions go through training; Opus's own calls are not rerouted")
check('"Training mode"' not in read("dictate/ui/MaFeatureRow.kt") and "MaReEngine.setEngine(e)" in read("dictate/ui/MaFeatureRow.kt"), "long press: the four engines, no training switch")
dcx = read("dictate/DictateController.kt")
check("MaReEngine.canFallBack(context)" in dcx and "MaReEngine.groqAsk(context, MaReEngine.FALLBACK_SYSTEM" in dcx and "requestRewordRawDirect(" in dcx, "Qwen answers only when Sonnet failed, with the same prompt")
if fail:
    print("\n".join("FAIL " + f for f in fail)); sys.exit(1)
print("re training ok")
