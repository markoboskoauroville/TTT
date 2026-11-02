/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

/**
 * The feature rows: three of them, each a preset, each switchable on its own.
 *
 * This is the app's main feature and its model is deliberately flat. Everything that can sit on a
 * row is a button of equal standing — the app's own keys, the ten clipboard slots C1 to C10, and the
 * ten macro slots M1 to M10. Nothing nests inside anything else and nothing is protected.
 *
 * ### What replaced what, and why
 *
 * An earlier design made the clipboard a child row of the feature row that expanded and collapsed.
 * That is gone. Clipboard slots are now simply buttons, so they can be put anywhere, mixed with
 * anything, in any of the three rows, and a row of five clipboard keys beside a backspace is as
 * valid an arrangement as any other. The nesting bought nothing and cost the freedom to arrange.
 *
 * ### Rows collapse upward
 *
 * Three rows exist always. Switching one off does not leave a gap: the rows that remain move up and
 * take the space, so with only row two enabled it is drawn where row one would have been. The order
 * among the survivors never changes, which is what makes the arrangement predictable — switching row
 * one off must not reshuffle two and three.
 *
 * ### Nothing is protected, except the way back
 *
 * There is no locked key. Every button, including backspace and enter, can be unticked. The single
 * guarantee is that if the user switches off everything, in every row, the settings key appears on
 * its own — because a keyboard with no keys at all and no route to the settings that would restore
 * them is a keyboard that can only be fixed by uninstalling the app. That is a floor, not a lock:
 * the settings key is an ordinary button the rest of the time and can be placed, moved and unticked
 * like any other.
 */
object MaRows {

    private const val ROW_SEP = '\u001E'
    private const val BTN_SEP = '\u001F'
    private const val FIELD_SEP = '\u001D'
    private const val ROW_META_SEP = '\u001C'

    private const val T_BUILTIN = "b"
    private const val T_CLIP = "c"
    private const val T_MACRO = "m"

    /** Three rows, always. They are presets to switch between, not a list to grow. */
    /**
     * SIX ROWS, up from three.
     *
     * The upgrade is free and that is not luck — `parse` has always padded a short read to exactly
     * ROW_COUNT with empty, disabled rows. His three arranged rows come back as rows one to three
     * and rows four to six arrive empty and switched off, so nothing he built moves and nothing new
     * appears on the keyboard until he puts something on it.
     *
     * Going the other way would not be free: a stored six-row arrangement read by a three-row build
     * silently loses rows four to six, because `parse` truncates as well as pads. That is the
     * rollback clause of the delivery gate, and it is why this note exists — **if this number is
     * ever lowered, the rows beyond it are deleted from his preference the first time the app
     * writes.**
     */
    const val ROW_COUNT = 6

    /**
     * The copy row: a fourth row that belongs to the transcription view alone.
     *
     * It is the same machinery as the feature rows — same entries, same catalogue, same editor —
     * and stored separately because it is not one of the three presets. **It appears only in the
     * transcription view**, where there are no letter keys and the clipboard is the whole job, and
     * never on the typing keyboard where the feature rows already live.
     *
     * Its own preference rather than a fourth slot in `maRows`, because `parse` pads and truncates
     * to exactly ROW_COUNT — a fourth row there is silently dropped, and the source would look
     * right while the keyboard never showed it.
     */
    fun defaultCopyRow(): Row = Row(
        listOf(
            MaFeatureKey.SELECT_ALL,
            MaFeatureKey.PASTE,
            // COPY, sixth in the staging row he photographed, and it belongs here rather than
            // anywhere else: select-all, paste, copy and cut are one operation in four directions
            // and having three of them together was the odd arrangement, not this.
            MaFeatureKey.COPY,
            MaFeatureKey.CUT,
            MaFeatureKey.CLIP_HISTORY,
            // HISTORY — the transcription history — is off this row, at his request.
            //
            // It sat between clipboard history and AP and belonged to neither side: everything else
            // here acts on what is on the clipboard RIGHT NOW, and that one opened a list of things
            // he had dictated. Two ideas of "history" a key apart, and the one that did not fit was
            // the one that got pressed by mistake.
            //
            // Still in the catalogue, under Dictation, where somebody looking for it will look.
            MaFeatureKey.ALL_PASTE,
            MaFeatureKey.ALL_CLEAR,
            // PIN, last, after AC.
            //
            // It is on this row for a reason that has nothing to do with the clipboard and
            // everything to do with using it: **selecting text makes Android collapse the
            // keyboard**, and the pin is what keeps it up. So the key he needs before he can press
            // any of the others is on the same row as the others, rather than one row away on a row
            // that has just been hidden.
            MaFeatureKey.PIN,
        ).map { Entry(Button.Builtin(it)) },
        enabled = true,
    )

