/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The inside of the H and E keys (Marko, 9.11.2026): the red dot and the letter at the LEFT edge, the
 * name of the engine that key uses in big letters across the rest of the key, sized to fit. A fallback
 * is shown by the engine that really answers, with a return arrow.
 */
@Composable
internal fun MaEngineKeyContent(letter: String, dot: Color, name: String, fallback: Boolean, fg: Color, sub: String = "") {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val shown = if (fallback) "$name ↺" else name
        // Marko, 9.11.2026: name left, right after the letter, a bit smaller; the quota at half the name's size on the same line.
        // The dot, letter and gaps take about 48 dp; a glyph is about 0.58 of its size wide; the sub is half size.
        // Marko, 10.11.2026: the text next to H/E aims at the size of the letter (17 sp); if the key is too narrow it
        // shrinks step by step on one line, measured with the real font, and only below 9 sp goes in two lines. Never cut.
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        fun widthDp(t: String, sp: Float, bold: Boolean = false): Float = with(density) {
            measurer.measure(
                t,
                androidx.compose.ui.text.TextStyle(fontSize = sp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
                maxLines = 1, softWrap = false,
            ).size.width.toDp().value
        }
        val target = 18f
        val room = (maxWidth.value - (10f + 6f + 9f + 4f + widthDp(letter, 17f, true) + 6f + 2f)).coerceAtLeast(20f)
        fun fit(parts: List<String>, gap: Float): Float {
            var sz = target
            while (sz > 6f && parts.sumOf { widthDp(it, sz).toDouble() }.toFloat() + gap * (parts.size - 1) > room) sz -= 0.5f
            return sz
        }
        val oneLine = fit(if (sub.isEmpty()) listOf(shown) else listOf(shown, sub), 4f)
        val stacked = sub.isNotEmpty() && oneLine < 9f
        val size = oneLine
        val nameSize = fit(listOf(shown), 0f)
        val subSize = minOf(nameSize, fit(listOf(sub.ifEmpty { " " }), 0f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(4.dp))
            Text(text = letter, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            if (stacked) {
                androidx.compose.foundation.layout.Column {
                    Text(text = shown, color = fg, fontSize = nameSize.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    Text(text = sub, color = fg.copy(alpha = 0.75f), fontSize = subSize.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
            } else {
                Text(text = shown, color = fg, fontSize = size.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                if (sub.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(text = sub, color = fg.copy(alpha = 0.75f), fontSize = size.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

/** One short label on the key: "free 480" (minutes left, 38k when large) while the provider still has free quota, "paid" when it has not. */
internal fun engineSubLabel(c: android.content.Context, id: String): String {
    val spec = dev.patrickgold.florisboard.dictate.engines.EngineCatalog.byId(id) ?: return ""
    return when {
        spec.freeSeconds == Long.MAX_VALUE -> "free"
        spec.paid -> "paid"
        else -> {
            val sec = dev.patrickgold.florisboard.dictate.engines.Quota.remainingSeconds(
                spec, dev.patrickgold.florisboard.dictate.engines.EngineStore.usage(c, id), System.currentTimeMillis())
            val min = sec / 60
            if (sec <= 0) "paid" else if (min >= 10000) "free ${min / 1000}k" else "free $min"
        }
    }
}

/** Minutes left only, for the H/E buttons (Marko, 10.11.2026: "Gemini 2/25 45"): "478", "38k", "paid" without a number, unlimited free as "∞". */
internal fun engineMinutesLabel(c: android.content.Context, id: String): String {
    val spec = dev.patrickgold.florisboard.dictate.engines.EngineCatalog.byId(id) ?: return ""
    return when {
        spec.freeSeconds == Long.MAX_VALUE -> "\u221e"
        spec.paid -> "paid"
        else -> {
            val sec = dev.patrickgold.florisboard.dictate.engines.Quota.remainingSeconds(
                spec, dev.patrickgold.florisboard.dictate.engines.EngineStore.usage(c, id), System.currentTimeMillis())
            val min = sec / 60
            if (sec <= 0) "paid" else if (min >= 10000) "${min / 1000}k" else "$min"
        }
    }
}
