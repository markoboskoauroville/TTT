/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The record button's instrument panel: readings on top, meter along the bottom.
 *
 * The previous version put the numbers in the middle of the bar, on top of the moving trace, where
 * they were genuinely hard to read: dark text over a bar that is green in one place and pale in
 * another has no reliable contrast anywhere. Nothing overlaps now. The readings live on their own
 * line above, in bold, and the meter has the bottom of the box to itself.
 *
 * Three readings, each on the side where it belongs:
 *
 *   left    level in dB, the thing that changes fastest and is glanced at rather than read
 *   centre  elapsed time, the one number always wanted, so it gets the middle
 *   right   size on disk in megabytes while recording, and transfer figures while sending
 *
 * While sending, the meter has no microphone to show, so it becomes a stereo-style bar spreading out
 * from the centre: decoration rather than data, and honest about it, but it keeps the panel alive
 * during the wait rather than freezing at whatever the last syllable happened to be.
 */
@Composable
fun MaScopeCanvas(active: Boolean, tint: Color) {
    if (!active) return
    val level by DictateController.audioLevel.collectAsState()
    val state by DictateController.state.collectAsState()
    val sending = state is DictateController.UiState.Transcribing ||
        state is DictateController.UiState.Rewording

    val db = maToDb(level)
    val smoothed by animateFloatAsState(
        targetValue = db,
        animationSpec = tween(70),
        label = "maDb",
    )
    var peakDb by remember { mutableFloatStateOf(FLOOR_DB) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = maToDb(DictateController.audioLevel.value)
            peakDb = if (now > peakDb) now else (peakDb - 0.6f).coerceAtLeast(FLOOR_DB)
            delay(60L)
        }
    }

    // Sweeps 0..1 and back, driving the sending animation. Its own clock rather than the audio
    // level, because there is no audio arriving once the file has gone.
    var sweep by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sending) {
        if (!sending) return@LaunchedEffect
        var rising = true
        while (true) {
            sweep = (sweep + if (rising) 0.06f else -0.06f).coerceIn(0f, 1f)
            if (sweep >= 1f) rising = false
            if (sweep <= 0f) rising = true
            delay(40L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        MaReadings(sending = sending, tint = tint)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // A hairline rather than a bar. The thin jumping line reads as movement without
                // becoming a block of colour competing with the numbers above it.
                .height(3.dp)
                .padding(bottom = 1.dp),
        ) {
            val h = size.height
            val full = size.width
            drawRoundRect(
                color = tint.copy(alpha = 0.18f),
                topLeft = Offset(0f, 0f),
                size = Size(full, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
            )
            if (sending) {
                // Out from the middle, both ways at once, like a broadcast meter. It says "this is
                // going somewhere" without pretending to measure anything.
                val half = full / 2f
                val reach = half * sweep
                drawRoundRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = Offset(half - reach, 0f),
                    size = Size(reach * 2f, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                )
            } else {
                val filled = full * maNorm(smoothed)
                drawRoundRect(
                    color = maDbColour(smoothed, tint),
                    topLeft = Offset(0f, 0f),
                    size = Size(filled, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                )
                // Peak hold, falling back slowly so a transient stays readable.
                val peakX = (full * maNorm(peakDb)).coerceIn(0f, full - 2f)
                drawRoundRect(
                    color = maDbColour(peakDb, tint).copy(alpha = 0.9f),
                    topLeft = Offset(peakX, 0f),
                    size = Size(2f, h),
                    cornerRadius = CornerRadius(1f, 1f),
                )
            }
        }
    }
}

/** The three readings, bold, on their own line clear of the meter. */
@Composable
private fun MaReadings(sending: Boolean, tint: Color) {
    val recording = DictateController.state.collectAsState().value
        as? DictateController.UiState.Recording

    // Size on disk, sampled rather than computed: the recorder is writing the file, so asking the
    // file how big it is now is both simplest and always true.
    var bytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sending) {
        while (!sending) {
            bytes = DictateController.currentRecordingBytes()
            delay(400L)
        }
    }

    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording?.startedAtMs, recording?.accumulatedMs, recording?.paused) {
        val r = recording ?: return@LaunchedEffect
        while (true) {
            elapsedMs = if (r.paused) {
                r.accumulatedMs
            } else {
                r.accumulatedMs + (android.os.SystemClock.elapsedRealtime() - r.startedAtMs)
            }
            delay(200L)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        // CENTRED, AS A GROUP.
        //
        // `Arrangement.Center` rather than a weight on each side, which is what centred them before
        // and is what broke the megabyte figure: a weight hands each child a SHARE of the line, so
        // the size got whatever the clock did not use, and at 15,0 MB that was narrower than the
        // text and it wrapped onto a second line.
        //
        // The arrangement centres children that have already been measured at their own width. The
        // room is decided by the readings and then the leftover is split around them, which is the
        // opposite order to a weight and the reason this is centred AND cannot wrap.
        //
        // Left-aligned in build 265 because the wrap had to stop and moving everything to the edge
        // stopped it. It was the wrong half of the fix — the arrangement, not the alignment — and
        // he said so as soon as he saw it.
        horizontalArrangement = Arrangement.Center,
        // Bottom-aligned so the megabyte figure sits just above the meter line with a couple of
        // pixels of air, rather than floating in the middle of the row beside a much larger number.
        verticalAlignment = Alignment.Bottom,
    ) {
        // The dB number is gone. The bar underneath already says how loud, continuously and without
        // being read, which is what a meter is for; a number saying the same thing was one reading
        // too many on a strip this size and it was taking room from the one that matters.
        //
        // The lamp, the clock and the size, in that order, centred as a group by the row's
        // arrangement above.
        // The red dot, immediately before the clock.
        //
        // Marko asked for it and the reason is the right one: a red circle has meant "recording" on
        // every camera and every tape machine for fifty years, and a number counting up only means
        // recording once you have read it. The dot is understood before it is read.
        //
        // It obeys the colour rule rather than breaking it. Colour in this app means state and
        // nothing else, and this is the state itself, in the app's own recording red. It does NOT
        // pulse: nothing in this app breathes, and a blinking light on a bar that already has a
        // moving meter and a running clock would be the third thing on the strip demanding an eye.
        //
        // Dark while paused, because a paused recording is not recording. That is the one thing a
        // steady lamp has to get right, or it is lying for as long as the pause lasts.
        Box(
            modifier = Modifier
                // Centred on the digits, not sitting on their baseline.
                //
                // The row is bottom-aligned so the megabyte figure tucks under the clock, which is
                // right for a small number beside a large one — but the lamp is not a number, and
                // hanging it from the same line made it read as having slipped. Only the dot is
                // re-aligned; everything else keeps the arrangement it had.
                .align(Alignment.CenterVertically)
                .padding(end = 8.dp)
                .size(11.dp)
                .background(
                    // recording?.paused is the honest signal here rather than the meter's own
                    // "active" flag, which is not in scope in this function and also covers being
                    // frozen for a discard. Lit means audio is being captured, now.
                    color = if (sending || recording == null || recording.paused) {
                        MaRecordLampOff
                    } else {
                        MaRecordLampOn
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            // As large as the row allows. This is the number actually watched during a dictation,
            // and it now has the space the level readout was using.
            text = "%d:%02d".format(elapsedMs / 60000, (elapsedMs / 1000) % 60),
            color = tint,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            // One digit, the separator, one digit, then MB. It was three bare characters and it did
            // not say what they were: Marko had to guess that 5,6 meant megabytes, which is a
            // reading nobody can use.
            //
            // The separator is the phone's, not a full stop forced on it. On his Croatian phone
            // "%.1f" already gives 5,6 and that is correct there; hardcoding a dot would make the
            // keyboard the only thing on the screen writing numbers the other way.
            //
            // maxLines and softWrap are the actual fix for the break he photographed. Sized to its
            // own content now rather than to a share of the line, so there is nothing left to wrap.
            text = if (sending) "" else "%.1f MB".format(bytes / 1_048_576.0),
            color = tint.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 6.dp),
        )
        // MAC MIC cue (14.11.2026): which microphone is live, only while the Mac's is.
        val macMic by dev.patrickgold.florisboard.dictate.audio.MacMicLink.active.collectAsState()
        if (macMic && !sending) {
            Text(
                text = "MAC MIC",
                color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp),
            )
        }
        // No spacer at either end. A weight anywhere in this row would take the leftover space for
        // itself and shove the group off centre, which is exactly what the one that used to sit here
        // did.
    }
}

/**
 * The recording lamp, lit and dark.
 *
 * The lit colour is the same red the whole app uses for recording, so the dot and the record key
 * agree. The dark one is not grey: it is the same red taken most of the way down, which reads as a
 * lamp that is off rather than as a different indicator that has appeared.
 */
/**
 * The recording red, named once and shared.
 *
 * The lamp on the recording bar, the dot on the RECORD key and the dot in the settings picker are
 * all this. A second red mixed by eye somewhere else would be the same colour to whoever wrote it
 * and a different one on the screen, and this is a mark that means "recording" wherever it appears.
 */
val MaRecordRed = Color(0xFF9B3B33)

/**
 * THE RULE: every key that records his voice wears a red outline (Marko, 2.11.2026), so the row
 * says what pressing it does before it is pressed. Green replaces it only while that key is the one
 * recording. The record red itself, so the outline, the dot and the bar's lamp are one colour.
 */
val MaRecordOutline = MaRecordRed

/**
 * The same outline while that key is the one recording: light red (Marko, 4.11.2026: "record buttons
 * are dark red, when recording light red"). Red stays red, so the key still says "this records".
 */
val MaRecordOutlineLive = Color(0xFFE8776C)

private val MaRecordLampOn = MaRecordRed
private val MaRecordLampOff = MaRecordRed.copy(alpha = 0.22f)

/** Below this a speech signal is silence as far as this meter is concerned. */
private const val FLOOR_DB = -54f

private fun maToDb(level: Float): Float {
    val v = abs(level)
    if (v <= 0.0005f) return FLOOR_DB
    return (20.0 * kotlin.math.log10(v.toDouble())).toFloat().coerceIn(FLOOR_DB, 0f)
}

private fun maNorm(db: Float): Float = ((db - FLOOR_DB) / (0f - FLOOR_DB)).coerceIn(0f, 1f)

private fun maDbColour(db: Float, tint: Color): Color = when {
    db > -3f -> Color(0xFF9B3B33)
    db > -12f -> Color(0xFFF0883E)
    else -> Color(0xFF56D364)
}

/**
 * THE SAME RECORDER READOUT, FED FROM OUTSIDE (Marko, 13.11.2026: a recorder in another module "follows the same
 * visual style or even reuses the same modules"). The lamp, the bold clock, the megabytes and the hairline meter of
 * the keyboard's recording strip, drawn by the same helpers, for a recorder that is not DictateController's (the RE
 * chat). [level] is 0..1 like DictateController.audioLevel.
 */
@Composable
fun MaRecorderReadout(level: Float, elapsedMs: Long, bytes: Long, recording: Boolean, sending: Boolean, tint: Color) {
    val db = maToDb(level)
    val smoothed by animateFloatAsState(targetValue = db, animationSpec = tween(70), label = "maDbOut")
    var peakDb by remember { mutableFloatStateOf(FLOOR_DB) }
    LaunchedEffect(db) { peakDb = if (db > peakDb) db else (peakDb - 0.6f).coerceAtLeast(FLOOR_DB) }
    var sweep by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sending) {
        if (!sending) return@LaunchedEffect
        var rising = true
        while (sending) {
            sweep = (sweep + if (rising) 0.06f else -0.06f).coerceIn(0f, 1f)
            if (sweep >= 1f) rising = false
            if (sweep <= 0f) rising = true
            delay(40L)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Bottom) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).size(11.dp)
                    .background(if (recording && !sending) MaRecordLampOn else MaRecordLampOff, CircleShape),
            )
            Text(
                text = "%d:%02d".format(elapsedMs / 60000, (elapsedMs / 1000) % 60),
                color = tint, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(
                text = if (sending) "" else "%.1f MB".format(bytes / 1_048_576.0),
                color = tint.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp),
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(3.dp).padding(bottom = 1.dp)) {
            val h = size.height
            val full = size.width
            drawRoundRect(color = tint.copy(alpha = 0.18f), topLeft = Offset(0f, 0f), size = Size(full, h), cornerRadius = CornerRadius(h / 2f, h / 2f))
            if (sending) {
                val half = full / 2f
                val reach = half * sweep
                drawRoundRect(color = tint.copy(alpha = 0.85f), topLeft = Offset(half - reach, 0f), size = Size(reach * 2f, h), cornerRadius = CornerRadius(h / 2f, h / 2f))
            } else {
                drawRoundRect(color = maDbColour(smoothed, tint), topLeft = Offset(0f, 0f), size = Size(full * maNorm(smoothed), h), cornerRadius = CornerRadius(h / 2f, h / 2f))
                val peakX = (full * maNorm(peakDb)).coerceIn(0f, full - 2f)
                drawRoundRect(color = maDbColour(peakDb, tint).copy(alpha = 0.9f), topLeft = Offset(peakX, 0f), size = Size(2f, h), cornerRadius = CornerRadius(1f, 1f))
            }
        }
    }
}
