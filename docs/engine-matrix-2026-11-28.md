# TTT engine matrix, 28.11.2026 (Pixel 7, builds 708 to 711)

Marko: "with artificial synthetic voice. Test all the engines ... Using Frankfurt, no Frankfurt. And see where is the bug,
why it doesn't work all the time. With styling, without styling. And for styling must be simple way to switch between API
from Anthropic or API from Groq."

## How it was measured

- **Clips** (tests/matrix/clips): macOS `say`, Samantha for English and Lana for Croatian, 16 kHz mono WAV with the known
  text beside each one: en_short (2.3 s), en_para (15.2 s, numbers and punctuation), en_long (45.0 s), hr_short (2.7 s),
  hr_para (14.5 s). Synthetic speech is clean, so WER here ranks the engines on clean speech, not on Marko's voice.
- **Takes** go through the keyboard's own `transcribe()` (adb broadcast `dev.ttt.engines.MATRIX`, EngineMatrix.kt +
  DictateController.debugTakeFile): the FRA / chain / old-path decision, the engine, the key ring, the 3 s retries. Nothing
  is typed or kept (no history, no clip buffer). The H/E engine and the FRA switch are set per case and put back afterwards.
- **Styling** (`dev.ttt.engines.STYLE_MATRIX`): the Style tab's exact request on each clip's known text, per provider.
- **End to end**: a real recording through the Mac-microphone link (tests/matrix/fake_mac_mic.py stands in for Screen
  Copy), an adb-pressed E key, the style box, Type, and the field read back in a new Google Keep note.
- 13 engines x 5 clips x 3 runs per route = 195 takes per route; 30 styling requests per build.
- Raw data: tests/matrix/results/*.jsonl, tables: */results.csv and */summary.md. `python3 tests/matrix/analyze.py`.

## Build 708 (before the fixes)

### Frankfurt (FRA on, Tailscale on for the test)

| engine | success | WER median % | WER max % | latency median s | latency max s | answered by |
|---|---|---|---|---|---|---|
| groq_turbo | 15/15 | 0.0 | 9.4 | 1.5 | 3.8 | groq_turbo |
| groq_v3 | 15/15 | 0.0 | 33.3 | 1.5 | 4.2 | groq_v3 |
| deepgram | 15/15 | 0.0 | 5.0 | 2.5 | 8.6 | deepgram |
| assemblyai | 15/15 | 0.0 | 9.4 | 5.4 | 10.1 | assemblyai |
| cloudflare | 15/15 | 0.0 | 9.4 | 3.6 | 11.6 | cloudflare (key only in Frankfurt) |
| gemini | 15/15 | 2.5 | 50.0 | 2.1 | 5.1 | gemini |
| google_v1 | 15/15 | 2.5 | 12.5 | 3.6 | 12.4 | google_v1 (key only in Frankfurt) |
| google_chirp | 15/15 | 4.7 | 16.7 | 5.0 | 12.5 | google_chirp (key only in Frankfurt) |
| speechmatics | 15/15 | 3.1 | 33.3 | 2.8 | 8.6 | speechmatics |
| parakeet (local) | 15/15 | 0.7 | 34.4 | 1.5 | 8.2 | parakeet |
| android (local) | 0/15 | - | - | - | - | EN: empty answer; HR: 10 retries, 31 s, fail |
| soniox | 0/15 | - | - | - | - | "engine error" retried 10 x, about 60 s each |
| elevenlabs | 0/15 | - | - | - | - | no key anywhere |

High WER is almost all the 2-word Croatian clip ("Bok Ana" heard as "Bokana") and Parakeet on hr_para.
One extra take with **Tailscale off** (as found on the Pixel): 74.8 s of "Frankfurt not answering" retries, then failure.

### Direct (FRA off, Tailscale off as found)

| engine | success | WER median % | latency median s | latency max s | answered by |
|---|---|---|---|---|---|
| groq_turbo | 15/15 | 0.0 | 1.8 | 5.3 | groq_turbo |
| groq_v3 | 15/15 | 0.0 | 1.8 | 6.5 | groq_v3 |
| deepgram | 15/15 | 0.0 | 2.3 | **37.9** | deepgram (en_long run 1: first try "no answer") |
| assemblyai | 15/15 | 2.5 | 2.6 | 6.2 | legacy AssemblyAI path (by design) |
| gemini | 15/15 | 4.7 | 3.0 | 7.8 | gemini |
| speechmatics | 15/15 | 3.1 | 2.8 | **55.5** | speechmatics (en_long run 1: first try "no answer") |
| parakeet | 15/15 | 0.7 | 1.5 | 5.1 | parakeet |
| android | 0/15 | - | - | - | EN empty, HR 31 s fail |
| cloudflare, google_v1, google_chirp, soniox, elevenlabs | "15/15" | - | - | - | **groq_v3 (EN) / parakeet (HR): another engine, silently** |

### Styling (build 708)

| chosen | requests | success | latency median s | answered by |
|---|---|---|---|---|
| Anthropic | 15 | 15/15 | 1.6 | claude-sonnet-5-5 |
| Groq | 15 | 15/15 | 1.7 | **claude-sonnet-5-5** (the Pixel has no RE Groq key, so "Groq" was Sonnet) |

End to end on 708 (en_short, style on): the box opened from the keyboard, Sonnet styled it, Type put it into Keep.

## Why it does not work all the time (ranked by how often it hit)

1. **Another engine answered without a word** (EngineChain.dictate): 75 of 195 direct takes. When the H/E engine is not
   usable on the phone (no key here) and FRA is off, the first usable engine of the order answered. Fixed in 709: the take
   stops, the audio is kept, the status says "X is not set up on this phone · FRA on runs it in Frankfurt".
