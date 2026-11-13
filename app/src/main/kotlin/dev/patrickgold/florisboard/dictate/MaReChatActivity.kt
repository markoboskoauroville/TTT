/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.audio.MaResample
import dev.patrickgold.florisboard.dictate.audio.RecordingController
import dev.patrickgold.florisboard.dictate.engines.EngineAdapters
import dev.patrickgold.florisboard.dictate.engines.EngineCatalog
import dev.patrickgold.florisboard.dictate.engines.EngineChain
import dev.patrickgold.florisboard.dictate.engines.EngineStore
import dev.patrickgold.florisboard.dictate.engines.FailKind
import dev.patrickgold.florisboard.dictate.engines.FallbackRunner
import dev.patrickgold.florisboard.dictate.ui.MaHost
import dev.patrickgold.florisboard.dictate.ui.MaHostKey
import dev.patrickgold.florisboard.dictate.ui.MaRecorderReadout
import dev.patrickgold.florisboard.dictate.ui.MaSubtitleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefAsState

/**
 * The RE Magic chat (Marko, 13.11.2026, second version): in the keyboard's own look (.claude/skills/ttt-design), the
 * balloon idea from iPhone Messages.
 *
 * - **No title bar.** The chat fills the screen; at the bottom, the composer and one row of keys.
 * - **Balloons**: his on the right, RE's on the left; under every RE answer its own keys: Accept (into the app behind),
 *   Edit (change it, then Accept; the change is a lesson), Copy, Speak, Retry, Wrong (the next thing he says is how it
 *   should be, and that becomes a lesson at once).
 * - **Voice**: the keyboard's recorder: language badge, trash, the red-ringed recorder key with lamp, clock, MB and
 *   meter ([MaRecorderReadout], the same drawing as the keyboard strip), send. Transcribed with his H/E engine only.
 * - **Keys row**: Text, Voice, Sound (green ring when on), People (summaries per person), Clear (RE writes its memory,
 *   then the chat goes), Close.
 * - RE's spoken answer lights the sentence and the word in the balloon (the Reader's index); with Reader subtitles on,
 *   the subtitle box is shown too.
 */
class MaReChatActivity : ComponentActivity() {

