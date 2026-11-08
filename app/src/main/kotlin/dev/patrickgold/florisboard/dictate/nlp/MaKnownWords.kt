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

/**
 * Snaps the names and words of his own world back to their right spelling, after the speech model.
 *
 * ### Why
 *
 * Parakeet has no prompt and no word list (its output cannot be steered the way AssemblyAI's can), and
 * it spells what it does not know the way it sounds: Auroville comes out as "Orville", "Wilbers",
 * "orwheel", Yshai Afterman as "Ashai Afterman", frame drum as "Rend Drum" (Marko, 8.11.2026: "a lot
 * of misspelling"). [MaDictationCorrections] fixes only what he has already corrected once by hand.
 * This fixes the words that are his every day, from the first dictation.
 *
 * ### How, in two steps
 *
 * 1. **Aliases**: phrases he is known to produce for a word ("rend drum" for "frame drum"), replaced
 *    whole, any case. Certain, so they are always applied.
 * 2. **Sound-alikes**: a run of one to four words that sounds like a fuzzy entry is replaced by the
 *    entry. "Sounds like" is the edit distance between two sound skeletons (see [skeleton]), with one
 *    slip allowed in a word of six letters or more, two in a word of eleven or more, none below.
 *    Only entries marked `fuzzy` take part, and only rare words are marked: a common name (Natasha)
 *    next to a real one (Natalia) would be the same distance apart, so those get aliases only.
 *
 * Pure Kotlin, no Android: it runs in a unit test. No network, nothing leaves the phone.
 */
object MaKnownWords {

    /**
     * One word or name, spelled right.
     *
     * @param aliases phrases that are always this entry, replaced whole and in any case.
     * @param fuzzy whether a sound-alike also becomes this entry.
     */
    class Entry(val canonical: String, val aliases: List<String> = emptyList(), val fuzzy: Boolean = true)

    /** His world: the words dictation gets wrong most. Grow it from `FACTS.md` of marko_communication. */
    val BUILT_IN: List<Entry> = listOf(
        Entry(
            "Auroville",
            aliases = listOf("wilbers", "orwheel", "orville", "orovil", "our will community"),
        ),
        Entry("Pondicherry", aliases = listOf("pondy cherry", "pondi cherry", "the cherry")),
        Entry("Santoshi"),
        Entry("Santoshi Kirtan Band"),
        Entry("Yshai Afterman", aliases = listOf("ashai afterman", "yshai aferman", "ishai afterman")),
        Entry("Mantreshwar", aliases = listOf("mantreshvar", "mantreswar")),
        Entry("Mantra Productions", aliases = listOf("mantra production")),
        Entry("MPA Academy", aliases = listOf("precaution school", "percussion academy"), fuzzy = false),
        Entry("frame drum", aliases = listOf("rend drum", "trim drum", "trem drum"), fuzzy = false),
        Entry("Venkatesh"),
        Entry("Fiverr", aliases = listOf("fever profile", "fever account", "fever gig"), fuzzy = false),
        Entry("Ableton Live", fuzzy = false),
        Entry("DaVinci Resolve", aliases = listOf("da vinci resolve", "divinci resolve")),
        Entry("Claude", aliases = listOf("cloud code", "cloud ai", "load code"), fuzzy = false),
        Entry("Hume AI", aliases = listOf("hume i"), fuzzy = false),
        Entry("Kanal Ri", aliases = listOf("canal v", "canal ri", "kanal v"), fuzzy = false),
    )

    /** Longest run of words looked at for one entry, however long its name is. */
    private const val MAX_WINDOW = 4

    /** A token: letters and digits, with an apostrophe-s kept so "Auroville's" stays one. */
    private val TOKEN = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    /** What an entry is compared against, prepared once. */
    private class Prepared(
        val entry: Entry,
        val words: Int,
        val skeleton: String,
        val letters: Int,
        /** One skeleton per word of the name, to be matched word for word. */
        val wordSkeletons: List<String>,
        val wordLetters: List<Int>,
    )

