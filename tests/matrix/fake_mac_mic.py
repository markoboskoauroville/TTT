#!/usr/bin/env python3
"""A stand-in for Screen Copy's Mac microphone (MacMicLink contract), for end-to-end tests of the real recorder (28.11.2026).

    adb -s <serial> reverse tcp:8879 tcp:18879
    python3 tests/matrix/fake_mac_mic.py 18879 <control_file>

TTT sends "TTT-MIC 1 <rate>\\n"; we answer "OK 16000 1 s16le\\n", stream the PCM of the WAV named in <control_file>
(16 kHz mono, 44-byte header) in real time, add 0.8 s of silence and close: the take ends with what arrived, exactly as
a dropped link does, and TTT transcribes it. Only adb-injected presses ask the link (a finger never does), and nothing
is played out loud anywhere.
"""
import socket
import sys
import time

port = int(sys.argv[1])
control = sys.argv[2]
srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("127.0.0.1", port))
srv.listen(4)
print(f"fake mac mic on {port}", flush=True)
while True:
    c, _ = srv.accept()
    try:
        c.settimeout(5)
        line = b""
        while not line.endswith(b"\n") and len(line) < 120:
            b = c.recv(1)
            if not b:
                break
            line += b
        wav = open(control).read().strip()
        print(f"hello {line!r} -> streaming {wav}", flush=True)
        c.sendall(b"OK 16000 1 s16le\n")
        pcm = open(wav, "rb").read()[44:]
        chunk = 640  # 20 ms
        t0 = time.time()
        for i in range(0, len(pcm), chunk):
            c.sendall(pcm[i:i + chunk])
            ahead = (i / 32000.0) - (time.time() - t0)
            if ahead > 0:
                time.sleep(ahead)
        c.sendall(bytes(32000 * 8 // 10))
        time.sleep(0.9)
        print(f"done {len(pcm) / 32000.0:.1f} s", flush=True)
    except Exception as e:
        print("error", e, flush=True)
    finally:
        c.close()
