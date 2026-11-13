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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.settings.dictate.RetranscribeDialog
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HISTORY AS ITS OWN APP (Marko, 13.11.2026): "when I press the history button it takes the full screen and I can
 * really luxuriously press buttons and delete individual transcription and audio files."
 *
 * Full screen, big buttons on every entry: Insert (into the field the History key was pressed in), Copy, Play,
 * Re-transcribe (any engine; "Keep this text" writes it back), Delete text (the audio stays, the entry becomes
 * "audio only"), Delete audio (the text stays), Delete (both). Clear history at the top deletes every text and every
 * audio file, after a confirmation; it is the only thing besides his own per-entry deletes that removes audio.
 *
 * "Audio only" entries are cancelled or failed dictations whose audio was kept: they wear a badge and their first
 * button is Re-transcribe.
 */
class MaHistoryActivity : ComponentActivity() {

    private var pkg: String? = null
    private var fieldId: Int = 0
    private var inserted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG)
        fieldId = intent.getIntExtra(EXTRA_FIELD, 0)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    HistoryScreen(
                        canInsert = pkg != null,
                        onInsert = { text ->
                            inserted = true
                            MaReResult.acceptFor(text, pkg, fieldId)
                            finish()
                        },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (!inserted) MaReResult.closedWithoutInsert()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_FIELD = "field"
        private val BG = dev.patrickgold.florisboard.dictate.ui.MaHost.Bg

        fun open(context: Context, pkg: String?, fieldId: Int) {
            context.startActivity(
                Intent(context.applicationContext, MaHistoryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(EXTRA_PKG, pkg)
                    .putExtra(EXTRA_FIELD, fieldId),
            )
        }
    }
}

