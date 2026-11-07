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

import java.util.concurrent.ConcurrentHashMap

/**
 * What the two shipped dictionaries (English and Croatian, both in the APK since 7.11.2026) say about
 * a word, for [MaTypingLanguage].
 *
 * Tested live against SwiftKey (7.11.2026): on a fresh phone the personal models are empty, every
 * word was unknown to both, and an unknown word leans Croatian, so "good m" offered "mogu, mislim".
 * The dictionaries know: "good" is a top English word and absent in Croatian. Croatian is often typed
 * without diacritics ("noc", "mozes"), so its words are also looked up with the diacritics folded.
 *
 * Filled by LatinLanguageProvider as it loads each dictionary; empty (says nothing) until then.
 */
object MaShippedWords {

    /** A language must beat the other by this much on the 128..255 frequency scale to count. */
    private const val MARGIN = 20

    private val byLang = ConcurrentHashMap<String, Map<String, Int>>()

    private fun fold(s: String): String =
        s.replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace('đ', 'd')

    /** Called with a dictionary as loaded (word → frequency). Keys are stored lowercase, Croatian also folded. */
    fun put(lang: String, words: Map<String, Int>) {
        val m = HashMap<String, Int>(words.size * 2)
        for ((w, f) in words) {
            val l = w.lowercase()
            if ((m[l] ?: 0) < f) m[l] = f
            if (lang == MaWordLanguage.HR) {
                val folded = fold(l)
                if (folded != l && (m[folded] ?: 0) < f) m[folded] = f
            }
        }
        byLang[lang] = m
    }

    fun isLoaded(lang: String): Boolean = byLang.containsKey(lang)

    /** True when [word] is clearly a word of [lang] rather than of the other shipped language. */
    fun leans(lang: String, word: String): Boolean {
        if (word.length < 3) return false
        val other = if (lang == MaWordLanguage.EN) MaWordLanguage.HR else MaWordLanguage.EN
        val mine = byLang[lang]?.get(word.lowercase()) ?: 0
        val theirs = byLang[other]?.get(word.lowercase()) ?: 0
        return mine > 0 && mine >= theirs + MARGIN
    }
}
