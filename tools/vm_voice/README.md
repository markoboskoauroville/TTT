# vm_voice: a virtual microphone for TTT on the VM's phones

Marko, 8.11.2026: *"build their virtual audio device so you can talk virtually to the virtual machine
and emulator for Pixel 7 and continue working on corrections with local model."*

The redroid Pixels on chromium-vm run Android's stub audio HAL: "Built-In Mic" exists and records
silence, so no sound can be played into it. TTT's own pending-audio folder is the way in: a WAV in
`cache/dictate_pending` is transcribed as a recording when a text field opens, by the engine and in the
language set in TTT. `vsay.sh` speaks a sentence with edge-tts, drops it there as root and reads TTT's
result from its history database (exact text, no OCR).

    EDGE_TTS=~/venv/bin/edge-tts tools/vm_voice/vsay.sh -l hr "Možeš li mi poslati adresu restorana?"
    On-device (offline)	103s	možeš li mi poslati i adresu restorana, molim te

First run on the emulator, build 504, 8.11.2026 (Parakeet loading included; later runs are faster).
On the real Pixel 7 there is no root: there the phone's speaker plays the clip and TTT's own record
key records it (session notes in momentaryupdates.md, 8.11.2026).
