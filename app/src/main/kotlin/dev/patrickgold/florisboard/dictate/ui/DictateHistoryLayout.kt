/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import android.text.format.DateUtils
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.data.history.DictateHistorySource
import dev.patrickgold.florisboard.dictate.MaLog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * The transcription-history panel (issue #140), rendered as its own [ImeUiMode.HISTORY] next to the
 * typing keyboard (see `ImeWindow`). Opened via the history QuickAction in the Smartbar (the button that
 * previously did a one-shot "re-insert last dictation"). Lists recent dictations newest-first and lets
 * the user re-insert one into the field with a tap, or re-transcribe its retained audio.
 *
 * Full management (playback, delete, export, retention settings) lives on the History settings screen;
 * this in-keyboard panel is the fast recovery/insert surface. Reuses the themed `media-*` Snygg elements
 * (compact text, large tap targets) and the prompt panel's accent scrollbar.
 */
@Composable
fun DictateHistoryLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val accent by prefs.theme.accentColor.collectPrefAsState() // follows the user's keyboard accent.
    // null = not loaded yet (show a spinner), empty list = genuinely no history (#205).
    // Built AND collected on IO so opening the Room database never runs on the composition's main-thread
    // context. (The loading spinner freezing was caused by eagerly composing every row — see the list below.)
    val entries by remember(context) {
        flow { emitAll(DictateHistoryStore.flow(context)) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)

    // Once when the panel opens, never while it scrolls.
    //
    // A row can outlive its audio file — storage cleared, a backup that carried the database but not
    // the recordings — and it then offers Re-Transcribe on something that cannot be read.
    //
    // Checked HERE and not in the row: a file check inside a list item is disk work during scrolling
    // and it runs again on every recomposition. `LaunchedEffect(Unit)` runs it once per opening, on
    // IO, and the repair writes the truth into the database so every other reader is fixed too.
    //
    // Nothing is said when nothing was wrong, which is every ordinary open.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val repaired = runCatching { DictateHistoryStore.repairMissingAudio(context) }.getOrDefault(0)
            if (repaired > 0) MaLog.add("history", "$repaired row(s) had lost their audio, cleared")
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Which entry is being deleted, if any. The three deletions are genuinely different outcomes, so
    // the panel asks rather than guessing: the transcript is worth keeping forever, the audio is what
    // fills the phone, and being made to lose one to reclaim the other is a false choice.
    var pendingDelete by remember { mutableStateOf<DictateHistoryEntry?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            // Lock to the normal keyboard height so opening history never changes the IME height (no jump).
            .height(FlorisImeSizing.panelUiHeight()),
    ) {
        // Header: back to the typing keyboard + panel title.
        SnyggRow(
            elementName = FlorisImeUi.MediaBottomRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIconButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
                modifier = Modifier.size(FlorisImeSizing.smartbarHeight),
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack)
            }
            // BALANCE. The arrow is on the left, so the cog belongs on the right, and the title
            // sits between them.
            //
            // Removing the language badge left the cog stranded beside the arrow and the whole row
            // leaning into the left corner. **A row with everything at one end is not a row, it is a
            // pile** — an interface is a composition, and weight has to be distributed as
            // deliberately as it is in a photograph.
            SnyggText(
                elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                text = stringRes(R.string.dictate__history_title),
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            // Delete all, at the top, where a thing that acts on the WHOLE list belongs.
            //
            // It was only reachable per entry, so clearing a long history meant pressing Delete once
            // per recording. An action whose scope is the list should live on the list, not be
            // assembled out of repeated single actions.
            //
            // It asks first. Everything else here can be undone or re-transcribed; this cannot, and
            // it is two taps from the thing he actually came to do.
            Text(
                text = "Delete all",
                color = MaDestructive,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { confirmClearAll = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            // Jump straight to the full history management screen in the settings app.
            SnyggIconButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                onClick = { FlorisImeService.launchSettings("settings/dictate/history") },
                modifier = Modifier.size(FlorisImeSizing.smartbarHeight),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        val loadedEntries = entries
        if (loadedEntries == null || loadedEntries.isEmpty()) {
            SnyggBox(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
            ) {
                if (loadedEntries == null) {
                    // Still loading from disk — the same centered accent spinner as the GIF panel. It
                    // animates properly now that opening the panel no longer blocks the UI thread.
                    CircularProgressIndicator(color = accent)
                } else {
                    SnyggText(
                        elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                        text = stringRes(R.string.dictate__history_empty),
                    )
                }
            }
        } else {
            // Lazy on purpose: a full history is up to several hundred entries, and composing them all
            // eagerly blocked the UI thread for well over a second — which is what froze the loading
            // spinner (and everything else) while the panel opened. Only visible rows are composed now.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .dictateLazyPanelScrollbar(listState, accent),
            ) {
                items(loadedEntries, key = { it.id }) { entry ->
                    HistoryPanelRow(
                        entry = entry,
                        rowContext = context,
                        accent = accent,
                        onInsert = {
                            DictateController.insertHistoryText(context, entry.text)
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        },
                        // Long-press inserts the raw transcript instead, for entries a prompt rewrote
                        // (issue #240). The row is marked so this isn't a hidden gesture.
                        onInsertOriginal = {
                            DictateController.insertHistoryText(context, entry.originalText)
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        },
                        onRetranscribe = {
                            DictateController.retranscribeHistoryEntry(context, entry)
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        },
                        onDeleteRequested = {
                            if (entry.audioPath != null) {
                                pendingDelete = entry
                            } else {
                                // Nothing to choose between when there is no audio.
                                scope.launch(Dispatchers.IO) {
                                    DictateHistoryStore.delete(context, entry)
                                }
                            }
                        },
                    )
                }
            }
        }
        // Inline, NOT an AlertDialog. **This is what crashed.**
        //
        // A Compose `AlertDialog` opens a real platform Dialog, and a dialog needs a window token
        // from an Activity. An input method has no Activity — its window belongs to the system —
        // so the first tap on "Delete all" took the keyboard down with it.
        //
        // `MaDeleteChooser` beside it was already inline for exactly this reason. I added a dialog
        // next to a working example of why not to. **When a screen already solves a problem, copy
        // that, do not reach for the standard component.**
        if (confirmClearAll) {
            SnyggRow(
                elementName = FlorisImeUi.MediaBottomRow.elementName,
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                MaHistoryAction(
                    label = "Delete everything",
                    enabled = true,
                    tint = MaDestructive,
                    onClick = {
                        confirmClearAll = false
                        // ON IO, AND WRAPPED — like the other two, which it was not.
                        //
                        // `clearAll` walks the audio directory and deletes every file in it. On the
                        // main thread that blocks the keyboard for as long as the disk takes, and
                        // with a few hundred recordings that is long enough for Android to kill the
                        // input method for not responding.
                        //
                        // Wrapped because a delete can throw — a file held open by a media scanner,
                        // a revoked permission — and an exception escaping a launched coroutine
                        // takes the keyboard down. **He asked for delete that does not crash; the
                        // crash is not always in the dialog.**
                        scope.launch(Dispatchers.IO) {
                            runCatching { DictateHistoryStore.clearAll(context) }
                                .onFailure { MaLog.add("history", "clear all failed: ${it.javaClass.simpleName}") }
                        }
                    },
                )
                MaBullet(accent)
                MaHistoryAction(
                    label = "Cancel",
                    enabled = true,
                    tint = accent,
                    onClick = { confirmClearAll = false },
                )
            }
        }
        pendingDelete?.let { target ->
            MaDeleteChooser(
                accent = accent,
                onAudioOnly = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { DictateHistoryStore.deleteAudioOnly(context, target) }
                            .onFailure { MaLog.add("history", "audio delete failed: ${it.javaClass.simpleName}") }
                    }
                    pendingDelete = null
                },
                onEverything = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { DictateHistoryStore.delete(context, target) }
                            .onFailure { MaLog.add("history", "delete failed: ${it.javaClass.simpleName}") }
                    }
                    pendingDelete = null
                },
                onCancel = { pendingDelete = null },
            )
        }
    }
}

