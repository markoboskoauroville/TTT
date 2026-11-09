# Speech-to-text providers for TTT (catalogue, 9.11.2026)

Marko, 9.11.2026: "explore the different options, free to high-end, be flexible and cheap." No OpenAI (modules/no-chatgpt.md).
Figures are from each provider's own page fetched 9.11.2026. **V** = verified on that page today, **U** = unverified (page did not show it, or from memory; confirm before relying). Prices per hour of audio, USD unless noted. "Free" means free credit/quota; "reset" is when it renews. hr = Croatian.

| Provider / model | Free quota (reset) | Price/h after | Croatian | Mode | Custom vocab | Notes |
|---|---|---|---|---|---|---|
| **Groq** whisper-large-v3 | Free plan exists, limits per account (U); Developer-plan table: 20 req/min, 2,000 req/day, 7,200 audio-s/h, 28,800 audio-s/day (V, daily/hourly windows) | 0.111 (V) | Multilingual Whisper, hr not named on page (U; Whisper supports hr) | batch, 25 MB free / 100 MB dev | `prompt` up to 224 tokens (V) | fastest hosted Whisper; our current fallback |
| **Groq** whisper-large-v3-turbo | same limits | 0.04 (V) | as above | batch | prompt | cheapest decent Whisper |
| **AssemblyAI** (current) | $50 credit at signup, no card, one-off (V); free streaming 5 new streams/min | Universal-2 0.15, Universal-3.5 Pro 0.21 (V); streaming 0.15 multilingual, 0.45 U3.6 Pro (V) | Universal-2: yes, "good" tier 10-25% WER (V); Universal-3: not confirmed (V) | batch + streaming | keyterms +0.05/h (up to 200 / 1,000 terms); streaming multilingual included (V) | word_boost may silently route to Universal-2 (V) |
| **Deepgram** Nova-3 | $200 credit, no card, no expiry (V) | batch 0.26 mono / 0.31 multi; streaming 0.46 / 0.55 (V, pay as you go) | Nova-3 yes; Nova-2 no (V) | batch + streaming | keyterm prompting +0.078/h (V) | code-switching `multi` list is limited (V) |
| **Speechmatics** | $100 credit one-off (V) | Melia 1 0.24 batch / 0.40 realtime; Standard 0.45 (V) | yes, hr supported (V); no hr+en bilingual pack (V) | batch + realtime | custom dictionary on Standard/Enhanced, not Melia 1 (V) | 56 languages |
| **Soniox** | no free credit shown (V); sign-up trial credit U | async 0.10, realtime 0.12 (V) | yes, `hr` (V) | async + realtime | context billed as tokens (V) | cheapest cloud with real streaming; code-switching U |
| **Google Cloud STT v2 / Chirp 3** | page did not load (U); historically 60 free min/month and new-customer credit (U) | U | Chirp: hr-HR supported (U) | batch + streaming | phrase hints / adaptation (U) | needs a GCP project |
| **Google on-device** (SpeechRecognizer, Android 13+) | free, offline | 0 | needs the Croatian offline pack (U) | streaming | none | quality/latency unmeasured; keep as offline fallback |
| **Gemini audio** (Gemini API) | free tier exists for 3.5 Flash-Lite and others (V), rate limits not on page (U) | 3.5 Flash-Lite $0.30 per 1M audio tokens; 3.1 Flash-Lite 0.50; Flash 0.75 until 31.12.2026 then 1.50 (V); 2.5 models deprecated, routed 1.12.2026 (V) | Gemini understands hr (U) | batch only, prompt-driven | any prompt vocabulary | at ~32 audio tokens/s (U) this is about $0.035/h on Flash-Lite, i.e. cheapest of all; it is an LLM, may "tidy" text |
| **NVIDIA Parakeet TDT 0.6B v3** | open weights CC BY 4.0, free local (V) | local 0; OpenRouter/Together listing $0.09/h, Prime Intellect $0.061/h (V) | yes, 25 languages incl. Croatian; FLEURS WER hr 12.46%, en 4.85% (V) | batch (on device streaming via sherpa) | none | already on the Nothing phone (models/parakeet-tdt-0.6b-v3) and on the Mac. build.nvidia.com trial page 404, no verified free hosted key |
| **Cloudflare Workers AI** Whisper / whisper-large-v3-turbo | 10,000 neurons/day free, resets 00:00 UTC (V) = about 214 min/day of turbo (computed from 46.63 neurons/min) | 0.03 (=$0.0005/min, V) ; Nova-3 0.31, WebSocket 0.55 (V) | Whisper hr (U) | batch; Nova-3 streaming | initial_prompt (U) | free daily quota is the best free fallback; fails with an error past the limit (V) |
| **Hugging Face Inference** | free users: none; PRO $2/month credit (V) | provider pass-through (V) | per model | batch | per model | hf-inference now mostly CPU small models (V); not for ASR |
| **Fireworks** | speech pricing not on page (V) | U | Whisper (U) | batch | U | skip unless prices found |
| **Together / Replicate Whisper** | trial credit (U) | Together Parakeet 0.09 (V); Whisper U | U | batch | U | not needed |
| **ElevenLabs Scribe v2** | no free STT quota shown (V) | 0.22 batch, realtime 0.39 (V); keyterms +0.05 | yes, "Excellent (<=5% WER)" tier (V); realtime hr not named | batch + realtime | keyterms up to 1,000 (V) | strongest published hr tier; priced like AssemblyAI |
| **Azure Speech** | F0: 5 audio hours/month real-time, no batch (V), monthly | not shown on page (U) | hr-HR (U) | both | phrase lists | skip |
| **AWS Transcribe** | 60 min/month for 12 months (V) | about 0.36 batch ($0.006/min) / 0.60 streaming (V examples) | not confirmed (V) | both | custom vocabulary (V) | skip, weak free tier |
| **Rev.ai** Reverb | 5 h credit (V) | English 0.20, foreign 0.30 (V) | not named (V) | batch + streaming | U | skip |
| **Gladia** | 50 EUR credit, 1 year (V) | Solaria-3 0.16 EUR, Solaria-1 0.27 EUR; realtime 0.68 (V) | Solaria-3 only 5 languages; hr likely Solaria-1 (V) | both | vocab included (V) | |
| **Local whisper.cpp on phone** | free, offline | 0 | Whisper hr, slow on phone (U) | batch | none | only as last resort |

