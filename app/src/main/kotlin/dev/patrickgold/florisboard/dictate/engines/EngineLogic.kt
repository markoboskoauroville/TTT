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

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/*
 * Engines + benchmark (Marko, 9.11.2026): many free speech engines, a fallback chain per language,
 * and TTT scoring each engine on his own recordings. Everything in this file is plain Kotlin with no
 * Android types, so the unit tests in CI run it as it is: the catalogue, the quota windows, the
 * word error rate and the fallback walk. Figures come from docs/stt-providers.md (9.11.2026).
 * Never OpenAI.
 */

enum class QuotaReset(val label: String) { HOURLY("hourly"), DAILY("daily"), MONTHLY("monthly"), ONE_OFF("one-off credit"), NONE("none") }

enum class EngineKind { LOCAL, CLOUD }

/**
 * One engine. [freeSeconds] is the free audio per reset period (Long.MAX_VALUE: unlimited, on the
 * phone); 0 with [paid] true means no free quota is known, so it only runs when Marko enables it.
 */
data class EngineSpec(
    val id: String,
    val name: String,
    val kind: EngineKind,
    val freeSeconds: Long,
    val reset: QuotaReset,
    val pricePerHour: Double,
    val paid: Boolean,
    val keyHelp: String,
    val note: String,
) {
    val needsKey: Boolean get() = kind == EngineKind.CLOUD
}

object EngineCatalog {
    const val FAKE = "fake429"

    val ALL: List<EngineSpec> = listOf(
        EngineSpec("parakeet", "Parakeet (on the phone)", EngineKind.LOCAL, Long.MAX_VALUE, QuotaReset.NONE, 0.0, false, "", "free, offline"),
        EngineSpec("android", "Android on-device", EngineKind.LOCAL, Long.MAX_VALUE, QuotaReset.NONE, 0.0, false, "", "free, offline, needs the language pack"),
        EngineSpec("groq_turbo", "Groq Whisper turbo", EngineKind.CLOUD, 28_800, QuotaReset.DAILY, 0.04, false, "Groq key (gsk_...)", "free 28,800 audio s/day"),
        EngineSpec("groq_v3", "Groq Whisper large-v3", EngineKind.CLOUD, 28_800, QuotaReset.DAILY, 0.111, false, "Groq key (gsk_...)", "free 28,800 audio s/day"),
        EngineSpec("cloudflare", "Cloudflare Workers AI Whisper", EngineKind.CLOUD, 12_840, QuotaReset.DAILY, 0.03, false, "account id and token, one per line", "free 10,000 neurons/day, about 214 min"),
        EngineSpec("gemini", "Gemini Flash-Lite audio", EngineKind.CLOUD, 3_600, QuotaReset.DAILY, 0.04, false, "Gemini API key (AIza...)", "free tier, rate limited (about 1 h/day assumed)"),
        EngineSpec("deepgram", "Deepgram Nova-3", EngineKind.CLOUD, 2_300_000, QuotaReset.ONE_OFF, 0.31, false, "Deepgram key", "USD 200 credit"),
        EngineSpec("assemblyai", "AssemblyAI", EngineKind.CLOUD, 1_200_000, QuotaReset.ONE_OFF, 0.15, false, "AssemblyAI key", "USD 50 credit"),
        EngineSpec("speechmatics", "Speechmatics", EngineKind.CLOUD, 1_500_000, QuotaReset.ONE_OFF, 0.24, false, "Speechmatics key", "USD 100 credit"),
        EngineSpec("google_v1", "Google Speech v1", EngineKind.CLOUD, 3_600, QuotaReset.MONTHLY, 1.44, false, "Google Cloud Speech API key (AIza...)", "free 60 min/month, then USD 0.024/min; clips up to 1 min"),
        EngineSpec("google_chirp", "Google Chirp 3 (v2)", EngineKind.CLOUD, 3_600, QuotaReset.MONTHLY, 0.96, false, "service account JSON (OAuth)", "assumed 60 min/month free (unverified), then USD 0.016/min"),
        EngineSpec("soniox", "Soniox", EngineKind.CLOUD, 0, QuotaReset.NONE, 0.10, true, "Soniox key", "no free quota shown: only when enabled, USD 0.10/h"),
        EngineSpec("elevenlabs", "ElevenLabs Scribe", EngineKind.CLOUD, 0, QuotaReset.NONE, 0.22, true, "ElevenLabs key", "no free quota shown: only when enabled, USD 0.22/h"),
    )

