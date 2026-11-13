#!/usr/bin/env python3
"""
Test 1 for the history and RE chat request (Marko, 13.11.2026): a cancelled dictation stays in the history as
"audio only"; its audio goes only with Clear history or his own delete; History and RE Magic are full-screen
windows; the RE chat has Close / Accept / Clear, reads TAKEOVER, LESSONS and SKILLS at every start, writes them
itself, and Clear wipes only the chat.

    python3 scripts/test_re_chat_history.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard"
failures, checks = [], 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        failures.append(f"{name}: {detail}")


def code(path):
    t = path.read_text()
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"^\s*//.*$", "", t, flags=re.M)


ctrl = code(SRC / "dictate/DictateController.kt")
hist = code(SRC / "dictate/data/history/DictateHistory.kt")
hact = code(SRC / "dictate/MaHistoryActivity.kt")
chat = code(SRC / "dictate/MaReChat.kt")
cact = code(SRC / "dictate/MaReChatActivity.kt")
row = code(SRC / "dictate/ui/MaFeatureRow.kt")
man = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

# 1. cancel keeps the audio
i = ctrl.find("fun cancelRecording(")
j = ctrl.find("recorder?.cancel()", i)
check("cancelRecording keeps the audio before deleting it", 0 < i < j and "maKeepCancelledAudio(" in ctrl[i:j])
i = ctrl.find("fun cancelTranscription(")
j = ctrl.find("transcribeJob?.cancel()", i)
check("cancelTranscription keeps the audio", 0 < i < j and "maKeepCancelledAudio(" in ctrl[i:j])
k = ctrl.find("private fun maKeepCancelledAudio(")
body = ctrl[k:k + 2500]
check("audio-only entry is failed + forceAudio", "failed = true" in body and "forceAudio = true" in body)
check("copied before the original is deleted", "copyTo(copy" in body)

# 2. audio goes only with clear / own delete
p = hist.find("private suspend fun prune(")
check("prune never drops audio-only entries", "entry.pinned || entry.failed" in hist[p:] and hist[p:].count("entry.failed") >= 2)
check("clearAll deletes every audio file", "audioDir(context).deleteRecursively()" in hist)
check("delete text keeps audio", "fun deleteTextOnly(" in hist and "markAudioOnly" in hist)

# 3. full-screen history
check("History key opens the full-screen window", "MaHistoryActivity.open(" in row)
for label in ["Insert", "Copy", "Play", "Re-transcribe", "Delete text", "Delete audio", "\"Delete\"", "Clear history"]:
    check(f"History has {label}", label in hact)
check("Clear history asks first", "Clear the whole history?" in hact)
check("Re-transcribe can keep the new text", "onKeep" in hact)

# 4. RE chat
check("RE key opens the chat", "MaReChatActivity.openFromKey(" in row)
for label in ["\"Close\"", "\"Accept\"", "\"Clear\""]:
    check(f"chat option {label}", label in cact)
for f in ["TAKEOVER.md", "LESSONS.md", "SKILLS.md"]:
    check(f"memory file {f}", f in chat)
check("memory is seeded by default", "fun ensureMemory()" in chat and "ensureMemory()" in cact)
pr = chat[chat.find("fun prompt("):chat.find("suspend fun ask(")]
check("every prompt reads the memory", "memoryBlock()" in pr)
check("clear learns first, then wipes only the chat", cact.find("MaReChat.learn(app, snapshot, \"cleared\")") < cact.find("MaReChat.clearChat()"))
_c = chat.find("fun clearChat()")
cc = chat[_c:chat.find("fun ", _c + 5)]
check("clearChat deletes only chat.json", "CHAT" in cc and "TAKEOVER" not in cc and "SKILLS" not in cc)
check("writeFiles stays inside re/", "notes/" in chat and "[a-z0-9_-]{1,40}\\\\.md" in chat)
check("Accept only inserts, never sends", "MaReResult.acceptFor(" in cact and "performEditorAction" not in cact)
check("chat stored on the phone", "chat.json" in chat)

# 5. voice / text / mute, highlight, subtitles
check("Text / Voice / Sound switches", all(s in cact for s in ["\"Text\"", "\"Voice\"", "\"Sound\"", "\"Muted\""]))
check("reply read with the Reader voice", "MaReader.speakText(" in cact)
check("word highlight from the Reader index", "MaReader.currentIndex" in cact and "MaReader.allWords" in cact)
check("subtitles when the Reader shows subtitles", "display == \"subtitle\"" in cact and "MaSubtitleRow()" in cact)
check("voice uses his H/E engine only", "EngineStore.selected(app, lang)" in cact)
check("3 s retries, 10 times", "delay(3_000L)" in cact and "attempt > 10" in cact)

# 6. people (13.11.2026, second request)
check("person from the chat title", "MaReChat.person = " in cact and "MaSkills.chatTitle()" in cact)
check("person file in every prompt", "<person name=" in chat)
check("learn rewrites the person file", "people/${MaSkills.slug(who)}.md" in chat and "## Conversations" in chat and "## Flow" in chat)
check("people files only under re/people", "name.startsWith(\"people/\")" in chat)
check("History button opens People", "showPeople = true" in cact and "PeopleScreen(" in cact)
check("clear one person and all", "deletePerson(which)" in cact and "deleteAllPeople()" in cact)
check("chat Clear keeps people", "peopleDir" not in chat[chat.find("fun clearChat()"):chat.find("fun ", chat.find("fun clearChat()") + 5)])

# 7. manifest
check("both windows declared", "MaHistoryActivity" in man and "MaReChatActivity" in man)

for f in failures:
    print("FAIL", f)
print(f"re chat and history, test 1: {checks} checks, {len(failures)} failed")
sys.exit(1 if failures else 0)
