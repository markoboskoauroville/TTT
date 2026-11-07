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
 * Every email address and phone number typed on this phone, kept whole and offered whole.
 *
 * Marko, 7.11.2026: *"TTT must nicely save all email addresses and names"* and *"our app needs to
 * recognize 3 categories of text: normal text, email addresses, and phone numbers. And when I'm typing
 * number, if I'm starting with certain digits, it needs to recognize it's the phone number and just pull
 * it out of the database. Email is the same."* The personal n-gram model splits text at `@`, `.` and
 * digits, so an address became four words and a number nothing at all. SwiftKey keeps both whole.
 *
 * **Three kinds of text.** What is being typed right before the cursor is read as one of them:
 * - an **email** in progress: letters/digits with `.`, `_`, `-`, `+`, `@` (`marko.b`, `marko.bosko@g`),
 *   matched against saved addresses from their first letters, or from the name part (`bosko`);
 * - a **phone number** in progress: digits with `+`, spaces, `-`, `/`, brackets, at least [PHONE_MIN_TYPED]
 *   digits, matched by digits, so `091 23` finds `+385 91 234 5678` as well;
 * - **normal text**, which this leaves to the word engines.
 *
 * **Learned** once complete: the cursor has moved past it, or the field is left (closed, or another field
 * of the same app focused). **Offered** first in the strip; an empty email field offers the most used
 * addresses, an empty phone field the most used numbers, and "my email is" / "moj broj je" offer one's own.
 *
 * Also web addresses (www…, https://…, name.tld/…) and IBANs (checked with their ISO checksum), offered
 * from their first characters, in a URL field, or after "IBAN" / "moj račun je" (7.11.2026: "find more
 * such useful shortcuts to present the right information at the right time").
 *
 * One small file, `ma_addresses.tsv` (value, count, kind). Never learned in incognito mode.
 */
object MaAddressBook {

    /** Characters an address is made of (besides letters and digits). */
    const val ADDRESS_CHARS = "._%+-@"

    /** Characters a phone number is written with (besides digits). */
    const val PHONE_CHARS = "+-/() "

    private const val EMAIL_KIND = "e"
    private const val PHONE_KIND = "p"
    private const val URL_KIND = "u"
    private const val IBAN_KIND = "i"

    /** Characters a web address is made of (besides letters and digits). */
    private const val URL_CHARS = "._-/:%?=&#~+"

    private const val PHONE_MIN_DIGITS = 6
    private const val PHONE_MAX_DIGITS = 15
    private const val PHONE_MIN_TYPED = 2

    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
    private val PHONE = Regex("""(?<![\w@.])\+?\(?\d[\d \-/()]{4,22}\d(?![\w@])""")
    private val DATE_LIKE = Regex("""^\d{1,4}([./]\d{1,4}){2}$""")
    private val URL = Regex("""(?i)(?<![@\w.])((https?://)?(www\.)?[a-z0-9-]+(\.[a-z0-9-]+)*\.(com|net|org|hr|eu|io|ai|app|dev|co|uk|de|at|si|ba|rs|it|fr|info|me|tv|in|ly|gg|xyz|online|site|shop|store)(/[^\s]*)?)(?![\w@])""")
    private val IBAN = Regex("""(?<![A-Za-z0-9])[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,4})?(?![A-Za-z0-9])""")
    private val ASK_IBAN = Regex("""(?i)\b(iban|moj (žiro |ziro |tekući |tekuci )?račun je|moj (ziro |tekuci )?racun je|account number is|uplati na|uplatite na)\s*:?\s*$""")

    /** "my email is", "moj mail je": the next word is one's own address or number. */
    private val ASK_EMAIL = Regex("""(?i)\b(my e-?mail( address)? is|e-?mail me at|moj (e-?)?mail je|mail mi je|moja (e-?)?mail adresa je)\s*$""")
    private val ASK_PHONE = Regex("""(?i)\b(my (phone |mobile |cell )?number is|call me (on|at)|moj (broj( mobitela| telefona)?|mobitel) je|broj mi je|nazovi me na)\s*$""")

    private const val MAX_ENTRIES = 800
    private const val SAVE_DELAY_MS = 3_000L

    private class Entry(val value: String, var count: Int, val kind: String)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()   // key: email lowercase / phone digits

    /** Values already counted in the field that is open now, so one value is counted once per field. */
    private val countedThisField = HashSet<String>()

    @Volatile
    private var file: File? = null
    private var saveJob: Job? = null

    /** A complete value sitting at the cursor is saved once it has stayed unchanged this long. */
    private const val SETTLE_MS = 2_500L
    private var settleJob: Job? = null

    private fun log(msg: String) { runCatching { android.util.Log.i("MaAddressBook", msg) } }

    fun initialize(context: Context) {
        val f = File(context.filesDir, "ma_addresses.tsv")
        file = f
        val app = context.applicationContext
        scope.launch {
            delay(CONTACTS_DELAY_MS)
            importContacts(app)
        }
        scope.launch {
            runCatching { if (f.exists()) f.readLines() else emptyList() }.getOrDefault(emptyList()).forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 2) {
                    val kind = parts.getOrNull(2) ?: EMAIL_KIND
                    val value = parts[0]
                    val n = parts[1].toIntOrNull() ?: 1
                    synchronized(lock) { entries[keyOf(value, kind)] = Entry(value, n, kind) }
                }
            }
        }
    }

    private fun digitsOf(s: CharSequence): String = buildString {
        s.forEachIndexed { i, c -> if (c.isDigit() || (c == '+' && i == 0)) append(c) }
    }

    private fun keyOf(value: String, kind: String): String = when (kind) {
        PHONE_KIND -> digitsOf(value.trim())
        IBAN_KIND -> "iban:" + value.filter { it.isLetterOrDigit() }.uppercase()
        URL_KIND -> "url:" + bareUrl(value)
        else -> value.lowercase()
    }

    /** "https://www.Example.com/" → "example.com": how a web address is compared. */
    private fun bareUrl(s: String): String =
        s.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    /** ISO 13616 check: an IBAN typed with a wrong digit is not saved. */
    private fun validIban(compact: String): Boolean {
        if (compact.length !in 15..34) return false
        val moved = compact.substring(4) + compact.substring(0, 4)
        var rest = 0
        for (c in moved) {
            val v = if (c.isDigit()) c - '0' else if (c in 'A'..'Z') c - 'A' + 10 else return false
            rest = if (v >= 10) (rest * 100 + v) % 97 else (rest * 10 + v) % 97
        }
        return rest == 1
    }

    /** The web address being typed right before the cursor, or "" when it is not one. */
    fun trailingUrl(textBefore: CharSequence): String {
        var i = textBefore.length
        while (i > 0 && (textBefore[i - 1].isLetterOrDigit() || textBefore[i - 1] in URL_CHARS)) i--
        val run = textBefore.substring(i)
        if (run.contains('@')) return ""
        val l = run.lowercase()
        return if (l.startsWith("www") || l.startsWith("http") || (run.contains('.') && run.length >= 4)) run else ""
    }

    /** The IBAN being typed right before the cursor ("HR12 2340 00"), or "". */
    fun trailingIban(textBefore: CharSequence): String {
        var i = textBefore.length
        while (i > 0 && (textBefore[i - 1].isLetterOrDigit() || textBefore[i - 1] == ' ')) i--
        // From the first word that looks like the start of an IBAN: two capitals, two digits.
        val run = textBefore.substring(i)
        val start = Regex("""[A-Z]{2}\d{2}""").find(run)?.range?.first ?: return ""
        val tail = run.substring(start)
        return if (tail.filter { it.isLetterOrDigit() }.length >= 4 && (start == 0 || run[start - 1] == ' ')) tail else ""
    }

    /** The run of address characters right before the cursor: what is being typed if it is an address. */
    fun trailingToken(textBefore: CharSequence): String {
        var i = textBefore.length
        while (i > 0 && (textBefore[i - 1].isLetterOrDigit() || textBefore[i - 1] in ADDRESS_CHARS)) i--
        return textBefore.substring(i).trimStart('.', '-', '@')
    }

    /** The phone number being typed right before the cursor ("+385 91 23"), or "" when it is not one. */
    fun trailingPhone(textBefore: CharSequence): String {
        var i = textBefore.length
        while (i > 0 && (textBefore[i - 1].isDigit() || textBefore[i - 1] in PHONE_CHARS)) i--
        // Directly after a letter it is part of a word ("abc12"), not a number.
        if (i > 0 && textBefore[i - 1].isLetter()) return ""
        val run = textBefore.substring(i).trimStart(' ', '-', '/', ')')
        if (run.isEmpty()) return ""
        val digits = run.count { it.isDigit() }
        val starts = run.first().let { it.isDigit() || it == '+' || it == '(' }
        return if (digits >= PHONE_MIN_TYPED && starts) run else ""
    }

    /** How many characters before the cursor a picked [candidate] replaces (0: not an address or number). */
    fun spanToReplace(textBefore: CharSequence, candidate: String): Int = when (kindOf(candidate)) {
        EMAIL_KIND -> trailingToken(textBefore).length
        PHONE_KIND -> trailingPhone(textBefore).length
        URL_KIND -> trailingUrl(textBefore).length
        IBAN_KIND -> trailingIban(textBefore).length
        else -> 0
    }

    /** The kind of a saved value ([candidate] as offered in the strip), or null when it is not one. */
    private fun kindOf(candidate: String): String? = synchronized(lock) {
        listOf(EMAIL_KIND, PHONE_KIND, URL_KIND, IBAN_KIND).firstOrNull { k -> entries[keyOf(candidate, k)]?.kind == k }
    }

    /** Whether [text] is a saved email, number, web address or IBAN (so the strip commits it whole). */
    fun isSaved(text: String): Boolean = kindOf(text) != null

    /** Whether [text] is one of the saved phone numbers (so the strip knows how to commit it). */
    fun isPhone(text: String): Boolean {
        if (text.none { it.isDigit() }) return false
        return synchronized(lock) { entries[keyOf(text, PHONE_KIND)]?.kind == PHONE_KIND }
    }

    /**
     * Learns the complete addresses and numbers in [textBefore] / [textAfter]. While the field is open only
     * values the cursor has left are counted ([fieldClosing] false); when it is left, every one is.
     */
    fun observe(textBefore: CharSequence, textAfter: CharSequence, isIncognito: Boolean, fieldClosing: Boolean) {
        if (isIncognito) return
        val before = textBefore.takeLast(400).toString()
        val text = before + (if (fieldClosing) textAfter.take(400).toString() else "")
        var changed = false
        val typingEmail = !fieldClosing && trailingToken(before).isNotEmpty()
        val typingPhone = !fieldClosing && trailingPhone(before).isNotEmpty() && !before.endsWith(" ")
        fun count(value: String, kind: String, endsAt: Int, stillTyping: Boolean) {
            if (stillTyping && endsAt >= before.length - 1) return   // the cursor is still in it
            val key = keyOf(value, kind)
            synchronized(lock) {
                // Never a value that only starts one already saved ("+3859155" of "+385915550101"), and a new
                // value clears the once-seen starts of it saved while it was being typed.
                if (entries.values.any { it.kind == kind && it.count >= 1 && keyOf(it.value, kind).let { k -> k.length > key.length && k.startsWith(key) } }) return
                if (countedThisField.add(key)) {
                    log("saved $kind ${value.length} chars (closing=$fieldClosing)")
                    entries.entries.removeAll { (k, e) -> e.kind == kind && e.count <= 1 && k != key && key.startsWith(k) }
                    val e = entries[key]
                    if (e != null) e.count++ else entries[key] = Entry(value, 1, kind)
                    changed = true
                }
            }
        }
        // A complete address or number at the cursor that then stays as it is: saved after SETTLE_MS (a web
        // page's fields often never close the input, and he moves on without typing past it).
        if (!fieldClosing) {
            val atCursor = EMAIL.findAll(before).lastOrNull()?.takeIf { it.range.last == before.length - 1 }?.let { it.value to EMAIL_KIND }
                ?: PHONE.findAll(before).lastOrNull()?.takeIf {
                    it.range.last == before.length - 1 && it.value.count { c -> c.isDigit() } >= PHONE_MIN_DIGITS + 1
                }?.let { it.value.trim() to PHONE_KIND }
            settleJob?.cancel()
            if (atCursor != null) {
                val snapshot = before
                settleJob = scope.launch {
                    delay(SETTLE_MS)
                    log("settled ${atCursor.second} ${atCursor.first.length} chars")
                    observe(snapshot + " ", "", isIncognito = false, fieldClosing = false)
                }
            }
        }
        for (m in EMAIL.findAll(text)) count(m.value.trimEnd('.', '-'), EMAIL_KIND, m.range.last, typingEmail)
        val typingUrl = !fieldClosing && trailingUrl(before).isNotEmpty()
        for (m in URL.findAll(text)) count(m.value.trimEnd('.', ',', ')'), URL_KIND, m.range.last, typingUrl)
        val typingIban = !fieldClosing && trailingIban(before).isNotEmpty() && !before.endsWith(" ")
        for (m in IBAN.findAll(text)) {
            val compact = m.value.filter { it.isLetterOrDigit() }
            if (validIban(compact)) count(m.value.trim(), IBAN_KIND, m.range.last, typingIban)
        }
        for (m in PHONE.findAll(text)) {
            val value = m.value.trim()
            val digits = value.count { it.isDigit() }
            if (digits < PHONE_MIN_DIGITS || digits > PHONE_MAX_DIGITS) continue
            // Not a date: those have separators a phone number does not ("7.11.2026", "07/11/2026").
            if (DATE_LIKE.matches(value)) continue
            count(value, PHONE_KIND, m.range.last, typingPhone)
        }
        if (fieldClosing) {
            log("field left")
            synchronized(lock) { countedThisField.clear() }
        }
        if (changed) scheduleSave()
    }

    /**
     * What to offer for the text before the cursor: saved addresses or numbers that complete what is being
     * typed, most used first. [isEmailField] / [isPhoneField] make an empty field offer the most used ones.
     */
    fun suggest(
        textBefore: CharSequence,
        isEmailField: Boolean,
        isPhoneField: Boolean,
        isUrlField: Boolean = false,
        limit: Int = 3,
    ): List<String> {
        val all = synchronized(lock) { entries.values.map { Triple(it.value, it.count, it.kind) } }
        if (all.isEmpty()) return emptyList()
        fun top(kind: String) = all.filter { it.third == kind }.sortedByDescending { it.second }.map { it.first }.take(limit)
        val before = textBefore.toString()
        // Shortcuts: "my email is " → one's addresses, "moj broj je " → one's numbers.
        if (ASK_EMAIL.containsMatchIn(before)) return top(EMAIL_KIND)
        if (ASK_PHONE.containsMatchIn(before)) return top(PHONE_KIND)
        if (ASK_IBAN.containsMatchIn(before)) return top(IBAN_KIND)

        val iban = trailingIban(before)
        if (iban.isNotEmpty()) {
            val typed = iban.filter { it.isLetterOrDigit() }.uppercase()
            val hits = all.filter { it.third == IBAN_KIND }
                .filter { (v, _, _) -> v.filter { c -> c.isLetterOrDigit() }.uppercase().let { it.startsWith(typed) && it != typed } }
                .sortedByDescending { it.second }.map { it.first }.take(limit)
            if (hits.isNotEmpty()) return hits
        }

        val url = trailingUrl(before)
        if (url.isNotEmpty() || (isUrlField && before.isBlank())) {
            if (url.isEmpty()) return top(URL_KIND)
            val typed = bareUrl(url)
            val hits = all.filter { it.third == URL_KIND }
                .filter { (v, _, _) -> bareUrl(v).let { it.startsWith(typed) && it != typed } || (typed.length >= 3 && v.lowercase().startsWith(url.lowercase()) && !v.equals(url, true)) }
                .sortedByDescending { it.second }.map { it.first }.take(limit)
            if (hits.isNotEmpty() || url.lowercase().startsWith("www") || url.lowercase().startsWith("http")) return hits
        }

        val phone = trailingPhone(before)
        if (phone.isNotEmpty() || (isPhoneField && before.isBlank())) {
            if (phone.isEmpty()) return top(PHONE_KIND)
            val typed = digitsOf(phone.trimStart()).removePrefix("+")
            return all.asSequence()
                .filter { it.third == PHONE_KIND }
                .filter { (value, _, _) ->
                    val saved = digitsOf(value).removePrefix("+")
                    saved != typed && (saved.startsWith(typed) || nationalForms(saved).any { it.startsWith(typed) && it != typed })
                }
                .sortedByDescending { it.second }.map { it.first }.take(limit).toList()
        }

        val token = trailingToken(before)
        if (token.isEmpty()) return if (isEmailField) top(EMAIL_KIND) else emptyList()
        if (token.length < 2 && !isEmailField) return emptyList()
        return all.asSequence()
            .filter { it.third == EMAIL_KIND }
            .filter { (a, _, _) ->
                a.startsWith(token, ignoreCase = true) && !a.equals(token, ignoreCase = true) ||
                    // also from the second part of the name: "bosko" finds marko.bosko@...
                    (token.length >= 3 && !token.contains('@') && !token.contains('.') &&
                        a.substringBefore('@').split('.', '_', '-').any { p -> p.startsWith(token, ignoreCase = true) })
            }
            .sortedByDescending { it.second }.map { it.first }.take(limit).toList()
    }

    // --- The phone's contacts (opt-in) ---------------------------------------------------------------

    private const val CONTACTS_DELAY_MS = 8_000L

    /** Contact names, kept apart from the typed values: never written to the file, re-read at each start. */
    private val contactNames = HashSet<String>()

    /**
     * Reads names, emails and numbers from the phone's contacts, only if Contacts was allowed for TTT in
     * Android settings (the permission is declared, never asked for). Values already typed keep their
     * counts; contact values join with a count of 1, so what he actually types still comes first.
     */
    fun importContacts(context: Context) {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val cr = context.contentResolver
            val data = android.provider.ContactsContract.Data.CONTENT_URI
            val mime = android.provider.ContactsContract.Data.MIMETYPE
            val emailType = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
            val phoneType = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            val nameCol = android.provider.ContactsContract.Data.DISPLAY_NAME_PRIMARY
            val valueCol = android.provider.ContactsContract.Data.DATA1
            cr.query(data, arrayOf(mime, valueCol, nameCol), "$mime IN (?, ?)", arrayOf(emailType, phoneType), null)?.use { c ->
                while (c.moveToNext()) {
                    val kind = if (c.getString(0) == emailType) EMAIL_KIND else PHONE_KIND
                    val value = c.getString(1)?.trim().orEmpty()
                    val name = c.getString(2)?.trim().orEmpty()
                    synchronized(lock) {
                        if (value.isNotEmpty()) {
                            val key = keyOf(value, kind)
                            if (key.isNotEmpty() && entries[key] == null) entries[key] = Entry(value, 1, kind)
                        }
                        if (name.length >= 2 && name.any { it.isLetter() }) contactNames.add(name)
                    }
                }
            }
        }
    }

    /**
     * Contact names for a capitalised word being typed ("Mar" → "Marko Boško", "Marta"): only when he
     * started the word with a capital, the way a name is written, so ordinary words are never crowded out.
     */
    fun names(textBefore: CharSequence, limit: Int = 2): List<String> {
        val word = textBefore.takeLastWhile { it.isLetter() }.toString()
        if (word.length < 2 || !word.first().isUpperCase()) return emptyList()
        val names = synchronized(lock) { contactNames.toList() }
        return names.asSequence()
            .filter { n -> n.split(' ').any { it.startsWith(word, ignoreCase = false) && !it.equals(word) } }
            .sortedBy { it.length }
            .take(limit).toList()
    }

    /** "385912345678" is typed at home as "0912345678": the national forms of an international number. */
    private fun nationalForms(saved: String): List<String> =
        (1..3).filter { it < saved.length }.map { "0" + saved.substring(it) } +
            listOfNotNull(saved.removePrefix("00").takeIf { saved.startsWith("00") })

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            val f = file ?: return@launch
            val text = synchronized(lock) {
                entries.values.sortedByDescending { it.count }.take(MAX_ENTRIES)
                    .joinToString("") { "${it.value}\t${it.count}\t${it.kind}\n" }
            }
            runCatching {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(f)
            }
        }
    }
}