    /** The debug-only provider that always answers 429, to show the switch. */
    val FAKE_SPEC = EngineSpec(FAKE, "Test engine (always 429)", EngineKind.CLOUD, Long.MAX_VALUE, QuotaReset.NONE, 0.0, false, "", "debug")

    /** The name written on a key: short, one or two words. */
    fun shortName(id: String): String = when (id) {
        "parakeet" -> "Parakeet"
        "android" -> "Android"
        "groq_turbo" -> "Groq turbo"
        "groq_v3" -> "Groq"
        "cloudflare" -> "Cloudflare"
        "gemini" -> "Gemini"
        "deepgram" -> "Deepgram"
        "assemblyai" -> "AssemblyAI"
        "speechmatics" -> "Speechmatics"
        "google_v1" -> "Google"
        "google_chirp" -> "Chirp 3"
        "soniox" -> "Soniox"
        "elevenlabs" -> "ElevenLabs"
        FAKE -> "Test 429"
        else -> id
    }

    fun byId(id: String): EngineSpec? = if (id == FAKE) FAKE_SPEC else ALL.firstOrNull { it.id == id }

    /** Recommended chains from docs/stt-providers.md, the rest appended so every engine has a place. */
    val DEFAULT_EN = listOf("parakeet", "groq_turbo", "cloudflare", "deepgram", "assemblyai", "gemini", "speechmatics", "groq_v3", "android", "google_chirp", "google_v1", "soniox", "elevenlabs")
    val DEFAULT_HR = listOf("groq_v3", "elevenlabs", "deepgram", "parakeet", "cloudflare", "gemini", "assemblyai", "speechmatics", "groq_turbo", "android", "google_chirp", "google_v1", "soniox")

    fun defaultOrder(lang: String): List<String> = if (lang == "en") DEFAULT_EN else DEFAULT_HR

    /** A stored order made whole: unknown ids dropped, duplicates collapsed, missing ones appended. */
    fun complete(stored: List<String>, lang: String): List<String> {
        val known = (ALL.map { it.id } + FAKE).toSet()
        val out = LinkedHashSet<String>()
        stored.filter { it in known }.forEach { out.add(it) }
        defaultOrder(lang).forEach { out.add(it) }
        ALL.forEach { out.add(it.id) }
        return out.toList()
    }
}

/** Quota windows. Daily windows reset at 00:00 UTC (Cloudflare's), monthly on the first, UTC. */
object Quota {
    data class Usage(val windowStart: Long = 0L, val seconds: Double = 0.0, val costUsd: Double = 0.0)

    private fun cal(now: Long) = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply { timeInMillis = now }

    fun windowStart(reset: QuotaReset, now: Long): Long {
        val c = cal(now)
        return when (reset) {
            QuotaReset.HOURLY -> { c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); c.timeInMillis }
            QuotaReset.DAILY -> { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); c.timeInMillis }
            QuotaReset.MONTHLY -> { c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); c.timeInMillis }
            else -> 0L
        }
    }

    /** When the window next resets, or null for one-off and none. */
    fun nextReset(reset: QuotaReset, now: Long): Long? {
        val start = windowStart(reset, now)
        val c = cal(start)
        when (reset) {
            QuotaReset.HOURLY -> c.add(Calendar.HOUR_OF_DAY, 1)
            QuotaReset.DAILY -> c.add(Calendar.DAY_OF_MONTH, 1)
            QuotaReset.MONTHLY -> c.add(Calendar.MONTH, 1)
            else -> return null
        }
        return c.timeInMillis
    }

    /** Usage with a stale window thrown away. */
    fun current(spec: EngineSpec, u: Usage, now: Long): Usage {
        val ws = windowStart(spec.reset, now)
        return if (spec.reset == QuotaReset.ONE_OFF || spec.reset == QuotaReset.NONE) u
        else if (u.windowStart != ws) Usage(ws, 0.0, 0.0) else u
    }

    /** Free audio seconds left (Long.MAX_VALUE for unlimited). Paid engines with no free quota: 0. */
    fun remainingSeconds(spec: EngineSpec, u: Usage, now: Long): Long {
        if (spec.freeSeconds == Long.MAX_VALUE) return Long.MAX_VALUE
        val used = current(spec, u, now).seconds
        return (spec.freeSeconds - used).toLong().coerceAtLeast(0L)
    }

    fun add(spec: EngineSpec, u: Usage, seconds: Double, now: Long): Usage {
        val c = current(spec, u, now)
        val freeLeft = if (spec.freeSeconds == Long.MAX_VALUE) Double.MAX_VALUE else (spec.freeSeconds - c.seconds).coerceAtLeast(0.0)
        val paidSeconds = (seconds - freeLeft).coerceAtLeast(0.0)
        return Usage(windowStart(spec.reset, now), c.seconds + seconds, c.costUsd + paidSeconds / 3600.0 * spec.pricePerHour)
    }

    /**
     * Spend-aware: may [seconds] of audio go to this engine without leaving the free quota?
     * A paid engine (no free quota) only when [paidAllowed] (explicitly enabled).
     */
    fun canAfford(spec: EngineSpec, u: Usage, seconds: Double, now: Long, paidAllowed: Boolean): Boolean {
        if (spec.paid) return paidAllowed
        return remainingSeconds(spec, u, now) >= seconds
    }
}

