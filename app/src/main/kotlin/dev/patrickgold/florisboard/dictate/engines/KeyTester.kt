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

import android.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class KStatus { WORKING, LIMITED, DEAD, NO_BALANCE, NETWORK }

/**
 * Tests one key with the cheapest call its provider has: a list or an auth call, never a message, a
 * generation or a transcription (spend gate). Only the HTTP status and, for Google, the status word in
 * the answer are read; no key and no answer body is ever stored, shown or logged. Endpoints are the
 * ones of Key_Tester and KeyRingTester (shared/endpoints.json).
 */
object KeyTester {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
    private val JSONT = "application/json".toMediaType()
    private const val UA = "curl/8"

    private fun rate(code: Int, body: String): KStatus {
        val low = body.lowercase()
        return when {
            code in 200..299 -> KStatus.WORKING
            code == 429 -> KStatus.LIMITED
            code == 402 -> KStatus.NO_BALANCE
            code == 401 -> KStatus.DEAD
            code == 403 -> if (low.contains("balance") || low.contains("credit") || low.contains("insufficient")) KStatus.NO_BALANCE else KStatus.DEAD
            code == 400 && (low.contains("api key not valid") || low.contains("api_key_invalid")) -> KStatus.DEAD
            else -> KStatus.NETWORK
        }
    }

    private fun call(req: Request): Pair<Int, String> =
        http.newCall(req).execute().use { r -> r.code to runCatching { r.body.string() }.getOrDefault("").take(2000) }

    private fun get(url: String, vararg h: Pair<String, String>): Request {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        h.forEach { b.header(it.first, it.second) }
        return b.get().build()
    }

    private fun once(req: Request): KStatus = try {
        val (c, b) = call(req); rate(c, b)
    } catch (t: Throwable) { KStatus.NETWORK }

    /** Test [value] as [kind] (a ring id: groq, gemini, google_v1, deepgram, assemblyai, speechmatics, soniox, elevenlabs, speechify, anthropic, cloudflare, google_chirp). */
    suspend fun test(kind: String, value: String): KStatus = withContext(Dispatchers.IO) {
        var s = testOnce(kind, value)
        if (s == KStatus.NETWORK) s = testOnce(kind, value)
        s
    }

    private fun testOnce(kind: String, v: String): KStatus = when (kind) {
        "groq" -> once(get("https://api.groq.com/openai/v1/models", "Authorization" to "Bearer $v"))
        "anthropic" -> once(get("https://api.anthropic.com/v1/models", "x-api-key" to v, "anthropic-version" to "2023-06-01"))
        "assemblyai" -> once(get("https://api.assemblyai.com/v2/transcript?limit=1", "authorization" to v))
        "deepgram" -> once(get("https://api.deepgram.com/v1/projects", "Authorization" to "Token $v"))
        "elevenlabs" -> once(get("https://api.elevenlabs.io/v1/user", "xi-api-key" to v))
        "speechify" -> once(get("https://api.sws.speechify.com/v1/voices", "Authorization" to "Bearer $v"))
        "speechmatics" -> once(get("https://asr.api.speechmatics.com/v2/jobs?limit=1", "Authorization" to "Bearer $v"))
        "soniox" -> once(get("https://api.soniox.com/v1/files", "Authorization" to "Bearer $v"))
        "gemini" -> once(get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1", "x-goog-api-key" to v))
        "google_v1" -> speechV1(v)
        "cloudflare" -> cloudflare(v)
        "google_chirp" -> serviceAccount(v)
        else -> KStatus.NETWORK
    }

    /** Google Speech v1 with an empty audio field: 400 means the key is valid and the API on, 403 means the API is off. No audio, no billing. */
    private fun speechV1(k: String): KStatus = try {
        val body = """{"config":{"languageCode":"en-US"},"audio":{"content":""}}""".toRequestBody(JSONT)
        val (c, b) = call(Request.Builder().url("https://speech.googleapis.com/v1/speech:recognize").header("x-goog-api-key", k).post(body).build())
        val low = b.lowercase()
        when {
            c == 400 && !(low.contains("api key not valid") || low.contains("api_key_invalid")) -> KStatus.WORKING
            c in 200..299 -> KStatus.WORKING
            c == 429 -> KStatus.LIMITED
            else -> KStatus.DEAD
        }
    } catch (t: Throwable) { KStatus.NETWORK }

    private fun cloudflare(v: String): KStatus {
        val l = v.lines().filter { it.isNotBlank() }
        if (l.size < 2) return KStatus.DEAD
        val a = once(get("https://api.cloudflare.com/client/v4/accounts/${l[0]}/tokens/verify", "Authorization" to "Bearer ${l[1]}"))
        if (a == KStatus.WORKING || a == KStatus.NETWORK) return a
        return once(get("https://api.cloudflare.com/client/v4/user/tokens/verify", "Authorization" to "Bearer ${l[1]}"))
    }

    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** A service account is good when Google hands out an access token for it (the same JWT exchange the Chirp engine does). */
    private fun serviceAccount(v: String): KStatus = try {
        val sa = JSONObject(v)
        val now = System.currentTimeMillis() / 1000
        val uri = sa.optString("token_uri", "https://oauth2.googleapis.com/token")
        val head = b64url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claims = b64url(
            JSONObject().put("iss", sa.getString("client_email")).put("scope", "https://www.googleapis.com/auth/cloud-platform")
                .put("aud", uri).put("iat", now).put("exp", now + 600).toString().toByteArray(),
        )
        val pem = sa.getString("private_key").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
        val pk = java.security.KeyFactory.getInstance("RSA").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
        val sig = java.security.Signature.getInstance("SHA256withRSA").apply { initSign(pk); update("$head.$claims".toByteArray()) }.sign()
        val form = FormBody.Builder().add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer").add("assertion", "$head.$claims.${b64url(sig)}").build()
        val (c, _) = call(Request.Builder().url(uri).post(form).build())
        when {
            c in 200..299 -> KStatus.WORKING
            c == 400 || c == 401 || c == 403 -> KStatus.DEAD
            c == 429 -> KStatus.LIMITED
            else -> KStatus.NETWORK
        }
    } catch (e: java.io.IOException) { KStatus.NETWORK } catch (t: Throwable) { KStatus.DEAD }
}
