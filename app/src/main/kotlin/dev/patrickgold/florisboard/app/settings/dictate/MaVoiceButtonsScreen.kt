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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.engines.EngineCatalog
import dev.patrickgold.florisboard.dictate.engines.EngineChain
import dev.patrickgold.florisboard.dictate.engines.EngineLive
import dev.patrickgold.florisboard.dictate.engines.Quota
import dev.patrickgold.florisboard.dictate.engines.EngineStore
import dev.patrickgold.florisboard.dictate.ui.MaEngineKeyContent
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

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
                        MaEngineKeyContent(letter, Color(0xFFE53935), EngineCatalog.shortName(id), false, MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
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
                                        },
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
