/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile

/**
 * The volume keys. His main shortcut, and the specification is in `VOLUME_KEYS.md`.
 *
 * ### There is no setting
 *
 * There was one, and it is the reason this was dead for five builds. It defaulted on, it sat in a
 * list of thirteen switches he had just been given the ability to drag, and one stray touch turned
 * it off. Nothing said so. He lost days believing the feature had been deleted, and I spent three
 * rounds reading a handler that was correct the whole time.
 *
 * **A control that can silently disable the thing somebody uses most is not a feature, it is a
 * trapdoor.** The switch is gone. These keys are always live while the keyboard is on screen.
 *
 * ### What they do
 *
 * **Volume up, quick tap** — start recording. Tap again — stop, and send to transcription.
 *
 * **Volume down, quick tap while recording** — stop, transcribe, and when the words land, press Send
 * on screen with the accessibility finger. One press for the whole errand.
 *
 * **Volume down, quick tap otherwise** — press Send on screen.
 *
 * **Either, held** — the real volume, repeating, with the system's own bar showing.
 *
 * ### Why the decision happens on RELEASE
 *
 * At the moment of pressing there is no way to know which of the two he meant. A quick tap is a tap;
 * a finger still down after [HOLD_MS] was never a tap at all. Deciding on release is what lets one
 * key do both jobs without either guessing.
 *
 * Two invariants, and they are the feature: **never both** — no press produces an app action and a
 * volume change — and **always one** — no press does nothing at all.
 */
object MaVolumeKeys {

    /**
     * How long a key must be held before it stops being a tap and becomes the volume.
     *
     * Half a second. Long enough that a deliberate tap never crosses it, short enough that a finger
     * resting on a key is not left wondering.
     */
    private const val HOLD_MS = 500L

    /** The gap between volume steps once the hold has taken over. His number. */
    private const val REPEAT_MS = 111L

    /**
     * Its own scope, on Main.
     *
     * The service's lifecycle scope would carry a repeat across a keyboard that has been hidden, and
     * a volume key repeating for a keyboard nobody can see is how an app gets uninstalled.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var holdJob: Job? = null

    /** Set once a press has become the volume, so its release does not also fire an app action. */
    @Volatile
    private var didRepeat = false

    private fun isVolume(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    /**
     * Whether this module is taking the volume keys at all.
     *
     * ### This is the setting VOLUME_KEYS.md forbids, and it is here on purpose
     *
     * That document says plainly: there is no preference, do not add one back. It says so because a
     * switch buried in a draggable list of thirteen was turned off by one stray touch, silently, and
     * cost him days believing the feature had been deleted while three rounds were spent reading a
     * handler that was correct the whole time.
     *
     * He has asked for it back, and the reason is real: the keys are live whenever the keyboard is
     * up, and sometimes he wants the volume to be the volume. So the rule that matters is not "no
     * switch" — it is **the thing that failed was a switch he could not see.** That is what this
     * avoids:
     *
     *  - **The key on the row is the only control.** No switchboard entry, no settings screen, no
     *    gestures list. It cannot be brushed while dragging something else, because it is not in a
     *    list of switches.
     *  - **It shows its own state, on the row, in front of him.** Green means the keys are live.
     *    The old switch's fatal property was that nothing on screen changed when it flipped.
     *  - **It only exists if he puts it there.** A key that is not on his row cannot turn anything
     *    off.
     *
     * Persisted, deliberately. A state that resets itself when the keyboard restarts is a control
     * that lies — he would switch the keys off for a film and find them live again at the next text
     * field, with no press of his to explain it.
     *
     * Read fresh on every press rather than cached. This is read a few times a second at most, and a
     * cached copy is how a toggle comes to disagree with the key that draws it.
     */
    private fun live(): Boolean {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maVolumeKeysLive.get()
    }

    /**
     * A volume key going down. Returns true when this module has taken the key.
     *
     * Nothing happens on the way down except starting the clock — see the note on release above.
     */
    @Volatile private var offLoggedAt = 0L

    fun onDown(context: Context, keyCode: Int, event: KeyEvent?): Boolean {
        if (!isVolume(keyCode)) return false
        // Switched off, this module is not here. Returning false hands the key straight to the
        // system, which is the ordinary volume with the system's own bar — so the two invariants
        // still hold: never both, and always one.
        //
        // Logged, because the whole point of the log line is to answer "why did nothing happen".
        if (!live()) {
            // Once per minute at most (28.11.2026): every press of an ordinary volume change was a log line, and 286 of the
            // 400 lines on the Pixel were these, which pushed every dictation line out of the log.
            val now = System.currentTimeMillis()
            if (now - offLoggedAt > 60_000L) { offLoggedAt = now; MaLog.add("vol", "${name(keyCode)} \u2014 keys switched off, system volume") }
            return false
        }
        MaLog.add("vol", "${name(keyCode)} down")
        // The system repeats a held key on its own clock. Only the first press opens a hold; the
        // repeats are swallowed, because the repeating is done here.
        if (event != null && event.repeatCount > 0) return true
        didRepeat = false
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(HOLD_MS)
            didRepeat = true
            MaLog.add("vol", "held \u2014 passing to the system volume")
            val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                AudioManager.ADJUST_RAISE
            } else {
                AudioManager.ADJUST_LOWER
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            while (isActive) {
                runCatching {
                    am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                }
                delay(REPEAT_MS)
            }
        }
        return true
    }

