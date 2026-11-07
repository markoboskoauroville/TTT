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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Every email address typed on this phone, kept whole and offered whole.
 *
 * Marko, 7.11.2026: *"TTT must nicely save all email addresses and names ... Swift is doing all these
 * things better than TTT."* The personal n-gram model splits text at `@` and `.`, so an address became
 * four unrelated words and could never be offered again. SwiftKey keeps the address as one token and
 * offers it from its first letters on, in any field. This does the same.
 *
 * - **Learned** from the text around the cursor: an address counts once it is complete, i.e. the cursor
 *   has moved past it, or the field is closed with it inside.
 * - **Offered** while the address is being typed: the trailing run of address characters before the
 *   cursor (`mar`, `marko.b`, `marko.bosko@g`) is matched against the saved ones, most used first. In an
 *   email field, an empty field already offers the most used addresses.
 *
 * One small file, `ma_addresses.tsv` (address, count). Never learned in incognito mode.
 */
object MaAddressBook {

    /** Characters an address is made of (besides letters and digits). */
    const val ADDRESS_CHARS = "._%+-@"

    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    private const val MAX_ADDRESSES = 500
    private const val SAVE_DELAY_MS = 3_000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val counts = LinkedHashMap<String, Int>()

    /** Addresses already counted in the field that is open now, so one address is counted once per field. */
    private val countedThisField = HashSet<String>()

    @Volatile
    private var file: File? = null
    private var saveJob: Job? = null

    fun initialize(context: Context) {
        val f = File(context.filesDir, "ma_addresses.tsv")
        file = f
        scope.launch {
            runCatching { if (f.exists()) f.readLines() else emptyList() }.getOrDefault(emptyList()).forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val n = line.substring(tab + 1).toIntOrNull() ?: 1
                    synchronized(lock) { counts[line.substring(0, tab)] = n }
                }
            }
        }
    }

    /** The run of address characters right before the cursor: what is being typed if it is an address. */
    fun trailingToken(textBefore: CharSequence): String {
        var i = textBefore.length
        while (i > 0 && (textBefore[i - 1].isLetterOrDigit() || textBefore[i - 1] in ADDRESS_CHARS)) i--
        return textBefore.substring(i).trimStart('.', '-', '@')
    }

    /**
     * Learns the complete addresses in [textBefore] / [textAfter]. While the field is open only addresses
     * the cursor has left are counted ([fieldClosing] false); when it closes, every one is.
     */
    fun observe(textBefore: CharSequence, textAfter: CharSequence, isIncognito: Boolean, fieldClosing: Boolean) {
        if (isIncognito) return
        val before = textBefore.takeLast(400).toString()
        val stillTyping = if (fieldClosing) "" else trailingToken(before)
        var changed = false
        for (m in EMAIL.findAll(before + (if (fieldClosing) textAfter.take(400).toString() else ""))) {
            val address = m.value.trimEnd('.', '-')
            // The address the cursor is still inside is not finished yet ("marko@gmail.co" on the way to .com).
            if (!fieldClosing && m.range.last == before.length - 1 && stillTyping.isNotEmpty()) continue
            synchronized(lock) {
                if (countedThisField.add(address.lowercase())) {
                    val key = counts.keys.firstOrNull { it.equals(address, ignoreCase = true) } ?: address
                    counts[key] = (counts[key] ?: 0) + 1
                    changed = true
                }
            }
        }
        if (fieldClosing) synchronized(lock) { countedThisField.clear() }
        if (changed) scheduleSave()
    }

    /**
     * Saved addresses that complete [token], most used first. An empty [token] gives the most used ones
     * only when [isEmailField]. The address already typed in full is not offered again.
     */
    fun suggest(token: String, isEmailField: Boolean, limit: Int = 3): List<String> {
        if (token.isEmpty() && !isEmailField) return emptyList()
        if (token.isNotEmpty() && token.length < 2 && !isEmailField) return emptyList()
        val all = synchronized(lock) { counts.entries.map { it.key to it.value } }
        return all.asSequence()
            .filter { (a, _) ->
                a.startsWith(token, ignoreCase = true) && !a.equals(token, ignoreCase = true) ||
                    // also from the domain or the second part of the name: "bosko" finds marko.bosko@...
                    (token.length >= 3 && a.substringBefore('@').split('.', '_', '-').any { p -> p.startsWith(token, ignoreCase = true) } &&
                        !token.contains('@') && !token.contains('.'))
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            val f = file ?: return@launch
            val text = synchronized(lock) {
                counts.entries.sortedByDescending { it.value }.take(MAX_ADDRESSES)
                    .joinToString("") { "${it.key}\t${it.value}\n" }
            }
            runCatching {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(f)
            }
        }
    }
}
