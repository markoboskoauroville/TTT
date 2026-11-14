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
import android.util.Base64
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Frankfurt relay (Marko, 14.11.2026): "only the actual transcription is going through Frankfurt always and if
 * Frankfurt is down of course I need to have a switch in the settings to skip the Frankfurt."
 *
 * The phone records exactly as before; the WAV goes to ttt-relay on chromium-vm (eu-frankfurt-1, Tailscale
 * 100.102.86.30:8897, repo vm-chromium ttt-relay/). Frankfurt holds a copy of the phone's keys, runs the engine
 * the H / E key chose (never another), turns the forward-only key ring, retries 3 s x 10, and the phone shows its
 * status under the recorder ("H · Frankfurt · Groq 2/8 · retry 1/10 · no internet"). Trash while it works cancels
 * the job in Frankfurt too; the audio is kept and History offers Re-transcribe with any engine.
 *
 * Frankfurt not reachable (Tailscale off, VM down): the phone waits 3 s and tries Frankfurt again, up to 10 times,
 * then stops with the audio kept. It never sends direct by itself; the switch (Settings and the FRA chip on the
 * H/E long-press panel) skips Frankfurt. Keys go to Frankfurt by themselves whenever the phone's keys change
 * (write only: Frankfurt never gives a key back). Access is Tailscale itself: the relay only answers Marko's devices.
 */
object FrankfurtRelay {
    const val DEFAULT_URL = "http://100.102.86.30:8897"
    private const val PREFS = "ttt_frankfurt"

    /** The rings Frankfurt can run, the same ids as [KeyRing] uses on the phone. */
    val RINGS = listOf("groq", "gemini", "deepgram", "assemblyai", "speechmatics", "soniox", "google_v1", "elevenlabs", "cloudflare", "google_chirp")

