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
import android.util.Base64
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.provider.MaClaudeModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * THE HELP CHAT IN SETTINGS SEARCH (Marko, 13.11.2026): "in the search add the prompt box which is using Anthropic
 * Claude to answer the questions ... a data repository ... private ... logging all my questions ... I will explain how
 * TTT works".
 *
 * Claude (Sonnet) answers questions about TTT from: the guide in assets/ttt_help (GUIDE.md, the benchmark, the provider
 * catalogue), KNOWLEDGE.md from the data repository (what Marko taught it), the last turns of this chat, and, when a
 * GitHub token is there, the TTT source itself (the same read-only looks RE uses, [MaReGithub]).
 *
 * **Teaching.** When Marko explains something, Claude puts each fact in `<learn>...</learn>`; those lines are added to
 * KNOWLEDGE.md in the repository, so every later answer knows them.
 *
 * **The log.** Every question and answer goes to qa/YYYY-MM-DD.md in the private repository [REPO]. If that repository
 * does not exist (or the token cannot reach it), it goes to [FALLBACK_REPO] under ttt-help/, which is private too.
 * The chat is also kept on the phone (files/ttt_help/chat.json), so it continues where it was left.
 */
object MaHelpChat {

    const val REPO = "markoboskoauroville/TTT_DATA"
    const val FALLBACK_REPO = MaRemoteDb.REPO
    private const val FALLBACK_DIR = "ttt-help/"
    /** The models the badge steps through, in tap order (Marko, 13.11.2026: "just switch to Sonnet, Opus"). */
    val TIERS = listOf("sonnet", "opus", "haiku")

    /** [error]: an answer that is a failure, drawn as its own fix (MaFixMessage), never sent back to the model. */
    data class Turn(val me: Boolean, val text: String, val at: Long = System.currentTimeMillis(), val error: Boolean = false)

    private fun tierPrefs(c: Context) = c.getSharedPreferences("ttt_help", Context.MODE_PRIVATE)
    fun tier(c: Context): String = tierPrefs(c).getString("tier", "sonnet")?.takeIf { it in TIERS } ?: "sonnet"
    fun setTier(c: Context, t: String) { tierPrefs(c).edit().putString("tier", t).apply() }
    fun model(c: Context): String = MaClaudeModels.resolve(MaClaudeModels.alias(tier(c)), emptyList())
    /** What the badge says: "Claude Sonnet 5.5". */
    fun modelLabel(c: Context): String = MaClaudeModels.displayName(model(c))

    private val prefs by FlorisPreferenceStore
    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()
    private val LEARN = Regex("<learn>([\\s\\S]*?)</learn>", RegexOption.IGNORE_CASE)

    @Volatile
    private var knowledgeCache: String? = null

    // ---------------------------------------------------------------- the chat on the phone

    private fun chatFile(c: Context) = File(File(c.filesDir, "ttt_help").apply { mkdirs() }, "chat.json")

