---
name: local-first
description: Local first, free first (Marko 15.11.2026). Before ANY call to a paid model in TTT or any Android app, use the phone's free on-device tools: accessibility tree, then Google's ML Kit (text recognition OCR, language identification, on-device translation, entity extraction), Android SpeechRecognizer / TextToSpeech / TextClassifier. Models get text only, never pictures. Use for ANY screen reading, OCR, language detection, translation, "ask a model to..." or new AI-call task.
---

# Local first, free first

Marko, 15.11.2026: *"screenshots needs to be read with the free SDK from Google, not using Claude, not using Gemini ...
models get text only, not pictures ... we need to use free resources to save money on using AI APIs always ... we have more
for food, which is saving humanity ... If you find out any such other blessing ... you always need to suggest and build in."*

Source of the rule and the full catalogue: MANTRA_MANIFEST `modules/local-first-free-sdks.md`. Read it before adding any
AI call.

1. **Screen to text:** accessibility tree first, then screenshot + ML Kit Text Recognition on the phone (TTT
   `MaScreenRead.kt`, fallback ON by default since 15.11.2026). No image ever goes to an API.
2. **Before a model call:** can an on-device kit do it? Language → ML Kit Language ID; translation → ML Kit Translate;
   dates/numbers/addresses → Entity Extraction; links/sentences → TextClassifier / ICU. Only what is left goes to a model.
3. **Ready-made, bundled, small:** the bundled ML Kit variant (offline from the first start), a few MB; no big models.
4. **Suggest the next one:** when you see a paid call a free kit could replace, tell Marko in one line and offer to build
   it; add it to the manifest table the same day.
5. **Check before push:** no Bitmap / base64 image in any AI request body (`scripts/test_screen_read.py` "nothing sends a
   picture"); every new cloud call says in a comment why no on-device kit can do it.

## Next candidates for TTT
- ML Kit Language Identification for RE's HR/EN decision (today MaWordLanguage word lists).
- ML Kit On-device Translation (Croatian model ~30 MB) for "what does this say" / checking a Croatian draft in English.