/** Word error rate and name errors, ported from tools/stt_bakeoff/run_bakeoff.py. */
object Wer {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}_\\s]")

    fun norm(t: String): List<String> {
        val n = Normalizer.normalize(t.lowercase(Locale.ROOT), Normalizer.Form.NFC)
        return NON_WORD.replace(n, " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    /** Word-level edit distance. */
    fun distance(r: List<String>, h: List<String>): Int {
        val d = IntArray(h.size + 1) { it }
        for (i in 1..r.size) {
            var prev = d[0]
            d[0] = i
            for (j in 1..h.size) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (r[i - 1] == h[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[h.size]
    }

    data class Score(val errors: Int, val words: Int, val nameErrors: Int, val names: Int)

    /**
     * [names] are the canonical spellings (dictation_corrections.json keyterms): a name counts when
     * it is in the reference, and is an error when the hypothesis does not contain it.
     */
    fun score(ref: String, hyp: String, names: List<String>): Score {
        val r = norm(ref)
        val h = norm(hyp)
        val rj = " " + r.joinToString(" ") + " "
        val hj = " " + h.joinToString(" ") + " "
        var inRef = 0
        var missed = 0
        for (n in names.map { norm(it).joinToString(" ") }.filter { it.isNotEmpty() }.distinct()) {
            if (rj.contains(" $n ")) {
                inRef++
                if (!hj.contains(" $n ")) missed++
            }
        }
        return Score(distance(r, h), r.size, missed, inRef)
    }
}

enum class FailKind { QUOTA, TIMEOUT, NETWORK, AUTH, ERROR, NOT_CONFIGURED }

class EngineFailure(val kind: FailKind, message: String) : Exception(message)

/** One engine in a chain: what it is called and what to run. [quotaLeft] null means unknown/unlimited. */
class Candidate(
    val id: String,
    val name: String,
    val quotaLeftSeconds: Long?,
    val isLocal: Boolean,
    val call: suspend () -> String,
)

data class Attempt(val id: String, val ok: Boolean, val kind: FailKind?, val ms: Long)

data class ChainResult(val text: String, val engineId: String, val engineName: String, val ms: Long, val attempts: List<Attempt>)

class AllEnginesFailed(val attempts: List<Attempt>) : Exception("every engine failed")

/**
 * The fallback walk. On an error, a timeout (bounded by [timeoutMs] per engine), a 429 or an
 * exhausted quota the next engine starts at once, with no pause in between. The status line says
 * what is happening in plain words.
 */
object FallbackRunner {

    fun classify(t: Throwable): FailKind = when (t) {
        is EngineFailure -> t.kind
        is TimeoutCancellationException -> FailKind.TIMEOUT
        is java.net.UnknownHostException, is java.net.ConnectException -> FailKind.NETWORK
        is java.net.SocketTimeoutException -> FailKind.TIMEOUT
        is java.io.IOException -> FailKind.NETWORK
        else -> FailKind.ERROR
    }

    private fun reason(k: FailKind) = when (k) {
        FailKind.QUOTA -> "out of quota"
        FailKind.TIMEOUT -> "timed out"
        FailKind.NETWORK -> "unreachable"
        FailKind.AUTH -> "key refused"
        FailKind.NOT_CONFIGURED -> "not set up"
        FailKind.ERROR -> "failed"
    }

    fun seconds(ms: Long) = String.format(Locale.ROOT, "%.1f", ms / 1000.0)

    suspend fun run(
        chain: List<Candidate>,
        timeoutMs: Long,
        onStatus: (String) -> Unit,
        clock: () -> Long = { System.currentTimeMillis() },
    ): ChainResult {
        val attempts = ArrayList<Attempt>()
        val t0 = clock()
        for ((i, c) in chain.withIndex()) {
            val next = chain.drop(i + 1).firstOrNull()
            // Out of quota before even trying: switch at once.
            if (c.quotaLeftSeconds != null && c.quotaLeftSeconds <= 0L) {
                attempts.add(Attempt(c.id, false, FailKind.QUOTA, 0))
                if (next != null) onStatus("${c.name} out of quota → switching to ${next.name}")
                continue
            }
            onStatus("Sending to ${c.name}…")
            val s = clock()
            try {
                val text = withTimeout(timeoutMs) { c.call() }
                val ms = clock() - s
                attempts.add(Attempt(c.id, true, null, ms))
                onStatus("done (${c.name}, ${seconds(clock() - t0)} s)")
                return ChainResult(text, c.id, c.name, clock() - t0, attempts)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException && t !is TimeoutCancellationException) throw t
                val k = classify(t)
                attempts.add(Attempt(c.id, false, k, clock() - s))
                if (next != null) {
                    onStatus(
                        if (k == FailKind.NETWORK && next.isLocal) "offline → on-device"
                        else "${c.name} ${reason(k)} → switching to ${next.name}",
                    )
                }
            }
        }
        onStatus("no engine answered")
        throw AllEnginesFailed(attempts)
    }
}

/** What the benchmark measures and how it picks the best. */
object BenchMath {
    data class Row(
        val engineId: String,
        val lang: String,
        val clips: Int,
        val errors: Int,
        val words: Int,
        val nameErrors: Int,
        val names: Int,
        val avgMs: Long,
        val costUsd: Double,
        val failed: Int,
    ) {
        val wer: Double get() = if (words == 0) 1.0 else errors.toDouble() / words
    }

    fun aggregate(engineId: String, lang: String, scores: List<Wer.Score>, latenciesMs: List<Long>, costUsd: Double, failed: Int): Row =
        Row(
            engineId, lang, scores.size,
            scores.sumOf { it.errors }, scores.sumOf { it.words },
            scores.sumOf { it.nameErrors }, scores.sumOf { it.names },
            if (latenciesMs.isEmpty()) 0L else latenciesMs.sum() / latenciesMs.size,
            costUsd, failed,
        )

    /** Best first: lowest WER, then fewest name errors, then fastest. Engines with no clips scored go last, in their old order. */
    fun bestOrder(rows: List<Row>, oldOrder: List<String>): List<String> {
        val scored = rows.filter { it.clips > 0 }
            .sortedWith(compareBy<Row> { it.wer }.thenBy { it.nameErrors }.thenBy { it.avgMs })
            .map { it.engineId }
        return scored + oldOrder.filter { it !in scored }
    }
}

/** Reading a key out of a pasted or pushed file: the first long token, or two lines for Cloudflare. */
object KeyText {
    private val ACCT = Regex("\\b[0-9a-f]{32}\\b")

    fun firstKeys(text: String): List<String> =
        text.split(Regex("\\s+")).map { it.trim().trim('"', '\'', ',') }
            .map { if (it.contains('=') && !it.startsWith("http")) it.substringAfter('=') else it }
            .filter { it.length >= 20 && !it.contains("://") }

    /** Cloudflare: account id (32 hex, found in the dashboard URL) and token, joined with a newline. */
    fun cloudflare(text: String): String? {
        val acct = ACCT.find(text)?.value ?: return null
        val token = firstKeys(text).firstOrNull { it != acct && it.contains('_') && !it.contains('/') } ?: return null
        return acct + "\n" + token
    }

    /** The service account file as one line, only when it holds what OAuth needs. */
    fun serviceAccount(text: String): String? = runCatching {
        val o = org.json.JSONObject(text)
        if (o.optString("client_email").isEmpty() || !o.optString("private_key").contains("PRIVATE KEY")) null
        else org.json.JSONObject().put("client_email", o.getString("client_email")).put("private_key", o.getString("private_key"))
            .put("project_id", o.optString("project_id")).put("token_uri", o.optString("token_uri", "https://oauth2.googleapis.com/token")).toString()
    }.getOrNull()

    fun forEngine(id: String, text: String): String? = when (id) {
        "google_chirp" -> serviceAccount(text)
        "cloudflare" -> cloudflare(text)
        "gemini" -> firstKeys(text).take(30).joinToString("\n").ifEmpty { null }
        "groq_turbo", "groq_v3" -> firstKeys(text).filter { it.startsWith("gsk_") }.joinToString("\n").ifEmpty { null }
        else -> firstKeys(text).firstOrNull()
    }
}

/** The chosen engine could not finish (every key out, not set up, or no answer after the retries). The audio is kept by the caller; no other engine is tried. */
class EngineGaveUp(message: String) : Exception(message)
