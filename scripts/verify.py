#!/usr/bin/env python3
"""
Run before every push. Every check here exists because a red build taught it.

    python3 scripts/verify.py

Exit 0 and it is worth pushing. Exit 1 and CI would have told you the same thing five minutes
later, having spent five minutes.

WHY THIS EXISTS
---------------
Six of the last thirty builds were red, and not one of them was a design mistake. They were all the
same shape: a scripted edit that left a duplicate declaration, an orphaned annotation, an import
that was added twice or removed while still used, or a symbol used above the line that declares it.

Brace and paren balancing — the check this project already had — passes for every one of them,
because nothing about the *shape* of the file is wrong. These checks look at what the shape cannot
see.
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin"
PREFS = SRC / "dev/patrickgold/florisboard/app/AppPrefs.kt"

problems: list[str] = []


def fail(where: str, msg: str) -> None:
    problems.append(f"{where}: {msg}")


def changed_files() -> list[Path]:
    """Only what this commit touches. A full-tree scan finds old debts and buries today's mistake."""
    out = subprocess.run(
        ["git", "-C", str(ROOT), "diff", "--name-only", "HEAD"],
        capture_output=True, text=True,
    ).stdout.split()
    staged = subprocess.run(
        ["git", "-C", str(ROOT), "diff", "--cached", "--name-only"],
        capture_output=True, text=True,
    ).stdout.split()
    files = {f for f in out + staged if f.endswith(".kt")}
    return [ROOT / f for f in sorted(files) if (ROOT / f).exists()]


def strip_code(text: str) -> str:
    """
    Comments and string bodies out, so counting is not fooled by prose.

    Scanned character by character with real state, not with regexes applied to the whole file. The
    regex version reported this project's largest file as two braces out when it was balanced, and
    cost three rounds of hunting a fault that did not exist — a checker that is wrong is worse than
    no checker, because it is believed.
    """
    out: list[str] = []
    i, n = 0, len(text)
    in_block = False
    while i < n:
        if in_block:
            if text.startswith("*/", i):
                in_block = False
                i += 2
            else:
                i += 1
            continue
        if text.startswith("/*", i):
            in_block = True
            i += 2
            continue
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        # CHARACTER LITERALS, BEFORE STRINGS.
        #
        # A Kotlin char literal can hold a double quote — `append('"')` — and this scanner did not
        # know about char literals at all, so that quote opened a string that never closed. Found on
        # 21.8.2026 in MaScreenTargets.kt: of its 963 lines, **68 survived stripping**. Every check
        # that reads stripped code had been running on nothing there, silently, and reporting
        # "nothing to report" for a file it could not see.
        #
        # That is the failure the manifest names: a check that finds nothing and a check that runs
        # nothing look identical from outside. This one had looked identical for as long as the file
        # has existed.
        if text[i] == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if text[i] == '"':
            # Raw strings first: inside `\"\"\"…\"\"\"` a lone quote is ordinary text and a backslash
            # escapes nothing, so the ordinary rules would end the string in the wrong place.
            if text.startswith('\"\"\"', i):
                i += 3
                while i < n and not text.startswith('\"\"\"', i):
                    i += 1
                i += 3
                continue
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def check_duplicate_imports(path: Path, text: str) -> None:
    """Red build: `MaKeys` imported twice — 'imported name is ambiguous'."""
    imports = [ln for ln in text.splitlines() if ln.startswith("import ")]
    seen = set()
    for imp in imports:
        if imp in seen:
            fail(path.name, f"duplicate import: {imp.split('.')[-1]}")
        seen.add(imp)


def check_import_order(path: Path, text: str) -> None:
    """Red build: imports hoisted above `package`, which does not compile."""
    lines = text.splitlines()
    pkg = next((i for i, ln in enumerate(lines) if ln.startswith("package ")), None)
    if pkg is None:
        return
    for i, ln in enumerate(lines):
        if ln.startswith("import ") and i < pkg:
            fail(path.name, "an import sits above the package declaration")
            return


def signature(lines: list[str], start: int) -> str:
    """The parameter list of the declaration at [start], however many lines it is spread over.

    Stops at the line where the parens balance, and returns "" for a property, which has none.
    """
    if "(" not in lines[start]:
        return ""
    depth = 0
    collected: list[str] = []
    for line in lines[start:]:
        collected.append(line)
        depth += line.count("(") - line.count(")")
        if depth <= 0:
            break
    joined = " ".join(collected)
    return " ".join(joined.split("(", 1)[1].rsplit(")", 1)[0].split())


