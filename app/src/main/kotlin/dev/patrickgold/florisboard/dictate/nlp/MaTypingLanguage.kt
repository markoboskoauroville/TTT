/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.nlp

import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaLanguage
import kotlin.jvm.Volatile

/**
 * THE LANGUAGE HE IS TYPING, READ FROM WHAT HE TYPES: ENGLISH, OR NOT ENGLISH.
 *
 * Marko, 27.10.2026: "I would like the app to detect whether I'm typing in English or another
 * language. For now the only other language is Croatian, so it is just English or not English. If
 * I'm typing something that is not English, it should give me Croatian suggestions."
 *
 * Until now the suggestions followed the HR/ENG badge, which is also the transcription language. He
 * writes a Croatian message with the badge on ENG and the row offers English. This reads the last
 * few words before the cursor and decides the SUGGESTION language only: the badge, the keyboard
 * layout and the transcription language are not touched.
 *
 * ### How it decides
 *
 *  - Any of č ć ž š đ: Croatian. Certain.
 *  - Otherwise the finished words (the one being typed is left out: "h" is half of everything) are
 *    weighed. A small word of one language (the, and, is / je, se, da) counts most. A word only
 *    one of his personal models knows counts too.
 *  - English wins only on English evidence. A word neither language knows leans Croatian, a little:
 *    "not English" is Croatian, as he put it; but a typo in an English sentence is outweighed by the
 *    English words around it.
 *  - No evidence (an empty field, a single fresh word): the last decision stands, and at the very
 *    start the badge. So the row does not flicker between languages on every letter.
 *
 * [decide] is pure (MaTypingLanguageTest); [of] adds the setting, the models and the memory.
 */
object MaTypingLanguage {

    /** How many finished words before the cursor are read. A sentence's worth, not the whole field. */
    const val WINDOW_WORDS = 8

    private const val CROATIAN_LETTERS = "čćžšđ"

    private val EN_SMALL = setOf(
        "the", "and", "is", "are", "of", "that", "it", "you", "for", "with", "this", "be", "was",
        "have", "has", "not", "but", "we", "they", "my", "your", "will", "can", "what", "so", "at",
        "would", "there", "their", "from", "were", "been", "if", "or", "about", "just", "please",
        "thanks", "thank", "here", "how", "when", "which", "who", "should", "could", "i'm", "it's",
        // Not "i", "a", "to", "on", "do", "no", "me": they are Croatian words too, and count for neither.
        "he", "she", "him", "her", "our", "an", "in", "does", "did", "yes", "hello", "hi", "all",
        "some", "one", "out", "up", "get", "got", "an", "were", "don't", "i'll", "i've",
    )

    private val HR_SMALL = setOf(
        "je", "se", "da", "u", "na", "za", "su", "od", "ne", "sam", "si", "smo", "ste", "kako",
        "ali", "ili", "sto", "kad", "kada", "bi", "ja", "ti", "mi", "vi", "ona", "oni", "ovo", "taj",
        "koji", "koja", "koje", "iz", "sa", "po", "pa", "jer", "ako", "jos", "vec", "samo", "biti",
        "bio", "bila", "nije", "ima", "imam", "hvala", "molim", "dobro", "danas", "sutra", "jutro",
        "ovaj", "ova", "neki", "svi", "sve", "mogu", "moze", "treba", "evo", "nego", "kao", "tako",
        "bok", "sad", "sada", "tu", "tamo", "gdje", "zasto", "jesi", "jesam", "nisam", "hocu",
        "necu", "volim", "idem", "dodi", "vidimo", "fala", "mene", "tebe", "nas", "vas",
    )

    /**
     * The language of the words before the cursor, or null when they do not say.
     *
     * [textBefore] is what is before the cursor; a trailing letter run is the word being typed and
     * is not read. [knowsEn] / [knowsHr] say whether his personal models know a word.
     */
    fun decide(
        textBefore: String,
        knowsEn: (String) -> Boolean,
        knowsHr: (String) -> Boolean,
    ): String? {
        val finished = textBefore.dropLastWhile { it.isLetter() || it == '\'' || it == '’' }
        val words = finished.lowercase()
            .split(Regex("[^\\p{L}'’]+"))
            .filter { it.isNotEmpty() }
            .takeLast(WINDOW_WORDS)
        if (words.isEmpty()) return null
        if (words.any { w -> w.any { it in CROATIAN_LETTERS } }) return MaWordLanguage.HR
        var en = 0.0
        var hr = 0.0
        for (w in words) {
            val enSmall = w in EN_SMALL
            val hrSmall = w in HR_SMALL
            when {
                enSmall && !hrSmall -> en += 2
                hrSmall && !enSmall -> hr += 2
                enSmall && hrSmall -> Unit
                else -> {
                    val e = knowsEn(w)
                    val h = knowsHr(w)
                    when {
                        e && !h -> en += 1
                        h && !e -> hr += 1
                        !e && !h && w.length >= 3 -> hr += 0.5
                    }
                }
            }
        }
        return when {
            en > hr -> MaWordLanguage.EN
            hr > en -> MaWordLanguage.HR
            else -> null
        }
    }

    @Volatile
    private var last: String? = null

    fun enabled(): Boolean {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maDetectTypingLanguage.get()
    }

    /**
     * The suggestion language for the text before the cursor: detected when the setting is on, the
     * badge when it is off. Remembers its answer, so text that says nothing keeps the last one.
     */
    fun of(textBefore: String): String {
        if (!enabled()) return MaLanguage.active()
        val decided = decide(
            textBefore,
            knowsEn = { MaNgram.knowsIn(MaWordLanguage.EN, it) || MaShippedWords.leans(MaWordLanguage.EN, it) || MaShippedWords.isMisspelling(it) },
            knowsHr = { MaNgram.knowsIn(MaWordLanguage.HR, it) || MaShippedWords.leans(MaWordLanguage.HR, it) },
        )
        val language = decided ?: last ?: MaLanguage.active()
        last = language
        return language
    }

    /** The language of a whole finished sentence, for learning it: detected, else the last, else the badge. */
    fun ofSentence(text: String): String {
        if (!enabled()) return MaLanguage.active()
        // A space at the end, so the last word counts as finished.
        return decide(
            "$text ",
            knowsEn = { MaNgram.knowsIn(MaWordLanguage.EN, it) || MaShippedWords.leans(MaWordLanguage.EN, it) || MaShippedWords.isMisspelling(it) },
            knowsHr = { MaNgram.knowsIn(MaWordLanguage.HR, it) || MaShippedWords.leans(MaWordLanguage.HR, it) },
        ) ?: last ?: MaLanguage.active()
    }

    /** A new field starts from the badge again rather than from the last field's language. */
    fun reset() {
        last = null
    }
}
