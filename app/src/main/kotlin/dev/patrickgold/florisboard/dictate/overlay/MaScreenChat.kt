/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * THE SCREEN AS A CHAT: what is shown, in order, and on which side (MaScreenRead, 9.11.2026).
 *
 * Source (a) of RE's "read screen": the accessibility node tree of the app under the keyboard. Unlike
 * the reader's `readableText` this keeps short lines and pressable ones (a chat bubble is long-pressable
 * and "ok" is a message) and records the side: a message whose box sits on the left is the other
 * person's, on the right is his own, in the middle a date or a notice. READ ONLY: nothing is pressed.
 */
object MaScreenChat {

    data class Item(val text: String, val top: Int, val left: Int, val right: Int)

    /** The items of every visible text node outside the keyboard and outside this app, top to bottom. */
    fun fromTree(service: AccessibilityService): List<Item> {
        val out = ArrayList<Item>()
        for (root in MaScreenTargets.appWindowRoots(service)) {
            try {
                collect(root, out, 0)
            } finally {
                runCatching { root.recycle() }
            }
        }
        // Everything at or below the message box is the app's own bar (emoji, attach, camera, mic): not chat.
        val inputTop = inputTopSeen
        inputTopSeen = Int.MAX_VALUE
        return out.filter { it.top < inputTop - 4 }.sortedBy { it.top }
    }

    @Volatile
    private var inputTopSeen = Int.MAX_VALUE

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Item>, depth: Int) {
        if (depth > 40 || out.size > 300) return
        if (node.isEditable) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (r.top > 0 && r.top < inputTopSeen) inputTopSeen = r.top
        }
        if (node.isVisibleToUser && !node.isEditable && !node.isPassword) {
            // A message shows as text. Descriptions are button labels ("Emoji, GIFs and stickers"): never chat.
            val shown = node.text?.toString()?.trim().orEmpty()
            if (shown.isNotEmpty() && shown.any { it.isLetterOrDigit() }) {
                val r = Rect().also { node.getBoundsInScreen(it) }
                if (r.width() > 0 && r.height() > 0) out.add(Item(shown, r.top, r.left, r.right))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
            runCatching { child.recycle() }
        }
    }

    /** The top edge of the keyboard window, so a screenshot is cut above it; null when there is none. */
    fun keyboardTop(service: AccessibilityService): Int? =
        runCatching {
            service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .minOfOrNull { Rect().also { r -> it.getBoundsInScreen(r) }.top }
        }.getOrNull()

    /**
     * The items as lines for the model: `[left]`, `[right]` or `[center]`, then the text; identical
     * consecutive lines dropped, at most [MAX_CHARS] kept from the BOTTOM (the newest messages).
     */
    fun format(items: List<Item>, screenWidth: Int): String {
        if (items.isEmpty() || screenWidth <= 0) return ""
        val lines = ArrayList<String>()
        for (it in items.sortedBy { i -> i.top }) {
            val centre = (it.left + it.right) / 2f / screenWidth
            val width = (it.right - it.left).toFloat() / screenWidth
            val side = when {
                width > 0.85f || (centre in 0.42f..0.58f && width < 0.6f && it.text.length < 40) -> "center"
                centre < 0.5f -> "left"
                else -> "right"
            }
            val line = "[$side] ${it.text.replace('\n', ' ')}"
            if (lines.lastOrNull() != line) lines.add(line)
        }
        var total = 0
        val kept = ArrayList<String>()
        for (l in lines.asReversed()) {
            total += l.length + 1
            if (total > MAX_CHARS) break
            kept.add(l)
        }
        return kept.asReversed().joinToString("\n")
    }

    const val MAX_CHARS = 4000
}
