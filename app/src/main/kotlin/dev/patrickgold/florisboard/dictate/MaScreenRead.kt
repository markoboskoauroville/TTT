/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.overlay.MaScreenChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * RE'S "READ SCREEN" TOOL (Marko, 9.11.2026): "take a screenshot, read the latest message and reply to it".
 *
 * Marko says it to the RE key ("take a screenshot and reply to the latest message", "odgovori na zadnju
 * poruku"); [wants] hears it, [read] gets the screen as text, and the text goes into RE's prompt
 * (MaReprompt) so the model writes the reply, in his skill's style, into the field. NEVER sent.
 *
 * Order, the cheapest and most exact first, nothing leaves the phone in any of them:
 * 1. **The accessibility node tree** of the app under the keyboard: the visible messages as text, each
 *    with the side it sits on (MaScreenChat). Exact, free, instant.
 * 2. **A screenshot and on-device OCR** (AccessibilityService.takeScreenshot, API 30+, then ML Kit's
 *    bundled Latin text recognition, offline): for apps that draw their text (canvas, games, some
 *    Flutter and web views). The bitmap is cut above the keyboard, kept in memory, never saved or sent.
 * 3. **Vision on the model: NOT BUILT.** It would send the picture to a paid API; nothing sends an
 *    image anywhere, so when 1 and 2 both fail RE says so and writes nothing.
 */
object MaScreenRead {

    const val NAME = "Read screen"

    /** What was read, and by which source. */
    data class Screen(val text: String, val how: String)

    fun on(): Boolean = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maReScreen.get()
    }.getOrDefault(true)

    /** The spoken commands, English and Croatian; the dictation may spell "screenshot" two ways. */
    private val WANTS = Regex(
        "screen\\s?shot|take (a |the )?screen|read (the )?screen|read (the )?(last|latest|newest|previous|recent) message|" +
            "reply to (the )?(last|latest|newest|previous|recent)|answer (the )?(last|latest|newest) message|" +
            "snimk\\w* zaslona|snimk\\w* ekrana|slikaj (ekran|zaslon)|uslikaj|" +
            "odgovori na (zadnju|posljednju|najnoviju|prethodnu)|pročitaj (zadnju|posljednju|najnoviju) poruku|" +
            "procitaj (zadnju|posljednju|najnoviju) poruku|pročitaj (ekran|zaslon)",
        RegexOption.IGNORE_CASE,
    )

    fun wants(words: String): Boolean = on() && WANTS.containsMatchIn(words)

    /** Pure: the same test without the switch, for the test suite. */
    fun hears(words: String): Boolean = WANTS.containsMatchIn(words)

    /** The screen as text, or null when no source could read it. */
    suspend fun read(): Screen? = runCatching {
        kotlinx.coroutines.withTimeout(READ_TIMEOUT_MS) { readInner() }
    }.getOrNull()

    /** The whole read may take this long: no screenshot or OCR call can hang RE. */
    const val READ_TIMEOUT_MS = 8_000L

    /** Apps whose accessibility tree is the only reader (Marko, 9.11.2026: "accessibility can read WhatsApp"). */
    private val TREE_ONLY = setOf("com.whatsapp", "com.whatsapp.w4b")

    private fun screenshotFallbackOn(): Boolean = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maReScreenshot.get()
    }.getOrDefault(false)

    private suspend fun readInner(): Screen? {
        // (a) the node tree
        val tree = runCatching { DictateAccessibilityService.chatItems() }.getOrNull()
        if (tree != null) {
            val text = MaScreenChat.format(tree.first, tree.second)
            MaLog.add("re", "screen tree: ${tree.first.size} items, ${text.length} chars")
            if (tree.first.size >= 2 && text.count { it.isLetterOrDigit() } >= 12) return Screen(text, "accessibility tree")
        }
        // (b) screenshot + OCR: only when switched on, and never in WhatsApp (its tree is complete).
        if (!screenshotFallbackOn() || MaReprompt.lastPkg in TREE_ONLY) return null
        val bmp = runCatching { DictateAccessibilityService.screenshotAboveKeyboard() }.getOrNull()
        if (bmp != null) {
            val text = runCatching { ocr(bmp) }.getOrDefault("")
            val width = bmp.width
            runCatching { bmp.recycle() }
            MaLog.add("re", "screen ocr: ${text.length} chars (width $width)")
            if (text.count { it.isLetterOrDigit() } >= 12) return Screen(text, "screenshot and OCR")
        }
        return null
    }

    private suspend fun ocr(bmp: Bitmap): String = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspendCancellableCoroutine<String> { cont ->
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { r ->
                        val items = r.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                            val b = l.boundingBox ?: return@mapNotNull null
                            MaScreenChat.Item(l.text, b.top, b.left, b.right)
                        }
                        cont.resume(MaScreenChat.format(items, bmp.width))
                    }
                    .addOnFailureListener { cont.resume("") }
            }
        } finally {
            runCatching { recognizer.close() }
        }
    }

    /** The block added to RE's prompt when the screen was read. */
    fun block(screen: Screen): String =
        "\n\nThe phone's screen, read just now by the keyboard (${screen.how}). Each line starts with where it sits: " +
            "[left] is the other person's message, [right] is Marko's own, [center] is a date or a notice; lines are in " +
            "order from top to bottom, so the newest message is the last one.\n<screen>\n" + screen.text + "\n</screen>\n" +
            "Ignore the part of the instruction about taking or reading the screenshot: it is done. Find the newest " +
            "message from the other person ([left]) in that chat and write Marko's reply to it, following the skill, in " +
            "the language of that message unless the instruction names another. If no line is [left] (a chat with himself, " +
            "or the newest message is his own), NEVER refuse and never say he cannot reply to himself: write the next message " +
            "that continues from the newest one, or, if that message is a request or a question to himself, answer it. " +
            "Only if the screen shows no message at all, return one line starting with RE-NOTE: and say so. Return only the " +
            "reply, ready to send."
}
