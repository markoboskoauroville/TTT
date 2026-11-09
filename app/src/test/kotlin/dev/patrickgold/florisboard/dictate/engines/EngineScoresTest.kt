/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.engines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineScoresTest {
    @Test
    fun `accuracy is 100 times one minus WER, clamped`() {
        assertEquals(96, EngineScores.pct(0.04))
        assertEquals(100, EngineScores.pct(0.0))
        assertEquals(0, EngineScores.pct(1.7))
    }

    @Test
    fun `diff lights only the words that differ, ignoring case and punctuation`() {
        val (a, b) = WordDiff.diff("Send it to Svemir, please.", "send it to Sveta please")
        assertEquals(listOf("Svemir,"), a.filter { it.changed }.map { it.text })
        assertEquals(listOf("Sveta"), b.filter { it.changed }.map { it.text })
        assertTrue(a.first { it.text == "Send" }.changed.not())
    }

    @Test
    fun `identical texts have no changed words`() {
        val (a, b) = WordDiff.diff("dobar dan", "Dobar dan.")
        assertTrue(a.none { it.changed } && b.none { it.changed })
    }
}