def check_duplicate_declarations(path: Path, text: str) -> None:
    """
    Red build: the same preference declared twice, fifteen lines apart — 'conflicting declarations'.

    Scope is tracked by brace depth rather than by indentation. Two earlier versions used indent as a
    proxy and raised 454 and then 106 false alarms on code that compiles perfectly, because Kotlin is
    happy for the same name to exist in two different scopes — and a checker that cries wolf is a
    checker that gets ignored, which is worse than not having one.

    Only declarations sharing an enclosing scope are a conflict.
    """
    code = strip_code(text)
    scope: list[int] = []
    paren_depth = 0
    counter = 0
    seen: set[tuple[tuple[int, ...], str]] = set()
    # Functions as well as properties. Two definitions of one composable compiled fine twice in this
    # project — Kotlin only complains when something calls the ambiguous name, so a duplicate that
    # nothing calls yet sits there silently until an edit wakes it up.
    decl = re.compile(
        r"^\s*(?:private |internal |public |@Volatile |const |lateinit |suspend |inline )*"
        r"(va[lr]|fun) (\w+)\s*[:=(]"
    )

    lines = code.splitlines()
    for index, line in enumerate(lines):
        m = decl.match(line)
        if m:
            # Keyed by name AND the parameter list, because Kotlin allows overloads: `fun clear()`
            # and `fun clear(mode: Mode)` are two functions, not a mistake. Comparing names alone
            # raised seventy complaints about code that has compiled for years.
            #
            # The parameter list is gathered across lines until its parens balance. Reading only the
            # declaring line made every WRAPPED signature look like `sig = ""`, so two real overloads
            # written one parameter per line — the ordinary Compose style — collided with each other.
            # `MaSwitchRow(Boolean pref)` and `MaSwitchRow(String pref, on, off)` were reported as one
            # name declared twice on 21.8.2026, in a file that had compiled for months. A checker that
            # cries wolf is a checker that gets ignored.
            # The KIND is part of the key too. Kotlin keeps properties and functions in separate
            # namespaces, so `val foregroundPackage: StateFlow<String?>` and
            # `fun foregroundPackage(): String?` are two different members of one companion object
            # and have compiled together for months. Both take no parameters, so comparing name and
            # signature alone called them a conflict — reported on 21.8.2026 the moment an unrelated
            # edit brought that file into the checked set.
            kind = "fun" if m.group(1) == "fun" else "prop"
            # Constructor parameters are not declarations in the enclosing scope.
            #
            # `data class InFlight(val audioFile: File, …)` and `data class Recording(val audioFile:
            # File?, …)` both sit at the same brace depth, so this reported `audioFile` as declared
            # twice — on 22.8.2026, on two data classes that had coexisted for months. A parameter
            # list belongs to its own class and cannot collide with anything outside it.
            #
            # Tracked by paren depth: anything declared while a paren is open is a parameter.
            if paren_depth > 0:
                continue
            key = (tuple(scope), kind, m.group(2), signature(lines, index))
            if key in seen:
                fail(path.name, f"declared twice in the same scope: {m.group(2)}")
            seen.add(key)
        for ch in line:
            paren_depth += (ch == "(") - (ch == ")")
            if ch == "{":
                counter += 1
                scope.append(counter)
            elif ch == "}" and scope:
                scope.pop()


def check_annotation_pairs(path: Path, text: str) -> None:
    """Red build: a replacement left the old @Composable above the new one — 'not repeatable'."""
    lines = text.splitlines()
    for i, ln in enumerate(lines):
        if ln.strip() != "@Composable":
            continue
        # what follows, skipping doc comments and other annotations
        j = i + 1
        while j < len(lines):
            nxt = lines[j].strip()
            if nxt.startswith(("/**", "*", "*/")) or nxt == "":
                j += 1
                continue
            break
        else:
            continue
        if lines[j].strip() == "@Composable":
            fail(path.name, f"two @Composable in a row, line {i + 1}")


def check_icon_imports(path: Path, text: str) -> None:
    """Red build: `Icons.Default.X` used with no import, in a file that compiled before."""
    imports = {ln.rsplit(".", 1)[-1].strip() for ln in text.splitlines() if ln.startswith("import ")}
    used = set(re.findall(r"Icons\.(?:Default|Filled|AutoMirrored\.Filled|Outlined)\.(\w+)", strip_code(text)))
    for icon in sorted(used - imports):
        fail(path.name, f"icon used but not imported: {icon}")


def check_delegation_imports(path: Path, text: str) -> None:
    """
    Red build in waiting: an unused-import sweep removed `getValue`.

    Property delegation — `val x by y` — needs getValue and never names it, so any tool that removes
    imports by searching for the symbol deletes it and the file stops compiling with an error
    pointing at the delegation rather than at the import.
    """
    code = strip_code(text)
    imports = {ln.strip() for ln in text.splitlines() if ln.startswith("import ")}
    # Only COMPOSE delegation, named by its right-hand side.
    #
    # This asked whether the file had any `by` at all and whether the word "compose" appeared
    # anywhere in it, and on 21.8.2026 it failed EditorInstance.kt — a file whose delegations are
    # `by FlorisPreferenceStore` and `by context.appContext()`, custom delegates that carry their own
    # getValue and need no import. That file has compiled green for months. It only fired because a
    # one-line change brought the file into the set this checks.
    #
    # A false positive is not free. It has to be argued with every time, and a check that is argued
    # with is a check that stops being read — which is worse than not having it, because the real
    # hits arrive with the same voice as the false ones.
    #
    # The red builds this exists for were all Compose: `by remember`, `by mutableStateOf`,
    # `by collectAsState`. Those are what it looks for now.
    compose_delegation = re.search(
        r"\b(?:val|var)\s+\w+(?:\s*:\s*[\w<>?., ]+)?\s+by\s+"
        r"(?:remember|mutableStateOf|mutableIntStateOf|mutableLongStateOf|mutableFloatStateOf|"
        r"derivedStateOf|animate\w*AsState|\w[\w.]*\.collectAsState)",
        code,
    )
    if compose_delegation and "import androidx.compose.runtime.getValue" not in imports:
        fail(path.name, "uses Compose `by` delegation but does not import getValue")
    # setValue, the same way: a `var` delegated to Compose state, not any `var` in a file that
    # happens to contain the word.
    compose_var = re.search(
        r"\bvar\s+\w+(?:\s*:\s*[\w<>?., ]+)?\s+by\s+"
        r"(?:remember|mutableStateOf|mutableIntStateOf|mutableLongStateOf|mutableFloatStateOf)",
        code,
    )
    if compose_var and "import androidx.compose.runtime.setValue" not in imports:
        fail(path.name, "uses Compose `var ... by` delegation but does not import setValue")


