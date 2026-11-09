/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.app.settings

/**
 * The Mantra settings list, as data, so its order belongs to the person using it.
 *
 * Same argument as the feature row and the same shape of solution. The order of a list of links is
 * not a thing the person who wrote the list can know: it depends entirely on which four of them
 * somebody opens every week and which four they have opened twice ever.
 *
 * **The group headings are gone with it, and that is the real change.** "Start here", "Dictation",
 * "Saved", "Keyboard extras" were a filing system, and a filing system contradicts a free order: as
 * soon as History can sit above Recording, a heading saying *Saved* is either a lie or a cage. A flat
 * list that the user arranges is the honest version, and on a phone it is also two screens shorter.
 *
 * **Order only, nothing hidden.** As with the feature row: rearranging cannot lose anything, and for
 * a settings list the stakes are the same in kind, since API keys is on it and a keyboard with no
 * reachable key screen is a keyboard that cannot be fixed from inside itself.
 */
enum class MaSettingsEntry(val id: String, val title: String, val summary: String?) {
    // Keys first in the NAME too, because the name is the thing he scans for and the keys are what
    // he comes here to do. The permissions are the once-ever half.
    PERMISSIONS("permissions", "API keys and permissions", "Everything the keyboard needs to work"),
    // Marko, 7.11.2026: "Transcription model is the settings entry." The on-device engine is Parakeet.
    TRANSCRIPTION_MODEL("transcription_model", "Transcription model", "AssemblyAI, Parakeet on the phone, or Parakeet as fallback"),
    // Engines (Marko, 9.11.2026): the free engines, their order per language, quotas, keys and the benchmark.
    ENGINES("engines", "Engines", "Fallback between free engines, quotas, keys, and Benchmark voice engines"),
    SWITCHBOARD("switchboard", "Switchboard", "Every row the keyboard can show, in one place"),
    FEATURE_ROW("feature_row", "Feature row", "Three rows of keys, drag them into the order you want"),
    MAGIC("magic", "Magic finger", "What it presses on screen, and which it tries first"),
    // RE Magic (Marko, 9.11.2026): the skill RE writes by, its editor, and what it learns. Wears the key's own icon.
    RE_MAGIC("re_magic", "RE Magic", "The skill RE writes by, its editor, and what it learns from your corrections"),
    // Spelling (Marko, 9.11.2026): the misheard names and what they become, each pair its own switch.
    SPELLING("spelling", "Spelling", "Names your dictation gets wrong, fixed automatically, each pair on or off"),
    VOICE_COMMANDS("voice_commands", "Voice commands", "Words that press instead of being typed"),
    SHORTCUTS("shortcuts", "Keyboard shortcuts", "Ctrl and a letter, including Ctrl+P to proofread"),
    PROMPTS("prompts", "Prompts", "Ctrl+P, Ctrl+F, E>H and the Croatian spell check: wording and model"),
    // Beside Prompts: what those prompts cost.
    CLAUDE_COSTS("claude_costs", "Claude costs", "What each request cost, and an estimate of what is left"),
    // Beside them, once: the fixes learned from his corrections. Inside Auto prediction since
    // 27.10.2026; kept so a stored order holding "corrections" puts Auto prediction in its place.
    CORRECTIONS("corrections", "My corrections", "Words you fix, fixed for you next time"),
    VOICE_FORMAT("voice_format", "Voice formatting", "Marks you can speak, and how to say them"),
    READER("reader", "Reader", "The voice that reads the screen aloud"),
    // Beside the reader, because it is the reader's memory. A chat he listened to and can go back to.
    // The number row's second characters. Its editor has existed for weeks inside
    // DictateLayoutScreen, which has no entry here — so the only way to reach it was a deep link
    // nobody types, and he asked for it as a new feature because from where he sits it did not exist.
    NUMBER_ROW("number_row", "Number row", "What each digit carries on a long press"),
    // Beside the number row: the colours are most of all for telling its macro digits apart.
    // Renamed from "Key colours" (Marko, 22.10.2026), beside its twin that reuses its grid.
    KEY_COLORS("key_colors", "Coloring the Buttons", "Every key on one grid, each in its own colour"),
    POPUPS("popups", "Pop-up Configuration", "What a long press on each letter offers"),
    CLOUD_READER("cloud_reader", "Claude.ai reader", "Chats it read, kept so you can read them again"),
    COPY_ROW("copy_row", "Copy row", "The clipboard row, shown in the transcription view"),
    PROFILES("profiles", "Profiles", "Whole configurations, saved by name and swapped in one tap"),
    /**
     * Gone from the list; it lives inside PERMISSIONS now.
     *
     * Kept as an entry so a stored order containing "keys" still parses — `byId` returning null for
     * it would be harmless, but leaving the id known means an old arrangement is read exactly as it
     * was written rather than silently repaired.
     */
    KEYS("keys", "API keys", "Import, test and manage every key"),
    VOCABULARY("vocabulary", "Learn my words", "Teach the keyboard the names you dictate"),
    BUCKETS("buckets", "Paste timing", "How long each step waits, for the buckets, AP and AC"),
    RECORDING("recording", "Recording", null),
    // Every setting about the suggestion row, the AI key's model and Learn my words (27.10.2026).
    // Every automatic change to text, one switch each (Marko, 9.11.2026). Its icon is the AUTO cut-out.
    AUTO("auto", "AUTO corrections", "Every automatic text change, each its own switch, all off until you turn one on"),
    PREDICTIONS("predictions", "Auto prediction", "Word prediction, the AI key's model, your corrections"),
    MAPPINGS("mappings", "Custom mappings", null),
    OUTPUT("output", "Output", null),
    HISTORY("history", "History", "Every transcription, with its audio"),
    RECOVERED("recovered", "Recovered recordings", "Audio saved when a recording was cut short"),
    SETTINGS_ORDER("settings_order", "Edit settings order", "Drag these entries into the order you want");

