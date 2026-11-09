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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaReSkill
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference

/**
 * RE MAGIC: the skill editor (Marko, 9.11.2026: "in settings it must have an editor of the skill").
 *
 * The skill is a Markdown file, `Documents/TTTmini/re_ttt_skill.md`, pushed from the Mac
 * (`re-skill-sync push`) and never shipped with the app. Here it is viewed, edited and saved; the
 * switch turns the learning from corrections on and off; the last corrections are listed; and the
 * two sync buttons are the phone's half of `re-skill-sync` (the Mac does the transfer over Wi-Fi
 * with adb, because the phone cannot reach the Mac): RELOAD takes the file the Mac pushed, SAVE
 * leaves the edited file where the Mac pulls it from.
 */
@Composable
fun MaReScreen() = FlorisScreen {
    title = "RE Magic"

    content {
        val prefs by FlorisPreferenceStore
        var text by remember { mutableStateOf(MaReSkill.read()) }
        var status by remember { mutableStateOf("") }
        var rev by remember { mutableStateOf(0) }
        val (changed, kept) = remember(rev) { MaReSkill.counts() }
        val recent = remember(rev) { MaReSkill.recent(8) }

        SwitchPreference(
            prefs.dictate.maReLearn,
            title = "Learn from my corrections",
            summary = "When you send a message RE wrote, the keyboard compares what you sent with what RE wrote, " +
                "and adds a short rule to the skill. Only fields where RE wrote are compared. It only reads, " +
                "never presses Send. Off: nothing is kept or compared.",
        )

        SwitchPreference(
            prefs.dictate.maReScreen,
            title = "Read screen",
            summary = "Say \"take a screenshot and reply to the latest message\" (or in Croatian, \"odgovori na zadnju poruku\"): " +
                "RE reads the chat on screen and shows the reply in a pop-up; OK puts it in the field. It never sends. It reads " +
                "the app's accessibility text with the side of each message (the only reader in WhatsApp). No picture ever leaves the phone.",
        )
        SwitchPreference(
            prefs.dictate.maReScreenshot,
            title = "Screenshot fallback",
            summary = "Off. For apps whose accessibility text is empty: take a screenshot and read it on the phone (OCR). Never used in WhatsApp.",
        )
        SwitchPreference(
            prefs.dictate.maReSkills,
            title = "Skills",
            summary = "After a conversation, RE updates notes on this phone, in Documents/TTTmini/skills: your style, one " +
                "file per person and one per chat (what was agreed, what is done, what is open). RE reads that chat's notes " +
                "before it writes the next reply. They stay on the phone and in your private repo. Say \"update skills\" to do it now.",
        )
        var skillTick by remember { mutableStateOf(0) }
        var openFile by remember { mutableStateOf<java.io.File?>(null) }
        var openText by remember { mutableStateOf("") }
        var skillStatus by remember { mutableStateOf("") }
        val skillFiles = remember(skillTick) { dev.patrickgold.florisboard.dictate.MaSkills.files() }
        val skillScope = rememberCoroutineScope()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    skillStatus = "Updating from the chat on screen..."
                    skillScope.launch { skillStatus = dev.patrickgold.florisboard.dictate.MaSkills.updateNow(); skillTick++ }
                }) { Text("Update skills now") }
                OutlinedButton(onClick = { skillTick++ }) { Text("Refresh list") }
            }
            if (skillStatus.isNotEmpty()) Text(skillStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(
                text = if (skillFiles.isEmpty()) "No skill files yet: they appear after your first RE message in a chat." else "${skillFiles.size} skill files:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            for (f in skillFiles) {
                Text(
                    text = f.relativeTo(dev.patrickgold.florisboard.dictate.MaSkills.root()).path + "  ·  " + dev.patrickgold.florisboard.dictate.MaSkills.stamp(f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable {
                        openFile = f; openText = runCatching { f.readText() }.getOrDefault("")
                    }.padding(vertical = 8.dp),
                )
            }
            openFile?.let { f ->
                OutlinedTextField(
                    value = openText, onValueChange = { openText = it },
                    label = { Text(f.name) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = { runCatching { f.writeText(openText) }; skillStatus = "Saved ${f.name}."; skillTick++ }) { Text("Save file") }
                    OutlinedButton(onClick = { openFile = null }) { Text("Close") }
                }
            }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val shotOk = dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService.canTakeScreenshot()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = if (shotOk) "Screenshot permission: granted (step 2 works)."
                else "Screenshot permission: not granted yet. Step 1 works without it. For step 2, open the accessibility " +
                    "settings and switch the TTT service off and on; the system asks, and you tap Allow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!shotOk) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Open accessibility settings") }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "RE transcribes with AssemblyAI only. It writes by the skill file: ${MaReSkill.skillFile().path}" +
                    (if (MaReSkill.exists()) "" else " (not there yet: run re-skill-sync push on the Mac; until then RE uses a short built-in voice)") +
                    ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Corrections learned from: $changed. Sent as written: $kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("The skill (Markdown)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {
                    status = if (MaReSkill.save(text)) "Saved to Documents/TTTmini. The Mac can pull it." else "Could not save: allow file access."
                    rev++
                }) { Text("Save") }
                OutlinedButton(onClick = {
                    text = MaReSkill.read(); status = "Reloaded the file from Documents/TTTmini."; rev++
                }) { Text("Reload") }
                OutlinedButton(onClick = {
                    MaReSkill.forgetRules()
                    text = MaReSkill.read(); status = "Learned rules forgotten; the rest of the skill stays."; rev++
                }) { Text("Reset rules") }
            }
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                text = "Last corrections",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            if (recent.isEmpty()) {
                Text("None yet. Write with RE, correct it, send it.", style = MaterialTheme.typography.bodySmall)
            }
            for (o in recent) {
                val isChanged = o.optBoolean("changed")
                Text(
                    text = (if (isChanged) "CHANGED " else "SENT AS WRITTEN ") + o.optString("at") + " in " + o.optString("app"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (isChanged) {
                    Text("RE: " + o.optString("draft").take(240), style = MaterialTheme.typography.bodySmall)
                    Text("You: " + o.optString("final").take(240), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
