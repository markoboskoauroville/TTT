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
13. The recorder (dictation not idle) is shown in every view; suggestions only with the letter keys open. Test: collapse the keyboard, press record, screenshot.
14. DESIGN: before any UI work load the ttt-design skill; every new screen, panel, recorder or menu reuses the keyboard's modules (MaHostKey, MaKeyPanel, MaRecorderReadout) and MaHost colours. No title bars, no Material default looks.
15. BEFORE EVERY PUSH, the checklist (the hole Marko found 13.11.2026: rules written but not checked): grep the diff and the touched screens for TextField/OutlinedTextField with key/token/password labels (none allowed), DropdownMenu on the keyboard (none), new hard-coded colours (only MaHost); re-read rules 1-15 against the diff; record the request in momentaryupdates.md, the state in TAKEOVER.md, the mistake in LESSONS.md, the rule here.
16. RE memory lives in Documents/TTTmini/re/ (TAKEOVER, LESSONS, SKILLS, people/, notes/) and is pushed to the private repo RE_MAGIC_DATABASE after every change (MaRemoteDb, token only from a key file).
17. RE engine = Anthropic Auto (Haiku judge -> Sonnet/Opus) / Opus / Sonnet / Qwen (Groq, own ring re_qwen, own picker in Settings > RE Magic). No other models. Compare them with the adb broadcast dev.ttt.engines.RE_COMPARE (files/re_compare.json).
18. DEBUGGING ON HIS PHONE = SPEAK, TEXT ONLY: every update goes as text to Voice Engine v4 (POST /say on 127.0.0.1:8720 via adb forward; tools/voice/say_on_phone.sh "text"); Voice Engine speaks it through Speechify in Beatrice with subtitles. Never send audio, never a robot voice. adb over Tailscale: tools/voice/adb_tailscale_fwd.py 15555 100.67.149.40 5555, then adb connect 127.0.0.1:15555.
19. REMOTE: tools/ttt_remote.py drives TTT over adb (say, status, type, re, engine, compare, shot, ui, tap, install). Add a command there and in EngineJobs.kt together; answers in files/remote/<id>.json; never a key value.
20. AUTO-ROTATE STAYS OFF on his phone (13.11.2026): never accelerometer_rotation 1 or user-rotation free; rotate by hand with `cmd window user-rotation lock N` only for a test, then lock 0. Voice: only text travels (MANTRA_MANIFEST modules/voice-text-only.md).
21. SHARING THE PHONE (MANTRA_MANIFEST phone-lock.md): `ttt_remote.py lock "what"` before touching the screen, `done` (unlock + his Claude chat in front) as soon as it is not needed, tidy first; after LET ME USE IT never touch it until he says so.
22. THE SOLUTION AT THE NOSE (13.11.2026, MANTRA_MANIFEST solution-at-the-nose.md): a message about a missing key/permission/failed step IS the fix (file picker in Download/Api, the permission request, Retry/Resend). Never send him to Settings. First ask whether the gate is needed at all.
## Run log
- 13.11.2026: RE engines Auto/Opus/Sonnet/Qwen, RE Qwen key picker with per-key test, per-key results in the main picker, MaReCompare harness.
- 13.11.2026: RE chat redesigned in the host look (ttt-design skill, MaHost/MaKeyPanel/MaRecorderReadout), per-answer keys, lessons at once, re/ synced to GitHub, token and KLIPY key only from files, CI test_host_rules.py.
- 13.11.2026: build 635 (audio-only history, full-screen History, RE Magic chat with memory, per-person People screen); compiled, not yet installed (no adb in the cloud session, Pixel 7 offline).
- 10.11.2026: shipped default UI, RE Groq switch, Groq small model gpt-oss-20b.
- 10.11.2026: no automatic engine switch, 3 s retries.
- 10.11.2026: forward-only cursor, short H/E label.
- 10.11.2026: width percent per button (build 596).
- 10.11.2026: width percent per button (build 596).
- 10.11.2026: numbered keys with automatic switch (build 592).
- 10.11.2026: builds 577 to 590 (labels, key picker, engine panel, benchmark percent); open: Croatian voice check, history re-transcribe by hand.

- **Pressable controls highlight** on hover/press, nothing else does: MANTRA_MANIFEST modules/workflow-interface.md, "Pressable controls highlight" (Marko 10.11.2026).

**Copyright footer (10.11.2026):** at the very bottom of the app's Settings, one muted centred line "© 2026 Mantra Productions" (MANTRA_MANIFEST modules/copyright-footer.md).
