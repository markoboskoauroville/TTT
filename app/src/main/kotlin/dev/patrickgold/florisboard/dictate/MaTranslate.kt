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

import dev.patrickgold.florisboard.app.FlorisPreferenceStore

/**
 * E > H: DICTATE IN ENGLISH, WRITE IN CROATIAN, IN HIS VOICE.
 *
 * Marko, 2.11.2026: "I dictate my message in English and then Anthropic is translating and applying
 * my style of communication that message." The key is a red dot, E, ">" and H: record English,
 * translate, style, paste Croatian.
 *
 * The recording is transcribed as English (the key sets English first, as the E key does), then sent
 * once to Claude with the Anthropic key loaded in the app: translate into Croatian, and write it the
 * way he writes (the same rules as the style pass, [MaPhoneStyle.INSTRUCTION], in Croatian). One
 * request, not two: a translation styled in a second pass costs twice and drifts twice.
 *
 * The instruction and the model are editable on Settings → Prompts, like the spell check. The
 * Croatian spell check and the style pass do not run on top: the translation is already written by
 * Claude in Croatian, and a second rewrite would only move it further from what he said.
 */
object MaTranslate {

    const val NAME = "English to Croatian (E>H)"

    const val INSTRUCTION =
        "Marko dictated the message below in English and a speech recogniser wrote it down. Translate " +
            "it into Croatian and write it the way Marko writes: this is his message, sent under his " +
            "name to someone who reads Croatian. Reply with the Croatian message only, nothing before " +
            "or after it.\n\n" +
            "Translate the meaning, not word by word: natural, everyday Croatian as a Croatian speaker " +
            "would write it. Keep every fact, name, number, date, request and decision. Add nothing he " +
            "did not say, and leave nothing out.\n\n" +
            "Keep his point of view exactly: \"I\" stays \"ja\", \"you\" stays \"ti\" (or \"Vi\" only if " +
            "the English is clearly formal). A request stays a request, with its \"please\" " +
            "(\"molim te\"). Do not soften what he said firmly and do not make firmer what he said " +
            "gently.\n\n" +
            "First fix what the recogniser misheard in the English, then remove repetitions, false " +
            "starts and filler words (um, uh, you know, like, I mean).\n\n" +
            "Style (Yshai Afterman's, with normal capitals): short paragraphs of one or two sentences, " +
            "a blank line between them, never three sentences in a row. Plain everyday words, written to " +
            "one person as \"ti\". Put the reason before the request, so the request reads as an " +
            "invitation. Keep a \"but\" or an honest qualification he made, and keep what he says he " +
            "saw or did himself, as he said it. Practical facts (time, place, price) stay bare. Keep any " +
            "line that takes a worry away (\"if you can't come, nothing is lost\"), last. Never invent " +
            "any of these. Warm, never gushing. Correct Croatian spelling (č, ć, dž, đ, ije, je), capitals and punctuation. " +
            "Never use dashes, bullet points, headings, bold, exclamation marks or emoji.\n\n" +
            "Keep names, brand names and technical terms as they are (Claude, Kie, Higgsfield, " +
            "DaVinci Resolve, Premiere Pro, UGC, Pixar, Mantra Productions, Auroville). English words " +
            "that Croatians use as they are (e.g. \"render\", \"timeline\", \"link\") may stay English.\n\n" +
            "Names: Marko (never Marco), Groq (never Grok)."

    /** The wording in force: his ticked instance, or the shipped one. */
    fun instruction(): String {
        val prefs by FlorisPreferenceStore
        return MaPrompts.activeText(prefs.dictate.maTranslatePrompt.get()).ifBlank { INSTRUCTION }
    }

    /** The model for this job, always by name (MaPromptModel): the newest Haiku unless he picks one. */
    fun model(): String {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maTranslateModel.get().let { MaPromptModel.effective(it) }
    }

    /**
     * Whether [translated] may replace [english]. A translation is about as long as what it
     * translates; blank, under a third or over two and a half times as long is a model talking, not a
     * translation, and the English is kept instead, so a dictation is never lost.
     */
    fun accept(english: String, translated: String): Boolean {
        val t = translated.trim()
        if (t.isEmpty()) return false
        val ratio = t.length.toDouble() / english.trim().length.coerceAtLeast(1)
        return ratio in 0.33..2.5
    }
}
