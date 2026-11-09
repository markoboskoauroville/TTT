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

import android.content.Context
import android.content.Intent
import kotlin.jvm.Volatile

/**
 * RE NEVER WRITES INTO THE CHAT BY ITSELF: EVERY RESULT IS SHOWN IN A POP-UP FIRST (Marko, 9.11.2026).
 *
 * "Teach TTT not to write its replies to me into the chat. There must be a pop-up window: I read its reply
 * and I say OK. Only messages go into the chat. Right now, when there's some confusion, it just dumps its
 * answer into the chat itself." (Found the same day on the Nothing: asked to reply in a chat with only his own
 * messages, RE put its explanation "There is no real message in this chat to answer" into the message box.)
 *
 * - A result that is a **message** opens [MaReResultActivity]: scrollable, big text, OK (the draft goes into the
 *   field exactly as shown), EDIT (change it in the pop-up first), CANCEL.
 * - A result that is **not a message** (RE's commentary, a question, "NO MESSAGE FOUND", an error such as "could
 *   not read the screen") opens the same pop-up with only Close: it can never be inserted ([isNote]).
 *   The model is told to start such an answer with `RE-NOTE:` (MaReSkill.preamble); the app also catches the
 *   old "NO MESSAGE FOUND" and any error it raises itself.
 * - OK stores the text here; when the field is back ([takeInsert], from the keyboard's onStartInputView) the
 *   keyboard inserts it and only then it becomes the draft the learning loop compares with what he sends. An
 *   EDIT in the pop-up is itself a correction and is logged and learned from.
 */
object MaReResult {

    const val NOTE_PREFIX = "RE-NOTE:"

    /** How long an accepted result waits for its field to come back. */
    const val WAIT_MS = 60_000L

    data class Result(
        val text: String,
        val note: Boolean,
        /** Reshape mode: the field's text is replaced; write mode: inserted at the cursor. */
        val replace: Boolean,
        val pkg: String?,
        val fieldId: Int,
        val words: String,
        val at: Long = System.currentTimeMillis(),
        val viaAssistant: Boolean = false,
    )

    @Volatile
    var appContextForInsert: Context? = null

    @Volatile
    var shown: Result? = null
        private set

    @Volatile
    private var accepted: Result? = null

    /** True for text that is RE talking and not a message: it is never inserted. */
    fun isNote(text: String): Boolean {
        val t = text.trim()
        return t.startsWith(NOTE_PREFIX, ignoreCase = true) || t.startsWith("NO MESSAGE FOUND", ignoreCase = true)
    }

    /** What the pop-up displays: the note without its marker. */
    fun display(text: String): String =
        text.trim().let { if (it.startsWith(NOTE_PREFIX, true)) it.substring(NOTE_PREFIX.length).trim() else it }

    /** Shows [text] (the model's answer) in the pop-up; a note when it is one. */
    fun present(context: Context, text: String, replace: Boolean, words: String) {
        // The app in front must still be the one the command was given in (Marko, 9.11.2026): if it changed, the
        // result is shown in the pop-up only and nothing can be inserted.
        val now = runCatching { dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService.frontPackage() }.getOrNull()
        val moved = now != null && MaReprompt.lastPkg != null && now != MaReprompt.lastPkg && now != context.packageName
        val note = isNote(text) || moved
        val shownText = if (moved && !isNote(text)) "The app in front changed since you gave the command, so nothing is inserted. RE's draft was: " + display(text) else display(text)
        if (note) MaVoice.say(context, shownText)
        appContextForInsert = context.applicationContext
        shown = Result(shownText, note, replace, MaReprompt.lastPkg, MaReprompt.lastFieldId, words, viaAssistant = MaAssistant.viaOverlay)
        open(context)
    }

    /** Shows RE's own words (an error, a refusal, a question): the pop-up only, never insertable. */
    fun note(context: Context, text: String) {
        shown = Result(text, true, false, MaReprompt.lastPkg, MaReprompt.lastFieldId, MaReprompt.lastWords)
        MaVoice.say(context, text)
        open(context)
    }

    private fun open(context: Context) {
        context.startActivity(
            Intent(context.applicationContext, MaReResultActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    /** The pop-up's OK (with [finalText] possibly edited): the keyboard inserts it when the field is back. */
    fun accept(finalText: String) {
        val r = shown ?: return
        shown = null
        markClosed()
        if (r.note || finalText.isBlank()) return
        accepted = r.copy(text = finalText.trim(), at = System.currentTimeMillis())
        // From the assistant there is no keyboard field to wait for: the accessibility sink inserts into the app behind.
        if (r.viaAssistant) {
            val ctx = appContextForInsert
            val a = accepted
            val front = runCatching { dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService.frontPackage() }.getOrNull()
            if (front != null && a?.pkg != null && front != a.pkg) {
                accepted = null // the app changed again: nothing is inserted into another app
            } else if (ctx != null && a != null) {
                accepted = null
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ DictateController.insertReResult(ctx, a) }, 600)
            }
        }
        if (!MaReSkill.same(r.text, finalText)) MaReSkill.noteCorrection(r.pkg, r.words, r.text, finalText.trim(), "popup-edit")
    }

    fun cancel() {
        shown = null
        accepted = null
        markClosed()
    }

    /** When the pop-up last closed (any way): the keyboard shows itself again and RE's state is reset. */
    @Volatile
    var closedAt: Long = 0L

    private fun markClosed() {
        closedAt = System.currentTimeMillis()
    }

    /** True once, within a few seconds of the pop-up closing: the keyboard must come back. */
    fun consumeClosed(now: Long = System.currentTimeMillis()): Boolean {
        val c = closedAt
        if (c == 0L || now - c > 6_000L) return false
        closedAt = 0L
        return true
    }

    /** Called when a field starts: the accepted result for THIS field, once, or null. */
    fun takeInsert(packageName: String?, fieldId: Int, now: Long = System.currentTimeMillis()): Result? {
        val r = accepted ?: return null
        if (packageName == null) return null
        if (r.pkg != packageName) return null
        if (now - r.at > WAIT_MS) { accepted = null; return null }
        if (r.fieldId != 0 && fieldId != 0 && r.fieldId != fieldId) return null
        accepted = null
        return r
    }
}
