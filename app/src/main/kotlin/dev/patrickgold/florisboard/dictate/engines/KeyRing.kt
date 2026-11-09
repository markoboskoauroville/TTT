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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * A provider holds a ring of working keys (Marko, 9.11.2026). A call tries the keys in turn: a quota
 * answer (429) or a refused key (401/403) or a timeout moves to the next key, and the status line under
 * the recorder says so ("Groq key 2/5 rate-limited -> key 3"). When the last key fails the failure goes
 * up and the engine chain moves on to the next provider. A key that failed is rested (an hour when
 * refused, a minute when rate-limited) so the next dictation does not start with it. Keys are never
 * printed; only their position.
 */
object KeyRing {
    /** Where the status line goes while a dictation is running; set by [EngineChain.dictate]. */
    @Volatile var statusSink: ((String) -> Unit)? = null

    private val rest = ConcurrentHashMap<Int, Long>()

    suspend fun <T> run(c: Context, ringId: String, label: String, block: suspend (String) -> T): T {
        val all = EngineKeys.lines(c, ringId).ifEmpty { throw EngineFailure(FailKind.NOT_CONFIGURED, "no key") }
        val now = System.currentTimeMillis()
        val live = all.filter { (rest[it.hashCode()] ?: 0L) <= now }.ifEmpty { all }
        var last: Throwable = EngineFailure(FailKind.NOT_CONFIGURED, "no key")
        for ((i, k) in live.withIndex()) {
            val more = i < live.size - 1
            var what = ""
            try {
                return block(k)
            } catch (f: EngineFailure) {
                if (f.kind != FailKind.QUOTA && f.kind != FailKind.AUTH) throw f
                rest[k.hashCode()] = now + (if (f.kind == FailKind.AUTH) 3_600_000L else 60_000L)
                what = if (f.kind == FailKind.QUOTA) "rate-limited" else "refused"
                last = f
            } catch (e: IOException) {
                what = "timed out"
                last = e
            }
            if (more) statusSink?.invoke("$label key ${i + 1}/${live.size} $what → key ${i + 2}")
        }
        throw last
    }
}
