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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.engines.EngineCatalog
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import dev.patrickgold.florisboard.dictate.engines.EngineStore
import dev.patrickgold.florisboard.dictate.engines.KeyImporter
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch

/**
 * API (Marko, 23.11.2026: "make a new settings API ... API is the only thing and inside there is intelligent file picker
 * for API's and ... if I file pick the token from GitHub, then this feature is working automatically without asking me
 * nonsense like my username. Everything is inside the token").
 *
 * One picker for every key and token (KeyImporter: providers by key shape, each tested, the working ones kept encrypted;
 * a GitHub token goes to RE's database, RE's repository reading, the settings sync (repository found or made from the
 * token, sync switched on) and the shared corrections, wherever it can write). Then the report, and what is set up now.
 * No fields: nothing to type, ever.
 */
@Composable
fun MaApiScreen() = FlorisScreen {
    title = "API"

    content {
        val c = LocalContext.current
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf("") }
        var report by remember { mutableStateOf(EngineStore.lastKeys(c).orEmpty()) }
        var tick by remember { mutableStateOf(0) }
        fun finish(text: String) { EngineStore.setLastKeys(c, text); report = text; busy = ""; tick++ }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                busy = "Reading…"
                scope.launch { finish(KeyImporter.importUris(c, uris) { busy = it }.summary) }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Pick one or more files with your keys and tokens (Download/API). TTT finds what each one is, tests it, keeps the " +
                    "working ones encrypted on this phone and switches on what they unlock. A GitHub token sets up settings sync, " +
                    "RE's memory and the shared corrections by itself.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = busy.isEmpty(),
                onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (busy.isEmpty()) "Import keys from file" else busy, style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                enabled = busy.isEmpty(),
                onClick = { busy = "Re-testing…"; scope.launch { finish(KeyImporter.retest(c) { busy = it }.summary) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Re-test keys") }

            // What is set up now: counts only, never a key.
            Text("Set up now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            val lines = remember(tick) {
                val engines = EngineCatalog.ALL.mapNotNull { s -> EngineKeys.countLabel(c, s.id)?.let { EngineCatalog.shortName(s.id) + ": " + it } }.distinct()
                val github = listOfNotNull(
                    if (dev.patrickgold.florisboard.dictate.MaRemoteDb.hasToken()) "RE memory (RE Magic Database): token kept" else null,
                    if (dev.patrickgold.florisboard.dictate.MaSettingsSync.hasOwnToken()) "Settings sync: " + dev.patrickgold.florisboard.dictate.MaSettingsSync.repo.ifBlank { "repository at the next sync" } else null,
                    dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.status.takeIf { it.isNotBlank() },
                )
                (engines + github).ifEmpty { listOf("Nothing yet: pick your key files above.") }
            }
            for (l in lines) Row { Text("• $l", style = MaterialTheme.typography.bodyMedium) }
            if (report.isNotBlank()) {
                Text("Last import", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(report, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
