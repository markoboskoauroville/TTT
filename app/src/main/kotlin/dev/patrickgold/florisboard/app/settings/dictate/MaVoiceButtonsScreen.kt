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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.engines.EngineCatalog
import dev.patrickgold.florisboard.dictate.engines.EngineChain
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import dev.patrickgold.florisboard.dictate.engines.KeyImporter
import dev.patrickgold.florisboard.dictate.engines.EngineLive
import dev.patrickgold.florisboard.dictate.engines.Quota
import dev.patrickgold.florisboard.dictate.engines.EngineStore
import dev.patrickgold.florisboard.dictate.ui.MaEngineKeyContent
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch

private fun colName(id: String): String = when (id) {
    "groq_v3" -> "Groq v3"
    "google_v1" -> "Google v1"
    else -> EngineCatalog.shortName(id)
}

/**
 * Voice transcription buttons (Marko, 9.11.2026): the two keys, H and E, as they are on the keyboard,
 * and under each a radio list of providers. Choosing one changes the key at once. The fallback chain
 * and the benchmark are under Advanced.
 */
@Composable
fun MaVoiceButtonsScreen() = FlorisScreen {
    title = "Voice transcription buttons"

    content {
        val c = LocalContext.current
        val nav = LocalNavController.current
        val live by EngineLive.state.collectAsState()
        LaunchedEffect(Unit) { EngineLive.load(c) }

        val red = Color(0xFFB3261E)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((lang, letter) in listOf("hr" to "H", "en" to "E")) {
                val id = live.selected[lang] ?: EngineStore.selected(c, lang)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, red),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        MaEngineKeyContent(letter, Color(0xFFE53935), EngineCatalog.shortName(id), false, MaterialTheme.colorScheme.onSurface, dev.patrickgold.florisboard.dictate.ui.engineSubLabel(c, id))
                    }
                }
            }
        }
        // Keys enter only through files (Marko, 9.11.2026): one picker, every provider detected, tested, the working ones kept.
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<String?>(null) }
        var lastKeys by remember { mutableStateOf(EngineStore.lastKeys(c)) }
        var keysTick by remember { mutableStateOf(0) }
        fun finish(text: String) {
            EngineStore.setLastKeys(c, text); lastKeys = text; result = text; busy = ""; keysTick++
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                busy = "Reading\u2026"
                scope.launch { finish(KeyImporter.importUris(c, uris) { busy = it }.summary) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                enabled = busy.isEmpty(),
                onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) },
                modifier = Modifier.weight(1f),
            ) { Text(if (busy.isEmpty()) "Import keys from file" else busy, maxLines = 1) }
            TextButton(
                enabled = busy.isEmpty(),
                onClick = { busy = "Re-testing\u2026"; scope.launch { finish(KeyImporter.retest(c) { busy = it }.summary) } },
            ) { Text("Re-test keys") }
        }
        lastKeys?.let { sum ->
            val tail = sum.lines().lastOrNull { l -> l.startsWith("Stored") || l.startsWith("Removed") || l.startsWith("No keys") } ?: sum.lines().first()
            Text(
                "Last: $tail",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        result?.let { text ->
            AlertDialog(
                onDismissRequest = { result = null },
                title = { Text("Keys") },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (lang in listOf("hr", "en")) {
                val chosen = live.selected[lang] ?: EngineStore.selected(c, lang)
                val ids = EngineCatalog.ALL.map { it.id }.filter { it == chosen || EngineChain.available(c, it) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (id in ids) {
                        val spec = EngineCatalog.byId(id) ?: continue
                        val sel = id == chosen
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) red else MaterialTheme.colorScheme.outlineVariant),
                            color = if (sel) red.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().clickable { EngineLive.select(c, lang, id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 0.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)) {
                                RadioButton(selected = sel, onClick = { EngineLive.select(c, lang, id) }, modifier = Modifier.size(36.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(colName(id), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        when {
                                            spec.freeSeconds == Long.MAX_VALUE -> "free"
                                            spec.paid -> "$%.2f/h".format(java.util.Locale.ROOT, spec.pricePerHour)
                                            else -> (Quota.remainingSeconds(spec, EngineStore.usage(c, id), System.currentTimeMillis()) / 60).toString() + " min"
                                        } + (if (keysTick >= 0) EngineKeys.countLabel(c, id)?.let { " \u00b7 $it" }.orEmpty() else ""),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { nav.navigate(Routes.Settings.MaEngines) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text("Advanced") }
    }
}
