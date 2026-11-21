/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.ui.MaHost
import dev.patrickgold.florisboard.dictate.ui.MaHostKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RE = Reply, a Claude-style chat above the keyboard (Marko, 15.11.2026).
 *
 * First version (same day): "the experience of typing in RE very similar to typing in any other software ... the same entry
 * box ... at the end of the pop up accept and re-prompt". Second (same day): "This is not working as background service ...
 * I close it and it's supposed to work in background and then paste the message into the WhatsApp ... My prompts are gone
 * ... I want to see the history of my chat ... Claude style chat ... I can collapse or expand this kind of chat history ...
 * RE, we can call it now a reply ... stores all the history of my reply in one long chat ... learn of my usage habit".
 *
 * - **One long chat**: the RE thread (MaReChat chat.json, the same as the full chat, memory, lessons, people). His messages
 *   in a rounded box on the right, RE's answers as plain text across the width, the draft in gold on a key face (the
 *   Claude app's layout in the keyboard's colours). The history folds to the last exchange or opens over the whole space
 *   above the keyboard (the fold key, remembered).
 * - **Background**: the work runs in [MaReSession], not in the window. Close the pop-up while RE writes and RE goes on;
 *   when the draft is ready it goes into the field he came from (at once if the keyboard is on it, else when he comes back
 *   within 30 minutes) and a short message says so. Nothing is ever sent. Open RE again and the chat is there, with the
 *   answer or "RE is writing".
 * - **Entry box**: an ordinary field; tap it, type or dictate with H/E, Enter or send asks RE. The keyboard's RE key while
 *   the pop-up is open sends the box.
 * - **Keys at the end**: Accept, Reprompt (the same request from scratch; earlier tries one arrow back), Edit, Copy,
 *   Speak, fold / unfold, Close.
 * - **Habits**: every open, ask, reprompt, edit, accept, close-while-working and background insert is a line in
 *   re/HABITS_LOG.md ([MaReHabits]); RE's memory step turns it into notes/usage-habits.md.
 */
object MaRePopup {
    @Volatile var open = false
    /** The keyboard's RE key while the pop-up is open: "send what is in the box". */
    val sendNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/** The RE conversation and its work, alive for as long as the keyboard's process, whether the pop-up is open or not. */
object MaReSession {
    private const val FROM_SCRATCH =
        "Write it again from scratch, a fresh new version for the same request. Do not reuse the wording of your previous answer."

    val msgs = mutableStateListOf<MaReChat.Msg>()
    private var loaded = false
    var thinking by mutableStateOf(false)
    var status by mutableStateOf("")
    /** Every try for the newest request, newest last; [shown] is the one in the thread and the one Accept takes. */
    val tries = mutableStateListOf<MaReChat.Msg>()
    var shown by mutableIntStateOf(-1)
    var asked by mutableStateOf("")
    /** The pop-up is on screen; when false a finished draft goes into the field by itself. */
    @Volatile var visible = false
    private var answeredAt = 0L

    /** Where the background answer goes: the field RE was opened from when the request was made. */
    private data class Target(val pkg: String?, val fieldId: Int, val replace: Boolean)
    private var target: Target? = null

    /** The prefix of the message that shows the field's text in the chat ("before"), so his instruction follows it. */
    const val FIELD_PREFIX = "Message in the field:\n"

    /**
     * RE opened over a message (Marko, 21.11.2026: "beginning is the message and then I need to prompt after the message what
     * to change"): the field's text becomes his first line of this exchange, so the chat shows before (his message),
     * his instruction, and after (RE's draft). Not added twice for the same text.
     */
    fun showFieldText(text: String?) {
        val t = text?.trim().orEmpty()
        if (t.isEmpty() || thinking) return
        val last = msgs.lastOrNull { it.me && it.text.startsWith(FIELD_PREFIX) }
        if (last != null && last.text == FIELD_PREFIX + t && msgs.lastOrNull() == last) return
        msgs.add(MaReChat.Msg(true, FIELD_PREFIX + t)); persist()
    }

