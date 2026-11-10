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
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.MaProviders
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * One adapter per engine: a 16 kHz mono WAV in, the text out, or an [EngineFailure] that says why
 * (429 / 402 or a quota message = QUOTA, so the chain switches at once). Keys come from
 * [EngineKeys] and never appear in a message, a log or an exception.
 */
object EngineAdapters {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
    private val WAV = "audio/wav".toMediaType()
    private val JSONT = "application/json".toMediaType()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }

    /** The body of a 2xx answer; any other status becomes a typed failure. The body is never put in a message. */
    private suspend fun send(req: Request): String {
        http.newCall(req).await().use { r ->
            val body = runCatching { r.body.string() }.getOrDefault("")
            if (r.isSuccessful) return body
            val low = body.lowercase()
            // A retired model id is said plainly, never a silent failure (Marko / research 10.11.2026).
            if (r.code == 404 && (low.contains("model_not_found") || low.contains("decommission") || low.contains("does not exist"))) {
                throw EngineFailure(FailKind.NOT_CONFIGURED, "model retired")
            }
            val kind = when {
                r.code == 429 || r.code == 402 -> FailKind.QUOTA
                r.code == 401 -> FailKind.AUTH
                r.code == 403 -> if (low.contains("quota") || low.contains("credit") || low.contains("limit")) FailKind.QUOTA else FailKind.AUTH
                else -> FailKind.ERROR
            }
            throw EngineFailure(kind, "HTTP ${r.code}" + if (r.code == 400) ": " + body.take(140).replace(Regex("\\s+"), " ") else "")
        }
    }

    private fun lang2(lang: String) = if (lang == "en") "en" else "hr"

    private fun multipart(fields: Map<String, String>, fileField: String, wav: File): MultipartBody {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> b.addFormDataPart(k, v) }
        b.addFormDataPart(fileField, "clip.wav", wav.asRequestBody(WAV))
        return b.build()
    }

    suspend fun run(c: Context, id: String, wav: File, lang: String, names: List<String>): String {
        if (id == EngineCatalog.FAKE) throw EngineFailure(FailKind.QUOTA, "HTTP 429 (test engine)")
        return when (id) {
            "parakeet" -> parakeet(c, wav, lang, names)
            "android" -> androidRecognizer(c, wav, lang)
            "groq_turbo" -> KeyRing.run(c, "groq", "Groq") { k -> groq(k, "whisper-large-v3-turbo", wav, lang, names) }
            "groq_v3" -> KeyRing.run(c, "groq", "Groq") { k -> groq(k, "whisper-large-v3", wav, lang, names) }
            "cloudflare" -> cloudflare(c, wav, lang)
            "gemini" -> KeyRing.run(c, "gemini", "Gemini") { k -> geminiWith(k, wav, lang, names) }
            "deepgram" -> KeyRing.run(c, "deepgram", "Deepgram") { k -> deepgram(k, wav, lang, names) }
            "assemblyai" -> KeyRing.run(c, "assemblyai", "AssemblyAI") { k -> assemblyai(k, wav, lang, names) }
            "speechmatics" -> KeyRing.run(c, "speechmatics", "Speechmatics") { k -> speechmatics(k, wav, lang) }
            "soniox" -> KeyRing.run(c, "soniox", "Soniox") { k -> soniox(k, wav, lang) }
            "google_v1" -> KeyRing.run(c, "google_v1", "Google") { k -> googleV1(k, wav, lang, names) }
            "google_chirp" -> googleChirp(c, wav, lang)
            "elevenlabs" -> KeyRing.run(c, "elevenlabs", "ElevenLabs") { k -> elevenlabs(k, wav, lang) }
            else -> throw EngineFailure(FailKind.NOT_CONFIGURED, "unknown engine")
        }
    }

    private fun key(c: Context, id: String): List<String> =
        EngineKeys.lines(c, id).ifEmpty { throw EngineFailure(FailKind.NOT_CONFIGURED, "no key") }

    // ------------------------------------------------------------------ on the phone

    private suspend fun parakeet(c: Context, wav: File, lang: String, names: List<String>): String = withContext(Dispatchers.Default) {
        val local = MaProviders.localTranscriptionAccount()
        val model = MaProviders.transcriptionModelFor(c, local, ProviderRegistry.LOCAL)
        if (!LocalModelManager.isInstalled(c, model)) throw EngineFailure(FailKind.NOT_CONFIGURED, "model not installed")
        LocalTranscriptionProvider(LocalTranscriptionProvider.modelDir(c, model))
            .transcribe(TranscriptionRequest(audioFile = wav, model = model, language = lang2(lang))).text
    }

    /** SpeechRecognizer on the device (Android 13+ reads the audio from a file descriptor). */
    private suspend fun androidRecognizer(c: Context, wav: File, lang: String): String {
        if (android.os.Build.VERSION.SDK_INT < 33) throw EngineFailure(FailKind.NOT_CONFIGURED, "needs Android 13")
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(c)) throw EngineFailure(FailKind.NOT_CONFIGURED, "no on-device recognizer")
        val pcm = File(c.cacheDir, "engine_raw.pcm")
        withContext(Dispatchers.IO) { wav.inputStream().use { i -> i.skip(44); pcm.outputStream().use { o -> i.copyTo(o) } } }
        return suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(c)
                val pfd = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)
                fun finish(r: Result<String>) {
                    runCatching { sr.destroy() }; runCatching { pfd.close() }
                    if (cont.isActive) cont.resumeWith(r)
                }
                cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { runCatching { sr.destroy() }; runCatching { pfd.close() } } }
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(p: android.os.Bundle?) {}
                    override fun onEvent(t: Int, p: android.os.Bundle?) {}
                    override fun onError(e: Int) { finish(Result.failure(EngineFailure(FailKind.ERROR, "recognizer error $e"))) }
                    override fun onResults(r: android.os.Bundle?) {
                        val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        finish(Result.success(t))
                    }
                })
                val i = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang2(lang) == "en") "en-US" else "hr-HR")
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                sr.startListening(i)
            }
        }
    }

    // ------------------------------------------------------------------ cloud, one call

    /** Groq with one key; [KeyRing] rolls to the next key on a quota or refused answer. */
    /** One Groq text-to-text call (RE): faithful rewrite, same language, only the text back. Temperature low. */
    suspend fun groqChat(k: String, model: String, system: String, user: String): String {
        val msgs = org.json.JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.2).toString().toRequestBody(JSONT)
        val req = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $k").header("User-Agent", "curl/8").post(body).build()
        return JSONObject(send(req)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }

    private suspend fun groq(k: String, model: String, wav: File, lang: String, names: List<String>): String {
        val fields = linkedMapOf("model" to model, "response_format" to "json", "temperature" to "0", "language" to lang2(lang))
        if (names.isNotEmpty()) fields["prompt"] = names.joinToString(", ").take(600)
        val req = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $k").header("User-Agent", "curl/8")
            .post(multipart(fields, "file", wav)).build()
        return JSONObject(send(req)).getString("text")
    }

    private suspend fun cloudflare(c: Context, wav: File, lang: String): String {
        val k = key(c, "cloudflare")
        if (k.size < 2) throw EngineFailure(FailKind.NOT_CONFIGURED, "needs account id and token")
        val b64 = withContext(Dispatchers.IO) { Base64.encodeToString(wav.readBytes(), Base64.NO_WRAP) }
        val body = JSONObject().put("audio", b64).put("language", lang2(lang)).toString().toRequestBody(JSONT)
        val req = Request.Builder().url("https://api.cloudflare.com/client/v4/accounts/${k[0]}/ai/run/@cf/openai/whisper-large-v3-turbo")
            .header("Authorization", "Bearer ${k[1]}").post(body).build()
        return JSONObject(send(req)).getJSONObject("result").getString("text")
    }

    private suspend fun geminiWith(k: String, wav: File, lang: String, names: List<String>): String {
        val b64 = withContext(Dispatchers.IO) { Base64.encodeToString(wav.readBytes(), Base64.NO_WRAP) }
        val l = if (lang2(lang) == "en") "English" else "Croatian"
        val p = "Transcribe this speech exactly and verbatim in $l, with punctuation, no commentary." +
            if (names.isNotEmpty()) " Names that may occur: " + names.joinToString(", ").take(600) + "." else ""
        val b = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", p))
                        .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", b64))),
                ),
            ),
        )
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent")
            .header("x-goog-api-key", k).post(b.toString().toRequestBody(JSONT)).build()
        return JSONObject(send(req)).getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }

    private suspend fun deepgram(k: String, wav: File, lang: String, names: List<String>): String {
        val u = Uri.Builder().scheme("https").authority("api.deepgram.com").path("/v1/listen")
            .appendQueryParameter("model", "nova-3").appendQueryParameter("language", lang2(lang))
            .appendQueryParameter("smart_format", "true")
        names.take(50).forEach { u.appendQueryParameter("keyterm", it) }
        val req = Request.Builder().url(u.build().toString()).header("Authorization", "Token $k").post(wav.asRequestBody(WAV)).build()
        return JSONObject(send(req)).getJSONObject("results").getJSONArray("channels").getJSONObject(0)
            .getJSONArray("alternatives").getJSONObject(0).getString("transcript")
    }

    private suspend fun elevenlabs(k: String, wav: File, lang: String): String {
        val req = Request.Builder().url("https://api.elevenlabs.io/v1/speech-to-text").header("xi-api-key", k)
            .post(multipart(mapOf("model_id" to "scribe_v1", "language_code" to lang2(lang)), "file", wav)).build()
        return JSONObject(send(req)).getString("text")
    }

    private fun gLang(lang: String) = if (lang2(lang) == "en") "en-US" else "hr-HR"

    private suspend fun googleV1(k: String, wav: File, lang: String, names: List<String>): String {
        val b64 = withContext(Dispatchers.IO) { Base64.encodeToString(wav.readBytes(), Base64.NO_WRAP) }
        val cfg = JSONObject().put("encoding", "LINEAR16").put("sampleRateHertz", 16000).put("languageCode", gLang(lang))
            .put("enableAutomaticPunctuation", true)
        if (names.isNotEmpty()) cfg.put("speechContexts", JSONArray().put(JSONObject().put("phrases", JSONArray(names.take(100)))))
        val body = JSONObject().put("config", cfg).put("audio", JSONObject().put("content", b64))
        val req = Request.Builder().url("https://speech.googleapis.com/v1/speech:recognize").header("x-goog-api-key", k)
            .post(body.toString().toRequestBody(JSONT)).build()
        val res = JSONObject(send(req)).optJSONArray("results") ?: return ""
        return (0 until res.length()).joinToString(" ") { res.getJSONObject(it).getJSONArray("alternatives").getJSONObject(0).optString("transcript") }.trim()
    }

    @Volatile private var gToken: Pair<String, Long>? = null

    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** OAuth for the service account: a signed JWT exchanged for an access token (cached 50 min). */
    private suspend fun googleToken(c: Context): String {
        gToken?.let { if (it.second > System.currentTimeMillis()) return it.first }
        val sa = JSONObject(key(c, "google_chirp").first())
        val now = System.currentTimeMillis() / 1000
        val head = b64url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val uri = sa.optString("token_uri", "https://oauth2.googleapis.com/token")
        val claims = b64url(
            JSONObject().put("iss", sa.getString("client_email")).put("scope", "https://www.googleapis.com/auth/cloud-platform")
                .put("aud", uri).put("iat", now).put("exp", now + 3000).toString().toByteArray(),
        )
        val pem = sa.getString("private_key").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
        val pk = java.security.KeyFactory.getInstance("RSA").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
        val sig = java.security.Signature.getInstance("SHA256withRSA").apply { initSign(pk); update("$head.$claims".toByteArray()) }.sign()
        val form = okhttp3.FormBody.Builder().add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer").add("assertion", "$head.$claims.${b64url(sig)}").build()
        val tok = JSONObject(send(Request.Builder().url(uri).post(form).build())).getString("access_token")
        gToken = tok to System.currentTimeMillis() + 50 * 60_000L
        return tok
    }

    /** Chirp 3 on the v2 API, regional endpoint "us". */
    private suspend fun googleChirp(c: Context, wav: File, lang: String): String {
        val project = JSONObject(key(c, "google_chirp").first()).optString("project_id").ifEmpty { throw EngineFailure(FailKind.NOT_CONFIGURED, "no project id") }
        val tok = googleToken(c)
        val b64 = withContext(Dispatchers.IO) { Base64.encodeToString(wav.readBytes(), Base64.NO_WRAP) }
        val cfg = JSONObject().put("autoDecodingConfig", JSONObject()).put("languageCodes", JSONArray().put(gLang(lang))).put("model", "chirp_3")
        val body = JSONObject().put("config", cfg).put("content", b64)
        val req = Request.Builder().url("https://us-speech.googleapis.com/v2/projects/$project/locations/us/recognizers/_:recognize")
            .header("Authorization", "Bearer $tok").post(body.toString().toRequestBody(JSONT)).build()
        val res = JSONObject(send(req)).optJSONArray("results") ?: return ""
        return (0 until res.length()).joinToString(" ") { res.getJSONObject(it).getJSONArray("alternatives").getJSONObject(0).optString("transcript") }.trim()
    }

    // ------------------------------------------------------------------ cloud, upload then poll

    private suspend fun assemblyai(k: String, wav: File, lang: String, names: List<String>): String {
        val up = JSONObject(
            send(Request.Builder().url("https://api.assemblyai.com/v2/upload").header("authorization", k).post(wav.asRequestBody("application/octet-stream".toMediaType())).build()),
        ).getString("upload_url")
        val b = JSONObject().put("audio_url", up).put("language_code", lang2(lang)).put("speech_models", JSONArray().put("universal-3-5-pro").put("universal-2"))
        if (names.isNotEmpty()) b.put("word_boost", JSONArray(names.take(100)))
        val id = JSONObject(send(Request.Builder().url("https://api.assemblyai.com/v2/transcript").header("authorization", k).post(b.toString().toRequestBody(JSONT)).build())).getString("id")
        while (true) {
            val r = JSONObject(send(Request.Builder().url("https://api.assemblyai.com/v2/transcript/$id").header("authorization", k).get().build()))
            when (r.getString("status")) {
                "completed" -> return r.optString("text")
                "error" -> throw EngineFailure(FailKind.ERROR, "engine error")
            }
            delay(700)
        }
    }

    private suspend fun speechmatics(k: String, wav: File, lang: String): String {
        val cfg = JSONObject().put("type", "transcription").put("transcription_config", JSONObject().put("language", lang2(lang)))
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("config", cfg.toString()).addFormDataPart("data_file", "clip.wav", wav.asRequestBody(WAV)).build()
        val id = JSONObject(send(Request.Builder().url("https://asr.api.speechmatics.com/v2/jobs").header("Authorization", "Bearer $k").post(form).build())).getString("id")
        while (true) {
            val st = JSONObject(send(Request.Builder().url("https://asr.api.speechmatics.com/v2/jobs/$id").header("Authorization", "Bearer $k").get().build()))
                .getJSONObject("job").getString("status")
            if (st == "done") break
            if (st == "rejected" || st == "deleted") throw EngineFailure(FailKind.ERROR, "job $st")
            delay(800)
        }
        return send(Request.Builder().url("https://asr.api.speechmatics.com/v2/jobs/$id/transcript?format=txt").header("Authorization", "Bearer $k").get().build()).trim()
    }

    private suspend fun soniox(k: String, wav: File, lang: String): String {
        val auth = "Bearer $k"
        val fileId = JSONObject(
            send(Request.Builder().url("https://api.soniox.com/v1/files").header("Authorization", auth).post(multipart(emptyMap(), "file", wav)).build()),
        ).getString("id")
        val body = JSONObject().put("model", "stt-async-v6").put("file_id", fileId).put("language_hints", JSONArray().put(lang2(lang)))
        val id = JSONObject(send(Request.Builder().url("https://api.soniox.com/v1/transcriptions").header("Authorization", auth).post(body.toString().toRequestBody(JSONT)).build())).getString("id")
        try {
            while (true) {
                val st = JSONObject(send(Request.Builder().url("https://api.soniox.com/v1/transcriptions/$id").header("Authorization", auth).get().build())).getString("status")
                if (st == "completed") break
                if (st == "error") throw EngineFailure(FailKind.ERROR, "engine error")
                delay(600)
            }
            return JSONObject(send(Request.Builder().url("https://api.soniox.com/v1/transcriptions/$id/transcript").header("Authorization", auth).get().build())).getString("text")
        } finally {
            runCatching { http.newCall(Request.Builder().url("https://api.soniox.com/v1/files/$fileId").header("Authorization", auth).delete().build()).execute().close() }
        }
    }

    /** A tiny test: only the HTTP status, never a body. Used to prove a key answers. */
    suspend fun ping(c: Context, id: String, wav: File): String = try {
        run(c, id, wav, "en", emptyList()); "answers (200)"
    } catch (f: EngineFailure) { "${f.kind} (${f.message})" } catch (t: Throwable) { "failed (${t.javaClass.simpleName})" }
}
