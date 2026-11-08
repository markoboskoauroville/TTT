#!/bin/bash
# vsay.sh — the VM's virtual microphone for TTT on the redroid Pixel (Marko, 8.11.2026: "build their
# virtual audio device so you can talk virtually to the virtual machine and emulator").
#
#   tools/vm_voice/vsay.sh [-s SERIAL] [-v VOICE] [-l hr|en] "text to say"
#
# The redroid phones run Android's stub audio HAL: "Built-In Mic" records silence, so nothing can be
# played into it. TTT has its own way in instead: a WAV in cache/dictate_pending is transcribed as a
# recording the next time a text field opens, with the engine and language set in TTT. This script:
#   1. speaks the text with edge-tts (VOICE, default hr-HR-SreckoNeural / en-US-AndrewNeural),
#   2. makes it 16 kHz mono WAV (ffmpeg -nostdin),
#   3. copies it as root into TTT's pending folder (the phone is userdebug: su 0),
#   4. focuses the text field of the page in front (tap X,Y = FIELD, default 540 700),
#   5. waits for the new row in TTT's history database and prints:  engine <TAB> seconds <TAB> text
# The text is read from sqlite, exactly as TTT wrote it: no OCR.
#
# Needs: adb, ffmpeg, edge-tts (EDGE_TTS=path, default: edge-tts on PATH), a page with a text field
# open on the phone (tools/swiftkey_compare/pad.html), and -l to match the language key pressed last
# in TTT (the pending file uses TTT's current language; the script only picks the voice from it).
set -euo pipefail
SERIAL=127.0.0.1:5556; VOICE=""; LANG_=hr; FIELD="${FIELD:-540 700}"
while getopts "s:v:l:" o; do case $o in s) SERIAL=$OPTARG;; v) VOICE=$OPTARG;; l) LANG_=$OPTARG;; *) exit 2;; esac; done
shift $((OPTIND-1)); TEXT="$*"; [ -n "$TEXT" ] || { echo "usage: vsay.sh [-s serial] [-v voice] [-l hr|en] text" >&2; exit 2; }
[ -n "$VOICE" ] || VOICE=$([ "$LANG_" = en ] && echo en-US-AndrewNeural || echo hr-HR-SreckoNeural)
EDGE=${EDGE_TTS:-edge-tts}
A="adb -s $SERIAL"; P=com.mantraproductions.tttlight; DB=/data/data/$P/databases/dictate_history
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
"$EDGE" -v "$VOICE" -t "$TEXT" --write-media "$T/v.mp3" >/dev/null 2>&1
ffmpeg -nostdin -loglevel error -i "$T/v.mp3" -ar 16000 -ac 1 "$T/v.wav"
U=$($A shell su 0 stat -c %u /data/data/$P | tr -d '\r')
last=$($A shell su 0 sqlite3 $DB "'select coalesce(max(_id),0) from dictate_history'" | tr -d '\r')
$A shell input tap 500 310 >/dev/null; sleep 1          # another field first, so the text field reopens
$A push "$T/v.wav" /data/local/tmp/vsay.wav >/dev/null
$A shell su 0 sh -c "'mkdir -p /data/data/$P/cache/dictate_pending && cp /data/local/tmp/vsay.wav /data/data/$P/cache/dictate_pending/vsay-\$(date +%s).wav && chown -R $U:$U /data/data/$P/cache/dictate_pending'"
t0=$(date +%s); $A shell input tap $FIELD
for _ in $(seq 1 90); do
  sleep 1
  row=$($A shell su 0 sqlite3 -separator "'	'" $DB "'select providerName, text from dictate_history where _id > $last order by _id desc limit 1'" | tr -d '\r')
  [ -n "$row" ] && { name=${row%%	*}; printf '%s\t%ss\t%s\n' "$name" "$(( $(date +%s) - t0 ))" "${row#*	}"; exit 0; }
done
echo "no transcription within 90 s" >&2; exit 1
