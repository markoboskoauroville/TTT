#!/usr/bin/env python3
"""
Builds site/public/index.html, the page of TTT, Talk to Type Advanced Keyboard (Marko, 22.10.2026: "start building
product page for TTT Mini and rename it to only TTT ... Call it Talk to Type Advanced Keyboard. Then make many
screenshots through ADB and explain and ask for donations ... same principle").

    python3 site/build.py

Screenshots in public/img were taken over adb on the Pixel 7 (build 410), with no private text or keys on them;
set-switch.jpg and set-predict.jpg on the Nothing Phone (build 423, 27.10.2026).
The donation bowl is MANTRA_MANIFEST's kit, pasted in at build time. Deploy: Cloudflare Pages project "talktotype".
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent
BOWL = (Path.home() / "Developer/MANTRA_MANIFEST/kits/donation-bowl/donation-bowl.html").read_text()
DOWNLOAD = "https://github.com/markoboskoauroville/TTT_MINI/releases/latest"

# (title, text, picture, wide picture?)
FEATURES = [
    ("Speak, and it types", "Two keys on the bottom row record: ● H for Croatian, ● E for English. The key you press is the language, so there is nothing to set first and nothing to check after. Your words land at the cursor in any app.", "keyboard.jpg", False),
    ("Croatian letters, your way", "Hold c and you get č and ć, hold d for đ, s for š, z for ž. Nothing else pops up to get in your way. Any letter can carry more pop-ups: you add them yourself.", "popup-c.jpg", True),
    ("Pop-up Configuration", "Every letter on one grid with its pop-ups printed under it. Press a letter and its ID card opens: the key big at the top, Pop-up 1, Pop-up 2, and a + for as many as you like.", "set-idcard.jpg", False),
    ("Coloring the Buttons", "Paint the letters themselves, like a Logitech G815: choose a colour, tap the keys. You find the keys you use most by colour before you read them.", "set-colors.jpg", False),
    ("Rows you arrange", "Up to six rows of feature keys above the letters, each with its own name. Drag the keys into the order your thumb wants; F1 to F6 show and hide a row from the keyboard.", "set-feature.jpg", False),
    ("The copy row", "Select all, paste, copy, cut, clipboard history and two paste helpers, one tap each, right above the keys.", "row-copy.jpg", True),
    ("A cursor pad", "One row turns into a touchpad: slide along it to move the cursor exactly where you want it, letter by letter.", "row-cursor.jpg", True),
    ("Number row that types whole phrases", "Each digit can carry a snippet: a symbol, a word, a whole sentence, typed with one long press.", "set-numrow.jpg", False),
    ("It reads to you", "The speaker key reads what is on the screen aloud, with the current word lit in a subtitle row, on the spacebar, or full screen. Speed and text size are yours.", "set-reader.jpg", False),
    ("Proofread and spell check", "Ctrl+P proofreads, Ctrl+F reformats, and every Croatian dictation is checked for spelling before it is typed. You keep every wording of every prompt.", "set-prompts.jpg", False),
    ("Everything in one switchboard", "The keys, the number row, the feature rows, the copy row, the reader: one screen, one switch each. The prediction row is always there, so no key ever jumps.", "set-switch.jpg", False),
    ("Auto prediction", "Word prediction in the language you are typing, Croatian or English, whatever the dictation key says. When the guesses are wrong, the AI key asks Claude for five words, on the model you choose: Haiku, Sonnet or Opus. Your corrections and the words it learned are on the same screen.", "set-predict.jpg", False),
    ("Symbols, two pages", "All the punctuation and symbols on two pages, one tap away from the letters.", "symbols.jpg", True),
]

MORE = [
    ("Word prediction that learns you", "It learns the words you write, in Croatian and English separately. When you correct a word, it learns that it was a typo and offers what you meant next time; recent words rank above old ones. A word you fix in a dictation twice is fixed for you from then on."),
    ("Volume keys", "Hold volume up to record, hold volume down to press send. A quick tap still changes the volume."),
    ("Magic finger", "One key presses a button on the screen for you, like Send, found by its name."),
    ("History", "Every transcription is kept with its audio, and a recording cut short is saved, never lost."),
    ("Profiles", "Whole configurations saved by name and swapped in one tap."),
    ("Your keys, your data", "You bring your own AssemblyAI and Anthropic keys, imported from a file, never typed. Nothing is learned in incognito fields."),
]


def feature_html(title, text, pic, wide):
    cls = "feat wide" if wide else "feat"
    return (f'<div class="{cls}"><figure><img src="img/{pic}" alt="{title}" loading="lazy"></figure>'
            f'<div><h3>{title}</h3><p>{text}</p></div></div>')


features = "\n".join(feature_html(*f) for f in FEATURES)
more = "\n".join(f'<div class="card"><h3>{h}</h3><p>{p}</p></div>' for h, p in MORE)

PAGE = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="icon" type="image/svg+xml" href="/favicon.svg">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<title>TTT · Talk to Type</title>
<meta name="description" content="TTT, Talk to Type Advanced Keyboard: an Android keyboard you speak to, in Croatian and English, with Croatian letters, colours, rows you arrange and a reader.">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=Inter:wght@400;500;600;700&family=Spectral:wght@400&display=swap" rel="stylesheet">
<style>
:root{{--bg:#0D1117; --bg2:#141a23; --line2:rgba(255,255,255,.08); --ink:#E6EDF3; --ink2:#9AA6B2; --ink3:#6E7781;
  --a:#3FB950; --b:#F2CC60; --c:#58C4DC; --r:#FF7B72}}
*{{box-sizing:border-box}}
html{{scroll-behavior:smooth}}
body{{margin:0; background:var(--bg); color:var(--ink); font:16px/1.6 Inter,system-ui,sans-serif; overflow-x:hidden}}
a{{color:var(--c)}}
.wrap{{max-width:1080px; margin:0 auto; padding:0 16px}}
section{{padding:64px 0}}
.eyebrow{{font:500 12px "IBM Plex Mono",monospace; letter-spacing:.18em; text-transform:uppercase; color:var(--a); margin-bottom:10px}}
h1{{font:400 clamp(40px,7vw,70px)/1.05 Spectral,Georgia,serif; margin:0 0 16px}}
h1 em,h2 em{{font-style:normal; color:var(--b)}}
h2{{font:400 clamp(30px,4.5vw,44px)/1.15 Spectral,Georgia,serif; margin:0 0 16px}}
.lede{{font-size:19px; color:var(--ink2); max-width:40em}}
.btn{{display:inline-block; font:600 16px Inter,sans-serif; text-decoration:none; color:#0D1117; background:var(--a); border-radius:999px; padding:14px 26px; box-shadow:0 0 28px rgba(63,185,80,.35)}}
.btn.ghost{{background:transparent; color:var(--ink); border:1px solid var(--line2); box-shadow:none}}
.links{{display:flex; gap:12px; flex-wrap:wrap; margin-top:24px}}
/* the page order (MANTRA_MANIFEST app-page-order.md): banner, introduction, lifestyle, technical, donation */
.top{{padding:24px 0 8px}}
.banner{{position:relative; border-radius:18px; overflow:hidden; margin:0 0 28px; background:#000}}
.banner > img{{display:block; width:100%; height:auto; aspect-ratio:1376 / 768; object-fit:cover}}
.banner .over{{position:absolute; left:0; right:0; bottom:0; padding:60px 26px 22px;
  background:linear-gradient(transparent, rgba(0,0,0,.82)); display:flex; gap:18px; align-items:center}}
.banner .over .keys{{margin:0}}
.banner h1{{margin:0 0 4px; font-size:clamp(34px,5.5vw,56px)}}
.banner .by{{color:var(--ink2); font-size:16px}}
.life{{border-radius:18px; overflow:hidden; margin:8px 0 0}}
.life img{{display:block; width:100%; height:auto}}
.keys{{display:flex; gap:8px; margin:0 0 18px}}
.keys span{{font:600 22px "IBM Plex Mono",monospace; width:44px; height:44px; display:grid; place-items:center; border-radius:10px; background:var(--bg2); border:1px solid var(--line2)}}
.feats{{display:grid; gap:22px; margin-top:30px}}
.feat{{display:grid; grid-template-columns:minmax(0,300px) minmax(0,1fr); gap:28px; align-items:center; background:var(--bg2); border:1px solid var(--line2); border-radius:18px; padding:20px}}
.feat.wide{{grid-template-columns:minmax(0,1.2fr) minmax(0,1fr)}}
.feat figure{{margin:0}}
.feat img{{width:100%; display:block; border-radius:12px; border:1px solid var(--line2)}}
.feat:not(.wide) img{{max-height:560px; object-fit:cover; object-position:top}}
.feat h3{{margin:0 0 8px; font-size:22px}}
.feat p{{margin:0; color:var(--ink2); font-size:16.5px}}
.cards{{display:grid; grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); gap:14px; margin-top:26px}}
.card{{background:var(--bg2); border:1px solid var(--line2); border-radius:16px; padding:20px}}
.card h3{{margin:0 0 6px; font-size:17px}}
.card p{{margin:0; color:var(--ink2); font-size:15px}}
.get{{text-align:center}}
.note{{font-size:13.5px; color:var(--ink3)}}
footer{{padding:40px 0 60px; color:var(--ink3); font-size:13.5px; border-top:1px solid var(--line2)}}
@media (max-width:760px){{
  section{{padding:46px 0}}
  .banner .over{{position:static; background:var(--bg2); padding:16px; flex-wrap:wrap}}
  .feat,.feat.wide{{grid-template-columns:1fr}}
}}
</style>
</head>
<body>

<main class="top">
  <div class="wrap">
  <!-- 1. the banner: the app at work -->
  <div class="banner">
    <img src="img/hero.jpg" alt="A man on a café veranda in Pondicherry speaks into his phone and TTT types his words" width="1376" height="768">
    <div class="over">
      <div class="keys" aria-hidden="true"><span style="color:var(--r)">T</span><span style="color:var(--b)">T</span><span style="color:var(--c)">T</span></div>
      <div>
        <h1>Talk, and it <em>types</em></h1>
        <div class="by">TTT · Talk to Type Advanced Keyboard · Android · free</div>
      </div>
    </div>
  </div>

  <!-- 2. the introduction -->
  <p class="lede">A keyboard you speak to. Press ● H and speak Croatian, press ● E and speak English: your words are written where the cursor is, in any app. Around the voice, a full keyboard made to be arranged your way. It was made by a filmmaker who dictates all day, in two languages, on his phone.</p>
  <div class="links"><a class="btn" href="{DOWNLOAD}" target="_blank" rel="noopener" data-dl-app="ttt">Download the latest version</a><a class="btn ghost" href="#features">See what it does</a></div>
  <p class="note" style="margin-top:14px">The latest build · Android 8 and newer · free and open source (Apache 2.0)</p>

  <!-- 3. the lifestyle picture -->
  <div class="life" style="margin-top:28px"><img src="img/lifestyle.jpg" alt="A smiling woman in a printed kurta sits on a bench under a big tree on a sunny Pondicherry street, colourful colonial houses and bougainvillea behind her, speaking into her phone" width="1024" height="765" loading="lazy"></div>
  </div>
</main>

<!-- 4. the technical part -->

<section id="features">
  <div class="wrap">
    <div class="eyebrow">What it does</div>
    <h2>Made for a person who <em>writes by voice</em></h2>
    <p class="lede">Every screen below is the real app.</p>
    <div class="feats">
{features}
    </div>
  </div>
</section>

<section id="more">
  <div class="wrap">
    <div class="eyebrow">And more</div>
    <h2>Small things that <em>save the day</em></h2>
    <div class="cards">
{more}
    </div>
  </div>
</section>

<section id="get" class="get">
  <div class="wrap">
    <div class="eyebrow">Get TTT</div>
    <h2>Start <em>talking</em></h2>
    <p class="lede" style="margin:0 auto 26px">For Android 8 and newer. Install it, turn it on as your keyboard, import your speech key from a file, and press ● H or ● E.</p>
    <a class="btn" href="{DOWNLOAD}" target="_blank" rel="noopener" data-dl-app="ttt">Download the latest version</a>
    <p class="note" style="margin-top:14px">Free and open source (Apache 2.0), built on Dictate and FlorisBoard. Speech to text uses your own AssemblyAI key; proofreading your own Anthropic key. If it helps you, put something in the bowl below.</p>
  </div>
</section>

<!-- 5. the donation bowl -->
{BOWL}

<footer>
  <div class="wrap">
    <p>TTT, Talk to Type Advanced Keyboard, by Marko Boško, Mantra Productions · <a href="https://markobosko.pages.dev/#/kod">more of my tools</a> · <a href="https://github.com/markoboskoauroville/TTT_MINI">source code</a></p>
    <p>Built on Dictate by DevEmperor and FlorisBoard, Apache License 2.0. Logitech and G815 are trademarks of their owners.</p>
  </div>
</footer>
<script src="https://speechifykeys.pages.dev/speechify-keys.js" defer></script>
<script src="https://mantra-downloads.marko-bosko-925.workers.dev/counter.js" defer></script>
</body>
</html>
"""

(HERE / "public" / "index.html").write_text(PAGE)
print("wrote", HERE / "public" / "index.html", len(PAGE), "bytes")
