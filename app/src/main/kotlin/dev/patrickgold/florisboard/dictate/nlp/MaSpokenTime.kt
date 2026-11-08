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
 * A spoken clock time written as one: "the meeting is at 330" → "the meeting is at 3:30".
 *
 * Found on the Pixel 7, 8.11.2026: Parakeet writes "three thirty" as "330". Only after a word that
 * introduces a time (at, by, until, from, before, after, around) and only when it reads as a time:
 * the hour 1–23 and the minutes a multiple of five ("at 2024" stays a year, "at 1037" stays a number).
 */
object MaSpokenTime {

    private val TIME = Regex("""(?i)\b(at|by|until|till|from|before|after|around|about|since)\s+(\d{1,2})(\d{2})\b(?![:.,]\d)""")

    fun apply(text: String): String = TIME.replace(text) { m ->
        val hour = m.groupValues[2].toInt()
        val minutes = m.groupValues[3].toInt()
        val digits = m.groupValues[2] + m.groupValues[3]
        if (hour in 1..23 && minutes in 0..55 && minutes % 5 == 0 && !(digits.length == 4 && digits.startsWith("0"))) {
            "${m.groupValues[1]} $hour:${m.groupValues[3]}"
        } else {
            m.value
        }
    }
}
