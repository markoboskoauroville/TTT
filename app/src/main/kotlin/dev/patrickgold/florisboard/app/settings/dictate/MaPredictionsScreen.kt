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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.dictate.MaProviders
import dev.patrickgold.florisboard.dictate.nlp.MaDictationCorrections
import dev.patrickgold.florisboard.dictate.provider.MaClaudeModels
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.stringRes

/**
 * AUTO PREDICTION: EVERYTHING ABOUT THE SUGGESTION ROW, ON ONE SCREEN.
 *
 * Marko, 27.10.2026: "everything related to it should be under the auto prediction menu entry,
 * including the AI model we are using … model switching and all the other settings related to word
 * prediction should be under that one menu item only." And the same day: My corrections, "for when I
 * manually enter the word to be replaced", goes under it too. It was on five: the row and next-word
 * prediction under Typing, the display mode under Smartbar, Learn my words under Dictate and on its
 * own entry, and the AI key's model nowhere (it was always the cheapest Claude).
 *
 * The switches first, then what adjusts them (MANTRA_MANIFEST design language §1.9): how the row
 * shows its words, the Claude model the AI key asks, what has been learned, and teaching it from
 * past dictations.
 *
 * ### The row, and why the switch fixes the flicker
 *
 * On means on: the row keeps its height whether or not it has anything in it. It used to be drawn
 * only when there was something to show, so an empty moment removed a strip and every key below
 * moved while he typed.
 *
 * ### The AI key
 *
 * The last key of the row asks Claude for five words when the local guesses are wrong; the one he
 * picks is taught to the local model (MaAiPredict). It uses the Anthropic key loaded in the app, the
 * same one Ctrl+P uses. The model is stored as a tier, so the newest Haiku, Sonnet or Opus is used
 * as Anthropic release them, and named here by its real name.
 */
