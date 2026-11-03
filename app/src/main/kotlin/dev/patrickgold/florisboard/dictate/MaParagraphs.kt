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

/**
 * ONE LINE BREAK BETWEEN PARAGRAPHS, NEVER AN EMPTY LINE.
 *
 * Marko, 3.11.2026, with a WhatsApp screenshot: "When I press reflow or whenever this Sonnet is
 * correcting my text, it is adding the spaces between paragraphs. I don't want that. It should add
 * only new line without any space in between."
 *
 * Every model writes paragraphs with an empty line between them, and the paragraph splitter did too.
 * Rather than ask each prompt nicely, everything the app writes into a field passes through
 * [single] on its way: dictation, styling, translation, Ctrl+P, Ctrl+F, live prompts.
 */
object MaParagraphs {

    private val BLANK_LINES = Regex("\n[ \t\u00A0]*(?:\n[ \t\u00A0]*)+")

    /** [text] with every run of empty lines between paragraphs reduced to one line break. */
    fun single(text: String): String = text.replace("\r\n", "\n").replace(BLANK_LINES, "\n")
}
