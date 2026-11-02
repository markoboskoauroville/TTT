#!/usr/bin/env python3
"""
Test 1 for the key ring's verdicts, against MANTRA_MANIFEST/modules/quota-and-fallback.md.

The module's one sentence: **a status code alone cannot tell you whether to bury a key, rest it, or
blame yourself. Read the body.** These are the cases it names, checked against this app's classifier.

    python3 scripts/test_fallback.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider"

failures: list[str] = []
checks = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def classify(status, body=""):
    """A port of DictateApiException.fromHttp, in the order the Kotlin evaluates."""
    hay = body.lower()
    if status == 403 and ("1010" in hay or "cloudflare" in hay):
        return "SERVER_ERROR"
    if status in (401, 403) or any(w in hay for w in
                                   ("api key", "api_key", "invalid_api_key", "unauthorized", "authentication")):
        return "INVALID_API_KEY"
    if any(w in hay for w in ("insufficient_quota", "quota exceeded", "billing", "credit",
                              "exhausted", "balance", "budget")) or status == 402:
        return "QUOTA_EXCEEDED"
    if status == 429 or any(w in hay for w in ("rate limit", "rate_limit", "too many requests")):
        return "RATE_LIMITED"
    if 500 <= status <= 599:
        return "SERVER_ERROR"
    return "UNKNOWN"


# ---------------------------------------------------------------- trap 1, the expensive one
cf = 'error code: 1010'
check("a Cloudflare 403 is not the key's fault", classify(403, cf) == "SERVER_ERROR",
      "one request would bury every key in the ring")
check("a real 403 still kills the key", classify(403, "forbidden for this model") == "INVALID_API_KEY")
check("the word cloudflare works too", classify(403, "Cloudflare Ray ID") == "SERVER_ERROR")
check("1010 in a 401 does not rescue it", classify(401, "1010") == "INVALID_API_KEY",
      "401 is the key, whatever else the body says")

# ---------------------------------------------------------------- dead vs cool, never confused
check("Groq's bad key is dead", classify(401, '{"error":{"code":"invalid_api_key"}}') == "INVALID_API_KEY")
check("AssemblyAI's bad key is dead",
      classify(401, "Authentication error, API token missing/invalid") == "INVALID_API_KEY")
check("a 429 rests, never buries", classify(429, "Rate limit reached") == "RATE_LIMITED",
      "a ring of twenty-one becomes a ring of none over an afternoon")
check("a per-day 429 also rests", classify(429, "requests per day (RPD)") == "RATE_LIMITED")

# ---------------------------------------------------------------- an empty account that does not say 402
hume = '{"message":"Exhausted credit balance.","details":{"code":"E0300","slug":"zero_credits"}}'
check("a 400 saying exhausted is quota, not our fault", classify(400, hume) == "QUOTA_EXCEEDED",
      "one empty account would stop the whole run and the other keys never be tried")
check("402 is quota", classify(402, "") == "QUOTA_EXCEEDED")
check("credit words are read from the body", classify(400, "insufficient credit") == "QUOTA_EXCEEDED")

# ---------------------------------------------------------------- default to soft
check("an unknown status blames nobody", classify(418, "") == "UNKNOWN",
      "an unclassified failure must not burn the ring on a guess")
check("their outage is theirs", classify(503, "") == "SERVER_ERROR")


# ---------------------------------------------------------------- the wiring
def code(path: Path) -> str:
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


ex = code(SRC / "DictateApiException.kt")
check("the Cloudflare branch is FIRST", ex.index('hay.contains("1010")') < ex.index("status == 401 || status == 403"),
      "the plain 403 branch would catch it first and bury the ring")

# Trap 3: a failure that arrives as 200. AssemblyAI's poll does exactly this.
client = code(SRC / "OpenAiCompatibleClient.kt")
check("a 200 carrying an error is caught", '"error" -> throw DictateApiException' in client,
      "the HTTP call succeeded and the work failed — different questions")

# The User-Agent, on every client that talks to a provider.
for f in ("OpenAiCompatibleClient.kt", "MaAssemblyStats.kt", "RealtimeClient.kt"):
    body = (SRC / f).read_text()
    check(f"{f} sends a User-Agent", '"User-Agent", "TTT/1.0' in body,
          "Cloudflare refuses a client that sends none, on every key at once")
    check(f"{f} does not impersonate a browser", "Mozilla" not in body, "a lie that can be checked")

print(f"fallback, test 1: {checks} checks, {len(failures)} failed")
for f in failures:
    print(f"  FAIL  {f}")
sys.exit(1 if failures else 0)