def check_preferences_exist(path: Path, text: str) -> None:
    """Red build: a row named a preference that had been renamed out from under it."""
    if not PREFS.exists() or path == PREFS:
        return
    declared = set(re.findall(r"\bva[lr]\s+(\w+)\s*=\s*(?:boolean|string|int|long|float|enum|custom)\(", PREFS.read_text()))
    if not declared:
        return
    used = set(re.findall(r"prefs\.\w+\.(\w+)\b", strip_code(text)))
    known_other = {"get", "set", "collectAsState"}
    for name in sorted(used - declared - known_other):
        # only complain about things that look like our own preferences
        if name.startswith("ma"):
            fail(path.name, f"preference does not exist in AppPrefs: {name}")


def is_new_file(path: Path) -> bool:
    """True when HEAD has never seen this file."""
    rel = path.relative_to(ROOT).as_posix()
    r = subprocess.run(["git", "-C", str(ROOT), "cat-file", "-e", f"HEAD:{rel}"], capture_output=True)
    return r.returncode != 0


def check_symbols_resolve(path: Path, text: str) -> None:
    """
    Red build: a block moved to a new file left four symbols behind.

    Moving code is the one edit that breaks references without touching the line that uses them. The
    old file keeps compiling — it lost only the caller — while the new file names constants, classes
    and helpers that are no longer in scope, and nothing about either file's shape looks wrong.

    Every capitalised name the file uses must be imported, declared here, or reachable in the same
    package. The allow-list below is the language itself plus enum members reached through a
    receiver, which cannot be told apart from types by regex alone.
    """
    # NEW files only, and that scope is the point.
    #
    # Run over the whole tree this raises 256 complaints on code that has compiled for years —
    # annotations, nested types, generated references, things a regex cannot see the shape of. Run
    # over a file that did not exist before this commit it is exact, and a file that did not exist
    # before is precisely when code has been MOVED, which is the only edit that breaks references
    # without touching the line that uses them.
    if not is_new_file(path):
        return
    code = strip_code(text)
    code = "\n".join(ln for ln in code.splitlines() if not ln.startswith(("import ", "package ")))
    used = set(re.findall(r"\b([A-Z][A-Za-z0-9_]{2,})\b", code))
    # Exact final segment, and that is the point: matching `SnyggBox` as an import of `Box` is how
    # a missing import was reported as present. A suffix match is not a match.
    imported = {ln.rsplit(".", 1)[-1].strip() for ln in text.splitlines() if ln.startswith("import ")}
    declared = set(re.findall(r"\b(?:val|const val|fun|object|class|enum class|interface)\s+(\w+)", text))

    # Same package: anything declared in a sibling file needs no import.
    siblings: set[str] = set()
    for f in path.parent.glob("*.kt"):
        siblings |= set(re.findall(r"\n(?:internal |private )?(?:object|class|enum class|interface|data class) (\w+)", f.read_text()))

    builtins = {
        "String", "Boolean", "Int", "Long", "Float", "Double", "List", "Set", "Map", "Unit", "Any",
        "Exception", "Throwable", "Pair", "Triple", "Array", "IntArray", "Regex", "Result", "Char",
        "FloatArray", "LongArray", "ByteArray", "CharArray", "Math", "Byte", "Short", "Number",
        "Comparable", "Iterable", "Sequence", "Collection", "MutableList", "MutableSet", "MutableMap",
    }
    unknown = sorted(used - imported - declared - siblings - builtins)
    # Enum members and companion constants arrive as ALL_CAPS through a receiver; a regex cannot see
    # the receiver, so they are reported only if nothing anywhere declares them.
    unknown = [u for u in unknown if not u.isupper()]
    # Nested types reached through a receiver — `DictateApiException.Kind.NETWORK` — read as a bare
    # `Kind` to a regex, and the outer type is what actually needs importing. If the name always
    # appears with something before the dot, it is not the thing to complain about.
    unknown = [u for u in unknown if re.search(rf"(?<![.\w])\b{re.escape(u)}\b", code)]
    for u in unknown:
        fail(path.name, f"uses `{u}` with no import, declaration or sibling in this package")


def check_imports_resolve(path: Path, text: str) -> None:
    """
    Red build: an import that exists but points at the wrong package.

    `check_symbols_resolve` asks whether a symbol has an import. It does NOT ask whether that import
    is true — and a moved block carries its imports with it, so a name that lived in a different
    package in the old file arrives looking perfectly well imported. That cost a second red build on
    the same extraction.

    New files only, same reasoning as above: this is the failure that happens when code moves.
    """
    if not is_new_file(path):
        return
    for ln in text.splitlines():
        if not ln.startswith("import "):
            continue
        fq = ln[len("import "):].strip()
        # Only imports this repository could satisfy. Everything else comes from a dependency whose
        # source is not here, and reporting those as unresolved is reporting that a library exists
        # elsewhere — which is true and useless. `jetpref` and `kotlinx` sit outside `app/` and
        # `lib/`, and the first version of this check called both of them broken.
        if "*" in fq or not fq.startswith("dev.patrickgold.florisboard."):
            continue
        pkg, name = fq.rsplit(".", 1)
        hits = subprocess.run(
            ["grep", "-rl", f"^package {pkg}$", "--include=*.kt", "app/", "lib/"],
            capture_output=True, text=True, cwd=ROOT,
        ).stdout.split()
        decl = re.compile(rf"\b(?:object|class|interface|enum class|data class|val|fun) {re.escape(name)}\b")
        if not any(decl.search((ROOT / h).read_text()) for h in hits):
            fail(path.name, f"import does not resolve: {fq}")


