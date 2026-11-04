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

import java.util.Locale

/**
 * HIDDEN CODE WORDS: A NAME IN A REPROMPT THAT STANDS FOR A WHOLE INSTRUCTION, KEPT IN THE CODE.
 *
 * Marko, 4.11.2026: "Please hardcode right inside application Ashai Afterman style … when I'm
 * reprompting, when I refer to Ashai Afterman or something similar, if there is misspelling, it will
 * recognize from the code. And this is first of our hidden code words … We're going to find more."
 *
 * A reprompt instruction ("make it Yshai Afterman style") is read for the code words below. Each one
 * found, however it is spelled or misheard (Ashai, Ishai, Jišai, Aftermen, after man), adds its full
 * description to what is sent, so Claude is told exactly what the name means instead of guessing.
 *
 * To add the next code word: one more [CodeWord] in [ALL], with the spellings it answers to and the
 * description. Nothing else changes. Walked by `scripts/test_code_words.py`.
 */
object MaCodeWords {

    /**
     * One code word: its [name], the [keys] it is recognised by (each key is one word or two words;
     * a word matches a key word within [MaCodeWords.close]), and the [description] that is sent.
     */
    data class CodeWord(val name: String, val keys: List<List<String>>, val description: String)

    /**
     * YSHAI AFTERMAN, the teaching voice (MANTRA_MANIFEST modules/writing-styles.md §2, taken from
     * two of his letters, 5.8. and 16.8.2026). Normal sentence capitals and correct apostrophes: his
     * lowercase is the one thing not copied (Marko, 4.11.2026: "don't make it all small caps").
     */
    val YSHAI_AFTERMAN = CodeWord(
        name = "Yshai Afterman style",
        keys = listOf(
            listOf("yshai", "afterman"),
            listOf("afterman"),
            listOf("yshai"),
        ),
        description =
            "The Yshai Afterman style is a teaching voice, for anything where the reader needs to " +
                "understand why something matters before being asked for anything. Write it like this:\n" +
                "Short paragraphs of one or two sentences, almost never three.\n" +
                "Say the thing, then the honest counterweight to it. The qualification is what makes the " +
                "claim trustworthy (\"Speed is not the enemy. Technique matters, because you cannot say " +
                "anything if your hands cannot form the words. But technique is the vocabulary, not the " +
                "poem.\").\n" +
                "Witness instead of asserting: say what was seen, not what is true in general (\"I have " +
                "watched players spend years building dazzling hands and still leave a room cold.\").\n" +
                "One image, carried through. Never a stack of metaphors.\n" +
                "The principle before the request, so the ask arrives already justified and reads as an " +
                "invitation rather than an instruction.\n" +
                "Practical facts bare: time, place, link, price, with no apology and no padding.\n" +
                "End by taking a worry away from the reader, in the last line (\"If you cannot be there " +
                "live, nothing is lost.\").\n" +
                "Written to one person as \"you\", even when it goes to many.\n" +
                "Normal sentence capitalisation, a capital after every full stop, and correct " +
                "apostrophes. Never all lowercase.\n" +
                "No exclamation marks, no emoji, no bullet points, no dashes. Warm without gushing. " +
                "Nothing said twice, nothing said that does no work.\n" +
                "Keep the meaning and every fact of the text; change its voice, not what it says.",
    )

    /** Every code word, in the order they are added to the instruction. */
    val ALL: List<CodeWord> = listOf(YSHAI_AFTERMAN)

    /** Lowercase words of [text], with č ć ž š đ read as c c z s d and j/i/a/e/y before "shai" as y. */
    fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .map { c ->
                when (c) {
                    'č', 'ć' -> 'c'
                    'ž' -> 'z'
                    'š' -> 's'
                    'đ' -> 'd'
                    else -> c
                }
            }
            .joinToString("")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

    /**
     * Whether [heard] is [key] misspelled or misheard: the same word, or within two edits for words
     * of six letters or more (afterman ← aftermen, aftreman, afterma), one for shorter (yshai ← ashai,
     * ishai, jshai, isai). Words of three letters or fewer match only exactly.
     */
    fun close(heard: String, key: String): Boolean {
        if (heard == key) return true
        if (heard in REAL_WORDS) return false
        if (key.length <= 3 || heard.length <= 3) return false
        val allowed = if (key.length >= 6) 2 else 1
        if (kotlin.math.abs(heard.length - key.length) > allowed) return false
        return distance(heard, key) <= allowed
    }

    /** Real words that are near a key but mean something else: never a code word. */
    private val REAL_WORDS = setOf("aftermath", "aftermaths", "shah", "shai")

    /** Damerau-Levenshtein (optimal string alignment): a swap of two neighbours is one edit. */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /** Whether [text] names [word], under any of its keys, in any spelling within [close]. */
    fun mentions(text: String, word: CodeWord): Boolean {
        val w = words(text)
        // "after man" said as two words is also "afterman"; only with "man" or "men" second, or "a man
        // after my own heart" would join into something close to it.
        val joined = w.zipWithNext { a, b -> if (b == "man" || b == "men") a + b else "" }.filter { it.isNotEmpty() }
        return word.keys.any { key ->
            when (key.size) {
                1 -> w.any { close(it, key[0]) } || joined.any { close(it, key[0]) }
                else -> w.windowed(key.size).any { win -> win.indices.all { close(win[it], key[it]) } }
            }
        }
    }

    /** The code words [text] names. */
    fun found(text: String): List<CodeWord> = ALL.filter { mentions(text, it) }

    /**
     * [instruction] with the code words added after it.
     *
     * Every code word is listed, always, with its description (Marko, 4.11.2026: "for some unknown
     * terms … this application must point AI to look at our code words, and then it will find it").
     * The spelling match above is the app's own guess; the list lets Claude find a code word the app
     * did not catch, from a term it does not recognise. One the app did catch is named first, so
     * Claude does not have to guess at all.
     */
    fun expand(instruction: String): String {
        val hits = found(instruction)
        return buildString {
            append(instruction.trim())
            append("\n\n").append(INDEX_RULE)
            if (hits.isNotEmpty()) {
                append("\nThe instruction above names: ").append(hits.joinToString(", ") { it.name }).append('.')
            }
            for (w in ALL) {
                append("\n\nCode word \"").append(w.name).append("\":\n").append(w.description)
            }
        }
    }

    /** What Claude is told about the list. */
    const val INDEX_RULE =
        "Code words: the user has private code words, listed below. If a term in the instruction is " +
            "unfamiliar, or looks like a misspelled or misheard form of one of these names, it means " +
            "that code word: follow its description. Use a code word only when the instruction refers " +
            "to it; otherwise ignore the list."
}
