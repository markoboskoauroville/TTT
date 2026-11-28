#!/usr/bin/env python3
"""TTT engine matrix (28.11.2026): results.jsonl from the phone -> results.csv + a summary in Markdown.

    python3 tests/matrix/analyze.py <results.jsonl> <out_dir> [label]

WER is word error rate against the known text of the clip (tests/matrix/clips/<clip>.txt) after a normalisation
that does not punish style: lower case, punctuation removed, number words written as digits (English and Croatian),
"14th" -> "14", "7:30" -> "7 30", hyphens -> spaces. The clips are synthetic (macOS say: Samantha for English,
Lana for Croatian), so WER here measures the engines on clean speech, not on Marko's voice.
"""
import csv
import json
import os
import re
import statistics
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
CLIPS = os.path.join(HERE, "clips")

EN = {"zero": 0, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9,
      "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14, "fifteen": 15, "sixteen": 16,
      "seventeen": 17, "eighteen": 18, "nineteen": 19, "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50,
      "fourteenth": 14, "first": None, "second": None}
HR = {"nula": 0, "jedan": 1, "jedna": 1, "dva": 2, "dvije": 2, "tri": 3, "četiri": 4, "pet": 5, "šest": 6, "sedam": 7,
      "osam": 8, "devet": 9, "deset": 10, "jedanaest": 11, "dvanaest": 12, "dvadeset": 20, "trideset": 30,
      "četrdeset": 40, "pedeset": 50}


def words(text, lang):
    t = unicodedata.normalize("NFC", text.lower())
    t = re.sub(r"(\d+)(st|nd|rd|th)\b", r"\1", t)
    t = t.replace(":", " ").replace("-", " ").replace("’", "'")
    t = re.sub(r"[^\w\s']", " ", t)
    out = []
    table = HR if lang == "hr" else EN
    for w in t.split():
        w = w.strip("'")
        if not w:
            continue
        v = table.get(w)
        if v is not None:
            # "forty five" -> 45, "četrdeset pet" -> 45
            if out and out[-1].isdigit() and int(out[-1]) % 10 == 0 and int(out[-1]) >= 20 and v < 10:
                out[-1] = str(int(out[-1]) + v)
            else:
                out.append(str(v))
        else:
            out.append(w)
    return out


def wer(ref, hyp):
    r, h = ref, hyp
    if not r:
        return 0.0 if not h else 1.0
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev = cur
    return d[len(h)] / len(r)


def ref_of(clip):
    p = os.path.join(CLIPS, clip + ".txt")
    return open(p, encoding="utf-8").read().strip() if os.path.exists(p) else ""


def main():
    src, out_dir = sys.argv[1], sys.argv[2]
    label = sys.argv[3] if len(sys.argv) > 3 else ""
    os.makedirs(out_dir, exist_ok=True)
    rows = [json.loads(l) for l in open(src, encoding="utf-8") if l.strip()]
    stt = [r for r in rows if r.get("kind") == "stt"]
    sty = [r for r in rows if r.get("kind") == "style"]
    for r in stt + sty:
        ref = ref_of(r["clip"])
        r["wer"] = round(100 * wer(words(ref, r["lang"]), words(r.get("text", ""), r["lang"])), 1) if r.get("ok") else ""
    with open(os.path.join(out_dir, "results.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["kind", "clip", "lang", "engine_or_provider", "route", "run", "ok", "wer_pct", "ms", "path", "answered_by",
                    "outcome", "error", "last_status", "text"])
        for r in stt:
            w.writerow(["stt", r["clip"], r["lang"], r["engine"], r["route"], r["run"], r["ok"], r["wer"], r["ms"], r.get("path", ""),
                        r.get("answeredBy", ""), r.get("outcome", ""), r.get("error", ""), (r.get("statuses") or [""])[-1],
                        r.get("text", "")])
        for r in sty:
            w.writerow(["style", r["clip"], r["lang"], r["provider"], "", r["run"], r["ok"], r["wer"], r["ms"], "",
                        r.get("answeredBy", ""), "", r.get("error", ""), "", r.get("text", "")])

    md = [f"# TTT engine matrix {label}".rstrip(), ""]
    md.append(f"{len(stt)} transcription takes, {len(sty)} styling requests. WER against the known text after normalisation "
              "(case, punctuation, number words). Synthetic voices (macOS say: Samantha EN, Lana HR).")
    md.append("")
    md.append("| engine | route | takes | success | WER median % | WER max % | latency median s | latency max s | path | answered by | errors |")
    md.append("|---|---|---|---|---|---|---|---|---|---|---|")
    keys = sorted({(r["engine"], r["route"]) for r in stt})
    for e, rt in keys:
        g = [r for r in stt if r["engine"] == e and r["route"] == rt]
        ok = [r for r in g if r["ok"]]
        wers = [r["wer"] for r in ok if r["wer"] != ""]
        lat = [r["ms"] / 1000 for r in ok]
        paths = sorted({r.get("path", "") for r in g})
        by = sorted({r.get("answeredBy", "") for r in ok})
        errs = sorted({(r.get("error") or "")[:70] for r in g if not r["ok"]})
        md.append(f"| {e} | {rt} | {len(g)} | {len(ok)}/{len(g)} | "
                  f"{statistics.median(wers) if wers else '-'} | {max(wers) if wers else '-'} | "
                  f"{round(statistics.median(lat), 1) if lat else '-'} | {round(max(lat), 1) if lat else '-'} | "
                  f"{', '.join(p for p in paths if p) or '-'} | {', '.join(b for b in by if b) or '-'} | {'; '.join(errs) or ''} |")
    if sty:
        md.append("")
        md.append("## Styling (the Style tab's request on the known text)")
        md.append("")
        md.append("| provider | requests | success | latency median s | latency max s | answered by | errors |")
        md.append("|---|---|---|---|---|---|---|")
        for pv in sorted({r["provider"] for r in sty}):
            g = [r for r in sty if r["provider"] == pv]
            ok = [r for r in g if r["ok"]]
            lat = [r["ms"] / 1000 for r in ok]
            by = sorted({r.get("answeredBy", "") for r in ok})
            errs = sorted({(r.get("error") or "")[:80] for r in g if not r["ok"]})
            md.append(f"| {pv} | {len(g)} | {len(ok)}/{len(g)} | {round(statistics.median(lat), 1) if lat else '-'} | "
                      f"{round(max(lat), 1) if lat else '-'} | {', '.join(b for b in by if b) or '-'} | {'; '.join(errs)} |")
    open(os.path.join(out_dir, "summary.md"), "w", encoding="utf-8").write("\n".join(md) + "\n")
    print("\n".join(md))


if __name__ == "__main__":
    main()
