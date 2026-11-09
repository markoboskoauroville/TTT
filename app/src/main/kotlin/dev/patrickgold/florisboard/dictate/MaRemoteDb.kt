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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import kotlin.jvm.Volatile

/**
 * RE MAGIC DATABASE: RE'S MEMORY IN A PRIVATE GITHUB REPOSITORY, MIRRORED ON THE PHONE (Marko, 9.11.2026).
 *
 * "RE has its own repository on GitHub called RE Magic Database and stores its data there, in a private repo."
 * The repository markoboskoauroville/RE_MAGIC_DATABASE holds the same paths as Documents/TTTmini/ on the phone:
 * `re_ttt_skill.md`, `dictation_corrections.json`, `dictation_learned.json`, `re_corrections.jsonl`,
 * `skills/style.md`, `skills/people/`, `skills/threads/`, `skills/projects/` (small text files only).
 *
 * **Sync** ([syncNow]): pull on start and now and then ([maybePull]), push after each conversation update
 * ([syncSoon]). Per file, with the last agreed git blob hash remembered: only the phone changed = push; only the
 * repository changed = pull; both changed = KEEP BOTH (the repository's copy is saved beside it as
 * NAME.conflict-<time>) and the phone's copy, the newest edit, is pushed. Each pushed file is one commit.
 *
 * **Token.** A fine-grained token scoped to ONLY this repository (Contents read and write), created and pasted by
 * Marko on the phone. It is stored encrypted with an Android Keystore key, shown masked, deletable, and is never
 * written to a log, a prompt, a file in Documents or this repository.
 */
object MaRemoteDb {

    const val REPO = "markoboskoauroville/RE_MAGIC_DATABASE"
    const val BRANCH = "main"
    private const val API = "https://api.github.com/repos/$REPO"
    private const val ALIAS = "re_db_token_key"
    private const val PREFS = "re_db"

    /** The GitHub page where Marko makes the token, with what can be filled in already. */
    const val TOKEN_PAGE = "https://github.com/settings/personal-access-tokens/new" +
        "?name=RE%20Magic%20Database&description=TTT%20keyboard%20RE%20Magic%20memory&target_name=markoboskoauroville" +
        "&expires_in=90&contents=write"

    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val JSON = "application/json".toMediaType()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var status: String = "Not synced yet."
        private set

    @Volatile
    private var lastPullAt = 0L

    @Volatile
    private var pending = false

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    // ---------------------------------------------------------------- the token, encrypted

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    fun saveToken(token: String): Boolean = runCatching {
        val t = token.trim()
        if (t.isEmpty()) return false
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(t.toByteArray())
        prefs()?.edit()?.putString("t", Base64.encodeToString(c.iv + enc, Base64.NO_WRAP))?.apply()
        true
    }.getOrDefault(false)

    fun clearToken() {
        prefs()?.edit()?.remove("t")?.apply()
        status = "Token deleted."
    }

    fun hasToken(): Boolean = prefs()?.getString("t", null) != null

