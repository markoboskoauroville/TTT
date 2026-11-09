# STT bake-off for the TTT keyboard (9.11.2026), status: waiting for Marko's recordings

**Question.** Replace AssemblyAI in TTT? Marko (9.11.2026): test English and Croatian (and mixed) on his own voice; two engines (one per language) are fine; switch only if clearly better. NEVER OpenAI (modules/no-chatgpt.md).

**Finding 1 (audio source).** TTT keeps no dictation audio or sent-text pairs (checked Nothing phone 100.67.149.40: Android/data/com.mantraproductions.tttlight/files empty, Documents/TTTmini holds only models, settings, corrections, keys). So the test set is 21 written sentences (7 English, 7 Croatian, 7 mixed, names from dictation-corrections.md) that Marko reads once. Truth = the sentences.

**Method.** `tools/stt_bakeoff/`: `record_server.py` (recorder page http://127.0.0.1:8851, clips to clips/), `sentences.json`, `names.txt`, `run_bakeoff.py` (ffmpeg to 16 kHz wav; every engine plain and with the same name vocabulary; text lowercased, punctuation stripped, diacritics kept; WER, name errors, diacritic errors, seconds per clip; scores per group en / hr / mixed / all).
Engines wired: Parakeet TDT 0.6B v3 (local on the Mac via sherpa_onnx, free, model in ~/Downloads/parakeet-from-pixel), Groq whisper-large-v3, AssemblyAI (current), Gemini 2.5 Flash audio, Deepgram nova-3 (needs key). Not yet wired: Soniox, Speechmatics, Google Chirp 3, Google on-device SpeechRecognizer (needs a small test activity on the phone).

**Cards for Marko (mantra-blocks, task 62):** read the 21 sentences; sign up free for Soniox, Speechmatics, Deepgram (key into ~/Downloads/API/<name>.txt); Google Cloud Speech-to-Text (Chirp 3) project with free credit. Spend gate: `spend ask` answered "free tier only; on 429 rest the key, then local Whisper".

**Results.** Not run yet: no recordings exist. Run `python3 tools/stt_bakeoff/run_bakeoff.py` after the clips are in; paste the table here.

| engine | vocab | group | WER % | name errors | diacritic errors | sec/clip |
|---|---|---|---|---|---|---|
| (pending) | | | | | | |

**Mixed sentences, how TTT should choose.** Decide from the results; options: the E/H key picks the engine; or the engine that wins on the mixed group handles both.
**Price reference.** Parakeet local 0 EUR (third-party listing about 0.09 USD/h on OpenRouter, to confirm); others to be filled from their pricing pages.