    /**
     * The bucket row: the buckets and the two keys that fill and empty them.
     *
     * A special row like the copy row and the number row — its own preference, its own switch, its
     * own arrangement — rather than one of the six ordinary rows. He asked for it in those words,
     * and the reason it is worth a special row is that it is a MODE rather than a set of keys: when
     * he is collecting code blocks he wants these six and nothing else, and when he is not he wants
     * the space back.
     *
     * The default is what he had on screen: bin, C1, C2, C3, the upward A, and the swap key.
     */
    fun defaultBucketRow(): Row = Row(
        listOf(
            MaFeatureKey.CLIP_CLEAR,
        ).map { Entry(Button.Builtin(it)) } +
            (1..3).map { Entry(Button.Clip(it)) } +
            listOf(MaFeatureKey.AUTO_BUCKET, MaFeatureKey.APP_SWITCH).map { Entry(Button.Builtin(it)) },
        enabled = true,
    )

    /**
     * The reader row: everything reading needs, in the order he does it.
     *
     * play/pause · previous · next · stop · watch · slower · faster
     *
     * Transport first because that is what a hand reaches for mid-listening, and the two settings
     * last because they are decided once and then left. The stop sits between them: it ends both the
     * reading and the wait, and it belongs beside the keys that started them rather than at the far
     * end where a thumb arrives by accident.
     */
    fun defaultReaderRow(): Row = Row(
        listOf(
            MaFeatureKey.READ_PLAY,
            MaFeatureKey.READ_PREV,
            MaFeatureKey.READ_NEXT,
            MaFeatureKey.READ_STOP,
            MaFeatureKey.READ_WATCH,
            MaFeatureKey.READ_SLOWER,
            MaFeatureKey.READ_FASTER,
        ).map { Entry(Button.Builtin(it)) },
        enabled = true,
    )

    fun parseReaderRow(raw: String): Row =
        if (raw.isBlank()) defaultReaderRow() else parse(raw).firstOrNull() ?: defaultReaderRow()

    fun serializeReaderRow(row: Row): String = serialize(listOf(row))

    fun parseBucketRow(raw: String): Row =
        if (raw.isBlank()) defaultBucketRow() else parse(raw).firstOrNull() ?: defaultBucketRow()

    fun serializeBucketRow(row: Row): String = serialize(listOf(row))

    fun parseCopyRow(raw: String): Row =
        if (raw.isBlank()) defaultCopyRow() else parse(raw).firstOrNull() ?: defaultCopyRow()

    fun serializeCopyRow(row: Row): String = serialize(listOf(row))

    /** C1 to C10: the last ten things copied, newest first. */
    const val CLIP_SLOTS = 10

    /** M1 to M10: the ten buttons the user writes himself. */
    const val MACRO_SLOTS = 10

    /**
     * Anything that can sit on a row. Three kinds, all of equal standing.
     */
    sealed interface Button {

        /** One of the app's own keys: record, backspace, AP, AC, a zone toggle, the gear. */
        data class Builtin(val key: MaFeatureKey) : Button

        /**
         * A clipboard slot, 1 to [CLIP_SLOTS]. C1 is the last thing copied.
         *
         * The slot number is stored, not the text. The clipboard changes constantly and a button
         * that captured its contents when it was placed would paste whatever happened to be copied
         * on the day the row was arranged.
         */
        data class Clip(val slot: Int) : Button

        /**
         * A macro slot, 1 to [MACRO_SLOTS]. What M4 does is stored in [MaMacroSlots], not here.
         *
         * The reference rather than the content: the same macro can then appear in two rows without
         * being written twice, and editing it in one place changes both. It also means moving a
         * button around cannot lose the macro attached to it.
         */
        data class Macro(val slot: Int) : Button
    }

