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

/**
 * Keys only enter through files (Marko, 9.11.2026). This reads one file's text and lists every key in
 * it with the providers it could belong to, in the order they are tried. Plain Kotlin, no Android, so
 * the unit tests run it. Shapes and the label logic come from Key_Tester's KeyParser and KeyRingTester.
 *
 * Kinds: groq, google (AIza / AQ.), anthropic, assemblyai, deepgram, elevenlabs, speechify,
 * speechmatics, soniox, cloudflare (account id + token, joined by a newline), serviceaccount (Google JSON).
 * A token whose shape fits several providers lists them all; [KeyImporter] tests them in order and the
 * one that authenticates gets it.
 */
object KeyScan {
    data class Cand(val value: String, val kinds: List<String>)

    private val HEX32 = Regex("[0-9a-fA-F]{32}")
    private val HEX40 = Regex("[0-9a-fA-F]{40}")
    private val HEX64 = Regex("[0-9a-fA-F]{64}")
    private val ALNUM32 = Regex("[A-Za-z0-9]{32}")
    private val GOOGLE = Regex("(AQ\\.[0-9A-Za-z._-]{20,}|AIza[0-9A-Za-z_-]{20,})")
    private val ANTHROPIC = Regex("sk-ant-[0-9A-Za-z_-]{20,}")
    private val GROQ = Regex("gsk_[0-9A-Za-z_-]{20,}")
    private val SKU = Regex("sk_[0-9A-Za-z_-]{16,}")
    private val CFTOKEN = Regex("cf[aut]t_[A-Za-z0-9_-]{20,}")
    private val NOT_OURS = Regex("^(gh[pousr]_|github_pat_|sk-|xox[a-z]-|AKIA|ya29\\.|rk_|pk_)[A-Za-z0-9_-]{6,}")
    private val SEP = Regex("[\\s,;:\"'=|\\[\\](){}<>]+")

    private val HINTS = listOf(
        "groq" to "groq", "deepgram" to "deepgram", "assembly" to "assemblyai", "speechmatics" to "speechmatics",
        "soniox" to "soniox", "eleven" to "elevenlabs", "speechify" to "speechify", "gemini" to "google",
        "google" to "google", "anthropic" to "anthropic", "claude" to "anthropic",
    )

    private fun hinted(ctx: String): List<String> = HINTS.filter { ctx.contains(it.first) }.map { it.second }.distinct()

    private fun tokenKinds(tok: String, ctx: String): List<String> {
        val h = hinted(ctx)
        return when {
            ANTHROPIC.matches(tok) -> listOf("anthropic")
            GROQ.matches(tok) -> listOf("groq")
            GOOGLE.matches(tok) -> listOf("google")
            CFTOKEN.matches(tok) -> listOf("cftoken")
            SKU.matches(tok) ->
                if ("speechify" in h || (tok.length >= 44 && "elevenlabs" !in h)) listOf("speechify", "elevenlabs") else listOf("elevenlabs", "speechify")
            NOT_OURS.containsMatchIn(tok) -> emptyList()
            tok.length in 24..128 && tok.any { it.isDigit() } && tok.any { it.isLetter() } -> {
                val shaped = when {
                    HEX64.matches(tok) -> listOf("soniox")
                    HEX40.matches(tok) -> listOf("deepgram")
                    HEX32.matches(tok) -> listOf("assemblyai")
                    ALNUM32.matches(tok) && tok.any { it.isUpperCase() } && tok.any { it.isLowerCase() } -> listOf("speechmatics")
                    else -> emptyList()
                }
                // A label wins; the shapes follow. Only the providers that take a plain token.
                (h.filter { it != "google" && it != "anthropic" } + shaped).distinct()
            }
            else -> emptyList()
        }
    }

    /** A Google service account file as one line, only when it holds what OAuth needs. */
    fun serviceAccount(text: String): String? = runCatching {
        val o = org.json.JSONObject(text)
        if (o.optString("type") != "service_account" || o.optString("client_email").isEmpty() ||
            !o.optString("private_key").contains("PRIVATE KEY")
        ) null
        else org.json.JSONObject().put("client_email", o.getString("client_email")).put("private_key", o.getString("private_key"))
            .put("project_id", o.optString("project_id")).put("token_uri", o.optString("token_uri", "https://oauth2.googleapis.com/token")).toString()
    }.getOrNull()

    fun scan(fileName: String, text: String): List<Cand> {
        serviceAccount(text)?.let { return listOf(Cand(it, listOf("serviceaccount"))) }
        val name = fileName.lowercase()
        val lines = text.split("\n")
        val out = LinkedHashMap<String, Cand>()

        // Pass 1: every token with its label (the file name, the line above when it has no key, the line itself).
        class Tok(val value: String, val kinds: List<String>)
        val toks = ArrayList<Tok>()
        for ((i, line) in lines.withIndex()) {
            val prev = if (i == 0) "" else lines[i - 1].trim()
            val prevHasKey = prev.split(SEP).any { tokenKinds(it.trim().trim('.', '-', '_'), name).isNotEmpty() }
            val ctx = (name + " " + (if (prev.isEmpty() || prevHasKey) "" else prev) + " " + line).lowercase()
            for (raw in line.split(SEP)) {
                val t = raw.trim().trim('.', '-', '_')
                if (t.isEmpty() || t == "DELETED") continue
                val k = tokenKinds(t, ctx)
                if (k.isNotEmpty()) toks += Tok(t, k)
            }
        }

        // Pass 2: Cloudflare is two parts, an account id (32 hex) and a token (cfat_/cfut_ or any token in a Cloudflare file).
        val cfFile = name.contains("cloudflare") || text.lowercase().contains("cloudflare") || toks.any { it.kinds == listOf("cftoken") }
        val taken = HashSet<String>()
        if (cfFile) {
            val acct = toks.firstOrNull { HEX32.matches(it.value) }?.value
            val token = toks.firstOrNull { it.kinds == listOf("cftoken") }?.value
                ?: toks.firstOrNull { it.value != acct && it.value.length >= 30 && !HEX32.matches(it.value) && it.kinds.none { k -> k == "groq" || k == "google" || k == "anthropic" } }?.value
            if (acct != null && token != null) {
                out["cf:$acct:$token"] = Cand(acct + "\n" + token, listOf("cloudflare"))
                taken += acct; taken += token
            }
        }
        for (t in toks) {
            if (t.value in taken || out.containsKey(t.value) || t.kinds == listOf("cftoken")) continue
            out[t.value] = Cand(t.value, t.kinds)
        }
        return out.values.toList()
    }

    /** At most the last four characters. */
    fun mask(v: String): String = "…" + v.takeLast(4)
}
