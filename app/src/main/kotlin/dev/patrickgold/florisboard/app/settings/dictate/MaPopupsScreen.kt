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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaPopups
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefAsState

/**
 * What a long press on each letter offers (Marko, 22.10.2026), on the same grid as Coloring the
 * Buttons: "we're going to reuse the part of TTT for coloring the buttons because the whole
 * layout is there".
 *
 * ### The key's ID card
 *
 * A long press (or a tap) on a letter opens its ID card, his picture of it: "an ID card has a
 * picture of the person. Now this ID card has the picture of the key itself." The key drawn big at
 * the top, then Pop-up 1, Pop-up 2 … each a field with its ✕, and under the last one a + that adds
 * another. Save writes them; the keyboard offers them on its next long press.
 *
 * ### The grid
 *
 * The letter rows as the keyboard last drew them, each letter with its pop-ups printed small under
 * it, so the whole configuration reads at a glance. Keys that are not letters are drawn dim: their
 * long press is not decided here. Before the keyboard has been opened this session, plain QWERTY
 * stands in, as on the colours screen.
 */
@Composable
fun MaPopupsScreen() = FlorisScreen {
    title = "Pop-up Configuration"

    content {
        val prefs by FlorisPreferenceStore
        val context = LocalContext.current
        val keyboardManager by context.keyboardManager()
        val scope = rememberCoroutineScope()

        val raw by prefs.dictate.maPopups.collectPrefAsState()
        val popups = remember(raw) { MaPopups.parse(raw) }
        val evaluator by keyboardManager.lastCharactersEvaluator.collectAsState()
        val keyboard = evaluator.keyboard as? TextKeyboard

        // The letter whose ID card is open, or null.
        var card by remember { mutableStateOf<Char?>(null) }
        card?.let { letter ->
            PopupIdCard(
                letter = letter,
                start = popups[letter].orEmpty(),
                onDismiss = { card = null },
                onSave = { list ->
                    scope.launch { prefs.dictate.maPopups.set(MaPopups.serialize(MaPopups.with(popups, letter, list))) }
                    card = null
                },
            )
        }

        Text(
            text = "Long press a letter to open its ID card: the pop-ups it offers when you hold it " +
                "on the keyboard, in order. Add as many as you like with +. A letter with none offers " +
                "nothing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            val letterRows: List<List<String>> =
                if (keyboard != null && keyboard.arrangement.isNotEmpty()) {
                    keyboard.arrangement
                        .map { row -> row.map { it.label.orEmpty() } }
                        .filter { row -> row.any { MaPopups.decides(it) } }
                } else {
                    MaPopups.QWERTY.map { line -> line.map { it.toString() } }
                }
            letterRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { label ->
                        val letter = label.lowercase().firstOrNull()
                        val isLetter = MaPopups.decides(label) && letter != null
                        PopupGridKey(
                            label = label,
                            under = if (isLetter) popups[letter].orEmpty().joinToString(" ") else null,
                            enabled = isLetter,
                            modifier = Modifier.weight(1f),
                            onOpen = { if (isLetter) card = letter },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { scope.launch { prefs.dictate.maPopups.set(MaPopups.DEFAULT) } },
            modifier = Modifier.padding(horizontal = 10.dp),
        ) { Text("Back to the Croatian letters only") }
        Spacer(Modifier.height(24.dp))
    }
}

/** The keycap on the grid: dark, like the keyboard's. */
private val POPUP_KEYCAP = Color(0xFF1E2228)

/** A letter on the grid, its pop-ups printed small under it. */
@Composable
private fun PopupGridKey(
    label: String,
    under: String?,
    enabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val ink = if (enabled) Color(0xFFD8D8D8) else Color(0xFFD8D8D8).copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(56.dp)
            .background(POPUP_KEYCAP, RoundedCornerShape(6.dp))
            .combinedClickable(enabled = enabled, onClick = onOpen, onLongClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = ink, fontSize = 16.sp, maxLines = 1)
            if (!under.isNullOrEmpty()) {
                Text(
                    text = under,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

/**
 * The key's ID card: the key big at the top, as a card's photo, then its pop-ups one under the
 * other, each with its ✕, and a + under the last.
 */
@Composable
private fun PopupIdCard(
    letter: Char,
    start: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val fields = remember(letter) { mutableStateListOf<String>().apply { addAll(start.ifEmpty { listOf("") }) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The photo: the key itself, big.
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(POPUP_KEYCAP, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = letter.toString(), color = Color(0xFFD8D8D8), fontSize = 64.sp)
                }
                Text(
                    text = "Key ${letter.uppercaseChar()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                )
                fields.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { fields[index] = MaPopups.clean(it.replace("\n", "")) },
                            label = { Text("Pop-up ${index + 1}") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { fields.removeAt(index); if (fields.isEmpty()) fields.add("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove pop-up ${index + 1}")
                        }
                    }
                }
                // The + that adds a pop-up under the last.
                IconButton(
                    onClick = { fields.add("") },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add a pop-up", modifier = Modifier.size(32.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(fields.toList()) }) { Text("Save") }
                }
            }
        }
    }
}
