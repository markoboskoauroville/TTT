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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import kotlin.jvm.Volatile

/**
 * REPROMPT: SAY WHAT TO DO WITH THE TEXT, AND IT IS DONE IN PLACE.
 *
 * Marko, 4.11.2026: "Once the message is written, there should be a button … Reprompt. You can just
 * put re on it with small magic symbol. And then I press the button and then the dialog open up
 * which I'm giving instruction to Anthropic what to do with the text which is in the box and it can
 * directly modify it."
 *
 * ### How it works, and why a window
 *
 * A keyboard cannot type into itself, so the instruction is written in a small window of the app's
 * own ([MaRepromptActivity]): TTT is the keyboard there too, so he can type it or dictate it with
 * •E / •H. While the window is open the message field is not connected; so the key remembers WHICH
 * field it came from ([Target]), and when the window closes and that field is back, the instruction
 * runs on its whole text, through the same path as Ctrl+P and Ctrl+F (selection if there is one,
 * else the whole field, replaced in one go). Another field coming back first, or nothing within a
 * minute, drops it rather than rewriting the wrong text.
 *
 * The model is Ctrl+F's (Settings → Prompts), because a reprompt is a rewrite. His last instructions
 * are kept (on this phone only) and offered as one tap in the window.
 *
 * ### Two modes, decided by the field (4.11.2026)
 *
 * "If message is empty, then it goes to the prompt mode and I'm going to tell him what kind of message
 * to write. If there is a message, then it's as before … reshape the message." An empty field gets a
 * new message written from his instruction ([writeInstructionFor]); a field with text is reshaped
 * ([instructionFor]). The field decides when it comes back, so text written meanwhile is reshaped.
 */
object MaReprompt {

    const val NAME = "Reprompt"

    /** How long an instruction waits for its field to come back. */
    const val WAIT_MS = 60_000L

    /** How many past instructions the window offers. */
    const val RECENT_MAX = 6

    /** The field the key was pressed in: its app and its id. */
    data class Target(val packageName: String, val fieldId: Int, val atMillis: Long)

    @Volatile
    var target: Target? = null
        private set

    /** The instruction written in the window, waiting for [target] to come back. */
    @Volatile
    var instruction: String? = null
        private set

    /** Whether the window was opened on an empty field: it then asks what message to write. */
    @Volatile
    var writeMode: Boolean = false
        private set

    /** The key: remember the field, open the window. */
    fun open(context: Context, packageName: String, fieldId: Int, write: Boolean = false) {
        writeMode = write
        ownPackage = context.packageName
        target = Target(packageName, fieldId, System.currentTimeMillis())
        instruction = null
        context.startActivity(
            Intent(context, MaRepromptActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    /** The window's Apply. */
    suspend fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return cancel()
        instruction = t
        remember(t)
    }

    fun cancel() {
        target = null
        instruction = null
    }

    /**
     * Called when a field starts: the instruction to run now, or null. Only for the field the key
     * was pressed in, and only within [WAIT_MS]; anything else drops it.
     */
    fun takeFor(packageName: String?, fieldId: Int, nowMillis: Long = System.currentTimeMillis()): String? {
        val t = target ?: return null
        val i = instruction ?: return null
        // The window's own field starts first, and must never receive the reprompt.
        if (packageName == null || packageName == ownPackage) return null
        cancel()
        if (nowMillis - t.atMillis > WAIT_MS) return null
        if (t.packageName != packageName || t.fieldId != fieldId) return null
        return i
    }

    /** This app's package: its window is where the instruction is written. */
    @Volatile
    private var ownPackage: String = ""

    /** What is sent: his words, as the instruction for the text below them. */
    fun instructionFor(words: String): String =
        "Apply this instruction to the text below and return only the resulting text, nothing " +
            // Hidden code words (MaCodeWords): a name such as "Ashai Afterman" brings its full
            // description with it, however it was spelled or misheard.
            "before or after it: " + MaCodeWords.expand(words.trim())

    /** The prompt mode: a new message from his instruction, nothing to reshape. */
    fun writeInstructionFor(words: String): String {
        val w = words.trim()
        // The language is named, not left to "the language of the instruction": this wrapper is
        // English, and a Croatian request must not come back English (the E|H lesson, 3.11.2026).
        val language = MaRewordPrompt.languageOf(w)
        val inLanguage = if (language != null) " Write it in $language unless the request names another language."
        else " Write it in the language of the request unless the request names another language."
        return MaCodeWords.expand(
            "Write the message the user asks for between the <request> tags. Write it as the user, " +
                "ready to send.$inLanguage Return only the message, nothing before or after it.\n\n" +
                "<request>\n$w\n</request>",
        )
    }

    /**
     * The prompt sent. [write] (an empty field): a new message, inserted at the cursor, nothing
     * selected or replaced. Otherwise the text is reshaped: the selection, else the whole field.
     */
    fun prompt(words: String, write: Boolean = false): PromptModel = PromptModel(
        id = PromptModel.ID_INSTANT_PROMPT,
        pos = 0,
        name = NAME,
        prompt = if (write) writeInstructionFor(words) else instructionFor(words),
        requiresSelection = !write,
        autoApply = false,
    )

    // ---------------------------------------------------------------- his last instructions

    fun recent(): List<String> {
        val prefs by FlorisPreferenceStore
        return parseRecent(prefs.dictate.maRepromptRecent.get())
    }

    private suspend fun remember(words: String) {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maRepromptRecent.set(serializeRecent(withRecent(recent(), words)))
    }

    /** [list] with [words] first, no duplicate, at most [RECENT_MAX]. */
    fun withRecent(list: List<String>, words: String): List<String> =
        (listOf(words) + list.filter { !it.equals(words, ignoreCase = true) }).take(RECENT_MAX)

    private const val SEP = '\u001E'

    fun parseRecent(raw: String): List<String> = raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    fun serializeRecent(list: List<String>): String = list.joinToString(SEP.toString())
}
