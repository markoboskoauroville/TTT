# TTT — every bug, and every reason

**The one job of this file: nobody undoes a decision that was already paid for.**

Read [`HANDOFF.md`](HANDOFF.md) to know what the app is and how to build it. Read this to know **why**
a thing is the way it is — and the answer is almost always a failure that cost something.

Structured per `MANTRA_MANIFEST/modules/repository-documents.md`. Four parts, in this order: the bugs
**grouped by shape**, the decisions **with what was rejected**, the versions, and what was inherited.

> **A note on what is here.** Parts One, Two and Four were written on 26.8.2026 by re-reading the
> whole archive. Part Three is that archive, unchanged, in the order it was written. Nothing was
> deleted — a bug that was fixed is still a bug that happened, and deleting it is how it comes back.
>
> The archive begins at **§147**. Sections §9–§146 are listed in
> [`NEXT_DEFAULTS_INDEX.md`](NEXT_DEFAULTS_INDEX.md) but their text is not in this file; it was
> trimmed at some point before this split and is recoverable only from git history. **That is a real
> loss and it is written down rather than glossed over.**

---

# PART ONE — THE BUGS, BY SHAPE

Grouped by what went wrong, not by when. Written per version these read as unrelated mistakes and
teach nothing; by shape, the repeat is the first thing a new session sees.

## SHAPE 1 — A scripted edit that failed silently. FIVE TIMES.

**Symptom, every time:** CI goes red on an unresolved reference, and the code looks correct.

**Cause:** a `str.replace` or a line-range cut that matched nothing, matched the wrong thing, or died
between two halves of one change. **A replace that matches nothing is indistinguishable from one that
worked.**

Three distinct ways, all the same shape:

| Way | Where | What it cost |
|---|---|---|
| Range cut by naming two ends | §150a | **six live declarations deleted**, 16 unresolved references |
| Anchor that did not exist in that file | §179a | the reference went in, the import did not |
| Script died between the two halves of one edit | §187a | the use went in, the declaration did not |
| Argument added without reading whether the call had one | §162a | `contentAlignment` passed twice |

**What stops it now:** `check_removed_declarations` in `verify.py` catches the first. Nothing catches
the others — a guard was measured at **17 false positives** and rejected. What actually works is a
habit: **after every scripted edit, grep for the thing the edit was supposed to add.** Do not trust
that no exception was raised.

## SHAPE 2 — A check that passed for a reason unrelated to what it claimed. SIX TIMES.

**Symptom:** sabotage the code the check guards, and the check stays green.

**Cause, every time: the check was looking somewhere wider than its claim.**

| Where it looked | What it found instead | §|
|---|---|---|
| the whole file, for a guard in one function | the same guard in `cancelTranscription` | 163 |
| the whole file, for a name | the **import line** containing it | 170 |
| the whole file, for a write to shift state | `MaNextFieldKey`, using shift correctly for another job | 171 |
| the whole file, for a control | the prompt-wordings picker, doing a real job | 181 |
| a slice to end-of-file | the helper below the section | 181 |
| a Python port of the logic | **itself** — sabotaging the Kotlin left it green | 178 |

**What stops it:** extract the function body or bound the section at both ends, and search inside it.
And for a ported model, **assert against the real function too** — a model is not a witness to the
code it models.

## SHAPE 3 — A rule written for one path, applied to one path.

**Symptom:** the bug survives the fix.

- **The reader loop** (§175, §176). The guard went in `continueBelow`. The loop was on another path.
  Moved to `speak`, where every reading must pass. **A guard on one route is a guard on one route.**
- **The language rule** (§179). Present in the proofread prompt for months, buried mid-list, in
  English. Naming the language and putting the rule LAST is what made it work.

## SHAPE 3b — A label read from the setting rather than from the thing it labels.

**Symptom:** *"When I'm sending English text, the status line says Croatian."*

**Cause:** the sending line displayed `maLanguageMode` — the current SETTING. A request already on
the wire was sent in whatever it was sent in; when the setting moved on for the next dictation, the
line relabelled the old request.

Twice, the same line, two different wrong sources:

1. it read `MaLanguage.badge()` once at composition, so it never updated at all (§179);
2. it read the preference as state, so it updated — **to the wrong thing** (§188).

**A label on a thing in flight must come from the thing, not from the setting that started it.** The
request carries its own language now, captured when it leaves.

## SHAPE 3c — A check written for one bug, holding the next one in place.

**§188.** `test_sending_badge.py` asserted the badge must be derived from the PREFERENCE. That was
right when it was written — the bug then was a badge read once at composition that never updated.

It became **wrong** when the next bug arrived on the same line: the preference is what the next
dictation will use, so reading it relabelled a request already on the wire.

**CI caught it because the fix made the old check fail**, which is the system working. The check was
restated rather than deleted — the claim is unchanged, *one source for the letter*, and only the
source moved.

Related, and now five occurrences: **a check that quotes a line rather than a behaviour breaks when
the line is reformatted.** `"inFlight = InFlight(audioFile"` broke because the constructor gained a
field and went multi-line. Nothing was wrong. The pattern is always the same — the check quoted the
easiest thing to grep on the day it was written.

## SHAPE 4 — Ordering, in a list a model reads.

**Symptom:** the instruction is correct and the output is wrong.

- **§179** — style rules in English written AFTER the language rule dragged the output back to English.
- **§180** — "return it unchanged if already correct" read BEFORE the fragment rule, so a three-word
  phrase came back untouched.

**Both tests assert the order by index**, not the presence of the sentence. A rule in the right file
in the wrong place is the same bug.

## SHAPE 5 — Two implementations of one thing.

Met four times, and the second implementation is always the one that drifts, because it is the one
nobody remembers when the first is improved.

- **two copy rows** (§146) — different preferences, one name, a comment at each site claiming they
  were the same
- **two shifts** (§171) — the feature row wrote `inputShiftState` directly; caps lock took three taps
- **two switches for one row** — the copy row, the buckets, the volume keys
- **the anagram and the keyboard** (§158) — a tracing of the key grid that could never be the keys

**The cure is always one owner with several doors**, never several owners.

## SHAPE 6 — A setting that could not be evaluated.

**§166, §167.** Silence trimming shipped ON, at a threshold that cut only pauses longer than two
seconds — which is almost none of a real dictation. It saved **nothing**, for months, and nobody
noticed because nothing reported what it saved.

**A setting that cannot be evaluated is a setting nobody can decide about.** It now prints what it
saved, under the switch that did it.

## SHAPE 7 — A convention applied late, not applied backwards.

**§172.** The three zone keys had ring-and-glyph backwards — a monochrome ring for kind, a green
glyph for state — because they were written before the convention existed and were never revisited.

**A convention introduced late does not apply itself retroactively.** Every control written before it
is an exception until somebody looks.

## SHAPE 8 — A check that fired on correct code.

Four, and every one of them cost trust rather than a build:

- `strip_code` could not see **900 of 963 lines** of one file — a Kotlin char literal holding a double
  quote opened a string that never closed. Every check reading that file had been reporting "nothing
  to report" on nothing at all.
- `check_property_call` — **434 false positives**. Deleted.
- `check_duplicate_declarations` — called a `val` and a `fun` of the same name a conflict, then called
  two data classes' constructor parameters duplicates.
- `check_removed_declarations` — could not tell `style = MaterialTheme…`, a named argument, from a use.

**The rule this produced:** measure a new check across the whole codebase before shipping it. Zero
false positives, or it does not ship. Two were deleted on that rule; five shipped because of it.

## SHAPE 9 — A check that goes red on success.

**§168a.** The APK secret-scanner failed the build **because the artefact was clean** — `set -o
pipefail` plus a `grep` that exits 1 when it matches nothing.

**Make every check fail on purpose once. And make it PASS on purpose once.** I had confirmed it could
stop a build and never that it could let one through.

---

# PART TWO — THE DECISIONS, AND WHAT THEY REJECTED

A decision without its rejected alternative is a decision that gets re-litigated.

| Decision | Rejected | What the rejected option would have cost |
|---|---|---|
| **Both sides of the reading window kill** | left kills, right pauses | the gesture is used without looking; a mis-aimed kill would leave the reading running while he believed he had ended it (§164) |
| **Volume keys let go of the reader** | keep next/previous sentence | he could not change the volume of the voice he was listening to — deleting a feature produced a better one (§164) |
| **Row reordering is a MOVE via drag** | a swap, as the buttons did | the buttons were right to swap: a sentence naming two rows should touch two. A drag depicts sliding in front, and the gesture is the specification (§170) |
| **F1–F6 and the arrows written once for the set** | one branch each | three copies of one key is three places for the ring to disagree with the row (§165, §169) |
| **Two automatic buckets, not one with a mode** | a direction toggle | a mode means remembering which way it points before every press, and being wrong collects from the wrong end (§182) |
| **One voice, no style chooser** | Marko / Yshai options | `prose-voice.md` §3 names Yshai's lowercase as the one thing we do NOT copy — the chooser offered to copy it (§181) |
| **The word-classification gate asks only about new words** | classify every word | his rule: a word already in a model is already in the right place. Turns a per-word cost into one that decays to zero (§160) |
| **Two record keys and two send keys, no language mode** | one key plus a setting | every recording began with a question he could only answer by looking, and getting it wrong was invisible until the words came back in the wrong language. **A mode you have to check before every use is not a setting, it is a trap** (§188) |
| **The hold repeats in `onPress`** | `onLongPress` | it fires once; this must continue until he lifts, and `tryAwaitRelease` is only available in `onPress` (§187) |
| **Two halves on the hold, three zones on the tap** | one rule for both | a tap must leave room for the space; a hold need not, because nobody holds a spacebar to type spaces (§187) |
| **Messages drawn by the keyboard** | `Toast.setGravity(TOP)` | **it does not work** — ignored for text toasts since Android 11 (§185) |
| **Lint runs but does not block** | make it fatal now | **67 errors, 971 warnings** on first measurement. A gate that arrives with 67 errors is bypassed the day it is added (§168) |
| **`libonnxruntime.so` stays** | drop on-device STT, or download it on first run | 25.8 MB of the 40 MB APK. Both alternatives change what works offline — his call, not mine (§186) |
| **`maProseStyle` left declared and unread** | delete the preference | a phone that stored "yshai" would read back a missing preference (§181) |
| **The cursor pad is unreachable, not deleted** | cut it in the same build | 17 references across five files including live `KeyboardManager` and `Smartbar` branches; cutting at the end of a build is how §150a happened (§187) |

## Reversals — where the first argument was not wrong, it lost

- **Swap → move** for rows (§165 → §170). The swap argument was an argument about a *button*.
- **Anagram → Matrix** (§157 → §158). The keyboard-as-display idea survived; the letters travelling
  did not, because the eye followed them instead of the word.
- **"Top line" effect → alignment setting** (§159 → §162). One effect became a property of every
  effect. A promotion, not a deletion.

---

# PART THREE — THE ARCHIVE, IN THE ORDER IT WAS WRITTEN

The original `NEXT_DEFAULTS.md`, unchanged, from here to Part Four. It is the detail behind the
shapes above: newer sections override older ones, and several early entries describe features that
have since been removed.

**Part Four follows it**, at the end of the file, because this part is thousands of lines long and
burying the inherited-code notes inside it would hide the cheapest paragraphs in the repository.

---

# THE LIST

Marko picks by number. When a feature is tested and working, it is struck off and the list is
reissued. This is the working menu — everything below this section is the detail behind it.

**Ready to ship, no design left**

| # | Feature | Size |
|---|---|---|
| ~~1~~ | ~~Paste timing defaults: 0, 500, 0~~ — **done, and it was already done**: the prefs shipped 0/500/0 and the screen already advises raising step 2. The list was stale. | — |
| ~~2~~ | ~~Settings order defaults~~ — **done, and it was already done**: `MaSettingsOrder.DEFAULT` already matched his thirteen exactly. | — |
| ~~3~~ | ~~Three more magic defaults~~ — **shipped**: `Use Image URL` (url), `Add URL` (+url), `Generate Images` (gen), unscoped. **See the caveat below.** | — |
| ~~4~~ | ~~Check a key exists before recording~~ — **already done**: `startRecording` refuses on no key AND no local model, with OPEN_SETTINGS. The list was stale. | — |
| ~~5~~ | ~~Restore keys button~~ — **shipped, build 112.** Restore existed but fired only on a completely empty keyring, which covers a fresh install and nothing else. Now a button beside Back up, merging through the same importer. | — |
| ~~6~~ | ~~Re-transcribe with a language beside it~~ — **already done**: the in-keyboard history panel carries the HR/ENG badge next to replay, and `retranscribeHistoryEntry` reads it through `MaLanguage.active()`. | — |
| 7 | Switchboard: rename to Magic Finger row, sand for tappable names, an icon per line | small |

**Caveat on 3, and a small job it creates.** `defaults()` is only ever reached through
`parse(raw).ifEmpty { defaults() }`, so a new default reaches a **fresh install only**. Marko already
has a stored target list, so the three new terms will not appear on his phone; the only route today
is the Reset button on the Magic button screen, which discards anything he has taught. **Worth
building: a one-time merge that appends missing built-in defaults to an existing list, guarded by a
"defaults merged up to version N" flag.** Small, and it makes every future default actually arrive.


**Needs a decision or a permission flow**

| # | Feature | Size |
|---|---|---|
| 8 | Grant all-files access moves into setup, beside the microphone | medium |
| 9 | Accessibility onboarding: the App info → ⋮ → Allow restricted settings walk | medium |
| 10 | Settings backup / restore / reset, same folder as the keys | medium |
| 11 | Strip Gemini and Anthropic; one AssemblyAI key for everything | medium |

**New rows and keys**

| # | Feature | Size |
|---|---|---|
| 12 | The pinned row: move the suggestion row and recording bar **below** the keys | medium |
| 13 | Zone keys independent of the keyboard, faces `1N` `2K` `3P` | medium |
| 14 | The new copy row: skip by word, locking Shift that selects, cut/copy/paste/all/delete | medium |
| 15 | Magic keys light up when their button is on screen, grey when it is not | medium |
| 16 | View switching: `?123` cycles, long press lists, Keyboard views screen, ticked quick-cycle | large |

**The big ones**

| # | Feature | Size |
|---|---|---|
| 17 | TRANSCRIPTION settings group — move everything about text into one place | medium |
| 18 | Shape: prompt library, 20 slots, model radio, through the LLM Gateway | large |
| ~~19~~ | ~~Croatian suggestions~~ — **shipped, build 116.** 50,000 Croatian words bundled, searched under the personal model. See §33. | — |
| 20 | Wire the ONNX predictor to a real model — **measured and argued against, see §33.** Do not start this without reading it. | large |
| 21 | Configurable long-press symbols on `Z X C V B N M` — touches upstream layout code | large |
| 22 | The sequencer — see SEQUENCER_PARKED.md; needs the action-extraction refactor first | large |
| 23 | The hardware trigger for voice commands — **not** Power, see §27 for what is possible | medium |
| ~~24~~ | ~~Voice commands settings entry~~ — **shipped**, build 111. Documents the commands; the trigger choice joins it with 23. | — |
| 26 | **Groq language detection on the first 5 seconds** — see §28. Groq is now a registered provider (build 114), so this is back to medium: chunk, call, clamp. | medium |
| ~~25~~ | ~~Merge new built-in defaults into an existing list~~ — **shipped, build 112.** `MaMagicTargets.mergeNewDefaults` plus a version high-water mark; runs when the Magic finger screen opens. Raise `DEFAULTS_VERSION` whenever a default is added. | — |

**Notes on order**

- 17 before 18, or Shape lands in the wrong place and moves twice.
- 8 before 5 and 10 — those need the permission.
- 12 and 13 together; the number row's icon is the `1N` glyph.
- 21 and 22 alone, each in its own build.

---

# Defaults to ship — note for the next build

Not code. A record of what Marko has actually arrived at on his own phone, so the next fresh
install starts there instead of at whatever was guessed. Everything below is read off his
screenshots of build 85, not proposed.

## Paste timing

He has tuned these by hand against real fields and settled on:

| Step | Value |
|---|---|
| 1. Before select all | **0** |
| 2. Before delete | **500** |
| 3. Before paste | **0** |

Currently all three default to 200. **Ship 0, 500, 0.** Confirmed by Marko: it is what works, tuned
against real fields on his phone over weeks.

It contradicts the advice printed on that screen, which tells the user to raise step 3 first. The
advice is the thing that is wrong, not the numbers — measurement beats reasoning, and the numbers
were measured. The wait that actually matters is **after asking the field to select everything**:
that is the step where the field has real work to do and the app is waiting on another process. The
other two are waits on this app's own calls, which have already returned.

So when shipping these, fix the on-screen text as well, or the app will ship advice that argues with
its own defaults.

## Settings order

The order he has dragged into place:

1. Edit settings order
2. Paste timing
3. Switchboard
4. Magic button
5. Feature row
6. API keys
7. Recording
8. Custom mappings
9. Output
10. History
11. Recovered recordings
12. Learn my words
13. Word predictions

Then the inherited FlorisBoard block: Keyboard, Smartbar, Gestures, Typing, Clipboard.

Note that **Switchboard sits third, not first**, although it was shipped first. He moved it below
Paste timing. Ship his order, not the alphabetical or the logical one.

## Magic button terms worth shipping

Confirmed working against real screens, with the app he uses them in:

**Claude (com.anthropic.claude)** — `Send`, `Stop responding`, `Copy message`.
Also available: `New chat`, `Share`, `Narrate`, `Retry message`, `Add to chat`, `Scroll to bottom`.

**imgtoimg.ai in Firefox** — `Use Image URL`, `Upload Image`, `Generate Images`, `Re-edit`,
`Regenerate`, `Download`.

The first three already ship. They belong to no app on purpose, and the same reasoning applies
below.

### Ship these four as defaults

| Term | Label | Why |
|---|---|---|
| `Copy message` | copy | already shipping |
| `Stop responding` | stop | already shipping |
| `Send` | send | already shipping |
| `Use Image URL` | url | **add this one** |
| `Add URL` | +url | **add this one** — confirms the dialog the one above opens |
| `Generate Images` | gen | **add this one** — starts the render |

`Use Image URL` and `Add URL` are one workflow rather than two keys: the first opens the dialog, he
pastes, the second confirms. Shipping one without the other leaves him reaching for the screen
halfway through, which is the reaching this feature exists to remove.

`Generate Images` completes that workflow: use URL, paste, add, generate. It is stored **without the
number**, although the button reads "Generate Images 2" — the number is the coin cost and changes
with the model, so a term carrying it would break the day he switches away from Nano Banana Pro.
Phrase matching finds the characters inside the longer label, which is exactly what that behaviour
is for.

All three are phrases and all three are safe. "Use Image URL" cannot fire on "Upload Image" beside it, and
"Add URL" cannot fire on the dialog's own title "Add Image URL" — phrase matching looks for the
characters in sequence, and "add image url" does not contain "add url".

Unscoped, like the other three. It belongs to a **web page** rather than an app, so scoping it to
`org.mozilla.firefox` would break the moment he opens the same site in another browser — and the
label is specific enough that it will not collide with anything else he uses.

The remaining imgtoimg terms — `Upload Image`, `Generate Images`, `Re-edit`, `Regenerate`,
`Download` — are his second daily workflow and the obvious candidates after that, but they are his
to choose rather than mine to assume. Four keys is a row he can read; nine is a row he has to
search.

## One thing not to forget

`Open menu, new feature available` was in an earlier Claude dump and is a trap: the label changes
when Anthropic stops showing the badge. Never ship a term containing words like "new feature
available" — match on the stable part or not at all.

---

# The pinned row — Marko's idea, worth building

He noticed the strip beside the pin at the bottom of the keyboard is empty, and it always has been.
It exists to hold the pin and nothing else, so it is free height the app already pays for.

**"It's government land. Let's use it."**

## What it is

A row at the very bottom, below the feature rows, permanently present because the pin lives there
anyway. Anything that currently appears and disappears above the keys can move into it instead.

## What should move there

**The suggestion row.** This is the fix for the flicker properly, rather than the switch that
merely keeps its height. Suggestions currently sit above the keys, so they push everything down when
they arrive; from the pinned row they change nothing above them because there is nothing above them
to move.

**The recording bar.** It appears on top and shoves the whole keyboard down mid-sentence. Marko:
*"put recording down, not to jump up."* He is right, and it is the same argument — a bar that
appears where there is already space costs nothing, and one that appears above the keys costs every
key its position.

## Why this is worth doing rather than nice to have

Every complaint about things jumping while he types has the same cause: transient rows are drawn
**above** the keys, so appearing and disappearing moves the keys. Anything drawn **below** them
cannot. This is not a workaround for the flicker — it is the actual fix, and the switch shipped in
build 85 is the workaround.

## Terms found in the Android photo picker

For picking a reference image without touching the screen:

| Term | Scope |
|---|---|
| `Done` | **must be scoped** to `com.google.android.photopicker` |
| `Preview` | same |
| `Deselect all` | same |

`Done` is the first term where scoping is not optional. Every app has one, so unscoped it would fire
in mail, in settings, anywhere. The six shipped defaults are specific enough to be safe unscoped;
this one is not, and the difference is worth stating rather than discovering.

---

# Magic keys that know whether their button is there

Marko's idea, and the better half of it is straightforward.

## The light-up

A magic key is grey when the button it presses is not on screen, and lit when it is. Same language
the buckets already use: dim means nothing there, lit means ready. He learns it once and reads it
everywhere.

This is worth more than it looks. A magic key currently gives no sign whether it will work until it
is pressed, so a press that finds nothing is indistinguishable from a broken key — which is exactly
the confusion that cost several builds earlier. A key that says *I can see it* before it is touched
removes that whole class of doubt.

**Feasible as described.** The accessibility service already walks the tree for the dump. The work
is to run that walk on window-content changes, collect which of his terms are present, and expose it
as state the row observes — the same shape as `MaScreenTargets.Learn.state`.

**The cost is the catch, and it needs designing rather than assuming.** A tree walk on every content
change is a walk many times a second in a scrolling app, and this runs in the keyboard's process on
a phone in someone's hand. It needs: throttling to no more than a few times a second, skipping
entirely while no magic key is drawn, and stopping when the screen has not changed. Get this wrong
and the feature is a battery complaint rather than a nicety.

## The auto-raise

*"When it recognizes the button, it just jumps up."*

**Partly possible, and the limit is Android's rather than ours.** An input method may not simply show
itself; it is shown when a text field takes focus. In the photo picker there is no text field, which
is precisely the case he wants it for.

What does exist is the pin, already built: when pinned, `onEvaluateInputViewShown` returns true and
the keyboard stays up with no editor at all. So the achievable version is **stay up**, not **jump
up** — pinned, the keyboard is already there when the picker opens, and the magic keys light as
their targets appear.

Whether an unpinned keyboard can be raised on demand needs testing on his phone rather than
reasoning about, since OEMs differ. Try `requestShowSelf` and believe the device, not the
documentation.

## Order to build

1. The light-up, throttled. It is self-contained, it makes every magic key honest, and it is the
   part he will feel immediately.
2. Then test whether auto-raise works at all on a Nothing Phone 2a, and only then decide whether it
   is a feature or a note saying "pin the keyboard first".

---

# The zone keys grow up: independent of the keyboard

Marko circled the `1` key and said it plainly: *"it's not child of the keyboard, it's now grown-up
kid who is independent."*

## What is wrong today

The zone keys switch parts of the typing keyboard on and off. They assume the keyboard is there.
Pressing `1` when the keyboard is collapsed asks for a number row on a keyboard that is not drawn,
so it does nothing visible and looks broken.

## What he wants

Each zone key brings up **its own row, on its own**, whether or not the letters are showing. Press
`1` and a number row appears — no keyboard needed. The zone is a thing in itself, not a switch on
something else.

This pairs with the pinned row note above: transient rows drawn below the keys cost nothing, and a
number row summoned without the alphabet is exactly that. It also makes the keys useful in the case
Marko keeps hitting — a screen with no text field, where the keyboard is collapsed and only the
magic keys matter.

## The labels: a letter for what the zone is

He asked for mnemonics on the key faces, because three bare numerals say nothing about what each one
opens:

| Key | Face | What it opens |
|---|---|---|
| Zone 1 | **1N** | **N**umbers — the digit row |
| Zone 2 | **2K** | **K**eys — the letter keyboard |
| Zone 3 | **3P** | **P**aste — the copy row |

The number keeps the position, the letter says the contents. Both fit inside the little keyboard
glyph drawn in build 72, so this is a label change rather than a new design — the glyph already has
room, since the numeral was sized for one character and there is space beside it.

Worth keeping the numeral first, in that order. He has learned the positions by number, and a face
reading `N1` would be a different key to relearn rather than the same key better labelled.

---

# The big batch — everything from the 14 August session

Written from Marko's screenshots and dictation. No code. Ordered by what unblocks what, not by the
order he said them.

---

## 1. Re-transcribe from the History screen

**What is wrong.** The top entry in his history reads *"Transcription failed — re-transcribe to
retry"* and there is no button to do it. The instruction names an action the screen does not offer.

**What to add**, per row: a re-transcribe control, and a language beside it.

The language matters more than it looks and he has hit it twice now. If the app was set to Croatian
while he spoke English, the transcript is wrong and re-running it in the same language reproduces
the same wrong answer. The keyboard's history panel already grew a language badge for exactly this
in build 46; the settings History screen never did. Same fix, other screen.

**Cheap, because the machinery exists.** `retranscribeHistoryEntry` already resolves the language
through `MaLanguage.active()` at the moment it sends, so setting the language and pressing replay is
all it takes. This is wiring, not new capability.

**Failed rows first.** They are the ones with nothing to lose and the ones carrying the instruction.

---

## 2. Restore keys, and move Grant all-files access into setup

**Restore is missing.** The screen has BACK UP KEYS and no way back. He said it plainly several
sessions ago — *"why do I store keys for next installation when I cannot get it? We locked ourselves
out of our own house."* Backup without restore is bookkeeping.

The backup already lands at `Documents/TTTmini/keys.txt`, which survives uninstall. Restore is
importing from that known path, and the importer already exists — with the provider mis-assignment
bug fixed in build 81, so a restore now puts each key where it belongs.

**Grant all-files access does not belong on this screen.** It is a permission, and permissions belong
in setup beside the microphone. By the time somebody is on the API keys screen wanting their keys
back, being told to go and grant a permission first is being told the door was locked after walking
to it.

**Sequence it in setup:** microphone, then all-files, then offer to restore keys from the backup
immediately — because at that moment the permission is fresh and the file is exactly where the app
put it.

---

## 3. Backup, restore and reset for settings

At the very top of the settings, three controls:

- **Backup** — write every preference to the same folder as the keys, `Documents/TTTmini/`.
- **Restore** — read them back.
- **Reset** — everything to shipped defaults, as it arrived.

Same folder as the keys on purpose: one permission grant covers both, and one folder is one thing to
remember when moving to a new phone.

**Reset needs a confirmation and reset needs to be honest** about what it does not undo — it cannot
bring back API keys, and it should say so rather than appear to have destroyed them.

---

## 4. Check the key before recording, not after

**Today:** he records, it uploads, it fails, and he learns there was no key. The recording is spent
and he has to re-transcribe.

**Instead:** before the microphone opens, check a usable key exists for the active provider. If not,
refuse immediately with a message that carries a button **straight to the API keys screen** — not to
the settings, to the screen.

Cheap to check, and it saves the worst kind of failure: one where the user did everything right and
lost the work anyway.

Less likely once §2 lands, but "less likely" is not "cannot happen" — a key can expire, be deleted,
or be rejected between one sentence and the next.

---

## 5. The new copy row

Scrap the old one. The new row is a text editor for the field in front, laid out like the transport
controls on a media player, which is a shape everybody already reads.

| Control | What it does |
|---|---|
| ⏮ / ⏭ | jump one word back / forward |
| **Shift** | a **locking toggle** — press once and it stays down |
| cut / copy / paste | the obvious three |
| select all | |
| delete | as on the keyboard |

**The lock is the good idea in this.** With Shift locked, the skip buttons stop moving the cursor and
start *selecting*. So selecting three words is: lock, skip, skip, skip. No dragging on glass, no
handles to miss, which for someone working by voice on a phone is the difference between editing and
not bothering.

Worth stating: this makes the copy row the first thing in the app that is a **text editor** rather
than a set of shortcuts. The buckets paste, this one *edits*.

---

## 6. The symbol row becomes configurable

The `Z X C V B N M` row carries symbols on long press — `* " ' : ; ! ?`. He wants each of those
long-press slots replaceable with **anything from the feature row**, and the chosen thing's symbol
drawn on the key so he can see what is there.

*"It is like a keyboard button and feature row in one."*

**This is the most invasive item in the batch and should be last.** The others add screens and rows;
this one reaches into FlorisBoard's key layout and popup system, which is upstream code the app has
otherwise left alone. Worth doing — it is a genuinely new idea, and the long-press slots are wasted
space at the moment — but not worth doing in the same build as anything else.

---

## 7. TRANSCRIPTION — one settings entry for the whole subject

A new entry, named in capitals as he asked, gathering everything about turning speech into text and
then working on that text. **Moved rather than copied** — the point is one place, and leaving the old
entries behind would make two.

What belongs in it:

- Word predictions (moves from its own entry)
- The transcription model, cloud and local
- **Download the local model** — Parakeet TDT v3, 25 languages, ~670 MB
- Translation, when it exists
- **Shape** — see below

The organising idea: *anything that recognises or then modifies the transcribed text*. That is a
real category rather than a folder, which is why it will still make sense after three more features
land in it.

---

## 8. Shape — prompts applied to text already transcribed

The biggest of these, and the one the AssemblyAI research already cleared the way for.

**How it works.** Text is already in the box. He presses **Shape**, picks a prompt, and the text is
replaced by the model's version of it — grammar fixed, tone changed, translated, whatever the prompt
says.

**Where the button lives.** On the pinned line at the bottom — the free strip beside the pin from the
note above. It appears when there is finished text to work on and costs nothing when there is not.

**The prompt library:** twenty slots, four across and five down. Each slot has a **title** and a
**prompt box**. The title is what he presses; the prompt is what gets sent.

**Model choice:** a radio button per model, one selected. Through AssemblyAI's LLM Gateway, so
Claude and the rest are reachable on the AssemblyAI key alone — no second credential, no second bill.
This is why the Gemini and Anthropic providers can go: not because the models are unwanted, but
because they arrive by a better road.

**After the fact, by design.** The text is sent as text, not as audio, and only when he asks. That is
the whole point of the Gateway over the transcription-time summarisation, which AssemblyAI has
deprecated anyway.

---

## Suggested order

1. **Restore keys + setup permissions** (§2) — unblocks fresh installs, and everything else assumes
   keys are present.
2. **Key check before recording** (§4) — small, and stops the worst failure.
3. **History re-transcribe + language** (§1) — wiring only, high daily value.
4. **Settings backup/restore/reset** (§3) — same folder, same permission as §2.
5. **TRANSCRIPTION grouping** (§7) — do the move before adding Shape to it, or Shape lands in the
   wrong place and has to be moved twice.
6. **Shape** (§8) — the big one, and it wants §7 finished first.
7. **New copy row** (§5) — self-contained, can slot in anywhere.
8. **Configurable symbol row** (§6) — last, alone, because it touches upstream layout code.

---

# 9. The Switchboard, three fixes

## Rename: Magic Finger row

"Magic button row" becomes **Magic Finger row**. His name for it, and the more distinctive one — the
app already has a great many buttons and only one finger.

Rename everywhere it faces the user: the switchboard line, the settings entry, the screen title, the
row's own switch. Not in the code, where `MaMagic*` is fine and renaming files would cost a large
diff for nothing.

## Nothing shows what is tappable

The names open settings and the switches toggle, and **the screen gives no sign which is which**. He
built the row and still could not tell by looking. Two targets on one line is only safe when the
targets look different, and here they do not.

The fix is the rule the app already uses: **sand means this takes you somewhere**, established in
build 76 for the wand's gear. Apply it here — the names in sand, so a tappable name reads as a link
rather than as a label that happens to respond.

The summaries stay grey. They are description, not destination, and colouring them would say they go
somewhere too.

Worth noting this is the same class of mistake as the drag handle that was really an edit button: a
control that is present, correct and invisible because it looks like something else. Two of those in
one app is a pattern rather than an accident, and the rule to take from it is that **anything
tappable must look different from anything that is not** — not merely be tappable.

## An icon on every line

Every row gets a mark, matching the key it controls wherever one exists, so the switchboard reads
like the keyboard rather than like a list of names:

| Line | Icon |
|---|---|
| Feature row | the drag handle it already uses in the settings list |
| Magic Finger row | the finger — same mark as the key and the settings entry |
| Copy buckets | the paste mark from Paste timing |
| Suggestion row | the spellcheck mark from Word predictions |
| Number row | **the 1N keyboard glyph** from the note above, once that lands |

The number row is the interesting one: it should carry the same little-keyboard glyph the zone key
carries, so the switch and the key are visibly the same thing. That ties this note to the zone-key
note above — do them together and both are better, do them apart and the icon has to be chosen
twice.

---

# 10. Switching between keyboard views, properly

Reaching a view takes several presses at different places on the screen. Marko: *"For Baba, that's
too much."*

The `?123` key becomes the switcher, and it stays in one place so his thumb does not have to move.

- **Tap** — cycle to the next view. Tap tap tap, always the same spot.
- **Long press** — a list of every view, tapped directly. Faster than cycling once there are more
  than three.
- **At the top of that list, Edit** — opens a new settings screen, **Keyboard views**, where the
  views are dragged into the order the cycle follows. Same drag-to-reorder as everything else in this
  app, so there is nothing new to learn.

## The two-view swap, which is the part he actually needs

*"Sometimes I need to go between numerical keyboard and normal keyboard. Even direct access is too
slow."*

A separate key that flips between the **last two views used**. Not a cycle, not a list — one key,
two views, no thinking.

**And the better version he arrived at while talking:** rather than remembering the last two, put
**tick boxes in Keyboard views** marking which views take part. The key then cycles only the ticked
ones. Two ticked gives the swap; three gives a small cycle of exactly the views he uses.

That is better than "last two" because last-two is a guess about intent that goes wrong the moment
he visits a third view for one keystroke, and ticks are a statement of intent that cannot.

## Why this deserves real effort

Switching is not a feature next to the others — it is the tax paid on every one of them. Every view
added makes reaching all the rest slower, so the app gets worse at this as it gets better at
everything else. Worth over-building rather than under-building.

---

# 10b. Shiva and Shakti share a key — the layout update

Marko's observation, and it is a real one about the layout rather than a flourish: **comma and dot
each occupy a whole key, while every other key on the board carries two characters.** `Z` carries
`*`, `X` carries `"`, `C` carries `'`. Only these two live alone.

## The merge

**Dot keeps the key. Comma moves onto its long press.**

Dot leads because it does more work — every sentence ends with one, and dictated text ends with
rather more than that. Comma is the second character on that key, reached by holding, exactly as `*`
is reached by holding `Z`.

This is the same rule the rest of the board already follows, so there is nothing new for anybody to
learn — only one key that stops being an exception.

## What the freed key becomes

The `?123` key becomes the **quick layout switcher**, and the freed comma space gives it room.

- **Tap** — jump straight between the layouts he has chosen. Not a cycle through all of them:
  through the two or three he ticked.
- **Long press** — *"beam me up, Scotty"* — straight into the settings screen where those layouts
  are ticked.

This is the same design as §10 above and should be built with it rather than beside it. §10 is the
slow switcher, walking every view in order; this is the fast one, jumping only between the ticked
few. Two keys, two speeds, one settings screen listing the layouts with tick boxes and a drag
handle.

## Status: half done in build 99

**Done:** the dot key now carries comma on its long press, so Shiva and Shakti share the seat.

**Also done in build 100:** the comma key is gone. Its slot now jumps straight to the second symbol
layout, so the two switchers sit side by side — one tap to either, where reaching the second used to
take two.

The faces changed with it. `?123` and `=\<` read as two unrelated keys and said nothing about
layouts; they are now ☰1 and ☰2, three stacked bars for the rows of a keyboard and a numeral for
which one. One family, and neither of them claims to be "the digits key" now that the layouts can be
reordered.

Still open from §16: the **ticked** cycle, where he chooses which layouts take part rather than
having two fixed keys.

## Note on effort

The merge itself is a **layout change** — FlorisBoard keeps its key layouts as data, so moving comma
onto the dot key's popup is editing that data rather than writing logic. Contained, and lower risk
than most of the list.

The switcher half is §16 and is large. **Do the comma merge first and on its own**: it is small, it
is immediately visible, and it frees the space the switcher will want.

---

# 23. Automatic language, clamped to two

Marko's objection to open detection is correct and settles the design: with 99 possible answers and
only two possible truths, a detector can return Spanish for accented English or Chinese for a noisy
room. **Whatever detects, its answer must be clamped to {hr, en} before anything acts on it.**

AssemblyAI's own `language_detection` cannot be constrained to a candidate set. That alone rules it
out as the router — not its accuracy, its unboundedness.

## Two detectors, at two moments, for two different jobs

### A. Before transcribing — audio, via Groq Whisper

Needed because the path must be chosen **before** the audio is sent: English can take Sync and be
fast; Croatian cannot, since Sync's eighteen languages do not include it.

- Send only the **first few seconds**, not the whole recording. Language is obvious in one sentence
  and this keeps it nearly free.
- Smallest Whisper on Groq, chosen for latency rather than accuracy — the question is much easier
  than transcription.
- **Clamp the answer:** `hr` stays `hr`; **everything else becomes `en`.** Not "unknown", not the
  raw guess. Two languages means two outcomes, and English is the safe default because it is the one
  Sync supports.

### B. After transcribing — text, and this one should be local and free

Marko's second idea, and better than it sounds: check the **transcript** rather than the audio.

**This needs no model and no network at all.** Telling Croatian from English in a sentence is easy
enough to do on the phone in microseconds:

- Croatian carries č ć ž š đ, which English never does — one of those is nearly conclusive.
- Failing that, the commonest words separate cleanly: *je, i, na, se, da, u, ne, su* against
  *the, and, of, to, is, it*.
- A few dozen words and five letters, counted. No download, no latency, no cost, works offline.

So the "send the text to another model to ask what language it is" step should never be built as an
API call. It is a hundred lines of Kotlin and a word list.

**What it is for:** disagreement. If the transcript looks Croatian but was transcribed as English,
the History row says so and offers the re-transcribe with one tap — the failure Marko keeps hitting,
caught automatically instead of noticed later.

It also removes any need for a second Groq call, and eventually the first: once §20's on-device model
exists, audio detection can be local too and Groq leaves entirely.

## The switch stays, and starts moving on its own

He was explicit: *"I want to see how it's switching automatically."*

The HR/ENG key remains exactly where it is and keeps its tap-to-override. What changes is that the
detector writes to it, so he watches it flip and can disagree. A setting that changes itself in front
of you is trustworthy in a way a hidden one never is — and it means the override is always one tap,
never a settings trip.

**Manual and automatic in one control**, which is what he asked for.

## Order

1. **B first.** It is local, free, and needs no key — and it makes every wrong transcription visible
   even before A exists.
2. **A second**, once B is proving how often the language is actually wrong.
3. **Drop Groq** when §20 lands and detection can be done on-device.

---

# 24. The corpus he already has

Marko asked for his transcriptions to be collected and analysed so the app learns from them, with a
cache he can inspect, delete, and read statistics from.

**Most of it exists.** Saying so is more useful than building a second copy.

- **The store is the History.** Every transcription is already kept with its text, language, model,
  duration and size, and the storage limits screen already shows what is retained.
- **The learning already happens twice.** `MaNgram` learns from what he commits and feeds the
  suggestion row. `Learn my words` mines the history for vocabulary the dictionary lacks.

So the answer is **not a transcription cache** — that would be a second copy of the History that can
disagree with it, and the wrong kind of work. What is actually missing:

## What to build

**A statistics screen over the history that already exists.**

- Total words dictated, total recordings, hours of audio.
- The words he uses most, with counts — his own vocabulary ranked.
- Croatian against English, by proportion.
- How often a transcription was re-run, and in which language. That number is the app grading
  itself.

**Make the corpus openable.** Export the history as one text file to `Documents/TTTmini/`, beside
the keys and the settings backup. Then he can read it, keep it, or feed it somewhere else — and
deleting it in the app does not destroy it.

**Say what it costs.** File size and word count on that screen, so deleting is an informed choice
rather than a guess.

## Why this fits the app rather than decorating it

His words: *"we are building applications which are not only useful, which are teachers. Student and
teacher at the same time."*

Statistics on his own speech are the app teaching him something about himself he cannot otherwise
see — which words he leans on, how much he actually dictates, how often the language guess is wrong.
And every one of those numbers is also the app being taught: the re-transcribe count says whether
§23 is working, the word ranks are what §19 needs for Croatian suggestions.

The same data serves both directions, which is what makes it worth building rather than merely nice.

---

# 25. The two switchers become two slots

Today each switcher key leads somewhere fixed. Marko wants each to be a **slot holding a layout he
chose**, so the pair becomes "my two layouts" rather than "the two layouts the app picked".

- **Tap** — go to whatever that slot holds. Unchanged from his thumb's point of view.
- **Swipe up / down** — change what the slot holds, cycling the available layouts.
- **Long press** — open the full list and choose directly.

Two keys, the same behaviour, **separate memories**. That is the whole design: `text`/`sym1` today
is one possible pair among many, and which pair he wants changes with what he is doing.

## What exists and what does not

**Long press now opens the list — built, not yet confirmed on the phone.** Every view-switcher key
in `charactersMod`, `symbolsMod` and `symbols2Mod` carries a popup listing the three text views, and
`VIEW_CHARACTERS`, `VIEW_SYMBOLS`, `VIEW_SYMBOLS2` were added to `ExceptionsForKeyCodes` in
`PopupUiController.kt`. That list is the gate: extended popups are refused to any key whose code
sits below `SPACE`, which is every system key, and that refusal was why holding a switcher did
nothing. Basic popups stay refused, so no preview bubble appears over the faces.

**This is navigation, not slots.** Holding a switcher and choosing takes you to that view; the key
does not remember. Slot memory and the face reading from a preference are still open, and are the
real §25.

**Numeric and phone are deliberately absent from the list.** `VIEW_NUMERIC`'s face is the two-line
string `1 2\n3 4`, which is a key face rather than a list entry. Adding those views wants a
single-line face first, and that face cannot simply be changed in `strings_dont_translate.xml`
because the same string draws the real numeric key.

**Swipe up and down on a specific key does not exist.** FlorisBoard has a gesture system, but it is
built around swipe-on-the-whole-keyboard actions and long-press popups, not per-key vertical swipes.
This is the part that needs real work and the reason this is not a small feature.

**Suggested fallback if per-key swipe proves awkward:** long press opens the picker, and that alone
delivers most of the value. Swipe is the refinement, not the feature.

## Storage

Two preferences holding a layout id each, defaulting to what ships now — `symbols` and `symbols2`
from the letters view. The key's face comes from the slot rather than being fixed, so `sym1` becomes
whatever is in it. That means the label logic in ComputingEvaluator stops being a constant and starts
reading a preference; small, but it touches the file that draws every key face, so it wants care.

---

# 26. Voice commands — "press send" (shipped, build 110)

A finished transcription reading **exactly two words**, a press verb and one more word, presses that
button instead of being typed. `MaVoiceCommand.targetIn` is the rule and `commitOutput` in
`DictateController` is the seam — the one place every finished transcription passes through on its
way to a field, whichever sink it is bound for.

Verbs: `press`, `pritisni`, `stisni`, `klikni`. Trailing punctuation is stripped first, because
AssemblyAI punctuates and "Press send." is what actually arrives.

**Why exactly two words.** Dictated text is what this app is for, and a rule that ate part of a
sentence would lose work that cannot be recovered — the words were spoken, not typed, so there is
nothing to undo back to. Two words alone is not something anybody dictates mid-sentence: it is said
to a machine, after a pause, on purpose. Tested against `I will press send now` and
`press the send button`; both stay as text.

**Nothing is lost when it misses.** `pressScreenTarget` returns the term it found, or null. On null
the code falls through and types the words as it always would have. That is what makes it safe to
fire without confirming.

**The spoken word is matched against taught terms first**, by face and by term, so "press stop"
finds the target labelled `stop` and searches for `Stop responding` — he never has to say the long
name. The raw spoken word goes last as a fallback so an untaught button still answers to its own
name.

Switch: `dictate__ma_voice_commands`, on by default, on the Magic finger screen.

---

# 27. The hardware trigger — and why it cannot be Power

**Volume Up + Power is not buildable. This is a platform limit, not a difficulty.** The power key is
consumed by the system's window manager and is never dispatched to applications, and it is not
delivered to accessibility services either, even ones holding `flagRequestFilterKeyEvents`. There is
no permission that changes this. Any design resting on Power has to be redrawn rather than
attempted.

## What is actually available

**Volume keys, globally, through the accessibility service.** An `AccessibilityService` declaring
`android:canRequestFilterKeyEvents` and `flagRequestFilterKeyEvents` receives `onKeyEvent` for the
volume keys **everywhere** — no keyboard, no focus, any app. This app already runs such a service
for the finger, the floating button and TAB, so the trigger has a home already built.

That gives these as real options:

- **Volume Up + Volume Down together.** The closest thing to what he described: two buttons, one
  gesture, works everywhere. Does not collide with the screenshot, which is Power + Volume Down.
  The one caution is Android's own "hold both volume keys" accessibility shortcut — a short chord is
  distinguishable from a three second hold, but it must be checked on his phone.
- **Long press Volume Up.** One button, no chord. Cheapest of the three.
- **Double press Volume Up.**

## What already exists and overlaps

`maHandleVolumeKey` in `FlorisImeService` already gives Volume Up start/stop dictation and Volume
Down cancel-or-switch-language — but **only while the input view is shown**, deliberately, because
an IME receives key events only then. The new trigger is the same idea moved into the service that
can hear those keys with no keyboard on screen, and the two must be made to agree rather than both
firing when the keyboard is up.

## Settings

A **Voice commands** entry of its own in the settings list, holding the on/off switch that now lives
on the Magic finger screen plus the trigger choice. Deferred with the trigger, since a screen for one
switch is a screen he has to find for no reason.

---

# 28. Groq decides the language, from the first five seconds

**Groq, the inference service — never xAI's Grok.** Marko was explicit and it is worth writing down
because dictation turns one into the other every time: when he says Grok he means the fast open
service already in `ProviderRegistry` as `"groq"`, with a key slot and a legacy migration path
(`DictateLegacySettings.transcriptionApiKeyGroq`).

## The shape, as he specified it

1. Cap the **first five seconds** of the recording into a second file, separate from the full audio.
2. Send that chunk to Groq's Whisper.
3. Take the language from the reply, **clamp it to {hr, en}** — `hr` stays `hr`, everything else
   becomes `en`, per §23.
4. **One millisecond before** the real request goes to AssemblyAI, apply that language.

## One call, not two

He asked for the chat model to be dropped and he is right: Groq's transcription response already
carries the detected language, so a second call to a chat model would be asking a slower question
that has already been answered. One call, and the answer arrives while he is still speaking.

## What it must not break

**Croatian must never reach the Sync path.** If detection returns `hr`, the request goes async, no
matter that the language was decided by a machine rather than by him. `SYNC_SAFE_LANGUAGES` stays an
allow-list, and the detection result is applied *before* `maUseSyncPath` reads it — that read
already happens late, after the resample, which is exactly where this fits.

## Where it plugs in

The language and the speed are both read **when the request is built**, not when recording starts —
see the note in `maHandleVolumeKey` about why the second volume press was reverted. That late read
is what makes this feasible without touching the recorder: the detection has the whole length of the
dictation to come back, and only has to win the race against the user pressing stop.

**If it is not back in time, or it fails, or there is no Groq key: use the switch as it stands.**
Detection is an improvement on the manual setting, never a precondition for dictating.

## The switch still moves, and it is now safe for it to

§23 said the detector should write to the HR/ENG badge so he can watch it flip and disagree. That
was written when volume down toggled the language on a short press, which made an automatic write
and an accidental write indistinguishable. Since build 111 the language only moves on a long press,
so a badge that changes by itself now means exactly one thing.

---

# 29. Audio focus: removed, and not to be rebuilt

Recording used to take `AUDIOFOCUS_GAIN_TRANSIENT` with `USAGE_VOICE_COMMUNICATION`, behind a
`dictate__audio_focus` preference that defaulted **on**. Two things followed from that, and both were
wrong:

- Every other player on the phone was asked to duck or pause whenever he started dictating.
- The focus listener **paused his recording** whenever another app took focus back.

With MA Reader playing in the background and something else starting up, those two rules met: each
program politely stopping for the other, which is why the behaviour read as backwards rather than
merely unwanted.

**Removed entirely**, not defaulted off: the request, the listener, the preference, the settings
toggle, the settings-search entry, the legacy migration and the legacy key. Six files. Nothing
replaces it.

**SUPERSEDED at build 159 — see §71.** He asked for exclusive recording back. The part of this
section that still stands is the second half: **nothing may react to what another app does with
audio.** Asking others to pause is fine; listening for focus changes is what was broken.
This app records through the microphone; it has no business holding the phone's audio session. A
future "pause music while recording" request should be read as a request to change *his* mind, not
as a gap to fill.

**Not covered by this:** the short press on volume down, which hands a volume change back to the
system through `adjustSuggestedStreamVolume`. That is the user pressing a volume key and getting a
volume change — the opposite of the app deciding something about somebody else's playback — and he
asked for it by name in build 111.

---

# 30. Ctrl+P, proofreading — and the correction to §28

## Ctrl+P

Bound in `maHandleCtrlCombo` alongside the clipboard and undo shortcuts, and taken deliberately: it
used to fall through to a real ctrl+P event, which means print on a desktop and nothing on a phone.

It goes through `DictateController.applyPrompt` with a throwaway `PromptModel` from `MaProofread` —
the same path the prompt library and the wand already use, so the network call, the key ring walk,
the stop button and the no-key error that opens settings were all already built and tested.
`requiresSelection = true` gives the right behaviour for free: correct the selection, or select the
whole field and correct that.

**The instruction is mostly prohibitions.** A model asked to correct will rewrite — shortening,
formalising, dropping deliberate repetition — and a paragraph that comes back reading better but no
longer sounding like him is worse than a missing comma, because it is harder to notice. It also
names no language, since naming English would translate his Croatian.

**Not yet done: pinning it to a model.** Ctrl+P uses whatever the rewording provider is set to. He
asked for Claude Sonnet specifically, and the honest state is that this respects his setting rather
than forcing one. Forcing a provider per prompt means an override threaded through `requestRewordRaw`,
which reads the account, the ring id and the model together. **Worth building: an optional
provider/model override on `applyPrompt`,** which would also let library prompts pick their own
model — §18 wants exactly that, so the two should be built together.

## §28 was wrong about Groq, and this is the correction

**There is no `groq` provider in `ProviderRegistry`.** The ids are openai, openrouter, gemini,
anthropic, together, deepinfra, mistral, soniox, elevenlabs, deepgram, assemblyai, assemblyai-sync,
xai, deepseek, ollama, local, custom.

What exists are references to one that is not there: `ProviderIcons` maps `"groq"` to a drawable,
`DictateLegacyMigrator` writes accounts with `providerId = "groq"`, and `SetupScreen` declares
`RECOMMENDED_PROVIDER_ID = "groq"` — **which is itself dead, never read anywhere**.

So §28's step 2 is not "send the chunk to Groq". It is: add the Groq preset to the registry, get a
key into the ring, wire and test the Whisper endpoint, and only then detect. Sizing it as medium was
wrong.

**Worth considering instead:** OpenAI runs Whisper on the same OpenAI-compatible shape and is already
a registered provider with a key slot. Same one call, same returned language field, none of the new
provider work. The clamp and the two-signal rule from §28 are unchanged either way — they read a
language string and a transcript, and do not care who produced them.

---

# 31. The keyring: Groq in, Gemini out

## Why a Groq key could not be stored

`ProviderRegistry.presets` was `ASSEMBLYAI, GEMINI, ANTHROPIC, LOCAL`. **A preset defined in that
file but absent from that list does not exist**: `byId` returns null, and `MaKeyImport.importAll`
does `byId(id) ?: continue`. Groq had no preset at all, and `MaKeyImport.PROVIDERS` never listed it.

Meanwhile `MaKeys` has known the Groq shape all along — `GROQ = gsk_[0-9A-Za-z_-]{20,}`, with an
`extract` branch and entries in both `belongsToAnotherProvider` and `mismatchWarning`. So a Groq key
in a keys file was recognised, matched, and then dropped on the floor for want of a provider to file
it under.

Fixed by adding the preset (`api.groq.com/openai/v1/`, chat and transcription, dynamic models) and
registering it. Also removed a dead branch in `extract`: `"groq" -> GROQ` was followed by
`"openai", "groq" -> OPENAI`, whose groq half could never be reached.

## Gemini, removed

Unregistered rather than deleted. Its preset stays defined, because removing it would take the
model-picker branch and the icon mapping with it for the sake of a few unread lines, and because
unregistering is the reversible half.

**A stored Gemini key is not erased.** It stops being importable and stops being shown — the keys
screen ends in `ids.mapNotNull { ProviderRegistry.byId(it) }`, so an id the registry does not know
drops out even when an account still holds it. If he wants the key actually gone from the ring, that
is a separate deliberate wipe and should be asked for rather than assumed.

## Model names to check

`llama-3.3-70b-versatile` and `whisper-large-v3-turbo` are the defaults for an account that never
chose one, and they are only as current as this was written. `supportsDynamicModels` is true, so the
picker can fetch the live list; a stale default costs a trip to the model picker, not a broken
provider.

---

# 32. The roles are fixed, and the chips are gone

## What went wrong

The API keys screen asked which provider held which role: a `transcription` and a `rewording` chip
per provider, writing `transcriptionProviderId` and `rewordingProviderId`.

Groq arrived in build 114, advertised transcription because it genuinely can transcribe, and **took
the transcription role**. Every dictated word went to Groq's Whisper instead of AssemblyAI, silently,
with the screen showing what looked like a correct configuration. The Croatian-never-Sync rule lives
inside the AssemblyAI path, so while that was true the rule protected nothing — AssemblyAI was not
being called at all.

A setting that can be wrong in a way nothing announces is a trap, not a setting.

## What replaces it

`MaRoles` holds the mapping: `assemblyai` transcribes, `anthropic` rewords and proofreads, `groq`
detects language. `transcriptionAccount()` and `rewordingAccount()` **resolve** through it at the
moment the job is done rather than reading a stored id, so a value that has drifted cannot survive,
because nothing consults it.

The stored preference remains as a fallback for a setup with no key for the fixed provider, which
keeps custom endpoints working — but it can never resolve to the language provider, in either role.

The chips are removed. Each provider now shows its role as a plain label, read from `MaRoles`, so the
screen cannot describe an arrangement different from the one in use. `MaRoleChip` and the two
`ProviderSection` parameters that fed it are deleted.

**The on-device model is unaffected**: that path reads `localTranscriptionAccount()` against
`ProviderRegistry.LOCAL` directly and never went through the role id.

## The rule to keep

When a new provider is added, give it a role in `MaRoles` — do not give the user a chip. Capability
is not the same as job: Groq can transcribe and must not, and the difference is a fact about this
app rather than about Groq.

---

# 33. Croatian suggestions, and why not the neural model

## What shipped

50,000 Croatian words in `assets/dictate/hr_words.txt`, 545 KB, searched by `MaBaseDictionary` and
appended by `MaNgram.predict` **after** the personal tiers.

Three conditions on it, each deliberate:

- **Only with a prefix.** A word list with no prefix returns the commonest word in the language,
  which is not a prediction.
- **Only when Croatian is active.** English already has a dictionary from upstream, and offering both
  would put Croatian under an English sentence.
- **Always last.** A word he has written outranks a word the language merely contains. `tier` is not
  read downstream, so position in the list *is* the ranking — which is why appending is the whole
  mechanism and `TIER_BASE = 0` is documentation.

It sits **outside** the `MIN_WORDS_BEFORE_PREDICTING` gate on purpose. An empty personal model is
exactly the state this exists for: a fresh install should suggest Croatian on the first word, not
after three hundred.

**Memory.** Held as one string plus one `IntArray` of line offsets, not 50,000 `String` objects,
which would cost megabytes in object headers alone inside a process Android kills for using what an
app may use freely. Loaded on first Croatian use; an English-only session never pays for it.

**Verified before building**: the binary search plus forward scan was run against brute force over
the real file for fourteen prefixes including `ž`, `šta`, `đ`, a one-letter prefix and a miss, plus
the exclude path. All identical.

**Licence.** CC BY-SA 4.0, from hermitdave/FrequencyWords over OpenSubtitles. Attribution and the
list of modifications are in `assets/dictate/hr_words_LICENSE.txt`. **That licence covers the data
file only**, not the app. Share-alike applies if the list is redistributed.

## Sizing, if it is ever revisited

Coverage of running text by list size, measured on the source corpus: 10k → 81%, 20k → 85%, 30k →
87.5%, **50k → 89.7%**, 80k → 91.3%, 120k → 92.3%. The next 50,000 words buy about three points and
double the file. 50k is where the curve flattens.

## §20, the neural predictor — measured, and argued against

`Xenova/distilgpt2` int8 was downloaded and its graph inspected rather than guessed:

- **81 MB**, 15 inputs (`input_ids`, `attention_mask`, twelve `past_key_values.N.key/value`,
  `use_cache_branch`), output `logits [batch, seq, 50257]`.
- **50257 is the GPT-2 BPE vocabulary: English only.** It cannot help the language that was actually
  missing.
- 81 MB inside an input method risks the keyboard being killed mid-sentence.
- `MaNeuralPredictor.BUDGET_MS` is 60 ms and discards slower passes, disabling the model after five.
  distilgpt2 int8 on this class of phone lands near that line, so it could install and then silently
  switch itself off.
- Uncounted work: a byte-level BPE tokenizer in Kotlin plus ~1.5 MB of vocab and merges, and a
  downloader, since 81 MB cannot go in the repo.
- `MaNeuralPredictor` is also called from nowhere; wiring it into `NlpManager` is a further step.

**If it is ever revisited, find a small multilingual model first.** An English-only model is the
impressive answer to a question this app does not have.

---

# 34. TAB for fields, through the node tree

**A real Tab key does not do this, and that was tested rather than assumed.** `MaMacroSyntax`
already maps `{Tab}` to `KEYCODE_TAB` and sends it through `sendKeyEvent`. In Suno it moves the
caret inside the field it is already in and nothing else.

That is not the app misbehaving. Android moves focus on Tab only between views marked focusable in
touch mode, and almost nothing is, because until recently no phone had a Tab key. So no amount of
sending a better Tab would have worked.

**`MaFeatureKey.NEXT_FIELD` reads the accessibility node tree instead.**
`DictateAccessibilityService.focusNextEditableField()` collects every visible editable node
depth-first, finds the one holding focus, and calls `ACTION_FOCUS` on the next, wrapping to the
first. It tries each following field in turn rather than stopping at the first refusal, because a
field can decline focus and stopping there makes the key look broken when the next would have
worked.

Depth-first is declaration order, which in practice is down the screen. Wrapping means that on a
two-field screen — lyrics and style — one key is a toggle between them, which is the actual problem.

**Why this works where Tab does not:** the tree exists because screen readers need it, so every app
provides one whether or not it was built with a keyboard in mind. Native, web view and browser all
behave the same.

Caps: `MA_FIELD_MAX_DEPTH` and `MA_FIELD_MAX_COUNT`, both 40. Deeper than the dictation-target search
because this must find every field rather than the nearest one, and modern layouts nest hard.

**Two keys share the word TAB and must not be merged.** `APP_SWITCH` moves between apps;
`NEXT_FIELD` moves inside one. Both are in the default row.

The icon is `Icons.Default.KeyboardTab`, **verified to exist** by extracting
`material-icons-extended-android` and finding `androidx/compose/material/icons/filled/KeyboardTabKt.class`
— this file's own history says an icon that only exists in the documentation is a red build.

---

# 35. Shift+Tab, a row Shift, and Aa

## The visibility caveat, fixed

`collectEditable` filtered on `isVisibleToUser`, which was wrong and was mine. A form taller than the
screen reports its later fields as not visible, so the key refused to reach **exactly the fields that
are hardest to reach by hand**. Off screen is a reason to scroll to a field, not a reason to pretend
it is not there.

The filter is gone and `ACTION_SHOW_ON_SCREEN` runs before `ACTION_FOCUS`, so the container scrolls
the field into the frame and then it takes focus.

## Shift on the feature row

`MaFeatureKey.SHIFT` writes the same `inputShiftState` the letter shift writes — the real modifier,
not a private flag sharing a name. It exists because the letter shift is unreachable in the state
this row is for: the row alone with the keys folded away.

Three states in a ring: off, once, locked. Locked earns its place here more than on the letters,
because walking backwards through a long form is several presses of TAB and re-arming shift before
each one would be the worse key. Lit in sand while it is holding.

## Shift+TAB

`NEXT_FIELD` reads `inputShiftState` and walks backwards when it is set, from either shift. A
`SHIFTED_MANUAL` is **spent** by the press, the way a letter spends it; `CAPS_LOCK` is not, because
somebody who locked it means to keep going.

## Aa, the case cycle

`MaCaseCycle`: lower → UPPER → Sentence → Title → lower. Selection if there is one, otherwise the
whole field — the same rule Ctrl+P uses.

**The next case is read from the text, never from a counter.** A remembered position would go wrong
the moment anything else changed the text and would do it invisibly, so the key would seem to skip a
step for no reason. Unrecognised text starts the ring rather than being refused.

It steps twice when a transform would change nothing, because two cases can produce identical text —
a single capitalised word is both Sentence and Title — and stopping there looks like a dead key.

The four transforms are `MaCaseTransform`'s, not new ones: locale-aware, with `don't` not becoming
`Don'T` and sentence case lowering the shout. **Verified against the ring** before wiring: four
presses return to the start, text with no letters returns null, and Croatian diacritics uppercase
correctly.

---

# 36. Volume keys, and the finger's reach

## Volume keys, settled

**Volume down is released entirely.** It has held three jobs — language, cancel-a-recording,
language again on a hold — and every one made the commonest button on the phone mean something other
than quieter. Marko changes language rarely and turns the volume down constantly. `maHandleVolumeKey`
returns false for it, so nothing is consumed and Android does what it always did. **Do not give this
key a job again.**

**Volume up decides on release.** Short raises the volume through `adjustSuggestedStreamVolume`; a
hold of `MA_VOL_RECORD_HOLD_MS` (500 ms) calls `onMicClick`, which is both ends of a dictation, so
the same hold starts and stops. The frequent action is the cheap gesture and the rare one costs a
deliberate hold — which is the right way round for somebody playing bhajan through the same phone.

Cancelling a recording lost its key and keeps the bar's own control and the mic key, both on screen
while recording.

## The finger reaches off screen now

`matchOf` refused any node that was not `isVisibleToUser`, which made the finger refuse the presses
worth most: a button five screens down is exactly the one that saves the most scrolling, and it was
the one press that could not be made.

The check is gone, and `ACTION_SHOW_ON_SCREEN` runs before the click so the app scrolls to what it
acted on — a press whose effect happens unseen is indistinguishable from one that did nothing.

**The bottom-most match wins, and that is now the whole rule rather than a tiebreak.** A node below
the fold has a larger `bottom` than anything on screen, so in a chat carrying a copy button under
every answer ever given, the finger takes the newest rather than the newest *visible* one.

### The limit that remains, and it is Android's

**A list only puts materialised rows in the tree.** `RecyclerView` and its equivalents recycle rows
that are far off screen, so those nodes do not exist to be found — not filtered out, absent. Removing
the visibility check reaches everything the app has built, which is typically the viewport plus a
screen or so either side. It cannot reach a button ten screens up, because there is nothing there.

**Worth building, for the "hundreds of copy buttons" problem:** a target that names an occurrence —
`copy 1`, `copy 2`, counted from the bottom — and a finger that scrolls and re-searches when a target
is not found. The second is what actually removes the limit above, since scrolling is what makes the
rows exist.

---

# 37. The spacebar mark, and volume down carries a term

## The spacebar was bare, and the comment said otherwise

`computeLabel` returned **null** for `KeyCode.SPACE`, with a comment saying the face was decided
where the key is drawn. It is — but that code reads `key.label?.let { ... }`, so a null label meant
the block never ran and the bar was drawn empty. **The mark existed in a comment and nowhere on the
keyboard.** A worked example of why a comment is not evidence.

Now returns `U+23B5`. The drawing code still honours `SpaceBarMode.NOTHING` and still substitutes its
own mark, so what is returned here only has to be non-null.

## Volume down: a hold presses a magic finger term

Both hardware keys now have the same shape — nothing decided on the way down, a short press is
volume, a hold is the other meaning. Neither key can take the volume away on a quick tap, which is
the requirement when bhajan is playing through the same phone.

The hold presses a term through `pressScreenTarget`, the same path the finger uses, so a term that
works on the row works on the key. `dictate__ma_volume_down_term`, default `Send`. Empty leaves it a
plain volume key, and with the accessibility service off it stays a volume key rather than swallowing
the press.

**Still to build: the settings UI for it** — a way to pick which taught term the key carries, rather
than the preference only being reachable in code.

## Bucket copy — designed, not built

Confirmed from a real node dump: **code boxes are distinguishable.** They carry
`desc="Copy code"`; message footers carry `desc="Copy message"`. A term of `copy code` finds only
code blocks.

`ClipboardManager.captureIntoClipSlots` already files every clipboard change into the next free
bucket, reads which buckets are visible on each copy, and stops when they are full. **So bucket copy
does not write buckets at all** — it presses copy buttons one after another and the existing system
fills them in order. Resetting the buckets already sends the next copy back to the first.

To build:
- A key that collects every `copy code` match, orders them **bottom-up** (newest first), and presses
  each with a settle pause between — the clipboard must land before the next press or a bucket is
  skipped. Start at 250 ms and tune on the phone.
- A second plain copy key that takes the last match whatever it is, code or not.
- **A face that says which bucket is next** — a `B` with a pouring arrow — because without it he
  cannot tell where the sequence has got to.
- **Long press to reset to bucket one.** This is the part that makes it usable rather than a trap:
  without it a mistake means copying from last month.

**The limit that remains is Android's**: recycled rows are absent from the tree, not hidden in it, so
one press reaches the code boxes near the viewport rather than the whole conversation. A finger that
scrolls and re-searches is what actually removes it.

---

# 38. Ctrl+F, better flow

**Ctrl+P is unchanged and stays unchanged.** It fixes what is wrong and may not touch a word he
chose. `MaFlow` is the other half: it is *allowed* to rewrite, and that difference is why there are
two keys rather than one compromise. When he wants the commas fixed he wants only the commas fixed;
when he wants the paragraph to read well he is asking for the words to move.

**What it targets is what speech leaves behind.** Dictation says the same thought three times in
three shapes, because the mouth finds the sentence while saying it and the false starts stay in the
transcript. A proofreader will not touch any of that, so the result reads as careless writing, which
is the opposite of true. The instruction is aimed at redundancy and order: say each thing once, put
them in the sequence that follows.

**Register named explicitly** — warm and direct, half friendly and half professional, and *not*
corporate. A model told to "improve" text reaches for the flattened business voice, which strips out
exactly what makes a message recognisably his.

**The line it may not cross: it may cut, join and reorder; it may not add.** Given permission to
rewrite, a model will invent a politeness he did not offer or a commitment he did not make, and a
message that goes out carrying an invented promise is worse than one that rambles.

The spinning dust already shows the running prompt's name, so the two keys are told apart mid-flight
by naming this one `Better flow` against the proofreader's `Proofread` — no new UI needed.

## Volume down's term now has a picker

In Gestures, under Volume keys. Offered as a **list of the terms already taught to the finger**
rather than a text field, because a term typed by hand that matches nothing on the Magic finger
screen is a key that silently does nothing, with nothing on that screen to say why. Includes an
explicit "nothing" option that leaves it a plain volume key.

The old summary there described behaviour that no longer existed — volume up starting on a short
press, volume down swapping views — and has been rewritten to match the code.

---

# 39. Dictation with no text field, from anywhere — SHIPPED, build 123

**Asked: can a long press on volume up start dictation when the keyboard is not on screen — in any
app, with no input box — and keep the result in History?**

**Yes, and the parts nearly all exist. It is not free.**

## Why it does not work today

The volume keys are handled in `FlorisImeService.onKeyDown`, which is guarded by `isInputViewShown`
and, more fundamentally, only receives key events at all while the IME has an input connection. No
text field means no keyboard means no key events. Nothing about the current wiring can reach outside
that.

## What makes it possible

`DictateAccessibilityService` already runs for the magic finger, TAB and the floating button. An
accessibility service that declares `android:canRequestFilterKeyEvents="true"` and adds
`flagRequestFilterKeyEvents` to its config receives `onKeyEvent` **globally** — every app, no
keyboard, including the volume keys.

`accessibility_service_config.xml` currently declares
`flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds|flagInputMethodEditor`. Neither the
flag nor the attribute is there, and `onKeyEvent` is not overridden. That is the whole gap for the
capture half.

## What to build

1. The flag, the attribute, and `onKeyEvent` in the service, with the same press-length rule the IME
   uses so a short press stays volume.
2. A destination when there is no field. `commitOutput` writes through a sink; with nothing to write
   to it must not simply fail. It should land in History and on the clipboard, and say so.
3. **Check History is written even when the commit fails** — `DictateHistoryEntry` has a `failed`
   flag, so the shape is there, but confirm the row is saved before assuming the note is recoverable.
   A dictation that is lost because there was nowhere to put it is the one outcome that must not
   happen.

## Warn him before building it

Granting "observe your keystrokes" is a serious-sounding Android prompt, and the service will need
re-enabling after the config change. Both are worth saying out loud rather than discovering.

Note this is also the honest home for §27's hardware trigger for voice commands: same mechanism, same
`onKeyEvent`, and the two should be built together rather than each growing its own key handling.

---

# 40. How §39 was actually built

`DictateAccessibilityService.onKeyEvent` now sees the volume keys everywhere, behind
`android:canRequestFilterKeyEvents` and `flagRequestFilterKeyEvents`.

**The destination question answered itself.** Dictation runs with `OutputTarget.OVERLAY`, the target
the floating button already uses, so everything needed was built: a focused field still receives the
text, a screen with no field fails the commit and takes the `!committed` branch — which calls
`rememberLastDictation`, `recordHistory` and surfaces Reinsert. **`recordHistory` runs on both the
success and the failure path**, so the note is kept either way. Nothing new had to be written for it.

**Three rules the handler obeys, each load-bearing:**

- **The keyboard wins when it is up.** `FlorisImeService.ownsVolumeKeys()` was added for this. Both
  handlers can hear one press, and without it a hold would start a recording and stop it at once.
- **A short press is always the volume.** The release decides and hands the change back by hand,
  since the press was swallowed. Consuming a volume key and giving nothing back is what would make
  the phone feel broken.
- **Every other key is handed straight back.** The service can see all of them and looks at two.

**It ships off** (`dictate__ma_global_volume_keys`, default false), with the switch in Gestures. This
is the one setting whose failure mode is "the volume buttons stop working", which must not arrive by
update. He turns it on having read what it does.

**This is also §27's hardware trigger.** Voice commands should reuse this `onKeyEvent` rather than
growing a second key handler.

---

# 41. The dot key's comma, and per-spacer width

## Why the dot key showed no comma

Hints are paired **by position** between the letter rows and the symbol rows, in
`LayoutManager.addRowHints`. The bottom row — ctrl, the switchers, space, dot, enter — has no symbol
row lined up with it, so it was never given a hint at all. The comma was reachable only by holding,
while every letter above advertised its second character in the corner.

A key on the board keeping its second meaning secret is worse than none of them showing it.

Fixed by a narrow rule after the pairing loop: a key with no symbol hint, whose popup declares a
CHARACTER `main`, takes that as its hint. The comma moved from `relevant` to `main` in
`charactersMod/default.json` to match.

**Keyed on `main` and not on the first `relevant` entry, deliberately.** The switcher keys carry a
popup listing every view (§25); a hint reading "text" in the corner of `sym1` would be noise. They
declare only `relevant`, so the rule passes them over. **Anything given a `main` popup from now on
will grow a hint — that is the trade.**

## Per-spacer width

Stored in the spacer's own `label`, which is unused on a spacer — there is nothing to write on a gap
— so it costs no change to how targets are serialised or parsed. Blank falls back to
`maSpacerTenths`, which is what every spacer made before this will have.

Minus, the number, plus, on the spacer's own row: no dialog and no text field, because the only
question is wider or narrower and the answer is judged by looking at the row rather than by choosing
a number. Ten tenths is one wand key, the same unit the Feature row screen uses, so the number means
the same on both screens. Steps of two tenths, clamped 1..200.

## Three-state language — still open, and why it was not built here

The button needs `English / Croatian / Auto`, and **Auto has no engine**: §28's Groq detection is
designed and not built, `MaLanguageGuess` is dead code that reads finished text rather than audio,
and a third state that silently resolves to English would give him English words for Croatian speech
— the exact failure that started this thread.

Two honest ways to build it:

1. **After 26.** Auto means what he asked for: the first seconds go to Groq, the answer is clamped by
   the not-English rule plus the free text check, and applied before the request is built.
2. **Before 26, as a stopgap:** Auto means "follow the language the last dictation actually turned
   out to be", using `MaLanguageGuess` on each finished transcription and remembering the verdict.
   Cheap, uses code already written, and honest so long as the button says so.

Do not ship a third state that has neither.

---

# 42. Dictating into a field nobody tapped

When nothing on screen has input focus, `activeWindowEditable` used to return
`findEditableDescendant(root, 0)` — **the first editable node depth-first, bounded to depth 6**. Two
things wrong with that for the case he described: first is whatever the layout declares earliest,
which is a search box at the top far more often than the composer at the bottom; and depth 6 does not
reach a composer inside a modern nested layout at all.

Now it collects every editable node and takes the **lowest on screen**, brings it into view, and
focuses it before writing. The box worth writing into is the one nearest the thumb, and on every
messaging screen ever built that is the last one down the page.

So: open the app, hold volume up, speak, release — the words land in the obvious box without tapping
it first to raise a keyboard he was never going to type on.

## Still open from this batch

- **Setup flow**: all-files access as its own step, restore-from-backup on the keys step, and a
  guided accessibility step that opens App info → ⋮ → Allow restricted settings and then the
  accessibility screen. §8 and §9 hold the earlier notes; this is now the priority of the three.
- **Three-state language + §28.** His design confirmed and correct: **English and Croatian skip Groq
  entirely** — the label is set, the language goes with the request, no detection call at all. Only
  **Auto** sends the first seconds to Groq, and the answer is applied when the request is built. That
  is exactly the seam §28 describes, and it means the cost of detection is paid only by the state
  that asked for it.

---

# 43. The setup flow, rebuilt

Seven steps now: enable IME, select IME, microphone, **all-files access**, keys, **accessibility**,
finish.

## All-files access sits before the keys, and that is the whole point

The previous version leaves a key backup in `Documents/`, and Android will not let the app open a
file it does not own without this grant. Asking for the keys first sent him to a picker to find a
backup the app was not allowed to read — which is exactly how it went.

## Restore, not just import

The keys step now offers **Restore keys from backup** above the file picker, reading
`MaVault.DISPLAY_PATH` directly: no picker, no remembering where the file was put, because the
location is known. It appears **only** when all-files access has been granted and a backup actually
exists, so it is never a button that cannot work.

## The accessibility step is two buttons in an order that looks wrong

A sideloaded app has its accessibility toggle **greyed out** until restricted settings are allowed,
and that lives behind the three-dot menu of App info. So the step sends him to **App info first**,
then to the accessibility list. Going straight to the accessibility list would show him a switch he
cannot move and no reason why.

## Polling, because these grants have no result

There is no launcher contract for all-files access or for the accessibility toggle — nothing comes
back when the user returns. So the wizard re-checks `MaVault.hasFullAccess()` and
`DictateAccessibilityService.isRunning` every 400 ms while it is open, and both are in the
`LaunchedEffect` key list; without that it would sit on a step already completed.

Both new steps can be skipped, and a skip is remembered for the session, so neither can trap someone
whose phone does not offer the setting at all.

---

# 44. The bottom row gives its width to the spacebar

`ctrl`, both switchers and `enter` were each `1.56f` — half again a letter's width. Four wide keys on
one row, and the spacebar took only what was left.

All four are now `1.00f`. **Nothing else had to change**: space carries `flayGrow = 1.0f`, so every
tenth taken from them is handed straight to it. Shift and delete keep `1.56f`, because they are the
two keys hit most often without looking and they have the edge of the row to aim at.

The switcher faces are `sy`, with the digit as a **corner hint**. It has its own branch in
`computeLabelsAndDrawables` rather than coming from a popup, because that digit is not a second
character the key can type — holding `sy` lists the layouts (§25), it does not insert a 1.

---

# 45. Global volume keys: what to check when they do nothing

Reported twice as not working. The code path is: config flag → service re-granted → preference on →
keyboard not in front. **All four are required, and three of them are invisible.**

1. `flagRequestFilterKeyEvents` and `canRequestFilterKeyEvents` — in `accessibility_service_config.xml`
   since build 124.
2. **The service must be re-enabled after that config change.** Android binds a service with the
   capabilities it had when it was switched on; a service left running across the update keeps the
   old ones and never receives `onKeyEvent`. Off and on again in Accessibility settings.
3. `dictate__ma_global_volume_keys` — **ships off**, switch in Gestures → Volume keys.
4. `FlorisImeService.ownsVolumeKeys()` must be false, i.e. the keyboard not on screen. That one is
   correct behaviour and is the intended case anyway.

**Worth building: make the state visible.** The Gestures switch should say whether the service is
actually receiving keys — a line reading "the service is running and receiving keys" versus "the
service needs re-enabling after the last update". Three of the four requirements currently fail
silently and identically, and no amount of explaining in a chat message substitutes for the screen
saying which one is unmet.

---

# 46. Build 124 broke the accessibility service. Reverted at 131.

**`android:canRequestFilterKeyEvents="true"` made the whole service unusable on a Nothing Phone 2a
running Android 16.** Not degraded — unusable: the accessibility toggle could not be turned on at
all, so the magic finger, TAB, the floating button and overlay dictation all died with it.

**Why.** Key filtering is the capability a keylogger needs. A sideloaded app that declares it is put
behind Android's "restricted settings" gate, and on that phone the App info overflow menu offering
"Allow restricted settings" **did not appear at all** — so there was no way through. §39 assumed the
cost of asking was a scarier warning. The real cost was the entire service, including every part
that had nothing to do with keys.

**Reverted.** The config is byte-identical to build 123. `onKeyEvent` and the global-volume-key code
remain but can never fire, which is harmless and keeps the work for when it can be tested.

**Do not re-declare that attribute without a way to verify on his phone first.** The next attempt
should be a separate debug APK, not the one he uses all day.

**This is also why §39 must be reconsidered rather than retried.** Dictating with no keyboard needs
another route entirely — the floating button and the OVERLAY sink already work without key filtering,
and that is the direction.

## Two shortcuts on the settings header

The build-number line now opens **App info** rather than the About page: it is the one line naming
the install, so it is where the hand goes when the install itself needs changing. Beside it,
**Accessibility** opens that settings screen directly. Everything built on the service dies together
when it is off, and Android switches it off often enough to deserve a door rather than directions.

## Still wanted: the log screen

He asked for a log with timestamps he can paste back. Nothing in the app records why the service is
not running, so every diagnosis so far has been guesswork across a chat window. **This is the next
thing worth building** — it is what would have found §46 in one step instead of three builds.

---

# 47. Rolled back: the volume keys act on press again, and the global handler is gone

**Volume up starts and stops the recording the moment it goes down**, as before build 121. The hold
was introduced so a short press could still be the volume while bhajan played. It solved that and
cost more than it saved: every dictation, dozens a day, began by waiting half a second for a key
that used to answer at once. **The volume is the rarer need while the keyboard is up, and it has an
easy answer — put the keyboard away and the keys are ordinary volume keys again.**

Volume down presses the magic finger term on the way down too, same reasoning.

Repeats are swallowed: a held key repeats, and a repeat would start a recording and stop it while
the finger is still down.

**The global handler is removed entirely** — `onKeyEvent`, `finishGlobalVolume`, both timestamps, the
hold constant, `dictate__ma_global_volume_keys` and its switch. It could not fire since §46 reverted
the capability, and a feature that cannot work is worse kept than deleted. §39 and §45 are history
now, not plans.

**Together, §46 and §47 undo builds 121 through 130's volume work.** What is left is what worked
before it: the keys are the app's while the keyboard is up, and the phone's the rest of the time.

## Not built, and asked for twice

**The log screen.** ~~Asked for repeatedly and missing~~ — **shipped, build 136. See §49.** Nothing in the
app records why the accessibility service is not running, which is why §46 took three builds and a
broken app to find. It should hold a timestamped list he can copy in one tap. **Build this before
anything else.**

---

# 48. The dot key has two functions again

Build 126 gave the dot its comma by setting a **symbol hint**, which was the wrong mechanism. A
symbol hint does not merely draw a glyph in the corner: `addComputedHints` also merges the hinted
key's own popup set into this key's. The dot ended up carrying a third thing. He asked for two
functions and got three.

Now:

- The layout gives the dot `popup.relevant = [comma]` and nothing else. **Tap is a dot, hold is a
  comma, and there is no third meaning to find.**
- The corner glyph is set directly in `computeLabelsAndDrawables`, beside the switchers' branch,
  because it is a label and nothing more.
- The §41 rule in `LayoutManager` that derived hints from `popup.main` is **removed**. It was the
  cause, and it existed to serve this one key.

**The lesson: a symbol hint is a behaviour, not a decoration.** To draw something in a corner, write
the label. To give a key a second character, give it a popup. Do not use one to get the other.

## Key widths, settled

Shift, delete, **ctrl and enter** are all `1.56f`, so the four corners of the two bottom rows match
and the block reads as one shape. Only the **switchers** stay at `1.00f` — they were merely wide,
not hit without looking, and their width is what the spacebar gained.

## The spacebar mark is drawn at 1.8×

`SnyggText` gained `fontSizeScale`, which **multiplies** the themed size rather than replacing it, so
the font scale, the theme and the user's size multiplier all still apply underneath. U+23B5 is a low
flat bracket about a third the height of a capital, so at letter size it read as a speck on the
widest key of the board.

---

# 48. The dot key, pinned to two meanings

It had three. A tap gave `.`, a second gave `…`, and the comma it was supposed to carry was buried in
a list — the wrong one on top and the wanted one hardest to reach.

The layout declares only a comma. The extra entries arrive from the **popup mappings merged in
`TextKey.compute`**, which run after the layout's own popup and add to it. Chasing which mapping
supplies them was the wrong fix: any future mapping could add more.

So the popup is now **cleared and rebuilt** for code 46, after every merge: main is the comma, and
nothing else. The corner label is set in the same file by the same code rather than derived from the
popup through §41's rule, which did not survive the merges on this key. **One place decides what the
dot key means and what it shows, and they cannot drift apart.**

## Bottom row, settled

Ctrl and enter return to `1.56f`, matching shift and delete, so the four corners of the two bottom
rows are one shape and the columns line up. **Only the switchers stay at `1.00f`** — they were the
keys genuinely too wide for what they say, and their width is what the spacebar gained.

The space mark goes from `1.8f` to `2.6f`. It is squat rather than small: a scale that would be huge
on a letter is modest on a glyph with no ascender, and 2.6 is about the height of a capital.

## The accessibility service is still not enabling, and I am out of ways to guess

The capability was reverted at 131 and the config is byte-identical to 123, which was working. If the
toggle is still refused, **the cause is no longer in this repo's diff** — it is the phone's state:
Android may have remembered the app as restricted from when 124–130 were installed.

Worth trying, in order: uninstall and reinstall (this clears the restricted flag), or Settings →
Apps → TTT → Force stop, then enable.

**And this is exactly why the log screen must come first.** Every diagnosis in this thread has been
inference from a chat message. Nothing in the app records whether the service ever started.

---

# 49. The log — shipped, build 136

Asked for four times before it was built, and he was right every time. Every diagnosis in this
project has been inference from a chat message; §46 took three builds and a broken app precisely
because nothing recorded whether the accessibility service had ever started.

**`log` sits in the settings header**, left of `access`. In the header rather than in the settings
list because that list is ordered by a stored preference — a new default entry would appear at the
*bottom* for anyone who has rearranged it, which is him, on every install.

## Shape

- **Writes to a file** in `filesDir`, not just memory. The interesting failures are the ones where
  something does not start, and the process dies and restarts constantly; an in-memory list would be
  empty at exactly the moment it was wanted.
- **400 lines**, then trimmed in batches of 100 so trimming does not cost a rewrite per line. Small
  enough to paste into a chat in one go — a log nobody can paste is a log nobody reads.
- **Never throws.** Every write is inside `runCatching`. A log that could break the accessibility
  service would produce exactly the silence it exists to end.
- **Newest first on screen**, oldest first in the file. Appending is what makes writing cheap;
  reading reversed is what makes the last failure the first thing seen.

## What it records

`app` start with build number, Android level and device model — so a paste identifies the phone
without asking. `a11y` service connected, **with flags and capabilities in hex**: a service can
connect with fewer capabilities than it asked for, which is silent and looks exactly like an app
bug. Also unbind and destroy, so "never started" reads differently from "started and died".
`dictate` commits, by **length not content** — his transcriptions are his writing, and the log is
meant to be pasted. `keys` the volume path, including whether the service was up and what the finger
found.

**Add a line whenever a failure would otherwise be silent.** That is the rule this screen is for.

---

# 50. The magic finger defaults are his row now

Was: `Copy message`, `Stop responding`, `Send`, `Use Image URL`, `Add URL`, `Generate Images`. Six
terms guessed at what a button-pressing keyboard ought to offer.

**He kept one.** The other five were keys he deleted on every reinstall, and he reinstalls
constantly, so the list cost him work each time and bought nothing.

Now: a **spacer 5.4 keys wide**, then **send**. The spacer is not decoration — it pushes send to the
right-hand end of the row, under the thumb that holds the phone, which is the entire reason the
spacer feature exists. Shipping the row without it would ship the feature without its point.

**`DEFAULTS_VERSION` deliberately stays at 1.** Bumping it would run `mergeNewDefaults` against
lists that already exist and append a second spacer to his. Defaults are for fresh installs here;
that flag is for genuinely new terms.

## The feature row defaults are NOT changed, and here is what is needed

He asked for these too, and only Row 1 was visible in the screenshot: **History, Settings, Dictation
view**. Rows 2 and 3 were not shown.

The feature row's default is a single flat list in `MaFeatureOrder.DEFAULT`, split into rows by
`MaRows` — so setting Row 1 alone means guessing the other two, and a wrong guess is worse than the
current default because it looks deliberate. **Ask for screenshots of Row 2 and Row 3, then set all
three together.**

The spacer width on that screen already defaults to 10, which matches what he has.

---

# 51. Ctrl+P and Ctrl+F stop typing the model's chatter into his messages

Two failures, both from his screenshots, both now guarded.

**Empty input reached the model.** It replied "I don't see any text to edit in your message", and
that sentence was written into the field he was typing in. The whole-field branch already refused a
blank field; the selection branch did not, and a selection can be reported present and come back
empty when the field is one the accessibility path cannot read. Now both routes end the same way:
say so, spend nothing, leave the field alone.

**The model answered instead of obeying.** "I've reviewed the text you provided. It appears to
contain only the phrase…" — pasted into a message to another person. The instruction forbids
preamble; the model ignored it. **No amount of rewording the prompt makes that impossible**, so the
guard is on the output rather than the input.

**The tell is length.** Proofreading and reflowing return roughly what they were given — shorter, or
a little different, never several times longer. Commentary about a short phrase is always far longer
than the phrase. Over `3 × input + 80` characters, the reply is refused, shown as an error with the
first 300 characters as detail, and **his text is left exactly as it was**. Deliberately generous: a
real correction never comes close, and guessing wrong this way costs one visible message rather than
a ruined one already sent.

## The status row at the bottom is still not done, and it is now the oldest debt

Asked for repeatedly since §12. It is a `Column` reorder in `TextInputLayout`: `Smartbar()` is
composed first, above the edit row, the number row, the keys and the feature row. Moving it after
the feature-row `Box` puts it where the pin already is.

It was not done here because the Ctrl+P failure was writing AI commentary into messages he sends to
people, and that had to stop first. **Do this next, before item 26.** The branch to be careful about
is the `isActionsOverflowVisible` / `GifSearchPanel` split at the top of the same `Column`, which
also composes a fixed-height `Box` and must keep doing so.

---

# 52. The status line moved to the bottom, and the automatic bucket

## The status line is under the keys now

`Smartbar()` was the first thing in `TextInputLayout`'s `Column`, directly above the keys — which
put the recorder, the errors and the word suggestions in the band of screen immediately below what
he was writing, covering the line he was reading. A keyboard takes the bottom of the screen and
gives back the top; a status line at the top of the keyboard took a second bite.

It is now composed **last**, below the feature rows and beside the pin, at the edge the eye already
goes to for the navigation buttons. Same component, so suggestions, the recording bar and the error
line all followed it down without any of them knowing. The `GifSearchPanel` branch still composes its
fixed-height box, since both sides of that `if` are measured the same way.

## The automatic bucket

`MaFeatureKey.AUTO_BUCKET`. Press once: the **last code block on screen** is copied. Press again:
the one above it. Again: the one above that.

**It only presses `copy code`.** Code blocks announce that name; the copy button under a whole
answer announces `copy message`. Confirmed from a real node dump (§37). So a chat full of both gives
up only its code.

**It does not touch the buckets.** `ClipboardManager.captureIntoClipSlots` already files every copy
into the next free slot and stops when they are full. This key only presses the right button in the
right order — which is why the feature is small at all.

**`MaScreenTargets.findIn` now takes a rank.** It sorts matches lowest-first instead of taking a
maximum, so "the next one up" becomes a question it can answer. Rank 0 is the bottom-most, which is
what every other caller wants and what `pressFirstMatch` still asks for.

**The face carries the count** — `A1`, `A2`, `A3` — because the count is the only thing about this
key that cannot be seen by looking at the screen. **Long press resets to A1**; without it one
mistaken press leaves the counter pointing at last month with no way back down the page.

The rank only advances on a press that landed, so running off the top does not keep climbing while
nothing happens.

`maBucketRank` is file-level Compose state, not `remember` and not a preference: collecting blocks
means leaving the keyboard to look at what was copied, so it must survive a close — but a rank
restored from last week would point at nothing.

---

# 53. Rolled back: the status line returns to the top, and the pin becomes a key

## The status line is above the keys again

Build 139 moved it to the bottom so nothing it showed could cover the text being written. **That
reasoning was sound and the result was still wrong.** At the bottom it sits below the feature rows,
past where his eye goes, and he reads it by hunting for it rather than by noticing it. Habit beat
the argument — which is the right way round for something read a hundred times a day.

**The lesson worth keeping: a correct argument about layout can still lose to where somebody's eye
already goes.** Ship the reasoning, but believe the person using it.

## The pin's strip is gone; the pin is a feature key

It held **half a key row, permanently**, for a switch set once and then forgotten. `MaFeatureKey.PIN`
now does the same job as an ordinary key that can be placed, moved or removed like any other.

The old strip existed on the argument that a pin removable from the row cannot be reached to put
back. True, and survivable: the same preference is in Settings, so unticking the key is inconvenient
rather than a locked door. **He was told the trade and chose it** — the same shape of decision as
`MIC` leaving `ALWAYS_ON`.

Filled when pinned, outlined when not — the pair the clipboard panel uses — and lit in sand while it
holds, like the row's other sticky key.

**`MaKeyboardPin.kt` is now referenced from nowhere.** Left in place rather than deleted at the end
of a build; delete it in a pass of its own, after confirming nothing else wants it.

---

# 54. Why it suggested "things" while he was typing "other"

**They were not wrong answers. The wrong question was being asked.**

`currentWordText` comes from the editor's composing region, and a great many apps never set one — a
web view, a dialog, anything drawing its own input. In those the prefix arrived **empty**, and an
empty prefix means something completely different downstream: `MaNgramModel.predict` stops filtering
by prefix and the bigram tier answers "what word follows `other`". Hence `things`, `parts`.

Now `MaNgram.predict` reads the word off the end of the text when the editor will not supply it —
the trailing run of letters before the cursor, apostrophes and hyphens kept so `don't` and
`well-known` survive. **And the context loses that word too**, or `contextOf` would count the
half-typed word as the previous one and predict what comes after `other` while he is still writing
it.

Which gives exactly the two behaviours he described, from one change: mid-word it completes the word;
after a space the trailing run is empty, the prefix is empty, and the bigram tier answers what comes
next. Tested against eight cases including the screenshot, `don't`, `well-known` and Croatian.

## On putting a model behind this

He asked again for AI. **§33 still stands and should be read before starting**: the only verifiable
candidate was 81 MB, English-only — the language that already had a dictionary — and close enough to
`MaNeuralPredictor`'s own 60 ms budget to disable itself. It would not have fixed this bug either;
the prefix was being dropped before any predictor saw it.

**What would actually raise the ceiling now, in order of value per effort:**

1. **Learn from a longer history.** `MaNgram` learns only from what is committed through this
   keyboard. Every transcription in `DictateHistory` is his own text and is already stored — feeding
   it in at first run would give the personal model months of his vocabulary immediately.
2. **An English base dictionary**, the counterpart of §33's Croatian one. Upstream's English list
   only serves the suggestion strip via its own provider, not `MaNgram`.
3. **A small multilingual model**, and only after 1 and 2 have been tried and measured. An
   English-only model remains the impressive answer to a question this app does not have.

---

# 55. The model learns from his history, the suggestion row can appear, and the spacebar stops doubling

## The word model reads the dictation history, once

`MaNgram` only ever learned from words committed **through this keyboard**, so a fresh install knew
nothing and had to be taught his vocabulary again by hand, over weeks — while every dictation he had
ever made sat in the history database. Thousands of his words, his phrasing, his subjects.

**That is a better corpus for predicting his writing than any general model**, because it is not a
sample of how people write, it is a record of how *he* writes.

`MaNgram.backfillFromHistory` runs after `initialize` (so it adds to the loaded model rather than
racing the load), takes the newest `BACKFILL_MAX = 2000` entries, skips anything over
`MAX_LEARN_LENGTH`, and marks `dictate__ma_ngram_backfilled`. **A flag, not an is-the-model-empty
check**: a model that has learned a little is still worth backfilling, and a second pass would double
every count. It logs how many it learned and the resulting word total.

## The suggestion row was structurally unable to appear

`MaBucketStrip` and `CandidatesRow` share one slot, and the bucket legend won **whenever any bucket
held text**. A bucket keeps its text until it is replaced — so for anybody who actually uses the
buckets, the legend held the strip permanently and the word suggestions could never be seen.

Reversed: candidates take the slot whenever there are any, the legend has it otherwise. Candidates
exist only while a word is being typed, which is exactly when they are wanted and exactly when nobody
is reading the legend. Patched in **both** smartbar layouts — the block appears twice.

## Two space marks on the numeric keyboard

`computeIcon` drew `Icons.Default.SpaceBar` on the numeric and phone layouts, from when
`computeLabel` returned null for space and the bar would have been blank. Since build 121 **the label
is the mark, on every mode** — so on those four layouts both fired, at two different sizes. The icon
branch is gone; the label is the one that stays, since it is the same glyph everywhere and scales
with the key face.

---

# 56. Why send worked in Claude and not in Gemini

**Not case, and not a missing label.** Both apps carry `contentDescription="Send"` on the button,
capital S. His two dumps made the real cause visible.

`matchOf` builds its label from description **plus text plus view id**. In Gemini the send button is
wrapped in a `ComposeView` whose id reads `assistant_robin_input_send_button_compose` — no name of
its own, but the id contains the token `send`, so **both nodes matched**. The wrapper's box is 26px
taller than the button's, and bottom-most wins, so the wrapper was chosen. Its nearest clickable
ancestor is `assistant_robin_chat_input_half_sheet` — the whole input sheet. The finger tapped the
text field, focus moved, and nothing was sent.

Claude has no such wrapper, so its only match was the button. That is the entire difference, and it
is why "try both commands" would not have helped: the term was right both times.

**Fixed by ranking, not by matching.** `isAnnounced` asks whether the match came from a name the app
*announces* — a description or a text — rather than from a view id. Announced names sort first;
position only decides between equals. A description is what a person means by a button's name; an id
is a programmer's spelling that leaked into the tree.

Verified against both dumps before building: Gemini now picks the button rather than the wrapper,
Claude is unchanged.

## The bucket red, removed (build 144)

Asking was right: he wanted **no behaviour change at all**. Buckets fill 1, 2, 3 up to the last
visible one and then stop accepting, exactly as before. The bin key empties them and they fill again
from the first.

**The red was the whole complaint.** Full is not a fault — it is the ordinary end of filling them —
and red on a keyboard reads as something being wrong, so every time he used the feature as intended
it sent him looking for a problem that did not exist.

Three places drew it, all now gone: the number in `MaBucketStrip`, the C-key labels, and the bin key
that turned red "so the row shows both the problem and its answer". `bucketsFull` and `MaRecordRed`
were left with no readers and were removed with them. `MaClipCapture.isFull` still governs whether a
copy is accepted — the state is unchanged, only its colour.

**The rule worth keeping: red is for a fault, and a full container is not one.**

---

# 57. Send has one definition and two triggers

The send key on the magic finger row and a long press on volume down are **the same press made two
ways**. They were nearly able to disagree: volume down held its own copy of the word in
`dictate__ma_volume_down_term` and pressed that directly, so renaming the term on the Magic finger
screen — or teaching send a longer name because some app announces it differently — would have fixed
the key on the row and left the hardware button pressing a word that no longer existed. Silently,
and only in the app where it mattered.

`MaMagicTargets.resolveTerm(targets, name)` is now the single source: the stored name is looked up
among the taught terms, by face or by term, and the target's own term comes back. Falls back to the
name as given, so a hand-typed term still works and a list that has not loaded cannot make the
button dead. The log records both — `volume down: 'Send' -> 'Send'` — so a mismatch is visible
rather than inferred.

**Written into HANDOFF as a standing rule**, because he asked for it to survive the session: any
future way of firing send joins the same way. One list, one term, no copies.

---

# 58. The automatic bucket and the bin are one mechanism

**A-bucket shipped in build 139** (§52) and he asked for it again, which usually means it was not
findable. Two things fixed rather than rebuilt:

**The bin now resets the ladder.** They are halves of one thing: A-bucket presses copy buttons and
the clipboard capture files each copy into the next free slot. Clearing the slots without clearing
`maBucketRank` left the two disagreeing — buckets ready for the newest block, the ladder still
pointing eight blocks up the page — so the next press collected something from far above and dropped
it into bucket one. The bin is the reset for both, which is what he expected, and it saves a second
control nobody would remember. Long press on A-bucket still resets it alone.

**It sits beside the bin in the default order**, not off among the unrelated keys. He asked for it in
the same part of the row as the buckets; the bin is where the eye already goes for them.

Behaviour is otherwise as built: press for the last code block, press again for the one above, `A1`
`A2` `A3` on the face showing which is next, `copy code` only so message-copy buttons are never
touched.

**The limit remains Android's** (§37): recycled list rows are absent from the tree, not hidden in it,
so one run collects the blocks near the viewport rather than a whole conversation. A finger that
scrolls and re-searches is what would lift that, and it is still unbuilt.

---

# 59. A-bucket scrolls the ladder along

After a copy lands, A-bucket now **reveals the next block up** —
`MaScreenTargets.revealMatch`, which is `findIn` with `clickIt = false`: it finds and scrolls into
view, and does not press.

**Two things at once, and they are the same action.**

The list scrolls to show the block about to be collected, which pushes the one just collected down
toward the bottom of the screen. So the last thing copied is the last thing visible, and he can read
down the page to check what went into the buckets instead of counting presses. That was his idea and
it is a better confirmation than any indicator on the key.

And it is what makes a long conversation reachable at all. His ten-box dump proved the limit exactly:
seven boxes present (two of them `hidden` but complete), one caught mid-recycle with its container
but no button, and **three absent from the tree entirely**. Rows far from the viewport do not exist
until something scrolls near them. Revealing the next block is what causes the ones above it to be
built — so the ladder keeps climbing instead of stopping at whatever was in memory when he started.

**Also confirmed from that dump:** each Claude code box carries exactly one copy button,
`desc="Copy code"` at the top right, with `Expand code` beside it. The fenced-backtick copy he wants
to avoid is `Copy message`, under the whole answer, and A-bucket has never targeted it.

---

# 60. The top strip always has something to say

The strip is meant to follow what he is doing, and one common case said nothing at all: a plain copy.
Now it shows `copied` and the clipboard text when no bucket holds anything.

**The order it resolves in, highest first:**

1. **Recording** — the recorder bar, in `DictateSmartbarUi`.
2. **Typing** — word suggestions. They exist only while a word is in progress (§55), which is exactly
   when they are wanted.
3. **Buckets** — the legend, number and contents per slot. Something he put there deliberately.
4. **Clipboard** — the last thing copied. Merely the last thing that happened, so it sits below a
   bucket, and it answers the question he would otherwise open the clipboard panel for.
5. Nothing.

**`maBucketStripHasContent` had to learn about the clipboard too.** The strip computes what to draw,
but the caller decides whether it gets the slot at all — so a clipboard line computed inside a strip
that is never composed would never have appeared. Two places, one rule; they are asked in the same
order.

`primaryClipFlow` is a `StateFlow`, so it needs Compose's `collectAsState` while the preferences
around it use JetPref's. Imported under an alias, the way `DictateHistoryScreen` already does it.

---

# 61. The dictation view can be pinned as the opening view

The gear is gone from that screen and a **pin** takes the corner.

**Settings did not deserve the corner.** It was there on the reasoning that it is opened rarely and
deliberately — which is the argument for it *not* having a key on the screen he uses most. It is
already on the feature row and in the app; a third route was worth less than the corner it occupied.

**`"dictation"` is restored to `maOpeningView`.** It was removed earlier on two arguments. One was
wrong: the view shows a mic key, it does not press it, and recording on open is a separate setting.
The other — that this screen is always secondary — was mine to assume and his to decide. **He
dictates far more than he types**, so for him the keyboard is the secondary screen, and until now
every open began on the wrong one.

The pin is the **only** thing that sets this preference, so it can always be unset from the same
corner it was set in. That reversibility is what the earlier removal was really protecting against,
and it is cheaper than removing the option.

Filled when pinned, outlined when not, as everywhere else in this app.

---

# 62. The cursor trackpad, and the road to flicking

Hold the spacebar and the keyboard becomes a pad: drag anywhere to move the caret. Lift to leave.

**It replaces the language picker on that long press.** Choosing a language is rare and deliberate
and has two other routes — the badge and a long press on volume down. Moving a caret through
dictated text is constant, and doing it by tapping at the text is the least accurate gesture on a
phone, because **the finger covers exactly the character being aimed at**.

`gestures__space_bar_long_press` is now ignored rather than read. Everything its old values could
choose is reachable elsewhere, so there was nothing to lose by not asking.

**Shape.** The pad fills the keyboard and is drawn last in the `Box`, so it takes the touches and
nothing underneath can be pressed — that is what makes it a mode rather than a hint. Only the
keyboard, so the text stays visible above and he can watch the caret move.

Horizontal drag steps by character (28px), vertical by line (56px). **Distance accumulates** and is
spent in whole steps: a threshold that reset each event would ignore a slow, careful finger, which is
exactly the finger this is for. Arrows go through `inputEventDispatcher`, the same pipe every key
uses, so the editor handles them by its own rules.

Lifting closes it. No confirm, no cancel — the caret moved with the drag, so what he sees on lifting
is what he gets, and a mode needing dismissal is a mode left open by accident.

## Next: flicking

He described the direction this opens. Instead of holding a key and waiting for a popup, **swipe
towards the second symbol printed on it** and get that symbol. Same information already on every key
face (§41 put the comma there, §48 pinned it), no wait, no popup.

The parts are in place: `TextKeyboardLayoutController` already tracks pointer movement per key for
the glide typing and delete-swipe paths, and every key already knows its hint. What it needs is a
direction test on release and a decision about how far a flick must travel before it stops being a
tap. **Build it after living with the pad**, since both change what a finger on a key means.

---

# 63. Configurable transcription view — designed, NOT built

**What he asked for:** every button on the transcription view except the pin can be swapped for any
feature-row key. Settings shows two rows — the original and the replacement — with an edit picker and
a reset.

**Why it was not built in one pass, measured rather than guessed:** the feature-row key rendering is
a single `when (button)` block of **556 lines** (`MaFeatureRow.kt` 427-983) closing over **18
locals** — scope, context, prefs, editorInstance, keyboardManager, bucket slots, prompt state,
language state and the rest. Nothing outside that composable can draw a feature key today.

## Three builds, in this order

**1. Extract the renderer.** One composable that draws any `MaFeatureKey`, given what it needs.
Behaviour-neutral: the feature row must look and act exactly as before, which is also how it is
verified. **This is the whole risk of the feature** — a rushed extraction does not fail red, it
quietly drops a behaviour in one of thirty branches and neither of us notices for days. Do it alone,
with nothing else in the build.

**2. The override.** A preference mapping slot id → `MaFeatureKey` id, empty meaning default. Slot
ids for the eleven swappable positions; **the pin has no slot and cannot be overridden**, since it is
the only way back out of a pinned opening view (§61).

**3. The settings screen.** Both rows visible at once, original above replacement, so a swap is read
as a substitution rather than as a list of unrelated keys. Reset clears all overrides.

## Shipped defaults, unchanged by this

The view he screenshotted is the intended default: `ENG ab AB Ab AbAb` / `db mic backspace` /
`pin keyboard space enter`. Overrides sit on top; reset returns to exactly this.

---

# 64. The cursor pad trapped him. Three ways out now, and it works in both views.

**Build 150 shipped a mode with no exit.** The pad closed only on the *end of a drag*. A finger that
pressed and lifted without moving did nothing, so the pad stayed over the keyboard and the app was
unusable until it was force stopped. He tapped it repeatedly — the one thing anybody tries — and
nothing happened.

**The lesson, and it is the important part: a mode with one way in needs a way out that works when
the first thing tried is doing nothing.** A gesture handler that only reports movement cannot be the
only exit, because the natural response to being stuck is to stop moving and tap.

Three exits now:

1. **Tap anywhere** — `detectTapGestures`, declared before the drag handler so a tap is heard even
   when the drag detector sees nothing worth reporting. The label says so: *tap to close*.
2. **Lift after a drag** — as before.
3. **Any new field** — `MaCursorPad.close()` in `onStartInputView`, unconditional. Even if every
   gesture failed, switching app or tapping another box returns a working keyboard.

**Vertical**: `STEP_Y` was 56px, half a key, so a natural upward drag ended before the first step was
spent. Lowered to 34. The arrow codes were fine — `KeyboardManager` sends `KEYCODE_DPAD_UP/DOWN` and
they are repeatable — so this was distance, not dispatch.

**It works in the transcription view now.** That view has no letter keys, so moving through a long
transcription meant tapping at the text, which is exactly what the pad replaces, on the screen where
the text is longest. Same hold, same pad, both keyboards.

**Rule: draw the pad wherever it can be opened.** A long press that raises a pad the screen never
renders is this same trap in a place with fewer ways out.

## Asked for and not built: SwiftKey-style prediction

Two distinct things, both real, neither small:

**Middle-weighted suggestions.** The likeliest word in the centre, larger, with weaker candidates
smaller on each side. This is presentation over the existing ranking and is the cheaper half —
`CandidatesRow` already receives an ordered list; it would draw index 0 centre-large rather than
left-first.

**Learning whole strings — emails, names, addresses.** Type `m` and get the whole address. This is
not the n-gram: it is a store of complete tokens seen often, keyed by prefix, ranked by frequency and
recency. §55's history backfill is the corpus for it, and every email he has ever dictated is already
in `DictateHistory`.

**Do not install SwiftKey to copy it.** The behaviour is describable without it, which is what he did.

---

# 65. The trackpad: stays open, and can select and delete

**It stays open now.** It used to close whenever the finger lifted, which meant one journey per hold
— to move, then select, then delete, he had to raise it three times. **A trackpad that shuts every
time the hand leaves it is not a trackpad, it is a long gesture.**

That is safe in a way build 150 was not, because there is now a **visible** way out. The trap was
never that it stayed open; it was that nothing on screen said how to leave.

**Three corners, and the fourth deliberately empty:**

- **Top right — keyboard icon**, closes. His choice over an X, and he was right: the icon already
  means "back to the keys" everywhere else in this app.
- **Bottom left — shift**, toggles selection. Lit in sand while it holds, because it changes what
  the next drag does and nothing else would say so.
- **Bottom right — backspace**, deletes.
- **Top left — nothing.** It is where a thumb crosses the pad on its way anywhere, and a key there
  would be pressed by accident more often than on purpose.

Keys rather than gestures: the pad is already one gesture, and a second and third layered on the
same finger would make every drag a guess about which was meant.

**Selection reuses the editor's own manual-selection mode.** `KeyboardManager` already extends the
selection on an arrow whenever `activeState.isManualSelectionMode` is set — so turning that on makes
every arrow the pad sends select instead of move, with no special cases and identical behaviour to
holding shift.

**Both exits clear it.** Closing the pad and opening any new field both unset the flag. Left set, the
next arrow from any key would extend a selection long after the pad was gone, with nothing on screen
to explain it.

---

# 66. The pad was drawn on top but did not receive touches

He pressed the close key and nothing happened, while his presses landed on the **keys hidden
underneath** — typing blind through a window he could not shut.

**Cause: `pointerInteropFilter` on the parent.** `TextKeyboardLayout` puts that filter on the
`BoxWithConstraints` that contains both the keys and the pad. It forwarded every touch to the key
controller and returned `true`, consuming it. A parent interop filter runs **before** its children,
so the pad's corner buttons never saw a click at all.

**Being drawn last is not the same as receiving touches.** Build 153 assumed z-order was enough. It
governs painting; the interop filter governs delivery, and they are decided in opposite directions.

Two changes:

- The filter **returns false while `MaCursorPad.active`**, handing events to the children instead.
- The pad **absorbs stray taps** with a `detectTapGestures` that does nothing. A press without
  movement is not a drag, so without this it fell through to the keys. Deliberately no action: the
  middle of the pad is for dragging, and a pad that vanished on a stray tap would be build 150's
  problem inverted.

`LegacyDictateLayout` has no parent interop filter — its gestures are per-key modifiers — so the pad
already worked there.

---

# 67. Every spacebar already opens the pad — and now says so in the log

He reported the hold working only on the letters keyboard. Traced rather than rebuilt, and **all four
routes were already wired**:

- **Typing and numeric/phone keyboards** — one `onLongPress` branch in `TextKeyboardLayout` covers
  every mode it draws, and all four numeric/phone layouts do carry a space key (verified in the
  layout JSON, `western_arabic.json` and `telpad.json`).
- **Transcription view** — `LegacyEditAction.SPACE` got `onLongClick` in build 152, and the overlay
  is drawn in that view's outer `Box`.
- **Feature row** — its space key *is* `LegacyEditAction.SPACE`, rendered by `LegacyActionKey`, so it
  inherited the same long press.

**What was almost certainly wrong was build 154's fault, not the wiring.** Until then the parent
interop filter ate every touch, so the pad opened and could not be used or dismissed — which reads
from the outside as "nothing happened". He was testing 153 or earlier.

**`MaCursorPad.open()` now writes `pad opened` to the log.** Every spacebar in the app routes through
that one function, so a hold that writes nothing never called it, and a hold that writes a line but
shows nothing is a drawing fault instead. Two very different problems that look identical from the
outside — and this is exactly the class of question the log was built for (§49).

---

# 68. A suggestion replaces the whole word, and the best guess sits in the middle

## The fragment bug

Tap into the middle of a word, tap a suggestion, and you got three fragments: half the old word, the
whole new one, the rest of the old word. With no composing region there is nothing marking which word
was meant, so `commitCompletion` fell back to inserting at the cursor — and the cursor is wherever
the finger last landed.

`EditorInstance.commitCompletion` now **selects the whole word around a collapsed cursor** before
committing, so the commit replaces a selection instead of inserting at a point. Apostrophes and
hyphens count as part of the word. **A selection he made by hand is left alone** — that is a
deliberate statement about what to replace, and widening it would overrule him.

## Middle-weighted candidates

The likeliest word is now **centre**, not left. Reading order puts the best guess where the eye
starts, which sounds right and is not: the thumb rests under the centre of the row, so the word most
often wanted was the furthest from the thumb and every acceptance cost a reach.

Only when there are exactly three. With two there is no middle; with four or more the centre stops
being one obvious place.

**This introduced a wrong-word bug that had to be fixed in the same pass.** The click handler read
`candidates[n]` — the *original* list at the *displayed* position. Harmless while those were the same
list, wrong the moment the reorder made position 0 hold candidate 1: tapping the left word would have
committed the middle one. The loop variable is now captured by value. **A reorder of a displayed list
means auditing every index that reaches back into the original.**

**Still to do: the middle one drawn larger.** Sizing lives in the Snygg stylesheet for
`SmartbarCandidateWord`, so it needs a style variant rather than a font size hardcoded here — which
would ignore his theme.

---

# 69. Step one of the modular rebuild: keys become modules

**The philosophy, in his words: Lego, not a moulded shell.** The same blocks make a castle or an
aeroplane because a block does not know what it is part of. Every key in this app was written inside
the row it belonged to, so a key on a different surface meant building it twice.

**`MaKeyModules.kt` holds the first three:** `MaPinKey`, `MaCaseKey`, `MaNextFieldKey`. Each takes
what it needs as arguments and nothing else, and can be drawn by the feature row, the transcription
view, a future row, or a test. `MaFeatureRow` now names the key and says nothing about what the key
is — 1242 lines to 1180.

## The contract for every key still to move

A module may take a `Context`, an `EditorInstance`, a `KeyboardManager` or a preference — things that
exist everywhere. **It may not take the feature row's own state.** Where a key seems to need that,
the state belongs somewhere shared, and moving it there is part of the job rather than a reason to
leave the key behind.

`MaNextFieldKey` is the worked example: reading the keyboard's shift state is the *row's* business,
because the row sits under a keyboard. The key only needs to know which way to walk, so it is told —
and the same module works on a surface that has no shift at all.

## What had to open up, and it was small

`ThemedTextKey` was private and is now `internal`; `maOpenAccessibilitySettings` likewise.
`ThemedIconKey` was already internal, which is why the icon keys were the easy half. **A key that can
only be drawn inside one file is a key that can only live in one row** — that visibility was the
whole cage.

## Order for the rest

Move the keys that depend on least, one or two per build, each behaviour-neutral. The hard ones —
the buckets, the magic finger row, the mic — depend on row state and should come last, after the
easy moves have shown where shared state actually needs to live. **Do not rewrite the row.** He uses
it every day, and the row keeps working throughout by construction.

Once enough keys are modules, §63 becomes small: the transcription view is just another surface that
names keys.

---

# 70. The likeliest word is now drawn larger

Finishes what §68 left half done. The centre word — the best guess — is drawn at `fontSizeScale =
1.2f`, its neighbours at the theme's own size.

**A scale, not a size.** `SnyggText.fontSizeScale` multiplies whatever the stylesheet decided, so a
larger or smaller keyboard font stays larger or smaller and the emphasis stays a fifth bigger than
its neighbours either way. A hardcoded size would have looked right on his theme and wrong on every
other — which is why §68 declined to fake it and left a note instead.

**Modest on purpose.** It has to read as "this is the one" at a glance without making the row jump
about as the ranking changes between keystrokes.

Applied to the primary word only, not the secondary line. And it follows the same rule the ordering
does: with three candidates the middle is emphasised, with any other count the first is, because
with two there is no middle and with more the centre is not one obvious place.

---

# 71. Exclusive recording: ask, but never listen

He asked for it back, and §29 anticipated exactly this: *"a future request should be read as a
request to change his mind, not as a gap to fill."* It is that request.

**What is different from the version §29 removed.** The old code also *listened* for focus loss and
paused **his recording** whenever another app took focus. With a reader playing in the background the
two rules met — each program politely stopping for the other — which is why it read as backwards.

**The listening was wrong, not the asking.** The new one asks and does not listen. The required
change listener is deliberately empty. **Nothing another app does can interrupt a recording; only he
can stop it.** That is the rule to keep.

**`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`**, not plain transient: other players *pause* rather than
duck, and do not resume until focus is abandoned. Ducking would leave his bhajan playing quietly into
the microphone, and a quieter recording of the wrong sound is no improvement on a loud one.

Requested before the microphone opens; released in `cleanupAudioRouting`, which **all six** exit
paths already call — sent, cancelled, failed or torn down — so the music always comes back. Both ends
are logged, so "the music never resumed" can be checked rather than guessed.

---

# 72. Ctrl+P and Ctrl+F carry his own wording

**The answer to "is there a place to write my own prompt" was no.** Both keys used a throwaway
`PromptModel` built from a constant, deliberately kept out of the prompt library so a renamed or
deleted library row could not leave a shortcut doing nothing (§30). That protected the shortcut and
locked the wording with it.

Now: **Keyboard shortcuts → Your own wording**, one box per key.

- **Empty means use the shipped instruction.** The default is `""`, not a copy of the text — so the
  built-in can be improved later without overwriting what he wrote, and "reset" is clearing a field
  rather than remembering what the original said.
- The shipped text shows as a **placeholder**, not as content. Filling the box would make every
  default look like something he had written.
- **Saved as he types, read fresh on every press.** No Save button, because there is nothing to get
  wrong: a half-finished sentence affects only the next press and is fixed by finishing it.

## Ctrl+F now carries the prompt he wrote

Shipped **word for word**, down to the sentence order, and the class comment says not to improve it.
The output goes out as his own words under his own name, so a prompt smoothed into something more
standard-sounding would produce messages that read like anybody.

**It does not say "return only the text", and that is left alone on purpose.** §51's length guard
catches the failure that line would have prevented — a reply far longer than its input is refused and
shown rather than typed. **A guard on the output beats a line in the prompt, because a model can
ignore the line and cannot ignore the guard.**

`NAME` is still "Better flow", so the spinning dust reads the same. Worth renaming when he says what
he wants it called.

---

# 73. Settings back up beside the keys

**`backup` at the top of settings**, next to `log` and `access`. One press writes every preference to
`Documents/TTTmini/settings.jetpref` — the same folder as the key backup.

**Why not the existing backup screen.** That one makes a zip through a file picker: choose a place,
name it, find it again later. Right for an occasional archive, wrong for somebody who reinstalls
several times a day. **A backup whose location has to be remembered is not there when the phone is
wiped.** This writes to the one place the app already knows how to find, under a fixed name, so a
fresh install can look for it without being told where.

**No picker, no confirmation.** Pressing twice is harmless and there is nothing to name.

**The same folder as the keys, deliberately** — one grant of all-files access (§43) covers both, and
a restore that means finding two files in two places is a restore that gets half done.

**Separate files, also deliberately.** Keys are secrets that outlive any version of this app;
settings are shaped by it and may not survive a large enough change. Losing one must never cost the
other.

**Setup offers it**, above the key restore, because redoing settings by hand costs him more. Offered
rather than applied: it overwrites what is currently set, which is right on a fresh install and a
real loss on a configured one, so the choice stays his.

`ImportStrategy.Merge`, not `Erase` — a setting the backup predates keeps whatever this version
decided for it, instead of being blanked because an older file had never heard of it.

Uses `FlorisPreferenceStore.export`/`import`, the same calls the backup screen makes, so the
datastore's own format and version handling are not reimplemented here to drift.

---

# 74. One prompt, one place

**Two places held the same two prompts on one screen** — the shortcut rows described them, and a
section below let you edit them. One key, two locations, and the question of which was real.

Now the row **is** the editor. The name is blue and underlined, the way a link is on the settings
home, so it reads as something that opens. Tapping it opens a dialog with the prompt in it.

**What the row shows is the prompt actually in use**, not a summary of it: his wording if he has
written one, the shipped one otherwise, three lines and then an ellipsis. A summary is a second
thing to keep true and was already drifting from what the key really did. `your wording` appears
under it when it is his.

**Saving the shipped text unchanged stores nothing.** Only a real edit becomes his, so a prompt left
alone keeps following the default if that is improved later. `Use default` appears in the dialog only
when there is something to clear.

**A dialog rather than an inline box**, because these are paragraphs: a text box inside a list is
either too small to write in or too tall to scroll past.

## Ctrl+F's default is his current wording, exactly

Shipped word for word. The earlier version ended on a line reading "Text to rewrite:" — **that line
was mine, not his**, and it is gone with the rest of my edits to his words. The rewording path
appends the text after a blank line, so nothing has to announce it.

**If a model ever starts treating the trailing sentence as text to rewrite**, the fix is a separator
in the *path*, not a line added to his prompt.

---

# 75. The number row stands on its own

Key 1 did nothing whenever the main keyboard was folded away. `MaExtraRow` was gated on
`maZoneKeyboard` **as well as** its own switch, on the reasoning that folding the keys should take
the number row with them.

**That reasoning treated it as part of the keyboard, and it is not.** It is a row he switches on and
off with key 1, exactly as key 3 does for the copy row — so tying it to the keys made a switch that
silently does nothing in the state where it is wanted most. One borrowed condition removed; its own
switch still decides.

## Tested by the four (§ his Four Tests, 18.8.2026)

**Test 1 — the mechanism alone.** The gating rule as a truth table, six cases: everything on, keys
folded with the row on (the bug), row off with keys on, both off, and the overflow panel open over
each. 6/6. **Row and keys resolve differently in 2 of the 6**, which is what proves they are now
independent rather than one condition wearing two names.

**Test the test.** Restored the old rule and re-ran: 1 failure, and the independence count dropped
from 2 to 1. The test watches the thing it claims to.

**Test 4 — the upgrade.** `dictate__ma_extra_row` keeps its key, its default and its meaning; no
migration touches it. His stored choice survives. **One visible consequence, stated rather than
discovered:** anybody upgrading with the row switched ON and the keys folded will now see the number
row appear where it did not before. That is the fix working, not a regression.

**Tests 2 and 3 were NOT run.** Both need the app on the phone: whether the row lays out correctly
with no keyboard beneath it, and what the keyboard height becomes when the row is the only thing in
zone two. Neither can be reached from here. **The height arithmetic is the one to watch** — the note
in `TextInputLayout` says a previous attempt at row-by-row hiding left a band of nothing where the
keys used to be.

---

# 76. Spoken formatting commands

Say **"the word parenthesis"** and get "the (word)". **"hello dot"** gives "hello." Also
`question mark`, `exclamation mark`, `uppercase`, and Croatian `zagrada`, `točka`, `upitnik`,
`uskličnik`, `velika slova`.

**Only as the last word, and it is removed** — the same rule as §26's voice commands, for the same
reason: a rule that fired anywhere would eat the word "dot" out of a sentence about a dot, and
dictated words that get eaten cannot be undone back to.

**Stacking falls out of the rule**: "hello world uppercase dot" applies the stop, then uppercase,
because after removing one the next is now last.

`uppercase` takes the whole **preceding sentence**, not the last word — he said "everything, what I
just said", which is more than one word. **Say if that is wrong**; the other reading is one line.

Both languages recognised at once, since he switches mid-thought.

## Tested by the four

**Test 1 — 19 cases, 0 failures.** The cases it is for, the cases it must **refuse** ("the dot on the
map", "put it in parenthesis for me", "uppercase letters are useful" — all untouched), both
boundaries (empty, one word, command alone), transcriber punctuation, and the two-word commands.

**It caught a real bug.** "hello world uppercase dot" lost its full stop: the stop was added, then
consumed with the word it had attached to. Fixed by a rule rather than a patch — **transforming
commands preserve trailing punctuation, ending commands define it** — because re-appending in both
cases would give "Hello world..".

**Test 3 — every ugly case survives.** Empty, whitespace only, one word, commands only, 5000 words
(1.65 ms), malformed "....!!!", hostile "(((( parenthesis", stacking past the bound, mixed language,
Croatian diacritics. Nothing throws. **Idempotent**: applying twice equals applying once, on all four
sampled forms.

**Known defect, found by test 3 and NOT fixed: newlines are flattened.** "first line\nsecond line
uppercase" returns one line. It only bites when a command is used on multi-line text, and the fix is
to operate on the last line rather than the whole string. Written here rather than rushed in.

**Tests 2 and 4 not run.** Test 2 needs the phone. Test 4 has nothing to check: no stored data, no
preference, no format — a fresh install and an upgrade behave identically.

## Still queued from the same request, NOT built

**The CC settings screen** — keys visible and testable, prompt groups for Ctrl+P and Ctrl+F, several
named prompts each with a radio button, `new` to add one, **each shown as ONE collapsed line** that
opens a full editor. His words: it must read like meditation, one line, no scrolling.

**Better Ctrl+P and Ctrl+F prompts** — "professional proofreader", and reflow that does **not change
meaning**, which is his repeated complaint.

**Offline resilience** — retry, then a **resend** button in the status line when it gives up.

**History resend and status** — every recording marked transcribed or not, colour coded (his
suggestion: yellow done, blue not), with resend, so no dictation is ever lost.

---

# 77. Voice formatting: the full set, with pronunciations he can hear

**31 spoken marks**, grouped by what they do to the text: ending a sentence, wrapping the last word,
following the last word, and the whole dictation. Settings → **Voice formatting**.

**Every row can be switched off.** A command is a word taken out of his sentence, and the commoner
the word the more often that is wrong — "at", "plus" and "minus" appear in ordinary speech
constantly. Ticking is how he decides which trade is worth it, mark by mark.

**Stored as the ones switched OFF**, not the ones on, so a mark added in a later version is live by
default instead of invisible to anybody whose saved list predates it. Empty means everything works,
which is also the right first run.

## Underscore is the odd one, deliberately

Every other command reaches back one word or one sentence. `underscore` takes **the whole
dictation** and replaces every space, because it is for naming a file and a file name is the whole
line or nothing.

## The recordings

His English carries an accent the transcriber sometimes mishears, and a misheard command is worse
than a missing one — it types a stray word into the middle of a sentence. Reading "say: ampersand"
does not say which sounds the recogniser wants. Each row has a speaker that plays the word in
**Received Pronunciation**.

Made with Hume TTS, converted to **Opus 24 kbit mono: 31 files, 76 KB total, ~2.5 KB each** — less
than one photograph. Raw WAV was 121 KB *per file*; the conversion is what makes carrying all of
them free.

**The id is the filename and the command word**, one string tying row, audio and matcher, so they
cannot drift apart. Verified: 31 catalogue entries, 31 files, none missing, none orphaned.

## Tested

**Test 1 — 18 cases, 0 failures** on the expanded set: every group, both languages, the refusals
("the hash of the file", "meet me at the door", "underscore is a character" — all untouched), and
stacking.

**It caught two real bugs.** "my file name underscore dot" lost its full stop, and "the word
parenthesis comma" lost its comma — the suffix rule only preserved `.!?` and underscore preserved
nothing. **A comma placed by "comma" is as much his as a stop placed by "dot"**, so the rule now
covers `.!?,;:` and underscore keeps it too.

**Test 2 on the API — real key, real call, HTTP 200**, and the failure path was real as well: 15 of
31 came back **429**, which turned out to be a per-minute limit rather than a quota, proved by a
single call succeeding after a pause. Pacing at 12 s produced 31 of 31.

**Not tested: playback on the phone.** Whether Opus in assets plays through `MediaPlayer` on his
Android 16, and whether the volume is right against his ringer. Both need the device.

## The key

Used from the sandbox only, never echoed, never committed, never logged. It stays available for the
rest of this project and is shredded when he says the work is done.

---

# 78. AUTO language, at last (item 26)

The badge has three states now: **ENG, HR, and AUTO**. On AUTO the recording goes to Groq's Whisper
first, and the answer decides what AssemblyAI is told.

**The question asked is "is this English", not "which language is this"** — his rule, and the right
one. Measured with real speech through the real API: English audio returns `"English"`, Croatian
returns `"Croatian"`. But Croatian is routinely heard as Slovenian, Serbian, Czech or Polish, while
English is almost never mistaken for anything. Everything not recognisably English becomes Croatian,
**which also fails in the safe direction**, since Croatian must never reach the sync path and a wrong
answer here only makes it slower.

**Null is a real answer.** No Groq key, no network, a timeout, a refused key: the language stays
exactly as it was set by hand. **AUTO can only improve on the manual setting, never break it** — which
is what makes it safe to leave on.

**The badge shows what AUTO settled on** — `A·HR`, not just `AUTO` — because a badge that hid the
resolved language would remove the one thing a badge is for, and a wrong detection would arrive later
as a bad transcription instead of being visible at a glance.

## Three traps, all real

**The scope is `Dispatchers.Main`.** `transcribe` is not a suspend function, so the probe is pushed
to IO with `runBlocking(Dispatchers.IO)`. A network call left on Main does not merely freeze the
keyboard on modern Android — it throws `NetworkOnMainThreadException`.

**The User-Agent is load-bearing.** `api.groq.com` sits behind Cloudflare, which refuses a request
with no browser-like User-Agent with 403 and an HTML body, indistinguishable from every key being
dead. Do not remove it.

**One badge function, no arguments.** Four places draw the badge; a per-caller version is four places
to disagree. Each one is keyed on the mode as well as the language, or a tap to AUTO leaves the badge
still reading HR.

## Tested

**Test 1 — 20 cases, 0 failures** on the clamp: English in five spellings, nine neighbour languages
that must all land on Croatian, and the empty, null, whitespace and unknown cases.

**Test 2 — real API, real keys, real speech.** Croatian audio generated with Hume and sent to Groq
came back `'Croatian'`; a tone came back `'English'`. HTTP 200 both times, with the User-Agent header.

**The 30-second cap is done — see §79.**

**Not tested: the probe on the phone.** The multipart upload, the key ring walking a real ring, and
whether 12 s is the right timeout on mobile data.

---

# 79. The probe sends 30 seconds, not the whole recording

`AudioConcat.trimSeconds` writes the first N seconds of a PCM WAV to a new file.

**A byte copy, not a re-encode.** PCM has no frames to align to and no codec state to carry, so the
first 30 seconds are simply the first 30 seconds' worth of bytes with a corrected header. No ffmpeg,
no decoder, nothing lost. It reuses `parseWav` and `wavHeader`, already in that file and already
proven by the segment merge.

**Why it matters:** the probe must finish *before* the real request starts, so its upload is time he
waits with nothing happening. Whisper settles the language from the opening sentence; a five minute
dictation was spending the whole upload answering a question decided at the start.

**A failed trim is not fatal** — the untrimmed file is sent instead, which is slower and still
correct. The temp file is deleted in a `finally`, so a copy of his voice never outlives the question
it was made to answer, whether the probe answered, failed or threw.

## Tested

**Test 1 — 7 arithmetic cases, 0 failures**: longer than the limit, exactly the limit, shorter (the
whole file, not a refusal), one byte, odd length, zero data, zero seconds. Plus frame alignment
checked across 1ch/2ch, 16/32-bit, 16/44.1/48 kHz — all aligned.

**Test 2 — real file, real API, and an outside party agreeing.** A 62.3 s Croatian WAV trimmed to
960,044 bytes; `ffprobe` reports **30.000000 s**, and Groq's own response reports
`duration: 30.000001024`. **Both the full and the trimmed file returned `'Croatian'`** — half the
upload, same answer. That external duration figure is the number worth having, because it is measured
by something I do not control.

## Trap repeated, and it is now twice

`AudioConcat` was already imported and I added it again — the identical mistake that made build 166
red with `MaKeys`, two builds earlier. **Grep for the import LINE, never for the symbol.** The check
now runs after every import edit and reports duplicate lines directly.

---

# 79. Croatian text, English suggestions — a chain I built myself

His screenshot: badge on HR, suggestions offering `credits`, `credited`, `creditors`. Two of my own
changes made it, one build apart.

**Build 111** cut `MaLanguage.set` from the keyboard subtype, at his request: typing and speaking are
two acts, and a control for the microphone should not relay the keys. **Build 150** then took the
language picker off the spacebar long press and gave that gesture to the cursor pad.

Suggestions come from the **subtype**. So after 150 there was no route to the suggestion language at
all — not the badge, not the spacebar, not settings. He typed Croatian and the keyboard answered in
English, correctly, from the only language it had been left with.

**The subtype follows the badge again.** What 111 was really protecting against was the keys being
relaid unasked; that is a layout question, and the two subtypes here are qwertz and qwerty, which is
the layout he wants for that language anyway. **If a control writes a language, it must write the one
the suggestions read, or the two will drift and only a screenshot will find it.**

## The bold was on the wrong word

It followed `isEligibleForAutoCommit`, a property of the FIRST candidate — so when §68 moved the best
guess to the centre, the bold stayed left and pointed at the wrong word. Emphasis is now one decision
made by position, so the large word and the bold word cannot separate again.

## The dizziness

Long-pressing the spacebar clears the candidates, the strip fell back to something shorter or to
nothing, and the text above jumped down and back. **Hidden now means empty but still there**: the
strip keeps its height and draws nothing, so nothing above it moves.

## Hiding the row

Long press the language badge. He asked for an X at the end of the row and then talked himself out of
it — an X costs a slot in a strip that is already narrow and only ever does one thing, while the
badge is already there and already about that row. Short press still cycles ENG, HR, AUTO.

Its own preference, not the existing suggestion setting: that one governs whether suggestions are
COMPUTED. This is a curtain, and drawing it should not throw away the word model's work.

## Asked for and NOT built: the split recording

He wants the recorder writing **two files at once** — the first capped at 30 s for the Groq probe,
the rest continuing in the second — so AUTO gets exactly the audio it needs without waiting for a
long dictation to finish.

That is the right design and it is a real change to `MaAudioRecorder`: a second `MediaRecorder` or a
tee on the encoder, a cap that closes one file cleanly while the other keeps writing, and both paths
tidied on cancel, failure and process death. **It deserves its own build with the four tests, not the
end of this one.** Today the probe sends the whole file, which is correct but slower on a long
dictation.

---

# 79. History says what is finished, and can send again

**A stripe down the left of every row.** Sand for transcribed, blue for not sent yet. The question he
asks while scrolling is "is anything here unfinished", and a colour answers that without being read.

**Deliberately not red.** A recording that has not been sent is **work outstanding, not a fault** —
the same reasoning that took red off the buckets (§44). Red on a list he opens to find something
makes every visit feel like an incident. The red text on failed rows is gone with it; the stripe
carries the state now.

**Resend, on the rows that need it.** A dictation that never transcribed is the one loss in this app
that trying again later cannot undo: the words were spoken once. The audio is kept for exactly this,
and until now the only way back to it was the keyboard's own history panel — which meant finding it
with the keyboard up, rather than while reading the list.

**Shown only on a failed entry.** On a successful one it would redo work already done and overwrite
text he may have edited since.

It calls `DictateController.retranscribeHistoryEntry`, the same function the keyboard panel uses, so
a recording resent from the list behaves identically and writes its result back into the same row.

## Still queued

- **Bucket copy by occurrence** — `copy 1`, `copy 2` counted from the bottom, and a finger that
  scrolls and re-searches when a target is not found (§37, §59).
- **The CC settings screen** — key list, prompt groups per shortcut, several named prompts with radio
  buttons, each one collapsed to a single line that opens an editor (§ his request, 18.8).
- **Offline retry with a manual resend in the status line** — the recording path gives up silently
  today; he wants a button when it does.
- **English base dictionary**, the counterpart of §33's Croatian one.

---

# 80. Two bugs in AUTO, both mine, both about WHEN not WHAT

He spoke English and got a Croatian transcription, and the language switch in history was frozen.
**The detector was never the problem.**

## The detection was right and was thrown away

`MaLanguage.set` launches its write on the **Main** dispatcher, and `transcribe` also runs on Main —
so `set` followed by a read of `active()` in the same function returned the **old** value, every
time. The probe said English, the write was queued, the request was built from the stale value, and
Croatian went out.

`setNow` writes with `runBlocking` and returns only when it has landed. **Anything that decides a
language and acts on it in the same breath must use it.** `cycleMode` writes the same way now, for
the same reason: he taps the badge and speaks immediately, so the tap must land before the send
reads it.

## The frozen switch

Re-transcribing goes through the same `transcribe`, so **the probe ran again and overwrote the
language he had just chosen** — every attempt. The switch was not broken; the app was arguing with
him and winning.

`if (!isReplay && ...)`. **Re-transcribing exists BECAUSE the language was wrong**, which makes it
the one moment when his choice is better information than any detector.

## Tested by the four

**Test 1 — 10 cases, 0 failures.** Both his reported cases, both replay directions, probe failure,
and manual modes ignoring the probe.

**Test the test — restoring the async write turns exactly 2 cases red**, and they are precisely the
two he reported. The test watches the real fault rather than agreeing with the code.

**Test 3 — 5 ugly cases, 0 failures:** empty probe answer, unknown language, junk stored language,
junk mode, junk mode on replay. Everything lands on a usable language.

**Test 4** — `dictate__ma_language_mode` defaults to `hr`, so an upgrade never switches AUTO on by
itself, and no stored value changes meaning.

**Not tested: the phone.** Whether `runBlocking` on Main is fast enough not to be felt on a
preference write — it is a local datastore write, but it is a block on the UI thread and worth
watching.

---

# 81. The screen reader, with Speechify voices

A speaker key in the feature row. **Short press reads what is on screen; press again to pause, again
to continue. Long press opens the reader settings.** The face shows what the NEXT press will do —
speaker, pause bars, play triangle — because a key that describes its own state leaves him working
out what pressing it would achieve.

## Ukrainian stands where Croatian would

**Speechify has no Croatian voice.** Verified across the whole catalogue: 985 voices, every page
walked, no `hr-HR` on any model. The only Slavic locales that exist are Russian (50 voices), Polish
(2) and Ukrainian (2).

He compared five renderings of the same Croatian sentence and chose **Lesya (Ukrainian)** first,
**Beatrice (British, multilingual)** second. Slavic phonetics turn `č ć ž š đ` and the `-lj- -nj-`
clusters into sounds rather than spellings.

**The model follows the voice, never a global default.** `lesya` exists only on `simba-multilingual`
and `simba-3.0`; `simba-english` would fail for her, and `simba-3.2` answers HTTP 400 for anything
outside the eight curated `_32` ids.

**The language pill picks the voice.** The same badge that sets the dictation language chooses who
reads, so there is nothing extra to keep in step.

## The hard part was refusing, not reading

A screen is mostly chrome. Two rules do nearly all the work:

- **Anything clickable is never read.** Nobody writes a paragraph inside a button, so this separates
  "Copy message" and "More options" from prose without a list to maintain — and those two were the
  exact cases the length rule could not catch.
- **Under 12 characters is a label.** "Send", "New chat", "Opus 5", every timestamp.

Then: nothing without a letter in it, a short chrome list for the long boilerplate, and duplicates
dropped — a string usually appears twice, on the container and on the text node inside it, and
hearing every sentence twice is the fastest way to ruin a reader.

**Reading order comes from the screen, not the tree.** Sorted by top edge then left, because the tree
is a layout hierarchy and gets it wrong exactly when there are columns.

## Tested

**Test 1 — 22 cases, 0 failures**, using real strings from his own dumps. Both refusal cases that
failed the first attempt drove the clickability rule. Reading order and de-duplication verified.

**Not tested: a real synthesis from the app.** The key ring, the mp3 playback, and whether the
reading is pleasant. All need the phone and his Speechify keys imported.

**He must import the keys first** — Speechify is registered now (it was recognised by `MaKeys` all
along but missing from `ProviderRegistry`, exactly the gap Groq had in §31), so the keys screen will
accept them.

---

# 82. The Speechify key test asked the wrong question

The keys screen showed **"no connection"** with *The requested resource could not be found* against
keys that work perfectly. The API was answering; the test was asking for something that does not
exist.

`OpenAiCompatibleClient.validateKey()` requests **`/models`**. Every OpenAI-shaped provider has it and
**Speechify does not**, so it answered 404, which fell to the catch-all branch and was reported as a
network failure — **sending him to look at his wifi over a healthy key**.

`MaSpeechify.validateKey` asks `GET /v1/voices?limit=1` instead: the cheapest thing Speechify will
answer, one voice, no synthesis, nothing billed. Measured against the real API — a good key returns
200, a nonsense key returns 401 with `{"error":{"code":"unauthorized"}}`.

**A test must ask the question the service can answer, not the question the other services happen to
share.** That is the general lesson; the specific one is that a 404 is not a connection failure and
must never be reported as one.

Statuses follow the engine handoff: 200 works, 401/403 condemns the key, **429 is throttling and does
not condemn**, anything else says what it was rather than guessing. 7 mapping cases, 0 failures.

`MaKeyRingStore` is told on both success and rejection, so the verdict shown on this screen is the
same one the reader will act on — there is only one place holding it.

---

# 82. Karaoke: the subtitle row, the spacebar word, and reading speed

**Speechify returns word timings free with every synthesis.** Measured: each word carries
`start_time` and `end_time` in milliseconds, and the windows have no gaps or overlaps. So following
along costs one comparison every 60 ms and no second request.

## The subtitle row is the default, and the spacebar version is the option

The spacebar borrows a key that **only exists while the letter keyboard is shown** — so it vanishes
exactly when he reads a screen with the keys folded away, which is most of the time. The subtitle row
belongs to the reader instead, and is there whenever the reader is.

**One sentence, not the passage.** A screen of text on one line is unreadable and chasing a highlight
through it is worse than nothing. Sand and bold for the word being said, dim for the rest — two
states only, because a fade of "recently said" makes the eye chase the gradient.

**Nothing at all when nothing is being read**, not an empty bar, so it can sit in the layout
unconditionally without the keyboard changing height mid-reading.

**Sentences are derived, not given.** Measured: three sentences come back as ONE mark object with a
flat word list, whatever the `type` field claims. A sentence is therefore the run between one word
ending in `.` `!` `?` and the next. Verified against real timings — every boundary lands correctly.

## The S key

`MaFeatureKey.SUBTITLE`, face `S`, toggles the row. A key rather than only a setting because it is
changed **while reading**: wanted for a passage being followed closely, in the way for one half
heard. Lit when on, because between sentences the row is blank and an unlit press would look like it
had done nothing.

## Speed

`0.5×` to `2.5×` in tenths, stepped in reader settings. **Applied to playback, not to the
synthesis** — so it costs nothing, takes effect on the next press rather than the next request, and
no audio is re-bought to hear it faster.

**The karaoke divides the playhead by the speed before looking up the word.** The timings describe
the audio at normal rate while `currentPosition` advances in real time, so without that the
highlight would drift further behind the longer he listened — worse than not having it.

## Tested

**Test 1 on the word lookup — 13 cases, 0 failures**, using the real timings from a live call: every
boundary, both sides, past the end, and negative. Confirmed no gaps and no overlaps.

**Test 1 on sentences — every boundary correct** across three sentences of real marks.

**Not tested: the phone.** Whether 60 ms feels smooth, whether the highlight sits right against the
voice, and whether `setSpeed` is honoured on his Android 16.

---

# 83. Volume button passthrough: quick tap acts, hold is the volume

His design, and it dissolves a problem that had no good answer. The keys are the app's while the
keyboard is up, which left no way to change the volume without folding the keyboard away.

**The decision moves to RELEASE.** A quick tap is a tap; a finger still down after 500 ms was never
a tap at all. Nothing happens on the way down any more — at the moment of pressing there is no way to
know which of the two he meant.

- **Volume up, tap** — start or stop recording.
- **Volume down, tap** — press the magic finger term, still resolved through the row's own list.
- **Either, held past 500 ms** — real volume, repeating every **111 ms** (his number) with the
  system's own volume UI showing, until the finger lifts.

**The cost, stated:** recording used to start on the way down and now starts on the way up. For a tap
that is the length of the tap — not felt. It is the price of one key doing two jobs, and it is the
right trade.

**Its own coroutine scope, on Main.** The service's lifecycle scope would carry a repeat across a
keyboard that has been hidden, and a volume key repeating for a keyboard nobody can see is how an app
gets uninstalled.

**Held-key repeats from the system are swallowed**, because the repeating is done here on our own
clock — otherwise both would run and the volume would race.

## Tested

**Test 1 — 7 cases, 0 failures**: instant tap, normal tap, the last millisecond that is still a tap,
the first that is not, and three hold lengths with their exact step counts.

**Both invariants proved across 429 hold lengths:** *never both* — no press ever produces an app
action and a volume change — and *always one* — no press ever does nothing at all. Those two are the
whole feature, and a case-by-case table would not have shown them.

**Not tested: the phone.** Whether 500 ms reads as the right threshold under a real thumb, and
whether `FLAG_SHOW_UI` puts the system volume bar somewhere that covers the keyboard. Both are one
constant away if they feel wrong.

---

# 84. The subtitle box, and reading past the fold

## Why it vibrated

The row scrolled horizontally to keep the current word in view, so **every word shifted the whole
line** — and on a long sentence the highlight ran off the edge anyway.

It is one wrapped block now, three lines, in a rounded box **the height of the composer he reads
beside** (96dp), so the two read as one interface. Nothing moves except the colour.

**Built as one `AnnotatedString`, not a `Row` of `Text`s** — a Row cannot wrap, so it would push the
sentence sideways again: the same bug in different clothes.

**Fixed height rather than wrapping to fit**, or the keyboard would change height between a short
sentence and a long one, with his thumb already moving towards a key.

## Reading past the fold

When a screenful finishes, it scrolls a page and reads the next, until the bottom.

**Two independent stop conditions, because either alone fails:**

- **Nothing scrolled** — catches a screen that cannot move at all.
- **The text is identical to last time** — catches the far commoner case of a list that reports a
  *successful* scroll and does not actually move. **That is what would otherwise scroll forever at
  the bottom.**

Comparing text rather than counting attempts is deliberate: a fixed number of tries would stop early
on a long page and spin on a short one.

`lastPassage` is cleared in `stop()` and **not** in `stopPlayer()`, which runs between screenfuls —
clearing it there would erase the very comparison that detects the end, and pressing play again on
the same screen would stop instantly, reading as a reader that had died.

**450 ms settle** after a scroll, because a list needs a moment to build the rows it just moved into
place; reading instantly finds the old ones or none.

## Tested

**Test 1 — 5 scenarios, no runaway in any:** three normal pages, a scroll refused immediately, a
**list that lies** (reports success, text repeats), a blank page after two, and a single-page screen.
The liar case stops after one passage rather than spinning, which was the whole point.

**Not tested: the phone.** Whether 450 ms is enough for Claude's message list, and whether the box
lines up with the composer as intended.

---

# 85. Jump between pages, do not glide

He cannot watch animated scrolling. **The animation is the app's, not ours** — `ACTION_SCROLL_FORWARD`
is a *request*, and a list answers it with its own smooth scroll. There is no flag from here that
turns that off.

But there is another action. **`ACTION_SCROLL_TO_POSITION` is answered by a RecyclerView with
`scrollToPosition`, which moves in one frame with nothing to watch.** So `scrollBy` now tries the
jump first and only falls back to the animated scroll when the app will not take it:

- Read the list's `collectionInfo` for the total row count.
- Work out the visible span from the `collectionItemInfo` of the attached children.
- Ask for the row one screenful further on.
- If any of that is missing — no collection, no item rows, target out of range — return false and
  let the old action run.

**Where an app publishes no collection, the animation stays and is not ours to remove.** Said
plainly rather than promised away.

## The waits, which WERE ours

`SETTLE_MS` cut from **450 ms to 120 ms**. The long wait was sized for a smooth scroll to finish
travelling; a jump has nothing to travel, and the only thing left is the rows being attached — one
or two frames.

**Not zero.** At zero the read happens in the same frame as the jump and finds the OLD rows, which
would not look like a fast reader, it would look like one that skips a screen.

`SCROLL_SETTLE_MS` (220 ms) is untouched and never fires for the reader: it only runs *between*
pages of a multi-page scroll, and the reader asks for one page at a time. Verified rather than
assumed — **total wait per page is 120 ms and nothing else.**

---

# 86. Two real highlight bugs, found by looking rather than tuning

He said the highlight was "a little bit off" and asked for the algorithm to be checked rather than
adjusted. He was right to: **neither fault was timing, and no amount of tuning would have reached
either.**

## Bug one: the highlight matched by TEXT

`words.indexOfFirst { it.text == word }` — the subtitle looked up the current word **by its letters**.
A passage says "the" many times, so it always landed on the **first** one.

Measured on a 36-word passage: `"the"` appears at positions 0, 6, 11, 15, 18, 23, 26, 30 and 34, and
the old code highlighted position **0 every single time**. That is the jumping he saw, and it had
nothing to do with milliseconds.

`MaReader` now publishes `currentIndex`, and the subtitle compares by **position**. The text is not
an identity; the index is.

## Bug two: the speed was counted twice

`MediaPlayer.currentPosition` reports a position in the **media timeline**, which already advances at
the playback rate. Multiplying it by the speed again made the highlight run ahead by exactly that
factor.

Measured against real timings: at 1.5×, the old arithmetic was wrong in **3 of 5 samples**; the new
one in **0**. At 1.0× the two agree — which is why it read as vague drift rather than a plain error,
and why it would have survived any amount of adjusting the tick rate.

The `speed` parameter is **gone from `startTicker`** so it cannot creep back.

## Pages that fit, instead of sentences

Sentences were the wrong unit: a long one overflows a three-line box and the text jumped as the
highlight moved through the part that could not be shown; a short one left the box nearly empty.
Neither has anything to do with how much room there is.

A page is now a run of words that **fits** — by characters, since what fills three lines is a number
of letters rather than of words. Whole words only, computed once per passage.

**The page changes only when the spoken word leaves it.** Verified: over the whole passage the page
changed exactly as many times as there are pages, so it never flickers back.

Every page fits, and every word belongs to exactly one page — both checked across the passage.

---

# 87. Detect the language from the TRANSCRIPT, not from the sound

Auto-detection was unreliable and he was right that it should not be. **The mistake was asking the
wrong question.**

Whisper's `language` field is an acoustic guess made from the opening seconds, before it has heard
much, and Croatian is a small language sitting beside four larger ones it is routinely confused
with. Meanwhile the same response carries **the transcript** — and text is decisive in a way sound is
not. No English sentence contains `č`. No Croatian sentence is built out of "the" and "of".

**Asking for a language and discarding what was actually heard was the whole error.**

## Three independent signals

- **Diacritics, counted triple.** One `ž` is nearly proof by itself.
- **Function words.** The commonest words in either language, so present in almost any sentence.
- **Endings** — and this is the one that matters most. Croatian inflects and English does not, so
  `-ati -iti -ost -ući -ima -oga` need **no vocabulary at all**. A sentence about camera rigs or tax
  forms, full of words no list will ever hold, still scores correctly.

The acoustic label is kept only as a fallback for a transcript too short to score — "Okay", "Hvala"
— where nothing could do better.

## Tested

**16 cases, 0 failures**: both languages, Croatian without diacritics, badly transcribed Croatian,
one-word utterances, mixed sentences, and empty input.

**The endings rule was added because a real case failed without it** — *"Trebam napraviti novi
projekt i poslati ga danas"* has no diacritics and no listed function words, and scored nothing.

**Checked against the real Groq transcript** of real Croatian speech, mistakes and all — `"Dobar dan,
ja som Baba i ovo je snimka Rahavatskom jeziku"` — and it lands on Croatian despite Whisper having
mangled three words.

## The badge says AUTO

It read `A·HR`, meaning auto mode had resolved to Croatian. **One fact too many on a key the size of
a thumbnail**, and he read it as a state he did not recognise. On AUTO the language is no longer his
decision, so showing it invites him to check something he cannot change.

## Right language, right route

`SYNC_SAFE_LANGUAGES` still gates the fast path and still contains only English, and it reads
`MaLanguage.active()` — which the probe now writes **synchronously** before the request is built
(§80). So detection genuinely drives the routing: English takes sync, Croatian takes the slow
accurate model, and neither is a coincidence of timing.

---

# 88. Suggestions were eating the trackpad's selection

He reported selecting a few words on the trackpad and watching the selection vanish, over and over.
It read as chaos and it was one line of cause.

**Computing suggestions marks a composing region around the word at the cursor, and marking one
collapses whatever is selected.** So every arrow the pad sent triggered a refresh, and the refresh
threw the selection away.

Three parts to the fix, and all three are needed:

- **`resetSuggestions` returns early while the pad is open**, and *clears* rather than merely
  skipping, so no stale row is left hanging over a pad it no longer describes.
- **The Smartbar treats the candidate list as empty while the pad is open.** Suppressing new
  suggestions does not remove the last set from the flow — without this the row would still show
  words describing a cursor that has since moved.
- **Closing the pad asks for suggestions again**, through `MaCursorPad.onClosed`. Otherwise the row
  stays empty until the next keystroke, which looks like the pad broke suggestions on its way out.
  Registered once in the service, not at the two places the pad is drawn, so the two cannot restore
  different things. A callback rather than the pad reaching for the keyboard manager — it is drawn
  by two layouts and knows about neither.

## Tested

**4 cases, 0 failures**, and the invariant that matters: **a selection made on the trackpad is never
destroyed**, across 200 runs.

**The first version of the test failed, and the test was wrong, not the code.** It asserted that a
selection survives with the pad closed during ordinary typing — but there is no selection then, and
a composing region collapsing nothing is correct. Fixing the model rather than the code is the
whole point of testing the test.

## Still open: making them work together

He asked for suppression as a first step and coexistence as the better answer. Coexistence needs
suggestions computed **without** marking a composing region — the region exists so that a picked
suggestion knows what to replace, and §68 already replaces the whole word around the cursor without
needing one. **That is the thread to pull** if the row is ever wanted during selection.

---

# 89. Voice formatting stops flattening newlines

The defect found by §76's own test and written down rather than fixed. **Splitting the whole text on
whitespace and rejoining with spaces destroyed every line break**, so a spoken command used on
multi-line text silently collapsed it — worse than the command not working at all.

`applyOnce` now works on the **last line only** and puts the earlier lines back untouched.

**Not a compromise, the correct scope.** A command counts only as the final word, so the last line is
the only line a command can be on. The whole-text version was never right; it merely looked right
until the text had two lines.

The inner word list is renamed `body`, because `head` now means the untouched earlier lines and two
different `head`s in one function is a bug waiting to be written.

**8 cases, 0 failures**, including three-line and four-line input with every command family, and the
check that matters: **the newline count is identical before and after, in every case.**

---

# 90. Two more keys become modules

`MaSubtitleToggleKey` and `MaReaderKey` join the pin, the case cycle and the next-field key in
`MaKeyModules.kt`. **Five of about twenty.** The row is 1237 lines down to 1199, and four imports it
no longer needs went with them.

Both obey the contract from §69. The subtitle toggle takes only a colour and reads its own
preference — the simplest kind of module, droppable anywhere without the surface knowing what a
subtitle is. The reader key takes a context, a way to open its settings and a way to say something;
`MaReader` holds the state, so **the same key drawn on two surfaces shows the same thing without
either surface tracking it.**

## The mistake worth recording

The first attempt sliced from `SUBTITLE` to `PIN` and replaced **nothing**, because `SUBTITLE` sits
*after* `PIN` in that file — the slice was empty and the write was a no-op.

**The balance check passed and the line count was identical, and both were true.** A structural check
cannot tell "nothing changed" from "changed correctly"; it took reading the file for the branches to
see it. Same lesson as §87's type mismatch, from the other direction: **after a scripted edit, verify
the thing the edit was supposed to produce, not the shape of the file.**

---

# 91. The copy row becomes an ordinary feature row

His copy row lived in the **Smartbar quick actions** — a separate arrangement with its own editor and
its own key set. That is why it could hold paste **or** AP and never both: `ALL_PASTE` and
`ALL_CLEAR` are not Smartbar actions and never could be dragged there.

**So the four clipboard keys moved into the feature row vocabulary:** `PASTE`, `CUT`, `COPY`,
`CLIP_HISTORY`. Each goes through `tapKey`, the very path the Smartbar action used, so a paste from
this row is the same paste — **the key moved, its behaviour did not.**

That makes his request possible in one place: the copy row is now just a feature row, arranged in the
editor that already exists, where any key can replace any key and reset restores the default. No new
editor was needed; the row simply had to stop being special.

**Shipped as default row two:** select all, paste, cut, clipboard history, dictation history, AP, AC.

**COPY is deliberately absent.** He listed seven keys and copy was not among them. One tick away in
the catalogue if that was an oversight rather than a decision — and it is worth asking, because a
copy row without copy is a surprising thing to ship silently.

## Two traps caught before the build

**`ROW_COUNT` is 3.** Adding the copy row as a fourth would have been **silently dropped** — `parse`
pads or truncates to exactly three, so the list would have looked right in the source and never
reached the keyboard. The copy row takes slot two and one empty row remains.

**Three failed anchors before one landed.** `SELECT_ALL("selectall"` was wrong — the real id is
`select_all` — and the script asserted, wrote nothing, and reported success for the parts that ran
before it. **A scripted edit that aborts halfway leaves the file in neither state**, and only reading
the result afterwards showed it.

## Note for the next session

He must press **Reset all three rows** in Feature row settings, or install fresh. A stored
arrangement always wins over a changed default — that is the whole point of storing it, and it means
a default change never reaches an existing install by itself.

---

# 92. Groq removed, restore added, and the copy row gets its own screen

## Groq and auto-detection are gone

**Detection was never reliable enough to be trusted with a whole dictation**, and a wrong language
does not fail visibly — it returns fluent nonsense in the other language, which is worse than an
error. Manual is one tap and right every time.

Removed: `MaLanguageProbe` (file deleted), `probeLanguage`, `probeOnce`, the three probe constants,
the probe call in the send path, and `MODE_AUTO` from the badge cycle. The badge is ENG/HR again.

**Two things kept on purpose.** The `GROQ` preset stays in `ProviderRegistry`, just out of the
offered list, so an existing stored Groq account still parses instead of becoming an unknown
provider. And `MODE_AUTO` survives as a constant so a stored `"auto"` from an older install is
recognised rather than treated as junk.

## Restore, beside backup

It shipped with only the writing half — **the half that feels finished and never gets used**. Restore
asks first, because it replaces every current setting: right on a fresh install, a real loss on a
configured one, and a mis-tap away from backup.

## The copy row is its own thing

Same `MaRows.Row`, same catalogue, same entries — **a key added anywhere in the app appears in this
editor with nothing to remember**. What differs is where it lives: `dictate__ma_copy_row`, and it is
drawn **only in the transcription view**, which has no letter keys and where the clipboard is most of
the job.

Its own preference rather than a fourth slot in `maRows`, because `parse` pads and truncates to
exactly `ROW_COUNT` — a fourth row there is silently dropped and the source looks right while the
keyboard never shows it.

`MaFeatureRow` gained `copyRowOnly`, so the transcription view draws that row and not the three that
belong to the typing keyboard. **Same composable either way**, so a key behaves identically in both
places; the caller chooses which rows, not which implementation.

---

# 92. Groq removed, restore added with history, copy row made its own thing

## Auto language detection is gone

The probe, its constants, `MaLanguageProbe`, the AUTO badge state and Groq from the offered
providers. **Detection was never reliable enough to be trusted with a whole dictation, and a wrong
language does not fail visibly** — it returns fluent nonsense in the other language. Manual is one
tap slower and right every time.

The Groq *preset* stays in the registry so an existing stored account still parses instead of
becoming an unknown provider. `MODE_AUTO` stays as a constant for the same reason: an older install
with `"auto"` saved is read as Croatian rather than as a value nobody recognises.

## Restore, with dates

Backup shipped without its other half. It now writes **`settings-YYYY-MM-DD-HH-mm.jetpref`** beside
the plain file and keeps **twenty**.

**The stamp is big-endian in the filename and day-first on screen.** Sorting is by name, and only
year-month-day sorts correctly; what he reads is `19.08. 18:30`. Two orders on purpose, and one
function knows both.

**History is read from the folder**, never from a list this class maintains — a list drifts the first
time a file is moved by hand, and the folder is the truth.

**The list is the confirmation.** A yes/no dialog followed by "restore the latest" gives him no way
to reach the backup from *before* the change he is undoing, which is the only reason anybody opens
this. Choosing a date is both the safeguard and the feature. The newest is labelled, because it is
what he wants nine times in ten and should not need a date read.

The plain `settings.jetpref` is untouched, so setup's restore still finds "the backup" without
knowing history exists.

## The copy row is its own row

Not one of the three. Stored in `maCopyRow`, edited on its own screen, and **drawn only in the
transcription view** — the screen with no letters, where the clipboard is most of the work.

**Its own preference rather than a fourth slot**, because `MaRows.parse` pads and truncates to
exactly `ROW_COUNT` — a fourth row there is silently dropped and the source looks right while the
keyboard never shows it.

Same `Row`, same `Entry`, same catalogue, same keys. **A key added to the app appears in this editor
with nobody remembering to add it.** Only the storage and the surface differ.

---

# 93. "null" instead of every dictation — the worst bug I have shipped

Build 184 replaced **every dictation that did not end in a formatting command** with the word `null`.
That is essentially all of them.

`return head + when (command) { ... else -> null }`. **In Kotlin `String + null` does not produce
null — it produces the four letters "null".** So `applyOnce` returned a non-null string, the caller
read that as a successful transform, and the dictation was thrown away and replaced.

The transform is computed into a value now, and the head is prepended **only when it is not null**.

## Why nothing caught it

The §89 test exercised the whole `apply` loop and every case in it **ended in a command**. Not one
case asked what happens to an ordinary sentence, because the change was about newlines and ordinary
sentences were not what I was thinking about.

**The rule this earns: a transform that can decline must be tested on input it declines.** The
"nothing happens" path is the commonest path in production and it was the only one with no test.

Four cases now, including two that decline, and the invariant stated: **a dictation with no command
is returned unchanged, never replaced.**

---

# 94. n, k, c — and the copy row editor is the feature row editor

## The zone keys say what they toggle

`1 2 3` became **`n` `k` `c`**: number row, keyboard, copy row. A digit only said which position a
key held in a list nobody can see. **He reads a picture faster than a word and a word faster than a
code, and 1, 2, 3 was a code.**

Lowercase, because the letter sits inside a keyboard outline only two thirds of the glyph tall and a
capital reaches its walls. The settings labels changed with the glyphs, so the list and the key agree.

**Six call sites**, and three of them were in the settings preview — a signature change that compiled
in one file and broke another is exactly the shape of the last three red builds.

## Standing rule: a button in settings carries its icon

**Written into HANDOFF.** Whenever a setting lists keys or buttons, each row shows its own glyph
beside the name. He recognises the image before he reads the word, and a list of names alone makes
him translate twice.

## The copy row editor reuses the feature row's

`MaRowKeyItem`, `MaKeyPicker` and `MaReorderableColumn` are shared rather than reimplemented, so the
copy row has **drag handles, icons, position numbers and ticks** — identical to the feature row,
because it is literally the same code. `MaRowKeyItem` and `MaKeyPicker` went from private to
internal; that visibility was the only thing standing in the way.

**Two independent surface ticks** replace the single "show" switch: *on the typing keyboard* and *in
the transcription view*. It is one row drawn in two places, not two rows to keep in step, so both,
one or neither are all sensible answers.

---

# 95. The copy row gets its own colour, and the icon rule becomes standing

**A dark amber behind the copy row's keys**, so a glance says which list is being edited. Not the
sand used on the keyboard: **that colour already means "this key is holding something"**, and reusing
it would give one colour two jobs — the first time a lit key and a copy row shared a screen they
would look identical and mean different things.

**Lifted still wins over the accent.** The key under the finger must read as lifted whatever list it
is in; drag feedback matters more than the identity of the row.

The accent is a parameter on the shared `MaRowKeyItem` with a null default, so the feature row is
untouched and there is still exactly one implementation.

## The icon rule is now in HANDOFF

> **A button in a settings list always carries its icon.**

His words: *he recognises an image faster than text*. A list of names alone makes him translate twice
— word to picture, picture to key. It also catches a class of mistake for free: **a row whose glyph
is wrong is visible the moment the screen opens.**

---

# 95. The zone keys: a big letter, and the border says it is on

The keyboard outline inside the glyph is gone, and the little spacebar line with it. **At the size a
key actually is, that outline left room for a letter too small to read at a glance** — which defeats
the whole reason for using `n k c` instead of `1 2 3`.

**The border moved to the key.** A lit outline around the whole key reads from further away than a
lit glyph inside one, and it leaves the entire face for the letter, which is now 30dp against the
old 24dp with most of that spent on the outline.

`MaZoneKeyShape` matches the shape the keys are already clipped to, so the border sits exactly on the
key's own edge rather than near it.

**Written into HANDOFF as a standing rule**, because it generalises: *emphasise a key with a border
around the whole key, not with a mark inside it.*

## Also into HANDOFF: a button in settings always carries its icon

Wherever a settings screen lists keys, buttons or rows, each entry shows the glyph it has on the
keyboard, beside the name. **He recognises an image faster than a word**, and a list of names alone
makes him translate twice — name to picture, picture to key.

## The copy row is amber

`MaRowKeyItem` takes an optional `accent`, and the copy row editor passes a dark amber. Lifted still
wins, so the key under the finger reads as lifted in either list — drag feedback matters more than
which row it belongs to.

**Deliberately not the sand** used on the keyboard. That colour means "this key is holding
something" throughout the app; reusing it for "this is the copy row" would give one colour two jobs,
and the first screen showing both would say the same thing twice and mean different things.

---

# 95. The letter is the glyph, and the ring is the state

The zone keys drew a small keyboard outline with a spacebar line and a digit tucked inside. **Three
keys that toggle three different things all drew the same picture**, and the only mark that
distinguished them — the letter — got the space left over.

Inverted. **The letter is now the whole glyph**, at 0.86 of the key face, and whether the key is ON
is said by a **green ring around the key itself**. One thing per surface, and the thing that varies
gets the room.

The keyboard outline, the spacebar line and seven drawing imports are gone with it.

**Written into HANDOFF as a rule**, because it generalises: an important key says it is on with an
outline around the key, not by tinting something inside it. A ring reads from across the room.

## Also into HANDOFF: icons in settings lists

Whenever a screen lists keys or buttons, every row carries the same glyph the keyboard shows. He
recognises the image before he reads the word. `MaRowKeyItem` is the shape to copy — and reusing it
beats matching it, which is why the copy row editor shares it outright.

## The copy row gets its own colour

A dark amber behind its rows, so a glance says which list is being edited. **Deliberately not the
sand** used for a lit key: that colour already means "this key is holding something" everywhere in
the app, and giving one colour two jobs fails the first time a lit key and a copy row share a screen.

**Lifted still wins over the accent** while dragging, because the feedback under the finger matters
more than the identity of the list.

---

# 96. Tap the subtitle to skip, and hear a voice before choosing it

## The highlight stopped moving the text

**Colour only, never weight.** Bold changes the *width* of a word, so every word the highlight
touched nudged the whole line sideways — the text appeared to breathe as it was read. A colour
change moves nothing.

## Tap the subtitle to skip the sentence

The commonest thing he wants while listening is *not this bit*. The speaker key pauses; the subtitle
now skips.

**A seek, not a re-synthesis.** The passage is one file with word timings, so skipping is free and
instant. Asking Speechify for audio from a later point would be a second charge for words already
bought.

The end of a sentence is found in `MaReader` from the same flat word list the subtitle uses, so the
two cannot disagree about where a sentence stops. At the last sentence it does nothing, which is what
"skip the rest" means when the rest is all of it.

## Reader settings

**Speed moved to the top**, above the display choice — it is the thing he changes most and it was
below two lists.

**Tapping a voice speaks it: "Hi, I am Lesya."** The voice says its own name, which gives accent,
pace and warmth in four words and ties the sound to the row he is looking at. On IO, because a
synthesis on Main would freeze the settings screen.

**A tick to switch previews off.** On by default, since hearing a voice is the whole basis for
choosing one — but a sample is a synthesis and costs characters, and once chosen he should not pay to
reorder a list.

## Gemini: instrumented rather than guessed

The reader works in Claude and not in Gemini, and his dump does not show why — the message text is
present, not clickable, and long enough to pass every filter.

**"The reader does nothing here" has three causes that look identical:** no window reachable, a window
with no text worth reading, or text found and the voice failing after. `readableText` now logs
**windows, lines and characters**, so one paste of the log separates them instead of another round of
guessing.

---

# 96. Profiles — the first piece of the Avid model

He asked for Avid's settings architecture: collapsible folders, duplication, checkmarks, a
master-detail pane. **That is a redesign of every settings screen, not one build.** This is the piece
worth having first and on its own.

## What shipped

**Profiles**: every setting in the app, saved under a name, swapped in one tap. Settings → Profiles.

Three of Avid's behaviours, and they are the ones that carry the idea:

- **The checkmark is a radio.** Exactly one profile is in force and choosing another visibly takes it
  from the last, so the list reads as a state rather than as a folder of files.
- **Duplicate.** Nobody edits a working configuration directly — they copy it, rename the copy, and
  edit that. It is the difference between a settings screen and version control.
- **Saving under an existing name updates it**, rather than quietly creating "studio 2".

**A profile is the same bytes the timestamped backup writes.** The only difference is that the name
is his rather than a clock's, which is what turns a thing to recover into a thing to choose.

`maActiveProfile` stores a **name, not a path** — the folder is the truth, a path breaks on rename or
a new phone, and a name that no longer exists simply shows nothing active, which is correct.

## What is deliberately not built

**The collapsible tree and the master-detail pane.** They reorganise every screen in the app, and
profiles are worth having before anything is rearranged around them.

**When they are built**, the shape to follow: `MaSettingsEntry` is already an ordered list of
categories, and `MaSettingsOrderScreen` already routes each to a screen. A tree is that list with a
`children` field and a twist-down; the detail pane is the existing screen rendered beside it rather
than pushed onto it.

**And the deeper idea worth stealing before the UI:** Avid's real move is that a *setting* is an
instance you can have several of — three export presets, four keyboard maps. Here that would mean
several named feature-row layouts or prompt sets, not just one of each. **Profiles do it for the whole
app at once; per-category instances would do it properly.**

---

# 96. The Avid settings architecture: what exists, what is missing, and the order to build it

He specified the model precisely: **profiles → categories → instances**, with radio-button
activation, twist-down folders, duplication, master-detail routing and a search filter. It is the
right model for someone with twenty-seven years of Avid muscle memory, and it should be built.

**It must not be built in one pass.** It touches all nineteen settings screens and he uses this
keyboard every hour of every day.

## Layer 1 — the profile. ALREADY BUILT.

`MaSettingsVault` + `MaProfilesScreen` have named profiles, `RadioButton` activation, **duplicate**,
delete, and a folder that is the truth rather than a cached list. `switchProfile` and
`exportProfile` are covered by activation and the shared Documents folder.

**Do not rebuild this.** Read it first.

## Layer 2 — instances within a category. NOT BUILT, and it is the whole idea.

Today each screen holds **one** configuration. Avid's power is that a category holds *several named
instances* and one carries the checkmark: three keyboard maps, four export presets, and you switch
by clicking.

**Where this is worth having, in order of value:**

1. **Prompts** — Ctrl+P and Ctrl+F already hold one custom wording each. Several named prompts with
   a checkmark is exactly his earlier request (§ the CC screen), and this is the same feature.
2. **Rows** — the three feature rows plus the copy row are already presets; naming them and adding
   duplicate makes them instances.
3. **Voices** — already a radio list. It is an instance list without the vocabulary.

**Everything else holds one thing and should stay holding one thing.** A category with one possible
instance is a folder with one file in it, and Avid users know that is noise.

## Layer 3 — the tree, twist-downs, master-detail, search. LAST.

The settings home is a flat draggable list he arranged himself. Turning it into a tree is the most
visible change and the least valuable one: **nineteen categories that mostly hold a single instance
would make every setting two taps further away than it is now.**

Build layer 2 first. If enough categories end up holding several instances, the tree earns itself. If
they do not, the tree was never the feature — the instances were.

## The generic mechanism to write once

`name`, `isActive`, `isDefault`, `configurationData`, and `duplicate()`. One storage shape reused by
prompts, rows and voices rather than three implementations — the same argument that made the copy row
share the feature row's editor rather than copy it.

---

# 97. The zone keys get their frame back, and the ring goes

Two builds ago the `n k c` keys were a letter inside a small keyboard outline with a spacebar line
under it. Then the outline moved to the key as a lit green ring and the letter grew to fill the bare
face. He looked at it on the row and called it ugly, amateurish, a downgrade, and out of standard
with the rest of the application — and every word of that is correct.

**Why the ring was wrong.** Nothing else in that row wears a border. AP, AC, the clipboard keys, the
mic, the gear, the speakers — all of them are a shape on a plain key face, and state is said by
colour. Three keys with a green outline around them are not emphasised, they are *different*, and
different is what the eye lands on first. The ring did read from further away. It also read as
something added later by somebody who had not looked at the row.

**Why the bare letter was wrong.** Every neighbour has a silhouette. A loose lowercase letter sitting
among drawn glyphs looks like a placeholder waiting for its icon.

**What it is now.** The keyboard outline is back inside the glyph, drawn at the full width of the
box. The spacebar line is gone: at this size the rounded outline reads as a keyboard without it, and
the bar was costing the bottom quarter of the body to prove something already obvious. That quarter
went to the letter, which is now `0.70` of the glyph box against the `0.40` it had — nearly twice the
height, inside a frame, which was the whole point of the exercise the ring was invented to solve.

**The rule this leaves.** Colour is the state channel and it is the only one. Shape is the identity
channel. If a frame crowds its content, take detail out of the frame; do not take the frame away.
Both HANDOFF entries that said the opposite have been rewritten rather than deleted, so the next
person can see it was tried.

---

# 98. Prompts become a category of named instances — Avid layer 2, first use

Section 96 said layer 2 is the actual idea and prompts are where it pays first. This builds it there
and nowhere else.

**Ctrl+P and Ctrl+F each hold a set now**, not a wording. Any number of named instances, exactly one
with the checkmark, and that is the one the key sends. Radio and not a tick-box: there is one wording
in force and the control should say so rather than leave two looking equally chosen.

**Default is a real instance and it is locked.** Its text is empty, which means *use the instruction
compiled into the app* — empty rather than a copy of the shipped words, so improving the built-in
text later reaches everybody instead of only the people who never touched it. It cannot be renamed,
edited or deleted, and its editor offers one verb: duplicate. That is the Avid behaviour rather than
a restriction. Nobody edits a factory setting; they copy it and edit the copy.

**Duplicate does not steal the checkmark.** A copy is the start of an experiment, and a key that
changed what it does the moment you pressed copy would be a nasty surprise mid-sentence.

## Migration is a shape check, not a flag

The old preference held plain text. A stored value that does not start with `{` is therefore a
wording from the previous version, and is read as two instances: the locked Default and one called
**Mine** holding what he wrote, active. No flag, no version number, nothing to run once — a value
written by the old build is simply legible to the new one, forever.

## The bug this nearly shipped with

The shortcuts screen used to write a paragraph straight into that same preference. Left alone it
would have looked like a legacy value on the next read and **silently discarded every other instance
in the category**. A row that destroys data by doing exactly what it always did is the worst kind,
because nothing about it looks new. It shows and routes now; the editing lives on one screen.

## Tested

Ten cases against the parse/serialize rules before any of it was wired up: first run, legacy
promotion, a round trip carrying quotes, newlines, tabs and a backslash, an active name that no
longer exists, a file with no Default, duplicate names, corrupt JSON, and the copy-naming ladder.

**Also caught before the build:** an unused-import sweep removed `getValue`, which `by` delegation
needs without ever naming it. HANDOFF already warns that import checking must be exact-line; it
should also say that `getValue` and `setValue` are never textually present and must never be swept.

---

# 99. The highlight becomes a setting, and the page turns white

Three separate controls in Reader settings rather than a list of presets, because they combine:
white-and-underlined is a real choice and so is yellow-and-bold, and a preset list would need eight
rows to say what three switches say.

- **Colour: yellow or white.** Two, not a picker. It has to carry against a near-black box and it
  has to differ from the words around it; everything outside those two is either one of them or
  worse than both.
- **Bold, off by default.** The reason it was removed in the first place still stands: bold changes
  the WIDTH of a word, so the line re-flows as the highlight passes and the text appears to breathe.
  It is a choice now rather than a ban, because he asked for it and on a short page the movement is
  small.
- **Underline, off by default.** The one mark that costs nothing: it sits in the descender space the
  line already reserves, so it can be on with a white highlight on white text and still be the only
  thing that moves.

**The rest of the page is now plain white**, where it was a dim grey. He asked for it, and it is also
what makes the white highlight usable at all — with a grey page, "white" would just have been a
second colour rather than the same colour marked a different way.

Defaults are yellow, not bold, not underlined, which is exactly what it did before, so nobody's
reading changes until they change it.

---

# 100. One gesture: up to record, down to send

> *"I record up, I press down, it sends. No waiting, not many steps."*

Volume down now does two jobs, decided by whether a recording is running.

**While recording** it is the whole thing in one press: stop, transcribe, and press Send once the
words have landed. **Otherwise** it is exactly what it always was — press the send target on screen —
so nothing anybody relied on changed.

## Three things that had to be right

**The send is armed, not performed.** The key handler cannot send: the text is not in the field yet,
and a send that fires first sends an empty message that nobody notices until they read the other end.
So volume down sets `sendAfterCommit` and the transcription path fires it when the words are down.

**It fires below BOTH delivery branches.** The realtime path commits through `commitDictationFinal`
and never touches `committed`, so a send placed inside the other branch would simply never fire for
anyone using realtime — and it would fail *silently*, which is the worst way for this to be wrong.

**It is disarmed on every abandoned path** — `cancelRecording`, `cancelTranscription`, and an empty
transcript. Otherwise the arming survives and the *next* ordinary dictation sends itself. **A stray
send is worse than a missed one: it puts words in front of somebody.**

## One definition, three triggers

`MaMagicTargets.pressSend()` now owns resolving and pressing the send term, and the volume tap, the
volume-while-recording path and the row's key all go through it. The moment a second copy exists the
three can disagree about what "send" means, and the one that disagrees is the one used at four in the
morning.

## Tested

Seven cases, 0 failures: the full gesture, the old idle behaviour, up-then-up not sending, a cancel
followed by an ordinary dictation that must not self-send, an empty transcript, and two gestures in a
row producing exactly two sends.

**Not tested: the phone.** Whether the send lands reliably after the commit in a given app, and
whether any app needs a moment between the text arriving and the button being pressed.

---

# 101. Five caption styles, and the box goes full screen

He asked for the styles people put on captions, and for the small box to be able to fill the screen
like the reader in `ma-reader-thermux` does.

## The five

| Style | What it does |
|---|---|
| **Highlight** | the page stays readable, one word marked — the old behaviour, still the default |
| **Typewriter** | the words arrive as they are spoken; nothing ahead is visible |
| **Karaoke** | the line fills up behind the voice: said in amber, saying in white, coming in grey |
| **Spotlight** | everything turned down but the word being said |
| **One word** | that word alone, large, centred — the style Instagram and TikTok made ordinary |

**All five leave the layout alone, and that is why these five and not five others.** Colour, alpha
and decoration change nothing about where a word sits.

**Typewriter draws what has not been said yet as fully transparent rather than absent**, so it still
occupies its space and the words already on screen never move to make room. That is the whole trick;
without it, typewriter is the worst offender of them all.

**Karaoke's current word is white and NOT bold.** The first version bolded it, and Test 1 caught that
it was the only style able to re-flow the line — in a style whose entire effect is a steady front
edge moving along a *still* line, that ruins it. Three colours say it without weight.

**One word is the only one that hides its neighbours**, which is the point: there is nothing to read
ahead to.

## Full screen

Long-press the box and it grows to 330dp, covering the keys; 26sp text, nine lines, and pagination
follows — 460 characters a page instead of 105, because a fullscreen box paged for three lines shows
a paragraph floating in an empty screen and turns a page every few seconds for nothing.

**The rule taken from MA Reader:** full screen there hides *every* child of the view except the text,
so anything added later is hidden by default and has to argue its way back on. The version before it
named the things to hide — a list — and lists go stale, which is exactly how the player bar survived
into full screen. Here the same idea costs one number: the box covers the keys and nothing else is
asked to move.

**Two gestures, no controls drawn.** Tap skips the sentence, long press toggles full screen. A
caption with buttons on it is not a caption, and both gestures are on the element already under the
thumb.

## Tested

The style rules alone across four styles at three positions, and the layout check that found the
karaoke bold. **Not tested: the phone** — whether 330dp covers the keys on his Nothing Phone 2a, and
whether 460 characters is right for nine lines at 26sp.

---

# 102. Full screen means full screen, and the line comes to the eye

Three corrections and one new thing, all from one message.

## Full screen now hides everything

It was a taller box with the keyboard still under it. While reading with the box expanded, the
subtitle row is now the **only thing composed** — no rows, no keys, nothing beneath.

**Gated on something actually being read**, so a preference can never leave the keyboard unusable:
stop the reader and every key is back.

Its height is `FlorisImeSizing.keyboardUiHeight()`, the height the keys would have taken, rather than
a number of its own that would be wrong on the next phone. `weight(1f)` does not work here — the
enclosing Column wraps its content, so a weight resolves to nothing.

## The font stopped growing

Full screen enlarged the type as well as the box, so one gesture changed two things and he could not
have the whole page without large type he had not asked for. **Full screen shows MORE, not BIGGER.**

Size is now `maReaderFontSize`, one number for both views, stepped in settings, 10–40. One word is the
exception and scales from the same number — there is only ever one on screen, so it takes the room
the others share.

## The reader, as MA Reader does it

Full screen no longer pages. **The whole passage is there, in short lines, and the line holding the
spoken word is scrolled to the TOP.** His eye rests in one place and the sentences come up to meet
it; everything below is what is coming, and he can glance at it without losing the place, because the
place does not move. Lines already read are dimmed.

**A "page" full screen is ONE line**, not three: it is the unit that jumps, and a three-line unit
would jump three lines at a time and lose the steadiness that makes it readable. Scaled by the font,
since larger type fits fewer letters across the same width.

**`scrollToItem`, never `animateScrollToItem`.** He cannot watch animated scrolling, and a line
sliding into position is precisely the thing that turns his eyes inside out.

Verified: every word maps to the line that contains it, the scroll fires exactly once per line, and
it is monotonic — it never scrolls back up.

## Postponed, at his suggestion

The Avid-style settings — a triangle beside each entry that opens it in place instead of pushing a
new screen. He said postpone if it is too much for this pass, and it is: it touches all nineteen
screens. Layer 2 in `MANTRA_MANIFEST/modules/design-language.md` §10 already describes the model.

---

# 103. The switchboard becomes the whole remote

Five switches became thirteen, in three groups, and every row carries the glyph the keyboard itself
shows — the standing rule from `MANTRA_MANIFEST/modules/design-language.md` §4.

**On the keyboard:** the keys, the number row, the feature row, the suggestion row.
**The copy row:** on the keyboard, in dictation, the edit row, the buckets.
**Pressing and speaking:** the magic finger row, the volume keys, the subtitle row, full screen.

Grouped because thirteen switches in one list is a list, and three groups of four is a remote. He
described it as a remote controller and that is the right word for what it should feel like.

## The subtitle row is stored as a word, and still gets a switch

`maReaderDisplay` holds `subtitle`, `spacebar` or `off` — three values, of which two are "shown". A
switch is still the honest control: **he is asking for it or not asking for it, and *where* it
appears is a choice for the Reader screen** where there is room to explain the difference. Switching
it back on returns `subtitle` rather than whatever he last had, because what he last had was `off`.

## Two entry points, not one clever function

The first version took either a boolean or a string preference and branched. That would call
`collectAsState` a **different number of times depending on which argument was given**, which is the
kind of thing that works until it does not. There are two small composables over one shared row
instead, so every pass reads its preference the same way.

## Checked before building

All eleven icons confirmed present in the artifact, and every preference named by a row confirmed to
exist in `AppPrefs` — both by script rather than by eye. Those two are the cause of most red builds
in this project.

---

# 104. The housekeeping: every red build becomes a check

Six of the last thirty builds were red and **not one was a design mistake.** They were all the same
shape — a scripted edit leaving a duplicate declaration, an orphaned annotation, an import added
twice or removed while still used, or a symbol used above the line declaring it.

Each cost five minutes of CI and a message to him saying it had gone red. `scripts/verify.py` turns
every one of them into a check that runs in under a second.

**What it checks**, each learned from a specific failure: duplicate imports · an import above
`package` · a name declared twice in one scope · two `@Composable` in a row · an icon used without
its import · `by` delegation without `getValue` · a preference that no longer exists in `AppPrefs` ·
a `when` with no branch for a new enum entry · brace and paren balance against `HEAD` · anything
key-shaped in the diff.

**It reads only the files this commit touches.** A full-tree scan finds old debts and buries today's
mistake among them.

## The part that took the longest was not crying wolf

The duplicate-declaration check used indentation as a proxy for scope. First version: **454 false
alarms** on code that compiles perfectly. Tightened: **106**. Both useless, because Kotlin is happy
for one name to live in two scopes — and **a checker that cries wolf is worse than no checker**,
since it trains you to ignore it.

It tracks scope by brace depth now. Down to 17 across 407 files, all in files this project never
edits, and it still catches the real one.

## Tested

**All five reproduced red builds caught, 5 of 5.** Then end to end: two real faults injected into a
real file → exit 1 naming both; restored → exit 0.

---

# 105. The controller starts coming apart, at the only seam that is free

`DictateController` was 3,821 lines holding recording, transcription, history, prompts, audio routing
and provider resolution. It is 3,657 now, with **`MaProviders.kt`** holding the last of those.

## Why this block and not a bigger one

The obvious candidate was history — eleven functions, contiguous, 230 lines. It reads `_state`,
`transcribe` and the output sink, so moving it would mean **making the controller's private state
public purely to relocate code.** That trades one problem for a worse one, and the worse one is
permanent.

This block was measured before it was moved: **it reads nothing but preferences.** No state, no sink,
no scope. It answers questions — which account, which preset, which model, may this take the fast
path — from settings alone, which is exactly the shape that belongs outside a controller.

**The rule for the next extraction:** measure what a block touches before deciding to move it. A
seam that requires widening visibility is not a seam.

## What the file must never become

A place where anything is decided by side effect. Everything in it is a pure question about
configuration; the moment something there writes a preference or touches the UI, it has stopped
being resolution and belongs back in the controller.

## The red build this avoided

`maUseSyncPath` uses `SYNC_SAFE_LANGUAGES`, a **private** const still sitting in the controller.
Moved with it, since its only user went with it — and the doc comment went too, because the reason
it is an allow-list is the whole value of the line.

`scripts/verify.py` did **not** catch this and CI did: four more symbols were left behind — `File`,
`OpenAiCompatibleClient`, `MaResample` and two sync constants. **Moving code is the one edit that
breaks references without touching the line that uses them**, so both files keep their shape and
neither looks wrong.

`check_symbols_resolve` closes it, and its scope is the interesting part. Over the whole tree it
raises **256 complaints on code that has compiled for years** — annotations, nested types, things a
regex cannot see the shape of. Over a file **HEAD has never seen** it is exact, and a file that did
not exist before is precisely when code has been moved. So it runs on new files only.

Verified both ways on a genuinely new path: both missing imports named, and silent on the corrected
version.

---

# 106. The documents get their own housekeeping

The code came out of this round clean: no dead preferences, no settings entry without a screen, no
`TODO` left in the dictate package, every enum branch covered. The debt was in the documents, and it
was mine.

**`HANDOFF.md` said "last updated at build 122."** We are at 214. Its "open and not built" list named
Groq language detection — a feature that has since been **built and then removed entirely**. A
briefing that is ninety builds stale is worse than none, because it is believed.

Rewritten to what is actually true, including the honest list of what is open: the Avid settings, the
rest of the controller split, the key modules, offline retry, the English dictionary.

**`NEXT_DEFAULTS.md` is 4,000 lines and 101 sections**, and `HANDOFF` was telling every new chat to
read it second. My own rule in `MANTRA_MANIFEST/modules/handoff.md` says a document that grows
forever becomes a log and a log is read by nobody — and this is exactly that, written by the person
who wrote the rule.

It is not deleted, because the reasoning in it is the most valuable thing in the repository. It is
**named as an archive**: search it for one decision, do not read it end to end, and do not trust an
old section as current, since later ones override earlier and several describe features that are
gone.

`NEXT_DEFAULTS_INDEX.md` lists all 94 titled sections so a decision can be found without reading the
log. Regenerate it whenever sections are added.

**The rule this leaves:** when a briefing and a log both exist, the briefing must be rewritten every
time it stops being true, and the log must never be the thing a new chat is told to read.

---

# 107. The Black Void, and full screen that actually is

He is dyslexic. The reader is not a nice extra in this app; it is the part that does the most work,
and this round is about making it serve that properly.

## Full screen was never full screen

The branch sat two thirds of the way down the layout, so the early return skipped only what came
**after** it — the status line, the edit strip and the number row had already been composed. "Full
screen" arrived with three bands above it and read as a large box.

It is now the **first thing in the Column**, above the Smartbar and every row, so the return is
total: while reading expanded, the reader is the entire keyboard.

## The Black Void

A fifth style, and the one built for how he actually reads. **Full screen, true black, one word at a
time, as large as it will go.** No page, no neighbours, no box edge, no background lighter than the
void.

With nothing else on screen there is nothing to track back to, nothing to skip ahead to, and no line
to lose. The word arrives, is read, and is replaced.

**True black rather than the box's near-black**, because anything lighter draws an edge and an edge
is a second thing on screen. Long press collapses it back to the small box — the same gesture that
opened it.

## The zone keys get real icons

`n k c` were a hand-drawn keyboard outline with a letter inside, and he was right about them twice.
The neighbours in that row are drawn glyphs from one set; **a shape drawn by hand beside them reads
as an amateur patch however carefully it is proportioned.**

Numbers, Keyboard and ContentPaste now, at 24dp like every other icon, green when the zone is open.
`MaZoneGlyph.kt` is deleted rather than left unused.

**The rule this settles**, after two attempts at drawing it by hand: *use the icon set. If the set
has nothing for it, that is a reason to reconsider the key, not a reason to draw one.*

## Not built this round

**The colour picker** — a full-screen wheel, DaVinci-grade, with a draggable loupe, used everywhere
a colour is chosen. It is a real build of its own and deserves one.

---

# 108. A void that is actually the screen, and the colour wheel

## The half void

The reader asked for `keyboardUiHeight()`, so it filled the keyboard and the chat stayed above it. He
called it a half void and he was right.

**An input method draws in its own window, and that window is as tall as the view asks to be.** It
asks for `LocalConfiguration.screenHeightDp` now, so the window covers the display and the void is
the screen. The app behind pans up as it does for any tall keyboard.

## The colour wheel

Full screen, HSV, with a loupe. Hue round the rim, saturation from white at the centre to full colour
at the edge, brightness on a bar underneath — **the arrangement every professional tool uses**, and
using a different one would cost him muscle memory he already has.

**The loupe sits above the finger**, not under it. That offset is the entire reason a loupe exists;
without it a picker is a guess made with a thumb in the way.

**Held as HSV, not as a Color.** Converting back and forth loses the hue of a fully desaturated
colour — drag to the centre and out again and the hue would have been forgotten on the way.

**Two gradients, not a bitmap:** a sweep for hue, a radial white for saturation. Scales to any screen
and costs nothing.

**Live and cancellable.** The colour applies as it is dragged, so it is judged against the thing it
is for; Cancel puts back what it was, which is what makes dragging freely safe.

Wired to the reader highlight first. `maReaderHighlightHex` overrides the yellow-or-white pair rather
than replacing it, so no existing setting changes the day the wheel arrives, and a corrupted value
falls back rather than becoming black on black.

**Next place it belongs:** the subtitle page colour, the void's word colour, and the sand used for a
lit key — one wheel, everywhere a colour is chosen.

---

# 109. The dashboard, live speed, and swatches

## The speed was not slow, it was not connected

He changed the speed in settings while the voice was reading and it "started to respond after a few
sentences". That is exactly what it was doing: **the speed was read once, when a passage began
playing**, so a change reached the audio only when the next passage started.

`MaReader.setSpeedNow` sets `playbackParams` on the running player, so it applies at once. **A dial
is judged against what it is doing** — a control that takes effect later is one he cannot judge,
because by the time it works he has forgotten what he changed it from.

The karaoke needed no adjustment: it reads `currentPosition`, which is a position in the media
timeline and stays true whatever rate the media plays at. Same reason the speed came out of the
ticker in §86.

One trap in it: setting params on a *paused* player starts it on some Android versions, so the paused
state is restored immediately rather than trusted to survive.

## The dashboard

Long press on the reader key no longer leaves the keyboard for the settings app — **it opens three
dials over the reading: voice, speed, highlight.** Same long press closes it.

**Half the screen, not all of it.** The void takes the display because one word needs no context;
this is the opposite — he is adjusting *while listening*, so the reading has to stay visible. Covering
it would mean adjusting blind, which is the problem this solves.

**Only three.** Everything else about the reader is chosen once and left; putting it here would bury
the one thing wanted mid-sentence. **It closes itself when the reading stops**, so he can never be
left with a panel to dismiss.

## Swatches beside the wheel

Adobe puts swatches in every program and keeps the picker behind them, because the two answer
different questions: **a wheel answers "which colour", a grid answers "which of the ones I keep
using"** — and nine times in ten it is the second.

**Black and white first**, and that ordering is the design. Pure white is a single point at the
centre of the brightness axis and pure black is another, so on a wheel they are the two colours a
finger is least likely to hit exactly. A grid makes them one tap. Then greys, the app's own amber,
then hues in spectrum order, because that is where the eye looks without reading.

Swatches is the default view. Four of them also sit on the dashboard, where there is room for four.

## The verifier learned one more thing

It called `jetpref` and `kotlinx` imports unresolved, because it searched only `app/` and `lib/`.
Reporting that a dependency lives outside the repository is true and useless. It now checks only
imports this repository could satisfy.

---

# 110. Switchers get a ring, the switchboard gets his order, and the volume keys say why

## The volume keys

**Nothing was lost in the split.** `onKeyDown`, `onKeyUp`, `maHandleVolumeKey` and
`maFinishVolumeKey` are all intact and correct — up records, down while recording stops and sends,
down otherwise presses the target. The code that took weeks is there.

What is missing is an explanation when it declines, and there are exactly two reasons: **the Volume
keys switch is off**, or **the keyboard is not on screen**. Those look identical from outside, and
one of them is now reachable by a stray tap on the switchboard that did not exist a week ago.

Both now write a line to the log naming the cause. The next time these seem dead, `log` answers it in
one line rather than costing a session searching for code that was never lost.

## Switchers wear a ring

The row mixes two kinds of key that look identical and behave nothing alike: **a clipboard icon that
pastes, and a clipboard icon that shows the copy row.** Same picture, opposite jobs. Nothing on the
face can separate them, so the distinction goes around the key.

A ring in the ordinary ink at 55% — **not a colour.** Colour is the state channel: green means a
switcher is ON, and a second colour meaning "this is a switcher" would give one channel two jobs. The
ring says *kind*; the ink says *state*. Monochrome, as he asked.

Worn by the three zone keys and the language badge. Any future key that switches rather than types
passes `switcher = true`.

Drawn **after the background and on the key's own shape** — the first attempt put it on the modifier
before `ThemedKey`'s own padding, which would have placed it outside the key entirely.

## The switchboard is one list, in his order

Three headings and a paragraph of prose are gone. He said he already knows what "on the keyboard"
means, and he is right: **a heading naming a category he can see is a line to scroll past.** Titles
and icons only.

And it drags. Every list he reads often is arrangeable — the settings home, the rows, the copy row —
and the switchboard was the last one still in an order somebody else chose. **A shipped order is a
guess, and a guess repeated every day is worse than a control.**

**Stored by id, never by position.** A list of numbers would mean something different the moment a
switch is added. Unknown ids are dropped, missing ones **appended rather than hidden**, so a switch
added next year is reachable by somebody whose arrangement predates it.

Tested: 5 cases, and the invariant that every switch is present exactly once whatever is stored —
empty, unknown, duplicated, partial.

---

# 111. The reader that went dead, and brightness instead of colour

## Why the speaker key died

Not fragmentation, and no module was lost. **`state` and `player` are two facts that can disagree**,
and when they do the key goes dead in a way that looks like nothing at all.

With `state = SPEAKING` and no player: a press pauses nothing and sets PAUSED, the next press starts
nothing and sets SPEAKING, and it flips between them **forever**. No sound, no way back to IDLE, and
the reader is dead until the keyboard is rebuilt. That is exactly what he described.

They come apart easily. The completion handler releases the player and hands off to the
scroll-and-continue coroutine; if that coroutine is cancelled in between — keyboard hidden, process
trimmed — the state is left claiming to read with nothing behind it.

**The fix is not to audit every path that could cancel.** There is always one more, and the audit has
to be redone whenever the code changes. `toggle` now checks the claim against the thing before acting
on it: **the player is the truth, `state` is a claim about it.** No player means idle, whatever the
machine believes.

Verified against the exact sequence: the old machine flips paused/resumed forever; the new one starts
reading on the first press. Normal use is unchanged.

## Brightness, not colour

Four coloured chips became one **grey-to-white ramp**. A highlight over white text does not need a
hue — it needs to be *more* than the page, and that is one axis.

**Bounded at half grey.** Below that the mark stops out-reading the page and does the opposite of its
job; a control should not offer settings that defeat it. **Neutral at every position** — r = g = b —
or it is a colour picker pretending to be a brightness control.

Tested both directions: position → hex → position round-trips exactly, never darker than `#808080`,
always neutral, and an old coloured value falls back sanely rather than throwing.

## Written into the manifest

Sections 10 to 13 of `MANTRA_MANIFEST/modules/design-language.md`: a dial is judged against what it
is doing; a switcher is not an action and the face cannot say so; brightness is not colour; state and
reality are two facts that will disagree.

---

# 112. Three integrity passes

Asked for a check across everything, three times, each reading the last.

## Pass 1 — structure, whole tree

407 files swept for duplicate imports, doubled annotations, icons used without importing them,
delegation without `getValue`, and Compose helpers without theirs.

**Three hits, two false.** `SpaceBar` was inside a comment explaining why that icon is *not* used;
the twelve "delegation without `getValue`" were custom non-Compose delegates in inherited
FlorisBoard files.

**One real:** `MaRowsScreen` had `@Composable` above a doc comment above the function. It compiles —
the annotation still binds — but it is the exact shape that caused red build 209, where a second
annotation hid behind a doc comment. Moved below its doc.

## Pass 2 — do the parts agree

Every enum entry has a branch in every screen that switches on it; every settings entry has both a
route and an icon; every route object is registered in the nav graph; every `ma*` preference read
anywhere exists in `AppPrefs`.

**All clean — but the first run reported `MaSwitchboardOrder: 0 entries`**, which is not a pass, it is
a check that never ran. Its enum is indented differently from the others, so the pattern missed it.
Re-run with the right pattern: 12 entries, all covered, all preferences real.

**A check that finds nothing and a check that runs nothing look identical from the outside.** Print
the count, always.

## Pass 3 — can a state machine reach a dead end

Reader, volume keys and send arming, each walked exhaustively rather than sampled.

- **Volume:** 1,000 hold lengths — 0 do both, 0 do nothing.
- **Send:** no sequence produces a send that was not asked for; none leaves the arming set.
- **Reader:** without the heal, **`SPEAKING` and `PAUSED` with no player are silent forever**. With
  it, none.

**And the reader test was wrong twice before it was right.** First it called pausing a live player a
dead end. Then it reported AUDIO from a null player — modelling the very bug away. Only after
modelling `start()` on nothing as *silence* did it reproduce the failure he actually had.

**The rule: when a test says the bug does not exist, suspect the test.** It had already been fixed in
the code, so the test agreeing was worthless — what proved it was the test failing on the old
behaviour and passing on the new.

---

# 113. Swatches as asked, effects not voices, and the volume keys prove themselves

## The swatches I should have built the first time

He asked for swatches. I built a slider and wrote a paragraph justifying it. **That was not a better
answer to his question, it was a different question answered instead.**

**Seven greys, dark to white.** Seven because a row of seven is read at a glance and chosen without
aiming; a slider needs a precise finger and hands back a number nobody asked for.

**The dark end is not black.** `#000000` on a near-black page is a square that appears to do nothing,
and a highlight set to it disappears into the text — a setting that defeats the control offering it.
The row starts at `#6E6E73`, the darkest that still reads as a mark.

## Effects, not voices

The voice is chosen once and left; it is taste, settled in an afternoon. **The effect is what changes
per passage** — void for something hard, highlight for skimming, karaoke to see the sentence coming.
That is what belongs on a panel opened mid-reading.

The six effects now live in `MaReaderEffects`, shared by the dashboard and the Reader screen, so a
new one cannot be added to one and forgotten in the other.

## The volume keys, made to prove what happened

The handlers are correct — read twice now, line by line. But *"the volume keys do not work"* has
**three** causes that are indistinguishable from outside: **the events never reach the keyboard**, the
switch is off, or the keyboard is not on screen. Only the last two were logged, which is why two
rounds of checking the handler found nothing.

Every volume press is now logged **before any decision is taken**. If nothing appears in the log when
he presses volume, the events are not arriving and no amount of reading the handler will help,
because it is never called.

And a disabled switch now says so **out loud, on screen** rather than only in a log. That switch did
not exist a week ago, it sits in a list, and one stray tap turns it off. An invisible failure cost him
days of believing a feature had been deleted; a toast turns it into a fact he cannot miss.

---

# 114. The seventh setup page is gone

"You're all set" said nothing that was not already true, and everything it pointed at was in the
settings list its own button opened. **A page whose only content is a summary of the pages already
read is a page to press past.**

The step, its five strings and its entry in the enum are deleted. The flag it set and the navigation
it did now live on a **Finish** button at the end of the accessibility step, so the last thing he does
in setup is a real step rather than an acknowledgement of one. Six steps, not seven.

The router's fallback pointed at `FinishUp` when nothing was left to grant; it lands on the last real
step now. Translated copies of the dead strings are left in the locale files — unused strings cost
nothing, and rewriting forty of them to delete text nobody reads is not a good trade.

---

# 115. A key that copies the screen

Everything this keyboard does to another application depends on the accessibility tree, and when the
finger cannot find a button or the reader cannot find the words, **that tree is the only place the
answer lives.**

Reading it used to mean summoning the wand, pressing the thing, and copying from the bar it raised —
a diagnostic that needed a rehearsal, at the moment something is already going wrong.

**One key.** Press it and the screen underneath is on the clipboard, ready to paste into a chat.

It calls `DictateAccessibilityService.dumpScreen`, the same path the wand's copy button uses, so the
two cannot produce different dumps. The toast says how many characters were copied — a dump that came
back tiny is itself the answer, and a silent success looks identical to a silent failure.

**A stack of layers, not a bug icon.** What it reads is the layer under the picture, which is exactly
what the tree is. Nothing here is broken, and an instrument should not look like an alarm.

Add it from Feature row settings; it is called **Dump the screen for diagnosis**.

---

# 116. The dead end at the end of setup, and why the wizard kept coming back

## The dead end, which was mine

Removing the seventh page put **Finish** inside the `else` of the accessibility step — the branch that
only draws when accessibility is **off**. His has been on for weeks, so he reached the last page of
setup with no button on it and no way forward. **A dead end at the end of a wizard**, which is the
worst place to put one.

Outside the branch now. Setup ends whatever the state of that switch, because the switch is optional
and finishing is not.

## Why it appeared on every update

Inherited from upstream, and it had nothing to do with updating:

```kotlin
if (API33+ && notificationPermissionState == NOT_SET) {
    prefs.internal.isImeSetUp.set(false)   // "show the setup screen again"
}
```

**If the notification permission has never been answered, the whole setup reappears.** On a phone
where that permission is simply never granted, the condition is true **forever** — so every launch
after every install walked back through six pages he had already finished, several times a day.

**Setup reappearing is not a way to ask for a permission.** Once somebody has finished it, *finished
is a fact and not a state to be revoked* by an unrelated toggle. The reset is gone; anything that
still needs asking asks where it is needed.

## Skip setup

A link in the header, on **every** page. Skipping one slide was never the problem — the problem is
being asked at all, six times, by a wizard whose entire content he wrote.

**In the header rather than the footer**, so it is reachable on a page with no other button and
cannot be scrolled past on the pages that have several. Nothing is lost by taking it: every grant it
offers is also in Settings, and the ones already granted stay granted.

**One `finishSetup`**, shared by Skip and Finish, because the flag must be written or the wizard
returns on the next launch, and `popUpTo` must be inclusive or Back walks straight into it again.

## Also fixed: reading anything long

His log gave the answer to the other bug in one pass. Every passage of **1,906 characters or fewer
spoke; every one of 2,110 or more came back HTTP 400** — twenty-three readings, no exceptions either
side of that gap. `MAX_CHARS` was **4,000**, chosen by me and never tested against the service, so a
long screen simply refused to read and filled the log with 400s that looked like a key fault.

Now 1,800, measured. **A cap invented rather than measured is wrong in one direction and silent
about it.**

---

# 116. The dead end I built, and the character cap I invented

## The wizard could not be finished

Removing the seventh page moved Finish onto the accessibility step — **inside its `else` branch**. So
anyone whose accessibility was already running, which is everyone after the first install, reached
the last page and found a line of text and nothing to press. **A dead end at the end of the wizard.**

And because finishing is what writes `isImeSetUp`, the flag was never written, so setup returned on
every launch. One misplaced brace produced both complaints.

**The rule: the way out of a screen must never be inside a branch.** A step can have nothing left to
do; it can never have no way forward.

`finishSetup` is one function now, passed into the steps rather than rebuilt inside them, because the
Skip link at the top does the same thing and two definitions of "setup is over" would eventually
disagree about whether the flag was written.

## The other reason the wizard kept returning

Inherited from upstream: if the notification permission had never been answered, the app set
`isImeSetUp = false` on every launch. On a phone where that permission is simply never granted — his
— that condition is true forever.

**Setup reappearing is not a way to ask for a permission.** Once somebody has finished it, *finished
is a fact, not a state to be revoked* by an unrelated toggle. Left as it was for a genuine first run,
where the flag is already false and the line changes nothing.

## Speechify refuses over ~2,000 characters

From his log, 23 readings: **every passage of 1,906 characters or fewer spoke; every one of 2,110 or
more came back `speak refused, http 400`.** No exceptions on either side.

`MAX_CHARS` was 4,000 — a number I chose and never tested against the service — so long screens
simply refused to read and the log filled with 400s that looked like a key problem.

Now 1,800, below the largest measured success with room for the service to tighten. **A cap invented
rather than measured is wrong in one direction and silent about it.**

---

# 117. The volume keys, rewritten, with no way to switch them off

Rewritten as `MaVolumeKeys`, specified in **`VOLUME_KEYS.md`**, and **the setting is gone** — no
preference, no switchboard row, no gestures entry.

That setting is the whole story. It defaulted on, it sat in a list of thirteen switches that had just
become draggable, and one touch turned it off in silence. He lost days believing the feature had been
deleted; I spent three rounds reading a handler that was correct the entire time.

**A control that can silently disable the thing somebody uses most is not a feature, it is a
trapdoor.** The specification says not to add it back and why.

Behaviour unchanged and now written down: up taps to record and to stop; down while recording stops,
transcribes and presses Send when the words land; down otherwise presses Send; either held is the
real volume. Decision on release, send armed rather than performed, disarmed on every abandoned path.

## One word is gone

It was the void with a smaller word and a box around it — not a second effect, the same one done less
well. A stored `oneword` falls through to `highlight`, which is the safe direction: a page he can read
rather than one word he did not choose.

## A way out of full screen that can be seen

Long press still closes it, but a gesture is invisible and a full-screen view with no visible exit is
a trap the first time it is forgotten. A dark grey **✕** in the corner, with a touch target larger
than the glyph. Deliberately near-invisible: an escape hatch, not a control.

## Two tools that were wrong, and cost more than the bugs

**The balance checker in `verify.py` reported the largest file in this project as two braces out when
it was balanced.** It stripped block comments with a regex over the whole file. Three rounds went into
hunting a fault that did not exist. It scans character by character with real state now — **a checker
that is wrong is worse than none, because it is believed.**

**And my own cut of the One word block took the Void's comment with it**, because I chose the
boundary by eye rather than by structure. Restored from HEAD and cut again by locating both blocks
first. When removing code, find the end of what you are removing before removing the start of it.

---

# 118. The reader becomes the thing the keys serve

## The twenty seconds

**The key ring restarted at key one on every single read.** Every dead and every throttled key ahead
of the good one was paid for again — a full network round trip each — before a word could be spoken.
His log shows it plainly: `key 1 throttled, rolling forward`.

It resumes from the key that answered last. Measured against his situation — 21 keys, five tired ones
at the front — that is **six requests per read down to one.** The skipped keys are not forgotten: the
walk wraps, so a key that recovers is found again.

## Nothing interrupts a reading

`onStartInputView` stopped the reader, and it fires for a notification, a dialog, switching views,
pinning the keyboard — every one killed a reading mid-sentence. **A reading is a task he started
deliberately and can end with one press.** It now survives all of that, and the keyboard being
collapsed by the system.

Three things stop it: the reader key, the ✕, and the end of the passage.

## The volume keys drive the reading

Nobody dictates into a screen they are listening to. While a reading is in progress, **up taps skip
forward and down taps go back**; holding either is still the volume.

Not a setting — the keys follow what is on screen. A control that means the obvious thing for the
situation in front of him needs no mode and no memory.

**Back is two-step**, as every media player is: part-way through a sentence it restarts that
sentence, pressing again goes to the previous one. 1.5 seconds is the line between "I missed that"
and "I want the one before".

## The swatches collapse

Seven open by default is seven things to look past every time the panel is raised, and the panel is
raised for speed far more often than for colour. One swatch showing the current choice; tap to unfold,
and it folds itself away once chosen — the row exists to answer one question and it has been answered.

---

# 119. Down is forward, and back is one step

Both corrections from him, both about the same instinct.

**Down goes to the next sentence, up to the previous.** The text moves down the screen as it is read,
so down is where the next sentence is, and every scroll on the phone has already trained the hand.
Mapping up to "next" because up is bigger imposes a rule from arithmetic on a rule from movement.

**Back is one step. No replay window.** The first version restarted the current sentence if he was
already part-way into it — the media-player convention, and wrong here. A skip key skips. To hear a
sentence again he presses up then down: two deliberate presses, and less thought than a key whose
meaning changes with how long he has been listening.

**A control that does two different things depending on timing is one that has to be predicted.** At
speed, predictable beats clever. `REPLAY_WINDOW_MS` is deleted rather than raised, because any value
for it is the wrong idea.

Checked: up-then-down returns to the same sentence from anywhere except the first, where there is no
previous one and up restarts it instead. Each press moves exactly one sentence, and the last sentence
holds rather than running off the end.

---

# 120. Which key is speaking, in the corner

A dim `3/21` in the bottom-right of every reading view: the Speechify key that spoke, and how many
are on the ring.

**A ring that walks silently is a ring nobody can reason about.** When a reading is slow to start,
this says at once whether it is on key 3 because two are tired, or on key 1 and simply waiting for the
network — a distinction that used to cost a log export and a round trip to me.

**Updated on every attempt — it IS the progress bar.** The first version updated only on success, on
the reasoning that a flickering number is a progress bar rather than an answer. He wanted the progress
bar, and he was right: while a reading is slow to start, watching the number climb *is* the diagnosis.
A still `1` means waiting for the network; `1 2 3 4` means the ring is walking past tired keys.

**One number, no total.** He can count his keys in settings, and a second number on a screen built for
one word at a time is one more thing competing for the eye.

Held as Compose state rather than a plain field, so it actually refreshes — a `@Volatile Int` would
have been correct and invisible, which is the worst combination for an instrument.

As dim as the close mark, and for the same reason: it is an instrument, not a control, and anything
brighter would compete with the words. Drawn only when there is something to say.

**In every reading view**, not only full screen, because the question it answers is asked when
something is wrong, and being wrong does not pick a view first.

---

# 121. A line that says it is listening

The volume keys record whether or not the keyboard is on screen, and he found that by accident and
kept it — dictation without opening anything is the feature, not a side effect.

But **a recording nobody can see is a recording he does not know he started**, and the first he would
learn of it is a transcript arriving from a conversation he had with somebody else in the room.

**Anything that captures a microphone must be visible while it does.** Not as a courtesy — as the
minimum honesty of a device that listens.

## A line, not a bubble

There is already a floating button and it is a *control*: draggable, pressable, occupying a corner.
This carries one bit — *this is recording* — and the smallest shape that carries one bit is a line.

Three device-pixels at the very bottom, full width, not touchable and not focusable, sitting in the
gesture bar's own margin so it covers nothing.

**Red, and this is the one place in this app where red is right.** Buckets stopped turning red when
full and untranscribed recordings are blue, because neither is a fault — but a live microphone
genuinely is something to be aware of.

## Only when the keyboard is hidden

With the keyboard up he can already see the timer, the waveform and the red dot. A second indicator
would be a second thing saying what the first one says.

Driven by combining the recorder's state with `_imeVisible` rather than by an event, so **it cannot
be left showing by a path nobody thought of** — every change to either fact re-answers the question.
`show()` is idempotent because that flow emits on every change and adding a window twice throws.

---

# 122. The strip says what it is, and the history badge answers again

## The history badge

It called `MaLanguage.badge()`, which reads the preference directly. **Compose cannot see that
dependency**, so the label had no reason to redraw when the preference changed — the tap worked, the
language changed, and the badge went on saying what it said before. From outside: a button that does
nothing.

The label now comes from the state already being collected, so the redraw is a fact rather than a
hope. **A value read outside Compose's knowledge is a value that will eventually stop updating**, and
it fails as "the control is broken" rather than as anything that looks like a stale read.

Its touch target grew too: it was text with padding, and text is exactly as wide as its letters — two
of them here. On a list where every other control is a full row, a target the size of "HR" is one a
thumb misses.

## The recording strip

He asked for **the recording bar**, and I built a strip with the word `recording` on it — my own idea,
answering a question he had not asked. He had already described it and sent a screenshot.

It is the bar now: **ENG, the bin, the red dot and the clock, and send.** The same controls doing the
same things, so the interface does not change identity depending on whether the keyboard is up — it
moves back to its usual place when the keyboard arrives, and that is all.

**Tapping the numbers brings the keyboard up.** His instruction, and the right target: the clock is
the biggest thing on the bar and the one the eye is already on.

Plain Android views rather than Compose, because a Compose overlay needs lifecycle and saved-state
owners this service does not have. The clock reads the recorder's own state once a second instead of
counting for itself, so a pause is honoured and the two views can never disagree about how long he
has been speaking.

`FLAG_NOT_TOUCHABLE` is gone since every glyph is a control; `NOT_FOCUSABLE` stays, because it must
never take the cursor from the field being dictated into.

---

# 123. The language badge moves onto the recording it describes

**The header badge is gone.** It said what the *next* send would use — a fact about the future,
attached to a list of the past — and it was clipping to "HR" besides.

**Each recording now carries its own badge: the language it was actually sent in.** That answers the
question being asked in that screen: *this came back wrong, what did I send it as?*

**Tapping it switches the language and re-transcribes in one press.** Correcting the mistake is one
gesture, because that is what the mistake costs him.

**"Transcribe" is now "Retranscribe"** — the whole word, and the right one: this recording has
already been transcribed once. It had been clipping to "Transcri", so the row is tighter too: four
labels share the line now and the words carry the meaning, so the space between them was doing
nothing but pushing them apart.

## Still owed: the overlay bar, pixel for pixel

He has asked three times and I have approximated it twice, which is worse than not starting.

The obstacle is real and worth writing down: the bar is a **Compose** composable using `SnyggIconButton`
and the IME's theme scope, and the overlay is a plain window owned by the accessibility service —
which has no lifecycle owner, no saved-state registry and no Snygg provider. Rebuilding it in Views is
what produced two near-misses.

**The answer is to host the real composable**, by giving the overlay window the owners Compose needs
and the theme provider the bar reads. That is its own build, and it is the next one.

---

# 124. The badge appears, the words are whole, and the header balances

Three faults, one cause: **I sized words by fractions of the line.**

A weight is a promise about width that a word cannot keep. Given a quarter each, "Insert" clipped to
"Ins", "Delete" to "Del", "Re-Transcribe" to "Retranscri", and the badge — the narrowest — was
squeezed to nothing and **never appeared at all.** He reported it as missing; it had been drawn with
no room to exist.

Sized to content, spread with `SpaceBetween`. Each label says its whole word and every gap is equal.

**The badge is `[ENG]` / `[HR]`**, in square brackets so it reads as a label on the recording rather
than a fourth action. Tap it: the language swaps and the audio goes back up in one press.
**Re-Transcribe** keeps its dash and its final letters.

## The header

Removing the old header badge left the cog stranded beside the back arrow, with the whole row leaning
into the left corner. The title is back between them and the cog is at the far edge.

**A row with everything at one end is not a row, it is a pile.** Written into
`MANTRA_MANIFEST/modules/design-language.md` §10 along with the rest: equal distances everywhere, and
**after removing anything from a row, look at the row** — balance breaks silently, and it breaks in
whatever was left behind.

---

# 125. The VU meter, from the module rather than from scratch

Third time asked, and he was right to keep asking.

**It reads `DictateController.audioLevel` — the same StateFlow the keyboard's meter collects.** The dB
conversion, the −54 floor, the 0.6 dB peak decay and the three colours are lifted from
`MaRecordMeter` unchanged. **One source of numbers drawn twice, rather than two meters that happen to
look similar.**

Drawn with a Canvas rather than composed, because the overlay window has no lifecycle owner for
Compose to attach to. **That is a difference in the brush, not in the picture** — and it is the whole
of what "the module" could not be reused literally, which I should have said two builds ago instead
of quietly approximating.

**Peak hold, because a bar alone is a flicker at speech rates.** The mark falls at 0.6 dB per frame:
slow enough to read, fast enough to follow a sentence. That is what makes it a meter rather than a
light.

**Fast attack, slow release**, the asymmetry every hardware meter has — an attack that lags feels
dead, a release that snaps feels nervous.

It sits directly under the clock, as one column, which is how the keyboard's bar arranges them: the
numbers with the level beneath, not two things sharing a row.

---

# 126. The overlay hosts the real bar

Asked four times, approximated three. **It is the same function now: `DictateSmartbarUi`, called
directly.** Same controls, same icons, same VU meter, same theme — anything that changes on the
keyboard changes on the overlay, because it is the same code.

## What was actually in the way

A window added by an accessibility service has **no Activity behind it**, and `ComposeView` refuses
to compose without three things an Activity supplies invisibly: a **lifecycle owner**, a
**saved-state registry** and a **view-model store**. The failure is a blank view, not an error, which
is why "reuse the composable" kept collapsing into "reimplement the composable".

`MaOverlayHost` supplies all three in eighty lines.

**Eighty lines against three failed rebuilds.** That trade should have been obvious two builds
earlier, and the rule is worth keeping: **when reuse is blocked by plumbing, build the plumbing.** A
copy of a living thing has to be maintained forever and drifts the first time the original changes —
which is exactly what happened here, three times.

The lifecycle goes RESUMED on attach and DESTROYED on removal. A registry left at CREATED composes
but never animates, so the meter would sit still — a failure that looks like a broken meter rather
than a missing lifecycle.

---

# 127. The offline note

He dictates with no text field open — the volume keys record whether or not a keyboard is up — and
until now the transcript went into the history looking exactly like every other entry: a first line, a
timestamp, and nothing saying **it was never delivered anywhere.**

**A dictation with nowhere to go is a note, and a different kind of thing.**

- **Its own source.** `DictateHistorySource.OFFLINE`, set from the commit's own result rather than
  guessed: `commitOutput` already returns whether the words landed, and that is the fact.
- **Its own title**, written by the model. He comes back to these hours later, and a wall of first
  lines is not something anybody can search by eye.
- **Its own colour** — teal, deliberately not amber and not red. Amber means "the live one"
  everywhere else here and red means a fault; a note he dictated on purpose is neither.

## Three decisions worth keeping

**Only notes get titled.** Every other entry was watched arriving in a field. Titling all of them
would spend his money labelling things he already knows.

**The title never fails the save.** Wrapped, and a failure returns empty — which the list renders as
the first line, exactly as before. A note without a title is a note; a note lost because a titling
call timed out is not.

**Under forty characters gets no title.** A six-word title on an eight-word note is not a summary, it
is the note again in worse handwriting.

## The migration

The title needed a column, and this database falls back to **destructive** migration — which would
have deleted every recording he has. `MIGRATION_4_5` is one line of SQL. **His history is not
replaceable and a column is not expensive**, so the fallback must never be the plan.

---

# 128. The crash I shipped in 239

Build 239 hosted the real bar and **would have crashed the moment it was shown.**

`FlorisImeTheme` reads `LocalWindowController`, and that local's default is not a fallback — it is
`error("only available within an IME view")`, a hard throw. I wrapped the composable in the theme and
never provided the local, so the first background recording would have taken down the composition.

**That is exactly what "it does not survive without the original keyboard" was**, and he described it
before I had built it.

Fixed by providing one. `ImeWindowController` takes preferences and a scope and needs no IME; all the
theme wants from it is a font scale.

## And a guard, because the blast radius was wrong

The accessibility service owns the magic finger and the reader as well as this window. An exception
while adding the bar would have killed all three — **losing the finger and the reader because a strip
of UI could not be drawn is a wildly disproportionate way to fail.**

It is wrapped now, and a failure logs and detaches the host. The recording is unaffected either way:
the microphone does not run through this window, so the worst case is recording without seeing the
bar, which is where the feature started.

## The lesson

**A composition local whose default is `error()` is a dependency, not a convenience.** Grepping for
what a composable *calls* found nothing; the dependency was one level down, in what the theme reads.
When lifting a composable out of its home, check what its wrapper reads too.

---

# 129. Why the bar recorded but never appeared

He asked the right question: **the hand-built version showed and the real one did not — what is
different?** Comparing them found it in one line.

| | views version (worked) | compose version (invisible) |
|---|---|---|
| window height | `WRAP_CONTENT` | `WRAP_CONTENT` |
| **who decides the height** | **the LinearLayout, from its children** | **`DictateSmartbarUi`, via `fillMaxSize()`** |
| children with a fixed size | yes — dot 10dp, meter 4dp, text | no — everything fills the parent |
| **resulting height** | sum of the children | **zero** |
| did it throw | no | no |
| did recording work | yes | yes |

`DictateSmartbarUi` sizes itself with `fillMaxSize()`, which is exactly right **inside the keyboard**:
the smartbar slot has a fixed height and the bar fills it. My window is `WRAP_CONTENT`, so the
parent's height is whatever the child asks for — and **a child asking to fill its parent, in a parent
sized by its child, resolves to nothing.**

The window was added. The composition ran. The recording worked. It was nought pixels tall.

Fixed by wrapping it in a `Box` of `FlorisImeSizing.smartbarHeight` — the same number the keyboard
gives it, so the bar is the size it is at home rather than a size invented here.

## The pattern, twice in three builds

`LocalWindowController` threw; this one was silent. Both are the same mistake: **a composable carries
assumptions about its parent that are invisible in its own source.** The theme it is wrapped in, the
locals it reads, the height it expects to be given.

**When lifting a composable out of its home, the question is not what it calls — it is what it was
being given.**

---

# 130. Reverted to the version that works

Back to the hand-built bar from build 238: `LinearLayout` with ENG, the bin, the red dot, the clock,
the VU meter and send. It draws, and that is the whole of what matters.

The Compose route is abandoned. Two obstacles were found and fixed — the missing
`LocalWindowController`, then `fillMaxSize()` resolving to zero — and it still did not appear, which
means there is at least a third I have not found. **Three builds of a feature that already worked,
spent on making it "properly" reuse code, is a bad trade whichever way it ends.**

`MaOverlayHost` is deleted rather than left in the tree. An unused class that solves a problem nobody
has is a thing the next person has to read and decide about.

## What this cost, and the rule

He named it correctly: **go back to the one that was working.** I had a working bar at 238 and spent
239 to 242 replacing it with something that did not, because "the same function drawn twice" is a
better *argument* than "two implementations of one bar".

It is not a better outcome. **A duplicated thing that works beats a shared thing that does not, and
the time to find that out is one attempt, not three.**

The duplication is real and it will drift — if the keyboard's bar changes, this one will not follow.
That is now a known, written-down cost rather than a hidden one, and it is the cheaper of the two.

---

# 131. The badge becomes a switch, and the note carries its own colour

## Two steps, not one

The badge swapped the language **and** sent the audio back up in the same tap. That sounded
efficient and it is wrong: the tag could not be looked at or corrected without spending a
transcription, and a mis-tap cost a call to the provider.

**The badge now only changes what the tag says. Re-Transcribe sends, in whatever the badge shows.**

**A switch that also acts is two controls wearing one coat.**

The choice lives beside the row rather than in the entry, because it is a decision about the *next*
send and not a fact about the recording: until Re-Transcribe is pressed, the recording is still
exactly what it always was.

## Delete had fallen off the row

It was in a stale second copy of `HistoryPanelRow` that an earlier edit of mine left behind — and
removing that revealed a second duplicate, `MaDeleteChooser`, which then failed the build with
"conflicting overloads".

**Kotlin only complains when something calls the ambiguous name**, so a duplicate that nothing calls
yet sits there silently until an edit wakes it up. `verify.py` checked duplicate properties but not
duplicate functions; it does both now, **keyed by name and parameter list** — comparing names alone
raised seventy complaints about legitimate overloads, which is how a checker gets ignored.

## The note carries the colour, not its title

The colour was on the title, so a note whose title failed to generate looked exactly like every other
entry — **the very case where recognising it matters most.** The transcript itself is teal now, so an
offline note is identifiable whether or not the model ever named it.

---

# 132. Bullets, and Delete all at the top

## Why Delete kept disappearing

`Arrangement.SpaceBetween` **spaces children; it does not shrink them.** When the four words were
wider than the row, the last one — Delete — simply left the screen. It happened twice, and neither
time did anything report it: **an off-screen child is not an error.**

The actions are one centred line joined by `·` now. Words plus two characters each, no arrangement to
overflow, nothing to clip. And it reads as what it is: a short list of things he can do to this
recording.

`Insert · [ENG] · Re-Transcribe · Delete`

## Delete all belongs on the list

It was reachable only per entry, so clearing a long history meant pressing Delete once per recording.
**An action whose scope is the list should live on the list**, not be assembled out of repeated
single actions.

At the top, in the header, red, and it asks first — everything else here can be undone or
re-transcribed; this cannot, and it sits two taps from what he came to do.

---

# 133. Two real causes, after four cosmetic attempts

He asked why this is going slowly. The answer is in this entry: **I changed the appearance four times
without once checking what the components actually were.**

## Delete kept leaving the screen because these were BUTTONS

`MaHistoryAction` wrapped every label in `SnyggButton` — a Material button, with a **minimum width
and its own internal padding.** Measured: the four labels as buttons need about **419dp** in a row
about **360dp** wide. They never fitted, in any arrangement.

I tried weights, then `SpaceBetween`, then bullets, then centring. **All four were arrangements, and
no arrangement shrinks a child.** As clickable text the same four labels come to 300dp and fit with
room over.

## Delete all crashed because a dialog needs an Activity

A Compose `AlertDialog` opens a real platform Dialog, and a dialog needs a window token from an
Activity. **An input method has no Activity** — its window belongs to the system — so the first tap
took the keyboard down.

`MaDeleteChooser`, ten lines away, was already inline for exactly this reason. **I put a dialog next
to a working example of why not to.**

## The rule, since he asked

**When a screen already solves a problem, copy that solution; do not reach for the standard
component.** And when a fix does not work, the next attempt should change *kind*, not degree — four
attempts at arranging a row that could not be arranged is three more than the evidence justified.

---

# 133. EN in the circle, and the deletes tested properly

## EN, not ENG

Three letters do not fit inside a round key and the last one was being cut at the edge. Two letters
fit, and HR is already two — so the pair is even, and **a badge whose width changed with the language
would shift every key beside it each time he switched.**

The history panel keeps ENG, where the row is wide and the extra letter costs nothing.

## The deletes, put through the four tests

**Test 1, the logic alone.** Eleven cases against a model of the three deletions, holding one
invariant: *no row may point at a file that is gone.* Delete, audio-only, an entry with no audio, a
file delete that fails, clear-all with an undeletable directory, deleting twice. **0 failures** — the
order in the store is already right, file first and row second.

**Test 3, the ugly cases** — and this is where the real faults were, after the dialog crash was
already fixed:

- **`clearAll` ran on the main thread.** It walks the audio directory and deletes every file in it.
  With a few hundred recordings that blocks the keyboard long enough for Android to kill the input
  method for not responding. The other two were already on IO; this one was not.
- **None of the three was wrapped.** A delete can throw — a file held open by a media scanner, a
  revoked permission — and **an exception escaping a launched coroutine takes the keyboard down.** He
  asked for delete that does not crash; the crash is not always in the dialog.

All three are on IO and guarded now, and a failure writes a line naming what threw.

## Not fixed, and worth knowing

**A row whose audio file was removed from outside the app** — storage cleared, files pruned — still
shows Re-Transcribe, and pressing it will fail. The row is not corrupt and nothing crashes; the
button is simply offering something that cannot happen. Fixing it means checking the file exists when
the row is drawn, which is disk work in a list, so it needs doing carefully rather than quickly.

---

# 134. A row that lost its audio now says so

A row records where a recording was put; it cannot know when something else removes it. Clearing the
app's storage, a restore that carried the database but not the audio, an OS pruning files — each
leaves a row insisting it has audio that is gone, and the list then offers **Re-Transcribe on a file
that cannot be read.**

## Repair, not hide

The quick fix was to check the file while drawing each row and quietly not offer the button. That is
**disk work inside a scrolling list**, it runs again on every recomposition, and it leaves the
database still lying — the same wrong answer handed to every other reader.

`repairMissingAudio` writes the truth once instead: any row whose file is missing has its `audioPath`
cleared. **The transcript is untouched — losing a file is not a reason to lose the words.**

## Three things that make it safe

**It cannot loop.** A row is only written when the path is set *and* the file is missing; afterwards
the path is null, so the next pass skips it. The write triggers one more emission of the list, and
that emission finds nothing to do.

**A failed check leaves the row alone.** `exists()` throwing — a revoked permission, an unmounted
volume — returns *true* by default, so an unreadable check can never destroy a good pointer. **Only a
definite answer is acted on.**

**Once per opening, not per row.** `LaunchedEffect(Unit)` on IO, in both the keyboard panel and the
settings screen, because they read the same rows and a fix in one that leaves the other lying is not
a fix.

It says nothing when nothing was wrong, which is every ordinary open.

## Tested

Seven cases: no repairs when every file is present, exactly one when one is missing, **the second
pass doing nothing** (the convergence property), all-missing then stable, rows without audio skipped,
a throwing check leaving the row intact, and the transcript surviving in every case. 0 failures.

---

# 135. The bar moves to the top, and the finger learns to tap

## The bar was trapping him

At the bottom it sat over the navigation bar, so while recording he could not go back or switch
apps — and **switching apps mid-recording is exactly what he does.** An indicator that traps you is
worse than no indicator.

It is at the top now. That strip is free: the bar only appears when the keyboard is down, so nothing
of his is up there.

## Gemini's send button, and why ACTION_CLICK was not enough

His dump shows the tree is fine — a clickable `View` wrapping a `Send` label, one hop up from the
match. The finder found it. **The press was refused.**

Compose builds its accessibility nodes by hand, and a node that reports `isClickable` does not always
accept `ACTION_CLICK`. The action returns false and nothing happens, which from outside is a magic
finger that does not work in that app.

**A dispatched tap at the centre of the bounds is the fallback** — fifty milliseconds down and up,
which is what a finger does and works on anything genuinely on screen.

**Second, not first.** `ACTION_CLICK` is precise, survives an element moving between the look and the
press, and does not care what is drawn on top. **The gesture is the fallback because it is the
blunter instrument, not because it is worse.**

It logs when it falls back, so the next time an app needs it there is a record rather than a guess.

---

# 136. The helper bar's looks, and nothing else

**Look only. No engine.** Every action on the bar is the same call it was — cancel, mic, cycle
language, show keyboard — and the tests from earlier still describe it. He asked for that explicitly
and he was right to: the last time I changed how it worked it broke in a thousand pieces.

## What changed

**A frame, not a weighted row**, so the clock is at the true centre of the *screen*. Weighted spacers
centre the middle group only when the things either side weigh the same — ENG plus a bin against one
arrow, which they do not — so the numbers sat slightly off. **Off-centre is the kind of wrong the eye
reports without being able to name.**

**The meter moved above the clock.** This bar lives at the top of the screen; putting the level under
the numbers pushes it toward the middle of his view, and something glanced at belongs at the edge
with everything else that is only glanced at.

**No cog.** Settings are not reached for mid-recording, and its room went to the send arrow, now the
biggest control on the bar.

**Send is amber** — the same amber as the meter's headroom. On this bar send means *into the archive*
rather than into a text field, and the colour is the only difference between the two recorders he
needs to see.

**Fully opaque black.** It sits over the status bar, and a translucent strip there is two layers of
information competing in one place.

**The dot is centred on the digits**, in both bars. In the keyboard's meter the row is bottom-aligned
so the megabyte figure tucks under the clock — right for a small number beside a large one — but a
lamp is not a number, and hanging it from the same line made it read as having slipped. **Only the
dot was re-aligned; everything else kept its arrangement.**

---

# 137. The bar becomes the status bar, and the note finally shows

## It replaces the status bar rather than sitting on it

Exactly `status_bar_height` tall, opaque, at the top. **It occupies a strip that was never his**:
nothing of the app moves, nothing of his is hidden, and the row he loses is one he was not reading.
The old two-line bar covered the clock *and* took a band of the app underneath.

**One line, so the meter stands up.** A horizontal meter needs a run of width and a line of its own;
upright it needs the height it already has. Same numbers, same colours, same peak — and upright it
fills from the **bottom**, because level rising is level going up, which is the one direction a meter
is allowed to grow.

Left to right: meter · dot · clock · — · bin · send · **ENG last**, because the language is the one
thing here he changes rather than watches.

Send still stops and transcribes. **The destination is decided by what is on screen, not by this
being a different button** — with no field open it lands in the archive, and when he opens the
keyboard mid-recording the keyboard's own bar picks it up, because both read one state.

## Why the offline note never showed, three times

`deliveredToField` was set in **one** of the two delivery branches. The realtime path left it at
whatever the previous dictation happened to be, so a note dictated with no field open inherited a
`true` from earlier and was filed as ordinary.

**A flag written on one path and read on both is not a flag, it is a memory of somewhere else.**

## The colour, harmonious

Teal was a contrast colour and he asked for harmony. The list is sand and amber on near-black, and a
cyan line in it reads as an error or a link — **a different SHADE of the same hue says "another kind
of the same thing"; a different hue says "something else has gone wrong".**

`#C9A227`, a deeper gold. Measured at **7.8:1** against the panel, comfortably past the 4.5:1 needed
for body text, and darker than the accent so the two never compete: amber marks what can be pressed,
this marks what was never sent.

## The sweep, all three passes

**Structure:** one hit, false — `SpaceBar` inside a comment explaining why that icon is not used.
**Agreement:** 30 feature keys, 20 settings entries, 11 switchboard entries, 310 preferences, 15
routes — **0 disagreements, and every count printed**, because a check reporting zero is a check that
did not run.
**Dead ends:** reader never silent from any state; volume 4,000 hold lengths with 0 doing both and 0
doing nothing; send never fires unasked and never stays armed; offline classification correct on all
four combinations; repair converges on the second pass.

---

# 138. The notch

He is right that an overlay cannot draw over Android's own status bar — that strip belongs to the
system, and what my window took was the row *beneath* it. Moving the bar to the top therefore moved
the problem: the app's own top-corner buttons were now the ones covered.

**The window is only as wide as its contents and sits in the middle.**

A window is either touchable or it is not — there is no per-region setting, and making the whole
strip pass touches through would have taken the bin, the send and the language with it.

So: **the corners are not click-through because they let touches pass. They are click-through because
there is nothing there.** Like the notch on a phone — the middle is spoken for, the sides are his,
and app buttons live in the corners.

Which is also why the controls are now packed with no gaps between them: **every dp of width this
takes is a dp of his screen that stops working.**

---

# 139. One copy row, and a beat before Send

## The clash

The transcription view had **two clipboard rows**: the old edit strip at the top and the arrangeable
copy row below it — different orders, different settings, neither aware of the other. He could not
tell which one his arrangement applied to, because the answer was "one of them".

**Two controls for one job is not a choice, it is a bug with an options screen.**

The copy row is the master — it is the one the editor arranges — so it **takes the strip's position at
the top** and the strip is removed from that view. The strip stays on the typing keyboard, where it is
not duplicated and `maEditRow` still switches it.

**In the transcription view the copy row is always on**, not a switch: that view has no letters and
the clipboard is its whole job, so a switch offering to remove the only row on the screen is offering
to break it. On the typing keyboard it stays switchable, where it is one row among several.

## 333 ms before pressing Send

The gesture works every time in Claude and sometimes in Gemini. **The delay is not for a race in this
app — it is politeness toward one in theirs.** Gemini's send button enables itself once the field
reports content, and a press landing in the same frame as the text arrives at a button that is still
disabled. Nothing throws; the message just sits there.

Nothing here can observe the other side becoming ready, so a wait is the only instrument available. A
third of a second is under the threshold where he would call it slow and well over the frame or two
the other app needs.

---

# 140. Permissions, numbered, and warnings that open the fix

## A screen of its own, first in the settings

Permissions were spread across a wizard he sees once and a scatter of errors he sees when something
has already failed. Reinstalling — several times a day — meant hunting them one at a time from
different starting points.

**Numbered, because the order is not arbitrary.** Restricted settings must be allowed before
accessibility can be switched on; the microphone is useless without the keyboard enabled. A list in
execution order is one he can work down without thinking, which is the point of gathering it.

**Each row says whether it is granted and opens the exact page that grants it.** A permission screen
that only names permissions is a worse version of the system settings — the value is entirely in
those two things.

**It rechecks on resume.** A grant happens in another app, so this screen is always holding a stale
answer after a trip out. Without the recheck a granted permission keeps reading as missing until the
screen is closed and reopened, which looks like the grant not having worked.

**"Allow restricted settings" reports as never granted**, because Android exposes no flag for it. An
unticked step he has already done costs a glance; a ticked one he has not done costs an afternoon.

## The warning is the door

`grant the microphone permission` said what was wrong and left him to find where — from a keyboard,
which cannot show a permission dialog, so the answer was always several screens away.

**An error that names a fix and does not offer it is only half an error message.** Any failure
mentioning a permission, AudioRecord or accessibility now opens the Permissions screen.

Matched on the message text rather than an error kind, because these arrive from the recorder, the
service and the system, and none of them share an enum. **Wrong in the harmless direction**: a false
positive opens a useful screen, a false negative leaves the banner as it was.

## Caught before building

`Icons.Default.Lock` is **absent** from the icon artifact this project builds against — a missing
icon is a red build, not a blank space. Shield instead. And `FlorisImeService` was used in the
smartbar without being imported.

---

# 141. The permissions screen, fixed

Two bugs, both mine, both shipped in 257.

## The crash

I used `androidx.compose.ui.platform.LocalLifecycleOwner`. **Every other screen in this project uses
`androidx.lifecycle.compose.LocalLifecycleOwner`** — the Compose one is deprecated and throws where
the lifecycle one resolves. It compiled, so nothing said a word until the screen was opened.

**The project already had the right pattern in `DictateFloatingButtonScreen` and I wrote a new one
instead.** When a screen needs to do something another screen already does, copy that screen.

And the observer was added inside `LaunchedEffect` and never removed — it outlived the screen and
fired against a composition that was gone. `DisposableEffect` with `onDispose` now, as the working
screen does.

## Every query is wrapped

Seven `runCatching`s, each defaulting to **not granted**. These are queries into the system and any
of them can throw on a ROM that answers differently. **One screen that cannot open is worse than one
row that says the wrong thing**, and the safe default leaves the step visible with the button that
fixes it.

## Permissions is pinned to the top

Appending new entries is the right rule — it is what stops a feature added next year being invisible
to somebody whose arrangement predates it. But it put this one at the bottom of a long list, and
**this is the screen he reaches for when nothing else works**. A repair tool at the end of the list is
one he has to scroll to while the thing is broken.

Pinned rather than reordered once, so it survives any drag and any restored backup. Tested against
five stored orders — empty, an old order without it, one where he dragged it to the middle, one with
an unknown id, one with it stored last: first every time, every entry present exactly once.

---

# 142. Permissions and API keys, one entry

**A permission and a key are the same kind of thing from where he stands:** something that must be
granted before the app can do its job, and something he has to set up again after a reinstall. They
fail together and they were fixed in two different places.

First entry in the settings, **"Permissions and API keys"** — the numbered grants, then a Keys
section that opens the key manager.

**The keys screen stays its own screen.** It is long: the ring, the tester, the importer. Nesting its
body inside another screen would mean unpicking four hundred lines to gain nothing he can see.
**Merging the entry is what he asked for; merging the implementation would have been me hearing a
different request.**

**Its row is never ticked**, and that is deliberate. A key is not a yes-or-no — it can be present and
dead, or present for one provider and missing for another. A tick claiming "done" is the kind of
false reassurance that costs an afternoon.

## The old entry is kept, and hidden

`KEYS` still exists in the enum so a stored order containing `keys` parses exactly as written rather
than being silently repaired. It is filtered out of the list wherever it appears: **an entry that
opens the same screen from two places is two doors to one room.**

Tested against five stored orders — fresh, an old one that lists `keys`, one with it dragged to the
middle, one listing it twice, one with an unknown id: permissions first every time, `keys` never
shown, every other entry present exactly once.

---

# 143. His settings become the defaults

He sent his exported `.jetpref`. **It contained his live AssemblyAI ring — 23 key-shaped values — so
none of it was copied verbatim.** The preferences were read, the accounts blob was skipped, and no
key was printed at any point. See `SECRETS.md` §2a.

The non-secret ones are now what ships:

| | was | now |
|---|---|---|
| number row on the feature row | off | **on** |
| edit strip | on | **off** |
| copy buckets | on | **off** |
| scroll pages | 1 | **4** |
| keyboard number row | on | **off** |
| settings order | alphabetical default | **his** |
| feature row arrangement | default | **his** — zone keys, mic, reader, switchboard, settings |

**A fresh install should look like the keyboard he uses**, not like a starting point he rebuilds every
time — and he reinstalls several times a day.

## Permissions cannot be moved

`parse` already pinned it first, so a drag appeared to work and then silently undid itself on reopen.
**A control that accepts a gesture and then discards it is worse than one that refuses**, because it
teaches him the setting does not stick rather than that it is fixed.

`move` now refuses to move it and refuses to drop anything above it. Four cases tested.

## The copy row's position is fixed

Above the record button, below the language badge, in the transcription view, **and not a setting**.

It was under the record row and switchable. Both are wrong: that view has no letter keys, so
select-all, paste, cut and the histories are most of what the screen is *for* — **a switch offering to
remove them is offering to empty the screen** — and its place is where his thumb already is between
speaking and pasting.

---

# 144. A settings export is a secret file

He sent a `.jetpref` to set the shipped defaults from. It held **twenty-three key-shaped values** —
his live AssemblyAI ring — inside a `provider_accounts` blob, among sixty ordinary preferences.

**The secret was not the point of the file, which is exactly what makes it dangerous.** A key file
announces itself. A settings export looks like configuration.

What was actually done, and what should always be done: read in place, values taken by name, the
accounts blob skipped, nothing copied into the working tree, nothing printed. Verified afterwards —
no 32-hex value anywhere in the repo except one inherited FlorisBoard file from June, no `.jetpref`
in any commit.

**Take the values, never the file.** A shipped default is a number written into `AppPrefs`; the file
it came from is not something this repository should ever have held.

Written into `MANTRA_MANIFEST/modules/secrets.md` §2c and into the handoff, with the general rule
underneath it: **anything he sends to configure something is a file about his setup, and a file about
his setup contains whatever his setup contains.** Ask what is in it before deciding how to treat it,
and assume the answer is "secrets".

---

# 145. The pin comes off

I pinned PERMISSIONS to the top — forced there on every read, and refused by `move`. He rejected it,
and the distinction he drew is the right one:

**A default is where something starts. A pin is a decision taken away from him.**

This list is his. **The one entry he was not allowed to move would have been the one proving it is
not his** — and the reason I gave (it is the screen he needs when things are broken) is an argument
for putting it first, not for holding it there.

It is first in `DEFAULT`, so a fresh install opens with it at the top. After that it is an entry like
any other: drag it anywhere, and it stays where it is put.

`parse` no longer reorders, `move` no longer refuses. Nine cases: first on a fresh install; moved to
the middle and it stays; that order survives a reload; moved to the end and it stays; another entry
can be dropped above it; and across four stored orders nothing is lost or duplicated, with `keys`
still filtered out.

**The lesson worth keeping:** when a rule protects him from himself, check that he asked to be
protected. He did not, and the version that trusted him was both simpler and correct.

---

# 146. One copy row, for real this time

## What §139 left behind

§139 unified the transcription view and said the strip could stay on the typing keyboard, "where it
is not duplicated". That sentence was true of the screen and false of the app. It left **two rows
that were both called the copy row**:

| | transcription view | typing keyboard |
|---|---|---|
| composable | `MaFeatureRow(copyRowOnly = true)` | `LegacyEditRow` |
| preference | `maCopyRow` | `legacyActionRow` |
| key set | `MaFeatureKey` | `LegacyEditAction` |
| editor | Copy row screen | `LegacyActionRowSetting` |

Marko sent two screenshots and said: both come from one source of truth, yet the last button differs,
so delete whatever makes it differ. The last key on the keyboard was `LegacyEditAction.KEYBOARD`,
which draws a **microphone** in the typing view because it means "swap views" — a key that does not
exist in the other row's vocabulary at all. The fourth and sixth keys differed for the same reason.

**He was right about the symptom and the cause was worse than the one he named.** They were never one
source. And the comment above the call site said, in as many words, that both views drew the identical
row from identical code. **A comment is not evidence** — the same lesson the spacebar mark taught,
paid for a second time in the same file.

## What it is now

One row, one preference, one composable, one editor.

- The typing keyboard draws `MaFeatureRow(copyRowOnly = true)` in the slot the strip had.
- `maEditRow` still switches it there — **the copy-row key on the feature row, key 3, the key he
  already presses.** Not a new switch and not a settings-screen tick three rooms away.
- The transcription view is unchanged: always on, no switch, by §139.
- `LegacyEditRow` is deleted. `LegacyActionKey` and the `LegacyEditAction` enum stay, because the
  feature row draws AP, AC, select-all and backspace through them — the same keys from the same code.
- `LegacyActionRowSetting` is deleted. It arranged a row that now appears nowhere, and **an editor for
  a row that does not exist is a control that does nothing**, which this project has repeatedly found
  to be worse than a missing one.

## The two switches that were not switches

`maCopyRowOnKeyboard` switched an **appended second copy** of the real copy row onto the keyboard,
while `maEditRow` switched the strip. So the keyboard had two clipboard rows available under two
switches, and the tick labelled "on the typing keyboard" did not control the row that was on the
typing keyboard.

`maCopyRowOnDictate` controlled **nothing at all**. Nothing read it after §139 made that view fixed.
It sat in the switchboard and on the copy row screen looking like a working control.

Both preferences are gone, both ticks are gone, and `COPY_KEYBOARD` / `COPY_DICTATION` are gone from
the switchboard. `MaSwitchboardOrder.parse` drops ids it does not recognise, so an arrangement written
before this loses those two and keeps everything else — no migration needed, by that design. The
remaining entry keeps the id `edit_row` (an id is a name in a file, not a label) and is now called
**Copy row**, with `ContentPaste`, the glyph key 3 draws.

## `drawChrome`

The keyboard now hosts **two** `MaFeatureRow` instances: the feature rows at the bottom and the copy
row at the top. `maDashboardOpen` is file-level state and `maMagicRowShown` is a preference, so both
instances would have drawn the wand bar, the magic row and the reader dashboard — two of each,
stacked. The second instance passes `drawChrome = false`.

The scroll stepper is deliberately **not** gated: `scrollMenu` is local to each instance, so it opens
over the row whose key was actually held.

## Rejected

**Putting the copy row at the bottom of the keyboard** by leaving the append path in and pointing key
3 at it. It would have been a smaller diff and it moves a row he did not ask to move.

**Keeping the two preferences as deprecated.** A preference nothing reads is indistinguishable from
one that is broken, and this whole entry exists because of controls that looked like they worked.

## Tested

`scripts/test_copy_row.py`, 838 checks, Test 1, no build required: one reader of `maCopyRow`, both
views calling the same composable with `copyRowOnly`, no surviving `LegacyEditRow` caller, no appended
second copy, no reader of either dead preference, the keyboard switched by `maEditRow` and the
transcription view switched by nothing, chrome gated. Made to fail on purpose twice — the appended row
grown back, and `drawChrome = true` — and it went red for each.

**Not tested:** anything visual. Heights, balance and the row's position on the real keyboard are code
inspection only until it is on his phone.

## The check that cried wolf

`verify.py` reported `MaSwitchRow` "declared twice in the same scope" in a file that has compiled for
months. It was a **false positive**, and confirmed against the source before anything was changed:
`MaSwitchRow(pref: PreferenceData<Boolean>, …)` and `MaSwitchRow(stringPref: PreferenceData<String>,
onValue, offValue, …)` are legal overloads.

The check already keyed on the parameter list for exactly this reason — but it read only the declaring
line, so every **wrapped** signature, which is every Compose signature in this project, hashed as the
empty string and collided with its own overload. It only fired now because the file had never been in
a diff since the check was written.

Fixed by gathering the parameter list until its parens balance. Verified by putting a genuine
duplicate in — identical signature, same scope — and confirming it still goes red.

**A checker that cries wolf is a checker that gets ignored**, and it had one chance to be believed.

---

# §147 — The buckets lose their switch, and three detections stop guessing

Build 265. Four things he photographed on 21.8.2026, all of them the same shape underneath: a
feature that was working exactly as written and reading, from the phone, as broken.

## The buckets were off, and that is why A was off too

`captureIntoClipSlots` opened with `if (!prefs.dictate.maBucketsEnabled.get()) return`, and that
preference defaults to **false**. So on his phone the C keys were drawn on the row, dim, catching
nothing — and the A key, which presses "copy code" on a code block and lets the ordinary clipboard
capture put it away, had nowhere to put anything. He reported the A key as broken. It was not. The
bucket behind it was closed.

**The switch is gone, and presence on the row replaces it.** C keys on a row means the buckets are
live; no C keys means there is nowhere for a copy to go, which the existing `visible` set already
enforces without any preference at all — `capture` returns the buckets unchanged when the visible set
has no free slot, and an empty set has no free slot.

Why this is better than fixing the default: **a switch that can be left in the position where the
feature looks broken is worth less than no switch.** The gesture that makes buckets wanted is putting
a C key on the row, and that gesture can now do the whole job. Nothing to find in a settings list
three screens away, and no state in which the keys are on the keyboard and inert.

Removed with it: `maBucketsEnabled`, the switchboard entry `BUCKETS`, and the switch at the top of
the Paste timing screen. `MaSwitchboardOrder.parse` drops the stored id, so his arrangement survives
minus that row.

**A1 fills C1, A2 fills C2, A3 fills C3** — and always did, by construction rather than by a rule
written anywhere: A presses the *n*th code block's copy button, the copy reaches the clipboard, and
capture pours it into the lowest empty visible bucket. Modelled in `scripts/test_buckets_and_
permissions.py` and walked, along with the cases it must refuse.

## The tick that vanishes belongs to somebody else

He asked for the "copied" checkmark to stay a minute. That checkmark is drawn by the chat app on its
own copy button, and nothing in this keyboard can hold it there — worth saying plainly rather than
appearing to fix it.

So the same question is answered on our own key. `MaClipCapture.lastFilled` records which bucket took
the last copy and when; the C key wears a small tick for `FILL_MARK_MS`, one minute, and then stops.
Compose state rather than a plain field, because the capture happens in the clipboard manager with
the keyboard already drawn — a plain field would be written and nothing would redraw, which is the
wand bar's old bug. A `LaunchedEffect` ticks once a second only while a mark is young, so the tick
clears itself and costs nothing the rest of the day.

The tick is a **shape**, not a colour: colour on that key already says whether the bucket is holding
something, and a second meaning on the same channel would make neither readable. It is drawn over the
key in a Box so the number does not move to make room for a mark that comes and goes.

Which bucket is found by **diffing the two lists** rather than by having `capture` report it. capture
stays a pure function over a list, and the difference between what was stored and what is stored now
cannot disagree with what was actually written.

## One source is not a detection

"Enable the keyboard" said **not done** on a phone where the keyboard was enabled and in use. It
asked one question — does `Settings.Secure.ENABLED_INPUT_METHODS` contain our package name — and that
string is not reliably readable by an ordinary app on a modern Android. It comes back null, and a
null contains nothing, so the answer was no.

It asks three now and takes any yes: `InputMethodManager.enabledInputMethodList` first, since that is
the public API for exactly this question; the Secure string second; `DEFAULT_INPUT_METHOD` third,
because nothing can be the current keyboard without being enabled. **Each is wrapped on its own.**
One `runCatching` around all three would have let the first failure discard the other two answers,
which is the same fault in a different place.

**Allow restricted settings** was hardcoded `false` and could never tick. Android exposes no flag for
it, so it is now answered by its consequence: restricted settings is the gate that stops a sideloaded
app's accessibility service being switched on at all, so an accessibility service that is *running*
is proof the gate was opened. Not an assumption — a thing that could not otherwise have happened.
Still false when accessibility is off, which is honest: the next step is the same either way.

A row that is permanently outstanding on a screen whose whole job is to say what is left teaches him
to ignore the numbers, and then the ones that mean something are ignored too.

The API keys row below the steps is still deliberately never ticked. A key can be present and dead.
The first version of the test could not tell that apart from the bug and would have had to be argued
with; it now looks only inside `maPermissionSteps`.

## The megabytes broke a line

`"%.1f"` in a column sized by `Modifier.weight(1f)`. The clock takes what it takes and the size gets
the remainder, so at 15,0 it was narrower than the text and wrapped onto a second line inside a strip
one line tall. **A weight is a promise about width that a number cannot keep** — the same rule that
clipped "Insert" to "Ins", in digits.

The lamp, the clock and the size now start at the left edge, where the meter below them starts, and
the single weight is at the *end* taking up what is left. The size says `MB`, `maxLines = 1`,
`softWrap = false`, sized to its own content.

The decimal separator stays the phone's own. `"%.1f"` gives 5,6 on his Croatian phone and that is
correct there; forcing a full stop would make this keyboard the only thing on his screen writing
numbers the other way.

## Tested

Test 1: 438 checks, 0 failed, including the capture rule modelled and walked in Python. Broken on
purpose four ways and confirmed red, then green again. One of those sabotage runs made a check
*throw* rather than fail — the count never printed and the exit code was right by accident — and that
check is now guarded. A check that raises is not a check that fails.

Not tested: nothing ran on a phone. The A key's press path through the accessibility service was not
exercised, the three permission sources were not observed on his ROM, and the tick has not been seen.

---

# §148 — Undo reaches the buckets

Build 266. He asked for Ctrl+Z to cover the buckets: a code block collected from the wrong place
should be undoable rather than requiring the bin and a fresh start on the ladder.

## The rule, and why it is a rule about order

Undo reverses the last thing that happened. That is what it means everywhere, so it is the rule
here too and nothing cleverer:

> **If the newest bucket change is newer than the last text this keyboard wrote, undo reverses it.
> Otherwise the key does exactly what it did before.**

This is ORDER, not timing. It does not depend on how long ago anything happened or how long the key
is held; the same sequence of actions always gives the same answer. The thing `VOLUME_KEYS.md`
forbids is a key whose meaning changes with the duration of the press — a key that has to be
predicted. A key that undoes whatever you did last does not have to be predicted, because you were
there.

Every text commit passes through `EditorInstance.commitText`, so that one place stamps the clock.
Typed, dictated and pasted all hand the key back to the field, with no list of call sites to keep
current.

## What is not undoable, on purpose

**Pouring a bucket into a field.** That press does two things — fills the field, empties the bucket
— and undoing only the second leaves the text in the document AND back in the bucket. The paste
stamps the text clock on its way out, so undo after it goes to the field, where the visible half of
the action is.

## What the walk found

The first draft's comment said the bucket change was still reachable on a second press. The walked
test failed on exactly that, and the code was right: **once text is the newest thing the key stays
with the field.** It has to. The field keeps a multi-level undo history this keyboard cannot see, so
a second press that jumped back to the buckets would undo a collected block for somebody who was
still undoing sentences.

The comment was corrected rather than the code. A comment is not evidence, and a comment that
overstates what a rule does is the same failure as the spacebar mark that existed only in a comment.

## The arming, and why not a time window

The A key advances the ladder the moment its press lands, while the copy reaches the clipboard a
moment later through the system. A step recorded at capture time would therefore hold a rank that had
already moved on, and undo would put the ladder back one rung short.

So the A key **arms** the step before it presses — the slots and the rank as they were — and the next
capture uses that instead of reading the rank itself. A flag consumed by the next event is exact; a
window measured in milliseconds is a guess that is usually right, and "usually right" on a ladder
means collecting from the wrong place on the day it is wrong.

The arming is dropped on a press that did not land, and dropped by any copy that changed no bucket —
a repeat, a blank, a full row — so it can never be spent by an unrelated copy much later.

`maBucketRank` moved out of `MaFeatureRow` into `MaClipCapture.autoRank`, because undo is handled in
`KeyboardManager` and a private variable in a composable's file cannot be reached from there. The A
key and the buckets are one mechanism anyway.

## verify.py: a check that had to be argued with

`check_delegation_imports` failed `EditorInstance.kt` the moment a one-line change brought that file
into the checked set. Its delegations are `by FlorisPreferenceStore` and `by context.appContext()` —
custom delegates that carry their own `getValue` and need no import. The file has compiled green for
months.

The check asked whether the file contained any `by` at all and whether the word "compose" appeared
anywhere in it. It looks for the Compose right-hand sides now — `remember`, `mutableStateOf`,
`collectAsState`, and the rest — which is what every red build it exists for actually was.

**A false positive is not free.** It has to be argued with every time, and a check that is argued
with stops being read, at which point the real hits arrive in the same voice as the false ones.
Proven still to catch the real thing by deleting `getValue` from `MaClipCapture.kt` and watching it
go red.

## Tested

Test 1: 1,912 checks, 0 failed, including all 625 four-action sequences drawn from copy, A, bin,
type and paste. Two invariants asserted after every one of them, in the shape the volume keys use:
**always one** — undo either reverses a bucket change or hands the key to the field, never neither —
and **never both** — it touches the buckets or the field, not both at once. Broken on purpose two
ways, at the wiring and at the rule, and confirmed red.

Not tested: nothing ran on a phone. Whether the field actually honours Ctrl+Z is the field's
business and always was.

---

# §149 — The key picker, grouped by meaning

Build 267. He asked for two things in one sentence: put the A key with the buckets, since A *is* the
bucket system, and then look at the whole picker and group it properly.

## What it was

Three headings. Two of them real — the buckets and the macros — and a third called **"Keys"**
holding twenty-six unrelated things in `MaFeatureKey` declaration order, which is to say in the order
they were written over eight months. Enter beside the screen dump, the reader beside shift.

A heading is a promise about what is underneath it. That one promised nothing, and the list under it
could only be read, never scanned — which on a picker is the difference between finding a key and
hunting for it.

## The grouping is a property of the key

`MaFeatureGroup` lives in `MaFeatureOrder.kt` beside the keys, and `MaFeatureKey.group` is an
exhaustive `when`. So **a key cannot be added to this app without saying what it is for**: the
compiler asks, at the moment it is written, of the person who knows the answer.

Written as a `when` on the screen it would have had an `else`, and an `else` is where a new key goes
to be forgotten. That is exactly how twenty-six of them ended up under "Keys".

Nine sections, in the order they are read down the screen: Clipboard · Copy buckets · Editing the
text · Dictation · Reading aloud · Getting about · The keyboard itself · Settings and diagnosis ·
Your macros.

## A belongs with the buckets

His point and it is right: A presses a code block's copy button and the capture files the result into
the next free bucket. One mechanism in two halves, and the halves were in different sections. Somebody
looking for the way to collect code blocks looks under the buckets, so that is where it is, with the
bin that empties them.

**Within the section the order is the lifecycle**: A fills, C1 to C10 hold, the bin empties.
Catalogue order would have put A and the bin together above all ten, because both are `Builtin` and
the `Clip` entries come after — the shape of the model showing through as an arrangement, which is
the thing a grouped list exists to stop.

## Two false positives in verify.py, both fixed rather than argued with

`check_when_coverage` read **every** enum in `MaFeatureOrder.kt` and then demanded that
`MaFeatureRow` carry a branch for `CLIPBOARD` and `BUCKETS`. It switches on `MaFeatureKey` and always
did. It reads only the first enum in the file now — the keys, which are the thing that must be
covered everywhere, and which are declared first.

And a check in the new test fired on the comment explaining why the old heading was removed. Comments
are stripped before that check now. **A check that reads its own documentation is a check that
reports itself.**

## Still open, and not touched: two keys with one face

`SCROLL` draws **S** and `SUBTITLE` draws **S**. Same glyph, different jobs, sitting in one list — the
fault the design language names directly, and the picker cannot fix it, because the picker's rule is
to show the glyph the keyboard actually draws. The fix belongs on the keyboard and it is his call
which one changes. Left alone rather than quietly changed.

## Tested

Test 1: 58 checks, 0 failed. Every key parsed out of the source and asserted to have exactly one
section, every section asserted to have keys, and the bucket order walked. Broken on purpose by
moving A into Settings and confirmed red.

Not tested: nothing ran on a phone. The picker has not been seen with nine headings in it, and how it
scrolls with them is a thing only the screen can answer.

---

# §150 — The bucket wears a ring, and undo gets its pair of keys

Build 268. Three things, all from one message about the automatic buckets.

## The tick becomes a ring

He asked for the checkmark to be a green outline instead, and then for the confirmation to last until
the bucket is emptied. Those are the same request twice, and the second one is what settles the
design: **a mark that should last until a state ends is not a mark, it is the state.**

So the tick is gone, the one-minute clock with it, and a bucket holding something wears a green ring
for exactly as long as it is holding something. `text != null` already meant that; nothing new is
tracked. A tick is an event and has to be caught while it is on screen — he had already asked once
for it to last longer, which was the sign the shape was wrong rather than the duration.

`MaClipCapture.lastFilled` and `noteFilled` went with it. They recorded WHICH bucket filled most
recently, and nothing needs to know that now the ring is on all of them. State nothing reads is state
that drifts.

**The colour rule, and the tension in it.** A ring around a key already means "this is a switcher",
and that one is monochrome precisely so a row of tinted rings does not read as decoration. This is a
second ring in a colour. They stay separate because the channels are different — ink says what KIND
of key, green says what STATE it is in — and no bucket is a switcher, so nothing wears both. If it
reads as bolted on beside the zone keys, that is his call and it goes back to a mark inside the key.
Recorded here because the border-as-state question was argued and reversed once before, on 19.8.2026,
and this is a deliberate exception to it rather than a forgotten rule.

**What is still not possible:** the confirmation inside the chat. That checkmark belongs to the other
app's copy button and no keyboard can hold it there. Said plainly rather than fixed quietly.

## Undo and Redo as keys

They existed only on the old edit strip, which is gone, and through a Ctrl+Z nobody has on a phone.
Added to the catalogue under **Editing** — not under Buckets, although undo reaches the buckets,
because the key is the general one and filing it there would promise it only did the second thing.

Drawn through `LegacyActionKey`, so the key on the row and every other way of firing undo are one
press. Nothing about the bucket rule lives in the key.

## Redo reaches the buckets, under a stricter rule

A redo that reversed text but not buckets is the asymmetry that makes a pair of keys untrustworthy:
undo the collection, press redo, get half of it back.

Undo's rule is "newer than the last text". Redo's is tighter: **a redo survives only while nothing at
all has happened since the undo.** Any bucket change clears the stack, and text since the undo
disqualifies it by the same clock undo uses.

Deliberately stricter, and the reason is worth keeping: undo reverses something that definitely
happened, while redo restores something already decided against. Restoring it into a world that has
moved on is how a bucket ends up holding what nobody put in it — which is the failure the whole
bucket design exists to prevent.

## Tested

Test 1: 3,804 checks, 0 failed, all 625 four-action sequences walked with undo AND redo asserted
after each — **always one**, **never both**, for both keys, or they are not a pair. Broken on purpose
two ways, at the redo rule and at the ring, and confirmed red.

One check was loosened rather than updated: `test_key_groups.py` asserted exactly thirty keys and
failed for the only reason a catalogue ever changes, which is that a key was added. A number that has
to be edited on every addition is a number that gets edited without being read.

Not tested: nothing ran on a phone. The ring has not been seen next to the switcher rings, which is
the one question that matters about it and the one only the screen can answer.

## §150a — The red build, and the check it bought

Build 268 went red. Sixteen unresolved references, and every one of them was a symbol my own edit had
deleted.

Two edits replaced a range by naming its two ends — *from this comment down to that `LaunchedEffect`*
— and in both cases the second end sat further down the file than the thing being removed. The cuts
took `autoRank`, `clipKeysPresent`, `learn`, `magicRowShown`, `magicAll` and `magicTargets` with
them. **This is the failure the handoff already names: cutting code by eye fails, and the pattern is
always that the second end was whatever came next in my head rather than what came next in the file.**

Nothing in `verify.py` could see it. Braces balance perfectly when a whole declaration is removed;
nothing is duplicated, no import is orphaned, the file is well formed. It is simply missing something
the rest of it still uses.

`check_removed_declarations` closes it: every `val`/`var` declared at HEAD, not declared now, whose
name is still referenced. Proved against the red build itself rather than against a hypothetical —
run over HEAD~1 against HEAD it names `clipKeysPresent`, `learn`, `magicRowShown` and `autoRank`,
which is what CI said five minutes and one paid build later.

The first version of it missed `autoRank`, because inside `MaClipCapture.kt` the reference is written
`MaClipCapture.autoRank` and the check skipped anything with a dot — a rule added so that a
declaration deliberately moved into an object would not report itself forever. It now treats a
qualified reference as this file's business when the object is declared in this file, which is the
distinction that was actually meant.

---

# §151 — A takes what you are looking at

Build 270. He took the automatic bucket into real use and found the flaw in its rule, which is the
only place that kind of flaw can be found.

## The ladder was answering the wrong question

A walked the whole document by a counter: press for the newest code block, press again for the one
above it, and the face showed how far up it had climbed. His words: *"It's not linear as I was
thinking it's going to be."* He scrolls up, takes a block, scrolls down, takes another, comes back to
a screen he has already collected from.

A counter answers **how many have I taken**. The question he is actually asking is **this one, the
one on my screen**. So the frame is the whole world now: what is not on screen does not exist to this
key, and scrolling is how he chooses. `autoRank` is gone, the face says `A`, and `Step.rank` went
with it — undo has nothing to rewind but the buckets.

**Lowest first, then upward.** The same rule the rest of the app uses for a chat, where the lowest is
the newest. It also answers his question about two blocks competing: they do compete, it is common,
and no question needs asking — press twice and you have both, bottom then top. A dialog mid-collection
would cost more than the ambiguity does.

**A block already in a bucket is skipped**, not taken again, and the key moves to the next one up.
Only when every block in view is already held does it say so. That is the message he asked for after
copying the same thing twice on a screen he had scrolled back to.

**How "already copied" is known.** Not by reading the block before pressing — that text is not
available until the copy button has been pressed. The press happens, the capture refuses a duplicate,
and the buckets come back unchanged. Unchanged IS the answer, and it costs one press. Full buckets
and a held block are two different messages, because one of them is a thing he has to act on.

## The visibility filter, which is a feature exactly once

Every other `isVisibleToUser` filter in `MaScreenTargets` was a bug: it made TAB refuse the fields
hardest to reach, and it quietly crippled the magic finger for months, because **off screen is a
reason to scroll to something, not a reason to decide it does not exist.**

Here the frame is the question, so the filter is the feature. `visibleOnly` defaults to false and
only the A key passes true. It checks bounds against the display as well as `isVisibleToUser`, since
a node just past the fold can still answer true and a node of no size answers true while occupying
nothing.

## verify.py could not see most of that file

While working on it, a deliberate duplicate function was added to `MaScreenTargets.kt` to prove a
check still fired. It did not fire. `strip_code` was keeping **68 of 963 lines**.

The cause: `append('"')`. A Kotlin char literal holding a double quote, which the scanner did not
know about at all — so that quote opened a string that never closed and swallowed the rest of the
file. Every check that reads stripped code had been passing on nothing there, silently, for as long
as the file has existed, and saying "nothing to report".

**This is the exact failure the manifest names**: a check that finds nothing and a check that runs
nothing look identical from outside. Char literals and raw strings are handled now, and the file
strips to 697 lines. Found by accident, which is the uncomfortable part — the sabotage was aimed at
something else entirely.

Two other false positives fixed the same day rather than argued with: `check_duplicate_declarations`
treated `val foregroundPackage` and `fun foregroundPackage()` as one name declared twice, when Kotlin
keeps properties and functions in separate namespaces; the kind is part of the key now.

## The list he asked about, and what it needs first

He asked whether the boxes could be listed — title above the block plus a few words from it — for him
to pick from, as a lab feature. **It is the right idea and I have not built it**, because it depends
on something unmeasured: whether the code block's own text and the heading above it are readable in
the accessibility tree of that app at all. Building a picker on a guess about that would be building
the screen before knowing there is anything to put in it.

The long press is the first honest step: it counts what is in the frame and how many buckets are
free. The next step is a screen dump from a real chat — the DUMP key exists for exactly this — and
then the picker can be built against what is actually there.

## Tested

Test 1: 3,097 checks, 0 failed, including 1,024 walked sequences of four screens drawn from a page of
five blocks with a two-block frame — his non-linear movement, modelled. The invariant asserted after
every one: **no block is ever in two buckets.** Broken on purpose three ways — highest-first,
no-skip, and wrong wiring — and confirmed red with 464 failures.

Not tested: nothing ran on a phone. The 350ms wait between the press and reading the buckets is
reasoned from the 150ms the snippet key uses, not measured. Whether `isVisibleToUser` behaves in that
chat app's hierarchy is unmeasured. Both are Test 2 and both are his to find.

---

# §152 — Centred, the way that cannot wrap

Build 271. The recording readings are centred again, which is where they were two builds ago, and
the round trip is the point.

**Originally centred by a weight on each side.** That is what broke the megabyte figure: a weight
hands each child a SHARE of the line, so the size got whatever the clock did not use, and at 15,0 MB
that share was narrower than the text and it wrapped onto a second line inside a strip one line tall.

**Build 265 left-aligned them.** The wrap stopped, because a group against the edge with a single
weight at the end is measured at its own width. But left was never what he wanted — it was the side
effect of the thing that actually fixed it, and he said so the moment he saw it.

**Now centred by `Arrangement.Center`, which is the fix without the side effect.** The arrangement
measures children at their own width FIRST and splits the leftover around them; a weight divides the
line first and asks the children to fit. Opposite order, and that order is the whole difference
between centred-and-wrapping and centred-and-not.

The rule worth keeping: **a weight is a promise about width that a number cannot keep.** When
something has to be positioned, position it; do not divide the space and hope.

## Tested

Test 1: 440 checks, 0 failed. The layout check was split into two, because it was making two claims
at once — that the readings are centred, and that there is no weight in the row. Those are different
things and the old single check could pass for the wrong reason. Broken on purpose both ways and
confirmed red.

Not tested: nothing ran on a phone. Whether the centred group looks right against the meter beneath
it is the only question here and it is his.

---

# §153 — The volume keys get a switch, and it is a key

Build 272. Two requests in one message, and the first one cannot be built.

## Power + Volume Up: no

He asked for the toggle to be a hardware chord, reasoning that Power + Volume **Down** is already the
screenshot so Power + Volume **Up** is free. The reasoning is sound and the conclusion is not
available: **Android never delivers the power key to an app.** It is consumed in the system policy
layer, above everything an ordinary process or even an accessibility service can see — which is the
same reason the screenshot chord belongs to the system rather than to whatever is on screen. There is
no permission that changes this and no accessibility flag that opens it.

Said plainly rather than approximated. A chord that fires "sometimes, on some ROMs" would be worse
than no chord, because he would stop trusting the toggle without knowing why.

## The switch is a key, and `VOLUME_KEYS.md` is amended

That document says: no preference, no switch in the switchboard, no gestures entry, **do not add it
back**. It is a contract, so the honest move is to amend it in the open rather than to quietly write
code that contradicts it.

Read again, the failure it was protecting against was not *that a switch existed*. It was that the
switch was **invisible, in a place he was not looking, among a dozen others he was dragging**. One
stray touch, five dead builds, days lost believing the feature had been deleted.

So the rule that replaces "no setting" is narrower and stronger:

> **The only control is the key on the row. If this state ever becomes reachable from a list of
> switches, the trapdoor is back.**

`scripts/test_volume_switch.py` enforces it by name: exactly three files may mention
`maVolumeKeysLive` — `AppPrefs`, `MaVolumeKeys`, `MaFeatureRow` — and the switchboard may not. A rule
in prose is a hope; a rule with a test is a rule.

Persisted, deliberately. A state that resets itself on restart is a control that lies — he would
switch the keys off for a film and find them live again at the next text field with no press of his
to explain it.

## Both halves, or neither

`onDown` and `onUp` both consult the gate. Gating only the press would hand the key to the system on
the way down and still act on release: a volume change **and** a recording, which is the "never both"
invariant broken in the worst direction. The walked test covers both states across the whole range of
hold lengths, and confirms red when either half is left ungated.

## One visual language

He asked for the green ring, the same one a bucket wears when it is holding something, and he is
right that the consistency is worth more than any individual choice: a ring in this green now means
one thing wherever it appears, and a mark that means one thing is read without being learned.

A first version also swapped the glyph to a crossed-out speaker and tinted it green — three channels
saying one thing. With the ring carrying the state the shape holds still. **Shape is identity.** A
key whose face changes is a key he has to re-find, and on a row pressed from memory that costs more
than the crossed speaker was worth.

## Tested

Test 1: 2,192 checks, 0 failed, 968 walked presses — both keys, both states, reading and not, every
hold length from 0 to 1200 ms in 10 ms steps. **Never both** and **always one** asserted at every one
of them, which is what that contract asks for and the reason the switch could be added at all. Broken
on purpose twice — one half of the gate, and the ring removed — and confirmed red both times.

Not tested: nothing ran on a phone. Whether the ring reads clearly on a key holding an icon rather
than a two-character label is a question only the screen answers.

---

# §154 — Send and Record become keys

Build 273. Two keys, and both of them are a route to something that already exists rather than a new
behaviour — which is the whole design constraint.

## Send

`MaMagicTargets.pressSend()`, not a press of anything called "Send". That function reads his
configured term and presses whatever button carries it in the app he is in, which is what volume-down
does. So the key, the volume key and the wand are one behaviour with three ways in, and changing the
term changes all three at once.

**Two ways of doing one thing is one way too many when one of them can drift.** A key with its own
hardcoded "Send" would work perfectly until the day he changed the term, and then would be wrong in a
way nothing on screen explains.

Upward arrow, as he asked. A paper plane is the other convention and it is not the one on his screen.
It says so out loud when no Send button is found, because silence from a key is indistinguishable
from a key that does nothing — the failure this app keeps meeting.

## Record

`DictateController.onMicClick`, the same call volume-up makes and the same one the mic on the
recording bar makes. Third route in, and nothing taken away.

The note on the MIC key says a third route to recording bought nothing and cost the only route to the
transcribe view. **That still holds and this does not contradict it**: MIC keeps the view, and this
key is additional rather than a replacement. The objection there was to losing the view, not to the
number of ways of recording.

The dot is red always, not only while recording. It is what the key IS rather than what it is doing,
and a dot that appeared only while recording would leave an empty key the rest of the time. While
recording it grows and takes the **green ring** — the app's one way of saying a thing is live, now
worn by the buckets, the volume key and this. The dot does not change colour, because the colour is
its name.

`MaRecordRed` is named once in `MaRecordMeter.kt` and used by the lamp, the key and the settings
picker. A second red mixed by eye is the same colour to whoever wrote it and a different one on the
screen.

## The test that reported a happy total having run nothing

The new checks were appended to the end of `test_key_groups.py` — **after `sys.exit`**. It printed
73 checks, 0 failed, and had executed none of them. The count had gone up for an unrelated reason,
which is exactly the kind of coincidence that makes this invisible.

Same family as the `strip_code` bug found two builds ago: **a check that runs nothing looks exactly
like a check that finds nothing.** The rule is written into the file now — anything added goes above
the summary, and the summary is the last thing in the file.

## Tested

Test 1: 73 checks, 0 failed, plus every other suite still green. Broken on purpose two ways — Send
hardcoded to the word "Send", and the record key's ring removed — and confirmed red on both.

Not tested: nothing ran on a phone. Whether the red dot reads at key size beside lettered keys, and
whether `pressSend` finds the button in the apps he actually sends from, are both Test 2.

## §154a — Red again, on a null

Build 273 went red on one line: `contentDescription = null` passed to `ThemedIconKey`, which
declares it `String`. My mistake, and the second red build of the day.

The parameter is non-null on purpose — every key on that row has to be announceable, and a key that
reads as nothing is a key he cannot find with a screen reader. So the fix is a real description,
`"Send"`, not a nullable parameter.

`check_nullable_args` closes it, and **its first version was worse than the gap it filled.** It
collected parameter names across the repo and flagged `name = null` wherever that name was non-null
somewhere in the app's own code — and immediately fired on four correct `Icon(contentDescription =
null)` calls, because `Icon` is Compose's and its parameter is nullable. Four false alarms on correct
code, on the first run, which is how a check teaches somebody to stop reading it.

It reads the CALL now: the innermost unclosed `Name(` before the argument, flagged only if `Name` is
declared in this repo and that parameter is non-null in its signature. Anything from a library is
left alone, because this file cannot see those signatures and a check that guesses is a check that
gets ignored.

Proved by putting the null back and watching it name the line, the argument and the function.

---

# §155 — Finding a key by typing what it does

Build 275. Forty-six keys in nine sections is past the number anybody reads, and he asked for a
filter that does not require him to know the app's word for a thing.

## Three layers, cheapest first

1. **Literal.** Every word he typed must appear in the label, the section heading, the letters on the
   face or the id. Every word rather than any word: adding a word is how somebody narrows a list, and
   a search that widened would be answering the opposite of what the typing meant. A hit in the label
   sorts above a hit in a description, so "copy" finds the Copy bucket before the six keys whose
   descriptions mention copying.
2. **What he has meant before.** Every pick made while a query is showing stores the pair. "sound
   off" reaches the volume key on the second try, forever. A prefix of a remembered query finds it
   too, so it fires while he is still typing — but not from one letter, because too many things start
   with one letter to be a name.
3. **A model**, marked `(AI)`.

## The memory is not a spell-checker, and that is the point

He is dyslexic and dictates in two languages. A corrector would try to turn his word into the app's
word, which is the wrong direction. **His word is not a mistake; it is what that key is called in his
head.** So nothing is corrected — the pair is stored and his word becomes a real name for that key,
as good as the printed one and better for him.

Keyed on the id, not the label: the app's labels have changed twice already and a memory keyed on
them would forget everything he taught it the next time a word was improved. Folded through the same
accent-stripping the search uses, or "čisti" typed two ways would be two names for one key and
neither would fire reliably.

The cap drops the **least used**, not the oldest. Dropping by age would keep throwing away the habit
and keeping the accident.

## The model, and the three conditions on it

Only when both offline layers are empty, only after 700ms of quiet, only for three characters or
more. Asking per keystroke would spend his credit on the way to a word that was going to match
anyway.

It goes through `requestRewordRaw` rather than opening a second path to the same providers: the key
ring, the roll to the next key when one is refused, the proxy and the certificate setting are all
already there and already tested. **A second HTTP path is a second set of bugs.** `cheapest = true`
pins the preset's default model, which is the mini/flash/haiku one for every provider in the
registry — the cheapest possible *with the key he already has*, rather than demanding a particular
provider for one suggestion in a settings screen.

It is asked for **one id from a list it was given**, and an answer naming anything else is discarded.
A model that cannot invent an id cannot mislead him about what this keyboard has. Failure is silent:
no network, no key, a refusal all mean the same thing to him, which is that no suggestion came, and
the list is still there.

**Marked `(AI)` because it is a guess.** A guess that looked like a match would teach him to trust
the next one the same way. And picking it teaches the memory exactly as any other pick does — which
is the whole design: **layer three is a teacher for layer two, not a dependency.** The more he uses
it, the less it is needed.

## Tested

Test 1: 38 checks, 0 failed, all three layers ported and walked — folding, accents, narrowing,
ranking, the prefix rule, the cap dropping the least used, and every condition under which a model is
and is not asked. Broken on purpose two ways — asking on every query, and letting a second word widen
the search — and confirmed red on both.

Not tested: no model was actually called. The prompt has never been sent, so how good the suggestion
is remains unmeasured, and it is the one thing only real use answers.

---

# §156 — The AI key on the prediction row

Build 276. His words: *use AI as prediction algorithm with user triggering*, and when he picks a
word, put it in the local database as the right choice.

## It is a teacher, not an engine

The n-gram already learns from everything he commits. This key reaches the same place in one press,
on the words the local model happens to be wrong about, at the moment he notices — which is the only
moment anybody knows the local guess was wrong.

> **The model is a teacher for the database, not a replacement for it.**

The same shape as the key search two builds ago, and now written down once for both in
`MANTRA_MANIFEST/modules/find-by-typing.md`.

**Only an AI pick teaches.** An ordinary candidate came from the local model or the dictionary, and
teaching a prediction engine its own output back is how it becomes certain of one word.

**The lesson is the word IN ITS CONTEXT** — the chosen word with the six before it. An n-gram learns
sequences; a bare word teaches it that the word exists, which it already knew, and nothing about when
to offer it. The whole point is fixing a wrong guess *in a context*, so the context is the part that
must be stored.

**Taught before the commit**, while the context that produced the suggestion is still the context on
screen. After the commit the chosen word is itself part of the text before the cursor and the lesson
would contain its own answer. The test asserts the order by position in the file.

## Asked, never automatic

Every press costs money and a second or two. Automatic would spend both on every word — including
the overwhelming majority the local model gets right — and would put a pause into typing, which is
the one thing a keyboard may never do.

The key is always the last thing in the row, whether or not there are candidates. A key that moved
with the number of guesses would be somewhere different every keystroke, and this row is used without
being read.

The words **replace** the row rather than being appended to it. Appending would leave him choosing
between two kinds of guess with nothing saying which was which, and the reason he pressed the key is
that the ordinary ones were wrong. Press again and the ordinary guesses come back.

## The filter that matters

Tapping a suggestion replaces the composing word. So **a suggestion that does not start with what he
has already typed would silently rewrite it.** Anything not continuing his letters is dropped rather
than shown, and that is the check with the most assertions on it.

`readWords` is defensive beyond that: a model asked for bare words will still sometimes number them,
quote them, bullet them or introduce them, and a candidate reading `1. "hello"` would be committed
with the numbering attached.

Nothing is ever auto-committed. A guess he paid for and has not read yet is the last thing that
should insert itself.

## Tested

Test 1: 30 checks, 0 failed — the reply reader against numbering, bullets, quotes, prose, duplicates,
runaway lengths and the continuation rule, and the lesson builder against trimming, empty context and
word order. Broken on purpose two ways — the continuation filter removed, and the teaching moved
after the commit — and confirmed red on both, five failures.

One check had to be fixed rather than trusted: `"import android" not in …` matched
`androidx.compose.runtime` and called a Compose state holder an Android dependency. **A check failing
on correct code is the fastest way to teach somebody to stop reading it.**

Not tested: no model has been called. How good the five words are, and whether one press is fast
enough to be worth pressing, are Test 2 and his.

## §156a — Red on one character, and a check that was deleted rather than shipped

Build 276 went red on `locale.displayLanguage` where `displayLanguage()` is a function. One
character. Kotlin says so instantly; CI took five minutes and one paid build to say the same thing.

The obvious guard is to collect the repo's function names and flag any `.name` used without
parentheses. I wrote it. Swept over the whole app it produced **434 hits**, essentially all of them
correct code — a name that is a function in one file is a property on a library type in another, and
`verify.py` cannot see library signatures to tell them apart.

**A check with 434 false positives is not a weak check, it is a harmful one.** It would have to be
argued with on every file, and a check that is argued with stops being read, at which point the real
hits arrive in the same voice as the noise. So it was deleted before shipping, and the reasoning is
written where the function would have been.

The same reasoning narrowed the delegation check and the duplicate-declaration check earlier today.
Here there was nothing left to narrow it *by*, so the gap stays open: Kotlin catches this instantly
and CI reports it in five minutes. That is the cost, and it is smaller than the cost of a script
nobody believes.

Also worth recording, because it wasted a step: sabotaging the fix to prove a check fires does not
work when HEAD already contains the bug — reverting to the broken line made the file identical to
HEAD, so `verify.py` saw no changed files at all and reported nothing. **When the last commit is the
red one, sabotage tests nothing.** Sweep instead.

---

# §157 — Two n-grams, one per language, never touching

Build 278. He asked, after the last build's finding, for the personal model to be split: *English
n-gram and Croatian n-gram, isolated pools. I can mix it manually in the middle of my text if I want
— one sentence in English, switch, another in Croatian, and the algorithm corrects and finishes.*

## What was wrong

One model for everything he wrote. It learned both languages into the same counts and offered
whichever was commoner, so typing Croatian produced English words with a matching prefix and the
reverse. The EN/HR badge already moved the transcription language, the keyboard subtype and the
shipped dictionary — **the personal model was the only part still deaf to it**, and every AI word he
accepted went into the same undivided store.

## Isolated, not weighted

`MaNgram` keeps a model per language and keys on `MaLanguage.active()` for learning, predicting,
saving and forgetting.

A shared model with a language column would have been the smaller change and the wrong one: one
language's counts would still decide the ranking of the other's. **That is the same failure wearing a
schema.**

Predict reads the active model only, never the other to fill an empty row. An English word offered
while writing Croatian is not a weaker suggestion, it is a wrong one.

**The language is captured at the commit, not inside the coroutine.** He presses the badge
mid-sentence; a sentence belongs to the language it was written in, not to the one showing when the
save happens to run.

Forgetting clears both. "Forget what you have learned", asked while writing English, cannot leave the
Croatian model standing — he would watch the count go to zero and still be offered his own words the
moment he pressed the badge.

## The mixed file could be thrown away, and that is not a loss

Nothing in `ma_ngram.tsv` records which language each count came from, and guessing per word is
exactly the mixing being ended. So it is deleted once, `maNgramBackfilled` is reset, and both models
are rebuilt from the dictation history — **which has carried a language per entry all along.** The
evidence for the split was already on disk; only the model was not using it.

## The AI half

The row now asks by the **badge** rather than by the keyboard subtype. The two are linked today, but
the badge is the control he presses and the thing the model is split by, so the whole prediction
stack reads one source.

The prompt names the language three times and forbids the other outright. That is not clumsiness:
the text before the cursor is frequently in the *other* language — a sentence in English, the badge,
a sentence in Croatian — and a model shown mostly English and asked for "the next word" answers in
English however the header is worded. **One mention loses to the evidence in the middle.**

## Tested

Test 1: 785 checks, 0 failed, including 384 walked turns — every order of six language-alternating
sentences, asserting after each that neither model has ever seen the other's words. Also that counts
do not leak, that switching the badge alone learns nothing, and that forgetting clears both. Broken
on purpose two ways — the models made to share a store, and predict pointed at the wrong model — and
confirmed red with 648 failures.

Not tested: nothing ran on a phone. The migration in particular is reasoned, not observed: whether
the history holds enough entries to rebuild a useful Croatian model is something only his phone can
say. If it comes back thin, the model is not broken — it is young, and it fills again as he writes.

---

# §158 — Three small things the row was missing

Build 279.

## Add was behind the keyboard

The moment the picker grew a search field it grew a keyboard, and the keyboard covered the Add button
at the bottom of the screen. Searching for a key left him with a ticked box, no visible way to accept
it and no visible way out — which is what his screenshot shows.

**Two fixes, and they are not duplicates of each other.** `imePadding()` is the actual bug: the
column now ends where the keyboard begins instead of underneath it. **Add beside Cancel** in the top
bar is the answer to what he asked for — both answers in one place, reachable without dismissing the
keyboard first, which is the state he is in while searching. It carries the count, because "Add"
alone gives no way to notice a tick that went astray two columns from where he was looking.

The bottom button stays. It is the primary target when the keyboard is down, and it is the one his
thumb already knows.

## The record key gets a meter and a clock

A vertical level meter on the left, the lamp in the middle, the elapsed time on the right. Time only,
as he asked — the megabytes live on the recording bar where there is room.

Both read **exactly the sources the recording bar reads**: `DictateController.audioLevel` and the
same `startedAtMs`/`accumulatedMs` arithmetic. Two ways of counting the same seconds would drift, and
a key and a bar disagreeing about how long a recording is would be worse than neither showing it.

**Only while recording.** Idle, the key is the red dot alone: a meter reading nothing and a clock
reading 0:00 are two more things saying "not recording" that the dot already says, and they would
take room from the one thing on the key that has to be found by touch.

**The meter is on the dB curve, not the raw level**, and the test asserts why: a linear bar sits at a
tenth of its height for an ordinary voice and only wakes up for a shout. Floored at a visible sliver,
so silence reads as "recording, hearing nothing" rather than as an empty slot.

## Send goes dim when there is nothing to send

`MaMagicTargets.sendVisible()` looks without pressing, using the same resolved term `pressSend` uses —
so the key cannot be lit for one button and press another.

**Polled every two seconds, not watched.** The accessibility service reports window changes, and the
case that matters is "the Send button became available inside a window that was already there", which
is exactly what happens when he finishes dictating. The loop lives and dies with the key: no key on
the row, nobody's view tree being walked.

**Dim, never hidden.** A key that disappears takes its neighbours' positions with it, and this row is
pressed from memory. Nothing may move because of something happening in another app.

## Tested

Test 1: 25 checks, 0 failed — the meter curve walked for monotonicity across 500 points and asserted
to put ordinary speech well up the bar, the clock formatted from zero to over an hour, and the wiring
of all three changes. Broken on purpose three ways — a linear meter, the send key always lit, the
picker back under the keyboard — and confirmed red with six failures.

Not tested: nothing ran on a phone. Whether a 3dp bar and a five-character clock fit legibly on one
key beside the dot is the question this build turns on, and only the screen answers it. If it is
cramped, the clock is the part to drop first: the recording bar already carries it.

---

# §158 — Anagram: the keyboard spells what is being read

Build 281. His words: *the letters will jump around the keyboard to form this word — it will look
like the keyboard became alive and is producing the letters back to the user.*

## What is actually moving, said plainly

Not the keys. The key grid is measured and laid out by the layout engine every frame, and a key
cannot be re-parented mid-frame without fighting it for control of its own position.

So `MaAnagramOverlay` draws a **tracing** of the grid across the key area, with a scrim underneath
that dims the real keyboard, and the letters that fly are drawn copies leaving from exactly where the
real ones sit. He sees the keyboard come alive; what is really happening is that a faithful tracing
of it does.

Worth stating rather than glossing, because the difference shows in one place: while the effect runs
the keys underneath are not pressable. Correct here — he is listening, not typing — but a consequence
rather than a design.

The scrim is 0.88 alpha, not opaque. A trace of the real keyboard underneath is what makes it read as
the keyboard coming alive rather than as a black panel with letters on it.

## Three things the geometry has to get right

**The rows are centred on each other.** The middle row has nine keys and the bottom seven; dividing
by a fixed ten would leave every row further left than the keys it is tracing, and the illusion dies
the moment a letter takes off from beside its own key rather than from it.

**QWERTZ or QWERTY by the language badge.** They differ in one swap, Y and Z. Getting it backwards
sends a letter across the whole keyboard to the wrong key — **and it is completely invisible while
testing in English.** The same badge already moves the subtype, so the tracing matches the keys
underneath in both languages.

**Accented letters arrive rather than being dropped.** Č, ć, š, ž and đ have no key of their own. A
plan that only included letters it could find a key for would have the keyboard spell *itanje* while
the voice says *čitanje* — the effect quietly lying about the word. They fade in where they land,
with no flight, and the word is always complete.

## Tested

Test 1: 79 checks, 0 failed — every letter of the alphabet has a key, rows centred on one another,
the Y/Z swap both ways round with all twenty-four other letters asserted identical, words from one
letter to twenty landing in order, on one line, inside the screen, and five Croatian words keeping
every character. Broken on purpose three ways — keyless letters dropped, rows left-aligned, one
layout for both languages — and confirmed red with twelve failures.

**One of those sabotages made the test THROW rather than fail**, on an unguarded `plan(...)[1]`, and
the count never printed. That is the second time today; it is now written in the file as a rule
rather than patched as a bug. *A check that raises is not a check that fails.*

Not tested: nothing ran on a phone, and this is the most visual thing in the app. Whether 260ms per
flight reads as alive or as frantic, whether the letters are legible at 22sp over a dimmed keyboard,
and whether it keeps up with Speechify at his reading speed are all Test 2 and all his.

## §158a — Red on an import that blamed something else

Build 281 went red on `import androidx.compose.runtime.collectAsState` in a file that reads a
preference. A jetpref preference is not a Flow, and its `collectAsState` is jetpref's own extension.

**What makes it worth a section is where Kotlin pointed.** The error names the `by` DELEGATE three
lines above — *"Property delegate must have a getValue method"* — so it reads as a delegation
problem, which is a thing this project has actually had before and has a check for. The delegate was
fine. The import two lines up was not.

`check_prefs_collect_import` closes it: a file that reads `prefs.…collectAsState(` must import
jetpref's. Both imported is fine, since a real Flow may be collected in the same file.

**Swept over the whole app before shipping it: 0 hits.** That sweep is now the price of admission for
any new check here — `check_property_call` produced 434 and was deleted for it, and a check is not
allowed to be trusted on the evidence of the one file that prompted it.

Proved against the red commit itself rather than by sabotage, because §156a applies again: HEAD *is*
the red build, so reverting the fix made the file identical to HEAD and `verify.py` saw no changed
files at all. Running the check directly against `git show HEAD:…` named the file and the reason.

---

# §158 — Matrix, four gestures, and four ways to stop

Build 283.

## Anagram out, Matrix in

Anagram spelled the word across a tracing of the key grid. He watched it and said it was not good,
and the reason is worth keeping: it read as *a clever thing happening to the keyboard* rather than as
a word being read. The eye followed the letters travelling and lost the word. **A reading effect has
one job — to put the word where the eye already is.**

Matrix keeps the part that worked, which is that the display is not a strip of text, and drops the
part that competed with the reading. The word stands in the middle; behind it columns of characters
fall; each letter settles out of the noise **one letter ahead of the voice**, because a letter
arriving exactly as it is spoken is too late to read.

Latin glyphs, not the film's katakana — partly the font, mostly that a screen of characters he cannot
read even in principle is noise in a sense nobody wanted.

Two clocks: the noise runs at a fixed speed, the resolve follows the speaking rate. One clock would
make the background speed up with the voice and turn it into a second thing to read.

It shares the void's window rather than being a new kind of window, so there is one copy of the
gestures rather than two.

## The gestures

| Gesture | What |
|---|---|
| swipe left **or right** | kill: voice, window, and a transcription in flight |
| swipe down | next sentence |
| swipe up | previous sentence |
| pinch | full screen, or back |

He asked for the left swipe to kill and asked me to suggest something for the right. **The best
suggestion was the same thing.** The gesture is reached for in one situation — something is running
that he wants gone — usually without looking. A kill that works from one side only is a kill he has
to aim, and a mis-aimed kill does something else instead; for a pair like *stop* and *pause* that
means the reading carries on while he believes he has ended it.

Down is forward, the rule the volume keys used to carry, and it outlived them.

## The volume keys let go

While reading they now return false, so the system hears them and they change the volume of the voice
he is listening to. That was the one thing the old mapping made impossible, and deleting a feature
produced it for free.

## Four ways to stop, all identical

The reader key, the ✕, the swipe, and **STOP** in the long-press dashboard — which had every control
for adjusting a reading and none for ending one. **A screen that can start a thing and cannot stop it
is a trap with settings.**

All four end the voice *and* cancel a transcription in flight, because from where he stands those are
one thing — the phone is busy with something I no longer want — and a stop that ended only the
visible half would leave the other running with nothing on screen to stop it.

## The rocker

The volume-keys key wore `VolumeUp`: a speaker with waves, which is the **reader key's** glyph. Two
keys with one picture is the fault the design language names first, and he found it by looking at the
row. It draws the physical rocker now — a rounded pill with + and − — because the key is not about
sound, it is about the two buttons on the side of the phone. Material has no glyph for that; every
candidate describes the sound instead of the button.

The usual objection to a hand-drawn shape beside real glyphs is that it reads as a patch. It does not
apply when the alternative is a glyph that means something else.

## Tested

Test 1: 7,920 checks, 0 failed, 3,939 walked points — every word length to 40 against every percent
of progress, asserting the resolve is never behind the voice, never past the end, and never reverses.
Plus the wiring: both sides kill, down is next, pinch zooms, the volume keys let go, no anagram left
anywhere. Broken on purpose two ways — left-only kill, and a stop that left the transcription running
— and confirmed red.

One check earned itself immediately: **matrix was in the effects list with nothing drawing it**, for
one commit. Picking it would have shown the plain highlight and looked like a setting that does
nothing. A style in the menu with no branch behind it is worse than an absent feature; the absent one
is honest.

Not tested: nothing ran on a phone. Whether the resolve reads at speaking pace, and whether a swipe
on a 96dp box is comfortable enough to trust as a kill, are both his.

## §158a — The third missing import, and the check that finally survived measurement

Build 283 went red on `Column` with no `import androidx.compose.foundation.layout.Column`. That is
the third build today lost to a missing Compose import, and twice already the obvious guard had been
measured, found to be noise, and left unwritten with a note where it would have gone.

**This time the narrow version survives.** Not "every capitalised symbol" — six names from
`foundation.layout` that always need that exact import, are never declared in this app's packages,
and are never Kotlin stdlib.

Measured before writing it, as the earlier notes demand: swept over the whole app it produced **one**
hit, `MaRows.kt`, which declares its own `Row` type. A file that declares the name means its own
thing by it, so declarations are skipped — and the sweep went to **zero**. Proved in both directions:
one hit on the broken file, naming the symbol; nothing anywhere else.

The difference between this and `check_property_call` is not cleverness, it is the closed list. A
guard over a set somebody can enumerate is checkable; a guard over "any symbol" is a guess.

---

# §159 — The reading starts at once

Build 285. He waited twenty seconds after pressing the speaker and asked why it could not begin
immediately, then described the answer himself: read the first sentence, fetch the next two while it
plays, then four, and grow from there.

## Two causes, and the first was already gone

**The key ring restarted at key one on every read.** With twenty-one keys and a few tired ones at the
front, every dead or throttled key ahead of the good one cost a full network round trip before a word
could be spoken. That was found earlier and fixed — the ring starts from the key that answered last —
and it was the larger half of the twenty seconds.

**The rest is arithmetic and cannot be argued with**: a whole screenful of text takes as long to
synthesise as a whole screenful of text. The only way to start sooner is to ask for less.

## His growth rule, and why both ends of it are right

One sentence, then two, then four, to a ceiling of sixteen.

- **Small at the start**, because that is the only part he waits through. One sentence is about a
  second.
- **Bigger later**, because each request has a fixed cost of its own — connection, round trip, the
  model warming — and forty one-sentence requests would pay it forty times and stutter between them.
- **Stops doubling at sixteen**, roughly a minute of speech. Past that a chunk takes longer to
  synthesise than the previous chunk takes to play, which is the exact moment prefetching stops being
  ahead and starts being behind. **A gap in the middle of a sentence is worse than a wait at the
  start**, because he cannot tell it from the reader having failed.

The prefetch is launched *before* playback begins, not after, since the point is to spend the current
chunk's playing time on the next chunk's synthesis.

Each chunk gets a numbered file. Alternating two names would be enough for two in flight, but
numbering means a slow request can never overwrite the audio that is playing.

## One timeline, and why that is the whole trick

The ticker, skip, previous, the caption and every effect were written for **one file and one word
list**. Teaching all of them about chunks would have been a large, risky change to code that is
carefully tuned.

So the chunks are hidden in `speak`/`playChunk`: `allWords` is the whole passage with each chunk's
timings shifted by the audio before it, and `chunkBaseMs` is that offset. **Nothing above that line
was taught about chunks at all**, which is the only reason a change this deep was safe.

`MaSpeechify.lastWords` now holds whichever chunk came back most recently — which, while chunk two
plays and chunk three is in flight, is chunk three. Everything that read it was moved to `allWords`,
including the caption, which would otherwise have jumped forward to text he had not heard and back
again.

## Sentence splitting is shared on purpose

`MaReadChunks.sentences` uses the same rule `skipSentence` uses. Two different ideas of where a
sentence ends would put chunk boundaries somewhere the skip could never land, and would drift apart
the first time either was improved.

## Tested

Test 1: 1,222 checks, 0 failed, 400 walked lengths — every passage from 0 to 399 sentences, asserting
every sentence is spoken exactly once, no chunk is empty, and every plan starts with a single
sentence. Plus the joined timeline: strictly increasing, no overlap, ending at the total duration, and
a position inside chunk two resolving to a chunk-two word. Broken on purpose two ways — a big first
chunk, and the offset dropped — and confirmed red with 401 failures.

A false positive was fixed rather than argued with on the way: `check_removed_declarations` reported
`file` as removed-but-used when a local `val file` became a function **parameter** of the same name.
A parameter declares a name as much as a val does, and the check now knows it.

Not tested: nothing ran on a phone. The one number worth measuring is whether a chunk ever arrives
late enough to leave a gap — the design says it cannot after the first few, and only his network can
confirm it.

---

# §159 — The window stops blinking, and a mode that never moves the line

Build 286.

## The blink

`if (index < 0) return`, and a doc comment defending it: *between sentences and while idle it draws
nothing at all — not an empty bar. A band of blank space that appears and disappears is more
distracting than the words it was meant to carry.*

**The reasoning was right and the rule built from it was wrong.** The distracting thing was never the
blank bar; it was the appearing and disappearing. The rule was safe while the whole passage arrived
as one file, because there was no moment mid-passage without a current word. Chunked fetching created
exactly that moment — between one chunk ending and the next starting — and the box vanished and came
back several times a reading. He called it blinking.

Now only IDLE hides the window. LOADING, SPEAKING and PAUSED all keep it, and a missing page draws an
empty box rather than returning. **Empty and still beats full and flickering**, which is what the old
comment was reaching for and stated backwards.

Worth noting for the next one of these: the comment was not wrong when it was written. It became
wrong when something underneath it changed. A rule that names the thing it is protecting against —
*appearing and disappearing* — can be re-read against new circumstances; a rule that only states what
to do cannot.

## Top line

The seventh effect. The sentence being read sits at the top edge, always, and what has not been said
yet sits under it, dimmed to scenery.

Every other style centres the reading in a page and moves a mark through it, so the eye tracks a
highlight down a block and comes back up for the next one. **This one never asks the eye to come
back.** For a page of text and a moving mark the hunt is the work, and he is dyslexic — the hunt is
the part that costs him. The line is in the same place every time and only the text underneath moves.

What has been read is dropped rather than greyed above. Keeping it would put finished text where the
eye lands first and push the live sentence back into the middle, which is the arrangement this exists
to leave behind.

## verify.py earned its newest check immediately

`check_layout_imports`, added one build ago after the third red build from a missing Compose import,
caught `Spacer` in this very change — before the push, in the second it took to run. That is the
whole argument for the closed-list approach: it was measured to zero false positives, so when it
speaks it is believed and acted on rather than argued with.

## Tested

Test 1: 7,926 checks, 0 failed. Three new: only idle hides the window, no bare index guard survives,
a missing page draws an empty box. Broken on purpose by restoring the old guard and the old return,
and confirmed red on all three. Every other suite green.

Not tested: nothing ran on a phone. Whether the window is genuinely still across a chunk boundary is
the one question that matters and only his eye answers it.

---

# §160 — The wrong-language words, and a gate that is nearly free

Build 287. He photographed the badge reading **HR** and the row offering *what · existing · are ·
for · to · and · in*, and said the n-gram was still broken.

## It was not the n-gram

Those words never came from the personal model. That one was split by language in build 278 and is
innocent. They came from the **shipped dictionary**, which is chosen per subtype and falls back to an
English one when the subtype has no dictionary of its own — so the badge moved, the personal model
moved with it, and the shipped provider carried on answering in English underneath.

**A provider whose locale disagrees with the badge is now dropped entirely.** Not ranked lower,
dropped. A wrong-language suggestion is not a weaker answer, it is a wrong one, and an English row
under a Croatian badge is worse than an empty row — the empty row tells the truth, which is that
nothing here knows Croatian yet except his own model, and that one fills as he writes.

Worth recording as a pattern: **the fix for the last bug was correct and did not fix the symptom**,
because the symptom had two sources and only one had been found. The screenshot was the evidence that
found the second.

## The gate, and why it costs almost nothing

His rule, and it is the right one: **a word already in a model is already in the right place.** It
was filed once and asking again would pay to be told what is on disk.

| Evidence | Answer | Cost |
|---|---|---|
| known to either model | leave it there | nothing |
| known to both | leave it in both | nothing |
| carries č ć ž š đ | Croatian | nothing |
| new and unmarked | learn under the badge, queue it | one word in a batch, once ever |

Walked over all sixteen combinations of known-en, known-hr, badge and diacritic: **two of them reach
the network**, and only until that word has been seen once.

There is deliberately **no matching "plain letters means English" rule**. Most Croatian words have no
diacritics, so it would be right often and wrong constantly, which is the worst kind of rule.

**Known beats the diacritic rule.** A filed word is not re-examined for any reason, including
evidence that would have been decisive when it was new. That is what makes the cost decay rather than
recur.

**The sentence is still learned whole into the badge's model**, because an n-gram learns sequences
and a sentence chopped into per-word destinations would teach neither model how the words follow each
other. The gate decides where a NEW WORD belongs; the queue corrects the badge afterwards if it was
wrong.

## The asking

Batched, capped at 60 a request, capped at 400 waiting, and **behind a button**. Never automatic and
never on the typing path: it costs money and a second, and there is no moment while typing when
either is acceptable. A word answered `both` — *radio, auto, student, film* — is learned into both
models rather than forced into one; forcing a choice would delete a suggestion he had earned to
satisfy a schema.

An answer naming a word nobody asked about is refused. **A model must not be able to add words to his
dictionary**, only to sort the ones already in it.

## The screen

Both models, each with words known and words read, a wipe each — wiping Croatian to fix Croatian
should not cost him a year of English — and the number waiting to be sorted.

## Tested

Test 1: 41 checks, 0 failed, the sixteen-case walk included. Broken on purpose two ways — asking
about known words, and letting the English dictionary back in under a Croatian badge — and confirmed
red with 15 failures. Every other suite green; one check in the split suite updated for a renamed
variable rather than deleted, since the claim it makes is still the one that matters.

Not tested: no classification has been run. Whether Haiku is actually good at single Croatian words
out of context is unmeasured, and it is the assumption the whole queue rests on. If it is poor, the
gate still works — everything simply stays where the badge put it, which is where it would have been
anyway.

## §160a — Suspending inside a lock

Build 287 went red twice on one shape: `prefs…set(…)` inside `synchronized(askLock) { … }`. `set`
suspends, and Kotlin refuses a suspension point in a critical section — rightly, because a coroutine
that suspends holding a monitor can resume on another thread and try to release a lock it does not
own.

**Decide inside the lock, write outside it.** The critical section computes the new list and hands it
out; the write is an ordinary suspending call in the coroutine that called it.

`check_suspend_in_lock` catches the shape, and was measured before being written: swept over the
whole app it found zero, so it stays silent until somebody writes it again. That is now three checks
added this way — layout imports, this one — and two deleted for failing the same measurement. The
rule has settled into something simple: **a guard over a shape somebody can describe in one sentence
is checkable; a guard over "anything suspicious" is a guess, and a guess with a hundred false
positives is worse than the gap it fills.**

---

# §161 — The overlay becomes a hairline

Build 289. He looked at the recording notch and asked for one thing: a meter, one pixel tall, edge to
edge, and nothing else.

## He was applying this file's own rule to it

The header of `MaRecordingLine.kt` already said it: **this is not a control, it carries one bit — the
microphone is open — and the smallest shape that carries one bit is a line.** Then six things grew on
it: a meter, a red dot, a clock, a bin, a send arrow and the language badge.

Every one of them had a better home already. The bin and the send are volume down and volume up. The
clock and the badge are on the keyboard. And none of them were reachable without looking at the top
of the screen, which is the one thing somebody dictating into another app is not doing.

## The click-through problem dissolved rather than being solved

The notch was narrow *because* it was touchable: a window is touchable or it is not, there is no
per-region setting, and a full-width strip would have stopped every touch in the corners where apps
put their buttons. So it was made only as wide as its contents and pushed to the middle — the corners
were click-through because there was nothing there.

With no controls on it that whole argument disappears. `FLAG_NOT_TOUCHABLE` goes on, every touch
reaches the app beneath, and the strip costs him nothing at all. **The constraint was never the
width; it was the buttons.**

One device pixel, not `px(1)` — on his phone that is three, which is a stripe rather than a hairline.
A line is legible at any height as long as it is moving.

The once-a-second ticker went with the clock. The meter's own 40ms redraw stays, because that is the
meter working.

## Tested

Test 1: 18 checks, 0 failed — full width, one pixel, top gravity, horizontal meter, exactly one view
added, no touches taken, and every removed control asserted absent by name. Broken on purpose by
restoring the notch geometry and dropping the touch flag: red on three.

A check had to be fixed before it was trusted: it banned every `postDelayed` and failed on the
meter's animation loop — **a check firing on the one thing the file exists to do.** It bans the
clock's once-a-second tick now and asserts the meter's loop is still there.

## The one thing worth watching

With the controls gone, stopping a recording from another app is volume up, and cancelling is volume
down. Those keys can now be switched off by the key added in build 272. **Switched off, with the
keyboard hidden, there is no way to stop a recording except opening the keyboard.** That is not new
today and it is not wrong — the hairline is honest about the microphone being open, which is what it
is for — but it is the corner where two good decisions meet, and it is written down here so the next
person meets it on paper rather than on the phone.

---

# §162 — The slideshow, the alignment, and six short words

Build 290.

## The text was still blinking

Build 286 stopped the *window* vanishing between chunks. He came back and said the text was blinking
now, and he was right: **an empty box that fills again is a blink whether or not the box survived.**
The fault had simply moved one layer in, and the fix at that layer was the same shape.

The last page that had content is held and redrawn while there is nothing current. His word for it
was slideshow, and that is exactly the behaviour: one thing is on screen until the next is ready, and
**the change is the only event.**

Two details that matter more than the hold itself:

- `remember` sits outside the null check, so the held page survives the recompositions where there is
  nothing current. Inside it, the hold would be discarded at exactly the moment it is needed.
- Keyed on the passage. A new reading starts with nothing held, or the last sentence of the previous
  reading would appear under the first second of the next — a caption for something nobody is saying.

Blank before the very first page is kept, and is honest: it says the reading is coming.

## Alignment, and one effect promoted out of the list

Top, middle or bottom, in the empty space beside the highlight swatch that he pointed at. **Zero
delay in all three** — the line is drawn where it belongs rather than travelling there. An animation
between sentences puts movement on screen at the moment he is listening rather than looking, which is
the opposite of what a caption is for.

**The "Top line" effect is deleted, and that is a promotion.** It pinned the reading to the top edge
as one style among six. Where the line sits turns out to be a question worth asking of *every*
effect: an effect decides what the marking looks like, alignment decides where it sits. Two
questions, two controls, and every answer to one now works with every answer to the other — instead
of one combination being available and the rest not.

Keeping both would have been two controls for one job, which this archive complains about roughly
every fortnight.

## Six short words

Hi · Ty · Kar · Sp · Vo · Mx, sized to a key on the feature row underneath — he asked for that by
pointing at the row, and he was right that a chip taller than the keys makes the panel look like a
different app.

All six fit one row now. **A chip behind a scroll is a chip he does not know exists**, which is the
real cost of the long labels rather than the space they took.

The short form is an abbreviation of the name and never a different name, and the test asserts it:
somebody reading the chip and somebody reading the settings must be looking at the same word.

## Tested

Test 1: 536 checks, 0 failed, 510 walked sequences — every pattern of present and absent pages up to
eight long, asserting **never blank after the first page**. Broken on purpose by dropping the hold
and by hardcoding the alignment: 470 failures.

Two checks in the matrix suite were rewritten rather than deleted when the top-line effect went, so
the suite now asserts its *absence* — a deleted feature with its checks quietly removed is how a
feature comes back by accident.

## §162a — The fourth missing import, and a duplicate argument

Build 290 went red on three lines: `mutableStateOf` and `remember` used with no `compose.runtime`
imports, and a `contentAlignment` passed twice to the same `Box` — my new one added above the
existing `if (full) Center else TopStart`.

The duplicate is the more interesting mistake. I added an argument without reading whether the call
already had it, which is the same family as the range cuts that took live declarations: **editing by
what I expected to be there rather than by what was there.** The old line was not redundant either —
it guessed the alignment from the window size, and his setting is the thing that replaces it. Deleting
it was right; passing both was not a compromise, it was a compile error.

`check_layout_imports` now covers `compose.runtime` as well as `foundation.layout` — same closed-list
discipline, measured before writing. The sweep found two hits, `MaLivePrompts.remember` and
`MaAiPredict.remember`, both files that DECLARE a `fun remember` of their own; the existing
declaration skip covers it and the sweep went to zero. It fires on the real broken file, naming the
symbol and the package.

Four builds have now been lost to missing Compose imports and three of them are closed by one check
that took two measurements to get right. The remaining gap is arguments and types, which needs a
compiler, which is what CI is.

---

# §163 — The badge on the sending line

Build 292. He watches the spinner, knows from the badge that the answer is coming back in the wrong
language, and until now could only wait for it to be wrong and re-transcribe from the history.

## Both halves, or neither

The badge shows the language while the request is in flight, in the same form the history rows use,
because it answers the same question and a reader who has learned it in one place should not have to
learn it twice.

Tapping it cycles the language, **cancels the request and sends the same audio again**. That is the
only honest answer to the tap: the language is settled before the upload — a field in the request,
and on AUTO the result of a probe that has already run — so there is no way to change it mid-flight.
A badge that appeared to change the language of a request already on the wire would lie about the one
thing he tapped it for.

**Seeing a mistake and being unable to act on it is worse than not seeing it.** Shipping the badge
without the tap would have been half a feature and the frustrating half.

The old request is cancelled rather than left to land: two answers racing would mean the one that
arrived last won, and which that was would depend on the network. The audio is untouched — the same
file, sent again — so nothing is re-recorded.

## Two checks that were not checking

Both found by sabotage, which is the entire reason for doing it.

**The model could not express the bug.** The walked port reassigned `jobs` wholesale on every tap, so
deleting the cancellation changed nothing and the sabotage run came back green. It appends now, with
the cancellation as the thing that keeps the list at one. Sabotaged again: 241 failures.

**A wiring check passed from the wrong function.** It searched the whole of `DictateController` for
the state guard — and `cancelTranscription`, a few lines below, contains the same guard and the same
`transcribeJob?.cancel()`. Removing the guard from the resend left the test green. The three checks
now extract the body of `retranscribeInLanguage` and search inside it.

**A check that can be satisfied by a different function is not checking the function**, and a model
that cannot express the bug cannot test for it. Neither was visible by reading; both took a
deliberate break to expose.

## verify.py, again

`check_duplicate_declarations` reported `audioFile`, `isReplay` and `source` as declared twice: they
are constructor parameters of two different data classes at the same brace depth, coexisting for
months. Parameters belong to their own class. It tracks paren depth now and skips anything declared
inside a parameter list — swept to zero across the app, and still catches a real duplicate function.

## Tested

Test 1: 527 checks, 0 failed, 160 walked taps — any sequence of five taps at any point, asserting
**never two in flight**, always the current language, always the same audio, and recorded exactly
once. Fifteen other suites green.

Not tested: nothing ran on a phone. Whether the badge is reachable with a thumb at the end of a line
that shallow is the open question, and if it is not the answer is padding rather than a redesign.

## §163a — weight without a scope

Build 292 went red on `Modifier.weight(1f)` in `TranscribingContent` — a composable that is *called
into* a Row rather than declaring one, so `RowScope` belongs to the caller and the extension does not
resolve.

It did not need the weight anyway: the status line is `maxLines = 1` with an ellipsis, so it takes
what it needs and leaves the badge the rest. The alternative — making the function a `RowScope.`
extension — would tie it to one kind of parent for the sake of one modifier.

`check_weight_scope` catches the shape, and the measurement is the part worth recording. **The first
sweep reported one hit and the hit was the detector's fault**: it looked for `Row(` and missed
`Row {`, so a correct file looked wrong. One hit is exactly where the temptation is to accept the
noise and move on. With the trailing-lambda form counted it swept to zero, and it names the function
on the real broken file.

Five checks have now been added by measuring first and two deleted for failing the same measurement.
The discipline has paid for itself twice today alone.

---

# §164 — The sending line becomes a terminal

Build 294.

## The badge moves to where his thumb goes

```
recording:  ENG · bin · 0:23  2,1 MB · send · gear
sending:    ENG ·  X  · ⠹ status…
```

One build ago the badge was at the *end* of the sending line, which was tidy and wrong. **A control
that moves between two views he switches between constantly is a control he has to find twice.** He
reaches for it without looking; it is in the same place in both now.

The bin's position becomes **X** — same place, same job, throw this away. A letter and not a drawn
bin, because the two lines are the same skeleton in different voices: **the recording bar is an
instrument panel, this is a terminal.** Where the recorder counts up in figures, this one turns a
braille spinner. Both answer *is it still alive*, one by counting and one by turning.

## Hold is a cancel that keeps the tape

There is no pausing a request already on the wire — it is thrown away. What makes this a hold is what
is **not** thrown away: the audio, and the fact that he still means to send it. **The distinction the
user cares about is not whether the socket closed, it is whether the recording is still his.**

`Transcribing(held = true)` — a flag on the existing state rather than a fourth state, because
everything that asks *is the recorder busy* must keep answering yes. A new state would have meant
auditing every `when` in the app for a case that means what Transcribing already means.

**While held, the badge sets the language and does not send.** That is the whole reason the hold
exists: he stopped it in order to choose, and sending the instant he chooses would take the decision
away at the moment he was making it. Stop · choose · send, two taps around whatever he needs in
between.

No spinner while held. Nothing is turning because nothing is happening, and a spinner over a stopped
request is precisely the lie this line exists to avoid.

The middle is the whole target — spinner and text together — because it is pressed while watching
rather than aiming, and a small button beside the text would be one more thing on a line whose job is
to be read.

## Tested

Test 1: 4,433 checks, 0 failed, 1,375 walked taps — every sequence of five actions drawn from hold,
ENG and HR, asserting **never more than one request in flight**, **held means nothing in flight**, and
**the audio survives every tap**. Plus X from both states, and that nothing can resume after a kill.

Broken on purpose two ways — the hold throwing the audio away with the request, and the badge sending
while held — and confirmed red with 797 failures.

One check needed updating rather than deleting: the guard in `retranscribeInLanguage` became a cast
because the function now needs the state's `held` flag, so the check reads the cast. Same refusal,
different spelling.

Not tested: nothing ran on a phone. Whether the middle is comfortable to hit without looking, and
whether the held line reads as stopped rather than broken, are both his.

---

# §165 — Three faults on one line

Build 295. He used the sending line for a day and found all three.

## The badge that worked perfectly and looked dead

`Text(text = MaLanguage.badge())`. A plain function call: the tap wrote HR, the preference changed,
and Compose had no reason to redraw anything. The letters stayed ENG.

**The same fault as the wand bar, and the same sentence fixes it: the bar has to be told, not asked.**
The badge is derived from `maLanguageMode` collected as state, so the letters come from the thing the
tap writes and cannot disagree with it.

Worth naming as a pattern, because it has now happened twice in this app and both times the code
looked obviously correct: **a value read from a store inside a composable is a snapshot unless it is
collected.** Anything a tap changes must be observed, or the control is a mime.

## The tap that cancelled instead of sending

Releasing a hold re-ran the **speech gate** — the pass that decides whether the audio contains speech
and trims the silence out of it. That audio had already been through it once, which is what "held"
means. Run again on the trimmed result it found nothing worth sending and raised an error, and from
where he was standing a tap that should have sent had thrown everything away.

`gate = false` on release, exactly as `retranscribeHistoryEntry` has done since it was written. The
same problem in a different hat, solved once already and not looked at.

And the hold now **copies the audio to a file of its own** before dropping the request, because the
trimmer rewrites the original in place: "send the same file" was sending whatever the trimmer had
left behind. The history replay has copied to a temp file since day one for the same reason.

Both halves of this bug were solved years of builds ago somewhere else in the file. Reading how the
neighbouring feature does it is cheaper than finding out.

## Brackets and room

`[ENG] [×] [⠹ status…]`, 12dp between. He said he was missing them, and the layout deserved it:
these are three separate decisions and two are irreversible in opposite directions. **Where the cost
of a miss is asymmetric, the gap is not decoration.**

The brackets around the status group are their own characters rather than part of the text, because
that text is machine output and is often ellipsised — a bracket that vanished with the overflow would
make the row look broken rather than truncated.

## Tested

Test 1: 4,441 checks, 0 failed, 1,375 walked taps. Broken on purpose by putting the gate back on
release: red.

One check had to be re-aimed rather than kept: it asserted `text = MaLanguage.badge()`, which is
precisely the dead code he reported. **A check written against the broken spelling keeps passing on
the bug**, so it now looks for the observed form.

---

# §165 — F1, F2, F3, and rows that trade places

Build 296.

## The keys

Three keys, one per feature row, showing and hiding it from the keyboard. The switchboard has had
these three switches all along — three screens away. **A control for what is on screen belongs on the
screen it controls.**

Each wears the green ring while its row is showing: the same ring the buckets and the volume key
wear, because he asked for an outline and the app already had one that means exactly this. One ring,
one green, one meaning.

Written once for the three rather than three times. The only difference between them is an index, and
three copies of one key would be three places for the ring to disagree with the row.

A key can switch off the row it is standing on, and that is the point rather than a trap: the same
key on another row brings it back, and `visibleRows` already refuses to leave him with no keyboard.

## Swap, never insert

"Row 2 becomes → Row 1", on the tab, taking keys, arrangement and on/off state in one move.

He said swapping and he was right. **Three rows exist, always.** A move that shuffled the others along
would renumber a row he never touched — and row 1 is the one his thumb reaches without moving, so an
arrangement built by muscle memory must not shift under him because he rearranged something else. A
swap changes exactly two things and leaves the third alone.

The `enabled` flag travels with the keys, not the position: he switched a row off because he did not
want *those keys* today, not because he did not want a row in that place.

**The tab follows the keys.** He was looking at a set of keys; after the swap they are on the other
row, and leaving him on the old tab number would show him something different and read as the swap
having gone the wrong way.

Through `commit`, like every other edit on that screen, so there is no path that updates the state
without writing the preference.

## Tested

Test 1: 6,582 checks, 0 failed, 2,187 walked swaps — every sequence of three swaps over three rows,
asserting **still three rows**, **no row lost**, **no row duplicated** after each. Plus the toggles,
and that a swap is its own undo.

Broken on purpose by making the swap an insert — the shape he explicitly did not ask for — and it
went red on three: the flag stayed with the position instead of the keys, and the operation stopped
being reversible. **The walked property is what tells the two apart**, since both look like "the rows
moved" from a single example.

Not tested: nothing ran on a phone. Whether three more keys fit a row he already fills is his to see,
and if they do not, F1–F3 are exactly the keys to put on a row he switches off.

---

# §166 — Paying for silence

Build 297. He is watching the bill and asked for the app to be made cost-effective.

## The thresholds were doing almost nothing

`TRIM_MAX_SILENCE_MS` was **2000**. Only a pause longer than two seconds was touched — and a real
dictation is made of half-second and second-and-a-half pauses. Modelled on a plausible dictation, the
old numbers saved **nothing at all**: every gap in it was under the threshold.

700/250 now. 700ms is above the longest gap inside fluent speech and below the shortest deliberate
pause: a gap that long is somebody thinking, not somebody speaking.

**250ms is kept rather than 0**, and that is not timidity. A hard cut mid-breath sounds like a splice
and makes the recogniser guess at the join; a sentence with every pause removed reads back as one
breathless run; and the padding is what stops the first syllable after a pause being clipped. **A
clipped word costs a re-record, which costs more than the silence did.**

## The saving is logged, every time

`trimmed 34% of the audio before upload`. **A saving nobody can see is a saving nobody can trust** —
and if these thresholds are ever wrong, that line is how it gets noticed, either by being
suspiciously large or by being stuck at zero.

## Two cost problems found and NOT fixed

Both need a decision or a verification I could not make alone, and both are worth more than the trim.

**1. The async path is on a model he is not pricing.** `defaultTranscriptionModel = "universal-3-pro"`,
with `universal-2` offered in the picker. His price sheet has Universal-3.5 Pro and
Universal-Streaming. Whatever the async path bills, it is not one of the two rates he quoted. The fix
is a two-line change, but the sync path passes that model id as an `X-AAI-Model` **header** while
async sends a body field — those are not interchangeable, and I will not switch it on the strength of
a guess about the schema.

**2. `longform` skips trimming entirely.** `if (gate && !longform && …)`. Longform is the mode for the
longest recordings, which is exactly where silence costs most. The exclusion presumably exists for
latency on big files. **It is backwards for cost**, and it is his call whether a longform dictation
should wait a little longer to upload half as much.

## Tested

Test 1: 1,525 checks, 0 failed, 504 walked timelines — every arrangement of speech and gaps up to six
segments at four length profiles, asserting **no speech is ever lost**, the upload is never longer
than the recording, and no segment vanishes. Plus a modelled dictation showing the old thresholds
saving nothing and the new ones saving real time.

Broken on purpose two ways — padding to zero, and a trim that eats 100ms of every speech segment —
and confirmed red with 483 failures.

Not tested: nothing ran on a phone, and no audio was actually trimmed. The percentage in the log is
the measurement that matters and it will only exist once he dictates.

---

# §167 — The switch says what it saved

Build 298. He asked for a setting to turn trimming on and off so he could experiment and see the
result. **The setting already existed** — Settings → Dictation → Trim silent gaps, default on, with
`Skip silent recordings` above it. What did not exist was the result.

That gap is the whole story of the last two builds: the switch sat at a threshold that saved
essentially nothing on a real dictation, for months, switched on, telling him nothing. **A setting
that cannot be evaluated is a setting nobody can decide about** — and nobody did decide about it,
which is exactly why nobody noticed.

So the switch reports its own last outcome: *Last dictation: 34% less audio uploaded*. Off, on,
dictate the same paragraph twice, compare. That is what he asked for, and it is the part that was
missing rather than the switch.

Appended to the summary rather than replacing it, so the switch still explains itself before the
first dictation, when there is nothing to report. `-1` means never run, and shows nothing at all
rather than `0%`, which would read as *this does not work*.

The percentage is clamped to 0..100. A trim that somehow grew the file must not report a saving of
minus twenty percent, and a rounding artefact must not report 101 — **an impossible number on a
screen destroys trust in every number beside it.**

## Tested

Test 1: 1,536 checks, 0 failed. Eleven new, including the clamp at both ends and the divide-by-zero
case. Broken on purpose by removing the report: red on two, one of them the *nothing before the first
run* case, which is the one a careless fix would get wrong.

---

# §168 — The delivery gate, run, and the one thing it stopped

Build 299. He asked for the nine gates from `MANTRA_MANIFEST/modules/delivery-gate.md` to be run
against the app, and then for the findings to be acted on.

## What the gate actually found

Five of nine ran here; G6, G7 and G8 need a phone. One blocking finding, and it was real.

**G1: every action pinned by a mutable tag.** `actions/checkout@v4` and three others. A tag is
whatever its owner points it at, and this workflow runs with a token that can push to every
repository in the account — the module cites GhostAction, where repos pinned to moving tags pulled a
modified action and their CI secrets were taken, with no compromise of the victim's own account
needed. All four are pinned by commit now, each with the tag it came from in a comment beside it, and
the rule for updating one written above them.

**G7's `+0 bytes` was a true zero, and worth the ten minutes it took to disbelieve.** Two builds,
different sha256, byte-identical file size. §14 says a suspicious zero is a failure of the check until
proven otherwise, so the APKs were opened and diffed entry by entry: 250 entries each, five differ,
`classes.dex` is **+1,920 bytes**. The zip total landing on the same number is alignment padding
absorbing the delta. **The file size was the wrong metric** — the sum of uncompressed entry sizes
(50,001,970 vs 50,000,050) is the one that moves, and that is what a future G7 should record.

## What is now automated, and what deliberately is not

§13: G1, G2 and G3 belong in CI on every build, failing rather than warning, because "everything a
person has to remember will eventually not be remembered".

    G3   verify.py and all 18 Test 1 suites          BLOCKING
    G5   count of while(true) in the tree, printed   recorded, not a threshold
    G2   the built APK scanned for key shapes        BLOCKING
    G3   Android Lint                                MEASURING, continue-on-error

**Lint is the one not turned on.** `-x lintVitalRelease` has been on the build line since before the
gate existed — Lint has never run on a release of this app. The gate says it should be fatal. It is
not being made fatal blind: §5.2 says a check that produces noise gets fixed, narrowed or deleted in
the session it produced it, and that a check which cries wolf is worse than no check. So the first
build reports the error and warning counts and blocks nothing. Once the number is known and the real
findings are cleared, the flag comes off.

That is written in the workflow beside the step, so the next session finds a decision rather than an
oversight.

## The record

The full delivery record for build 298 was produced in chat rather than committed, and that is the
next gap: G9 asks for a document with a fixed shape so two releases can be compared. The NOT TESTED
block for this app is currently longer than the pass list — no soak, no monkey, no upgrade test, no
device of any kind on this side — and per §11 that makes it a more believable delivery than one with
nine passes and an empty exclusions list, not a less believable one.

## §168a — The gate that went red on a clean build

The first run of the new G2 step failed, and the artefact was clean. `set -o pipefail` plus a `grep`
that exits 1 when it matches nothing: **the check reported failure because it found no secrets.**

A check that goes red on success is worse than no check, because it teaches you that red means
nothing — and this one would have done it on every single build.

§14 says make every check fail on purpose once, before trusting it. The inverse turned out to matter
just as much here: **make it pass on purpose once.** I wrote a scanner and confirmed it could stop a
build without confirming it could let one through.

Lint's measurement, from the same run: **67 errors, 971 warnings**, on a codebase where Lint has
never run on a release. That number is the entire justification for not making it blocking in the
same commit that switched it on — a gate that arrives with 67 errors is a gate that gets deleted or
bypassed on the day it is added, and the module says so in §5.2.

---

# §169 — Six rows, and four arrows

Build 301.

## The upgrade was free, and it was not luck

`ROW_COUNT` 3 → 6. `parse` has padded a short read to exactly `ROW_COUNT` with empty, disabled rows
since it was written — so a stored three-row arrangement opens as three arranged rows and three empty
ones, nothing he built moves, and nothing new appears on the keyboard until he puts something on it.

**The other direction is not free**, and the constant now says so. `parse` truncates as well as pads,
so a six-row arrangement read by a three-row build loses rows four to six the first time the app
writes. That is exactly the delivery gate's rollback clause — a change to the meaning of stored data
that cannot be rolled back — and the note at the constant is the version marker's cheap cousin: a
warning to the next person that lowering this number deletes his work.

F4, F5 and F6 join F1–F3. The key that draws them was already written once for the three rather than
three times, so it became six by extending one `when`.

## The arrows

Left, right, up, down. They matter more here than on an ordinary keyboard: he dictates rather than
types, and moving a cursor by touch means aiming at a caret a few pixels wide in text he can barely
see — the gesture with the worst accuracy on the phone. **A key press cannot miss by three
characters.**

They send the letter keyboard's own key codes through `keyboardManager.tapKey`, not their own
movement logic, so long-press repeat and shift-selection are whatever they already were. Written once
for the four, because four copies of one key is four chances for one of them to drift.

Filed under **Getting about** rather than Editing the text: an arrow changes where he is, not what is
written.

## Tested

Test 1: 7,810 checks, 0 failed, 2,592 walked swaps — now over six rows rather than three, with the
count read from a constant instead of written out. Plus the upgrade modelled directly: an old
three-row arrangement pads to six, the new rows arrive empty AND switched off.

Broken on purpose by putting `ROW_COUNT` back to 3: red, on the check that says the keys and the
model must agree.

One check had to be repaired first: it compared a swap against the literal names `keysA`/`keysB`,
which the six-row fixture no longer produces. **A test that repeats its fixture's contents by hand
fails on the day the fixture changes, for no reason at all** — it reads from the fixture now.

---

# §170 — The tabs are dragged

Build 302. The "Row 2 becomes → Row 1" buttons are gone and the tabs are dragged instead.

At three rows the buttons were two per tab. At six they were **five per tab, thirty sentences to
read**, for something a finger says in one movement. Six rows made a workable control unworkable, and
that is worth noticing as a shape: a control that scales with the number of things it operates on
stops being a control at some size, and the size is usually smaller than you expect.

## The semantics changed, on purpose

The buttons **swapped**. The drag **moves**, and §165's argument for swapping does not carry over —
it was an argument about a BUTTON. A sentence that names two rows and touches two rows is honest; an
insert that silently renumbered a third would be a surprise.

A drag says something different. Dragging the third tab to the front depicts sliding it in front of
the others, and every draggable list on the phone behaves that way. **The gesture is the
specification.** A drag that swapped would leave the row he dragged past sitting where he started,
which is not what his finger drew.

`swapRows` stays in the model, still correct and still tested. It is simply no longer what the editor
calls. `MaFeatureOrder.move` has done exactly this for keys within a row since it was written, with
the same reasoning written out.

## Long press first

A plain tap still selects, so the gesture he uses constantly is untouched and reordering is
deliberate. A drag that began on the first pixel of movement would reorder his rows every time his
thumb slid slightly while tapping — and he would not know which of the six had moved.

The target is the distance travelled divided by the tab width, **rounded**: the row lands where the
finger is rather than where it has fully passed. A drag needing 51% of a tab to register reads as
being ignored.

## The check that read its own import

The check for the long press searched for `detectDragGesturesAfterLongPress` anywhere in the file —
and passed with the long press removed, because **the import line still contained the name.** A check
satisfied by the very line the mistake would leave behind.

It matches the call now, with the trailing paren, plus a second check that no bare `detectDragGestures(`
appears. Found by sabotage, as usual, and it is the third check in a fortnight that was passing for a
reason unrelated to what it claimed.

## Tested

Test 1: 15,602 checks, 0 failed, 5,184 walked reorders — every sequence of two moves over six rows,
asserting six rows survive, nothing is lost and nothing is duplicated, exactly as the swap suite does.
Plus the distance-to-index arithmetic at both clamps.

Broken three ways: the drag made a swap (red, including *a move is not a swap*, which is the check
that would catch somebody quietly reverting the semantics), and the long press removed (red, once the
check was repaired).

---

# §171 — One shift, not two

Build 303. He circled the feature row's Shift and asked for the letter keyboard's shift instead —
identical function, caps, lockable, all of it.

## What it was doing

Writing `inputShiftState` directly, cycling off → once → locked on every tap. **It produced capitals,
which is why it survived this long, and it was still a second shift:**

- caps lock needed three taps rather than the double tap everybody's thumb already knows
- an auto-shift at the start of a sentence was *cleared* by touching it, because the cycle started
  from wherever the state happened to be
- `handleShiftDown` and `handleShiftUp` never ran, so the Gboard-style recapitalisation of a
  selection — cycle a selected word through Title, UPPER, lower — did not exist on this key
- nothing re-evaluated after a sentence ended

**Two implementations of one key is one too many**, and the second one is always the one that drifts,
because it is the one nobody thinks of when the first is improved.

## What it is now

`inputEventDispatcher.sendDown(TextKeyData.SHIFT)` then `sendUp`. That is exactly what the letter key
does, so every behaviour above arrives without being written here at all.

**Both halves, and in that order.** The lock is decided inside `handleShiftUp`, from whether the
dispatcher saw an uninterrupted sequence. A tap that sent only the down would arm shift and never be
able to lock it — which is the same failure the volume keys had, in a different room.

Three states, three appearances, matching the letter key: off plain, armed lit, locked lit and
wearing the capslock glyph. An armed shift that looked like an off shift is how a sentence comes out
wrong with nobody able to say why.

## The check that read the whole file

`"activeState.inputShiftState =" not in row_ui` — and it failed, correctly, on a **different key**:
`MaNextFieldKey` consumes shift as a modifier for backwards-TAB, which is a different job and right
where it is.

**A check that reads the whole file to make a claim about one key will find every other key that
touches the same thing.** It reads the SHIFT branch only now — the same fix as the
`retranscribeInLanguage` checks two weeks ago, which passed because `cancelTranscription` contained
the same line.

## Tested

Test 1: 15,607 checks, 0 failed. Broken two ways — the key-up removed, and the whole thing reverted
to writing the flag — red on two and three respectively.

---

# §172 — The ring convention reaches the keys that predate it

Build 304.

## The three zone keys had it backwards

Monochrome ring meaning "this is a switcher", green **glyph** meaning "the zone is open". Everywhere
else in this app the ring carries the state and the picture holds still — the buckets, the volume
key, the record key all learned that over the last fortnight. **These three were written before the
convention existed and were never revisited**, which is how a house style ends up with three
exceptions that nobody decided on.

He photographed them and said they were not following the convention. Green ring on, cream ring off,
glyph constant.

**The ring is always there**, in one colour or the other, rather than appearing when the zone opens.
Same reason the reading window keeps its box when there is nothing in it: a border that appears and
disappears makes the row flicker as he works.

Worth keeping as a pattern: a convention introduced late does not apply itself retroactively. Every
key written before it is an exception until somebody looks. **He is the one who looks, because he is
the one holding it.**

## The copy row

`select all · paste · cut · clipboard history · AP · AC`, and the transcription history key is off it.

It sat between clipboard history and AP and belonged to neither side. Everything else on that row
acts on what is on the clipboard right now; that one opened a list of things he had dictated. **Two
ideas of "history" one key apart**, and the one that did not fit is the one that got pressed by
mistake. Still in the catalogue under Dictation, where anybody looking for it will look.

Only the DEFAULT changed. A stored arrangement is his and is not rewritten — so this reaches him only
if he has never edited the copy row, and the handoff says so rather than leaving him to wonder why
nothing moved.

## A shadowed variable in a test

The new block reused the name `order`, which already held the text of `MaFeatureOrder.kt` further up
the file. Two unrelated checks — SEND and RECORD are in the catalogue — went red immediately,
pointing at code that was completely fine.

**A shadowed variable in a test file is a false failure pointing at innocent code**, and the ten
minutes it costs are spent looking in the wrong place. Caught here only because those two checks had
no business failing in a commit that touched neither key.

## Tested

Test 1: 102 checks, 0 failed. Broken on purpose by reverting zone 2 to the old convention: red on
both of its checks, and not on zones 1 and 3 — which is what makes it a check on each key rather than
on the file.

---

# §173 — Copying when the keyboard has been taken away

Build 306.

## The failure

Selecting text in another app makes Android collapse the keyboard, and the input connection goes with
it. Pinning keeps the keyboard on screen and **does not give the connection back** — so
`activeContent.selectedText` is empty, `getSelectedText` returns null, and the copy key said:

> *Failed to retrieve selected text requested to copy: either selection state is invalid or an error
> occurred within the input connection.*

Accurate, and no use to anybody holding a phone.

**The selection never went anywhere.** It is on the screen, in another app's view, and the
accessibility service can see it and act on it. The keyboard lost its handle on the text; the text is
still there.

## `ACTION_COPY`, on the node, not the text

The view that owns the selection performs the copy itself, so it copies exactly what that view thinks
is selected — including a partial selection inside a formatted document, which reading `text` and
slicing by index gets wrong the moment the view's idea of an index is not a character offset.

A selection of zero length is a cursor, and copying a cursor is a no-op that would look like success
and leave the old clipboard in place. Refused.

**Second route, not first.** When the connection works it is faster, needs no permission, and is what
every other keyboard does. This runs only after the first has come back empty. And when both fail the
message now says *"Nothing to copy — select some text first"*, because at that point it is true and
it tells him what to do.

## The copy row, from his staging row

`select all · paste · copy · cut · clipboard history · AP · AC · pin`

Copy was the one of the four directions that was missing. **The pin is on this row for a reason that
has nothing to do with the clipboard**: selecting text is what collapses the keyboard, so the key he
needs *before* he can press any of the others was one row away — on a row that had just been hidden.

## A check that threw instead of failing

`editor.index(...)` in the ordering check. The sabotage run raised `ValueError`, the count never
printed, and every other result was lost.

**This is §162 exactly, made again three weeks later.** A check that raises is not a check that
fails. It uses `find` now, and the guard is also the assertion — both routes present, in the right
order.

## Tested

Test 1: 112 checks, 0 failed. Broken on purpose by removing the fallback: red on two, cleanly, with
the count printed.

Not tested: nothing ran on a phone, and the accessibility copy has never been performed. Whether the
Claude app's text view honours `ACTION_COPY` is the one thing that decides whether this works at all,
and only his phone can answer it.

---

# §174 — The circle that pins

Build 307.

## A shape borrowed for how it reads, not for how it behaves

It looks like a radio button. It is not one: any number can be filled at once, and filling one moves
that entry to the pinned section at the top.

A ring rather than a tick box, because **a ring reads as "one of a set" and a tick box reads as "a
setting"** — and this is a set. He is choosing which of these he is keeping.

Borrowing the radio button's shape while refusing its behaviour is a real trade and worth writing
down, because the next person to see it will think it is a bug. The alternative — a tick box — would
have been technically more honest and would have read as a preference, which is the wrong idea
entirely.

## The pin already existed

Behind a long press and a popup: two gestures and a menu to keep one line. This is the same
operation with the lid off — one tap, on a target that says what it will do before it is pressed,
and that shows the current state without being touched.

It toggles `pinClip`/`unpinClip` and reads `item.isPinned`, so there is no second idea of "selected"
to drift from the one the history sorts by. `ClipboardHistory` has always sectioned pinned above
unpinned; the circle simply makes that reachable.

Top right of the card, because the whole card pastes on a tap — the circle has to be somewhere the
thumb does not travel on its way to pasting.

## Tested

Test 1: 7,515 checks, 0 failed, 2,500 walked taps — every sequence of four taps across five entries,
asserting five entries survive, none is lost, and **nothing pinned ever sits below something
unpinned**. Plus that order within each section is preserved: a history that reshuffles when he pins
something is a history he cannot find anything in.

Broken on purpose into an actual radio button — one filled at a time — and confirmed red with nine
failures, including *a second tap empties it*, which is the one that would leave him unable to unpin.

---

# §175 — The reader that would not stop

Build 308. *"When it comes to the end, it starts to loop. It reads the same thing 1,000 times."*

## Why the old guard could not work

`continueBelow` compared the new passage against `lastPassage` — the one immediately before — and
stopped when they were identical. At the bottom of a chat the screen does not move, so they should
have been identical.

They were not. **A live screen differs by a character or two between two reads**: the clock in the
status bar, a relative timestamp, a typing indicator, the reading overlay itself. One character made
the equality false, so it read the same screen again. Nothing in that loop was bounded, so *a
thousand times* is not hyperbole.

And `scrollScreenDown` returns whether the scroll ACTION was accepted, not whether anything moved. At
the bottom of a list it is accepted and nothing moves.

## Three answers, because one is the kind of fix that comes back

1. **The screen must actually have moved** — read it before the scroll and after, and compare.
2. **The passage must not have been read already**, at any point in this reading, not just last.
3. **A ceiling of 30 screens.**

The third is a backstop and is meant never to fire. It is there because the first two are judgements
about text belonging to another app, and a judgement can be wrong, while a counter cannot run
forever.

Comparison is on **letters only** — every other character becomes a space. Digits go because a clock
is the commonest thing that changes between two reads of one screen; whitespace collapses because a
re-laid-out list wraps the same words differently.

The record is cleared on stop and seeded with the first screen. Kept across readings, the fix would
refuse to read a page a second time ever — the fix becoming the bug, which is how the loop arrived.

## The test found two bugs in the fix before the build

**The normaliser dropped newlines** instead of turning them into spaces, so `two\nthree` became
`twothree` and a re-wrapped screen compared unequal to the same words. That is precisely the failure
this function exists to prevent, reintroduced inside the fix for it.

**A fixture could not reach the condition it was written for**: the ceiling test used "screen 1",
"screen 2"… and the normaliser strips digits, so every one of them was the *same* screen and the
check was exercising the revisit rule instead. A fixture that cannot reach its condition passes for
the wrong reason.

A third, smaller: an escaped `\\n` survived a heredoc into the test, so the newline check was
comparing literal backslash-n.

## Tested

Test 1: 18 checks, 0 failed. Sabotaged back to the old behaviour — compare against the last passage
only — and the bug reproduced exactly: **"the same screen is read once: read 50 times"**, plus the
ceiling and the revisit rule. That is the failure he described, in a test, on purpose.

---

# §176 — One idea, one mark

Build 309. Two keys on his row open a past: transcription history and clipboard history. They wore
different pictures, and only one of them said *history*.

The clipboard one was `ContentPasteGo` — a clipboard with an arrow pointing **right**, which means
**go**, because that is what the glyph is drawn for. It was chosen because it was the closest thing
in the icon set to "clipboard, and something happens", which is how an icon set quietly writes your
design language for you.

## Compose the mark rather than hunt for a glyph

Material has `History`. It does not have "the history of a clipboard". Every near-miss was wrong in
the same direction — `ContentPasteGo` says go, `ContentPasteSearch` says find, `Restore` says undo —
because none of them was drawn to mean **the ones before**.

So `MaHistoryGlyph(base, tint)`: base glyph for the subject, the counter-clockwise arrow for the
past. That turns a coincidence into a rule — learn the arrow once on one key and read it everywhere.

The proportions are the part that decides whether it looks drawn or stuck on, and they are in
`design-language.md §14a` rather than only here: overlap rather than sit beside, about 60% of the
base, punched out of the background so the strokes do not tangle where they cross, in the base
glyph's quietest corner, one tint for both.

## The general form, which is the reason it went in the manifest

He asked for the rule to be written down for every button that opens a history. The rule generalises
past history: **when an idea applies to many subjects — history, locked, shared, offline — give it
one mark and apply it, rather than finding a whole glyph per combination.**

A vocabulary of marks scales. A vocabulary of pictures does not — it runs out at the first
combination nobody drew.

## Tested

Test 1: 117 checks, 0 failed. Five new, including that the picker and the key draw the same glyph —
this key is the one that proved that rule was only being followed where a Material icon happened to
exist. Broken back to a plain clipboard: red.

## §176a — The fifth missing import

Build 309 went red on `Modifier.clip` with no `androidx.compose.ui.draw.clip`. Fifth build lost to a
missing Compose import, and the closed-list check added for `foundation.layout` and `compose.runtime`
did not cover it: `clip` lives in a third package.

`check_modifier_clip_import` is **narrower than either of those**, and had to be. A bare `.clip(`
sweep found four hits — `canvas.rotate`, `paint.alpha` and friends — ordinary View and Canvas calls
with nothing to do with Compose. So it looks only for `.clip(` inside a **modifier chain**:
`Modifier` on the same line or within the three above. Measured that way, zero.

One name rather than a list of the package's members, because `clip` is the only one this codebase
uses, and a list of names nobody calls is a list nobody maintains.

Proving it hit the trap from §158a again: **HEAD was the red commit**, so removing the import made
the file identical to HEAD and `verify.py` reported "no Kotlin files changed". Called directly, it
names the file and the symbol, and sweeps the app clean.

---

# §176 — A guard at the door, and a square

Build 311. He came back: *"Loop issue is still not fixed."*

## The first fix was on one path

§175 put the check in `continueBelow` — the path taken after a chunk finishes and the screen is
scrolled — and the reasoning about ticking clocks and unmoved screens was right about that path. The
loop survived, which says it is **not on that path**, or not only on it: something else is calling
`speak` again with a passage already read.

Rather than keep hunting the caller, the check moved to `speak`, where every reading has to pass.
**A guard on one route is a guard on one route; a guard at the door is a guard.** Whatever is calling
twice now ends at the first line instead of speaking.

It says so out loud — *"Already read this — stopping"* — because a reader that silently refuses looks
exactly like a reader that has died, and he would be back with a different bug report about the same
thing.

What it deliberately does not do: stop him re-reading a screen on purpose. `passagesRead` is cleared
by `stop`, and stopping is what the reader key does, so read → stop → read on the same text works as
before. Only an unasked-for repeat inside one reading is refused.

## The square

His idea, offered as a fallback in case the loop could not be found, and worth having either way. **A
reading that starts again says so, visibly, without anybody having to diagnose why.** Two squares
means the same words, not similar ones, and he can stop it himself rather than listening to work out
what happened.

It clears after the third word: long enough to catch a restart, short enough that it is not
decoration on a passage he is halfway through. A mark that never leaves stops being a signal.

Drawn by the caption in one place rather than per effect, because the question it answers does not
depend on which effect he chose. Never sent to Speechify — it is in the caption, not in the text.

## Two checks that were checking spelling

Both broke on this change and neither had found a bug: one looked for
`passagesRead.add(normalisedForCompare(text))`, which became `passagesRead.add(key)` when `speak`
started computing the key once; the other for an empty lambda `{ }` that now holds the square.

**Checking for a line rather than for a behaviour costs a false failure every time the line is
rewritten**, and the temptation each time is to delete the check rather than restate it. Both were
restated.

## Tested

Test 1: 25 checks, 0 failed. Broken by removing the door guard: red, on the check that names it.

Not tested: nothing ran on a phone, and **the loop has not been reproduced here.** This is a guard
against a cause not yet found, plus a marker so the next report can say where it starts rather than
that it happens. If it loops again, the log line *"refused to read the same passage twice in one
reading"* will say whether the guard fired — and if it did not, the passage differs between reads and
the next place to look is `readableScreenText`.

---

# §177 — Audited against the fallback module

Build 312. He wrote `modules/quota-and-fallback.md` after auditing four rings in another project and
finding three broken the same way, and asked for this app to be brought in line.

## Trap 1 was live, and worse here than the module describes

`fromHttp` classified **every** 403 as a bad key. Groq sits behind Cloudflare, which refuses the
CLIENT — so a single refusal returns 403 to every key identically and **buries the entire ring in one
pass**, with nothing on screen to explain it and no way back but editing the store by hand.

And the app sent **no User-Agent at all**, which is precisely what provokes that refusal. MEASURED in
his other project on 25.8.2026: no User-Agent gives 403 with `error code: 1010` on all twenty-one
accounts, and 200 on all twenty-one with one.

**The first version of my fix carried a comment saying the app already sent a User-Agent.** I wrote
it from memory; the grep afterwards said otherwise. Both were corrected in the same commit, and the
comment now records that it was wrong — because a confident false comment is worse than no comment,
and this file has said so about somebody else's comments twice.

> **Not being refused is better than recovering from being refused.**

Both were fixed. The header, so it is not refused. The classifier, so a refusal cannot bury the ring.

**The branch must come first**, before the plain 403, and the test asserts the ordering **by index**
rather than by presence: a branch in the right file in the wrong place is the same bug, and a check
that only asks whether the line exists would pass.

## What was already right, and worth recording as such

- **Trap 3 — a failure arriving as HTTP 200.** The AssemblyAI poll already reads `status` inside the
  body and throws on `"error"`. Closed before the module named it.
- **`dead` and `cool` never confused.** A 429 rests and never buries, and the body is read before the
  status so a monthly cap wearing a 429 is quota rather than a rate limit. The comment in that file
  records the afternoon that cost.
- **Credit words** matched across code, type and message together — so Hume's `zero_credits`-in-a-400
  shape would classify as quota rather than stopping the run.
- **Unknown defaults to soft**, touching no key.

Four of the module's five traps were already closed. It is worth saying that plainly: the audit found
one live fault, not a broken ring.

## Still open, and named rather than quietly skipped

**`Retry-After` and `x-ratelimit-reset-*` are parsed nowhere.** §4 of the module is unimplemented: a
rested key waits a fixed period rather than the one the provider asked for. Groq answers a per-minute
429 with `retry-after: 2`. Fixing it means threading a duration from the response through the client
to the ring — a real change, not a line, and it is in the module's open list rather than pretended
away.

## Tested

`scripts/test_fallback.py`: 21 checks, 0 failed, porting the classifier and running the module's
measured bodies through it — Hume's exhausted 400, Groq's rate-limited 429 and invalid-key 401,
AssemblyAI's 401, and the Cloudflare 403. Broken on purpose by moving the Cloudflare branch below the
403 branch: red, on the ordering check.

Not tested: no provider was called. Every body here is quoted from his measurements, not from mine.

---

# §178 — Rows have names

Build 313. Six rows called Row 1 to Row 6 are six rows he has to remember the contents of. Now they
can be called what they are.

## A new field in an old slot

The name goes inside the existing META field, beside the enabled flag: `1~bucket row`.

Appending a fourth separator would have made every arrangement stored before today unparseable by the
new code, or the new one unparseable by the old. **`parse` runs while the keyboard is opening, in
front of whatever he was about to type** — a damaged preference there costs a keyboard that never
appears, and there is no route to the settings app from behind one. An old string has no `~` and
reads exactly as it did; a new one carries a name the old code would have ignored.

## Stripped, not escaped

Every separator is removed from a name rather than escaped. Escaping means an unescaper, and an
unescaper is a second thing that can be wrong about a string read at that moment. He is naming a row,
not writing a document: losing a tilde costs nothing, and a name containing a row separator would
otherwise split one row into two.

Capped at 24 characters, trimmed. Blank means blank rather than a pre-filled "Row 3", so the editor
can tell a row he has named from one he has not.

## One fallback, in the model

`MaRows.displayName(row, index)` — his name, or "Row 3". On the model rather than in the tab, so
nothing else that names a row can disagree with the tab about what it is called.

## A test that was testing itself

The separator cases walked a Python `sanitise` I had written to match the Kotlin. **Sabotaging the
Kotlin to strip nothing left all of them green** — a port proves only that it agrees with itself.

> **A model is not a witness to the code it models.**

Fixed by reading the real function's body and asserting it names each separator. Sabotaged again: red
on five.

Same session, a smaller version of the same fault: the port had invented `":"` and `"|"` for two
separators that are really 0x1D and 0x1C, and every check passed anyway. The separators are read out
of the source now.

## Tested

Test 1: 15,636 checks, 0 failed. The round trip, the upgrade from a nameless arrangement, the four
corrupting separators, the cap and the trim.

---

# §179 — Reflow stopped translating

Build 314.

## The bug

He reflowed Croatian and got English back, and usually only found out after pasting it somewhere.

The reflow instruction **never mentioned language at all**, and every style rule in it is written in
English. So the model followed the language of the instruction rather than the language of the text.
It was not translating on purpose; it was answering in the language it was being spoken to in.

## Three parts, and the ordering is the one that is easy to miss

1. **Detected here, not inferred there.** `MaWordLanguage.detect` — Croatian letters settle it
   outright, and without them the badge decides, because a six-word dictation has too little
   evidence for anything cleverer. **It never returns "unknown"**, because unknown resolves to
   English every time, which is the bug.
2. **Named.** "Croatian", not "the original language". A rule that describes a language without
   naming it is a rule the model has to resolve, and it resolves it towards the language it is being
   addressed in. The proofread prompt has carried `Keep the original language. Never translate.` for
   months — buried mid-list, in English — and this happened anyway.
3. **Last.** After the instruction, after the system prompt, immediately before his text. Anything
   in English written *after* the language rule pulls the output back.

Applied in `requestReword`, so it covers every rewording prompt rather than only reflow. The failure
is a property of an English instruction meeting foreign text, and all of them are English
instructions.

The test asserts the **ordering by index**, not the presence of the sentence. Sabotaged by moving the
rule to the front: red.

## The voice

`MANTRA_MANIFEST/modules/prose-voice.md`, added to the instruction. The rules already there said some
of it in other words; these are the parts the module names that were missing — short paragraphs, the
honest counterweight, witness rather than assert, one image carried through, the principle before the
request, practical facts bare, ending by taking pressure off, and normal sentence capitalisation,
which is the module's one deliberate deviation from its source.

And the sentence that matters most: **change the shape only, never the meaning.** Three rambling
sentences become the same three points, not four.

## The badge, checked rather than assumed

He asked whether the tap is applied. It is: build 292 put the badge on the sending line and build 295
moved it to the left, where the recorder's badge sits. A tap runs `MaLanguage.cycleMode` and then
`retranscribeInLanguage` — cancel, switch, send the same audio again.

And the switch is **global**, which is what he says it should be. `cycleMode` writes both
`maLanguageMode` and `activeInputLanguage`, synchronously, so a tap followed immediately by a send
has landed before the send reads it. There is no per-field or per-session scope anywhere.

The badge showing ENG while he speaks Croatian is therefore not a bug in the badge — it is the badge
telling the truth about a keyboard set to English. Tapping it is the fix, and it now also fixes the
reflow, because reflow reads the same setting.

## Tested

Test 1: 53 checks, 0 failed. Sabotaged by putting the language rule first: red on the ordering check.

## §179a — An edit whose anchor did not exist

Build 314 went red on `MaWordLanguage` used with no import.

The edit that added the reference also tried to add the import, anchored on a `MaNgram` import line
that **does not exist in `DictateController.kt`**. The replace matched nothing, did nothing, and said
nothing. The reference went in; the import did not.

That is the fourth time this month a scripted edit has failed silently — the range cuts that removed
live declarations, twice, and now an anchor that was never there. **A `str.replace` that matches
nothing is indistinguishable from one that worked**, and the only way to tell is to check the result
rather than the absence of an error.

`check_layout_imports` gained a group for this app's own `dictate.nlp` package. Measured first, as
the file demands — and the first measurement was **wrong**: it reported zero because the probe
excluded files by their package declaration while the real check did not, so `MaNgram.kt`, which
lives in that package, produced two false positives once it was wired in.

Two false positives is not many, and two is exactly enough to teach somebody to skim past the third.
The check skips a file whose own `package` line matches the group. Re-measured: zero across the app,
and it still names the real broken file.

---

# §180 — A fragment is still text, and one voice with a switch

Build 316.

## Ctrl+P did nothing to three words

The instruction ended with *"If the text is already correct, return it completely unchanged."* A
model handed a bare fragment decides the fragment is what was intended, and returns it. No capital,
no full stop, nothing.

**A short phrase is still text**, and the rule now says so — placed ABOVE the unchanged rule, because
the unchanged rule is read first and a fragment qualifies for it. Ordering again, exactly as with the
language rule yesterday: the same instruction with the same lines in a different order behaves
differently, and the test asserts the order by index.

## Grammar correction (Mantra)

Named with Mantra in it at his request, so it cannot be confused with the settings inherited from the
app this was forked from.

Under the Ctrl+F prompt rather than on a screen of its own. **A setting one screen away from the
thing it modifies is a setting nobody connects to the result.**

**Two options that are not two voices.** `prose-voice.md` defines ONE voice, taken from Yshai
Afterman's letters, with exactly one deliberate deviation: Marko's prose uses sentence capitalisation
and apostrophes where Yshai writes his most personal letters in lowercase and drops them. So the
chooser names the respect they differ in rather than offering two names as if they were different
characters — otherwise the next person adds a third "style" and the module's one rule becomes a
palette.

His own wording, if he has written one, is used exactly as written. The style toggle appends nothing
to it. **A setting that edits text he typed is a setting that has exceeded its authority.**

The whole row is the tap target, not the radio circle. He has low vision; a 20dp circle is not a
target, it is a dare.

## Row renaming

Already shipped, build 313. The field is above the tabs on the feature row editor, labelled *Name
this row*, and the tab shows the name as you type. Nothing was missing; it needed pointing at.

## Tested

Test 1: 61 checks, 0 failed. Broken by moving the fragment rule below the unchanged rule: red on the
ordering check — the version that reads correctly and does nothing.

---

# §181 — The choice that should not have existed

Build 317. He looked at the chooser shipped one build earlier and said the plain thing: the Yshai
option is not the style, because we capitalise after a full stop.

He is right, and it is worse than a wrong description. **I turned a note about what we do not copy
into a setting offering to copy it.**

`prose-voice.md` §3, which I had read and quoted in the same commit:

> Yshai writes his most personal letters entirely in lowercase, and drops apostrophes. **We do not.**
> … This is the only deviation. Do not add others.

The module names ONE voice. A chooser with two entries makes it a palette, which is exactly what §1
warns against when it says a voice is extracted from real writing rather than invented from
adjectives. And "Marko" and "Yshai" as two options implies two people's writing are both on offer,
when one of them is the source and the other is the source with one correction applied.

So the chooser is gone and a sentence replaces it: what the voice is, and which single part of it we
do not copy. **A control offering a choice that does not exist is worse than no control** — it
invites him to pick the wrong one and then wonder why nothing changed.

`maProseStyle` stays declared and unread rather than being deleted, so a phone that already stored
"yshai" reads back harmlessly.

The tests now assert the ABSENCE: no lowercase rule anywhere in the instruction, no style constants,
no chooser in that section. Sabotaged by putting the lowercase line back: red.

## Three checks that were wrong about correct code, in one sitting

**`check_removed_declarations` could not tell a use from a label.** After a local `style` was
removed it reported `style` as still used — every remaining hit being `style = MaterialTheme…`, a
named argument. On a Compose codebase that is most of the file. It now excludes `name =`.

**A test searched the whole file for a control belonging to one section.** It found the
prompt-wordings picker, which is a different control doing a real job. Same fault as the shift check
that found `MaNextFieldKey`.

**Then the scoped version sliced to end-of-file**, which swept the helper below it back in.
**An open-ended slice is not a section.** Bounded at both ends, it passes and still fails on the
sabotage.

---

# §182 — The other direction

Build 318. A second automatic bucket, starting from the other end of the frame.

`A↑` takes the LOWEST code block in view and works upward — newest first, which is right in a chat,
where the answer he just received is at the bottom. `A↓` takes the HIGHEST and works downward, which
is right when he has scrolled back to a run of answers and wants them in the order they were written.

## One key, one direction

Not one key with a mode. A mode would mean remembering which way it was pointing before every press,
and being wrong collects from the wrong end of the screen — which is the failure the frame rule was
introduced to end in §151.

## One body, one range reversed

Both keys are the same branch. Rank 0 is the lowest block in the frame, so counting up walks upward
and `(inView - 1) downTo 0` walks downward. **One range, reversed — not a second loop with the
comparison flipped**, which is where an off-by-one lives and where the skip rule, the arming and the
three messages would drift apart.

The test walks both directions meeting in the middle over the same three blocks and asserts nothing
is taken twice.

## The faces

`A↑` and `A↓`. The old face was `A1` — a leftover from the ladder that counted how far up the page it
had climbed, deleted in §151. There is no ladder and no number, and a key wearing a number nothing
counts is a key that promises something it cannot do.

## A check on a line rather than a behaviour, again

`"for (rank in 0 until inView)" in row` broke, because the loop is now written over a range variable.
Nothing was wrong; the line had moved. Restated as `for (rank in order)`.

That is the fourth in three weeks. The pattern is always the same — the check quotes an
implementation detail because it was the easiest thing to grep for on the day.

## Tested

Test 1: 3,107 checks, 0 failed. Broken by making both keys walk upward: red on the range check.

---

# §183 — The bucket row becomes a row

Build 319. He had assembled the buckets on an ordinary feature row and asked for it to be a special
row instead — the copy row's shape, switchable like a keyboard zone, invokable as a preset.

## Why it deserves to be special rather than ordinary

**It is a MODE, not a set of keys.** When he is collecting code blocks he wants those six and nothing
else; when he is not, he wants the space back. An ordinary row is a place to keep keys; a special row
is a thing you turn on.

Same shape as the copy row throughout: its own preference, its own parser, its own default, its own
switch. Nothing shared, so arranging one cannot disturb the other.

**Off by default.** A row that appears uninvited on every keyboard is a row he has to switch off
before he can type, and the first impression of a feature that arrives in the way is that it is in
the way.

## Two ways in, one preference

The **Cs** key on any feature row, wearing the green ring — ringed means the row is there. And the
switchboard, where every other row lives.

Both write `maBucketRowShown`. **Two switches for one row is the fault this app has met three times**
— the copy row on the keyboard, the buckets themselves, the volume keys — and each time the cure was
one preference with several doors rather than several preferences.

## Two booleans rather than an enum

`copyRowOnly` and `bucketRowOnly` are never both true, which usually argues for an enum. Not yet: two
booleans are honest about what they are — two callers each asking for one specific row — where an
enum invites a third value nobody has designed. **If a fourth special row is ever wanted, that is the
moment to introduce the enum**, and the comment says so where the next person will read it.

## The switchboard id is appended

`parse` keeps his stored order and appends ids it did not know about, so `bucket_row` arrives at the
bottom of the arrangement he built rather than shuffling it. An id inserted in the middle of the enum
would have moved nothing — the order is his, not the enum's — but the note is there because the next
person will want to insert one alphabetically.

## Tested

Test 1: 19 checks, 0 failed — its own storage, its own switch, off by default, the default row he
photographed, one preference behind both doors, and no chrome drawn twice. Broken by making the row
unconditional: red.

Not tested: nothing ran on a phone. Whether six keys at smartbar height under the copy row leaves
enough screen is the question, and if it does not the answer is fewer keys in the default, not a
smaller row.

---

# §184 — The same setting in two windows

Build 320.

## The alignment bug

Top alignment worked in the small box and did nothing in full screen. **The box was aligned
correctly both times.** Two different things were wrong underneath it:

**The small box found a page rather than cutting one.** `paginate` chopped the passage into fixed
pages and the code then looked for the one containing the current word — so aligning the PAGE to the
top says nothing about where the WORD is. It is also why the highlight leapt: crossing a page
boundary replaced the whole block and the marked word jumped from the bottom of one page to the top
of the next.

Now the page is cut around the word. Top starts at it, bottom ends at it, middle grows both ways.
The page therefore moves with the reading rather than in jumps, and consecutive words always give
overlapping pages — which is the property the test asserts, because it is what stops the leaping.

**The scrolling full-screen view ignored the setting entirely**, always scrolling the live line to
the top. That path was written before alignment existed and nobody went back to it. It counts in
LINES rather than pixels, because that is the unit the list moves in and a pixel offset would drift
as the font size changed.

`paginate` survives, and should: the scrolling view needs every line at once. What changed is that
the small box no longer uses it.

## The dashboard was gated on a reading

`maDashboardOpen && MaReader.currentIndex >= 0`, so a long press before pressing play did nothing at
all. **The settings he wants are the ones he wants BEFORE he reads** — the speed, the effect, where
the line sits. The gate was never a decision: the dashboard was built while a reading was running,
and the condition described the only state anybody had tried it in.

## The test found a bug in the fix

Middle never grew backwards. The generic `takeAfter` branch caught every step after the first, so
middle grew forward for ever and **behaved exactly like top** — the setting it exists to differ from.
Both middle branches come first now.

Also corrected: my first check asserted middle was centred to within one word, which asserts the
loop's step order rather than anything he can see. It asserts the word is not at an edge and that
there is text on both sides.

## Tested

Test 1: 1,217 checks, 0 failed, 360 walked cuts — every alignment against every index at three page
sizes, asserting the word is always on the page, the page never leaves the passage, and consecutive
pages overlap. Broken two ways — full screen ignoring the setting, and middle not growing backwards —
and confirmed red on three.

---

# §185 — Three zones, and messages that stop covering the keys

Build 321.

## The spacebar

Left quarter ←, right quarter →, middle half space. **The width was the opportunity**: the spacebar
is the widest key on the keyboard and does the least, and the two things he reaches for most while
dictating are the space and the cursor — where the cursor keys are the smallest targets on the row.

Zones by width rather than three keys. Three keys would need three shapes, three gaps and a decision
about which wears the spacebar glyph. One key with a wide middle stays the spacebar it has always
been, and the arrows are room that was there anyway. A quarter of a spacebar is still a bigger target
than a letter key.

**A boundary press is a space**, so the wider target wins a tie. The reverse would move his cursor
when he meant to type, and the test walks all 1,001 points across the bar to hold it.

`onClickAt` replaces `onClick` when given rather than joining it — a key cannot have two answers to
one tap, and a key that ran both would send a space AND an arrow. The positional path uses
`detectTapGestures`, which gives the offset `combinedClickable` throws away, and carries the long
press itself so the cursor pad survives.

## The toasts

He called them rude, and he is right: they land over the keys while he is using them.

**The obvious fix does not exist.** `Toast.setGravity` has been ignored for ordinary text toasts
since Android 11 — asking the system to put one at the top does nothing, and the platform puts it
near the bottom, which is where the keyboard is. So it had to stop being a toast.

It is a line the keyboard draws itself, at the top of its own area, above every row. Nothing it
covers is a key. Drawn with the chrome, so two feature rows cannot each show a copy.

Three things that follow from owning it, none of which a toast could do:

- it disappears when the keyboard does, instead of outliving the thing it was about and landing over
  another app;
- **a new message replaces the old** rather than queueing behind it — three quick presses used to
  mean six seconds of stale text;
- 2.5 seconds, one timer. A toast's SHORT is not quite enough to read a sentence at his speed and
  LONG is still there when he has moved on.

Cleared by time or by the next message, never by a tap. A message he has to dismiss has taken a press
away from what he was doing.

## Tested

Test 1: 20 checks, 0 failed, 1,001 walked points across the bar — the middle is half, each arrow a
quarter, the zones are contiguous, and both boundaries are space. Broken two ways: boundaries
resolving to arrows, and the positional key also firing `onClick`. Red on three.

Not tested: nothing ran on a phone. Whether a quarter of the bar is enough to hit reliably without
looking is the question, and if it is not the answer is a narrower middle, not a different gesture.

---

# §186 — Drag feedback, and where the 40 MB is

Build 322.

## The drag was invisible

He could reorder the tabs and could not see it happening: the long press armed silently, the finger
moved, nothing changed until he let go and the row had moved. **A gesture with no feedback is a
gesture he has to believe in.**

Three signals, the settings list's vertical ones laid on their side:

- **lifted** — brighter and 8% larger, so it reads as picked up rather than merely selected;
- **carried** — it moves with the finger, using the RAW drag rather than the rounded target, so it
  tracks continuously instead of snapping between slots;
- **landing** — the tab it would swap with dims, so he sees WHERE it goes before committing.

The third is the one that was really missing. Lifting and carrying say *you are dragging*; only the
landing mark says *and this is what will happen*.

The landing tab steps back rather than lighting up, because two bright things compete and the one
under his thumb must stay the brighter. And the lifted tab is drawn last, so it passes over its
neighbours rather than sliding under them — which is the difference between reading as lifted and
reading as sliding through a slot.

**The mark and the drop share their expression**, and the test asserts all three parts of it. A
highlight computed one way and a drop computed another would point at a tab the drop does not use,
which is worse than no highlight at all.

## Where the 40 MB goes — measured, not guessed

    30.6 MB  lib/          of which 25.8 MB is libonnxruntime.so
     3.9 MB  classes.dex   all of the app's own code
     3.2 MB  resources.arsc
     2.2 MB  assets/       silero_vad 0.5, dictionaries ~1.2
     0.1 MB  res/

**76% of the app is one file**, and it is not ours: `libonnxruntime.so`, the runtime the on-device
speech engine needs, plus `libsherpa-onnx-jni.so` at 4.7 MB. Everything TTT does — every key,
every row, the reader, the buckets, the settings — is the 3.9 MB dex.

It is already built as leanly as it can be without a decision from him:

- **one ABI only.** `abiFilters += listOf("arm64-v8a")`, so there is one copy rather than four. A
  universal APK would be roughly 120 MB.
- **the JNI bridge is extracted rather than vendored whole**, avoiding a second copy of
  libonnxruntime per ABI — that note is already in `build.gradle.kts`.

**What would actually shrink it**, and both are his call rather than mine:

1. **Drop on-device transcription.** Nothing else needs onnxruntime, and it would take the app from
   40 MB to about 9. The cost is the one provider that costs nothing per minute and works with no
   network — which the cost work in §166 leaned on as the free tier for notes.
2. **Download the engine on first use** instead of shipping it. Same 9 MB install, on-device still
   available, at the cost of a first-run download and code to manage it.

Not done in this pass. Both change what the app can do offline, and that is not a decision to make
inside a build about drag feedback.

## Tested

Test 1: 15,644 checks, 0 failed. Eight new. Broken by removing the landing mark: red on the check
that names it.

---

# §187 — Hold a half, and the cursor runs

Build 323.

## The hold

Left half repeats ←, right half repeats →, every 111ms after a 350ms hold. His number, and a good
one: about nine characters a second, which is reading speed rather than typing speed — fast enough to
cross a sentence without waiting, slow enough to stop on a word.

**Two halves on the hold, three zones on the tap.** The two gestures ask different questions. A tap
has to leave room for the space, which is what the bar is for; a hold does not, because nobody holds
a spacebar to type spaces. So on a hold the middle belongs to whichever arrow is nearer and every
hold lands on one. **No seam is drawn** — a line on the bar would advertise a boundary that only
exists while he is holding it.

Two details that decide whether it works:

- **The repeat runs in `onPress`, not `onLongPress`.** `onLongPress` fires once and this has to keep
  going until he lifts; `tryAwaitRelease` is what gives us the lift and is only available inside
  `onPress`.
- **The first wait is what separates a hold from a tap.** Without `HOLD_START_MS`, every tap would
  fire one arrow before the tap handler ever ran.

The coroutine is scoped to the key's composition, so lifting cancels it and so does the key leaving
the screen — a repeat that outlived its key would type arrows into whatever came next.

## The cursor pad

He asked for total removal. **What is done: it is unreachable.** Both doors are shut — the feature
row's spacebar now repeats instead of opening it, and the letter keyboard's spacebar long press is
inert. A test asserts that neither file calls `open()` again, so a third door cannot be opened by
accident.

**What is NOT done: the code is still in the tree** — 17 references across five files, including the
overlay itself, a branch in `KeyboardManager`, and a branch in `Smartbar` that swaps the candidate
row while the pad is active. Cutting those is an excision through live keyboard code, and doing it at
the end of a build to save a round trip is exactly how the range cuts of §150a happened.

Left for its own build, and named here rather than quietly skipped.

**The letter keyboard's long press is inert rather than repurposed.** Nothing replaces it there: the
half-hold belongs on the feature row's spacebar, which is the one he uses while dictating, and on the
letter keyboard a long press on the spacebar has meant the language picker on every phone he has
owned. Leaving it doing nothing is better than giving it a third meaning nobody asked for.

## Tested

Test 1: 26 checks, 0 failed, 1,001 walked points. Broken by removing the hold delay: red on the check
that says a tap must not reach the repeat.

Not tested: nothing ran on a phone, and the repeat has never been held. Whether 111ms is right, and
whether 350ms is long enough not to fire on a slow tap, are both his and both one number to change.

## §187a — The fifth silent no-op edit

Build 323 went red on `keyScope`, unresolved. The edit that used it also declared it, anchored on the
`val interaction = remember { … }` line — which appears TWICE in that file, so the assertion I wrote
to guard the replace failed, the script exited, and **the half of the edit that had already been
written stayed.** The use went in; the declaration did not.

That is the fifth scripted edit this month that failed silently and the third distinct way of doing
it: an anchor that did not exist, a range whose second end was too far down, and now a script that
died between two halves of one change.

**The common shape is that the edit is not atomic and nothing checks the result.** `verify.py` caught
none of them because they are all "this file no longer compiles for a reason only a compiler knows".

A guard was measured and **rejected**: flagging a bare name used but never declared in a file found
**17 hits across the app**, essentially all false — `prefs` and `context` arrive by delegation and by
receiver in ways a regex cannot see. Per the rule that has now saved this file twice, it is not
shipped. The gap stays open and CI keeps it, at five minutes a time.

What did work, and is worth keeping as the habit: after every scripted edit, **grep for the thing the
edit was supposed to add** rather than trusting that no exception was raised.

---

# PART FOUR — WHAT WAS INHERITED

- **Forked from FlorisBoard.** The IME framework, the letter keyboard, the theming engine
  (`Snygg`), the clipboard, the NLP provider structure. Everything under `ime/` is largely theirs;
  everything under `dictate/` is largely ours.
- **`sherpa-onnx` 2.0.1** (was 1.13.3 until 8.11.2026; ONNX Runtime 1.28.2 inside, the 1.24.3 Java bridge retagged by `tools/retag_elf_verneed.py`), vendored from the GitHub release AAR — not on Maven Central. The fetch
  script extracts only the API jar and the JNI bridge, avoiding a second 20–27 MB copy of
  `libonnxruntime` per ABI. One ABI only (`arm64-v8a`); a universal APK would be about 120 MB.
- **Deliberately NOT carried across:** Groq, and with it automatic language detection. **A detector
  that is confidently wrong returns fluent text in the other language, which is worse than an
  error.** The language is chosen by hand, on the badge, and the badge is global.
- **`MaNeuralPredictor` and `MaLanguageGuess`** were written and never called. Check for callers
  before assuming a file is live.

---

---

## §189 — Three compile faults in the language keys, and the sixth silent edit

Build 327 fixes what build 326's push left red.

**Three faults, all of them things the compiler knows and no check here could:**

- `tint = …` passed to `ThemedKey`, which has no such parameter. Only `ThemedIconKey` does, and the
  plain send key dims through it. A key that draws its own content dims **the content**, not a
  parameter it does not have.
- `MaMagicTargets.pressSend()` treated as a `Boolean`. It returns the **term it pressed**, or null —
  and that term is worth showing, because when the wrong thing gets pressed the word it found is the
  whole diagnosis.
- `sendVisible()` called during composition. It reads preferences and parses the target list; it is
  polled now, like the plain send key.

All three are recorded as checks in `test_language_keys.py`. They cannot fail again silently, though
a fourth of the same kind will still need CI.

**And the sixth silent scripted edit.** The line dimming the letter beside the arrow was written by a
`str.replace` that matched nothing — the anchor had a different indentation than I assumed. The
arrow dimmed; the letter did not. **The check that caught it is one I had written ten minutes
earlier, in the same commit**, asserting both halves dim together.

That is the argument for writing the check with the fix rather than after it. The habit from §187a —
grep for what the edit was supposed to add — worked here only because the grep was already written
down as a check.

---

## §190 — The badge goes, and what SwiftKey actually does

Build 328.

### The badge

Removed from the sending line, along with its computation. It showed the language and cycled it on a
tap, and that was right **while the language was a mode**. It is not one: `● H` and `● E` record,
`→H` and `→E` send, and the press is the choice.

**A badge reporting a mode nobody sets is a control with nothing to control** — and it was still
tappable, which made it the last route by which the language could go wrong behind him.

The status text still names the language in words. **That is a report, not a control**, and he asked
to be told.

Five checks inverted rather than deleted: no badge, nothing cycles the language, nothing resends in
another language. **A deleted feature whose checks are quietly removed is how a feature comes back by
accident.**

### `inFlightLanguage` was never actually made state

§189 records this as fixed. It was not — the change was lost with a sandbox restart, and the tree
still had a plain `get()` under a comment claiming otherwise. Done properly now, and the check
asserts the declaration rather than the behaviour it enables, so it cannot be recorded as done again
while missing.

### SwiftKey, researched

What the sources actually say, so the next session does not re-research it:

- **`123` sits in the lower left, directly below shift**, and `abc` returns from the symbol and emoji
  layouts in the same corner. Microsoft's own accessibility page describes exactly that navigation.
- **The emoji key is lower right.**
- **Symbols are two panels deep**: `123`, then a symbols key for the rarer characters.
- **The number row and the arrow keys are OPTIONAL**, both switched on in settings. SwiftKey's arrow
  keys sit at the bottom.

### What that means here, and what was NOT done

TTT already has the arrow keys — `ARROW_LEFT/RIGHT/UP/DOWN` in the catalogue, droppable on any
row — and a number row on zone 1. **So the two things he asked me to build if missing already exist**,
and the honest answer is where to find them rather than a second copy.

**The bottom-row layout change is not done.** Moving `123` to the lower left under shift is a change
to the letter keyboard's own layout files, inherited from FlorisBoard and shared by every language
subtype. That is a different kind of edit from anything this session has touched, and starting it in
the tail of a build about a badge is how the six silent edits happened. Named here, not attempted.

---

## §191 — SwiftKey's corner, and what it measured about FlorisBoard

Build 332. The layout job, done as its own build, and it answers a bigger question than it was asked.

### What SwiftKey does, measured from Microsoft's own documentation

- **`123` sits in the lower left, directly below shift.**
- **`abc` returns from the symbol and emoji layouts in that same corner.**
- The emoji key is lower right; symbols are two panels deep (`123`, then a symbols key).

**The corner is the whole point.** A layout switcher that moves between panels is a switcher he has
to find twice, and finding it twice is what makes a keyboard feel foreign.

### What changed

The switcher moved to position one on all three layouts. It was second on the letters and second on
both symbol panels, behind `ctrl` — so it was neither under shift nor in the same place as the key
that comes back.

**Nothing was removed.** `ctrl` in particular stays, and the test asserts it by name on all three
layouts: he uses Ctrl+P and Ctrl+F constantly, and a reorder that drops a key is a reorder that
deletes a feature. The comma stays on the period's popup, which is where it has always been.

**Not done: a dedicated comma key.** SwiftKey has one, and adding it costs width that `space` and the
three switchers are already using. That is a trade he should make, not me.

### The finding that matters more than the layout

**This took ZERO Kotlin files.** Three JSON assets, and the keyboard reads them.

That is the real answer to *how tangled are we with FlorisBoard* — for layout, not at all. The layout
engine consumes data, and the data is ours to write. The 30,367 lines of `ime/` are the engine, and
this build never touched them.

Which reframes the independence question. **The parts that feel like FlorisBoard are mostly
configuration, and the parts that are genuinely theirs are the parts nobody wants to rewrite** — the
event dispatcher, the geometry, the subtype system, the theming engine. A rename would change what
the files say; it would not change who wrote the hard 30,000. And Apache-2.0 §4 attaches to
derivative works regardless of how much is later replaced, so no amount of rewriting ends the
attribution — only a clean-room implementation by somebody who has never read this code does that.

Recorded here so the next session does not start the rewrite on a feeling.

### Tested

Test 1: 44 checks, 0 failed — the corner on all three layouts, the invariant that the switcher never
moves between panels, no key dropped from any of them, ctrl present by name, the comma still on the
popup, and every file still parsing with a code on every key. Sabotaged by putting `abc` back behind
`ctrl` on one panel: red on two, including the invariant.

Not tested: nothing ran on a phone. Whether the corner feels right, and whether `ctrl` in second
place is where his thumb now expects it, are both his.

---

## §192 — Only one key rings, and six keys nobody could find

Build 334.

### Both record keys lit

Press `● E` and both `● H` and `● E` ringed. The comment written at the time argued for it: one
recording is running, and lighting a single key would suggest the other could start a second.

**That argument protects a misunderstanding nobody has and destroys the answer he needs.** Which
language is recording is the ONE thing these two keys exist to make visible. If both ring, they are a
record key drawn twice — and he photographed exactly that.

The dark key is the true statement anyway: while a recording runs it cannot start one, and pressing
it stops this one. **A ring that says "this is yours" is worth more than a ring that says "something
is happening"**, which the timer above already says.

One detail that decides whether it works: it reads `MaLanguage.active()`, not `inFlightLanguage`. The
latter is written when a REQUEST starts, and this is a RECORDING — not sent yet. Reading the wrong
one would leave both keys dark until the upload began, which is a different wrong answer.

Walked over recording × language: **exactly one rings while recording, and it is the right one.**

### Six keys that could not be found

He searched "feature row" in the key picker and got nothing. The six were labelled *"F3, show row
3"* — and the phrase he uses for the thing was not the phrase they were named with.

**A key nobody can find by its own name is a key that does not exist.** They read "show feature row
3" now, with `F3` still in front because that is what the key face says and he will search for that
too. The test asserts all three phrasings match: `F3`, `feature row`, and the old `row 3`.

Worth generalising: the picker's search folds the label, the description, the letters and the id into
one haystack, so **naming a key is naming its search terms**. A key whose label uses the developer's
word for a thing rather than his is findable only by the person who wrote it.

---

## §193 — A label written for the source file, not the card

Build 336.

**"History, what you dictated"** truncated to **"History, what you"** on the picker's two-line card —
a question that answers nothing, sitting directly beneath "Clipboard history". He photographed the
pair and could not tell what the second one was.

**Two faults in one label.**

It was too long for the place it is drawn. And it described the wrong thing: that screen keeps the
**audio** — playback, export, a retention budget in megabytes, and recovery of failed sends. Calling
it *what you dictated* names the transcript and hides the recordings, which are the reason to open it.

It reads **"Audio history"** now. Short enough to survive the card, true about what is behind it, and
beside "Clipboard history" the pair reads as two histories of two things rather than one history and
a sentence.

### The general rule, and the sweep

**A label is written for the narrowest place it appears**, not for the source file where it is
comfortable.

So this is not a fix for one key. Measured across all 51 labels: **seven were longer than the one
that broke.** All were shortened, and the test now asserts a 24-character ceiling on every label in
the catalogue, so the next one cannot be written too long.

### A check that could not tell code from prose

The first version searched the whole file for the old sentence — and failed, on the comment recording
why the label had changed. **The explanation of a fix is not the fix undone.** It reads the extracted
labels now.

That is the seventh check this month that looked wider than its claim, and it is the same cure every
time: extract what the claim is about, then assert against that.

---

## §194 — SwiftKey's bottom row, applied

Build 337. He sent the screenshot and said it is better than his layout. It is, and the reason is
subtraction.

    SwiftKey:  123 |  ,  | space |  .  | enter
    His was:   123 | ctrl | lang | sy2 | space | . | enter

**Seven keys doing what five do.** Two of the seven were switches, and switches on the letter
keyboard are the thing this app has spent three builds removing.

### What went, and why each was safe

**`language_switch` — gone, and good riddance.** The language is chosen by `● H`, `● E`, `→H` and
`→E` now. A key that switches it is the mode §190 deleted the badge for, sitting one row lower. It
was the last one.

**`view_symbols2` — moved, not removed.** It is on the `123` key's popup, where it already was. The
test asserts that popup still carries it, because a tidy-up that quietly deletes a panel is worse
than a cluttered row.

### What stayed, against the screenshot

**`ctrl`.** SwiftKey has no Ctrl+P and no Ctrl+F; he uses both hourly. **Copying a layout is not a
reason to delete a feature that layout never had** — the screenshot is a model, not a specification,
and the difference between them is exactly the keys that exist because of how he works.

So the row is six keys, not five, and the one extra is the one he would notice within a minute.

### What arrived

**A dedicated comma**, left of space, where SwiftKey has it. It was on the period's popup — reachable
by long press, which is a gesture for something rare, and a comma is not rare.

The period now offers **! and ?** on its popup, which is what SwiftKey prints above that key as
`,!?`. The comma left the popup because it has its own key; the other two stayed.

### Tested

Test 1: 57 checks, 0 failed — six keys on all three panels, the comma left of space, enter last, no
language switcher anywhere, the second symbol panel still reachable from the popup, `ctrl` present by
name, and every file still parsing. Sabotaged by dropping `view_symbols2` from the popup as well as
the row: red.

Still zero Kotlin files. Three JSON assets, again.

---

## §195 — Self-repairing models, and one switcher that cycles

Build 339.

### The keyboard

`ctrl` is far left. **One view switcher, not two.** The second `sy` key is gone — two keys that both
change the view are two orders to remember, and he had to learn which one skipped what.

The one that remains **cycles with no skipping**: letters → symbols → symbols² → letters. Each panel's
key goes to the next, so pressing it repeatedly walks every view and comes back. Before this, two keys
each jumped to a fixed panel and neither visited all three in order.

**Long press offers all three**, on every panel, for when he has no time to cycle. That popup is
FlorisBoard's own long-press menu: it opens and stays, and a second tap chooses — which is exactly the
behaviour he described.

One thing I did not do as asked: **the key still shows where it GOES, not where he IS.** A key
labelled with the panel he is already looking at tells him something he can see; the destination is
the thing he cannot. If it still feels wrong on the phone, it is one field per layout to change.

### Model self-repair

Speechify retire `simba-english` and `simba-multilingual` on 21 November 2026. Both are named
explicitly in `MaSpeechify` — eleven times — because **the model follows the voice**, so the notice's
"leave the field out and you are fine" escape does not apply here.

The app repairs itself now. `MaModelHealing` is pure and walked; the wiring is four lines in the
request path.

**Three decisions worth keeping:**

**Probe only after a failure, and only a failure about the MODEL.** 410 outright; 404 or 400 when the
body names it. Never 401, 403 or 429 — those are the key ring's. Never a plain 400: *input too long*
is not a retired model, and **a probe at the wrong failure can write a wrong name into the settings
and make tomorrow fail as well.** The cost of over-eagerness is not a wasted call, it is a corrupted
setting.

**Never invent a name.** The successor must be on the provider's own list. Bumping the number and
hoping is what turns one broken model into two.

**Write it down.** In memory only, he would pay the failed request, the probe and the retry every
morning, and never be told why the first reading of the day is slow.

### The bug the test found, and the one it did not

`family()` dropped a trailing word as well as a trailing version, so `whisper-large` collapsed to
`whisper` — and **a large model could have been replaced by a tiny one.** My own comment three lines
above said that must not happen.

Fixed by keeping the word, which then broke `simba-english → simba-3.2`, the migration this exists
for. The answer is a second, narrower fallback: match on the leading segment, **only for a model with
no version of its own.** Applied to `whisper-large-v2` the leading segment is `whisper` and the tiny
model qualifies again.

And the one the test did not find, at first: **sabotaging the Kotlin left every check green**, because
they all walked the Python port. §178 exactly, met again. Five checks now read the real function's
body, and the sabotage fails on the guard by name.

### Tested

`test_model_healing.py`: 42 checks, including his actual migration by name, every status that must not
probe, and the family rule in both directions. `test_bottom_row.py`: 73 checks — one switcher, the
full cycle, the long-press menu carrying all three views.

---

## §195 — The bar that walked while he deleted

Build 342.

### The bug

He held backspace. Suggestions empty at a word break, so the bar expands to the action row; one more
character and a suggestion returns, so it collapses. Held down, that happens several times a second
and **the whole keyboard walks up and down, taking the text above it with it.** His word: dizzy.

### The fix was already written, and switched off

`autoExpandCollapseSmartbarActions` had exactly this guard commented out above it, with a `TODO` and
a note that it prevents "annoying UI changes". FlorisBoard found the problem, wrote the answer and
disabled it.

**Turned on rather than reinvented**, and narrowed: theirs also bailed while the overflow menu was
open, which is a different problem. This asks one question — is a delete repeat running — because
that is when the answer changes fastest and matters least.

**Not a delay and not smoothing.** The bar does not move at all while the key is down, and settles
once when he lets go, which is the moment he is ready to read it. A debounce would still move it,
only later, and the test asserts the guard `return`s rather than deferring.

Checked BEFORE the state is computed, because computing it and discarding it still writes the
preference — and the preference write is what makes the bar move.

### The other half was already there

`Smartbar.kt` keeps the strip's HEIGHT when it has nothing to show, added when the same jump appeared
after a caret move. **Two mechanisms for one symptom, in two files, months apart** — which is why the
first fix did not end it: the strip stopped collapsing, and the bar kept switching.

### A check that raised instead of failing. Again.

Removing the guard made `fn.split(...)[1]` throw. The count never printed and every other result was
lost — **§173 exactly, in a test written the same afternoon that section was being read.**

Guarded with `find` and an index comparison. The sabotage now gives four clean failures.

### The comma on the period

He asked for it removed. It already was, in build 337: the period's popup carries `!` and `?`, and
the comma has its own key. He is either on an older build or seeing the symbol HINT drawn above the
key, which comes from the hinted-symbols setting rather than the popup. **Nothing was changed on a
guess** — the layout is right, and if the hint is what he means it is one setting, not a layout edit.

---

## §196 — Watch mode: reading text that has not been written yet

Build 343. He reads Claude while it is still answering. The reader reached the bottom and stopped,
and everything written after that was never spoken.

### The problem is that the screen GROWS, it does not CHANGE

Compare whole screens and every poll looks like a brand new passage, because the old text is still in
it. The loop guard from §176 then refuses it, correctly, and nothing is ever read.

**So read the difference, not the screen.** `newTail(before, now)`: if what is on screen now begins
with what was there before, the reading is everything after that point; if it does not — he scrolled,
or switched conversation — it is a new passage and all of it is read.

Compared on the NORMALISED forms, so a ticking clock does not make a still screen look like it grew.
Cut from the RAW text, so what gets spoken keeps its punctuation.

**Cut on a word boundary.** The first version sliced the raw string by a normalised offset — the two
have different lengths, and it cut words in half. It walks words now.

### Why a poll and not a listener

The accessibility service can report window content changes, and a streaming answer fires those
several times a second, each carrying a few more characters. Reading on every event would speak
half-words. **A poll asks the question at a speed a voice can answer at**, which is 1.8 seconds — long
enough for a sentence to accumulate, short enough that the wait after the writing stops is not felt.

### It never stops by itself

No timeout, no ceiling. `MAX_SCREENS` bounds a runaway scroll; **watching is not runaway, it is
waiting**. A watch that ended after five quiet minutes would end during the one long pause he stepped
away for, which is exactly when he was relying on it.

`stop()` is the only thing that clears the flag, and the test asserts there is exactly one place that
does — a second would be a second way to end a watch he did not ask to end.

### Two of my own faults, both found by sabotage

**A test expectation that was wrong while the code was right.** I asserted the tail of
`"well" → "well, that is done."` should be `", that is done."` — which would require cutting
mid-word, the bug the implementation avoids. The expectation was the claim that was wrong.

**A wiring check that found a line inside `if (false)`.** Searching for `watchForMore(...)` anywhere
in the file passed a sabotage that made the branch unreachable. **A check that finds a line does not
know whether the line runs** — it asserts the condition now, and the call within it.

### Tested

Test 1: 21 checks, 0 failed — a six-frame stream where every word must be spoken exactly once in
order, a different screen read whole, a blank poll silent, a ticking clock ignored, the word-boundary
cut, and only one place clearing the flag.

## §196a — Correct and unreachable

Build 344. He asked for watch mode a second time, because build 343 did not do it.

He was right. The code was correct and **it could not run.**

Watch mode's decision was written at ONE of the routes by which a reading ends — the branch after the
scroll succeeds. The route he actually takes is the other one:

    val moved = DictateAccessibilityService.scrollScreenDown()
    if (!moved) {
        state = State.IDLE     // <- returns HERE, above the watch branch
        return
    }

**At the bottom of a chat the scroll cannot move**, because there is nothing below. So `continueBelow`
returned before ever reaching the watch decision — and the bottom of a chat is precisely where he
wants it to wait.

### This is §176 again, in the same file

The reader loop was reported twice for the same reason: a guard written at one route, on a path the
bug did not take. The cure then was to move the decision to the door. **The cure now is the same
shape:** one `endOfText()` that asks the watch question, called from every exit.

Twice in one file, six weeks apart, is not a coincidence. `MaReader` has ten places that set
`State.IDLE`, and the difference between "the text ran out" and "something went wrong" is not visible
at any of them. Naming it in a function is what makes it visible.

### The checks were about behaviour, not reachability

The first suite asserted the branch existed and that the call inside it was the right one. Both were
true. **Neither asked whether anything reaches it.**

They ask now: one place decides, the scroll-failed exit calls it by name, and `continueBelow` sets
`IDLE` nowhere at all. Sabotaged by restoring the direct `IDLE` on that one exit: three failures,
where the old suite gave none.

---

## §197 — Watch mode was correct, unreachable, and then in the wrong state

Build 344. He asked for this a second time. Both times the reasoning was right and the mechanism
never ran.

### The first miss: one exit, several ways out

§196 put the wait inside `continueBelow`'s end-of-text branch. At the bottom of a chat the reader
does not reach that branch — `scrollScreenDown` fails first and returns straight to IDLE. **A guard
on one route is a guard on one route**, which is the same sentence §176 earned, about the same
function, five builds earlier.

Every end-of-text exit calls `endOfText()` now, and it asks the watch question once.

### The second miss: waiting in a state that means something else

It then waited in **PAUSED** — a state with a paused PLAYER behind it. While watching there is none,
and two mechanisms written for a different problem both fired:

- the self-heal at the top of `toggle` sees PAUSED with no player, correctly calls that a lie, and
  resets to IDLE. **The next press then starts a second reading beside the running watch.**
- the PAUSED branch calls `player?.start()` on null and sets SPEAKING — the dead-key state the heal
  exists to prevent.

**A state meaning "waiting for text" cannot be borrowed from a state meaning "audio is paused".** They
differ in whether a player exists, and every mechanism here keys on exactly that.

`WATCHING` is its own state now, exempt from the heal alongside LOADING, stopping on a press, and
wearing the stop square so the face says what the press does.

    SPEAKING, PAUSED   have a player
    IDLE, WATCHING     do not

### Why it was invisible twice

Nothing failed. No red build, no exception, no wrong output — the reader simply stopped, which is
what it used to do. **A feature that silently does not run looks exactly like a feature that was
never asked for**, and the only thing that catches it is him telling me a second time.

### A check that asserted True

Four of the new checks were `check(f"{st}: player expected = {needs}", True)`. They cannot fail, they
document nothing the comment above them did not, and they inflate the count this file reports as
evidence. Deleted, and the distinction stated as prose.

### Tested

Test 1: 30 checks, 0 failed. Sabotaged back to PAUSED and to the unexempted heal: red on three,
including the one that says a real state must not be healed away.

---

## §198 — Scrolling back re-read what he had already heard

Build 346. Watch mode works — it waits instead of stopping. Two faults showed up the moment it did.

### The passage guard could not see it

He scrolled up while watching and heard old text again. The guard refuses a PASSAGE it has read, and
a scroll gives a different WINDOW onto the same words: **half a screen he has heard plus half he has
not is a passage nobody has seen**, and it goes straight through.

**The unit of memory has to be the unit of speech.** A sentence is what gets spoken, so a sentence is
what gets remembered. The text is cut into sentences, the heard ones dropped, and what is left is
synthesised — so scrolling back over read text is silent and a new paragraph halfway up the screen is
read on its own.

Filtered BEFORE the passage check, because on a scroll the passage is genuinely new and only the
sentences say otherwise.

One line decided whether it worked: the synthesis reads `sentences`, which had to become the FILTERED
list. Left pointing at the screen's sentences, the filter would have decided what to read and the
next line would have ignored it — **a fix that computes the right answer and then discards it**, which
is the shape of §162a and §179 both.

### The cues are a signal, not text

*Thought process*, *Still working on it…*, *Identifying each image in the sequence*, *Running
command*. Reading them aloud is noise. Treating them as the end of the text is worse — **they are the
strongest possible evidence that it is not the end.**

They are dropped from the reading and, when a screen holds nothing else, it goes back to waiting
rather than stopping.

Matched on a normalised CONTAINS, because they arrive with a chevron, a spinner or a count beside
them. And **never remembered as spoken**, or a sentence legitimately containing those words later
would be silenced for the wrong reason.

**The list is deliberately four entries.** Every one is a phrase this app refuses to read, and a
careless addition silences something he wanted. The test asserts it stays short.

### In memory, not in a file

He asked for a temporary file. This is a set with exactly a temporary file's lifetime — cleared by
`stop` — and one fewer thing to clean up. If a reading should ever survive the keyboard being killed,
that is when it becomes a file, and it would then also have to survive being wrong.

### Tested

Test 1: 24 checks, 0 failed — the scroll-up he reported, modelled directly: three sentences read, a
window scrolled up by one, and only the unheard line spoken. Sabotaged by synthesising the screen
instead of the filtered list, and by remembering cues: red on four.

---

## §199 — The reader gets a row

Build 347. Reading has grown from one key into a thing with a speed, an effect, an alignment, a watch
and a place in the text. **One key cannot carry that**, and the dashboard behind a long press is a
panel he has to open, read and close for presses that should take no thought.

    ▶/⏸ · ⏮ · ⏭ · ■ · 👁 · − · +

Transport first, because that is what a hand reaches for mid-listening. The two settings last,
because they are decided once and then left. **The stop sits between them** — it ends the reading AND
the wait, and it belongs beside the keys that started them rather than at the far end where a thumb
arrives by accident.

**The plain READER key is unchanged.** One press, read this screen: that is the common case and it
must not need a row. `Rr` opens the row, which is the uncommon one. A row rather than a panel because
it does not cover the text he is listening to.

Play and pause are one key, as he asked, and it calls `MaReader.toggle` rather than deciding for
itself — **one definition of what a press means, not two.** The stop calls `MaReader.stop()`, which
clears the watching flag, so "stop waiting" and "stop reading" are one key because from where he sits
they are one thing he wants ended.

Two ways to switch the row on, one preference behind them: the `Rr` key and the switchboard.

### A debt, named rather than left to be found

This is the THIRD `xRowOnly` boolean. The comment on the second said two were honest about being two
callers each asking for one row, and that a fourth would be the moment for an enum. **Three is close
enough that pretending otherwise is rules-lawyering my own note.**

It is a boolean today anyway, and that is a decision: the refactor touches four call sites in three
files, and §150a and §187a are both what happens when a structural change is made in the tail of a
long session to save a round trip. The next special row does the enum first and adds itself second.

### Tested

Test 1: 36 checks, 0 failed — every command present in the catalogue AND drawn, play/pause as one key
delegating to the reader, the row off by default, both switches on one preference, the plain reader
key surviving, and the speed clamped at both ends. Sabotaged by making the row unconditional: red.

## §199a — Seven keys with no section, and seven keys nobody asked for

Build 347 pushed red, and the check that caught it was one of ours rather than the compiler.

**`check_when_coverage`'s counterpart in `test_key_groups.py`**: every key must belong to a group, and
the seven new reader commands belonged to none. In the picker they would have fallen through into
nothing — present in the catalogue, invisible in the list, findable only by search.

They are all **Reading** now. That is the one question somebody asks when looking for them; "which row
is this on" is not.

**And the same edit had put them in the DEFAULT feature row**, which would have dealt every new
install seven transport keys it never asked for, on rows he would then have to clear. They live on the
reader row, which has its own arrangement and is off by default. Removed.

Both faults came from one anchor: the insert went into the first `MaFeatureKey.READER,` in the file,
which is the default ROW, when the group branch further down was what needed it. **Two lists of the
same names in one file, and the edit chose the wrong one** — the same shape as §179a's missing anchor,
inverted: there the anchor did not exist, here it existed twice.

## §199b — A signature written from memory

Build 347's second red: `imageVector = …` passed to `ThemedIconKey`, four times in one file. It takes
`code` and `icon`.

**I wrote it from memory of what a Compose `Icon` takes.** The app's own key composable has its own
names, twenty lines away in a file I had read that afternoon, and I did not look.

A guard was measured and **rejected**: flagging `ThemedIconKey(` calls without a named `icon =`
argument found **13 hits**, nearly all of them positional calls — `ThemedIconKey(KeyCode.UNDO,
action.icon, label, …)` — which are correct Kotlin and correct style. A check that fires on thirteen
correct calls to catch one wrong one is the shape `check_property_call` was deleted for.

So the gap stays open and CI keeps it, at five minutes a time. **The habit that would have prevented
it costs nothing: read the signature before calling it, especially the ones in this repository, and
most especially the ones that look like a library's.**

---

## §200 — Haiku is a rule, and the keys come first

Build 350.

### The screen was ordered for the first day

`Permissions and API keys`, seven numbered steps, and the keys as **step eight of a seven-step
setup**. The permissions are granted once, on a new phone, and never touched again. The keys are
edited, tested, imported and replaced constantly.

**A screen ordered by the sequence a new install goes through is ordered wrongly for every day after
it.** Keys first, with no number and no tick — a key is not a step, and it is never simply "done": it
can be present and dead, present for one provider and missing for another, or working this morning
and out of credit this afternoon.

The entry is `API keys and permissions` now, because the name is what he scans for.

`FIND A WORKING KEY` became **`TEST KEYS`**. The old label described the ALGORITHM — walk the ring
until one answers — and he presses it to find out whether his keys work.

### Haiku, enforced rather than defaulted

Every language job here is small: proofread a sentence, reflow a paragraph, restyle a line, guess a
word the local n-gram cannot. **None is improved by a larger model and all are billed by one.**

The code trusted `preset.defaultChatModel`, under a comment asserting it is haiku, mini or flash "for
every provider in the registry". **That was true when it was written and nothing kept it true.** A
preset edited next year, or a provider renaming its default, silently promotes every rewording to an
expensive model — and the only symptom is the bill, arriving a month later with nothing to point at.

`MaSmallModel` names the small model per provider, checked FIRST, with the preset as fallback rather
than source. Anthropic gets haiku by name.

Two decisions inside it worth keeping:

- **Matched on a contains**, because provider ids are not uniform — "anthropic", "anthropic_claude"
  and similar have all appeared, and a lookup that misses returns silently to the expensive path.
- **An unknown provider falls through** to the old behaviour. This is a list of promises kept, not a
  gate; a provider nobody has thought about should keep working.

The test asserts no named model contains `opus`, `sonnet`, `70b`, `405b`, `ultra` or `pro`.

### Two checks, both wrong in the way this file keeps recording

**One raised instead of failing.** `ctrl.index(...)` on a sabotage that removed the call: the count
never printed and every other result was lost. **Third time this month**, and each time in a check
written the same hour as the section documenting the previous one. `find` now.

**One asserted an expression rather than a promise.** `test_key_search` checked for
`preset.defaultChatModel ?: account.chatModel` — how the pinning happened to be written — and broke
the moment the mechanism improved. Nothing was wrong. **A check on an expression fails when the
expression changes; a check on the promise does not.** It asserts the model is small.

## §200a — Icon and Icons, and a closed list that had to ask what the name meant

Build 350 red on `Icon` and `Icons`, unresolved — a composable drawing an icon, added to a file that
had never drawn one.

`check_layout_imports` covers them now, which makes four groups it guards: `foundation.layout`,
`compose.runtime`, this app's `dictate.nlp`, and `material3`/`icons`.

The measurement found **one** hit: `ThemeManager.kt`, which imports `android.graphics.drawable.Icon`.
A different `Icon` entirely, and demanding the Compose one there would have been wrong.

So the check now skips a file that imports the name from ANY package. **A closed list still has to ask
whether the name means what you think it means in that file** — the list bounds which names are
checked, not which meanings exist.

Re-measured at zero across the app, and it names the real broken file.

---

## §201 — The three dots that arrive hours late

Build 353. He waits hours, sometimes days, for *Allow restricted settings* to appear in App info, and
asked whether the app can force a refresh.

**It cannot, and that is the point of the restriction.** The gate exists so a sideloaded app cannot
grant itself accessibility. Anything here claiming to force a refresh would be an app asking the
system to stop restricting it, which is the attack it was built to stop.

### But the delay is not random

Since Android 13 a sideloaded app has `ACCESS_RESTRICTED_SETTINGS` set to errored, and the overflow
item appears **only once the system has seen the app refused at that gate.** It is a response to an
attempt, not a property of the phone — which is exactly why it turns up "suddenly, after hours":
something tripped the gate in the background.

**The old instruction made this worse.** It read *"Skip if your phone does not offer it"* — sending
him away from the one action that makes it appear. It now says to go and be refused first,
deliberately, then come back.

### The two levers, on the screen, copyable

    appops set com.mantraproductions.tttlight ACCESS_RESTRICTED_SETTINGS allow
    pm install -i com.android.vending -r ttt.apk

The first clears it for this install; the second names an installer the restriction does not apply
to, so it never arises again — for that install and every update over it. Both need Shizuku or a
computer, and neither is a workaround for a bug: **they are the mechanisms the three-dot menu drives,
reached directly.**

**Copyable rather than printed.** He dictates rather than types, and a package name with two dots is
the sort of string mistyped once and debugged for twenty minutes. The test asserts the package in the
command matches `applicationId` in the build file — **a command with the wrong package name does
nothing and says nothing**, which is the worst possible failure for an instruction.

And the second command's consequence is stated on screen rather than left to be discovered: it
changes what every future update inherits, which is worth knowing before running it.

### Tested

Test 1: 56 checks, 0 failed. Sabotaged by changing the package name in the command: red on the check
that compares it with the build file.

---

## §202 — The bar stops moving. Third report, and the first real fix.

Build 355. He reported the jumping suggestion bar three times. The third time he described it as
seasickness and asked me not to build apps like this.

### What I got wrong twice

**§195** guarded the switching while a delete key was held. That was the case he had described, and
it was too narrow: the bar switches on ordinary typing too — a space empties the suggestions, the
next letter refills them, and the bar moves both times.

**A narrow fix for a symptom already reported twice is worse than no fix**, because it looks
addressed. He then has to notice it is still happening, decide it is worth saying again, and say it
again. That is three rounds of his time for one problem, and the second round was spent on my
half-measure.

### What the fix is now

**The automatic switching is gone, not narrowed.** `autoExpandCollapseSmartbarActions` does nothing.
The bar opens when he opens it and stays open until he long-presses it.

**What is lost, stated plainly:** a row of actions no longer appears by itself when there is nothing
to suggest. That is what the feature did, and it buys a few pixels of screen when the suggestions are
empty. **A few pixels are not worth a moving keyboard**, and the actions are one long press away.

The function is kept rather than deleted: it is called from three places on the typing path, and
removing three call sites at the end of a session is §150a. An empty function with the reasoning in
it is also the one place somebody will look when they ask why the bar never moves.

### His gesture

**Long press the bar to close it.** On the whole row, so nothing has to be aimed at while a word is
under his thumb; a long press rather than a tap, because a tap picks a word and the two cannot share.

`pointerInput` sits BEFORE the horizontal scroll modifier — after it, a drag would win the gesture
and swallow the press on exactly the crowded rows where he most wants it gone. And no animation on
the toggle, because a bar that slides is the movement he is complaining about.

### Three checks that had to be retired or bounded

Two asserted the shape of the delete guard — a guard inside a function that now does nothing. The
claim that replaced them is stronger: **nothing sets the expanded state automatically, and nothing
even computes it.** A decision computed is a decision that will be acted on again one day.

One compared modifier positions by searching the whole file, and found the `florisHorizontalScroll`
IMPORT at the top — comparing a use against an import, and failing on correct code. **A position
check has to bound the region it is talking about.**

### Tested

Test 1: 16 checks, 0 failed. Sabotaged by restoring the switching: red on two.

---

## §203 — He opens it, he closes it, and the recorder borrows it

Build 357. The approach he asked for, and it is simpler than everything that came before it.

    Sg key           opens and closes the suggestion row. Nothing else does.
    record           borrows the row for the recording row.
    stop recording   gives it back — IF it was on.

### The switch existed and was unreachable

`maSuggestionsShown` has been there all along, toggled by a **long press on the language badge** — a
control deleted three builds ago. So the row was switchable in theory and not in practice, and that
is why the automatic switching mattered so much: **it was the only thing that ever moved the row, and
it moved it constantly.**

Now it has a key, `Sg`, on a row he arranges, wearing the green ring while the row is up.

### The recorder borrows, it does not decide

The one automatic change he asked for, and the clause that makes it safe: **`maSuggestionsShown` is
never written by the recorder.** A row he had closed stays closed when the recording ends; one he had
open comes back. The recorder takes the space and returns it, and has no opinion about whether he
wants suggestions afterwards.

That is the difference between borrowing and deciding, and it is one line — the absence of a
`set(...)` — so the test asserts the absence rather than the behaviour.

### Why this is the right shape and the earlier ones were not

Every previous attempt had the app inferring what he wanted from what was on screen: suggestions
empty, so show actions; suggestions back, so hide them. **Inference is what made it move**, and no
amount of narrowing the inference fixes a design that infers.

This has no inference in it. Two switches, both his, and one bounded exception tied to an action he
starts and ends deliberately.

### Tested

Test 1: 29 checks, 0 failed — the key exists, is drawn, toggles the same preference; the recorder
hides the row and never writes the preference; and the state machine walked over both settings,
asserting that stopping restores exactly what he chose. Sabotaged twice — the record row not taking
over, and the recorder writing the preference — red once each.

---

## §204 — Sg was filed by what its name sounds like

Build 359. He found the suggestion-row switch under **Reading aloud** and said it belongs in **The
keyboard itself**. He is right, and the reason is worth keeping.

`Sg` reads as a reading key — "suggestion" is a word, the reader deals in words — and it is nothing
of the kind. It shows and hides a row of the keyboard, exactly like `n`, `k`, `c` and F1–F6.

> **The group answers what a key is a part of, not what its name sounds like.**

It sits with the zone keys now, and the test asserts it by GROUP rather than by position:
`group_of("SUGGESTIONS") == group_of("ZONE_1")`. A check on the line it appears on would pass while
the key was in the wrong section, which is exactly how it got there.

### Two lists of the same names, again

The move took three attempts.

The first `str.replace` matched nothing — the anchor spanned a comment I had reformatted — and only
the test edit landed, so the suite went red against unchanged code.

The second inserted beside `MaFeatureKey.ZONE_1,` and hit **the DEFAULT ROW at line 511** rather than
the group branch at 721. Same names, two lists, and the edit chose the first. **That is §199a exactly,
in the same file, about the same pair of lists** — and it was found the same way, by the group check
returning `None` instead of the wrong answer.

Third attempt anchors after `val MaFeatureKey.group`, which is the only unambiguous way to name that
branch. **When a file contains two lists of the same identifiers, an anchor must name the section
before it names the line.**

---

## §205 — The send pair goes, and why the record pair stays

Build 361. `→H` and `→E` are deleted — the keys, the branch that drew them, the picker glyphs and the
catalogue entries.

### The asymmetry is the whole point, and I missed it

`● H` and `● E` are right, and he confirmed it. `→H` and `→E` are wrong. They look like a matching
pair and they are not:

**Recording in a language is a choice that must be made BEFORE the audio exists.** The recorder reads
the language when it starts; there is no later moment at which it can be supplied. The key IS the
choice, which is what killed the mode.

**Sending happens after all that.** By then the language is settled — chosen when the recording began
and carried on the request. A per-language send key is a second way to pick something already
picked, and a second way to pick is a second way to pick wrongly.

I built the pair because he described two record keys and two send arrows in one sentence, and I
matched the shape instead of asking what each one decides. **Symmetry in a request is not evidence of
symmetry in the problem.**

The plain SEND key is untouched.

### Four checks retired, one inverted, one repaired

Two asserted the deleted pair's dimming and its polling of `sendVisible`. **A check that asserts the
shape of deleted code fails for a reason unrelated to the shape** — keeping them would mean re-adding
the key to make the suite green.

Two inverted rather than disappearing: `SEND_HR` and `SEND_EN` must now be ABSENT from the catalogue,
so the pair cannot return by accident. Sabotaged by re-adding one: red.

And one was repaired. `pressSend` returns a `String?`, and the check tested for `sent != null` — the
deleted pair's spelling. The surviving plain key writes `pressSend() == null`, which is equally
correct, and the check failed on the survivor for using the shorter form. **A check that knows one
spelling of a correct thing will fail on the other.**

---

## §206 — One voice, and the last automatic decision in the reader

Build 364. He picks a voice and that voice reads, whatever the text is in.

### What was there and why it looked right

Two lists, two selections, and `chosenVoice(language)` picking between them. English text got the
English voice and Croatian text got the Croatian one.

**That sounds obviously correct and it is the thing this month has been spent deleting.** It means the
app decides, from a language that is itself a guess, and when the guess is wrong the reading comes
out in a voice he did not choose with nothing on screen explaining why.

> **A voice chosen for him by a language detector is a choice he cannot see being made.**

A Ukrainian voice reading English is a choice he can hear and correct in one tap. A voice silently
swapped because a detector called his sentence Croatian is not.

### The shape now

One preference. One `ALL_VOICES` list, English first because that is what he reads most, Croatian
after with its note about why they all sound foreign. **Both sections write the same preference**, so
lighting a voice anywhere darkens the one that was lit, wherever it was.

The headings stay — they tell him what he is choosing between. What is gone is the idea that a
heading OWNS a selection.

`chosenVoice()` takes no argument and the test asserts `MaLanguage` does not appear in its body:
**the absence is the rule**, and a check for the absence is the only kind that can hold it.

The fallback is the head of the list rather than "the first of the matching language", because there
is no matching language any more.

`maReaderVoiceEn` and `maReaderVoiceHr` stay declared and unread, so a phone that stored them reads
back harmlessly.

### Tested

Test 1: 15 checks, 0 failed. Sabotaged by pointing the Croatian group back at its own preference: red
on two — two selections, and something would have to choose between them.

## §206a — `name` for `label`, in the session that recorded doing it

Build 364 red on `chosenVoice.name`. The field is `label`. `Voice` is eight lines long, two files
away, and I had read it in this same build to write `ALL_VOICES`.

**This is §199b, one day later, in the same session that documented it.** There I passed
`imageVector` to a composable that takes `icon`, from memory of what a Compose `Icon` takes. Here I
wrote `.name` from memory of what a voice object "should" have.

No check is possible: a property that does not exist is a compiler question, and the guard measured
for §199b found thirteen false positives. **The only defence is the habit, and writing the habit down
twice has not yet installed it.** What CI costs is five minutes; what it protects against is shipping
a build that does not compile, which it does reliably.

Recorded again rather than quietly fixed, because two occurrences a day apart is a pattern and the
third will be cheaper to recognise than to diagnose.

---

## §207 — The cloud reader: what it read, kept

Build 367. The reader speaks a chat once and it is gone — scrolled past, or lost when the chat
closes. **A conversation he listened to is a conversation he cannot go back to**, and the answers are
often the only place a decision was explained.

### Capture rides the reading's own poll

Not a second loop. A capture on its own timer would be a second thing reading the screen every two
seconds, and **it would disagree with the reader about what is new** — so the file would hold
sentences he never heard and miss ones he did. One poll, one idea of what arrived.

It runs BEFORE the cue filter and the already-read check: those decide what is worth SPEAKING, and
the log wants everything the chat said. A "Still working on it" he does not need read aloud is still
part of the conversation.

### What decides a "chat", and how it fails

Nothing tells this app which conversation is on screen. A log is keyed by **the first text seen when
capture started**, folded to eight words — enough to survive a re-wrap and a ticking clock, enough to
separate two real conversations.

The failure modes are stated in the source rather than discovered later: two chats opening with the
same words share a log; a reopened chat continues its own. **A wrong guess costs a merged file. It
never costs text** — nothing in the capture path deletes, and the test asserts there is no `delete()`
in it at all.

Off by default, because it writes files to internal storage without being asked.

### Reading a log back

Tap a sentence and it reads **from there to the end**, not that sentence alone: he is looking for an
answer, and an answer is a paragraph. Stopping is one press away at the top.

`MaReader.speakText` shares everything with the screen reader — voice, speed, effects, chunking —
because **a second reading engine for saved text would drift from the live one within a month.** It
clears the sentence memory first, or a passage he heard live this morning would be silently skipped
when he opens the log this afternoon.

The sentence splitter is deliberately NOT `MaReadChunks.sentences`: that one splits for synthesis,
where a chunk too long costs latency. This splits for TAPPING, where a chunk too short is a target he
cannot hit. One rule serving both would be tuned for neither.

### Two checks that were wrong about their own subject

**An ordering check compared first appearances** — and `captureToLog` appears in its own function
definition further down the file, so it was comparing a definition against a call. The sabotage
passed. It compares the two CALLS now, guarded with `find`.

**A count-based check broke on a legitimate addition.** `watching = false` appeared once, so the test
asserted exactly one — a proxy for "no timer ends a watch". `speakText` added a second, correctly,
because a file does not grow. **A proxy fails the first time something legitimate is added**, so it
now asserts the thing itself.

### What is NOT done

**Capture only runs while the reader is watching.** A chat he reads with his eyes is not logged. That
is a real limit and the honest one to ship: capturing continuously means a second always-on poll of
another app's screen, which is a battery and privacy decision he should make deliberately rather than
inherit from a build about reading.

Nothing has been captured on a phone. The key, the append and the splitter are walked; the file has
never been written.

## §207a — A screen written from memory of a screen

Build 367 red twice in one file, and both are the same fault: **I wrote a settings screen from memory
of what one looks like instead of opening one that compiles.**

**`previewFieldVisible = false`** — no such property on `FlorisScreen`. Invented from the shape of
other screens.

**`collectAsState` imported from Compose.** A preference's `collectAsState` is jetpref's. Both are
called the same thing, both are importable, and only one works on a `PreferenceData` — so the wrong
one gives four errors on one line, **none of which names the real cause.** That is the expensive kind
of mistake: not hard to fix, hard to read.

### A check, measured and shipped

`check_preference_collect`: a file that reads `prefs.x.y.collectAsState()` must import jetpref's.
**Zero false positives across the app**, and it names the file that broke.

This one is worth having where the `ThemedIconKey` and `Voice.name` guards were not: those needed to
know a type's members, which a regex cannot; this needs to know which of two identically-named
imports is present, which is exactly what a regex can see. **The difference between a check worth
writing and one worth rejecting is whether the mistake is visible in the text.**

Three guesses-from-memory in three builds — `imageVector`, `.name`, and now a whole screen. Two were
uncheckable. This third one was not, and the check exists now.

---

## §208 — The app would not start, and every gate was green

Build 370. He installed 369 and the app would not open.

### The cause

`Routes.kt` registers a settings screen with `composableWithDeepLink`, which begins:

    val deeplink = requireNotNull(kClass.annotations.firstOrNull { it is Deeplink } as? Deeplink)

My route object had `@Serializable` and **no `@Deeplink`**. So the nav graph threw while it was being
built — which is while the app is starting. It installs, launches, and dies with nothing on screen.

### Why nothing caught it

It compiled. `verify.py` was clean. Twenty-five Test 1 suites passed, including twenty-seven checks
written for this very feature. CI was green and cut a release.

**Every gate this project has was designed to check things that are wrong on the page.** This was
right on the page and wrong at runtime, in the one code path no test here can reach — and the cheapest
possible symptom, a hard crash at launch, was invisible to all of it.

It is also invisible to the eye. The annotation sits three lines above the object, and every
neighbouring route has one: **exactly the shape a reader completes for himself.**

### What was done, in order

**The feature was reverted first, before the cause was known.** His keyboard is how he writes, and a
keyboard that will not start is not something to leave broken while I read code. Diagnosing with his
only input method down is the wrong order, however much faster it feels.

Then the cause, then the check: `check_route_deeplink` reads every
`composableWithDeepLink(Settings.X::class)` and requires `@Deeplink` above `object X`. Measured at
zero false positives on the real file, and it names the broken route when the annotation is removed
from any existing one.

### The lesson worth more than the check

**A green CI proves the code compiles and the logic is walked. It does not prove the app runs.** Gates
G6 through G8 — stress, upgrade, a phone — have never run in this project, and this is the first time
that gap cost him rather than being a line in a delivery record.

Every "it works" in this repository still means "it compiled, and its logic was walked". That sentence
has been in `HANDOFF.md` for weeks. Today it had a price.

---

## §209 — The cloud reader, again, with the annotation

Build 374. The feature from §207 restored unchanged, plus the one line whose absence stopped the app
starting.

**Restored by reverting the revert**, not rewritten. The code was reviewed once and the fault was
never in it — it was in a line that was missing beside it. Retyping it would have invented new bugs
to fix an old one.

### The gate worked before the build

`check_route_deeplink` refused the restored feature on the first run, naming the route. That is the
whole point of yesterday's section: the crash that cost him a keyboard is now a five-second refusal
at my desk.

### And then the gate was blind to me

I wrote a comment between `@Serializable` and `@Deeplink` explaining why the annotation matters. The
check walks a run of `@Thing` tokens above the object, and **a comment breaks the run** — so an
annotated route read as unannotated, and the feature's own test failed on correct code.

Worse in the other direction: a route whose `@Deeplink` was separated from it by a comment would have
read as annotated while the annotation attached to nothing.

**The check was blind to the shape its own author produced ten minutes later.** Comments are stripped
first now, in both the gate and the test, and both cases are proven: the annotation removed is
caught, and correct code with a comment among the annotations is not reported.

### The sweep, re-run against the restored feature

    registered without @Deeplink   none
    entries without an icon        none
    entries without a route        none
    entry targets unregistered     none
    duplicate deeplink paths       settings/dictate/prompts

That last one is **not new and not mine**: `MaPrompts` and `DictatePrompts` have shared a path since
long before this session. Recorded rather than fixed — changing a deeplink is a change to something
outside this build, and an audit finding is not a licence to edit whatever it touches.

## §209a — The menu entry that could never appear

Build 376. He opened settings and the cloud reader was not there.

Everything was right except one thing: the enum entry existed, the icon branch existed, the route
existed and was registered with its annotation, the screen compiled, `check_route_deeplink` passed
and so did the agreement sweep — which checks that every entry HAS an icon and a route, and never
asked whether the list that produces entries contains it.

`MaSettingsOrder.parse` returns the stored order plus `DEFAULT`, **and nothing else**. An entry
missing from `DEFAULT` can never be returned, however correct it is everywhere else.

> **Membership of a list is not something an exhaustiveness check looks at.** Every `when` had its
> branch. `check_when_coverage` was satisfied. The gap was in a `listOf(...)`, which has no
> compiler opinion at all.

This is the same class as §208 one day later: **something right on the page, absent at runtime, with
no error anywhere.** The 208 version killed the app and was obvious within seconds of launching it.
This one is quieter and would have survived much longer — he had to notice a thing that was not
there, which is the hardest kind of bug for a person to report.

`check_settings_default` requires every entry to be in `DEFAULT`, with `KEYS` exempted because
`parse` filters it deliberately. Zero false positives, and it names the entry when it is removed.

## §209a — Complete, compiling, and invisible

Build 379. He opened the settings list and the cloud reader was not in it.

`MaSettingsOrder.parse` returns his stored order plus `DEFAULT.filterNot { it in wanted }`. The entry
was in the enum, had an icon, had a registered route — **and was not in `DEFAULT`**, so it could only
appear on a phone that had never saved an order. His has.

`verify.py` was clean. 34 checks passed, five of them written for this feature's wiring. The only
symptom was him saying he could not see it.

**Second time in three days that a feature shipped complete and unreachable.** Build 369 crashed on a
missing `@Deeplink`; this one vanished for a missing line in a list. Both compiled, both passed every
test, and **neither was a logic error — both were a thing not added to a second place.**

That is now a shape with a name, and it has a gate: `check_settings_entry_reachable` requires every
entry to be in `DEFAULT`. Measured at zero false positives, and it names the entry when the line is
removed.

The check also refuses to pass silently if it cannot find the entries or the `DEFAULT` block —
**a check that runs nothing must look like a failure**, per `four-tests.md`, because it otherwise
reads exactly like a pass.

---

## §210 — Claude.ai reader: the name, the crash, and the rename

Build 380.

### The name

"Cloud reader" was my word and it was wrong twice over: it reads **Claude.ai**, and "cloud" suggests
something stored remotely when the whole point is a file on his phone. It is the **Claude.ai reader**
everywhere now.

### The crash

Opening the screen killed the app. `FlorisScreen` wraps its content in a vertical scroller, and a
`LazyColumn` inside a parent offering infinite height cannot measure itself — it throws.

**Three sibling screens set `scrollable = false` for exactly this**: `DictateHistoryScreen`,
`DictatePromptsScreen`, `DictatePromptLibraryScreen`. I wrote a screen with a lazy list without
opening one of them.

That is the same fault as `ThemedIconKey`'s parameter names (§199b) and `Voice.label` (§206a): **a
thing written from memory of what such a thing looks like, with three working examples in the same
folder.** Third time in a week.

A gate was written and **rejected**: it flagged `SubtypeEditorScreen`, whose lazy list is inside a
dialog with scrolling already disabled, and it failed to catch the real break reliably. One false
positive and an unreliable catch — the rule is the rule. The feature's own test asserts it instead.

### Renaming a chat

He renames conversations. The key comes from the first text seen, so a rename would open a second
file and split the conversation in two — half under the old name, half under the new. **The rename is
his; the split would be mine.**

So before starting a log, the existing ones are asked whether one already CONTAINS this conversation.
A file whose text the screen still shows IS this chat, whatever it is called now, and the log
continues into it.

Three decisions inside that:

- **Matched on the tail**, not anywhere in the file: a chat grows at the end, and anywhere-in-file
  would match a quotation of one chat inside another.
- **A short screen never matches.** Two chats that both open with "hello" would otherwise merge.
- **No match means a new log**, which is the safe direction. **A wrong match appends one chat to
  another; a wrong miss makes a second file.** One is recoverable by reading, the other by nothing.

And when a log is continued, `logSeen` is seeded from the file's own tail — otherwise opening a chat
logged yesterday would append its whole visible screen a second time.

### Tested

Test 1: 49 checks, 0 failed. Sabotaged by removing `scrollable = false`: red.

---

## §211 — The Log key

Build 381. Capture happened only while the reader was WATCHING, so a chat he read with his eyes was
never kept. That was the honest limitation named in §207 and he asked for the press that removes it.

`Log` on any feature row. It captures the screen into the log immediately.

**Through `captureToLog`, the same function the reading poll uses.** One capture path, so the file
cannot end up with two ideas of what "already logged" means — the session's `logFile` and `logSeen`
are shared, and pressing it mid-reading appends to the same log rather than opening a rival one.

**It says which log it wrote to**, not "saved". He has many logs, and the only question a message can
usefully answer is WHICH — that is also what tells him a rename was followed. A press that writes a
file and says nothing is indistinguishable from a press that did nothing.

**Ringed while the Claude.ai reader is switched on**, because a press with it off does nothing, and a
ring says so before the press rather than after it. It also refuses in words when the reader is off
or the accessibility service is not running.

### A check that read the wrong function

`reader.split("captureToLog")[1]` found the FIRST mention — which is now inside `captureNow`, the new
caller. So the check looked in the caller for a guard that lives in the callee, and failed on correct
code. It splits on `private fun captureToLog(` now.

**A name is not a location when more than one thing uses it.** That is the same shape as the
first-appearance ordering check in §207 and the import-versus-use position check in §202 — three
variants of one mistake in a fortnight, each time because the cheapest thing to grep for was a bare
identifier.

---

## §212 — The AssemblyAI Dictation API, researched and not wired

4.9.2026. He asked for the keyboard to use AssemblyAI's new Dictation API. **Researched first, and
the research says it cannot carry this app's main job.**

    POST https://dictation.assemblyai.com/v1/transcribe/live
    multipart: `config` (JSON) FIRST, then `audio`
    Authorization: <key>     — raw, no Bearer
    returns: text (verbatim) + llm_response (cleaned) + llm_error

### Three constraints, measured from the documentation

**1. NO CROATIAN.** The endpoint accepts 32 language codes and `hr` is not one of them — a code
outside the set is rejected with `400`. Half his dictation is Croatian. **This is not a limitation to
work around; it is the feature not existing for him.**

**2. 120 seconds of audio per call.** He dictates long. A cap that silently truncates would be worse
than a refusal, so any wiring needs a length guard and a message.

**3. WAV or raw PCM only.** Compressed formats are rejected with `415` because the endpoint decodes
as it arrives. **This app compresses to Opus before upload** precisely to keep his mobile data down —
using Dictation means sending uncompressed audio, which is roughly an order of magnitude more bytes.

### What it is genuinely good at

One call instead of two: `llm_response` comes back cleaned, which is what Ctrl+F does today in a
separate request to a separate provider. p50 of 134 ms. $0.62 per hour, flat.

For **English, short, on wifi**, that is a better shape than what this app does now.

### The decision, which is his

It belongs as **one more transcription provider he can pick**, never the default, with the language
guard refusing Croatian before the request rather than after a `400`. That is a build, and it needs
his answer first: a provider that handles half his dictation and refuses the other half is a thing he
has to remember the state of, which is exactly the kind of mode this month has been spent deleting.

**Not wired. Researched, recorded, and stopped at the point where the next step is his.**

---

## §213 — The Dictation API, English only

Build 382. Wired per §212, with the constraint that decides everything: **Croatian is not one of the
32 languages this endpoint accepts.**

    English, under 2 minutes   ->  dictation.assemblyai.com/v1/transcribe/live
    Croatian, any length       ->  the existing path, unchanged
    anything over 2 minutes    ->  the existing path, unchanged. His rule: we go the old way, always.

### It is a sibling, not a new mechanism

`ASSEMBLYAI_SYNC` already exists with the same 2-minute cap, the same WAV/PCM requirement and the
same caller-enforced eligibility. So **`maUseDictationPath` asks exactly one question — the language**
— and everything else is `maUseSyncPath`'s five guards, already applied. Restating them would have
been two places to keep in step.

### Three details that decide whether it works

**`config` comes FIRST.** The server transcribes as bytes arrive and cannot begin without it; an
audio part that arrives first is rejected with `400`. **Sync's config is optional and last** — the two
siblings differ in exactly this, which is the kind of thing that is obvious in the documentation and
invisible in the code. The test asserts the order by index.

**No `llm_instruction` is sent.** The default cleanup is what a dictation wants. His own voice
belongs to Ctrl+F, a deliberate press against a model whose prompt he controls. **Putting his prose
rules here would silently apply them to every recording** — and this month has been spent removing
things that decide for him.

**A failed rewrite is a successful request.** The service returns `200` with `llm_response: null` and
an `llm_error`. The transcript is intact and is used. Treating the error as a failure would throw
away a perfectly good transcription.

### The language comes from the key he pressed

`MaLanguage.active()`, set when he pressed `● E` or `● H`, before the audio existed. **There is no
detector in this path and there must not be** — a detector saying "English" about a Croatian sentence
turns a good recording into a 400 after the whole upload.

### The switch says what it excludes

"English recordings under two minutes… Croatian and anything longer are unaffected." A summary
promising *faster dictation* would be a switch he turns on and then wonders about every time a
Croatian recording behaves as before.

### Tested

Test 1: 30 checks, 0 failed — the full 32-code list recorded so a future change to it is visible,
every Croatian-family code refused, `config` before `audio` by index, no instruction sent, the
transcript used when the rewrite fails. Sabotaged two ways: red on both.

**Not tested: no request has ever been made.** The wire format is read from the documentation, not
observed. The first English recording under two minutes is the test.

## §213a — Verbatim text, and no way to tell why

He dictated in English and got back text with no punctuation and no capitals.

**That is precisely what `text` looks like.** Punctuation and capitalisation are applied by the
CLEANUP, not by the transcription — so what he read is the verbatim fallback, which means
`llm_response` came back null or blank.

Two causes, and they have different fixes:

1. **The rewrite failed** — a timeout or an error, which the service reports in `llm_error` while
   still returning `200`. The fallback then did exactly what it should.
2. **This path never ran**, and he was on Sync or async all along. Sync returns punctuated text, so
   this is the less likely of the two — but "less likely" is not "ruled out".

**Nothing in the result said which**, which is the actual defect. A fallback that is invisible is a
fallback that cannot be diagnosed: the code did the right thing and told nobody.

One line of log now reports the length of both texts and the `llm_error`. `cleaned=0 llmError=timeout`
is the first case; no dictation line at all is the second.

The general shape, which this file has now met three times: **a correct fallback is still a silent
one.** The reader's cue-skipping, the model healer's rewire and now this all choose a second-best
answer without saying so. The healer logs it. The other two did not.

---

## §214 — Verbatim or corrected, as a switch

Build 384. He asked for one switch in the recording settings, in those words.

    Corrected dictation   ON   filler out, punctuation in, "three, no, four" becomes four
                          OFF  exactly the words he said

### It was never a choice, and that is what hid the bug

The code PREFERRED the corrected text and fell back to the verbatim one. So a rewrite that failed
produced exactly what a setting he had chosen would produce, and §213a was a day spent asking which
of the two had happened.

**A fallback and a preference that produce the same output are indistinguishable until one of them
says so.** Now verbatim returns early because he asked for it, and the fallback logs that it is a
fallback.

### Named by the result, not the mechanism

"Dictation API" is the name of an endpoint. **"Corrected" is the name of a result**, and the result is
what he is choosing between. The other switch, which decides where the text comes from, stays below
it and keeps its own name.

### The summary admits what it does

*"...and 'three, no, four' becomes four."*

That is the part worth naming, because it is the part that is a judgement rather than a tidy-up.
Usually right; and when it is wrong, nothing on screen shows that anything was changed. **A setting
that silently changes his words should say so where it is switched on**, not in a document he would
have to go and find.

### Tested

Test 1: 45 checks, 0 failed — all four combinations of the switch against a present and an absent
rewrite, the choice carried on the request, the early return, and the fallback's log line. Sabotaged
by deleting the early return: red.

---

## §215 — His settings become the defaults

Build 386. He sent an export and asked for it to be built in.

**82 entries came in. 49 shipped. 33 did not, and the 33 are the interesting part.**

### What must never ship

**His API keys.** The first line of that file held five AssemblyAI keys in plain text. They were
never read, never printed and never stored — and `test_shipped_defaults.py` now refuses any
key-shaped string in `AppPrefs.kt` for ever.

**His content.** `last_dictation` held a paragraph he had just spoken. `ma_clip_captured` held his
clipboard. `ma_ngram_pending` held words he had dictated. **A default carries to every install**, so
shipping these would hand a stranger his sentences.

**His usage.** `total_audio_seconds` was 60,620 — seventeen hours. A fresh install claiming that is a
lie in the only direction that matters.

**One-time flags.** Ten `*_applied` and `*_migrated` booleans that mean "this upgrade already ran".
Shipping them true tells a fresh install it has already done work it has never done, and the
migration is then skipped for ever.

> An exported settings file is a mix of what he wants, what he did, and who he is. **Only the first
> is a default.**

### What shipped

Rows, the settings order, macro slots, magic targets, paste delays, scroll pages, reader voice, speed
and alignment, the theme, the keyboard switches, the provider ids, and the two new dictation
switches. 21 defaults changed; the rest already matched from the 21.8 export.

### Two type changes, caught by reading the diff

The export stores everything as a string, so a mechanical rewrite turned
`DictatePromptsLayout.ROW` into `"ROW"` and `Color(0xFFE8B15C)` into `"ffe8b15c00000000"`. **Neither
compiles**, and the colour was not even a change — the hex is the same value in the export's format.

Caught by reading every changed line, not by a check. The test asserts both types now, which is the
cheap half of the lesson; **the expensive half is that a mechanical edit across 49 sites needs its
diff read line by line**, and there is no substitute for that.

### Tested

Test 1: 22 checks, 0 failed. Sabotaged by putting his clipboard in a default and by re-breaking the
enum: red on both.

## §215a — A test that asserted my caution, not his choice

Shipping his export turned the Claude.ai reader's log on, and `test_cloud_log.py` went red: it
asserted `default = false`, with the reason that a feature writing files to internal storage should
be one he switched on.

**He did switch it on.** That is what the export is.

The check was defending a decision I made on his behalf against the decision he actually made. **A
default is his choice, not my caution about his choice** — and once he has stated it, a test holding
the old value is just an argument I am having with him in a file he does not read.

What survives is the claim underneath, which is the one that mattered all along: the switch must
exist and be visible, so he can turn it off again on any install without editing anything. **On, with
no visible control, is not a setting — it is a behaviour.**

## §215b — Four type changes, not two

Build 386 went red twice. The mechanical rewrite produced **four** type errors, not the two I caught
by reading the diff:

- `DictatePromptsLayout.ROW` → `"ROW"` — caught by reading
- `Color(0xFFE8B15C)` → its hex — caught by reading, and not even a change
- `extCoreTheme("sunrise")` → `"org.florisboard.themes:sunrise"` — **twice**, missed, caught by CI

Every one is **the right value in the wrong type**. The export stores everything as a string, so a
rewrite from it cannot know that two of these fields are `ExtensionComponentName` and one is a
`Color`.

And a fifth fault of a different shape: replacing a multi-line default left the PREVIOUS version's
continuation lines dangling after the new one, so a string ended and two more began. That is not a
type error; it is an edit that did not know where its target ended.

**The lesson is not "add four checks".** It is that a mechanical edit across 49 sites cannot be
verified by reading a diff of 21 changed lines — I read them, and still missed half. The checks are
here because they are cheap, but the thing that actually caught these was the compiler, and the only
honest conclusion is that this edit should have been run past a compile before being pushed.

---

## §216 — The number row gets a door

Build 390. He asked for a settings entry to customise the second character on each digit.

**It already existed.** `MaNumericSecondary` and `MaNumericSecondarySetting` have been written and
working for weeks — inside `DictateLayoutScreen`, which **has no entry in the settings list**. The
only way to reach it was a deep link nobody types.

So he asked for it as a new feature, because from where he sits it did not exist.

> **A feature that compiles, passes its tests and cannot be opened is not a feature.**

Third time this month. Build 369's route crashed for a missing annotation; the Claude.ai reader was
missing from `DEFAULT`; this was built and never given a door. **All three were something not added
to a second place**, and two of the three now have gates — `check_route_deeplink` and
`check_settings_entry_reachable`, which both fired on this one during the build.

### Its own entry, not "Layout"

`DictateLayoutScreen` also holds the prompt-row count and the enter long-press characters, inherited
settings he has never mentioned. Putting all of it in his menu to reach one of them would be three
doors to find one room. **The number row is what he asked for, so the number row is the entry.**

### Punctuation only, and filtered rather than refused

His rule: anything but a number or a letter. He is right, and the reason is worth stating — **the key
already IS a digit, and the letters are two rows below**, so a long press producing "7" or "k" spends
the only spare gesture this row has on something he can already reach.

`sanitize` drops letters and digits rather than rejecting the whole value, so pasting `a/b` leaves the
slash. **The useful part of what he pasted survives**, which is better than an error and an empty
field.

The rule is stated in the editor, where he is typing. A field that silently drops what he types
without saying why is a field he retries.

### Tested

Test 1: 54 checks, 0 failed — thirty punctuation marks accepted, letters and digits in several
scripts dropped, the separator stripped so one field cannot become two, ten slots whatever is stored,
and the whole reachability chain: entry, DEFAULT, icon, route, `@Deeplink`, registration, and the
screen actually hosting the editor. Sabotaged by removing it from `DEFAULT`: red in the test and in
`verify.py`.

## §216a — A property copied from my own broken file

Build 390 went red on `previewFieldVisible`, which `FlorisScreen` does not have.

**I copied it from `MaCloudLogScreen`** — my own screen, where the same line caused the same error and
was fixed in §207a's follow-up. The fixed version is in the repository; the line I copied was the one
I had written from memory the first time, which I then reproduced from memory a second time.

**Copying from a neighbour is the habit this file keeps recommending**, and it works — three sibling
screens are what told me `scrollable = false` was needed. It fails when the neighbour is one I wrote
badly. **"Read a working example" means read one that works**, not one that exists.

A guard was measured and **rejected**: flagging a `FlorisScreen` property used by exactly one screen
found **19 hits**, all legitimate — `numeric`, `symbols`, `prettyPrint` and the rest, from unrelated
builders that share the shape. A check firing on nineteen correct lines to catch one wrong one is
what `check_property_call` was deleted for.

So CI keeps this one, at five minutes a time. Fourth signature-from-memory error in two weeks:
`ThemedIconKey`, `Voice.label`, `scrollable`, and now this.

## §217 — Symbols 2 in one press, and the number row found where he looks

**He still could not find the number row editor** after §216 gave it an entry. Two reasons, both
fixed. Builds before 391 do not have it at all. And on a phone with a saved settings order, `parse`
appended any new entry **at the bottom**, under twenty entries he had arranged: never invisible,
but not where anyone looks. A missing entry is now inserted straight after the entry `DEFAULT` puts
before it — Number row lands after Reader — and the order he chose is otherwise untouched. The
Switchboard's Number row line now opens the editor too, because that row is where he thinks of it.

**Symbols 2 took two presses** (sy1, then sy2). The comma key right of sy1 on the letters panel is
now the Symbols 2 key, with the same three-view hold as sy1. The comma moved to the period's hold
(`main`), which the period already advertised with "," in its corner — before this, holding it
offered only `!` and `?`, so the corner was a promise the key did not keep. The symbol panels keep
their dedicated comma; from either one, the other symbol panel was already one press away.

### Tested

Bottom row, Test 1: 89 checks, 0 failed; sabotaged by restoring the old letters row, 5 red. Number
row, Test 1: 59 checks, 0 failed, including the insertion walked over `DEFAULT` read from source.
Not compiled locally (no Android SDK in the session); CI compiles it.

## §218 — The version line is the door to the next build

Mantra Manifest rule, 10.10.2026: tapping an app's version opens its latest GitHub release, so
updating is tap, download, install. The build line at the top of the settings now opens
`/releases/latest` (not `/releases`: CI keeps two, and the newer is the one he wants).

A tap used to open App info. **Moved to a hold, not removed** — it is the one line that names the
install, and permissions, storage and force stop still need a door.

Test 1: 7 checks, 0 failed; with the change stashed, 6 red.

## §219 — A number row slot is a snippet now

His ask, 10.10.2026: like AutoHotkey, a slot holds anything — a word, a sentence, a command — the
key shows its first character, and the hold types the whole box. **This replaces his own §216 rule**
(punctuation only, 16 characters); he made it and he unmade it.

- `sanitize` keeps letters, digits and line breaks, strips only the separator and `\r`, caps at
  2000. The editor field is multi-line (up to five visible) and says what the hold does.
- `hint` puts the first non-blank code point in the corner, so a leading space still marks the key
  and an emoji is not cut in half.
- The hold now commits the slot **in one `commitText`**. It used to send one key event per
  character: harmless for `_`, wrong for a sentence, because each space was a space key that
  autocorrect and auto-capitalisation could act on mid-snippet, and an emoji went in as two halves
  of a surrogate pair. `commitText` is dictation's path, so undo and the word model treat it as
  written text. Phantom space is decided the same way as for a typed character.

Test 1: 39 checks, 0 failed; with the Kotlin changes stashed, 6 red.

## §220 — Keys you cannot type, on the digit's hold

His ask, 10.10.2026: in the number row editor, holding the digit opens a menu of keys that are not
characters — Enter, Tab, the arrows and the rest — to put into a slot.

**One syntax, not two.** The menu inserts MaMacroSyntax's AutoHotkey brace tokens (`{Enter}`,
`{Tab}`, `{Left}`, `{Ctrl+V}`), the syntax the macro keys already use and he already knows from
AutoHotkey. It inserts at the cursor, so `Regards,{Enter}Marko` is type, hold, pick, type.

**On the keyboard**, a slot containing a token that resolves to a real key runs through
`MaMacroSyntax.run`, so text and key presses arrive in order. A slot whose braces resolve to no key
— a lone `{`, a `{}` — is still committed literally, so a bracket assigned as a symbol keeps
working. A slot starting with a key shows that key's glyph in the corner (⏎ for Enter), not a brace.

Test 1: 65 checks, 0 failed, including every offered token checked against the macro runner's own
key table read from source; with the Kotlin changes stashed, 6 red.

---

# §188 — Always the newest Claude, and the exact message on screen

He asked three things: can the app follow new Claude models by itself, is the Croatian path the best
it can be, and can he see and change exactly what Ctrl+P and Ctrl+F send.

## Models follow Anthropic's list, by tier

`claude-haiku-4-5-20251001` was the shipped default months after Haiku 5.5 arrived, cheaper and
better. And `MaModelHealing` could not have rescued it: its family rule reads `20251001` as the
version, so the family is `claude-haiku-4-5` and `claude-haiku-5-5` never matches.

`MaClaudeModels` stores a TIER — `claude-haiku-latest` — and resolves it against `GET /v1/models`
(read once per process, `limit=1000` so nothing hides on page two). The highest version of that tier
on the list is sent, compared as numbers. A model Anthropic refuse as a model (404/400 naming it,
410) re-reads the list and retries once on the newest of the same tier. A tier is never crossed:
that is a price change, and his. With no list, real fallback ids are used, never a guess.

The old default id stored on a phone is read as the Haiku alias; any other concrete id is his pin
and is left alone. Prompts screen has the choice: newest Haiku (default), Sonnet, Opus, with prices.

## Claude through the Messages API, not the OpenAI shim

The shim cannot send `output_config.effort`, and the 5.5 models cannot switch thinking off, so every
proofread thought at the default depth. `/v1/messages` with effort `low` (the reasoning setting can
raise it) — sent only to models that accept effort; Haiku 4.5 rejects it with a 400.

## Ctrl+P on Croatian carried an order to answer in English

The be-precise system prompt ends *"Output the text in the language of the instruction"*. The
instruction is English. The language line after it said the opposite. That sentence is now dropped
whenever there is text to work on.

The language line itself guessed from the last recording key when the text had no č/ć/ž/š/đ, so a
plain Croatian sentence after an English recording was declared English. `detectPassage` counts small
words (je, se, kako, nije / the, is, and, you); no winner means a line that names no language.

## The exact message, on screen and editable

`MaRewordPrompt.build` makes the message; the keyboard and the Prompts screen both call it, so the
preview cannot drift. *Show exactly what is sent* under each key, with Croatian / English / Unclear.
Both language lines are editable. *Last message sent* shows the real last request and the model that
answered — in memory only, because it holds his text.

## AssemblyAI: universal-2 behind the chosen model

Croatian is not a Universal-3 language. The async job now sends `speech_models: [chosen,
"universal-2"]`, AssemblyAI's own routing; Croatian goes to Universal-2 by their rule. Default async
model is `universal-3-5-pro`, the one on his price sheet and the one Sync already uses (§166, cost
problem 1, closed).

## Tested, and not

`test_claude_models.py` (new), `test_word_language.py` and `test_small_model.py` updated, all suites
green, verify.py clean. The pure Kotlin (`MaClaudeModels`, `MaRewordPrompt.build`, `detectPassage`)
was compiled with kotlinc and run against 33 cases, 0 failed. **The Android build was not run here**
(no SDK in the sandbox), and no request reached Anthropic or AssemblyAI: the sandbox's network
policy refuses `api.assemblyai.com` and the Edge speech host, so the Croatian end-to-end test with
Srečko and Gabrijela could not run.

---

# §189 — Key colours, G815 style, and the command under each digit

His ask: every key on one grid in settings, each in its own colour like a Logitech G815, so the
number row's macro digits can be told apart — and under each digit, small, the command it types,
so he reads it before holding.

## Colours

`MaKeyColors`, one preference (`k97=#C0392B n0=#2E86DE`). Main keys by code, letters folded to lower
case so shift keeps the colour; the number row by POSITION, so a colour stays with the place the
thumb goes when the row switches to Croatian or brackets. Unpainted keys keep the theme.

Drawn through a `backgroundOverride` on `SnyggBox` — inside the margin and the key's shape, over
the theme's background, under the label — plus a text/tint override on `SnyggText` and `SnyggIcon`.
The label colour is chosen by WCAG contrast (near-black or white, whichever is higher): he has low
vision, and a label the colour swallows is worse than no colour. A press darkens the same hue.

Settings → Key colours: pick a swatch, tap keys to paint; tap again or use the eraser to clear. The
grid is read from the layout the keyboard last drew, so it is his real layout with its special keys'
icons. Before the keyboard has been opened in the process, plain letter rows stand in and say so.

## The feature rows too

Every feature-row key drawn through `ThemedKey` — text, icon and action keys — reads a
`LocalMaKeyPaint` the row provides per key, so all of them take a colour without each being edited.
Id by what the key IS (`f:SHIFT`, `f:C3`, `f:M2`), so a key moved between rows keeps its colour.
Keys that draw state themselves (recording, lit, the bucket ring) draw it on top of the paint, so
**colour still means state** where it did. The grid lists the rows as arranged, wrapped at eight,
each key wearing the glyph the keyboard draws (`MaButtonGlyph`, now shared).

## The third line

`MaNumericSecondary.preview`: the whole slot on one line — `{Enter}` as ⏎, line breaks as ⏎ —
printed at 7.5sp, two lines at most, under a digit lifted to make room. Not printed when it would
only repeat the corner (an empty slot or a single character, such as the default underscore).

## Tested, and not

`test_key_colors.py` new (29 checks); every suite green, verify.py clean. `MaKeyColors` and
`preview` compiled with kotlinc and run against 16 cases. **No Android build here**, and nothing
seen on a phone: whether 7.5sp under a 15sp digit fits the row height on the Nothing Phone 2a is
the first thing to look at.

---

# §190 — Croatian spell check, a model per prompt, and what Claude costs

## Spell check, after every Croatian dictation

AssemblyAI's Universal-2 invents Croatian words. `MaSpellcheck` sends every Croatian transcript to
Claude once before it is typed, with one job — put back the word that was spoken — plus Croatian
spelling and punctuation. No rephrasing. Its own switch, on by default (he called it mandatory), not
tied to the rewording switch. A reply more than half again as long or under half as long is refused
and the transcript kept. It counts as a pure transcript for paragraph splitting; the raw transcript
stays the history's original. Wording editable as a named set on the Prompts screen.

## A model per prompt

Ctrl+P, Ctrl+F and the spell check each choose Default / Haiku / Sonnet / Opus — tiers, resolved to
the newest (§188). Used only when the rewording provider is Anthropic.

## Costs, and why the balance is typed in

There is no API that tells an ordinary key its remaining credit; the Admin API reports spend, not
balance, and needs an admin key. So every answered Claude request is priced from the usage Anthropic
returns × the published price and logged (no text, only time, purpose, model, tokens, dollars).
Settings → Claude costs: today, this month, the log, and "left" = the Console balance he types in
minus everything this phone spent since. A model newer than the price table is priced as its tier's
newest and marked `~`.

## Tested, and not

`test_spellcheck_costs.py` new (24 checks); all suites green; verify.py clean. `MaClaudeCost`
compiled with kotlinc against an Android stub and run against 9 cases. Whether Haiku is good at
Croatian mishearing is the open question: if it misses, choose Sonnet for the spell check.

---

# §191 — The Mac's style model on the phone, and no more "Default"

## What he asked

MARKO_TRANSCRIBE on his MacBook styles every message with a language model on the Mac
(`server/styled.py`: Qwen3 8B, 4-bit, MLX; Qwen3 4B Instruct 2507 as the fast one). He asked for the
same model here, on the phone, downloaded by the app, to restyle and correct what he dictates. And
for "Default" to go from the model choice: he wants to see which model is in use.

## Is it feasible: yes, with the 4B

MLX runs only on Apple chips, so the phone runs the same weights in llama.cpp's format (GGUF):

| Model | File | Fits the Nothing Phone 2a |
|---|---|---|
| Qwen3 4B Instruct 2507, Q4_0 (unsloth) | 2.38 GB | yes, the one to use |
| Qwen3 8B, Q4_K_M (Qwen) — the Mac's current model | 5.03 GB | only the 12 GB phone, about twice as slow |

Measured on 20.10.2026 with the real bridge (`ma_llm.cpp`) built for a 4-core x86 cloud machine and
the Mac's six artificial-test messages: first message 15 s to read the rules plus 6 s to write;
from the second message on the rules come from the cache (426 of ~500 tokens reused) and a message
takes **2.3–3.3 s to read and 6–8.5 s to write**. The phone's two fast cores are in the same range;
**not measured on the phone**. The Mac does it in 2–4 s.

Quality on the same messages, as on the Mac's 4B (test/COMPARISON.md): repetitions and filler went,
"Grok" became Groq, "Amarco" became Marko. But hr3 **dropped "Marko je rekao"**, the exact fact the
Mac's 4B dropped, en1 turned "bring the hard drive, the SSD" into "bring the SSD to the hard drive",
and Croatian spelling stayed ("u jutro", "klient"). That is the model, not the port: the Mac moved to
the 8B for this reason, and the 8B is the one that does not fit an 8 GB phone.

## How it is built

- **llama.cpp b11400** (5.10.2026), fetched by `tools/fetch-llama-cpp.sh`, pinned by tag AND commit,
  compiled by CMake into `libmallm.so` with the bridge. Static, no OpenMP, no KleidiAI (a second
  download at configure time). `armv8.2-a+dotprod+fp16`: dotprod doubles the speed of a 4-bit model
  and every arm64 phone of six years has it; **i8mm is left out on purpose**, a phone without it
  would crash instead of running slower. Always built Release: a debug llama.cpp is ten times slower.
- **Its own process.** `MaLlmService` runs in `:llm`. A 2.4 GB model and a native engine next to the
  keyboard would mean that running out of memory, or a crash, ends the keyboard. Here it ends one
  style pass. `FlorisApplication.onCreate` returns at once in that process: two processes on one
  preference store would corrupt it, and `init` empties the cache directory.
- **Bound while he dictates, released after five idle minutes** (`MaLocalLlm`). The stop key cancels
  the request between two tokens. If the process dies, the waiting request fails with a sentence and
  the dictation keeps its text.
- **The rules are read once.** The bridge keeps the last prompt's tokens and recomputes only what
  follows the longest shared prefix. Every style request starts with the same rules, so that is most
  of the waiting saved.
- **Greedy**, as on the Mac (`temp=0`): the same message gives the same result. Room for the answer:
  three tokens a word, never fewer than 64. Qwen3 8B is told not to think with the empty think block,
  which is what `enable_thinking=False` does on the Mac.
- **Downloaded like Parakeet**: `LocalModelDownloads`, foreground service, size and SHA-256 checked,
  installed only when whole. Free space is checked first. Source: Hugging Face (unsloth for the 4B,
  Qwen for the 8B), Apache-2.0.

## The style pass

`MaPhoneStyle`: the Mac's RULES word for word and its standing corrections (Marco → Marko, Grok →
Groq, "a Marco" → "and Marko") before and after the model. **After every dictation, both languages,
on by default, inert until its model is downloaded.** It runs before the Croatian spell check and,
when it succeeds, the spell check is not sent too: it fixes the same misheard words and spelling.
When it fails, or its reply is refused (blank, under a third or over 1.6× the length), the spell
check runs as before, so a dictation is never left with neither. A dictation over 350 words skips
the phone model (it writes six to eight words a second, so ten minutes of speech would hold the text
for minutes) and gets the spell check; Claude has no such limit. Its wording and its model are on the
Prompts screen like the others; choosing Haiku there sends it to Claude instead.

Every prompt can now choose a phone model: Ctrl+P, Ctrl+F, the spell check and the style pass. A
phone request needs no key, goes nowhere, and is recorded for "Show exactly what is sent" the same
way. It is split into the rules (system message) and his text; the language rule is still the last
thing before his words.

## "Default" is gone

"Default" meant "whatever the rewording account is set to" and the screen never said what that was.
Now a prompt's model is always a name. `MaPromptModel.nameBlankModels()` writes the account's model
into every empty per-prompt model at start, the chooser does it again if it sees one, and the
getters never return null. Under each choice: **"In use: claude-haiku-… (newest Haiku)"** or **"In
use: Qwen3 4B Instruct 2507 on this phone, downloaded"**, in red with what to do when the phone model
is not downloaded. The "(default)" label on the Claude tier list went too. The built-in prompt
*wording* is still called Default: that is a wording, not a model, and renaming it would orphan the
stored sets.

## Tested, and not

`test_phone_model.py` new (43 checks); `test_spellcheck_costs.py` now expects four model choosers;
every suite green, verify.py clean. The bridge ran for real on x86 (above). The arm64 library and the
whole release APK were built here with the CI's command. **Nothing was run on a phone**: the speed
on the Dimensity 7200, whether the 8 GB phone keeps the `:llm` process alive with the keyboard and
the reader running, and how the first, slow message feels are the first things to look at.

---

# §192 — aria2 inside the app, and one copy of each model in Download/models

Merged with §191 (the phone model, from the other session) and its resumable download
(`phone-resume-download`), as Marko asked on 20.10.2026.

## Downloads in pieces

Hugging Face's CDN reset the connection twice in the first 70 MB on his phone; aria2 with eight
connections fetched the same 2.4 GB at 2.6 MB/s. `MaFileDownloader` does the same: a file of 64 MB or
more, from a server that answers a `bytes=0-0` probe with 206, comes down as 8 MB pieces on eight
connections, each written at its offset into one file of the final size. A piece map beside it
(`<file>.pieces`, written atomically) says which pieces are whole and how far each unfinished one got,
saved after every piece and every 2 MB — so a drop, an app kill or a restart fetches only what is
missing, continuing inside a piece rather than from its start. A piece retries with 2 s … 60 s backoff
and the download stops only after eight tries in a row on one piece bring nothing. A server that
ignores Range gets the one-connection resume from §191. Size and SHA-256 are checked from disk at the
end; Cancel deletes the file and its map, any other failure keeps both. Progress is the sum of every
piece. A partial file left by the one-connection code carries on from the pieces it already holds.

The engine has no Android in it, so it was run under plain Kotlin against a local server serving
100 MB with dropped connections and a kill halfway; the results are in
`scripts/test_parallel_download.py`. The piece arithmetic (`MaPieces`) was run against 14 cases.

## One copy, in Download/models

`MaSharedModels`: the phone models live in `Download/models/` under Hugging Face's names
(`Qwen3-4B-Instruct-2507-Q4_0.gguf`, `Qwen3-8B-Q4_K_M.gguf`), shared with aria2 and anything else.
A file there at the exact size is used at once — on every start, so also after an update — with no
download and no question; its SHA-256 is checked once in the background and remembered by path, size
and modified time. A wrong size or hash means not downloaded, with the reason in red. Downloads go
there too (`<name>.part`, renamed when whole). It needs All files access, asked once from Settings →
Prompts → Models on the phone, saying why; without it the app's private folder is used as before.
Delete says it deletes the shared file.

## Built here first

The Android SDK, NDK 29 and CMake 3.31.6 were installed in the sandbox and the whole app, llama.cpp
included, was built with Gradle before anything was pushed.

---

# §193 — Switches first, models and wordings under them

Marko, 21.10.2026, on build 399's Prompts screen: "Croatian spell check" and "Style after every
dictation" sat below lists of models. "These switches which are basically disabling and enabling
options should be at the top … What is global should be up." Written into MANTRA_MANIFEST as
`mantra-design-language.md` §1.9, for every settings screen of every app.

Here: both switches in one block at the top of Settings → Prompts (`FeatureSwitch`), then the
models on the phone, then each prompt's wording and model in the order of the switches (style,
spell check, Ctrl+P, Ctrl+F). `test_spellcheck_costs.py` fails the build if a switch moves below
a model or wording list. Also in build 399's follow-up: radio buttons with exact model names and no
"Default" anywhere (§192 follow-up, commit cda6161).

---

# §194 — Key colours light the letter, not the key

Marko, 21.10.2026: "you understand me wrongly when I say color the buttons, I meant color the
letters itself. So each letter and each symbol have its own color, similar as … Logitech G815."
§189 painted the keycap. Now the keycap keeps the theme's colour everywhere (letters, number row,
feature rows) and the colour lights the legend: the letter, symbol or icon, the corner mark and the
command under a number-row digit. The palette is bright (each colour has a channel of at least 128)
because it lights a letter on a dark key; the G815 itself shows nothing below about 45 of 255
(MANTRA_MANIFEST `logitech-g815.md`). Settings → Key colours draws dark keycaps with lit letters,
as the keyboard will. Stored colours are unchanged; a dim colour chosen before still works.

---

# §195 — Switches first on every settings screen

Marko, 21.10.2026: yes, every screen follows §193's rule (MANTRA_MANIFEST
mantra-design-language.md §1.9). An audit of every screen reachable from settings found eight that
broke it; each now has its on/off switches together at the top and the lists, sliders, pickers
and dialogs that adjust them underneath, in the same order:

- Recording: Bluetooth mic, keep screen awake, skip silent recordings, trim silent gaps, instant
  recording moved above Longform, audio source and animation.
- Output: resend button, haptics, remember last dictation above the two sliders.
- Magic finger: Voice commands beside "Show the magic finger row", before the explanation.
- Floating button: all seven switches above design, size and colour.
- Gestures: Swipe to symbol at the top (its two thresholds stay under Other).
- Clipboard: clipboard history beside the internal clipboard; in the history group the six
  switches above the four sliders.
- Keyboard: popups, merged hint popups, space bar returns to letters beside the utility key.
- Typing and Smartbar: the switches in a group above that group's lists.
- Learn my words: its switch was only on a layout screen with no settings entry; it is now at the
  top of the Learn my words screen.

`scripts/test_switches_first.py` (68 checks) pins each of these orders.

## §221 — The icon is the keyboard, lit

Marko, 21.10.2026, with a screenshot of the keyboard: the app icon in the Mantra Shruti Box style,
"instead of buttons of the Shruti box, there will be buttons of this keyboard … each button has
different color … glowing nice style".

- Dark keycaps on black; each legend lit in its own colour inside a halo, as the Shruti valves
  glow and as Settings → Key colours lights the letters (G815): A, B and C (red, gold, cyan), lit tubes, over
  a violet microphone and a green space bar.
- One source: `design/make_app_icon.py` writes the vector templates in `app/src/main/icon/` and an
  SVG preview (`design/app_icon.svg`). Keycaps span 42 of the 72 mask (0.583, app-icon.md).
- The build number is drawn under the keys (version-visible.md): `app/build.gradle.kts` writes the
  real drawables into `build/generated/versionIcon` with `vN`, N = versionCode − 1000 = build-N.
- The themed icon is the same keys in outline with filled legends. The notification icon stays the
  plain microphone, now `ic_notification_mic`. The old bitmap foregrounds are gone.

## §222 — It learns from his corrections

Marko, 25.10.2026: "please confirm whether my application is tracking my corrections and also … correcting
the suggestion towards them". Typing already
learned a typo retyped on the keys (22.10.2026); dictation did not, and nothing knew that a dictated
"Amina" had become "Emina". Now (`MaDictationCorrections`, `MaCorrectionRules`):

- **Watched.** Each dictation into the keyboard's own field is remembered with where it went in
  (`MaDictationCorrections.noteDictation`, read before the commit). Not in password fields, not in incognito.
- **Compared.** When the next dictation arrives, before Ctrl+P / Ctrl+F rewrite the text, and when the field
  closes (`onFinishInput`, not when the keyboard merely hides), that stretch of the field is read again and
  compared word by word (`MaCorrectionRules.diff`, longest common subsequence). One to three words replaced by
  one to three words, inside unchanged text, is a correction; more than half changed is a rewrite and teaches
  nothing. A capital added to a word mid-sentence counts ("emina" → "Emina"); punctuation does not.
- **Counted.** Fixed once is counted; fixed the same way twice it is in use (`ACTIVE_AT = 2`), per language.
  A different fix of the same word starts the count again; fixing a rule's output back switches the rule off.
- **Used.** Rules in use are applied to every new dictation, whole words, any case, the longest first, never
  doubling a form already right ("DaVinci Resolve" stays). Their right forms join his word list for the
  speech model's prompt. The typing side learns it too (`MaNgram.learnFix`): the wrong words leave the
  personal model, and a one-word fix joins the store the keys already learn typos into (22.10.2026,
  `MaCorrectionWatcher` / `MaCorrectionStore`), so typing the wrong word offers the right one first.
- **His.** Settings → My corrections: the switch first, then "Add a fix" and "Add my replace list" (his
  ALWAYS REPLACE list, 26 fixes; "Cloud" on its own left out because it is Claude only when it means the
  AI), then the rules in use, being counted, and switched off, each with its own switch and Delete.
- Local and free: one file, `ma_dictation_corrections.tsv`; the log says how many were learned, never which.
- Typed text is the typing side's (a typo retyped on the keys, 22.10.2026); this watches dictations. Not
  covered: a fix to the very last word of a dictation with nothing after it to anchor it (a full stop is
  enough).

Tests: `MaCorrectionRulesTest` (kotest, 23 cases), `scripts/test_dictation_corrections.py` (34 checks).

Also from the Pixel 7 report on build 403: Smartbar and Typing put their switches first (Layout and Incognito
moved under them); a cost under a hundredth of a cent shows six decimals ($0.000040, not $0.0000); "1
request"; the "Sent to Claude" block no longer names the account's model as if every key used it; the
download bar's track is faint, so 0 % no longer looks nearly full.

## §223 — Auto prediction: one entry for everything about the suggestion row

Marko, 27.10.2026: "Everything related to it should be under the auto prediction menu entry, including the
AI model we are using … model switching and all the other settings related to word prediction should be
under that one menu item only."

It was on five screens. Now all on Settings → **Auto prediction** (the entry once called Word predictions,
renamed to his word for it), switches first:

- the switches: show the suggestion row, next-word prediction, auto-correct, multilingual typing, Learn my
  words, the AI key in the row (new, `maAiPredictKey`, on), inline suggestions (Android 11+);
- how the words show (the display mode, from Smartbar);
- **AI key model** (new, `maPredictModel`): a radio list with the real names (Claude Haiku 5.5, Sonnet 5.5,
  Opus 5.5), stored as the tier so the newest is used; starts at Haiku. It is paid with the Anthropic key
  loaded in the app, and the screen says whether one is loaded (never the key). `askPredictionModel`
  serves the AI key and the sorting of new words into a language; other jobs keep `askCheapModel`;
- what was learned, per language, with forget (MaNgramSetting, from the Dictate screen), and "Teach it
  from my dictations" (the old Learn my words screen, now without its own settings entry).

Taken off Typing (the Suggestions group), Smartbar (display mode), Dictate (Learn my words) and the
settings list (Learn my words). The search index points the moved settings at the new screen.
`scripts/test_auto_prediction.py` fails the build if a prediction setting appears on any other screen.

## §224 — Auto prediction gets My corrections, a big A, and a place he can find

Marko, 27.10.2026: My corrections, "for when I manually enter the word to be replaced", goes under Auto
prediction too; and Auto prediction "should be there, but it is missing … the icon can be a big A".

- **My corrections inside Auto prediction.** Its switch ("Learn from my corrections") joins the switches at
  the top; the screen itself (heard → write, and the learned fixes) opens from a row there, with how many
  are in use. It has no settings entry of its own: a stored order holding `corrections` puts Auto
  prediction in its place.
- **The big A.** The entry's icon is `FontDownload` (an A in a square), already resolved in this app.
- **Where he looks.** It was the last entry of all, under twenty others. DEFAULT now puts it after Claude
  costs, and a one-time migration (`movePredictionsBesidePromptsIfNeeded`, `maPredictionsMovedV1`) moves it
  there in his stored order; after that the order is his again.

## §225 — The model on top, and a prediction row that is never hidden

Marko, 27.10.2026: "At the top, I want to have the radio buttons for the models. I need the settings so the
prediction row always stays on screen. It should never be hidden."

- **Auto prediction order:** the AI key model (Claude Haiku 5.5 / Sonnet 5.5 / Opus 5.5) first, then the
  switches, then the display mode, what was learned, My corrections and Teach it. His named exception to
  switches-first, for this screen.
- **Never hidden.** Every way the row could go is gone: "Show the suggestion row" (Auto prediction), the
  Suggestion row switch (Switchboard), "Enable Smartbar" (Smartbar), the Smartbar toggle key, the Sg key and
  a hold on the language badge (both now only make sure it is on), and the row vanishing when it had no
  words (`maSmartbarHasContent` is always true; the drawing and the height still ask it together).
  `keepPredictionRowOn` switches the three stored flags back on at every start, so an older install or a
  restored profile comes back with the row. The recorder still borrows the row while recording, the one
  exception he asked for earlier.
- **From the build 421 phone test:** plain names on the switches (Next-word prediction, Auto-correct,
  Multilingual typing, Inline suggestions); the key line says whether an Anthropic key is loaded; the new
  items are in settings search; the AI words clear the moment the text before the cursor changes (typed,
  deleted, emptied), and an answer that arrives after he typed on is dropped; the AI key's label is centred.

## §226 — Auto-hide as a choice, and suggestions in the language he types

Marko, 27.10.2026: "The prediction row is always on screen. It can be black when there is no text, but
having it come up and down is annoying. So auto hiding should be a setting … I would like the app to detect
whether I'm typing in English or another language … just English or not English. If I'm typing something
that is not English, it should give me Croatian suggestions."

- **Auto-hide the prediction row** (`maPredictRowAutoHide`, off): right after the model on Auto prediction.
  Off, the row is always there and dark when empty (`maSmartbarHasContent` returns true); on, the old
  coming and going with its content.
- **Detect typing language** (`maDetectTypingLanguage`, on), next to it. `MaTypingLanguage` reads the last
  eight finished words before the cursor: č ć ž š đ settle it; English small words against Croatian ones
  (words both languages have, like i, a, to, on, count for neither); a word only one personal model knows;
  a word neither knows leans Croatian a little, so "not English" is Croatian but one typo in an English
  sentence is not. No evidence keeps the last answer; a new field starts from the badge. It moves only the
  SUGGESTIONS: the personal model asked and learned into, the shipped dictionary (the installed subtype of
  that language, both preloaded), the Croatian base list and the AI key's language. The badge, the
  keyboard layout and the dictation language stay where the HR/ENG key put them. Off: the badge, as before.

Tests: `MaTypingLanguageTest` (kotest, 11 cases), `scripts/test_auto_prediction.py`.

## §227 — The copied line: centred, and a tap pastes it

Marko, 28.10.2026, with a screenshot of WhatsApp: the "copied https://…" line in the prediction row "should
be in the vertical middle … not at the top", and "I press on it and it pastes into the chat or wherever I
am".

- The line (and the bucket legend) fill the row's height, so `CenterVertically` puts them in the middle.
- A tap commits the clipboard's text at the cursor, line breaks included, replacing only a selection. Not
  the buckets' select-all paste: the clipboard is not emptied and nothing he wrote is selected away.
- From the build 423 phone test: the suggestion row scrolls back to its first word whenever the words
  change, so the first AI word no longer sits half under the badge ("ello").

## §228 — Pasted, the copied line goes; My corrections first

Marko, 28.10.2026: "once it is pasted, it disappears from the screen" and "please put my corrections at the
top of the … autocorrection menu, so i can always reach it first".

- A tap on the copied line pastes it and the line leaves the row (`MaCopiedLine`, the copy's timestamp), so a
  new copy, even of the same text, shows again. The clipboard is untouched.
- Auto prediction: the My corrections row is first, then the model radio buttons, then the switches.

## §229 — E>H: dictate in English, write Croatian

Marko, 2.11.2026: "I dictate my message in English and then Anthropic is translating and applying my style
of communication … a red dot … an E … greater than … H."

- **The key** `RECORD_EN_HR` ("English to Croatian"), drawn like E and H: a red dot and "E>H". It sets English
  and records, exactly as E does, and marks the recording (`translateRecording`, set at every start so any
  other way of recording clears it). Only the key that started a recording rings.
- **The step** `translateToCroatian` (MaTranslate): the English transcript goes to Claude once, with the
  Anthropic key loaded in the app, to be translated into Croatian and written in his style (his style rules,
  in Croatian). It replaces the style pass and the Croatian spell check rather than running on top. A
  failure, or a reply that is not a translation (`MaTranslate.accept`), keeps the English and says so.
- **Editable** on Settings → Prompts ("E>H — English to Croatian, wording and model"), Haiku by default.
- **On his keyboard**: `addTranslateKeyIfNeeded` puts it right after the E key on every row that has one
  (else after H), once; after that it is his to move or remove in Feature row.
- From the build 425 phone test: copying the same text again brings the copied line back (any copy clears
  the pasted mark, through Android's clip-changed listener).

Test: `scripts/test_translate_key.py` (24 checks).

## §230 — One line break between paragraphs; E|H stays Croatian

Marko, 3.11.2026, with a WhatsApp screenshot: "When I press reflow or whenever this Sonnet is correcting my
text, it is adding the spaces between paragraphs … It should add only new line without any space in
between. … E to H is not working. I press that and then I get English text."

- **Paragraphs** (`MaParagraphs.single`): every run of empty lines becomes one line break, on everything
  the app writes into a field: dictation (`commitOutput`), the realtime final text, and Ctrl+P / Ctrl+F.
- **E|H**: the translation was followed by the auto-formatting pass, which names the language setting to
  the model, and E|H sets English, so the Croatian came back as English. A translated message now skips
  the formatting and auto-apply passes (it is already finished), and single-call audio is translated too.

Test: `scripts/test_paragraphs_translate.py` (12 checks).

## §231 — E|H: the real cause, from the phone's log

The Nothing Phone session read TTT's log of his E|H dictation (build 1434): `E>H reply refused: 701 chars for
38`. No formatting pass ran (the §230 change is still right, but it was not this). The 38-character English
line sat bare under the instruction, so the model took it as a request and wrote a whole new message; the
length guard refused it and the English was pasted.

- The dictation is now fenced: `MaTranslate.message` puts it between `<english>` tags with a rule that it is
  his message to translate, not a request to answer, and asks for the translation only.
- A reply still refused gets one second try with the bare job (`strictMessage`), then the English with a
  message saying no translation came back.
- `accept` allows short lines to grow ("Ok" → "U redu") but still refuses 701 for 38.
- The prompt's style section says one line break between paragraphs, never an empty line (§230).

## §232 — Reprompt: "re" and a sparkle, an instruction, the field rewritten

Marko, 4.11.2026: "Once the message is written, there should be a button … Reprompt. You can just put re
on it with small magic symbol. And then I press the button and then the dialog open up which I'm giving
instruction to Anthropic what to do with the text which is in the box and it can directly modify it."

- **The key** `REPROMPT` ("re" + a small AutoAwesome sparkle), put once on his rows right after E|H
  (`addRepromptKeyIfNeeded`). An empty field says "write something first".
- **The window** `MaRepromptActivity`: at the bottom over his app, the keyboard open on one line, so the
  instruction is typed or dictated with •E / •H; his last six instructions under it, one tap each; Apply or
  Enter submits, Back or Cancel rewrites nothing.
- **The rewrite**: a keyboard cannot type into itself, so the key remembers the field (app + field id) and
  `FlorisImeService.onStartInputView` runs the instruction when that field is back (`MaReprompt.takeFor`):
  through `applyPrompt`, the same path as Ctrl+P and Ctrl+F (the selection, else the whole field, replaced
  in one go), with Ctrl+F's model, and the length guard off (a reprompt may ask for more text). Another app
  first, or more than a minute, drops it.

Test: `scripts/test_reprompt.py` (25 checks).

## §233 — Hidden code words: the first is the Yshai Afterman style

Marko, 4.11.2026: "Please hardcode right inside application Ashai Afterman style … when I'm reprompting,
when I refer to Ashai Afterman or something similar, if there is misspelling, it will recognize from the
code. And this is first of our hidden code words … don't make it all small caps, normal sentence caps, but
style must be there."

- `MaCodeWords`: a list of code words, each with the spellings it answers to and a full description.
  A Reprompt instruction is read for them (`MaReprompt.instructionFor` → `MaCodeWords.expand`) and each one
  named brings its description into what Claude is sent.
- Spelling: Croatian letters folded (Jišai → jisai), one edit for short keys (yshai ← ashai, ishai, jshai),
  two for long ones (afterman ← aftermen, aftreman), "after man" joined only when the second word is
  man/men, and real words that are near (aftermath, shah, shai) never count.
- The Yshai Afterman description comes from MANTRA_MANIFEST modules/writing-styles.md §2 (two of his
  letters), with normal sentence capitals and correct apostrophes, never all lowercase.
- The next code word is one more entry in `MaCodeWords.ALL`.

Tests: `MaCodeWordsTest` (kotest), `scripts/test_code_words.py` (32 checks).
- (same day) **Claude reads the list too.** Marko: "for some unknown terms … this application must point AI to
  look at our code words, and then it will find it". Every Reprompt now carries the whole code-word list with
  descriptions and a rule: an unfamiliar term, or a misspelled or misheard form of a name, means that code
  word; otherwise ignore the list. A code word the app's own spelling match caught is named first.

## §234 — Reprompt on an empty field writes the message

Marko, 4.11.2026: "If message is empty, then it goes to the prompt mode and I'm going to tell him what kind
of message to write. If there is a message, then it's as before … reshape the message."

- The key opens the window on an empty field too (no more "write something first"); the window asks
  "The field is empty: what message should Claude write?".
- When the field is back, it decides: empty → `MaReprompt.writeInstructionFor`, the request fenced in
  `<request>` tags, its language named (detected from his words: Croatian stays Croatian), the code words
  listed; inserted at the cursor (`requiresSelection = false`). Text → reshaped as before.

Tests: `MaRepromptTest` (kotest, 4), `scripts/test_reprompt.py` (29 checks).

## §235 — AI words while typing: Haiku in parallel with the local engine

Marko, 4.11.2026: "Add feature to always do parallel to word prediction using Anthropic Haiku, parallel to
local engine, both displayed at same time."

- `maAiLivePredict` (on), a switch on Auto prediction: "AI words while typing (Haiku)".
- `CandidatesRow`: after a pause in typing (`LIVE_PAUSE_MS` 450 ms), Claude Haiku (always the newest Haiku,
  `askLiveModel`, whatever the AI key's model is) is asked for three words for the text before the cursor,
  in the typing language. `collectLatest` drops a question when the text moves on; the words of an older text
  are cleared at once.
- Shown after the local words, behind a small sparkle, never instead of them; a word the local engine already
  shows is not repeated; shown even when the local engine has nothing. A picked Haiku word is taught to the
  personal model, like the AI key's.
- Never in incognito, in a password field, with no Anthropic key, or while the AI key's own words show.
- Cost: one small Haiku request per pause, each in the Claude costs log as "Live words".

Test: `scripts/test_live_ai_words.py` (17 checks).

## §236 — Haiku teaches the local engine as it goes

Marko, 4.11.2026: "Haiku should improve local prediction as we go, using its database to better predict
words itself."

- `MaTeacherMemory` (per language, on the phone, `ma_teacher_<lang>.tsv`): every live Haiku answer is kept
  under the two words before the cursor, newest first, six words a context, 3000 contexts, least recently
  used dropped first.
- `MaNgram.predict` offers those words itself: after his own words and corrections, before the shipped
  Croatian list, continuing the prefix, in the case he types. So the local row learns from Haiku even on a
  fresh install.
- The live effect asks Haiku only where the memory has fewer than three words for that spot, so the more he
  types, the fewer requests (and the less cost).
- A soft layer, never his counts: a guess is not a word he wrote. Only what he writes or picks enters the
  n-gram model. Cleared with "forget" like the rest; not in incognito; not with Learn my words off.

Tests: `MaTeacherMemoryTest` (kotest, 4), `scripts/test_live_ai_words.py` (23 checks).

## §237 — Reprompt sent bare; its buttons always visible (Mac emulator test, build 440)

The Mac session tested build 440 in the Android emulator with his key loaded there: 6 pass, 1 fail.
- **Fail B:** "make it shorter" came back only proofread. The instruction went through the rewording path,
  which adds the proofreading system prompt and a language line after it, and those outweighed his words.
  Reprompt is now sent bare (`applyPrompt(bare = true)` → `MaReprompt.message`): his instruction ("do exactly
  what it asks … do not just proofread"), his text fenced in `<text>` tags, the language of the text named,
  and "return only the resulting text, single line breaks". Phone models keep the old path.
- With the keyboard hidden and three instructions listed, Cancel and Apply slid under the navigation bar:
  they now sit right under the line, before the list, and the window keeps clear of the navigation bar.
- Noted, not changed: the first Back only hides the keyboard (Android's own behaviour); loading keys left
  TTT's copy in the emulator's Documents/TTTmini/keys.txt (the app's key vault, as on a phone); fast adb
  typing once turned "for the" into "fort he" (autocorrect, to watch).

## §238 — Tapping a suggestion typed an older word

Marko, 4.11.2026: "tapping on words in suggestion row is typing wrong word" ("since since since … also
also"). `CandidateItem` installs its gesture once per slot (`pointerInput(Unit)`), so it kept calling the
first `onClick` that slot was given; when the row's words changed, a tap committed the word that slot showed
before. Now the gesture reads `rememberUpdatedState(onClick)` / `(onLongPress)` / `(longPressDelay)`, so
it always commits the word on screen. More visible since the live Haiku words make the row change more
often. Check: `scripts/test_live_ai_words.py`.

## §239 — The feature-row colour language, made consistent

Marko, 4.11.2026, with a screenshot of the feature row: "record buttons are dark red, when recording light
red; buttons showing other rows have grey outline, when selected green. In this example F2 should have grey
outline but one is missing. Please go through all buttons."

- Record keys (E, H, E|H, Record): dark red outline (`MaRecordOutline`), light red (`MaRecordOutlineLive`,
  #E8776C) while that key is recording. They no longer turn green.
- Keys that show a row (F1–F6, #, the keys, zone 3, reader row, bucket row): grey outline (`MaSwitcherRingOff`)
  when the row is hidden, green when it shows. F1–F6 had no outline when off: fixed.
- Switches use the same pair: the volume keys switch had no outline when off: fixed (Claude.ai reader and
  reader watch already did).
- Unchanged: green on a full C bucket (state, not a switch); keys that only act (history, settings, re✦,
  undo…) have no outline; the transcribe view's big record button hides its ring while recording on purpose
  (the meter is drawn there).

Test: `scripts/test_colour_language.py` (13 checks).

## §240 — Reprompt by voice, in English; the record outline on its key

Marko, 5.11.2026: "action button re-prompt is not have its outline … when I click on re-prompt … it's
automatically running audio recorder. So every prompt I'm recording through audio and transcription, and
it's always in English language by default."

- A press on re✦ sets English and starts recording (`onMicClick(reprompt = true)`); a second press sends it.
  `finalizeAndCommit` sees `repromptRecording`: nothing is typed (a streamed preview is taken back), the
  transcript goes to Reprompt's list and is applied to the field: reshaped when there is text, written at the
  cursor when the field is empty (bare, with the code words), as with the window.
- It records, so it wears the record outline: dark red, light red while it is the one recording; the E key
  stays dark meanwhile.
- A long press still opens the typing window.

Checks: `scripts/test_reprompt.py` (42).

# AUTO: nothing changes his text unless he switched it on (9.11.2026)

**Bug.** Typing AT2020 on Amazon lost a 2. In `KeyboardManager`, any non-alphabetic character, a digit included, committed the
auto-correct candidate for the word before it. "AT" had a fix, so pressing "2" replaced the composing word and the digits
typed so far went with it. **Fix:** a digit never ends a word; `NlpManager.getAutoCommitCandidate` returns nothing when the
current word contains a digit or when Auto-correct is off; the provider offers no corrections for a word with a digit.

**Rule (his):** no automatic text replacement unless switched on in AUTO. Auto-correct, auto-capitalise, auto-space and
double-space-period are off by default (fresh install) and were switched off once on existing installs
(`applyAutoOffV1IfNeeded`, guard `dictate__ma_auto_off_v1_applied`). One screen holds them: toolbar key AUTO
(`KeyCode.MA_AUTO` -250, first in the panel, also put first in an older saved arrangement) and Settings, AUTO corrections
(`MaAutoScreen`, route `settings/dictate/auto`). Every new automatic behaviour gets a switch there in the same commit.
**Dictation fix-ups he asked for by name** (spoken fix-ups, text replacements, learned corrections, phone style, AI spell
check, Markdown cues, auto Enter) keep their state but each has its switch under "On dictation". Icon: white square with
the word cut out, `tools/make_auto_icon.py` (MANTRA_MANIFEST modules/six-letter-icon.md).

**AUTO v2 (9.11.2026, his principle, answered by main):** dictation fix-ups are off too: spoken fix-ups, text replacements, learned
corrections, phone style, AI spell check (it rewrites, there is no underline-only mode), Markdown cues, auto Enter. Fresh
installs by default, existing installs once by `applyAutoOffV2IfNeeded` (guard `dictate__ma_auto_off_v2_applied`). Each is its own
switch on the AUTO screen. Supersedes the "dictation ones keep their state" paragraph above.

## RE Magic skill and the learning loop (9.11.2026)

**Asked (momentaryupdates.md, 9.11.2026):** RE's icon in settings with a skill editor; RE transcribes with AssemblyAI only; it
writes by a skill file that is not shipped with the app, lives on the Mac (`~/Downloads/API/re_ttt_skill.md`), is pushed to the
phone over Wi-Fi, is readable in `Documents/TTTmini/`, and learns from every correction he makes before sending.

**What was built.** `dictate/MaReSkill.kt` (the file, the draft, the comparison, the learning); `MaReprompt` puts
`MaReSkill.preamble()` in front of both prompts (reshape and write), read fresh from disk on every use (Documents first, a private
copy second, a short built-in voice if neither exists). The RE key calls `onMicClick(reprompt = true, engine = ENGINE_ASSEMBLYAI)`;
`MaProviders.localFallbackProvider` already returns null for that engine, so there is no Parakeet fallback. Settings gets an entry
"RE Magic" (`MaSettingsEntry.RE_MAGIC`, route `settings/dictate/re`) wearing `MaReIcon` (the key's "re" and the AutoAwesome sparkle),
opening `MaReScreen`: the learning switch (`maReLearn`, on), the skill as an editable Markdown text with Save / Reload / Reset rules
(Reload = take what the Mac pushed, Save = leave it where the Mac pulls from), counters and the last corrections.

**The learning loop, and why these two methods.** The task offered: press Send for him after copying, the IME's own send action,
an accessibility click watcher, or the field being cleared. Pressing Send for him was rejected: it delays or risks his send and an
AccessibilityService that clicks is a different permission class. The IME's send action fires only in apps whose own button is the
IME action, which is a minority (WhatsApp, Gmail, Telegram use their own buttons). So the loop is read-only and has two triggers,
whichever comes first, one draft at a time:
- **A. Send click** (`DictateAccessibilityService.noteSendClickForSkill`): while an RE draft waits, a `TYPE_VIEW_CLICKED` on a node whose
  text, description or view id is "send" (also Pošalji, Submit, Post, Reply, ...) reads the focused field's text and hands it to
  `MaReSkill.onSendClicked`. Nothing is clicked. The click event arrives with the click; if the app has already cleared the field, the
  last text the keyboard saw (B) is used.
- **B. Field cleared** (`FlorisImeService.onUpdateSelection` -> `MaReSkill.noteFieldText`): the keyboard remembers the field's text while a
  draft waits; a field that held at least 12 characters and is empty in one step is a sent message. A cut or select-all-delete looks the
  same; the model is told to answer NONE when the sent text is not the same message.
Only fields where RE wrote are compared (same app package, 30 minute window, newest draft only). Same text: counted as "sent as
written". Different: a line goes to `Documents/TTTmini/re_corrections.jsonl` and a background prompt (`DictateController.askModel`:
the model RE uses, else the cheapest) returns ONE rule or NONE, merged into section 7 of the skill (dedupe, 40 rules maximum, merged by
the model beyond that). Nothing leaves the phone except that prompt to the API he already uses.

**Sync.** `re-skill-sync push|pull|status [-s SERIAL]` on the Mac (adb over USB or Wi-Fi); registered in TERMINAL COMMANDS.md.

**Not proven yet (the honest list).** The send-click label match has only been written, not seen in WhatsApp; whether WhatsApp emits
`TYPE_VIEW_CLICKED` with a readable label for its send button decides method A, and method B is the net under it. The icon's glyph
paths have not been looked at on a screen. Test 1: `scripts/test_re_skill.py`.

## Spelling: his misheard names fixed three ways (9.11.2026)

**Asked (momentaryupdates.md):** collect the words his speech-to-text misspells and correct them automatically.

**AssemblyAI docs read 9.11.2026.** `keyterms_prompt` is supported on all three endpoints the app uses: pre-recorded
(200 terms on Universal-2, 1000 on Universal-3.5 Pro, at most 6 words a phrase), sync (`config` part, 100 terms, 8000 characters)
and dictation (`config` part, 100 terms, 8000 characters; `keyterms_prompt` and `prompt` are separate). `custom_spelling`
(`[{"from":["x"],"to":"Y"}]`, `to` one word, `from` at most five, matching case-insensitive) is documented for the pre-recorded
endpoint only. So: keyterms on every request, custom spelling only on pre-recorded and only for unambiguous one-word pairs, and the
local pass for everything else and for every engine (incl. Parakeet).

**Built.** `TranscriptionRequest.keyterms / customSpelling`; `OpenAiCompatibleClient.withVocabulary` wraps the three AssemblyAI
paths and on a 400 resends once without the vocabulary. `MaSpelling` reads `Documents/TTTmini/dictation_corrections.json`
(generated by `re-skill-sync corrections push` from the machine table in MANTRA_MANIFEST modules/dictation-corrections.md; a short
built-in list until it arrives), applies pairs whole-word and case-aware with `unless` / `onlyIf` context rules, and is called in
`DictateController` right before `finalizeAndCommit`. The RE learning loop calls `MaSpelling.noteEdit`: a one or two word substitution
that looks like a misspelling (edit distance at most half) seen twice becomes a learned pair in `dictation_learned.json`, applied at
once, shown in Settings, Spelling marked "learned", and brought to the Mac by `re-skill-sync pull`. Settings, Spelling:
`MaSpellingScreen`, a switch per pair (`maSpellingOff`), a master switch and a keyterms switch. Test 1: `scripts/test_spelling.py`.

**Not proven yet.** No real AssemblyAI request has carried keyterms from this build (sync with `prompt` plus `keyterms_prompt` for
Croatian is the one to watch; a 400 falls back to no vocabulary and the log says so). Whether keyterms make "Groq" come out right
without the local pass is his test. Samir, Diana and mode are common words: the first is unconditional (as he asked), the other two
carry an `onlyIf`.

## RE reads the screen (9.11.2026)

**Asked (momentaryupdates.md):** "take a screenshot, read the latest message and reply to it", spoken to RE, in the app and in the skill.

**Built.** `MaScreenRead` (the tool and the trigger phrases, English and Croatian), `overlay/MaScreenChat` (the accessibility node
tree as chat lines with the side each message sits on: left = the other person, right = Marko, centre = dates), and in
`DictateAccessibilityService.screenshotAboveKeyboard()` (`takeScreenshot`, API 30+, cut above the keyboard window, kept in memory
only) followed by ML Kit's bundled Latin OCR (`com.google.mlkit:text-recognition`, offline). Order: tree, then screenshot and OCR.
**Source (c), vision on the model, is NOT built:** it would send a picture to a paid API and nothing in this app sends an image; when
both sources fail RE says "could not read the screen" and writes nothing. The voice path (`DictateController`) and the typed window
(`FlorisImeService.takeFor`) both read the screen when `MaScreenRead.wants(words)`, then build the prompt with `MaScreenRead.block`
(sides, newest last, "return only the reply"); the reply lands in the field through the existing RE path and is never sent.
Skill section 6b (re_ttt_skill.md) says the same to the model. Settings, RE Magic: the Read screen switch (`maReScreen`) with the order,
and the screenshot permission line with a button to the system's accessibility settings (the system asks, Marko taps Allow; nothing
clicks it). `accessibility_service_config.xml` gained `canTakeScreenshot="true"`. Test 1: `scripts/test_screen_read.py`.

**Not proven yet.** Whether the new capability is granted after an UPDATE: Android may need the TTT service switched off and on once
(Settings shows the state). The tree path has not met WhatsApp's real tree: if its bubbles are not text nodes, step 2 takes over.
The left/right rule is a guess from the box position and is the first thing to look at if a reply answers the wrong message.

## RE never writes into the chat by itself: the pop-up (9.11.2026)

**Asked (Marko, relayed):** "Teach TTT not to write its replies to me into the chat. There must be a pop-up window: I read its reply and I say OK. Only messages go into the chat."
**Found the same day, on the Nothing, in his own chat:** "read the last message and reply" answered with RE's explanation ("There is no real message in this chat to answer") in the message box. The reading itself worked (accessibility tree: it saw the chat); two bugs: the tree reader took the app's own bar label "Emoji, GIFs and stickers" as a message from the other person, and a chat with only his own messages had no [left] line to answer.
**Fixed.** `MaScreenChat` drops everything at or below the message box and no longer takes content descriptions; the screen block says to answer the newest message whatever its side when there is no [left]. `MaReResult` + `MaReResultActivity`: every RE answer opens a pop-up (large scrollable text; OK inserts exactly that text when the field is back, EDIT changes it in the pop-up first, CANCEL drops it); an answer that is not a message (the model is told to start it with `RE-NOTE:`; also "NO MESSAGE FOUND" and every error RE raises, such as "could not read the screen") opens the pop-up with only Close and can never be inserted. An EDIT in the pop-up is logged and learned from like a sent correction; the inserted text becomes the draft compared with what he sends. Voice OK ("okay", "u redu") is NOT built: the pop-up has no recorder; the buttons are large. Test 1: `scripts/test_re_popup.py`.

## Skills written on the phone after each conversation (9.11.2026)

**Asked (momentaryupdates.md):** skills on his own phone, written by AI after each conversation, in Documents/TTTmini (the default folder).
**Built.** `MaSkills`: `Documents/TTTmini/skills/style.md`, `people/<name>.md`, `threads/<chat>.md`, the shape of MARKO_COMMUNICATION (Who / Channel / Language / Tone; Summary, Done, Open, Next step, Last messages). When RE inserts a draft the chat (its title from the top of the accessibility tree, and how it looks) is remembered; when he sends it, the exchange (what he asked, what RE wrote, what he sent) is added; after a quiet minute, or at once on "update skills" / the Settings button, ONE background prompt on the cheapest model (`askModel(cheap = true)`) returns three marked sections that replace the person and thread files and add lines to the style (deduped, 40 at most). The next time RE writes in that chat, `MaSkills.contextBlock()` puts that chat's person and thread files and the style in front of the prompt. No person file for groups or his own chat. Settings, RE Magic: the Skills switch, the file list with date, an editor with Save, Update skills now. Privacy: the files stay on the phone; only the one prompt goes to the API he already uses; `re-skill-sync skills pull|push` moves them to and from the private repo MARKO_COMMUNICATION without overwriting (differences land as .phone.md; style.md becomes PHONE_STYLE.md). Test 1: `scripts/test_skills.py`.
**Not proven yet.** The chat title is the first text at the top of the screen: right for WhatsApp's header, unproven elsewhere. "When he leaves a chat" is approximated by a quiet minute after the last RE message he sent.

## RE assistant slice 1 and RE Magic Database (9.11.2026)

**Database.** `MaRemoteDb`: the phone mirrors markoboskoauroville/RE_MAGIC_DATABASE (seeded from the Mac: TAKEOVER.md, re_ttt_skill.md, dictation_corrections.json, skills/ from MARKO_COMMUNICATION). Pull on start (every 20 minutes at most), a push 15 s after each skills update, learned rule or learned spelling pair; per file the last agreed git blob hash is remembered: only one side changed = copy over, both changed = keep both (the repo's copy saved as NAME.conflict-<time>), the phone's copy pushed. Token: fine-grained, this repo only, Contents read and write, pasted by Marko into Settings, RE Magic, RE Magic Database; encrypted with an Android Keystore AES-GCM key, masked, deletable, never logged. Mac side: `re-skill-sync db push|pull`. **Not proven end to end until the token is pasted.**
**Assistant slice 1.** `MaAssistant`: the Quick Settings tile "RE" and the accessibility button start/stop RE listening (AssemblyAI); the transcript is an assistant command over the app in front, read from the accessibility tree. A reply ("reply to the latest message saying...", "odgovori...") is a draft in the pop-up and OK inserts it into the field behind (accessibility sink); anything else ("read me the last messages", "who wrote last", "what's on the screen", "summarise this chat", "read this email", EN and HR) goes to the model with the screen, and the answer is shown in the pop-up and spoken with Android TTS. Read only: never sends, buys, deletes; banking apps refused; password fields skipped. Marko adds the tile once (Quick Settings edit) or uses the accessibility button. Test 1: `scripts/test_assistant.py`. **Not proven yet:** Gmail's tree on the Nothing, the Croatian TTS voice, and the tile on a locked screen. "Open the chat with Ivan", "scroll up" are not in slice 1.

## RE speaks, and RE understands what is in front (9.11.2026)

**Voice.** `MaVoice`: RE's notes, questions and read-outs are spoken with TTT's own Reader voice (`MaReader.speakText`: Speechify English, Croatian Edge) and shown as Voice Engine (Mantra) subtitles: VOICE_ENGINE_ANDROID is only an overlay with no speak API for other apps, so RE posts `/start`, `/pos` and `/end` to its localhost port 8720-8735 (length estimated from the text; nothing happens if Voice Engine is off). Settings, RE Magic: "RE speaks". The voice stops when he starts dictating (`onMicClick`) and when the pop-up closes. A note that ends in "?" gets an Answer button (starts the RE recorder). Not Android TextToSpeech.
**The app in front.** `MaAssistant.appHint()` tells the model which app is in front (WhatsApp: messages, Gmail: mail, Claude: his prompts). If the app in front at result time is not the one the command was given in, the result is a pop-up note only and nothing is inserted (`MaReResult.present`, and again at OK for assistant inserts). The device test runner checks the foreground before every adb input and pauses while another app (Claude) is in front, resuming 30 s after WhatsApp is back.
**Voice-through-speaker tests** (the Mac speaks, the Nothing listens) are NOT done: they need the Nothing next to the MacBook and a switch of the Mac's output away from the ADAM monitors; to be arranged with Marko.

## STT bake-off method (9.11.2026)
Movie shape: 21 sentences (7 en, 7 hr, 7 mixed), Marko reads them into `tools/stt_bakeoff/record_server.py`; `run_bakeoff.py` runs each engine plain and with the canonical names (`names.txt`, from MANTRA_MANIFEST modules/dictation-corrections.md), same normalisation, WER / name errors / diacritics / latency per group. Results in docs/stt-bakeoff-9nov.md. TTT keeps no dictation audio, so truth = the written sentences.


## RULE (Marko, 9.11.2026): a new menu entry is the second item from the top

> "Whenever there is a new feature in a menu, it must be the second item from the top, so I'm not searching for something new at the bottom."

`MaSettingsOrder.parse()` inserts every entry that is not yet in the saved order at position 2 (after the first entry), never at the end; his own arrangement is untouched. Hidden ids (KEYS, VOCABULARY, CORRECTIONS, TRANSCRIPTION_MODEL) are filtered inside `parse()` so the home list and the Settings order list are the same set (build 571 showed a ghost "Transcription model" only in the order list, because the filter lived in HomeScreen alone). Build 572: "Voice transcription buttons" moved to position 2 once (`moveEnginesSecondIfNeeded`). The H/E key engine name is regular weight at 0.8 of the build 571 size, still auto-fit.

**NEW badge (Marko, 9.11.2026):** "Add a NEW badge to new items in the settings, and once the user has selected it, the NEW disappears." Every settings entry whose id is not in the seen set (`maSettingsSeen`, comma separated ids) shows a small accent-coloured NEW pill on its row; opening it adds the id to the set for good. The set is seeded once (`seedSettingsSeenIfNeeded`, build 572) with every entry that existed then, except "Voice transcription buttons"; so a developer only adds the enum entry and the app marks it new, and `parse()` puts it second from the top.

## Voice transcription buttons as two columns (9.11.2026)

Marko: no description text, the models as columns, each column as wide as its key. The screen is now the key row plus a second Row built the same way (two `weight(1f)` columns, 10 dp gap, 12 dp side padding), so the edges line up with the keys by construction. No paragraph, no "H · Croatian" headings: the key preview is the header. A provider is a bordered card: radio (36 dp), short name (bodyMedium, wraps, never cut), quota line (labelSmall: "480 min", "free", "$0.10/h"); the selected card has a red border and tint. Names come from `EngineCatalog.shortName`, with "Groq v3" and "Google v1" in `colName` here only, because the key itself still shows "Groq" and "Google". Advanced is one full-width button at the bottom. Rejected: a heading per column (the key above says it), the long quota wording (does not fit a half-width column).

### Key label: one source of truth, name left with the quota (9.11.2026)

Marko: Settings said H = Groq, the key said AssemblyAI. Two causes found. (1) The key fell back to the literal "assemblyai" while `EngineLive` was still empty (before its `LaunchedEffect` ran); it now falls back to `EngineStore.selected`, the same store the radio list writes. (2) The "Talk to" keys (RECORD_HR_NET / RECORD_EN_NET) force AssemblyAI for one recording by design and are labelled AssemblyAI; they are not the H key. `EngineLive` stays the one live state both screens observe. Layout: `MaEngineKeyContent` = dot, letter, name left (8 to 16 sp, auto-fit), quota at half the name size on the same line ("480 min free", "free", "$0.10/h paid"; "paid" once the free minutes are gone: no paid balance is known to the app, only the price). `engineSubLabel` is the one wording.

### Key label: name and one word at the same size (9.11.2026, task 69)

Marko disliked build 576 (half-size minutes). The H/E key (and both previews in Voice transcription buttons) now shows dot, letter, provider name, then `free <minutes left>` (38332 shown as 38k, no unit) while the provider has free quota, `free` alone if unlimited, `paid` when none, both at the build 574 size (0.8 x 26 sp, regular weight). If the key is narrow both shrink by the same factor, never cut. The radio cards keep their small quota line.

### Keys enter only through files: one picker, detect, test, keep the working ones (9.11.2026, task 70)

**The rule (Marko, 9.11.2026): API keys are only entered through text files.** There is no key paste field anywhere in TTT any more (the Advanced one is gone; the legacy key manager already loaded files only). One row, **Import keys from file**, sits second from the top of Voice transcription buttons, under the H/E keys, with **Re-test keys** beside it. It opens the Android picker (text files, `.txt/.json/.env`, several at once).

**Pipeline** (`dictate/engines/`): `KeyScan` (plain Kotlin, unit-tested) pulls every token out of a file and lists the providers it could be, by shape and by the label (file name, line above, text before the token): `gsk_` Groq, `AIza`/`AQ.` Google, `sk-ant-` Anthropic, 32-hex AssemblyAI, 40-hex Deepgram, 64-hex Soniox, 32 mixed alphanumerics Speechmatics, `sk_` ElevenLabs or Speechify (label, then length, then the test decides), `cfat_`/`cfut_` plus a 32-hex account id Cloudflare (the id is never read as an AssemblyAI key), a `type: service_account` JSON Google Chirp. `KeyTester` then tests each with a free list or auth call (status code only, never a generation; Google Speech v1 is asked with empty audio, a service account by its token exchange), trying the candidate providers in order until one authenticates. `KeyImporter` shows the summary ("Groq: 10 keys, 10 tested, 5 working, 5 refused"), stores only the working ones (429 counts as working but rate-limited) in the provider's Keystore ring (`EngineKeys.merge`, dedupes, so a re-import merges) and mirrors Anthropic, Speechify and AssemblyAI into the keyboard's older key ring (RE reads it there). A key from a file for an engine with no free quota (Soniox, ElevenLabs) switches that engine on. Re-test drops the ones that died.

**Fallback** (`KeyRing`): every cloud engine calls through the ring. 401/403, 429 or a timeout moves to the next key and the status line says it ("Groq key 2/5 rate-limited -> key 3"); a refused key rests an hour, a rate-limited one a minute; when the last key fails the failure goes up and the engine chain moves to the next provider as before. The provider cards in Voice transcription buttons show "5 keys" (or "1 account") small.

**No key is ever shown, logged or put in a summary.** Debug (adb, DUMP): `dev.ttt.engines.KEYS` runs the same importer over files in `Android/data/<app>/files/keys/` and deletes them; `dev.ttt.engines.RETEST` re-tests; results in `engines_log.txt`, counts only. Tests: `KeyScanTest`.
