/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.MaHelpChat
import dev.patrickgold.florisboard.dictate.ui.MaHost
import dev.patrickgold.florisboard.dictate.ui.MaHostKey
import dev.patrickgold.florisboard.dictate.ui.MaFix
import dev.patrickgold.florisboard.dictate.ui.MaFixMessage
import dev.patrickgold.florisboard.dictate.ui.MaModelBadge
import dev.patrickgold.florisboard.dictate.ui.rememberKeyFixPicker
import kotlinx.coroutines.launch

private val BalloonMine = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
private val BalloonClaude = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

/**
 * The help chat, drawn in place of the search results (Marko, 13.11.2026), in the host look (ttt-design): the
 * keyboard background, his balloons right, Claude's left (Messages layout), the box and the keys at the bottom like
 * the feature row. Back or ✕ returns to the search; the chat stays on the phone and continues next time. The sweep key
 * empties it here only: the data repository keeps every question and answer.
 */
@Composable
fun MaHelpChatPanel(firstQuestion: String?, onClose: () -> Unit, onSeedUsed: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var turns by remember { mutableStateOf(MaHelpChat.load(context)) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf(MaHelpChat.tier(context)) }

    BackHandler(onBack = onClose)

    /** Asks the last question again (after an error, or after a new key was loaded from the error itself). */
    fun askAgain() {
        if (busy || turns.none { it.me }) return
        turns = turns.filterNot { it.error }
        busy = true
        status = ""
        scope.launch {
            val r = runCatching { MaHelpChat.ask(context, turns) { s -> scope.launch { status = s } } }
            turns = turns + r.fold(
                { MaHelpChat.Turn(me = false, text = it) },
                { e -> MaHelpChat.Turn(me = false, text = e.message ?: e.javaClass.simpleName, error = true) },
            )
            MaHelpChat.save(context, turns)
            busy = false
        }
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || busy) return
        turns = turns.filterNot { it.error } + MaHelpChat.Turn(me = true, text = q)
        MaHelpChat.save(context, turns)
        input = ""
        askAgain()
    }

    // The error is the fix: a key problem opens the key file picker, and a working key asks again at once.
    val keyPicker = rememberKeyFixPicker(onStatus = { status = it }, onFixed = { askAgain() })

    // The question typed in the search box is asked at once.
    LaunchedEffect(firstQuestion) {
        if (!firstQuestion.isNullOrBlank()) {
            send(firstQuestion)
            onSeedUsed()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().background(MaHost.Bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (turns.isEmpty()) {
            Text(
                text = "Ask anything about TTT, or explain how something works: what you teach is kept and used in " +
                    "every later answer. Every question and answer goes to your private data repository.",
                color = MaHost.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        turns.forEach { t ->
            if (t.error) {
                MaFixMessage(t.text, Modifier.padding(vertical = 4.dp), onPickKey = { keyPicker.launch(MaFix.ANY_FILE) }, onRetry = { askAgain() })
            } else Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = if (t.me) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                SelectionContainer {
                    Text(
                        text = t.text,
                        color = MaHost.Ink,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .widthIn(max = if (t.me) 310.dp else 340.dp)
                            .padding(start = if (t.me) 48.dp else 0.dp, end = if (t.me) 0.dp else 24.dp)
                            .clip(if (t.me) BalloonMine else BalloonClaude)
                            .background(if (t.me) MaHost.Mine else MaHost.Theirs)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (!busy && MaFix.needsKey(status)) {
            MaFixMessage(status, onPickKey = { keyPicker.launch(MaFix.ANY_FILE) })
        } else if (busy || status.isNotBlank()) {
            Text(
                text = if (busy) status.ifBlank { "Claude is thinking" } + " …" else status,
                color = if (busy) MaHost.Gold else MaHost.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        // Which Claude answers, always in sight; a tap switches Sonnet -> Opus -> Haiku for the next question.
        MaModelBadge(
            label = remember(tier) { MaHelpChat.modelLabel(context) },
            options = MaHelpChat.TIERS,
            current = tier,
            onPick = { MaHelpChat.setTier(context, it); tier = it },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            MaHostKey(label = null, icon = Icons.Default.Close, onClick = onClose)
            MaHostKey(
                label = null, icon = Icons.Default.DeleteSweep, ink = MaHost.DangerInk, enabled = !busy && turns.isNotEmpty(),
                onClick = { MaHelpChat.clear(context); turns = emptyList(); status = "" },
            )
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).padding(horizontal = 3.dp, vertical = 4.dp)
                    .clip(MaHost.KeyShape).background(MaHost.Key).padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) Text("Ask Claude about TTT", color = MaHost.Muted, fontSize = 17.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = MaHost.Ink, fontSize = 17.sp),
                    cursorBrush = SolidColor(MaHost.Gold),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                )
            }
            MaHostKey(
                label = null, icon = Icons.AutoMirrored.Filled.Send, ink = MaHost.Gold,
                enabled = !busy && input.isNotBlank(),
                onClick = { send(input) },
            )
        }
    }
}