def check_compose_helpers(path: Path, text: str) -> None:
    """
    Red build: `remember` used with no import.

    `check_symbols_resolve` only looks at capitalised names, because a regex cannot tell a lowercase
    function call from a variable. But a short list of Compose helpers is used constantly, always
    needs an import, and is easy to miss when a block is pasted from another file — so those are
    checked by name.
    """
    helpers = [
        "remember", "rememberCoroutineScope", "rememberLazyListState", "rememberScrollState",
        "mutableStateOf", "mutableIntStateOf", "mutableFloatStateOf", "collectAsState",
        "derivedStateOf", "produceState", "rememberUpdatedState",
    ]
    code = strip_code(text)
    imports = {ln.rsplit(".", 1)[-1].strip() for ln in text.splitlines() if ln.startswith("import ")}
    for h in helpers:
        # A call, not a mention: followed by an opening bracket or brace.
        # Not a declaration. A file may define its own `remember` — one here does — and a function
        # is not a missing import just because it shares a name with a Compose helper.
        if re.search(rf"\bfun {h}\b", code):
            continue
        if re.search(rf"(?<![.\w]){h}\s*[({{]", code) and h not in imports:
            fail(path.name, f"`{h}` used without an import")


def check_balance(path: Path, text: str) -> None:
    """The old check, kept: braces and parens against HEAD rather than against zero."""
    rel = path.relative_to(ROOT).as_posix()
    head = subprocess.run(
        ["git", "-C", str(ROOT), "show", f"HEAD:{rel}"], capture_output=True, text=True
    ).stdout
    now = strip_code(text)
    was = strip_code(head) if head else ""
    for ch_open, ch_close, label in (("{", "}", "braces"), ("(", ")", "parens")):
        d_now = now.count(ch_open) - now.count(ch_close)
        d_was = was.count(ch_open) - was.count(ch_close) if head else 0
        if d_now != d_was:
            fail(path.name, f"{label} unbalanced: {d_now} now, {d_was} at HEAD")


def check_removed_declarations(path: Path, text: str) -> None:
    """
    RED BUILD 268, and it cost him one: a scripted range cut removed declarations still in use.

    Two edits replaced a range by naming its two ends — "from this comment to that LaunchedEffect" —
    and the second end was further down the file than the thing being removed. Six live declarations
    went with it: `autoRank`, `clipKeysPresent`, `learn`, `magicRowShown`, `magicAll`, `magicTargets`.
    Sixteen unresolved references, every one of them a symbol the cut had deleted.

    This is the failure the handoff names — *cutting code by eye fails* — and none of the other
    checks here could see it. Braces balance perfectly when a whole declaration is removed. Nothing
    was duplicated, no import was orphaned, the file is well formed. It is simply missing something
    the rest of it still uses.

    So: every `val`/`var` declared at HEAD and not declared now, whose bare name is still referenced
    in the file, is a cut that took too much.

    Bare name only. `MaClipCapture.autoRank` is a qualified reference to somebody else's property and
    is not this file's business — a local declaration deliberately moved into an object would
    otherwise report itself forever.
    """
    rel = path.relative_to(ROOT).as_posix()
    head = subprocess.run(
        ["git", "-C", str(ROOT), "show", f"HEAD:{rel}"], capture_output=True, text=True
    ).stdout
    if not head:
        return
    # Parameters count as declarations too.
    #
    # This reported `file` as removed-but-used when a local `val file` became a function PARAMETER of
    # the same name — a rename in shape only, and correct code. The check is about a name that has
    # nothing declaring it any more, and a parameter declares it just as a val does.
    def declared(t: str) -> set:
        vals = set(re.findall(r"^\s*(?:private\s+|internal\s+)?(?:val|var)\s+(\w+)", t, re.M))
        params = set(re.findall(r"^\s*(\w+)\s*:\s*[\w<>?., ()\[\]-]+,?\s*$", t, re.M))
        return vals | params
    now = strip_code(text)
    removed = declared(strip_code(head)) - declared(now)
    # Objects declared in THIS file. A reference to `MaClipCapture.autoRank` inside MaClipCapture.kt
    # is this file's business even though it is written with a dot, and skipping it is how the first
    # version of this check reported three of the four names the red build actually failed on.
    local_objects = set(re.findall(r"^\s*(?:private\s+|internal\s+)?object\s+(\w+)", now, re.M))
    for name in sorted(removed):
        # NOT a named argument. `style = MaterialTheme.typography...` is a parameter called style,
        # not a use of a local called style — and after a local `style` was removed this check
        # reported it as still used, on a file where every remaining hit was somebody else's
        # parameter name. A check that cannot tell a use from a label is a check that fires on
        # ordinary Compose code, which is most of this app.
        bare = re.search(r"(?<![.\w])" + re.escape(name) + r"\b(?!\s*=[^=])", now)
        qualified = any(
            re.search(r"(?<![.\w])" + re.escape(obj) + r"\." + re.escape(name) + r"\b", now)
            for obj in local_objects
        )
        if bare or qualified:
            fail(path.name, f"declaration of `{name}` was removed but it is still used")


def check_nullable_args(path: Path, text: str) -> None:
    """
    RED BUILD 273: `contentDescription = null` passed to a parameter declared
    `contentDescription: String`.

    Kotlin catches it instantly; CI took five minutes and one build to say so. Nothing here could see
    it because no check read one file's call against another file's declaration.

    **The first version of this was worse than useless.** It collected parameter names across the
    repo and flagged `name = null` wherever the name was non-null in the app's own code — which
    immediately fired on four `Icon(contentDescription = null)` calls, where `Icon` is Compose's and
    its parameter IS nullable. Four false alarms on correct code, on the first run.

    So it reads the CALL, not just the name: the innermost unclosed `Name(` before the `= null`, and
    only if `Name` is a function declared in this repo and that parameter is non-null in its
    signature. A call to anything from a library is left alone, because this file cannot see those
    signatures and a check that guesses is a check that gets ignored.
    """
    sigs: dict[str, dict[str, bool]] = {}
    for src in sorted(ROOT.glob("app/src/main/kotlin/**/*.kt")):
        body = src.read_text()
        for m in re.finditer(r"\bfun\s+(\w+)\s*\(", body):
            name, i, depth = m.group(1), m.end() - 1, 0
            for j in range(i, min(len(body), i + 4000)):
                depth += (body[j] == "(") - (body[j] == ")")
                if depth == 0:
                    params = body[i + 1:j]
                    break
            else:
                continue
            found: dict[str, bool] = {}
            for pm in re.finditer(r"(\w+)\s*:\s*([\w<>., ]+?)(\?)?\s*(?:=[^,]*)?(?:,|$)", params):
                found[pm.group(1)] = pm.group(3) != "?"
            sigs.setdefault(name, {}).update(found)

    code = strip_code(text)
    for m in re.finditer(r"(\w+)\s*=\s*null\b", code):
        # Walk back for the innermost call this argument belongs to.
        depth, k, owner = 0, m.start(), None
        while k > 0:
            k -= 1
            if code[k] == ")":
                depth += 1
            elif code[k] == "(":
                if depth == 0:
                    cm = re.search(r"(\w+)\s*$", code[:k])
                    owner = cm.group(1) if cm else None
                    break
                depth -= 1
        if owner and sigs.get(owner, {}).get(m.group(1)) is True:
            fail(path.name, f"`{m.group(1)} = null` but {owner} declares it non-null")


