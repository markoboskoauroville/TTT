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
import java.io.File
import org.json.JSONObject

/**
 * The clip buffer (Marko, 9.11.2026): the last [CAPACITY] dictations per language, each the audio
 * (16 kHz WAV), the language the E/H key said, and the text he finally sent (the reference, kept
 * up to date by the corrections loop). On the phone only, never uploaded except to an engine while
 * a benchmark runs. A Settings switch and a delete button.
 */
object ClipBuffer {
    const val CAPACITY = 20

    data class Clip(val id: String, val lang: String, val wav: File, val reference: String, val seconds: Double, val ts: Long, val engine: String)

    @Volatile
    private var lastId: String? = null

    @Volatile
    private var lastLang: String? = null

    private fun dir(c: Context, lang: String) = File(c.applicationContext.filesDir, "ttt_clips/$lang").apply { mkdirs() }

    fun seconds(wav: File): Double {
        val n = wav.length() - 44
        return if (n <= 0) 0.0 else n / 32000.0
    }

    /** Copies [wav] in with [text] as the first reference. */
    fun add(c: Context, wav: File, lang: String, text: String, engine: String, ts: Long = System.currentTimeMillis()): String? {
        if (!EngineStore.clipsOn(c) || !wav.exists() || text.isBlank()) return null
        val l = if (lang == "en") "en" else "hr"
        return runCatching {
            val id = ts.toString()
            val d = dir(c, l)
            wav.copyTo(File(d, "$id.wav"), overwrite = true)
            File(d, "$id.json").writeText(JSONObject().put("ref", text).put("engine", engine).put("sec", seconds(wav)).toString())
            prune(c, l)
            lastId = id
            lastLang = l
            id
        }.getOrNull()
    }

    fun list(c: Context, lang: String): List<Clip> {
        val d = dir(c, lang)
        return d.listFiles().orEmpty().filter { it.name.endsWith(".wav") }.mapNotNull { w ->
            val id = w.name.removeSuffix(".wav")
            val j = runCatching { JSONObject(File(d, "$id.json").readText()) }.getOrNull() ?: return@mapNotNull null
            Clip(id, lang, w, j.optString("ref"), j.optDouble("sec", seconds(w)), id.toLongOrNull() ?: 0L, j.optString("engine"))
        }.sortedByDescending { it.ts }
    }

    fun latest(c: Context, lang: String, n: Int) = list(c, lang).take(n)

    fun count(c: Context, lang: String) = list(c, lang).size

    fun setReference(c: Context, lang: String, id: String, text: String) {
        runCatching {
            val f = File(dir(c, lang), "$id.json")
            val j = JSONObject(f.readText())
            f.writeText(j.put("ref", text).toString())
        }
    }

    /** The newest added clip gets the text that really went into the field, and later what he made of it. */
    fun setLastReference(c: Context, text: String) {
        val id = lastId ?: return
        setReference(c, lastLang ?: return, id, text)
    }

    fun lastRef(): Pair<String, String>? = (lastLang ?: return null) to (lastId ?: return null)

    fun discardLast(c: Context) {
        val id = lastId ?: return
        val l = lastLang ?: return
        File(dir(c, l), "$id.wav").delete(); File(dir(c, l), "$id.json").delete()
        lastId = null
    }

    fun clear(c: Context) {
        File(c.applicationContext.filesDir, "ttt_clips").deleteRecursively()
        lastId = null
    }

    private fun prune(c: Context, lang: String) {
        list(c, lang).drop(CAPACITY).forEach { it.wav.delete(); File(it.wav.parentFile, it.id + ".json").delete() }
    }
}
