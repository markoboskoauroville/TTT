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
        val readLine = "GitHub (RE reads your repositories): " + reads.joinToString(", ") + (if (bestRead != null) "; kept" else "; none kept") +
            (syncLine?.let { "\n$it" } ?: "")
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
    private fun importRelay(c: Context, files: List<Pair<String, String>>): String? {
        val f = files.firstOrNull { (n, _) -> n.contains("mantra-relay-token", true) } ?: return null
        val tok = f.second.lines().map { it.trim() }.firstOrNull { Regex("[A-Za-z0-9_-]{16,}").matches(it) }
            ?: return "Mac relay token: none found in ${f.first}"
        EngineKeys.setLines(c, dev.patrickgold.florisboard.dictate.MaClaudeLink.RING, listOf(tok))
        dev.patrickgold.florisboard.dictate.MaClaudeLink.flush(c)
        return "Mac relay token (chat with Claude Code): 1 key stored"
    }

    suspend fun importTexts(c: Context, all: List<Pair<String, String>>, onProgress: (String) -> Unit): Outcome {
        val relay = importRelay(c, all)
        val files = all.filterNot { (n, _) -> n.contains("mantra-relay-token", true) }
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
