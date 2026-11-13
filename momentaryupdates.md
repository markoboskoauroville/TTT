# TTT: momentary updates

Every request word for word, saved and pushed before any code (MANTRA_MANIFEST requests-saved-first).



## 9.11.2026: RE Magic as an assistant, a GitHub token, the RE Magic Database, the pop-up, the flexibility tests

> "RE Magic should actually become an accessibility service. It can do anything I ask. I can ask 'read me the last messages'. It's like a Siri for WhatsApp, or email, or whatever is in front." / "TTT should also be able to load a GitHub token, and then if I ask RE something related to GitHub, or to find some history on GitHub to fill the chat, it can do it." / "RE has its own repository on GitHub called RE Magic Database and stores its data there, in a private repo." / "Teach TTT not to write its replies to me into the chat. There must be a pop-up window: I read its reply and I say OK. Only messages go into the chat." / "Write a test for TTT. Also test the corrections: changing the style, taking out certain words, adding punctuation, anything. Imagine what a user can ask: delete this message and create a new one. Just test the flexibility of the system." (Marko, 9.11.2026, relayed by the master session; plan in RE_ASSISTANT_PLAN.md.)

**Status:** saved (late, after the work started; the pop-up, flexibility tests, RE Magic Database seed and sync, and assistant slice 1 are built or coded, the GitHub history token is next).

## 9.11.2026: skills on the phone, written by AI after each conversation

> "Is my feature coded, writing skills as we go, understanding my style of communication and what is done and what is not done? I want skills on my own phone, written by AI after each conversation, updating the skills, stored in Documents/TTT (the default folder)." (Marko, 9.11.2026, relayed by the master session: style.md, people/, threads/ in the MARKO_COMMUNICATION shape, updated by one cheap background prompt after a chat, RE uses the thread file as context, Settings lists and edits them, re-skill-sync merges both ways.)

**Status:** saved first.

## 9.11.2026: RE reads the screen: "take a screenshot, read the latest message and reply to it"

> "Update TTT so it can take screenshots on the phone. If I say to RE Magic 'take a screenshot, read the latest message and reply to it', it is able to do it. It has the tools inside the app, and also in the skill it understands that when I say 'take the screenshot', it takes it and transcribes it." (Marko, 9.11.2026, relayed by the master session with the plan: read the accessibility node tree first, then takeScreenshot plus on-device OCR, vision on the model only as a last paid resort; spoken intents in English and Croatian; the reply is put in the field and never sent; skill section "Screen reading"; a Read screen switch in Settings, RE Magic.)

**Status:** saved first.

## 9.11.2026: the AssemblyAI record key's dot is red, not blue

> "Sometimes, by the way, update my app TTT. I need the red colour, the red dot, in the button in the feature row which is activating AssemblyAI. Now it's blue; make it red." (Marko, 9.11.2026, relayed by the master session)

**Status:** saved first; the dot of the E/H network keys (RECORD_EN_NET, RECORD_HR_NET) is now clear red (0xFFE53935), Parakeet's stays amber.

## 9.11.2026: Spelling: misheard words collected and corrected automatically (AssemblyAI keyterms + a local pass)

> "Any misspelled words — you understand from the content what the correct spelling is. Just collect those mistakes somewhere and correct them automatically in the future, because my AssemblyAI is doing this. What can I do?" (Marko, 9.11.2026, relayed by the master session with the plan: keyterms/custom spelling on every request; a local post-correction pass with the same table, Grok stays only with Elon Musk/xAI; one file Documents/TTTmini/dictation_corrections.json generated from MANTRA_MANIFEST modules/dictation-corrections.md by `re-skill-sync corrections push`; the RE learning loop appends pairs he changes twice to a learned list that `re-skill-sync pull` brings back; Settings, a small Spelling page with on/off per pair.)

**Status:** saved first.

## 9.11.2026: RE Magic upgrade: icon in settings, a skill editor, AssemblyAI only, a skill that learns from his corrections

> "Let's update TTT keyboard: the RE Magic button. It must have its icon inside the settings, which looks the same as the button icon, and in settings it must have an editor of the skill. Every time I'm typing with this button, which must use exclusively AssemblyAI for transcription, I say what kind of message to write, and then I make corrections. I also have my specific Ashay Afterman style and my writing style. Research my writing style and the Ashay Afterman style, to have the right Croatian text and the right English text, and write the skill — we are not shipping the skill with the app — in my API folder, call it re_ttt_skill.md. That skill will be transferred over Wi-Fi to my Nothing Phone for testing. Every time I correct something, the skill is automatically updated by the same API, so over time the RE Magic Button works flawlessly, reading the skill file, which is also readable in the same Documents folder where the TTT stuff is. To start not from zero, seed the skill in advance from my other communication repositories, which are private and only collect data. And the app must monitor the messages I send and compare them with what it composed, so my correction gets logged, compared, and the skill is updated. With accessibility it needs to understand which button I pressed — just before I press send, copy that message, analyse it, update the skill, and then press send for me — or any other method you find in which the application recognises my changes and updates the skill automatically in the background by sending prompts comparing the generated message with the corrected one, so next time it writes the right message without correction." (Marko, 9.11.2026)

**Status:** saved first. "Ashay Afterman" = Yshai Afterman (MANTRA_MANIFEST writing-styles.md section 2). Skill: ~/Downloads/API/re_ttt_skill.md.

## 22.10.2026: Croatian long-press letters; POP-UP CONFIGURATION with a key's ID card

> "After you do everything, quickly jump to my application TTT Mini and fix the pop-up of the letters when I long press. I want to see just creation letters popping up. I'm missing Croatian letters. This keyboard is Croatian and English in letters only. Secondary pop up can also be adjusted in settings, so there will be many empty buttons for the secondary pop up in normal letters. For C, D, and S we have special characters in Croatian and other languages, but what is empty can be again configured in settings. Plus other buttons can be added, not only one. So we're going to reuse the part of TTT mini for coloring the buttons because the whole layout is there and you need to write these settings. Rename it to "Coloring the Buttons" and "Pop-up Configuration". In that pop-up configuration I have all the keys. So when I long press on the key, it opens up the additional setting menu where there is like an ID card for that key. Then there will be a big key at the top. That's nice UI design. It's like an ID card has a picture of the person. Now this ID card has the picture of the key itself. Then there will be pop-up one, and under pop-up one will be + icon, and then I can add more pop-ups. Then I can customize my keyboard with pop-ups as much as I like. Thank you. And you can test the keyboard on my phone, which is attached already, so just continue testing." (Marko, 22.10.2026)

