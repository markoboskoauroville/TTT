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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.nlp.MaCorrectionRules
import dev.patrickgold.florisboard.dictate.nlp.MaDictationCorrections
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import kotlinx.coroutines.launch

/**
 * My corrections: the fixes the keyboard learned from him, and his own replace list.
 *
 * Reached from Settings → Auto prediction, where its switch is. Adding a rule first, then the rules: in use
 * first, then the ones still being counted, then the ones switched off. Each has its own switch and
 * its own delete, because a rule learned wrongly must cost one tap, never the whole list.
 */
@Composable
fun MaCorrectionsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__ma_corrections__title)

    content {
        val rules by MaDictationCorrections.rules.collectAsState()

        // The switch is on Auto prediction, with every other prediction switch (27.10.2026); this
        // screen is reached from there.

        // ---------------------------------------------------------------- shared with the Mac and the other phone (21.11.2026)
        var shared by remember { mutableStateOf(dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.status) }
        val syncScope = androidx.compose.runtime.rememberCoroutineScope()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shared + "  ·  " + (dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.source()?.let { "this phone writes learned/$it.jsonl" } ?: "this device only reads"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                shared = "Syncing\u2026"
                syncScope.launch { shared = dev.patrickgold.florisboard.dictate.nlp.MaCorrectionsSync.syncNow() }
            }) { Text("Sync now") }
        }

        // ---------------------------------------------------------------- add one
        Text(
            text = "Add a fix",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        var wrong by remember { mutableStateOf("") }
        var right by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = wrong,
                onValueChange = { wrong = it },
                singleLine = true,
                label = { Text("Heard") },
                modifier = Modifier.weight(1f),
            )
            Text(" → ", modifier = Modifier.padding(horizontal = 4.dp))
            OutlinedTextField(
                value = right,
                onValueChange = { right = it },
                singleLine = true,
                label = { Text("Write") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(
                enabled = wrong.isNotBlank() && right.isNotBlank() && wrong.trim() != right.trim(),
                onClick = { MaDictationCorrections.add(wrong, right); wrong = ""; right = "" },
            ) { Text("Add") }
            TextButton(onClick = { MaDictationCorrections.addSeeds() }) {
                Text("Add my replace list (${MaCorrectionRules.SEEDS.size})")
            }
        }

        // ---------------------------------------------------------------- the rules
        val inUse = rules.filter { it.isActive }
        val counting = rules.filter { it.enabled && !it.isActive }
        val off = rules.filter { !it.enabled }

        Spacer(Modifier.height(12.dp))
        if (rules.isEmpty()) {
            Text(
                text = "Nothing learned yet. Dictate, fix a word, and fix it the same way once more: " +
                    "it appears here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        RuleSection("In use (${inUse.size})", inUse)
        RuleSection("Seen once, used after one more (${counting.size})", counting)
        RuleSection("Switched off (${off.size})", off)

        if (rules.isNotEmpty()) {
            var confirm by remember { mutableStateOf(false) }
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                TextButton(onClick = { confirm = true }) { Text("Delete all") }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Delete every correction?") },
                    text = { Text("All ${rules.size} fixes, learned and added, are deleted here and switched off on the Mac and the other phone too (shared corrections).") },
                    confirmButton = {
                        TextButton(onClick = { MaDictationCorrections.deleteAll(); confirm = false }) { Text("Delete all") }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

@Composable
private fun RuleSection(title: String, rules: List<MaCorrectionRules.Rule>) {
    if (rules.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        rules.sortedBy { it.wrong.lowercase() }.forEach { rule -> RuleRow(rule) }
    }
}

@Composable
private fun RuleRow(rule: MaCorrectionRules.Rule) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${rule.wrong}  →  ${rule.right}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val where = when (rule.language) {
                MaCorrectionRules.ANY -> "every language"
                "hr" -> "Croatian"
                "en" -> "English"
                else -> rule.language
            }
            val how = when (rule.source) {
                MaCorrectionRules.Source.MINE -> "added by you"
                MaCorrectionRules.Source.LEARNED -> if (rule.count == 1) "fixed once" else "fixed ${rule.count} times"
            }
            Text(
                text = "$how · $where",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = { MaDictationCorrections.setEnabled(rule, it) })
        TextButton(onClick = { MaDictationCorrections.delete(rule) }) { Text("Delete") }
    }
}