    companion object {
        fun byId(id: String): MaSettingsEntry? = entries.firstOrNull { it.id == id }
    }
}

/** Reads and writes the stored order. Pure, so it is provable in the sandbox. */
object MaSettingsOrder {

    /**
     * Marko's order, and the one a reset returns to.
     *
     * His instruction was "at the top the custom ones", and this is that read literally: the screens
     * that exist only in this app and that change how the keyboard behaves come first, then the
     * dictation settings in the sequence a dictation actually happens in, then the two archives, and
     * the editor for this very list last, where a thing used once belongs.
     *
     * `SETTINGS_ORDER` is deliberately in the list rather than pinned outside it. It is a link like
     * any other and somebody who wants it at the top should be able to drag it there.
     */
    val DEFAULT: List<MaSettingsEntry> = listOf(
        MaSettingsEntry.PERMISSIONS,
        MaSettingsEntry.AUTO,
        MaSettingsEntry.TRANSCRIPTION_MODEL,
        MaSettingsEntry.ENGINES,
        MaSettingsEntry.SETTINGS_ORDER,
        MaSettingsEntry.BUCKETS,
        MaSettingsEntry.SWITCHBOARD,
        MaSettingsEntry.MAGIC,
        MaSettingsEntry.VOICE_COMMANDS,
        MaSettingsEntry.SHORTCUTS,
        MaSettingsEntry.PROMPTS,
        MaSettingsEntry.RE_MAGIC,
        MaSettingsEntry.SPELLING,
        MaSettingsEntry.CLAUDE_COSTS,
        // Beside Prompts and its costs, where he looked for it (27.10.2026: "it should be there, but
        // it is missing" — it was the last entry of all, under twenty others).
        MaSettingsEntry.PREDICTIONS,
        MaSettingsEntry.VOICE_FORMAT,
        MaSettingsEntry.READER,
        MaSettingsEntry.NUMBER_ROW,
        MaSettingsEntry.KEY_COLORS,
        MaSettingsEntry.POPUPS,
        MaSettingsEntry.CLOUD_READER,
        MaSettingsEntry.COPY_ROW,
        MaSettingsEntry.PROFILES,
        MaSettingsEntry.FEATURE_ROW,
        MaSettingsEntry.RECORDING,
        MaSettingsEntry.MAPPINGS,
        MaSettingsEntry.OUTPUT,
        MaSettingsEntry.HISTORY,
        MaSettingsEntry.RECOVERED,
        MaSettingsEntry.VOCABULARY,
        MaSettingsEntry.CORRECTIONS,
    )

