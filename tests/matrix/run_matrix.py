#!/usr/bin/env python3
"""Drive TTT's engine matrix on a phone over adb (28.11.2026). Never prints or touches a key.

    python3 tests/matrix/run_matrix.py <serial> stt   [--engines a,b] [--routes fra,direct] [--runs 3] [--only clip,clip]
    python3 tests/matrix/run_matrix.py <serial> style [--providers anthropic,groq] [--runs 3] [--only ...]
    python3 tests/matrix/run_matrix.py <serial> pull <out.jsonl>

Clips: tests/matrix/clips/<lang>_<name>.wav (16 kHz mono, plain 44-byte header) + .txt, pushed to
Android/data/<pkg>/files/matrix/. Results: files/matrix/results.jsonl (appended), progress in files/engines_log.txt.
"""
import argparse
import os
import subprocess
import sys
import time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "com.mantraproductions.tttlight"
RCV = PKG + "/dev.patrickgold.florisboard.dictate.engines.EngineDebugReceiver"
FILES = f"/sdcard/Android/data/{PKG}/files"
HERE = os.path.dirname(os.path.abspath(__file__))


def adb(serial, *a, check=True):
    return subprocess.run([ADB, "-s", serial, *a], capture_output=True, text=True, check=check).stdout


def log_lines(serial):
    return adb(serial, "shell", f"cat {FILES}/engines_log.txt", check=False).splitlines()


def wait_done(serial, start, marker, timeout):
    t0 = time.time()
    seen = start
    while time.time() - t0 < timeout:
        lines = log_lines(serial)
        for l in lines[seen:]:
            print("  " + l, flush=True)
        seen = len(lines)
        if any(marker in l for l in lines[start:]):
            return True
        time.sleep(5)
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("serial")
    ap.add_argument("mode", choices=["stt", "style", "pull", "push"])
    ap.add_argument("out", nargs="?")
    ap.add_argument("--engines")
    ap.add_argument("--routes", default="fra,direct")
    ap.add_argument("--providers", default="anthropic,groq")
    ap.add_argument("--runs", type=int, default=1)
    ap.add_argument("--only")
    ap.add_argument("--timeout", type=int, default=7200)
    a = ap.parse_args()
    s = a.serial
    if a.mode == "pull":
        adb(s, "pull", f"{FILES}/matrix/results.jsonl", a.out)
        print("pulled", a.out)
        return
    adb(s, "shell", f"mkdir -p {FILES}/matrix")
    clips = os.path.join(HERE, "clips")
    for f in sorted(os.listdir(clips)):
        if f.endswith(".wav") or f.endswith(".txt"):
            adb(s, "push", os.path.join(clips, f), f"{FILES}/matrix/{f}")
    if a.mode == "push":
        return
    start = len(log_lines(s))
    cmd = ["shell", "am", "broadcast", "-n", RCV]
    if a.mode == "stt":
        cmd += ["-a", "dev.ttt.engines.MATRIX", "--es", "r", a.routes, "--ei", "n", str(a.runs)]
        if a.engines:
            cmd += ["--es", "e", a.engines]
        marker = "matrix done"
    else:
        cmd += ["-a", "dev.ttt.engines.STYLE_MATRIX", "--es", "p", a.providers, "--ei", "n", str(a.runs)]
        marker = "style matrix done"
    if a.only:
        cmd += ["--es", "only", a.only]
    print(adb(s, *cmd).strip())
    ok = wait_done(s, start, marker, a.timeout)
    print("DONE" if ok else "TIMEOUT")


if __name__ == "__main__":
    main()
