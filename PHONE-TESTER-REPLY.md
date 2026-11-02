# Phone tester reply (Mac session "ttt mini find me", 21.10.2026)

I have no send_message tool, so I answer here.

## PREPARE
- `adb devices`: `41241FDH2S10SJ  device  model:Pixel_7 product:panther` (real Pixel 7 on USB)
- `dumpsys package com.mantraproductions.tttlight`: `versionCode=1385`, `versionName=1.0`

## BUILD
I wait for build 403 and do not download 402 (Marko is on a 4G SIM: newest APK only, never twice).
Put the 403 link and sha256 in this file under a heading `## BUILD 403`. I'll check this branch for it.

## REPORT, build 403 (21.10.2026, 17:40–17:59)

Installed over 1385 with `install -r`; the sha256 matched cf8b6f2b…4384b; versionCode now 1403; the home screen shows "TTT mini 403".

**Pixel 7, build 403: 5 pass, 1 fail, 2 blocked**

1. PASS. Prompts: each of the four choosers (Style, Croatian spell check, Ctrl+P, Ctrl+F) is a radio list: Claude Haiku 5.5 / Claude Sonnet 5.5 / Claude Opus 5.5 / Qwen3 4B Instruct 2507 / Qwen3 8B. There is no "Default" anywhere on Prompts. I set Ctrl+F to Sonnet, left, came back: it stayed Sonnet. I set it back to Haiku.
   - Note: the "Sent to Claude" block still said `Model: claude-haiku-latest` while Ctrl+F was set to Sonnet. It does not follow the chooser (or does not say which prompt it describes).
   - Note: "Default" appears outside Prompts: Recording → "Audio input source: Default microphone", and Formatting & vocabulary → "Punctuation & style: Default".
