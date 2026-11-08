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

import dev.patrickgold.florisboard.dictate.nlp.MaKnownWords
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MaKnownWordsTest : FunSpec({

    context("Auroville, the word Parakeet spells by ear") {
        test("Orville, Wilbers and orwheel are Auroville") {
            MaKnownWords.apply("I saw Orville and Wilbers and the orwheel") shouldBe
                "I saw Auroville and Auroville and the Auroville"
        }
        test("the possessive stays: Orville's birthday") {
            MaKnownWords.apply("I hope to be there for Orville's birthday.") shouldBe
                "I hope to be there for Auroville's birthday."
        }
        test("split in two by the model: Aura Ville") {
            MaKnownWords.apply("Aura Ville is nice") shouldBe "Auroville is nice"
        }
        test("the English phrase our will is left alone") {
            MaKnownWords.apply("Our will to live is strong") shouldBe "Our will to live is strong"
        }
    }

    context("names of several words are matched word for word") {
        test("Ashai Afterman is Yshai Afterman") {
            MaKnownWords.apply("I study with Ashai Afterman every week.") shouldBe
                "I study with Yshai Afterman every week."
        }
        test("a name already right is not doubled") {
            MaKnownWords.apply("Yshai Afterman teaches") shouldBe "Yshai Afterman teaches"
        }
        test("a first word that only looks near is not swallowed") {
            MaKnownWords.apply("the Aftermann family") shouldBe "the Aftermann family"
        }
        test("Santosh Kirtan Band is Santoshi Kirtan Band") {
            MaKnownWords.apply("Santosh Kirtan Band played") shouldBe "Santoshi Kirtan Band played"
        }
    }

    context("aliases he is known to produce") {
        test("Rend Drum and Trim drum are the frame drum") {
            MaKnownWords.apply("the Rend Drum and the Trim drum") shouldBe "the frame drum and the frame drum"
        }
        test("the cherry is Pondicherry, and a word before a name stays") {
            MaKnownWords.apply("Natasha is in the cherry") shouldBe "Natasha is in Pondicherry"
            MaKnownWords.apply("his Pondicherry trip") shouldBe "his Pondicherry trip"
        }
    }

    context("it leaves other people alone") {
        test("Natalia is not Natasha, a common name stays a common name") {
            MaKnownWords.apply("Natalia and Natasha met Tanya") shouldBe "Natalia and Natasha met Tanya"
        }
        test("Venkatesh is kept, Venkateshwar is another man") {
            MaKnownWords.apply("Venkateshwar shot it, Venkatesh too") shouldBe "Venkateshwar shot it, Venkatesh too"
        }
        test("empty and blank text come back as they are") {
            MaKnownWords.apply("") shouldBe ""
            MaKnownWords.apply("   ") shouldBe "   "
        }
        test("a word of his own (custom words) of seven letters or more is snapped") {
            MaKnownWords.apply("Kukljika is lovely", extra = listOf("Kukljica")) shouldBe "Kukljica is lovely"
        }
        test("a short custom word is not made fuzzy") {
            MaKnownWords.apply("The Koma ride", extra = listOf("Kuma")) shouldBe "The Koma ride"
        }
    }
})
