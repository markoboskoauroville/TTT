#!/usr/bin/env python3
"""
Test 1 for the language gate: which words cost money, and which do not.

The claim he made and I am holding to: **a word already in a model is already in the right place.**
Everything else follows from it, and the thing most worth testing is the set of cases that reach the
network, because that set is the bill.

    python3 scripts/test_word_language.py
"""

import itertools
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"

failures: list[str] = []
checks = 0
CROATIAN = "čćžšđ"


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def normalise(w):
    return "".join(c for c in w.lower() if c.isalpha() or c == "'").strip("'")


def decide(word, badge, known_en, known_hr):
    """The port of MaWordLanguage.decide. Returns (kind, payload)."""
    w = normalise(word)
    if not w:
        return ("known", set())
    known = set()
    if known_en:
        known.add("en")
    if known_hr:
        known.add("hr")
    if known:
        return ("known", known)
    if any(c in CROATIAN for c in w):
        return ("certain", "hr")
    return ("ask", badge)


# ---------------------------------------------------------------- what never costs anything
#
# Walked, not sampled: every combination of known-in-English, known-in-Croatian, badge, and a word
# with or without Croatian letters. Sixteen cases, and only two of them may reach the network.
asked = []
for ken, khr, badge, marked in itertools.product([True, False], [True, False], ["en", "hr"], [True, False]):
    word = "čaša" if marked else "table"
    kind, payload = decide(word, badge, ken, khr)
    case = f"ken={ken} khr={khr} badge={badge} marked={marked}"
    if ken or khr:
        check(f"{case}: known words are free", kind == "known", kind)
    elif marked:
        check(f"{case}: Croatian letters are free", kind == "certain" and payload == "hr", f"{kind} {payload}")
    else:
        check(f"{case}: only new unmarked words are asked about", kind == "ask", kind)
        asked.append(case)
check("exactly two of sixteen cases cost money", len(asked) == 2, str(asked))

# Known beats the diacritic rule: a filed word is not re-examined for any reason.
kind, payload = decide("čaša", "en", known_en=True, known_hr=False)
check("known beats the letters", kind == "known" and payload == {"en"}, f"{kind} {payload}")

# Known in both stays in both. Forcing a choice would delete an earned suggestion.
kind, payload = decide("radio", "hr", True, True)
check("a word in both models stays in both", payload == {"en", "hr"}, str(payload))

# There is NO "plain letters means English" rule, and there must not be: most Croatian words have no
# diacritics, so that rule would be right often and wrong constantly.
kind, _ = decide("kolodvor", "hr", False, False)
check("a plain Croatian word is not assumed English", kind == "ask", kind)

# ---------------------------------------------------------------- folding
check("case folds", normalise("Table") == "table")
check("punctuation goes", normalise("word,") == "word")
check("an apostrophe survives inside", normalise("don't") == "don't")
check("an empty word is not asked about", decide("...", "en", False, False)[0] == "known")


# ---------------------------------------------------------------- reading the answers
def read_answer(reply, asked_set):
    out = {}
    for line in reply.splitlines():
        parts = line.strip().lower().split("=")
        if len(parts) != 2:
            continue
        w = normalise(parts[0])
        if w not in asked_set:
            continue
        v = parts[1].strip().strip('."\'')
        if v == "en":
            out[w] = {"en"}
        elif v == "hr":
            out[w] = {"hr"}
        elif v == "both":
            out[w] = {"en", "hr"}
    return out


A = {"table", "kolodvor", "radio"}
check("a clean answer parses", read_answer("table=en\nkolodvor=hr\nradio=both", A) ==
      {"table": {"en"}, "kolodvor": {"hr"}, "radio": {"en", "hr"}})
check("prose is ignored", read_answer("Here you go:\ntable=en\nHope that helps", A) == {"table": {"en"}})
check("a word nobody asked about is refused", read_answer("banana=en", A) == {},
      "a model can add words to his dictionary")
