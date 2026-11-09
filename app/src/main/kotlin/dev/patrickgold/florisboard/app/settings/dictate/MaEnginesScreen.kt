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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.engines.ClipBuffer
import dev.patrickgold.florisboard.dictate.engines.EngineBenchmark
import dev.patrickgold.florisboard.dictate.engines.EngineBenchmarkJob
import dev.patrickgold.florisboard.dictate.engines.EngineCatalog
import dev.patrickgold.florisboard.dictate.engines.EngineChain
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import dev.patrickgold.florisboard.dictate.engines.EngineLive
import dev.patrickgold.florisboard.dictate.engines.EngineStore
import dev.patrickgold.florisboard.dictate.engines.Quota
import dev.patrickgold.florisboard.dictate.engines.QuotaReset
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

private fun quotaLine(c: android.content.Context, id: String): String {
    val spec = EngineCatalog.byId(id) ?: return ""
    if (spec.freeSeconds == Long.MAX_VALUE) return "free, no limit"
    if (spec.paid) return "no free quota · USD %.2f/h · only when on".format(Locale.ROOT, spec.pricePerHour)
    val now = System.currentTimeMillis()
    val left = Quota.remainingSeconds(spec, EngineStore.usage(c, id), now)
    val min = left / 60
    val fmt = SimpleDateFormat("d.M. HH:mm 'UTC'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val reset = Quota.nextReset(spec.reset, now)?.let { "resets " + fmt.format(Date(it)) } ?: spec.reset.label
    return "$min min left of ${spec.freeSeconds / 60} · $reset"
}

/**
 * Engines (Marko, 9.11.2026): every free engine, the order per language the dictation falls back
 * through, each engine's quota and reset, the keys (pasted here, Keystore-encrypted, never shown),
 * the clip buffer, and "Benchmark voice engines".
 */
@Composable
fun MaEnginesScreen() = FlorisScreen {
    title = "Advanced: fallback and benchmark"

    content {
        val c = LocalContext.current
        val scope = rememberCoroutineScope()
        var tick by remember { mutableStateOf(0) }
        var lang by remember { mutableStateOf("hr") }
        var order by remember(lang, tick) { mutableStateOf(EngineStore.order(c, lang)) }
        var chainOn by remember { mutableStateOf(EngineStore.chainOn(c)) }
        var clipsOn by remember { mutableStateOf(EngineStore.clipsOn(c)) }
        var weeklyOn by remember { mutableStateOf(EngineStore.weeklyOn(c)) }
        var progress by remember { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        var bench by remember { mutableStateOf(EngineStore.lastBenchmark(c)?.let { EngineBenchmark.rowsFromJson(it) }.orEmpty()) }
        var benchNote by remember { mutableStateOf("") }
        var keyEngine by remember { mutableStateOf("groq_turbo") }
        var keyText by remember { mutableStateOf("") }
        var keyNote by remember { mutableStateOf("") }

        @Composable
        fun toggle(title: String, summary: String, value: Boolean, set: (Boolean) -> Unit) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = value, onCheckedChange = set)
            }
        }

        toggle(
            "Fallback between engines",
            "Each dictation goes through the chain below for the language on the E/H key. On an error, a timeout or a quota answer it switches to the next engine at once and says so under the recorder. Off: the old single engine.",
            chainOn,
        ) { chainOn = it; EngineStore.setChainOn(c, it) }

        toggle(
            "Keep my last 20 dictations per language",
            "The audio, the language and the text you finally sent, on this phone only. The benchmark scores the engines on them.",
            clipsOn,
        ) { clipsOn = it; EngineStore.setClipsOn(c, it) }

        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Clips: HR ${ClipBuffer.count(c, "hr")}, ENG ${ClipBuffer.count(c, "en")}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { ClipBuffer.clear(c); tick++ }) { Text("Delete clips") }
        }

        Text("Order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { lang = "hr" }) { Text(if (lang == "hr") "● HR" else "HR") }
            OutlinedButton(onClick = { lang = "en" }) { Text(if (lang == "en") "● ENG" else "ENG") }
            OutlinedButton(onClick = { EngineStore.setOrder(c, lang, EngineCatalog.defaultOrder(lang)); tick++ }) { Text("Default") }
        }
        Text(
            "Hold a row and drag. The mixed chain is the one of the E/H key you are on.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        val shown = order.filter { it != EngineCatalog.FAKE }
        MaReorderableColumn(
            items = shown,
            rowHeight = ROW_HEIGHT,
            onMove = { from, to ->
                val m = shown.toMutableList(); val x = m.removeAt(from); m.add(to, x); order = m
            },
            onSettled = { EngineStore.setOrder(c, lang, order) },
        ) { index, id, _ ->
            val spec = EngineCatalog.byId(id)!!
            var on by remember(id, tick) { mutableStateOf(EngineStore.enabled(c, id)) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    val ready = EngineChain.available(c, id)
                    Text(
                        (if (ready) "" else if (spec.needsKey) "no key · " else "not installed · ") + quotaLine(c, id),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Switch(checked = on, onCheckedChange = { on = it; EngineStore.setEnabled(c, id, it) })
            }
        }

        Text("Keys", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        Text(
            "Paste a key, tap Save. It is encrypted in this phone's keystore and never shown again. Cloudflare: account id on the first line, token on the second.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val keyEngines = EngineCatalog.ALL.filter { it.needsKey }
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                val i = keyEngines.indexOfFirst { it.id == keyEngine }
                keyEngine = keyEngines[(i + 1) % keyEngines.size].id; keyText = ""; keyNote = ""
            }) { Text("Engine: " + (EngineCatalog.byId(keyEngine)?.name?.substringBefore(" (") ?: keyEngine)) }
        }
        Text(
            EngineKeys.describe(c, keyEngine) + " · " + (EngineCatalog.byId(keyEngine)?.keyHelp ?: ""),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = keyText, onValueChange = { keyText = it },
            label = { Text("Key") }, singleLine = false, maxLines = 3,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val v = dev.patrickgold.florisboard.dictate.engines.KeyText.forEngine(keyEngine, keyText) ?: keyText.trim()
                keyNote = if (EngineKeys.save(c, keyEngine, v)) "Saved." else "Nothing to save."
                keyText = ""; tick++
            }) { Text("Save key") }
            OutlinedButton(onClick = { EngineKeys.delete(c, keyEngine); keyNote = "Key deleted."; tick++ }) { Text("Delete key") }
        }
        if (keyNote.isNotEmpty()) Text(keyNote, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

        Text("Benchmark", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        Text(
            "Takes your last 10 dictations in Croatian and 10 in English and sends them through every engine that is on and set up, but only within each engine's free quota. An engine with no free quota is only used when it is switched on above.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !running, onClick = {
                running = true; benchNote = ""
                scope.launch {
                    val r = EngineBenchmark.run(c) { progress = it }
                    bench = r.rows; benchNote = (r.skipped + r.saved).joinToString("\n"); running = false; tick++
                }
            }) { Text(if (running) "Running…" else "Benchmark voice engines") }
            OutlinedButton(enabled = bench.isNotEmpty(), onClick = { EngineBenchmark.applyBest(c, bench); EngineBenchmark.bestPerLanguage(bench).forEach { (l, id) -> EngineLive.select(c, l, id) }; tick++ }) { Text("Use best per language") }
        }
        if (running) Text(progress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        SelectionContainer {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                for ((l, name) in listOf("hr" to "Croatian", "en" to "English")) {
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(EngineBenchmark.table(bench, l), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
                }
                if (benchNote.isNotEmpty()) Text(benchNote, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        toggle(
            "Run it every week",
            "On Wi-Fi and while charging, within the free quotas. The result is also saved to RE Magic Database (benchmarks) when its token is set.",
            weeklyOn,
        ) { weeklyOn = it; EngineStore.setWeeklyOn(c, it); EngineBenchmarkJob.schedule(c, it) }
    }
}