# check_property_call was written here and DELETED before it ever shipped.
#
# Build 276 went red on `locale.displayLanguage` where `displayLanguage()` is a function — one
# character, five minutes of CI, one paid build. The obvious guard is to collect the repo's function
# names and flag any `.name` used without parentheses.
#
# Swept over the whole app it produced **434 hits**, essentially all of them correct code: a name
# that is a function in one file is a property on a library type in another, and this script cannot
# see library signatures to tell them apart.
#
# A check with 434 false positives is not a weak check, it is a harmful one. It would have to be
# argued with on every file, and a check that is argued with stops being read — at which point the
# real hits arrive in the same voice as the noise. The same reasoning that narrowed the delegation
# check and the duplicate-declaration check applies here, and here it ends in deletion rather than
# narrowing, because there is nothing left to narrow it BY.
#
# This gap stays open. Kotlin catches it instantly and CI reports it in five minutes; that is the
# cost, and it is smaller than the cost of a script nobody believes.


# A NOTE WHERE A CHECK WOULD GO, for the third time today.
#
# Build 279 went red on `Arrangement.Center` with no `import androidx.compose.foundation.layout.
# Arrangement`. The guard is obvious — list the capitalised symbols used, list what is imported,
# report the difference — and it is the same trap as check_property_call: this script cannot see
# what a wildcard import, a same-package declaration or a Kotlin stdlib name brings in, so the
# difference is full of names that resolve perfectly well.
#
# Measured before writing it rather than after: `check_symbols_resolve` already covers the cases
# where the answer is knowable from inside this repo, and widening it to every androidx symbol
# reintroduces the noise that got check_property_call deleted an hour earlier.
#
# The gap stays open. Kotlin names the symbol and the line; CI reports it in five minutes.


def check_weight_scope(path: Path, text: str) -> None:
    """
    RED BUILD 292: `Modifier.weight(1f)` in a composable that is CALLED into a Row rather than
    declaring one.

    `weight` is a `RowScope`/`ColumnScope` extension. A composable called into a Row does not have
    that scope — the caller does — so it does not resolve, and the fix is either a `RowScope.`
    receiver or not using it. Kotlin says `Unresolved reference 'weight'` and CI says it five minutes
    later.

    Measured before writing, and the FIRST measurement was wrong: it looked for `Row(` and missed
    `Row {`, reporting a correct file. With the trailing-lambda form counted the sweep went to zero.
    Worth recording because a candidate check that reports one hit is exactly where the temptation
    is to accept it and move on — the hit was the detector's fault, not the code's.
    """
    code = strip_code(text)
    for m in re.finditer(r"^(private |internal )?fun ([\w.]*?)(\w+)\s*\(", code, re.M):
        start = m.start()
        ends = [x for x in (code.find("\nfun ", start + 1), code.find("\nprivate fun ", start + 1)) if x > 0]
        end = min(ends) if ends else len(code)
        body = code[start:end]
        if ".weight(" not in body:
            continue
        receiver = m.group(2) or ""
        if "RowScope" in receiver or "ColumnScope" in receiver:
            continue
        if re.search(r"\b(Row|Column|LazyRow|LazyColumn|SnyggRow|SnyggColumn)\s*[({]", body):
            continue
        fail(path.name, f"`weight` in {m.group(3)}, which is not in a Row or Column scope")


def check_modifier_clip_import(path: Path, text: str) -> None:
    """
    RED BUILD 309: `Modifier.…clip(CircleShape)` with no `import androidx.compose.ui.draw.clip`.

    The fifth build lost to a missing Compose import, and the closed-list check added for
    `foundation.layout` and `compose.runtime` did not cover it because `clip` lives in a third
    package.

    **Narrower than those two, deliberately.** A bare `.clip(` sweep found four hits across the app —
    `canvas.rotate`, `paint.alpha` and friends — all of them ordinary View and Canvas calls that have
    nothing to do with Compose. So this looks only for a `.clip(` inside a MODIFIER CHAIN: `Modifier`
    on the same line or within the three above it. Measured that way the sweep is zero.

    One name rather than a list, because `clip` is the only one of that package this codebase uses.
    A list of names nobody calls is a list nobody maintains.
    """
    imports = {ln.strip() for ln in text.splitlines() if ln.startswith("import ")}
    if any(i == "import androidx.compose.ui.draw.*" for i in imports):
        return
    if "import androidx.compose.ui.draw.clip" in imports:
        return
    lines = strip_code(text).splitlines()
    for i, line in enumerate(lines):
        if ".clip(" not in line:
            continue
        window = "\n".join(lines[max(0, i - 3):i + 1])
        if "Modifier" in window:
            fail(path.name, "`Modifier.clip` used but androidx.compose.ui.draw.clip is not imported")
            return