## Reading the numbers

Cost per hour of dictation is tiny everywhere: Marko dictates minutes a day, so even 0.30 USD/h is cents per month. The real choice is accuracy on Croatian + names and the free ceiling. Free ceilings that renew: Groq (hourly/daily windows), Cloudflare (daily neurons), Azure F0 (monthly), Gemini free tier (rate-limited). One-off credits: AssemblyAI $50, Deepgram $200, Speechmatics $100, Gladia 50 EUR, Rev 5 h.

## Recommended chains (before the in-app benchmark; reorder by its WER)

- **English:** 1) Parakeet v3 on device (WER 4.85% FLEURS, 0 cost, offline); 2) Groq whisper-large-v3-turbo (0.04/h, prompt for names); 3) Cloudflare Whisper turbo (free 10k neurons/day); 4) Deepgram Nova-3 with keyterms while the $200 lasts; 5) AssemblyAI (current) as the paid fallback.
- **Croatian:** 1) Groq whisper-large-v3 with the name prompt (best free hosted Whisper for hr, U until benchmarked); 2) ElevenLabs Scribe v2 or Deepgram Nova-3 (both state Croatian, Scribe "Excellent" tier) if the benchmark shows a clear gain; 3) Parakeet v3 on device (hr 12.46% FLEURS, offline); 4) Cloudflare Whisper; 5) Gemini 3.5 Flash-Lite with the name list in the prompt (cheapest, test for text "tidying"); 6) AssemblyAI Universal-2 (hr only "good").
- **Mixed hr+en:** Whisper large-v3 (Groq) and Gemini detect per sentence; AssemblyAI/Parakeet auto-detect once per clip. Let the E/H key choose the first engine, and the benchmark decide.
- **Cheapest paid tier worth adding:** Soniox async/realtime at 0.10 / 0.12 per hour (needs Croatian check) or Groq turbo at 0.04/h on a Developer key if the free limits bite; Gemini Flash-Lite (about 0.04/h) if its accuracy holds.

## What to verify next

Google Chirp 3 pricing (page would not load), Gemini audio token rate, Groq's actual free-plan limits for Marko's org, Soniox free credit and context, Cloudflare/Gemini Croatian quality. The harness `tools/stt_bakeoff/run_bakeoff.py` has adapters for Groq, AssemblyAI, Gemini, Deepgram and local Parakeet.

## In the app since 9.11.2026 (Engines + benchmark)

Settings > Engines lists every engine of this catalogue that has a free quota or runs on the phone: Parakeet, Android on-device, Groq turbo and large-v3, Cloudflare Workers AI, Gemini Flash-Lite, Deepgram Nova-3, AssemblyAI, Speechmatics, Google Speech v1, Google Chirp 3 (v2, service-account OAuth), plus Soniox and ElevenLabs Scribe which have no free quota shown and run only when switched on. Order per language is draggable; defaults are the chains above. "Benchmark voice engines" scores the last 10 clips per language (WER, names from dictation_corrections.json, latency, cost) inside each free quota. Code: app/src/main/kotlin/.../dictate/engines/. Unit tests: EngineLogicTest (CI job unit-tests).
