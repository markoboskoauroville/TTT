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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.jvm.Volatile

/**
 * RE SPEAKS (Marko, 9.11.2026: "give RE a voice so it can ask me questions"; "Voice Engine is already installed, with
 * subtitles").
 *
 * The voice is TTT's own Reader voice ([MaReader.speakText]: Speechify for English, Croatian Edge for Croatian, his
 * chosen voice and speed). The subtitles are Voice Engine (Mantra)'s: it is an overlay app with no speak API of its own
 * (VOICE_ENGINE_ANDROID README), fed over HTTP on 127.0.0.1:8720-8735 (`POST /start {id,text,words}`,
 * `POST /pos {id,pos,paused,dur}`, `POST /end {id}`), so RE tells it what it is saying and how far it is, with the
 * length estimated from the text (no word timings: Voice Engine spreads the clip over the words). If Voice Engine is
 * not running nothing happens beyond the voice. RE's questions, notes and read-outs are spoken; the voice stops when he
 * starts dictating ([stop], called from the recorder). The switch is in Settings, RE Magic.
 */
object MaVoice {

    private val http = OkHttpClient.Builder().connectTimeout(400, TimeUnit.MILLISECONDS).callTimeout(1, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val JSON = "application/json".toMediaType()

    @Volatile
    private var job: Job? = null

    @Volatile
    private var id = 0

    fun on(): Boolean = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maReSpeaks.get()
    }.getOrDefault(true)

    /** Speak [text] with the Reader voice and show it as Voice Engine subtitles. */
    fun say(context: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty() || !on()) return
        stop()
        val app = context.applicationContext
        MaReader.speakText(app, t.take(1500)) { }
        val myId = ++id
        job = scope.launch {
            val port = findPort() ?: return@launch
            val seconds = (t.length * 0.065).coerceIn(1.5, 90.0)
            post(port, "start", JSONObject().put("id", "re-$myId").put("text", t).put("words", org.json.JSONArray()))
            val t0 = System.currentTimeMillis()
            while (id == myId && (System.currentTimeMillis() - t0) / 1000.0 < seconds) {
                post(port, "pos", JSONObject().put("id", "re-$myId").put("pos", (System.currentTimeMillis() - t0) / 1000.0).put("paused", false).put("dur", seconds))
                delay(250)
            }
            post(port, "end", JSONObject().put("id", "re-$myId"))
        }
    }

    /** He starts dictating, or closes the pop-up: the voice and the subtitle stop. */
    fun stop() {
        id++
        job?.cancel()
        runCatching { MaReader.stop() }
    }

    private fun findPort(): Int? {
        for (p in 8720..8735) {
            val ok = runCatching {
                http.newCall(Request.Builder().url("http://127.0.0.1:$p/status").build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) return p
        }
        return null
    }

    private fun post(port: Int, path: String, body: JSONObject) {
        runCatching {
            http.newCall(Request.Builder().url("http://127.0.0.1:$port/$path").post(body.toString().toRequestBody(JSON)).build()).execute().close()
        }
    }
}
