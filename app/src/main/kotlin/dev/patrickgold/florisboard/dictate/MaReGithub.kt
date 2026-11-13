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
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * RE READS HIS GITHUB (Marko, 13.11.2026): "AI Magic must be aware of my Github token and in that way it can search
 * through my Github repositories, all of them private or not, and search for information if I ask it to do that."
 *
 * **The token** comes only from a key file ("Import keys from file"): a github_pat_/ghp_ token that can read is kept,
 * encrypted, in the ring [RING]. If none is there, the RE database token is tried (it may be scoped to one repository).
 * **Read only**: search code, search repositories, list repositories, read a file. Nothing here can write.
 * **How RE uses it** (any engine, Anthropic or Qwen): the model writes `<github_search>words</github_search>` or
 * `<github_read>repo/path</github_read>`; the chat runs it and gives the model the result ([MaReChat.ask]), at most
 * [MAX_LOOKS] times per answer, then it answers. The token never reaches the model or a log.
 */
object MaReGithub {
    const val RING = "github_read"
    const val MAX_LOOKS = 4
    private const val API = "https://api.github.com"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()

    fun token(c: Context): String? = EngineKeys.ownLines(c, RING).firstOrNull() ?: MaRemoteDb.readToken()

    fun available(c: Context): Boolean = token(c) != null

    @Volatile private var login: String? = null

    private fun get(url: String, tok: String, accept: String = "application/vnd.github+json"): Pair<Int, String> =
        http.newCall(
            Request.Builder().url(url).header("Authorization", "Bearer $tok").header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28").build(),
        ).execute().use { it.code to it.body.string() }

    /** The owner whose repositories RE searches (the token's user), cached. */
    private fun owner(tok: String): String? = login ?: runCatching {
        val (code, body) = get("$API/user", tok)
        if (code == 200) JSONObject(body).getString("login").also { login = it } else null
    }.getOrNull()

