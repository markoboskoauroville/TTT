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
        p.prompt!! shouldStartWith "Apply this instruction to the text below"
    }
    test("code words work in both modes") {
        MaReprompt.prompt("a thank-you note, Ashai Afterman style", write = true)
            .prompt!! shouldContain "The instruction above names: Yshai Afterman style."
        MaReprompt.prompt("Ashai Afterman style", write = false)
            .prompt!! shouldContain "The instruction above names: Yshai Afterman style."
    }
})
