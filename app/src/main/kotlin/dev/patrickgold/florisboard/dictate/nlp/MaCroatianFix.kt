/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.dictate.nlp

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.log10

/**
 * Croatian dictation corrected on the phone, with no key and no network.
 *
 * Marko, 8.11.2026: *"since local engine doesn't support [Croatian] and it makes a lot of mistakes,
 * first of all, try to force it not to make mistakes. But if it makes mistakes, it needs to be
 * corrected by this application."* Parakeet has no language switch, so the forcing happens after it:
 * Cyrillic is written in Latin ([MaCyrillic]) and then every word is checked against the keyboard's own
 * Croatian dictionary and its word pairs and triples (assets/ime/dict/hr*), the same data that lets
 * the keyboard type "sto radis veceras" as "Što radiš večeras".
 *
 * Per word, left to right, using the already corrected words before it as context:
 * - **missing diacritics** ("mozes", "rucku", "doci"): the form with diacritics;
 * - **a known plain word far rarer than its diacritic twin** ("kuci" 178 vs "kući" 209) when the
 *   context does not prefer the plain one;
 * - **a rare word with a far commoner twin** one letter away and the context behind the twin
 *   ("žal mi je" → "žao mi je");
 * - **an unknown word** of four or more letters ("kavo", "bozji"): the commonest Croatian word one edit
 *   away (or with "ije" for an ikavian "i": "mliko" → "mlijeko"), scored by frequency plus how often it follows the words before it ("idemo na kavu"); a change
 *   of length costs extra, so "kavo" becomes "kavu" rather than "kao";
 * - **a word split in two** ("adres u restorana"): joined when the two pieces make a known word;
 * - **words split in the wrong places** ("li je pdan" for "lijep dan", VM, 8.11.2026): an unknown piece
 *   and its neighbours are joined and split again into FEWER known words, commonest first.
 *
 * Never touched: numbers, English words (in the English dictionary), and capitalised words inside a
 * sentence (names: "Auroville", "Boško"). The Claude or phone-model spell check runs after this, when
 * it can, on text that is already closer.
 */
object MaCroatianFix {

    private const val MIN_FREQ = 170
    private const val MIN_FREQ_SAME_LENGTH = 150
    private const val RARE = 140
    private const val RARE_MARGIN = 60
    private const val COMMON = 200
    private const val DIACRITIC_MARGIN = 30
    private const val LENGTH_CHANGE_COST = 30.0

    @Volatile private var hr: Map<String, Int>? = null
    @Volatile private var en: Set<String> = emptySet()
    @Volatile private var folded: Map<String, Pair<String, Int>> = emptyMap()
    @Volatile private var bigrams: Map<String, Int> = emptyMap()
    @Volatile private var trigrams: Map<String, Int> = emptyMap()

    private val WORD = Regex("""[\p{L}]+|[^\p{L}]+""")
    private val SENTENCE_END = Regex("""[.!?]\s*$""")

    private fun fold(s: String): String =
        s.replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace('đ', 'd')

    private fun freqMap(context: Context, asset: String): Map<String, Int> {
        val obj = Json.parseToJsonElement(context.assets.open(asset).bufferedReader().use { it.readText() }).jsonObject
        val m = HashMap<String, Int>(obj.size * 2)
        for ((w, v) in obj) {
            val l = w.lowercase()
            val f = v.jsonPrimitive.int
            if ((m[l] ?: 0) < f) m[l] = f
        }
        return m
    }