def check_suspend_in_lock(path: Path, text: str) -> None:
    """
    RED BUILD 287: `prefs…set(…)` inside `synchronized(askLock) { … }`.

    `set` on a preference suspends, and Kotlin refuses a suspension point inside a critical section —
    rightly, because a coroutine that suspends holding a monitor can resume on another thread and try
    to release a lock it does not own.

    Narrow, and measured before being written, like `check_layout_imports` and unlike the two guards
    that were deleted: it looks only for a `.set(` inside a `synchronized(…) { … }` body, which is the
    exact shape that failed. Swept over the whole app after the fix it found **zero**, so it is
    silent until somebody writes the same thing again.

    The fix that shape wants is always the same and is worth stating: decide inside the lock, write
    outside it.
    """
    code = strip_code(text)
    for m in re.finditer(r"synchronized\s*\(", code):
        i = code.find("{", m.end())
        if i < 0:
            continue
        depth, j = 0, i
        while j < len(code):
            depth += (code[j] == "{") - (code[j] == "}")
            if depth == 0:
                break
            j += 1
        body = code[i:j]
        if re.search(r"\.set\s*\(", body):
            fail(path.name, "a suspending `.set(` inside synchronized: decide in the lock, write outside")


def check_settings_default(path: Path, text: str) -> None:
    """Every MaSettingsEntry must appear in DEFAULT.

    `MaSettingsOrder.parse` builds its answer from the stored order plus `DEFAULT`, and returns
    nothing else. **An entry missing from DEFAULT can therefore never be shown**, however correctly
    it is declared, iconed and routed.

    That is what happened to the cloud reader: the enum entry existed, the icon existed, the route
    existed and was registered, the screen compiled — and the menu item was simply absent, with
    nothing anywhere to say why. `check_when_coverage` was satisfied, because every `when` had its
    branch; the missing thing was membership of a list, which no exhaustiveness check looks at.

    KEYS is exempt: `parse` filters it out deliberately, because it is a section inside PERMISSIONS
    and two doors to one room is one too many.
    """
    if path.name != "MaSettingsOrder.kt":
        return
    entries = set(re.findall(r"^\s{4}([A-Z_0-9]+)\(\"", text, re.M))
    m = re.search(r"val DEFAULT: List<MaSettingsEntry> = listOf\((.*?)\n    \)", text, re.S)
    if not m:
        problems.append(f"{path.name}: DEFAULT list not found — this check is not running")
        return
    listed = set(re.findall(r"MaSettingsEntry\.([A-Z_0-9]+)", m.group(1)))
    missing = entries - listed - {"KEYS"} - _hidden_entries(text)
    for name in sorted(missing):
        problems.append(
            f"{path.name}: `{name}` is a settings entry but is not in DEFAULT — it can never be shown"
        )



def _hidden_entries(text: str) -> set:
    """Entries `parse` hides on purpose (its `val hidden = setOf(...)`): sections inside other screens."""
    m = re.search(r"val hidden = setOf\((.*?)\)", text, re.S)
    return set(re.findall(r"MaSettingsEntry\.([A-Z_0-9]+)", m.group(1))) if m else {"KEYS"}

def check_settings_entry_reachable(path: Path, text: str) -> None:
    """Every settings entry must appear in DEFAULT, or nothing ever shows it.

    `MaSettingsOrder.parse` returns his stored order plus `DEFAULT.filterNot { it in wanted }`. An
    entry that is in the enum and NOT in DEFAULT is therefore invisible on any phone that has ever
    saved an order — which is his phone, and every phone that has opened this screen once.

    It compiles, it has an icon, it has a route, `verify.py` was clean and 34 checks passed. **The
    entry simply never appeared**, and the only symptom was him saying he could not see it.

    This is the second time in three days that a feature shipped complete and unreachable: build 369
    crashed on a missing annotation, and the cloud reader's menu entry was missing from one list.
    Both compiled. **Neither was a logic error — both were a thing not added to a second place.**
    """
    if path.name != "MaSettingsOrder.kt":
        return
    code = re.sub(r"^\s*//.*$", "", text, flags=re.M)
    entries = set(re.findall(r"^\s{4}([A-Z_0-9]+)\(\"", code, re.M))
    m = re.search(r"val DEFAULT[^=]*=\s*listOf\((.*?)\n\s*\)", code, re.S)
    if not m or not entries:
        problems.append(f"{path.name}: could not read the entries or DEFAULT — this check ran nothing")
        return
    in_default = set(re.findall(r"MaSettingsEntry\.([A-Z_0-9]+)", m.group(1)))
    # KEYS is deliberately excluded by parse: it is a section inside PERMISSIONS now.
    missing = entries - in_default - {"KEYS"} - _hidden_entries(text)
    for name in sorted(missing):
        problems.append(
            f"{path.name}: settings entry `{name}` is not in DEFAULT — it will never appear for "
            f"anyone who has saved an order"
        )


