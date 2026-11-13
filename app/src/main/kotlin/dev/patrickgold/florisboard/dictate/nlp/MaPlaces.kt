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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * THE PLACE BOOK: streets, towns, post codes, world cities, offered whole while they are typed.
 *
 * Marko, 13.11.2026: *"Please update this application ... that it understands special kinds of data. Phone numbers,
 * email addresses, also addresses ... if I start to type street, then it just search in the database of all the
 * streets and find which streets I want to type. If I type the name of the city, it understands. You find the logic
 * behind it."*
 *
 * The database is shipped, offline (assets/dictate/places.tsv, built by tools/places/build_places.py from
 * OpenStreetMap and GeoNames): every named street of Croatia with the towns it has house numbers in, every Croatian
 * town and village with its post code, and the world's cities of 15 000 people or more. **Nothing typed leaves the
 * phone**: a keyboard that sent each keystroke to a map service would be a keylogger.
 *
 * THE LOGIC (when a place is offered, so ordinary words are never crowded out):
 * - **an address field** (Android says so): every street and town from the second letter, three of them;
 * - **a place being written**: a word started with a capital inside a sentence ("idem u Zag" → Zagreb, "Savs" →
 *   Savska cesta, "Ulica gr" → Ulica grada Vukovara), or any word after an address cue ("ulica", "adresa",
 *   "živim u", "address", "street"); a capital that only opens a sentence does not count;
 * - **street, number, comma** ("Ilica 5, "): the post code and town that street is in ("10000 Zagreb");
 * - **a post code** being typed ("100" → "10000 Zagreb"), and a whole town after an address ("Ilica 5, Zagreb" → the
 *   code in front of it).
 *
 * Letters are compared without diacritics, so "zitnjak" finds Žitnjak and "cakovec" Čakovec.
 */
object MaPlaces {

    private class Place(val name: String, val kind: Char, val extra: String, val weight: Int, val folded: String)

    private const val STREET = 's'
    private const val TOWN = 'c'
    private const val WORLD = 'w'

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var places: List<Place> = emptyList()
    /** Folded first letter of the name, or of any later word of it → the places that start so, heaviest first. */
    @Volatile private var byLetter: Map<Char, List<Place>> = emptyMap()
    @Volatile private var townCode: Map<String, String> = emptyMap()
    @Volatile private var streetTowns: Map<String, List<String>> = emptyMap()
    @Volatile private var codeTowns: List<Pair<String, String>> = emptyList()

    /** What each offered place replaces before the cursor (the strip commits it with this). */
    private val offered = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private val CUE = Regex(
        """(?i)\b(ulica|ulici|ul\.|adresa|adresi|adresu|živim u|zivim u|stanujem u|nalazi se u|address( is)?|street|lives at|live at|located at)\s*:?\s*$""",
    )
    private val STREET_NUMBER_COMMA = Regex("""(\p{L}[\p{L}\d .'-]*?)\s+\d+\s*[a-zA-Z]?\s*,\s*$""")
    private val STREET_NUMBER_COMMA_TOWN = Regex("""\p{L}[\p{L}\d .'-]*?\s+\d+\s*[a-zA-Z]?\s*,\s*(\p{L}[\p{L} -]*)$""")
    private val POSTCODE_TYPED = Regex("""(?:^|[\s,])(\d{3,5})$""")

