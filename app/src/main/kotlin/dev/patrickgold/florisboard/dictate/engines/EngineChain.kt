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
     * The dictation entry: stages the clip and runs the chain (when [runChain]). Returns null when
     * no engine answers or none is set up, so the caller's old path goes on.
     */
    suspend fun dictate(c: Context, wav: File, lang: String, runChain: Boolean, onStatus: (String) -> Unit): ChainResult? {
        val l = if (lang == "en") "en" else "hr"
        var result: ChainResult? = null
        if (runChain) {
            val ids = usableIds(c, l)
            if (ids.isNotEmpty()) {
                val sec = ClipBuffer.seconds(wav)
                val chain = ids.map { candidate(c, it, wav, l) }
                // The local engine in the chain is bounded longer; the rest by clip length.
                result = try {
                    FallbackRunner.run(chain, timeoutMs(sec, false), onStatus)
                } catch (e: AllEnginesFailed) {
                    onStatus("all engines failed, using the old path")
                    null
                }
            }
        }
        if (result != null) ClipBuffer.add(c, wav, l, result.text, result.engineId)
        return result
    }
}
