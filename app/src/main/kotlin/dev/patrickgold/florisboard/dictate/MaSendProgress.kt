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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WHAT IS GOING UP, AND WHEN THE WORDS COME BACK (Marko, 14.11.2026: "everywhere I want to see when I'm sending the
 * transcription what is the size of original file and size of compressed file while it's sending and also estimated
 * time of delivery of finished transcription").
 *
 * One state for every sender (the keyboard's H/E keys, Talk-to keys, Reprompt, the RE window): the recording as it
 * was captured ([Info.originalBytes]), the file that is really uploaded after resampling / AAC ([Info.sentBytes]),
 * and the expected time until the text is back ([Info.etaMs]). The recorder readouts show it while sending.
 *
 * The estimate is learned per engine from his own sends: the last [KEEP] (audio seconds, total ms) pairs, a straight
 * line through them (fixed cost + cost per spoken second). Until there are two different lengths it uses a
 * conservative default, so the very first send already shows a number.
 */
object MaSendProgress {

    data class Info(
        val engine: String,
        val originalBytes: Long,
        val sentBytes: Long,
        val audioSeconds: Double,
        val startedAt: Long,
        val etaMs: Long,
    )

    private val _state = MutableStateFlow<Info?>(null)
    val state: StateFlow<Info?> = _state

    private const val PREFS = "ma_send_eta"
    private const val KEEP = 12

    /** A send starts. [key] names the engine (and model) the learned timing belongs to. */
    fun begin(c: Context, key: String, originalBytes: Long, sentBytes: Long, audioSeconds: Double, local: Boolean = false) {
        _state.value = Info(
            engine = key,
            originalBytes = originalBytes.coerceAtLeast(0L),
            sentBytes = sentBytes.coerceAtLeast(0L),
            audioSeconds = audioSeconds,
            startedAt = System.currentTimeMillis(),
            etaMs = estimate(c, key, audioSeconds, local),
        )
    }

    /** The send ended. On success the real time is learned for the next estimate. */
    fun end(c: Context, ok: Boolean) {
        val i = _state.value ?: return
        _state.value = null
        if (!ok || i.audioSeconds <= 0.0) return
        val ms = System.currentTimeMillis() - i.startedAt
        runCatching {
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pts = (read(p.getString(i.engine, null)) + (i.audioSeconds to ms.toDouble())).takeLast(KEEP)
            p.edit().putString(i.engine, pts.joinToString(";") { "%.2f:%d".format(java.util.Locale.ROOT, it.first, it.second.toLong()) }).apply()
        }
    }

    fun estimate(c: Context, key: String, sec: Double, local: Boolean = false): Long {
        val pts = runCatching { read(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)) }.getOrDefault(emptyList())
        return fit(pts, sec, local)
    }

    /** Least squares through the stored pairs; falls back to a scaled average, then to defaults. */
    internal fun fit(pts: List<Pair<Double, Double>>, sec: Double, local: Boolean): Long {
        val defBase = if (local) 500.0 else 1500.0
        val defRate = if (local) 400.0 else 120.0
        if (pts.isEmpty()) return (defBase + defRate * sec).toLong()
        val n = pts.size.toDouble()
        val mx = pts.sumOf { it.first } / n
        val my = pts.sumOf { it.second } / n
        val sxx = pts.sumOf { (it.first - mx) * (it.first - mx) }
        if (pts.size >= 3 && sxx > 4.0) {
            val b = (pts.sumOf { (it.first - mx) * (it.second - my) } / sxx).coerceAtLeast(0.0)
            val a = (my - b * mx).coerceAtLeast(300.0)
            return (a + b * sec).toLong()
        }
        // Too few or all the same length: the average time, moved by the default rate for a longer or shorter clip.
        return (my + defRate * (sec - mx)).coerceAtLeast(300.0).toLong()
    }

    private fun read(s: String?): List<Pair<Double, Double>> = s.orEmpty().split(';').mapNotNull { e ->
        val parts = e.split(':')
        if (parts.size != 2) null else {
            val a = parts[0].toDoubleOrNull(); val b = parts[1].toDoubleOrNull()
            if (a == null || b == null) null else a to b
        }
    }

    /** "48.2 → 1.6 MB", sizes under 1 MB in kB. */
    fun sizes(i: Info): String = "${size(i.originalBytes)} → ${size(i.sentBytes)}"

    fun size(b: Long): String = if (b >= 1_048_576L) "%.1f MB".format(b / 1_048_576.0) else "${(b + 1023) / 1024} kB"

    /** "~4 s" while the estimate runs, "+3 s" once it is overdue. */
    fun eta(i: Info, now: Long = System.currentTimeMillis()): String {
        val left = i.etaMs - (now - i.startedAt)
        return if (left >= 0) "~${(left + 999) / 1000} s" else "+${(-left) / 1000} s"
    }
}