    private var accepted = false
    private var recorder: RecordingController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaRemoteDb.attach(this)
        MaReChat.ensureMemory()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = MaHost.Bg, surface = MaHost.Bg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaHost.Bg) {
                    ChatScreen(
                        onAccept = { text, words ->
                            accepted = true
                            MaReResult.acceptFor(text, MaReChat.pkg, MaReChat.fieldId, words = words)
                            finish()
                        },
                        onClose = { finish() },
                        recorderHolder = { recorder },
                        setRecorder = { recorder = it },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { recorder?.cancel() }
        recorder = null
        MaReader.stop()
        if (!accepted) MaReResult.closedWithoutInsert()
        DictateController.recoverAfterPopup()
        super.onDestroy()
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(
                Intent(context.applicationContext, MaReChatActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }

        /** The RE key: remember the field, read who he talks to and the screen behind, then open the chat over it. */
        fun openFromKey(context: Context, pkg: String?, fieldId: Int) {
            MaReChat.pkg = pkg?.takeIf { it != context.packageName }
            MaReChat.fieldId = fieldId
            MaReprompt.noteField(pkg, fieldId)
            MaReChat.screen = null
            MaReChat.person = null
            MaReChat.scope.launch {
                if (MaReChat.pkg != null) MaReChat.person = runCatching { MaSkills.chatTitle() }.getOrNull()?.trim()?.takeIf { it.length in 2..60 }
                if (MaScreenRead.on()) MaReChat.screen = withTimeoutOrNull(3_000L) { MaScreenRead.read() }
                withContext(Dispatchers.Main) { open(context) }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    onAccept: (String, String) -> Unit,
    onClose: () -> Unit,
    recorderHolder: () -> RecordingController?,
    setRecorder: (RecordingController?) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<MaReChat.Msg>().apply { addAll(MaReChat.load()) } }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf(MaReChat.voiceMode(app)) }
    var muted by remember { mutableStateOf(MaReChat.muted(app)) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var speakingAt by remember { mutableLongStateOf(-1L) }
    var editingAt by remember { mutableLongStateOf(-1L) }
    var editText by remember { mutableStateOf("") }
    var wrongAt by remember { mutableLongStateOf(-1L) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(MaLanguage.active()) }
    // The recorder's readings, sampled like the keyboard strip samples its own.
    var level by remember { mutableFloatStateOf(0f) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var bytes by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    val prefs by FlorisPreferenceStore
    val display by prefs.dictate.maReaderDisplay.collectPrefAsState()

    if (showPeople) {
        PeopleScreen(onBack = { showPeople = false })
        return
    }

    LaunchedEffect(msgs.size, thinking) {
        val n = msgs.size + (if (thinking) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }
    LaunchedEffect(recording) {
        while (recording) {
            val r = recorderHolder() ?: break
            level = (r.maxAmplitude() / 32767f).coerceIn(0f, 1f)
            bytes = r.bytesWritten
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            delay(60L)
        }
        level = 0f
    }

    fun speak(m: MaReChat.Msg, force: Boolean = false) {
        if (muted && !force) return
        speakingAt = m.at
        MaReader.speakText(app, m.shown) { }
    }

    fun ask() {
        thinking = true
        status = ""
        val sofar = msgs.toList()
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { MaReChat.ask(app, sofar) { s -> scope.launch { status = s } } } }
            thinking = false
            r.onSuccess { a ->
                status = ""
                val m = MaReChat.Msg(false, a.ifBlank { "(no answer)" })
                msgs.add(m)
                MaReChat.save(msgs.toList())
                speak(m)
            }.onFailure { e ->
                if (e is CancellationException) return@onFailure
                status = "RE could not answer: " + (e.message ?: e.javaClass.simpleName).take(120)
            }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        MaReader.stop()
        // A cue: Wrong was pressed, or this reply corrects RE. It becomes a lesson now, not at Clear.
        val lastRe = msgs.lastOrNull { !it.me }
        val wrongMsg = msgs.firstOrNull { it.at == wrongAt }
        val cue = when {
            wrongMsg != null -> wrongMsg to "pressed Wrong under the answer"
            lastRe != null && msgs.lastOrNull()?.me == false && MaReChat.looksLikeCorrection(t) -> lastRe to "corrected RE in the chat"
            else -> null
        }
        if (cue != null) {
            MaReChat.scope.launch {
                val rule = MaReChat.lesson(app, cue.first.shown, t, cue.second)
                if (rule != null) withContext(Dispatchers.Main) { status = "Lesson kept: " + rule.take(90) }
            }
        }
        wrongAt = -1L
        msgs.add(MaReChat.Msg(true, t))
        MaReChat.save(msgs.toList())
        ask()
    }

    fun startListening() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is off for TTT"
            return
        }
        MaReader.stop()
        val r = RecordingController(app)
        runCatching { r.start() }.onFailure { status = "Microphone busy: " + (it.message ?: ""); return }
        setRecorder(r)
        startedAt = SystemClock.elapsedRealtime()
        elapsedMs = 0L
        bytes = 0L
        recording = true
        status = ""
    }

    fun discardListening() {
        recorderHolder()?.let { runCatching { it.cancel() } }
        setRecorder(null)
        recording = false
        status = ""
    }

    fun stopListening() {
        val r = recorderHolder() ?: return
        setRecorder(null)
        recording = false
        val wav = runCatching { r.stop() }.getOrNull()
        if (wav == null || !wav.exists() || wav.length() < 4_000L) { status = "Nothing heard"; return }
        val mine = File(app.cacheDir, "re_chat_${System.currentTimeMillis()}.wav")
        runCatching { wav.copyTo(mine, overwrite = true) }
        transcribing = true
        val l = MaLanguage.active()
        val engine = EngineStore.selected(app, l)
        scope.launch {
            // His H/E engine only, never another one; no answer or no internet: 3 s and the same engine again, 10 times.
            var attempt = 0
            var words: String? = null
            while (words == null) {
                status = EngineCatalog.shortName(engine) + (if (attempt > 0) " · retry $attempt/10" else "")
                val res = runCatching {
                    withContext(Dispatchers.IO) {
                        val w = MaResample.toTargetRate(mine) ?: mine
                        try { EngineAdapters.run(app, engine, w, l, EngineChain.names()) } finally { if (w !== mine) w.delete() }
                    }
                }
                res.onSuccess { words = it.trim() }
                val err = res.exceptionOrNull() ?: continue
                if (err is CancellationException) break
                val kind = FallbackRunner.classify(err)
                attempt++
                if ((kind != FailKind.NETWORK && kind != FailKind.TIMEOUT) || attempt > 10) {
                    status = "Transcription failed: " + (err.message ?: kind.name).take(100)
                    break
                }
                delay(3_000L)
            }
            runCatching { mine.delete() }
            transcribing = false
            val w = words
            if (!w.isNullOrBlank()) { status = ""; send(w) } else if (w != null) status = "Nothing heard"
        }
    }

    fun accept(m: MaReChat.Msg, text: String) {
        val asked = msgs.lastOrNull { it.me && it.at < m.at }?.text.orEmpty()
        val snapshot = msgs.toList()
        MaReChat.scope.launch { MaReChat.learn(app, snapshot, "kept (a draft was accepted)") }
        onAccept(text, asked)
    }

    BackHandler { onClose() }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        // ---- the chat: no title bar
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (msgs.isEmpty() && !thinking) {
                item {
                    Text(
                        (MaReChat.person?.let { "With $it. " } ?: "") + "Ask what to write; RE reads the chat behind and its notes.",
                        color = MaHost.Muted, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            itemsIndexed(msgs, key = { _, m -> m.at.toString() + m.me }) { _, m ->
                if (m.me) {
                    MineBalloon(m)
                } else Column {
                    val editing = editingAt == m.at
                    ReBalloon(
                        m = m,
                        speaking = speakingAt == m.at && MaReader.state != MaReader.State.IDLE,
                        editing = editing,
                        editText = editText,
                        onEditText = { editText = it },
                        wrong = wrongAt == m.at,
                    )
                    ReActions(
                        canAccept = MaReChat.pkg != null,
                        editing = editing,
                        speaking = speakingAt == m.at && MaReader.state != MaReader.State.IDLE,
                        wrong = wrongAt == m.at,
                        onAccept = {
                            if (editing) {
                                val original = m.draft ?: m.shown
                                val edited = editText.trim()
                                if (!MaReSkill.same(original, edited)) {
                                    MaReChat.scope.launch { MaReChat.lesson(app, original, edited, "edited the draft before accepting it") }
                                }
                                accept(m, edited)
                            } else {
                                accept(m, m.draft ?: m.shown)
                            }
                        },
                        onEdit = {
                            if (editing) editingAt = -1L else { editingAt = m.at; editText = m.draft ?: m.shown }
                        },
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("RE", m.draft ?: m.shown))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        onSpeak = { if (speakingAt == m.at && MaReader.state != MaReader.State.IDLE) MaReader.stop() else speak(m, force = true) },
                        onRetry = {
                            if (!thinking) {
                                MaReader.stop()
                                msgs.remove(m)
                                MaReChat.save(msgs.toList())
                                ask()
                            }
                        },
                        onWrong = {
                            wrongAt = if (wrongAt == m.at) -1L else m.at
                            status = if (wrongAt == m.at) "Say or type how it should be; RE keeps it as a lesson." else ""
                        },
                    )
                }
            }
            if (thinking) item { TypingBalloon() }
        }

        // ---- the Reader's subtitle box, when the Reader shows subtitles
        if (display == "subtitle" && MaReader.state != MaReader.State.IDLE && speakingAt > 0) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) { MaSubtitleRow() }
        }
        if (status.isNotBlank()) {
            Text(status, color = MaHost.Muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        }

        // ---- the composer, as the keyboard's strip: language badge, (trash), field or recorder, send
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaHostKey(
                label = if (lang == MaLanguage.EN) "ENG" else "HR",
                ring = MaHost.RingSwitch,
                onClick = { MaLanguage.toggle(app); lang = MaLanguage.active() },
            )
            if (voice) {
                MaHostKey(label = null, icon = Icons.Default.Delete, enabled = recording, onClick = { discardListening() })
                val busy = transcribing || thinking
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp)
                        .clip(MaHost.KeyShape).background(MaHost.Key)
                        .border(1.5.dp, if (recording) MaHost.RingRecordLive else MaHost.RingRecord, MaHost.KeyShape)
                        .clickable(enabled = !busy || recording) { if (recording) stopListening() else startListening() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (recording || transcribing) {
                        MaRecorderReadout(level, elapsedMs, bytes, recording = recording, sending = transcribing, tint = MaHost.Ink)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(MaHost.RingRecord, CircleShape))
                            Text(
                                if (thinking) "RE is answering…" else "Tap and talk",
                                color = MaHost.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                MaHostKey(label = null, icon = Icons.AutoMirrored.Filled.Send, ink = MaHost.Gold, enabled = recording, onClick = { stopListening() })
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp)
                        .clip(MaHost.KeyShape).background(MaHost.Key).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) Text("Message", color = MaHost.Muted, fontSize = 17.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = MaHost.Ink, fontSize = 17.sp),
                        cursorBrush = SolidColor(MaHost.Gold),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                    )
                }
                MaHostKey(
                    label = null, icon = Icons.AutoMirrored.Filled.Send, ink = MaHost.Gold,
                    enabled = input.isNotBlank() && !thinking,
                    onClick = { val t = input; input = ""; send(t) },
                )
            }
        }

        // ---- one row of keys, like the feature row
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).padding(bottom = 4.dp)) {
            MaHostKey("Text", Modifier.weight(1f), icon = Icons.Default.Keyboard, ring = if (!voice) MaHost.RingOn else null, fontSize = 13, padH = 4) {
                if (recording) discardListening()
                voice = false; MaReChat.setVoiceMode(app, false)
            }
            MaHostKey("Voice", Modifier.weight(1.1f), icon = Icons.Default.Mic, ring = if (voice) MaHost.RingOn else MaHost.RingRecord, fontSize = 13, padH = 4) {
                voice = true; MaReChat.setVoiceMode(app, true)
            }
            MaHostKey(
                null, Modifier.weight(0.7f),
                icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                ring = if (!muted) MaHost.RingOn else null,
            ) {
                muted = !muted
                MaReChat.setMuted(app, muted)
                if (muted) MaReader.stop()
            }
            MaHostKey(null, Modifier.weight(0.7f), icon = Icons.Default.People, ring = MaHost.RingSwitch) { showPeople = true }
            MaHostKey(null, Modifier.weight(0.7f), icon = Icons.Default.DeleteSweep, ink = MaHost.DangerInk, enabled = msgs.isNotEmpty() && !clearing) { confirmClear = true }
            MaHostKey(null, Modifier.weight(0.7f), icon = Icons.Default.Close, onClick = onClose)
        }
    }

    if (confirmClear) {
        HostDialog(
            title = "Clear the chat?",
            text = "RE first writes what it learned into TAKEOVER, LESSONS, SKILLS and the file of the person you are talking to " +
                "(synced to GitHub). Then the chat is deleted; those files stay (People).",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                clearing = true
                status = "Writing takeover, lessons, skills…"
                MaReader.stop()
                val snapshot = msgs.toList()
                scope.launch {
                    val n = withContext(Dispatchers.IO) { MaReChat.learn(app, snapshot, "cleared") }
                    MaReChat.clearChat()
                    msgs.clear()
                    clearing = false
                    status = if (n > 0) "Chat cleared · RE kept $n memory file(s)" else "Chat cleared · RE could not update its memory this time"
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

private val BalloonMine = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
private val BalloonRe = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

@Composable
private fun MineBalloon(m: MaReChat.Msg) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            m.text, color = MaHost.Ink, fontSize = 17.sp, lineHeight = 23.sp,
            modifier = Modifier.widthIn(max = 310.dp).padding(start = 48.dp).clip(BalloonMine).background(MaHost.Mine)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun ReBalloon(m: MaReChat.Msg, speaking: Boolean, editing: Boolean, editText: String, onEditText: (String) -> Unit, wrong: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp).padding(end = 40.dp).clip(BalloonRe).background(MaHost.Theirs)
                .border(1.dp, if (wrong) MaHost.DangerInk else MaHost.RingSwitch.copy(alpha = 0.35f), BalloonRe)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            when {
                editing -> BasicTextField(
                    value = editText, onValueChange = onEditText,
                    textStyle = TextStyle(color = MaHost.Ink, fontSize = 17.sp, lineHeight = 23.sp),
                    cursorBrush = SolidColor(MaHost.Gold),
                    modifier = Modifier.fillMaxWidth(),
                )
                speaking -> SpokenText()
                else -> {
                    // In order (13.11.2026: the draft was pulled to the end and left a hole where it stood): the talk before
                    // the draft, the draft card, the talk after it. Blank runs collapse; markdown stars are dropped.
                    val m0 = Regex("<draft>([\\s\\S]*?)</draft>", RegexOption.IGNORE_CASE).find(m.text)
                    fun clean(t: String) = t.replace("**", "").replace(Regex("\\n{3,}"), "\n\n").trim()
                    val before = clean(if (m0 != null) m.text.substring(0, m0.range.first) else m.shown)
                    val after = clean(if (m0 != null) m.text.substring(m0.range.last + 1) else "")
                    if (before.isNotBlank()) Text(before, color = MaHost.Ink, fontSize = 17.sp, lineHeight = 23.sp)
                    if (m0 != null) {
                        Column(
                            modifier = Modifier.padding(vertical = if (before.isNotBlank() || after.isNotBlank()) 8.dp else 0.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)).background(MaHost.Bg)
                                .border(1.dp, MaHost.Gold.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            Text("DRAFT", color = MaHost.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(m0.groupValues[1].trim(), color = MaHost.Ink, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    if (after.isNotBlank()) Text(after, color = MaHost.Ink, fontSize = 17.sp, lineHeight = 23.sp)
                }
            }
        }
    }
}

/** The keys under every RE answer. */
@Composable
private fun ReActions(
    canAccept: Boolean,
    editing: Boolean,
    speaking: Boolean,
    wrong: Boolean,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRetry: () -> Unit,
    onWrong: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 24.dp)) {
        MaHostKey("Accept", icon = Icons.Default.Check, ring = MaHost.RingOn, enabled = canAccept, fontSize = 13, padH = 8, onClick = onAccept)
        MaHostKey(if (editing) "Done" else null, icon = Icons.Default.Edit, lit = editing, fontSize = 13, padH = 8, onClick = onEdit)
        MaHostKey(null, icon = Icons.Default.ContentCopy, padH = 8, onClick = onCopy)
        MaHostKey(null, icon = if (speaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, lit = speaking, padH = 8, onClick = onSpeak)
        MaHostKey(null, icon = Icons.Default.Refresh, padH = 8, onClick = onRetry)
        MaHostKey("Wrong", icon = Icons.Default.ThumbDown, ink = MaHost.DangerInk, ring = if (wrong) MaHost.DangerInk else null, fontSize = 13, padH = 8, onClick = onWrong)
    }
}

/**
 * The answer being read, from the Reader's own word list: the sentence being read on a lit band, the word being said
 * in gold and bold (the Reader's highlight colour). Same index as the Reader's karaoke.
 */
@Composable
private fun SpokenText() {
    val words = MaReader.allWords
    val idx = MaReader.currentIndex
    if (words.isEmpty()) {
        Text("…", color = MaHost.Ink, fontSize = 17.sp)
        return
    }
    fun ends(w: String) = w.trimEnd('"', '”', ')', '\'').lastOrNull() in setOf('.', '!', '?')
    var start = 0
    var end = words.size - 1
    if (idx in words.indices) {
        start = idx
        while (start > 0 && !ends(words[start - 1].text)) start--
        end = idx
        while (end < words.size - 1 && !ends(words[end].text)) end++
    }
    val text = buildAnnotatedString {
        words.forEachIndexed { i, w ->
            if (i > 0) append(" ")
            when {
                i == idx -> withStyle(SpanStyle(color = MaHost.Gold, fontWeight = FontWeight.Bold, background = Color(0x33E8B15C))) { append(w.text) }
                idx in words.indices && i in start..end -> withStyle(SpanStyle(background = Color(0x1FFFFFFF))) { append(w.text) }
                else -> append(w.text)
            }
        }
    }
    Text(text, color = MaHost.Ink, fontSize = 17.sp, lineHeight = 23.sp)
}

@Composable
private fun TypingBalloon() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.clip(BalloonRe).background(MaHost.Theirs).padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0..2) {
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i",
            )
            Box(Modifier.size(8.dp).alpha(a).background(MaHost.Sand, CircleShape))
        }
    }
}