    /**
     * One button on a row, and whether its tick is on.
     *
     * Unticked buttons stay in the list rather than being deleted. A list whose entries disappear
     * when switched off cannot be learned by position, and this is a list navigated by position, by
     * somebody who reads it with difficulty. Switching a button back on has to put it back where it
     * was, not at the end.
     */
    data class Entry(val button: Button, val enabled: Boolean = true)

    /** One of the three rows: its buttons in order, and whether the row itself is switched on. */
    /**
     * [name] is his word for this row — "bucket row", "keyboard row" — or blank for "Row 3".
     *
     * Blank rather than a pre-filled "Row 3", so the editor can tell a row he has named from one he
     * has not and fall back without having to guess whether "Row 3" was chosen or inherited.
     */
    data class Row(
        val entries: List<Entry>,
        val enabled: Boolean = true,
        val name: String = "",
    ) {
        val visibleButtons: List<Button> get() = entries.filter { it.enabled }.map { it.button }
    }

    /**
     * What the keyboard actually draws.
     *
     * Rows that are off are absent rather than empty, so the ones below move up and the keyboard is
     * genuinely shorter. A row that is on but has nothing ticked is dropped too — an empty strip is
     * wasted height, and the row is still there in the editor to be filled in again.
     *
     * The floor: when that leaves nothing at all, the settings key is drawn alone. Without it the
     * user would be looking at a keyboard with no keys and no way to reach the screen that puts them
     * back.
     */
    /**
     * Swaps two rows whole — their keys, their arrangement and their on/off state.
     *
     * ### Swap, never insert
     *
     * He asked for it as swapping and that is the right shape: **three rows exist, always, and a
     * move that shuffled the others along would renumber a row he did not touch.** Row 1 is the one
     * his thumb reaches without moving; if arranging row 3 could silently make row 2 into row 1, the
     * arrangement he built by muscle memory would move under him.
     *
     * A swap changes exactly two things and leaves the third alone. Whatever was on row 2 is now on
     * row 1 and whatever was on row 1 is now on row 2, and row 3 is where it was.
     *
     * The `enabled` flag travels with the row, because it belongs to the KEYS rather than to the
     * position: he switched off a row because he did not want those keys today, not because he did
     * not want a row in that place.
     */
    fun swapRows(rows: List<Row>, a: Int, b: Int): List<Row> {
        if (a == b) return rows
        if (a !in rows.indices || b !in rows.indices) return rows
        val out = rows.toMutableList()
        val held = out[a]
        out[a] = out[b]
        out[b] = held
        return out
    }

    /**
     * Moves a row to another position, sliding everything between it and there along by one.
     *
     * ### A move, where `swapRows` was a swap, and that is a real change
     *
     * The buttons this replaces said "Row 2 becomes Row 1", and for a BUTTON a swap is right: the
     * sentence names two rows and touches two rows, and an insert that silently renumbered a third
     * would be a surprise.
     *
     * A DRAG says something different. Dragging the third tab to the front depicts sliding it in
     * front of the others, and every list on the phone that can be dragged behaves that way. **The
     * gesture is the specification.** A drag that swapped instead would leave the row he dragged
     * past sitting where he dragged it from, which is not what his finger drew.
     *
     * So the semantics follow the gesture rather than the model, and `swapRows` stays where it is:
     * still correct, still tested, no longer called from the editor.
     *
     * `MaFeatureOrder.move` does exactly this for keys within a row, and the reasoning there is the
     * same one written out — a move, not a swap, because that is what the eye expects from watching
     * a drag.
     */
    fun moveRow(rows: List<Row>, from: Int, to: Int): List<Row> {
        if (from !in rows.indices) return rows
        val target = to.coerceIn(0, rows.size - 1)
        if (from == target) return rows
        val out = rows.toMutableList()
        out.add(target, out.removeAt(from))
        return out
    }

    /** Turns one row on or off, leaving everything else as it was. */
    fun setRowEnabled(rows: List<Row>, index: Int, enabled: Boolean): List<Row> {
        if (index !in rows.indices) return rows
        return rows.mapIndexed { i, row -> if (i == index) row.copy(enabled = enabled) else row }
    }