2. **STYLE "Groq" answered with Sonnet** (DictateController.reChatAnthropic): 15 of 15. Groq without an RE key fell through
   to Anthropic. Fixed in 709: RE's Groq ring, else TTT's Groq keys, else "Groq: no key" and the Original is shown.
3. **Android on-device**: 30 of 30. English answers "" (the take looked successful and typed nothing), Croatian is not on
   the recognizer and was retried 10 x. Fixed in 710: an empty answer is a failure with the audio kept; a missing
   recognizer language is said at once.
4. **Frankfurt unreachable**: Tailscale is disabled on the Pixel (package disabled by the user, app stopped), so FRA on
   = 75 s of retries and a failure every time; that is why FRA was off on the Pixel. Fixed in 709: the status names
   Tailscale; TTT asks a running Tailscale to connect (it cannot start a disabled or force-stopped one: Marko's switch).
5. **Long takes timed out on the first try**: 2 of 30 long direct takes (Deepgram 37.9 s, Speechmatics 55.5 s), the whole
   audio sent twice. The attempt deadline (8 s + 0.5 s per audio second) also covers the upload. Fixed in 711: 10 s + 1 s
   per audio second.
6. **Dead engines retried for a minute**: Soniox "engine error" 15 of 15 in Frankfurt, about 60 s each. A plain HTTP 4xx
   now fails at once on the phone and in Frankfurt (relay v3); Soniox's credit is still empty.
7. **Android blocks TTT's internet when the keyboard hides** (seen, not as a failed take): on this metered Wi-Fi, with
   "Background data" off for TTT, the keyboard up = allowed (BFGS), Home pressed = blocked (METERED_USER_RESTRICTED).
   A take, a Frankfurt poll or a style request in flight at that moment retries "no internet". 711 names it in the
   status line; the cure is Marko's switch (Background data on for TTT), not changed by the test.
8. **Frankfurt key store** (code and store state, not reproduced live): each phone sent its keys with `replace`, so the
   phone that changed keys last wiped the other phone's rings, and the other phone's hash said "already there". 709 copies
   additively and resends once when Frankfurt says it has no key.

Not a cause on the Pixel: the style box opening from the keyboard and Type landing in the field (both worked on 708).
Also fixed: the "trimmed 70%" log line compared 48 kHz bytes with 16 kHz bytes; volume-key presses filled the 400-line log.

## The styling switch (709)

STYLE: OFF / ANTHROPIC / GROQ in one place:
- the recorder badge (the status line he sees while talking): "STYLE off", "✓ STYLE Sonnet" or "✓ STYLE Groq"; a tap goes
  OFF -> ANTHROPIC -> GROQ -> OFF;
- a STYLE row with three chips at the top of the H/E long-press panel, the model under each (Sonnet / qwen model);
- remembered (the Style badge setting + the AI engine setting, the same ones Settings > Auto correction and AI shows).

Models: ANTHROPIC = the Anthropic model chosen in the box (Sonnet 5.5 by default, Opus or Haiku if picked); GROQ = Qwen on
Groq, the largest Qwen in Groq's live list (fallback qwen/qwen3.8-27b), RE's own Groq key ring if it has keys, else TTT's
Groq keys. Failover: ANTHROPIC fails -> Groq writes it (backup, said in the box); the chosen provider fails, has no key or
takes more than 30 s -> the box turns to Original at once with one line saying why; Type always works; if the box cannot
open, the words are typed as dictated after 1.5 s; if Type cannot reach the field in 4 s, the text is on the clipboard.

## After the fixes (build 711 on the Pixel; 712-713 only add the separate Groq style cursor and the short badge)

Quick pass, 1 run per clip (tests/matrix/results/build711_*):

| check | build 708 | build 711 |
|---|---|---|
| Direct, engine without a key on this phone (Cloudflare, Google, Chirp, Soniox, ElevenLabs) | answered by groq_v3 / parakeet, "success" | stops at once: "X is not set up on this phone; with FRA on, Frankfurt runs it" (25/25) |
| Android on-device, English | "" returned as success, nothing typed | "returned no text for speech (audio kept)", 0.5-6 s |
| Android on-device, Croatian | 10 retries, 31 s | "not set up", under 0.1 s |
| FRA with Tailscale disabled | 74.8 s, "Frankfurt did not answer" | 80.8 s (6 s waiting for Tailscale to wake), status "Tailscale off" on every retry and in the error |
| ElevenLabs in Frankfurt | "no key in Frankfurt" | "no key in Frankfurt (and none on this phone)", 1-3 s |
| STYLE Groq | answered by claude-sonnet-5-5 (15/15) | answered by groq:qwen/qwen3.8-27b (10/10, median 0.9 s); Anthropic Sonnet 10/10, median 2.0 s |
| Working engines direct | groq_turbo, groq_v3, gemini, deepgram, assemblyai, speechmatics, parakeet: 35/35 | 35/35 |
| Working engines in Frankfurt | 150/150 incl. Cloudflare, Google, Chirp | 50/50 (1 run) |
| End to end (fake Mac mic, E key, box, Type into Keep) | Sonnet: landed | GROQ picked on the panel chip: box styled by Groq, Type landed in Keep |

Still open: Deepgram direct on the 45 s clip took 45.6 s once more (one attempt, no timeout now with the wider deadline;
through Frankfurt the same clip takes 2-9 s). Soniox has no credit (engine error in Frankfurt). ElevenLabs has no key.
Android on-device returns nothing for English from a file on this Pixel (cause in Google's recognizer not found; it now
fails honestly). Groq's free keys hit Qwen's rate limit after about three style requests in a row (each key rests a minute);
from 712 that no longer moves the dictation key ring. The badge text "✓ STYLE Groq" was cut on the recorder; 713 shortens it.
