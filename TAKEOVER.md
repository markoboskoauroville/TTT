# TTT takeover (state 10.11.2026, build 590)

**What it is.** TTT, Talk to Type Advanced Keyboard: Marko's Android dictation keyboard (FlorisBoard/Dictate fork, package com.mantraproductions.tttlight). Repo markoboskoauroville/TTT (folder ~/Developer/TTT_MINI). Builds come only from GitHub Actions (releases build-NNN, Latest); install with `adb install -r`, never uninstall (it wipes keys and settings). Test phone: the Nothing; the Pixel 7 emulator is the default tester.

**State at build 590.**
- Keys: no manual key fields. Settings has one picker, "Import keys from file", plus "Re-test keys". It detects the provider by key shape, tests each key with a free authenticated call, keeps only the working ones encrypted in the Keystore. KeyRing rotates with a visible status ("Groq key 2/5 rate-limited, key 3"). Key labels on the keyboard show "free 480", "free 38k" or "paid".
- Engines: long press on the H or E key opens a vertical panel growing up from the key with all 13 providers; tap selects and closes (no OK). H/E keys of the old NET/LOCAL kinds follow the chosen engine (EngineStore sel_hr/sel_en, EngineSlotTest).
- AI correction after Croatian dictation is off behind the switch maAiCorrectDictation.
- History: "Re-transcribe with..." and an accuracy percentage next to every model, from a benchmark on the last 5 HR + 5 EN real dictations (docs/benchmark-2026-11-09.md). Groq large-v3 100/99 (circular, it made most references), Chirp 3 98/89, AssemblyAI 96/91, Deepgram 94/87, Cloudflare 90/88, Speechmatics 90/85, Groq turbo 90/89, Gemini 88/90, Google v1 86/82, Parakeet 78/85, Android on-device failed.
- Keys imported on the Nothing (from /sdcard/Download/API/MacOS): Groq 3 of 8 working, Google 21 of 23, AssemblyAI 20, Deepgram, Speechmatics, Anthropic, Google service account, Cloudflare Workers AI 1. Cloudflare key file on the Mac: ~/Downloads/API/cloudflare-workers-ai.txt (token "TTT Workers AI", free plan about 214 min Whisper per day).

**Open.** (1) Croatian dictation "no AI call after" not confirmed with Marko's voice. (2) History re-transcribe not tapped by hand. (3) Android on-device recogniser fails (language pack). (4) Soniox paid and skipped (balance exhausted); no ElevenLabs key. (5) Marko tops up Anthropic, Groq, Deepgram, AssemblyAI, Speechmatics himself (billing pages were opened).

**Next.** Marko dictates Croatian once and taps Re-transcribe; then judge the benchmark again after correcting the history texts by hand (removes the Groq circularity).