/** A confirmation in the host look. */
@Composable
private fun HostDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaHost.Key,
        title = { Text(title, color = MaHost.Ink, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = MaHost.Ink, fontSize = 16.sp) },
        confirmButton = { MaHostKey(confirmLabel, ink = MaHost.DangerInk, ring = MaHost.Danger, onClick = onConfirm) },
        dismissButton = { MaHostKey("Keep", onClick = onDismiss) },
    )
}

/**
 * People (the People key): every person RE has met, with the summaries of their conversations and the flow, from
 * re/people/<name>.md. These files survive Clear in the chat; only this screen deletes them, one or all, after asking.
 */
@Composable
private fun PeopleScreen(onBack: () -> Unit) {
    var people by remember { mutableStateOf(MaReChat.people()) }
    var open by remember { mutableStateOf<MaReChat.Person?>(null) }
    var asking by remember { mutableStateOf<String?>(null) } // slug, or "*" for everyone
    BackHandler { if (open != null) open = null else onBack() }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        val o = open
        if (o != null) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                item {
                    Text(o.name, color = MaHost.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    for (line in o.text.lines()) {
                        when {
                            line.startsWith("# ") -> {}
                            line.startsWith("## ") -> Text(
                                line.removePrefix("## "), color = MaHost.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                            line.isBlank() -> Spacer(Modifier.size(4.dp))
                            else -> Text(line, color = MaHost.Ink, fontSize = 16.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
        } else if (people.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No one yet. Open RE from a chat with someone; after Accept or Clear RE writes what it learned about them.",
                    color = MaHost.Muted, fontSize = 16.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(people, key = { _, p -> p.slug }) { _, p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(MaHost.KeyShape).background(MaHost.Key)
                            .clickable { open = p }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).background(MaHost.Bg, CircleShape).border(1.5.dp, MaHost.RingSwitch, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(p.name.take(1).uppercase(), color = MaHost.Sand, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, color = MaHost.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            val last = p.text.lines().lastOrNull { it.isNotBlank() && !it.startsWith("#") }.orEmpty()
                            Text(last.trim().removePrefix("-").trim(), color = MaHost.Muted, fontSize = 14.sp, maxLines = 2)
                            Text(
                                android.text.format.DateUtils.getRelativeTimeSpanString(p.updated).toString(),
                                color = MaHost.Muted, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).padding(bottom = 4.dp)) {
            MaHostKey("Back", Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.ArrowBack) { if (open != null) open = null else onBack() }
            MaHostKey(
                if (o != null) "Clear ${o.name.substringBefore(' ')}" else "Clear all",
                Modifier.weight(1f), icon = Icons.Default.DeleteSweep, ink = MaHost.DangerInk,
                enabled = o != null || people.isNotEmpty(),
            ) { asking = o?.slug ?: "*" }
        }
    }

    asking?.let { which ->
        HostDialog(
            title = if (which == "*") "Clear every person?" else "Clear ${open?.name ?: "this person"}?",
            text = "The summaries, the flow and the style notes are deleted, here and in the GitHub copy at the next sync.",
            confirmLabel = "Delete",
            onConfirm = {
                if (which == "*") MaReChat.deleteAllPeople() else MaReChat.deletePerson(which)
                asking = null
                open = null
                people = MaReChat.people()
            },
            onDismiss = { asking = null },
        )
    }
}
