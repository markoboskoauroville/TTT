/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.engines

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * The one way keys enter TTT (Marko, 9.11.2026): files. Each file is scanned for keys ([KeyScan]),
 * every key is tested with a free auth call ([KeyTester]), the working ones go into the provider's
 * Keystore-encrypted ring ([EngineKeys]) and the rest are dropped, never stored. The summary says it
 * in words: "Groq: 10 keys, 10 tested, 5 working". No key value is ever shown, logged or put in the summary.
 */
object KeyImporter {
    data class Outcome(val summary: String, val stored: Int)

    private val ALL_RINGS = listOf("groq", "gemini", "google_v1", "deepgram", "assemblyai", "speechmatics", "soniox", "elevenlabs", "speechify", "anthropic", "cloudflare", "google_chirp")

    private fun label(kind: String) = when (kind) {
        "groq" -> "Groq"; "google" -> "Google"; "gemini" -> "Google"; "google_v1" -> "Google"
        "serviceaccount", "google_chirp" -> "Google service account"
        "anthropic" -> "Anthropic"; "assemblyai" -> "AssemblyAI"; "deepgram" -> "Deepgram"
        "elevenlabs" -> "ElevenLabs"; "speechify" -> "Speechify"; "speechmatics" -> "Speechmatics"
        "soniox" -> "Soniox"; "cloudflare" -> "Cloudflare"
        else -> kind
    }

    private fun unit(kind: String, n: Int) = when (kind) {
        "cloudflare", "serviceaccount", "google_chirp" -> if (n == 1) "account" else "accounts"
        else -> if (n == 1) "key" else "keys"
    }

    private fun ringOf(kind: String) = if (kind == "serviceaccount") "google_chirp" else kind

    private fun good(s: KStatus) = s == KStatus.WORKING || s == KStatus.LIMITED

    private class G(val kind: String) {
        var keys = 0; var working = 0; var limited = 0; var dead = 0; var noBalance = 0; var unreached = 0
        var gemini = 0; var speech = 0
        /** One word per key, in file order (Marko, 13.11.2026: the picker says which key works and which does not). */
        val marks = ArrayList<String>()
        fun line(): String {
            val parts = mutableListOf("$keys ${unit(kind, keys)}", "$keys tested", "$working working")
            if (limited > 0) parts += "$limited rate-limited for now"
            if (dead > 0) parts += "$dead refused"
            if (noBalance > 0) parts += "no balance"
            if (unreached > 0) parts += "$unreached not reached"
            val extra = if (kind == "google" && working > 0) " (Gemini $gemini, Speech v1 $speech)" else ""
            val each = if (marks.isEmpty()) "" else "\n   " + marks.mapIndexed { i, m -> "key ${i + 1} $m" }.joinToString(", ")
            return label(kind) + ": " + parts.joinToString(", ") + extra + each
        }
    }

    /** Reads the chosen files (text only, small) and imports them. */
    suspend fun importUris(c: Context, uris: List<Uri>, onProgress: (String) -> Unit): Outcome {
        val files = ArrayList<Pair<String, String>>()
        var skipped = 0
        for (u in uris) {
            val name = runCatching {
                c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: "file"
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in setOf("txt", "json", "env", "csv", "conf", "cfg", "ini", "md", "key", "")) { skipped++; continue }
            val text = runCatching { c.contentResolver.openInputStream(u)?.use { String(it.readBytes().take(2_000_000).toByteArray()) } }.getOrNull()
            if (text == null) { skipped++; continue }
            files += name to text
        }
        val o = importTexts(c, files, onProgress)
        return if (skipped > 0) o.copy(summary = o.summary + "\n$skipped file(s) skipped: text files only (.txt, .json, .env).") else o
    }

    /** GitHub tokens: only for RE's private memory repository (MaRemoteDb), never a transcription engine. */
    private val GITHUB = Regex("github_pat_[A-Za-z0-9_]{40,}|gh[pousr]_[A-Za-z0-9]{30,}")

