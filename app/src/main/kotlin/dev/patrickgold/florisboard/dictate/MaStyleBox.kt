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
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AUTO CORRECTION AND AI: ONE BOX (Marko, 23.11.2026).
 *
 * "I really want to simplify this correction of the style ... after transcription there is a text inside with two types.
 * Original and styling. And then when I choose original or styling then I press button type and then it's typed inside the
 * application wherever the cursor was before I'm dictating. And the third one is replacement for RE ... reprompt ... there
 * is nothing, there is just ability for me to reprompt and give instructions for new message. And then after message is
 * corrected there is fourth tab which says new message. And then I can choose to type it. If not I can go back to reprompt
 * and prompt again. And this is completely replacing RE."
 *
 * - **The Style badge** on the recorder (and in Settings > Auto correction and AI): on = a dictation is not typed at once;
 *   this box opens with it. Off = the dictation is typed straight into the app, as before.
 * - **Tabs**: Original (what was transcribed, with his corrections) · Style (the same in his writing style, written by the
 *   chosen model at once) · Reprompt (an empty box for an instruction) · New message (what the model wrote from it).
 *   Original, Style and New message can be edited by hand before typing.
 * - **Type** puts the text of the tab in front into the app he came from, at the cursor (MaReResult, never sent).
 * - **Model row**, like a chat app's model picker: Sonnet (default), Opus, Haiku, Groq. Groq is also the backup when the
 *   Anthropic model does not answer. The choice is the one in Settings.
 * - **The RE key** opens this box on Reprompt, with the text of the field as Original. The RE chat and pop-up are gone
 *   from the keys; what RE learned (memory, lessons, his style, people) is still in every prompt (MaReChat.ask).
 */
object MaStyleBox {
    const val ORIGINAL = 0
    const val STYLE = 1
    const val REPROMPT = 2
    const val NEW = 3

    private const val STYLE_ASK = "Rewrite this message in my writing style. Keep the meaning, the facts and the language. " +
        "Fix dictation mistakes and punctuation. Do not add anything. Give only the rewritten message, as the draft."

    private fun p(c: Context) = c.applicationContext.getSharedPreferences("ma_style_box", Context.MODE_PRIVATE)

    /** The Style badge: on by default. */
    fun on(c: Context) = p(c).getBoolean("on", true)
    fun setOn(c: Context, v: Boolean) { p(c).edit().putBoolean("on", v).apply(); badge = v }
    var badge by mutableStateOf<Boolean?>(null)
    fun badgeOn(c: Context): Boolean = badge ?: on(c).also { badge = it }

    /** The box is on screen: a dictation made inside it is typed into its own field, never opens a second box. */
    @Volatile var visible = false

    var original by mutableStateOf("")
    var styled by mutableStateOf("")
    var newMessage by mutableStateOf("")
    var tab by mutableStateOf(ORIGINAL)
    var styling by mutableStateOf(false)
    var writing by mutableStateOf(false)
    var status by mutableStateOf("")
    var engine by mutableStateOf(MaReEngine.SONNET)
    private var pkg: String? = null
    private var fieldId = 0
    private var replace = false
    private var asked = ""