@Composable
private fun HistoryScreen(canInsert: Boolean, onInsert: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by remember(context) { DictateHistoryStore.flow(context).flowOn(Dispatchers.IO) }.collectAsState(initial = null)
    var confirmClear by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var retx by remember { mutableStateOf<DictateHistoryEntry?>(null) }

    fun stopPlay() {
        player.value?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player.value = null
        playingId = null
    }
    fun play(e: DictateHistoryEntry) {
        if (playingId == e.id) { stopPlay(); return }
        stopPlay()
        val path = e.audioPath ?: return
        val p = runCatching {
            MediaPlayer().apply { setDataSource(path); setOnCompletionListener { stopPlay() }; prepare(); start() }
        }.getOrNull()
        if (p == null) Toast.makeText(context, "The audio file is gone", Toast.LENGTH_SHORT).show()
        else { player.value = p; playingId = e.id }
    }
    DisposableEffect(Unit) { onDispose { stopPlay() } }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { DictateHistoryStore.repairMissingAudio(context) } } }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // Top bar: Close, title with counts, Clear history.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigIconButton(Icons.Default.Close, "Close", onClick = onClose)
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text("History", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                val list = entries.orEmpty()
                val audio = list.count { it.audioPath != null }
                val mb = list.sumOf { it.audioBytes } / 1_000_000.0
                Text(
                    "${list.size} entries · $audio with audio · ${"%.1f".format(mb)} MB",
                    fontSize = 13.sp, color = dev.patrickgold.florisboard.dictate.ui.MaHost.Muted,
                )
            }
            TextButton(onClick = { confirmClear = true }, enabled = !entries.isNullOrEmpty()) {
                Icon(Icons.Default.DeleteForever, null, tint = dev.patrickgold.florisboard.dictate.ui.MaHost.DangerInk)
                Spacer(Modifier.width(4.dp))
                Text("Clear history", color = dev.patrickgold.florisboard.dictate.ui.MaHost.DangerInk, fontSize = 16.sp)
            }
        }
        val list = entries
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = Color.Gray) }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing dictated yet.", color = Color.Gray, fontSize = 18.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { e ->
                    EntryCard(
                        e = e,
                        canInsert = canInsert,
                        playing = playingId == e.id,
                        onInsert = { onInsert(e.text) },
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("TTT", e.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        onPlay = { play(e) },
                        onRetranscribe = { stopPlay(); retx = e },
                        onDeleteText = { scope.launch(Dispatchers.IO) { DictateHistoryStore.deleteTextOnly(context, e, DictateController.MA_AUDIO_ONLY_TEXT) } },
                        onDeleteAudio = { if (playingId == e.id) stopPlay(); scope.launch(Dispatchers.IO) { DictateHistoryStore.deleteAudioOnly(context, e) } },
                        onDelete = { if (playingId == e.id) stopPlay(); scope.launch(Dispatchers.IO) { DictateHistoryStore.delete(context, e) } },
                    )
                }
            }
        }
    }

    retx?.let { e ->
        RetranscribeDialog(
            entry = e,
            onKeep = { text -> scope.launch(Dispatchers.IO) { DictateHistoryStore.updateText(context, e.id, text) } },
            onDismiss = { retx = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the whole history?") },
            text = { Text("Every transcription and every audio file is deleted. This cannot be undone.", fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    stopPlay()
                    scope.launch(Dispatchers.IO) { DictateHistoryStore.clearAll(context) }
                }) { Text("Delete all", color = dev.patrickgold.florisboard.dictate.ui.MaHost.DangerInk, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(
    e: DictateHistoryEntry,
    canInsert: Boolean,
    playing: Boolean,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onPlay: () -> Unit,
    onRetranscribe: () -> Unit,
    onDeleteText: () -> Unit,
    onDeleteAudio: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val audioOnly = e.failed
    val hasAudio = e.audioPath != null
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(dev.patrickgold.florisboard.dictate.ui.MaHost.Key, dev.patrickgold.florisboard.dictate.ui.MaHost.KeyShape)
            .then(if (audioOnly) Modifier.border(1.5.dp, dev.patrickgold.florisboard.dictate.ui.MaHost.Gold, dev.patrickgold.florisboard.dictate.ui.MaHost.KeyShape) else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (audioOnly) {
                Text(
                    "AUDIO ONLY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                    modifier = Modifier.background(dev.patrickgold.florisboard.dictate.ui.MaHost.Gold, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            val meta = buildList {
                add(DateUtils.getRelativeDateTimeString(context, e.createdAt, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString())
                if (e.durationSecs > 0) add("${e.durationSecs} s")
                if (e.language.isNotBlank()) add(e.language.uppercase())
                if (!audioOnly && e.providerName.isNotBlank()) add(e.providerName)
                add(if (hasAudio) "audio ${"%.1f".format(e.audioBytes / 1_000_000.0)} MB" else "no audio")
            }.joinToString(" · ")
            Text(meta, fontSize = 13.sp, color = dev.patrickgold.florisboard.dictate.ui.MaHost.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (e.title.isNotBlank() && !audioOnly) {
            Text(e.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            text = e.text,
            fontSize = if (audioOnly) 16.sp else 19.sp,
            lineHeight = if (audioOnly) 22.sp else 26.sp,
            color = if (audioOnly) dev.patrickgold.florisboard.dictate.ui.MaHost.Sand else dev.patrickgold.florisboard.dictate.ui.MaHost.Ink,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { expanded = !expanded },
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (audioOnly && hasAudio) Action(Icons.Default.Autorenew, "Re-transcribe", strong = true, onClick = onRetranscribe)
            if (!audioOnly && canInsert) Action(Icons.AutoMirrored.Filled.KeyboardReturn, "Insert", strong = true, onClick = onInsert)
            if (!audioOnly) Action(Icons.Default.ContentCopy, "Copy", onClick = onCopy)
            if (hasAudio) Action(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) "Stop" else "Play", onClick = onPlay)
            if (!audioOnly && hasAudio) Action(Icons.Default.Autorenew, "Re-transcribe", onClick = onRetranscribe)
            if (!audioOnly && hasAudio) Action(Icons.Default.TextFields, "Delete text", danger = true, onClick = onDeleteText)
            if (!audioOnly && hasAudio) Action(Icons.Default.MusicOff, "Delete audio", danger = true, onClick = onDeleteAudio)
            Action(Icons.Default.DeleteOutline, "Delete", danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, strong: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    // The keyboard's own key (MaHostKey): green ring = the main action, light red text = deletes.
    dev.patrickgold.florisboard.dictate.ui.MaHostKey(
        label = label, icon = icon,
        ring = if (strong) dev.patrickgold.florisboard.dictate.ui.MaHost.RingOn else null,
        ink = if (danger) dev.patrickgold.florisboard.dictate.ui.MaHost.DangerInk else dev.patrickgold.florisboard.dictate.ui.MaHost.Ink,
        onClick = onClick,
    )
}

@Composable
private fun BigIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.padding(2.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.width(28.dp).height(28.dp))
    }
}