    /** Reads the thread from the phone when RE is idle (the full chat may have changed it); a running request keeps its list. */
    fun ensureLoaded() {
        if (loaded && thinking) return
        loaded = true
        msgs.clear(); msgs.addAll(MaReChat.load())
    }

    private fun persist() = MaReChat.save(msgs.toList())

    fun latestAnswer(): MaReChat.Msg? = msgs.lastOrNull()?.takeIf { !it.me }

    fun textOf(m: MaReChat.Msg) = m.draft ?: m.shown

    fun send(app: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        ensureLoaded()
        MaReader.stop()
        val last = latestAnswer()
        if (last != null && MaReChat.looksLikeCorrection(t)) {
            // Echo the original RE message as a ME bubble before the correction so Marko sees what he is correcting.
            msgs.add(MaReChat.Msg(true, "\u21a9 correcting: " + last.shown.take(300)))
            MaReChat.scope.launch { MaReChat.lesson(app, last.shown, t, "corrected RE in the pop-up") }
        }
        asked = t
        tries.clear(); shown = -1
        msgs.add(MaReChat.Msg(true, t)); persist()
        MaReHabits.note("asked", "${t.length} chars" + (if (last != null) ", follow-up" else ""))
        run(app, null)
    }

    fun reprompt(app: Context) {
        val c = latestAnswer() ?: return
        if (thinking) return
        MaReader.stop()
        if (tries.isEmpty()) { tries.add(c); shown = 0 }
        msgs.removeAll { it.at == c.at }; persist()
        MaReHabits.note("reprompt", "try ${tries.size + 1}")
        run(app, FROM_SCRATCH)
    }

    /**
     * Re-asks the last question with a specific model override (H / S / O / Q buttons under the answer).
     * [engine] is one of MaReEngine.AUTO / SONNET / OPUS / QWEN.
     * The override is set before the call and cleared by [run] after the answer arrives.
     */
    fun retryWith(app: Context, engine: String) {
        val c = latestAnswer() ?: return
        if (thinking) return
        MaReader.stop()
        MaReEngine.override = engine
        if (tries.isEmpty()) { tries.add(c); shown = 0 } else tries.add(c)
        msgs.removeAll { it.at == c.at }; persist()
        MaReHabits.note("retry with $engine")
        run(app, null)
    }

    /** The arrows: the chosen try becomes the answer in the thread. */
    fun showTry(i: Int) {
        if (i !in tries.indices || thinking) return
        latestAnswer()?.let { a -> msgs.removeAll { it.at == a.at } }
        msgs.add(tries[i]); persist()
        shown = i
    }