    /**
     * A GitHub token in the chosen files becomes the RE Magic Database token (Marko, 13.11.2026: "keys enter through
     * file picking"; the Settings paste box is gone). The first one that can write to the repository is kept, encrypted.
     */
    private suspend fun importGithub(c: Context, files: List<Pair<String, String>>, onProgress: (String) -> Unit): String? {
        val toks = files.flatMap { (_, t) -> GITHUB.findAll(t).map { it.value }.toList() }.distinct()
        if (toks.isEmpty()) return null
        dev.patrickgold.florisboard.dictate.MaRemoteDb.attach(c)
        // RE reads his repositories with the token that can see the most (13.11.2026): each token is tested for reading.
        val reads = ArrayList<String>()
        var bestRead: String? = null
        for ((i, t) in toks.withIndex()) {
            onProgress("Testing GitHub token ${i + 1} of ${toks.size} for reading…")
            val (ok, what) = dev.patrickgold.florisboard.dictate.MaReGithub.test(t)
            reads += "token ${i + 1} $what"
            if (ok && bestRead == null) bestRead = t
        }
        if (bestRead != null) EngineKeys.setLines(c, dev.patrickgold.florisboard.dictate.MaReGithub.RING, listOf(bestRead))
        // Settings sync (13.11.2026): the first token that can write to his settings repository is kept for it too.
        dev.patrickgold.florisboard.dictate.MaSettingsSync.attach(c)
        val syncLine = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dev.patrickgold.florisboard.dictate.MaSettingsSync.offerTokens(toks)
        }
        // Shared corrections (MANTRA_CORRECTIONS, 21.11.2026): the first token that can write there is kept for it.
        dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.attach(c)
        val corrLine = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.offerTokens(toks)
        }
        val readLine = "GitHub (RE reads your repositories): " + reads.joinToString(", ") + (if (bestRead != null) "; kept" else "; none kept") +
            (syncLine?.let { "\n$it" } ?: "") + "\n" + corrLine
        for ((i, t) in toks.withIndex()) {
            onProgress("Testing GitHub token ${i + 1} of ${toks.size}…")
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.patrickgold.florisboard.dictate.MaRemoteDb.tokenWorks(t) }
            if (ok && dev.patrickgold.florisboard.dictate.MaRemoteDb.saveToken(t)) {
                dev.patrickgold.florisboard.dictate.MaRemoteDb.syncSoon()
                return readLine + "\nGitHub (RE Magic Database): ${toks.size} token(s), token ${i + 1} can write, stored"
            }
        }
        return readLine + "\nGitHub (RE Magic Database): ${toks.size} token(s), none can write to ${dev.patrickgold.florisboard.dictate.MaRemoteDb.REPO}, not stored"
    }

    /** The KLIPY GIF key (13.11.2026: its paste field is gone): a token on a line, or in a file, labelled klipy. */
    private suspend fun importKlipy(files: List<Pair<String, String>>): String? {
        val tok = Regex("[A-Za-z0-9_-]{20,}")
        var found: String? = null
        for ((name, text) in files) {
            val lines = text.lines()
            for ((i, line) in lines.withIndex()) {
                val labelled = name.contains("klipy", true) || line.contains("klipy", true) ||
                    (i > 0 && lines[i - 1].contains("klipy", true) && !tok.containsMatchIn(lines[i - 1].substringAfter(':', "")))
                if (!labelled) continue
                found = tok.findAll(line).map { it.value }.firstOrNull { !it.contains("klipy", true) } ?: continue
                break
            }
            if (found != null) break
        }
        val k = found ?: return null
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        prefs.gif.klipyApiKey.set(k)
        return "KLIPY (GIF search): 1 key stored"
    }

    /**
     * The Mac relay token (MaClaudeLink, 13.11.2026): a file named mantra-relay-token*.txt holds it on one line. Kept
     * encrypted in its own ring; the file is not scanned for engine keys.
     */
    private suspend fun importRelay(c: Context, files: List<Pair<String, String>>): String? {
        val f = files.firstOrNull { (n, _) -> n.contains("relay", true) } ?: return null
        // Stored and tested at once (13.11.2026: "I want to have some kind of feedback and some kind of testing").
        return "Mac relay key (chat with Claude Code): " + dev.patrickgold.florisboard.dictate.MaClaudeLink.importToken(c, f.second)
    }

    suspend fun importTexts(c: Context, all: List<Pair<String, String>>, onProgress: (String) -> Unit): Outcome {
        val relay = importRelay(c, all)
        val files = all.filterNot { (n, _) -> n.contains("relay", true) }
        if (files.isEmpty() && relay != null) return Outcome(relay, if (relay.endsWith("stored")) 1 else 0)
        val gh = importGithub(c, files, onProgress)
        val gif = importKlipy(files)
        val o = importEngineKeys(c, files, onProgress)
        val extra = listOfNotNull(relay, gh, gif)
        val more = extra.count { it.endsWith("stored") && !it.endsWith("not stored") }
        return if (extra.isEmpty()) o else o.copy(summary = (extra + o.summary).joinToString("\n"), stored = o.stored + more)
    }

    private suspend fun importEngineKeys(c: Context, files: List<Pair<String, String>>, onProgress: (String) -> Unit): Outcome {
        val seen = HashSet<String>()
        val cands = files.flatMap { (n, t) -> KeyScan.scan(n, t) }.filter { seen.add(it.value) }
        if (cands.isEmpty()) return Outcome("No keys found in ${files.size} file(s).", 0)
        val groups = LinkedHashMap<String, G>()
        val keep = LinkedHashMap<String, MutableList<String>>()
        var dropped = 0
        for ((i, cand) in cands.withIndex()) {
            onProgress("Testing ${i + 1} of ${cands.size}…")
            val first = cand.kinds.first()
            if (first == "google") {
                val g = groups.getOrPut("google") { G("google") }
                g.keys++
                val gem = KeyTester.test("gemini", cand.value)
                val sp = KeyTester.test("google_v1", cand.value)
                if (good(gem)) { g.gemini++; keep.getOrPut("gemini") { mutableListOf() } += cand.value }
                if (good(sp)) { g.speech++; keep.getOrPut("google_v1") { mutableListOf() } += cand.value }
                when {
                    gem == KStatus.WORKING || sp == KStatus.WORKING -> { g.working++; g.marks += "works" }
                    good(gem) || good(sp) -> { g.limited++; g.working++; g.marks += "works (rate-limited now)" }
                    gem == KStatus.NETWORK && sp == KStatus.NETWORK -> { g.unreached++; dropped++; g.marks += "not reached" }
                    else -> { g.dead++; dropped++; g.marks += "does not work" }
                }
                continue
            }
            var adopted: String? = null
            var status = KStatus.DEAD
            for ((n, k) in cand.kinds.withIndex()) {
                val s = KeyTester.test(ringOf(k), cand.value)
                if (n == 0) status = s
                if (good(s) || s == KStatus.NO_BALANCE) { adopted = k; status = s; break }
            }
            val kind = adopted ?: first
            val g = groups.getOrPut(kind) { G(kind) }
            g.keys++
            g.marks += when (status) {
                KStatus.WORKING -> "works"
                KStatus.LIMITED -> "works (rate-limited now)"
                KStatus.NO_BALANCE -> "does not work (no balance)"
                KStatus.NETWORK -> "not reached"
                KStatus.DEAD -> "does not work"
            }
            when (status) {
                KStatus.WORKING -> { g.working++; keep.getOrPut(ringOf(kind)) { mutableListOf() } += cand.value }
                KStatus.LIMITED -> { g.working++; g.limited++; keep.getOrPut(ringOf(kind)) { mutableListOf() } += cand.value }
                KStatus.NO_BALANCE -> { g.noBalance++; dropped++ }
                KStatus.NETWORK -> { g.unreached++; dropped++ }
                KStatus.DEAD -> { g.dead++; dropped++ }
            }
        }
        // Store the working ones. Cloudflare and the service account replace; the rest merge into the ring.
        var stored = 0
        for ((ring, vals) in keep) {
            stored += if (ring == "cloudflare") { EngineKeys.setLines(c, ring, vals.first().lines()); 1 } else EngineKeys.merge(c, ring, vals)
            // A key given by file is Marko's own act: an engine with no free quota is then switched on.
            if (EngineCatalog.byId(ring)?.paid == true) EngineStore.setEnabled(c, ring, true)
        }
        mirrorLegacy(keep)
        val lines = groups.values.map { it.line() }
        val tail = "Stored $stored new working key(s); $dropped not stored."
        return Outcome((lines + tail).joinToString("\n"), stored)
    }

    /** The keyboard's older key ring (Anthropic for RE and the restyle prompts, Speechify, AssemblyAI) gets the working ones too. */
    private suspend fun mirrorLegacy(keep: Map<String, List<String>>) {
        val text = listOf("anthropic", "speechify", "assemblyai").flatMap { keep[it].orEmpty() }.joinToString("\n")
        if (text.isBlank()) return
        runCatching {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            val r = dev.patrickgold.florisboard.dictate.MaKeyImport.importAll(text, prefs.dictate.providerAccounts.get())
            if (r.added > 0) prefs.dictate.providerAccounts.set(r.accounts)
        }
    }

    /** Re-tests every stored key; the ones that no longer work are dropped. */
    // ------------------------------------------------------------------ every key, listed and tested (23.11.2026)
    //
    // Marko, 23.11.2026: "listing all the API keys and also have a button to test all the API keys. Testing is very
    // important so people know is it working or not working and to be able to troubleshoot." Unlike [retest], testing
    // here never removes a key: it says per key what is wrong and what to do. No key value is shown: only the provider,
    // its number in the ring and the first characters that tell the kind (gsk_, sk-ant-, AIza...). Results are kept with a
    // hash of the key (never the key) so the list shows them until the next test.

    data class KeyRow(
        val ring: String, val name: String, val number: Int, val total: Int, val hint: String,
        val result: String?, val ok: Boolean?, val at: String?,
    )

    private fun testsPrefs(c: Context) = c.applicationContext.getSharedPreferences("ttt_key_tests", Context.MODE_PRIVATE)

    private fun hashOf(v: String) = java.security.MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    private fun hint(ring: String, v: String): String = when {
        ring == "google_chirp" -> "service account"
        ring == "cloudflare" -> "account"
        else -> v.trim().take(if (v.startsWith("sk-ant-")) 7 else 4) + "\u2026"
    }

    /** The answer in plain words, and whether it works. */
    fun explain(s: KStatus): Pair<String, Boolean> = when (s) {
        KStatus.WORKING -> "works" to true
        KStatus.LIMITED -> "works, but busy right now (rate limit): it rests a minute or until the daily reset" to true
        KStatus.NO_BALANCE -> "no credit left: top up this account or use another key" to false
        KStatus.DEAD -> "refused: the key is wrong, deleted or revoked; make a new one" to false
        KStatus.NETWORK -> "not reached: no internet, or the provider is down; try again" to false
    }

    private fun units(c: Context, ring: String): List<String> {
        val cur = EngineKeys.ownLines(c, ring)
        return if (ring == "cloudflare" && cur.isNotEmpty()) listOf(cur.joinToString("\n")) else cur
    }

    private fun ringName(ring: String) = when (ring) {
        "gemini" -> "Google (Gemini)"; "google_v1" -> "Google (Speech v1)"; "re_qwen" -> "Qwen for RE (Groq)"
        else -> label(ring)
    }

    /** Every stored key, with the result of its last test. */
    fun rows(c: Context): List<KeyRow> {
        val p = testsPrefs(c)
        return (ALL_RINGS + "re_qwen").flatMap { ring ->
            val us = units(c, ring)
            us.mapIndexed { i, v ->
                val saved = p.getString(ring + "#" + hashOf(v), null)?.split('|')
                KeyRow(ring, ringName(ring), i + 1, us.size, hint(ring, v), saved?.getOrNull(0), saved?.getOrNull(1)?.let { it == "1" }, saved?.getOrNull(2))
            }
        }
    }

    /** Tests every key once, keeps every key, and returns the list with the answers. */
    suspend fun testAll(c: Context, onProgress: (String) -> Unit): List<KeyRow> {
        val p = testsPrefs(c)
        val stamp = java.text.SimpleDateFormat("d.M. HH:mm", java.util.Locale.ROOT).format(java.util.Date())
        val total = (ALL_RINGS + "re_qwen").sumOf { units(c, it).size }
        var n = 0
        for (ring in ALL_RINGS + "re_qwen") {
            val us = units(c, ring)
            for ((i, v) in us.withIndex()) {
                n++
                onProgress("Testing $n of $total: ${ringName(ring)} ${i + 1}/${us.size}\u2026")
                val s = KeyTester.test(if (ring == "re_qwen") "groq" else ring, v)
                val (words, ok) = explain(s)
                p.edit().putString(ring + "#" + hashOf(v), words + "|" + (if (ok) "1" else "0") + "|" + stamp).apply()
            }
        }
        return rows(c)
    }

    suspend fun retest(c: Context, onProgress: (String) -> Unit): Outcome {
        val out = ArrayList<String>()
        var removed = 0
        for (ring in ALL_RINGS) {
            val cur = EngineKeys.ownLines(c, ring)
            if (cur.isEmpty()) continue
            onProgress("Re-testing ${label(ring)}…")
            val units: List<String> = if (ring == "cloudflare") listOf(cur.joinToString("\n")) else cur
            val kept = ArrayList<String>()
            var w = 0; var bad = 0; var unreached = 0
            for (u in units) {
                val s = KeyTester.test(ring, u)
                when {
                    good(s) -> { w++; kept += u }
                    s == KStatus.NETWORK -> { unreached++; kept += u }
                    else -> bad++
                }
            }
            if (bad > 0) {
                removed += bad
                EngineKeys.setLines(c, ring, if (ring == "cloudflare") kept.flatMap { it.lines() } else kept)
            }
            val k = if (ring == "gemini" || ring == "google_v1") "google" else ring
            out += (if (ring == "gemini") "Google (Gemini)" else if (ring == "google_v1") "Google (Speech v1)" else label(k)) +
                ": ${units.size} ${unit(ring, units.size)}, ${units.size} tested, $w working" +
                (if (bad > 0) ", $bad removed" else "") + (if (unreached > 0) ", $unreached not reached" else "")
        }
        if (out.isEmpty()) return Outcome("No keys stored yet. Import a file first.", 0)
        return Outcome((out + "Removed $removed key(s) that no longer work.").joinToString("\n"), removed)
    }
}