    /**
     * [text] with the known words spelled right.
     *
     * @param extra words of his own (the keyboard's custom words, the right forms he taught): fuzzy
     *   entries too, but only those of seven letters or more, so a common word is not pulled in.
     * @param isCommonWord true for an ordinary word that must never be replaced by a sound-alike.
     */
    fun apply(
        text: String,
        extra: List<String> = emptyList(),
        isCommonWord: (String) -> Boolean = { false },
    ): String {
        if (text.isBlank()) return text
        val entries = BUILT_IN + extra.asSequence()
            .map { it.trim() }
            .filter { it.length >= 7 && it.none { c -> c.isWhitespace() } }
            .distinct()
            .map { Entry(it) }
            .toList()
        val aliased = applyAliases(text, entries)
        return applySoundAlikes(aliased, entries, isCommonWord)
    }

    // ---------------------------------------------------------------- step 1: aliases

    private fun applyAliases(text: String, entries: List<Entry>): String {
        var out = text
        for (e in entries) {
            for (alias in e.aliases) {
                val re = Regex(
                    "(?i)(?<![\\p{L}\\p{N}])" + alias.trim().split(Regex("\\s+"))
                        .joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}\\p{N}])",
                )
                out = re.replace(out) { e.canonical }
            }
        }
        return out
    }

    // ---------------------------------------------------------------- step 2: sound-alikes

    private class Tok(val start: Int, val end: Int, val word: String, val tail: String)

    private fun tokens(text: String): List<Tok> = TOKEN.findAll(text).map { m ->
        val v = m.value
        val cut = v.indexOfFirst { it == '\'' || it == '’' }
        if (cut > 0) Tok(m.range.first, m.range.last + 1, v.substring(0, cut), v.substring(cut))
        else Tok(m.range.first, m.range.last + 1, v, "")
    }.toList()

    private fun applySoundAlikes(text: String, entries: List<Entry>, isCommonWord: (String) -> Boolean): String {
        val prepared = entries.filter { it.fuzzy }.map {
            val letters = it.canonical.filter { c -> c.isLetter() }
            val parts = it.canonical.trim().split(Regex("\\s+"))
            Prepared(
                it, parts.size, skeleton(letters), letters.length,
                parts.map { w -> skeleton(w) }, parts.map { w -> w.count { c -> c.isLetter() } },
            )
        }.filter { it.skeleton.isNotEmpty() }
        if (prepared.isEmpty()) return text
        val toks = tokens(text)
        val out = StringBuilder()
        var cursor = 0
        var i = 0
        while (i < toks.size) {
            val hit = bestAt(text, toks, i, prepared, isCommonWord)
            if (hit == null) {
                i++
                continue
            }
            val (len, p, already) = hit
            // Already right: step over the whole name, so its second word is not read as a name by itself.
            if (already) {
                i += len
                continue
            }
            val first = toks[i]
            val last = toks[i + len - 1]
            out.append(text, cursor, first.start).append(p.entry.canonical).append(last.tail)
            cursor = last.end
            i += len
        }
        out.append(text, cursor, text.length)
        return out.toString()
    }

    /** The entry that the words from [i] sound like, and how many words that takes, or null. */
    private fun bestAt(
        text: String,
        toks: List<Tok>,
        i: Int,
        prepared: List<Prepared>,
        isCommonWord: (String) -> Boolean,
    ): Hit? {
        var best: Triple<Int, Prepared, Int>? = null
        for (p in prepared) {
            val lo = (p.words - 1).coerceAtLeast(1)
            val hi = (p.words + 1).coerceAtMost(MAX_WINDOW)
            for (len in lo..hi) {
                if (i + len > toks.size) break
                if (len > 1 && !joinedBySpaces(text, toks, i, len)) break
                val window = (i until i + len).joinToString("") { toks[it].word }
                if (window.any { it.isDigit() }) continue
                val spoken = (i until i + len).joinToString(" ") { toks[it].word }
                if (spoken == p.entry.canonical) return Hit(len, p, true)
                if (len == 1 && isCommonWord(spoken.lowercase())) continue
                val letters = window.length
                if (letters < 5) continue
                val allowed = allowedSlips(p.letters)
                // Not much longer or shorter than the name: "in Pondicherry" is not a Pondicherry.
                if (kotlin.math.abs(letters - p.letters) > allowed + 1) continue
                if (allowed == 0 && spoken.equals(p.entry.canonical, ignoreCase = true).not()) continue
                val d = distance(skeleton(window), p.skeleton, allowed)
                if (d > allowed) continue
                // A name of several words is matched word for word: "the Aftermann" is not "Yshai Afterman"
                // because the two as a whole are near. A word of five letters or fewer must sound the same.
                if (len == p.words && len > 1 && !wordsMatch(toks, i, p)) continue
                // A name split in a different number of words ("Aura Ville") must sound exactly the same,
                // else a word before it ("his Pondicherry") would be swallowed with it.
                if (len != p.words && d != 0) continue
                // Fewest slips first, then the window nearest in length to the name.
                val better = best == null || d < best.third ||
                    (d == best.third && kotlin.math.abs(len - p.words) < kotlin.math.abs(best.first - best.second.words))
                if (better) best = Triple(len, p, d)
            }
        }
        return best?.let { Hit(it.first, it.second, false) }
    }

    /** [len] words from the start sound like [prepared]; [already] when they are spelled right as they are. */
    private class Hit(val len: Int, val prepared: Prepared, val already: Boolean) {
        operator fun component1() = len
        operator fun component2() = prepared
        operator fun component3() = already
    }

    private fun wordsMatch(toks: List<Tok>, i: Int, p: Prepared): Boolean {
        for (k in 0 until p.words) {
            val allowed = allowedSlips(p.wordLetters[k])
            if (distance(skeleton(toks[i + k].word), p.wordSkeletons[k], allowed) > allowed) return false
        }
        return true
    }

    private fun joinedBySpaces(text: String, toks: List<Tok>, i: Int, len: Int): Boolean {
        for (k in i until i + len - 1) {
            val gap = text.substring(toks[k].end, toks[k + 1].start)
            if (toks[k].tail.isNotEmpty() || gap != " ") return false
        }
        return true
    }

    private fun allowedSlips(letters: Int): Int = when {
        letters >= 11 -> 2
        letters >= 6 -> 1
        else -> 0
    }

    /**
     * What a word sounds like, as letters: the spelling differences that speech models make (c/k,
     * ph/f, w/v, double letters, which vowel) disappear, the consonants stay.
     */
    internal fun skeleton(word: String): String {
        var s = word.lowercase().filter { it.isLetter() }
        s = s.replace("sch", "x").replace("sh", "x").replace("ch", "x").replace("ph", "f")
            .replace("ck", "k").replace("qu", "kv")
        val sb = StringBuilder()
        for ((idx, c) in s.withIndex()) {
            val next = s.getOrNull(idx + 1)
            val mapped = when (c) {
                'a', 'e', 'i', 'o', 'u', 'y' -> 'a'
                'w' -> 'v'
                'q' -> 'k'
                'c' -> if (next == 'e' || next == 'i' || next == 'y') 's' else 'k'
                'z' -> 's'
                'h' -> if (sb.isEmpty()) continue else if (sb.last() == 'a') continue else 'h'
                else -> c
            }
            // Doubles and vowel runs count once.
            if (sb.isNotEmpty() && sb.last() == mapped) continue
            sb.append(mapped)
        }
        return sb.toString()
    }

    /** Edit distance of [a] and [b], giving up above [limit] (then any number above it is returned). */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }
}
