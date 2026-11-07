# TTT: momentary updates

Every request word for word, saved and pushed before any code (MANTRA_MANIFEST requests-saved-first).

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
