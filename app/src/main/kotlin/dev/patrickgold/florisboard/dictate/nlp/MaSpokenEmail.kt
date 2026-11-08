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
 * A dictated email address, written as one and matched to the address book.
 *
 * Marko, 8.11.2026: *"spoken email addresses be corrected to match the address book. Yes."* Parakeet
 * heard "marko.bosko@gmail.com" as "marco.bosco at gmail.com"; AssemblyAI tends to write
 * "marko dot bosko at gmail dot com". Both are put back together here, and when a saved address
 * ([MaAddressBook]) is close, the saved one is written instead, spelled as it was saved.
 *
 * **What counts as a spoken address:** a name part, the word `at` / `et` / `@` / `majmun` / `monkey`,
 * and a domain with at least one dot (`gmail.com`, `gmail dot com`, `gmail točka com`) ending in a
 * top-level domain. The name part may itself be spoken with `dot` / `točka`, `underscore` / `donja crta`
 * or `dash` / `crtica` between its pieces.
 *
 * **When it is rewritten:**
 * - a saved address matches (same domain, give or take one letter; the name part within a quarter of its
 *   letters, separators ignored, so "marco bosco" finds "marko.bosko"): the saved address;
 * - otherwise only for a well-known mail domain (gmail, yahoo, outlook, …) or a domain one of the saved
 *   addresses uses, and never for a pronoun ("email me at gmail dot com" stays words): the address as
 *   heard, lower case.
 * Anything else is left as dictated: "meet me at home dot com" is not mail.
 *
 * Written addresses ("marco.bosco@gmail.com") are snapped to a close saved one the same way.
 */
object MaSpokenEmail {

    private val AT_WORDS = setOf("at", "et", "@", "majmun", "monkey", "affe")
    private val DOT_WORDS = setOf("dot", "točka", "tocka", "punkt", "period")
    private val NAME_SEPARATORS = mapOf(
        "dot" to ".", "točka" to ".", "tocka" to ".", "period" to ".",
        "underscore" to "_", "podvlaka" to "_",
        "dash" to "-", "hyphen" to "-", "crtica" to "-", "minus" to "-",
    )
    private val MAIL_DOMAINS = setOf(
        "gmail.com", "googlemail.com", "yahoo.com", "outlook.com", "hotmail.com", "live.com", "icloud.com",
        "me.com", "proton.me", "protonmail.com", "gmx.com", "gmx.net", "aol.com", "mail.com", "yandex.com",
        "net.hr", "t-com.hr", "gmail.hr", "inet.hr", "zg.t-com.hr", "auroville.community", "auroville.org.in",
    )
    private val PRONOUNS = setOf(
        "me", "us", "you", "him", "her", "them", "it", "i", "we", "mi", "nas", "ja", "ti", "te", "vas",
        "mene", "tebe", "njega", "nju", "email", "e-mail", "mail", "write", "piši", "pisi", "home", "work",
    )
    private val TLD = Regex("""^[a-z]{2,12}$""")
    private val DOMAIN_PIECE = Regex("""^[a-z0-9-]+$""")
    private val NAME_PIECE = Regex("""^[\p{L}0-9._%+-]+$""")
    private val WRITTEN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
    private val LEADING = " \t\n\"'(“‘"
    private val TRAILING = " \t\n.,!?;:\"')”’"

    /** [text] with every spoken or written address put together and snapped to the address book. */
    fun apply(text: String): String = runCatching { apply(text, MaAddressBook.savedEmails()) }.getOrDefault(text)

    /** The pure rule, for tests: [saved] are the address book's emails. */
    fun apply(text: String, saved: List<String>): String {
        if (text.isBlank()) return text
        var out = snapSpoken(text, saved)
        if (saved.isNotEmpty()) {
            out = WRITTEN.replace(out) { m -> score(m.value.lowercase(), saved)?.first ?: m.value }
        }
        return out
    }

    private class Tok(val start: Int, val end: Int, val core: String, val coreStart: Int, val coreEnd: Int)

    private fun tokens(text: String): List<Tok> = Regex("""\S+""").findAll(text).map { m ->
        val s = m.value
        var a = 0
        var b = s.length
        while (a < b && s[a] in LEADING) a++
        while (b > a && s[b - 1] in TRAILING) b--
        Tok(m.range.first, m.range.last + 1, s.substring(a, b), m.range.first + a, m.range.first + b)
    }.toList()

