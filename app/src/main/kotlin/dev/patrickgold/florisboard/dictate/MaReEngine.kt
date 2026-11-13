/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
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
import dev.patrickgold.florisboard.dictate.engines.EngineAdapters
import dev.patrickgold.florisboard.dictate.engines.EngineFailure
import dev.patrickgold.florisboard.dictate.engines.FailKind
import dev.patrickgold.florisboard.dictate.engines.FallbackRunner
import dev.patrickgold.florisboard.dictate.engines.KeyRing
import kotlinx.coroutines.delay

/**
 * The engine of the RE button: exactly two choices, Anthropic or Groq (Marko, 10.11.2026), chosen in Settings or by a long
 * press on RE. Only the chosen one is ever used. Groq goes through the numbered forward-only key ring (Groq n/total), a
 * quota or refused key moves to the next key of Groq, no answer / no internet waits 3 s and tries the same key again
 * (10 times). A retired model id says "model retired".
 */
object MaReEngine {
    private val prefs by FlorisPreferenceStore

    /** True while an RE instruction is being applied; the one flag requestRewordRaw reads. */
    @Volatile var inRe = false

    const val ANTHROPIC = "anthropic"
    const val GROQ = "groq"
    val GROQ_MODELS = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b")

    fun engine(): String = prefs.dictate.maReEngine.get().let { if (it == GROQ) GROQ else ANTHROPIC }
    fun groqModel(): String = prefs.dictate.maReGroqModel.get().let { if (it in GROQ_MODELS) it else GROQ_MODELS[0] }

    /** Short label for the RE key's panel and Settings. */
    fun label(): String = if (engine() == GROQ) "Groq" else "Anthropic"

    const val SYSTEM = "Rewrite the user's text exactly as instructed. Stay faithful to the meaning, keep the same language as the input, " +
        "and return only the resulting text, with no comments, quotes or explanations."

    suspend fun groq(c: Context, user: String, status: (String) -> Unit): String = groqAsk(c, SYSTEM, user, status)

    /** One Groq chat call with its own [system] prompt (the RE chat, 13.11.2026); same ring and retry rules. */
    suspend fun groqAsk(c: Context, system: String, user: String, status: (String) -> Unit): String {
        val model = groqModel()
        var attempt = 0
        while (true) {
            try {
                return KeyRing.run(c, "groq", "Groq") { k -> EngineAdapters.groqChat(k, model, system, user) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                val kind = FallbackRunner.classify(t)
                if (kind == FailKind.QUOTA || kind == FailKind.AUTH) throw EngineFailure(kind, "every Groq key is out")
                if (t is EngineFailure && t.message == "model retired") throw t
                if (kind == FailKind.NOT_CONFIGURED) throw t
                attempt++
                if (attempt > 10) throw t
                status("RE \u00b7 Groq \u00b7 retry $attempt/10 \u00b7 " + if (kind == FailKind.NETWORK) "no internet" else "no answer")
                delay(3000)
            }
        }
    }
}