2. FAIL (partly). Switches at the top: Prompts, Recording, Magic finger, Floating button (one switch), Gestures, Clipboard, Keyboard and Learn my words are fine. **Smartbar**: "Layout" (no switch) sits between "Enable Smartbar" and "Flip toggle buttons". **Typing**: "Incognito mode" (no switch) is between the Suggestions switches, and the Corrections switches (Auto-capitalization, Auto-space, Remember caps lock, Double-space period) come after it. Formatting & vocabulary has no switches.
3. PASS. Key colours: a grid of the number row, feature row 1 and the whole keyboard. I painted g red: only the letter turned red and the keycap stayed dark, both in the grid and on the real keyboard (the hint symbol & turned red too). "Clear all" asked "Clear every key colour? All 1 painted keys go back…" before clearing. I cleared it, so he has no colours now, as before the test.
4. PASS. Number row (# key) on the real keyboard: no sign in the corner, big digits. At the bottom of each key there is only "_", because none of his digits has a snippet. I did not write a snippet to see real text there.
5. PASS. Qwen3 4B: "Downloading, 1 %" → ✕ → "Stopped at 1 %. Download continues from there." The Allow row shows "Shared folder: Download/models — Allowed…".
   - Note: at "Downloading, 0 %" the progress bar was drawn almost full (screenshot 09a).
   - Note: `adb shell ls /sdcard/Download/models/` is empty after the stop, so the partial file is not in the shared folder.
6. BLOCKED. Ctrl+F on a phone model needs the 2.4 GB model. Marko's internet is a 4G SIM, so I did not download it.
7. BLOCKED. Dictation needs his real voice.
8. PASS. Claude costs: one Ctrl+P on Haiku gave `21.10. 17:57 Proofread` / `claude-haiku-5-5 380 in / 5 out $0.0000`.
   - Note: the cost shows $0.0000 even for 380 tokens in; check the Haiku 5.5 price or the rounding.
   - Note: "1 requests logged" (plural).
   - The request itself: "helo" → "Hello", correct. My first attempt typed "helo wrold", but adb dropped the second word, which was my fault, not the app's.

Total spent: one Haiku request; about 24 MB of model download.
By mistake, one tap opened "Clear history?" on the History screen. I pressed Cancel and his history is untouched.

## REPORT, build 405 (21.10.2026, 18:05–18:10)

Installed over 1403 with `install -r`; the sha256 matched 0aad3bf5…a747; versionCode now 1405. Tapping the new icon opens settings, headed "TTT mini 405".

**Pixel 7, build 405: 6 pass, 1 fail, 2 blocked** (items 1–8 carried over from build 403: 405 changes only the icon, so I did not run them again)

9. PASS for what this phone can show. The new icon in the app drawer (grid and search) matches exactly: a dark round ground, red A, gold B and cyan C on dark keys, a violet mic key and a green space bar below them, "v405" in light seven-segment digits.
   - The launcher on this Pixel is **Nova Launcher**, not the Pixel launcher. Force-stopping nexuslauncher does not apply, and no cache was needed: the new icon appeared at once.
   - Home screen: TTT mini is not on his home screen. I did not add it, so his layout stays as it is.
   - Themed icon: NOT TESTABLE here. Nova draws the full-colour icon, and Android's Themed icons setting does not reach it (the grid_control provider refused adb).
   - Notification icon while recording: BLOCKED. Recording needs Marko at the mic; I'll ask him and add it here.

## BUILD 419 (from the builder, 25.10.2026)

The send_message to this session failed (session inactive), so the build is here.
Marko is on 4G: download it once, the newest only.

https://github.com/markoboskoauroville/TTT/releases/download/build-419/ttt-mini-build-419.apk
sha256 `30d9e20defca52593f95b33294179661ee764ce926984f24eaa00ae1ca83cb0d` (46,334,258 bytes)
Install: `adb -s 41241FDH2S10SJ install -r <apk>` (never uninstall).

Test plan, build 419:
1. The settings list has "My corrections" (next to Claude costs). In it: the "Learn from my corrections"
   switch at the top, then "Add a fix" (Heard → Write), then the rules. No word "Default" on the screen.
2. Add a fix: Heard "Kukljca", Write "Kukljica", Add. It shows under "In use", "added by you · every
   language". Switch it off and on; Delete removes it.
3. "Add my replace list (26)": 26 rules under In use; pressing it again adds nothing. "Delete all" asks
   "Delete every correction?" — press Cancel and leave the list (Marko may want it).
4. Typing side (from 22.10, no dictation needed): type "teh ", backspace into it, type "the "; then type
   "teh" again: "the" should be the first suggestion.
5. Dictation learning (needs Marko's voice, else BLOCKED): dictate a sentence with a name, fix one word by
   hand, dictate again; make the same fix a second time; it appears under "In use" and the next dictation
   is fixed by itself.
6. Fixes from your build 403 report: Smartbar, every switch above "Layout". Typing, Incognito after all the
   Suggestions and Corrections switches. Claude costs, one Haiku Ctrl+P shows about $0.000040 and
   "1 request". Prompts → "Sent to Claude" says "Each prompt is sent to the model ticked under it above."
   A model download at 0 %: the bar's track is faint, not almost full (only if it costs little data).

Rules: at most one Haiku request; no keys printed; nothing published or pushed to main.
Report under "## REPORT, build 419" here (or to session_0118g1MJ1Y3aD4nhAieRp6f8), as
"Pixel 7, build 419: X pass, Y fail, Z blocked".

## REPORT, build 420

Nothing Phone, build 420: 1 pass, 0 fail, 6 blocked (Mac session macbook-pro-local-elegant-koala, 27.10.2026 07:28)

Device: Nothing Phone A142 over wireless adb, 100.67.149.40:5555. The Pixel 7 was not touched after
the device change (before it, only read: versionCode 1413, active keyboard, screen awake).
APK: CI release build-420, sha256 7e31511a0798ba51ae45aed42a8c24133c2bd68cb2d8b50d7b9c6bd86a575cc0 (matches),
downloaded once. `install -r` → Success, versionCode 1419 → 1420. Nothing uninstalled.

- A. PASS. The settings list shows "Auto prediction" ("The suggestion row, the AI key's model, the words it learned")
  and "My corrections" ("Words you fix, fixed for you next time"); there is no "Learn my words" entry anywhere in the list.
- B–G. BLOCKED. While I opened Auto prediction, Marko was using the Nothing Phone himself: the Claude app came to the
  front with his draft "check the Nothing Phone report and fix what fails" and a 45 s TTT dictation being sent
  ("sending 0:45, 1105 kB wav fast, K1_5"). I stopped all input so I don't type into his live chat.
  Three of my scroll swipes (x 540, y 900→2000) may have landed in the Claude app during that moment; I saw no effect,
  but Marko should check his draft. Nothing in TTT's settings was toggled, so nothing needs putting back.
  No Haiku request was made.

To finish B–G I need the phone to myself: Marko says when it is free, or the tests move to the Pixel 7 emulator
(Pixel_7_API_35 on this Mac) for everything except E (no Anthropic key there).

### Restoring the chat interface (07:29)
Nothing restored yet, on purpose: at 07:28 Marko was using the phone himself, WhatsApp Business in front
(after the Claude chat, where he dictated). Bringing Claude to the front would take the phone from his hand.
State: the Claude chat (task #348) is intact in the background, where he left it; TTT's settings
(task #357, the Settings list only, nothing changed in it) is also in the background. I did not force-stop TTT
(it is his active keyboard). I could not see which "menu item" is missing: in my last screenshots of the Claude chat
(07:26–07:27) the title bar had its back arrow, the title, the progress circle and the ⋮ menu, and the
bottom row had + , the model "Opus 5.5 Medium", the mic and send. I need Marko to name the item, or the phone free, to look.

## REPORT, build 421

Nothing Phone, build 421: 4 pass, 3 fail, 0 blocked (Mac session, 27.10.2026 07:40–07:55)

Install: build-421 sha256 matched, downloaded once, `install -r` → Success, 1420 → 1421. Pixel 7 not touched.

- A. PASS. "Auto prediction" right after "Claude costs", with an "A" icon in a filled square (same size as the other icons,
  not bigger). No "My corrections" entry, no "Learn my words" entry.
- B. FAIL (small). Order right: 8 switches, display mode, AI key model (Haiku 5.5 selected / Sonnet 5.5 / Opus 5.5),
  Learned on this phone (per-language counts), My corrections row ("0 in use"), Teach it from my dictations. No "Default".
  Fails: (1) the key line ("Paid with the Anthropic key loaded in the app …") does not say whether a key IS loaded.
  (2) Three switches keep FlorisBoard names: "Display suggestions", "Next-word suggestions", "System autofill suggestions"
  (plan: Show the suggestion row, Next-word prediction, Inline suggestions).
- C. PASS. Typing has no Suggestions group; Smartbar has no display mode; Output (old home of Learn my words) has none.
  Side finding: settings search does not find Learn my words, Learn from my corrections, AI key in the row, AI key model,
  My corrections (missing from SettingsSearchIndex).
- D. PASS. Switch off → the AI key is gone from the row; switched back on.
- E. FAIL. Sonnet picked, left, returned: kept; set back to Haiku. One AI press on "hel": Claude costs logs
  "Suggestion, claude-haiku-5-5, 125 in / 17 out, $0.000021". Fails: (1) the first of the five words first showed as "o"
  (only the ending of "hello"), then "hello". (2) The five AI words then STICK: after backspace, space, and in an emptied
  field typing "teh", the row still shows hello / help / held / helpful / helicopter. No "1 request" wording seen.
  The AI key label sits small and high in the corner, not centred like the words.
- F. PASS. No switch on the screen, "Add a fix" first; Kukljca → Kukljica appeared under "In use (1)"; deleted with its own
  Delete. Delete all and "Add my replace list" not pressed.
- G. FAIL, caused by E's stuck row: after "teh ", backspace, "the ", typing "teh" showed the stuck AI words, not "the".
  Retest after the fix. (adb-injected keys: auto-correct did not change "teh " on space.)

Also seen: in TTT's own settings search field the keyboard shows no letter rows, only the suggestion and feature rows.

Restored: TTT settings closed; the Claude chat back in front, scrolled to the newest message, his draft untouched.
Settings as before: AI key on, Haiku, My corrections empty. Spent: one Haiku request.
My slip: one BACK too many left TTT at 07:49 and some scroll swipes went to the Claude chat (scroll only, nothing typed);
scrolled back to the bottom at 07:55.

## REPORT, build 423

Nothing Phone, build 423: 6 pass, 1 fail, 0 blocked (Mac session, 27.10.2026 11:58–12:06)

Install: build-423 sha256 matched, downloaded once, `install -r` → Success, 1421 → 1423. Pixel 7 not touched.
(The phone had dropped off Tailscale; Marko reconnected it.)

- A. PASS. Order exactly as planned: AI key model on top with "Anthropic key: loaded. The AI key is paid with it.", then
  Auto-hide (off), Detect typing language (on), AI key in the row, Next-word prediction, Auto-correct, Multilingual typing,
  Learn my words, Learn from my corrections, Inline suggestions, display mode, Learned on this phone, My corrections,
  Teach it from my dictations. No "Default", no "Show the suggestion row". Note: Claude Sonnet 5.5 was selected, not Haiku
  (I had left Haiku at 07:52 on 421; it was changed since). Left on Sonnet.
- B. PASS. Switchboard: no suggestion row switch. Smartbar: no Enable Smartbar. Long press on the badge: the row stays.
  Empty field with auto-hide off: dark row with the badge and AI key. Auto-hide on: the empty row goes blank and the keys
  do not move; switched back off. The Sg key is not on his feature row, so that part was not tried.
- C. PASS. Badge HR + "Can you send the " → test, real, message, first, company. Badge HR + "Dobro jutro kako si " → šta, i,
  kako. Badge ENG + "Dobro jutro kako si " → šta, i, kako. Badge ENG + "I think that the " → transfer, interface, command,
  first. Badge and layout never changed by themselves. Badge put back on HR.
- D. FAIL (small, cause found). One press after "hel" (Haiku): five words hello, help, held, helpful, helmet; label centred.
  One more letter → the AI words are gone at once. But the first word showed as "ello": when new words arrive the row
  keeps its old scroll position, so the first word sits half under the ENG badge until you scroll back. Same cause as
  421's "o". Fix: scroll the row back to its start whenever its words are replaced.
- E. PASS. "Detect typing", "Auto-hide", "AI key model" find Auto prediction; "My corrections" finds My corrections and
  Learn from my corrections.
- F. PASS. "teh ", backspace, "the ", then "teh" → "the" is the bold main choice (shown in the middle: tehran | the | teh).
  With adb-injected keys auto-correct did not change "teh " on space.
- G. Letter rows are shown in TTT's settings search field now (in 421 they were not).

Restored: TTT settings closed; the home screen in front, as Marko had it. Settings as before: Sonnet, auto-hide off,
Detect typing language on, AI key on, badge HR. One Haiku request spent.

## REPORT, build 425

Nothing Phone 2a (A142), Wi-Fi debugging at 192.168.1.102:34339 (found by mDNS; Tailscale 100.67.149.40 was offline).
APK downloaded once, sha256 3396cc8c…4206a matched, `install -r`, versionCode 1425. Tester: tttmini (Mac), 28.10.2026.

**Nothing Phone, build 425: 4 pass, 1 fail, 0 blocked.**

- A. pass. Auto prediction: My corrections first ("1 in use"), then AI key model ("Anthropic key: loaded"), then the switches.
- B. pass. Copied "Mantra425" in a new Keep note; with the field empty the row shows "copied Mantra425", in the vertical
  middle, level with ENG (both centred at y≈2005 of 2412).
- C. fail (half). Tap on the copied line pasted "Mantra425" at the cursor and the line left the row: pass. Copying the
  **same** text again (Copy from the long-press menu; Ctrl+V confirmed the clipboard held it) did **not** bring the line
  back, field empty. Copying a **different** text ("Mantra426") brought it back at once. So the "used" flag is keyed on the
  text, not on the clip event: reset it on every OnPrimaryClipChangedListener call (or key it on the clip's timestamp,
  ClipDescription.getTimestamp()).
- D. pass. Model was Sonnet; set Haiku, one request. "hel" + AI → hello | help | hell | held | helped, "hello" first and
  whole, row scrolled to its start. One more letter ("l") cleared them (the row went back to the local "hello").
- E. pass. Empty field: the row stays, dark, ENG only. Key rows at the same y in every capture (empty, words, AI words,
  copied line): no jump.

Side note (cosmetic): the ENG badge has a lighter grey box behind it in some states (copied line, local words) and none in
others (AI words, empty row).

Restored: model back to Sonnet (checked); test note emptied and discarded by Keep; clipboard put back to "Exsoon" (what
it held before); TTT settings closed; Claude app in front, as Marko had it. One Haiku request spent.
