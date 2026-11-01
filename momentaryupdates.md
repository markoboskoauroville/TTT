# TTT Mini: momentary updates

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
