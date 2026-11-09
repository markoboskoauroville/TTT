# TTT — the finished state

**Build 413 (22.10.2026).** Renamed **TTT, Talk to Type Advanced Keyboard** (launcher name "TTT"; repo and package
unchanged then; on 2.11.2026 the repo became TTT and the APK ttt-build-N.apk). A dictation keyboard for Marko Boško.

**New on 22.10.2026:** a letter's long press offers only his pop-ups (MaPopups; default č ć / đ / š / ž; Settings →
Pop-up Configuration with a key's ID card), Key colours renamed Coloring the Buttons; the personal model learns from
corrections (MaCorrections: a typo cut back and retyped is unlearned and its fix offered first; recent words outrank
old ones, after AOSP LatinIME); deleted letters no longer learned. Tests on the Mac: scripts/test_popups.py,
scripts/test_corrections.sh (Gradle's own Kotlin compiler, no kotlinc). Product page: https://talktotype.pages.dev
(site/build.py, Cloudflare Pages "talktotype"), linked from the biography. Not yet tried by his thumbs: the correction
learning.

**Repo:** `markoboskoauroville/TTT` (Apache-2.0) · **Package:** `com.mantraproductions.tttlight`
> **The artefact link above rots.** CI keeps the two newest releases, so a build named here is
> unreachable within a few pushes. The tag is the durable part; take the newest release rather than
> this number if it 404s.

**Latest artefact:** https://github.com/markoboskoauroville/TTT/releases/download/build-390/ttt-mini-build-390.apk

> **This file is the present state and nothing else.** Every reason, every bug and every rejected
> alternative is in [`DEVELOPMENT.md`](DEVELOPMENT.md), grouped by shape. If you are about to reverse
> a decision, the reason it was made is there — look before you do.
>
> Structured per `MANTRA_MANIFEST/modules/repository-documents.md`.

**AUTO (9.11.2026):** no automatic text replacement unless switched on in AUTO (toolbar key AUTO, or Settings, AUTO corrections).
All typing and dictation corrections are off by default (v2). A digit never ends a word (AT2020 bug). See DEVELOPMENT.md, "AUTO".

## What to read, and in what order

1. **This file.** Everything true about the app now.
2. **`MANTRA_MANIFEST`** — the principles across all his apps. Start at its `START_HERE.md`.
3. **`VOLUME_KEYS.md`** before touching the volume keys. **A contract, not a description:** if the
   code differs from it, the code is wrong.
4. **`SECRETS.md`** before touching a key file. Note §2a — a redaction built from known key shapes
   fails *open* on a format it has not seen, and that leaked a key.
5. **[`DEVELOPMENT.md`](DEVELOPMENT.md)** when you need the reason for one particular thing.
6. **[`DELIVERY_RECORD.md`](DELIVERY_RECORD.md)** for what has and has not been measured.

**Run `python3 scripts/verify.py` before every push.** CI runs it too, and it blocks.

## What this app is

A dictation keyboard for Marko Boško: Android IME, forked from FlorisBoard, transcribing through
AssemblyAI. He is a filmmaker who works almost entirely by voice on a Nothing Phone 2a, has low
vision and dyslexia, and uses this all day. He dictates in Croatian and English.

**Repo:** `markoboskoauroville/TTT` (Apache-2.0)
**Package:** `com.mantraproductions.tttlight`
**CI:** GitHub Actions builds every push, publishes an APK, keeps the two newest releases.

## How he works

He sends screenshots and dictates. The dictation arrives lightly garbled — read through it rather
than around it, and ask when a word could be two things. He is precise about what he wants and
usually right about why; when he pushes back on a decision, he has almost always spotted something
real.

**He is paying for every build.** A red build costs him the same as a green one.

**A settings export is a secret file.** A `.jetpref` he sends to set defaults from carries his API
ring among ordinary preferences and does not announce it. Read it in place, take the values, never
copy the file, never print it, say by name what was skipped, and shred it after. `SECRETS.md` §2c.

**Keys and tokens follow `SECRETS.md`, without exception.** Extract by shape into
`/home/claude/.secret`, never print, never commit, use by reference, and scan the staged diff for
key shapes before every push. The vault does not survive a session — rebuild it from
`/mnt/user-data/uploads` at the start of each one, and if that folder is empty say so instead of
hunting. Shred only on his word, and say plainly that shredding is not revoking.

**`getValue` and `setValue` are invisible and must never be swept.** Property delegation — `val x by
pref.collectAsState()`, `var y by remember { mutableStateOf(...) }` — needs those imports and never
names them in the code. Any tool that removes imports by searching for the symbol will delete both
and the file stops compiling with an error that points at the delegation rather than at the import.

**A button in settings always carries its icon.** Wherever a settings screen lists keys, buttons or
rows, each entry shows the glyph it actually has on the keyboard, beside the name. He recognises an
image faster than a word, and a list of names alone makes him translate twice — once from the name
to the picture, once from the picture to the key. This is not decoration; it is how he finds things.

**A key says its state with colour, and its identity with a drawn shape.** Reversed on 19.8.2026,
after a build that emphasised the zone keys with a lit ring around the whole key and stripped the
glyph down to a bare letter to make room for it. He called it a downgrade, amateurish, and out of
standard with the rest of the app — and it was, because no other key in the row wears a border, so
the three that did read as bolted on. Green means on. The face carries a shape, and the shape is
what tells the keys apart. Do not put a border on one key that the keys beside it do not have.

**He runs several projects at once, and sometimes a message lands in the wrong chat.** He asked for
this check by name. If a message does not belong to TTT — another repo, another app, a website,
a film, a design note about something this app does not contain — say so and stop:

> Please notice you are in the wrong chat.

Nothing more. Do not answer the question anyway, do not guess which project it was, and do not
lecture him about it. One line, and wait.

**Check before saying it.** The tell is content with no counterpart in this app — the giveaway is
usually a noun that does not exist here at all. Look first: a grep or a `find` costs one tool call
and is the difference between a useful warning and an irritating one. A screenshot of an unfamiliar
screen is not enough on its own, since this app has many screens; a portrait photo in a keyboard
with no images anywhere is.

**If it is ambiguous, ask rather than assert.** He switches context faster than the chat does, and
being told he is in the wrong place when he is not is worse than a question.

## Things that are true about the app

- **Croatian must never use the Sync path.** Sync covers 18 languages and Croatian is not one; it
  defaults to English and returns fluent Croatian that is the wrong words. `SYNC_SAFE_LANGUAGES` is
  an allow-list and must stay one.
- **The feature row is the main feature.** Three rows, drag-arranged, row 1 nearest the keys.
- **Colour means state.** `#9B3B33` recording, sand means "this takes you somewhere", dim means
  present but inactive.
- **The accessibility service powers the magic button, TAB and the floating button.** It must be
  declared in the manifest — it once was not, and everything depending on it failed silently.
- **Send is one thing with two triggers, and they must never drift apart.** The send key on the
  magic finger row and a long press on volume down are the same press. Volume down resolves its
  term through `MaMagicTargets.resolveTerm` against the taught list — it must never read a term
  straight out of a preference and press that. Rename it once on the Magic finger screen and both
  follow. **Any future way of firing send joins them the same way**: one list, one term, no copies.
- **A button in a settings list always carries its icon.** Wherever a screen lists keys, buttons or
  rows, each line shows the same glyph the keyboard draws, beside the name. He asked for this by
  name: *he recognises an image faster than text*, and a list of names alone makes him translate
  twice — once from the word to the picture, again from the picture to the key. It also catches a
  whole class of mistake, because a row whose glyph is wrong is visible the moment the screen opens.
- **Roles are wired, not chosen.** `MaRoles` decides: AssemblyAI transcribes, Anthropic rewords and
  proofreads, Speechify speaks. The call path resolves through it at the moment of use, so a stale
  stored id cannot survive. Give a new provider a role there; never give the user a chip. Capability
  is not the same as job.
  **Groq is gone**, and with it automatic language detection: the language is chosen by hand, on the
  badge. A detector that is confidently wrong returns fluent text in the other language, which is
  worse than an error.
- **Anthropic is no longer slated for removal.** Ctrl+P and Ctrl+F depend on it. Gemini is
  unregistered (§31); a stored Gemini key is hidden, not erased.
- **A preset absent from `ProviderRegistry.presets` does not exist.** `byId` returns null and key
  import skips it. That is why a Groq key could not be stored for months.
- **Both hardware keys decide on release.** Short press is volume, a 500 ms hold is the other
  meaning: volume up records and stops, volume down presses a magic finger term. Neither may take
  the volume away on a quick tap — he adjusts bhajan on the same phone.
- **Croatian has a shipped dictionary** (`assets/dictate/hr_words.txt`, CC BY-SA, see §33). It is
  searched only with a prefix, only when Croatian is active, and always after the personal model.
- **`MaNeuralPredictor` and `MaLanguageGuess` are both dead code** — written, never called. Check for
  callers before assuming a file is live.

## Working agreement

Ship small, verified builds rather than large ones. Tell him plainly what was not done and why. When
a request is bigger than the room left, write it to `NEXT_DEFAULTS.md` rather than starting it badly.

He pays for every build, red or green. Verify before pushing and read the result before committing —
not in the same command. Where the logic can be checked without a phone, check it: the case ring, the
voice-command parser and the dictionary search were each run against brute force or a table of cases
before being wired in, and none of them needed a build to find their bugs.

He dictates, so messages arrive garbled. Read through it, and ask when a word could mean two things —
Groq and Grok cost a whole exchange before that was written down.

## Settings rules

- **A button in a settings list carries its icon.** Whenever a screen lists keys or buttons, every
  row shows the same glyph the keyboard shows, beside the name. He recognises the image before he
  reads the word, and a list of bare names makes him translate twice. `MaRowKeyItem` is the shape to
  copy, and reusing it is better than matching it.
- **An important key says it is on by COLOUR, not by a border around the key.** Tried the other way
  round on 19.8.2026 and reversed the same day: a ring on three keys in a row where nothing else has
  one is the thing the eye lands on, and it reads as a patch rather than as emphasis. Colour is the
  app's state channel everywhere else; keep it the only one.
- **A key on the row carries a drawn shape from the icon set, not a bare letter and not a hand-drawn
  one.** The zone keys were letters, then a hand-drawn keyboard outline with a letter inside, and he
  called both amateurish — correctly: every neighbour is a glyph from one set, and a shape drawn by
  hand beside them reads as a patch however carefully proportioned. They are `Numbers`, `Keyboard`
  and `ContentPaste` now. **If the set has nothing for a key, that is a reason to reconsider the key,
  not to draw one.**


## The keyboard, as it stands

**Six feature rows**, arranged in settings, row 1 nearest the keys. Each can be **named**. Tabs are
**dragged** to reorder — long press, then slide; the held tab lifts and the tab it would swap with
dims. `F1`–`F6` show and hide a row from the keyboard itself, green ring when showing.

**Two special rows**, each with its own preference, arrangement and switch:

- **the copy row** — `select all · paste · copy · cut · clipboard history · AP · AC · pin`
- **the bucket row** — `bin · C1 · C2 · C3 · A↑ · swap`, off by default, switched by the `Cs` key or
  the switchboard
- **the reader row** — `▶/⏸ · ⏮ · ⏭ · ■ · watch · − · +`, off by default, switched by the `Rr` key or
  the switchboard. The plain reader key still just reads.

**Recording and sending are chosen by which key you press, not by a mode.** `● H` records Croatian,
`● E` records English. The key IS the choice, so there is
nothing to set beforehand and nothing to check afterwards. The status line says **Sending Croatian** or **Sending English**. There is no language badge and
nothing on that line changes the language: the keys decide it.

**The bottom row**: `ctrl · view · , · space · . · enter`. One view switcher that CYCLES with no
skipping — letters, symbols, symbols², letters — and a long press offers all three for jumping
straight there. The comma has its own key; the period offers ! and ?

**Claude follows the newest model by tier.** `claude-haiku-latest` is resolved against Anthropic's
model list; Haiku is the default, Sonnet or Opus chosen on the Prompts screen. Claude is called
through the native Messages API at effort `low`. Prompts → *Show exactly what is sent* is built by
the same function the keyboard sends (`MaRewordPrompt`). See §188.

**Keys can wear their own colours** (Settings → Key colours, G815 style: letters, number row and feature rows), and each number row digit
prints the command it types in small letters under the digit. See §189.

**Every Croatian dictation is spell-checked by Claude before it is typed** (MaSpellcheck, on by
default). Ctrl+P, Ctrl+F and the spell check each choose their own model. Settings → Claude costs
logs what each request cost; the balance is typed in from the Console because the API cannot be
asked for it. See §190.

**The Mac's style model runs on the phone** (§191). Settings → Prompts → *Models on the phone*
downloads Qwen3 4B Instruct 2507 (2.4 GB, the Mac's fast model) or Qwen3 8B (5 GB, the Mac's current
one, too big for an 8 GB phone). llama.cpp runs it in its own process (`:llm`), so running out of
memory never takes the keyboard. **Style after every dictation** sends each transcript through it
with the Mac's rules word for word (`MaPhoneStyle`), on by default and inert until the model is
downloaded; when it has run, the Croatian spell check is not sent too. Every prompt can choose a phone
model beside Haiku, Sonnet and Opus. **There is no "Default" model**: each prompt names its model and
says under it exactly which one is in use. Not yet run on a phone.

**Model downloads come in eight parallel pieces and resume after any drop** (MaFileDownloader,
aria2's way), into the shared `Download/models/`; a model already there at the right size is used
without downloading. See §192.

**Models repair themselves.** A request that fails because of the model asks the provider what exists,
picks the successor by family and version, writes it down and retries once. See
`MANTRA_MANIFEST/modules/model-self-repair.md`.

**The spacebar**: tap left quarter ←, right quarter →, middle half space. **Hold** a half and that
arrow repeats every 111ms after 350ms. No seam is drawn.

**Recording and sending name their language.** `● H` and `● E` record in Croatian and English;
`➤ H` and `➤ E` send in each. The key is the answer to *which language is this*, so there is nothing
to check before pressing. Each confirms in words — *Recording Croatian*, *Sending English*.

**Buckets.** Ten slots. `A↑` takes the lowest code block in the frame and works upward, `A↓` the
highest downward. **A block already in any bucket is never taken again**, at any slot, in either
direction.

**The reader.** Speechify, chunked 1·2·4·8·16 so it starts after one sentence rather than after the
whole screen. Six effects (`Hi Ty Kar Sp Vo Mx`), alignment top/middle/bottom which means the same
thing in both windows, and a filled square marking the head of every passage. On the window: swipe
**either way** kills, **down** next sentence, **up** previous, **pinch** full screen.

**The reader watches by default.** When the text runs out it waits, polls every 1.8s, and speaks
whatever has been ADDED — so a Claude answer still being written is read as it arrives. **Only a stop
ends it**: no timeout, no ceiling.

**English dictations under two minutes** go to AssemblyAI's Dictation API, which returns cleaned-up
text in the same request. Croatian is not one of its 32 languages, and anything longer than two
minutes takes the old path — both unaffected. Switched in Settings → Recording, where **Corrected dictation** also chooses between the cleaned
text and exactly the words spoken.

**The Claude.ai reader** keeps what it reads. While the reader is watching, the screen text is appended
to a per-chat log under `filesDir/cloud_logs`; Settings → Claude.ai reader lists them, opens one, and
reads from any sentence you tap. Off by default. The `Log` key captures the screen on demand,
without waiting for a reading.

**Messages** are drawn by the keyboard above every row — not system toasts, which cannot be moved off
the keys on Android 11 and later.

## RE Magic skill (9.11.2026)

RE transcribes with AssemblyAI only and writes by `Documents/TTTmini/re_ttt_skill.md`, the skill file that lives on the Mac
(`~/Downloads/API/re_ttt_skill.md`, never shipped in the app) and is moved with `re-skill-sync push|pull|status`. Settings has an
"RE Magic" entry with the key's icon and a skill editor; the app compares what RE wrote with what Marko sent (accessibility send-click
read-only, or the field emptied) and appends a rule to the skill's section 7. Details and the unproven list: DEVELOPMENT.md, last section.

## What has never been proven

**Nothing in this app has ever been run by me on a phone.** Not one build. Every "it works" in this
repository means "it compiled, and its logic was walked in a test".

Specifically unmeasured:

- **The nine gates**: G6 stress, G7 beyond artefact size, and G8 upgrade have never been run at all.
- **Android Lint has never blocked a release.** First measurement: **67 errors, 971 warnings**. It
  runs in CI and is deliberately non-blocking until those are cleared.
- **The silence trim has never reported a real number.** The percentage in its log line is the first
  real measurement and only exists once he dictates.
- **The word-classification queue has never called a model.** Whether Haiku is good at single
  Croatian words out of context is the assumption the whole queue rests on.
- **The reader loop is guarded, not diagnosed.** Reported twice; the second guard sits at the door of
  `speak` because the cause was never found.
- **`Retry-After` and `x-ratelimit-reset-*` are parsed nowhere**, so a rested key waits a fixed
  period rather than the one the provider asked for.
- **The cursor pad is unreachable but not deleted** — 17 references across five files, including live
  branches in `KeyboardManager` and `Smartbar`.
- **The accessibility copy fallback has never performed a copy.** Whether another app's text view
  honours `ACTION_COPY` decides whether it works at all.

## Building

Push to `main`. GitHub Actions builds, runs `verify.py` and all 24 Test 1 suites as **blocking**
gates, scans the built APK for key shapes, cuts a release tagged `build-N`, and keeps the two newest.

**Every action is pinned by commit SHA, never by tag.** A tag is mutable and this workflow runs with a
token that can push to every repository in the account.

## The files

| Path | What lives there |
|---|---|
| `dictate/DictateController.kt` | recording, transcription, rewording, the whole state machine |
| `dictate/llm/` | the model on the phone: catalog, the `:llm` service, its client, the JNI side |
| `app/src/main/cpp/` | `ma_llm.cpp`, the llama.cpp bridge; llama.cpp itself is fetched by `tools/fetch-llama-cpp.sh` |
| `dictate/ui/MaFeatureRow.kt` | every feature-row key |
| `dictate/ui/LegacyDictateLayout.kt` | `ThemedKey`, the spacebar, the transcription view |
| `dictate/ui/MaSubtitleRow.kt` | the reading window, its effects and its gestures |
| `dictate/MaRows.kt` | rows, buckets, the storage format |
| `dictate/MaReader.kt` | the reader and its chunking |
| `dictate/nlp/` | the n-gram, the language gate, AI prediction |
| `dictate/overlay/` | the accessibility service, the recording hairline |
| `lib/dictate-core/.../provider/` | providers, the key ring, the failure classifier |
| `scripts/verify.py` | 20+ static checks, blocking in CI |
| `scripts/test_*.py` | 24 Test 1 suites, blocking in CI |
