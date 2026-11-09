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
import org.json.JSONObject

/**
 * The accuracy shown next to each engine (Marko, 9.11.2026: "In parentheses next to each model, the
 * accuracy: 100% is the best, less is worse"). Accuracy = 100 x (1 - WER) of the last benchmark on
 * his own dictations, per language, kept per engine so an engine that sat out a later run keeps its
 * last figure. Nothing is shown for an engine that was never scored.
 */
object EngineScores {
    fun pct(wer: Double): Int = Math.round((100.0 * (1.0 - wer)).coerceIn(0.0, 100.0)).toInt()

    /** Merge the rows of a run into the stored table {lang: {engine: pct}}. Pure. */
    fun merge(stored: String?, rows: List<BenchMath.Row>): String {
        val o = runCatching { JSONObject(stored ?: "{}") }.getOrDefault(JSONObject())
        for (r in rows) {
            if (r.clips <= 0) continue
            val l = o.optJSONObject(r.lang) ?: JSONObject().also { o.put(r.lang, it) }
            l.put(r.engineId, pct(r.wer))
        }
        return o.toString()
    }

    fun read(stored: String?, lang: String, id: String): Int? = runCatching {
        val l = JSONObject(stored ?: return null).optJSONObject(if (lang == "en") "en" else "hr") ?: return null
        if (l.has(id)) l.getInt(id) else null
    }.getOrNull()

    fun record(c: Context, rows: List<BenchMath.Row>) = EngineStore.setScores(c, merge(EngineStore.scores(c), rows))

    fun get(c: Context, id: String, lang: String): Int? = read(EngineStore.scores(c), lang, id)

    /** " (96%)" or "". */
    fun suffix(c: Context, id: String, lang: String): String = get(c, id, lang)?.let { " ($it%)" }.orEmpty()
}

/** Word diff for the re-transcribe view: which words of each side are not in the longest common run. */
object WordDiff {
    data class Tok(val text: String, val changed: Boolean)

    private fun key(t: String) = Wer.norm(t).joinToString(" ")

    fun diff(a: String, b: String): Pair<List<Tok>, List<Tok>> {
        val x = a.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val y = b.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kx = x.map { key(it) }
        val ky = y.map { key(it) }
        val l = Array(x.size + 1) { IntArray(y.size + 1) }
        for (i in x.size - 1 downTo 0) for (j in y.size - 1 downTo 0)
            l[i][j] = if (kx[i] == ky[j]) l[i + 1][j + 1] + 1 else maxOf(l[i + 1][j], l[i][j + 1])
        val ra = ArrayList<Tok>()
        val rb = ArrayList<Tok>()
        var i = 0
        var j = 0
        while (i < x.size && j < y.size) {
            when {
                kx[i] == ky[j] -> { ra.add(Tok(x[i], false)); rb.add(Tok(y[j], false)); i++; j++ }
                l[i + 1][j] >= l[i][j + 1] -> { ra.add(Tok(x[i], true)); i++ }
                else -> { rb.add(Tok(y[j], true)); j++ }
            }
        }
        while (i < x.size) ra.add(Tok(x[i++], true))
        while (j < y.size) rb.add(Tok(y[j++], true))
        return ra to rb
    }
}
