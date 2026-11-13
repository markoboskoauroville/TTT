/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
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

package dev.patrickgold.florisboard.dictate.ui

import dev.patrickgold.florisboard.dictate.MaProviders
import dev.patrickgold.florisboard.dictate.MaReprompt
import dev.patrickgold.florisboard.dictate.MaWhatsApp
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import dev.patrickgold.florisboard.dictate.overlay.MaAppSwitcher
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.overlay.MaScreenTargets
import androidx.compose.material.icons.filled.History
import dev.patrickgold.florisboard.dictate.MaReader
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.MaMagicTargets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import dev.patrickgold.jetpref.datastore.model.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.dictate.MaMacroSlots
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.MaRows
import dev.patrickgold.florisboard.dictate.MaMacroSyntax
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaKeyColors
import dev.patrickgold.florisboard.dictate.MaFeatureOrder
import dev.patrickgold.florisboard.dictate.MaFeatureKey
import androidx.compose.runtime.LaunchedEffect
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.dictate.MaBucketUndo
import dev.patrickgold.florisboard.dictate.MaClipCapture
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaSettingsResume
import dev.patrickgold.florisboard.dictate.DictateLongformMode
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.R

/**
 * The feature row: one row, drawn by both views and the last row standing when everything
 * above it is folded away.
 *
 * `AP · select-all · backspace · mic · book · 1 2 3 · enter`, **by default**. The order now lives in a
 * preference and is rearranged by the user in Settings, Mantra, Feature row; what follows is the
 * reasoning behind the default and the shape of the row, not a description of what is on screen.
 *
 * **This order is Marko's and it has been corrected twice.** At build 139 the switches were on the
 * left and the borrowed keys on the right, and he swapped them. At build 146 he moved the microphone
 * in beside backspace, put the book next to it, and sent enter to the far end. Do not rearrange this
 * row on a theory; it is arranged by the person using it.
 *
 * What the current order means, so a future change can tell what it would be breaking:
 *
 * - **The busy end is the end the thumb starts from.** AP, select-all and backspace are used inside
 *   a sentence; the numbered switches are reached a few times a session.
 * - **The two view swaps sit together.** The microphone and the book are the only keys here that
 *   change which view is on screen, and a pair that does one kind of thing is found by feel. They
 *   were at opposite ends of the row before, which is the arrangement that made that hard.
 * - **Enter is bottom right**, where every keyboard ever made has put it. Borrowing a habit somebody
 *   already has beats any argument about grouping.
 * - **The numbers read left to right** as the parts of the keyboard they control.
 * - **AP, select-all, backspace and enter are the keys that must survive a fold.** The first two are
 *   the copy row's most used pair; the last two are the only keys from the keyboard proper with no
 *   substitute anywhere else once zone two is shut.
 *
 * The rule it exists to satisfy is Marko's, and it is a good one: a feature you have to enable in a
 * settings app before you can see it is a feature most people never find. The dictation view is not
 * the full keyboard, so it has the vertical room the typing view does not, and this spends some of
 * that room on reach.
 *
 * Three things borrowed from keyboards that have already been judged by very large numbers of
 * people:
 *
 * - **Gboard put its shortcuts in key-shaped buttons and then deleted its overflow menu**, freeing
 *   the slot it occupied. A shortcut behind a menu is a shortcut to a menu. Every key here acts on
 *   the first tap; none of them opens a list of more keys except the dashboard, which is a list of
 *   switches rather than of actions.
 * - **HeliBoard distributes its toolbar keys evenly** rather than packing them from one edge. Equal
 *   weights below, so the row reads as a row and the thumb learns positions rather than icons.
 * - **Ten keys is exactly what the letter row already is.** The usual objection to ten controls in a
 *   phone-width row is that each falls under the 48dp touch minimum, and at a typical 360dp width
 *   these are about 36dp. But the QWERTY row above is ten keys at the same width, on the same
 *   device, in the same hand, and it has been usable for as long as touch keyboards have existed.
 *   The precedent is not theoretical, it is one row up.
 *
 * Colour is state only, as everywhere else in this app: gold ink on the near-black key, and green
 * on the three keys that are switches so their positions are readable without pressing them.
 */
