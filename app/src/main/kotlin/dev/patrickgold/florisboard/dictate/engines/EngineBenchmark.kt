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
import dev.patrickgold.florisboard.dictate.MaRemoteDb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Benchmark voice engines": the last 10 clips in Croatian and 10 in English through every enabled
 * engine, scored against the text he finally sent. Spend-aware: an engine is only given a clip when
 * its free quota covers it (a paid engine only when he enabled it, its price shown), so a run can
 * never leave a free quota.
 */
object EngineBenchmark {
    const val CLIPS = 10

    data class Result(val rows: List<BenchMath.Row>, val skipped: List<String>, val json: String, val date: String, val saved: String)

    @Volatile var running = false
        private set

    suspend fun run(c: Context, onProgress: (String) -> Unit): Result = withContext(Dispatchers.IO) {
        running = true
        try {
            val names = EngineChain.names()
            val rows = ArrayList<BenchMath.Row>()
            val skipped = ArrayList<String>()
            for (lang in listOf("hr", "en")) {
                val clips = ClipBuffer.latest(c, lang, CLIPS).filter { it.reference.isNotBlank() }
                if (clips.isEmpty()) { skipped.add("$lang: no clips yet"); continue }
                val ids = EngineStore.order(c, lang).filter { it != EngineCatalog.FAKE && EngineStore.enabled(c, it) && EngineChain.available(c, it) }
                for (id in ids) {
                    val spec = EngineCatalog.byId(id) ?: continue
                    val scores = ArrayList<Wer.Score>()
                    val lat = ArrayList<Long>()
                    var cost = 0.0
                    var failed = 0
                    var skippedClips = 0
                    for ((i, clip) in clips.withIndex()) {
                        onProgress("${lang.uppercase()} · ${spec.name} · clip ${i + 1}/${clips.size}")
                        val now = System.currentTimeMillis()
                        val u = EngineStore.usage(c, id)
                        // Paid engines are only in the list when explicitly enabled: that is the consent.
                        if (!Quota.canAfford(spec, u, clip.seconds, now, paidAllowed = EngineStore.enabled(c, id))) { skippedClips++; continue }
                        val t0 = System.currentTimeMillis()
                        try {
                            val text = withTimeout(EngineChain.timeoutMs(clip.seconds, spec.kind == EngineKind.LOCAL) + 20_000L) {
                                EngineAdapters.run(c, id, clip.wav, lang, names)
                            }
                            lat.add(System.currentTimeMillis() - t0)
                            scores.add(Wer.score(clip.reference, text, names))
                            val before = EngineStore.usage(c, id)
                            EngineStore.addUsage(c, id, clip.seconds)
                            cost += EngineStore.usage(c, id).costUsd - Quota.current(spec, before, now).costUsd
                        } catch (e: CancellationException) {
                            if (e is kotlinx.coroutines.TimeoutCancellationException) failed++ else throw e
                        } catch (f: EngineFailure) {
                            failed++
                            if (f.kind == FailKind.QUOTA) { skipped.add("${spec.name} ($lang): quota reached, stopped"); break }
                            if (f.kind == FailKind.AUTH || f.kind == FailKind.NOT_CONFIGURED) { skipped.add("${spec.name} ($lang): ${f.kind.name.lowercase()}"); break }
                        } catch (t: Throwable) { failed++ }
                    }
                    if (skippedClips > 0) skipped.add("${spec.name} ($lang): $skippedClips clips skipped, free quota would be exceeded")
                    if (scores.isNotEmpty() || failed > 0) rows.add(BenchMath.aggregate(id, lang, scores, lat, cost, failed))
                }
            }
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            val json = toJson(date, rows, skipped)
            runCatching {
                File(c.filesDir, "engine_bench").apply { mkdirs() }.let { File(it, "$date.json").writeText(json) }
            }
            EngineStore.setLastBenchmark(c, json)
            val saved = if (MaRemoteDb.hasToken()) MaRemoteDb.pushBenchmark(date, json) else "not saved to the database (no token set)"
            onProgress("done")
            Result(rows, skipped, json, date, saved)
        } finally { running = false }
    }

    fun toJson(date: String, rows: List<BenchMath.Row>, skipped: List<String>): String {
        val o = JSONObject().put("date", date).put("source", "TTT Settings > Engines > Benchmark voice engines")
        for (lang in listOf("hr", "en")) {
            val a = JSONArray()
            rows.filter { it.lang == lang }.forEach {
                a.put(
                    JSONObject().put("engine", it.engineId).put("clips", it.clips).put("failed", it.failed)
                        .put("wer_pct", Math.round(it.wer * 1000) / 10.0).put("name_errors", it.nameErrors).put("names", it.names)
                        .put("avg_ms", it.avgMs).put("cost_usd", Math.round(it.costUsd * 10000) / 10000.0),
                )
            }
            o.put(lang, a)
        }
        o.put("notes", JSONArray(skipped))
        return o.toString(2)
    }

    /** The plain table for Settings (one per language). */
    fun table(rows: List<BenchMath.Row>, lang: String): String {
        val r = rows.filter { it.lang == lang }.sortedBy { it.wer }
        if (r.isEmpty()) return "no results yet"
        val sb = StringBuilder()
        r.forEach {
            val n = EngineCatalog.byId(it.engineId)?.name?.substringBefore(" (") ?: it.engineId
            sb.append(String.format(Locale.ROOT, "%-22s WER %5.1f%%  names %d/%d  %4d ms  $%.4f  (%d clips%s)\n",
                n.take(22), it.wer * 100, it.names - it.nameErrors, it.names, it.avgMs, it.costUsd, it.clips, if (it.failed > 0) ", ${it.failed} failed" else ""))
        }
        return sb.toString().trimEnd()
    }

    /** "Use best per language": reorders both chains by this run. */
    fun applyBest(c: Context, rows: List<BenchMath.Row>) {
        for (lang in listOf("hr", "en")) {
            EngineStore.setOrder(c, lang, BenchMath.bestOrder(rows.filter { it.lang == lang }, EngineStore.order(c, lang)))
        }
    }

    fun rowsFromJson(json: String): List<BenchMath.Row> = runCatching {
        val o = JSONObject(json)
        val out = ArrayList<BenchMath.Row>()
        for (lang in listOf("hr", "en")) {
            val a = o.optJSONArray(lang) ?: continue
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                val clips = x.optInt("clips")
                // WER is stored as a percentage; rebuild words/errors on a 1000-word scale.
                out.add(BenchMath.Row(x.getString("engine"), lang, clips, Math.round(x.optDouble("wer_pct") * 10).toInt(), 1000,
                    x.optInt("name_errors"), x.optInt("names"), x.optLong("avg_ms"), x.optDouble("cost_usd"), x.optInt("failed")))
            }
        }
        out
    }.getOrDefault(emptyList())
}
