/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

/**
 * ALWAYS THE NEWEST ASSEMBLYAI PRO MODEL, FOUND BY NAME RATHER THAN WRITTEN DOWN.
 *
 * AssemblyAI name their flagship `universal-<major>-<minor>-pro`: `universal-3-pro`,
 * `universal-3-5-pro`, `universal-3-6-pro`. The family stays; the number moves. A hardcoded id goes
 * stale the day they ship the next one, so the app stores the FAMILY, [ALIAS] —
 * `universal-pro-latest` — and this file turns it into the newest version AssemblyAI offer on that
 * path. The same trick as [MaClaudeModels].
 *
 * ### Where the list comes from
 *
 * AssemblyAI have no model-list endpoint. Their documentation does list, per path, the exact ids that
 * path accepts, and it is served as plain Markdown ([DOC_URL]). The three paths do NOT accept the same
 * models — on 2.11.2026 streaming had `universal-3-6-pro` while pre-recorded and Sync stopped at
 * `universal-3-5-pro` — so each path reads its own page.
 *
 * ### The evidence rule
 *
 * **Only an id AssemblyAI's own page for that path names is ever sent**, and nothing is guessed by
 * bumping a number. If the page cannot be read, the [FALLBACK] id is used — a real id, checked when
 * this was written. If AssemblyAI then refuse the id anyway (a page that mentions a model before the
 * API takes it), the request is sent once more on the [FALLBACK] and the refused id is not used again
 * in this process.
 *
 * ### Never in the way of a dictation
 *
 * Reading the page never delays a recording: a request uses what is already known (or the fallback)
 * and the page is re-read in the background when it is older than [MAX_AGE_MS].
 *
 * Pure: strings in, a decision out. Walked by `scripts/test_assembly_models.py`.
 */
object MaAssemblyModels {

    /** The stored form meaning "the newest Universal Pro this path accepts". */
    const val ALIAS = "universal-pro-latest"

    enum class Path { ASYNC, SYNC, STREAMING }

    /** Real ids, used only when the page cannot be read. Checked against AssemblyAI's docs on 2.11.2026. */
    val FALLBACK = mapOf(
        Path.ASYNC to "universal-3-5-pro",
        Path.SYNC to "universal-3-5-pro",
        Path.STREAMING to "universal-3-6-pro",
    )

    /** The page that lists the ids each path accepts. */
    val DOC_URL = mapOf(
        Path.ASYNC to "https://www.assemblyai.com/docs/pre-recorded-audio/select-the-speech-model.md",
        Path.SYNC to "https://www.assemblyai.com/docs/api-reference/sync-api/transcribe.md",
        Path.STREAMING to "https://www.assemblyai.com/docs/streaming/select-the-speech-model.md",
    )

    /** A week: AssemblyAI ship a new Pro a few times a year, and the page costs one small GET. */
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Ids that shipped as defaults before this file existed. Stored on a phone because the picker
     * wrote the default back, not because he chose them, so they read as "the newest".
     * `universal-streaming` was the old live label: the socket never sent a model, so AssemblyAI
     * already served their newest; it keeps meaning that.
     */
    private val OLD_DEFAULTS = setOf("universal-3-5-pro", "u3-sync-pro", "u3-pro", "universal-streaming")

    data class Version(val major: Int, val minor: Int)

    // universal-3-pro, universal-3-5-pro, universal-4-pro, universal-3-10-pro
    private val PRO = Regex("""^universal-(\d+)(?:-(\d{1,2}))?-pro$""")
    private val PRO_IN_TEXT = Regex("""universal-\d+(?:-\d{1,2})?-pro\b""")

    fun parse(id: String): Version? =
        PRO.matchEntire(id.trim().lowercase())?.let { m ->
            Version(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0)
        }

    /** Every Universal Pro id a documentation page names, once each. */
    fun idsInDoc(markdown: String): List<String> =
        PRO_IN_TEXT.findAll(markdown.lowercase()).map { it.value }.distinct().toList()

