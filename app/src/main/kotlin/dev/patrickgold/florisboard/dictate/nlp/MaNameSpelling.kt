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

/**
 * His name, spelled his way in every dictation.
 *
 * Marko, 8.11.2026: *"my name is spelled with k, and any dictation should convert marko with c to marko
 * with k, bosko with c to bosko with k."* Both engines hear the Italian spelling ("marco.bosco at
 * gmail.com"). Whole words only, inside an address too ("marco.bosco@" → "marko.bosko@"), keeping the
 * case it was written in.
 */
object MaNameSpelling {

    private val NAMES = listOf("marco" to "marko", "bosco" to "bosko")
    private val PATTERNS = NAMES.map { (from, to) -> Regex("(?i)(?<![\\p{L}\\p{N}])$from(?![\\p{L}\\p{N}])") to to }

    /** His own names, written with a capital even at the start of a lower-cased fragment. */
    fun isName(lower: String): Boolean = lower in OWN

    private val OWN = setOf("marko", "bosko", "boško")

    fun apply(text: String): String {
        var out = text
        for ((re, to) in PATTERNS) {
            out = re.replace(out) { m -> sameCase(m.value, to) }
        }
        return out
    }

    private fun sameCase(heard: String, to: String): String = when {
        heard.all { !it.isLetter() || it.isUpperCase() } -> to.uppercase()
        heard.first().isUpperCase() -> to.replaceFirstChar { it.uppercaseChar() }
        else -> to
    }
}
