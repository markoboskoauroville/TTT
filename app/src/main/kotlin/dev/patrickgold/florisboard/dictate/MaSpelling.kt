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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.jvm.Volatile

/**
 * SPELLING: THE NAMES HIS SPEECH-TO-TEXT GETS WRONG, FIXED BEFORE THEY ARE TYPED.
 *
 * Marko, 9.11.2026: "Any misspelled words, you understand from the content what the correct spelling
 * is. Just collect those mistakes somewhere and correct them automatically in the future, because my
 * AssemblyAI is doing this." Three layers, one table:
 *
 * 1. **Keyterms on every AssemblyAI request** (`keyterms_prompt`, all three endpoints the app uses:
 *    pre-recorded, sync, dictation; docs read 9.11.2026): the correct names bias the engine itself.
 * 2. **Custom spelling** (`custom_spelling`, the pre-recorded endpoint only): the unambiguous pairs whose
 *    right form is one word. The engine's own rule: `to` is one word, `from` at most five.
 * 3. **The local pass** ([apply]) on the finished transcript, whatever engine produced it: whole words,
 *    case-aware, with context rules (`unless`: Grok stays when "Elon Musk" or "xAI" is in the same
 *    message; `onlyIf`: mode becomes mod only beside Claude, plugin and the like).
 *
 * The table is ONE FILE the phone reads, `Documents/TTTmini/dictation_corrections.json`, generated on the
 * Mac from MANTRA_MANIFEST modules/dictation-corrections.md by `re-skill-sync corrections push`. Beside
 * it `dictation_learned.json` is written by the phone: a word he changes the same way twice when he
 * corrects an RE message ([noteEdit]) becomes a learned pair, applied at once and brought back to the Mac
 * by `re-skill-sync pull` for the manifest to absorb. Settings, Spelling lists every pair with a switch.
 */
object MaSpelling {

    const val FILE = "dictation_corrections.json"
    const val LEARNED_FILE = "dictation_learned.json"

    /** A pair: any of [from] (whole words, any case) becomes [to]. */
    data class Pair(
        val id: String,
        val from: List<String>,
        val to: String,
        /** The pair is skipped when ANY of these appears in the same message. */
        val unless: List<String> = emptyList(),
        /** When not empty, the pair applies only when ANY of these appears in the same message. */
        val onlyIf: List<String> = emptyList(),
        /** May be sent to the engine as custom spelling (unambiguous, no context rule). */
        val engine: Boolean = true,
        val learned: Boolean = false,
    )

    data class Table(val keyterms: List<String>, val pairs: List<Pair>)

    /** Used until the Mac has pushed the real file, so the main names work from the first install. */
    const val DEFAULT_JSON = """{"version":1,"keyterms":["Groq","Svemir","Santoshi","Suno","Tajana","Kinisi","Mantreshwar","Natasha","Damjan","Emina","Fiverr","Nano Banana","Claude Code","MixPad","CQ"],"pairs":[
{"id":"grok","from":["Grok"],"to":"Groq","unless":["Elon Musk","xAI"],"engine":false},
{"id":"svemir","from":["Sfemeri","Samir","Sveimir"],"to":"Svemir"},
{"id":"santoshi","from":["Satoshi","Sunthoshi"],"to":"Santoshi"},
{"id":"suno","from":["Sunod","Suna"],"to":"Suno"},
{"id":"marko","from":["Marco Bosco"],"to":"Marko Bosko","engine":false},
{"id":"tajana","from":["Diana"],"to":"Tajana","engine":false,"onlyIf":["Santoshi","Svemir","singer","band","song"]},
{"id":"yshai","from":["Ashay Afterman","Ashai Afterman","Ishai Afterman"],"to":"Yshai Afterman","engine":false},
{"id":"kinisi","from":["Kinezi"],"to":"kinisi"},
{"id":"mod","from":["mode"],"to":"mod","engine":false,"onlyIf":["Claude","plugin","plug-in"]},
{"id":"footbell","from":["Footbell","foot bell"],"to":"foot-bell","engine":false}]}"""

    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private fun privateDir(): File? = appContext?.filesDir?.let { File(it, "re") }
    fun file(): File = File(MaVault.dir(), FILE)
    fun learnedFile(): File = File(MaVault.dir(), LEARNED_FILE)

    @Volatile
    private var cache: Table? = null
    @Volatile
    private var cacheKey: String = ""

    private fun readText(f: File, name: String): String? =
        runCatching { f.takeIf { it.length() > 0L }?.readText() }.getOrNull()
            ?: runCatching { privateDir()?.let { File(it, name) }?.takeIf { it.length() > 0L }?.readText() }.getOrNull()