    fun visibleRows(rows: List<Row>): List<List<Button>> {
        val drawn = rows.filter { it.enabled }
            .map { it.visibleButtons }
            .filter { it.isNotEmpty() }
        // Reversed, so row 1 is the one nearest the keys.
        //
        // The rows are numbered by how close they sit to the typing, not by how far down the editor
        // they appear. Row 1 is the one his thumb reaches without moving, which is why it is the
        // one he fills first, and it has to stay in that place as rows above it come and go. With
        // 1 and 3 on, 1 is at the bottom and 3 above it; with all three, 1, 2, 3 from the bottom up.
        //
        // Drawn last means drawn lowest in a Column, so the list is reversed here rather than at the
        // point of drawing — one place, and it cannot disagree with itself.
        return drawn.reversed().ifEmpty {
            listOf(listOf(Button.Builtin(MaFeatureKey.SETTINGS)))
        }
    }

    /**
     * Which copy buckets are actually on the keyboard, as slot numbers.
     *
     * The buckets a copy may fill are exactly the ones the user can see and press. A bucket that is
     * not on any row cannot be pasted from, so filling it silently swallows a copy: the text is
     * captured, the row looks unchanged, and the only way to reach it would be to go into the editor
     * and add the button afterwards. That is not a bucket, it is a hole.
     *
     * Returned as the set of numbers rather than a count, because the buttons need not be
     * contiguous. Somebody with C1, C2 and C7 on the row has three buckets and they are 1, 2 and 7 —
     * a capacity of three would fill C1, C2 and C3, and C3 is not on the screen.
     */
    fun visibleClipSlots(rows: List<Row>): Set<Int> =
        visibleRows(rows).flatten().filterIsInstance<Button.Clip>().map { it.slot }.toSet()

    /**
     * The starting arrangement: one full row, two empty rows waiting to be built.
     *
     * Rows two and three start switched off and empty rather than pre-filled with a guess. A row
     * arriving full of keys somebody did not choose has to be emptied before it can be used, which
     * is more work than filling an empty one.
     */
    fun defaultRows(): List<Row> = listOf(
        // The row Marko actually settled on, shipped as it stands.
        //
        // It is six keys rather than eleven, and that is the finding rather than a compromise: after
        // weeks of arranging, what he kept was the gear, the keyboard zone, history, the dictation
        // view, AC, and the magic row above it. Everything else he took off. Shipping the row he
        // built beats shipping the row I guessed at, and a new install starts where he finished
        // instead of where I started.
        //
        // Row one is nearest the keys, so this is the row under his thumb.
        Row(
            listOf(
                MaFeatureKey.SETTINGS,
                MaFeatureKey.ZONE_2,
                MaFeatureKey.HISTORY,
                MaFeatureKey.MIC,
                MaFeatureKey.ALL_CLEAR,
            ).map { Entry(Button.Builtin(it)) },
            enabled = true,
        ),
        // Row two: the copy row, in the order he asked for.
        //
        // Select all, paste, cut, clipboard history, dictation history, AP, AC. It is the row he
        // had been arranging by hand in the Smartbar, which could never hold AP and AC because
        // those are not Smartbar actions — the four clipboard keys were moved into the feature row
        // vocabulary precisely so this row could exist in one place and be edited like any other.
        //
        // COPY is deliberately absent: he listed seven keys and copy was not among them. It is one
        // tick away in the catalogue if that was an oversight rather than a decision.
        Row(
            listOf(
                MaFeatureKey.SELECT_ALL,
                MaFeatureKey.PASTE,
                MaFeatureKey.CUT,
                MaFeatureKey.CLIP_HISTORY,
                MaFeatureKey.HISTORY,
                MaFeatureKey.ALL_PASTE,
                MaFeatureKey.ALL_CLEAR,
            ).map { Entry(Button.Builtin(it)) },
            enabled = true,
        ),
        // Three starts empty and off. A row arriving full of keys nobody chose has to be emptied
        // before it can be used, which is more work than filling an empty one.
        //
        // Only one empty row now, because the copy row above took the second slot and ROW_COUNT is
        // three. A fourth Row here would be silently dropped by `parse`, which pads or truncates to
        // exactly three — the list would look right in this file and never reach the keyboard.
        Row(emptyList(), enabled = false),
    )

