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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class MaRepromptTest : FunSpec({
    test("an empty field writes a new message: nothing selected or replaced") {
        val p = MaReprompt.prompt("tell Damjan I'm working on it, give me an hour", write = true)
        p.requiresSelection shouldBe false
        p.prompt!! shouldStartWith "Write the message the user asks for"
        p.prompt!! shouldContain "<request>\ntell Damjan I'm working on it, give me an hour\n</request>"
        p.prompt!! shouldContain "Write it in English"
    }
    test("a Croatian request is written in Croatian") {
        MaReprompt.prompt("napiši Damjanu da radim na tome i da mi treba sat vremena", write = true)
            .prompt!! shouldContain "Write it in Croatian"
    }
    test("a field with text is reshaped, as before") {
        val p = MaReprompt.prompt("make it shorter", write = false)
        p.requiresSelection shouldBe true
        p.prompt!! shouldStartWith "Carry out this instruction on the text between the <text> tags."
        p.prompt!! shouldContain "Do not just proofread."
    }
    test("the reshape message fences his text and keeps its language") {
        val m = MaReprompt.message("Carry out: make it shorter", "Dobro jutro Damjan, radim na tome i javit cu se kasnije danas.")
        m shouldContain "<text>\nDobro jutro Damjan"
        m shouldContain "answer in Croatian"
        m shouldContain "single line break"
    }
    test("the prompt mode sends its instruction alone") {
        MaReprompt.message("Write the message…", null) shouldBe "Write the message…"
    }
    test("code words work in both modes") {
        MaReprompt.prompt("a thank-you note, Ashai Afterman style", write = true)
            .prompt!! shouldContain "The instruction above names: Yshai Afterman style."
        MaReprompt.prompt("Ashai Afterman style", write = false)
            .prompt!! shouldContain "The instruction above names: Yshai Afterman style."
    }
})