    /**
     * A volume key coming up. Returns true when this module has taken the key.
     *
     * This is where the work happens. For a quick tap that is a delay of the length of the tap,
     * which is not felt; for a hold it is the difference between changing the volume and starting a
     * recording nobody asked for.
     */
    fun onUp(context: Context, keyCode: Int): Boolean {
        if (!isVolume(keyCode)) return false
        // The same answer on the way up. Both halves have to agree, or a press that went to the
        // system on the way down would come back here and start a recording on release.
        if (!live()) return false
        holdJob?.cancel()
        holdJob = null
        if (didRepeat) {
            // The hold already spent this press on the volume. Doing the app action as well would
            // give him both, which is the one outcome nobody wants.
            didRepeat = false
            MaLog.add("vol", "${name(keyCode)} released after a hold \u2014 nothing more")
            return true
        }
        // WHILE IT IS READING, THE SAME KEYS DRIVE THE READING.
        //
        // Nobody dictates into a screen they are listening to. So for as long as there is a reading
        // in progress, a tap means skip rather than record — forward on up, back on down — and the
        // hold still means the volume, because that is the one thing wanted at exactly that moment.
        //
        // The keys are not remapped by a setting; they follow what is on screen. A control that
        // means the obvious thing for the situation in front of him needs no mode and no memory.
        // THE READER NO LONGER ANSWERS TO THESE KEYS.
        //
        // While reading, volume up and down used to be previous and next sentence. He asked for that
        // connection to be deleted: the reading is steered by swiping on the window now — down for
        // next, up for previous, either side to kill it — and a second way of doing it with hardware
        // keys is a second thing to remember for no gain.
        //
        // What they do instead while reading is nothing, which means the system hears them, which
        // means **they change the volume of the voice he is listening to.** That is the thing
        // actually wanted with a hardware key during playback, and it was the one thing he could not
        // do while the old mapping held. Deleting a feature gave him a better one for free.
        //
        // The invariants still hold: exactly one thing happens per press, and never both.
        if (MaReader.currentIndex >= 0) return false


        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                MaLog.add("vol", "up tap \u2014 record / stop")
                DictateController.onMicClick(context)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val recording = DictateController.state.value is DictateController.UiState.Recording
                if (recording) {
                    // The whole errand in one press: stop, transcribe, and send once the words are
                    // in the field. The send is ARMED here and fired by the transcription path,
                    // because the text has to land first — a send from this line would send an
                    // empty message and nobody would find out until they read the other end.
                    MaLog.add("vol", "down tap while recording \u2014 stop, transcribe, then send")
                    DictateController.sendAfterCommit = true
                    DictateController.onMicClick(context)
                } else {
                    MaLog.add("vol", "down tap \u2014 press Send on screen")
                    MaMagicTargets.pressSend()
                }
            }
        }
        return true
    }

    private fun name(keyCode: Int) =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "up" else "down"
}
