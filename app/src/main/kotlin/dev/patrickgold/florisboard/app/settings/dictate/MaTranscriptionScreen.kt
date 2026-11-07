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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaProviders
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog
import dev.patrickgold.florisboard.dictate.provider.LocalModelDownloads
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState as collectFlow

/**
 * Settings → Transcription model (Marko, 7.11.2026): "either AssemblyAI, either Whisper or Whisper as a
 * fallback if AssemblyAI gets rejected or is not happening for some reason". The on-device engine is Parakeet
 * TDT 0.6B v3 (sherpa-onnx, int8, ~670 MB, downloaded here once): asked to compare it with Whisper Turbo, he
 * chose to keep it ("parakeet should be fine"), being the faster of the two on a phone.
 * The choice is read by [MaProviders.transcriptionAccount] and [MaProviders.localFallbackProvider].
 */
@Composable
fun MaTranscriptionScreen() = FlorisScreen {
    title = "Transcription model"

    content {
        val prefs by FlorisPreferenceStore
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val engine by prefs.dictate.maTranscriptionEngine.collectAsState()

        val downloads by LocalModelDownloads.state.collectFlow()
        val installedTick by LocalModelDownloads.installedTick.collectFlow()
        var deleteTick by remember { mutableStateOf(0) }
        val spec = LocalModelCatalog.PARAKEET_TDT_V3
        val installed = remember(installedTick, deleteTick) { LocalModelManager.isInstalled(context, spec.id) }
        val dl = downloads[spec.id]

        @Composable
        fun option(value: String, title: String, summary: String) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { prefs.dictate.maTranscriptionEngine.set(value) } }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = engine == value,
                    onClick = { scope.launch { prefs.dictate.maTranscriptionEngine.set(value) } },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = "Which engine turns your voice into text.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        option(
            MaProviders.ENGINE_ASSEMBLYAI, "AssemblyAI",
            "In the cloud, needs the AssemblyAI key and a connection.",
        )
        option(
            MaProviders.ENGINE_LOCAL, "Parakeet (on the phone)",
            "Parakeet TDT on this phone. Offline, nothing leaves the phone.",
        )
        option(
            MaProviders.ENGINE_FALLBACK, "AssemblyAI, Parakeet as fallback",
            "AssemblyAI first. When it is rejected or does not answer (no key, refused key, no " +
                "connection, timeout, server error), the same recording goes to Parakeet on the phone.",
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "On-device model",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            text = "${spec.displayName}: ${spec.description}. " + when {
                installed -> "Downloaded."
                dl != null && dl.error == null -> "Downloading, ${dl.percent} %. It continues if you leave this screen."
                dl?.error != null -> "Download failed: ${dl.error}"
                else -> "Not downloaded yet. Parakeet and the fallback need it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (dl != null && dl.error == null) {
            LinearProgressIndicator(
                progress = { dl.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                installed -> OutlinedButton(onClick = {
                    LocalModelManager.delete(context, spec.id)
                    deleteTick++
                }) { Text("Delete (frees ~670 MB)") }
                dl != null && dl.error == null -> OutlinedButton(onClick = {
                    LocalModelDownloads.cancel(spec.id)
                }) { Text("Cancel download") }
                else -> Button(onClick = {
                    if (dl?.error != null) LocalModelDownloads.clearError(spec.id)
                    LocalModelDownloads.start(context, spec)
                }) { Text("Download Parakeet (~670 MB)") }
            }
        }
        if (!installed && engine != MaProviders.ENGINE_ASSEMBLYAI) {
            Text(
                text = if (engine == MaProviders.ENGINE_LOCAL) {
                    "Until the model is downloaded, dictation will ask for it."
                } else {
                    "Until the model is downloaded, a failed AssemblyAI call ends in an error, as before."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