    fun isAlias(model: String): Boolean {
        val s = model.trim().lowercase()
        return s.isEmpty() || s == ALIAS || s in OLD_DEFAULTS
    }

    /** Compared as numbers: 3.10 is newer than 3.9. */
    fun newest(available: List<String>, refused: Set<String> = emptySet()): String? =
        available.map { it.trim().lowercase() }
            .filter { it !in refused }
            .mapNotNull { id -> parse(id)?.let { id to it } }
            .sortedWith(compareByDescending<Pair<String, Version>> { it.second.major }.thenByDescending { it.second.minor })
            .firstOrNull()?.first

    /**
     * The id to send for [model] on [path]. The alias (or an old default) becomes the newest Pro the
     * page for [path] lists, or the [FALLBACK]. Anything else is a deliberate choice and sent as it is.
     */
    fun resolve(model: String, path: Path, available: List<String>, refused: Set<String> = emptySet()): String {
        if (!isAlias(model)) return model.trim()
        return newest(available, refused) ?: FALLBACK.getValue(path)
    }

    /** "Universal-3.6 Pro" for `universal-3-6-pro`; anything else as it is. */
    fun displayName(id: String): String {
        val v = parse(id) ?: return id
        return if (v.minor == 0) "Universal-${v.major} Pro" else "Universal-${v.major}.${v.minor} Pro"
    }

    /**
     * Whether AssemblyAI refused the request because of the MODEL, never a key, quota or rate problem.
     * Their errors name the field (`speech_models`, `speech_model`, `X-AAI-Model`) or the id.
     */
    fun isModelFailure(status: Int?, message: String?, model: String): Boolean {
        if (status == null || status == 401 || status == 403 || status == 429) return false
        if (status != 400 && status != 404 && status != 422) return false
        val hay = message.orEmpty().lowercase()
        return (model.isNotBlank() && hay.contains(model.lowercase())) ||
            hay.contains("speech_model") || hay.contains("x-aai-model") ||
            (hay.contains("model") && (hay.contains("invalid") || hay.contains("not supported") ||
                hay.contains("unsupported") || hay.contains("not found") || hay.contains("does not exist")))
    }
}

/**
 * What each path's documentation page listed, kept for the life of the process and re-read in the
 * background when it is a week old. Never blocks: [model] answers from what is known at once.
 */
object MaAssemblyCatalog {

    private val lists = java.util.concurrent.ConcurrentHashMap<MaAssemblyModels.Path, Pair<List<String>, Long>>()
    private val reading = java.util.Collections.synchronizedSet(HashSet<MaAssemblyModels.Path>())
    private val refused = java.util.Collections.synchronizedSet(HashSet<String>())

    /** The id to send for [asked] on [path], starting a background re-read when the list is stale. */
    fun model(client: okhttp3.OkHttpClient, asked: String, path: MaAssemblyModels.Path): String {
        val entry = lists[path]
        if (MaAssemblyModels.isAlias(asked) &&
            (entry == null || System.currentTimeMillis() - entry.second > MaAssemblyModels.MAX_AGE_MS)
        ) {
            refresh(client, path)
        }
        return MaAssemblyModels.resolve(asked, path, entry?.first.orEmpty(), synchronized(refused) { refused.toSet() })
    }

    /** AssemblyAI refused [id]: it is not chosen again in this process; the fallback takes its place. */
    fun markRefused(id: String) {
        refused.add(id.trim().lowercase())
    }

    private fun refresh(client: okhttp3.OkHttpClient, path: MaAssemblyModels.Path) {
        if (!reading.add(path)) return
        val request = okhttp3.Request.Builder().url(MaAssemblyModels.DOC_URL.getValue(path)).get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                reading.remove(path)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use {
                        if (it.isSuccessful) {
                            val ids = MaAssemblyModels.idsInDoc(it.body.string())
                            if (ids.isNotEmpty()) lists[path] = ids to System.currentTimeMillis()
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    reading.remove(path)
                }
            }
        })
    }
}
