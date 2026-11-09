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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The inside of the H and E keys (Marko, 9.11.2026): the red dot and the letter at the LEFT edge, the
 * name of the engine that key uses in big letters across the rest of the key, sized to fit. A fallback
 * is shown by the engine that really answers, with a return arrow.
 */
@Composable
internal fun MaEngineKeyContent(letter: String, dot: Color, name: String, fallback: Boolean, fg: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val shown = if (fallback) "$name ↺" else name
        // The letter, the dot and the gaps take about 44 dp; the rest is the name's. A glyph is about 0.58 of its size wide.
        val room = (maxWidth.value - 52f).coerceAtLeast(20f)
        val size = (room / (shown.length * 0.58f)).coerceIn(9f, 26f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(4.dp))
            Text(text = letter, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                text = shown,
                color = fg,
                fontSize = size.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
