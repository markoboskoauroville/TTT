/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.engines.EngineFailure
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import dev.patrickgold.florisboard.dictate.engines.FailKind
import dev.patrickgold.florisboard.dictate.engines.FallbackRunner
import dev.patrickgold.florisboard.dictate.engines.KeyRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * THE RE ENGINE (Marko, 13.11.2026): exactly four choices, chosen by him in the RE key's long-press panel or Settings,
 * never switched automatically.
 *
 * - **Anthropic Auto**: the model router, as in his MANTRA_ROUTER mod: Haiku judges each request and picks Sonnet or
 *   Opus by complexity; when unsure, the higher one (the router's own rule).
 * - **Opus** / **Sonnet**: that model, always.
 * - **Qwen**: Groq's Qwen, the larger one, as in his Flask app (groq_ai_studio, model_router.py: qwen/qwen3.8-27b
 *   "the stronger and cheaper of the two"; no OpenAI models: "please don't use anything from open AI"). Picked from
 *   Groq's live model list (largest Qwen first) so a newer, larger Qwen is used when Groq offers one. RE's Qwen has
 *   its OWN key ring ([RING]), from its own file picker, separate from every other Groq use in TTT.
 *
 * Qwen reads the same memory files Anthropic writes (TAKEOVER, LESSONS, SKILLS, people). What his Flask lessons taught
 * about Qwen is applied here: its `<think>` block is hidden and stripped, it gets an output cap with room to think, and
 * it gets a worked example, because "rules are weaker than an example" (groq_ai_studio LESSONS 12.11.2026).
 */
object MaReEngine {
    private val prefs by FlorisPreferenceStore

    /** True while an RE instruction is being applied; the one flag requestRewordRaw reads. */
    @Volatile var inRe = false

    const val AUTO = "anthropic_auto"
    const val OPUS = "opus"
    const val SONNET = "sonnet"
    const val QWEN = "qwen"

    /** Older names, still accepted from an older install. */
    const val ANTHROPIC = AUTO
    const val GROQ = QWEN

    const val OPUS_MODEL = "claude-opus-5-5"
    const val SONNET_MODEL = "claude-sonnet-5-5"
    const val JUDGE_MODEL = "claude-haiku-5-5"
    const val QWEN_FALLBACK = "qwen/qwen3.8-27b"

    /** RE's own Groq key ring: nothing else in TTT reads it, and RE's Qwen reads nothing else. */
    const val RING = "re_qwen"

    /** The comparison harness (MaReCompare, adb only) sets this for one run; nothing else does. */
    @Volatile var override: String? = null

    fun engine(): String = override ?: when (val e = prefs.dictate.maReEngine.get()) {
        AUTO, OPUS, SONNET, QWEN -> e
        "groq" -> QWEN
        else -> AUTO
    }

    fun isQwen() = engine() == QWEN

    /** The model Auto chose last (for the chat's status line). */
    @Volatile var lastChoice: String = ""

    fun label(): String = when (engine()) {
        QWEN -> "Qwen"
        OPUS -> "Opus"
        SONNET -> "Sonnet"
        else -> "Auto"
    }

    /** One letter on the RE key. */
    fun letter(e: String = engine()): String = when (e) { QWEN -> "Q"; OPUS -> "O"; SONNET -> "S"; else -> "A" }

    // ------------------------------------------------------------------ Anthropic: Auto, Opus, Sonnet

    private const val JUDGE = "You route one request for RE, a writing assistant inside a phone keyboard, to the cheaper model " +
        "that will do it well. sonnet: an ordinary short message, a reply, a fix of spelling or tone, shorter/longer, a " +
        "translation, a simple question. opus: a long or delicate message (conflict, money, apology, a decision), several " +
        "people or threads at once, anything that needs the whole conversation understood, a request that is ambiguous, " +
        "writing memory files. When unsure, pick opus. Reply with one word: sonnet or opus."

    /** The Anthropic model for this request: fixed, or (Auto) judged by Haiku from the request itself. */
    suspend fun anthropicModel(c: Context, request: String, memoryWork: Boolean = false): String = when (engine()) {
        OPUS -> OPUS_MODEL
        SONNET -> SONNET_MODEL
        else -> {
            val tier = if (memoryWork) "opus" else runCatching {
                DictateController.reChatRaw(c, JUDGE + "\n\n<request>\n" + request.takeLast(3000) + "\n</request>", JUDGE_MODEL)
            }.getOrNull()?.lowercase().orEmpty()
            (if (tier.contains("sonnet") && !tier.contains("opus")) SONNET_MODEL else OPUS_MODEL).also { lastChoice = it }
        }
    }