def check_route_deeplink(path: Path, text: str) -> None:
    """Every route handed to composableWithDeepLink must carry @Deeplink.

    `composableWithDeepLink` does `requireNotNull(... as? Deeplink)`, so a route object without the
    annotation throws while the nav graph is being built — **which is while the app is starting.**
    The app installs, launches, and dies, with nothing on screen to say why.

    That is what build 367 did. The route had `@Serializable` and not `@Deeplink`, it compiled
    cleanly, every test passed, CI was green, and the app would not open.

    Cheap to check and impossible to see by eye: the annotation sits three lines above the object and
    every neighbour has one, which is exactly the shape a reader's eye completes for itself.
    """
    if path.name != "Routes.kt":
        return
    # Comments stripped FIRST. A comment between the annotations and the object breaks the run of
    # `@Thing` tokens the pattern walks, so an annotated route reads as unannotated — and worse, a
    # route whose @Deeplink is separated from it by a comment would read as annotated when the
    # annotation is actually attached to nothing.
    #
    # Found by writing a comment between @Serializable and @Deeplink on the very route this check was
    # written for. **The check was blind to the shape its own author produced ten minutes later.**
    text = re.sub(r"^\s*//.*$", "", text, flags=re.M)
    registered = set(re.findall(r"composableWithDeepLink\(Settings\.(\w+)::class", text))
    for name in sorted(registered):
        # The object's declaration and whatever annotations sit above it.
        # `object` OR `data class` OR `class`. The first version matched only `object`, so five
        # routes declared as data classes were silently unchecked — the check would have passed a
        # data-class route missing its annotation, which crashes exactly the same way.
        #
        # Found by a sweep, not by the check: the sweep's own regex had the identical blind spot and
        # reported those five as broken. **Confirming a sweep hit against the source is what turned a
        # false positive into a real gap in the shipped check.**
        m = re.search(
            rf"((?:\s*@\w+(?:\([^)]*\))?\s*)*)\b(?:object|data class|class) {name}\b", text)
        if m and "@Deeplink" not in m.group(1):
            problems.append(
                f"{path.name}: route `{name}` is registered with composableWithDeepLink but has no "
                f"@Deeplink — this throws at startup"
            )


def check_preference_collect(path: Path, text: str) -> None:
    """A preference's `collectAsState` is jetpref's, not Compose's.

    Both are called `collectAsState`, both are importable, and only one works on a `PreferenceData` —
    so the wrong import gives four errors on one line, none of which names the real cause. Build 367
    spent a CI round on it.

    Measured at zero across the app before shipping.
    """
    code = strip_code(text)
    if not re.search(r"prefs\.\w+\.\w+\.collectAsState\(\)", code):
        return
    if "import dev.patrickgold.jetpref.datastore.model.collectAsState" not in text:
        problems.append(
            f"{path.name}: reads a preference with collectAsState but imports only Compose's"
        )


def check_layout_imports(path: Path, text: str) -> None:
    """
    RED BUILD 283: `Column` used with no `import androidx.compose.foundation.layout.Column`.

    The third time today a missing Compose import cost a build, and twice the general guard was
    measured, found to be full of noise, and left unwritten — a symbol this script cannot resolve
    might come from a wildcard import, the same package, or the Kotlin stdlib.

    **This is the narrow version that survives measurement.** A closed list of six layout names that
    always need this exact import, are never declared in this app's packages, and are never stdlib.
    Swept over the whole app it produced ONE hit: `MaRows.kt`, which declares its own `Row` type —
    so a file that declares the name is skipped, and the count went to zero.

    Measured before it was written rather than after, which is the only thing separating a guard from
    the 434-hit noise that got `check_property_call` deleted.
    """
    # foundation.layout and compose.runtime, in one pass. Both are closed lists of names that always
    # need their exact import, are never Kotlin stdlib, and are never same-package here.
    #
    # The runtime half was added after build 290, which went red on `mutableStateOf` and `remember`
    # with no imports — the fourth missing-Compose-import build. Measured first, as always: it found
    # two hits across the app, `MaLivePrompts.remember` and `MaAiPredict.remember`, both of them
    # functions those files DECLARE. The declaration skip below already handles that, and the sweep
    # went to zero.
    # Compose's Icon and Icons. Added after build 350 went red on both, in a composable added to a
    # file that had never drawn one.
    #
    # Measured first: one hit across the app, `ThemeManager.kt`, which imports
    # `android.graphics.drawable.Icon` — a DIFFERENT Icon entirely. So a file that already imports
    # the name from anywhere is skipped, and the sweep went to zero. **A closed list still has to ask
    # whether the name means what you think in that file.**
    groups = {
        # This app's own nlp package. Added after build 314 went red on `MaWordLanguage` used with
        # no import — the edit that added the reference used an anchor that did not exist in that
        # file, so the import was silently never inserted and nothing noticed until CI.
        #
        # Same closed-list discipline, measured first: zero hits across the app.
        "dev.patrickgold.florisboard.dictate.nlp": [
            "MaWordLanguage", "MaNgram", "MaNgramModel", "MaAiPredict",
        ],
        "androidx.compose.material3": ["Icon"],
        "androidx.compose.material.icons": ["Icons"],
        "androidx.compose.foundation.layout": ["Column", "Row", "Box", "Spacer", "Arrangement", "BoxWithConstraints"],
        "androidx.compose.runtime": [
            "mutableStateOf", "mutableIntStateOf", "mutableLongStateOf", "remember",
            "rememberCoroutineScope", "LaunchedEffect", "DisposableEffect", "derivedStateOf",
        ],
    }
    imports = {ln.strip() for ln in text.splitlines() if ln.startswith("import ")}
    code = strip_code(text)
    # A file IN the package needs no import from it. The measurement before adding the nlp group
    # missed this because it excluded on the package DECLARATION and the sweep ran on the whole
    # file — two hits in MaNgram.kt, which lives there. Two is not many, and two is enough to teach
    # somebody to skim past the third.
    declared_package = next(
        (ln.removeprefix("package ").strip() for ln in text.splitlines() if ln.startswith("package ")),
        "",
    )
    for package, names in groups.items():
        if package == declared_package:
            continue
        # A wildcard brings them all in and this cannot see which.
        if any(i == f"import {package}.*" for i in imports):
            continue
        _check_group(path, code, imports, package, names)


