# RE Magic as an assistant: the plan (9.11.2026)

Marko, 9.11.2026: "RE Magic should actually become an accessibility service. It can do anything I ask. I can ask 'read me the last
messages'. It's like a Siri for WhatsApp, or email, or whatever is in front." Plus: a read-only GitHub token so RE can fetch history,
and "RE has its own repository on GitHub called RE Magic Database and stores its data there, in a private repo."

## What exists (builds 549 to 551)
RE key and RE window; AssemblyAI only; the skill file; learning from corrections; spelling; screen reading from the accessibility tree
(WhatsApp, WhatsApp Business: tree only); the pop-up (OK / Edit / Cancel, notes and errors never inserted); skills written on the phone
(style, people, threads). RE already runs inside the TTT accessibility service (`DictateAccessibilityService`).

## Slice 1: "read me the last messages", "reply to the latest message" from an assistant trigger
1. **Trigger when no field is focused:** the accessibility button (service flag `flagRequestAccessibilityButton`, `AccessibilityButtonController`)
   and a Quick Settings tile (`TileService`) both start the same entry, `MaAssistant.listen()`: record with AssemblyAI (the RE recorder),
   run the command. The keyboard RE key stays the entry when a field is focused.
2. **Commands (EN + HR), a small intent table (regex first, the cheap model only for what the table does not know):**
   read the last messages / pročitaj zadnje poruke; who wrote last / tko je zadnji pisao; reply to X saying ... / odgovori X da ...;
   what's on the screen / što je na ekranu; summarise this chat; read this email; open the chat with Ivan; scroll up.
3. **Output is always the pop-up** (MaReResult): spoken answers also go through the phone TTS or the voice API (`:8837/speak` is on the Mac;
   on the phone Android TTS, Croatian voice when installed). A reply is a draft in the pop-up; OK inserts it into the field; sending is
   his own tap or his explicit spoken "send it" in the pop-up, never automatic.
4. **Gmail slice:** the same tree reader on `com.google.android.gm`: sender, subject, the opened mail's text; read-only. Test on his inbox, nothing
   opened for editing, nothing sent.
5. **Safety layer (before any action):** a table of what needs a spoken confirmation in the pop-up (send, delete, buy, pay, change a setting,
   post), password and banking fields are never read or written (`isPassword`, a list of banking packages), and every action RE takes is listed in
   the pop-up first.

## Slice 2: RE Magic Database (private GitHub repo markoboskoauroville/RE_MAGIC_DATABASE)
RE's memory lives in that repo and is mirrored on the phone in Documents/TTTmini/ (offline copy): style.md, people/, threads/, projects/,
the corrections log, learned spelling pairs, the skill files. The phone pulls on start and periodically, commits and pushes after each
conversation update (one commit per update, small text files only); a conflict keeps both files, newest wins per file. Token: a fine-grained token
scoped to ONLY that repo (Contents read and write), created and pasted by Marko on the phone, kept in Keystore-encrypted storage, masked, with a
delete button, never printed or logged. The Mac side: `re-skill-sync` talks to the repo through git. Seed from the Mac first: structure, TAKEOVER.md as
the description, re_ttt_skill.md, dictation_corrections.json, MARKO_COMMUNICATION people/threads/projects (copied; merging the two repos is a later step).

## Slice 3: read-only GitHub history
Settings, RE Magic, GitHub: an optional second read-only token for his other repos. Tools: search repos, read a file, list recent commits and
issues; commands like "what did we agree with Mislav", "status of the CQ project" put the answer into the pop-up draft. No pushes or edits from this token.

## Order and rules
1 (read + reply in WhatsApp w4b and Gmail from the trigger), 2 (the database), 3 (GitHub history). Each slice: written plan, build on GitHub only, installed on the
Nothing, tested in his self-chat and his own inbox (read-only), docs updated. Nothing is sent, bought, deleted or changed without his spoken confirmation.
