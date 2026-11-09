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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The RE pop-up (MaReResult): what RE answered, shown before anything touches the chat.
 *
 * A message: the text in large type (scrollable), OK inserts exactly that text into the field, EDIT lets him
 * change it here first, CANCEL drops it. Not a message (RE's commentary, a question, an error): the same
 * window with the words and only Close, so it can never be inserted. Back or a tap outside is Cancel.
 */
class MaReResultActivity : ComponentActivity() {

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = MaReResult.shown
        if (result == null) { finish(); return }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var editing by remember { mutableStateOf(false) }
                var text by remember { mutableStateOf(result.text) }
                fun cancel() { done = true; MaReResult.cancel(); finish() }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { cancel() }.imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().clickable(enabled = false) {},
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (result.note) "RE says (not a message, nothing goes into the chat)" else "RE draft: read it, then OK",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (result.note) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                            if (editing) {
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).padding(top = 8.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 8.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(text = text, fontSize = 20.sp, lineHeight = 28.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (result.note) {
                                    TextButton(onClick = { cancel() }) { Text("Close", fontSize = 18.sp) }
                                } else {
                                    TextButton(onClick = { cancel() }) { Text("Cancel", fontSize = 18.sp) }
                                    if (!editing) TextButton(onClick = { editing = true }) { Text("Edit", fontSize = 18.sp) }
                                    TextButton(
                                        enabled = text.isNotBlank(),
                                        onClick = { done = true; MaReResult.accept(text); finish() },
                                    ) { Text("OK", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Swiped away or Back: nothing is inserted.
        if (!done) MaReResult.cancel()
        // However it closed, RE's state is reset at once: no spinner, no stuck keyboard.
        DictateController.recoverAfterPopup()
        super.onDestroy()
    }
}
