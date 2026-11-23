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
        @Suppress("UNUSED_VARIABLE") val unusedTick = tick
        var rows by remember { mutableStateOf(KeyImporter.rows(c)) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                busy = "Reading…"
                scope.launch { finish(KeyImporter.importUris(c, uris) { busy = it }.summary); rows = KeyImporter.rows(c) }
            }
        }
        // The services the GitHub token and the relay unlock, each with the answer of its own test.
        var services by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        suspend fun testServices(): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>()
            val reTok = runCatching { dev.patrickgold.florisboard.dictate.MaRemoteDb.readToken() }.getOrNull()
            out += "RE memory (GitHub, RE Magic Database)" to when {
                reTok == null -> "no token: pick the file with your GitHub token"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.patrickgold.florisboard.dictate.MaRemoteDb.tokenWorks(reTok) } -> "works"
                else -> "refused: the token cannot write to RE_MAGIC_DATABASE (expired, or no access)"
            }
            out += "Settings sync (GitHub)" to dev.patrickgold.florisboard.dictate.MaSettingsSync.let { it.attach(c); if (!it.enabled && !it.hasOwnToken()) "off: pick the GitHub token file to switch it on" else it.syncNow() }
            out += "Shared corrections (GitHub)" to dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.syncNow()
            out += "Frankfurt relay" to dev.patrickgold.florisboard.dictate.engines.FrankfurtRelay.check(c)
            return out
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
            // TEST ALL (23.11.2026): every key and every service, one answer each; nothing is removed.
            Button(
                enabled = busy.isEmpty(),
                onClick = {
                    busy = "Testing\u2026"
                    scope.launch {
                        rows = KeyImporter.testAll(c) { busy = it }
                        busy = "Testing GitHub and Frankfurt\u2026"
                        services = testServices()
                        busy = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Test all keys", style = MaterialTheme.typography.titleMedium) }

            // Every key: provider, its number, the first characters that tell its kind, and the answer of the last test.
            Text("Your keys (${rows.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (rows.isEmpty()) Text("No keys yet: pick your key files above.", style = MaterialTheme.typography.bodyMedium)
            val good = androidx.compose.ui.graphics.Color(0xFF6FA85A)
            val bad = androidx.compose.ui.graphics.Color(0xFFE8776C)
            for (r in rows) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("${r.name} ${r.number}/${r.total}  \u00b7  ${r.hint}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        r.result?.let { (if (r.ok == true) "\u2713 " else "\u2717 ") + it + (r.at?.let { a -> "  ($a)" } ?: "") } ?: "not tested yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (r.ok) { true -> good; false -> bad; null -> MaterialTheme.colorScheme.onSurfaceVariant },
                    )
                }
            }
            if (services.isNotEmpty()) {
                Text("Services", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                for ((name, answer) in services) {
                    val ok = answer.startsWith("works") || answer.startsWith("answers") || answer.contains("rules in use") || answer.contains("synced", true) || answer.startsWith("Up to date", true)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text((if (ok) "\u2713 " else "\u2717 ") + answer, style = MaterialTheme.typography.bodySmall, color = if (ok) good else bad)
                    }
                }
            }
            OutlinedButton(
                enabled = busy.isEmpty(),
                onClick = { busy = "Re-testing\u2026"; scope.launch { finish(KeyImporter.retest(c) { busy = it }.summary); rows = KeyImporter.rows(c) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Remove keys that no longer work") }
            if (report.isNotBlank()) {
                Text("Last import", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(report, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