    /** Engines that run in Frankfurt; parakeet / android stay on the phone, the test engine too. */
    fun handles(id: String) = id != "parakeet" && id != "android" && id != EngineCatalog.FAKE && EngineCatalog.byId(id) != null

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** ON by default (Marko: "this is by default selected"). */
    fun on(c: Context) = p(c).getBoolean("on", true)
    fun setOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("on", v).apply()
        _onState.value = v
    }
    private val _onState = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    /** For the FRA chip: null until read once. */
    fun onFlow(c: Context): kotlinx.coroutines.flow.StateFlow<Boolean?> { if (_onState.value == null) _onState.value = on(c); return _onState }

    fun url(c: Context) = p(c).getString("url", DEFAULT_URL)!!.trimEnd('/')

    /** The last thing Frankfurt said (Settings shows it): "answers, 41 keys" / "not answering". */
    fun lastState(c: Context): String? = p(c).getString("last", null)
    private fun setLast(c: Context, s: String) = p(c).edit().putString("last", s).apply()

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSONT = "application/json".toMediaType()
    private val WAV = "audio/wav".toMediaType()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }

    private class RelayDown(msg: String) : IOException(msg)

    /** JSON of a 2xx answer. IOException = Frankfurt not reachable; a non-2xx answer = [EngineFailure]. */
    private suspend fun call(req: Request): JSONObject {
        http.newCall(req).await().use { r ->
            val body = runCatching { r.body.string() }.getOrDefault("")
            if (r.code == 403) throw EngineFailure(FailKind.AUTH, "Frankfurt does not know this phone (Tailscale user)")
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("error") }.getOrDefault("").ifEmpty { "HTTP ${r.code}" }
                throw EngineFailure(FailKind.ERROR, "Frankfurt: $msg")
            }
            return runCatching { JSONObject(body) }.getOrElse { throw RelayDown("bad answer") }
        }
    }

    // ------------------------------------------------------------------ keys: the phone's keys, copied to Frankfurt

    private fun keysPayload(c: Context): Pair<JSONObject, String> {
        val rings = JSONObject()
        for (r in RINGS) rings.put(r, JSONArray(EngineKeys.lines(c, r)))
        val text = rings.toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        return rings to hash
    }

    /**
     * Sends the phone's keys when they changed since the last copy (or [force]). Returns "41 keys in Frankfurt" or
     * throws. Never a key value in a message or a log.
     */
    suspend fun syncKeys(c: Context, force: Boolean = false): String = withContext(Dispatchers.IO) {
        val (rings, hash) = keysPayload(c)
        if (!force && p(c).getString("keys_hash", null) == hash) return@withContext "keys already in Frankfurt"
        val body = JSONObject().put("rings", rings).put("replace", true).toString().toRequestBody(JSONT)
        val out = call(Request.Builder().url(url(c) + "/keys").post(body).build()).optJSONObject("rings") ?: JSONObject()
        p(c).edit().putString("keys_hash", hash).apply()
        var total = 0
        out.keys().forEach { total += out.optInt(it) }
        "$total keys in Frankfurt".also { setLast(c, "answers, $total keys") }
    }

    /** Settings: "answers, 3 rings" or "not answering". */
    suspend fun check(c: Context): String = withContext(Dispatchers.IO) {
        try {
            syncKeys(c)
            val st = call(Request.Builder().url(url(c) + "/status").get().build())
            val rings = st.optJSONObject("rings") ?: JSONObject()
            val parts = mutableListOf<String>()
            rings.keys().forEach { r -> rings.optJSONObject(r)?.let { o -> if (o.optInt("keys") > 0) parts += "$r ${o.optInt("cursor")}/${o.optInt("keys")}" } }
            ("answers · " + parts.joinToString(", ").ifEmpty { "no keys yet" }).also { setLast(c, it) }
        } catch (e: IOException) {
            "not answering (Tailscale on? chromium-vm up?)".also { setLast(c, it) }
        } catch (f: EngineFailure) {
            (f.message ?: "refused").also { setLast(c, it) }
        }
    }

    // ------------------------------------------------------------------ one transcription

    private fun namesHeader(names: List<String>) =
        Base64.encodeToString(JSONArray(names.take(200)).toString().toByteArray(), Base64.NO_WRAP)

    /**
     * Transcribes [wav] with engine [id] in Frankfurt. [onStatus] gets Frankfurt's own status line. Returns the
     * text; throws [EngineGaveUp] when Frankfurt says the engine failed or Frankfurt itself stayed unreachable.
     * Cancelling the coroutine cancels the job in Frankfurt.
     */
    suspend fun transcribe(c: Context, id: String, wav: File, lang: String, names: List<String>, onStatus: (String) -> Unit): String {
        val l = if (lang == "en") "en" else "hr"
        val name = EngineCatalog.shortName(id)
        var attempt = 0
        // 1. reach Frankfurt and hand over the audio (3 s x 10 when it does not answer, like every engine).
        var job: JSONObject
        while (true) {
            try {
                onStatus(if (attempt == 0) "Frankfurt · sending to $name…" else "Frankfurt not answering · retry $attempt/${EngineChain.MAX_RETRIES}")
                runCatching { syncKeys(c) }.onFailure { if (it is EngineFailure) throw EngineGaveUp(it.message ?: "Frankfurt refused") else if (it is IOException) throw it }
                val req = Request.Builder().url(url(c) + "/jobs?engine=$id&lang=$l")
                    .header("X-TTT-Names", namesHeader(names)).post(wav.asRequestBody(WAV)).build()
                job = withContext(Dispatchers.IO) { call(req) }
                break
            } catch (e: IOException) {
                attempt++
                if (attempt > EngineChain.MAX_RETRIES) {
                    setLast(c, "not answering")
                    onStatus("Frankfurt not answering · gave up, audio kept · FRA off = direct")
                    throw EngineGaveUp("Frankfurt did not answer after ${EngineChain.MAX_RETRIES} tries (switch FRA off on the H/E panel to send direct)")
                }
                onStatus("Frankfurt not answering · retry $attempt/${EngineChain.MAX_RETRIES}")
                delay(3000)
            } catch (f: EngineFailure) {
                throw EngineGaveUp(f.message ?: "Frankfurt refused")
            }
        }
        val jobId = job.getString("id")
        // 2. follow the job (long poll) until it is done; Frankfurt's status goes straight to the recorder line.
        var version = 0
        var lost = 0
        try {
            while (true) {
                val st = job.optString("state")
                job.optString("status").takeIf { it.isNotEmpty() }?.let(onStatus)
                when (st) {
                    "done" -> return job.optString("text")
                    "failed" -> throw EngineGaveUp(job.optString("error").ifEmpty { "Frankfurt: $name failed" })
                    "cancelled" -> throw EngineGaveUp("cancelled in Frankfurt")
                }
                version = job.optInt("version", version)
                try {
                    job = withContext(Dispatchers.IO) { call(Request.Builder().url(url(c) + "/jobs/$jobId?since=$version&wait=20").get().build()) }
                    lost = 0
                } catch (e: IOException) {
                    lost++
                    if (lost > EngineChain.MAX_RETRIES) throw EngineGaveUp("lost Frankfurt while it was transcribing; audio kept")
                    onStatus("Frankfurt · $name · lost the line · retry $lost/${EngineChain.MAX_RETRIES}")
                    delay(3000)
                }
            }
        } catch (ce: CancellationException) {
            // Trash / cancel on the phone: stop it in Frankfurt too (Marko: "I can then cancel the transcription").
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { call(Request.Builder().url(url(c) + "/jobs/$jobId/cancel").post(ByteArray(0).toRequestBody(null)).build()) }
            }
            throw ce
        }
    }

    /** For History re-transcribe and the RE chat voice: Frankfurt when on and the engine runs there, else direct. */
    suspend fun runOrDirect(c: Context, id: String, wav: File, lang: String, names: List<String>, onStatus: (String) -> Unit = {}): String =
        if (on(c) && handles(id)) transcribe(c, id, wav, lang, names, onStatus) else EngineAdapters.run(c, id, wav, lang, names)
}