@Composable
fun MaFeatureRow(
    modifier: Modifier = Modifier,
    rowHeight: Dp,
    /**
     * Draw ONLY the copy row, for the transcription view.
     *
     * That view wants the clipboard row and not the three feature rows, which belong to the typing
     * keyboard. Same composable either way, so a key behaves identically in both places — the caller
     * chooses which rows, not which implementation.
     */
    copyRowOnly: Boolean = false,
    /**
     * Draw the bucket row instead of the feature rows.
     *
     * A second special row beside `copyRowOnly`, and a second boolean rather than one enum for the
     * same reason the copy row got its own: **two booleans that are never both true are honest
     * about what they are** — two callers each asking for one specific row — where an enum invites
     * a third value nobody has designed. If a fourth special row is ever wanted, that is the moment
     * to introduce the enum, not before.
     */
    bucketRowOnly: Boolean = false,
    /**
     * Draw the reader row instead of the feature rows.
     *
     * **THE THIRD OF THESE, AND THE ENUM IS NOW DUE.** The comment on `bucketRowOnly` said two
     * booleans were honest about being two callers each asking for one row, and that a fourth
     * special row would be the moment to introduce an enum. Three is close enough that pretending
     * otherwise is rules-lawyering my own note.
     *
     * It is a boolean anyway, today, and that is a decision rather than an oversight: the refactor
     * touches four call sites in three files at the end of a long build, and §150a and §187a are
     * both what happens when a structural change is done in the tail of a session to save a round
     * trip. **The debt is named here rather than left to be discovered.** The next special row does
     * the enum first and adds itself second.
     */
    readerRowOnly: Boolean = false,
    /**
     * Draw the things that sit ABOVE the rows: the wand bar, the magic row, the reader dashboard.
     *
     * True everywhere except the typing keyboard's copy row, which is a second instance of this
     * composable on a screen that already has one. `maDashboardOpen` is file-level state and
     * `maMagicRowShown` is a preference, so both instances would draw them and the keyboard would
     * show two wand bars, two magic rows and two dashboards stacked on each other.
     *
     * The scroll stepper is deliberately NOT gated: it hangs off `scrollMenu`, which is local to
     * each instance, so it opens over the row whose key was actually held.
     */
    drawChrome: Boolean = true,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    // Three zones, and that is the whole keyboard, numbered as they are stacked on screen from the
    // keys outwards: 1 the number row, 2 the keys themselves, 3 the copy and paste row along the
    // top. Each has one switch and each switch has one key here.
    //
    // The numbering is Marko's and it is the arrangement, not a naming: he drew the three dividing
    // lines on the keyboard and these are the three parts they cut it into.
    val zone1 by prefs.dictate.maExtraRow.collectAsState()
    val zone2 by prefs.dictate.maZoneKeyboard.collectAsState()
    val zone3 by prefs.dictate.maEditRow.collectAsState()

    // Green when the zone is showing, dark when it is folded away, so the row is a map of what is
    // above it and can be read without pressing anything.
    //
    // Each key shows its own switch rather than what is actually on screen. Folding the keys away
    // with 2 takes the number row with them, since the number row sits inside that zone, but it
    // must not erase the decision about whether the number row should be there when the keys come
    // back. So 1 can be green while nothing is visible, and that is the truth about the switch.
    val onGreen = Color(0xFF6FA85A)

    // The long press that hid the whole row is gone, on every key.
    //
    // It was a gesture with no sign of itself and no undo: a long press anywhere on the row made the
    // row vanish, and nothing on screen then said what had happened or how to bring it back. A key
    // held a moment too long — while thinking, or while the phone was slow — cost the feature, and
    // the way back was a settings screen the user had no reason to connect with it.
    //
    // Hiding the row now lives in one place, the switch at the top of the feature row editor, where
    // it is labelled and reversible.

    // Select-all becomes deselect when there is a selection, so the key it draws has to know. Read
    // once here and handed down, exactly as the copy row reads it.
    val editorInstance by context.editorInstance()
    val editorContent by editorInstance.activeContentFlow.collectAsState()
    val hasSelection = editorContent.selection.isSelectionMode

    // Every row the user has, drawn one under another. The height is per row rather than for the
    // whole block, so the stack is as tall as it needs to be and the caller does not have to know
    // how many rows there are — which it cannot, since the number is a preference and changes while
    // the keyboard is open.
    // The clipboard history, for the CH row. Read once here rather than per key: nine keys each
    // collecting the same flow would recompose nine times for every copy anywhere on the phone.
    val clipboardManager by context.clipboardManager()
    val clipHistory by clipboardManager.historyFlow.collectAsState()


    // What C1 to C10 currently hold, in the order they were copied.
    val spacerTenths by prefs.dictate.maSpacerTenths.collectAsState()
    val keyWidthsRaw by prefs.dictate.maKeyWidths.collectAsState()
    // Once: the row keeps the look it had before the per-button width setting (H 40, F1 20, E 40), now written down so the editor shows it.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val sp = context.applicationContext.getSharedPreferences("ttt_misc", android.content.Context.MODE_PRIVATE)
        // His custom UI ships with the app: a fresh install, and every update with a newer shipped version, applies it.
        runCatching { dev.patrickgold.florisboard.dictate.MaUiConfig.applyShipped(context) }
        if (!sp.getBoolean("widths_migrated_v1", false)) {
            if (prefs.dictate.maKeyWidths.get().isBlank()) prefs.dictate.maKeyWidths.set(dev.patrickgold.florisboard.dictate.MaKeyWidths.migrationDefaults())
            sp.edit().putBoolean("widths_migrated_v1", true).apply()
        }
    }
    val clipDelaySelect by prefs.dictate.maClipDelaySelect.collectAsState()
    val clipDelayDelete by prefs.dictate.maClipDelayDelete.collectAsState()
    val clipDelayPaste by prefs.dictate.maClipDelayPaste.collectAsState()
    val capturedRaw by prefs.dictate.maClipCaptured.collectAsState()
    val capturedSlots = remember(capturedRaw) { MaClipCapture.parse(capturedRaw) }


    val rowsRaw by prefs.dictate.maRows.collectAsState()
    val macroRaw by prefs.dictate.maMacroSlots.collectAsState()
    val macroSlots = remember(macroRaw) { MaMacroSlots.parse(macroRaw) }
    // The colours he painted (Settings → Key colours), read here once for every key on the rows.
    val maKeyColorsRaw by prefs.dictate.maKeyColors.collectAsState()
    val maKeyColors = remember(maKeyColorsRaw) { MaKeyColors.parse(maKeyColorsRaw) }

    // Rows that are off are absent rather than empty, so the ones below move up and the keyboard is
    // genuinely shorter. When everything is off everywhere this hands back the settings key alone,
    // which is the floor that keeps a route back to the screen that would restore the rest.
    val storedRows = remember(rowsRaw) {
        if (rowsRaw.isBlank()) MaRows.defaultRows() else MaRows.parse(rowsRaw)
    }
    val copyRowRaw by prefs.dictate.maCopyRow.collectAsState()
    val copyRow = remember(copyRowRaw) { MaRows.parseCopyRow(copyRowRaw) }
    // Appended last, so it sits nearest the keys in the transcription view where the hand is.
    // visibleRows yields List<List<Button>>, so the copy row contributes its BUTTONS — appending
    // its entries would have been a type error hiding behind a plausible name.
    val copyButtons = if (copyRow.enabled) copyRow.visibleButtons else emptyList()

    // ONE COPY ROW IN THE WHOLE APP, AND THIS IS IT.
    //
    // It is drawn by `copyRowOnly = true` and by nothing else. Both views ask for it that way, so
    // both get the same buttons in the same order from the same preference, and the only difference
    // between them is *whether* it is asked for:
    //
    //   transcription view — always. Not a switch. That view has no letter keys and the clipboard is
    //                        its whole job, so a switch offering to remove it offers to empty the
    //                        screen.
    //   typing keyboard    — `maEditRow`, the copy-row key on the feature row. One row, one switch,
    //                        and it is the key he already presses.
    //
    // There used to be a second way in: the copy row appended to the feature rows here, behind
    // `maCopyRowOnKeyboard`. That is gone. A row with two ways of appearing and two switches is two
    // rows wearing one name, and it is how the keyboard ended up showing a copy row that did not
    // match the one in the transcription view.
    //
    // visibleRows yields List<List<Button>>, so the copy row contributes its BUTTONS — appending
    // its entries would have been a type error hiding behind a plausible name.
    // THE BUCKET ROW, the second special row.
    //
    // Its own preference, its own switch, its own arrangement — the copy row's shape exactly, and
    // for the same reason: it is a MODE rather than a set of keys. When he is collecting code
    // blocks he wants these six and nothing else; when he is not, he wants the space back.
    val bucketRaw by prefs.dictate.maBucketRow.collectAsState()
    val bucketRow = remember(bucketRaw) { MaRows.parseBucketRow(bucketRaw) }
    val bucketButtons = if (bucketRow.enabled) bucketRow.visibleButtons else emptyList()

    val readerRaw by prefs.dictate.maReaderRow.collectAsState()
    val readerRow = remember(readerRaw) { MaRows.parseReaderRow(readerRaw) }
    val readerButtons = if (readerRow.enabled) readerRow.visibleButtons else emptyList()

    val rows = when {
        copyRowOnly -> if (copyButtons.isNotEmpty()) listOf(copyButtons) else emptyList()
        bucketRowOnly -> if (bucketButtons.isNotEmpty()) listOf(bucketButtons) else emptyList()
        readerRowOnly -> if (readerButtons.isNotEmpty()) listOf(readerButtons) else emptyList()
        else -> MaRows.visibleRows(storedRows)
    }
    // The buckets this user actually has, and whether they are all holding something. Derived from
    // the same rows the keyboard is drawing, so the answer here and the answer the capture uses
    // cannot disagree about how many buckets exist.
    val visibleClipSlots = remember(storedRows) { MaRows.visibleClipSlots(storedRows) }
    // Never full when they are off, so nothing turns red while he is not using them.
    // What the magic key looks for, newest first. Editable in settings so a new site costs a line
    // of text rather than a build.
    val magicRaw by prefs.dictate.maMagicTargets.collectAsState()
    // What the last scan found, held only while the picker is open.
    val activeLangCode by prefs.dictate.activeInputLanguage.collectAsState()
    val langMode by prefs.dictate.maLanguageMode.collectAsState()
    val scrollPages by prefs.dictate.maScrollPages.collectAsState()
    var scrollMenu by remember { mutableStateOf(false) }
    // Long press on the H or E key: the engines for that language, pick one and it closes (Marko, 9.11.2026). "hr" / "en" / null.
    var engineMenu by remember { mutableStateOf<String?>(null) }
    // Where the pressed key sits in the keyboard window (updated by the key itself), the anchor of the engine panel.
    val engineKeyRect = remember { arrayOf<androidx.compose.ui.geometry.Rect?>(null) }
    val reKeyRect = remember { arrayOf<androidx.compose.ui.geometry.Rect?>(null) }

    // The one-minute fill mark and its clock are gone. They existed to make a tick last long enough
    // to be seen; the ring on the key says the same thing for as long as it is true, so there is
    // nothing left to time, and the state that recorded which bucket filled last is gone with it.

    // What the wand is doing, shown in a bar above the keys.
    //
    // Observed rather than read once when the keyboard is drawn. He presses the button in another
    // app while the keyboard is still up, so nothing of ours is redrawn — which is exactly why the
    // wand appeared to do nothing at all. The bar has to be told, not asked.
    val magicRowShown by prefs.dictate.maMagicRowShown.collectAsState()
    val learn by DictateAccessibilityService.learnState.collectAsState()

    val magicAll = remember(magicRaw) {
        MaMagicTargets.parse(magicRaw).ifEmpty { MaMagicTargets.defaults() }
    }
    val magicTargets = remember(magicRaw) {
        // Ticked terms only, in the order the user dragged them into: the wand presses the first it
        // finds, so the order is the user's answer to "which did you mean" on a screen with two.
        MaMagicTargets.activeTerms(MaMagicTargets.parse(magicRaw))
            .ifEmpty { MaMagicTargets.activeTerms(MaMagicTargets.defaults()) }
    }
    // Changing the default is not enough on its own. Anyone whose preferences already hold an
    // explicit false — written by the old default, or by the FlorisBoard screen that still offers
    // the switch — would keep an empty history and ten dead keys, and would have no way to guess
    // that a clipboard setting three screens away is why. So when a C key is actually on a row,
    // recording is switched on. The keys cannot work without it and nothing else here reads it.
    val clipKeysPresent = remember(storedRows) {
        storedRows.any { row -> row.entries.any { it.button is MaRows.Button.Clip } }
    }
    LaunchedEffect(clipKeysPresent) {
        if (clipKeysPresent && !prefs.clipboard.historyEnabled.get()) {
            prefs.clipboard.historyEnabled.set(true)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
      // The wand's own bar, above the keys, in the place the recording bar uses.
      //
      // Everything this app does to somebody else's screen reports itself here. Waiting for a
      // button, and then asking before storing what it caught: he presses the thing, and the
      // keyboard asks whether that was the one. Before this the wand did its work in silence, which
      // is indistinguishable from doing nothing.
      // THE MESSAGE STRIP, above every row, where it covers no key.
      //
      // With the chrome, because it is chrome: one instance on the keyboard, drawn by the same
      // composable that owns the wand bar, so two feature rows cannot each show their own copy.
      if (drawChrome) {
          val messageText = MaMessage.text.value
          val messageSerial = MaMessage.serial.value
          LaunchedEffect(messageSerial) {
              if (messageText.isNotBlank()) {
                  delay(MaMessage.SHOW_MS)
                  MaMessage.clear()
              }
          }
          if (messageText.isNotBlank()) {
              Text(
                  text = messageText,
                  color = MaSand,
                  fontSize = 13.sp,
                  maxLines = 2,
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 4.dp),
              )
          }
      }
      if (drawChrome) MaWandBar(
          state = learn,
          onCopyDump = {
              // Onto the clipboard, which is where it has to go: it is far too long to read on a
              // phone and its whole purpose is to be pasted somewhere else.
              val dump = DictateAccessibilityService.dumpScreen()
              clipboardManager.addNewPlaintext(dump)
              MaMessage.show(context.getString(R.string.ma__wand_copied))
          },
          onDismiss = { MaScreenTargets.Learn.cancel() },
          onEdit = {
              // Straight to the wand's own screen, not to wherever the settings were last left.
              // The bookmark is right when he opens the settings to do something unrelated; here he
              // is already looking at the wand and asking for its list.
              MaScreenTargets.Learn.cancel()
              FlorisImeService.launchSettings("settings/dictate/magic")
          },
      )
      // The magic row: the wand, then one key per term he has taught it.
      //
      // Its own row rather than keys in the editor, because its contents are not a fixed set: it is
      // one button per term and the terms change as he learns them. A row that rewrites itself does
      // not belong in an editor where every other row is arranged by hand.
      //
      // Drawn above the feature rows, so it sits furthest from the typing. It is pressed
      // deliberately — send this, generate that — rather than reached for mid-sentence the way
      // backspace is.
      if (drawChrome && magicRowShown) {
        val magicKeys = remember(magicRaw) {
          MaMagicTargets.parse(magicRaw).filter { it.enabled }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          // The wand itself, first and always. Long press arms the screen dump, which is how a new
          // term gets its name — so the way to grow this row is on the row.
          // The circled wand, not the loose one. Rewording draws the loose wand for the feature
          // that fixes grammar with a model; two features sharing a picture means the picture has
          // stopped saying which one you are looking at, and these two are worth telling apart —
          // one presses a button on screen, the other sends text away and costs money.
          ThemedKey(
              code = KeyCode.NOOP,
              modifier = Modifier.width(56.dp).fillMaxHeight().padding(2.dp),
              onLongClick = {
                  if (!DictateAccessibilityService.isRunning) {
                      maOpenAccessibilitySettings(context)
                  } else {
                      DictateAccessibilityService.armLearn()
                  }
              },
              onClick = {
                  if (!DictateAccessibilityService.isRunning) {
                      maOpenAccessibilitySettings(context)
                  } else {
                      FlorisImeService.launchSettings("settings/dictate/magic")
                  }
              },
          ) { fg ->
              // The same finger the settings list uses, in the row's own colour.
              //
              // Two pictures for one feature is the fault this was meant to fix, and drawing a
              // circled wand here while the settings drew a finger simply moved the mismatch rather
              // than removing it. One mark, everywhere.
              //
              // No colour of its own either. Sand said "this leaves the keyboard", which this key
              // no longer does — it dumps the screen — so the colour was describing something that
              // had stopped being true.
              Icon(
                  imageVector = Icons.Default.TouchApp,
                  contentDescription = stringRes(R.string.ma__magic_button),
                  tint = fg,
                  modifier = Modifier.size(22.dp),
              )
          }
          magicKeys.forEach { target ->
            if (target.isSpacer) {
              // Room, and nothing else — the same idea as the feature row spacer and deliberately
              // the same setting, so one number moves both.
              //
              // A fixed width here rather than a weight, because this row scrolls sideways. A
              // weight inside a scrolling row has no finite width to take its share of, and the
              // feature row can use one only because it does not scroll. Ten tenths is one wand
              // key, so the number still means what it says on the other screen.
              // Its own width, stored on the spacer itself.
              //
              // The label field is unused on a spacer — there is nothing to write on a gap — so the
              // width lives there and costs no change to how targets are stored or parsed. A blank
              // one falls back to the shared setting, which is what every spacer made before this
              // existed will have.
              //
              // Ten tenths is one wand key, the same unit the Feature row screen uses, so a number
              // means the same thing on both screens.
              val tenths = target.label.trim().toIntOrNull()?.coerceIn(1, 200) ?: spacerTenths
              Spacer(
                  modifier = Modifier
                      .width((56 * tenths / 10f).dp)
                      .fillMaxHeight(),
              )
            } else {
            // One key per term, carrying the term itself. Pressing it presses that button and no
            // other — which is the whole difference from the wand, which guesses from a list.
            ThemedTextKey(
                // The face, which is the label when he has given one and the term when he has
                // not. "Stop responding" is the right thing to search for and the wrong thing to
                // write on a key.
                label = target.face,
                modifier = Modifier.fillMaxHeight().padding(2.dp),
                tint = null,
            ) {
                if (!DictateAccessibilityService.isRunning) {
                    maOpenAccessibilitySettings(context)
                } else {
                    DictateAccessibilityService.pressScreenTarget(listOf(target.term))
                }
            }
            }
          }
        }
      }

      // The dashboard, above the rows and below whatever is reading.
      //
      // Shown only while something is being read: it is a set of dials for a thing in motion, and a
      // dashboard for silence would just be a settings screen in the wrong place. Closing itself
      // when the reading stops also means he can never be left with a panel he has to dismiss.
      // THE DASHBOARD OPENS WHETHER OR NOT ANYTHING IS BEING READ.
      //
      // It was gated on `currentIndex >= 0` — a reading in progress — so a long press before he
      // started reading did nothing at all. **The settings he wants are the ones he wants BEFORE he
      // presses play**: the speed, the effect, where the line sits. Set them first, then read.
      //
      // The gate was never a decision. The dashboard was built while a reading was running and the
      // condition described the only state anybody had tried it in.
      if (drawChrome && maDashboardOpen) {
        MaReaderDashboard(onClose = { maDashboardOpen = false })
      }

      rows.forEach { rowButtons ->
        Row(
            modifier = Modifier.fillMaxWidth().height(rowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        val rowShares = dev.patrickgold.florisboard.dictate.MaKeyWidths.shares(
            rowButtons.map { maFeatureToken(it) }, keyWidthsRaw,
        )
        var keyIdx = -1

        // THE ARRANGEMENT IS THE USER'S. It was corrected by hand twice, at builds 139 and 146,
        // both times from a screenshot with arrows drawn on it and both times in the direction
        // opposite to what looked sensible from inside this file. That is the argument for the
        // editor: the person holding the keyboard should not have to send a picture and wait for a
        // build to move a key.
        //
        // The old ALWAYS_ON guarantee is gone with the fixed row, and it has to be. It could promise
        // backspace and enter survived because the row held every key there was and hiding was the
        // only edit; now a row is a list somebody builds, and a list can be built without them. What
        // replaces it is the editor warning before saving a set of rows with no way to delete or to
        // record, which is a warning about a real arrangement rather than a rule that makes some
        // arrangements unreachable.
        rowButtons.forEach { button ->
          keyIdx++
          val keyMod = Modifier.weight(rowShares.getOrElse(keyIdx) { 1f }).fillMaxHeight()
          // His colour for this key, if he painted one (Settings → Key colours). Provided rather
          // than passed, so every key drawn through ThemedKey takes it without each being changed.
          CompositionLocalProvider(
            LocalMaKeyPaint provides maKeyColors[MaKeyColors.idForFeature(maFeatureToken(button))],
          ) {
          when (button) {
            // A macro key: what the user wrote, on the face of it, running what he attached to it.
            //
            // M1 to M10. What the button does lives in MaMacroSlots, so the row holds only which
            // slot it is: the same macro can then sit in two rows, edited once.
            //
            // A long press is free on these keys now that folding is gone, if a macro ever wants
            // one for something of its own.
            //
            // An empty slot still draws, showing its own name. A macro button that vanished until
            // it was configured would leave the user hunting for a key that is not there yet, and
            // the label is how he knows which slot to go and fill in.
            is MaRows.Button.Macro -> {
                val slot = MaMacroSlots.at(macroSlots, button.slot)
                ThemedTextKey(
                    label = slot.label,
                    modifier = keyMod,
                    tint = null,
                ) {
                    if (!slot.isEmpty) {
                        MaMacroSyntax.run(slot.macro, FlorisImeService.currentInputConnection())
                    }
                }
            }

            // C1 to C10. Always paste-replace: select all, delete, paste.
            is MaRows.Button.Clip -> {
                // The slot's own text, not the history's newest-first ordering. C4 is the fourth
                // thing copied since the row was cleared and stays that until it is cleared again.
                // No switch in front of it any more. A bucket on the row is a live bucket: what it
                // holds is what it pastes, and the only reason it is empty is that nothing has been
                // copied into it yet.
                val text = MaClipCapture.at(capturedSlots, button.slot)
                ThemedKey(
                    code = KeyCode.NOOP,
                    // THE GREEN RING: THIS BUCKET IS HOLDING SOMETHING, AND IT STAYS UNTIL IT IS NOT.
                    //
                    // It replaces the tick that appeared for a minute after a copy landed. Marko
                    // asked for the change and the reasoning is sound: a tick is an EVENT and has
                    // to be caught while it is on screen, and he had already asked once for it to
                    // last longer. A ring is a STATE. It needs no clock, it cannot be missed by
                    // looking away, and "until I empty the bucket" is exactly what `text != null`
                    // already means.
                    //
                    // It is also the honest version of what he asked for next. The confirmation he
                    // watches inside the chat belongs to that app's copy button and nothing here
                    // can hold it there — but this answers the same question, on our own row, and
                    // it does stay until the bucket is poured out.
                    //
                    // On the colour rule: the ring around a key already means "this is a switcher",
                    // and that one is monochrome for the reason a row of tinted rings would read as
                    // decoration. This is a colour, so the two channels stay separate — ink means
                    // what kind of key, green means what state. If it reads as bolted on beside the
                    // switcher keys, say so and it goes back to a mark inside the key.
                    ring = if (text != null) onGreen else null,
                    // The key shows only its number: ten text previews across a row would be a few
                    // characters wide each and unreadable. What it holds is spoken instead, which is
                    // the only way to answer "what is on C4" without pasting it somewhere to find out.
                    modifier = keyMod.semantics {
                        contentDescription = MaClipCapture.describeSlot(text, button.slot)
                    },
                    onClick = {
                        if (text != null) {
                            scope.launch {
                                // Always replace. Never insert.
                                //
                                // This was behind a preference, and the screen that could switch
                                // that preference back on was deleted three builds ago — so an
                                // install holding a stored false had these keys inserting into
                                // whatever was already in the field, with no way to correct it.
                                // That is not a setting anybody wants wrong: pasting a bucket into
                                // the middle of existing text is how a document gets damaged rather
                                // than edited. The condition is gone, not defaulted.
                                // Each wait is its own setting. They are three different waits and
                                // the field that is too slow for one is often fine with the others,
                                // so one number for all three could only ever be wrong somewhere.
                                keyboardManager.activeState.isManualSelectionMode = false
                                if (clipDelaySelect > 0) delay(clipDelaySelect.toLong())
                                FlorisImeService.currentInputConnection()
                                    ?.performContextMenuAction(android.R.id.selectAll)
                                if (clipDelayDelete > 0) delay(clipDelayDelete.toLong())
                                FlorisImeService.currentInputConnection()?.commitText("", 1)
                                if (clipDelayPaste > 0) delay(clipDelayPaste.toLong())
                                FlorisImeService.currentInputConnection()?.commitText(text, 1)
                                // The bucket is poured out once its contents are in the field.
                                // After the paste, not before: if the commit fails the text is
                                // still in the bucket to try again, whereas emptying first would
                                // lose it with nothing to show for it.
                                prefs.dictate.maClipCaptured.set(
                                    MaClipCapture.serialize(
                                        MaClipCapture.pour(capturedSlots, button.slot),
                                    ),
                                )
                            }
                        }
                    },
                    // Long press opens the clipboard history, which is
                    // what every other key here does on a long press. The exception is deliberate:
                    // the key shows a number and nothing else, so the only way to find out what is
                    // on C4 before pasting it is to look, and the place to look is one gesture away
                    // from the key itself. Folding is still reachable from any other key on the row.
                    onLongClick = {
                        keyboardManager.activeState.imeUiMode = ImeUiMode.CLIPBOARD
                    },
                ) { fg ->
                  // The tick is gone, and the one-minute clock with it. The ring above says the
                  // same thing without a deadline.
                    Text(
                        text = "C${button.slot}",
                        // Three states, read at a glance without pressing anything: dim means the
                        // bucket is empty and normal means it is holding something. Two states, and
                        // that is all there is to say.
                        //
                        // There was a third: red, for every bucket full. Full is not a fault — it is
                        // the ordinary end of filling them — and red on a keyboard reads as
                        // something being wrong, so it sent him looking for a problem that did not
                        // exist every time he used the feature as intended. The bin key empties them
                        // and they fill again.
                        color = if (text == null) fg.copy(alpha = 0.4f) else fg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            is MaRows.Button.Builtin -> when (button.key) {
                MaFeatureKey.ALL_PASTE, MaFeatureKey.SELECT_ALL, MaFeatureKey.BACKSPACE,
                MaFeatureKey.ALL_CLEAR, MaFeatureKey.SPACE,
                // Undo and redo join them rather than being written again here. Both send
                // KeyCode.UNDO and KeyCode.REDO through the keyboard manager, which is where the
                // bucket rule lives, so this key and every other way of firing undo are one press.
                MaFeatureKey.UNDO, MaFeatureKey.REDO -> {
                    // The five borrowed from the copy row, drawn by that row's own code rather than
                    // rebuilt here, so a fix to AP or to backspace lands in both places at once.
                    // Backspace and space are the two keys from the keyboard proper that nothing
                    // else stands in for: with zone two closed there is no other way to delete a
                    // character, and no other way to put a space between two dictated sentences.
                    //
                    // None of these fold the row on a long press, and must not. Backspace holds to
                    // repeat and swipes to select, which is the behaviour it has everywhere else in
                    // this app, and a key that repeats cannot also mean something else when held.
                    LegacyActionKey(
                        action = when (button.key) {
                            MaFeatureKey.ALL_PASTE -> LegacyEditAction.ALL_PASTE
                            MaFeatureKey.SELECT_ALL -> LegacyEditAction.SELECT_ALL
                            MaFeatureKey.ALL_CLEAR -> LegacyEditAction.ALL_CLEAR
                            MaFeatureKey.SPACE -> LegacyEditAction.SPACE
                            MaFeatureKey.UNDO -> LegacyEditAction.UNDO
                            MaFeatureKey.REDO -> LegacyEditAction.REDO
                            else -> LegacyEditAction.BACKSPACE
                        },
                        modifier = keyMod,
                        keyboardManager = keyboardManager,
                        hasSelection = hasSelection,
                    )
                }

                MaFeatureKey.MIC -> {
                    // The way into the transcribe view, not a record button.
                    //
                    // It was the recording control because the transcribe view had been deleted and
                    // this key was the only thing left in that position. The view is back, and
                    // recording already has two ways in that nothing can take away: volume up, and
                    // the mic on the recording bar itself. A third one on an editable row bought
                    // nothing and cost the only route to the view.
                    //
                    // Red while recording is gone with it. This key no longer starts or stops
                    // anything, so a colour saying "recording" would be describing something the
                    // key is not doing.
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.Default.Mic,
                        contentDescription = stringRes(R.string.ma__feature_transcribe_view),
                        modifier = keyMod,
                    ) {
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TRANSCRIBE
                    }
                }

                MaFeatureKey.CLIP_CLEAR -> {
                    // Empties C1 to C10 so they fill again from the next copy.
                    //
                    // The slots stopped moving, so a full row is a finished row: capturing stops and
                    // nothing changes until this is pressed. Without it the feature would work once
                    // per install.
                    //
                    // The clipboard history is deliberately left alone. This key resets the ten
                    // keys, and wiping somebody's whole clipboard as a side effect of tidying a row
                    // is not recoverable — the history is still there behind a long press on any C
                    // key, which is where anything wanted back can be found.
                    val filled = MaClipCapture.filledCount(capturedSlots, visibleClipSlots)
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.Default.Delete,
                        contentDescription = stringRes(
                            if (filled == 0) R.string.ma__clip_clear_empty else R.string.ma__clip_clear,
                        ),
                        modifier = keyMod,
                        // Dim when there is nothing to clear, ordinary otherwise. It used to turn
                        // red alongside the buckets, on the reasoning that the row should show both
                        // the problem and its answer — but full is not a problem, so there was
                        // nothing to answer.
                        tint = if (filled == 0) MaDimmed else null,
                        ) {
                        // Recorded before it is thrown away, so undo brings all ten back. The bin
                        // is the one press here that can lose a morning's collecting, and it is one
                        // key away from the buckets themselves.
                        MaBucketUndo.push(capturedSlots)
                        scope.launch { prefs.dictate.maClipCaptured.set("") }
                    }
                }

                MaFeatureKey.SHIFT -> {
                    // THE LETTER KEYBOARD'S SHIFT, NOT A COPY OF IT.
                    //
                    // It used to write `inputShiftState` directly, cycling off → once → locked on
                    // every tap. That produced capitals, and it was still a second shift: caps lock
                    // needed three taps instead of a double tap, an auto-shift at a sentence start
                    // was cleared by touching it, and nothing about a press reached the editor's own
                    // shift handling.
                    //
                    // **Two implementations of one key is one too many**, and this is the one he
                    // photographed. It sends `KeyCode.SHIFT` down and up through the input event
                    // dispatcher now, which is exactly what the letter shift key does — so
                    // `handleShiftDown`, `handleShiftUp`, the double-tap lock, the Gboard-style
                    // recapitalisation of a selection and the automatic re-evaluation after a
                    // sentence all happen here because they are not written here at all.
                    //
                    // Down AND up, in that order, because the lock is decided in `handleShiftUp`
                    // from whether the sequence was uninterrupted. A tap that only sent one half
                    // would arm shift and never be able to lock it.
                    val shiftState = keyboardManager.activeState.inputShiftState
                    ThemedIconKey(
                        code = KeyCode.SHIFT,
                        icon = if (shiftState == InputShiftState.CAPS_LOCK) {
                            Icons.Default.KeyboardCapslock
                        } else {
                            Icons.Default.KeyboardArrowUp
                        },
                        contentDescription = "Shift",
                        modifier = keyMod,
                        // Three states, three appearances, the same three the letter key shows:
                        // off is plain, armed-for-one-letter is lit, locked is lit and wears the
                        // capslock glyph. An armed shift that looked like an off shift is how a
                        // sentence comes out wrong and nobody knows why.
                        tint = if (shiftState != InputShiftState.UNSHIFTED) MaSand else null,
                    ) {
                        keyboardManager.inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                        keyboardManager.inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                    }
                }

                // Drawn by its module. The row now says which key goes here and nothing about
                // what the key is — which is the whole point of the move.
                MaFeatureKey.PIN -> MaPinKey(modifier = keyMod, litColor = MaSand)

                // Both drawn by their modules. The row names the key and says nothing about what
                // the key is, which is the whole point of moving them out.
                // The four clipboard keys, now ordinary feature keys.
                //
                // Each goes through tapKey, the very path the Smartbar action used, so a paste from
                // this row is the same paste — the key moved, its behaviour did not.
                MaFeatureKey.PASTE -> ThemedIconKey(
                    code = KeyCode.CLIPBOARD_PASTE,
                    icon = Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    modifier = keyMod,
                    tint = null,
                ) { keyboardManager.tapKey(KeyCode.CLIPBOARD_PASTE) }

                MaFeatureKey.CUT -> ThemedIconKey(
                    code = KeyCode.CLIPBOARD_CUT,
                    icon = Icons.Default.ContentCut,
                    contentDescription = "Cut",
                    modifier = keyMod,
                    tint = null,
                ) { keyboardManager.tapKey(KeyCode.CLIPBOARD_CUT) }

                MaFeatureKey.COPY -> ThemedIconKey(
                    code = KeyCode.CLIPBOARD_COPY,
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = keyMod,
                    tint = null,
                ) { keyboardManager.tapKey(KeyCode.CLIPBOARD_COPY) }

                MaFeatureKey.CLIP_HISTORY -> ThemedKey(
                    code = KeyCode.NOOP,
                    modifier = keyMod,
                    onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.CLIPBOARD },
                ) { fg ->
                    // A CLIPBOARD WEARING THE HISTORY ARROW.
                    //
                    // It was `ContentPasteGo` — a clipboard with an arrow pointing RIGHT — while the
                    // transcription history key next to it wears the circular arrow round a clock.
                    // Two keys that open a history, two different pictures, and only one of them
                    // says "history" at all: an arrow to the right means GO, which is what that
                    // glyph is drawn for.
                    //
                    // **The subject changes, the mark does not.** A clipboard for what is being
                    // looked through, the counter-clockwise arrow for the fact that it is a past.
                    // See MANTRA_MANIFEST/modules/design-language.md — this is now the rule for
                    // every key in every app of mine that opens a history.
                    MaHistoryGlyph(base = Icons.Default.ContentPaste, tint = fg)
                }

                MaFeatureKey.DUMP -> ThemedIconKey(
                    code = KeyCode.NOOP,
                    // A stack of layers: what this reads is the layer under the picture, which is
                    // exactly what the tree is. Not a bug icon — nothing here is broken, and an
                    // instrument should not look like an alarm.
                    icon = Icons.Default.Layers,
                    contentDescription = "Copy the screen's accessibility tree",
                    modifier = keyMod,
                    tint = null,
                ) {
                    // Straight to the clipboard, because it is far too long to read on a phone and
                    // its whole purpose is to be pasted somewhere else. The same path the wand's
                    // copy button uses, so the two cannot produce different dumps.
                    if (DictateAccessibilityService.isRunning) {
                        val dump = DictateAccessibilityService.dumpScreen()
                        clipboardManager.addNewPlaintext(dump)
                        MaMessage.show("Screen copied \u2014 ${dump.length} characters")
                    } else {
                        MaMessage.show("Turn on the accessibility service to read the screen")
                    }
                }

                MaFeatureKey.SUBTITLE -> MaSubtitleToggleKey(modifier = keyMod, litColor = MaSand)

                MaFeatureKey.READER -> MaReaderKey(
                    modifier = keyMod,
                    context = context,
                    litColor = MaSand,
                    // Long press opens the dashboard rather than the settings app.
                    //
                    // It used to leave the keyboard for the Reader screen, which meant walking away
                    // from the thing being adjusted. The three dials he reaches for while listening
                    // are one press away now and the reading keeps going underneath. Everything else
                    // about the reader still lives in settings, reachable from there.
                    onOpenSettings = { maDashboardOpen = !maDashboardOpen },
                    onMessage = { message ->
                        MaMessage.show(message)
                    },
                )

                MaFeatureKey.AUTO_BUCKET, MaFeatureKey.AUTO_BUCKET_DOWN -> {
                    // TWO KEYS, ONE BODY. The only difference is which end of the frame it starts
                    // at, so writing it twice would be two places for the skip rule, the messages
                    // and the undo arming to drift apart.
                    val downward = button.key == MaFeatureKey.AUTO_BUCKET_DOWN
                    // A: TAKE THE CODE BLOCK YOU ARE LOOKING AT.
                    //
                    // It used to walk the whole document by a counter — press once for the newest
                    // block, again for the one above it, and so on up the page, with the key's face
                    // showing how far up it had climbed. Marko took it out into real use and found
                    // the flaw himself: **collecting is not linear.** He scrolls up, takes a block,
                    // scrolls down, takes another. A counter answers "how many have I taken", and
                    // the question he is actually asking is "this one, the one on my screen".
                    //
                    // So the frame is the whole world now. What is not on screen does not exist to
                    // this key, and scrolling is how he chooses. The ladder is gone, and the face
                    // says A rather than A1, because there is no longer a position to report.
                    //
                    // With several blocks in view it takes the LOWEST first and works upward. That
                    // is the same rule the rest of the app uses for a chat — the lowest is the
                    // newest — and it means two blocks in the frame need no question asked: press
                    // twice and you have both, bottom then top.
                    //
                    // A block already in a bucket is skipped rather than taken again, and the key
                    // moves to the next one up. Only when every block in view is already held does
                    // it say so, which is the message he asked for after copying the same thing
                    // twice on a screen he had scrolled back to.
                    ThemedTextKey(
                        label = if (downward) "A\u2193" else "A\u2191",
                        modifier = keyMod,
                        tint = null,
                        onLongClick = {
                            // What is on this screen, before touching anything. The seed of the
                            // list he asked for: this counts, it does not yet name them.
                            scope.launch {
                                if (!DictateAccessibilityService.isRunning) {
                                    maOpenAccessibilitySettings(context)
                                    return@launch
                                }
                                val here = DictateAccessibilityService
                                    .countScreenTargetsInView(listOf("copy code"))
                                val free = visibleClipSlots.count {
                                    MaClipCapture.at(capturedSlots, it) == null
                                }
                                MaMessage.show(if (here == 0) {
                                        "No code block on this screen"
                                    } else {
                                        "$here code block(s) on screen, $free bucket(s) free"
                                    })
                            }
                        },
                    ) {
                        if (!DictateAccessibilityService.isRunning) {
                            maOpenAccessibilitySettings(context)
                        } else {
                            scope.launch {
                                val inView = DictateAccessibilityService
                                    .countScreenTargetsInView(listOf("copy code"))
                                if (inView == 0) {
                                    MaMessage.show("No code block on this screen")
                                    return@launch
                                }
                                var landed = false
                                var alreadyHeld = 0
                                // Lowest first, then upward. Every rank in the frame is tried, so a
                                // block already in a bucket costs one press and not a dead key.
                                // Rank 0 is the LOWEST block in the frame, so counting up walks
                                // upward and counting down from the top walks downward. One range,
                                // reversed — not a second loop with the comparison flipped, which
                                // is where an off-by-one lives.
                                val order = if (downward) (inView - 1) downTo 0 else 0 until inView
                                for (rank in order) {
                                    val before = MaClipCapture.parse(
                                        prefs.dictate.maClipCaptured.get(),
                                    )
                                    // Armed before the press, as ever, so undo has the state from
                                    // before this whole attempt rather than from between two of
                                    // its presses.
                                    MaBucketUndo.armAuto(before)
                                    val hit = DictateAccessibilityService
                                        .pressScreenTargetInView(listOf("copy code"), rank)
                                    if (hit == null) {
                                        MaBucketUndo.disarm()
                                        break
                                    }
                                    // The copy travels through the system clipboard and the capture
                                    // rides on that, so the answer is not readable in the same
                                    // breath. Measured rather than assumed: the same 150ms the
                                    // snippet key waits, with room for a slower app.
                                    delay(350L)
                                    val after = MaClipCapture.parse(
                                        prefs.dictate.maClipCaptured.get(),
                                    )
                                    if (after != before) {
                                        landed = true
                                        val slot = after.indices.firstOrNull {
                                            after[it] != null && before.getOrNull(it) == null
                                        }
                                        MaMessage.show(if (slot != null) {
                                                "Code block \u2192 C${slot + 1}"
                                            } else {
                                                "Copied"
                                            })
                                        break
                                    }
                                    // Nothing changed. Either this block is already in a bucket, or
                                    // every bucket is full — two different messages, and the
                                    // difference is worth getting right because one of them is a
                                    // thing he must do something about.
                                    alreadyHeld++
                                }
                                if (!landed) {
                                    val full = visibleClipSlots.isNotEmpty() &&
                                        visibleClipSlots.all {
                                            MaClipCapture.at(capturedSlots, it) != null
                                        }
                                    val held = clipboardManager.primaryClip?.text?.toString()
                                        ?.let { MaClipCapture.slotFor(capturedSlots, it) }
                                    MaMessage.show(when {
                                            full -> "Every bucket is full \u2014 empty them with the bin"
                                            held != null -> "Already copied \u2014 this block is in C$held"
                                            alreadyHeld > 0 ->
                                                "Every code block on this screen is already in a bucket"
                                            else -> "Nothing was copied"
                                        })
                                }
                            }
                        }
                    }
                }

                MaFeatureKey.CHANGE_CASE -> MaCaseKey(modifier = keyMod, context = context)

                // Shift stays here, at the call site, not inside the key.
                //
                // Reading the keyboard's shift state is this row's business — it is the row that
                // sits under a keyboard. The key itself only needs to know which way to walk, so it
                // is told, and the same module works on a surface that has no shift at all.
                MaFeatureKey.NEXT_FIELD -> MaNextFieldKey(
                    modifier = keyMod,
                    context = context,
                    backwards = {
                        val shift = keyboardManager.activeState.inputShiftState
                        val backwards = shift != InputShiftState.UNSHIFTED
                        if (shift == InputShiftState.SHIFTED_MANUAL) {
                            keyboardManager.activeState.inputShiftState = InputShiftState.UNSHIFTED
                        }
                        backwards
                    },
                )

                MaFeatureKey.APP_SWITCH -> {
                    // Alt+Tab. Dim when nothing has been seen to switch to yet, which is the state
                    // right after the phone starts or the accessibility service is switched off —
                    // both cases where the key would do nothing and should say so first.
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = stringRes(R.string.ma__app_switch),
                        modifier = keyMod,
                        // No longer dimmed on what this app has observed. Recents knows the task
                        // order whether or not we saw it, so a dim key would understate what the key
                        // can actually do.
                        tint = null,
                        ) {
                        if (!DictateAccessibilityService.isRunning) {
                            // The whole reason this key fires blanks, and the user cannot guess it.
                            // Both these keys read the screen through the accessibility service,
                            // which is switched on by hand in the system settings and is off until
                            // somebody does. Opening that screen is more use than a message saying
                            // something did not work.
                            maOpenAccessibilitySettings(context)
                        } else if (!MaAppSwitcher.switchViaRecents(scope) &&
                            !MaAppSwitcher.switchToPrevious(context)
                        ) {
                            MaMessage.show(context.getString(R.string.ma__app_switch_none))
                        }
                    }
                }

                MaFeatureKey.HISTORY -> {
                    // Everything dictated, ready to put back. The recovery route when a sentence
                    // went into the wrong app or was replaced by the next one.
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.Default.History,
                        contentDescription = stringRes(R.string.ma__feature_history),
                        modifier = keyMod,
                        ) {
                        // Full screen (Marko, 13.11.2026): History is its own app inside the app. Insert goes
                        // back into this field.
                        dev.patrickgold.florisboard.dictate.MaHistoryActivity.open(
                            context, editorInstance.activeInfo.packageName, editorInstance.activeInfo.base.fieldId,
                        )
                    }
                }

                MaFeatureKey.LANGUAGE -> {
                    // The language it will transcribe in, on its face, and one tap changes it.
                    //
                    // The same MaLanguage.toggle the recording bar uses, so the two cannot disagree.
                    // It writes activeInputLanguage, which is what the transcription request reads,
                    // what the keyboard's own suggestions follow, and what decides whether a clip
                    // may take the fast path — one tap, all three.
                    // Derived from the observed preference, not from MaLanguage.badge() alone.
                    // badge() reads the store directly, which is not Compose state, so a key drawn
                    // from it would keep its old face after a tap until something else redrew the
                    // row — a toggle that appears not to have worked.
                    ThemedTextKey(
                        // EN, not ENG. Two letters, because this key is a circle.
                        //
                        // Every other key in this row holds one glyph; three letters do not fit
                        // inside a round face and the last one was being cut off at the edge. HR is
                        // already two, so the pair is even as well — and a badge whose width changes
                        // with the language would shift the keys beside it every time he switched.
                        //
                        // The history panel keeps ENG, where the row is wide and the extra letter
                        // costs nothing.
                        label = remember(activeLangCode, langMode) {
                            if (MaLanguage.active() == MaLanguage.EN) "EN" else "HR"
                        },
                        // ENG and HR is a switcher: it changes which language is heard, and types
                        // nothing.
                        switcher = true,
                        modifier = keyMod,
                        tint = null,
                    ) {
                        MaLanguage.cycleMode(context)
                    }
                }

                MaFeatureKey.SCROLL -> {
                    // S, and the number of pages beside it, so the key says what it will do before
                    // it is pressed rather than after. An up arrow appears when the count is
                    // negative: the sign is the direction, and a minus sign alone is easy to misread
                    // at this size.
                    val label = when {
                        scrollPages < 0 -> "S\u2191${-scrollPages}"
                        scrollPages > 1 -> "S\u2193$scrollPages"
                        else -> "S"
                    }
                    ThemedTextKey(
                        label = label,
                        modifier = keyMod,
                        tint = null,
                        onLongClick = { scrollMenu = true },
                    ) {
                        if (!DictateAccessibilityService.isRunning) {
                            maOpenAccessibilitySettings(context)
                        } else {
                            scope.launch {
                                val service = DictateAccessibilityService.gestureService()
                                if (service == null || !MaScreenTargets.scrollBy(service, scrollPages)) {
                                    MaMessage.show(context.getString(R.string.ma__scroll_none))
                                }
                            }
                        }
                    }
                }

                MaFeatureKey.SWITCHBOARD -> {
                    // A switch, because that is what the screen is: a page of them.
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.Default.ToggleOn,
                        contentDescription = stringRes(R.string.ma__feature_switchboard),
                        modifier = keyMod,
                    ) {
                        FlorisImeService.launchSettings("settings/dictate/switchboard")
                    }
                }

                MaFeatureKey.SPACER -> {
                    // Room, and nothing else. No background, no ripple, no click target — a spacer
                    // that could be pressed would be a key that appears broken, and one that drew a
                    // surface would be a key that appears blank.
                    // Weight, not a fixed width, because every key in this row is weighted — the
                    // row divides whatever width the phone has. A spacer measured in dp would be
                    // one size on his phone and another on a wider one, and would not stay in
                    // proportion when he adds a key beside it.
                    //
                    // Ten tenths is exactly one key, so the number means what it says.
                    Spacer(
                        modifier = Modifier
                            .weight(spacerTenths / 10f)
                            .fillMaxHeight(),
                    )
                }

                MaFeatureKey.SETTINGS -> {
                    // Settings, reopened where they were left rather than at the top.
                    //
                    // Reaching a setting is the expensive part on a phone driven by voice; changing
                    // it once you are there is not. MaSettingsResume remembers the screen and how
                    // far down it he was, and hands back a deep link to both.
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.Default.Settings,
                        contentDescription = stringRes(R.string.ma__feature_settings),
                        modifier = keyMod,
                        ) {
                        MaSettingsResume.open(context)
                    }
                }

                MaFeatureKey.ZONE_1 -> {
                    // 1, the number row. Digits, or whichever set the row is showing.
                    ThemedKey(
                        code = KeyCode.NOOP,
                        // A real icon, at the size and weight of every other icon in the row.
                        //
                        // These were a hand-drawn keyboard outline with a letter inside, and he was
                        // right that they looked like nothing else on the board — the neighbours are
                        // drawn glyphs from one set, and a shape drawn by hand beside them reads as
                        // an amateur patch however carefully it is proportioned. Colour still says
                        // whether the zone is open, the way it does everywhere in this app.
                        modifier = keyMod,
                        // THE RING SAYS ON OR OFF; THE GLYPH NEVER CHANGES.
                        //
                        // It was the other way round — a monochrome ring meaning "this is a
                        // switcher" and a green GLYPH meaning "the zone is open". He photographed
                        // the three of them and said they were not following the convention, and he
                        // is right: everywhere else in this app the ring carries the state and the
                        // picture holds still. The buckets, the volume key and the record key all
                        // learned that; these three were written before it and never revisited.
                        //
                        // Green ring on, cream ring off. **The ring is always there**, so nothing
                        // appears or disappears and the row does not reflow — the same reason the
                        // reading window keeps its box when there is nothing in it.
                        ring = if (zone1) onGreen else MaSwitcherRingOff,
                        onClick = { scope.launch { prefs.dictate.maExtraRow.set(!zone1) } },
                    ) { fg ->
                        Icon(
                            imageVector = Icons.Default.Numbers,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                MaFeatureKey.ZONE_2 -> {
                    // 2, the keyboard itself, all of it at once. This is the one that gives back real estate,
                    // and on a keyboard driven by voice it is off more often than it is on.
                    ThemedKey(
                        code = KeyCode.NOOP,
                        // A real icon, at the size and weight of every other icon in the row.
                        //
                        // These were a hand-drawn keyboard outline with a letter inside, and he was
                        // right that they looked like nothing else on the board — the neighbours are
                        // drawn glyphs from one set, and a shape drawn by hand beside them reads as
                        // an amateur patch however carefully it is proportioned. Colour still says
                        // whether the zone is open, the way it does everywhere in this app.
                        modifier = keyMod,
                        // THE RING SAYS ON OR OFF; THE GLYPH NEVER CHANGES.
                        //
                        // It was the other way round — a monochrome ring meaning "this is a
                        // switcher" and a green GLYPH meaning "the zone is open". He photographed
                        // the three of them and said they were not following the convention, and he
                        // is right: everywhere else in this app the ring carries the state and the
                        // picture holds still. The buckets, the volume key and the record key all
                        // learned that; these three were written before it and never revisited.
                        //
                        // Green ring on, cream ring off. **The ring is always there**, so nothing
                        // appears or disappears and the row does not reflow — the same reason the
                        // reading window keeps its box when there is nothing in it.
                        ring = if (zone2) onGreen else MaSwitcherRingOff,
                        onClick = { scope.launch { prefs.dictate.maZoneKeyboard.set(!zone2) } },
                    ) { fg ->
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                MaFeatureKey.ZONE_3 -> {
                    // 3, the copy and paste row along the top. Paste, copy, history and the rest of it.
                    ThemedKey(
                        code = KeyCode.NOOP,
                        // A real icon, at the size and weight of every other icon in the row.
                        //
                        // These were a hand-drawn keyboard outline with a letter inside, and he was
                        // right that they looked like nothing else on the board — the neighbours are
                        // drawn glyphs from one set, and a shape drawn by hand beside them reads as
                        // an amateur patch however carefully it is proportioned. Colour still says
                        // whether the zone is open, the way it does everywhere in this app.
                        modifier = keyMod,
                        // THE RING SAYS ON OR OFF; THE GLYPH NEVER CHANGES.
                        //
                        // It was the other way round — a monochrome ring meaning "this is a
                        // switcher" and a green GLYPH meaning "the zone is open". He photographed
                        // the three of them and said they were not following the convention, and he
                        // is right: everywhere else in this app the ring carries the state and the
                        // picture holds still. The buckets, the volume key and the record key all
                        // learned that; these three were written before it and never revisited.
                        //
                        // Green ring on, cream ring off. **The ring is always there**, so nothing
                        // appears or disappears and the row does not reflow — the same reason the
                        // reading window keeps its box when there is nothing in it.
                        ring = if (zone3) onGreen else MaSwitcherRingOff,
                        onClick = { scope.launch { prefs.dictate.maEditRow.set(!zone3) } },
                    ) { fg ->
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                MaFeatureKey.SEND -> {
                    // Lit only when there is a Send button on the screen.
                    //
                    // He asked for this and the reason is the one that keeps coming back: a key that
                    // looks alive and does nothing is indistinguishable from a broken key. Dim, it
                    // says "not here" before the press rather than after it.
                    //
                    // Polled rather than watched, because the accessibility service reports window
                    // changes and not "the Send button appeared inside a window that was already
                    // there" — which is what happens when he finishes dictating and the app enables
                    // its own button. Two seconds costs nothing and is quick enough that the key is
                    // right by the time his thumb arrives.
                    //
                    // The loop lives and dies with the key: no key on the row, nobody's view tree
                    // being walked.
                    var sendHere by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            sendHere = runCatching { MaMagicTargets.sendVisible() }.getOrDefault(false)
                            delay(2000L)
                        }
                    }
                    // Send, and the same send volume-down makes.
                    //
                    // `MaMagicTargets.pressSend()` rather than a press of anything named "Send":
                    // that reads his configured term and presses whatever button carries it in the
                    // app he is in. So the key, the volume key and the wand are one behaviour with
                    // three ways in, and changing the term changes all three at once. **Two ways of
                    // doing one thing is one way too many when one of them can drift.**
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        // An upward arrow, which is what he asked for and what the chat apps use:
                        // the message goes up and away. A paper plane is the other convention and it
                        // is not the one on his screen.
                        icon = Icons.Default.ArrowUpward,
                        // Not null: ThemedIconKey takes a non-null String, because every key on this
                        // row has to be announceable. He uses a screen reader some of the time and a
                        // key that reads as nothing is a key he cannot find.
                        contentDescription = "Send",
                        // Dim rather than hidden. A key that disappears takes its neighbours' places
                        // with it, and this row is pressed from memory — the positions must not move
                        // because of something happening in another app.
                        tint = if (sendHere) null else MaSand.copy(alpha = 0.3f),
                        modifier = keyMod,
                    ) {
                        if (!DictateAccessibilityService.isRunning) {
                            maOpenAccessibilitySettings(context)
                        } else if (MaMagicTargets.pressSend() == null) {
                            // Nothing was found, and silence here is indistinguishable from a key
                            // that does nothing — the failure this whole app keeps meeting.
                            MaMessage.show("No Send button found on this screen")
                        }
                    }
                }

                MaFeatureKey.RECORD_HR, MaFeatureKey.RECORD_EN, MaFeatureKey.RECORD_EN_HR,
                MaFeatureKey.RECORD_EN_NET, MaFeatureKey.RECORD_HR_NET,
                MaFeatureKey.RECORD_EN_LOCAL, MaFeatureKey.RECORD_HR_LOCAL,
                -> {
                    // RECORD IN A LANGUAGE. The key is the answer to "which language is this".
                    //
                    // Written once for the two: they differ by one string, and two copies would be
                    // two places for the order of "set the language, then record" to drift — which
                    // is the one thing here that must not.
                    val language = if (button.key == MaFeatureKey.RECORD_HR || button.key == MaFeatureKey.RECORD_HR_NET ||
                        button.key == MaFeatureKey.RECORD_HR_LOCAL
                    ) MaLanguage.HR else MaLanguage.EN
                    // Talk to AssemblyAI / Parakeet (8.11.2026): the engine for this one recording, and
                    // the dot's colour that says which. Null = the Transcription model setting.
                    // Marko, 9.11.2026: "the Croatian button does not change". The H/E keys that were hard-bound to
                    // AssemblyAI / Parakeet ignored the engine chosen in Settings and in the long-press panel, so a
                    // row holding such an H showed and used one provider forever. Every H/E key now follows the
                    // chosen engine of its language (the same EngineStore slot); only the dot colour still tells the old kinds apart.
                    val engine: String? = null
                    val dot = when (button.key) {
                        MaFeatureKey.RECORD_EN_NET, MaFeatureKey.RECORD_HR_NET -> MaEngineNetRed
                        MaFeatureKey.RECORD_EN_LOCAL, MaFeatureKey.RECORD_HR_LOCAL -> MaEngineLocalAmber
                        else -> MaRecordRed
                    }
                    // E>H records English like E, and the transcript is then written in Croatian.
                    val translate = button.key == MaFeatureKey.RECORD_EN_HR
                    val label = when {
                        translate -> "E>H"
                        language == MaLanguage.HR -> "H"
                        else -> "E"
                    }
                    val rec = DictateController.state.collectAsState().value as?
                        DictateController.UiState.Recording
                    val recording = rec != null
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod.androidx_onPlaced { engineKeyRect[0] = it },
                        // ONLY THE KEY THAT STARTED IT RINGS.
                        //
                        // Both rang, on the reasoning that one recording is running and lighting one
                        // key would suggest the other could start a second. **That argument protects
                        // a misunderstanding nobody has and destroys the answer he actually needs.**
                        // He photographed it: press E, both light, and the row no longer tells him
                        // which language is being captured.
                        //
                        // Which language is recording is the ONE thing these two keys exist to make
                        // visible. If both ring, they are a record key drawn twice.
                        //
                        // The other key going dark is the true statement anyway: while a recording is
                        // running it cannot start one, and pressing it stops this one. A ring that
                        // says "this is yours" is more use than a ring that says "something is
                        // happening", which the timer above already says.
                        //
                        // `MaLanguage.active()` rather than `inFlightLanguage`: the latter is written
                        // when a REQUEST starts, and this is a recording — it has not been sent yet.
                        // Reading the wrong one would leave both keys dark until the upload began.
                        // E and E>H both record English, so the one that started it is the one that
                        // rings: the controller knows whether this recording translates.
                        // RED OUTLINE AT REST, GREEN WHILE IT RECORDS (Marko, 2.11.2026): every key
                        // that records his voice wears the record red, so the row says "press this and
                        // you are recorded" before it is pressed. See MaRecordOutline.
                        ring = if (recording && MaLanguage.active() == language &&
                            DictateController.isTranslating() == translate &&
                            DictateController.recordingEngine() == engine &&
                            !DictateController.isRepromptRecording()
                        ) MaRecordOutlineLive else MaRecordOutline,
                        onLongClick = if (!translate) {
                            { engineMenu = if (language == MaLanguage.HR) "hr" else "en" }
                        } else null,
                        onClick = {
                            // THE LANGUAGE IS SET FIRST, AND SET SYNCHRONOUSLY.
                            //
                            // `MaLanguage.set` writes with runBlocking precisely so a press followed
                            // immediately by a recording cannot race it. Started first, the audio
                            // would exist before the language did and `transcribe` would capture
                            // whatever the previous setting was — which is the bug he has been
                            // living with, arriving by a different road.
                            if (!recording) MaLanguage.set(context, language)
                            DictateController.onMicClick(context, translate = translate, engine = engine)
                            // Talk-to keys are silent (Marko, 8.11.2026: "Please remove this pop-up
                            // message when I'm recording ... with Parakeet or with AssemblyAI ...
                            // Silent, please."). The engine name on the recording strip says it.
                            if (engine == null) MaMessage.show(
                                if (recording) {
                                    if (translate) "Sending English, writing Croatian"
                                    else "Sending " + if (language == MaLanguage.HR) "Croatian" else "English"
                                } else {
                                    if (translate) "Recording English, writing Croatian"
                                    else "Recording " + (if (language == MaLanguage.HR) "Croatian" else "English")
                                },
                            )
                        },
                    ) { fg ->
                        if (!translate) {
                            // The key says which engine it uses (Marko, 9.11.2026): letter and dot left, name big.
                            val engLang = if (language == MaLanguage.HR) "hr" else "en"
                            val liveEngines by dev.patrickgold.florisboard.dictate.engines.EngineLive.state.collectAsState()
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                dev.patrickgold.florisboard.dictate.engines.EngineLive.load(context)
                            }
                            val chosen = liveEngines.selected[engLang]
                                ?: dev.patrickgold.florisboard.dictate.engines.EngineStore.selected(context, engLang)
                            val answering = liveEngines.active[engLang]
                            val keyName = when (engine) {
                                MaProviders.ENGINE_ASSEMBLYAI -> "AssemblyAI"
                                MaProviders.ENGINE_LOCAL -> "Parakeet"
                                else -> (answering ?: chosen).let {
                                    // Marko, 10.11.2026: "Groq 2/3" = active key number over the number of keys, when a provider has several.
                                    val ringId = dev.patrickgold.florisboard.dictate.engines.EngineKeys.keyId(it)
                                    val total = androidx.compose.runtime.remember(ringId, context) {
                                        runCatching { dev.patrickgold.florisboard.dictate.engines.EngineKeys.lines(context, ringId).size }.getOrDefault(0)
                                    }
                                    val act = (dev.patrickgold.florisboard.dictate.engines.KeyRing.active[ringId] ?: (dev.patrickgold.florisboard.dictate.engines.KeyRing.cursor(context, ringId) + 1)).coerceAtMost(maxOf(total, 1))
                                    dev.patrickgold.florisboard.dictate.engines.EngineCatalog.shortName(it) +
                                        (if (total > 1) " $act/$total" else "")
                                }
                            }
                            val subId = when (engine) {
                                MaProviders.ENGINE_ASSEMBLYAI -> "assemblyai"
                                MaProviders.ENGINE_LOCAL -> "parakeet"
                                else -> answering ?: chosen
                            }
                            MaEngineKeyContent(label, dot, keyName, engine == null && answering != null && answering != chosen, fg,
                                dev.patrickgold.florisboard.dictate.ui.engineMinutesLabel(context, subId))
                        } else
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // E|H wears no red dot (Marko, 2.11.2026); the red outline says it records.
                            if (!translate) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(dot),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (translate) {
                                // E|H (Marko, 2.11.2026): E on the left, a straight vertical line,
                                // H on the right. English becomes Croatian, read left to right; the
                                // line divides like a fraction bar and is cleaner than the ">".
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "E",
                                        color = fg,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .width(1.5.dp)
                                            .height(16.dp)
                                            .background(fg),
                                    )
                                    Text(
                                        text = "H",
                                        color = fg,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Text(
                                    text = label,
                                    color = fg,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                MaFeatureKey.REPROMPT -> {
                    // REPROMPT (MaReprompt). 5.11.2026: "when I click on re-prompt … it's automatically
                    // running audio recorder … always in English". A press records the instruction in
                    // English and a second press sends it; the transcript is applied to the field (text:
                    // reshaped; empty: written). It records, so it wears the record outline: dark red,
                    // light red while it is the one recording. A long press opens the typing window.
                    val rec = DictateController.state.collectAsState().value as?
                        DictateController.UiState.Recording
                    val recordingThis = rec != null && DictateController.isRepromptRecording()
                    var reMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    val reEngine by prefs.dictate.maReEngine.collectAsState()
                    val reGroqModel by prefs.dictate.maReGroqModel.collectAsState()
                    Box(modifier = keyMod.androidx_onPlaced { reKeyRect[0] = it }) {
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = Modifier.fillMaxSize(),
                        ring = if (recordingThis) MaRecordOutlineLive else MaRecordOutline,
                        onClick = {
                            // RE Magic is a chat now (Marko, 13.11.2026): a tap opens the Messenger-style window over
                            // the app; its Accept writes into this field. The spoken-instruction RE is in the long press.
                            if (rec == null && dev.patrickgold.florisboard.dictate.MaReChat.chatOn(context)) {
                                dev.patrickgold.florisboard.dictate.MaReChatActivity.openFromKey(
                                    context, editorInstance.activeInfo.packageName, editorInstance.activeInfo.base.fieldId,
                                )
                            } else {
                            if (rec == null) {
                                MaLanguage.set(context, MaLanguage.EN)
                                // The field the message will be written into, for MaReSkill's comparison.
                                MaReprompt.noteField(editorInstance.activeInfo.packageName, editorInstance.activeInfo.base.fieldId)
                            }
                            // RE transcribes with AssemblyAI and nothing else (Marko, 9.11.2026): no Parakeet,
                            // no fallback (MaProviders.localFallbackProvider returns null for this engine).
                            DictateController.onMicClick(context, reprompt = true, engine = MaProviders.ENGINE_ASSEMBLYAI)
                            MaMessage.show(if (rec == null) "RE: say the instruction (AssemblyAI)" else "RE: sending")
                            }
                        },
                        // A long press opens the engine switch (Marko, 10.11.2026): Anthropic or Groq, the active one marked,
                        // a pick closes it. "Type the instruction" keeps the old typing window one tap away.
                        onLongClick = { reMenu = true },
                    ) { fg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "re", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(text = if (reEngine == "groq") "G" else "A", color = fg, fontSize = 10.sp, modifier = Modifier.offset(x = 1.dp, y = 4.dp))
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = MaReprompt.NAME,
                                tint = fg,
                                modifier = Modifier.size(11.dp).offset(y = (-5).dp),
                            )
                        }
                    }
                    // The app's own panel (Marko, 13.11.2026), grown up from the RE key like the H/E engine panel.
                    if (reMenu) MaKeyPanel(
                        title = "re", accent = MaRecordOutlineLive, anchor = reKeyRect[0], onDismiss = { reMenu = false },
                        items = listOf(
                            MaPanelItem("Anthropic", "RE engine", reEngine != "groq") { scope.launch { prefs.dictate.maReEngine.set("anthropic") } },
                            MaPanelItem("Groq \u00b7 gpt-oss-20b", "RE engine", reEngine == "groq" && reGroqModel != "openai/gpt-oss-120b") {
                                scope.launch { prefs.dictate.maReEngine.set("groq"); prefs.dictate.maReGroqModel.set("openai/gpt-oss-20b") }
                            },
                            MaPanelItem("Groq \u00b7 gpt-oss-120b", "RE engine", reEngine == "groq" && reGroqModel == "openai/gpt-oss-120b") {
                                scope.launch { prefs.dictate.maReEngine.set("groq"); prefs.dictate.maReGroqModel.set("openai/gpt-oss-120b") }
                            },
                            MaPanelItem("Tap opens the chat", if (dev.patrickgold.florisboard.dictate.MaReChat.chatOn(context)) "on" else "off", dev.patrickgold.florisboard.dictate.MaReChat.chatOn(context)) {
                                dev.patrickgold.florisboard.dictate.MaReChat.setChatOn(context, !dev.patrickgold.florisboard.dictate.MaReChat.chatOn(context))
                            },
                            MaPanelItem("Speak an instruction", "no chat") {
                                MaLanguage.set(context, MaLanguage.EN)
                                MaReprompt.noteField(editorInstance.activeInfo.packageName, editorInstance.activeInfo.base.fieldId)
                                DictateController.onMicClick(context, reprompt = true, engine = MaProviders.ENGINE_ASSEMBLYAI)
                                MaMessage.show("RE: say the instruction (AssemblyAI)")
                            },
                            MaPanelItem("Type the instruction", "no chat") {
                                val info = editorInstance.activeInfo
                                val pkg = info.packageName
                                val empty = editorInstance.activeContent.text.isBlank() && editorInstance.activeContent.selectedText.isBlank()
                                if (pkg == null) MaMessage.show("Reprompt: no field to write into")
                                else MaReprompt.open(context, pkg, info.base.fieldId, write = empty)
                            },
                        ),
                    )
                    }
                }
                MaFeatureKey.WHATSAPP -> {
                    // WA (MaWhatsApp, 7.11.2026): like RE, a press records the instruction in English and a
                    // second press sends it, here to Sonnet working WhatsApp. Live ring while recording or
                    // while Sonnet works; a long press stops the task. It never sends.
                    val rec = DictateController.state.collectAsState().value as?
                        DictateController.UiState.Recording
                    val recordingThis = rec != null && DictateController.isWhatsAppRecording()
                    val working = MaWhatsApp.busy.value
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        ring = if (recordingThis || working) MaRecordOutlineLive else MaRecordOutline,
                        onClick = {
                            if (rec == null) MaLanguage.set(context, MaLanguage.EN)
                            DictateController.onMicClick(context, whatsapp = true)
                            MaMessage.show(if (rec == null) "WhatsApp: say what to do" else "WhatsApp: sending to Sonnet")
                        },
                        onLongClick = { MaWhatsApp.stop() },
                    ) { fg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "WA", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = MaWhatsApp.NAME,
                                tint = fg,
                                modifier = Modifier.size(11.dp).offset(y = (-5).dp),
                            )
                        }
                    }
                }

                MaFeatureKey.RECORD -> {
                    // Record, start and stop, the same call volume-up and the bar's mic make.
                    //
                    // The note on MIC says a third route to recording bought nothing and cost the
                    // only route to the transcribe view. That still holds and this does not break
                    // it: MIC keeps the view, and this is a route that takes nothing away. He asked
                    // for it, and a hand already on the row should not have to find a hardware key.
                    val rec = DictateController.state.collectAsState().value as?
                        DictateController.UiState.Recording
                    val recording = rec != null

                    // The level and the clock, from exactly the sources the recording bar reads, so
                    // the key and the bar can never disagree about what is happening.
                    val level by DictateController.audioLevel.collectAsState()
                    var elapsedMs by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(rec?.startedAtMs, rec?.accumulatedMs, rec?.paused) {
                        val r = rec
                        if (r == null) {
                            elapsedMs = 0L
                            return@LaunchedEffect
                        }
                        while (true) {
                            elapsedMs = if (r.paused) {
                                r.accumulatedMs
                            } else {
                                r.accumulatedMs + (android.os.SystemClock.elapsedRealtime() - r.startedAtMs)
                            }
                            delay(200L)
                        }
                    }
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        // The same green ring the buckets and the volume key wear: this thing is
                        // live. One ring, one green, one meaning across the app.
                        ring = if (recording) MaRecordOutlineLive else MaRecordOutline,
                        onClick = { DictateController.onMicClick(context) },
                    ) { _ ->
                        // A red dot, as he described it, and the same red as the lamp on the
                        // recording bar. It is what the key IS rather than what it is doing, so it
                        // is red whether or not anything is being recorded — a dot that only
                        // appeared while recording would leave an empty key the rest of the time.
                        //
                        // While recording it grows and takes the ring, which is the app's one way of
                        // saying a thing is live. The dot does not change colour, because the colour
                        // is its name.
                        // A meter on the left, the lamp in the middle, the clock on the right.
                        //
                        // Only while recording. Idle, the key is the red dot alone: a meter reading
                        // nothing and a clock reading 0:00 are two more things saying "not
                        // recording" that the dot already says, and they would take room from the
                        // one thing on this key that has to be found by touch.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            if (recording) {
                                // Vertical, filling from the bottom, which is the direction every
                                // level meter ever built has moved. Not animated between values: at
                                // this size a smoothed bar lags the voice enough to look like it is
                                // metering somebody else.
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight(0.55f)
                                        .background(MaSand.copy(alpha = 0.18f), RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(maLevelToBar(level))
                                            .background(MaRecordRed, RoundedCornerShape(2.dp)),
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(if (recording) 12.dp else 14.dp)
                                    .background(MaRecordRed, CircleShape),
                            )
                            if (recording) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    // Time only, as he asked. The megabytes live on the recording
                                    // bar where there is room for them; here there is one key.
                                    text = "%d:%02d".format(elapsedMs / 60000, (elapsedMs / 1000) % 60),
                                    color = MaSand,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                MaFeatureKey.ARROW_LEFT, MaFeatureKey.ARROW_RIGHT,
                MaFeatureKey.ARROW_UP, MaFeatureKey.ARROW_DOWN -> {
                    // The four arrows, sending the letter keyboard's own key codes.
                    //
                    // Written once for the four: the only difference is which code goes out, and
                    // four copies of one key is four chances for one of them to drift.
                    val code = when (button.key) {
                        MaFeatureKey.ARROW_LEFT -> KeyCode.ARROW_LEFT
                        MaFeatureKey.ARROW_RIGHT -> KeyCode.ARROW_RIGHT
                        MaFeatureKey.ARROW_UP -> KeyCode.ARROW_UP
                        else -> KeyCode.ARROW_DOWN
                    }
                    val glyph = when (button.key) {
                        MaFeatureKey.ARROW_LEFT -> Icons.Default.KeyboardArrowLeft
                        MaFeatureKey.ARROW_RIGHT -> Icons.Default.KeyboardArrowRight
                        MaFeatureKey.ARROW_UP -> Icons.Default.KeyboardArrowUp
                        else -> Icons.Default.KeyboardArrowDown
                    }
                    ThemedIconKey(
                        code = code,
                        icon = glyph,
                        contentDescription = button.key.label,
                        modifier = keyMod,
                    ) {
                        // Through the keyboard manager, not the editor, so long-press repeat and
                        // shift-selection behave exactly as they do on the letter keyboard's arrows.
                        keyboardManager.tapKey(code)
                    }
                }

                MaFeatureKey.ROW_1, MaFeatureKey.ROW_2, MaFeatureKey.ROW_3,
                MaFeatureKey.ROW_4, MaFeatureKey.ROW_5, MaFeatureKey.ROW_6 -> {
                    // F1, F2, F3 — one row each, and the ring says which are showing.
                    //
                    // Written once for the three rather than three times: the only difference
                    // between them is an index, and three copies of one key is three places for the
                    // ring to disagree with the row.
                    val which = when (button.key) {
                        MaFeatureKey.ROW_1 -> 0
                        MaFeatureKey.ROW_2 -> 1
                        MaFeatureKey.ROW_3 -> 2
                        MaFeatureKey.ROW_4 -> 3
                        MaFeatureKey.ROW_5 -> 4
                        else -> 5
                    }
                    val on = storedRows.getOrNull(which)?.enabled == true
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        // A key that shows a row: grey outline, green while its row is showing
                        // (Marko, 4.11.2026: "F2 should have grey outline but one is missing").
                        ring = if (on) onGreen else MaSwitcherRingOff,
                        onClick = {
                            scope.launch {
                                prefs.dictate.maRows.set(
                                    MaRows.serialize(MaRows.setRowEnabled(storedRows, which, !on)),
                                )
                            }
                        },
                    ) { fg ->
                        Text(
                            text = "F${which + 1}",
                            color = fg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                MaFeatureKey.SUGGESTIONS -> {
                    // The suggestion row, on and off. Ringed while it is showing.
                    //
                    // This is the only control over that row now. Nothing opens it and nothing closes
                    // it except this key and the recorder — which is the whole point: a row that the
                    // app moves is a row that moves while he is reading the text above it.
                    //
                    // 27.10.2026: the row is never hidden any more, so the key no longer turns it off.
                    // It stays ringed, and a press only makes sure the row is showing.
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        ring = onGreen,
                        onClick = { scope.launch { prefs.dictate.maSuggestionsShown.set(true) } },
                    ) { fg ->
                        Text(text = "Sg", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                MaFeatureKey.CLOUD_LOG -> {
                    // Logs this chat now. Ringed while the Claude.ai reader is switched on, because
                    // a press with it off does nothing and the ring is what says so before the
                    // press rather than after it.
                    val logOn by prefs.dictate.maCloudLogEnabled.collectAsState()
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        ring = if (logOn) onGreen else MaSwitcherRingOff,
                        onClick = { MaMessage.show(MaReader.captureNow(context)) },
                    ) { fg ->
                        Text(text = "Log", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                MaFeatureKey.READER_ROW -> {
                    // Shows or hides the reader row, ringed while it is up, like every other row
                    // switch in this app.
                    val readerShown by prefs.dictate.maReaderRowShown.collectAsState()
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        ring = if (readerShown) onGreen else MaSwitcherRingOff,
                        onClick = { scope.launch { prefs.dictate.maReaderRowShown.set(!readerShown) } },
                    ) { fg ->
                        Text(text = "Rr", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                MaFeatureKey.READ_PLAY -> {
                    // ONE KEY FOR PLAY AND PAUSE, as he asked. The reader already treats a press as
                    // "do the opposite of what you are doing"; this is the same call the reader key
                    // makes, so there is one definition of what a press means and not two.
                    val st = MaReader.state
                    ThemedIconKey(
                        // `code` and `icon`, not `imageVector`. I wrote this from memory of what a
                        // Compose Icon takes; the app's own key composable has its own names, and
                        // CI said so four times in one file.
                        code = KeyCode.NOOP,
                        icon = if (st == MaReader.State.SPEAKING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (st == MaReader.State.SPEAKING) "Pause" else "Play",
                        modifier = keyMod,
                        tint = if (st == MaReader.State.IDLE) null else MaSand,
                    ) {
                        MaReader.toggle(context) { MaMessage.show(it) }
                    }
                }

                MaFeatureKey.READ_PREV -> ThemedIconKey(
                    code = KeyCode.NOOP,
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Previous sentence",
                    modifier = keyMod,
                ) { MaReader.previousSentence() }

                MaFeatureKey.READ_NEXT -> ThemedIconKey(
                    code = KeyCode.NOOP,
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next sentence",
                    modifier = keyMod,
                ) { MaReader.skipSentence() }

                MaFeatureKey.READ_STOP -> ThemedIconKey(
                    code = KeyCode.NOOP,
                    icon = Icons.Default.Stop,
                    contentDescription = "Stop reading",
                    modifier = keyMod,
                ) {
                    // Stops the reading AND the watch — `stop()` clears the watching flag, which is
                    // the "stop waiting" he asked for. One key, because from where he sits a reading
                    // that is waiting and a reading that is speaking are one thing he wants ended.
                    MaReader.stop()
                    MaMessage.show("Reading stopped")
                }

                MaFeatureKey.READ_WATCH -> {
                    // The watch, as a setting he can see. Ringed when on, so the row says whether
                    // the next reading will wait at the end or finish.
                    val watch by prefs.dictate.maReaderWatch.collectAsState()
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        ring = if (watch) onGreen else MaSwitcherRingOff,
                        onClick = {
                            scope.launch { prefs.dictate.maReaderWatch.set(!watch) }
                            MaMessage.show(if (watch) "Will stop at the end" else "Will wait for new text")
                        },
                    ) { fg ->
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                MaFeatureKey.READ_SLOWER, MaFeatureKey.READ_FASTER -> {
                    // Speed, in the same steps the dashboard uses, and clamped where it clamps.
                    // Two keys and one body: the only difference is the sign.
                    val faster = button.key == MaFeatureKey.READ_FASTER
                    val speed by prefs.dictate.maReaderSpeed.collectAsState()
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        onClick = {
                            val next = (speed + if (faster) 10 else -10).coerceIn(50, 300)
                            scope.launch { prefs.dictate.maReaderSpeed.set(next) }
                            MaMessage.show("Reading speed ${next / 100f}\u00D7")
                        },
                    ) { fg ->
                        Text(
                            text = if (faster) "+" else "\u2212",
                            color = fg,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                MaFeatureKey.BUCKET_ROW -> {
                    // Shows or hides the bucket row, wearing the green ring like every other
                    // switch in this app: ringed means the row is there.
                    val bucketShown by prefs.dictate.maBucketRowShown.collectAsState()
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        ring = if (bucketShown) onGreen else MaSwitcherRingOff,
                        onClick = {
                            scope.launch { prefs.dictate.maBucketRowShown.set(!bucketShown) }
                        },
                    ) { fg ->
                        Text(
                            text = "Cs",
                            color = fg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                MaFeatureKey.VOLUME_KEYS -> {
                    // Volume keys on or off, and the ONLY control for it.
                    //
                    // VOLUME_KEYS.md forbids a setting for this, and the reason it gives is exact:
                    // a switch buried among thirteen draggable ones was brushed off, silently, and
                    // cost him days. That failure was not "a switch existed" — it was **a switch he
                    // could not see, in a place he was not looking.**
                    //
                    // This is the opposite of that in every respect. It is on the row he is already
                    // looking at, it is not in any list, it shows its own state, and it only exists
                    // if he chose to put it there. Green means the keys are live and doing what the
                    // contract says; plain means they are the volume and nothing else.
                    //
                    // Not a `switcher`: that ring is monochrome and means "this changes what the
                    // keyboard shows", and this changes what a hardware key does. The green state
                    // ring is the other channel, and the buckets already wear it.
                    val volumeLive by prefs.dictate.maVolumeKeysLive.collectAsState()
                    ThemedKey(
                        code = KeyCode.NOOP,
                        modifier = keyMod,
                        // The green ring, the same one the buckets wear, and for the same reason.
                        //
                        // He asked for one visual language across the app rather than two, and that
                        // is worth more than any individual choice here: a ring in this green now
                        // means one thing wherever it appears — this is on, this is holding
                        // something — and a mark that means one thing is read without being learned.
                        // A tint on a glyph in one place and a ring in another is two dialects for
                        // one idea.
                        // A switch: grey outline off, green on, like every other switch (4.11.2026).
                        ring = if (volumeLive) onGreen else MaSwitcherRingOff,
                        onClick = {
                            scope.launch { prefs.dictate.maVolumeKeysLive.set(!volumeLive) }
                            // Said out loud as well as shown. The keys themselves cannot report
                            // being switched off — a dead key looks exactly like a broken one, which
                            // is the whole history of this feature.
                            MaMessage.show(if (volumeLive) {
                                    "Volume keys off \u2014 volume only"
                                } else {
                                    "Volume keys on \u2014 record and read"
                                })
                        },
                    ) { fg ->
                        // THE ROCKER, NOT A SPEAKER.
                        //
                        // It was `VolumeUp` — a speaker with waves — and he said plainly that it
                        // reads as the speaking key. It does: the reader key on the same row is a
                        // speaker, and two keys wearing one picture is the fault the design language
                        // names first. **The glyph was describing the wrong noun.** This key is not
                        // about sound at all; it is about the two buttons on the side of the phone.
                        //
                        // So it draws those buttons: the physical rocker, a rounded pill with + at
                        // the top and − at the bottom, which is what he actually presses and what
                        // nothing else on this keyboard looks like.
                        //
                        // Drawn rather than borrowed because Material has no glyph for a hardware
                        // volume key — every candidate is a speaker, a bell or a slider, and all
                        // three describe the sound instead of the button. The usual objection to a
                        // hand-drawn shape is that it reads as a patch beside a set of real glyphs;
                        // it does not apply when the alternative is a glyph that means something
                        // else.
                        MaVolumeRocker(tint = fg)
                    }
                }

                MaFeatureKey.ENTER -> {
                    // Enter, last, which is where every keyboard ever made has put it. It was beside backspace
                    // until Marko moved it here, and the bottom right corner is a position the thumb finds
                    // without looking because it has been finding it on other keyboards for decades. Borrowing a
                    // habit somebody already has is worth more than any argument about grouping.
                    //
                    // Still the same key the bottom row draws rather than a new one that types a newline: tap
                    // for a newline, hold for the character popup from the settings screen. And still here for
                    // the reason backspace is, that with zone two folded away there is no enter key anywhere
                    // else, and a keyboard that cannot end a line has to be unfolded to finish a sentence.
                    //
                    LegacyEnterKey(
                        keyboardManager = keyboardManager,
                        modifier = keyMod,
                    )
                }
            }
          }
          }
        }
        }
      }

      // The picker, over the row, listing what the last scan found.
      //
      // Drawn inside the keyboard's own window rather than as a system dialog: an input method
      // cannot put up a dialog, and it does not need to. The keyboard is already on screen and
      // already the thing the finger is on.
      // The stepper, set on the key while looking at the page it will scroll.
      if (scrollMenu) {
        MaScrollStepper(
          pages = scrollPages,
          onChange = { scope.launch { prefs.dictate.maScrollPages.set(it) } },
          onDismiss = { scrollMenu = false },
        )
      }

      engineMenu?.let { lang ->
        MaEngineMenu(lang = lang, anchor = engineKeyRect[0], onDismiss = { engineMenu = null })
      }

      // The scan picker was here. It is gone with the list it drew: nothing arms it any more,
      // because learning happens by pressing the real button instead of choosing from thirty
      // labels written for a screen reader.
    }
}

/**
 * How many pages the S key scrolls, set on the spot.
 *
 * Bounded at ten either way. Beyond that the page has usually stopped moving anyway — a list runs
 * out — and a number that can run to fifty is a number somebody sets by accident and then has to
 * count back down.
 *
 * Zero is allowed and means the key does nothing, which is the honest reading of a stepper that
 * passes through zero on its way from down to up. It is not a state anybody stays in.
 */
@Composable
private fun MaScrollStepper(
    pages: Int,
    onChange: (Int) -> kotlin.Unit,
    onDismiss: () -> kotlin.Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16161A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(R.string.ma__scroll_pages),
            color = Color(0x99FFFFFF),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "\u2212",
            color = Color(0xFFE8B15C),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                // Steps over zero rather than through it. Zero pages is a key that does nothing,
                // which on a stepper reads as the key having broken rather than as a value chosen —
                // and it is passed through twice on the way from down to up.
                .clickable { onChange(maStepScroll(pages, -1)) }
                .padding(horizontal = 18.dp, vertical = 4.dp),
        )
        Text(
            text = if (pages < 0) "\u2191${-pages}" else "\u2193$pages",
            color = Color(0xFFECEAE3),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "+",
            color = Color(0xFFE8B15C),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onChange(maStepScroll(pages, 1)) }
                .padding(horizontal = 18.dp, vertical = 4.dp),
        )
        Text(
            text = stringRes(R.string.ma__magic_scan_close),
            color = Color(0x99FFFFFF),
            fontSize = 13.sp,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * A round key carrying a numeral, styled exactly as every other key in the row.
 *
 * `internal` rather than private because keys are becoming modules: a key that can only be drawn
 * inside this file is a key that can only live in this row, which is the thing being undone.
 * `ThemedIconKey` in LegacyDictateLayout has been internal all along, which is why the icon keys
 * were the easy half to move.
 */
@Composable
internal fun ThemedTextKey(
    label: String,
    modifier: Modifier,
    tint: Color?,
    /** A switcher rather than an action. See [ThemedIconKey]. */
    switcher: Boolean = false,
    // Optional and null by default. The fold gesture that every key once had is gone, so a long
    // press here means whatever the individual key decides — the scroll key uses it for its stepper
    // and most keys use it for nothing.
    onLongClick: (() -> kotlin.Unit)? = null,
    onClick: () -> kotlin.Unit,
) {
    ThemedKey(
        code = KeyCode.NOOP,
        modifier = modifier,
        switcher = switcher,
        onLongClick = onLongClick,
        onClick = onClick,
    ) { fg ->
        Text(
            text = label,
            color = tint ?: fg,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}


/**
 * The pause between the steps of a clipboard replace, matching the copy row's own.
 *
 * Repeated rather than imported from LegacyDictateLayout because that file is being deleted with the
 * transcribe view, and a hundred milliseconds is cheaper to state twice than to route around.
 */


/** The wait before the paste itself. AP's value, for AP's reason. See the CH key. */
// 333ms, Marko's number. The sequence he specified is select all, 100ms, delete, 333ms, paste.

/** The grey a key wears when it is present but has nothing to act on. */
private val MaDimmed = Color(0xFF6B6B6B)

/**
 * Opens the system accessibility settings, where the service has to be switched on by hand.
 *
 * Android gives no way to turn it on from inside the app and no deep link to this app's own row in
 * that list, so this lands on the list and the user finds TTT in it. Still far better than a
 * message: the keys that need the service are useless until this is done, and nothing else in the
 * app says so at the moment it matters.
 */
internal fun maOpenAccessibilitySettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    MaMessage.show(context.getString(R.string.ma__accessibility_needed))
}

/**
 * Moves the scroll count one step, skipping zero and stopping at ten either way.
 *
 * The sign is the direction, so the sequence runs ... -2, -1, 1, 2 ... and never rests on a value
 * that would make the key do nothing.
 */
private fun maStepScroll(pages: Int, delta: Int): Int {
    val next = pages + delta
    val stepped = if (next == 0) pages + delta * 2 else next
    return stepped.coerceIn(-10, 10)
}

/**
 * The wand's bar: copy the screen, or leave, or go and edit the list.
 *
 * ### Three controls, and nothing else
 *
 * It held a hint, a text field, Copy, Store, Cancel and a link, which is six things in a strip the
 * height of a key. The field was the worst of them: typing a button's name into a box the width of a
 * thumb, on a keyboard that is itself the thing being configured, when the same box exists on the
 * wand's own settings screen with room to read it. Removing it removes the reason for Store as well.
 *
 * What is left is the loop that actually works. Copy the tree, ask someone who can read it against a
 * picture of the screen, then add the name where terms are added.
 *
 * ### Yellow means this leaves the keyboard
 *
 * The gear is sand, and that is a rule rather than a colour: in this app sand marks a control that
 * takes you somewhere else, the way red marks recording and green marks a level that is safe. A
 * label saying "Wand" had to be read to know what it did; a gear in the colour of leaving does not.
 */
@Composable
private fun MaWandBar(
    state: MaScreenTargets.Learn.State,
    onCopyDump: () -> kotlin.Unit,
    onDismiss: () -> kotlin.Unit,
    onEdit: () -> kotlin.Unit,
) {
    if (state is MaScreenTargets.Learn.State.Idle) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16161A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(R.string.ma__wand_dump_hint),
            color = Color(0x99FFFFFF),
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringRes(R.string.ma__wand_copy),
            color = MaSand,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onCopyDump() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            text = stringRes(R.string.ma__wand_cancel),
            color = Color(0x99FFFFFF),
            fontSize = 15.sp,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringRes(R.string.ma__wand_settings),
            tint = MaSand,
            modifier = Modifier
                .clickable { onEdit() }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .size(22.dp),
        )
    }
}

/**
 * Sand: this control leaves the keyboard and takes you somewhere.
 *
 * One meaning, one colour, everywhere — the same discipline as the recording red. A person should be
 * able to learn it once from any screen and read it on every other.
 */
/**
 * How far up the page the automatic bucket has reached. 0 is the last code block.
 *
 * A Compose state at file level rather than `remember` inside the row, so it survives the keyboard
 * closing and reopening. Collecting code blocks means leaving the keyboard to look at what was
 * copied and coming back for the next one — a counter that reset on every close would make the key
 * useless for the one thing it is for.
 *
 * Not a stored preference either: it is about the screen in front of him now, and a rank restored
 * from last week would point somewhere meaningless. Reset by a long press, and by the process
 * ending, which are both moments when starting again is what he would want.
 */
// The A key's ladder is gone entirely — it reads what is in the frame instead of counting.
// is handled in the keyboard manager, which cannot reach a private variable in this file.

/**
 * Whether the reader dashboard is showing.
 *
 * File-level rather than remembered inside the row, as the ladder position once was: the row
 * is recomposed constantly and rebuilt whenever the keyboard changes shape, and state remembered
 * inside it would close the dashboard every time he switched view while it was open.
 */
private var maDashboardOpen by mutableStateOf(false)

private val MaSand = Color(0xFFE8B15C)

/**
 * The audio level as a fraction of the meter's height.
 *
 * The same dB curve the recording bar uses rather than the raw amplitude: a linear bar on a raw
 * level sits near the floor for ordinary speech and only moves when something is shouted — a meter
 * that is technically correct and useless for seeing whether a voice is being heard at all.
 *
 * Floored at a visible sliver, so silence reads as "recording, hearing nothing" rather than as an
 * empty slot where a meter should be.
 */
private fun maLevelToBar(level: Float): Float {
    val v = kotlin.math.abs(level).coerceAtLeast(1e-6f)
    val db = (20.0 * kotlin.math.log10(v.toDouble())).toFloat()
    return ((db + 48f) / 48f).coerceIn(0.06f, 1f)
}

/**
 * The physical volume rocker: a rounded pill with + above and − below.
 *
 * Sized and weighted to sit in a line of 24dp Material glyphs — the same optical height, the same
 * stroke, so it does not read as a sticker on a row of drawn icons.
 */
@Composable
private fun MaVolumeRocker(tint: Color) {
    Box(
        modifier = Modifier
            .size(width = 15.dp, height = 24.dp)
            .border(1.5.dp, tint, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "+", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "\u2212", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A glyph with the history mark on it: [base] for the subject, the circular arrow for the past.
 *
 * ### Why a composed glyph rather than a Material one
 *
 * Material has `History` — the arrow round a clock — and nothing for "the history of a clipboard".
 * The alternatives were all wrong in the same way: `ContentPasteGo` says go, `ContentPasteSearch`
 * says find, `Restore` says undo. **None of them says "the ones before".**
 *
 * So the mark is applied rather than looked up, which also makes it a rule instead of a coincidence:
 * anything that opens a history takes the same arrow, and a reader who learns it once on the
 * transcription key reads it everywhere without being taught again.
 *
 * ### The proportions
 *
 * The arrow sits at the bottom-left at 60% and overlaps rather than sitting beside: a badge in the
 * corner of a glyph reads as one picture, a second glyph beside it reads as two keys crammed into
 * one. It is drawn over a small disc of the key's own background so the two sets of strokes do not
 * tangle where they cross — without it the arrow's tail reads as part of the clipboard's rim at key
 * size.
 *
 * Bottom-LEFT because the clipboard's clip is top-centre and its lines run right; the free corner is
 * the one the base glyph is quietest in, and that is where a badge belongs.
 */
@Composable
internal fun MaHistoryGlyph(
    base: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: Dp = 24.dp,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(imageVector = base, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(size * 0.62f)
                .clip(CircleShape)
                .background(MaKeyFaceForBadge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}

/**
 * What a badge is punched out of: the key face behind the glyph.
 *
 * Hardcoded rather than read from the theme because a key face is a Snygg-styled surface and this
 * runs inside the icon slot, where that colour is not in scope. It matches the dark key of the
 * app's own theme, which is the only theme these keys have ever been drawn in.
 */
private val MaKeyFaceForBadge = Color(0xFF161B22)

/**
 * What a feature-row key is called for its colour: the built-in key's name, `C3` for a bucket,
 * `M2` for a macro. Shared with Settings → Key colours, so the grid and the row agree on every id.
 */
internal fun maFeatureToken(button: MaRows.Button): String = when (button) {
    is MaRows.Button.Builtin -> button.key.name
    is MaRows.Button.Clip -> "C${button.slot}"
    is MaRows.Button.Macro -> "M${button.slot}"
}

/** Talk to AssemblyAI: the dot of the network's record keys (8.11.2026). Red since 9.11.2026 (Marko: "make it red"), it was blue. */
internal val MaEngineNetRed = androidx.compose.ui.graphics.Color(0xFFE53935)

/** Talk to Parakeet: the dot of the phone's record keys (8.11.2026). */
internal val MaEngineLocalAmber = androidx.compose.ui.graphics.Color(0xFFFFB300)

private fun Modifier.androidx_onPlaced(f: (androidx.compose.ui.geometry.Rect) -> kotlin.Unit): Modifier =
    this.onGloballyPositioned { f(it.boundsInWindow()) }

/**
 * The engines for one language, opened by a long press on any H or E record key (Marko, 9.11.2026): a VERTICAL
 * list in a popup window that grows UP from the pressed key to the top of the screen, every provider visible at
 * once (rows shrink to fit; vertical scroll only as a last resort). One tap chooses (the same store as Voice
 * transcription buttons, so the key changes at once) and the panel closes. The x, or a tap outside, closes it.
 * A Popup is its own window, so it may extend above the keyboard's own area.
 */
@Composable
private fun MaEngineMenu(lang: String, anchor: androidx.compose.ui.geometry.Rect?, onDismiss: () -> kotlin.Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val live by dev.patrickgold.florisboard.dictate.engines.EngineLive.state.collectAsState()
    val chosen = live.selected[lang] ?: dev.patrickgold.florisboard.dictate.engines.EngineStore.selected(context, lang)
    val ids = dev.patrickgold.florisboard.dictate.engines.EngineCatalog.ALL.map { it.id }
        .filter { it == chosen || dev.patrickgold.florisboard.dictate.engines.EngineChain.available(context, it) }
    val red = Color(0xFFE53935)
    val loc = IntArray(2).also { view.getLocationOnScreen(it) }
    val keyTop = anchor?.top ?: 0f
    val keyLeft = anchor?.left ?: 0f
    // Room above the key down from the status bar; header row + one row per provider share it.
    val statusPx = with(density) { 28.dp.toPx() }
    val availPx = (loc[1] + keyTop - statusPx).coerceAtLeast(with(density) { 120.dp.toPx() })
    val rows = ids.size + 1
    val rowDp = with(density) { (availPx / rows).toDp() }.coerceIn(30.dp, 52.dp)
    val panelDp = rowDp * rows
    val panelPx = with(density) { panelDp.toPx() }.coerceAtMost(availPx)
    val widthPx = minOf(with(density) { 300.dp.toPx() }, view.width.toFloat())
    val x = keyLeft.coerceIn(0f, (view.width - widthPx).coerceAtLeast(0f))
    val provider = object : androidx.compose.ui.window.PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: androidx.compose.ui.unit.IntRect,
            windowSize: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            popupContentSize: androidx.compose.ui.unit.IntSize,
        ) = androidx.compose.ui.unit.IntOffset(x.toInt(), (keyTop - panelPx).toInt())
    }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { panelPx.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16161A))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(rowDp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (lang == "hr") "H" else "E", color = red, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "\u2715", color = Color(0xFFECEAE3), fontSize = 18.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            for (id in ids.reversed()) {
                val sel = id == chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowDp)
                        .background(if (sel) red.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable {
                            dev.patrickgold.florisboard.dictate.engines.EngineLive.select(context, lang, id)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dev.patrickgold.florisboard.dictate.engines.EngineCatalog.shortName(id) + dev.patrickgold.florisboard.dictate.engines.EngineScores.suffix(context, id, lang),
                        color = if (sel) red else Color(0xFFECEAE3), fontSize = 16.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = engineSubLabel(context, id), color = Color(0x99FFFFFF), fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}