    val DEFAULT_RAW: String = serialize(DEFAULT)

    /**
     * Parses a stored order and **always returns every entry**.
     *
     * Unknown ids drop, duplicates collapse, and anything missing is inserted **right after the entry
     * DEFAULT puts before it**, which is what makes a new settings screen safe to add later.
     *
     * It used to be appended at the bottom. That was safe in the narrow sense — never invisible — and
     * not in the sense that matters: Number row landed under twenty entries he had arranged, and he
     * reported it as missing (10.10.2026). Beside its neighbour in DEFAULT is where it would be on a
     * fresh install, so that is where someone who has moved other things looks for it. His own order
     * is untouched: only entries absent from it are placed.
     */
    fun parse(raw: String?): List<MaSettingsEntry> {
        val wanted = raw.orEmpty()
            .split(',')
            .mapNotNull { MaSettingsEntry.byId(it.trim()) }
            // My corrections went inside Auto prediction (27.10.2026). Where he had it, Auto
            // prediction now stands: it is where he looks for corrections, and it moves Auto
            // prediction up from the bottom of the list, where he could not find it.
            .map { if (it == MaSettingsEntry.CORRECTIONS) MaSettingsEntry.PREDICTIONS else it }
            .distinct()
        val all = wanted.toMutableList()
        DEFAULT.forEachIndexed { index, entry ->
            if (entry in all) return@forEachIndexed
            val anchor = DEFAULT.subList(0, index).lastOrNull { it in all }
            all.add(if (anchor == null) 0 else all.indexOf(anchor) + 1, entry)
        }
        // PERMISSIONS is first only because DEFAULT puts it first.
        //
        // It was pinned here — forced to the top on every read whatever he had arranged. That is a
        // different thing from a default and he was right to reject it: **a default is where
        // something starts, a pin is a decision taken away from him.** This list is his, and the one
        // entry he is not allowed to move would be the one that proves it is not.
        //
        // It still lands first on a fresh install, because it is first in DEFAULT and an empty
        // stored order returns DEFAULT untouched. After that it is an entry like any other.
        //
        // KEYS stays filtered: it is a section inside PERMISSIONS now, and an entry that opens the
        // same screen from two places is two doors to one room.
        //
        // VOCABULARY likewise: "Teach it from my dictations" is inside Auto prediction now, and
        // everything about prediction is behind that one entry (27.10.2026).
        return all.filterNot {
            it == MaSettingsEntry.KEYS || it == MaSettingsEntry.VOCABULARY || it == MaSettingsEntry.CORRECTIONS
        }
    }

    fun serialize(order: List<MaSettingsEntry>): String = order.joinToString(",") { it.id }

    /**
     * [raw] with Auto prediction moved to just after Claude costs (or Prompts, when costs is not in
     * it). Run once, by DictateLegacyMigrator: 27.10.2026 he could not find it, because it was below
     * twenty entries, and he named where it belongs, with the prompts and the corrections.
     */
    fun withPredictionsBesidePrompts(raw: String): String {
        val ids = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "predictions" && it != "corrections" }
        val anchor = listOf("claude_costs", "prompts").firstOrNull { it in ids } ?: return raw
        val out = ids.toMutableList()
        out.add(out.indexOf(anchor) + 1, "predictions")
        return out.joinToString(",")
    }

    /** Moves the entry at [from] to [to], shifting the rest. A move, not a swap. */
    fun move(order: List<MaSettingsEntry>, from: Int, to: Int): List<MaSettingsEntry> {
        if (from !in order.indices) return order
        val target = to.coerceIn(0, order.size - 1)
        if (from == target) return order
        val out = order.toMutableList()
        out.add(target, out.removeAt(from))
        return out
    }
}
