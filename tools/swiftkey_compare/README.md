# SwiftKey vs TTT, live on two emulators (7.11.2026)

Marko: *"use Microsoft Swift Keyboard to improve our TTT keyboard ... in one emulator load Swift Keyboard
and open some text editor to type into it. On another, open the same text editor and TTT ... tune TTT to fit
predictions from Swift."*

Set-up used on chromium-vm (the Linux VM): Pixel 1 = adb 127.0.0.1:5555 with SwiftKey (signed in, English +
Croatian, QWERTY, emoji predictions off), Pixel 2 = 127.0.0.1:5556 with TTT. Both open `pad.html` (Name,
Email, Text) in the WebView Browser Tester, served from the VM (`python3 -m http.server 8765 --bind 172.17.0.1`).
Needs `tesseract-ocr` with `eng` and `hrv`.

- `kb.py` taps text on each keyboard by key coordinates (1080x2400 screens) and screenshots.
- `ocr.py` reads the suggestion strip (SwiftKey's band at y≈1598, TTT's at y≈1352).
- `run.py <sk|ttt> <tag>` types every phrase of `cases.py` (or `CASES=cases2`) and stores the strips.
- `run3.py` types the typo set (`cases3.py`) with a space and reads what ended up in the box.
- `compare.py <swiftkey-tag> <ttt-tag>` scores: is SwiftKey's word in TTT's strip / top three.
- `res_skref.json`, `res_sk2ref.json`: SwiftKey's answers, OCR corrected by eye (diacritics).
- `phone.py`: email learned in the Email box and recalled in Text; a number recalled from "09155" and after
  "my number is".
- `simulate.py`: the ranking re-implemented in Python over the shipped dictionaries, to tune weights offline.

Paths in the scripts say SCRATCH where the session's working folder was; set them before running.
Results 7.11.2026: SwiftKey words in TTT's strip 28 → 58 of 100 (builds 448 → 458); typo set: TTT fixed
2 → 12 of 30 (SwiftKey ~15), then the misspelling list (builds 463+).
