# TTT engine matrix build 708, route Frankfurt (Tailscale on)

195 transcription takes, 0 styling requests. WER against the known text after normalisation (case, punctuation, number words). Synthetic voices (macOS say: Samantha EN, Lana HR).

| engine | route | takes | success | WER median % | WER max % | latency median s | latency max s | path | answered by | errors |
|---|---|---|---|---|---|---|---|---|---|---|
| android | fra | 15 | 0/15 | - | - | - | - | chain direct | - | ; IllegalCallableAccessException: Android on-device did not answer after |
| assemblyai | fra | 15 | 15/15 | 0.0 | 9.4 | 5.4 | 10.1 | FRANKFURT | assemblyai |  |
| cloudflare | fra | 15 | 15/15 | 0.0 | 9.4 | 3.6 | 11.6 | FRANKFURT | cloudflare |  |
| deepgram | fra | 15 | 15/15 | 0.0 | 5.0 | 2.5 | 8.6 | FRANKFURT | deepgram |  |
| elevenlabs | fra | 15 | 0/15 | - | - | - | - | FRANKFURT | - | IllegalCallableAccessException: ElevenLabs: no key in Frankfurt |
| gemini | fra | 15 | 15/15 | 2.5 | 50.0 | 2.1 | 5.1 | FRANKFURT | gemini |  |
| google_chirp | fra | 15 | 15/15 | 4.7 | 16.7 | 5.0 | 12.5 | FRANKFURT | google_chirp |  |
| google_v1 | fra | 15 | 15/15 | 2.5 | 12.5 | 3.6 | 12.4 | FRANKFURT | google_v1 |  |
| groq_turbo | fra | 15 | 15/15 | 0.0 | 9.4 | 1.5 | 3.8 | FRANKFURT | groq_turbo |  |
| groq_v3 | fra | 15 | 15/15 | 0.0 | 33.3 | 1.5 | 4.2 | FRANKFURT | groq_v3 |  |
| parakeet | fra | 15 | 15/15 | 0.7 | 34.4 | 1.5 | 8.2 | chain direct | parakeet |  |
| soniox | fra | 15 | 0/15 | - | - | - | - | FRANKFURT | - | IllegalCallableAccessException: Soniox did not answer after 10 tries |
| speechmatics | fra | 15 | 15/15 | 3.1 | 33.3 | 2.8 | 8.6 | FRANKFURT | speechmatics |  |
