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
import dev.patrickgold.florisboard.dictate.MaSpelling
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import java.io.File

/** Builds the chain for a language from the stored order and runs it. */
object EngineChain {

    /** The canonical names (dictation_corrections.json), for prompts and for the name-error count. */
    fun names(): List<String> = runCatching { MaSpelling.keyterms() }.getOrDefault(emptyList())

    /** Is this engine usable right now on this phone (key present, model installed, Android new enough). */
    fun available(c: Context, id: String): Boolean = when (id) {
        EngineCatalog.FAKE -> EngineStore.fakeOn(c)
        "parakeet" -> runCatching { LocalModelManager.installedIds(c).isNotEmpty() }.getOrDefault(false)
        "android" -> android.os.Build.VERSION.SDK_INT >= 33 &&
            runCatching { android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(c) }.getOrDefault(false)
        else -> EngineKeys.has(c, id)
    }

    /** Enabled and available, in the order for [lang]. */
    fun usableIds(c: Context, lang: String): List<String> {
        val ids = EngineStore.order(c, lang).filter { EngineStore.enabled(c, it) && available(c, it) }
        return if (EngineStore.fakeOn(c)) listOf(EngineCatalog.FAKE) + ids.filter { it != EngineCatalog.FAKE } else ids.filter { it != EngineCatalog.FAKE }
    }

    fun quotaLeft(c: Context, spec: EngineSpec): Long? =
        if (spec.paid || spec.freeSeconds == Long.MAX_VALUE) null else Quota.remainingSeconds(spec, EngineStore.usage(c, spec.id), System.currentTimeMillis())

    fun candidate(c: Context, id: String, wav: File, lang: String, record: Boolean = true): Candidate {
        val spec = EngineCatalog.byId(id)!!
        val names = names()
        val sec = ClipBuffer.seconds(wav)
        return Candidate(id, spec.name.substringBefore(" (").let { if (id == "parakeet") "Parakeet" else it }, quotaLeft(c, spec), spec.kind == EngineKind.LOCAL) {
            try {
                val t = EngineAdapters.run(c, id, wav, lang, names)
                if (record) EngineStore.addUsage(c, id, sec)
                t
            } catch (f: EngineFailure) {
                // A real 429 means the quota is gone for this window whatever our own count says.
                if (f.kind == FailKind.QUOTA && record && spec.freeSeconds != Long.MAX_VALUE && !spec.paid) {
                    EngineStore.addUsage(c, id, Quota.remainingSeconds(spec, EngineStore.usage(c, id), System.currentTimeMillis()).toDouble())
                }
                throw f
            }
        }
    }

    const val MAX_RETRIES = 10

    /** An HTTP 4xx that no retry can change (not 408 timeout, 409, 425, 429 rate limit). Pure, for the tests. */
    fun permanent(message: String?): Boolean {
        val m = Regex("^HTTP (4\\d\\d)").find(message?.trim().orEmpty()) ?: return false
        return m.groupValues[1] !in setOf("408", "409", "425", "429")
    }

    fun timeoutMs(sec: Double, local: Boolean) = if (local) 40_000L else 8_000L + (sec * 500).toLong()

