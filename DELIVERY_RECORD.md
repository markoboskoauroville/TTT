# Delivery record — TTT

What was measured, what failed, and what was not tested. Specified by
`MANTRA_MANIFEST/modules/delivery-gate.md`; rewritten per release rather than appended.

**A record showing only green is a record of the checks, not of the work.**

---

## Build 373 — 4.9.2026

    ARTEFACT   ttt-mini-build-373.apk   40,055,172 bytes
    BUILT BY   GitHub Actions, from commit 8dc08b0d
    PREVIOUS   build-370, still downloadable — G8's parachute exists

### The four tests

| Test | Result |
|---|---|
| **T1 mechanism** | **PASS.** 33 suites, **53,940 checks**, 0 failed. Plus `verify.py`: 22 static checks, clean. |
| **T2 running app** | **NOT RUN.** No device. |
| **T3 ugly cases** | **PARTIAL.** Failure paths are walked as logic — empty screens, refused keys, silent writes, boundaries at 0 and 1 — but never exercised against a real provider, a real disk, or a real offline phone. |
| **T4 upgrade** | **NOT RUN**, and this is the one that matters: build 369 installed over 367 and **would not start**. The failure T4 exists to catch is exactly the failure that happened. |

### The nine gates

| Gate | Result |
|---|---|
| G1 provenance | **PASS.** HEAD == origin/main == the built commit. All 4 actions pinned by 40-char SHA, 0 by tag. Two releases kept. |
| G2 secrets | **PASS, with one thing to know.** 0 key shapes in the shipped APK, scanned as bytes. History carries one Gemini-shaped string in a deleted test fixture (see below). |
| G3 analysis | **PARTIAL.** `verify.py` clean, 53,940 assertions green. **Lint runs and does not block: 67 errors, 971 warnings.** detekt and the R8 report have never run. |
| G4 dead code | **PASS.** 58 feature keys: 0 undrawn, 0 without a picker glyph, 0 ungrouped. 73 `Ma*` preferences: 2 read nowhere, both deliberate (`maReaderVoiceHr/En`, kept so an old phone reads back harmlessly). 21 settings entries: all have an icon and a registered route. |
| G5 dead loops | **PASS with two findings.** 30 `while (true)`; 28 bounded by a visible delay, break or cancellation point. Two need reading: `MaSubtitleRow.kt:194` and `MaEncoder.kt:108`. Neither is new and neither has hung, but neither is proven. |
| G6 stress | **NOT RUN.** No device. |
| G7 budgets | **PARTIAL.** 40,055,172 bytes, of which `libonnxruntime.so` is 25.8 MB and the app's own dex is 3.9 MB. Everything else needs a phone. |
| G8 upgrade | **NOT RUN.** See T4 — this gate's absence is what shipped a keyboard that would not open. |
| G9 record | This document. |

### What failed, and what it cost

- **Build 369 would not start.** A route registered with `composableWithDeepLink` had no `@Deeplink`,
  so the nav graph threw while the app was starting. It compiled, `verify.py` was clean, 25 suites
  passed, CI was green, and the app died at launch. Reverted before the cause was known, then fixed,
  then guarded by `check_route_deeplink`. See DEVELOPMENT.md §208.
- **The sweep found a gap in that new check.** Its regex matched only `object`, so five routes
  declared as `data class` were silently unchecked. Widened and re-measured at zero false positives.
- **Three sweep hits were false** — routes registered under `Devtools` rather than `Settings`. As
  `four-tests.md` warns: confirm every sweep hit against the source.

### One thing for him to decide

`AIzaSyD9mK3pQ7rT2vX5yB8nC1eF4hJ6lN0oP2s` appears in git history, in
`MaSpeechifyTest.kt`, a file deleted in 895023f0. It sits beside an obviously fabricated Anthropic key
in a parser fixture, so it is almost certainly invented. **Almost certainly is not certainly.** If
that Gemini key was ever real, rotate it — it is public in the history of a public repository, and
rotating costs a minute.

### NOT TESTED — as specific as the passes

- **No build of this app has ever been installed or run on a phone by me.** Not one.
- **No recording has been made.** Microphone, WAV writer, silence trim, every provider call.
- **No transcription has been requested.** The classifier is tested against quoted bodies.
- **The accessibility service has never attached.** The magic finger, the screen reader, the copy
  fallback, the recording hairline.
- **No reading has been spoken.** Speechify has never been called from this code.
- **The keyboard has never been shown.** Every layout figure is arithmetic, not observation.
- **No upgrade has been performed** — and 369 is what that costs.
