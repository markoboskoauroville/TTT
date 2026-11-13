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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
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
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SYNC SETTINGS BETWEEN DEVICES (Marko, 13.11.2026, through the Remote Viewing session): *"make github repository ...
 * TTT settings. It's private. Inside what lives is these settings. And when I run TTT it checks if something is new and
 * then all devices are always updating ... when I change any setting in my TTT, it pushes it to my Git repository
 * because it has my key ... Write your repository when you want me to synchronize settings between devices. And then
 * they can put their own API if they have it ... this is disabled by default."*
 *
 * **Off by default.** On: a repository (owner/name, private) and a GitHub token that can write to it. The token
 * enters only through a file (Import keys from file), is stored encrypted, never shown, never logged; a token
 * already in TTT (RE's) is used when it can write there.
 *
 * **What travels.** Every TTT preference he changed from its default (the same file Backup writes: one
 * `type;key;value` line each), as `ttt_settings.json` in the repository, with the time and the phone that wrote it.
 * Never: API keys, passwords, tokens, proxy, provider accounts, the last dictation, counters, "seen" marks,
 * migrations, versions, internal state ([private]). Keys, history and clips never leave the phone this way.
 *
 * **When.** Pull when TTT starts and when the keyboard opens (at most once a minute); push a few seconds after a
 * setting changed (checked when the keyboard closes and when Settings is left). Both changed since the last sync:
 * the newer one wins, by time. A phone that syncs for the first time takes the repository's settings if there are
 * any (so a new phone gets the layout; it never overwrites the repository with its own old one); an empty
 * repository gets this phone's settings.
 */
object MaSettingsSync {
    private const val PREFS = "ttt_settings_sync"
    private const val ALIAS = "ttt_settings_sync_token_key"
    const val FILE = "ttt_settings.json"
    private const val PULL_EVERY_MS = 60_000L
    private const val PUSH_DELAY_MS = 4_000L

    /** Never synced: secrets, per-phone state, counters, marks of what was seen or migrated. */
    private val PRIVATE = Regex(
        "^(internal__|devtools__)|api_key|apikey|password|token|secret|proxy|provider_accounts|last_dictation|" +
            "_migrated|migrated_|_seen|seen_|_version$|version_|stats_|_last_|last_send|_epoch|_ms$|cursor_row|" +
            "settings_search_history|notification_permission|is_ime_set_up|open_floating_button_after_setup",
    )

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private var app: Context? = null
    private var lastPullAt = 0L
    private var pushJob: Job? = null

    @Volatile var status: String = ""
        private set

    fun attach(context: Context) { app = context.applicationContext }

    private fun sp() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp()?.getBoolean("on", false) == true
        set(v) { sp()?.edit()?.putBoolean("on", v)?.apply(); if (v) syncSoon(0) }

    /** "owner/name", as he wrote it. */
    var repo: String
        get() = sp()?.getString("repo", "").orEmpty()
        set(v) {
            val r = v.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
            if (r != repo) sp()?.edit()?.putString("repo", r)?.remove("remote_sha")?.remove("synced_hash")?.remove("dirty_since")?.apply()
        }

    fun validRepo(r: String = repo) = Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""").matches(r)

    // ---------------------------------------------------------------- the token, encrypted

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
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

    fun hasOwnToken(): Boolean = sp()?.getString("t", null) != null

    fun clearToken() { sp()?.edit()?.remove("t")?.apply(); status = "Token deleted." }

    /** True when [tok] can write to [r]. Blocking; never logs the token. */
    fun canWrite(tok: String, r: String = repo): Boolean = validRepo(r) && runCatching {
        http.newCall(req("https://api.github.com/repos/$r", tok).build()).execute().use { res ->
            res.isSuccessful && JSONObject(res.body.string()).optJSONObject("permissions")?.optBoolean("push") == true
        }
    }.getOrDefault(false)

    /**
     * From the key file picker (KeyImporter): the first of [tokens] that can write to the sync repository is kept.
     * Returns a line for the import report, or null when no repository is set.
     */
    fun offerTokens(tokens: List<String>): String? {
        if (!validRepo()) return null
        val i = tokens.indexOfFirst { canWrite(it) }
        if (i < 0) return "GitHub (settings sync): no token can write to $repo, not stored"
        saveToken(tokens[i])
        syncSoon(0)
        return "GitHub (settings sync): token ${i + 1} can write to $repo, stored"
    }

    /** The token for the repository: its own, else RE's when that one can write there. */
    private fun token(): String? {
        ownToken()?.let { return it }
        val re = runCatching { MaRemoteDb.readToken() }.getOrNull() ?: return null
        return if (canWrite(re)) re.also { saveToken(it) } else null
    }

    private fun req(url: String, tok: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $tok")
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")

    // ---------------------------------------------------------------- the settings, as lines

    /** This phone's settings that travel: sorted `type;key;value` lines without the private ones. */
    private suspend fun localLines(c: Context): String {
        val f = File(c.cacheDir, "settings_sync_out.jetpref")
        FlorisPreferenceStore.export(FileBasedStorage(f.path)).getOrThrow()
        val text = f.readText()
        f.delete()
        return text.lines().filter { it.isNotBlank() && travels(it) }.sorted().joinToString("\n")
    }

    private fun travels(line: String): Boolean {
        val key = line.split(';').getOrNull(1) ?: return false
        return !PRIVATE.containsMatchIn(key)
    }

    private suspend fun apply(c: Context, lines: String) {
        val f = File(c.cacheDir, "settings_sync_in.jetpref")
        f.writeText(lines.lines().filter { it.isNotBlank() && travels(it) }.joinToString("\n", postfix = "\n"))
        FlorisPreferenceStore.import(ImportStrategy.Merge, FileBasedStorage(f.path)).getOrThrow()
        f.delete()
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    // ---------------------------------------------------------------- sync

    /** TTT started or the keyboard opened: look for newer settings, at most once a minute. */
    fun maybePull() {
        if (!enabled || System.currentTimeMillis() - lastPullAt < PULL_EVERY_MS) return
        lastPullAt = System.currentTimeMillis()
        scope.launch { syncNow() }
    }

    /** A setting may have changed (keyboard closed, Settings left): push soon, one burst one push. */
    fun syncSoon(delayMs: Long = PUSH_DELAY_MS) {
        if (!enabled) return
        pushJob?.cancel()
        pushJob = scope.launch { delay(delayMs); syncNow() }
    }

    /** One full sync; returns the line shown in Settings. */
    suspend fun syncNow(): String = mutex.withLock {
        val c = app ?: return "Not ready."
        status = runCatching { doSync(c) }.getOrElse { "Sync failed: ${it.message ?: it.javaClass.simpleName}" }
        android.util.Log.i("TTT-SYNC", status)
        status
    }

    private suspend fun doSync(c: Context): String {
        if (!enabled) return "Off."
        if (!validRepo()) return "Write the repository as owner/name."
        val tok = token() ?: return "No GitHub token that can write to $repo. Put one in a text file and use Import keys from file."
        val s = sp() ?: return "Not ready."
        val api = "https://api.github.com/repos/$repo/contents/$FILE"

        // The repository's copy.
        var remoteSha: String? = null
        var remote: JSONObject? = null
        http.newCall(req(api, tok).build()).execute().use { r ->
            if (r.code != 404) {
                if (!r.isSuccessful) error("GitHub answered ${r.code}")
                val o = JSONObject(r.body.string())
                remoteSha = o.getString("sha")
                remote = JSONObject(String(Base64.decode(o.getString("content").replace("\n", ""), Base64.DEFAULT)))
            }
        }

        val local = localLines(c)
        val localHash = hash(local)
        val synced = s.getString("synced_hash", null)
        val firstTime = synced == null
        val localChanged = !firstTime && localHash != synced
        val remoteChanged = remoteSha != null && remoteSha != s.getString("remote_sha", null)
        val now = System.currentTimeMillis()
        if (localChanged && s.getLong("dirty_since", 0L) == 0L) s.edit().putLong("dirty_since", now).apply()
        val dirtySince = s.getLong("dirty_since", 0L).takeIf { it > 0 } ?: now

        val pull = remote != null && remoteChanged && (firstTime || !localChanged || remote!!.optLong("updated") >= dirtySince)
        if (pull) {
            val lines = remote!!.optString("settings")
            if (hash(lines) != localHash) apply(c, lines)
            val after = hash(localLines(c))
            s.edit().putString("remote_sha", remoteSha).putString("synced_hash", after).putLong("dirty_since", 0L).apply()
            return "Took the settings from ${remote!!.optString("device", "another phone")} (${stamp(remote!!.optLong("updated"))})."
        }
        if (remote == null || localChanged || (firstTime && remote == null)) {
            if (remote != null && !localChanged) return "Up to date."
            val body = JSONObject()
                .put("updated", now)
                .put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                .put("app", runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "")
                .put("settings", local)
            val put = JSONObject().put("message", "TTT settings from ${android.os.Build.MODEL}")
                .put("content", Base64.encodeToString(body.toString(1).toByteArray(), Base64.NO_WRAP))
            if (remoteSha != null) put.put("sha", remoteSha)
            http.newCall(req(api, tok).put(put.toString().toRequestBody(JSON)).build()).execute().use { r ->
                if (!r.isSuccessful) error("GitHub answered ${r.code} to the upload")
                val sha = JSONObject(r.body.string()).getJSONObject("content").getString("sha")
                s.edit().putString("remote_sha", sha).putString("synced_hash", localHash).putLong("dirty_since", 0L).apply()
            }
            return "Sent this phone's settings to $repo (${local.lines().size} settings)."
        }
        s.edit().putString("remote_sha", remoteSha).apply()
        return "Up to date."
    }

    private fun stamp(ms: Long): String =
        if (ms <= 0) "?" else java.text.SimpleDateFormat("d.M. HH:mm", java.util.Locale.ROOT).format(java.util.Date(ms))
}
