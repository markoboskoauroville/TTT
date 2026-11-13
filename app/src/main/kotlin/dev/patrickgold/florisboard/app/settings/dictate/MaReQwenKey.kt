/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import dev.patrickgold.florisboard.dictate.MaReEngine
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RE's own Groq key for Qwen (Marko, 13.11.2026): "I want to provide separate API key which has nothing to do with the
 * rest of the Groq functions inside the application." A file picker (never a paste box); every key in the file is
 * tested at that moment and the answer is said per key: "Key 1: works", "Key 2: does not work (refused)". Only the
 * working ones are kept, encrypted, in the ring `re_qwen`, which nothing else in TTT reads. Key values are never shown.
 */
@Composable
internal fun MaReQwenKeySection() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(EngineKeys.ownLines(c, MaReEngine.RING).size) }

    suspend fun testAll(keys: List<String>): List<Pair<String, String>> = keys.map { it to MaReEngine.testQwenKey(it) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        report = "Testing…"
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                uris.joinToString("\n") { u -> runCatching { c.contentResolver.openInputStream(u)?.use { String(it.readBytes().take(1_000_000).toByteArray()) } }.getOrNull().orEmpty() }
            }
            val keys = Regex("gsk_[A-Za-z0-9]{20,}").findAll(text).map { it.value }.distinct().toList()
            if (keys.isEmpty()) { report = "No Groq key (gsk_...) found in the file."; return@launch }
            val results = testAll(keys)
            val good = results.filter { it.second.startsWith("works") }.map { it.first }
            val stored = if (good.isNotEmpty()) EngineKeys.merge(c, MaReEngine.RING, good) else 0
            count = EngineKeys.ownLines(c, MaReEngine.RING).size
            report = results.mapIndexed { i, r -> "Key ${i + 1}: ${r.second}" }.joinToString("\n") +
                "\nKept for RE: $stored new, $count in all."
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("RE Qwen key (Groq, only for RE)", style = MaterialTheme.typography.titleSmall)
        Text(
            if (count == 0) "None yet. RE's Qwen needs its own key; the rest of TTT's Groq keys are never used for it."
            else "$count key(s) kept for RE's Qwen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }) { Text("Import RE key from file") }
            OutlinedButton(enabled = count > 0, onClick = {
                report = "Testing…"
                scope.launch {
                    val keys = EngineKeys.ownLines(c, MaReEngine.RING)
                    val results = testAll(keys)
                    val keep = results.filter { !it.second.contains("refused") }.map { it.first }
                    if (keep.size != keys.size) EngineKeys.setLines(c, MaReEngine.RING, keep)
                    count = keep.size
                    report = results.mapIndexed { i, r -> "Key ${i + 1}: ${r.second}" }.joinToString("\n") +
                        (if (keep.size != keys.size) "\nRemoved ${keys.size - keep.size} refused key(s)." else "")
                }
            }) { Text("Re-test") }
            OutlinedButton(enabled = count > 0, onClick = { EngineKeys.delete(c, MaReEngine.RING); count = 0; report = "RE Qwen keys deleted." }) { Text("Delete") }
        }
        if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}