    private fun snapSpoken(text: String, saved: List<String>): String {
        val toks = tokens(text)
        if (toks.size < 3) return text
        val sb = StringBuilder()
        var copiedTo = 0
        var i = 1
        while (i < toks.size - 1) {
            val at = toks[i]
            if (at.core.lowercase() !in AT_WORDS || at.coreEnd != at.end && at.core != "@") { i++; continue }
            // The domain: one piece with dots already in it, or pieces joined by spoken dots.
            val domainParts = ArrayList<String>()
            var j = i + 1
            var lastDomainTok = -1
            val first = toks[j].core.lowercase()
            if ('.' in first) {
                val parts = first.split('.')
                if (parts.all { DOMAIN_PIECE.matches(it) }) { domainParts += parts; lastDomainTok = j }
            } else if (DOMAIN_PIECE.matches(first)) {
                domainParts += first
                lastDomainTok = j
                // A token ending in punctuation closes the domain ("gmail dot com, and").
                while (toks[lastDomainTok].coreEnd == toks[lastDomainTok].end && lastDomainTok + 2 < toks.size &&
                    toks[lastDomainTok + 1].core.lowercase() in DOT_WORDS &&
                    DOMAIN_PIECE.matches(toks[lastDomainTok + 2].core.lowercase())
                ) {
                    domainParts += toks[lastDomainTok + 2].core.lowercase()
                    lastDomainTok += 2
                }
            }
            if (domainParts.size < 2 || !TLD.matches(domainParts.last()) || lastDomainTok < 0) { i++; continue }
            val domain = domainParts.joinToString(".")
            // The name part: the word before "at", with spoken separators joining earlier pieces.
            var k = i - 1
            if (!NAME_PIECE.matches(toks[k].core) || toks[k].coreEnd != toks[k].end) { i++; continue }
            var local = toks[k].core.lowercase()
            while (k - 2 >= 0 && toks[k - 1].core.lowercase() in NAME_SEPARATORS &&
                toks[k - 1].coreEnd == toks[k - 1].end && toks[k - 2].coreEnd == toks[k - 2].end &&
                NAME_PIECE.matches(toks[k - 2].core)
            ) {
                local = toks[k - 2].core.lowercase() + NAME_SEPARATORS.getValue(toks[k - 1].core.lowercase()) + local
                k -= 2
            }
            if (local.isBlank() || copiedTo > toks[k].coreStart) { i++; continue }
            // Up to two more plain words before it, for "marko bosko at gmail dot com".
            val extras = ArrayList<Pair<String, Int>>()
            var e = k - 1
            var extra = local
            while (e >= 0 && extras.size < 2 && toks[e].coreEnd == toks[e].end &&
                NAME_PIECE.matches(toks[e].core) && toks[e].start >= copiedTo
            ) {
                extra = toks[e].core.lowercase() + extra
                extras += extra to e
                e--
            }
            val snapped = if (saved.isNotEmpty()) nearestWithStart("$local@$domain", saved, extras, domain) else null
            val replacement: String
            val startTok: Int
            if (snapped != null) {
                replacement = snapped.first
                startTok = snapped.second ?: k
            } else {
                val known = domain in MAIL_DOMAINS || saved.any { it.substringAfter('@').equals(domain, true) }
                if (!known || local in PRONOUNS || local.length < 2) { i++; continue }
                replacement = "$local@$domain"
                startTok = k
            }
            sb.append(text, copiedTo, toks[startTok].coreStart).append(replacement)
            copiedTo = toks[lastDomainTok].coreEnd
            i = lastDomainTok + 1
        }
        if (copiedTo == 0) return text
        sb.append(text, copiedTo, text.length)
        return sb.toString()
    }

    /** The saved address closest to [heard] ("name@domain"), and which extra word it started at. */
    private fun nearestWithStart(
        heard: String,
        saved: List<String>,
        extras: List<Pair<String, Int>>,
        domain: String,
    ): Pair<String, Int?>? {
        var best: Triple<String, Int?, Double>? = null
        val variants = listOf(heard to null as Int?) + extras.map { (name, tok) -> "$name@$domain" to tok }
        for ((candidate, tok) in variants) {
            val hit = score(candidate, saved) ?: continue
            if (best == null || hit.second < best.third) best = Triple(hit.first, tok, hit.second)
        }
        return best?.let { it.first to it.second }
    }

    /** Best saved address for [candidate] with its relative distance, or null when none is close. */
    private fun score(candidate: String, saved: List<String>): Pair<String, Double>? {
        val cLocal = letters(candidate.substringBefore('@'))
        val cDomain = candidate.substringAfter('@').lowercase()
        if (cLocal.isEmpty()) return null
        var best: Pair<String, Double>? = null
        for (s in saved) {
            val sDomain = s.substringAfter('@').lowercase()
            if (sDomain != cDomain && distance(sDomain, cDomain) > 1) continue
            val sLocal = letters(s.substringBefore('@'))
            if (sLocal.isEmpty()) continue
            val d = distance(cLocal, sLocal)
            // A quarter of the letters: one for "marko", two for "markobosko"; a short name must match.
            val allowed = sLocal.length / 4
            if (d > allowed) continue
            val rel = d.toDouble() / sLocal.length + (if (sDomain == cDomain) 0.0 else 0.1)
            if (best == null || rel < best.second) best = s to rel
        }
        return best
    }

    /** Letters and digits only, folded: "Marco.Bosco" and "marko_bosko" compare as names. */
    private fun letters(s: String): String = buildString {
        for (c in s.lowercase()) {
            if (c.isLetterOrDigit()) append(
                when (c) { 'č', 'ć' -> 'c'; 'š' -> 's'; 'ž' -> 'z'; 'đ' -> 'd'; else -> c },
            )
        }
    }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