1. A long press shows only the Croatian letters (č ć đ š ž on c, d, s, z); the keyboard is Croatian and English letters only.
2. Every key's long-press pop-ups are configurable; keys with none are empty until he fills them, and a key can have several.
3. The settings screen that colours the buttons (its whole keyboard layout reused) becomes two: "Coloring the Buttons" and "Pop-up Configuration".
4. In Pop-up Configuration, a long press on a key opens its ID card: the key drawn big at the top (the card's "photo"), then Pop-up 1, and under it a + that adds more pop-ups.
5. Tested on the Pixel 7 (attached by wireless adb).

**Status:** built 22.10.2026: MaPopups (a letter offers exactly its list, the default č ć / đ / š / ž, no accents and no symbol or number hints on letters), Settings → "Coloring the Buttons" (renamed) and "Pop-up Configuration" (the letter grid, a long press or tap opens the ID card: the key big, Pop-up 1 …, ✕ per pop-up, + to add, Save). scripts/test_popups.py 14 checks. Found on the Pixel: with the Launchkey plugged in, Android hides the on-screen keyboard (the Launchkey also announces a USB typing keyboard). Checked on the Pixel 22.10.2026, build 410: both entries in Settings beside Number row; the grid shows č ć / đ / š / ž under their letters; a long press on c opens the ID card (big key C, Pop-up 1 č, Pop-up 2 ć, ✕ each, +, Cancel / Save). Not yet: the long press on the keyboard itself (Marko's thumb). Settings search does not find the new screen yet (SettingsSearchIndex.kt, next build).

## 22.10.2026: SwiftKey-quality prediction that learns from his typing and his corrections

> "Also update my engine in the keyboard for storing and predicting words. We want a SwiftKey quality, so you need to find the best algorithm open source on GitHub. Find the keyboard with most forks and most popular, and get its keys words prediction algorithm which learns from my own typing. Also, if I type something and correct it, that means this application needs to understand that it was wrong typed and I'm correcting. It needs to understand that and write correction in the prediction algorithm." (Marko, 22.10.2026)

1. The word store and prediction rebuilt from the best open-source keyboard on GitHub (the most popular, most forked), its prediction that learns from his own typing.
2. A word he types and then corrects is learnt as a typo: the correction goes into the prediction.

**Status:** built 22.10.2026. GitHub measured: FlorisBoard most starred (8.7k, this app's base, weak prediction), AnySoftKeyboard most forked (951), HeliBoard strongest learner (6.4k) but GPL-3.0, which cannot go into this Apache-2.0 app; its learning is AOSP LatinIME's (Apache-2.0), and two of its ideas were written fresh in Kotlin: (a) correction learning: the text before the cursor is watched; a finished word cut back and retyped close to it (Damerau–Levenshtein 1–3) is a typo → it is unlearned from the counts, never offered again, and when he types it the correction is the first suggestion (in his case); kept per language; (b) the forgetting curve: within a tier, count × 0.5^(days/30), floor 0.15. Also fixed: deleted characters no longer go into the sentence being learned (`teh` deleted + `the` used to learn `tehthe`). scripts/test_corrections.sh runs 22 cases on the Mac with Gradle's own Kotlin compiler. First build failed on a gate test that matched an exact line (fixed); build 410 green and installed on the Pixel 22.10.2026. Learning from real typing needs Marko's thumbs (adb text bypasses the keyboard).

## 22.10.2026: TTT, "Talk to Type Advanced Keyboard", gets its product page

> "now start building product page for TTT Mini and rename it to only TTT, which means "Talk to Type". Call it "Talk to Type Advanced Keyboard". Then make many screenshots through ADB and explain and ask for donations. One more product and push it also to my biography page. Build it please, same principle. Third product" (Marko, 22.10.2026)

1. The app is renamed TTT (Talk to Type); the product is "Talk to Type Advanced Keyboard".
2. A product page like Shruti Box and DJ Mantra: many real screenshots taken over adb, each feature explained, "Download the latest version", the Tibetan bowl for donations, the app icon as favicon.
3. Linked from the biography's Kod tab (product-pages-on-the-bio.md).

**Status:** done 22.10.2026. App renamed TTT (launcher), full name "TTT · Talk to Type Advanced Keyboard", release titles "TTT build N" (APK file name unchanged). Page live at https://talktotype.pages.dev (site/build.py): hero from Gemini on the Pixel (Pondicherry café veranda), 12 real screenshots over adb (keyboard, č ć pop-up on the keyboard itself, ID card, colours, feature rows, copy row, cursor pad, number row, reader, prompts, switchboard, symbols), six more features, Download the latest version, the Tibetan bowl, favicon = app icon. Biography Kod card now TTT · Talk to Type → talktotype.pages.dev. Seen on the Pixel: holding c on the keyboard shows č ć only.

## 22.10.2026: the Facebook post about TTT

> "please inside the chrome open the facebook and make a post about this keyboard" (Marko, 22.10.2026)

1. A post in the same style as the Shruti Box and DJ Mantra posts (plain, English then Croatian, the page link), written in Facebook in his Chrome and not sent: he posts it.

**Status:** composed 22.10.2026 in Create post on his profile (Chrome, auroville.community): plain text, English then Croatian, the link talktotype.pages.dev, the hero picture attached. Not posted; audience "Only me" left for him.

## 1.11.2026: the page order (banner, introduction, lifestyle, technical, donation)

> "the page is perfect. Let's do it all pages" (Marko, 1.11.2026, about mantramiditools.pages.dev)

1. Every public app page in the order of MANTRA_MANIFEST modules/app-page-order.md: the banner (the app at work, name and one line on it), a short introduction with the download button and the version line, the lifestyle picture, the technical part, the donation bowl.

**Status:** done 1.11.2026 (site/build.py): banner = img/hero.jpg with the TTT keys and the line on it, the introduction with Download the latest version, the technical part, the bowl. The lifestyle picture is still needed (a placeholder comment marks its place): a person using TTT in real life, to be made in Gemini.
**Status, 1.11.2026 (later):** the lifestyle picture is in (made in Gemini, lifestyle.jpg); the page order is complete.

## 7.11.2026: tune TTT to SwiftKey, live on two Pixel emulators; Croatian ships instead of German; emails, numbers and more

> "Please, now we are need to basically reverse engineer, or we can say use Microsoft Swift Keyboard to improve our TTT keyboard. Each, which means in one emulator load Swift Keyboard and open some text editor to type into it. On another, open the same text editor and TTT. And you need to compare how Swift keyboard is predicting the words. And if the same word is not predicted inside my TTT, you need to adjust algorithms so it fits the same algorithm from Swift. So you need to go back and forth and tune TTT to fit predictions from Swift. And also TTT must nicely save all email addresses and names. You need to figure out how by testing it live. Swift is doing all these things better than TTT. So idea is to bring TTT to the level of Swift in key— in predictions of the word, and for both English and Croatian." (Marko, 7.11.2026; later: "keep going until midnight")

> "Please, if TTT ships with German, you need to change the source code. Don't ship German, ship Croatian. Remove German from the source code." (Marko, 7.11.2026)

> "Also, our app needs to recognize 3 categories of text: normal text, email addresses, and phone numbers. And when I'm typing number, if I'm starting with certain digits, it needs to recognize it's the phone number and just pull it out of the database. Email is the same. Please build, upgrade our TTT to have this. I think this is how SwiftKey is working because when I type phone number or email address, it immediately recognizes it now for me from the database. and find more such useful shortcuts to present the right information at the right time" (Marko, 7.11.2026)

1. Pixel 1 (SwiftKey 9.13, signed in to his Microsoft account, English + Croatian) and Pixel 2 (TTT) type the same 56 phrases into the same page; TTT is tuned until it offers what SwiftKey offers.
2. Croatian replaces German in the APK; German is removed from the keyboard's dictionaries.
3. Email addresses, phone numbers (and names) are kept whole and offered as soon as they are started.

**Status (7.11.2026, builds 447–458):**
- German out, Croatian in: de.json removed; hr.json, hr_bigrams.txt, hr_trigrams.txt bundled; German umlaut/ß restoration replaced by Croatian diacritic restoration (c→č/ć, s→š, z→ž, dj→đ; sto→Što, kuca→Kuća swapped on space when the diacritic form is ≥20 points commoner, like SwiftKey). Speech-to-text keeps German as a dictation language (that is the transcription list, not the keyboard).
- Croatian actually reaches the strip: under the HR badge (his profile has only en-US) the shipped engine was dropped; now it reads the same keyboard with the Croatian dictionary. Bundled files win over older downloads (a downloaded old Croatian list kept answering "dobro → prihvaćen").
- Typing language: the two shipped dictionaries now count as evidence (on a fresh phone "good m" was read as Croatian and offered "mogu, mislim").
- Ranking like SwiftKey: completions ranked by the previous one and two words (bigrams + trigrams), conversational data from OpenSubtitles merged into the bigrams, trigrams new (tools/glide-dict/subtitle_pairs.py, merge_bigrams.py, subtitle_triples.py, make_trigrams.py). Croatian completion ignores diacritics (cov → čovjek). A phrase in the third slot when one word clearly follows (going to, want to, end of). Simulated on SwiftKey's own choices for the 56 phrases: 35 → 65 of 100 SwiftKey words in TTT's top three.
- MaAddressBook: emails, phone numbers, web addresses and IBANs (checksum-checked) saved whole when the field is left or the cursor moves past them; offered first from their first characters (091 55 also finds +385 91 555…), in empty email / phone / URL fields, and after "my email is", "moj broj je", "IBAN", "moj račun je". Contacts: opt-in (READ_CONTACTS declared, never asked; allow it in Android settings → TTT → Permissions), then contact emails, numbers and names (a capitalised "Mar" → "Marko …") join.
- Live: build 452 on the emulator: 37 of 100 SwiftKey words in TTT's strip (448: 28), by OCR; "laku n" → noć, "good m" → morning, "do you w" → want / want to, "thank you " → for. Phone number saved and offered after "my number is" (454).
- SwiftKey itself: emoji predictions switched off; it has no setting for more than three suggestions (the arrow at the left of its bar opens more).
**Status (7.11.2026, evening, builds 459–490):**
- Typos: common misspellings were in the English dictionary itself (teh, recieve, goverment …): 45 removed, the Wikipedia list of common misspellings (CC BY-SA) ships as en_misspellings.txt and is swapped in on space, plus im → I'm, dont → don't, i → I. Both languages count as words, fixes from the other language are offered. Typo set (30): TTT ~20 fixed (SwiftKey ~16).
- Live phrase sets (OCR, rescored): set 1 33 → 43 → 64 of 100 SwiftKey words in TTT's strip (448 → 452 → 480); held-out set 2 35 of 73.
- The personal model never learned from typing (only commitText reported to it; letters go through commitChar): fixed, typed letters and picked words now build the sentence it learns. Names (capitalised, unknown to both dictionaries, used twice) are offered from the start, from both language models: "Xim" → Ximenka, checked live (489).
- Address book checked live: email typed in the Email box recalled from "kbt"; number recalled from "09155" and after "my number is"; saved once, whole (no halves).
- ANR found and fixed: every keystroke started a suggestion job and none was cancelled; with the spelling-fix generator running in three or four at once, garbage collection stalled the main thread for 5 s. Now one job at a time, distance-2 fixes only up to 8 letters, an allocation-free Croatian prefix compare. No ANR over 56 phrases since (486).
- **Builds 473–477 crashed on the first letter** (address book, fixed in 479, guarded since; a gate test pins it). Use 480 or newer.
- CI kept the wrong two releases when builds finished out of order (it deleted the newest, 476): now by build number.
- Not from today and not fixed: on a very slow first start the keyboard can open before its layouts are indexed ("Extension org.florisboard.layouts not found", no letters) until TTT restarts. Seen only on the overloaded emulator.

## 7.11.2026: WA key, WhatsApp controlled by Sonnet, never sending

> "please in parallel Add a new feature to the app, and that is a WhatsApp control. It's going to be able to control WhatsApp through wireless debugging, and commands are going to Sonnet or to my engine. And I can ask Sonnet, for example, find me this and that person and write this and that message, or take screenshot of past conversation and continue conversation. So it's a WhatsApp controller working with Sonnet, and it needs to have a special button that WA is on the button and looks similar as RE button, and it puts similar mechanism, just different engine to control WhatsApp when it's opened. Like I'm controlling it, searching for contact, and then write the message and never send the message automatically." (Marko, 7.11.2026)

1. A WA key beside RE, looking like it, with the same mechanism (press, speak the instruction).
2. The instruction goes to Sonnet (or his engine), which drives WhatsApp while it is open: search a contact, open the chat, read the past conversation (screenshot), write the message.
3. The message is never sent automatically: it is left in the field for him.

**Status:** saved first.
**Status (7.11.2026, build 477):** the WA✦ key sits after re✦ (placed once on his rows, then his to move). A press records the instruction in English, a second press sends it (RE's mechanism); a long press stops a running task. MaWhatsApp: Claude Sonnet (newest, the Anthropic key in TTT) reads WhatsApp through TTT's accessibility service (chats, names, messages, buttons and fields as a numbered list) and acts with tap / type / scroll / back / open_whatsapp, up to 15 steps, then one line in the message bar. It never sends: there is no send action, taps on send, voice message, call, video call, Pošalji are refused in code, typing only into text fields, actions only while WhatsApp is in front. Accessibility rather than wireless debugging: the phone needs no adb pairing (which Android drops on every Wi-Fi change), and the past conversation is read as text from the screen instead of a screenshot (screenshots would need a new capability on the service, and a capability added once made the service impossible to switch on). Checked on the emulator: the key is drawn and starts the recording; the emulator has no keys and no WhatsApp, so the Sonnet loop itself is first tried on his phone (Accessibility → TTT must be on).

**Status (7.11.2026, 19:50 UTC, build 491 suite):** 0 ANR, set 1 60/100, set 2 34/73, address book recalled
(kbt → email, 09155 → number, "my number is" → number). Regression found: Croatian spelling fixes (mozda → možda,
hvla → hvala, kuca → kuća) stopped, because the personal model now learns typed words and its hit for the typed
word went first. Fix: the shipped engine's spelling fix goes first unless the personal hit is his own correction
(tier 4) or a Haiku word (tier 0).

**Status (7.11.2026, 20:55 UTC, build 492):** English typos 16/20 fixed (SwiftKey 14). Croatian misses in the suite were
mostly the test harness (Shift taps landing late typed "hVla", "zaSOt"); typed cleanly, mozda → možda and hvla → hvala
work. A real gap: "zasot" → "zašto" (a swap plus a dropped diacritic). Croatian corrections are now also looked up with
the diacritics folded away, so such a typo is one edit from its word (offline: zasot → zašto, nemma → nema,
dobor → dobro, prijatlj → prijatelj).

**Status (7.11.2026, 21:15 UTC, build 493):** Croatian typos now fixed live: dobor → dobro, sutar → sutra,
mozda → možda, vidmo → vidimo, prijatlj → prijatelj, kuca → kuća. Still missed: "zasot", "nemma" in a fresh
field after English, because an unknown word keeps the last language. Now the last language is left when it has no
usable word one edit away and the other has (zasot → Croatian, becuase after Croatian → English).

**Status (7.11.2026, 22:40 UTC, build 494):** 0 ANR. Typos: 20/30 fixed (SwiftKey 16), now also hvla → hvala,
nemma → nemam. Set 1: 62/100. Address book: kbt → email, 09155 → number, "my number is" → number. Found a race on a
slow phone: space before the suggestions for "is" were ready committed the fix for "i" ("I"). Auto-correction now
uses only suggestions computed for the word being typed.

> Please continue testing until five aam.

**Status:** saved first. 5 am taken as 05:00 CET = 04:00 UTC on 8.11.2026, same clock as "midnight" before.

> Please update TTT app to use offline transcription services using Whisper Turbo Model. It means you need to remove the old model and use a whisper toolbo and in settings I need to have a switch. Transcription model is the settings entry. Inside I have assembly AI and Whisper. So there are options either assembly AI, either whisper or whisper as a fallback if assembly AI get rejected or is not happening for some reason

**Status:** saved first (7.11.2026, 23:00 UTC).

**Status (7.11.2026, 23:40 UTC):** built. Whisper Turbo (large-v3-turbo, sherpa-onnx int8, ~1 GB from Hugging Face,
checksums verified) replaced Parakeet, which is deleted from the phone on start. Settings → **Transcription model**:
AssemblyAI / Whisper / AssemblyAI with Whisper as fallback (default). The fallback catches any failure: no key,
refused key, no connection, timeout, server error. The model downloads from that screen. Gate:
scripts/test_transcription_model.py (18 checks). Not yet run live: the 1 GB download and a Whisper transcription.

> Don't change anything, parquet should be fine.But just test parquet with artificially created voices with edge TTS.And see if everything working fine through emulator.And even you can combine the tests you type with par by with voice and you can see how Words are corrected. by our algorithm for creating words.

**Status:** saved first (7.11.2026, 23:50 UTC). Read as: Parakeet stays the on-device model (the Whisper Turbo swap is
undone, the Transcription model setting stays, its on-device engine is Parakeet); test it on the emulator with
edge-tts voices, English and Croatian, and with typing after the dictation.

**Status (7.11.2026, 23:58 UTC):** Whisper Turbo swap undone; Parakeet TDT 0.6B v3 is the on-device model again and
is not deleted. Settings → Transcription model stays: AssemblyAI / Parakeet (on the phone) / AssemblyAI with Parakeet
as fallback (default; any AssemblyAI failure). Next: edge-tts voices on the emulator.

**Status (8.11.2026, 00:50 CET, build 501): Parakeet with edge-tts voices on Pixel 2.** The Transcription model screen
works; Parakeet downloaded inside the app (~4 min) and was chosen. Six generated sentences (3 English, 3 Croatian, edge-tts
Guy/Jenny/Aria/Srećko/Gabrijela) went through TTT's pending-file path: 5 exact, diacritics included ("Možeš li mi poslati
adresu restorana"). "Danas je lijep dan, idemo na kavu" came out as "dana see alip dan, idem on a cable": Parakeet itself
mishears it; the sherpa-onnx inside TTT (1.13.3) gives exactly that on the VM too, 2.0.1 comes closer ("Dana se lipdan,
e demonaku"). In the app Parakeet took 11-14 s per 3 s sentence on this overloaded 2-core VM (2.3-8.7 s run directly on
the VM). Typing after a dictation: "see you t" → too, tomorrow; "and the ph" → phone; "i pon" → ponovno, ponekad;
"hvala ti n" → na. Two fixes: (1) a one-sentence dictation was lowercased whole ("i will call you", "friday", "marko"):
now only its first word, and not "I" or a name; (2) personal words with no context ("the", "to") no longer go before the
shipped engine's top two.

**Status (8.11.2026, 01:45 CET, build 502):** best suite so far. 0 ANR; typos 25/30 fixed (SwiftKey ~16), every Croatian
one; set 1 64/100, set 2 34/73; address book recalled. Parakeet + typing confirmed: "I will call you…" keeps its capital,
"see you t" → too, tomorrow. Remaining misses wich, alot, seperate, accomodate: a misspelling with several fixes now
swaps in the first when it is far commoner (which over witch, a lot over allot; 71 of 200 such entries), and a settled
misspelling skips the edit-distance search so the fix is ready before space on a slow phone.

**Status (8.11.2026, 03:15 CET):** suite with a fresh personal model (build 503): all 20 English typos fixed, Croatian
fixed when typed alone (the suite's misses were the emulator's timing); set 1 63/100 (top 3: 33), set 2 35/73 (top 3:
22, best). **Fallback tested live:** engine "AssemblyAI, Parakeet as fallback" with a deliberately invalid AssemblyAI
test key: AssemblyAI answered 401 in 0.4 s, the same recording went to Parakeet and "please send me the address of the
restaurant" was typed. The history entry said AssemblyAI; it now says "Parakeet (fallback)" and the Parakeet model.

**Status (8.11.2026, 03:40 CET, build 504):** history label confirmed live (entries 17, 18: "Parakeet (fallback)",
parakeet-tdt-0.6b-v3). Harder Parakeet sentences (edge-tts): "My number is 091 555 0101, call me after five" exact
("after 5"); "The meeting is at 3:30 on Monday the 12th in Zagreb" → "at 330 … the twelfth"; "marko dot bosko at gmail
dot com" → "marco.bosco at gmail.com" (dots heard, name spelled the Italian way, "at" stays a word); "Moj broj je 091 555
0101, nazovi me poslije pet" → number perfect, "nazovim"; "Sastanak je u ponedjeljak u pola četiri u Zagrebu" exact;
"Nazovi Ximenu kad sletiš, i pozdravi Ivanu" → "nazovi imenu Kacletiš, i pozdravi Ivanu" (rare names are hard).
Idea, not built: a dictated "x at gmail.com" could be joined into an address and snapped to a saved one in the address book.

**Status (8.11.2026, 04:10 CET, final of the night, build 504):** 0 ANR. Typos 28/30 fixed (SwiftKey ~16; the misses are
the emulator's timing, both fixed when typed alone). Set 1 62/100 (top 3: 30), set 2 33/73 (top 3: 18), within the
emulator's run-to-run noise of the best runs (65, 35). Address book: kbt → email, 09155 → both saved forms of the number
(the dictated "0915550101" was saved too, as typed text is). Parakeet: on-device and as fallback, tested live with edge-tts
voices. Stopped here: a further build plus a full suite (~1 h) would not finish before 5 am.

## 8.11.2026 – install on the real Pixel 7

> Please install on the phone over wireless debugging. The phone is Pixel 7, latest version of the TTT, and then summarize in voice and install on the same phone voice engine, latest what we had on the repositories, and tell me in voice summary of recent development and if more testing will help. i am on pixel 7, just to remind you
> feel free to use tailscale to access this phone

**Status:** done 05:20 UTC: Pixel 7 reached over Tailscale adb (100.70.23.53:5555); TTT build 506 (versionCode 1506, code = build 504) and VOICE_ENGINE_ANDROID v1 installed, TTT still the default keyboard; spoken summary sent with macsay.

## 8.11.2026 – yes to all, test on the real Pixel 7

> Yes to all. Test it on real phone. Fix the engine and spoken email addresses be corrected to match the address book. Yes. And 2 phones are now over Tailscale. You can use them to test this TTT in next couple of hours. Please pick a faster phone, which is Pixel phone, and test everything on the Pixel phone, please.

**Status:** saved first. Plan: sherpa-onnx 1.13.3 → 2.x; dictated "x at gmail dot com" snaps to an address-book email; build; test everything on the real Pixel 7 (100.70.23.53).

## 8.11.2026 – 10 minutes: TTT's Speechify voices into Parakeet on the Pixel 7

> Please also now for next 10 minutes using the engine for voice from the— this app TTT itself, Speechify Voices, test it how our local voice recognition engine Sherpa is working. Next 10 minutes only you need to talk speaker and recording microphone, debug this system and put in a manifest If any of API codes are missing, you need to ask the Cloud Code session inside macOS to transfer from the folder Downloads/API API codes as file to that phone and that load them automatically through file picker inside TTT.

**Status:** done 06:50 (Pixel time). Keys were already on the phone (6 AssemblyAI, Anthropic, 21 Speechify), so nothing had to come from the Mac. Parakeet downloaded on the phone (~4 min on Wi-Fi). Turning on TTT's Accessibility service from adb was refused (security setting): Marko switches it on himself; without it TTT's reader cannot speak the screen, so the voices were edge-tts clips played by Chrome through the phone's speaker, recorded by TTT's E key (speaker → microphone, real acoustics).
- AssemblyAI: "Please send the report to marco.bosco@gmail.com before Friday." / "I will call you tomorrow at half past 3 about the meeting."
- Parakeet (sherpa 1.13.3, build 506): English right ("marco.bosco at gmail.com", fixed by MaSpokenEmail in the next build); **Croatian came out in Cyrillic** ("Можеш ли ми послати адрес у ресторана? Молим те."). Fix: MaCyrillic, every on-device result written in Croatian Latin.
- The H key (record + translate) gave nothing for a Croatian clip and kept recording after the second press; stopped with send. Not yet looked into.
- Setting put back to "AssemblyAI, Parakeet as fallback".

## 8.11.2026 – Croatian or English only; names with k; one more hour of tests

> Please look at these tests. When the voice is speaking Croatian, I get Cyrillic letters. Can you force the model to either talk Croatian— I mean, recognize Croatian or English, nothing in between?
> and please keep on testing for next one hour
> we are doing both tests now, voice recognition and correction algorithms
> my name is spelled with k, and any dictation should convert marko with c to marko with k, bosko with c to bosko with k

**Status:** saved first. Parakeet v3 has no language switch (it picks the language itself), so the output is forced instead: Cyrillic → Croatian Latin (MaCyrillic, commit 17bd7348). Names: MaNameSpelling, Marco → Marko, Bosco → Bosko in every dictation.

## 8.11.2026 – feature row: Talk to AssemblyAI, Talk to Parakeet

> please, in the app, build in for my feature row new buttons: talk to assemblyai talk to, then the name of the local model

**Status:** saved first. Two buttons that record one dictation with that engine, whatever the Transcription model setting says.

## 8.11.2026 – a virtual audio link on the VM; keep the Pixel 7

> please, in the app, build in for my feature row new buttons ... and then button for both languages We already have this icon. They should be the same, only different color, so I know do I speak local or I speak to the network.
> Which means that since local engine doesn't support creation and it makes a lot of mistakes, first of all, try to force it not to make mistakes. But if it makes mistakes, it needs to be corrected by this application.
> Now you need to continue testing Croatian spelling and correction on virtual machine only. You need to build their virtual audio device so you can talk virtually to the virtual machine and emulator for Pixel 7 and continue working on corrections with local model.
> You have this phone now for next hours. Actually, just continue using it, but at the same time build this link if it's not available on virtual machine, but continue using Pixel phone as much as you need.

**Status:** in progress (08:40 Pixel time).
- Engine keys (da6af165, build 524): row "Talk to" with ● E ● H blue (AssemblyAI) and ● E ● H amber (Parakeet), added once to his rows. Tested on the Pixel 7: amber H ran Parakeet with the setting on fallback.
- Local corrections: MaCroatianFix (d8a67a3d, 526455b0, 6148d18f, 470127e1): Cyrillic → Latin, diacritics, one-edit mishearings scored with hr bigrams/trigrams, split words joined and re-split, rare words with a commoner twin, ikavian ije. Pixel 7, build 525, amber H: "Možeš li mi poslati adresu restorana? Molim te.", "žao mi je, danas nemam vremena za ručak", "kad stigneš kući, pošalji mi poruku" (518 wrote "stignes kuci, posalji").
- VM virtual microphone: the redroid mic is a stub (silence); tools/vm_voice/vsay.sh speaks with edge-tts into TTT's pending folder and reads the result from the history database; 16-20 s per sentence once Parakeet is loaded.
- Mac session (MANTRA_VOICE ears) took Parakeet + tools/hr_fix (hr_fix.py, spoken.py) and re-syncs when told.

## 8.11.2026 – stop testing; engine name before HR/ENG while recording

> please stop testing, write the latest updates if there are any, and then please close the session … there is something popping up when I'm recording this recorder. There is E and G badge. And just in a small font before E and G I want to have confirmation of the engine.

**Status:** saved first. Testing stopped (page server on the VM stopped; the Pixel 7 keeps build 525, setting "AssemblyAI, Parakeet as fallback", Parakeet downloaded, adb reverse removed).
- While recording, the HR/ENG key on the strip now has the engine in small letters before it: "AssemblyAI" in blue or "Parakeet" in amber (the Talk-to colours). Same rule as the request itself (MaProviders.transcribesLocally), so a Talk-to key, the setting "on the phone", and fallback without a key all show Parakeet.
- Open: Marko turns on Accessibility → TTT himself (Speechify reader, WA key). Builds after 525 (vowel-shape scoring, this label) are on CI, not installed on the phone.
- Build 529 (fc894bb0, this label): CI stopped twice at "Fetch sherpa-onnx native libs" with HTTP 504 from the sherpa-onnx download host (10:08 and 10:09 UTC), before compiling. Not the code: the local test gate passed. Next session: `gh run rerun 42470111209` once the host answers, then install on the Pixel 7.

## 8.11.2026 – Talk-to keys silent

> Please remove this pop-up message when I'm recording my voice with parkit or with assembly AI. Let it just be as before. Silent, please.

**Status:** saved first. The four Talk-to keys (blue/amber E, H) no longer show "Recording … to AssemblyAI / on the phone (Parakeet)"; the engine name on the recording strip stays. The plain E/H keys are unchanged.
- Build 531 (61047375) built and installed on the Pixel 7 (versionCode 1531): silent Talk-to keys and the engine name before HR/ENG.

## 8.11.2026 – amber key says "No API key yet"

> Dobijam ovu čudnu grešku u mojoj aplikaciji. Samo dotaknem lokalni engine i dobijem API grešku. O čemu se tu radi?

**Status:** saved first. The screenshot is 1084 px wide with three-button navigation: the Nothing Phone 2a, not the Pixel 7. TTT refuses a recording only when there is neither an AssemblyAI key nor a downloaded local model, so that phone has neither; the message only named the key. Fix: a Talk-to key is refused for its own engine's reason — amber: "Parakeet is not on this phone yet. Download it in settings first."; blue: no AssemblyAI key.

## 8.11.2026 – the Nothing Phone over ADB

> Molim te, imaj na umu da se našoj aplikaciji može pristupiti preko ADB-a. Ako ne možeš ići izravno, možeš proći kroz macOS sesiju.

**Status:** saved first.
- 12:47 Nothing Phone time: while loading AssemblyAI-api.txt in the picker, an incoming call (Igor) was answered — probably by the tap on the file, which landed when the call screen came up. Stopped touching the phone. Key import not confirmed; Parakeet not downloaded. Lesson: check `dumpsys telecom` for a ringing call before every tap on his phone.

## 8.11.2026 – remove the engine label

> Telefon je sada slobodan, pa molim te, moraš ukloniti ovu oznaku prije slanja Wavea. Tražio sam oznaku po kitu ili AssemblyAI, ali vidim da nije potrebna. Molim te ukloni tu oznaku, to je za sada moj zahtjev, hvala.

**Status:** saved first.
**Status:** done — the small AssemblyAI/Parakeet name before HR/ENG is gone (the Talk-to keys stay silent).
- Build 534 (2a2f6bde) installed on the Nothing Phone and the Pixel 7 (versionCode 1534).

## 1.11.2026: My studio setup, a link above the bowl

> "Just above the Tibetan bowl for donation, you can just make one link, and then it will open the page with all lifestyle images and images of these products which I'm using in my development studio ... For this link, you can say "My studio setup." ... It is opening a new page, which is going to call studio setup mantra.pages.dev" (Marko, 1.11.2026)

1. One centred link "My studio setup" right above the donation bowl, opening https://studiosetupmantra.pages.dev in a new tab, with the line "The gear these apps are made with · affiliate links on Amazon.de".

**Status:** committed 1.11.2026, NOT pushed or deployed: waiting until Marko registers studiosetupmantra.pages.dev in Amazon PartnerNet and the studio page is live.

## 9.11.2026 – AT2020: the keyboard deletes the 2; AUTO menu

> "I've just been on Amazon on my Nothing phone, and I was trying to type this microphone 'AT2020'. I type 8... 2... 0, and the keyboard is deleting [the] 2. Please debug this keyboard TTT, because I want no automatic replacement of anything. If there is auto replacement there must be a special item in the menu. It needs to have a nice icon: 'AUTO'. The new standard for icons: [a] white background with cut-out text, so it represents what's inside, similar to the 'A' with cut-out text we already have. And if there are any auto-corrections, put them under that menu, so I can quickly disable or enable them. The corrections must be easily accessed and seen in the settings, and separate things, because they're so annoying." (Marko, 9.11.2026)

**Status:** saved first; task 25 of mac-ses9-08-nov.
**Root cause (9.11.2026):** KeyboardManager treated a digit as punctuation: pressing "2" after "AT" committed the auto-correct fix for the word "AT" (replacing the composing word), so the digits typed so far vanished. Fixed: a digit never ends a word; a word with a digit is never corrected; the auto-commit needs the AUTO switch on. All typing corrections off by default + one-time migration; AUTO key in the toolbar and Settings, AUTO corrections.

## 9.11.2026 – engines and benchmark: many free engines, fallback, TTT scores them itself

> "I want TTT to be full of free engines so it can fall back between engines... install every engine with a free quota, from Groq etc. TTT itself tests my dictations from my recordings and scores each engine's accuracy, English and Croatian separately, shown in my Settings. Run the batch benchmark... engines keep improving."
> "In Settings there's a 'Benchmark voice engines' action: it takes my last 10 dictations in Croatian and 10 in English and gives an accuracy test across the APIs with free quotas that reset monthly."
> "Quick fallback from API to API, with a status under the recorder while it sends; if an API is out of quota it says 'switching to another API / provider'."
> "Explore options from free to high-end; be flexible and cheap." (Marko, 9.11.2026)

**Status:** saved first; task 63 of mac-ses9-08-nov. Slice: clip buffer (20 per language), pluggable engines with Keystore-encrypted keys, fallback chain per language with status line, Settings > Engines with benchmark, unit tests (CI), phone test with seeded clips and a fake 429 provider. Never OpenAI. Paid engines only when explicitly enabled.

## 9.11.2026 – build 571 feedback: engine name smaller, menu ghost, new entries second

> "The name on the button, AssemblyAI, is too big. Make it non-bold and 20% smaller."
> "Transcription model appears in the reordering list ('Settings order') but not in the settings list. Fix the bug."
> "From now on, whenever there is a new feature in a menu, it must be the second item from the top, so I'm not searching for something new at the bottom." (Marko, 9.11.2026)

**Status:** saved first; task 67 of mac-ses9-08-nov. Build 572: engine name regular weight, 0.8 x size, auto-fit kept; Transcription model hidden in parse() so home list and order list are one set (Voice transcription buttons in both); a new settings entry is inserted at position 2 of the saved order; Voice transcription buttons moved there once.

> "Please remove the text which describes this menu item, and make these models into columns. It needs to align the width of the column with the width of the button, and this will make a minimalistic, clear user interface." (Marko, 9.11.2026, screenshot of build 574, Voice transcription buttons)

**Status:** saved first; task 68 of mac-ses9-08-nov. Explanation paragraph and the H/E headings removed; two columns under the two key previews, each the width of its key (same Row, weight 1f, 10 dp gap); compact provider cards with radio, short name, quota line; Advanced one full-width row.

> "I choose something and it's not reflected in the buttons." / "On the buttons, push the model name to the LEFT side, next to the letter, and add the free minutes next to it, half the font size." / "The model name can be a bit smaller so it also fits the free time." (Marko, 9.11.2026)

**Status:** saved first; same build as task 68. Key layout: dot, letter, name (left), quota at half size; the key reads EngineStore directly when the live state is not yet loaded (no "assemblyai" flash); the Talk-to AssemblyAI key stays forced by design and is labelled so.

> "I don't like it. Bring back the old size of the model name on the button of transcription, and make the minutes the same size, and just shorten them. 'Minutes' is too long. Just make it one word: free or paid."

**Status:** saved first; task 69 of mac-ses9-08-nov. H/E key: name at the build 574 size plus one word free/paid at the same size, fitted together.

> "Yes, free and paid, but there should be a number of how many minutes."

## 9.11.2026 – keys only from files: one picker, detect, test, keep the working ones

> "You broke the rule. I have a rule: API keys are only entered through text files. Read the repositories Key Tester and others which have multiple keys with fallbacks. Give me one, only one, file picker, and then you recognise what type of key it is and give it to the right model. After importing you say, for example: 10 Groq keys, all 10 tested, 5 working — and then you take the 5 that work. Groq keys, Google keys, everything: detect it and put it smartly into the app, used with fallback mechanisms. I expect a simple key picker, one more row in this area where I choose the model for each [key]." (Marko, 9.11.2026)

**Status:** saved first; task 70 of mac-ses9-08-nov. One row "Import keys from file" on Voice transcription buttons; every paste field removed; detect by shape and label, test with free auth calls, keep the working ones in a Keystore ring per provider, rotate keys then providers, "N keys" on the cards.

## 9.11.2026 – long-press engine panel: both keys, vertical, grows up

> "The implementation is not good for long pressing. First of all, long press works only on the English side. On the Croatian side it doesn't work. And then it opens the options in ONE LINE at the bottom of the screen, and I need to scroll. My idea is that it opens VERTICALLY, because we have more space in the vertical direction, the screen being in portrait mode. When I long press, it opens from the bottom, from the button itself, up to the top of the screen, and I can see everything at the same time. Please fix that." (Marko, 9.11.2026)

**Status:** saved first; task 72 of mac-ses9-08-nov.

## 9.11.2026 – history, re-transcribe and benchmark with accuracy percent

> "Benchmark every provided key and engine on the last 5 Croatian and the last 5 English dictations, on the phone. Go into the history; if it doesn't exist, build it. There we can re-transcribe: the user chooses which model, Croatian version or English version — everything offered in one list — and transcribes again. Use this mechanism to compare our transcriptions, find the right one, and assess the quality of each algorithm for English and Croatian. Write what you find into our repository. In parentheses next to each model, the accuracy: 100% is the best model, less is worse. I want the percentage next to the model in Settings and on my button." (Marko, 9.11.2026)

**Status:** saved first; task 73 of mac-ses9-08-nov. Slice: History screen (date, language, sent text, Re-transcribe with one list of all engines marked HR/EN, result beside the original with differences), benchmark of the last 5 HR + 5 EN, "(96%)" after the provider name in Settings cards, on the H/E keys and in the long-press panel, docs/benchmark-2026-11-09.md.

## 10.11.2026 – numbered multi-account keys with automatic switch

> "And give work to another agent for multiple accounts, for Gemini or for Groq. You need to give them number: Groq -1, Gemini -1, and when this one expires, and then you automatically open the next key with new free space, free usage." Then: "Of course, second task is for TTT keyboard. Test it, install it in my Nothing phone." (Marko, 10.11.2026)

**Status:** saved first; task 76 of MAC_SES9_08-NOV. Slice: each key of a provider numbered (Groq-1, Gemini-1...), active number shown, automatic switch to the next number when quota/429/401, return to earlier keys when quota resets; extend KeyRing.

## 10.11.2026 – width percent per button in the feature row

> "Add the feature to TTT and that is for each button inside the feature row have another parameter how much percentage button is taking from the length of feature row. So if you take for example this configuration, you can see here I would uh give uh greater percentage to H and E and smaller percentage to F1." (Marko, 10.11.2026; screenshot /Users/markobosko/.claude/uploads/c3ae2f6d-b032-46d8-af48-dd5ba976fc2f/2b20d9b6-image.png)

**Status:** saved first; task 76, second feature. Each feature-row button gets a width percent (default H 40 / F1 20 / E 40), normalised when the sum is not 100, minimum so labels never cut.

## 10.11.2026 – engine label as large as H/E, shrinks to fit

> "regarding the TTT Uvijek, to učinite lijepo i razlika je lijepa, ali tekst Groq 100% free 479, što je sada na ovom butonu, ima više spasa da se vrti. Pa, počinimo da imamo tekst prije H ili E. let's aim to have text next to HNE, actually, uh, trying to be the size of HNE, but if there is no space, it shrinks" (Marko, 10.11.2026; screenshot /Users/markobosko/.claude/uploads/c3ae2f6d-b032-46d8-af48-dd5ba976fc2f/9adf9b1b-image.png)

**Status:** saved first; task 76 continues. The widths work and he likes them; the label next to H/E should aim at the size of the letter, shrink step by step on one line if it does not fit, never cut.

## 10.11.2026 – active key "Groq 2/3" on the label, fallback always on

> "sada sam čitajući i sada sam u koncu quota or not, I want to have next to the Gemini or Groq ... minus and then key slash how many keys I have or any other model having multiple keys, Groq 2 slash whatever number is there, and I don't see that label, please give me that label. A simple fallback mechanism for the multiple keys accounts providers should be there automatically. There is no disable enable for this. It just goes on as we know, and then it goes from the beginning again when it comes to the end." (Marko, 10.11.2026)

**Status:** saved first; task 76 continues. Label shows the active key number over the key count ("Groq 2/3") with the same size rule; fallback has no switch, wraps to key 1 at the end; the "key 1 fails" test switch becomes hidden debug or is removed.

## 10.11.2026 – H/E button label: name, key n/total, minutes left

> "and for the keyboard, you can remove three I want to say remove the free text and also percentage text from the button, but percentage text keep when I choose the models and when I see models in any other view, except on the button itself because we want bigger screen. So basically, for example, there will be e- Gemini 2/25 45, which is 45 minutes left. That's it." (Marko, 10.11.2026)

**Status:** saved first; task 76. On the H/E buttons only: "Gemini 2/25 45", no "free", no "(90%)"; accuracy percent stays in the engine panel, model picker and other views; label as big as the letter (17 sp) or larger, auto-shrink as fallback.

## 10.11.2026 – never look back: forward-only key cursor

> "Also, for TTT, if the first account is exhausted and I'm on second, you don't retry on the first. You just understand now I'm on second, then I'm going on third. So never look back." (Marko, 10.11.2026)

**Status:** saved first; task 76. KeyRing keeps a persistent cursor per provider: key n exhausted -> cursor n+1 and stays; wraps to key 1 only after the last key; kept across restarts; replaces "every call starts at the lowest key not resting".

## 10.11.2026 – width percent per button, Fit mode, next to each button

> "In TTT, I am still missing the percentage of space each button takes inside my feature row. So, all percentage does not need to be defined. It can be fit, to fit in available space, and others can be defined. Please do this update." (Marko, 10.11.2026)

**Status:** saved first; task 76. Every button of every feature row gets a width setting: "Fit" (default, equal share of what is left) or a defined percent; defined ones scale down if they exceed 100, floor share keeps labels whole; the control sits right next to each button in the Feature row editor.

## 10.11.2026 – bug: Gemini uses the first account, the label says second

> "And there is a bug because of the testings. Now, Gemini is using first account and down there it says second account." (Marko, 10.11.2026)

**Status:** saved first; task 76. One source of truth for the active key (the label reads what the request uses), test leftovers reset (test pref ignored, cursors and rest timers cleared once), a log line per request "provider key n/total used" (numbers only).

## 10.11.2026 – URGENT: never switch engine automatically; retry the same engine every 3 s

> "Now the big disaster happened. It my TTT. Gemini was not responding. And then the whole engine fall back to I don't want that. I want it tries. If it doesn't pass after some time Make a break of three seconds and try again. Tem tres. Tem tres. There is problem with internet connection. If key is exhausted, go in the next key of the same provider. Change models. Automatically never do that." (Marko, 10.11.2026)

**Status:** saved first; task 76. No cross-engine fallback in the dictation path; no response / timeout / network error: wait 3 s, retry the same engine with the same key, up to 10 times, show "Gemini · retry 2/10 · no internet", keep the audio; only quota/401/403 move to the next key of the same provider; if all keys are out, stop and say so.

## 10.11.2026 – apps tested on the Pixel 7 keep their icon on the desktop

> "please any you are testing on Pixel 7 leave its icon on the desktop" (Marko, 10.11.2026)

**Status:** saved first; task 76. Anything installed on the Pixel 7 for a test stays installed with its launcher icon; no uninstall. TTT is a keyboard, so its settings-activity icon stays.

## 10.11.2026 – no word-prediction row when no keyboard is open

> "Also please update the app. TTT, if there is no keyboard open to type things, then also the row for predicting the words is not on the screen, it's hidden." (Marko, 10.11.2026)

**Status:** saved first; task 76. The suggestion strip (ENG / to / the / that / AI) is shown only while the keyboard is open for typing; hidden when only the feature row is on screen; back the moment the keyboard shows.

## 10.11.2026 – test on the Pixel 7, install on the Nothing only when confirmed

> "instead of testing TTT on my phone, Nothing phone, use Pixel 7 phone and then when everything is confirmed, you just install it directly to my phone, Nothing phone, and let me know you did it" (Marko, 10.11.2026)

**Status:** saved first; task 76. All TTT tests and screenshots on the Pixel 7 (100.70.23.53:5555 or USB 41241FDH2S10SJ); the Nothing gets only the final confirmed release, then the master is told.

## 10.11.2026 – export his TTT configuration and ship it as the default

> "Before any upgrades and every upgrades of TTT and do it right now, you need to export my configuration of TTT from my Nothing phone and then distribute it as default configuration with the app itself. So any new update gets also UI update." (Marko, 10.11.2026)

**Status:** saved first; task 76, before any further change. Export the Nothing's layout/behaviour settings (read-only), strip every secret, ship as assets/default_config.json: a fresh install starts with his layout, an update applies the new UI defaults and keeps his keys and history. Rule: before every TTT upgrade, export the Nothing's config and refresh the shipped default first.

> "custom UI, that is it" (Marko, 10.11.2026, clarifying the export: only his custom UI: feature rows, buttons and their order, widths, labels, layout and look; not engine choices, languages, behaviour settings, keys or history)

> "Same for Android emulator. When you test something on the Android emulator, leave its icon living in the desktop, please." (Marko, 10.11.2026): TTT stays installed on the emulator with its icon after any test; never uninstall, never wipe the AVD.

## 10.11.2026 – copyright line at the bottom of settings

> "please also at the bottom of every of my app which is published on the website put copyright Mantra Productions at the bottom of settings" (Marko, 10.11.2026)

**Status:** saved first; task 76. The very bottom of the TTT settings screen: one quiet centred line "© 2026 Mantra Productions", muted, never cut; part of the app, not of the shipped default UI config.

## 10.11.2026 – RE button: Groq text-to-text again

> "Please in my TTT add option again to use Groq for text-to-text. So, RE button can be used by Groq text-to-text model, so I can prompt for changing in a text." (Marko, 10.11.2026)

**Status:** saved first; task 76. RE gets Groq chat models as a selectable engine (numbered forward-only key ring, no automatic switch, 3 s retry on network errors, next key of the same provider on quota); models from the official live list, default the best free one; low temperature; system prompt: rewrite faithfully, same language, only the text. Existing RE options stay.

> "so in settings and of course as usual long press on a button whatever it brings there is will be a switch one or the other a tropic or a Groq" (Marko, 10.11.2026; "a tropic" = Anthropic): the RE button gets an engine switch with exactly two choices, Anthropic or Groq, in Settings (RE section) and as a long-press panel on the RE button (active one marked, pick closes it); the RE label shows the active one; each has its own numbered key ring; no automatic switching between the two.

## 10.11.2026 – bug: the recording interface vanished with the suggestion row

> "separate recording interface from status line for correcting the words and not hiding that status line, that recorder in any view. Now because I asked to remove the line, suggestions are coming when I collapse my keyboard and Press my record button. I don't have any more recording interface. It is completely gone. That's the bug. These two parts should be separated. Please fix that." (Marko, 10.11.2026)

**Status:** saved first; task 96. The recorder (timer, trash, send, status) shows whenever dictation is not idle, in every view; the word-suggestion strip shows only while the letter keys are open and never in place of the recorder.

## 13.11.2026 – history keeps cancelled audio; RE Magic becomes a chat; full-screen History; RE voice/text switches

> "Also fix the history of TTT. It records in history even if I cancel the transcription and there is only audio file. In the history you can say audio only and then I can retranscribe. So if I by accident cancel my transcription, undeleted by trashcan, it exists in the history as audio file. And when I clear the history, that's the time when the audio gets deleted. Only then and you need to continue develop RE magic. RE magic is now becoming normal chat interface. I'm chatting with a friend over messenger or whatsapp or any other thing so RE has become a live chat bot inside and the history of the chat is stored in my phone so I can always continue when I left off and the chat has options. First option is closed. It means I'm closing the window and no actions. And second option is accept. So usually when we chat and chatbot is giving me solution for my message, then this message is written to the chat or email or wherever I am. And third option is clear. It means I'm clearing the history of the chat. Only what survived is skills. So it needs to write skills. and it needs to write takeovers and it can write any kind of file for learning about the my habits of writing messages and my styles also it needs to write the lessons file so basically in every startup the ai is reading takeovers lessons and skills that's what it's going to write by default and you're going to write this in the code and clear only only close the chat history and then without chat history he doesn't read the chat history it read these three files and chat history i mean the chat itself the bot should emulate facebook messenger Also improve the history view. History when I press the history button it takes the full screen and I can really luxuriously press buttons and delete individual transcription and audio files. So now we have two interfaces. One is RE Magic and another is History which is like two apps inside the app. On RE star I need few switches down in the interface and this is a voice interaction or text interaction and I can also mute sound. So if I switch voice interaction it means I will be talking every prompt and he is going to reply me every prompt but this what I am talking is transcribed. What he is talking is also transcribed. And when he is talking, please read my other applications, like for example, MA Reader Web. But already there is engine to highlight the words spoken. So when we are in the voice chat mode, then when he replies, he also highlight the sentence, highlight the word. And if local subtitles option is activated, then it use this subtitle thing, screen reader interface to read the last answer in the same effect, same style as it's already built in the app." (Marko, 13.11.2026)

**Status:** saved first. Plan: (1) cancel (trash) keeps the audio as an "audio only" history entry with Re-transcribe; audio files are deleted only by Clear history or the entry's own delete. (2) History opens full screen with big per-entry buttons (delete text, delete audio, re-transcribe). (3) RE Magic becomes a Messenger-style chat, history stored on the phone; buttons Close (nothing), Accept (insert last answer into the field), Clear (wipe the chat only; skills, TAKEOVER, LESSONS stay; the AI writes those files and reads them at every start, never the cleared chat). (4) Bottom switches: Voice / Text, Mute; voice mode transcribes both sides and highlights the spoken sentence and word; with local subtitles on, the last answer is read in the existing subtitle style.

## 13.11.2026 – RE knows who I am talking to; per-person history

> "when you are done, automatically push to main" (Marko, 13.11.2026)

> "Also, when an application is taking a screenshot, it understands with who I am talking to and then the skills and takeovers and manifests are describing actually the specific style of communication and history of the communication is kept, person especially. So then you need to add button history and even if I delete every person I am communicating with has its entry in the configuration files and I can see summaries of our conversations and the flow of our conversations and of course I can clear history if I want to." (Marko, 13.11.2026)

**Status:** saved first. Plan: when RE reads the screen it takes the chat title as the person; each person gets re/people/<name>.md (style with that person, dated summaries, flow of the conversation), read in every prompt about them and rewritten by RE on Clear/Accept; clearing the RE chat keeps the person files. A History button in the RE chat opens the list of people: summaries and flow per person, Clear one person, Clear all. Then merge to main for the build.

## 13.11.2026 – RE chat redesign in the host style; per-reply actions; GitHub sync; key paste box again

> "This chat part. Let's start with when I long press R E and I get options. It doesn't look like belong to this app. Take the screenshot of app settings, buttons, and other visual communication with the user, designs, etc. colors. And everything you make recently, it looks like it came from the different mindset. Write skills to follow the visual design of the host application and apply it to all its future modules. For example, you can look at this screenshot, second one right now, how my record button looks like and how it has room meter and timing. If I am recording audio inside this chat UI, it will be very logical that it follows the same visual style or even reuses the same modules. We need complete redesign of this RE. It needs to look of course, I don't need at the top any title, RE magic and this and that. Follow the style. I just need to borrow the idea from iPhone. It messenger chat app how we going left and right with these balloons and under them I have action buttons for each response and let's make new user interface and improve how application is learning from my cues and never repeat the same mistake twice. I mean that for RE magic when we are fixing my messages all skills, all takeovers, and all lessons living on the phone and are synchronized automatically to GitHub after every change because there is a key already entered  And with the key you again break the rule. I told you keys enter through file picking and there become paste box. I don't know why you are so stupid and forget my things between sessions. I am telling you non-stop to record skills, takeovers, manifests, but where is the hole in the system? Why it's not constantly improving?" (Marko, 13.11.2026; screenshots: the RE chat of build 635, and the keyboard recorder strip: ENG, trash, red dot 0:43 4,0 MB with the orange meter, send, settings; H/E/re keys with coloured outlines)

**Status:** saved first. Plan: (1) a design skill (.claude/skills/ttt-design) with the keyboard's own colours, outlines, key shapes and the recorder strip, binding for every new module; (2) RE chat rebuilt in that style: no title header, iMessage-style balloons left/right, action buttons under every RE answer, the recorder strip reused for voice; (3) the RE long-press options as the app's own vertical panel, not a Material menu; (4) learning: a correction becomes a lesson at once, read before every answer; (5) re/ files pushed to GitHub after every change with the key already on the phone; (6) find and remove the key paste box, key only from a file.

## 13.11.2026 – RE engines: Qwen (Groq, own key) or Anthropic Auto / Opus / Sonnet; live key test in every picker; ADB comparison

> "also each picker testing the key at the moment is important as says key works or key doesn't work
>  Please read my repositories when we develop a QEN or Groq Q V E N engine to do the task based on skills and manifest and takeovers. So in the same way I want to be able to switch from Anthropic and use API from Groq to replace functions in this chat and to understand how to do the work based on the same MDs atropic writes. So add this switch to this application. Also picker for Groq key for this purpose of working with this RE. Because for them I want to provide separate API key which has nothing to do with the rest of the Groq functions inside the application.
>  Please remove from the options of RE other models of Groq. We are doing now special route with this Queen model. Look at the manifest what I am using in my flask app on my Macintosh. And that should be it. It's either Queen, when I say Queen I mean Q, W, E, N, the models with more database, The larger one or Atropic. There is no more options.
>  Please pay attention when you are writing skills, manifest, lessons, takeovers. You help Queen do the work same as Anthropic is doing in the same quality. So keep in mind, first read all the lessons already in the repository for Queen and also please over ADB We run a comparison of Queen and Anthropic corrections and any possible scenario when I am dictating and asking for corrections. Just invent some person and some chat and see how to formulate the skills, takeovers, Manifests and lessons to have the same result with Queen as Anthropic is doing. And when I long place on ARI Magic I want to have Anthropic options for Opus and Sonnet. So I can directly switch here the model.
> please using local ADB test this keyboard the way I ask you to do it  Please do at least 30 minutes of typing, correcting, comparing opus, sonnet, queen and also make third options. Tropic Auto, which is going to choose appropriate model. It's like I already built this in my mod. It's a model router. To choose sonnet or opus depends on the complexity of the task." (Marko, 13.11.2026; "Queen"/"QEN" = Qwen, "Atropic"/"Tropic" = Anthropic, "ARI Magic" = RE Magic)

**Status:** saved first. Plan: RE engine = Anthropic Auto (router: Sonnet or Opus by complexity) / Opus / Sonnet / Qwen (Groq, the larger Qwen model from the Flask app's manifest); no other Groq models. RE's Groq key is its own ring, from its own file picker, tested at the moment of picking ("works" / "does not work"). Qwen reads the same MDs (TAKEOVER, LESSONS, SKILLS, people) with a prompt tuned so it matches Anthropic; lessons for Qwen from his repos first. A debug broadcast runs invented chats through all four and writes a comparison; 30+ minutes over adb.

## 13.11.2026 – RE searches all my GitHub repositories

> "one more feature, AI Magic must be aware of my Github token and in that way it can search through my Github repositories, all of them private or not, and search for information if I ask it to do that" (Marko, 13.11.2026; "AI Magic" = RE Magic)

**Status:** saved first. Plan: a GitHub token from a key file (never a paste box) that can read his repos is kept encrypted for RE (ring github_read; the RE database token is used if it is the only one and can read). In the RE chat, when he asks about his repos, projects or code, RE searches (GitHub code and repository search across all his repos, private too) and reads files (README, TAKEOVER, any path), up to 4 looks per answer, then answers; read only, never writes to any repository. Works with Anthropic and Qwen alike.

## 13.11.2026 – the RE Qwen key file; SY keys only switch layouts

> "The key leaves here and also fix one more bug. By the way, next to the left control there is two SY-SY buttons. SY1 is showing one symbol layout, SY2 is showing second symbol layout and that's all what I can do. Nothing else. And where they are in their symbol layout, they instead of SY, the text change to text. So I can quickly switch to my text layout. And no other functions this do. No dot, no comma, nothing." (Marko, 13.11.2026; screenshot: groq-payed-key.txt in Download/Api)

**Status:** key imported over adb through "Import RE key from file": "Key 1: works, kept for RE". SY: letters ctrl | sy1 | sy2; symbols 1 ctrl | text | sy2; symbols 2 ctrl | sy1 | text; no popups on them; the comma key in the symbol rows is gone and the period's hold types the comma there (as on the letters).

## 13.11.2026 – talk to me through my voice engine while debugging

> "Please talk to me via my local voice engine for updates, what you are doing, update me where we are. Because now we are in debugging mode, I will give you control of my phone and I cannot read this chat. You need to talk to me." (Marko, 13.11.2026)

**Status:** done. tools/voice/say_on_phone.sh: speech made locally (espeak-ng, nothing sent to an online voice), played by Voice Engine v3 on the Nothing (POST /play over an adb forward of port 8720) with its subtitle. Every step of the debugging is spoken.

> "beatrices ... if there is no batteries living on my phone, please build one ... beatrice ... or better yet, use speechify ... Please use speechify key to talk, speechify API keys" (Marko, 13.11.2026; "batteries" = Beatrice)

**Status:** TTT gets an adb broadcast dev.ttt.engines.SAY: the Reader voice (beatrice_32 on Speechify, his key inside TTT) speaks the text, Voice Engine shows the subtitle. tools/voice/say_on_phone.sh uses it from build 641 on, espeak only before that.

## 13.11.2026 – talk through TTT; a Python API to drive TTT from a distance

> "Yes, I hear the robot, but Beatrice is also building my TTT Mini, so you can talk through my TTT. It's not TTT Mini anymore, sorry. To TTT Advanced Keyboard, Talking Keyboard, you can talk to me, until this builds. Please build for this my application TTT make Python API so you can do anything from the distance. You can even talk through my keyboard if you want to." (Marko, 13.11.2026)

**Status:** tools/ttt_remote.py (connect, say = Beatrice through TTT, vsay = Voice Engine v4, status, type, re, engine, compare, shot, ui, tap, key, text, install, log); TTT receiver gets dev.ttt.remote.TYPE / STATUS / RE_ENGINE / RE_ASK (adb only, answers in files/remote/<id>.json, never a key value). Ships in build 642.

> "Here is the speechify key. There are multiple ones ... If the key is exhausted, we rotate it ... do it for my voice engine and for my TTT. We are using this fallback and free accounts." (Marko, 13.11.2026; Download/Api/speechify_api.txt)

**Status:** TTT's Reader (MaSpeechify.synthesize) already walked the keys from the last good one on 401/403/429; now also on 402 (free use spent). Voice Engine v5: the same ring.

## 13.11.2026 – lock the phone when using it, give it back with the chat in front

> "after you finish testing, please bring back, make everything tidy, bring back your  Also, when you don't need phone just bring back your chat so I can scroll to it and see what's going on. When you need phone make a lock. Look at my GitHub repositories how I make a lock in my Mac OS. When my Mantra macro recorder is using the keyboard I cannot use my computer at all. It blocks everything. Now you can do the same block here. You just put one badge over the middle of the screen, say Claude is using the phone, and then if I press some button on that badge, I can override it. But basically it says to me, stay away, I am now in action. And when it disappears, I can use it. So we can be in exchange. I don't need to give you a phone for the next five hours if you are not needing it. From time to time when you need it, you block me and I let you use it. That's the thing. please put this in mantra manifest so we always work like this" (Marko, 13.11.2026)

**Status:** Voice Engine v7 (/lock, /unlock, /through); tools/ttt_remote.py lock/unlock/done, taps and shots pass the lock and stop after an override; MANTRA_MANIFEST modules/phone-lock.md.

## 13.11.2026 – settings open where I left them

> "Please add a feature to TTT Mini to remember exact position when I left off when I was in my settings. So if I am in some deep settings and I go to my typing and I click setting again, it doesn't bring me to main menu. It brings me exactly to that settings." (Marko, 13.11.2026)

**Status:** done, build 648, tested on the Nothing: deep screen (Voice buttons > Advanced), Home, TTT removed from recents, reopened with the keyboard's flags → it opened on "Advanced: fallback and benchmark" (logcat TTT-SETTINGS).

## 13.11.2026 – suggestions for special data: email, phone, streets, cities

> "Please update this application if not already that it understands special kinds of data. Phone numbers, email addresses, also addresses and what else can be specific. Find a way, whatever else. So it just offers, when I start to type Marko, it offers email address. Or if I start to type street, then it just search in the database of all the streets and find which streets I want to type. If I type the name of the city, it understands. You find the logic behind it. The examples are email addresses and phone numbers." (Marko, 13.11.2026)

**Status:** built (build 649), MaPlaces.kt + MaAddressBook.kt. Already there before: typed emails, numbers, web addresses and IBANs saved and offered whole. New: (1) an offline place book (assets/dictate/places.tsv, tools/places/build_places.py): 26 762 Croatian streets with their towns (OpenStreetMap), 6 931 Croatian towns and villages with post codes, 34 215 world cities (GeoNames); nothing typed leaves the phone. Offered for a capitalised word inside a sentence ("u Zag" → Zagreb, "Savs" → Savska cesta, "Ulica gr" → Ulica grada Vukovara), any word after "ulica/adresa/živim u/address", every word in an address field; "Ilica 5, " → "10000 Zagreb"; "100" after a comma → "10000 Zagreb"; diacritics ignored. (2) A contact's name ("Mark", "Marko") offers that contact's email, then number, replacing the name. Needs Contacts allowed for TTT in Android settings (now off on the Nothing).

## 13.11.2026 – sync settings between devices (relayed by the Remote Viewing session)

> "make github repository ... TTT settings. It's private. Inside what lives is these settings. And when I run TTT it checks if something is new and then all devices are always updating. We are synced between devices ... when I change any setting in my TTT, it pushes it to my Git repository because it has my key ... In the settings you can put this feature for people. Write your repository when you want me to synchronize settings between devices. And then they can put their own API if they have it. If they don't have it, sorry. And this is disabled by default. For me it's enabled." (Marko, 13.11.2026, passed on by the Remote Viewing session; the bug: the Pixel 7 did not get the Nothing's layout)

**Status:** built (MaSettingsSync.kt, Settings > Sync settings between devices). Off by default; repository owner/name; token only through Import keys from file (or RE's token when it can write there). Every changed pref except secrets and per-phone state goes to ttt_settings.json; pull at start and keyboard open (once a minute), push after keyboard close / leaving Settings; first sync on a phone takes the repository's copy; empty repository gets this phone's. Waiting: the private repository markoboskoauroville/TTT_SETTINGS, a token file that can write to it, then switch on on the Nothing first (seeds), then on the Pixel.

## 13.11.2026 – the RE chat becomes the chat with a Claude Code session on the Mac (relayed by MAC_SES3_13-NOV)

> "again, let's copy what we built in TTT. It's an iMessage chat window, and we chat through that. This is no longer voice. Our chat window is like a voice, but I don't need to reply, and I can reply. You send a message, I send a message. It's an iMessage copy, with the tweaks of TTT, and it's already built." / "TTT also exists. Maybe it can drive this conversation it exists in every form" / "It existed in every telephone." / earlier: "I will send when I press Send. If there is an error (like in TTT), then it's sent, not automatically." (Marko, 13.11.2026, passed on by MAC_SES3_13-NOV)

**Status:** saved first. Plan from the Mac session: a chat partner "Claude Code · <session>" beside Auto/Opus/Sonnet/Qwen; out through the Mac relay (phonereply.py) with a token from a key file; in through an adb broadcast; WAV kept on transcription failure with Resend; one thread per session, header with its name.