    /** The table, re-read when either file changed on disk. */
    @Synchronized
    fun table(): Table {
        val a = runCatching { file().lastModified() + file().length() }.getOrDefault(0L)
        val b = runCatching { learnedFile().lastModified() + learnedFile().length() }.getOrDefault(0L)
        val key = "$a/$b"
        cache?.let { if (key == cacheKey) return it }
        val main = parse(readText(file(), FILE) ?: DEFAULT_JSON, learned = false) ?: parse(DEFAULT_JSON, false)!!
        val learned = readText(learnedFile(), LEARNED_FILE)?.let { parseLearned(it) }.orEmpty()
        val known = main.pairs.flatMap { p -> p.from.map { it.lowercase(Locale.ROOT) } }.toSet()
        val t = Table(main.keyterms, main.pairs + learned.filter { p -> p.from.none { it.lowercase(Locale.ROOT) in known } })
        cache = t
        cacheKey = key
        return t
    }

    // ---------------------------------------------------------------- parsing (pure)

    fun parse(json: String, learned: Boolean): Table? = runCatching {
        val o = JSONObject(json)
        val terms = o.optJSONArray("keyterms")?.strings().orEmpty()
        val pairs = o.optJSONArray("pairs")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                val from = p.optJSONArray("from")?.strings().orEmpty().filter { it.isNotBlank() }
                val to = p.optString("to").trim()
                if (from.isEmpty() || to.isEmpty()) return@mapNotNull null
                Pair(
                    id = p.optString("id").ifBlank { from.first().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() } },
                    from = from, to = to,
                    unless = p.optJSONArray("unless")?.strings().orEmpty(),
                    onlyIf = p.optJSONArray("onlyIf")?.strings().orEmpty(),
                    engine = p.optBoolean("engine", true),
                    learned = learned,
                )
            }
        }.orEmpty()
        Table(terms, pairs)
    }.getOrNull()

    private fun parseLearned(json: String): List<Pair> = runCatching {
        val arr = JSONObject(json).optJSONArray("learned") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val from = p.optString("from").trim()
            val to = p.optString("to").trim()
            if (from.isEmpty() || to.isEmpty()) null
            else Pair("learned-" + from.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }, listOf(from), to, engine = false, learned = true)
        }
    }.getOrDefault(emptyList())

    private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

    // ---------------------------------------------------------------- switches

    fun on(): Boolean = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maSpelling.get()
    }.getOrDefault(true)

    fun keytermsOn(): Boolean = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maSpellingTerms.get()
    }.getOrDefault(true)

    fun disabledIds(): Set<String> = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maSpellingOff.get().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    // ---------------------------------------------------------------- the three layers

    /** The names for `keyterms_prompt`: the table's list, then the right form of every enabled pair. */
    fun keyterms(): List<String> {
        if (!on() || !keytermsOn()) return emptyList()
        val t = table()
        val off = disabledIds()
        return keytermsOf(t, off)
    }

    fun keytermsOf(t: Table, off: Set<String>): List<String> =
        (t.keyterms + t.pairs.filter { it.id !in off }.map { it.to }).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(100)

    /** One line for the model: the right names and the misheard forms they replace. */
    fun promptHint(): String {
        val t = table()
        val off = disabledIds()
        val pairs = t.pairs.filter { it.id !in off && it.onlyIf.isEmpty() && it.unless.isEmpty() }
            .joinToString("; ") { it.from.joinToString("/") + " means " + it.to }
        return t.keyterms.joinToString(", ") + (if (pairs.isNotEmpty()) ". Misheard forms: $pairs" else "") +
            ". Grok means Groq unless Elon Musk or xAI is mentioned."
    }

    /** The pairs safe to hand to the engine as custom spelling: one-word result, no context rule. */
    fun engineSpelling(): List<kotlin.Pair<List<String>, String>> {
        if (!on()) return emptyList()
        return engineSpellingOf(table(), disabledIds())
    }

    fun engineSpellingOf(t: Table, off: Set<String>): List<kotlin.Pair<List<String>, String>> =
        t.pairs.filter {
            it.id !in off && it.engine && !it.learned && it.unless.isEmpty() && it.onlyIf.isEmpty() &&
                it.to.none { c -> c.isWhitespace() } && it.from.all { f -> f.trim().split(Regex("\\s+")).size <= 5 }
        }.map { it.from to it.to }

    /** The local pass over a finished transcript. */
    fun apply(text: String): String {
        if (text.isBlank() || !on()) return text
        return applyWith(text, table().pairs, disabledIds())
    }

    fun applyWith(text: String, pairs: List<Pair>, off: Set<String>): String {
        var out = text
        for (p in pairs) {
            if (p.id in off) continue
            if (p.unless.any { out.contains(it, ignoreCase = true) }) continue
            if (p.onlyIf.isNotEmpty() && p.onlyIf.none { out.contains(it, ignoreCase = true) }) continue
            for (f in p.from.sortedByDescending { it.length }) {
                val words = f.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
                val re = Regex("(?<![\\p{L}\\p{N}])$words(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                out = re.replace(out) { m -> caseLike(m.value, p.to) }
            }
        }
        return out
    }

    /** [to] in the case of [matched]: an all-caps match stays all caps, a capital start keeps a capital. */
    fun caseLike(matched: String, to: String): String {
        val letters = matched.filter { it.isLetter() }
        if (letters.length >= 2 && letters.all { it.isUpperCase() } && to.length >= 2 && to.none { it.isUpperCase() }) return to.uppercase(Locale.ROOT)
        if (matched.first().isUpperCase() && to.first().isLowerCase()) return to.replaceFirstChar { it.uppercase() }
        return to
    }

    // ---------------------------------------------------------------- learning from his edits

    private val WORD = Regex("\\p{L}[\\p{L}\\p{N}'’-]*")

    /** Word spans replaced by something similar: (from, to) of a one or two word substitution that looks like a misspelling. */
    fun editPairs(draft: String, final: String): List<kotlin.Pair<String, String>> {
        val a = WORD.findAll(draft).map { it.value }.toList()
        val b = WORD.findAll(final).map { it.value }.toList()
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0 || n > 400 || m > 400) return emptyList()
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i].equals(b[j], true)) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = ArrayList<kotlin.Pair<String, String>>()
        var i = 0
        var j = 0
        var delA = ArrayList<String>()
        var insB = ArrayList<String>()
        fun flush() {
            if (delA.isNotEmpty() && insB.isNotEmpty() && delA.size <= 2 && insB.size <= 2) {
                val f = delA.joinToString(" ")
                val t = insB.joinToString(" ")
                if (f.length >= 3 && t.length >= 3 && !f.equals(t, true) && similar(f, t)) out.add(f to t)
            }
            delA = ArrayList()
            insB = ArrayList()
        }
        while (i < n && j < m) {
            if (a[i].equals(b[j], true)) { flush(); i++; j++ }
            else if (lcs[i + 1][j] >= lcs[i][j + 1]) { delA.add(a[i]); i++ }
            else { insB.add(b[j]); j++ }
        }
        while (i < n) { delA.add(a[i]); i++ }
        while (j < m) { insB.add(b[j]); j++ }
        flush()
        return out
    }

    /** A misspelling, not a rewording: edit distance at most half the longer one. */
    fun similar(x: String, y: String): Boolean {
        val s = x.lowercase(Locale.ROOT)
        val t = y.lowercase(Locale.ROOT)
        val d = Array(s.length + 1) { IntArray(t.length + 1) }
        for (i in 0..s.length) d[i][0] = i
        for (j in 0..t.length) d[0][j] = j
        for (i in 1..s.length) for (j in 1..t.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (s[i - 1] == t[j - 1]) 0 else 1)
        }
        return d[s.length][t.length] * 2 <= maxOf(s.length, t.length)
    }

    /** Learned twice becomes a learned pair. */
    const val LEARN_AFTER = 2

    /** Called by the RE learning loop with what RE wrote and what he sent. */
    @Synchronized
    fun noteEdit(draft: String, final: String) {
        val pairs = runCatching { editPairs(draft, final) }.getOrDefault(emptyList())
        if (pairs.isEmpty()) return
        val f = learnedFile()
        val o = runCatching { JSONObject(readText(f, LEARNED_FILE) ?: "{}") }.getOrDefault(JSONObject())
        val cand = o.optJSONObject("candidates") ?: JSONObject()
        val learned = o.optJSONArray("learned") ?: JSONArray()
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        for ((from, to) in pairs) {
            val k = from.lowercase(Locale.ROOT) + "|" + to
            val c = cand.optInt(k, 0) + 1
            cand.put(k, c)
            val already = (0 until learned.length()).any { learned.optJSONObject(it)?.optString("from").equals(from, true) }
            if (c >= LEARN_AFTER && !already) {
                learned.put(JSONObject().put("from", from).put("to", to).put("count", c).put("at", stamp))
                MaLog.add("spelling", "learned a pair: $from -> $to")
            }
        }
        o.put("candidates", cand).put("learned", learned)
        val text = o.toString(1)
        runCatching { f.parentFile?.mkdirs(); f.writeText(text) }
        runCatching { privateDir()?.let { d -> d.mkdirs(); File(d, LEARNED_FILE).writeText(text) } }
        MaRemoteDb.syncSoon()
    }
}
