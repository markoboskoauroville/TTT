# TTT, Talk to Type: what the app is and how it works

TTT ("Talk to Type Advanced Keyboard", package dev.ttt / folder TTT_MINI) is Marko Boško's Android keyboard
(a fork of FlorisBoard, with the Dictate dictation work in it). Source: github.com/markoboskoauroville/TTT
(public). Built only by GitHub Actions on push to main (release build-NNN, Latest).

## Dictation
- Two record keys on the feature row: **H** (Croatian, "hr") and **E** (English). Tap to record, tap again to stop
  and send. A long press on H or E opens the vertical engine panel (13 providers); picking one closes it, and the
  key then uses that engine (sel_hr / sel_en). The key label reads like "Gemini 2/25 45": engine name, which key of
  that provider is in use / how many keys, minutes left today.
- Engines are never switched automatically. On an error TTT retries the same engine every 3 s (10 times); it moves
  to the next KEY of the same provider only on quota/refusal; network errors never move the key ring. The audio is
  kept, and History can Resend it.
- Keys: only imported from text files through the one picker ("Import keys from file", "Re-test keys"), files in
  /sdcard/Download/API. Numbered keys (Groq-1, Groq-2, ...) form a forward-only ring per provider.
- Settings: "Voice transcription buttons" (choose H and E engines), "Advanced: fallback and benchmark" (fallback
  chain, benchmark), "API keys", "Transcription model" (AssemblyAI / Parakeet on the phone).
- Accuracy percentages per engine come from the benchmark (last 5 Croatian + 5 English dictations), with a
  circularity caveat: the references were mostly made by Groq large-v3 itself (see benchmark.md).

## The transcription engines (EngineCatalog)
- Parakeet (on the phone): free, offline. Android on-device: free, offline.
- **Groq Whisper large-v3** ("Groq", groq_v3) and **Groq Whisper turbo** ("Groq turbo", groq_turbo, model
  whisper-large-v3-turbo): both free 28,800 audio seconds per day per key; paid 0.111 vs 0.04 USD/hour.
- Cloudflare Workers AI Whisper, Gemini Flash-Lite audio, Deepgram Nova-3, AssemblyAI, Speechmatics,
  Google Speech v1, Google Chirp 3, Soniox, ElevenLabs Scribe.

## Groq turbo versus Groq large-v3 (a question Marko asked)
- Precision: **large-v3 is the more accurate one**. In TTT's own benchmark (9.11.2026) Groq large-v3 scored
  100 % Croatian / 99 % English (partly circular), Groq turbo 90 % / 89 %; turbo was the quickest overall.
- History: Whisper is OpenAI's open-weights speech recognition model, released September 2022 (trained on
  ~680,000 hours of web audio). large-v2 came in December 2022, **large-v3** in November 2023 (more training data,
  128 mel bins, better on many languages). **large-v3-turbo** (October 2024) is large-v3 with the decoder cut from
  32 layers to 4 and fine-tuned: about 8x faster, slightly less accurate, weakest on less common languages and
  translation. Because the weights are open (MIT licence), anyone may host them, and Groq does.
- Groq (with a q) is a US chip company founded in 2016 by Jonathan Ross, one of the designers of Google's TPU. It
  makes the LPU (Language Processing Unit), a chip built for very fast inference, and runs GroqCloud, an API that
  serves open models (Whisper, Llama, Qwen, gpt-oss and others) at very high speed with a generous free tier.
  It is not Grok, the xAI chatbot. (In December 2025 Nvidia struck a large licensing and hiring deal with Groq.)

## The keyboard
- The feature row: the bottom row of keys that always stays, with any keys in any order (Settings > Feature row).
  Keys include H/E record, Send, arrows (left, right, up, down), TAB (a real Tab key, for terminals; with Shift on it
  is Shift+Tab), TAB the next field, TAB the last app, Shift, Aa (case cycle), clipboard keys, C1-C10 buckets,
  A-bucket, Reader, Pin, F1-F6 (show feature rows 1-6), zone keys, Settings.
- Holding the spacebar opens the cursor trackpad: drag to move the caret, corner keys to select, delete, close.
- Arrows are sent as real key presses (DPAD), one press per step, so terminals, web terminals and remote viewers get
  them.
- Widths per button in percent (H 40 / F1 20 / E 40 by default), Settings > Feature row.

## RE Magic
- A writing assistant chat: Anthropic Auto (Haiku judge, Sonnet/Opus), Opus, Sonnet, or Qwen on Groq. Memory in
  Documents/TTTmini/re/ (TAKEOVER, LESSONS, SKILLS, people/, notes/), synced to the private repo RE_MAGIC_DATABASE.

## This help chat
- Settings > search: type a question and press "Ask Claude". Every question and answer is saved in the private data
  repository (TTT_DATA, or RE_MAGIC_DATABASE/ttt-help when TTT_DATA is not there), and what Marko explains about TTT is
  kept in KNOWLEDGE.md there, so later answers know it.