    /** Every button that can be added, in the order the editor offers them. */
    fun catalogue(): List<Button> =
        // No filtering needed: the clipboard is no longer a set of keys in this enum, it is
        // Button.Clip. The enum now holds only the app's own keys, so every one of them is offered.
        MaFeatureKey.entries.map { Button.Builtin(it) } +
            (1..CLIP_SLOTS).map { Button.Clip(it) } +
            (1..MACRO_SLOTS).map { Button.Macro(it) }

    /** Which section of the picker a button belongs to. */
    fun groupOf(button: Button): MaFeatureGroup = when (button) {
        is Button.Builtin -> button.key.group
        // The buckets are the section, so the buckets are in it.
        is Button.Clip -> MaFeatureGroup.BUCKETS
        is Button.Macro -> MaFeatureGroup.MACROS
    }

    /**
     * The catalogue as sections, in the order they are read down the screen.
     *
     * Empty sections are left out rather than drawn as a heading with nothing under it, which is
     * what would happen to Macros if the slots ever stopped being offered.
     *
     * **Within the buckets, the order is the lifecycle**: A fills them, C1 to C10 hold, the bin
     * empties. Catalogue order would have put A and the bin together above all ten, since both are
     * Builtins and the Clips come after — an accident of the model showing through as an
     * arrangement, which is exactly what a grouped list is supposed to stop.
     */
    fun catalogueGrouped(): List<Pair<MaFeatureGroup, List<Button>>> {
        val all = catalogue()
        return MaFeatureGroup.entries.mapNotNull { group ->
            val buttons = all.filter { groupOf(it) == group }.sortedBy { bucketOrder(it) }
            if (buttons.isEmpty()) null else group to buttons
        }
    }

    /** A fills, the buckets hold, the bin empties. Everything else keeps catalogue order. */
    private fun bucketOrder(button: Button): Int = when {
        // The two A keys sit together at the head of the section, up before down.
        button is Button.Builtin && button.key == MaFeatureKey.AUTO_BUCKET -> -1
        button is Button.Builtin && button.key == MaFeatureKey.AUTO_BUCKET_DOWN -> 0
        button is Button.Clip -> button.slot
        button is Button.Builtin && button.key == MaFeatureKey.CLIP_CLEAR -> CLIP_SLOTS + 1
        else -> 0
    }

    /** The character that separates the enabled flag from the name inside the META field. */
    private const val NAME_SEP = '~'

    /**
     * A name that cannot damage the store.
     *
     * Every separator is stripped rather than escaped. Escaping means an unescaper, and an
     * unescaper is a second thing that can be wrong about a string read while the keyboard opens.
     * He is naming a row, not writing a document — losing a tilde from "bucket~row" costs nothing
     * and cannot corrupt the row after it.
     *
     * Capped at 24 characters: longer than any name that fits on a tab, short enough that a paste
     * accident cannot fill the preference.
     */
    fun sanitiseName(name: String): String =
        name.filter { it != ROW_SEP && it != BTN_SEP && it != FIELD_SEP && it != ROW_META_SEP && it != NAME_SEP }
            .trim().take(24)

    /** His name for the row, or "Row 3" when he has not given one. */
    fun displayName(row: Row, index: Int): String =
        row.name.ifBlank { "Row ${index + 1}" }

    /**
     * [rows] with [key] placed right after the first of [after] found in each row (the first of
     * [after] that the row holds), unless the row already has [key]. Rows holding none are unchanged.
     */
    fun withKeyAfter(rows: List<Row>, key: MaFeatureKey, after: List<MaFeatureKey>): List<Row> = rows.map { row ->
        if (row.entries.any { it.button == Button.Builtin(key) }) return@map row
        val anchor = after.firstOrNull { a -> row.entries.any { it.button == Button.Builtin(a) } } ?: return@map row
        val at = row.entries.indexOfFirst { it.button == Button.Builtin(anchor) }
        row.copy(entries = row.entries.toMutableList().apply { add(at + 1, Entry(Button.Builtin(key))) })
    }

