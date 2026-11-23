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

import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.llm.MaPhoneModels
import dev.patrickgold.florisboard.dictate.nlp.MaWordLanguage

/**
 * STYLE AFTER EVERY DICTATION, THE MAC'S WAY. §191.
 *
 * MARKO_TRANSCRIBE on the Mac sends every message through a small model on the Mac
 * (server/styled.py): misheard words, spelling, capitals and punctuation, repetitions and confused
 * explanations, in the Yshai Afterman style with normal capitals. He asked for the same here, on the
 * phone, with the same model. [INSTRUCTION] is the Mac's rules word for word, and [correct] its
 * standing corrections; change them there and here together.
 *
 * Its model is chosen like every prompt's (Settings → Prompts), and ships as Qwen3 4B on the phone.
 * It runs only when that model is downloaded; until then nothing changes.
 *
 * When it runs and succeeds it does the Croatian spell check's job as well, so the spell check is
 * not sent too. When it fails, the spell check runs as before: a dictation is never left with
 * neither.
 */
object MaPhoneStyle {

    const val NAME = "Style after dictation"

    /** server/styled.py RULES, MARKO_TRANSCRIBE, 19.10.2026. */
    const val INSTRUCTION =
            "You clean up a message that Marko dictated and a speech recogniser wrote down. Reply with the cleaned message only, nothing before or after it.\n\n" +
            "This is an edit, not a summary. Keep his sentences, his words and his order. Only remove what is repeated and fix what is wrong.\n\n" +
            "Keep his point of view exactly: \"I\", \"me\", \"my\" stay \"I\", \"me\", \"my\", and \"you\" stays \"you\". A request stays a request, with its \"please\" and its \"if you can\". A wish stays a wish (\"volio bih\" stays \"volio bih\"). Never change who does what.\n\n" +
            "Fix the words the recogniser misheard: choose the word that makes sense in the sentence. Fix spelling, grammar and punctuation in the language of the message. Every sentence starts with a capital letter, names have capitals.\n\n" +
            "Remove repetitions, false starts (\"no wait\") and filler words (um, uh, you know, like, I mean, ono, ovaj, znači, dakle). When he says the same thing twice, keep it once. When an explanation is confused, say it once, clearly, in the order that makes sense.\n\n" +
            "Keep every fact, name, number, date, request and decision. Add nothing he did not say. Do not soften what he said firmly and do not make firmer what he said gently. Keep English words he mixed into Croatian.\n\n" +
            "Style: short paragraphs of one or two sentences, separated by a single line break, never an empty line. Plain everyday words. Written to one person as \"you\". The reason comes before the request. Warm, never gushing.\n\n" +
            "Never use dashes, bullet points, headings, bold, exclamation marks or emoji. Normal sentence capitals and correct apostrophes.\n\n" +
            "Names: Marko (never Marco; \"a Marco\" or \"Amarco\" is \"and Marko\"), Groq (never Grok), Claude, Speechify, Mantra, Auroville."

    /**
     * The standing corrections (MANTRA_MANIFEST modules/transcription.md), before and after the model:
     * on the Mac every model kept "Grok", because Grok is also a real product. A rule in a prompt is a
     * hope; a replacement is a fact.
     */
    private val CORRECTIONS = listOf(
        Regex("""\b[Aa]\s?[Mm]arco\b""") to "and Marko",
        Regex("""\bMarco\b""") to "Marko",
        Regex("""\bGrok\b""") to "Groq",
    )

    fun correct(text: String): String = CORRECTIONS.fold(text) { t, (re, right) -> t.replace(re, right) }

    /**
     * One line break between paragraphs, never an empty line: the model writes its "short
     * paragraphs" with a blank line between them, and Marko wants them as Ctrl+Enter makes them,
     * a single new line. Done here, not only asked in [INSTRUCTION], so his own saved wording and
     * every model obey it too.
     */
    fun singleLineBreaks(text: String): String =
        text.replace("\r\n", "\n").replace(Regex("""[ \t]*\n(?:[ \t]*\n)+"""), "\n")

    /** The wording in force: his ticked instance, or the Mac's. */
    fun instruction(): String {
        val prefs by FlorisPreferenceStore
        return MaPrompts.activeText(prefs.dictate.maPhoneStylePrompt.get()).ifBlank { INSTRUCTION }
    }

    /** The model, always named: a phone model or a Claude tier. */
    fun model(): String {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maPhoneStyleModel.get().trim().ifBlank { MaPhoneModels.QWEN3_4B.choice }
    }

    /**
     * The longest dictation the phone model styles: about two minutes of speech. It writes six to
     * eight words a second, so this is already most of a minute; a ten-minute dictation would hold
     * his text for several. Longer ones go to the spell check as before. Claude has no such limit.
     */
    const val PHONE_MAX_WORDS = 350

    fun fitsPhone(text: String): Boolean = text.trim().split(Regex("\\s+")).size <= PHONE_MAX_WORDS

    /**
     * Fewer words than this are typed as they came. A dictation of two or three words has nothing to
     * clean up and everything to invent: near silence turned into a made-up sentence (build 398).
     */
    const val MIN_WORDS = 4

    fun worthStyling(text: String): Boolean = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } >= MIN_WORDS

    fun enabled(): Boolean {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maPhoneStyleEnabled.get()
    }

    /**
     * Whether [styled] may replace [original]. It may be shorter than a spell check's answer, since
     * repetitions go, but not a third of it, and not much longer: then it is a model talking.
     */
    fun accept(original: String, styled: String): Boolean {
        val s = styled.trim()
        if (s.isEmpty()) return false
        // Never a different language (phone test, build 398: a near-silent Croatian dictation came
        // back as an invented English sentence). Only when both can be told; unclear passes.
        val from = MaWordLanguage.detectPassage(original)
        val to = MaWordLanguage.detectPassage(s)
        if (from != null && to != null && from != to) return false
        val ratio = s.length.toDouble() / original.trim().length.coerceAtLeast(1)
        return ratio in 0.33..1.6
    }
}