    private fun run(app: Context, extra: String?) {
        thinking = true
        status = ""
        target = Target(MaReChat.pkg, MaReChat.fieldId, MaReChat.fieldText != null)
        val sofar = msgs.toList() + listOfNotNull(extra?.let { MaReChat.Msg(true, it) })
        val started = System.currentTimeMillis()
        // MaReChat.scope, not the window's: closing the pop-up does not stop RE.
        MaReChat.scope.launch {
            val r = runCatching { MaReChat.ask(app, sofar) { s -> MaReChat.scope.launch(Dispatchers.Main) { status = s } } }
            // Clear any per-request model override now that the answer arrived.
            MaReEngine.override = null
            withContext(Dispatchers.Main) {
                thinking = false
                r.onSuccess { a ->
                    status = ""
                    val m = MaReChat.Msg(false, a.ifBlank { "(no answer)" })
                    msgs.add(m); persist()
                    tries.add(m); shown = tries.size - 1
                    answeredAt = System.currentTimeMillis()
                    MaReHabits.note("answered", "${(answeredAt - started) / 1000} s, try ${tries.size}" + if (visible) "" else ", pop-up closed")
                    if (!visible) deliver(app, m)
                }.onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    status = "RE could not answer: " + (e.message ?: e.javaClass.simpleName).take(120)
                    if (!visible) Toast.makeText(app, "RE: " + status, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** The pop-up was closed while RE wrote: the draft goes into the field by itself (never sent). */
    private fun deliver(app: Context, m: MaReChat.Msg) {
        val d = m.draft
        val t = target
        if (d == null || t == null || t.pkg == null) {
            Toast.makeText(app, "RE answered. Tap RE to read it.", Toast.LENGTH_LONG).show()
            return
        }
        if (MaReResult.deliverInBackground(d, t.pkg, t.fieldId, asked, t.replace)) {
            MaReHabits.note("background insert", "${d.length} chars")
            Toast.makeText(app, "RE: reply put into the message field. Not sent.", Toast.LENGTH_LONG).show()
            val snapshot = msgs.toList()
            MaReChat.scope.launch { MaReChat.learn(app, snapshot, "kept (a draft went into the field in the background)") }
        }
    }

    fun noteAccepted(edited: Boolean) {
        val wait = if (answeredAt > 0) (System.currentTimeMillis() - answeredAt) / 1000 else -1
        MaReHabits.note("accepted", "try ${shown + 1}/${tries.size.coerceAtLeast(1)}" + (if (edited) ", edited" else "") + ", after $wait s")
    }

    fun noteClosed() {
        MaReHabits.note(if (thinking) "closed while RE writes (background)" else "closed")
    }
}

private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ma_re_chat", Context.MODE_PRIVATE)

@Composable
internal fun RePopupScreen(
    onAccept: (String, String) -> Unit,
    onClose: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenChat: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val s = MaReSession
    remember { s.ensureLoaded(); s.showFieldText(MaReChat.fieldText); 0 }
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(prefs(app).getBoolean("popup_expanded", false)) }
    val current = s.latestAnswer()
    val speaking = MaReader.state != MaReader.State.IDLE

    LaunchedEffect(Unit) {
        MaReHabits.note("opened", (MaReChat.fieldText?.let { "field ${it.length} chars" } ?: "empty field") +
            (MaReChat.screen?.let { ", screen by ${it.how}" } ?: ""))
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        s.visible = true
        onDispose { s.visible = false }
    }

    fun send() {
        val t = input
        if (t.isBlank() || s.thinking) return
        input = ""
        editing = false
        s.send(app, t)
    }

    fun accept() {
        val m = current ?: return
        val final = if (editing) editText.trim() else s.textOf(m)
        val edited = editing && !MaReSkill.same(s.textOf(m), final)
        if (edited) MaReChat.scope.launch { MaReChat.lesson(app, s.textOf(m), final, "edited the draft before accepting it") }
        s.noteAccepted(edited)
        val snapshot = s.msgs.toList()
        MaReChat.scope.launch { MaReChat.learn(app, snapshot, "kept (a draft was accepted in the pop-up)") }
        onAccept(final, s.asked)
    }

    fun close() { s.noteClosed(); onClose() }

    LaunchedEffect(Unit) { MaRePopup.sendNow.collect { send() } }
    BackHandler { close() }

    val listState = rememberLazyListState()
    // Folded: the last exchange only. Unfolded: the whole chat.
    // Folded: from his message in the field (the "before") when it leads the newest exchange.
    val lastMe = s.msgs.indexOfLast { it.me }.takeIf { it >= 0 } ?: 0
    val showFrom = if (expanded) 0 else if (lastMe > 0 && s.msgs[lastMe - 1].me && s.msgs[lastMe - 1].text.startsWith(MaReSession.FIELD_PREFIX)) lastMe - 1 else lastMe
    val visibleMsgs = s.msgs.drop(showFrom)
    LaunchedEffect(visibleMsgs.size, s.thinking, expanded) {
        val n = visibleMsgs.size + (if (s.thinking) 1 else 0)
        if (n > 0) listState.scrollToItem(n - 1)
    }
    val screenH = LocalConfiguration.current.screenHeightDp
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { close() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .let { if (expanded) it.fillMaxSize() else it }
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaHost.Bg)
                .border(1.dp, MaHost.Key, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(top = 6.dp, start = 3.dp, end = 3.dp, bottom = 2.dp),
        ) {
            // ---- the chat: folded = the last exchange, unfolded = everything
            if (visibleMsgs.isNotEmpty() || s.thinking) {
                val older = showFrom
                if (!expanded && older > 0) {
                    Text(
                        "⌃  $older earlier messages", color = MaHost.Muted, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true; prefs(app).edit().putBoolean("popup_expanded", true).apply() }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().let { if (expanded) it.weight(1f) else it.heightIn(max = (screenH * 0.40f).dp) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(visibleMsgs, key = { _, m -> m.at.toString() + m.me }) { i, m ->
                        val newest = i == visibleMsgs.lastIndex && !m.me
                        if (m.me && m.text.startsWith(MaReSession.FIELD_PREFIX)) FieldBox(m.text.removePrefix(MaReSession.FIELD_PREFIX))
                        else if (m.me) MineBox(m.text) else ReText(m, editing = editing && newest, editText = editText, onEditText = { editText = it })
                    }
                    if (s.thinking) item { Text("RE is writing…  (you can close this; RE goes on)", color = MaHost.Muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                }
                if (s.tries.size > 1 && !s.thinking) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                        MaHostKey("‹", enabled = s.shown > 0, padH = 10) { editing = false; s.showTry(s.shown - 1) }
                        Text("try ${s.shown + 1}/${s.tries.size}", color = MaHost.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        MaHostKey("›", enabled = s.shown < s.tries.size - 1, padH = 10) { editing = false; s.showTry(s.shown + 1) }
                    }
                }
            } else if (expanded) {
                Spacer(Modifier.weight(1f))
            }
            if (s.status.isNotBlank()) {
                Text(s.status, color = MaHost.Muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
            // ---- the entry box
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).padding(horizontal = 3.dp, vertical = 4.dp)
                        .clip(MaHost.KeyShape).background(MaHost.Key)
                        .border(1.5.dp, MaHost.RingSwitch, MaHost.KeyShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) Text(
                        when {
                            current != null -> "Reply to RE: shorter, warmer, add…"
                            MaReChat.fieldText != null -> "What should RE do with your text?"
                            else -> (MaReChat.person?.let { "To $it: " } ?: "") + "what should RE write?"
                        },
                        color = MaHost.Muted, fontSize = 17.sp, maxLines = 1,
                    )
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = MaHost.Ink, fontSize = 17.sp),
                        cursorBrush = SolidColor(MaHost.Gold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                    )
                }
                MaHostKey(null, icon = Icons.AutoMirrored.Filled.Send, ink = MaHost.Gold, enabled = input.isNotBlank() && !s.thinking) { send() }
            }
            // ---- model selector: H / S / O / Q (tap to regenerate last answer with that model)
            if (current != null && !s.thinking) {
                val currentEngine = MaReEngine.engine()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "ask with:", color = MaHost.Muted, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                    )
                    for ((label, engine) in listOf("H" to MaReEngine.AUTO, "S" to MaReEngine.SONNET, "O" to MaReEngine.OPUS, "Q" to MaReEngine.QWEN)) {
                        MaHostKey(
                            label, fontSize = 13, padH = 8,
                            ring = if (engine == currentEngine) MaHost.Gold else null,
                            ink = if (engine == currentEngine) MaHost.Gold else MaHost.Muted,
                            lit = engine == currentEngine,
                        ) { editing = false; s.retryWith(app, engine) }
                    }
                }
            }
            // ---- the keys at the end
            Row(modifier = Modifier.fillMaxWidth()) {
                MaHostKey(
                    "Accept", Modifier.weight(1.6f), icon = Icons.Default.Check, ring = MaHost.RingOn,
                    enabled = current != null && !s.thinking && MaReChat.pkg != null, fontSize = 13, padH = 4,
                ) { accept() }
                MaHostKey(
                    "Reprompt", Modifier.weight(1.8f), icon = Icons.Default.Refresh, ring = MaHost.RingSwitch,
                    enabled = current != null && !s.thinking, fontSize = 13, padH = 4,
                ) { editing = false; s.reprompt(app) }
                MaHostKey(
                    null, Modifier.weight(0.8f), icon = if (editing) Icons.AutoMirrored.Filled.Undo else Icons.Default.Edit,
                    lit = editing, enabled = current != null && !s.thinking,
                ) {
                    val c = current ?: return@MaHostKey
                    if (editing) editing = false else { editText = s.textOf(c); editing = true }
                }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.Default.ContentCopy, enabled = current != null) {
                    val c = current ?: return@MaHostKey
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("RE", if (editing) editText else s.textOf(c)))
                    MaReHabits.note("copied")
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                MaHostKey(
                    null, Modifier.weight(0.8f), icon = if (speaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    lit = speaking, enabled = current != null,
                ) {
                    val c = current ?: return@MaHostKey
                    if (speaking) MaReader.stop() else MaReader.speakText(app, s.textOf(c)) { }
                }
                MaHostKey(
                    null, Modifier.weight(0.8f), icon = if (expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                    ring = MaHost.RingSwitch, lit = expanded,
                ) {
                    expanded = !expanded
                    prefs(app).edit().putBoolean("popup_expanded", expanded).apply()
                    MaReHabits.note(if (expanded) "history opened" else "history folded")
                }
                // Reset: wipe only the visible thread; memory files (TAKEOVER, LESSONS, SKILLS) are kept.
                MaHostKey(
                    null, Modifier.weight(0.8f), icon = Icons.Default.DeleteSweep,
                    ink = MaHost.DangerInk, enabled = s.msgs.isNotEmpty() && !s.thinking,
                ) {
                    s.msgs.clear()
                    s.tries.clear()
                    s.shown = -1
                    MaReChat.resetSession()
                    MaReHabits.note("chat reset from popup")
                }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.Default.Close) { close() }
            }
        }
    }
}

/**
 * Long press on any message in RE's history copies it to the clipboard (Marko, 15.11.2026: "for any message from the
 * history, I can long press to the buffer, clipboard"). An RE answer copies its draft when it has one, else its text.
 * A short buzz and "Copied" say it worked. Used by the pop-up and the full chat.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.maCopyOnLongPress(text: String): Modifier {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        onClick = {},
        onLongClick = {
            if (text.isBlank()) return@combinedClickable
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("RE", text))
            MaReHabits.note("copied from history", "${text.length} chars")
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
    )
}

/** The message he had written in the field when he opened RE: the "before", labelled, across the width. */
@Composable
private fun FieldBox(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            .clip(MaHost.KeyShape).background(MaHost.Key).border(1.dp, MaHost.RingSwitch, MaHost.KeyShape)
            .maCopyOnLongPress(text).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Your message", color = MaHost.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = MaHost.Ink, fontSize = 16.sp)
    }
}

