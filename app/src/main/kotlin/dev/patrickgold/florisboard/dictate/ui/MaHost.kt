/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE HOST LOOK, in one place (Marko, 13.11.2026: "everything you make recently looks like it came from a different
 * mindset"). Every screen TTT opens outside the keyboard (RE chat, History, People, pop-ups) uses these colours and
 * [MaHostKey], which is the keyboard's own key: the same face, the same 16 dp corner, the same 1.5 dp ring, the same
 * ring colours with the same meaning. Rules: .claude/skills/ttt-design/SKILL.md.
 */
object MaHost {
    /** The keyboard background (the recording fill). */
    val Bg = Color(0xFF11151C)
    /** A key face on that background. */
    val Key = Color(0xFF1C2129)
    /** Pressed / chosen key face. */
    val KeyLit = Color(0xFF262C36)
    /** Text and icons on keys. */
    val Ink = Color(0xFFE6EDF3)
    /** Warm label ink (key labels, the switcher ring at 55 %). */
    val Sand = Color(0xFFF2DDB4)
    /** The gold accent: numbers, highlight, the spoken word. */
    val Gold = Color(0xFFE8B15C)
    val Muted = Color(0xFF8B949E)
    /** Rings: red = records voice (dark, light while recording), green = switched on, sand = changes the view. */
    val RingRecord = MaRecordOutline
    val RingRecordLive = MaRecordOutlineLive
    val RingOn = Color(0xFF6FA85A)
    val RingSwitch = Color(0xFFF2DDB4).copy(alpha = 0.55f)
    /** Destructive actions: the record red, text in the light red. */
    val Danger = MaRecordRed
    val DangerInk = MaRecordOutlineLive
    /** Balloons: his on the right (warm, lit key), RE's on the left (plain key with the switcher ring). */
    val Mine = Color(0xFF2B3340)
    val Theirs = Key
    val KeyShape = RoundedCornerShape(16.dp)
}

/**
 * A key, exactly as on the keyboard: face, 16 dp corners, optional ring, icon and/or label in ink. [ring] says what
 * the key is (see [MaHost]); [lit] is the chosen/pressed face. Height at least 44 dp so it is pressed easily.
 */
@Composable
fun MaHostKey(
    label: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ring: Color? = null,
    lit: Boolean = false,
    ink: Color = MaHost.Ink,
    enabled: Boolean = true,
    fontSize: Int = 16,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .clip(MaHost.KeyShape)
            .background(if (lit) MaHost.KeyLit else MaHost.Key)
            .then(if (ring != null) Modifier.border(1.5.dp, ring, MaHost.KeyShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(20.dp))
            if (!label.isNullOrEmpty()) {
                Text(
                    label, color = ink, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
                )
            }
        }
    }
}
