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

class MaCodeWordsTest : FunSpec({
    test("his spellings of Yshai Afterman are recognised") {
        for (said in listOf(
            "make it Yshai Afterman style", "Ashai Afterman style please", "like Ishai Afterman",
            "rewrite like Jišai Aftermen", "Afterman style", "use the after man style", "like Yshai",
        )) {
            MaCodeWords.found(said).map { it.name } shouldBe listOf("Yshai Afterman style")
        }
    }
    test("ordinary instructions are left alone") {
        for (said in listOf(
            "make it shorter", "the aftermath of the storm", "a man after my own heart", "after the man left",
        )) {
            MaCodeWords.expand(said) shouldBe said
        }
    }
    test("the expansion carries the style, in normal capitals") {
        val e = MaCodeWords.expand("make it Ashai Afterman style")
        e shouldContain "\"Yshai Afterman style\" in the instruction above means this:"
        e shouldContain "Normal sentence capitalisation"
        e shouldContain "Short paragraphs of one or two sentences"
    }
})