/** His message: a rounded box on the right (the Claude app's user turn), in the keyboard's colours. */
@Composable
private fun MineBox(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier.widthIn(max = 320.dp).padding(start = 40.dp)
                .clip(RoundedCornerShape(18.dp)).background(MaHost.Mine).maCopyOnLongPress(text).padding(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(text, color = MaHost.Ink, fontSize = 16.sp) }
    }
}

/** RE's answer: plain text across the width (the Claude app's assistant turn); the draft in gold on a key face. */
@Composable
private fun ReText(m: MaReChat.Msg, editing: Boolean, editText: String, onEditText: (String) -> Unit) {
    val d = m.draft
    val talk = if (d != null) m.shown.replace(d, "").trim() else m.shown
    Column(modifier = Modifier.fillMaxWidth().let { if (editing) it else it.maCopyOnLongPress(d ?: m.shown) }.padding(horizontal = 6.dp)) {
        if (talk.isNotEmpty() && !(d == null && editing)) Text(talk, color = MaHost.Ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = if (d != null) 6.dp else 0.dp))
        if (d != null || editing) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(MaHost.KeyShape).background(MaHost.Key)
                    .border(1.dp, MaHost.Gold.copy(alpha = 0.45f), MaHost.KeyShape).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (editing) BasicTextField(
                    value = editText, onValueChange = onEditText,
                    textStyle = TextStyle(color = MaHost.Gold, fontSize = 17.sp),
                    cursorBrush = SolidColor(MaHost.Gold),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                ) else Text(d ?: "", color = MaHost.Gold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Text(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, fontWeight: FontWeight? = null) =
    androidx.compose.material3.Text(text, color = color, fontSize = fontSize, modifier = modifier, maxLines = maxLines, fontWeight = fontWeight)
