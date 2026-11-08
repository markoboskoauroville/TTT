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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaCommandPalette
import dev.patrickgold.florisboard.dictate.MaKeySearch
import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import dev.patrickgold.florisboard.dictate.ui.MaHistoryGlyph
import dev.patrickgold.florisboard.dictate.MaFeatureKey
import dev.patrickgold.florisboard.dictate.ui.MaRecordRed
import dev.patrickgold.florisboard.dictate.MaMacroSlots
import dev.patrickgold.florisboard.dictate.MaRows
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/**
 * The feature row editor: three rows, three tabs, one row at a time.
 *
 * This is the app's main feature and the screen it is arranged from, so it is deliberately the
 * plainest thing in the settings. One tab is one row. Everything visible on the tab belongs to that
 * row and nothing else, which is what stops the screen becoming the crowded single list it was
 * before: with three rows of keys shown at once there was no way to see any of them.
 *
 * ### Same list, same gestures, as the rest of the app
 *
 * A numbered row, the key's own glyph, its name, a tick, and a handle to drag it by. That pattern is
 * already on the settings order screen and was on the feature row screen before this one, and
 * matching it was the whole point of the rewrite: an invented second style made the app look like
 * two apps. Dragging is [MaReorderableColumn], the same component the rest of the app drags with.
 *
 * ### Nothing is protected
 *
 * Every key can be unticked, backspace and enter included. The floor lives in the model rather than
 * here: switch everything off in all three rows and the keyboard draws the settings key alone, so
 * there is always a route back to this screen. A rule enforced by hiding tick boxes would be a rule
 * the user has to discover by failing.
 *
 * ### Unticked, not deleted
 *
 * A key switched off stays in the list, greyed. It keeps its position, so switching it back on puts
 * it where it was rather than at the end — a list whose entries move about cannot be learned by
 * position, and this one is navigated by position.
 */
