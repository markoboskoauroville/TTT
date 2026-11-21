/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.nlp

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.patrickgold.florisboard.dictate.MaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * THE SHARED CORRECTIONS (Marko, 17.11.2026: "make phone and this app write to same GitHub data repository so the
 * databases can be shared for corrections, and it learns from my mistakes"; built in TTT 21.11.2026).
 *
 * Repository markoboskoauroville/MANTRA_CORRECTIONS, spec in its TAKEOVER.md. Each device writes ONLY its own file,
 * `learned/<source>.jsonl` (source = `nothing`, `pixel`, or `mac` for Vox to Txt), append-only JSON lines:
 * `{"id":"<lang>|<from lower>","from","to","lang","source","kind":"learned|mine","count","enabled","updated"}`; the last
 * line of an id in a file is that device's state. The merge is a union: per id the newest `updated` decides `to` and
 * `enabled`; `count` is the sum over the devices that agree on that `to`; `kind` is mine if any agreeing line is. A rule
 * applies when enabled and (mine or count >= 2), TTT's [MaCorrectionRules.ACTIVE_AT].
 *
 * On this phone: [recordLocal] turns every change of the local rules (files/ma_dictation_corrections.tsv, unchanged)
 * into new lines of the device file, which is also the offline queue; the first time it runs every rule the phone
 * already learned becomes a line, so the old rules go up. [effective] is what the keyboard applies and the Corrections
 * screen lists: the union of every device's file. Pull at start and every 5 minutes, push a few seconds after learning,
 * through the GitHub contents API. Token: its own (encrypted with a Keystore key) from "Import keys from file", or RE's
 * database token if it can write here. Never in a file, a log or the repository. Words are never logged, only counts.
 */
object MaCorrectionsSync {
    const val REPO = "markoboskoauroville/MANTRA_CORRECTIONS"
    private const val API = "https://api.github.com/repos/$REPO/contents"
    private const val PREFS = "ttt_corrections_sync"
    private const val ALIAS = "ttt_corrections_sync_token_key"
    private const val PULL_EVERY_MS = 5 * 60_000L
    private const val PUSH_DELAY_MS = 4_000L

    data class Line(
        val id: String, val from: String, val to: String, val lang: String, val source: String,
        val kind: String, val count: Int, val enabled: Boolean, val updated: String,
    ) {
        fun json(): String = JSONObject().put("id", id).put("from", from).put("to", to).put("lang", lang).put("source", source)
            .put("kind", kind).put("count", count).put("enabled", enabled).put("updated", updated).toString()
    }

    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pushJob: Job? = null
    @Volatile private var app: Context? = null
    @Volatile private var lastPull = 0L
    @Volatile var status: String = "Shared corrections: not synced yet."
        private set
    /** Called when other devices' rules changed, so the keyboard recomputes what it applies. */
    @Volatile var onRemoteChanged: (() -> Unit)? = null

    fun attach(c: Context) { app = c.applicationContext }

    private fun sp() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dir(): File? = app?.filesDir?.let { File(it, "corrections_sync").apply { mkdirs() } }
    private fun remoteDir(): File? = dir()?.let { File(it, "remote").apply { mkdirs() } }

    /** This phone's name in the repository, or null for an unknown device (it then reads but never writes). */
    fun source(): String? = when {
        Build.MANUFACTURER.contains("nothing", ignoreCase = true) -> "nothing"
        Build.MODEL.contains("Pixel", ignoreCase = true) -> "pixel"
        else -> null
    }

    private fun ownFile(): File? = source()?.let { s -> dir()?.let { File(it, "$s.jsonl") } }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    fun idOf(from: String, lang: String) = lang + "|" + from.lowercase(Locale.ROOT)

    // ---------------------------------------------------------------- the token, encrypted

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
        )
        return g.generateKey()
    }

    private fun saveToken(t: String): Boolean = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(t.trim().toByteArray())
        sp()?.edit()?.putString("t", Base64.encodeToString(c.iv + enc, Base64.NO_WRAP))?.apply()
        true
    }.getOrDefault(false)

    private fun ownToken(): String? = runCatching {
        val raw = Base64.decode(sp()?.getString("t", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    }.getOrNull()

    private fun req(url: String, tok: String) = Request.Builder().url(url).header("Authorization", "Bearer $tok")
        .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")

    /** True when [tok] can write to the repository. Blocking; never logs the token. */
    fun canWrite(tok: String): Boolean = runCatching {
        http.newCall(req("https://api.github.com/repos/$REPO", tok).build()).execute().use { r ->
            r.isSuccessful && JSONObject(r.body.string()).optJSONObject("permissions")?.optBoolean("push") == true
        }
    }.getOrDefault(false)

    /** From "Import keys from file": the first token that can write here is kept. A line for the import report. */
    fun offerTokens(tokens: List<String>): String {
        val i = tokens.indexOfFirst { canWrite(it) }
        if (i < 0) return "GitHub (shared corrections): no token can write to $REPO, not stored"
        saveToken(tokens[i])
        syncSoon(0)
        return "GitHub (shared corrections): token ${i + 1} can write to $REPO, stored"
    }

    private fun token(): String? {
        ownToken()?.let { return it }
        val re = runCatching { dev.patrickgold.florisboard.dictate.MaRemoteDb.readToken() }.getOrNull() ?: return null
        return if (canWrite(re)) re.also { saveToken(it) } else null
    }

    // ---------------------------------------------------------------- lines

    fun parseLines(text: String): List<Line> = text.lines().mapNotNull { l ->
        if (l.isBlank()) return@mapNotNull null
        runCatching {
            val o = JSONObject(l)
            val from = o.getString("from"); val lang = o.optString("lang", MaCorrectionRules.ANY).ifBlank { MaCorrectionRules.ANY }
            Line(o.optString("id").ifBlank { idOf(from, lang) }, from, o.getString("to"), lang, o.optString("source"),
                o.optString("kind", "learned"), o.optInt("count", 0), o.optBoolean("enabled", true), o.optString("updated"))
        }.getOrNull()
    }

    /** The last line of each id in one file: that device's state. */
    private fun lastPerId(lines: List<Line>): Map<String, Line> = LinkedHashMap<String, Line>().also { m -> lines.forEach { m[it.id] = it } }

    /** The union of every device's state (TAKEOVER.md "The merge rule"), as rules for the keyboard. Pure, tested. */
    fun merge(files: List<List<Line>>): List<MaCorrectionRules.Rule> {
        val states = files.map { lastPerId(it) }
        val ids = LinkedHashSet<String>().apply { states.forEach { addAll(it.keys) } }
        return ids.mapNotNull { id ->
            val per = states.mapNotNull { it[id] }
            val newest = per.maxByOrNull { it.updated } ?: return@mapNotNull null
            val agree = per.filter { it.to == newest.to }
            MaCorrectionRules.Rule(
                wrong = newest.from, right = newest.to, language = newest.lang,
                count = agree.sumOf { it.count }, enabled = newest.enabled,
                source = if (agree.any { it.kind == "mine" }) MaCorrectionRules.Source.MINE else MaCorrectionRules.Source.LEARNED,
            )
        }
    }

    /** Every device's file as this phone knows it: its own (the queue) and the last pulled copies of the others. */
    private fun allFiles(): List<List<Line>> {
        val own = ownFile()
        val out = ArrayList<List<Line>>()
        own?.takeIf { it.exists() }?.let { out += parseLines(it.readText()) }
        remoteDir()?.listFiles()?.filter { it.name.endsWith(".jsonl") && it.name != own?.name }?.forEach { out += parseLines(it.readText()) }
        return out
    }

    /**
     * What the keyboard applies: the union of all devices, plus the local rules that have no line yet (an unknown device
     * that never writes). Read on a background thread by MaDictationCorrections.
     */
    fun effective(local: List<MaCorrectionRules.Rule>): List<MaCorrectionRules.Rule> {
        val merged = runCatching { merge(allFiles()) }.getOrDefault(emptyList())
        if (merged.isEmpty()) return local
        val have = merged.map { idOf(it.wrong, it.language) }.toSet()
        return merged + local.filter { idOf(it.wrong, it.language) !in have }
    }

    /**
     * The local rules changed (learned, switched, added, deleted): every rule whose state differs from the last line this
     * phone wrote gets a new line; a rule that is gone gets a line with enabled false. The first run writes them all.
     */
    @Synchronized
    fun recordLocal(rules: List<MaCorrectionRules.Rule>) {
        val f = ownFile() ?: return
        val src = source() ?: return
        val mine = lastPerId(if (f.exists()) parseLines(f.readText()) else emptyList()).filterValues { it.source == src }
        val stamp = now()
        val add = ArrayList<Line>()
        val seen = HashSet<String>()
        for (r in rules) {
            val id = idOf(r.wrong, r.language)
            seen += id
            val kind = if (r.source == MaCorrectionRules.Source.MINE) "mine" else "learned"
            val old = mine[id]
            if (old == null || old.to != r.right || old.count != r.count || old.enabled != r.enabled || old.kind != kind) {
                add += Line(id, r.wrong, r.right, r.language, src, kind, r.count, r.enabled, stamp)
            }
        }
        for ((id, old) in mine) if (id !in seen && old.enabled) add += old.copy(enabled = false, updated = stamp)
        if (add.isEmpty()) return
        runCatching { f.appendText(add.joinToString("\n", postfix = "\n") { it.json() }) }
        MaLog.add("corrections", "shared: ${add.size} line(s) queued")
        syncSoon()
    }

    // ---------------------------------------------------------------- sync

    fun maybePull() {
        if (System.currentTimeMillis() - lastPull < PULL_EVERY_MS) return
        lastPull = System.currentTimeMillis()
        scope.launch { runCatching { syncNow() } }
    }

    fun syncSoon(delayMs: Long = PUSH_DELAY_MS) {
        pushJob?.cancel()
        pushJob = scope.launch { delay(delayMs); runCatching { syncNow() } }
    }

    private fun gitSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray()); md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getFile(path: String, tok: String): Pair<String, String>? =
        http.newCall(req("$API/$path?ref=main", tok).build()).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val o = JSONObject(r.body.string())
            String(Base64.decode(o.optString("content"), Base64.DEFAULT)) to o.getString("sha")
        }

    private fun put(path: String, text: String, sha: String?, tok: String): Int {
        val body = JSONObject().put("message", "TTT ${source()}: corrections").put("branch", "main")
            .put("content", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
        if (sha != null) body.put("sha", sha)
        return http.newCall(req("$API/$path", tok).put(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { it.code }
    }

    /** Lines of [a] then the lines of [b] that [a] does not have, order kept: nobody's line is ever lost. */
    private fun union(a: String, b: String): String {
        val seen = LinkedHashSet<String>()
        (a.lines() + b.lines()).map { it.trim() }.filter { it.isNotEmpty() }.forEach { seen += it }
        return seen.joinToString("\n", postfix = "\n")
    }

    suspend fun syncNow(): String = mutex.withLock {
        val tok = token() ?: return "Shared corrections: no GitHub token for $REPO (Import keys from file).".also { status = it }
        try {
            // 1. pull the other devices' files whose sha changed
            val list = http.newCall(req("$API/learned?ref=main", tok).build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                JSONArray(r.body.string())
            }
            val prefs = sp()
            var changed = false
            val own = ownFile()
            var ownRemoteSha: String? = null
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val name = o.getString("name"); val sha = o.getString("sha")
                if (!name.endsWith(".jsonl")) continue
                if (own != null && name == own.name) { ownRemoteSha = sha; continue }
                if (prefs?.getString("sha:$name", null) == sha) continue
                val (text, _) = getFile("learned/$name", tok) ?: continue
                File(remoteDir(), name).writeText(text)
                prefs?.edit()?.putString("sha:$name", sha)?.apply()
                changed = true
            }
            // 2. push this phone's file (the queue); if the repository's copy moved (a reinstall), keep the union of lines
            var pushed = 0
            if (own != null) {
                var local = if (own.exists()) own.readText() else ""
                if (ownRemoteSha != null && prefs?.getString("own_sha", null) != ownRemoteSha && ownRemoteSha != gitSha(local.toByteArray())) {
                    getFile("learned/${own.name}", tok)?.let { (remote, _) -> local = union(remote, local); own.writeText(local); changed = true }
                }
                if (local.isNotBlank() && gitSha(local.toByteArray()) != ownRemoteSha) {
                    var code = put("learned/${own.name}", local, ownRemoteSha, tok)
                    if (code == 409 || code == 422) {
                        val (remote, sha) = getFile("learned/${own.name}", tok) ?: ("" to null)
                        local = union(remote, local); own.writeText(local)
                        code = put("learned/${own.name}", local, sha, tok)
                    }
                    if (code in 200..299) { pushed = 1; prefs?.edit()?.putString("own_sha", gitSha(local.toByteArray()))?.apply() }
                    else throw java.io.IOException("push HTTP $code")
                } else if (ownRemoteSha != null) prefs?.edit()?.putString("own_sha", ownRemoteSha)?.apply()
            }
            if (changed) onRemoteChanged?.invoke()
            val n = runCatching { merge(allFiles()).count { it.isActive } }.getOrDefault(0)
            status = "Shared corrections: $n rules in use from all devices" + (if (pushed > 0) ", this phone's changes sent" else "") +
                " (" + SimpleDateFormat("HH:mm", Locale.ROOT).format(Date()) + ")"
            MaLog.add("corrections", "shared sync: $n active, pushed $pushed")
        } catch (t: Throwable) {
            status = "Shared corrections: kept on the phone, will send later (" + (t.message ?: t.javaClass.simpleName).take(60) + ")"
        }
        return status
    }
}