check("an unknown language is dropped", read_answer("table=fr", A) == {}, "filed under a language that does not exist")
check("an empty reply is nothing", read_answer("", A) == {})


# ---------------------------------------------------------------- the wiring
def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


ngram = code(SRC / "dictate/nlp/MaNgram.kt")
check("the gate is on the learning path", "MaWordLanguage.decide(" in ngram, "words bypass it")
check("known words ask nothing", "is MaWordLanguage.Answer.Known -> Unit" in ngram, "pays for what it knows")
check("the sentence is still learned whole", "modelFor(badge).learn(text)" in ngram,
      "sequences lost, which is what an n-gram is for")
check("asking is batched", "BATCH = 60" in ngram, "a request per word")
check("asking is never automatic", "suspend fun classifyPending" in ngram, "fires while typing")
check("the queue is capped", "PENDING_CAP" in ngram, "a leak rather than a queue")
check("the model knows what it knows", "fun knows(" in code(SRC / "dictate/nlp/MaNgramModel.kt"),
      "cannot tell new from old, so everything is asked")

nlp = code(SRC / "ime/nlp/NlpManager.kt")
check("the shipped dictionary is dropped when it disagrees", "if (providerAgrees) {" in nlp,
      "English words under a Croatian badge — the bug in the screenshot")
check("and the personal model is not", "addAll(personal)" in nlp, "his own words dropped too")

setting = code(SRC / "app/settings/dictate/MaNgramSetting.kt")
check("the screen shows both languages", "MaWordLanguage.HR" in setting, "one number hides the empty model")
check("each can be wiped alone", "MaNgram.forget(language)" in setting, "wiping one costs the other")
check("the queue is visible", "MaNgram.pendingCount()" in setting, "no way to see what is waiting")

# ---------------------------------------------------------------- reflow must not translate
#
# He reported reflow returning English for Croatian text, and only noticing after pasting it. The
# instruction never named a language and every style rule in it is English, so the model followed the
# instruction's language rather than the text's.
CRO = "čćžšđ"


def detect(text, badge):
    """The port of MaWordLanguage.detect. Never returns unknown."""
    if any(c in CRO for c in text):
        return "hr"
    return badge


def name_of(code):
    return "Croatian" if code == "hr" else "English"


check("Croatian letters settle it", detect("Idemo na kolodvor u pet, čekaj me", "en") == "hr",
      "the badge would have said English and the reflow would have translated")
check("one accented letter is enough", detect("ovo je čudno", "en") == "hr")
check("plain English follows the badge", detect("meet me at five", "en") == "en")
check("plain Croatian follows the badge", detect("idemo na kolodvor", "hr") == "hr",
      "no diacritics, so the badge is the only evidence there is")
check("it never answers unknown", detect("", "en") in ("en", "hr"),
      "unknown resolves to English every time, which is the bug")
check("the name is a real language name", name_of("hr") == "Croatian" and name_of("en") == "English",
      "the original language is a rule the model has to resolve, and it resolves it to English")

wl = code(SRC / "dictate/nlp/MaWordLanguage.kt")
check("the detector exists", "fun detect(" in wl, "left to the model to infer")
check("it names the language", "fun nameOf(" in wl, "nothing to put in the instruction")

ctrl = code(SRC / "dictate/DictateController.kt")
# The message is built in MaRewordPrompt now, the same function the Prompts screen previews.
rp = code(SRC / "dictate/MaRewordPrompt.kt")
check("the controller sends what MaRewordPrompt builds", "MaRewordPrompt.forSending(instruction, input, answerLanguage)" in ctrl,
      "the preview and the request would be two different texts")
