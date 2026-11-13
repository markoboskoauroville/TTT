/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE SCRATCH BOX (Marko, 13.11.2026): *"if I'm not in any application, I want to be able to open TTT and when I'm inside
 * the settings I want to have test window, some kind of text box I can click and then continue to work inside to, for
 * example, use RE, since I'm actually not in any window I will use RE to remotely control WhatsApp."*
 *
 * One text field at the top of TTT's own app (every settings screen). Tapping it opens the TTT keyboard, so the RE key,
 * RE Magic and its WhatsApp tab are reachable with no other app open. Its text is kept between visits. The launcher
 * shortcut "TTT RE" and the Quick Settings tile "TTT RE" open the app with [EXTRA] set: the box takes the focus and the
 * keyboard at once.
 */
object MaScratchBox {
    const val EXTRA = "ttt_scratch"
    private const val PREFS = "ma_scratch"
    fun load(c: Context): String = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("text", "") ?: ""
    fun save(c: Context, t: String) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("text", t).apply()
}

@Composable
fun MaScratchBoxField(focusTick: Int) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(MaScratchBox.load(ctx)) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            runCatching { focus.requestFocus() }
            kb?.show()
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (text.isEmpty()) Text("Test box: tap here, then use RE Magic (RE, WhatsApp)", color = ink.copy(alpha = 0.55f), fontSize = 15.sp)
        BasicTextField(
            value = text,
            onValueChange = { text = it; MaScratchBox.save(ctx, it) },
            textStyle = TextStyle(color = ink, fontSize = 16.sp),
            cursorBrush = SolidColor(ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp, max = 96.dp).focusRequester(focus),
        )
    }
}
