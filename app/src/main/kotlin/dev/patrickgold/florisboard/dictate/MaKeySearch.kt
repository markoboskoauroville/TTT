/*
 * TTT, by Mantra Productions.
 */

package dev.patrickgold.florisboard.dictate

import java.text.Normalizer

/**
 * Finding a key in the picker by typing what you want, rather than by reading forty-six of them.
 *
 * ### Three layers, cheapest first
 *
 *  1. **What he typed, matched literally** against the label, the description, the letters on the
 *     face and the id. Instant, offline, and right nearly always.
 *  2. **What he has meant before.** Every time he picks a key while a query is showing, the pair is
 *     remembered. So "sound off" reaches the volume key on the second try forever, whatever the
 *     label says. This is the part that makes the third layer wither: **the model is a teacher, not
 *     a dependency.**
 *  3. **Ask a model**, only when the first two find nothing, and mark the answer `(AI)` so a guess
 *     is never mistaken for a match.
 *
 * ### Why the memory is not a spell-checker
 *
 * He is dyslexic and dictates in two languages. A corrector would try to turn his word into the
 * app's word, which is the wrong direction: **his word is not a mistake, it is what that key is
 * called in his head.** So nothing is corrected. The pair is stored and his word becomes a real name
 * for that key, as good as the printed one and better for him.
 *
 * Everything here is pure — strings in, strings out, no Android and no network — so the whole rule
 * can be walked in Python before a build.
 */
object MaKeySearch {

    /** One thing that can be found: a key, a bucket, a macro slot. */
    data class Entry(
        /** Stable id, used by the memory. `volume_keys`, `clip:3`, `macro:1`. */
        val id: String,
        val label: String,
        val description: String,
        /** What is written on the face, when it is letters: `AP`, `C3`, `A`. */
        val letters: String = "",
    ) {
        /** Everything searchable about this entry, folded once. */
        val haystack: String = fold("$label $description $letters $id")
    }

    private const val PAIR = '\u001F'
    private const val ROW = '\u001E'

    /**
     * Lowercase, unaccented, punctuation to spaces, runs of space collapsed.
     *
     * The accents matter more than they look. He types Croatian, and "č" typed on one keyboard and
     * "c" typed on another have to be the same letter here or the memory learns two names for one
     * key and neither of them fires reliably.
     */
    fun fold(text: String): String {
        val flat = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
        return flat.map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * Literal matches: every word he typed appears somewhere in the entry.
     *
     * Every word rather than any word. Typing two words is how somebody narrows a list, and a search
     * that widened when he added a word would be answering the opposite of what the typing meant.
     *
     * Ordering puts a hit in the LABEL above a hit anywhere else, so "copy" finds the Copy key
     * before the six keys whose descriptions mention copying.
     */
    fun literal(query: String, entries: List<Entry>): List<Entry> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return entries
        return entries
            .filter { entry -> words.all { it in entry.haystack } }
            .sortedBy { entry ->
                val label = fold(entry.label)
                when {
                    words.all { label.startsWith(it) } -> 0
                    words.all { it in label } -> 1
                    else -> 2
                }
            }
    }

    // ------------------------------------------------------------------ the memory

    /** One learned pair: this is what he calls that key, and how often. */
    data class Learned(val query: String, val id: String, val count: Int)

    fun parse(stored: String): List<Learned> =
        stored.split(ROW).mapNotNull { row ->
            val parts = row.split(PAIR)
            if (parts.size < 3) return@mapNotNull null
            val count = parts[2].toIntOrNull() ?: return@mapNotNull null
            if (parts[0].isBlank() || parts[1].isBlank()) null else Learned(parts[0], parts[1], count)
        }

    fun serialize(learned: List<Learned>): String =
        learned.joinToString(ROW.toString()) { "${it.query}$PAIR${it.id}$PAIR${it.count}" }

    /**
     * Records that he picked [id] while [query] was showing.
     *
     * Folded before storing, so the memory is keyed on the same thing the search folds to.
     *
     * Capped, and the cap drops the LEAST used rather than the oldest. A name he typed once a year
     * ago and never again is worth less than one he uses weekly, and dropping by age would keep
     * throwing away the habit and keeping the accident.
     */
    fun learn(stored: String, query: String, id: String, cap: Int = 200): String {
        val q = fold(query)
        if (q.isBlank() || id.isBlank()) return stored
        val existing = parse(stored)
        val hit = existing.firstOrNull { it.query == q && it.id == id }
        val updated = if (hit == null) {
            existing + Learned(q, id, 1)
        } else {
            existing.map { if (it === hit) it.copy(count = it.count + 1) else it }
        }
        return serialize(updated.sortedByDescending { it.count }.take(cap))
    }

    /**
     * What he has meant by this before: exact folded match first, then a query he typed that starts
     * with this one, so a name is found while it is still being typed.
     */
    fun remembered(query: String, stored: String, entries: List<Entry>): List<Entry> {
        val q = fold(query)
        if (q.isBlank()) return emptyList()
        val byId = entries.associateBy { it.id }
        return parse(stored)
            .filter { it.query == q || (q.length >= 2 && it.query.startsWith(q)) }
            .sortedWith(compareByDescending<Learned> { it.query == q }.thenByDescending { it.count })
            .mapNotNull { byId[it.id] }
            .distinct()
    }

    /**
     * The whole rule, in order. [Result.aiWanted] is true only when both offline layers came up
     * empty — it is the one condition under which a model is worth asking or paying for.
     */
    data class Result(val entries: List<Entry>, val fromMemory: Boolean, val aiWanted: Boolean)

    fun resolve(query: String, entries: List<Entry>, stored: String): Result {
        if (fold(query).isBlank()) return Result(entries, fromMemory = false, aiWanted = false)
        val hits = literal(query, entries)
        if (hits.isNotEmpty()) return Result(hits, fromMemory = false, aiWanted = false)
        val learned = remembered(query, stored, entries)
        if (learned.isNotEmpty()) return Result(learned, fromMemory = true, aiWanted = false)
        return Result(emptyList(), fromMemory = false, aiWanted = true)
    }

    // ------------------------------------------------------------------ the model, when asked

    /**
     * The question put to the model, and the whole list with it.
     *
     * It is asked for **one id from a list it has been given**, not for a description of a key, so
     * the answer either names something real or is discarded by [readAnswer]. A model that cannot
     * invent an id cannot mislead him about what this keyboard has.
     */
    fun prompt(query: String, entries: List<Entry>): String = buildString {
        append("A keyboard has these keys. Someone searched for a key and nothing matched.\n")
        append("Answer with ONE id from the list, nothing else. If none fit, answer NONE.\n\n")
        append("Search: ").append(query).append("\n\nKeys:\n")
        entries.forEach { append(it.id).append(" = ").append(it.label).append(" — ").append(it.description).append('\n') }
    }

    /** The id the model named, if it named one that exists. Anything else is nothing. */
    fun readAnswer(reply: String, entries: List<Entry>): Entry? {
        val cleaned = reply.trim().trim('"', '\'', '.', '`').lowercase()
        return entries.firstOrNull { it.id.lowercase() == cleaned }
            ?: entries.firstOrNull { cleaned.contains(it.id.lowercase()) }
    }
}
