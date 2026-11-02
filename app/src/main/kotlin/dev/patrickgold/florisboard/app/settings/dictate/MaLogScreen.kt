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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.MaLog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

/**
 * The log, on screen, copyable in one tap.
 *
 * ### What it is for
 *
 * Not for him to read. For him to **send**. Every problem in this app so far has been diagnosed by
 * describing symptoms into a chat window, which is slow and frequently wrong — the accessibility
 * service took three builds and a broken app to work out, because nothing recorded whether it had
 * ever started.
 *
 * So the important control on this screen is Copy, and the important property of the text is that
 * all of it fits in one paste.
 *
 * ### Newest first
 *
 * The file is written oldest first, because appending is what makes it cheap and safe. It is shown
 * the other way up: whatever just went wrong is what he is here to look at, and asking him to scroll
 * to the bottom of four hundred lines to find it would waste the thing this screen exists to save.
 */
@Composable
fun MaLogScreen() = FlorisScreen {
    title = "Log"

    content {
        val context = LocalContext.current
        // Read once per visit rather than watched.
        //
        // The log is written from the keyboard and the accessibility service, in other processes'
        // moments; a live view would redraw while he is trying to read it. `reload` is bumped by
        // the button, so refreshing is something he asks for.
        var reload by remember { mutableStateOf(0) }
        val lines = remember(reload) { MaLog.read().asReversed() }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    copyToClipboard(context, MaLog.readAll())
                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                },
            ) { Text("COPY ALL") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { reload++ },
            ) { Text("REFRESH") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    MaLog.clear()
                    reload++
                },
            ) { Text("CLEAR") }
        }

        Text(
            text = if (lines.isEmpty()) {
                "Nothing recorded yet. Use the keyboard once and come back."
            } else {
                "${lines.size} lines, newest first. Copy all, then paste them into the chat."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))

        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                // Monospaced so the timestamps form a column and the eye can drop down it.
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("TTT log", text)) }
}
