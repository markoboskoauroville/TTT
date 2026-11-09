# Engine benchmark on the phone, 9.11.2026 (build 590)

**Method.** TTT itself ran Settings > Voice transcription buttons > Advanced > "Benchmark voice engines" on the Nothing Phone: the **last 5 real Croatian and last 5 real English dictations** in the clip buffer (seed test clips excluded; 5 of each existed), every engine that has a working key or model, within free quotas only (spend gate approval 05d38b77, cost 0). Accuracy = 100 x (1 - WER), WER on lower-cased text without punctuation, against the text kept as the reference for that clip. Latency = average seconds-to-text per clip as measured on the phone (4G/Wi-Fi as it was).

**Caveat that matters.** The reference is the text TTT kept for the dictation, which began as the output of the engine that transcribed it live and is only replaced when Marko's corrections loop saw an edit. 3 of the 5 HR and all 5 EN clips were made by Groq large-v3, so **Groq v3's 100% / 99% is partly circular** (it is compared with itself) and every other engine is measured against Groq's wording. Treat the ranking among the others as the reliable part; to judge Groq fairly, correct the history texts by hand and re-run. Only 5 + 5 short clips (2 to 30 s): differences of a few points are noise.

| Engine | HR % | EN % | Avg ms HR / EN | Cost | Notes |
|---|---|---|---|---|---|
| Groq Whisper large-v3 | 100 | 99 | 792 / 615 | free (28,800 s/day) | fastest of the accurate ones; circular, see caveat; 8 keys |
| Google Chirp 3 | 98 | 89 | 2409 / 3139 | free 60 min/month assumed | best HR besides Groq; service account |
| AssemblyAI | 96 | 91 | 3642 / 4328 | USD 50 credit | slow; second on EN |
| Deepgram Nova-3 | 94 | 87 | 3680 / 745 | USD 200 credit | HR slow, EN fast |
| Cloudflare Workers AI Whisper | 90 | 88 | 1974 / 2658 | free ~214 min/day | |
| Speechmatics | 90 | 85 | 1858 / 1855 | USD 100 credit | |
| Groq Whisper turbo | 90 | 89 | 608 / 516 | free | quickest overall, a notch less accurate than v3 |
| Gemini Flash-Lite audio | 88 | 90 | 1810 / 1838 | free tier, 25 keys | |
| Google Speech v1 | 86 | 82 | 1329 / 2233 | free 60 min/month | clips under 1 min only |
| Parakeet (on the phone) | 78 | 85 | 1392 / 1001 | free, offline | the offline fallback |
| Android on-device | not scored | not scored | | free | all 5 clips failed per language (no language pack / recogniser unavailable); not investigated |
| Soniox | skipped | skipped | | USD 0.10/h | paid, not enabled: spend rule |
| ElevenLabs Scribe | skipped | skipped | | USD 0.22/h | no key on the phone |

**Names (dictation_corrections).** Only 3 name mentions in the EN clips and none in the HR ones, too few to rank: Groq v3 and turbo and Cloudflare, Deepgram got 1 of 3, AssemblyAI, Gemini and Chirp 3 got 2 of 3, Parakeet and Speechmatics 0 of 3.

**Reading it.** For Croatian: Groq v3, then Chirp 3, then AssemblyAI. For English: Groq v3, AssemblyAI, Gemini. For speed on the same free quota: Groq turbo. The percentages are stored per language on the phone (EngineScores) and shown after the provider name in Settings, on the H/E keys and in the long-press panel; a new benchmark run updates them, and an engine that sits out a run keeps its last figure.

**Tool.** History (Settings > History > an entry > "Re-transcribe with...") sends that entry's audio through any set-up engine as HR or EN and shows the result beside the used text with the differing words lit; use it to build a hand-checked reference.
