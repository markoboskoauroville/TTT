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

import android.content.Context
import dev.patrickgold.florisboard.dictate.nlp.MaWordLanguage
import java.io.File

/**
 * RE'S LANGUAGE RULE AND WRITING STYLE (Marko, 14.11.2026).
 *
 * "When I tap to talk, I am actually always talking in English. But re must understand: if the message is in Croatian,
 * it needs to apply all my corrections in Croatian language ... re doesn't understand my style of writing ... build it
 * in this app so there is no question asked when he's writing."
 *
 * Two things, in every RE prompt (the voice reprompt, the write mode, the RE chat, Qwen and Anthropic alike):
 *
 * 1. The LANGUAGE of the answer is the language of the message being edited or answered (the text in the field, else
 *    the newest message on the screen), never the language of his spoken instruction, which is always English. Only an
 *    explicit "translate", "in English", "na hrvatski" changes it. [rule] says it as a hard rule and, when the text
 *    says which language it is ([MaWordLanguage.detectPassage]), names it as well.
 * 2. His STYLE guide ([guide]): assets/re_style.md, or Documents/TTTmini/re_style.md when it exists (that one is
 *    synced with RE_MAGIC_DATABASE and wins, so he can edit it). RE never asks about style or language: it writes.
 */
object MaReStyle {
    const val FILE = "re_style.md"
    const val ASSET = "re_style.md"

    @Volatile private var appContext: Context? = null

    /** Called once from the input method service (beside MaReSkill.attach). */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** The style guide: the phone copy in Documents/TTTmini, else the one shipped in the app, else empty. */
    fun guide(): String {
        val docs = runCatching { File(MaVault.dir(), FILE).takeIf { it.length() > 0L }?.readText() }.getOrNull()
        if (!docs.isNullOrBlank()) return docs
        return runCatching { appContext?.assets?.open(ASSET)?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
    }

    /** The language of [text] as a name ("Croatian", "English"), or null when the text does not say. */
    fun languageName(text: String?): String? =
        text?.takeIf { it.isNotBlank() }?.let { MaRewordPrompt.languageOf(it) }

    /**
     * The hard rule, to go LAST in a prompt. [target] is the text being edited or answered (the field, or the screen's
     * messages); null when there is none.
     */
    fun rule(target: String?): String {
        val name = languageName(target)
        val named = if (name != null) "The message is in $name: write the result in $name" +
            (if (name == "Croatian") ", correct standard Croatian with its diacritics (č ć š ž đ), every correction applied in Croatian. " else ". ")
        else "Write the result in the language of that message. "
        return "LANGUAGE RULE (final, outranks everything above): Marko speaks his instructions in English, always. The language of " +
            "the instruction, of this prompt and of your notes never decides the language of the result. The result is in the " +
            "language of the message being edited or answered (the text in the field, else the newest message on the screen). " + named +
            "Never translate unless the instruction explicitly says so (translate, in English, na hrvatski). " +
            "Never ask which language or which style: write directly."
    }

    /** The style guide as a prompt block, empty when there is none. */
    fun block(): String {
        val g = guide().trim()
        if (g.isEmpty()) return ""
        return "His writing style (follow it without asking; the person's file and his corrections outrank it):\n<style>\n" +
            g.take(7000) + "\n</style>\n\n"
    }
}