check("every rewording names the language", "Your entire answer must be in" in rp, "silent translation")
check("it forbids translating outright", "Do not translate it into another language" in rp)
# THE ORDERING, which is the whole fix. The language rule must sit AFTER the instruction and the
# system prompt, and immediately before his text.
build_at = rp.find("fun build(")
sys_at = rp.find("if (sys.isNotBlank())", build_at)
lang_at = rp.find("languageLine(", build_at)
check("the language rule comes after the style rules", 0 < build_at < sys_at < lang_at,
      "English written after it drags the output back into English")
check("and immediately before the text", rp.find('append("\\n\\n").append(input)', lang_at) > lang_at,
      "something else sits between the rule and the text it governs")
# The be-precise prompt's "answer in the language of the instruction" contradicted the rule.
check("the instruction-language sentence is dropped when there is text",
      "ANSWER_IN_INSTRUCTION_LANGUAGE" in rp and 'replace(ANSWER_IN_INSTRUCTION_LANGUAGE, "")' in rp,
      "Ctrl+P on Croatian carried an order to answer in English")
check("passage detection, not the recording key", "fun detectPassage(" in wl and "MaWordLanguage.detectPassage(" in rp,
      "Croatian without diacritics was labelled English after an English recording")

# ---------------------------------------------------------------- short phrases and the styles
proof = code(SRC / "dictate/MaProofread.kt")
check("a fragment is still corrected", "A short phrase is still text" in proof,
      "three or four words came back untouched: no capital, no full stop")
check("the fragment rule is read before the unchanged rule",
      proof.index("A short phrase is still text") < proof.index("already correct, return it"),
      "the model reads 'return it unchanged' first and a fragment qualifies")

# ONE VOICE, AND NO LOWERCASE OPTION.
#
# Build 316 offered "Marko" and "Yshai", the second all lower case. prose-voice.md §3 says that
# lowercase is the one thing we deliberately do NOT copy — I turned a note about what not to do into
# a setting offering to do it, and he said so as soon as he saw it. These checks exist so it cannot
# come back.
flow = code(SRC / "dictate/MaFlow.kt")
check("no lowercase rule anywhere", "lower case" not in flow.lower(),
      "the one thing prose-voice.md says not to copy, offered as an option")
check("no style constants", "STYLE_YSHAI" not in flow and "STYLE_MARKO" not in flow,
      "a chooser between variants of a single voice")
check("the prompt is the instruction", "custom().ifBlank { INSTRUCTION }" in flow,
      "something still selects between styles")
check("his own wording is used as written", "custom().ifBlank" in flow,
      "a prompt he wrote would have rules appended to it")

prompts_ui2 = code(SRC / "app/settings/dictate/MaPromptsScreen.kt")
# Scoped to the Mantra section. The bare search found the prompt-wordings picker further up the
# file, which is a different control doing a real job — the same fault as the shift check that found
# MaNextFieldKey. **A claim about one section must be checked inside that section.**
# BOUNDED at both ends. Slicing to end-of-file swept in the PromptCategory helper below it, which
# has a RadioButton of its own doing a real job — so the check failed on correct code twice before
# the bound was added. **An open-ended slice is not a section.**
_m0 = prompts_ui2.index("Grammar correction (Mantra)")
_m1 = prompts_ui2.index("Spacer(Modifier.height(28.dp))", _m0)
mantra = prompts_ui2[_m0:_m1]
check("the chooser is gone from that section", "RadioButton(" not in mantra,
      "a control offering a choice that does not exist")
check("and no style preference is read there", "maProseStyle" not in mantra, "still selecting a variant")
check("the voice is stated instead", "one voice, taken from Yshai Afterman" in prompts_ui2,
      "the section says nothing about what Ctrl+F does")
check("and says which part is not copied", "that is the one thing we do not copy" in prompts_ui2,
      "somebody will re-add the lowercase option next year")

prompts_ui = code(SRC / "app/settings/dictate/MaPromptsScreen.kt")
check("the section is named for Mantra", "Grammar correction (Mantra)" in prompts_ui,
      "indistinguishable from the settings inherited from the fork")


print(f"word language, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