    /**
     * Tests a token for the key picker: "reads N repositories" or why not. Never shows the token.
     */
    suspend fun test(tok: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = get("$API/user/repos?per_page=100&visibility=all&affiliation=owner,collaborator,organization_member", tok)
            when {
                code == 200 -> { val n = JSONArray(body).length(); (n > 0) to (if (n > 0) "reads ${if (n == 100) "100+" else n.toString()} repositories" else "reads no repository") }
                code == 401 -> false to "refused"
                code == 403 -> false to "no permission to list repositories"
                else -> false to "HTTP $code"
            }
        }.getOrElse { false to "not reached" }
    }

    /** Code and repository search across everything the token can see, his private repositories included. */
    suspend fun search(c: Context, words: String): String = withContext(Dispatchers.IO) {
        val tok = token(c) ?: return@withContext "No GitHub token: import one from a key file."
        val who = owner(tok)
        val scope = if (who != null) " user:$who" else ""
        val out = StringBuilder()
        runCatching {
            val url = "$API/search/repositories".toHttpUrl().newBuilder().addQueryParameter("q", words + scope).addQueryParameter("per_page", "8").build()
            val (code, body) = get(url.toString(), tok)
            if (code == 200) {
                val items = JSONObject(body).getJSONArray("items")
                if (items.length() > 0) out.append("Repositories:\n")
                for (i in 0 until items.length()) {
                    val r = items.getJSONObject(i)
                    out.append("- ").append(r.getString("full_name")).append(if (r.optBoolean("private")) " (private)" else "")
                        .append(": ").append(r.optString("description").take(160)).append(" · pushed ").append(r.optString("pushed_at").take(10)).append("\n")
                }
            }
        }
        runCatching {
            val url = "$API/search/code".toHttpUrl().newBuilder().addQueryParameter("q", words + scope).addQueryParameter("per_page", "10").build()
            val (code, body) = get(url.toString(), tok, "application/vnd.github.text-match+json")
            if (code == 200) {
                val items = JSONObject(body).getJSONArray("items")
                if (items.length() > 0) out.append("Files:\n")
                for (i in 0 until items.length()) {
                    val r = items.getJSONObject(i)
                    out.append("- ").append(r.getJSONObject("repository").getString("full_name")).append("/").append(r.getString("path"))
                    val m = r.optJSONArray("text_matches")
                    if (m != null && m.length() > 0) out.append(": \"").append(m.getJSONObject(0).optString("fragment").replace('\n', ' ').take(220)).append("\"")
                    out.append("\n")
                }
            } else if (code == 403 || code == 422) out.append("(code search: GitHub answered $code)\n")
        }
        out.toString().ifBlank { "Nothing found for \"$words\"." }.take(6000)
    }

    /** One file ("repo/path" or "owner/repo/path"), or a repository's README and top-level files ("repo"). */
    suspend fun read(c: Context, spec: String): String = withContext(Dispatchers.IO) {
        val tok = token(c) ?: return@withContext "No GitHub token: import one from a key file."
        val parts = spec.trim().trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return@withContext "Nothing to read."
        val who = owner(tok)
        // "repo/path" (his own) or "owner/repo/path": an owner is assumed when the first part is not one of his repos.
        val (own, repo, path) = if (parts.size >= 2 && who != null && !parts[0].equals(who, true) &&
            runCatching { get("$API/repos/$who/${parts[0]}", tok).first == 200 }.getOrDefault(false)
        ) Triple(who, parts[0], parts.drop(1).joinToString("/"))
        else if (parts.size >= 2) Triple(parts[0], parts[1], parts.drop(2).joinToString("/"))
        else Triple(who ?: "", parts[0], "")
        runCatching {
            if (path.isEmpty()) {
                val (rc, readme) = get("$API/repos/$own/$repo/readme", tok, "application/vnd.github.raw")
                val (lc, list) = get("$API/repos/$own/$repo/contents", tok)
                val files = if (lc == 200) JSONArray(list).let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).getString("name") } } else "(no list: $lc)"
                "$own/$repo files: $files\n\nREADME:\n" + (if (rc == 200) readme.take(5000) else "(none)")
            } else {
                val (code, body) = get("$API/repos/$own/$repo/contents/$path", tok, "application/vnd.github.raw")
                when (code) {
                    200 -> if (body.trimStart().startsWith("[")) "Folder $own/$repo/$path: " + runCatching {
                        JSONArray(body).let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).getString("name") } }
                    }.getOrDefault(body.take(500)) else "$own/$repo/$path:\n" + body.take(6000)
                    404 -> "Not found: $own/$repo/$path"
                    else -> "GitHub answered $code for $own/$repo/$path"
                }
            }
        }.getOrElse { "Could not reach GitHub." }
    }

    /** What the model is told when a token is there. */
    const val TOOLS = "\n\nYou can look into Marko's GitHub, all his repositories, private ones too, read only. Use it when he asks " +
        "about his projects, repositories, code, notes, takeovers or lessons there, or says GitHub. To search, answer with ONLY " +
        "<github_search>a few words</github_search>. To read a file, answer with ONLY <github_read>REPO/PATH</github_read> " +
        "(REPO alone gives its README and files). You then get the result and can look again, at most 4 times, then answer him " +
        "normally and say which repository and file the answer came from. Never invent what a repository contains."

    private val SEARCH = Regex("<github_search>([\\s\\S]*?)</github_search>", RegexOption.IGNORE_CASE)
    private val READ = Regex("<github_read>([\\s\\S]*?)</github_read>", RegexOption.IGNORE_CASE)

    /** The look the model asked for, or null when the answer is final. */
    fun request(answer: String): Pair<String, String>? {
        SEARCH.find(answer)?.let { return "search" to it.groupValues[1].trim() }
        READ.find(answer)?.let { return "read" to it.groupValues[1].trim() }
        return null
    }
}
