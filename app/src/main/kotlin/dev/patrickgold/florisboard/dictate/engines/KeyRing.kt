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
 * A provider holds a ring of working keys, numbered in file order: Groq-1, Groq-2, Gemini-1 ...
 * (Marko, 10.11.2026: "when this one expires, you automatically open the next key with new free usage").
 * Every call starts at the lowest-numbered key that is not resting, so an earlier key comes back by itself
 * when its rest ends. A daily-quota answer rests a key until 00:00 UTC (the free tiers reset then); a plain
 * rate limit rests it a minute; a refused key (401/403) an hour. The status line under the recorder says
 * every switch ("Groq-1 out of free use -> Groq-2"). When the last key fails the failure goes up and the
 * engine chain moves on to the next provider. Keys are never printed; only their number.
 */
object KeyRing {
    /** Where the status line goes while a dictation is running; set by [EngineChain.dictate]. */
    @Volatile var statusSink: ((String) -> Unit)? = null

    private val rest = ConcurrentHashMap<String, Long>()

    /** Number (1-based) of the key that answered last, per ring id; read by the UI ("Groq-2 active"). */
    val active = ConcurrentHashMap<String, Int>()

    /** Safe name of one key: "Groq-2". Never the value. */
    fun tag(label: String, number: Int) = "$label-$number"

    private fun testKey1Fails(): Boolean = runCatching {
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        prefs.dictate.maTestKey1Fails.get()
    }.getOrDefault(false)

    private fun nextUtcMidnight(now: Long): Long = (now / 86_400_000L + 1) * 86_400_000L

    suspend fun <T> run(c: Context, ringId: String, label: String, block: suspend (String) -> T): T {
        val all = EngineKeys.lines(c, ringId).ifEmpty { throw EngineFailure(FailKind.NOT_CONFIGURED, "no key") }
        val now = System.currentTimeMillis()
        val numbered = all.mapIndexed { i, k -> (i + 1) to k }
        val live = numbered.filter { (rest["$ringId:${it.second.hashCode()}"] ?: 0L) <= now }.ifEmpty { numbered }
        var last: Throwable = EngineFailure(FailKind.NOT_CONFIGURED, "no key")
        for ((i, nk) in live.withIndex()) {
            val n = nk.first
            val k = nk.second
            val more = i < live.size - 1
            var what = ""
            try {
                if (n == 1 && live.size > 1 && testKey1Fails()) {
                    throw EngineFailure(FailKind.QUOTA, "HTTP 429 (test: key 1 fails)")
                }
                val r = block(k)
                val before = active.put(ringId, n)
                if (before != null && before != n) statusSink?.invoke("${tag(label, n)} active")
                return r
            } catch (f: EngineFailure) {
                if (f.kind != FailKind.QUOTA && f.kind != FailKind.AUTH) throw f
                val m = (f.message ?: "").lowercase()
                val daily = f.kind == FailKind.QUOTA &&
                    (m.contains("day") || m.contains("daily") || m.contains("quota") || m.contains("credit"))
                rest["$ringId:${k.hashCode()}"] = when {
                    f.kind == FailKind.AUTH -> now + 3_600_000L
                    daily -> nextUtcMidnight(now)
                    else -> now + 60_000L
                }
                what = if (f.kind == FailKind.QUOTA) "out of free use" else "refused"
                last = f
            } catch (e: IOException) {
                what = "timed out"
                last = e
            }
            if (more) statusSink?.invoke("${tag(label, n)} $what → ${tag(label, live[i + 1].first)}")
        }
        throw last
    }
}