    /**
     * The dictation entry. The key's own engine ([first]) goes first; the others follow in the stored
     * order and are tried only after a real failure (error, timeout, quota). [skip] holds an engine
     * that already failed outside this chain (the old AssemblyAI path). Returns null when no engine
     * answers or none is set up, so the caller's old path goes on. The key label shows the engine that
     * is really answering ([EngineLive]) and the status line says every switch.
     */
    suspend fun dictate(
        c: Context,
        wav: File,
        lang: String,
        first: String?,
        skip: Set<String>,
        legacyFailed: String?,
        onStatus: (String) -> Unit,
    ): ChainResult? {
        val l = if (lang == "en") "en" else "hr"
        val letter = if (l == "en") "E" else "H"
        var ids = usableIds(c, l).filter { it !in skip }
        // FRA on + Frankfurt runs the key's engine: that engine, even if it is not "usable" here (its keys live in Frankfurt).
        val fraFirst = first != null && first !in skip && FrankfurtRelay.on(c) && FrankfurtRelay.handles(first)
        if (first != null && (first in ids || fraFirst)) ids = listOf(first)
        // NEVER ANOTHER ENGINE (rule 11; found by the matrix 28.11.2026): the key's engine is not usable here (no key on
        // this phone, model not installed, switched off) and Frankfurt is not running it. Before, the first usable engine
        // of the stored order answered instead, without a word. Now the take stops with the audio kept and says why.
        else if (first != null) {
            val name = EngineCatalog.shortName(first)
            onStatus("$letter \u00b7 $name is not set up on this phone" + if (FrankfurtRelay.handles(first)) " \u00b7 FRA on runs it in Frankfurt" else "")
            throw EngineGaveUp("$name is not set up on this phone (no key or model)" + if (FrankfurtRelay.handles(first)) "; with FRA on, Frankfurt runs it" else "")
        }
        if (ids.isEmpty()) return null
        val sec = ClipBuffer.seconds(wav)
        // NEVER another engine by itself (Marko, 10.11.2026: "Change models. Automatically never do that."): the engine he chose is
        // the only one used. No answer / timeout / network error: wait 3 s and try the SAME engine again, up to 10 retries, the
        // state on the key line. A key that is out of quota or refused moves the ring to the next key of the SAME provider
        // (KeyRing); when every key of it is out the dictation stops and says so. The audio is kept by the caller.
        val id = ids.first()
        // FRANKFURT (Marko, 14.11.2026): the same engine, the same rules, but the call, the key ring and the retries run
        // on chromium-vm; the phone only sends the audio and shows Frankfurt's status. Off = everything below, direct.
        if (FrankfurtRelay.on(c) && FrankfurtRelay.handles(id)) {
            val name = EngineCatalog.shortName(id)
            val t0 = System.currentTimeMillis()
            try {
                EngineLive.setActive(l, id)
                val text = FrankfurtRelay.transcribe(c, id, wav, l, names()) { m -> onStatus("$letter · $m") }
                EngineStore.addUsage(c, id, sec)
                val ms = System.currentTimeMillis() - t0
                onStatus("done ($name via Frankfurt, ${FallbackRunner.seconds(ms)} s)")
                return ChainResult(text, id, name, ms, listOf(Attempt(id, true, null, ms)))
            } finally {
                EngineLive.setActive(l, null)
            }
        }
        val cand = candidate(c, id, wav, l)
        KeyRing.statusSink = { m -> onStatus("$letter \u00b7 $m") }
        val t0 = System.currentTimeMillis()
        try {
            var attempt = 0
            while (true) {
                EngineLive.setActive(l, id)
                onStatus(if (attempt == 0) "Sending to ${cand.name}\u2026" else "$letter \u00b7 ${cand.name} \u00b7 retry $attempt/$MAX_RETRIES")
                try {
                    val s = System.currentTimeMillis()
                    val text = kotlinx.coroutines.withTimeout(timeoutMs(sec, cand.isLocal)) { cand.call() }
                    val ms = System.currentTimeMillis()
                    onStatus("done (${cand.name}, ${FallbackRunner.seconds(ms - t0)} s)")
                    return ChainResult(text, cand.id, cand.name, ms - t0, listOf(Attempt(cand.id, true, null, ms - s)))
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException && t !is kotlinx.coroutines.TimeoutCancellationException) throw t
                    val k = FallbackRunner.classify(t)
                    if (k == FailKind.QUOTA || k == FailKind.AUTH) {
                        onStatus("$letter \u00b7 ${cand.name}: every key is out")
                        throw EngineGaveUp("${cand.name}: every key is out of free use or refused")
                    }
                    if (k == FailKind.NOT_CONFIGURED) throw EngineGaveUp("${cand.name} is not set up")
                    // A plain refusal of THIS request (HTTP 400 / 404 / 413 / 415 ...) is the same on every retry: say it now
                    // instead of after 10 x 3 s (28.11.2026). Timeouts, network errors and 5xx are still retried.
                    if (k == FailKind.ERROR && permanent(t.message)) {
                        onStatus("$letter \u00b7 ${cand.name}: ${t.message?.take(60)} \u00b7 audio kept")
                        throw EngineGaveUp("${cand.name} refused this audio: ${t.message?.take(100)}")
                    }
                    attempt++
                    if (attempt > MAX_RETRIES) {
                        onStatus("$letter \u00b7 ${cand.name} \u00b7 gave up after $MAX_RETRIES tries, audio kept")
                        throw EngineGaveUp("${cand.name} did not answer after $MAX_RETRIES tries")
                    }
                    // The reason in plain words; for an error the adapter's own short message ("HTTP 503"), never a key (28.11.2026).
                    val why = if (k == FailKind.NETWORK) "no internet" else if (k == FailKind.TIMEOUT) "no answer" else (t.message?.take(60)?.ifBlank { null } ?: "failed")
                    onStatus("$letter \u00b7 ${cand.name} \u00b7 retry $attempt/$MAX_RETRIES \u00b7 $why")
                    kotlinx.coroutines.delay(3000)
                }
            }
        } finally {
            KeyRing.statusSink = null
            EngineLive.setActive(l, null)
        }
    }
}
