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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.audio.MaResample
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.engines.EngineAdapters
import dev.patrickgold.florisboard.dictate.engines.EngineCatalog
import dev.patrickgold.florisboard.dictate.engines.EngineChain
import dev.patrickgold.florisboard.dictate.engines.EngineFailure
import dev.patrickgold.florisboard.dictate.engines.EngineScores
import dev.patrickgold.florisboard.dictate.engines.WordDiff
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private data class Retx(val engineId: String, val lang: String, val text: String, val ms: Long)

private fun highlighted(toks: List<WordDiff.Tok>): AnnotatedString = buildAnnotatedString {
    toks.forEachIndexed { i, t ->
        if (i > 0) append(" ")
        if (t.changed) withStyle(SpanStyle(background = Color(0x88FFD54F), fontWeight = FontWeight.Bold)) { append(t.text) }
        else append(t.text)
    }
}

/**
 * "Re-transcribe with..." (Marko, 9.11.2026): ONE list of every engine that is set up, each row marked
 * HR or EN (the language version it will be asked for) with its accuracy; the history entry's own audio
 * goes through the chosen one and the result stands beside the text he used, the differing words lit.
 * Nothing is written back to the history: it is a comparison.
 */
@Composable
internal fun RetranscribeDialog(entry: DictateHistoryEntry, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Retx?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val own = if (entry.language.lowercase().startsWith("en")) "en" else "hr"

    val rows = remember(entry.id) {
        val ids = EngineCatalog.ALL.map { it.id }.filter { EngineChain.available(c, it) }
        val all = ids.flatMap { id -> listOf("hr", "en").map { id to it } }
        all.sortedWith(compareBy<Pair<String, String>> { it.second != own }.thenByDescending { EngineScores.get(c, it.first, it.second) ?: -1 })
    }

    fun run(id: String, lang: String) {
        val spec = EngineCatalog.byId(id) ?: return
        busy = EngineCatalog.shortName(id) + " " + lang.uppercase() + "…"
        error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val src = File(entry.audioPath ?: throw IllegalStateException("no audio kept for this entry"))
                    if (!src.exists() || src.length() == 0L) throw IllegalStateException("the audio file is gone")
                    val tmp = File(c.cacheDir, "retx_${entry.id}.wav")
                    src.copyTo(tmp, overwrite = true)
                    val wav = MaResample.toTargetRate(tmp) ?: tmp
                    val t0 = System.currentTimeMillis()
                    val text = withTimeout(if (spec.kind == dev.patrickgold.florisboard.dictate.engines.EngineKind.LOCAL) 60_000L else 45_000L) {
                        EngineAdapters.run(c, id, wav, lang, EngineChain.names())
                    }
                    val ms = System.currentTimeMillis() - t0
                    if (wav !== tmp) runCatching { wav.delete() }
                    runCatching { tmp.delete() }
                    Retx(id, lang, text, ms)
                }
            }
            busy = null
            r.onSuccess { result = it }.onFailure { error = (it as? EngineFailure)?.let { f -> "${spec.name}: ${f.kind.name.lowercase().replace('_', ' ')}" } ?: (it.message ?: "failed") }
        }
    }

    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = { Text(if (result == null) "Re-transcribe with…" else EngineCatalog.shortName(result!!.engineId) + " " + result!!.lang.uppercase() + EngineScores.suffix(c, result!!.engineId, result!!.lang), maxLines = 2) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp)) }
                val r = result
                if (busy != null) {
                    Text(busy!!, style = MaterialTheme.typography.bodyLarge)
                } else if (r != null) {
                    val (a, b) = WordDiff.diff(entry.text, r.text)
                    val same = a.count { !it.changed }
                    Text(
                        "${b.count { it.changed }} new, ${a.count { it.changed }} gone of ${a.size} words · ${r.ms} ms",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            SelectionContainer { Text(highlighted(a), fontSize = 13.sp, lineHeight = 17.sp) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(EngineCatalog.shortName(r.engineId) + " " + r.lang.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            SelectionContainer { Text(highlighted(b), fontSize = 13.sp, lineHeight = 17.sp) }
                        }
                    }
                    if (same == a.size && a.size == b.size) Text("identical", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(
                        "HR = asked as Croatian, EN = asked as English. The percentage is the engine's accuracy on your last dictations.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    for ((id, lang) in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { run(id, lang) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                lang.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.width(34.dp).background(if (lang == "hr") Color(0xFFB3261E) else Color(0xFF1565C0), RoundedCornerShape(6.dp)).padding(vertical = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                EngineCatalog.shortName(id) + EngineScores.suffix(c, id, lang),
                                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp), maxLines = 1,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (result != null && busy == null) TextButton(onClick = { result = null }) { Text("Another engine") }
        },
        dismissButton = { TextButton(enabled = busy == null, onClick = onDismiss) { Text("Close") } },
    )
}
