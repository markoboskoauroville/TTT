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

import dev.patrickgold.florisboard.dictate.nlp.MaTeacherMemory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MaTeacherMemoryTest : FunSpec({
    test("what Haiku said after two words is offered there, continuing the prefix") {
        val m = MaTeacherMemory()
        m.remember("i", "will", listOf("bring", "be", "come"))
        m.words("I", "Will", "") shouldBe listOf("bring", "be", "come")
        m.words("i", "will", "b") shouldBe listOf("bring", "be")
        m.words("i", "will", "bring") shouldBe emptyList()
        m.words("you", "will", "") shouldBe emptyList()
    }
    test("newer answers first, no duplicates, at most six") {
        val m = MaTeacherMemory()
        m.remember(null, "the", listOf("a", "b", "c"))
        m.remember(null, "the", listOf("d", "A", "e", "f"))
        m.words(null, "the", "") shouldBe listOf("d", "A", "e", "f", "b", "c")
    }
    test("survives the file") {
        val m = MaTeacherMemory()
        m.remember("dobro", "jutro", listOf("Damjan", "svima"))
        val n = MaTeacherMemory()
        n.parse(m.serialize())
        n.words("dobro", "jutro", "") shouldBe listOf("Damjan", "svima")
        n.isDirty shouldBe false
    }
    test("the least recently used context goes first when full") {
        val m = MaTeacherMemory()
        for (i in 0..MaTeacherMemory.MAX_CONTEXTS) m.remember("w$i", "x", listOf("y"))
        m.size() shouldBe MaTeacherMemory.MAX_CONTEXTS
        m.words("w0", "x", "") shouldBe emptyList()
        m.words("w${MaTeacherMemory.MAX_CONTEXTS}", "x", "") shouldBe listOf("y")
    }
})
