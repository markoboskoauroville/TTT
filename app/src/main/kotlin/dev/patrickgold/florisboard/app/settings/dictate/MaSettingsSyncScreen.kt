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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.MaSettingsSync
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch

/** Sync settings between devices (MaSettingsSync): off by default, his own private repository and token. */
@androidx.compose.runtime.Composable
fun MaSettingsSyncScreen() = FlorisScreen {
    title = "Sync settings between devices"

    content {
        val scope = rememberCoroutineScope()
        var on by remember { mutableStateOf(MaSettingsSync.enabled) }
        var repo by remember { mutableStateOf(MaSettingsSync.repo) }
        var hasToken by remember { mutableStateOf(MaSettingsSync.hasOwnToken()) }
        var status by remember { mutableStateOf(MaSettingsSync.status) }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Your layout, feature rows and every other setting go to a private GitHub repository of yours, and " +
                    "every phone with this switch on takes them from there: change a setting on one phone and the " +
                    "others have it the next time the keyboard opens. API keys, tokens, history and clips never go.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Sync this phone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { MaSettingsSync.enabled = it; on = it })
            }
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it; MaSettingsSync.repo = it },
                label = { Text("Repository (owner/name)") },
                singleLine = true,
                isError = repo.isNotBlank() && !MaSettingsSync.validRepo(repo.trim()),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                if (hasToken) "Token: saved (hidden)."
                else "Token: none yet. Put a GitHub token that can write to this repository in a text file and use " +
                    "Import keys from file (Voice transcription buttons). A token TTT already has is used when it can.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(enabled = on, onClick = {
                    status = "Syncing..."
                    scope.launch {
                        status = MaSettingsSync.syncNow()
                        hasToken = MaSettingsSync.hasOwnToken()
                    }
                }) { Text("Sync now") }
                OutlinedButton(enabled = hasToken, onClick = { MaSettingsSync.clearToken(); hasToken = false; status = MaSettingsSync.status }) {
                    Text("Delete token")
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
