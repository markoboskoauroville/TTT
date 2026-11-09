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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaSpelling
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch

/**
 * SPELLING (Marko, 9.11.2026): the names his dictation gets wrong and what they become. Three switches
 * on top (the whole pass, the keyterms sent to AssemblyAI) and every pair below with its own switch.
 * The table is the file Documents/TTTmini/dictation_corrections.json, pushed from the Mac
 * (`re-skill-sync corrections push`); pairs the keyboard learned from his edits are marked "learned".
 */
@Composable
fun MaSpellingScreen() = FlorisScreen {
    title = "Spelling"

    content {
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        val offRaw by prefs.dictate.maSpellingOff.collectAsState()
        val off = offRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val table = MaSpelling.table()

        SwitchPreference(
            prefs.dictate.maSpelling,
            title = "Correct misheard names",
            summary = "After every dictation, the pairs below are fixed in the text (whole words, any case). " +
                "Grok stays when you say Elon Musk or xAI.",
        )
        SwitchPreference(
            prefs.dictate.maSpellingTerms,
            title = "Send the names to AssemblyAI",
            summary = "The correct names go with every transcription request, so the engine favours them itself.",
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "${table.pairs.size} pairs. Table: ${MaSpelling.file().path}" +
                    (if (MaSpelling.file().exists()) "" else " (not there yet: run re-skill-sync corrections push on the Mac; a short built-in list is used)") + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (p in table.pairs) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.from.joinToString(", ") + "  →  " + p.to, style = MaterialTheme.typography.bodyMedium)
                        val note = buildList {
                            if (p.learned) add("learned from your edits")
                            if (p.unless.isNotEmpty()) add("not when: " + p.unless.joinToString(", "))
                            if (p.onlyIf.isNotEmpty()) add("only with: " + p.onlyIf.joinToString(", "))
                        }.joinToString(" · ")
                        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = p.id !in off,
                        onCheckedChange = { on ->
                            val next = if (on) off - p.id else off + p.id
                            scope.launch { prefs.dictate.maSpellingOff.set(next.joinToString(",")) }
                        },
                    )
                }
            }
        }
    }
}