    private fun token(): String? = runCatching {
        val raw = Base64.decode(prefs()?.getString("t", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    }.getOrNull()

    // ---------------------------------------------------------------- which files

    /** Paths that belong in the repository (relative to Documents/TTTmini). */
    fun synced(path: String): Boolean =
        path.startsWith("re_ttt_skill") || path.startsWith("dictation_corrections") || path.startsWith("dictation_learned") ||
            path.startsWith("re_corrections") || (path.startsWith("skills/") && (path.endsWith(".md")))

    private fun localFiles(): Map<String, File> {
        val root = MaVault.dir()
        return runCatching {
            root.walkTopDown().filter { it.isFile && it.length() < 400_000 }
                .map { it.relativeTo(root).path.replace(File.separatorChar, '/') to it }
                .filter { synced(it.first) }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** The git blob hash of [bytes], what the GitHub tree API reports. */
    fun gitSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray())
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun stateFile(): File? = appContext?.filesDir?.let { File(File(it, "re"), "db_state.json") }

    private fun loadState(): JSONObject = runCatching { JSONObject(stateFile()!!.readText()) }.getOrDefault(JSONObject())

    private fun saveState(o: JSONObject) {
        runCatching { stateFile()?.let { it.parentFile?.mkdirs(); it.writeText(o.toString()) } }
    }

    // ---------------------------------------------------------------- GitHub calls

    private fun req(url: String, tok: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $tok")
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")

    private fun remoteTree(tok: String): Map<String, String> {
        http.newCall(req("$API/git/trees/$BRANCH?recursive=1", tok).build()).execute().use { r ->
            if (r.code == 404) return emptyMap()
            if (!r.isSuccessful) error("GitHub answered ${r.code}")
            val arr = JSONObject(r.body.string()).getJSONArray("tree")
            val out = HashMap<String, String>()
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                if (n.optString("type") == "blob" && synced(n.getString("path"))) out[n.getString("path")] = n.getString("sha")
            }
            return out
        }
    }

    private fun download(path: String, tok: String): ByteArray? {
        http.newCall(req("$API/contents/$path?ref=$BRANCH", tok).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            return Base64.decode(JSONObject(r.body.string()).getString("content").replace("\n", ""), Base64.DEFAULT)
        }
    }

    private fun upload(path: String, bytes: ByteArray, remoteSha: String?, tok: String): String? {
        val body = JSONObject().put("message", "RE: update $path").put("branch", BRANCH)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        if (remoteSha != null) body.put("sha", remoteSha)
        http.newCall(req("$API/contents/$path", tok).put(body.toString().toRequestBody(JSON)).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            return JSONObject(r.body.string()).getJSONObject("content").getString("sha")
        }
    }

    // ---------------------------------------------------------------- the sync

    /** Pulls and pushes as described above; returns a one line result (also kept in [status]). */
    fun syncNow(): String {
        val tok = token() ?: return "No token: paste it in Settings, RE Magic, RE Magic Database.".also { status = it }
        val result = runCatching { doSync(tok) }.getOrElse { "Sync failed: ${it.javaClass.simpleName} ${it.message?.take(80).orEmpty()}" }
        status = result
        MaLog.add("db", result)
        return result
    }

    private fun doSync(tok: String): String {
        val remote = remoteTree(tok)
        val local = localFiles()
        val state = loadState()
        val root = MaVault.dir()
        var pulled = 0
        var pushed = 0
        var kept = 0
        for (path in (remote.keys + local.keys).toSortedSet()) {
            val l = local[path]
            val lsha = l?.let { gitSha(it.readBytes()) }
            val r = remote[path]
            val s = state.optString(path, "")
            when {
                l == null && r != null -> {
                    val b = download(path, tok) ?: continue
                    File(root, path).also { it.parentFile?.mkdirs() }.writeBytes(b)
                    state.put(path, r); pulled++
                }
                l != null && r == null -> {
                    val n = upload(path, l.readBytes(), null, tok) ?: continue
                    state.put(path, n); pushed++
                }
                lsha == r -> state.put(path, r)
                else -> {
                    val localChanged = lsha != s
                    val remoteChanged = r != s
                    if (localChanged && !remoteChanged) {
                        val n = upload(path, l!!.readBytes(), r, tok) ?: continue
                        state.put(path, n); pushed++
                    } else if (!localChanged && remoteChanged) {
                        val b = download(path, tok) ?: continue
                        l!!.writeBytes(b); state.put(path, r!!); pulled++
                    } else {
                        // both changed: keep both, the phone's copy (the newest edit) wins the original name
                        val b = download(path, tok) ?: continue
                        val stamp = System.currentTimeMillis() / 1000
                        val dot = path.lastIndexOf('.')
                        val alt = if (dot > 0) path.substring(0, dot) + ".conflict-$stamp" + path.substring(dot) else "$path.conflict-$stamp"
                        File(root, alt).also { it.parentFile?.mkdirs() }.writeBytes(b)
                        val n = upload(path, l!!.readBytes(), r, tok) ?: continue
                        state.put(path, n); kept++; pushed++
                    }
                }
            }
        }
        saveState(state)
        return "Synced with RE Magic Database: $pulled pulled, $pushed pushed" + (if (kept > 0) ", $kept conflicts kept as copies" else "") + "."
    }

    /** Pull now and then: at most every 20 minutes. */
    fun maybePull() {
        if (!hasToken() || System.currentTimeMillis() - lastPullAt < 20 * 60_000L) return
        lastPullAt = System.currentTimeMillis()
        scope.launch { syncNow() }
    }

    /** Push after a conversation update: debounced so one burst is one sync. */
    fun syncSoon() {
        if (!hasToken() || pending) return
        pending = true
        scope.launch {
            delay(15_000L)
            pending = false
            syncNow()
        }
    }
}