    private fun open(context: Context) {
        engine = MaReEngine.engine()
        context.startActivity(
            Intent(context.applicationContext, MaStyleBoxActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    private fun target(context: Context, p: String?, f: Int, fieldText: String?) {
        pkg = p?.takeIf { it != context.packageName }
        fieldId = f
        MaReChat.pkg = pkg; MaReChat.fieldId = f; MaReChat.fieldText = fieldText?.trim()?.takeIf { it.isNotEmpty() }
        MaReChat.screen = null; MaReChat.person = null
        MaReChat.scope.launch { if (pkg != null) MaReChat.person = runCatching { MaSkills.chatTitle() }.getOrNull()?.trim()?.takeIf { it.length in 2..60 } }
    }

    /** A finished dictation (DictateController.commitOutput): shown here instead of being typed, when the badge is on. */
    fun openAfterDictation(context: Context, text: String, p: String?, f: Int) {
        target(context, p, f, null)
        replace = false
        original = text.trim(); styled = ""; newMessage = ""; status = ""; asked = ""
        tab = STYLE
        open(context)
        style(context.applicationContext)
    }

    /** The RE key: the field's text as Original (Type then replaces it), the box on Reprompt. */
    fun openFromKey(context: Context, p: String?, f: Int, fieldText: String?) {
        target(context, p, f, fieldText)
        original = fieldText?.trim().orEmpty(); styled = ""; newMessage = ""; status = ""; asked = ""
        replace = original.isNotEmpty()
        tab = REPROMPT
        open(context)
    }

    private fun ctx() = MaReChat.Ctx(pkg, MaReChat.person, null, field = null)

    private suspend fun ask(app: Context, msgs: List<MaReChat.Msg>): String {
        val a = withContext(Dispatchers.IO) { MaReChat.ask(app, msgs, ctx()) { s -> MaReChat.scope.launch(Dispatchers.Main) { status = s } } }
        val m = MaReChat.Msg(false, a)
        return (m.draft ?: m.shown).trim()
    }

    /** Writes the Style tab from Original with the chosen model. */
    fun style(app: Context) {
        val text = original
        if (text.isBlank() || styling) return
        styling = true; status = ""
        MaReChat.scope.launch {
            val r = runCatching { ask(app, listOf(MaReChat.Msg(true, MaReSession.FIELD_PREFIX + text), MaReChat.Msg(true, STYLE_ASK))) }
            withContext(Dispatchers.Main) {
                styling = false
                r.onSuccess { styled = it; status = "" }.onFailure { e ->
                    if (e !is CancellationException) status = "Style could not be written: " + (e.message ?: e.javaClass.simpleName).take(100)
                }
            }
        }
    }

    /** Reprompt: his instruction, about the text in the box when there is one; the answer lands on New message. */
    fun reprompt(app: Context, instruction: String) {
        val t = instruction.trim()
        if (t.isEmpty() || writing) return
        asked = t
        writing = true; status = ""
        // What he is working on: the newest of New message, Style, Original.
        val base = newMessage.ifBlank { styled.ifBlank { original } }
        val msgs = listOfNotNull(base.takeIf { it.isNotBlank() }?.let { MaReChat.Msg(true, MaReSession.FIELD_PREFIX + it) }, MaReChat.Msg(true, t))
        MaReHabits.note("reprompt in the box", "${t.length} chars")
        MaReChat.scope.launch {
            val r = runCatching { ask(app, msgs) }
            withContext(Dispatchers.Main) {
                writing = false
                r.onSuccess { newMessage = it; status = ""; tab = NEW }.onFailure { e ->
                    if (e !is CancellationException) status = "No answer: " + (e.message ?: e.javaClass.simpleName).take(100)
                }
            }
        }
    }

    fun pickEngine(app: Context, e: String) {
        if (e == engine) return
        MaReEngine.setEngine(e); engine = e
        // On Style, the other model writes it again; elsewhere the choice waits for the next request.
        if (tab == STYLE && !styling) { styled = ""; style(app) }
    }

    /** Type: the text goes into the field he came from, at the cursor (or over the message RE was opened on). */
    fun type(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || pkg == null) return false
        MaReHabits.note("typed from the box", listOf("Original", "Style", "Reprompt", "New message")[tab] + ", ${t.length} chars")
        MaReResult.acceptFor(t, pkg, fieldId, words = asked, replace = replace)
        return true
    }
}

class MaStyleBoxActivity : ComponentActivity() {
    private var typed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaRemoteDb.attach(this)
        MaReChat.ensureMemory()
        MaStyleBox.visible = true
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = MaHost.Bg, surface = MaHost.Bg)) {
                StyleBoxScreen(onType = { text -> if (MaStyleBox.type(text)) { typed = true; finish() } }, onClose = { finish() })
            }
        }
    }

    override fun onDestroy() {
        MaStyleBox.visible = false
        MaReader.stop()
        if (!typed) MaReResult.closedWithoutInsert()
        DictateController.recoverAfterPopup()
        super.onDestroy()
    }
}

