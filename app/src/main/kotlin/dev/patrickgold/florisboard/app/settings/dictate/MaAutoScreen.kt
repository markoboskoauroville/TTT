/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch

/**
 * AUTO CORRECTIONS: EVERY AUTOMATIC CHANGE TO TEXT, ONE SWITCH EACH.
 *
 * Marko, 9.11.2026, after typing AT2020 on Amazon and the keyboard eating a 2: "I want no automatic
 * replacement of anything. If there is auto replacement there must be a special item in the menu ...
 * put them under that menu, so I can quickly disable or enable them." The toolbar's AUTO key and
 * Settings, AUTO corrections both open this screen.
 *
 * The rule (TAKEOVER, LESSONS): nothing in this app changes text he typed unless its switch is on
 * here. Every new automatic behaviour gets a switch on this screen, off by default, in the same commit.
 * Typing and dictation ones are all off on a fresh install and were switched off once on every existing
 * one (DictateLegacyMigrator.applyAutoOffV1IfNeeded, V2 for dictation).
 */
@Composable
fun MaAutoScreen() = FlorisScreen {
    title = "AUTO corrections"

    content {
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()

        Preference(
            title = "Switch every typing correction off",
            summary = "One press: the four switches under While you type go off.",
            onClick = {
                scope.launch {
                    prefs.suggestion.autoCorrect.set(false)
                    prefs.correction.autoCapitalization.set(false)
                    prefs.correction.autoSpacePunctuation.set(false)
                    prefs.correction.doubleSpacePeriod.set(false)
                }
            },
        )

        PreferenceGroup(title = "While you type") {
            SwitchPreference(
                prefs.suggestion.autoCorrect,
                title = "Auto-correct words",
                summary = "Replaces a word that looks like a typo when you press space or a mark. Never touches a word with a digit in it.",
            )
            SwitchPreference(
                prefs.correction.autoCapitalization,
                title = "Auto-capitalise",
                summary = "Starts a sentence with a capital letter by itself.",
            )
            SwitchPreference(
                prefs.correction.autoSpacePunctuation,
                title = "Auto-space around punctuation",
                summary = "Adds a space after a full stop or comma and removes the one before it.",
            )
            SwitchPreference(
                prefs.correction.doubleSpacePeriod,
                title = "Double space makes a full stop",
                summary = "Two taps on space become a full stop and a space.",
            )
        }

        PreferenceGroup(title = "On dictation") {
            SwitchPreference(
                prefs.dictate.maSpokenFixups,
                title = "Spoken fix-ups",
                summary = "Croatian letters and spelling, your name with a k, a dictated e-mail address or time put together.",
            )
            SwitchPreference(
                prefs.dictate.maTextReplacements,
                title = "Text replacements list",
                summary = "Your find-and-replace list (Custom mappings) applied to every dictation.",
            )
            SwitchPreference(
                prefs.dictate.maCorrectionsEnabled,
                title = "Learned corrections",
                summary = "Words you fixed twice in a dictation are fixed for you from then on.",
            )
            SwitchPreference(
                prefs.dictate.maAiCorrectDictation,
                title = "Correct Croatian dictation with AI",
                summary = "Off: after transcription no model is called, no tokens are spent. On: the passes below may run.",
            )
            SwitchPreference(
                prefs.dictate.maPhoneStyleEnabled,
                title = "Phone style rewrite",
                summary = "An AI pass that rewrites a longer dictation the way you write on the phone.",
            )
            SwitchPreference(
                prefs.dictate.maSpellcheckEnabled,
                title = "AI spell check (Croatian)",
                summary = "An AI pass that repairs invented Croatian words in a dictation.",
            )
            SwitchPreference(
                prefs.dictate.autoFormattingEnabled,
                title = "Spoken formatting to Markdown",
                summary = "Turns spoken cues such as \"new paragraph\" into Markdown on every transcript.",
            )
            SwitchPreference(
                prefs.dictate.autoEnter,
                title = "Press Enter after a dictation",
                summary = "Sends the field's action key right after the text is written.",
            )
        }
    }
}
