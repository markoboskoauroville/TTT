#!/usr/bin/env python3
"""
Test 1 for the model on the phone, the style pass after dictation and the end of "Default". §191.

    python3 scripts/test_phone_model.py

The JNI bridge (ma_llm.cpp) was built for the host on 20.10.2026 and run with Qwen3 4B Instruct 2507
Q4_0 against the Mac's six artificial-test messages: every answer came back, the rules were reused
from the cache from the second message on (426 of ~500 tokens), and the answer stopped at the end
tag. This suite checks the wiring that run could not see.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"

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
style = code(APP / "dictate/MaPhoneStyle.kt")
models = code(APP / "dictate/llm/MaPhoneModels.kt")
native = code(APP / "dictate/llm/MaLlmNative.kt")
service = code(APP / "dictate/llm/MaLlmService.kt")
client = code(APP / "dictate/llm/MaLocalLlm.kt")
app = code(APP / "FlorisApplication.kt")
prompts = code(APP / "app/settings/dictate/MaPromptsScreen.kt")
prefs = code(APP / "app/AppPrefs.kt")
cpp = (ROOT / "app/src/main/cpp/ma_llm.cpp").read_text()
cmake = (ROOT / "app/src/main/cpp/CMakeLists.txt").read_text()
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
ci = (ROOT / ".github/workflows/build-apk.yml").read_text()
fetch = (ROOT / "tools/fetch-llama-cpp.sh").read_text()

# ---------------------------------------------------------------- no "Default" model
check("the chooser offers no Default", '"" to "Default"' not in prompts)
# One radio button per model, each with the model's own name (Marko, 20.10.2026), so the chosen one
# IS what is in use and no separate line is needed.
check("the chooser is radio buttons", "selected = current == value" in prompts)
check("Claude rows carry the model's own name", "MaClaudeModels.displayName(MaClaudeModels.resolve(alias, cached))" in prompts)
check("phone rows carry the model's own name", '"${m.name} ($state)"' in prompts)
check("no Default wording on screen", "MaPrompts.displayName(inst.name, builtInName)" in prompts)
check("an empty model is named on sight", "if (stored.isBlank()) pref.set(current)" in prompts)
check("and at start", "MaPromptModel.nameBlankModels()" in app)
for f in ("MaProofread", "MaFlow", "MaSpellcheck"):
    check(f"{f}.model() is never null", "ifBlank { null }" not in code(APP / f"dictate/{f}.kt"))

# ---------------------------------------------------------------- the JNI names match
for fn in ("load", "unload", "cancel", "lastStats", "generate"):
    check(f"native {fn} declared", f"external fun {fn}(" in native)
    check(f"native {fn} exported", f"Java_dev_patrickgold_florisboard_dictate_llm_MaLlmNative_{fn}(" in cpp)
check("library name", 'System.loadLibrary("mallm")' in native and "add_library(mallm SHARED" in cmake)

# ---------------------------------------------------------------- its own process
check("service in :llm", 'android:name="dev.patrickgold.florisboard.dictate.llm.MaLlmService"' in manifest
      and 'android:process=":llm"' in manifest)
check("the app does not start in :llm", "if (isLlmProcess()) return" in app)
check("only the service touches the native side",
      all("MaLlmNative." not in code(p) for p in APP.rglob("*.kt") if p.name not in ("MaLlmService.kt", "MaLlmNative.kt")))
check("a cancel reaches the model", "MSG_CANCEL" in client and "MaLlmNative.cancel()" in service)
check("a dead process fails the request", "pending?.completeExceptionally(" in client)

# ---------------------------------------------------------------- the build
check("cmake wired", 'path = file("src/main/cpp/CMakeLists.txt")' in gradle)
check("always optimised", '"-DCMAKE_BUILD_TYPE=Release"' in gradle)
check("no i8mm", "i8mm" not in re.sub(r"#.*", "", cmake), "a phone without it would crash")
check("CI fetches llama.cpp", "bash tools/fetch-llama-cpp.sh" in ci)
check("CI installs the cmake gradle names", "cmake;3.31.6" in ci and 'version = "3.31.6"' in gradle)
check("pinned by commit", re.search(r'COMMIT="[0-9a-f]{40}"', fetch) is not None)

# ---------------------------------------------------------------- the style pass
check("style before the spell check, spell check as fallback",
      "phoneStyle(local) ?: croatianSpellcheck(appContext, local)" in ctrl)
check("style needs its model downloaded", "!MaPhoneModels.isInstalled(context, phone)) return null" in ctrl)
check("a phone model needs no key for the spell check",
      "!MaPhoneModels.isPhone(model) && MaProviders.rewordingApiKey().isBlank()" in ctrl)
check("phone requests never go to Claude",
      "if (MaPhoneModels.isPhone(model)) return requestPhoneModel(" in ctrl)
check("the style model is named by default", 'default = MaPhoneModels.QWEN3_4B.choice' in prefs)
check("the Mac's rules", 'This is an edit, not a summary.' in style and 'Groq (never Grok)' in style)


# ---------------------------------------------------------------- pure functions, walked
def chat_prompt(thinks: bool, system: str, user: str) -> str:
    s = f"<|im_start|>system\n{system.strip()}<|im_end|>\n<|im_start|>user\n{user.strip()}<|im_end|>\n<|im_start|>assistant\n"
    return s + ("<think>\n\n</think>\n\n" if thinks else "")


def clean(a: str) -> str:
    a = re.sub(r"<think>.*?</think>", "", a, flags=re.S).replace("<|im_end|>", "").strip()
    return a.strip('"“”').strip()


def accept(o: str, s: str) -> bool:
    s = s.strip()
    return bool(s) and 0.33 <= len(s) / max(1, len(o.strip())) <= 1.6


check("prompt shape in Kotlin", '"<|im_start|>system\\n"' in models and '"<think>\\n\\n</think>\\n\\n"' in models)
check("8B told not to think", chat_prompt(True, "r", "t").endswith("<think>\n\n</think>\n\n"))
check("4B prompt ends at the assistant tag", chat_prompt(False, "r", "t").endswith("<|im_start|>assistant\n"))
check("think block removed", clean("<think>\nhm\n</think>\n\nDobro.") == "Dobro.")
check("quotes removed", clean('"Dobro jutro."') == "Dobro jutro.")
check("a shortened message passes", accept("ono znači ovaj sutra sutra dolazim", "Sutra dolazim."))
check("a reply that talks is refused", not accept("Sutra dolazim.", "Here is the cleaned message: Sutra dolazim. I removed the filler."))
check("blank is refused", not accept("Sutra.", "  "))

print(f"phone model, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL ", f)
sys.exit(1 if failures else 0)
