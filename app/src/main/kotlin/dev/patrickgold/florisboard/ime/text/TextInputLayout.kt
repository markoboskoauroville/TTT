/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.dictate.gif.GifSearchPanel
import dev.patrickgold.florisboard.dictate.ui.MaFeatureRow
import dev.patrickgold.florisboard.dictate.MaReader
import dev.patrickgold.florisboard.dictate.ui.MaSubtitleRow
import dev.patrickgold.florisboard.dictate.ui.MaExtraRow
import dev.patrickgold.florisboard.ime.clipboard.ClipboardEditorPanel
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSearchPanel
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.InlineSuggestionsStyleCache
import dev.patrickgold.florisboard.ime.smartbar.Smartbar
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()
    val clipboardEditorActive by keyboardManager.clipboardEditorText.collectAsState()
    val emojiSearchActive by keyboardManager.emojiSearchQuery.collectAsState()
    val gifSearchActive by keyboardManager.gifSearchQuery.collectAsState()

    InlineSuggestionsStyleCache()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        // THE READER, BEFORE ANYTHING ELSE.
        //
        // This branch sits at the very top of the Column on purpose. It used to sit two thirds of
        // the way down, which meant the early return skipped only what came AFTER it — the status
        // line, the edit strip and the number row had all already been composed, so "full screen"
        // arrived with three bands above it and looked like a large box rather than a screen.
        //
        // Above everything, the return is total: while he is reading with the box expanded, the
        // reader is the entire keyboard.
        val maReaderSubtitle = prefs.dictate.maReaderDisplay.collectAsState().value == "subtitle"
        val maReaderFull by prefs.dictate.maReaderFullscreen.collectAsState()
        val maReaderReading = MaReader.currentIndex >= 0
        if (maReaderSubtitle && maReaderFull && maReaderReading) {
            // THE WHOLE DISPLAY, not the whole keyboard.
            //
            // An input method draws in its own window, and that window is as tall as the view asks
            // to be. Asking for the keyboard's height gave a keyboard-sized void with the chat still
            // above it — a half void, which is what he got and what he correctly refused.
            //
            // Asking for the display's height makes the window cover the screen. The app behind
            // pans up as it does for any tall keyboard; nothing is hidden from it that it needs.
            MaSubtitleRow(
                modifier = Modifier.height(LocalConfiguration.current.screenHeightDp.dp),
            )
            return@Column
        }

        // While an emoji search is running (issue #110), the search panel takes the Smartbar's slot so the
        // keyboard layout below stays available for typing the query.
        if (clipboardEditorActive != null) {
            // Writing or rewriting a clipboard note. Same arrangement as the two searches: the editor
            // takes the Smartbar's slot and the keyboard below it does the typing, because an input
            // method has no way to type into a field of its own.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight),
            ) {
                ClipboardEditorPanel()
            }
        } else if (emojiSearchActive != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight),
            ) {
                EmojiSearchPanel()
            }
        } else if (gifSearchActive != null) {
            // GIF search: a results strip takes the Smartbar's slot; the keyboard below types the query.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight),
            ) {
                GifSearchPanel()
            }
        } else {
            // Back above the keys, where it was before build 139 and where he is used to it.
            //
            // It was moved to the bottom so nothing it showed could cover the text being written.
            // That reasoning was sound and the result was still wrong: a status line at the bottom
            // is below the feature rows, past the edge of where his eye goes, and he reads it by
            // looking for it rather than by noticing it. Habit beat the argument, which is the
            // right way round for something read a hundred times a day.
            // The word-prediction row belongs to the open keyboard (Marko, 10.11.2026): with the letters folded away it is not composed at all.
            val maZoneKeyboardForSmartbar by prefs.dictate.maZoneKeyboard.collectAsState()
            if (maZoneKeyboardForSmartbar) Smartbar()
        }
        // The macro bar is gone. It was the editable strip of user macros — undo, redo, all, copy —
        // and it has been superseded by the M1 to M10 buttons in the feature rows, which do the same
        // job inside the one editor that arranges everything else. It kept drawing its default
        // preset because it needs no switch of its own: an empty bar draws nothing, and nobody had
        // emptied it, so a bar nobody had chosen appeared above the keyboard.
        // The copy row, in the space the macro bar fix freed in build 88.
        //
        // This slot used to hold `LegacyEditRow`, and the comment here claimed it was the same row
        // the transcription view draws. It was not, and it never had been: that row read
        // `legacyActionRow` from the `LegacyEditAction` vocabulary, while the transcription view
        // read `maCopyRow` from `MaFeatureKey`. Two preferences, two key sets, two editors, one
        // name — which is why the two rows ended in different keys, an AC in one and the view-swap
        // microphone in the other, while both were described as one row. **A comment is not
        // evidence.**
        //
        // It is the real copy row now, from the same composable and the same preference as the
        // transcription view, so arranging it once arranges it in both places and they cannot drift
        // again.
        //
        // `maEditRow` still switches it — the copy-row key on the feature row, key 3, the one he
        // already presses — so the switch keeps its meaning and its key. In the transcription view
        // there is no switch at all.
        //
        // `drawChrome = false` because the keyboard already draws a full `MaFeatureRow` at the
        // bottom, and the wand bar, the magic row and the reader dashboard belong to that one.
        if (!state.isActionsOverflowVisible) {
            val maEditRowEnabled by prefs.dictate.maEditRow.collectAsState()
            if (maEditRowEnabled) {
                MaFeatureRow(
                    modifier = Modifier.fillMaxWidth(),
                    // The same height as the feature rows below and as the Smartbar above, because
                    // it is now a row of the same keys drawn by the same code. The strip it
                    // replaces had a height of its own, 46dp, which was a third measurement on a
                    // keyboard that only ever needed one.
                    rowHeight = FlorisImeSizing.smartbarHeight,
                    copyRowOnly = true,
                    drawChrome = false,
                )
            }

            // The bucket row, under the copy row, switched by its own preference.
            //
            // Its own switch rather than sharing the copy row's: they are shown at different times.
            // The copy row is on nearly always; this one is on while he is collecting.
            // The reader row, above the bucket row: the reading is what he is listening to, and the
            // controls for it should be nearer the text than the clipboard is.
            val readerRowShown by prefs.dictate.maReaderRowShown.collectAsState()
            if (readerRowShown) {
                MaFeatureRow(
                    modifier = Modifier.fillMaxWidth(),
                    rowHeight = FlorisImeSizing.smartbarHeight,
                    readerRowOnly = true,
                    drawChrome = false,
                )
            }

            val bucketRowShown by prefs.dictate.maBucketRowShown.collectAsState()
            if (bucketRowShown) {
                MaFeatureRow(
                    modifier = Modifier.fillMaxWidth(),
                    rowHeight = FlorisImeSizing.smartbarHeight,
                    bucketRowOnly = true,
                    drawChrome = false,
                )
            }
        }
        // The number row stands on its own, like the feature rows do.
        //
        // It used to be gated on `maZoneKeyboard` as well as its own switch, on the reasoning that
        // folding the keys away should take the number row with them. That reasoning treated it as
        // part of the keyboard, and it is not: it is a row he switches on and off with key 1,
        // exactly as key 3 does for the copy row. Tying it to the keys meant key 1 did nothing at
        // all whenever the keys were folded — a switch that silently does nothing in the state
        // where it is most wanted.
        //
        // Its own switch still decides whether it is there. Only the borrowed condition is gone.
        val maZoneKeyboard by prefs.dictate.maZoneKeyboard.collectAsState()
        if (!state.isActionsOverflowVisible) {
            MaExtraRow()
        }
        // The subtitle row, above the keys and below the status line.
        //
        // Placed here rather than at the bottom because the eye is already on the text being read,
        // which is above the keyboard — the closer the words are to it, the less the eye travels.
        //
        // Draws nothing at all when nothing is being read, so it costs no space the rest of the
        // time. That is why it can sit in the layout unconditionally instead of being switched in
        // and out, which would make the keyboard change height mid-reading.
        if (maReaderSubtitle) {
            MaSubtitleRow()
        }
        if (state.isActionsOverflowVisible) {
            QuickActionsOverflowPanel()
        } else {
            // Zone two, part two: the keys themselves, from the letters to the bottom row.
            //
            // Not composed at all rather than filtered row by row. The keyboard sets its own height,
            // so leaving it out returns that height exactly, with no arithmetic anywhere that could
            // fall out of step and leave a band of nothing where the keys used to be. That is what
            // went wrong when this was done by removing rows.
            //
            // Nothing is stranded by this. The arrow strip and the feature row always survive, the
            // feature row carries the way back, and it carries the microphone so the dictation view
            // is still one key away with the whole keyboard closed.
            if (maZoneKeyboard) Box {
                val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
                val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                    incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                if (showIncognitoIcon) {
                    SnyggIcon(
                        FlorisImeUi.IncognitoModeIndicator.elementName,
                        modifier = Modifier
                            .matchParentSize()
                            .align(Alignment.Center),
                        painter = painterResource(R.drawable.ic_incognito),
                    )
                }
                TextKeyboardLayout(evaluator = evaluator)
            }
            // Arrow strip along the very bottom, below the letters, exactly where the reference
            // keyboard puts it.
            // The cursor row is gone with the macro bar: the chevrons and the fold key that sat
            // between the macro bar and the feature rows. Its fold key is now the zone keys in the
            // feature row, and its arrows are available as ordinary buttons from the key picker, so
            // everything it did survives somewhere the editor can arrange.
            // The feature row, along the very bottom, the same one the transcribe view draws and
            // from the same code, so the two views cannot drift apart.
            //
            // It has to be here. The fold key that controls it lives in the arrow strip above, and
            // that strip is shared between both views, so without this row the key was drawn in the
            // keyboard view too and folded something that was not there. A control that does nothing
            // is worse than a missing one, because it teaches that pressing things has no effect.
            //
            // Folded away it is not composed at all, so the height is genuinely returned rather than
            // left as an empty strip holding the space it was asked to give up.
            val maFeatureRowShown by prefs.dictate.maFeatureRowShown.collectAsState()
            if (maFeatureRowShown) {
                // The pin sits in the top-left corner, over the first row rather than in it. In the
                // row it would be an ordinary key: editable, movable, and removable, and this is the
                // one control that must not be any of those. Somebody who has hidden the pin cannot
                // reach the pin to unhide it.
                Box(modifier = Modifier.fillMaxWidth()) {
                    MaFeatureRow(
                        modifier = Modifier.fillMaxWidth(),
                        // Per row, not for the block. There can be any number of rows now and the
                        // count changes while the keyboard is open, so the height cannot be decided
                        // here.
                        rowHeight = FlorisImeSizing.smartbarHeight,
                    )
                }
                // The pin was drawn over this row's top-left corner and looked like a badge stuck to
                // the gear key. It sits below the rows now, in the strip added at the end of this
                // Column.
            }

            // Nothing along the bottom any more. The pin's half-height strip is gone with it:
            // it cost a permanent band of screen to a control he sets once and then forgets, and
            // the same switch now lives in the feature row as an ordinary key he can place, move
            // or take off like any other.
        }
    }
}
