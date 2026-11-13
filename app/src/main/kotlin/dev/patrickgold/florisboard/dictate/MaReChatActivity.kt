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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.patrickgold.florisboard.dictate.engines.EngineChain
import dev.patrickgold.florisboard.dictate.engines.EngineStore
import dev.patrickgold.florisboard.dictate.engines.FailKind
import dev.patrickgold.florisboard.dictate.engines.FallbackRunner
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
 * The RE Magic chat window (Marko, 13.11.2026), in the look of Facebook Messenger: his messages on the right in blue,
 * RE's on the left in grey with its round avatar, a "typing" bubble while RE thinks, a pill composer at the bottom.
 *
 * Options at the top: **Close** (closes, nothing happens), **Accept** (RE's last draft goes into the field behind),
 * **Clear** (RE writes TAKEOVER, LESSONS and SKILLS from the chat, then the chat is wiped).
 *
 * Switches at the bottom: **Text / Voice** and **Sound**. In Voice mode he taps the microphone, speaks, taps again; his
 * words are transcribed with the engine of his H/E key and appear as his bubble. RE's answer is shown and, with Sound
 * on, read aloud by the Reader voice: the sentence being read and the word being said are lit in the bubble, the same
 * engine as the Reader. With "Where the reading is shown" set to subtitles, the subtitle box of the keyboard is shown
 * here too, in the same style.
 */
class MaReChatActivity : ComponentActivity() {

    private var accepted = false
    private var recorder: RecordingController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaReChat.ensureMemory()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
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
        internal val BG = Color(0xFF000000)
        internal val ME = Color(0xFF0084FF)
        internal val THEM = Color(0xFF303030)
        internal val DIM = Color(0xFF8A8D91)

        fun open(context: Context) {
            context.startActivity(
                Intent(context.applicationContext, MaReChatActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }

        /** The RE key: remember the field, read the screen behind, then open the chat over it. */
        fun openFromKey(context: Context, pkg: String?, fieldId: Int) {
            MaReChat.pkg = pkg?.takeIf { it != context.packageName }
            MaReChat.fieldId = fieldId
            MaReprompt.noteField(pkg, fieldId)
            MaReChat.screen = null
            MaReChat.scope.launch {
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
    var speakingAt by remember { mutableStateOf(-1L) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val prefs by FlorisPreferenceStore
    val display by prefs.dictate.maReaderDisplay.collectPrefAsState()

    LaunchedEffect(msgs.size, thinking) {
        val n = msgs.size + (if (thinking) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    fun speak(m: MaReChat.Msg) {
        if (muted) return
        speakingAt = m.at
        MaReader.speakText(app, m.shown) { }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        MaReader.stop()
        msgs.add(MaReChat.Msg(true, t))
        MaReChat.save(msgs.toList())
        thinking = true
        status = "typing…"
        val sofar = msgs.toList()
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { MaReChat.ask(app, sofar) { s -> scope.launch { status = s } } } }
            thinking = false
            status = ""
            r.onSuccess { a ->
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

    fun startListening() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is off for TTT"
            return
        }
        MaReader.stop()
        val r = RecordingController(app)
        runCatching { r.start() }.onFailure { status = "Microphone busy: " + (it.message ?: ""); return }
        setRecorder(r)
        recording = true
        status = "Listening… tap to send"
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
        val lang = MaLanguage.active()
        val engine = EngineStore.selected(app, lang)
        scope.launch {
            // His H/E engine only, never another one; no answer or no internet: 3 s and the same engine again, 10 times.
            var attempt = 0
            var words: String? = null
            while (words == null) {
                status = "Transcribing · " + engine + (if (attempt > 0) " · retry $attempt/10" else "")
                val res = runCatching {
                    withContext(Dispatchers.IO) {
                        val w = MaResample.toTargetRate(mine) ?: mine
                        try { EngineAdapters.run(app, engine, w, lang, EngineChain.names()) } finally { if (w !== mine) w.delete() }
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

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        // ---- header, Messenger style
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(40)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("RE Magic", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val sub = when {
                    thinking -> "typing…"
                    status.isNotBlank() -> status
                    else -> "Active now · " + MaReEngine.label()
                }
                Text(sub, color = if (thinking) MaReChatActivity.ME else MaReChatActivity.DIM, fontSize = 13.sp, maxLines = 2)
            }
        }
        // ---- the three options
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val lastRe = msgs.lastOrNull { !it.me }
            val acceptText = lastRe?.let { it.draft ?: it.shown }
            Option(Icons.Default.Close, "Close", Modifier.weight(1f), onClick = onClose)
            Option(
                Icons.Default.Check, "Accept", Modifier.weight(1f), strong = true, enabled = !acceptText.isNullOrBlank() && MaReChat.pkg != null,
            ) {
                val text = acceptText ?: return@Option
                val asked = msgs.lastOrNull { it.me }?.text.orEmpty()
                val snapshot = msgs.toList()
                // Accepting is a strong signal of his style: RE learns from it after the window has gone.
                MaReChat.scope.launch { MaReChat.learn(app, snapshot, "kept (a draft was accepted)") }
                onAccept(text, asked)
            }
            Option(Icons.Default.DeleteSweep, "Clear", Modifier.weight(1f), enabled = msgs.isNotEmpty() && !clearing) { confirmClear = true }
        }
        if (MaReChat.pkg == null) {
            Text(
                "Opened without a text field: Accept is off.",
                color = MaReChatActivity.DIM, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // ---- the chat
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (msgs.isEmpty() && !thinking) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(84)
                        Text("RE Magic", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            "Ask me what to write. I read the chat behind and my notes about you.",
                            color = MaReChatActivity.DIM, fontSize = 15.sp, modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            itemsIndexed(msgs, key = { _, m -> m.at.toString() + m.me }) { i, m ->
                val nextSame = i + 1 < msgs.size && msgs[i + 1].me == m.me
                Bubble(m, showAvatar = !m.me && !nextSame, speaking = speakingAt == m.at && MaReader.state != MaReader.State.IDLE)
            }
            if (thinking) item { TypingBubble() }
        }
        // ---- the subtitle box, when the Reader shows subtitles
        if (display == "subtitle" && MaReader.state != MaReader.State.IDLE && speakingAt > 0) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { MaSubtitleRow() }
        }
        // ---- composer
        if (voice) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val busy = transcribing || thinking
                Box(
                    modifier = Modifier.size(76.dp).background(
                        if (recording) Color(0xFFE53935) else if (busy) Color(0xFF3A3B3C) else MaReChatActivity.ME, CircleShape,
                    ).clickable(enabled = !busy) { if (recording) stopListening() else startListening() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, "Talk", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Text(
                    when { recording -> "Listening… tap to send"; transcribing -> "Transcribing…"; thinking -> "RE is answering…"; else -> "Tap and talk" },
                    color = MaReChatActivity.DIM, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).background(Color(0xFF242526), RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    if (input.isEmpty()) Text("Aa", color = MaReChatActivity.DIM, fontSize = 17.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        cursorBrush = SolidColor(MaReChatActivity.ME),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                    )
                }
                Box(
                    modifier = Modifier.padding(start = 8.dp).size(44.dp).clickable(enabled = input.isNotBlank() && !thinking) {
                        val t = input; input = ""; send(t)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (input.isNotBlank()) MaReChatActivity.ME else MaReChatActivity.DIM, modifier = Modifier.size(28.dp))
                }
            }
        }
        // ---- the switches
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segment(Icons.Default.Keyboard, "Text", !voice, Modifier.weight(1f)) {
                if (recording) { recorderHolder()?.cancel(); setRecorder(null); recording = false }
                voice = false; MaReChat.setVoiceMode(app, false)
            }
            Segment(Icons.Default.Mic, "Voice", voice, Modifier.weight(1f)) { voice = true; MaReChat.setVoiceMode(app, true) }
            Segment(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Muted" else "Sound", !muted, Modifier.weight(1f)) {
                muted = !muted
                MaReChat.setMuted(app, muted)
                if (muted) MaReader.stop()
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the chat?") },
            text = { Text("RE first writes what it learned into its takeover, lessons and skills files. Then the chat is deleted; only those files stay.", fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
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
                        status = ""
                        Toast.makeText(
                            app,
                            if (n > 0) "Chat cleared · RE kept $n memory file(s)" else "Chat cleared · RE could not update its memory this time",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Clear", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Avatar(sizeDp: Int) {
    Box(
        modifier = Modifier.size(sizeDp.dp).background(
            Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF8E2DE2), Color(0xFFFF6A88))), CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text("RE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (sizeDp * 0.38f).sp)
    }
}

@Composable
private fun Bubble(m: MaReChat.Msg, showAvatar: Boolean, speaking: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.me) {
            Box(modifier = Modifier.size(28.dp)) { if (showAvatar) Avatar(28) }
            Spacer(Modifier.size(6.dp))
        }
        val shape = if (m.me) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
                .background(if (m.me) MaReChatActivity.ME else MaReChatActivity.THEM, shape)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            if (speaking) {
                SpokenText()
            } else {
                val draft = m.draft
                val talk = if (draft != null) m.text.replace(Regex("<draft>[\\s\\S]*?</draft>", RegexOption.IGNORE_CASE), "").trim() else m.shown
                if (talk.isNotBlank()) Text(talk, color = Color.White, fontSize = 17.sp, lineHeight = 23.sp)
                if (draft != null) {
                    Column(
                        modifier = Modifier.padding(top = if (talk.isNotBlank()) 8.dp else 0.dp).fillMaxWidth()
                            .background(Color(0xFF1E1F21), RoundedCornerShape(12.dp))
                            .border(1.dp, MaReChatActivity.ME.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Text("DRAFT · Accept puts it in your app", color = MaReChatActivity.ME, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(draft, color = Color.White, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * The answer being read, from the Reader's own word list: the sentence being read is lit, the word being said is
 * yellow and bold. Same index the Reader's karaoke uses, so the two never disagree.
 */
@Composable
private fun SpokenText() {
    val words = MaReader.allWords
    val idx = MaReader.currentIndex
    if (words.isEmpty()) {
        Text("…", color = Color.White, fontSize = 17.sp)
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
                i == idx -> withStyle(SpanStyle(color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, background = Color(0x55FFEB3B))) { append(w.text) }
                idx in words.indices && i in start..end -> withStyle(SpanStyle(background = Color(0x33FFFFFF))) { append(w.text) }
                else -> append(w.text)
            }
        }
    }
    Text(text, color = Color.White, fontSize = 17.sp, lineHeight = 23.sp)
}

@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.Bottom) {
        Avatar(28)
        Spacer(Modifier.size(6.dp))
        Row(
            modifier = Modifier.background(MaReChatActivity.THEM, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for (i in 0..2) {
                val a by t.animateFloat(
                    initialValue = 0.25f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i",
                )
                Box(Modifier.size(8.dp).alpha(a).background(Color(0xFFB0B3B8), CircleShape))
            }
        }
    }
}

@Composable
private fun Option(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    strong: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.heightIn(min = 46.dp)
            .background(if (strong && enabled) MaReChatActivity.ME else Color(0xFF242526), RoundedCornerShape(23.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Segment(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.heightIn(min = 42.dp)
            .background(if (on) Color(0xFF2D3B55) else Color(0xFF18191A), RoundedCornerShape(21.dp))
            .border(1.dp, if (on) MaReChatActivity.ME else Color(0xFF3A3B3C), RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) Color.White else MaReChatActivity.DIM, modifier = Modifier.size(18.dp))
        Text(label, color = if (on) Color.White else MaReChatActivity.DIM, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