@Composable
private fun StyleBoxScreen(onType: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val b = MaStyleBox
    var instruction by remember { mutableStateOf("") }
    BackHandler { onClose() }

    fun current(): String = when (b.tab) {
        MaStyleBox.ORIGINAL -> b.original
        MaStyleBox.STYLE -> b.styled
        MaStyleBox.NEW -> b.newMessage
        else -> ""
    }
    fun send() {
        val t = instruction
        if (t.isBlank() || b.writing) return
        instruction = ""
        b.reprompt(app, t)
    }

    val maxText = (LocalConfiguration.current.screenHeightDp * 0.36f).dp
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() })
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(MaHost.Bg)
                .border(1.dp, MaHost.Key, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(top = 6.dp, start = 3.dp, end = 3.dp, bottom = 2.dp),
        ) {
            // ---- the four tabs
            Row(modifier = Modifier.fillMaxWidth()) {
                for ((i, name) in listOf("Original", "Style", "Reprompt", "New message").withIndex()) {
                    val sel = b.tab == i
                    MaHostKey(
                        name, Modifier.weight(if (i == 3) 1.35f else 1f), ring = if (sel) MaHost.RingOn else null, lit = sel,
                        ink = if (sel) MaHost.Ink else MaHost.Muted, fontSize = 13, padH = 2,
                    ) { b.tab = i }
                }
            }
            // ---- the text of the tab
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = maxText).padding(horizontal = 3.dp, vertical = 4.dp)
                    .clip(MaHost.KeyShape).background(MaHost.Key)
                    .border(1.5.dp, if (b.tab == MaStyleBox.REPROMPT) MaHost.RingSwitch else MaHost.Key, MaHost.KeyShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                when (b.tab) {
                    MaStyleBox.REPROMPT -> {
                        if (instruction.isEmpty()) Text(
                            if (b.writing) "Writing the new message…" else "What should the new message say, or what should change?",
                            color = MaHost.Muted, fontSize = 17.sp,
                        )
                        BasicTextField(
                            value = instruction, onValueChange = { instruction = it },
                            textStyle = TextStyle(color = MaHost.Ink, fontSize = 17.sp), cursorBrush = SolidColor(MaHost.Gold),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        )
                    }
                    else -> {
                        val text = current()
                        val waiting = (b.tab == MaStyleBox.STYLE && b.styling) || (b.tab == MaStyleBox.NEW && b.writing)
                        if (text.isEmpty()) Text(
                            when {
                                waiting -> MaReEngine.label(b.engine) + " is writing…"
                                b.tab == MaStyleBox.NEW -> "Nothing yet. Give an instruction on Reprompt."
                                b.tab == MaStyleBox.STYLE -> "No styled text yet."
                                else -> "Nothing was dictated."
                            },
                            color = MaHost.Muted, fontSize = 17.sp,
                        )
                        BasicTextField(
                            value = text,
                            onValueChange = {
                                when (b.tab) { MaStyleBox.ORIGINAL -> b.original = it; MaStyleBox.STYLE -> b.styled = it; else -> b.newMessage = it }
                            },
                            textStyle = TextStyle(color = if (b.tab == MaStyleBox.ORIGINAL) MaHost.Ink else MaHost.Gold, fontSize = 17.sp),
                            cursorBrush = SolidColor(MaHost.Gold),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
            if (b.status.isNotBlank()) Text(b.status, color = MaHost.Muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp))
            // ---- the model row, as in a chat app: one is lit, a tap changes it
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for (e in listOf(MaReEngine.SONNET, MaReEngine.OPUS, MaReEngine.HAIKU, MaReEngine.QWEN)) {
                    val sel = b.engine == e
                    MaHostKey(
                        MaReEngine.label(e), Modifier.weight(1f), ring = if (sel) MaHost.Gold else null, lit = sel,
                        ink = if (sel) MaHost.Gold else MaHost.Muted, fontSize = 13, padH = 2,
                    ) { b.pickEngine(app, e) }
                }
            }
            // ---- the keys at the end
            Row(modifier = Modifier.fillMaxWidth()) {
                if (b.tab == MaStyleBox.REPROMPT) {
                    MaHostKey(
                        "Send", Modifier.weight(2.2f), icon = Icons.AutoMirrored.Filled.Send, ink = MaHost.Gold, ring = MaHost.RingOn,
                        enabled = instruction.isNotBlank() && !b.writing, fontSize = 15,
                    ) { send() }
                } else {
                    MaHostKey(
                        "Type", Modifier.weight(2.2f), icon = Icons.Default.Check, ring = MaHost.RingOn,
                        enabled = current().isNotBlank(), fontSize = 15,
                    ) { onType(current()) }
                }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.Default.ContentCopy, enabled = current().isNotBlank()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("TTT", current()))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                MaHostKey(null, Modifier.weight(0.8f), icon = Icons.Default.Close) { onClose() }
            }
        }
    }
}

/** The Style badge on the recorder: lit = the dictation opens the box with Original and Style; a tap removes or returns it. */
@Composable
internal fun MaStyleBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val on = MaStyleBox.badgeOn(context)
    Text(
        text = if (on) "✓ STYLE" else "STYLE",
        color = if (on) MaHost.Gold else MaHost.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (on) MaHost.Gold else MaHost.Muted.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { MaStyleBox.setOn(context, !on) }.padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
