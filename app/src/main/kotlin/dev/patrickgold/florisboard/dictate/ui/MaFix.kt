/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.engines.KeyImporter
import kotlinx.coroutines.launch

/**
 * THE ERROR IS THE FIX (Marko, 13.11.2026; MANTRA_MANIFEST modules/solution-at-the-nose.md): "Error itself needs to
 * solve the problem ... I click on the error and it opens file picker for new API key and I enter new API key and it
 * works."
 *
 * Any message that says a key or token is missing, refused, out of credit or over its limit is drawn by [MaFixMessage]
 * as one gold-ringed key: a tap opens the file picker in Download/Api, the picked file goes through the one importer
 * ([KeyImporter], tested, sorted to its provider), and [onFixed] runs: the caller retries what failed. No trip to
 * Settings, no deleting keys by hand. Any other error is a retry key.
 */
object MaFix {
    private val KEY_PROBLEM = Regex(
        "(?i)(api )?(key|token)[^.\\n]*(missing|refused|invalid|expired|revoked|is out|out of|not work|wrong)|" +
            "no [^.\\n]*(key|token)|not configured|tap here and pick|\\b(401|403)\\b|unauthori[sz]ed|" +
            "credit|billing|balance|quota|limit (is )?reached|payment|insufficient",
    )

    /** Whether [message] is a key problem, so loading another key is the obvious fix. */
    fun needsKey(message: String): Boolean = KEY_PROBLEM.containsMatchIn(message)

    /** Download/Api, where Marko keeps the key files. */
    class PickInDownloadApi : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FApi"),
            )
    }

    val ANY_FILE = arrayOf("*" + "/" + "*")
}

/**
 * The key file picker, ready to launch from an error. [onStatus] gets the importer's progress and its one-line
 * result; [onFixed] runs when at least one working key was stored.
 */
@Composable
fun rememberKeyFixPicker(onStatus: (String) -> Unit, onFixed: () -> Unit): ManagedActivityResultLauncher<Array<String>, android.net.Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(MaFix.PickInDownloadApi()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onStatus("Testing the key file…")
        scope.launch {
            val r = runCatching { KeyImporter.importUris(context.applicationContext, listOf(uri)) { s -> scope.launch { onStatus(s) } } }
            val o = r.getOrNull()
            onStatus(o?.summary?.lines()?.firstOrNull { it.isNotBlank() } ?: ("Import failed: " + (r.exceptionOrNull()?.message ?: "")))
            if (o != null && o.stored > 0) onFixed()
        }
    }
}

/**
 * An error drawn as its own fix. A key problem: gold ring, key icon, "tap to load a key file", opens the picker. Any
 * other error with [onRetry]: a retry key. Otherwise plain muted text.
 */
@Composable
fun MaFixMessage(
    message: String,
    modifier: Modifier = Modifier,
    onPickKey: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val key = MaFix.needsKey(message)
    if (!key && onRetry == null) {
        Text(message, color = MaHost.Muted, fontSize = 13.sp, modifier = modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(MaHost.KeyShape).background(MaHost.Key)
            .border(1.5.dp, if (key) MaHost.Gold else MaHost.RingSwitch, MaHost.KeyShape)
            .clickable { if (key) onPickKey() else onRetry?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(if (key) Icons.Default.Key else Icons.Default.Refresh, contentDescription = null, tint = MaHost.Gold, modifier = Modifier.size(20.dp))
        Text(
            text = message.trim().trimEnd('.') + if (key) ". Tap: load a key file." else ". Tap: try again.",
            color = MaHost.Ink, fontSize = 14.sp, fontWeight = FontWeight.Normal, maxLines = 4,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * THE MODEL BADGE (Marko, 13.11.2026): "Whenever I'm chatting with Claude, I need to be aware which model is it
 * using ... I can click on ... model, and just switch to Sonnet, Opus". Small blue letters, e.g. "Claude · Sonnet 5.5";
 * one tap moves to the next model in [options] and the next request uses it. Put it wherever an AI answers.
 *
 * [label] is what is used now; [options] are the stored values in tap order; [current] the stored value now.
 */
@Composable
fun MaModelBadge(
    label: String,
    options: List<String>,
    current: String,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    Text(
        text = "$label  ⇄",
        color = MaHost.ModelBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(MaHost.KeyShape)
            .clickable(enabled = options.size > 1) {
                val i = options.indexOf(current)
                onPick(options[(if (i < 0) 0 else i + 1) % options.size])
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