    // ------------------------------------------------------------------ Qwen on Groq, its own keys

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    private val JSONT = "application/json".toMediaType()

    fun hasQwenKey(c: Context): Boolean = EngineKeys.ownLines(c, RING).isNotEmpty()

    /** Live: GET /models with [key]. "works" (200), "refused" (401/403), or what went wrong. Never shows the key. */
    suspend fun testQwenKey(key: String): String = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("https://api.groq.com/openai/v1/models").header("Authorization", "Bearer $key").build())
                .execute().use { r ->
                    when {
                        r.isSuccessful -> "works"
                        r.code == 401 || r.code == 403 -> "does not work (refused)"
                        r.code == 429 -> "works (rate-limited right now)"
                        else -> "does not work (HTTP ${r.code})"
                    }
                }
        }.getOrElse { "not reached (no internet?)" }
    }

    /** The larger Qwen Groq offers now: most parameters, then the newest version; cached for a day. */
    suspend fun qwenModel(c: Context): String {
        val p = c.applicationContext.getSharedPreferences("ma_re_engine", Context.MODE_PRIVATE)
        val at = p.getLong("qwen_at", 0L)
        p.getString("qwen_model", null)?.let { if (System.currentTimeMillis() - at < 86_400_000L) return it }
        val key = EngineKeys.ownLines(c, RING).firstOrNull() ?: return QWEN_FALLBACK
        val ids = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("https://api.groq.com/openai/v1/models").header("Authorization", "Bearer $key").build())
                    .execute().use { r ->
                        if (!r.isSuccessful) return@use emptyList<String>()
                        val a = JSONObject(r.body.string()).getJSONArray("data")
                        (0 until a.length()).map { a.getJSONObject(it).getString("id") }
                    }
            }.getOrDefault(emptyList())
        }
        val best = pickQwen(ids) ?: QWEN_FALLBACK
        p.edit().putString("qwen_model", best).putLong("qwen_at", System.currentTimeMillis()).apply()
        return best
    }

    /** Pure, for the tests: among qwen ids (no guard/vision-only), the most billions of parameters, then the newest. */
    fun pickQwen(ids: List<String>): String? {
        fun size(id: String) = Regex("(\\d+(?:\\.\\d+)?)b\\b", RegexOption.IGNORE_CASE).findAll(id).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        fun version(id: String) = Regex("qwen(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return ids.filter { it.contains("qwen", true) && !it.contains("guard", true) && !it.contains("embed", true) }
            .maxWithOrNull(compareBy<String>({ size(it) }, { version(it) }))
    }

    /** Qwen's own instructions on top of RE's system prompt (the Flask lessons: name the mistakes, show an example). */
    const val QWEN_RULES = "\n\nHow to answer (follow exactly):\n" +
        "1. A DRAFT is in the language of the message it answers or edits (the newest [left] message on the screen, else the text he gave you); he speaks English to you always, so his request language never decides it. A Croatian message gets a Croatian draft with č ć š ž đ. Only an explicit translate / in English / na hrvatski changes it. Never ask which language or style.\n" +
        "2. A message he could send goes inside <draft></draft>, ONE draft, only the message text, nothing else inside it.\n" +
        "3. Outside the draft at most one short sentence to him. No headings, no lists, no markdown, no emojis he did not use.\n" +
        "4. Use only facts from the screen and his words. Never invent names, times, places or promises.\n" +
        "5. Follow every rule in <lessons> and the style in <skills> and <person> before anything else.\n" +
        "6. Never show your reasoning, never write <think>.\n" +
        "7. Croatian: standard Croatian only. Future tense donijet ću, doći ću, javit ću (never donijeti ću); isprika, not izvinjenje.\n" +
        "8. His dictation slips: \"creation\" means Croatian, \"Vesper\" means Whisper; write the meant word.\n" +
        "9. Begin the draft with the greeting from the <person> style (\"Bok Ana!\", \"Hi Tom,\") and keep that register.\n" +
        "10. Never answer for him: do not write \"I got it\", a time, or a promise he did not say. Missing fact = ask, no draft.\n" +
        "11. After the draft add one short sentence about what on the screen is still unanswered, if anything is.\n" +
        "12. Your words to him (\"Evo:\", \"Here it is:\") are in the language of HIS LAST MESSAGE, never the chat's.\n" +
        "13. When he asks for more formal, shorter or casual, that wins over the person's usual style: formal Croatian = \"Poštovana Ana\" / Vi.\n" +
        "14. [left] lines are the other person. If Tom asked \"did you get the stems?\", Tom asked it: do not write that question to Tom.\n" +
        "Example. Marko: \"reply to Ana that I'll be ten minutes late, sorry\" -> RE: \"Here it is: <draft>Sorry Ana, " +
        "I'm running about ten minutes late, see you soon.</draft>\"\n" +
        "Example. Marko: \"odgovori Ivanu da sutra može u 10\" -> RE: \"<draft>Bok Ivane! Može sutra u 10, vidimo se!</draft>\"\n" +
        "Example. Marko: \"send Tom the studio address\" (no address anywhere) -> RE: \"I don't have the studio address. What is it?\"\n" +
        "Example. Marko: \"ispravi: sutra cu donjet kolac\" -> RE: \"<draft>Sutra ću donijeti kolač.</draft>\" (only spelling fixed)"

    private val THINK = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)

    /** Removes Qwen's thinking, even an unclosed one at the start (Flask lesson: never read the thinking as the output). */
    fun stripThinking(text: String): String {
        var t = THINK.replace(text, "")
        val open = t.indexOf("<think>", ignoreCase = true)
        if (open >= 0) t = t.substring(0, open)
        return t.trim()
    }

    /** One Qwen chat call with RE's own key ring: the same key rules as everywhere (next key on quota, 3 s retries x10). */
    suspend fun groqAsk(c: Context, system: String, user: String, status: (String) -> Unit): String {
        if (!hasQwenKey(c)) throw EngineFailure(FailKind.NOT_CONFIGURED, "no RE Qwen key yet")
        val model = qwenModel(c)
        var attempt = 0
        while (true) {
            try {
                // Qwen's skills, written by Opus in training mode (MaReTraining), on every Qwen call.
                val skills = MaReTraining.skills().let { if (it.isBlank()) "" else "\n\nYour skills (learned from your supervisor, follow them):\n$it" }
                return KeyRing.run(c, RING, "Qwen") { k -> stripThinking(chat(k, model, system + QWEN_RULES + skills, user)) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                val kind = FallbackRunner.classify(t)
                if (kind == FailKind.QUOTA || kind == FailKind.AUTH) throw EngineFailure(kind, "every RE Qwen key is out")
                if (kind == FailKind.NOT_CONFIGURED) throw t
                attempt++
                if (attempt > 10) throw t
                status("RE · Qwen · retry $attempt/10 · " + if (kind == FailKind.NETWORK) "no internet" else "no answer")
                delay(3000)
            }
        }
    }

    /** Kept for the old spoken-instruction RE path: a rewrite with Qwen. */
    const val SYSTEM = "Rewrite the user's text exactly as instructed. Stay faithful to the meaning, keep the same language as the input, " +
        "and return only the resulting text, with no comments, quotes or explanations."

    suspend fun groq(c: Context, user: String, status: (String) -> Unit): String =
        groqAsk(c, SYSTEM, user, status).replace(Regex("</?draft>", RegexOption.IGNORE_CASE), "").trim()

    private suspend fun chat(key: String, model: String, system: String, user: String): String = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        fun body(hidden: Boolean) = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.3)
            .put("max_completion_tokens", 6000).apply { if (hidden) put("reasoning_format", "hidden") }
            .toString().toRequestBody(JSONT)
        fun call(hidden: Boolean) = http.newCall(
            Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $key").post(body(hidden)).build(),
        ).execute()
        var r = call(true)
        if (r.code == 400) { r.close(); r = call(false) } // a model without reasoning_format: send it plain
        r.use { resp ->
            val text = resp.body.string()
            when {
                resp.code == 429 -> throw EngineFailure(FailKind.QUOTA, "rate limit")
                resp.code == 401 || resp.code == 403 -> throw EngineFailure(FailKind.AUTH, "refused")
                resp.code == 404 -> throw EngineFailure(FailKind.ERROR, "model retired")
                !resp.isSuccessful -> throw java.io.IOException("Groq HTTP ${resp.code}")
            }
            JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
        }
    }
}
