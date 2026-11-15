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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 * RE as a pop-up above the keyboard (Marko, 15.11.2026): "make the experience of typing in RE very similar to typing in
 * any other software ... the same entry box. I need to click with the finger to the entry box. Then I press the buttons.
 * RE ... just pop up above my keyboard and I interact as usual. So, there is no special interface. Only at the end of the
 * pop up I have button accept ... and re-prompt, meaning write starting from the scratch and do one more try for the
 * same question."
 *
 * - The app behind stays visible; the pop-up sits at the bottom and rides up on the keyboard when it opens.
 * - An ordinary entry box: tap it, the keyboard (TTT itself, with H/E dictation) opens, type, Enter or the send key asks
 *   RE. Every new instruction refines the current draft (the same RE thread, memory, lessons and people as the chat).
 * - RE's answer above the box: the draft in gold. Several tries are kept: the arrows step between them.
 * - The keys at the end: Accept (into the field behind), Reprompt (the same question again, written from scratch; the
 *   earlier tries stay one arrow away), Edit (change the draft by hand; the change is a lesson), Copy, Speak, Chat
 *   (the full chat with the history), Close.
 * - The RE key on the keyboard pressed while the pop-up is open sends the box (it never opens a second pop-up).
 */
object MaRePopup {
    @Volatile var open = false
    /** The keyboard's RE key while the pop-up is open: "send what is in the box". */
    val sendNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

private const val FROM_SCRATCH =
    "Write it again from scratch, a fresh new version for the same request. Do not reuse the wording of your previous answer."

@Composable
internal fun RePopupScreen(
    onAccept: (String, String) -> Unit,
    onClose: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    // The RE thread itself (null partner): the pop-up and the chat are two views of one conversation.
    val thread = remember { mutableStateListOf<MaReChat.Msg>().apply { addAll(MaReChat.load()) } }
    fun persist() = MaReChat.save(thread.toList())
    // What this pop-up produced: every try, newest last; [shown] is the one on screen and the one Accept takes.
    val tries = remember { mutableStateListOf<MaReChat.Msg>() }
    var shown by remember { mutableIntStateOf(-1) }
    var asked by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val current = tries.getOrNull(shown)
    val speaking = MaReader.state != MaReader.State.IDLE

    fun ask(extra: String? = null) {
        thinking = true
        status = ""
        val sofar = thread.toList() + listOfNotNull(extra?.let { MaReChat.Msg(true, it) })
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { MaReChat.ask(app, sofar) { s -> scope.launch { status = s } } } }
            thinking = false
            r.onSuccess { a ->
                status = ""
                val m = MaReChat.Msg(false, a.ifBlank { "(no answer)" })
                thread.add(m); persist()
                tries.add(m); shown = tries.size - 1
                editing = false
            }.onFailure { e ->
                if (e is CancellationException) return@onFailure
                status = "RE could not answer: " + (e.message ?: e.javaClass.simpleName).take(120)
            }
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || thinking) return
        MaReader.stop()
        // He corrects the draft on screen: that is a lesson at once, as in the chat.
        val last = current
        if (last != null && MaReChat.looksLikeCorrection(t)) {
            MaReChat.scope.launch { MaReChat.lesson(app, last.shown, t, "corrected RE in the pop-up") }
        }
        input = ""
        asked = t
        thread.add(MaReChat.Msg(true, t)); persist()
        ask()
    }

    fun reprompt() {
        if (thinking || asked.isBlank() && current == null) return
        MaReader.stop()
        // The try on screen leaves the thread (so RE does not build on it) but stays in [tries], one arrow back.
        current?.let { c -> thread.removeAll { it.at == c.at }; persist() }
        ask(extra = FROM_SCRATCH)
    }

    fun textOf(m: MaReChat.Msg) = m.draft ?: m.shown

    fun accept() {
        val m = current ?: return
        val final = if (editing) editText.trim() else textOf(m)
        if (editing && !MaReSkill.same(textOf(m), final)) {
            MaReChat.scope.launch { MaReChat.lesson(app, textOf(m), final, "edited the draft before accepting it") }
        }
        val snapshot = thread.toList()
        MaReChat.scope.launch { MaReChat.learn(app, snapshot, "kept (a draft was accepted in the pop-up)") }
        onAccept(final, asked)
    }

    LaunchedEffect(Unit) { MaRePopup.sendNow.collect { send() } }
    BackHandler { onClose() }

    val maxAnswer = (LocalConfiguration.current.screenHeightDp * 0.38f).dp
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        // Above the pop-up: the app behind, a tap there closes RE like tapping outside any pop-up.
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onClose() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaHost.Bg)
                .border(1.dp, MaHost.Key, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(top = 8.dp, start = 3.dp, end = 3.dp, bottom = 2.dp),
        ) {
            // ---- RE's answer (only once there is one, or while it writes)
            if (current != null || thinking) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(max = maxAnswer).padding(horizontal = 3.dp)
                        .clip(MaHost.KeyShape).background(MaHost.Key).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    when {
                        thinking -> Text("RE is writing…", color = MaHost.Muted, fontSize = 17.sp)
                        editing -> BasicTextField(
                            value = editText, onValueChange = { editText = it },
                            textStyle = TextStyle(color = MaHost.Gold, fontSize = 17.sp),
                            cursorBrush = SolidColor(MaHost.Gold),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        )
                        current != null -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            val d = current.draft
                            val talk = if (d != null) current.shown.replace(d, "").trim() else ""
                            if (talk.isNotEmpty()) Text(talk, color = MaHost.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                            Text(d ?: current.shown, color = if (d != null) MaHost.Gold else MaHost.Ink, fontSize = 17.sp)
                        }
                    }
                }
                if (tries.size > 1 && !thinking) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                        MaHostKey("‹", enabled = shown > 0, padH = 10) { shown--; editing = false }
                        Text("try ${shown + 1}/${tries.size}", color = MaHost.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        MaHostKey("›", enabled = shown < tries.size - 1, padH = 10) { shown++; editing = false }
                    }
                }
            }
            if (status.isNotBlank()) {
                Text(status, color = MaHost.Muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
            // ---- the entry box: an ordinary field; tap it and type or dictate with the keyboard as anywhere else
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
                            current != null -> "Change it: shorter, warmer, add…"
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
                MaHostKey(null, icon = Icons.AutoMirrored.Filled.Send, ink = MaHost.Gold, enabled = input.isNotBlank() && !thinking) { send() }
            }
            // ---- the keys at the end of the pop-up
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                MaHostKey(
                    "Accept", Modifier.weight(1.6f), icon = Icons.Default.Check, ring = MaHost.RingOn,
                    enabled = current != null && !thinking && MaReChat.pkg != null, fontSize = 13, padH = 4,
                ) { accept() }
                MaHostKey(
                    "Reprompt", Modifier.weight(1.8f), icon = Icons.Default.Refresh, ring = MaHost.RingSwitch,
                    enabled = current != null && !thinking, fontSize = 13, padH = 4,
                ) { reprompt() }
                MaHostKey(
                    null, Modifier.weight(0.8f), icon = if (editing) Icons.AutoMirrored.Filled.Undo else Icons.Default.Edit,
                    lit = editing, enabled = current != null && !thinking,
                ) {
                    val c = current ?: return@MaHostKey
                    if (editing) editing = false else { editText = textOf(c); editing = true }
                }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.Default.ContentCopy, enabled = current != null) {
                    val c = current ?: return@MaHostKey
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("RE", if (editing) editText else textOf(c)))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                MaHostKey(
                    null, Modifier.weight(0.8f), icon = if (speaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    lit = speaking, enabled = current != null,
                ) {
                    val c = current ?: return@MaHostKey
                    if (speaking) MaReader.stop() else MaReader.speakText(app, textOf(c)) { }
                }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.AutoMirrored.Filled.OpenInNew, ring = MaHost.RingSwitch) { onOpenChat() }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.Default.Close) { onClose() }
            }
        }
    }
}

@Composable
private fun Text(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, fontWeight: FontWeight? = null) =
    androidx.compose.material3.Text(text, color = color, fontSize = fontSize, modifier = modifier, maxLines = maxLines, fontWeight = fontWeight)
