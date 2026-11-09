#!/usr/bin/env python3
"""
Test 1 for the AssemblyAI Dictation API path.

**Croatian is not one of the 32 languages this endpoint accepts.** A code outside the set is rejected
with 400. So the rule is: English under two minutes goes here, everything else does not.

    python3 scripts/test_dictation_api.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
LIB = ROOT / "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider"

failures: list[str] = []
checks = 0
# From the documentation, 4.9.2026. Recorded here because the whole design turns on it.
SUPPORTED = {"en", "es", "de", "fr", "it", "pt", "tr", "nl", "sv", "no", "da", "fi", "hi", "vi",
             "he", "ur", "ko", "ca", "gl", "ru", "ro", "et", "fa", "yue", "af", "mr", "zu", "xh",
             "nn", "ar", "ja", "zh"}


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def use_dictation(language, enabled=True):
    """The port of maUseDictationPath. The length and format guards are maUseSyncPath's."""
    if not enabled:
        return False
    return language == "en"


# ---------------------------------------------------------------- the language rule
check("croatian is genuinely unsupported", "hr" not in SUPPORTED,
      "if this ever changes, the whole rule changes with it")
check("english goes to dictation", use_dictation("en"))
check("croatian does not", use_dictation("hr") is False, "a 400 on every Croatian recording")
check("the switch turns it off", use_dictation("en", enabled=False) is False)
for code in ("hr", "sr", "bs", "sl", ""):
    check(f"{code!r} stays on the old path", use_dictation(code) is False,
          "an unsupported code is rejected with 400, after the upload")

# Every language the app can be in must reach a path that works.
for lang in ("en", "hr"):
    routed = "dictation" if use_dictation(lang) else "sync-or-async"
    check(f"{lang} is routed somewhere", routed in ("dictation", "sync-or-async"))
    if lang != "en":
        check(f"{lang} is never sent to an endpoint that refuses it", routed != "dictation")


def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


client = code(LIB / "OpenAiCompatibleClient.kt")
prov = code(SRC / "dictate/MaProviders.kt")
ctrl = code(SRC / "dictate/DictateController.kt")
reg = code(LIB / "ProviderRegistry.kt")
prefs = code(SRC / "app/AppPrefs.kt")
screen = code(SRC / "app/settings/dictate/DictateScreen.kt")

# ---------------------------------------------------------------- the request shape
check("the endpoint path is right", '"v1/transcribe/live"' in client, "a 404")
check("the host is the dictation one", "dictation.assemblyai.com" in reg, "wrong service entirely")
check("the key is raw, no Bearer", 'header("Authorization", config.apiKey)' in client,
      "a Bearer prefix is rejected")

# CONFIG BEFORE AUDIO. The endpoint rejects the other order with a 400 that says nothing about
# ordering, and Sync's config comes LAST — so the two siblings differ in exactly this.
_body = client[client.index("transcribeAssemblyAiDictation"):]
_body = _body[:_body.index("private suspend fun transcribeAssemblyAiSync")]
_cfg = _body.find('addFormDataPart("config"')
_aud = _body.find('addFormDataPart("audio"')
check("config is sent before audio", 0 <= _cfg < _aud,
      "the endpoint rejects an audio part that arrives first")
check("the language is stated, not defaulted", 'put("language_codes", JsonArray(listOf(JsonPrimitive("en"))))' in _body,
      "the request would not say what it means")
check("no llm_instruction is sent", "llm_instruction" not in _body,
      "his prose rules would silently apply to every recording")

# ---------------------------------------------------------------- the response
check("the cleaned text is preferred", "llmResponse" in client, "the verbatim text would be used")
check("and the transcript is the fallback", "cleaned.ifBlank {" in client,
      "a failed rewrite returns 200 with text — throwing that away loses a good transcription")
check("a rewrite error is not a failure", "llmError" in client and "throw" not in
      client[client.index("AssemblyDictationDto"):client.index("AssemblyDictationDto") + 400],
      "a non-null llm_error is documented as a success")

# ---------------------------------------------------------------- the wiring
check("the rule exists", "fun maUseDictationPath(" in prov, "nothing decides")
check("it asks only the language", "language == MaLanguage.EN" in prov,
      "restating guards that maUseSyncPath already applied")
check("it is behind the switch", "maDictationApi.get()" in prov, "no way to turn it off")
check("the controller consults it", "MaProviders.maUseDictationPath(MaLanguage.active())" in ctrl,
      "the preset would never be used")
check("it reads the chosen language, not a guess", "MaLanguage.active()" in ctrl,
      "a detector saying English about Croatian turns a good recording into a 400")
check("sync is still the other branch", "ProviderRegistry.ASSEMBLYAI_SYNC" in ctrl,
      "Croatian would have no fast path at all")
check("the preference exists", "maDictationApi = boolean(" in prefs)
check("there is a switch in settings", "prefs.dictate.maDictationApi," in screen, "no way to change it")
check("the summary names the limits", "Croatian and" in screen,
      "a switch that promises speed and quietly excludes half his dictation")

# ---------------------------------------------------------------- it says which text came back
#
# He reported a dictation with no punctuation and no capitals, which is exactly what `text` looks
# like — punctuation and capitalisation are applied BY the cleanup, so what he saw is the verbatim
# fallback. But nothing in the result said whether the rewrite failed or this path never ran, and
# **those two have completely different fixes.**
check("the outcome is logged", "dictation cleaned=" in client,
      "a failed rewrite and an unused path look identical on screen")
check("the log names the error", "llmError=" in client, "no way to tell a timeout from an error")
check("it counts both texts", "verbatim=" in client,
      "a cleaned length of zero is the whole diagnosis")

# ---------------------------------------------------------------- verbatim or corrected
#
# His switch. Until now the code PREFERRED the corrected text and fell back to the verbatim one, so a
# failed rewrite looked exactly like a setting he had chosen — which is how §213a happened. Making it
# a choice is what lets the fallback say it is a fallback.
def which_text(prefer_corrected, cleaned, verbatim):
    if not prefer_corrected:
        return verbatim
    return cleaned or verbatim


check("corrected on, rewrite present", which_text(True, "Four.", "four") == "Four.")
check("corrected off, rewrite present", which_text(False, "Four.", "four") == "four",
      "his choice would be overridden by a rewrite he did not want")
check("corrected on, rewrite empty", which_text(True, "", "four") == "four",
      "a failed rewrite must not lose the transcript")
check("corrected off, rewrite empty", which_text(False, "", "four") == "four")
check("nothing at all is nothing", which_text(True, "", "") == "")

check("the request carries the choice", "val preferCorrected: Boolean = true," in
      (ROOT / "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/ProviderModels.kt").read_text(),
      "the transport cannot know what he picked")
check("the controller sets it from the switch",
      "preferCorrected = prefs.dictate.maCorrectedDictation.get()" in ctrl, "the switch would do nothing")
check("verbatim returns early", "if (!request.preferCorrected) {" in client,
      "the corrected text would be used anyway")
check("the fallback announces itself", "dictation rewrite empty" in client,
      "a failed rewrite is indistinguishable from a setting he chose — which is how this bug hid")
check("there is a switch for it", "prefs.dictate.maCorrectedDictation," in screen, "no way to choose")
check("named by the result, not the endpoint", '"Corrected dictation"' in screen,
      "a setting named after a mechanism")
check("the summary admits what it changes", "becomes four" in screen,
      "a setting that silently changes his words must say so where it is switched on")

print(f"dictation api, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
