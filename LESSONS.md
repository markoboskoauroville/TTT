# TTT lessons

- 10.11.2026 Keys only from text files: one picker, detect provider by shape, test with a free auth call, keep only the working keys. No manual key fields (they invite pasting keys into the chat).
- 10.11.2026 A benchmark whose references come from one engine is circular: say so next to the number (Groq 100/99).
- 10.11.2026 A long-press model picker closes on the pick; no OK button.
- 10.11.2026 Every H/E key follows the chosen engine of its language; old NET/LOCAL kinds must not keep their own engine.
- 10.11.2026 Dictation corrections: "creation" means Croatian, "Vesper" means Whisper (Marko's dictation).
- 10.11.2026 Cloudflare: an account-level user token is not enough; a token scoped "Workers AI" with the account id works (Workers AI 200). Workers AI is separate from Pages.
- Never uninstall the keyboard on his phone; always adb install -r.
- 10.11.2026 Multi-account keys are numbered by file order and shown as number only (Groq-2), never the value; rest until 00:00 UTC for daily quotas so the earlier number returns after reset. A test with an invalid first key proves the switch without spending quota.
- 10.11.2026 Feature-row buttons carry a width percent (relative weights, normalised, floor share); Marko: bigger H and E, smaller F1. Several pushes in a row queue builds: the Latest release is whichever run finishes last, so check targetCommit and versionCode of the one installed.
- 10.11.2026 Width percent per feature-row button is relative (normalised to 100) with a floor share; a long label stacks in two lines instead of shrinking to nothing. A switch that simulates a failure (Test: key 1 fails) is how rotation is tested without spending quota.
- 10.11.2026 Feature-row width is a percent per button, normalised; defaults only for rows of four or fewer so a ten-key row is not wrecked. A key tester drops invalid keys at import, so a rotation test needs a key that dies after import (or an engine test hook).
- 10.11.2026 Engine label next to H/E: target the letter's size (17 sp), shrink step by step by real text measurement (TextMeasurer), two lines only below 9 sp, never cut (build 604).
- 10.11.2026 Never look back: a key ring has a persistent forward-only cursor; an exhausted key moves it on and it stays, wrapping to key 1 only after the last. "Start at the lowest key not resting" retried exhausted keys and was wrong.
- 10.11.2026 H/E button text is name + n/total + minutes only; percentages live in the panel and model picker.