def _check_group(path: Path, code: str, imports: set[str], package: str, names: list[str]) -> None:
    for name in names:
        # A file that declares the name means its own thing by it.
        # A file that declares the name means its own thing by it. Both hits the runtime sweep found
        # were exactly this: a `fun remember(...)` of their own.
        if re.search(rf"\b(?:class|interface|object|data class|fun|suspend fun)\s+{name}\b", code):
            continue
        # Imported from somewhere ELSE under the same name: ThemeManager's `Icon` is
        # android.graphics.drawable.Icon, and demanding the Compose one there would be wrong.
        if any(i.endswith(f".{name}") and not i.endswith(f"{package}.{name}") for i in imports):
            continue
        if re.search(rf"(?<![.\w]){name}\s*[({{<.]", code) and f"import {package}.{name}" not in imports:
            fail(path.name, f"`{name}` used but not imported from {package}")


def check_prefs_collect_import(path: Path, text: str) -> None:
    """
    RED BUILD 281: `prefs.…collectAsState()` with Compose's `collectAsState` imported.

    A jetpref preference is not a Flow, and its `collectAsState` is jetpref's own extension. Import
    Compose's instead and the preference read does not resolve — and the error Kotlin prints names
    the `by` DELEGATE, three lines up, not the import. It reads as a delegation problem and is not
    one, which is exactly the kind of misdirection worth one line here.

    Narrow: it fires only on a file that reads `prefs.` with `collectAsState` and has the Compose
    import without the jetpref one. Both imported is fine — that is how several files here already
    are, since a real Flow may be collected in the same file.
    """
    code = strip_code(text)
    if not re.search(r"\bprefs\.[\w.]*collectAsState\(", code):
        return
    imports = {ln.strip() for ln in text.splitlines() if ln.startswith("import ")}
    jetpref = "import dev.patrickgold.jetpref.datastore.model.collectAsState"
    if jetpref not in imports:
        fail(path.name, "reads a preference with collectAsState but does not import jetpref's")


def check_when_coverage() -> None:
    """
    Red build: a new key added to the enum and one `when` never given its branch.

    Kotlin catches a non-exhaustive `when` that returns a value; it does NOT catch one used as a
    statement, which is exactly how a new key silently draws nothing.
    """
    for enum_file, users in (
        ("dictate/MaFeatureOrder.kt", ["dictate/ui/MaFeatureRow.kt", "app/settings/dictate/MaRowsScreen.kt"]),
        ("app/settings/MaSettingsOrder.kt", ["app/settings/MaSettingsOrderScreen.kt"]),
    ):
        ef = SRC / "dev/patrickgold/florisboard" / enum_file
        if not ef.exists():
            continue
        # Only the FIRST enum in the file. MaFeatureOrder.kt now declares two — MaFeatureKey and
        # the MaFeatureGroup that says what each key is for — and this pattern read both, then
        # demanded that MaFeatureRow carry a branch for CLIPBOARD and BUCKETS. It switches on
        # MaFeatureKey and always did.
        #
        # The keys are the thing that must be covered everywhere, and they are declared first, so
        # the text is cut at the next `enum class` and the entries read from what is above it.
        enum_text = ef.read_text()
        first_end = enum_text.find("enum class", enum_text.find("enum class") + 1)
        if first_end > 0:
            enum_text = enum_text[:first_end]
        entries = set(re.findall(r"^\s{4}([A-Z][A-Z_0-9]*)\(\"", enum_text, re.M))
        if not entries:
            continue
        for u in users:
            uf = SRC / "dev/patrickgold/florisboard" / u
            if not uf.exists():
                continue
            mentioned = set(re.findall(r"\b(?:MaFeatureKey|MaSettingsEntry)\.([A-Z][A-Z_0-9]*)", uf.read_text()))
            missing = entries - mentioned
            if missing:
                fail(uf.name, f"no branch for: {', '.join(sorted(missing))}")


def check_no_secrets() -> None:
    """A key in a commit is public the moment it is pushed, and history rewriting does not undo it."""
    diff = subprocess.run(
        ["git", "-C", str(ROOT), "diff", "HEAD"], capture_output=True, text=True
    ).stdout
    # Deny by default on long tokens after a known prefix, and both Gemini formats.
    pattern = re.compile(r"(sk_|gsk_|sk-|ghp_|github_pat_|AIza|AQ\.)[A-Za-z0-9_\-]{16,}")
    for m in pattern.finditer(diff):
        fail("STAGED DIFF", f"something key-shaped ({m.group(1)}…) — do not push")
        break


def main() -> int:
    files = changed_files()
    if not files:
        # Said precisely, because "nothing changed" when a file plainly did change reads as the tool
        # being broken, and a tool nobody believes is a tool nobody runs.
        print("no Kotlin files changed — nothing for this to check")
    for path in files:
        text = path.read_text()
        check_duplicate_imports(path, text)
        check_import_order(path, text)
        check_duplicate_declarations(path, text)
        check_annotation_pairs(path, text)
        check_icon_imports(path, text)
        check_delegation_imports(path, text)
        check_preferences_exist(path, text)
        check_symbols_resolve(path, text)
        check_compose_helpers(path, text)
        check_imports_resolve(path, text)
        check_balance(path, text)
        check_removed_declarations(path, text)
        check_nullable_args(path, text)
        check_prefs_collect_import(path, text)
        check_layout_imports(path, text)
        check_settings_default(path, text)
        check_settings_entry_reachable(path, text)
        check_route_deeplink(path, text)
        check_preference_collect(path, text)
        check_suspend_in_lock(path, text)
        check_modifier_clip_import(path, text)
        check_weight_scope(path, text)
    check_when_coverage()
    check_no_secrets()

    if problems:
        print(f"\n{len(problems)} problem(s):\n")
        for p in problems:
            print("  " + p)
        print("\nCI would have told you this in five minutes. Fix and re-run.")
        return 1
    print(f"checked {len(files)} file(s) — nothing to report")
    return 0


if __name__ == "__main__":
    sys.exit(main())
