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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE KEY PANEL: the one long-press panel of the feature row (Marko, 13.11.2026: the RE options "don't look like they
 * belong to this app"). Same look as the H/E engine panel (MaEngineMenu): a dark rounded panel in its own window that
 * grows UP from the pressed key, a header with the key's letters in its colour and an x, one row per choice; the
 * chosen row is lit in the key's colour, one tap picks and closes. No Material dropdown menus on the keyboard.
 */
internal data class MaPanelItem(
    val label: String,
    val sub: String = "",
    val on: Boolean = false,
    val pick: () -> Unit,
)

internal val MaPanelBg = Color(0xFF16161A)
internal val MaPanelEdge = Color(0x44FFFFFF)
internal val MaPanelInk = Color(0xFFECEAE3)
internal val MaPanelSub = Color(0x99FFFFFF)

@Composable
internal fun MaKeyPanel(
    title: String,
    accent: Color,
    anchor: androidx.compose.ui.geometry.Rect?,
    items: List<MaPanelItem>,
    onDismiss: () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val loc = IntArray(2).also { view.getLocationOnScreen(it) }
    val keyTop = anchor?.top ?: 0f
    val keyRight = anchor?.right ?: view.width.toFloat()
    val statusPx = with(density) { 28.dp.toPx() }
    val availPx = (loc[1] + keyTop - statusPx).coerceAtLeast(with(density) { 120.dp.toPx() })
    val rows = items.size + 1
    val rowDp = with(density) { (availPx / rows).toDp() }.coerceIn(30.dp, 52.dp)
    val panelPx = with(density) { (rowDp * rows).toPx() }.coerceAtMost(availPx)
    val widthPx = minOf(with(density) { 300.dp.toPx() }, view.width.toFloat())
    // Right-aligned to the key when the key is on the right half, so the panel stays on screen.
    val x = (keyRight - widthPx).coerceIn(0f, (view.width - widthPx).coerceAtLeast(0f))
    val provider = object : androidx.compose.ui.window.PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: androidx.compose.ui.unit.IntRect,
            windowSize: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            popupContentSize: androidx.compose.ui.unit.IntSize,
        ) = androidx.compose.ui.unit.IntOffset(x.toInt(), (keyTop - panelPx).toInt())
    }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { panelPx.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(MaPanelBg)
                .border(1.dp, MaPanelEdge, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(rowDp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "✕", color = MaPanelInk, fontSize = 18.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            for (item in items) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowDp)
                        .background(if (item.on) accent.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onDismiss(); item.pick() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label, color = if (item.on) accent else MaPanelInk, fontSize = 16.sp,
                        fontWeight = if (item.on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.sub.isNotEmpty()) Text(text = item.sub, color = MaPanelSub, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}