    fun serialize(rows: List<Row>): String =
        rows.joinToString(ROW_SEP.toString()) { row ->
            val flag = if (row.enabled) "1" else "0"
            val body = row.entries.joinToString(BTN_SEP.toString()) { entry ->
                val on = if (entry.enabled) "1" else "0"
                when (val b = entry.button) {
                    is Button.Builtin -> "$T_BUILTIN$FIELD_SEP${b.key.id}$FIELD_SEP$on"
                    is Button.Clip -> "$T_CLIP$FIELD_SEP${b.slot}$FIELD_SEP$on"
                    is Button.Macro -> "$T_MACRO$FIELD_SEP${b.slot}$FIELD_SEP$on"
                }
            }
            // The name rides in the META field beside the enabled flag, as `1~bucket row`.
            //
            // A NEW FIELD IN AN OLD SLOT, on purpose. Appending a fourth separator would have made
            // every stored arrangement written before today unparseable by the new code or the new
            // one unparseable by the old — and `parse` runs while the keyboard is opening, in front
            // of whatever he was about to type. Here, an old string has no `~` and reads exactly as
            // it did; a new one has a name the old code would ignore.
            //
            // Separators are stripped from the name on the way in, so a row called "a|b" cannot
            // corrupt the row after it.
            val meta = if (row.name.isBlank()) flag else "$flag$NAME_SEP${sanitiseName(row.name)}"
            "$meta$ROW_META_SEP$body"
        }

    /**
     * Parses the stored string, dropping anything malformed rather than throwing.
     *
     * Read while the keyboard is opening, in front of whatever the user was about to type into. A
     * damaged preference has to cost an arrangement, never a keyboard that refuses to draw: there is
     * no route to the settings app from behind a keyboard that never appears.
     *
     * Always returns exactly [ROW_COUNT] rows, padding with empty ones. The editor has three tabs and
     * indexes into this list; a short read would be a missing tab or a crash.
     */
    fun parse(raw: String): List<Row> {
        val parsed = if (raw.isBlank()) emptyList() else {
            raw.split(ROW_SEP).map { rowText ->
                val metaIdx = rowText.indexOf(ROW_META_SEP)
                val meta = if (metaIdx > 0) rowText.substring(0, metaIdx) else ""
                val enabled = metaIdx <= 0 || meta.substringBefore(NAME_SEP) == "1"
                // No NAME_SEP means a string written before names existed: no name, and everything
                // else read exactly as it always was.
                val name = if (NAME_SEP in meta) meta.substringAfter(NAME_SEP) else ""
                val body = if (metaIdx >= 0) rowText.substring(metaIdx + 1) else rowText
                val entries = if (body.isBlank()) emptyList() else {
                    body.split(BTN_SEP).mapNotNull { parseEntry(it) }
                }
                Row(entries, enabled, name)
            }
        }
        return (0 until ROW_COUNT).map { i ->
            parsed.getOrNull(i) ?: Row(emptyList(), enabled = false)
        }
    }

    private fun parseEntry(raw: String): Entry? {
        val f = raw.split(FIELD_SEP)
        if (f.size != 3) return null
        val enabled = f[2] == "1"
        val button = when (f[0]) {
            // A key that no longer exists is dropped rather than guessed at. Keys have been removed
            // from this app before — the reader's book key went with the reader — and a row stored
            // before that is otherwise still perfectly good.
            T_BUILTIN -> MaFeatureKey.byId(f[1])?.let { Button.Builtin(it) }
            T_CLIP -> f[1].toIntOrNull()?.takeIf { it in 1..CLIP_SLOTS }?.let { Button.Clip(it) }
            T_MACRO -> f[1].toIntOrNull()?.takeIf { it in 1..MACRO_SLOTS }?.let { Button.Macro(it) }
            else -> null
        } ?: return null
        return Entry(button, enabled)
    }

    fun defaultSerialized(): String = serialize(defaultRows())

    /** Moves a button within its row. Out-of-range indices are ignored rather than throwing. */
    fun move(rows: List<Row>, rowIndex: Int, from: Int, to: Int): List<Row> {
        val row = rows.getOrNull(rowIndex) ?: return rows
        if (from !in row.entries.indices || to !in row.entries.indices) return rows
        val moved = row.entries.toMutableList().apply { add(to, removeAt(from)) }
        return rows.mapIndexed { i, r -> if (i == rowIndex) r.copy(entries = moved) else r }
    }
}
