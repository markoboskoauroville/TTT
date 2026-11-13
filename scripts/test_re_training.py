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
check('getBoolean("on", true)' in t, "training mode on by default")
check("OPUS_MODEL" in t and "groqAsk(" in t, "Opus supervises, Qwen works")
check("addSkill(v1.skill)" in t and "QWEN_SKILLS.md" in t, "a failed answer becomes a Qwen skill at once")
check("v2.better.ifBlank" in t, "still failing: Marko gets Opus's corrected answer")
check("TRAINING_LOG.md" in t, "every round logged")
check("MaReTraining.skills()" in read("dictate/MaReEngine.kt"), "Qwen reads its skills on every call")
check("MaReTraining.run(" in read("dictate/MaReChat.kt"), "RE chat goes through training")
dc = read("dictate/DictateController.kt")
check("MaReTraining.run(" in dc and "!MaReTraining.supervising" in dc, "RE instructions go through training; Opus's own calls are not rerouted")
check('"Training mode"' in read("dictate/ui/MaFeatureRow.kt"), "switch in the RE panel")
if fail:
    print("\n".join("FAIL " + f for f in fail)); sys.exit(1)
print("re training ok")
