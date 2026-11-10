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

/**
 * Width of a feature-row button as a percentage of its row (Marko, 10.11.2026: "for each button inside the
 * feature row another parameter, how much percentage the button takes of the length of the feature row;
 * greater percentage to H and E, smaller to F1"). Stored as "TOKEN=40,TOKEN=20". Percentages are
 * relative, so a row that does not add up to 100 is normalised; no button gets less than a floor share
 * so its label is never cut. Until he sets anything, a row of four or fewer buttons uses H and E 40, F1 20.
 */
object MaKeyWidths {
    fun parse(raw: String): Map<String, Int> = raw.split(',').mapNotNull {
        val p = it.split('='); val v = p.getOrNull(1)?.trim()?.toIntOrNull()
        if (p.size == 2 && v != null) p[0].trim() to v.coerceIn(5, 80) else null
    }.toMap()

    fun write(m: Map<String, Int>) = m.entries.joinToString(",") { "${it.key}=${it.value}" }

    /** What Marko's row looked like before the per-button setting existed; written once so the row keeps its look. */
    fun migrationDefaults(): String = write(
        listOf("RECORD_HR", "RECORD_HR_NET", "RECORD_HR_LOCAL", "RECORD_EN", "RECORD_EN_NET", "RECORD_EN_LOCAL").associateWith { 40 } + mapOf("ROW_1" to 20),
    )

    /** Set one button to a defined percent, or null = Fit. */
    fun with(raw: String, token: String, pct: Int?): String {
        val m = parse(raw).toMutableMap()
        if (pct == null) m.remove(token) else m[token] = pct.coerceIn(5, 80)
        return write(m)
    }

    fun label(raw: String, token: String): String = parse(raw)[token]?.let { "$it%" } ?: "Fit"

    /**
     * Share (0..1) of the row for each token, in order; sums to 1. A defined button keeps its percent (scaled down
     * together if the defined ones add up to more than 100); Fit buttons split what is left equally. A floor share
     * keeps every label whole.
     */
    fun shares(tokens: List<String>, raw: String): List<Float> {
        val n = tokens.size
        if (n == 0) return emptyList()
        val set = parse(raw)
        val defined = tokens.map { set[it]?.toFloat() }
        var sumD = defined.filterNotNull().sum()
        val scale = if (sumD > 100f) 100f / sumD else 1f
        sumD *= scale
        val fitCount = defined.count { it == null }
        val fitEach = if (fitCount == 0) 0f else maxOf(100f - sumD, 0f) / fitCount
        val w = defined.map { if (it == null) fitEach else it * scale }
        val floor = minOf(0.08f, 0.5f / n)
        val sum = w.sum().takeIf { it > 0f } ?: 1f
        var s = w.map { maxOf(it / sum, floor) }
        val t = s.sum()
        s = s.map { it / t }
        return s
    }
}
