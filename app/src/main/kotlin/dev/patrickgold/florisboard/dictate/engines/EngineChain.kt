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
        if (first != null && first in ids) ids = listOf(first) + ids.filter { it != first }
        if (ids.isEmpty()) return null
        val sec = ClipBuffer.seconds(wav)
        val chain = ids.map { id ->
            val cand = candidate(c, id, wav, l)
            Candidate(cand.id, cand.name, cand.quotaLeftSeconds, cand.isLocal) {
                EngineLive.setActive(l, id)
                cand.call()
            }
        }
        if (legacyFailed != null) onStatus("$letter \u00b7 $legacyFailed failed \u2192 switching to ${chain.first().name}")
        return try {
            FallbackRunner.run(chain, timeoutMs(sec, false), { s -> onStatus(if (s.contains("\u2192")) "$letter \u00b7 $s" else s) })
        } catch (e: AllEnginesFailed) {
            onStatus("$letter \u00b7 all engines failed")
            null
        } finally {
            EngineLive.setActive(l, null)
        }
    }
}