    fun load(c: Context): List<Turn> = runCatching {
        val a = JSONArray(chatFile(c).readText())
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Turn(o.getBoolean("me"), o.getString("t"), o.getLong("at"), o.optBoolean("err")) } }
    }.getOrDefault(emptyList())

    fun save(c: Context, turns: List<Turn>) {
        runCatching {
            val a = JSONArray()
            turns.takeLast(200).forEach { a.put(JSONObject().put("me", it.me).put("t", it.text).put("at", it.at).put("err", it.error)) }
            chatFile(c).writeText(a.toString())
        }
    }

    fun clear(c: Context) {
        runCatching { chatFile(c).delete() }
    }

    // ---------------------------------------------------------------- asking

    private fun asset(c: Context, name: String): String =
        runCatching { c.assets.open("ttt_help/$name").bufferedReader().use { it.readText() } }.getOrDefault("")

    private fun anthropicKey(): String {
        val account = prefs.dictate.providerAccounts.get().getOrEmpty("anthropic")
        return MaKeyRingStore.currentKey("anthropic", account.apiKey)
    }

    private fun system(c: Context, knowledge: String, github: Boolean): String = buildString {
        append("You are the help assistant of TTT (Talk to Type), Marko's Android dictation keyboard, inside its Settings ")
        append("search. Answer his questions about TTT: what a setting or key does, where to find it, which engine or model ")
        append("is better and why, and general background (the companies, the models, their history) when he asks. Be ")
        append("concrete, short paragraphs, plain text (no tables). Name the Settings screen or key when it helps. When the ")
        append("material below does not say something about TTT, say so instead of guessing; general knowledge is fine for ")
        append("general questions. Answer in the language he writes in.\n")
        append("When Marko EXPLAINS how TTT works or corrects you, keep each new fact as one line inside <learn>...</learn> ")
        append("(several tags allowed) and also answer him normally; those lines are saved to KNOWLEDGE.md for later.\n")
        if (github) append(MaReGithub.TOOLS).append(" The TTT source is the repository markoboskoauroville/TTT.\n")
        append("\n<guide>\n").append(asset(c, "GUIDE.md")).append("\n</guide>\n")
        if (knowledge.isNotBlank()) append("\n<what_marko_taught_you>\n").append(knowledge.takeLast(20000)).append("\n</what_marko_taught_you>\n")
        append("\n<benchmark>\n").append(asset(c, "benchmark.md").take(8000)).append("\n</benchmark>\n")
        append("\n<providers>\n").append(asset(c, "stt-providers.md").take(9000)).append("\n</providers>\n")
        append("\nToday is ").append(SimpleDateFormat("d.M.yyyy", Locale.ROOT).format(Date())).append(".")
    }

    private fun claude(model: String, key: String, system: String, turns: List<Turn>, extra: String?): String {
        val msgs = JSONArray()
        // The API wants alternating roles starting with the user; merge neighbours of the same side.
        val merged = mutableListOf<Pair<Boolean, String>>()
        for (t in turns.filterNot { it.error }.takeLast(30)) {
            if (merged.isNotEmpty() && merged.last().first == t.me) {
                merged[merged.size - 1] = t.me to (merged.last().second + "\n\n" + t.text)
            } else merged += t.me to t.text
        }
        while (merged.isNotEmpty() && !merged.first().first) merged.removeAt(0)
        merged.forEachIndexed { i, (me, text) ->
            val content = if (i == merged.lastIndex && me && extra != null) "$text\n\n$extra" else text
            msgs.put(JSONObject().put("role", if (me) "user" else "assistant").put("content", content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2000)
            .put("system", system)
            .put("messages", msgs)
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key).header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) error(
                when (r.code) {
                    401, 403 -> "The Anthropic key was refused"
                    429 -> "Anthropic is busy or the limit is reached"
                    400 -> if (text.contains("credit", true)) "The Anthropic key is out of credit" else "Anthropic answered 400: " + text.take(160)
                    404 -> "This model is not available to the Anthropic key"
                    else -> "Anthropic answered ${r.code}"
                },
            )
            val content = JSONObject(text).getJSONArray("content")
            return (0 until content.length()).map { content.getJSONObject(it) }
                .filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
        }
    }

    /**
     * Answers the last question in [turns]. Returns the answer as shown (the <learn> tags taken out). Throws with a
     * plain message when there is no key or Anthropic refuses.
     */
    suspend fun ask(c: Context, turns: List<Turn>, status: (String) -> Unit): String = withContext(Dispatchers.IO) {
        MaRemoteDb.attach(c)
        val key = anthropicKey()
        if (key.isBlank()) error("No Anthropic key")
        status("Reading what you taught me")
        val knowledge = knowledgeCache ?: (pull("KNOWLEDGE.md") ?: "").also { knowledgeCache = it }
        val github = MaReGithub.available(c)
        val sys = system(c, knowledge, github)
        val looks = StringBuilder()
        var round = 0
        var answer: String
        while (true) {
            status(if (round == 0) "Claude is thinking" else "Claude is reading the TTT code")
            val extra = if (looks.isEmpty()) null else "What you looked up so far:\n$looks" +
                (if (round >= MaReGithub.MAX_LOOKS) "\nNo more looks: answer now." else "")
            answer = claude(model(c), key, sys, turns, extra).trim()
            val req = if (github && round < MaReGithub.MAX_LOOKS) MaReGithub.request(answer) else null
            if (req == null) break
            round++
            val (kind, what) = req
            status(if (kind == "search") "Searching GitHub: $what" else "Reading $what")
            val result = if (kind == "search") MaReGithub.search(c, what) else MaReGithub.read(c, what)
            looks.append("<").append(kind).append(" q=\"").append(what.take(120)).append("\">\n")
                .append(result.take(12000)).append("\n</").append(kind).append(">\n")
        }
        val learned = LEARN.findAll(answer).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        val shown = answer.replace(LEARN, "").replace(Regex("\\n{3,}"), "\n\n").trim()
        status("Saving to the data repository")
        val question = turns.lastOrNull { it.me }?.text.orEmpty()
        val saved = record(question, shown, learned)
        status(saved)
        if (learned.isNotEmpty()) shown + "\n\n(Kept: " + learned.joinToString("; ") + ")" else shown
    }

    // ---------------------------------------------------------------- the private data repository

    private fun token(): String? = MaRemoteDb.readToken()

    private fun req(url: String, tok: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $tok").header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    /** Where the files go: TTT_DATA when the token reaches it, else RE_MAGIC_DATABASE/ttt-help/. */
    @Volatile
    private var target: Pair<String, String>? = null

    private fun target(tok: String): Pair<String, String> = target ?: run {
        val ok = runCatching {
            http.newCall(req("https://api.github.com/repos/$REPO", tok).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        (if (ok) REPO to "" else FALLBACK_REPO to FALLBACK_DIR).also { target = it }
    }

    private fun getFile(repo: String, path: String, tok: String): Pair<String, String>? =
        http.newCall(req("https://api.github.com/repos/$repo/contents/$path", tok).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val o = JSONObject(r.body.string())
            String(Base64.decode(o.getString("content"), Base64.DEFAULT)) to o.getString("sha")
        }

    private fun putFile(repo: String, path: String, text: String, sha: String?, tok: String, message: String): Boolean {
        val body = JSONObject().put("message", message)
            .put("content", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
        if (sha != null) body.put("sha", sha)
        return http.newCall(req("https://api.github.com/repos/$repo/contents/$path", tok)
            .put(body.toString().toRequestBody(JSON)).build()).execute().use { it.isSuccessful }
    }

    private fun pull(name: String): String? = runCatching {
        val tok = token() ?: return null
        val (repo, dir) = target(tok)
        getFile(repo, dir + name, tok)?.first
    }.getOrNull()

    /** Appends to a file in the repository, retrying once when someone else wrote it in between. */
    private fun append(name: String, text: String, message: String): Boolean {
        val tok = token() ?: return false
        val (repo, dir) = target(tok)
        repeat(2) {
            val old = runCatching { getFile(repo, dir + name, tok) }.getOrNull()
            val now = (old?.first ?: "").let { if (it.isEmpty() || it.endsWith("\n")) it else it + "\n" } + text
            if (runCatching { putFile(repo, dir + name, now, old?.second, tok, message) }.getOrDefault(false)) return true
        }
        return false
    }

    private fun record(question: String, answer: String, learned: List<String>): String {
        if (token() == null) return "Not saved: no GitHub token (import it with \"Import keys from file\")"
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
        val entry = "\n## $time\n\n**Q:** ${question.trim()}\n\n**A:** ${answer.trim()}\n"
        val okQa = append("qa/$day.md", entry, "TTT help: question $day $time")
        if (learned.isNotEmpty()) {
            val lines = learned.joinToString("") { "- $it ($day)\n" }
            if (append("KNOWLEDGE.md", lines, "TTT help: Marko taught ${learned.size} fact(s)")) {
                knowledgeCache = (knowledgeCache ?: "") + "\n" + lines
            }
        }
        val (repo, dir) = target ?: (REPO to "")
        return if (okQa) "Saved to $repo/${dir}qa/$day.md" else "Not saved: GitHub refused (the token needs write access to $repo)"
    }
}
