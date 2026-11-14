#!/usr/bin/env python3
"""
Test 1 for RE's language rule and writing style (MaReStyle, 14.11.2026).

Marko: "I am always talking in English. But re must understand: if the message is in Croatian, it needs to apply all my
corrections in Croatian ... re doesn't understand my style of writing ... no question asked when he's writing."

    python3 scripts/test_re_language_style.py

The prompt rule is checked in the source (no device needed): a Croatian target with an English instruction yields a
prompt that demands Croatian output; the instruction is transcribed as English; the style guide is in every RE prompt.
"""
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


style = code(SRC / "dictate/MaReStyle.kt")
rp = code(SRC / "dictate/MaReprompt.kt")
skill = code(SRC / "dictate/MaReSkill.kt")
chat = code(SRC / "dictate/MaReChat.kt")
eng = code(SRC / "dictate/MaReEngine.kt")
lang = code(SRC / "dictate/MaLanguage.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
act = code(SRC / "dictate/MaReChatActivity.kt")
db = code(SRC / "dictate/MaRemoteDb.kt")
guide = (ROOT / "app/src/main/assets/re_style.md").read_text()

# 1. the rule
check("rule says instructions are English", "speaks his instructions in English, always" in style)
check("rule: instruction language never decides", "never decides the language of the result" in style)
check("rule names Croatian with diacritics", "č ć š ž đ" in style and 'name == "Croatian"' in style)
check("rule: translate only when asked", "Never translate unless the instruction explicitly says so" in style)
check("rule: never ask", "Never ask which language or which style" in style)
check("language detected from the text", "MaRewordPrompt.languageOf" in style)
check("reprompt message ends with the rule", "MaReStyle.rule(text)" in rp)
check("write mode names the language from the screen", "MaReStyle.rule(screen?.text)" in rp)
check("the old 'language of the instruction' fallback is gone", "language of the request unless" not in rp)
check("RE chat system prompt: draft in the target language", "LANGUAGE OF A DRAFT" in chat and "never in the language of his request" in chat)
check("RE chat prompt ends with the rule", "MaReStyle.rule(ctx.screen?.text)" in chat)
check("Qwen rule 1 is the target language", "A DRAFT is in the language of the message it answers" in eng)
check("Qwen rule 1 no longer follows his last message", "Write in the language Marko used in his last message" not in eng)
# 2. the instruction is transcribed as English
check("active() is English for an instruction", "DictateController.instructionInFlight()) return EN" in lang)
check("instructionInFlight covers RE and WhatsApp", "repromptRecording || whatsappRecording" in ctrl)
check("RE chat records English always", "val l = if (isWa) MaLanguage.active() else MaLanguage.EN" in act)
# 3. the style
check("style in the skill preamble", "MaReStyle.block()" in skill)
check("style in the RE chat prompt", "MaReStyle.block()" in chat)
check("the phone copy wins over the asset", "File(MaVault.dir(), FILE)" in style and "assets?.open" in style)
check("the style file is synced to RE_MAGIC_DATABASE", 'path.startsWith("re_style")' in db)
check("the guide has Croatian and English", "## Croatian" in guide and "## English" in guide and "## Never" in guide)
check("the guide never asks", "Never ask" in guide)
check("no key, token or phone number in the guide", not re.search(r"(sk-|ghp_|gsk_|\+?\d{9,})", guide))
check("attached at start", "MaReStyle.attach(this)" in code(SRC / "FlorisImeService.kt"))

if failures:
    print("FAILED:\n" + "\n".join(failures))
    sys.exit(1)
print(f"OK, {checks} checks")
