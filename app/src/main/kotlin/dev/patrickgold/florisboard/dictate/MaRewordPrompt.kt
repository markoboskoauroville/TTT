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
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.nlp.MaWordLanguage
import java.lang.System
import kotlin.jvm.Volatile

/**
 * THE EXACT MESSAGE CTRL+P AND CTRL+F SEND, BUILT IN ONE PLACE.
 *
 * He asked to see what is sent to Claude, word for word, and to change it. The instruction was
 * already his to edit, but it was never the whole message: two more pieces were added behind it
 * that no screen showed. So the message is built HERE, and the keyboard and the settings preview
 * both call [build]. The preview cannot drift from what is sent, because it is the same function.
 *
 * ### The four parts, in order
 *
 *  1. **The instruction** — the ticked wording of Ctrl+P or Ctrl+F (Settings → Prompts).
 *  2. **The rewording system prompt** — "be precise", or his own, or nothing (Settings → Rewording).
 *  3. **The language line** — names the language of the text. Editable on the Prompts screen.
 *  4. **The text** — the selection, or the whole field.
 *
 * Sent as one user message, blank lines between the parts.
 *
 * ### The sentence that told Claude to answer in English
 *
 * The shipped "be precise" prompt ends: *Output the text in the language of the instruction.* The
 * instruction is in English. So every Ctrl+P on Croatian carried an order to answer in English,
 * followed by the language line ordering the opposite — and the model had to pick one. That
 * sentence is for prompts that write something new ("write an email to…"). When there is text to
 * work on, it is dropped, and the language line is the only word on language.
 */
object MaRewordPrompt {

    /** `{language}` becomes Croatian or English. */
    const val LANGUAGE_RULE =
        "The text below is written in {language}. Your entire answer must be in {language}. " +
            "Do not translate it into another language."

    /** Used when the small words do not say which language the text is in. */
    const val LANGUAGE_RULE_UNSURE =
        "Answer in the same language the text below is written in. Do not translate it."

    /** What the preview shows where his text goes. */
    const val TEXT_PLACEHOLDER = "‹the selected text, or the whole field if nothing is selected›"

    /** The last sentence of [DictatePromptDefaults.REWORDING_BE_PRECISE]; see the class comment. */
    private const val ANSWER_IN_INSTRUCTION_LANGUAGE =
        "Output the text in the language of the instruction, unless a different language was explicitly requested."

    /** Part 2 as sent: the instruction-language sentence is dropped when there is text to work on. */
    fun appendix(systemPrompt: String, hasInput: Boolean): String =
        if (hasInput) systemPrompt.replace(ANSWER_IN_INSTRUCTION_LANGUAGE, "").trim() else systemPrompt.trim()

    /** Part 3: [template] with the language named, or [unsure] when [language] is null. */
    fun languageLine(template: String, unsure: String, language: String?): String =
        if (language == null) unsure.trim() else template.replace("{language}", language).trim()

    /**
     * The whole message. Pure, so it can be walked without a phone, and called by the preview with
     * [TEXT_PLACEHOLDER] for [input] and a language chosen by the screen.
     */
    fun build(
        instruction: String,
        systemPrompt: String,
        languageTemplate: String,
        languageUnsure: String,
        language: String?,
        input: String?,
    ): String = buildString {
        append(instruction)
        val hasInput = !input.isNullOrBlank()
        val sys = appendix(systemPrompt, hasInput)
        if (sys.isNotBlank()) append("\n\n").append(sys)
        if (hasInput) {
            val line = languageLine(languageTemplate, languageUnsure, language)
            if (line.isNotBlank()) append("\n\n").append(line)
            append("\n\n").append(input)
        }
    }

    // ---------------------------------------------------------------- read from settings

    /** The rewording system prompt as the settings hold it now. */
    fun currentSystemPrompt(): String {
        val prefs by FlorisPreferenceStore
        return when (prefs.dictate.systemPromptSelection.get()) {
            DictatePromptDefaults.SELECTION_PREDEFINED -> DictatePromptDefaults.REWORDING_BE_PRECISE
            DictatePromptDefaults.SELECTION_CUSTOM -> prefs.dictate.systemPromptCustom.get()
            else -> ""
        }
    }

    fun currentLanguageTemplate(): String {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maLanguageRule.get().ifBlank { LANGUAGE_RULE }
    }

    fun currentLanguageUnsure(): String {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maLanguageRuleUnsure.get().ifBlank { LANGUAGE_RULE_UNSURE }
    }

    /** The language name for [input], or null when it cannot be told. */
    fun languageOf(input: String): String? =
        MaWordLanguage.detectPassage(input)?.let { MaWordLanguage.nameOf(it) }

    /** [build] with everything taken from the settings, as the keyboard sends it. */
    fun forSending(instruction: String, input: String?, answerLanguage: String? = null): String = build(
        instruction = instruction,
        systemPrompt = currentSystemPrompt(),
        languageTemplate = if (answerLanguage != null) TRANSLATE_RULE else currentLanguageTemplate(),
        languageUnsure = currentLanguageUnsure(),
        language = answerLanguage ?: input?.takeIf { it.isNotBlank() }?.let { languageOf(it) },
        input = input,
    )

    /**
     * For a translation (E>H) the language line names the language of the ANSWER, not of the text.
     * The ordinary rule says "The text below is written in English. Your entire answer must be in
     * English. Do not translate it", which contradicts the translate instruction; Claude sometimes
     * obeyed it and answered "this cannot be translated into Croatian" (phone test, 2.11.2026).
     */
    const val TRANSLATE_RULE = "Your entire answer must be in {language}, translated from the text below."

    /**
     * The same message in two parts, for the model on the phone, which takes its rules as a system
     * message (§191): everything [forSending] puts before his text, and his text. The order inside
     * the first part is the same, so the language rule is still the last thing before his words.
     */
    fun forSendingSplit(instruction: String, input: String, answerLanguage: String? = null): Pair<String, String> {
        val rules = buildString {
            append(instruction)
            val sys = appendix(currentSystemPrompt(), hasInput = true)
            if (sys.isNotBlank()) append("\n\n").append(sys)
            val line = if (answerLanguage != null) languageLine(TRANSLATE_RULE, "", answerLanguage)
                else languageLine(currentLanguageTemplate(), currentLanguageUnsure(), languageOf(input))
            if (line.isNotBlank()) append("\n\n").append(line)
        }
        return rules to input
    }

    // ---------------------------------------------------------------- the last one sent

    /** One request as it left the phone. Kept in memory only: it holds his text. */
    data class Sent(val atMillis: Long, val model: String, val message: String)

    @Volatile
    var last: Sent? = null
        private set

    fun recordSent(model: String, message: String) {
        last = Sent(System.currentTimeMillis(), model, message)
    }
}
