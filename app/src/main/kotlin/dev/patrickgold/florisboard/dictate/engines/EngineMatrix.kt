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
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.MaReEngine
import dev.patrickgold.florisboard.dictate.MaStyleBox
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * THE ENGINE MATRIX (Marko, 28.11.2026: "test all the engines ... using Frankfurt, no Frankfurt ... with styling,
 * without styling ... see where is the bug, why it doesn't work all the time").
 *
 * adb pushes test clips to Android/data/<pkg>/files/matrix/ as <lang>_<name>.wav (16 kHz mono) with <lang>_<name>.txt
 * (the known text). Each case is one take through the keyboard's own transcribe() (DictateController.debugTakeFile:
 * the FRA / chain / old-path decision, the engine, the key ring, the 3 s retries), with the H/E engine and the FRA switch
 * set for that case and put back afterwards. Nothing is typed, kept in the history or in the clip buffer.
 * Styling cases send the known text through the Style tab's exact request (MaStyleBox) with each provider.
 * One JSON line per case in files/matrix/results.jsonl, never a key.
 */
object EngineMatrix {
    @Volatile var running = false

    fun dir(c: Context) = File(c.getExternalFilesDir(null), "matrix").apply { mkdirs() }

    private class Clip(val name: String, val lang: String, val wav: File, val ref: String)

    private fun clips(c: Context, only: Set<String>?): List<Clip> =
        dir(c).listFiles().orEmpty().filter { it.name.endsWith(".wav") }.sortedBy { it.name }.mapNotNull { w ->
            val name = w.name.removeSuffix(".wav")
            if (only != null && name !in only) return@mapNotNull null
            val ref = File(w.parentFile, "$name.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
            Clip(name, if (name.startsWith("hr")) "hr" else "en", w, ref)
        }

    suspend fun transcription(
        c: Context, engines: List<String>, routes: List<String>, runs: Int, only: Set<String>?, out: (String) -> Unit,
    ): File {
        val res = File(dir(c), "results.jsonl")
        if (running) { out("matrix already running"); return res }
        running = true
        val keepHr = EngineStore.selected(c, "hr")
        val keepEn = EngineStore.selected(c, "en")
        val keepFra = FrankfurtRelay.on(c)
        val keepLang = MaLanguage.active()
        try {
            val list = clips(c, only)
            out("matrix: ${list.size} clips x ${engines.size} engines x ${routes.size} routes x $runs runs")
            for (run in 1..runs) for (clip in list) for (engine in engines) for (route in routes) {
                EngineStore.setSelected(c, clip.lang, engine)
                FrankfurtRelay.setOn(c, route == "fra")
                MaLanguage.setNow(clip.lang)
                val copy = File(c.cacheDir, "matrix_take.wav")
                clip.wav.copyTo(copy, overwrite = true)
                val sec = ClipBuffer.seconds(clip.wav)
                val t0 = System.currentTimeMillis()
                val d = runCatching { DictateController.debugTakeFile(c, copy, sec.toLong().coerceAtLeast(1)) }
                    .getOrElse { e -> DictateController.DebugTake().also { it.error = "harness: " + e.javaClass.simpleName + " " + e.message } }
                val ms = System.currentTimeMillis() - t0
                val line = JSONObject().put("kind", "stt").put("clip", clip.name).put("lang", clip.lang).put("sec", sec)
                    .put("engine", engine).put("route", route).put("run", run).put("ok", d.text != null && d.text!!.isNotBlank())
                    .put("path", d.path).put("answeredBy", d.answeredBy).put("ms", ms).put("text", d.text ?: "")
                    .put("error", d.error ?: "").put("outcome", d.outcome).put("statuses", JSONArray(d.statuses.toList().takeLast(14)))
                    .put("at", t0)
                res.appendText(line.toString() + "\n")
                out("stt ${clip.name} $engine $route #$run: " + (if (d.text != null) "ok ${ms}ms" else "FAIL " + d.error?.take(80)))
            }
        } finally {
            EngineStore.setSelected(c, "hr", keepHr)
            EngineStore.setSelected(c, "en", keepEn)
            FrankfurtRelay.setOn(c, keepFra)
            MaLanguage.setNow(keepLang)
            running = false
            out("matrix done: " + res.absolutePath)
        }
        return res
    }

    /** The Style tab's request for each clip's known text, with each provider (anthropic = Sonnet, groq = Qwen). */
    suspend fun styling(c: Context, providers: List<String>, runs: Int, only: Set<String>?, out: (String) -> Unit): File {
        val res = File(dir(c), "results.jsonl")
        if (running) { out("matrix already running"); return res }
        running = true
        val keepEngine = MaReEngine.engine()
        try {
            val list = clips(c, only).filter { it.ref.isNotBlank() }
            for (run in 1..runs) for (clip in list) for (pv in providers) {
                MaReEngine.setEngine(if (pv == "groq") MaReEngine.QWEN else MaReEngine.SONNET)
                MaReEngine.lastAnswered = ""
                val t0 = System.currentTimeMillis()
                val r = runCatching { MaStyleBox.styleForTest(c, clip.ref) }
                val ms = System.currentTimeMillis() - t0
                val line = JSONObject().put("kind", "style").put("clip", clip.name).put("lang", clip.lang).put("provider", pv)
                    .put("run", run).put("ok", r.isSuccess && !r.getOrNull().isNullOrBlank()).put("ms", ms)
                    .put("answeredBy", MaReEngine.lastAnswered).put("text", r.getOrNull() ?: "")
                    .put("error", r.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message.orEmpty().take(160) } ?: "")
                    .put("at", t0)
                res.appendText(line.toString() + "\n")
                out("style ${clip.name} $pv #$run: " + (if (r.isSuccess) "ok ${ms}ms by ${MaReEngine.lastAnswered}" else "FAIL " + r.exceptionOrNull()?.message?.take(80)))
            }
        } finally {
            MaReEngine.setEngine(keepEngine)
            running = false
            out("style matrix done")
        }
        return res
    }
}
