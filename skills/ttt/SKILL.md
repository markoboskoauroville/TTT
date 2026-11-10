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
8. Multi-account keys: numbered Groq-1, Groq-2, Gemini-1 (KeyRing.kt); on 429/quota/401 switch to the next number, return to earlier ones when their rest (00:00 UTC for daily quota) ends; status shows the number. Test by putting an invalid key first.
9. Feature-row width: percent per button (maKeyWidths, MaKeyWidths.kt), default H 40 / F1 20 / E 40, normalised, floor 8%; steppers in Settings > Feature row.
9. Feature-row width: percent per button (MaKeyWidths), H 40 / F1 20 / E 40; labels never cut (stack in two lines). Rotation test: Settings > Feature row > "Test: key 1 fails".
9. Feature-row widths: percent per button (MaKeyWidths.kt, steppers H/F1/E in Settings > Feature row), defaults H 40 / F1 20 / E 40 for rows of four or fewer.
10. Key ring is forward-only: persistent cursor per provider, never back to a lower key, wraps to 1 after the last. H/E label: "Gemini 2/25 45" (name, n/total, minutes left); percent only in panel/pickers.
11. NEVER switch engine/provider/model automatically: same engine, retry every 3 s (10 times), next key of the same provider only on quota/refused, network errors never move the ring; audio kept, Resend from History.
12. Before EVERY TTT upgrade: export the Nothing UI (broadcast dev.ttt.engines.EXPORT_UI, pull ui_config.json), check for secrets, refresh app/src/main/assets/default_config.json (raise version). UI only. RE engine = Anthropic or Groq (switch in Settings and long press on RE).
## Run log
- 10.11.2026: shipped default UI, RE Groq switch, Groq small model gpt-oss-20b.
- 10.11.2026: no automatic engine switch, 3 s retries.
- 10.11.2026: forward-only cursor, short H/E label.
- 10.11.2026: width percent per button (build 596).
- 10.11.2026: width percent per button (build 596).
- 10.11.2026: numbered keys with automatic switch (build 592).
- 10.11.2026: builds 577 to 590 (labels, key picker, engine panel, benchmark percent); open: Croatian voice check, history re-transcribe by hand.

- **Pressable controls highlight** on hover/press, nothing else does: MANTRA_MANIFEST modules/workflow-interface.md, "Pressable controls highlight" (Marko 10.11.2026).

**Copyright footer (10.11.2026):** at the very bottom of the app's Settings, one muted centred line "© 2026 Mantra Productions" (MANTRA_MANIFEST modules/copyright-footer.md).
