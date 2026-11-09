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
        fun line(): String {
            val parts = mutableListOf("$keys ${unit(kind, keys)}", "$keys tested", "$working working")
            if (limited > 0) parts += "$limited rate-limited for now"
            if (dead > 0) parts += "$dead refused"
            if (noBalance > 0) parts += "no balance"
            if (unreached > 0) parts += "$unreached not reached"
            val extra = if (kind == "google" && working > 0) " (Gemini $gemini, Speech v1 $speech)" else ""
            return label(kind) + ": " + parts.joinToString(", ") + extra
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

    suspend fun importTexts(c: Context, files: List<Pair<String, String>>, onProgress: (String) -> Unit): Outcome {
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
                    gem == KStatus.WORKING || sp == KStatus.WORKING -> g.working++
                    good(gem) || good(sp) -> { g.limited++; g.working++ }
                    gem == KStatus.NETWORK && sp == KStatus.NETWORK -> { g.unreached++; dropped++ }
                    else -> { g.dead++; dropped++ }
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