    private fun counts(context: Context, asset: String): Map<String, Int> {
        val m = HashMap<String, Int>(160_000)
        context.assets.open(asset).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val tab = line.lastIndexOf('\t')
                if (tab > 0) line.substring(tab + 1).toIntOrNull()?.let { m[line.substring(0, tab)] = it }
            }
        }
        return m
    }

    /** Loads the dictionary data once (a few hundred ms); false when it cannot be read. */
    @Synchronized
    fun load(context: Context): Boolean {
        if (hr != null) return true
        return runCatching {
            val words = freqMap(context, "ime/dict/hr.json")
            val f = HashMap<String, Pair<String, Int>>(words.size)
            for ((w, n) in words) {
                val k = fold(w)
                if (n > (f[k]?.second ?: 0)) f[k] = w to n
            }
            en = freqMap(context, "ime/dict/en.json").keys
            bigrams = counts(context, "ime/dict/hr_bigrams.txt")
            trigrams = counts(context, "ime/dict/hr_trigrams.txt")
            folded = f
            hr = words
            true
        }.getOrElse { false }
    }

    /** True when more of [text]'s words are Croatian than English (for a Croatian dictation on the E key). */
    fun looksCroatian(context: Context, text: String): Boolean {
        if (!load(context)) return false
        val words = Regex("""\p{L}+""").findAll(text.lowercase()).map { it.value }.filter { it.length >= 2 }.toList()
        if (words.size < 2) return false
        val h = hr ?: return false
        val hrOnly = words.count { (it in h || fold(it) in folded) && it !in en }
        val enOnly = words.count { it in en && it !in h }
        return hrOnly > enOnly
    }

    /** [text] with its Croatian corrected; unchanged when the dictionary cannot be loaded. */
    fun apply(context: Context, text: String): String {
        if (text.isBlank() || !load(context)) return text
        return runCatching { correct(resegment(merge(text))) }.getOrDefault(text)
    }

    private fun ctx(prev2: String?, prev: String?, next: String?, cand: String): Double {
        var s = 0.0
        if (prev != null) s += 40 * log10(1.0 + (bigrams["$prev $cand"] ?: 0))
        if (next != null) s += 15 * log10(1.0 + (bigrams["$cand $next"] ?: 0))
        if (prev2 != null && prev != null) s += 40 * log10(1.0 + (trigrams["$prev2 $prev $cand"] ?: 0))
        return s
    }

    private fun edits(w: String): Set<String> {
        val out = HashSet<String>()
        val letters = "abcdefghijklmnoprstuvzj"
        for (i in 0..w.length) {
            if (i < w.length) out.add(w.removeRange(i, i + 1))
            if (i + 1 < w.length) out.add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
            for (c in letters) {
                if (i < w.length && c != w[i]) out.add(w.substring(0, i) + c + w.substring(i + 1))
                out.add(w.substring(0, i) + c + w.substring(i))
            }
        }
        return out
    }

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y')

    /** Parakeet mishears endings ("kavo" for "kavu") far more often than consonants ("kavo" for "kako"). */
    private fun shape(f: String, c: String): Double {
        if (c.length != f.length) return 0.0
        val diff = f.indices.filter { f[it] != c[it] }
        if (diff.size != 1) return 0.0
        val i = diff[0]
        val fv = f[i] in VOWELS
        val cv = c[i] in VOWELS
        return when {
            fv && cv -> if (i == f.length - 1) 20.0 else 5.0
            !fv && !cv -> -30.0
            else -> 0.0
        }
    }

    private fun fixWord(w: String, prev2: String?, prev: String?, next: String?): String {
        val h = hr ?: return w
        val l = w.lowercase()
        val f = fold(l)
        val known = h[l]
        if (known != null && known < RARE && f.length >= 3) {
            // A rare known word with a far commoner twin one letter away: "žal mi je" → "žao mi je".
            val here = ctx(prev2, prev, next, l)
            var best: String? = null
            var bestScore = Double.NEGATIVE_INFINITY
            for (c in edits(f)) {
                if (c.length != f.length) continue
                val (d, df) = folded[c] ?: continue
                if (df < known + RARE_MARGIN) continue
                val there = ctx(prev2, prev, next, d)
                if (there <= here) continue
                if (df + there > bestScore) { best = d; bestScore = df + there }
            }
            if (best != null) return best
        }
        if (known != null) {
            val (d, df) = folded[f] ?: return l
            if (d == l || df < known + DIACRITIC_MARGIN) return l
            val there = ctx(prev2, prev, next, d)
            val here = ctx(prev2, prev, next, l)
            // A common plain word ("sto", a hundred) stays unless the context prefers the twin ("što").
            return if (there > here || (there == here && known < COMMON)) d else l
        }
        folded[f]?.let { return it.first }
        if (f.length < 4 || l in en) return l
        var best: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val candidates = edits(f).toMutableSet()
        // Ikavian or shortened "ije" ("mliko" → "mlijeko", "lipo" → "lijepo").
        f.forEachIndexed { i, ch ->
            if (ch == 'i') {
                candidates.add(f.substring(0, i) + "ije" + f.substring(i + 1))
                candidates.add(f.substring(0, i) + "je" + f.substring(i + 1))
            }
        }
        for (c in candidates) {
            val (d, df) = folded[c] ?: continue
            if (df < (if (c.length == f.length) MIN_FREQ_SAME_LENGTH else MIN_FREQ)) continue
            val score = df + ctx(prev2, prev, next, d) - (if (c.length == f.length) 0.0 else LENGTH_CHANGE_COST) + shape(f, c)
            if (score > bestScore) { best = d; bestScore = score }
        }
        return best ?: l
    }

    /** "adres u restorana": an unknown word and a short next piece that together make a known word. */
    private fun merge(text: String): String {
        val h = hr ?: return text
        return Regex("""\b(\p{L}{3,}) (\p{L}{1,3})\b""").replace(text) { m ->
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            if (a.lowercase() !in h && a.lowercase() !in en && fold((a + b).lowercase()) in folded) a + b else m.value
        }
    }

    private val SINGLE_LETTER_WORDS = setOf("i", "u", "a", "s", "k", "o", "v")

    /** The best split of [letters] (folded) into one to three known words, or null. */
    private fun segment(letters: String): List<String>? {
        val n = letters.length
        val scores = arrayOfNulls<Pair<Int, List<String>>>(n + 1)
        scores[0] = 0 to emptyList()
        for (i in 1..n) {
            for (j in maxOf(0, i - 14) until i) {
                val from = scores[j] ?: continue
                if (from.second.size >= 3) continue
                val piece = letters.substring(j, i)
                if (piece.length == 1 && piece !in SINGLE_LETTER_WORDS) continue
                val (word, f) = folded[piece] ?: continue
                if (f < 150) continue
                val score = from.first + f - 40
                val at = scores[i]
                if (at == null || score > at.first) scores[i] = score to (from.second + word)
            }
        }
        return scores[n]?.second
    }

    private fun resegment(text: String): String {
        val h = hr ?: return text
        val toks = text.split(' ').toMutableList()
        fun core(t: String) = t.filter { it.isLetter() }.lowercase()
        fun plain(t: String) = t.isNotEmpty() && t.dropLastWhile { !it.isLetter() }.all { it.isLetter() } && t.first().isLetter()
        var i = 0
        while (i < toks.size) {
            val c = core(toks[i])
            if (c.isNotEmpty() && plain(toks[i]) && c !in h && fold(c) !in folded && c !in en) {
                var changed = false
                for ((a, b) in listOf(i - 2 to i, i - 1 to i, i to i + 1, i - 1 to i + 1)) {
                    if (a < 0 || b >= toks.size) continue
                    val win = toks.subList(a, b + 1).toList()
                    // Only the last piece may carry punctuation; no names inside.
                    if (win.dropLast(1).any { !it.all { ch -> ch.isLetter() } } || !win.all { plain(it) }) continue
                    if (win.drop(1).any { it.first().isUpperCase() }) continue
                    val seg = segment(fold(win.joinToString("") { core(it) })) ?: continue
                    if (seg.size >= win.size) continue
                    val trail = win.last().dropWhile { it.isLetter() }
                    var joined = seg.joinToString(" ")
                    if (win.first().first().isUpperCase()) joined = joined.replaceFirstChar { it.uppercaseChar() }
                    repeat(win.size) { toks.removeAt(a) }
                    toks.add(a, joined + trail)
                    i = a + 1
                    changed = true
                    break
                }
                if (changed) continue
            }
            i++
        }
        return toks.joinToString(" ")
    }

    private fun correct(text: String): String {
        val toks = WORD.findAll(text).map { it.value }.toMutableList()
        val wordIdx = toks.indices.filter { toks[it].first().isLetter() }
        val before = StringBuilder()
        var tokCursor = 0
        for ((n, i) in wordIdx.withIndex()) {
            while (tokCursor < i) { before.append(toks[tokCursor]); tokCursor++ }
            val t = toks[i]
            val sentenceStart = n == 0 || SENTENCE_END.containsMatchIn(before)
            if (t.first().isUpperCase() && !sentenceStart) continue
            if (t.length > 1 && t.all { it.isUpperCase() }) continue
            val prev = if (n > 0) toks[wordIdx[n - 1]].lowercase() else null
            val prev2 = if (n > 1) toks[wordIdx[n - 2]].lowercase() else null
            val next = wordIdx.getOrNull(n + 1)?.let { toks[it].lowercase() }
            val fixed = fixWord(t, prev2, prev, next)
            if (fixed == t.lowercase()) continue
            toks[i] = if (t.first().isUpperCase()) fixed.replaceFirstChar { it.uppercaseChar() } else fixed
        }
        return toks.joinToString("")
    }
}
