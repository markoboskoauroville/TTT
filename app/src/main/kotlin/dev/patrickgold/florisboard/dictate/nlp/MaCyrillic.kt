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
 * Cyrillic written back in Croatian Latin letters.
 *
 * Found on the real Pixel 7, 8.11.2026: Croatian played through the phone's speaker and recorded by
 * TTT came out of Parakeet as "Можеш ли ми послати адрес у ресторана? Молим те." The model takes
 * Croatian for Serbian (or Russian) and writes Cyrillic. Marko writes Latin only, so whatever Parakeet
 * writes in Cyrillic is put back letter by letter: Serbian Cyrillic maps one to one onto Gaj's Latin
 * (ж ž, ш š, ч č, ћ ć, ђ đ, љ lj, њ nj, џ dž, ј j), and the Russian letters it sometimes borrows get
 * their nearest Croatian spelling (я ja, ю ju, щ šč, ы i, э e, й j, ь and ъ dropped).
 */
object MaCyrillic {

    private val MAP: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e", 'ж' to "ž",
        'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n",
        'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š",
        'я' to "ja", 'ю' to "ju", 'ё' to "jo", 'й' to "j", 'щ' to "šč", 'ы' to "i", 'э' to "e",
        'ь' to "", 'ъ' to "", 'є' to "je", 'і' to "i", 'ї' to "ji", 'ґ' to "g", 'ѓ' to "đ", 'ќ' to "ć",
        'ѕ' to "dz",
    )

    fun hasCyrillic(text: String): Boolean = text.any { it in 'Ѐ'..'ӿ' }

    /** [text] with every Cyrillic letter in Croatian Latin; text without Cyrillic is returned as is. */
    fun toLatin(text: String): String {
        if (!hasCyrillic(text)) return text
        val sb = StringBuilder(text.length)
        for ((i, c) in text.withIndex()) {
            val lower = c.lowercaseChar()
            val latin = MAP[lower]
            if (latin == null) { sb.append(c); continue }
            if (c == lower || latin.isEmpty()) { sb.append(latin); continue }
            // Upper case: "Љубав" → "Ljubav", but "ЉУБАВ" → "LJUBAV".
            val next = text.getOrNull(i + 1)
            val wholeWordUpper = next != null && next.isLetter() && next.isUpperCase()
            sb.append(if (wholeWordUpper) latin.uppercase() else latin.replaceFirstChar { it.uppercaseChar() })
        }
        return sb.toString()
    }
}