/**
 * The delete choice, as a strip along the bottom of the panel rather than a dialog.
 *
 * A dialog over a keyboard is awkward: it steals focus from the field being typed into and can push
 * the panel around. A strip stays inside the keyboard's own bounds and reads as part of it.
 */
@Composable
private fun MaDeleteChooser(
    accent: Color,
    onAudioOnly: () -> Unit,
    onEverything: () -> Unit,
    onCancel: () -> Unit,
) {
    SnyggRow(
        elementName = FlorisImeUi.MediaBottomRow.elementName,
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaHistoryAction(
            label = "Audio only",
            enabled = true,
            tint = MaDestructive,
            onClick = onAudioOnly,
            modifier = Modifier.weight(1f),
        )
        MaHistoryAction(
            label = "Delete both",
            enabled = true,
            tint = MaDestructive,
            onClick = onEverything,
            modifier = Modifier.weight(1f),
        )
        MaHistoryAction(
            label = "Cancel",
            enabled = true,
            tint = accent,
            onClick = onCancel,
            modifier = Modifier.weight(0.8f),
        )
    }
}

@Composable
private fun HistoryPanelRow(
    entry: DictateHistoryEntry,
    rowContext: android.content.Context,
    accent: Color,
    onInsert: () -> Unit,
    onInsertOriginal: () -> Unit,
    onRetranscribe: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    // Both versions exist only when a prompt actually rewrote the dictation (issue #240).
    val hasOriginal = entry.originalText.isNotEmpty() && entry.originalText != entry.text
    // The transcript and its actions are one item, so they must sit in a column: two rows returned
    // side by side from a lazy item would be placed in the same slot and overlap.
    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
    SnyggRow(
        elementName = FlorisImeUi.MediaBottomRow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        // A failed entry has no committed text yet — inserting is disabled until it's re-transcribed.
        clickAndSemanticsModifier = Modifier.combinedClickable(
            enabled = !entry.failed,
            onClick = { onInsert() },
            onLongClick = if (hasOriginal) onInsertOriginal else null,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (false /* zero pinning, 23.11.2026 */) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
                tint = accent,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            // An offline note announces itself, in its own colour and with its own title.
            //
            // It was never delivered anywhere — he dictated it with no field open — so it is the one
            // kind of entry he comes back to *looking for*. A title he can scan beats a first line
            // he has to read, and the colour says at a glance which of these are notes and which are
            // things he already sent.
            val isNote = entry.source == DictateHistorySource.OFFLINE
            if (isNote && entry.title.isNotBlank()) {
                Text(
                    text = entry.title,
                    color = MaNoteAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // THE NOTE ITSELF IS COLOURED, not only its title.
            //
            // The colour was on the title, and a note whose title failed to generate looked exactly
            // like every other entry — which is the case where he most needs to recognise it. The
            // transcript carries the colour, so an offline note is identifiable whether or not the
            // model ever named it.
            if (isNote) {
                Text(
                    text = historyPreview(entry.text),
                    color = MaNoteAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    text = historyPreview(entry.text),
                )
            }
            SnyggText(
                elementName = FlorisImeUi.KeyHint.elementName,
                // The hint makes the long-press discoverable; without it the second version would exist
                // but nobody would know to reach for it (issue #240).
                text = if (hasOriginal) {
                    historyMetaLine(entry) + " · " + stringRes(R.string.dictate__history_hold_for_original)
                } else {
                    historyMetaLine(entry)
                },
            )
        }
    }

    // Actions on their own line, with words on them. The icons alone were a guess: a circular arrow
    // could mean replay, refresh or undo, and the only way to find out was to press it and see what
    // happened to the text. Naming them costs one line of height and removes the guessing.
    SnyggRow(
        elementName = FlorisImeUi.MediaBottomRow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Equal gaps, edge to edge. Composition: the eye reads uneven spacing as a mistake even
        // when it cannot name what is wrong, and four labels at four different distances is exactly
        // that. SpaceBetween puts the same air between every pair.
        // Centred, not spread. SpaceBetween spaces children but does not shrink them, so when the
        // words were wider than the row the last one — Delete — simply left the screen. Twice, and
        // neither time did anything report it: an off-screen child is not an error.
        //
        // The bullets provide the rhythm now, and centring keeps the group together as a phrase.
        horizontalArrangement = Arrangement.Center,
    ) {
        // Full words, never clipped. The labels were being cut to "Inse" and "Dele" because the row
        // shared its width with a long transcript; giving the actions their own line and equal
        // shares means each one has room to say what it does.
        // NO WEIGHTS. Every label is as wide as its own word.
        //
        // They were weighted, and a weight is a promise about width that a word cannot keep: given a
        // quarter each, "Insert" clipped to "Ins" and the badge was squeezed to nothing and never
        // appeared at all.
        MaHistoryAction(
            label = "Insert",
            enabled = !entry.failed,
            tint = accent,
            onClick = onInsert,
        )
        if (entry.audioPath != null) {
            MaBullet(accent)
            // THE BADGE IS A TOGGLE. RE-TRANSCRIBE IS THE ACTION. Two steps, not one.
            //
            // It did both at once: tapping the badge swapped the language and immediately sent the
            // audio back up. That is one gesture, which sounded efficient, and it is wrong — it
            // means the tag cannot be *looked at* or corrected without spending a transcription, and
            // a mis-tap costs a call to the provider.
            //
            // So the badge only changes what the tag says. Nothing is sent until Re-Transcribe is
            // pressed, and it sends in whatever the badge is showing. **A switch that also acts is
            // two controls wearing one coat.**
            //
            // The choice lives beside the row rather than in the entry, because it is a decision
            // about the next send and not a fact about the recording. Until he presses
            // Re-Transcribe, the recording is still what it always was.
            var chosen by remember(entry.id) { mutableStateOf(entry.language) }
            MaHistoryAction(
                label = if (chosen == MaLanguage.EN) "[ENG]" else "[HR]",
                enabled = true,
                tint = accent,
                onClick = {
                    chosen = if (chosen == MaLanguage.EN) MaLanguage.HR else MaLanguage.EN
                },
            )
            MaBullet(accent)
            MaHistoryAction(
                label = "Re-Transcribe",
                enabled = true,
                tint = accent,
                onClick = {
                    // The language is set at the moment of sending, from the badge, so what he can
                    // see is what goes up.
                    MaLanguage.set(rowContext, chosen)
                    MaLog.add("keys", "re-transcribing in $chosen")
                    onRetranscribe()
                },
            )
        }
        MaBullet(accent)
        MaHistoryAction(
            label = "Delete",
            enabled = true,
            // Destructive actions read red in every scheme, which is the one colour convention worth
            // keeping: it is the only action here that cannot be undone.
            tint = MaDestructive,
            onClick = onDeleteRequested,
        )
    }
    }
}

/** One labelled action. Text rather than an icon, because the words are the whole point here. */
/** The separator. Dimmed, because it is punctuation and not one of the things he can press. */
@Composable
private fun MaBullet(tint: Color) {
    Text(
        text = "\u00B7",
        color = tint.copy(alpha = 0.45f),
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun MaHistoryAction(
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A clickable word, not a Button. **This is why Delete kept leaving the screen.**
    //
    // `SnyggButton` is a Material button: it carries a minimum width and its own internal padding,
    // so four of them plus separators were far wider than the row whatever arrangement they were
    // given. Every attempt to fix this by changing the ARRANGEMENT was treating a symptom — the
    // children were simply too big, and no arrangement shrinks a child.
    //
    // A Text with its own padding is exactly as wide as its word.
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) tint else tint.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Red for the one action that cannot be taken back, in any colour scheme. */
private val MaDestructive = Color(0xFF9B3B33)

/** Collapses newlines so the transcript flows as prose; the two-line ellipsis is handled by SnyggText. */
private fun historyPreview(text: String): String = text.replace('\n', ' ').trim()

/** "5 min ago · OpenAI · 0:12 · 0.4 MB" — omits the parts that don't apply. */
private fun historyMetaLine(entry: DictateHistoryEntry): String {
    val parts = ArrayList<String>(4)
    // The provider is deliberately not here. It is the same on every row, so it told nobody
    // anything, and the width is better spent on how long this one took and in which format, which
    // differs row to row and is the whole reason for keeping the audio.
    if (entry.sendMs > 0L) {
        val secs = "%.1fs".format(entry.sendMs / 1000.0)
        parts.add(if (entry.sendFormat.isNotBlank()) "$secs ${entry.sendFormat}" else secs)
    }
    parts.add(
        DateUtils.getRelativeTimeSpanString(
            entry.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    )
    formatHistoryDuration(entry.durationSecs)?.let { parts.add(it) }
    formatHistorySize(entry.audioBytes)?.let { parts.add(it) }
    return parts.joinToString(" · ")
}

fun formatHistoryDuration(seconds: Long): String? = when {
    seconds <= 0L -> null
    seconds < 60L -> "0:${seconds.toString().padStart(2, '0')}"
    else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

fun formatHistorySize(bytes: Long): String? = when {
    bytes <= 0L -> null
    bytes < 1_000_000L -> "${(bytes / 1000L).coerceAtLeast(1L)} KB"
    else -> String.format("%.1f MB", bytes / 1_000_000.0)
}

/**
 * The colour of a note that was never sent anywhere.
 *
 * A deeper gold, in the same family as the app's amber rather than opposite it.
 *
 * It was teal — a contrast colour, and he asked for harmony instead. He is right: this list is sand
 * and amber on near-black, and a cyan line in it reads as an error or a link, which a note is
 * neither. **A different SHADE of the same hue says "another kind of the same thing"; a different
 * hue says "something else has gone on".**
 *
 * Darker than the accent so the two never compete: the amber marks what can be pressed, and this
 * marks what was never sent.
 */
private val MaNoteAccent = Color(0xFFC9A227)
