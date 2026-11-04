/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The Reprompt window (MaReprompt): one line for the instruction, his last ones under it, Apply.
 *
 * Over whatever app he was in, at the bottom where the thumb is, with the keyboard open on the
 * line at once, so he can type the instruction or dictate it with •E / •H. Apply (or the keyboard's
 * Enter) closes it, and the message field he came from is rewritten as soon as it is back.
 */
class MaRepromptActivity : ComponentActivity() {

    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recent = MaReprompt.recent()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val scope = rememberCoroutineScope()
                var text by remember { mutableStateOf("") }
                val focus = remember { FocusRequester() }
                fun apply(words: String) {
                    if (words.isBlank()) return
                    submitted = true
                    scope.launch {
                        MaReprompt.submit(words)
                        finish()
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .clickable { MaReprompt.cancel(); finish() }
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().clickable(enabled = false) {},
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Reprompt",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (MaReprompt.writeMode) "The field is empty: what message should Claude write?"
                                else "What should Claude do with the text in the field?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                            )
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = {
                                    Text(
                                        if (MaReprompt.writeMode) "e.g. tell Damjan I'm working on it, an hour"
                                        else "e.g. make it shorter and friendlier",
                                    )
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { apply(text) }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                            // Cancel and Apply right under the line, before the list, so they never
                            // slide under the navigation bar (Mac emulator test, build 440).
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { MaReprompt.cancel(); finish() }) { Text("Cancel") }
                                TextButton(enabled = text.isNotBlank(), onClick = { apply(text) }) { Text("Apply") }
                            }
                            // His last instructions: one tap runs one again.
                            for (r in recent) {
                                Text(
                                    text = r,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { apply(r) }
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
        }
    }

    override fun onDestroy() {
        // Closed with Back, or swiped away: nothing is rewritten.
        if (!submitted) MaReprompt.cancel()
        super.onDestroy()
    }
}