@Composable
fun MaRowsScreen() = FlorisScreen {
    title = "Feature row"

    content {
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        val rowsRaw by prefs.dictate.maRows.collectAsState()
        val macroRaw by prefs.dictate.maMacroSlots.collectAsState()

        var rows by remember(rowsRaw) {
            mutableStateOf(
                if (rowsRaw.isBlank()) MaRows.defaultRows() else MaRows.parse(rowsRaw),
            )
        }
        val macroSlots = remember(macroRaw) { MaMacroSlots.parse(macroRaw) }

        fun commit(next: List<MaRows.Row>) {
            rows = next
            scope.launch { prefs.dictate.maRows.set(MaRows.serialize(next)) }
        }

        var tab by remember { mutableStateOf(0) }
        var adding by remember { mutableStateOf(false) }
        var editingMacro by remember { mutableStateOf<Int?>(null) }

        // The only way to hide the feature row, now that a long press no longer does it.

        //

        // It has to live here, above the tabs, because it governs all three rows rather than the one

        // being edited. And it has to exist at all: the gesture that used to hide the row was the

        // only thing that could, so removing it without putting this here would have made the row

        // permanent — which is the opposite of what was asked for.

        SwitchPreference(

            prefs.dictate.maFeatureRowShown,

            title = "Show the feature row",

            summary = "The rows of keys above the keyboard. Turn this off to hide all of them.",

        )

        // How wide a spacer is. Here rather than on the keyboard, because a spacer is invisible
        // there and a control you cannot see is a control you cannot press.
        val spacerTenths by prefs.dictate.maSpacerTenths.collectAsState()
        val spacerScope = rememberCoroutineScope()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Spacer width", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Ten is one key wide. Add a spacer to a row to push the keys after it " +
                        "across \u2014 useful for reaching send with the thumb you actually use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { spacerScope.launch { prefs.dictate.maSpacerTenths.set((spacerTenths - 5).coerceAtLeast(5)) } },
                enabled = spacerTenths > 5,
            ) { Text("\u2212", fontSize = 20.sp) }
            Text(
                text = "$spacerTenths",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center,
            )
            TextButton(
                // Forty tenths is four keys, which is already most of a row. Past that a spacer is
                // not arranging the row, it is emptying it.
                onClick = { spacerScope.launch { prefs.dictate.maSpacerTenths.set((spacerTenths + 5).coerceAtMost(40)) } },
                enabled = spacerTenths < 40,
            ) { Text("+", fontSize = 20.sp) }
        }


        // The "Row 2 becomes Row 1" buttons are gone. The tabs are dragged instead — see the strip
        // below. Six rows made the button version untenable anyway: it was five buttons per tab,
        // thirty sentences to read, for a thing his finger can say in one movement.

        // DRAG A TAB TO REORDER THE ROWS.
        //
        // Long press, then slide. A plain tap still selects, so the gesture he uses constantly is
        // untouched and the new one is deliberate — a drag that started on the first pixel of
        // movement would reorder his rows every time his thumb slid while tapping.
        //
        // The drag MOVES rather than swaps, which is a real change from the buttons this replaces
        // and is the right one: dragging the third tab to the front depicts sliding it in front of
        // the others, and every draggable list on the phone behaves that way. **The gesture is the
        // specification.** A swap would leave the row he dragged past sitting where he started,
        // which is not what his finger drew.
        //
        // The target is worked out from the distance travelled divided by the tab width, because
        // the tabs are equal width by construction. Rounded, so the row lands where the finger is
        // rather than where it has fully passed — a drag that needs 51% of a tab to register reads
        // as ignoring him.
        var dragging by remember { mutableStateOf(-1) }
        var dragBy by remember { mutableStateOf(0f) }
        var tabWidth by remember { mutableStateOf(1f) }
        // NAMING THE ROW HE IS LOOKING AT.
        //
        // On the tab's own page rather than in a dialog: he is already here arranging this row, and
        // the name is one more thing about it. A dialog would be a second screen for one field.
        //
        // It writes on every keystroke through `commit`, like everything else on this screen. No
        // save button, because a save button on a settings screen is a thing to forget — and there
        // is nothing here that a half-typed name can damage.
        //
        // The placeholder is what the tab falls back to, so an empty field and the tab agree about
        // what the row is called.
        OutlinedTextField(
            value = rows.getOrNull(tab)?.name.orEmpty(),
            onValueChange = { typed ->
                val next = rows.mapIndexed { i, row ->
                    if (i == tab) row.copy(name = MaRows.sanitiseName(typed)) else row
                }
                commit(next)
            },
            singleLine = true,
            label = { Text("Name this row") },
            placeholder = { Text("Row ${tab + 1}") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        TabRow(
            selectedTabIndex = tab,
            modifier = Modifier.onSizeChanged { tabWidth = (it.width / MaRows.ROW_COUNT).toFloat().coerceAtLeast(1f) },
        ) {
            (0 until MaRows.ROW_COUNT).forEach { i ->
                // WHAT A DRAG LOOKS LIKE.
                //
                // He could reorder the tabs and could not see it happening: the long press armed
                // silently, the finger moved, and nothing on screen changed until he let go and the
                // row had moved. **A gesture with no feedback is a gesture he has to believe in.**
                //
                // Three signals, the same three the settings list uses for vertical reordering, laid
                // on their side:
                //
                //   lifted   — the dragged tab is drawn brighter and slightly larger, so it reads as
                //              picked up rather than merely selected
                //   carried  — it moves with the finger, so what he is holding is under his thumb
                //   landing  — the tab it would swap with dims, so he can see WHERE it will go
                //              before he commits to it
                //
                // The last one is the one that was really missing. Lifting and carrying say "you are
                // dragging"; only the landing mark says "and this is what will happen".
                val isDragged = dragging == i
                val landingIndex = if (dragging >= 0) {
                    (dragging + (dragBy / tabWidth).roundToInt()).coerceIn(0, MaRows.ROW_COUNT - 1)
                } else {
                    -1
                }
                val isLanding = dragging >= 0 && !isDragged && landingIndex == i
                Tab(
                    modifier = Modifier
                        .graphicsLayer {
                            // Carried: the offset is the raw drag, not the rounded target, so the
                            // tab tracks the finger continuously rather than snapping between slots.
                            translationX = if (isDragged) dragBy else 0f
                            scaleX = if (isDragged) 1.08f else 1f
                            scaleY = if (isDragged) 1.08f else 1f
                            // The landing tab steps back rather than lighting up: two bright things
                            // would compete, and the one he is holding must stay the brighter.
                            alpha = when {
                                isDragged -> 1f
                                isLanding -> 0.45f
                                else -> 1f
                            }
                        }
                        // Drawn last so a lifted tab passes over its neighbours instead of under
                        // them, which is what makes it read as lifted rather than as sliding
                        // through a slot.
                        .zIndex(if (isDragged) 1f else 0f)
                        .pointerInput(rows) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = i; dragBy = 0f },
                            onDragEnd = {
                                val moved = (dragBy / tabWidth).roundToInt()
                                val target = (i + moved).coerceIn(0, MaRows.ROW_COUNT - 1)
                                if (dragging >= 0 && target != i) {
                                    commit(MaRows.moveRow(rows, i, target))
                                    // Follow the keys, not the number, exactly as the buttons did.
                                    tab = target
                                }
                                dragging = -1
                                dragBy = 0f
                            },
                            onDragCancel = { dragging = -1; dragBy = 0f },
                        ) { change, drag ->
                            change.consume()
                            dragBy += drag.x
                        }
                    },
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            // His name for it, or "Row 3" when he has not given one. One helper, so
                            // the tab and anywhere else that names a row cannot disagree.
                            text = rows.getOrNull(i)?.let { MaRows.displayName(it, i) } ?: "Row ${i + 1}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // A row that is switched off says so on its own tab, so the state is
                            // visible before it is opened. Without it, an empty-looking row and a
                            // switched-off row read identically from here.
                            color = if (rows.getOrNull(i)?.enabled == true) {
                                Color.Unspecified
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
        }

        val row = rows.getOrNull(tab) ?: MaRows.Row(emptyList(), enabled = false)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show this row",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    // The behaviour worth stating, because it is the one that surprises: rows do not
                    // hold their place. Switching row one off moves row two up into it.
                    text = "Rows that are off leave no gap. The ones below move up.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = row.enabled,
                onCheckedChange = { on ->
                    commit(rows.mapIndexed { i, r -> if (i == tab) r.copy(enabled = on) else r })
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        if (row.entries.isEmpty()) {
            Text(
                text = "No keys in this row yet. Add one below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            Text(
                text = "Hold a key and drag to move it. Untick to take it off the keyboard.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            MaReorderableColumn(
                items = row.entries,
                rowHeight = ROW_HEIGHT,
                onMove = { from, to -> rows = MaRows.move(rows, tab, from, to) },
                onSettled = { commit(rows) },
            ) { index, entry, lifted ->
                MaRowKeyItem(
                    position = index + 1,
                    entry = entry,
                    macroSlots = macroSlots,
                    lifted = lifted,
                    onToggle = {
                        commit(
                            rows.mapIndexed { i, r ->
                                if (i != tab) r else r.copy(
                                    entries = r.entries.mapIndexed { j, e ->
                                        if (j == index) e.copy(enabled = !e.enabled) else e
                                    },
                                )
                            },
                        )
                    },
                    onEditMacro = { slot -> editingMacro = slot },
                    onRemove = {
                        commit(
                            rows.mapIndexed { i, r ->
                                if (i != tab) r
                                else r.copy(entries = r.entries.filterIndexed { j, _ -> j != index })
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { adding = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 52.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add a key to row ${tab + 1}")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { commit(MaRows.defaultRows()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset all three rows") }

        Spacer(Modifier.height(24.dp))

        if (adding) {
            MaKeyPicker(
                macroSlots = macroSlots,
                onAdd = { buttons ->
                    commit(
                        rows.mapIndexed { i, r ->
                            if (i != tab) r
                            else r.copy(entries = r.entries + buttons.map { MaRows.Entry(it) })
                        },
                    )
                    adding = false
                },
                onDismiss = { adding = false },
            )
        }

        editingMacro?.let { slot ->
            MaMacroEditor(
                slot = slot,
                current = MaMacroSlots.at(macroSlots, slot),
                onSave = { label, macro ->
                    val next = macroSlots.toMutableList()
                    next[slot - 1] = MaMacroSlots.slot(label, macro)
                    scope.launch { prefs.dictate.maMacroSlots.set(MaMacroSlots.serialize(next)) }
                    editingMacro = null
                },
                onDismiss = { editingMacro = null },
            )
        }
    }
}

/** What a button is called in the editor's list. */
/**
 * The id the search memory stores, stable across renames.
 *
 * Not the label: he will rename nothing, but the app's labels have changed twice already, and a
 * memory keyed on a label would forget everything he taught it the next time a word was improved.
 */
private fun MaRows.Button.searchId(): String = when (this) {
    is MaRows.Button.Builtin -> key.id
    is MaRows.Button.Clip -> "clip:$slot"
    is MaRows.Button.Macro -> "macro:$slot"
}

/** This button as something findable: its name, its section, and whatever is written on its face. */
private fun MaRows.Button.searchEntry(macroSlots: List<MaMacroSlots.Slot>): MaKeySearch.Entry =
    MaKeySearch.Entry(
        id = searchId(),
        label = title(macroSlots),
        // The section heading goes into the description, so typing "clipboard" finds everything in
        // the clipboard section even when the word is on none of the labels.
        description = MaRows.groupOf(this).heading,
        letters = when (this) {
            is MaRows.Button.Builtin -> key.label
            is MaRows.Button.Clip -> "C$slot"
            is MaRows.Button.Macro -> "M$slot"
        },
    )

private fun MaRows.Button.title(macroSlots: List<MaMacroSlots.Slot>): String = when (this) {
    is MaRows.Button.Builtin -> key.label
    // "C1, newest copy" was left over from when these were a window onto the history. C1 has not
    // meant the newest copy since the buckets landed, and a label describing behaviour the app no
    // longer has is worse than no label: it teaches the wrong model of the only feature that matters.
    is MaRows.Button.Clip -> "Copy bucket $slot"
    is MaRows.Button.Macro -> {
        val s = MaMacroSlots.at(macroSlots, slot)
        if (s.isEmpty) "M$slot, empty" else "M$slot, ${s.label}"
    }
}

/**
 * One key in the list: number, glyph, name, tick, handle.
 *
 * Macro rows carry an extra tap target to open their editor, because a macro button is the only
 * kind with anything behind it to edit. The remove button is here rather than as a swipe: a swipe
 * on a row inside a list that also drags is the gesture collision this screen cannot afford.
 */
/**
 * `internal` so the copy row editor can draw the same rows.
 *
 * The two screens must look and behave identically — icon, tick, position, drag handle — and the
 * only way to guarantee that is for them to be the same function. A private one meant a second
 * implementation, which is a second place for them to drift apart.
 */
@Composable
internal fun MaRowKeyItem(
    position: Int,
    entry: MaRows.Entry,
    macroSlots: List<MaMacroSlots.Slot>,
    lifted: Boolean,
    /**
     * A tint for the row's own colour, or null for the theme's.
     *
     * The copy row is edited on its own screen and is a different row on the keyboard, so it gets
     * its own colour — enough to know at a glance which list is being edited, without a second
     * layout to maintain. Null keeps the feature row exactly as it was.
     */
    accent: Color? = null,
    onToggle: () -> Unit,
    onEditMacro: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(if (lifted) 8.dp else 0.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = when {
            // Lifted always wins, so the key under the finger reads as lifted whatever list it is
            // in — the drag feedback matters more than the identity of the row.
            lifted -> MaterialTheme.colorScheme.surfaceVariant
            accent != null -> accent
            else -> MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
            )
            MaButtonGlyph(entry.button, macroSlots)
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.button.title(macroSlots),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (lifted) FontWeight.SemiBold else FontWeight.Normal,
                // Greyed rather than removed, so the list keeps its shape whatever is switched off.
                color = if (entry.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            if (entry.button is MaRows.Button.Macro) {
                TextButton(onClick = { onEditMacro(entry.button.slot) }) { Text("Edit") }
            }
            Checkbox(checked = entry.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove from this row",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Hold and drag to move",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).padding(end = 4.dp),
            )
        }
    }
}

/**
 * The same glyph the key carries on the keyboard, so the list can be read against it.
 *
 * AP, AC, the zone numerals and the clipboard numbers are letters on the row rather than pictures,
 * so they are letters here. Giving them an icon that appears nowhere on the keyboard would make this
 * list something to translate rather than something to recognise.
 */
@Composable
internal fun MaButtonGlyph(
    button: MaRows.Button,
    macroSlots: List<MaMacroSlots.Slot>,
    // Internal and tintable so Settings → Key colours draws the same glyph on a painted key.
    tintOverride: Color? = null,
) {
    val tint = tintOverride ?: MaterialTheme.colorScheme.onSurface
    val size = Modifier.size(24.dp)

    @Composable
    fun letters(text: String, color: Color = tint) {
        Box(modifier = size, contentAlignment = Alignment.Center) {
            Text(text = text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }

    when (button) {
        is MaRows.Button.Clip -> letters("C${button.slot}")
        is MaRows.Button.Macro -> letters(MaMacroSlots.at(macroSlots, button.slot).label)
        is MaRows.Button.Builtin -> when (button.key) {
            MaFeatureKey.ALL_PASTE -> letters("AP")
            MaFeatureKey.ALL_CLEAR -> letters("AC")
            MaFeatureKey.CLIP_CLEAR ->
                Icon(Icons.Default.Delete, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.LANGUAGE -> letters("HR")
            MaFeatureKey.SCROLL -> letters("S")
            // Visible here although invisible on the keyboard: he has to be able to find it in the
            // list to move or remove it, and an empty row entry would be unreachable.
            MaFeatureKey.SPACER -> letters("\u2194")
            MaFeatureKey.SWITCHBOARD ->
                Icon(Icons.Default.ToggleOn, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.APP_SWITCH ->
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.NEXT_FIELD ->
                Icon(Icons.Default.KeyboardTab, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.SHIFT ->
                Icon(Icons.Default.KeyboardCapslock, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.CHANGE_CASE -> letters("Aa")
            // The same two arrows LegacyEditAction draws, since that is what the key on the row is.
            MaFeatureKey.ROW_1 -> letters("F1")
            MaFeatureKey.ROW_2 -> letters("F2")
            MaFeatureKey.ROW_3 -> letters("F3")
            MaFeatureKey.ROW_4 -> letters("F4")
            MaFeatureKey.ROW_5 -> letters("F5")
            MaFeatureKey.ROW_6 -> letters("F6")
            // The arrows the keyboard itself draws, so the picker and the key are one picture.
            MaFeatureKey.ARROW_LEFT ->
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.ARROW_RIGHT ->
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.ARROW_UP ->
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.ARROW_DOWN ->
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.SEND ->
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = tint, modifier = size)
            // The red dot, drawn rather than tinted: on this screen every other glyph takes the row's
            // tint, and a red one would look like a key in a state. It is not a state — it is what
            // the key IS, the same lamp the recording bar lights.
            MaFeatureKey.RECORD -> Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(MaRecordRed, CircleShape),
                )
            }
            // The rocker the key itself draws. The rule on this screen is that a row shows the glyph
            // the keyboard shows, and a speaker here would send him looking for a speaker there.
            MaFeatureKey.VOLUME_KEYS -> Box(
                modifier = Modifier.size(width = 14.dp, height = 22.dp)
                    .border(1.5.dp, tint, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("+", color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("\u2212", color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            MaFeatureKey.UNDO ->
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.REDO ->
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = tint, modifier = size)
            // A with the direction it works in. The old face was "A1", a leftover from the ladder
            // that counted how far up the page it had climbed — there is no ladder and no number.
            MaFeatureKey.AUTO_BUCKET -> letters("A\u2191")
            MaFeatureKey.AUTO_BUCKET_DOWN -> letters("A\u2193")
            // The row it reveals, as a word. "C" would collide with the buckets themselves.
            MaFeatureKey.BUCKET_ROW -> letters("Cs")
            // The reader row and its commands. Letters and arrows rather than icons: they are read
            // in a list here, and a column of similar transport glyphs is harder to tell apart than
            // words.
            MaFeatureKey.SUGGESTIONS -> letters("Sg")
            MaFeatureKey.READER_ROW -> letters("Rr")
            MaFeatureKey.CLOUD_LOG -> letters("Log")
            MaFeatureKey.READ_PLAY ->
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.READ_PREV ->
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.READ_NEXT ->
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.READ_STOP ->
                Icon(Icons.Default.Stop, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.READ_WATCH ->
                Icon(Icons.Default.Visibility, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.READ_SLOWER -> letters("\u2212")
            MaFeatureKey.READ_FASTER -> letters("+")
            // A red dot and the letter, the same face the keys draw. The dot says "records", the
            // letter says "in this language" — and the letter is the whole point, so it is the
            // larger of the two.
            MaFeatureKey.RECORD_HR -> letters("\u25CF H")
            MaFeatureKey.RECORD_EN -> letters("\u25CF E")
            MaFeatureKey.REPROMPT -> letters("re\u2726")
            MaFeatureKey.WHATSAPP -> letters("WA\u2726")
            MaFeatureKey.RECORD_EN_HR -> letters("\u25CF E>H")
            MaFeatureKey.RECORD_EN_NET -> letters("\u25CF E net")
            MaFeatureKey.RECORD_HR_NET -> letters("\u25CF H net")
            MaFeatureKey.RECORD_EN_LOCAL -> letters("\u25CF E local")
            MaFeatureKey.RECORD_HR_LOCAL -> letters("\u25CF H local")
            MaFeatureKey.PASTE ->
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.CUT ->
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.COPY ->
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = tint, modifier = size)
            // The same composed glyph the key draws — a clipboard wearing the history arrow. The
            // rule on this screen has always been that a row shows what the keyboard shows, and
            // this key is the one that proved the rule was only being followed where a Material
            // icon happened to exist.
            MaFeatureKey.CLIP_HISTORY ->
                MaHistoryGlyph(base = Icons.Default.ContentPaste, tint = tint)
            MaFeatureKey.DUMP ->
                Icon(Icons.Default.Layers, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.SUBTITLE -> letters("S")
            MaFeatureKey.READER ->
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.PIN ->
                Icon(Icons.Default.PushPin, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.HISTORY ->
                Icon(Icons.Default.History, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.SPACE -> letters("\u23B5")
            MaFeatureKey.SELECT_ALL ->
                Icon(Icons.Default.SelectAll, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.BACKSPACE ->
                Icon(Icons.Default.Backspace, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.MIC ->
                Icon(Icons.Default.Mic, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.SETTINGS ->
                Icon(Icons.Default.Settings, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.ENTER ->
                // AutoMirrored, not Default: Icons.Default.KeyboardReturn is the deprecated spelling
                // and a build failure. ComputingEvaluator in this repo already uses it this way,
                // which is the check that settled it rather than a guess.
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = null,
                    tint = tint,
                    modifier = size,
                )
            // Green, the colour these three wear on the keyboard when their zone is showing. Colour
            // means state everywhere in this app, so it appears here only for the keys that carry it.
            MaFeatureKey.ZONE_1 ->
                Icon(Icons.Default.Numbers, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.ZONE_2 ->
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = tint, modifier = size)
            MaFeatureKey.ZONE_3 ->
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = tint, modifier = size)
        }
    }
}

/**
 * Picking keys to add: the whole screen, three columns, ticks, and as many as you like at once.
 *
 * ### What was wrong with the version this replaces
 *
 * It was a dialog holding one narrow column of thirty-odd entries, and it could not be scrolled at
 * all: `verticalScroll()` was applied before `heightIn(max = 440.dp)`, so the content was measured
 * at the height of its own viewport and there was never anything to scroll to. Everything past the
 * first eight keys was unreachable. Modifier order is not cosmetic — the constraint has to come
 * first, then the scroll — and the failure looks exactly like a frozen screen rather than a bug.
 *
 * ### Why the whole screen
 *
 * There is no reason to add keys one at a time through a dialog occupying a third of a phone. Three
 * columns fit every key on one screen with no scrolling at all, ticks let a whole row be assembled
 * in one pass, and one Add applies the lot. Adding ten clipboard keys was ten open-pick-reopen
 * cycles; now it is ten taps and a button.
 */
@Composable
internal fun MaKeyPicker(
    macroSlots: List<MaMacroSlots.Slot>,
    onAdd: (List<MaRows.Button>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Chosen order is kept rather than catalogue order: keys arrive on the row in the order they
    // were ticked, so a row can be assembled by tapping left to right.
    val chosen = remember { mutableStateListOf<MaRows.Button>() }

    Dialog(
        onDismissRequest = onDismiss,
        // The default dialog width is a fraction of the screen. This one is the screen.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // ABOVE THE KEYBOARD.
        //
        // The moment this screen grew a search field it grew a keyboard, and the keyboard covered
        // the Add button at the bottom — so searching for a key left him with a ticked box and no
        // visible way to accept it or to leave. `imePadding` is the fix: the column ends where the
        // keyboard begins instead of underneath it.
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add keys",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    // Add, beside Cancel, so both answers live in one place and neither can be
                    // hidden by anything. It is the one reachable without dismissing the keyboard
                    // first, which is the state he is in while searching — the bottom Add was behind
                    // the keyboard the moment this screen grew a search field.
                    //
                    // It carries the count, because "Add" alone gives him no way to notice that a
                    // tick went astray two columns from where he was looking.
                    TextButton(
                        onClick = { onAdd(chosen.toList()) },
                        enabled = chosen.isNotEmpty(),
                    ) {
                        Text(if (chosen.isEmpty()) "Add" else "Add ${chosen.size}")
                    }
                }
                Text(
                    text = "Tick as many as you like, then add them all at once.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                // ---------------------------------------------------------------- the search
                //
                // Forty-six keys is past the number anybody reads. Typing what he wants is faster
                // than finding it in nine sections, and it does not have to be the app's word for
                // it — see MaKeySearch, which matches literally first, then on what he has meant
                // before, and only then asks a model.
                val prefs by FlorisPreferenceStore
                val scope = rememberCoroutineScope()
                var query by remember { mutableStateOf("") }
                val memory by prefs.dictate.maKeySearchMemory.collectAsState()
                var suggestion by remember { mutableStateOf<MaKeySearch.Entry?>(null) }
                var asking by remember { mutableStateOf(false) }

                val allEntries = remember(macroSlots) {
                    MaRows.catalogue().associateBy({ it.searchEntry(macroSlots) }, { it })
                }
                val result = remember(query, memory, allEntries) {
                    MaKeySearch.resolve(query, allEntries.keys.toList(), memory)
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; suggestion = null },
                    singleLine = true,
                    label = { Text("Find a key") },
                    placeholder = { Text("what it does, in your words") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )

                // The model is asked only when both offline layers are empty, only after he has
                // stopped typing, and only for a query long enough to mean something. Asking on
                // every keystroke would spend his credit on the way to a word that was going to
                // match anyway.
                LaunchedEffect(query, result.aiWanted) {
                    suggestion = null
                    if (!result.aiWanted || query.trim().length < 3) return@LaunchedEffect
                    delay(700L)
                    asking = true
                    val reply = DictateController.askCheapModel(
                        MaKeySearch.prompt(query, allEntries.keys.toList()),
                    )
                    asking = false
                    suggestion = reply?.let { MaKeySearch.readAnswer(it, allEntries.keys.toList()) }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        // weight first, then the scroll. The bug this screen replaces was these two
                        // the other way round.
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                ) {
                    if (result.entries.isEmpty()) {
                        Text(
                            text = when {
                                asking -> "Nothing matched \u2014 asking\u2026"
                                suggestion != null -> "Nothing matched. Did you mean:"
                                else -> "Nothing matched."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    // Grouped by what a key is FOR, and the grouping lives in the model rather than
                    // here.
                    //
                    // This was three headings, and the third was "Keys" — twenty-six unrelated
                    // things in whatever order the enum happened to declare them, which is to say
                    // in the order they were written over eight months. A heading is a promise
                    // about what is underneath it, and that one promised nothing.
                    //
                    // Nine sections now, from `MaFeatureGroup`, and a key cannot be added to the app
                    // without saying which it belongs to: the `when` that answers is exhaustive, so
                    // the compiler asks the question at the moment somebody knows the answer. That
                    // is the whole reason it is not a `when` written on this screen.
                    // The suggestion, when the model named one. Marked (AI) because it is a GUESS,
                    // and a guess that looked like a match would teach him to trust the next one
                    // the same way. It is added to the list rather than replacing it, and picking
                    // it teaches the memory exactly as any other pick does — which is how the third
                    // layer makes itself unnecessary.
                    val aiHit = suggestion
                    val sections = if (MaKeySearch.fold(query).isBlank()) {
                        MaRows.catalogueGrouped()
                    } else {
                        val wanted = (result.entries + listOfNotNull(aiHit)).toSet()
                        MaRows.catalogueGrouped()
                            .map { (group, buttons) ->
                                group to buttons.filter { b -> b.searchEntry(macroSlots) in wanted }
                            }
                            .filter { it.second.isNotEmpty() }
                    }
                    sections.forEach { (group, buttons) ->
                        Text(
                            text = group.heading,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                        )
                        // Three per row, laid out by hand rather than with a lazy grid: this is a
                        // fixed, short list inside something that already scrolls, and nesting a
                        // lazy grid in a scrolling column is the arrangement that throws.
                        buttons.chunked(3).forEach { triple ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                triple.forEach { button ->
                                    val ticked = button in chosen
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp)
                                            .clickable {
                                                if (ticked) {
                                                    chosen.remove(button)
                                                } else {
                                                    chosen.add(button)
                                                    // Learned on the pick, not on the Add, because
                                                    // the query is what was on screen at the moment
                                                    // he recognised the key. By the time Add is
                                                    // pressed he may have searched three more
                                                    // times.
                                                    val q = query
                                                    if (q.isNotBlank()) {
                                                        scope.launch {
                                                            prefs.dictate.maKeySearchMemory.set(
                                                                MaKeySearch.learn(
                                                                    memory, q, button.searchId(),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = ticked, onCheckedChange = null)
                                        // The key's own glyph, the same one the row draws and the
                                        // same one the list beside it draws. Picking a key from a
                                        // column of words means matching a name to something seen
                                        // on the keyboard; picking it from its own face means
                                        // recognising it. That is the difference between reading
                                        // the list and scanning it, which on this screen is the
                                        // whole point.
                                        MaButtonGlyph(button, macroSlots)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = button.title(macroSlots) +
                                                if (aiHit != null && button.searchEntry(macroSlots) == aiHit) {
                                                    " (AI)"
                                                } else {
                                                    ""
                                                },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                // Keeps the last, short row aligned in three columns instead of
                                // letting one or two entries stretch across the width.
                                repeat(3 - triple.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                HorizontalDivider()
                Button(
                    onClick = { onAdd(chosen.toList()) },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        if (chosen.isEmpty()) "Add" else "Add ${chosen.size}",
                    )
                }
            }
        }
    }
}

/**
 * Writes what one macro button does.
 *
 * The palette is the reason this is usable. MaMacroSyntax accepts a generous range of names, which
 * is right for reading a macro and useless for writing one, and `{Ctrl+Shift+Z}` cannot be dictated
 * — spoken into a field it arrives as prose. So the commands are picked from a grouped list, and
 * picking one fills the label in too when it is still blank, so the common case is one tap.
 */
@Composable
private fun MaMacroEditor(
    slot: Int,
    current: MaMacroSlots.Slot,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(current.label) }
    // A TextFieldValue rather than a String, so the cursor can be placed after an insertion.
    //
    // With a plain String, Compose resets the selection to the start on every value change from
    // outside the field. Picking a command therefore dropped the caret in front of everything, and
    // the next command landed before the previous one — commands came out backwards, which is
    // exactly what Marko saw.
    var macro by remember {
        mutableStateOf(TextFieldValue(current.macro, TextRange(current.macro.length)))
    }
    var paletteOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macro M$slot") },
        text = {
            Column(
                modifier = Modifier
                    // Constraint first, then the scroll. The other way round measures the content
                    // at the height of its own viewport, so there is never anything to scroll to
                    // and the screen simply does not move under the finger. That is the bug that
                    // made the key picker unusable, and it was here too.
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = label,
                    // Truncated as it is typed rather than refused at the end, so the limit is
                    // discovered by the field simply stopping instead of by a complaint afterwards.
                    onValueChange = { label = it.take(MaMacroSlots.MAX_LABEL) },
                    label = { Text("What the key says (${MaMacroSlots.MAX_LABEL} characters)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = macro,
                    onValueChange = { macro = it },
                    label = { Text("What it does") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Plain text types itself. Anything in braces is a real key press.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { paletteOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Pick a command") }
                DropdownMenu(expanded = paletteOpen, onDismissRequest = { paletteOpen = false }) {
                    MaCommandPalette.GROUPS.forEach { group ->
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        group.entries.forEach { e ->
                            DropdownMenuItem(
                                text = { Text("${e.label}   ${e.description}") },
                                onClick = {
                                    // Appended rather than replacing: a macro is usually a short
                                    // sequence rather than a single key.
                                    // One command per line, and the caret left after it.
                                    //
                                    // A newline because a macro of several commands is a list of
                                    // steps and reads like one; running ignores the whitespace, so
                                    // the layout is free. The caret goes to the end so the next
                                    // pick lands after this one rather than in front of it.
                                    val existing = macro.text
                                    val joined = when {
                                        existing.isBlank() -> e.token
                                        existing.endsWith("\n") -> existing + e.token
                                        else -> existing + "\n" + e.token
                                    }
                                    macro = TextFieldValue(joined, TextRange(joined.length))
                                    if (label.isBlank() || label == "M$slot") {
                                        label = e.label.take(MaMacroSlots.MAX_LABEL)
                                    }
                                    paletteOpen = false
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // A blank label is filled back in with the slot's own name rather than left empty,
                // so an unconfigured key still says which slot it is instead of showing nothing.
                onClick = { onSave(label.ifBlank { "M$slot" }, macro.text) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
