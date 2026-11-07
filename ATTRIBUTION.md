# Attribution

**Voice Type** is developed by Marko Boško, Mantra Productions.

It is built on the work of others, and this file exists so that is never in doubt.

## Upstream

Voice Type began as a fork of **Dictate** by DevEmperor,
<https://github.com/DevEmperor/DictateKeyboard>, licensed under the Apache License 2.0.
Dictate is itself built on **FlorisBoard** by Patrick Goldinger,
<https://github.com/florisboard/florisboard>, also Apache 2.0.

The great majority of this codebase is still their work. Voice Type diverges in the
areas listed below and is maintained independently from here on, which means upstream
is not responsible for anything that goes wrong in this app.

If you like what this app does, the parts that are good are largely theirs. Dictate is
sold on Google Play and supporting the original author there is a fair thing to do.

## What Voice Type changes

- Providers reduced to AssemblyAI, Gemini, Anthropic and the on-device engine
- API keys are imported from a text file, never pasted, with several keys per provider
  and automatic fallback when one is rejected or out of quota
- Uploads are compressed to Ogg Opus before leaving the phone
- The microphone key opens a dedicated transcribe screen instead of recording in the bar
- That screen carries an oscilloscope, a braille spinner and an elapsed clock
- Provider errors are rewritten into one plain sentence instead of raw JSON
- Dark palette of the Mantra Productions house style

## Third party assets

Speech models are redistributed by upstream under their own licences: Whisper (MIT,
OpenAI), Parakeet TDT (CC-BY-4.0, NVIDIA), sherpa-onnx export tooling (Apache 2.0).
Bigram data derives from the Leipzig Corpora Collection (CC BY).

## Licence

Apache License 2.0, unchanged from upstream. See LICENSE and NOTICE.

## AOSP LatinIME (ideas, 22.10.2026)

The personal model's correction learning and recency weighting (`dictate/nlp/MaCorrections.kt`)
follow two ideas from the Android Open Source Project's LatinIME keyboard, Apache License 2.0: a
word the user corrects is unlearned from the user history, and a word's weight decays with the time
since it was last used (LatinIME's forgetting curve). No LatinIME code was copied; the Kotlin is
written for this app. HeliBoard (GPL-3.0), which carries the same LatinIME learning today, was read
for comparison only and nothing of it is included.

- **Common English misspellings** (`app/src/main/assets/ime/dict/en_misspellings.txt`, 7.11.2026): derived from
  [Wikipedia:Lists of common misspellings/For machines](https://en.wikipedia.org/wiki/Wikipedia:Lists_of_common_misspellings/For_machines),
  CC BY-SA 4.0. See `en_misspellings_LICENSE.txt` beside it.
- **Conversational word pairs and triples** (`*_bigrams.txt`, `*_trigrams.txt`, 7.11.2026): counted from the
  OpenSubtitles v2018 monolingual corpora (OPUS, opus.nlpl.eu), English and Croatian, first 12 million lines each.
