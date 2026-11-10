---
name: ttt
description: TTT, Talk to Type Advanced Keyboard (repo TTT, folder TTT_MINI): Marko's Android dictation keyboard, H/E record keys, engine panel, key picker, benchmark. Use for ANY TTT / keyboard / H key / E key / dictation engine / key import / benchmark task.
---
# TTT skill
1. Read TAKEOVER.md and LESSONS.md in the repo; DEVELOPMENT.md has the build log. Save Marko's request word for word in momentaryupdates.md and push before any code.
2. Build only by GitHub Actions on push to main (release build-NNN Latest); install on the Nothing with `adb install -r`; test the Pixel 7 emulator too. Never uninstall.
3. Keys: only from text files through the one picker ("Import keys from file", "Re-test keys"); no manual fields; never print key values. Files on the phone: /sdcard/Download/API/MacOS.
4. Engines: long press on H or E opens the vertical 13-provider panel; pick closes it. H/E follow the chosen engine (sel_hr/sel_en). AI correction after dictation stays off (maAiCorrectDictation).
5. Accuracy percent per model comes from docs/benchmark-2026-11-09.md method (last 5 HR + 5 EN dictations); state the circularity caveat.
6. Mistakes already made: key fields for manual paste; a picker with OK button; trusting a benchmark against its own references.
7. Vocabulary: "creation" = Croatian, "Vesper" = Whisper.
## Run log
- 10.11.2026: builds 577 to 590 (labels, key picker, engine panel, benchmark percent); open: Croatian voice check, history re-transcribe by hand.
