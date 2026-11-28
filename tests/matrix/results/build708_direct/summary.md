# TTT engine matrix build 708, route direct (Tailscale off)

195 transcription takes, 0 styling requests. WER against the known text after normalisation (case, punctuation, number words). Synthetic voices (macOS say: Samantha EN, Lana HR).

| engine | route | takes | success | WER median % | WER max % | latency median s | latency max s | path | answered by | errors |
|---|---|---|---|---|---|---|---|---|---|---|
| android | direct | 15 | 0/15 | - | - | - | - | chain direct | - | ; IllegalCallableAccessException: Android on-device did not answer after |
| assemblyai | direct | 15 | 15/15 | 2.5 | 16.7 | 2.6 | 6.2 | old direct path | legacy:assemblyai |  |
| cloudflare | direct | 15 | 15/15 | 5.0 | 33.3 | 1.6 | 5.2 | chain direct | groq_v3, parakeet |  |
| deepgram | direct | 15 | 15/15 | 0.0 | 5.0 | 2.3 | 37.9 | chain direct | deepgram |  |
| elevenlabs | direct | 15 | 15/15 | 5.0 | 33.3 | 1.5 | 5.2 | chain direct | groq_v3, parakeet |  |
| gemini | direct | 15 | 15/15 | 4.7 | 50.0 | 3.0 | 7.8 | chain direct | gemini |  |
| google_chirp | direct | 15 | 15/15 | 5.0 | 33.3 | 1.6 | 5.3 | chain direct | groq_v3, parakeet |  |
| google_v1 | direct | 15 | 15/15 | 5.0 | 33.3 | 1.6 | 5.2 | chain direct | groq_v3, parakeet |  |
| groq_turbo | direct | 15 | 15/15 | 0.0 | 9.4 | 1.8 | 5.3 | chain direct | groq_turbo |  |
| groq_v3 | direct | 15 | 15/15 | 0.0 | 33.3 | 1.8 | 6.5 | chain direct | groq_v3 |  |
| parakeet | direct | 15 | 15/15 | 0.7 | 34.4 | 1.5 | 5.1 | chain direct | parakeet |  |
| soniox | direct | 15 | 15/15 | 5.0 | 33.3 | 1.5 | 5.2 | chain direct | groq_v3, parakeet |  |
| speechmatics | direct | 15 | 15/15 | 3.1 | 33.3 | 2.8 | 55.5 | chain direct | speechmatics |  |