@Composable
fun MaPredictionsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__ma_predictions__title)

    content {
        val prefs by FlorisPreferenceStore
        val navController = LocalNavController.current

        // ---------------------------------------------------------------- My corrections, first
        // His order (28.10.2026): "please put my corrections at the top … so i can always reach it
        // first". Then the model, which he put at the top the day before.
        val rules by MaDictationCorrections.rules.collectAsState()
        Preference(
            title = stringRes(R.string.settings__ma_corrections__title),
            summary = "Words you enter to be replaced (heard → write), and the fixes learned from " +
                "your corrections: ${rules.count { it.isActive }} in use.",
            onClick = { navController.navigate(Routes.Settings.MaDictationCorrections) },
        )

        // ---------------------------------------------------------------- the model
        // His order (27.10.2026): "At the top, I want to have the radio buttons for the models."
        // Exceptions to switches-first, named by him for this screen.
        val aiKeyOn by prefs.dictate.maAiPredictKey.collectAsState()
        PredictionModelChooser(enabled = aiKeyOn)

        // ---------------------------------------------------------------- on and off
        // Right after the model, his order (27.10.2026): the row's own behaviour, then the language.
        Spacer(Modifier.height(12.dp))
        SwitchPreference(
            prefs.dictate.maPredictRowAutoHide,
            title = stringRes(R.string.pref__ma_predict_row_auto_hide__label),
            summary = "Off: the prediction row is always on screen, dark when there is nothing in it, " +
                "so no key moves. On: the row comes and goes with its words.",
        )
        SwitchPreference(
            prefs.dictate.maDetectTypingLanguage,
            title = stringRes(R.string.pref__ma_detect_typing_language__label),
            summary = "Suggestions in the language you are typing: English, or Croatian for anything " +
                "that is not English. Read from your last few words; the HR/ENG key still sets the " +
                "dictation language. Off: suggestions follow the HR/ENG key.",
        )
        SwitchPreference(
            prefs.dictate.maAiPredictKey,
            title = stringRes(R.string.pref__ma_ai_predict_key__label),
            summary = "The last key of the row asks the model above for five words when the guesses " +
                "are wrong. The word you pick is taught to Learn my words. Each press is one request.",
        )
        SwitchPreference(
            prefs.dictate.maAiLivePredict,
            title = stringRes(R.string.pref__ma_ai_live_predict__label),
            summary = "While you type, Claude Haiku is asked after each short pause, in parallel with " +
                "the local suggestions. Its words appear after them, behind a small sparkle. Each pause " +
                "is one small request on your Anthropic key; nothing in incognito or password fields.",
        )
        SwitchPreference(
            prefs.suggestion.nextWordPrediction,
            modifier = Modifier.settingsSearchAnchor("pref__suggestion__next_word_prediction__label"),
            title = stringRes(R.string.pref__ma_next_word__label),
            summary = "Offers the next word as soon as you finish one.",
        )
        SwitchPreference(
            prefs.suggestion.autoCorrect,
            modifier = Modifier.settingsSearchAnchor("pref__suggestion__auto_correct__label"),
            title = stringRes(R.string.pref__ma_auto_correct__label),
            summary = stringRes(R.string.pref__suggestion__auto_correct__summary),
        )
        SwitchPreference(
            prefs.suggestion.multilingualTyping,
            modifier = Modifier.settingsSearchAnchor("pref__suggestion__multilingual_typing__label"),
            title = stringRes(R.string.pref__ma_multilingual__label),
            summary = stringRes(R.string.pref__suggestion__multilingual_typing__summary),
        )
        SwitchPreference(
            prefs.dictate.maNgramEnabled,
            title = stringRes(R.string.pref__ma_ngram_enabled__label),
            summary = "Suggests from what you have written on this phone, including names and words " +
                "no dictionary has, and learns a typo you correct. Never leaves the phone; nothing " +
                "is learned in incognito mode.",
        )
        SwitchPreference(
            prefs.dictate.maCorrectionsEnabled,
            title = stringRes(R.string.pref__ma_corrections_enabled__label),
            summary = "A word you fix in a dictation, fixed the same way twice, is fixed for you " +
                "from then on, and the wrong word stops being suggested. Your own fixes are under " +
                "My corrections below.",
        )
        SwitchPreference(
            prefs.suggestion.api30InlineSuggestionsEnabled,
            modifier = Modifier.settingsSearchAnchor("pref__suggestion__api30_inline_suggestions_enabled__label"),
            title = stringRes(R.string.pref__ma_inline__label),
            summary = "Passwords and addresses your phone offers to fill in, shown in the row.",
            visibleIf = { AndroidVersion.ATLEAST_API30_R },
        )

        // ---------------------------------------------------------------- how the words show
        Spacer(Modifier.height(8.dp))
        ListPreference(
            prefs.suggestion.displayMode,
            modifier = Modifier.settingsSearchAnchor("pref__suggestion__display_mode__label"),
            title = stringRes(R.string.pref__suggestion__display_mode__label),
            entries = enumDisplayEntriesOf(CandidatesDisplayMode::class),
        )

        // ---------------------------------------------------------------- what was learned
        Text(
            text = "Learned on this phone",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
        MaNgramSetting(showSwitch = false)
        Preference(
            title = "Teach it from my dictations",
            summary = "Adds the names and words from your dictation history to the keyboard's dictionary.",
            onClick = { navController.navigate(Routes.Settings.MaVocabulary) },
        )

        Text(
            text = "The HR/ENG key sets the dictation language. Suggestions follow the language you type " +
                "when \"Detect typing language\" is on, and the HR/ENG key when it is off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

/**
 * The Claude model the AI key asks: a radio list with each model's real name, as on Prompts.
 * Claude only, because the AI key is paid with the Anthropic key loaded in the app.
 */
@Composable
private fun PredictionModelChooser(enabled: Boolean) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val current by prefs.dictate.maPredictModel.collectAsState()
    val account = MaProviders.rewordingAccount()
    val cached = account.cachedModels
    val hasKey = account.apiKey.isNotBlank() || MaProviders.transcriptionAccount().apiKey.isNotBlank()
    val notes = mapOf(
        "haiku" to "the fastest and cheapest; right for five words",
        "sonnet" to "about 10× the price of Haiku",
        "opus" to "about 50× the price of Haiku",
    )

    Text(
        text = stringRes(R.string.settings__ma_predict_model__title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
    Text(
        // Says whether a key IS loaded (phone test, build 421: it only said what pays). Never the key.
        text = if (hasKey) "Anthropic key: loaded. The AI key is paid with it."
        else "Anthropic key: none loaded. Add one under API keys and permissions; until then the AI key does nothing.",
        style = MaterialTheme.typography.bodySmall,
        color = if (hasKey) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    for (family in listOf("haiku", "sonnet", "opus")) {
        val alias = MaClaudeModels.alias(family)
        val name = MaClaudeModels.displayName(MaClaudeModels.resolve(alias, cached))
        val choose = { scope.launch { prefs.dictate.maPredictModel.set(alias) }; Unit }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = choose)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = current == alias, onClick = choose, enabled = enabled)
            Column {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = notes.getValue(family),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