    fun initialize(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val list = ArrayList<Place>(70_000)
                app.assets.open("dictate/places.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("#")) return@forEach
                        val f = line.split('\t')
                        if (f.size >= 4 && f[0].length == 1) {
                            list.add(Place(f[1], f[0][0], f[2], f[3].toIntOrNull() ?: 0, fold(f[1])))
                        }
                    }
                }
                val letters = HashMap<Char, ArrayList<Place>>()
                for (p in list) {
                    val starts = HashSet<Char>()
                    p.folded.split(' ', '-').forEach { w -> w.firstOrNull()?.let { if (it.isLetterOrDigit()) starts.add(it) } }
                    starts.forEach { letters.getOrPut(it) { ArrayList() }.add(p) }
                }
                letters.values.forEach { l -> l.sortByDescending { rank(it) } }
                townCode = list.filter { it.kind == TOWN && it.extra.isNotEmpty() }
                    .sortedByDescending { it.weight }.reversed().associate { it.folded to it.extra }
                streetTowns = list.filter { it.kind == STREET && it.extra.isNotEmpty() }
                    .associate { it.folded to it.extra.split(',') }
                codeTowns = list.filter { it.kind == TOWN && it.extra.isNotEmpty() }
                    .sortedByDescending { it.weight }.map { it.extra to it.name }
                places = list
                byLetter = letters
                android.util.Log.i("MaPlaces", "loaded ${list.size} places")
            }
        }
    }

    /**
     * One scale for all three: a town by its people, a street by its house numbers (one address ≈ 60 people), a
     * world city by a five-hundredth of its people, so Croatia comes first and Ilica (278 addresses) beats a village.
     */
    private fun rank(p: Place): Long = when (p.kind) {
        TOWN -> p.weight.toLong()
        STREET -> p.weight.toLong() * 60
        else -> p.weight.toLong() / 500
    }

    /** Lower case, without diacritics: "Ž" and "z" are one letter here. */
    fun fold(s: CharSequence): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                'č', 'ć', 'Č', 'Ć' -> 'c'
                'š', 'Š' -> 's'
                'ž', 'Ž' -> 'z'
                'đ', 'Đ' -> 'd'
                else -> {
                    val l = c.lowercaseChar()
                    val n = java.text.Normalizer.normalize(l.toString(), java.text.Normalizer.Form.NFD)
                    n.first()
                }
            },
        )
    }

    fun isOffered(text: String): Boolean = offered.containsKey(text)

    fun spanToReplace(text: String): Int = offered[text] ?: 0

    /**
     * Places for the text before the cursor ([isAddressField]: the field asked for a postal address). Remembers what
     * each one replaces, for [spanToReplace].
     */
    fun suggest(textBefore: CharSequence, isAddressField: Boolean, limit: Int = 3): List<String> {
        offered.clear()
        if (places.isEmpty()) return emptyList()
        val before = textBefore.takeLast(160).toString()
        val out = ArrayList<String>()
        fun offer(text: String, span: Int) {
            if (out.size < limit && text !in out) { out.add(text); offered[text] = span }
        }

        // "Ilica 5, " → "10000 Zagreb": the post code and town of that street.
        STREET_NUMBER_COMMA.find(before)?.let { m ->
            val towns = streetOf(m.groupValues[1])
            towns.forEach { t -> offer(townCode[fold(t)]?.let { "$it $t" } ?: t, 0) }
            if (out.isNotEmpty()) return out
        }
        // "Ilica 5, Zag" → "10000 Zagreb", and "Ilica 5, Zagreb" → the code put in front.
        STREET_NUMBER_COMMA_TOWN.find(before)?.takeIf { it.groupValues[1].first().isUpperCase() || isAddressField }?.let { m ->
            val typed = m.groupValues[1]
            val f = fold(typed)
            places.asSequence().filter { it.kind == TOWN && it.folded.startsWith(f) }
                .sortedByDescending { it.weight }.take(limit)
                .forEach { offer(if (it.extra.isNotEmpty()) "${it.extra} ${it.name}" else it.name, typed.length) }
            if (out.isNotEmpty()) return out
        }
        // A post code being typed: "100" → "10000 Zagreb".
        POSTCODE_TYPED.find(before)?.let { m ->
            val digits = m.groupValues[1]
            val addressy = isAddressField || before.trimEnd().dropLast(digits.length).trimEnd().endsWith(",")
            if (addressy || digits.length == 5) {
                codeTowns.asSequence().filter { it.first.startsWith(digits) }.distinctBy { it.first }
                    .take(limit).forEach { (code, town) -> offer("$code $town", digits.length) }
                if (out.isNotEmpty()) return out
            }
        }

        // A street or town being written: the last one to four words before the cursor.
        val run = Regex("""(\p{L}[\p{L}\d.'-]*(?: \p{L}[\p{L}\d.'-]*){0,3})$""").find(before)?.value ?: return out
        val words = run.split(' ')
        for (n in words.size downTo 1) {
            val typed = words.takeLast(n).joinToString(" ")
            if (typed.length < 2) continue
            val startIdx = before.length - typed.length
            val head = before.substring(0, startIdx)
            val cue = CUE.containsMatchIn(head)
            val capital = typed.first().isUpperCase() && !opensSentence(head)
            // A field holding only this (a search box, a map, a "to" line): a street is typed there with no sentence
            // around it. Four letters and one place only, so "Danas" opening a message is not crowded.
            val bare = head.isBlank() && typed.length >= 4 && typed.first().isUpperCase()
            if (!(isAddressField || cue || bare || (capital && typed.length >= 3))) continue
            val f = fold(typed)
            val bucket = byLetter[f.first()] ?: continue
            // First the places whose name starts so, then (one word typed, three letters) those with a later word
            // that does ("Vukov" → Ulica grada Vukovara), each pass heaviest first.
            for (pass in 0..1) {
                if (pass == 1 && (n != 1 || f.length < 3)) break
                for (p in bucket) {
                    if (out.size >= limit) break
                    if (p.kind == WORLD && !(capital || isAddressField || bare)) continue
                    if (p.folded == f) continue
                    val hit = if (pass == 0) p.folded.startsWith(f)
                        else p.kind != WORLD && !p.folded.startsWith(f) && p.folded.split(' ').drop(1).any { it.startsWith(f) }
                    if (hit) offer(p.name, typed.length)
                }
            }
            if (out.isNotEmpty()) return if (isAddressField || cue) out else if (bare && !capital) out.take(1) else out.take(2)
        }
        return out
    }

    /** The towns a typed street is in (folded comparison), most addresses first. */
    private fun streetOf(typed: String): List<String> {
        val t = fold(typed.trim())
        // The street may be preceded by other words ("živim na Ilica"): try the longest tail that is a street.
        val parts = t.split(' ')
        for (i in parts.indices) {
            val tail = parts.drop(i).joinToString(" ")
            streetTowns[tail]?.let { return it.take(3) }
        }
        return emptyList()
    }

    /** True when the word starting after [head] opens a sentence (so its capital says nothing). */
    private fun opensSentence(head: String): Boolean {
        val h = head.trimEnd()
        return h.isEmpty() || h.last() in ".!?\n:"
    }
}
