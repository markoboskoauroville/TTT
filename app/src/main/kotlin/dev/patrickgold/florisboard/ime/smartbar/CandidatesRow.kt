/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.Icons
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import dev.patrickgold.florisboard.dictate.MaProviders
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.NlpManager
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.nlp.MaAiPredict
import dev.patrickgold.florisboard.dictate.nlp.MaTypingLanguage
import dev.patrickgold.florisboard.dictate.nlp.MaNgram
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import dev.patrickgold.florisboard.editorInstance
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer
import androidx.compose.ui.text.font.FontWeight
import org.florisboard.lib.snygg.ui.SnyggText

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()
    val editorInstance by context.editorInstance()

    val scope = rememberCoroutineScope()
    val displayMode by prefs.suggestion.displayMode.collectAsState()
    val ordinaryCandidates by nlpManager.activeCandidatesFlow.collectAsState()

    // The AI suggestions, when he has asked for some.
    //
    // They REPLACE the row rather than being appended to it. Appending would leave him choosing
    // between two kinds of guess with nothing on screen saying which was which, and the whole reason
    // he pressed the key is that the ordinary guesses were wrong.
    val aiWords = MaAiPredict.words.value
    val aiBusy = MaAiPredict.busy.value
    // Haiku's words while typing (MaAiPredict.live), beside the local ones, never instead of them.
    val liveWords = MaAiPredict.live.value
    val liveOn by prefs.dictate.maAiLivePredict.collectAsState()
    val liveCandidates = remember(ordinaryCandidates, liveWords, aiWords) {
        if (aiWords.isNotEmpty()) emptyList() else {
            val local = ordinaryCandidates.map { it.text.toString().lowercase() }.toSet()
            liveWords.filter { it.lowercase() !in local }.map { word ->
                WordSuggestionCandidate(
                    text = word,
                    secondaryText = null,
                    confidence = 0.9,
                    isEligibleForAutoCommit = false,
                    sourceProvider = null,
                )
            }
        }
    }
    val candidates = remember(ordinaryCandidates, aiWords) {
        if (aiWords.isEmpty()) {
            ordinaryCandidates
        } else {
            aiWords.map { word ->
                WordSuggestionCandidate(
                    text = word,
                    secondaryText = null,
                    confidence = 0.95,
                    // Never auto-committed. An auto-commit is the keyboard deciding for him, and a
                    // guess he paid for and has not read yet is the last thing that should be
                    // inserted without a tap.
                    isEligibleForAutoCommit = false,
                    sourceProvider = null,
                )
            }
        }
    }
    // Read once per composition instead of a synchronous pref get() per candidate on every keystroke
    // (the candidates row recomposes on each character — issue: typing jank).
    val longPressDelay by prefs.keyboard.longPressDelay.collectAsState()
    // The AI key can be switched off under Settings → Auto prediction.
    val aiKeyShown by prefs.dictate.maAiPredictKey.collectAsState()
    // Back to the start whenever the words change. The row kept its old scroll position, so new
    // words began half under the badge: "hello" read as "ello" (phone tests, builds 421 and 423).
    val rowScroll = rememberScrollState()
    LaunchedEffect(candidates) { rowScroll.scrollTo(0) }
    // AI WORDS WHILE TYPING: after a short pause, Haiku is asked about the text before the cursor, in
    // parallel with the local engine (4.11.2026). collectLatest drops a question the moment the text
    // moves on, and the words of the last one are cleared at once, so nothing stale is ever shown.
    // Never with the setting off, in incognito, in a password field, with no Anthropic key, or while
    // the AI key's own words are showing.
    LaunchedEffect(editorInstance, liveOn) {
        if (!liveOn) {
            MaAiPredict.clearLive()
            return@LaunchedEffect
        }
        editorInstance.activeContentFlow
            .map { it.textBeforeSelection.toString() to it.currentWordText.toString() }
            .distinctUntilChanged()
            .collectLatest { (before, current) ->
                if (MaAiPredict.liveFor != before) MaAiPredict.clearLive()
                if (before.isBlank() || MaAiPredict.words.value.isNotEmpty()) return@collectLatest
                if (keyboardManager.activeState.isIncognitoMode || maIsSecretField(editorInstance.activeInfo.base.inputType)) return@collectLatest
                if (MaProviders.rewordingApiKey().isBlank()) return@collectLatest
                delay(MaAiPredict.LIVE_PAUSE_MS)
                // Haiku already taught the local engine enough for this spot (MaTeacherMemory): its
                // words are in the local row, so it is not asked again. This is where it gets cheaper.
                if (MaNgram.teacherWords(before, current).size >= MaAiPredict.LIVE_WANTED) return@collectLatest
                val reply = DictateController.askLiveModel(
                    MaAiPredict.prompt(
                        before = before,
                        current = current,
                        language = if (MaTypingLanguage.of(before) == MaLanguage.HR) "Croatian" else "English",
                        wanted = MaAiPredict.LIVE_WANTED,
                    ),
                ) ?: return@collectLatest
                MaAiPredict.remember(before)
                MaAiPredict.liveFor = before
                val words = MaAiPredict.readWords(reply, current, MaAiPredict.LIVE_WANTED)
                MaAiPredict.live.value = words
                // Taught to the local engine as we go (4.11.2026), so next time it knows this spot itself.
                MaNgram.rememberTeacher(before, current, words, keyboardManager.activeState.isIncognitoMode)
            }
    }
    // The AI words are for the text they were asked about, and go the moment it changes: a letter
    // typed, one deleted, the field emptied or the cursor moved. Picking one, or pressing the key
    // again, clears them too (below).
    LaunchedEffect(editorInstance) {
        editorInstance.activeContentFlow
            .map { it.textBeforeSelection.toString() }
            .distinctUntilChanged()
            .collect { before ->
                if (MaAiPredict.words.value.isNotEmpty() && !MaAiPredict.stillFor(before)) MaAiPredict.clear()
            }
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            // LONG PRESS THE BAR TO CLOSE IT. His gesture, and the only thing that moves this row
            // now that the automatic switching is gone.
            //
            // On the ROW rather than on a key: the whole strip is the target, so he does not have to
            // aim at anything while a word is under his thumb. And a long press rather than a tap,
            // because a tap on this row picks a word — the two gestures cannot share.
            //
            // `pointerInput` before the scroll modifier, so a long press is seen even where the row
            // scrolls. After it, a horizontal drag would win the gesture and the press would be
            // swallowed on exactly the crowded rows where he most wants it gone.
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        scope.launch {
                            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
                            prefs.smartbar.sharedActionsExpanded.set(
                                !prefs.smartbar.sharedActionsExpanded.get(),
                            )
                        }
                    },
                )
            }
            .conditional(displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidates.size > 1) {
                florisHorizontalScroll(state = rowScroll, scrollbarHeight = CandidatesRowScrollbarHeight)
            },
        horizontalArrangement = if (candidates.size > 1) {
            Arrangement.Start
        } else {
            Arrangement.Center
        },
    ) {
        if (candidates.isNotEmpty() || liveCandidates.isNotEmpty()) {
            val candidateModifier = if (candidates.size + liveCandidates.size == 1) {
                Modifier
                    .fillMaxHeight()
                    .weight(1f, fill = false)
            } else {
                Modifier
                    .fillMaxHeight()
                    .conditional(displayMode == CandidatesDisplayMode.CLASSIC) {
                        weight(1f)
                    }
                    .conditional(displayMode != CandidatesDisplayMode.CLASSIC) {
                        wrapContentWidth().widthIn(max = 160.dp)
                    }
            }
            val list = when (displayMode) {
                CandidatesDisplayMode.CLASSIC -> candidates.subList(0, 3.coerceAtMost(candidates.size))
                else -> candidates
            }.let { ordered ->
                // The likeliest word goes in the MIDDLE, not on the left.
                //
                // Reading order puts the best guess where the eye starts, which sounds right and is
                // not: the thumb rests under the centre of the row, so the word most often wanted
                // was the one furthest from the thumb, and every acceptance cost a reach. The
                // middle is the cheapest place on a phone, so the likeliest word belongs there.
                //
                // Only for three. With two there is no middle to speak of, and with four or more the
                // centre stops being a single obvious place — so the rule that would make it better
                // for three would make it arbitrary for the rest.
                if (ordered.size == 3) listOf(ordered[1], ordered[0], ordered[2]) else ordered
            } + liveCandidates
            val firstLive = list.size - liveCandidates.size
            for ((n, candidate) in list.withIndex()) {
                if (n == firstLive && liveCandidates.isNotEmpty()) {
                    // A small sparkle where Haiku's words begin, so the two kinds are told apart.
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp).align(Alignment.CenterVertically),
                    )
                } else if (n > 0) {
                    SnyggSpacer(
                        elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .align(Alignment.CenterVertically),
                    )
                }
                // The word under the finger, captured by value.
                //
                // This read `candidates[n]` — the ORIGINAL list at the DISPLAYED position. That was
                // harmless while the two were the same list, and became a wrong-word bug the moment
                // the middle-weighted order above made position 0 hold candidate 1. Tapping the
                // left word would have committed the middle one.
                //
                // The loop variable is already the right object, and in Kotlin it is a fresh binding
                // per iteration, so capturing it in the lambda is safe. The old comment saying
                // otherwise was the reason the index was there at all.
                val tapped = candidate
                CandidateItem(
                    modifier = candidateModifier,
                    candidate = candidate,
                    displayMode = displayMode,
                    // With three, the middle one is the best guess (§68). With any other count the
                    // list is in plain order, so the first is.
                    emphasised = n == (if (list.size == 3) 1 else 0),
                    onClick = {
                        // Taught to the local model BEFORE the commit, while the context that
                        // produced this suggestion is still the context on screen. After the commit
                        // the chosen word is itself part of the text before the cursor, and the
                        // lesson would contain its own answer.
                        //
                        // Only for a word he chose out of the AI's list. An ordinary candidate came
                        // from the local model or the dictionary, and teaching the model its own
                        // output back is how a prediction engine ends up certain of one word.
                        if (aiWords.isNotEmpty() || n >= firstLive) {
                            MaNgram.learn(
                                MaAiPredict.lesson(tapped.text.toString()),
                                keyboardManager.activeState.isIncognitoMode,
                            )
                        }
                        MaAiPredict.clear()
                        MaAiPredict.clearLive()
                        keyboardManager.commitCandidate(tapped)
                    },
                    onLongPress = {
                        val candidateItem = tapped
                        when {
                            // Words only now. The clipboard branch went with the clipboard
                            // suggestion itself; long press teaches the personal dictionary
                            // (issue #241), which is all this row carries.
                            else -> {
                                val subtype = subtypeManager.activeSubtype
                                val result = nlpManager.addToUserDictionary(subtype, candidateItem)
                                val message = when (result) {
                                    NlpManager.AddToDictionaryResult.ADDED ->
                                        R.string.suggestion__added_to_dictionary
                                    NlpManager.AddToDictionaryResult.ALREADY_PRESENT ->
                                        R.string.suggestion__already_in_dictionary
                                    NlpManager.AddToDictionaryResult.UNAVAILABLE -> null
                                }
                                if (message != null) {
                                    // Haptic as well as the toast: Android suppresses toasts entirely when
                                    // the user has turned notifications off for the app, and a silent
                                    // long-press would look broken.
                                    FlorisImeService.inputFeedbackController()?.keyLongPress()
                                    scope.launch {
                                        context.showShortToast(
                                            message,
                                            "word" to candidateItem.text.toString(),
                                        )
                                    }
                                }
                                result != NlpManager.AddToDictionaryResult.UNAVAILABLE
                            }
                        }
                    },
                    longPressDelay = longPressDelay.toLong(),
                )
            }
        }

        // THE AI KEY, AT THE END OF THE ROW.
        //
        // Always the last thing, whether or not there are candidates: a key that moved with the
        // number of guesses would be somewhere different every keystroke, and this row is used
        // without being read.
        //
        // Pressed once, it asks; pressed again while its words are showing, it puts the ordinary
        // guesses back. Nothing else clears it — typing does, on the next prediction, because the
        // words would no longer be about the word being typed.
        if (aiKeyShown) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
                text = if (aiBusy) "\u2026" else "AI",
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth()
                    // Centred in the row like the words (build 421: it sat small and high).
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp)
                    .clickable(enabled = !aiBusy) {
                        if (aiWords.isNotEmpty()) {
                            MaAiPredict.clear()
                            return@clickable
                        }
                        val content = editorInstance.activeContent
                        val before = content.textBeforeSelection.toString()
                        val current = content.currentWordText.toString()
                        MaAiPredict.remember(before)
                        MaAiPredict.busy.value = true
                        scope.launch {
                            val reply = DictateController.askPredictionModel(
                                MaAiPredict.prompt(
                                    before = before,
                                    current = current,
                                    // The badge, not the subtype. The two are linked today — the EN/HR
                                    // key moves the subtype as well — but the badge is the control he
                                    // presses and the one the personal model is now split by, so it is
                                    // the single source the whole prediction stack reads.
                                    // 27.10.2026: the language he is typing (MaTypingLanguage), which is
                                // the badge when detection is off.
                                language = if (MaTypingLanguage.of(before) == MaLanguage.HR) "Croatian" else "English",
                                ),
                            )
                            MaAiPredict.busy.value = false
                            // He typed on while it was asked: the answer is about text that is gone.
                            if (!MaAiPredict.stillFor(editorInstance.activeContent.textBeforeSelection.toString())) {
                                MaAiPredict.clear()
                                return@launch
                            }
                            // Nothing back, or nothing usable, leaves the row exactly as it was. There
                            // is no failure worth a message here: he can see the words did not change,
                            // and the guesses he already had are still under his thumb.
                            MaAiPredict.words.value = reply
                                ?.let { MaAiPredict.readWords(it, current) }
                                .orEmpty()
                        }
                    },
            )
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    /** Drawn larger. True for the likeliest word, which §68 moved to the middle. */
    emphasised: Boolean = false,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long,
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }
    // THE WORD TAPPED IS THE WORD SHOWN (Marko, 4.11.2026: "tapping on words in suggestion row is
    // typing wrong word", "since since since"). The gesture below is installed once per slot
    // (pointerInput(Unit)), so it kept calling the FIRST onClick it was given: when the words in the
    // row changed, a slot still committed the word it had shown before. Read through updated state,
    // the gesture always calls the lambda of the word on screen now.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentLongPressDelay by rememberUpdatedState(longPressDelay)

    val elementName = FlorisImeUi.SmartbarCandidateWord.elementName
    // Remembered so recomposing the row on each keystroke doesn't allocate a fresh map (which, as an
    // unstable arg to the Snygg composables below, would also defeat their skipping) — reduces the
    // per-keystroke recomposition + GC churn behind the typing jank.
    val autoCommit = candidate.isEligibleForAutoCommit
    val attributes = remember(autoCommit) { mapOf("auto-commit" to if (autoCommit) 1 else 0) }
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(currentLongPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (currentOnLongPress()) {
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        currentOnClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = if (displayMode == CandidatesDisplayMode.CLASSIC) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                // Gboard-style: bold the suggestion that will be auto-applied (autocorrect), so it's clear
                // what will replace the typed word; other suggestions stay normal weight (issue #150).
                // Bold marks the likeliest word, which is the one in the MIDDLE.
                //
                // It used to follow isEligibleForAutoCommit, which is a property of the FIRST
                // candidate — so after §68 moved the best guess to the centre, the bold stayed on
                // the left and pointed at the wrong word. He spotted it from a screenshot.
                //
                // Emphasis is now one decision, made by position, so the large word and the bold
                // word are the same word and cannot drift apart again.
                fontWeight = if (emphasised) FontWeight.Bold else null,
                // The likeliest word, larger than its neighbours.
                //
                // A scale rather than a size: it multiplies whatever the theme decided, so a bigger
                // or smaller keyboard font stays bigger or smaller and this stays a fifth larger
                // than its neighbours either way. A hardcoded size here would look right on his
                // theme and wrong on every other.
                //
                // Modest on purpose. It has to be readable as "this is the one" at a glance without
                // making the row jump about as the ranking changes between keystrokes.
                fontSizeScale = if (emphasised) 1.2f else null,
                text = candidate.text.toString(),
            )
            if (candidate.secondaryText != null) {
                SnyggText(
                    elementName = "$elementName-secondary-text",
                    attributes = attributes,
                    selector = selector,
                    text = candidate.secondaryText!!.toString(),
                )
            }
        }
    }
}

/** A password field of any kind: Haiku is never asked about what is typed there. */
private fun maIsSecretField(inputType: Int): Boolean {
    val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
    return when (inputType and android.text.InputType.TYPE_MASK_CLASS) {
        android.text.InputType.TYPE_CLASS_TEXT -> variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        android.text.InputType.TYPE_CLASS_NUMBER -> variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
