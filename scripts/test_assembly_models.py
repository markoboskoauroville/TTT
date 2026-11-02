#!/usr/bin/env python3
"""
Test 1 for the newest-AssemblyAI rule (MaAssemblyModels.kt): the family is stored, the version found.

    python3 scripts/test_assembly_models.py

The parsing and the choice are mirrored from MaAssemblyModels.kt and walked against the three
documentation pages as AssemblyAI served them on 2.11.2026 (cut down to their model ids); the
wiring is checked in the source.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
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


# ---------------------------------------------------------------- the mirror
ALIAS = "universal-pro-latest"
FALLBACK = {"ASYNC": "universal-3-5-pro", "SYNC": "universal-3-5-pro", "STREAMING": "universal-3-6-pro"}
OLD = {"universal-3-5-pro", "u3-sync-pro", "u3-pro", "universal-streaming"}
PRO = re.compile(r"^universal-(\d+)(?:-(\d{1,2}))?-pro$")
PRO_IN_TEXT = re.compile(r"universal-\d+(?:-\d{1,2})?-pro\b")


def parse(i):
    m = PRO.match(i.strip().lower())
    return (int(m.group(1)), int(m.group(2) or 0)) if m else None


def ids_in_doc(md):
    out = []
    for x in PRO_IN_TEXT.findall(md.lower()):
        if x not in out:
            out.append(x)
    return out


def is_alias(m):
    s = m.strip().lower()
    return s == "" or s == ALIAS or s in OLD


def newest(av, refused=()):
    c = [(i, parse(i)) for i in (a.strip().lower() for a in av) if i not in refused and parse(i)]
    return max(c, key=lambda p: p[1])[0] if c else None


def resolve(m, path, av, refused=()):
    if not is_alias(m):
        return m.strip()
    return newest(av, refused) or FALLBACK[path]


STREAM_DOC = 'speech_model: "universal-3-6-pro" (Recommended) ... "universal-3-5-pro" ... universal-streaming-english'
ASYNC_DOC = 'speech_models: ["universal-3-5-pro", "universal-2"]'
SYNC_DOC = 'X-AAI-Model enum: universal-3-5-pro; legacy aliases u3-sync-pro, u3-pro'

check("stream page", ids_in_doc(STREAM_DOC) == ["universal-3-6-pro", "universal-3-5-pro"], str(ids_in_doc(STREAM_DOC)))
check("alias on streaming", resolve(ALIAS, "STREAMING", ids_in_doc(STREAM_DOC)) == "universal-3-6-pro")
check("alias on async", resolve(ALIAS, "ASYNC", ids_in_doc(ASYNC_DOC)) == "universal-3-5-pro")
check("alias on sync", resolve(ALIAS, "SYNC", ids_in_doc(SYNC_DOC)) == "universal-3-5-pro")
check("old default upgrades", resolve("universal-3-5-pro", "ASYNC", ["universal-4-pro"]) == "universal-4-pro")
check("old live label upgrades", resolve("universal-streaming", "STREAMING", ["universal-3-7-pro"]) == "universal-3-7-pro")
check("pin respected", resolve("universal-2", "ASYNC", ["universal-4-pro"]) == "universal-2")
check("pin respected (english streaming)",
      resolve("universal-streaming-english", "STREAMING", ["universal-4-pro"]) == "universal-streaming-english")
check("numbers, not text", newest(["universal-3-9-pro", "universal-3-10-pro"]) == "universal-3-10-pro")
check("major beats minor", newest(["universal-3-9-pro", "universal-4-pro"]) == "universal-4-pro")
check("no page -> fallback", resolve(ALIAS, "SYNC", []) == "universal-3-5-pro")
check("refused skipped", resolve(ALIAS, "STREAMING", ["universal-3-7-pro", "universal-3-6-pro"],
                                 {"universal-3-7-pro"}) == "universal-3-6-pro")
check("never a guess", newest(["universal-2", "universal-streaming-english"]) is None)

# ---------------------------------------------------------------- the source agrees with the mirror
src = code(LIB / "MaAssemblyModels.kt")
check("kotlin alias", f'ALIAS = "{ALIAS}"' in src)
for k, v in FALLBACK.items():
    check(f"kotlin fallback {k}", f'Path.{k} to "{v}"' in src)
for o in OLD:
    check(f"kotlin old default {o}", f'"{o}"' in src)
check("kotlin regex", r'"""^universal-(\d+)(?:-(\d{1,2}))?-pro$"""' in src)

# ---------------------------------------------------------------- wiring
cli = code(LIB / "OpenAiCompatibleClient.kt")
check("async resolves", "MaAssemblyCatalog.model(client, request.model, MaAssemblyModels.Path.ASYNC)" in cli)
check("sync resolves", "MaAssemblyCatalog.model(client, request.model, MaAssemblyModels.Path.SYNC)" in cli)
check("sync model is the alias", "const val SYNC_MODEL = MaAssemblyModels.ALIAS" in cli)
check("refused retried on fallback", cli.count("MaAssemblyCatalog.markRefused") >= 2)
rt = code(LIB / "RealtimeClient.kt")
check("streaming sends speech_model", "&speech_model=" in rt and "Path.STREAMING" in rt)
reg = code(LIB / "ProviderRegistry.kt")
check("preset stores the family", "defaultTranscriptionModel = MaAssemblyModels.ALIAS" in reg
      and "defaultRealtimeModel = MaAssemblyModels.ALIAS" in reg)
check("no pinned version as a default", 'defaultTranscriptionModel = "universal-' not in reg)

print(f"{checks} checks, {len(failures)} failed")
for f in failures:
    print("  FAIL", f)
sys.exit(1 if failures else 0)
