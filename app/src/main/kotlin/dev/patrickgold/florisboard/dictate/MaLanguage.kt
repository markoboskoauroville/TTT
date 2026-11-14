/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
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

package dev.patrickgold.florisboard.dictate

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The language of this app, in one place.
 *
 * There were two languages before, and they were allowed to disagree. One decided what the speech
 * service was told it was listening to, the other decided which dictionary the suggestions came
 * from, and each had its own control. Two controls for one intention is one too many: nobody speaks
 * Croatian while wanting English suggestions, and the setup where that is possible is the setup
 * where one of the two is quietly wrong and there is no way to see which.
 *
 * So there is one switch, and what it moves is the **dictation** language: what the speech service
 * is told to expect. Every control that changes it goes through this object rather than writing the
 * preference directly.
 *
 * It deliberately does NOT move the keyboard's own language. That was tried and was wrong: typing
 * and speaking are two acts at two moments, and a control meant for the microphone must not relay
 * the keys.
 *
 * Two languages, Croatian and English, because that is what this app is for. Auto-detect is not one
 * of them: it exists in the catalog for the settings screen, but the toggle deliberately cannot land
 * on it, since a toggle whose third state means "I do not know" cannot be operated without looking.
 */
object MaLanguage {
    const val HR = "hr"
    const val EN = "en"

    /** The two, in the order the toggle moves through them. */
    val PAIR = listOf(HR, EN)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * The active language, always one of [PAIR].
     *
     * Anything else, including auto-detect left over from an older install, reads as Croatian rather
     * than as itself, so a caller never has to handle a third case.
     */
    fun active(): String {
        // An instruction for RE or WhatsApp is always spoken in English (Marko, 14.11.2026), whatever the badge says:
        // the language of the instruction never decides the language of the result (MaReStyle).
        if (DictateController.instructionInFlight()) return EN
        val prefs by FlorisPreferenceStore
        val code = prefs.dictate.activeInputLanguage.get().substringBefore('-').lowercase()
        return if (code == EN) EN else HR
    }

    /**
     * The badge shown wherever the language is displayed: **HR** or **ENG**.
     *
     * Three letters for English and two for Croatian, which is deliberately uneven. `EN` and `HR`
     * are the same shape and the same weight, so at a glance in the corner of a moving keyboard they
     * read as "some code" rather than as a word, and telling them apart takes a moment of actual
     * reading. `ENG` does not look like `HR`. Marko asked for these two spellings by name and this is
     * why they are right.
     *
     * Display only. The stored codes stay `en` and `hr`, and nothing branches on this string.
     */
    /**
     * What every badge in the app says: ENG, HR, or AUTO with the language it settled on.
     *
     * AUTO shows the resolved language beside it — "A·HR" — because a badge reading only AUTO would
     * leave him unable to see what the next dictation is about to use, which is the one thing a
     * badge exists to tell him. It also makes a wrong detection visible at a glance rather than
     * arriving later as a bad transcription.
     *
     * No arguments, so the four places that draw a badge cannot end up saying different things.
     */
    fun badge(): String = if (active() == EN) "ENG" else "HR"

    /**
     * Sets the language **the microphone is told to expect**, and nothing else.
     *
     * It used to switch the keyboard subtype at the same time, on the argument that nobody speaks
     * Croatian while wanting English suggestions. Marko's objection settles it: these are two
     * different things that happen at two different moments. He dictates in one language and types
     * in another all day, and a dictation language that quietly relaid his keyboard meant a press
     * meant for the microphone changed the keys under his thumb.
     *
     * The keyboard's own language keeps its own two routes, which are the ones every Android
     * keyboard has: hold the spacebar, or change it in settings. Neither of them touches this.
     */
    /**
     * Sets the language and **does not return until it has been written**.
     *
     * This exists because [set] launches its write on the Main dispatcher, and the transcription
     * path also runs on Main — so a `set` immediately followed by a read of [active] returned the
     * OLD value, every time. The detector was working perfectly and its answer was being thrown
     * away one line later: he spoke English, the probe said English, and the request went out in
     * Croatian.
     *
     * Anything that decides a language and then acts on it in the same breath must use this.
     */
    fun setNow(code: String): String {
        val target = if (code.substringBefore('-').lowercase() == EN) EN else HR
        val prefs by FlorisPreferenceStore
        runBlocking { prefs.dictate.activeInputLanguage.set(target) }
        return target
    }

    fun set(context: Context, code: String) {
        val target = if (code.substringBefore('-').lowercase() == EN) EN else HR
        val prefs by FlorisPreferenceStore
        scope.launch { prefs.dictate.activeInputLanguage.set(target) }
        // The keyboard's own language follows again, and it has to.
        //
        // Build 111 cut this link at his request: typing and speaking are two acts, and a control
        // meant for the microphone should not relay the keys under his thumb. The reasoning held,
        // but it left the suggestion language with no route at all once build 150 took the language
        // picker off the spacebar long press — so he typed Croatian and was offered "credits",
        // "credited", "creditors", with nothing anywhere to change it.
        //
        // Suggestions come from the subtype, so the subtype must move with the badge. What build
        // 111 was really protecting against was the keys being relaid without asking; that is a
        // layout question, and the two subtypes here are qwertz and qwerty, which is the layout he
        // wants for that language anyway.
        val subtypeManager by context.subtypeManager()
        subtypeManager.subtypes
            .firstOrNull { it.primaryLocale.language.lowercase() == target }
            ?.let { subtypeManager.switchToSubtypeById(it.id) }
    }

    /** Moves to the other one. */
    const val MODE_EN = "en"
    const val MODE_HR = "hr"
    /**
     * Removed. The badge has two states and the language is his decision.
     *
     * Kept as a constant only so a stored "auto" from an older install can be recognised and
     * treated as Croatian rather than as an unknown value. Nothing sets it any more.
     */
    const val MODE_AUTO = "auto"

    /**
     * Which of the three the badge is set to: English, Croatian, or work it out.
     *
     * Separate from [active] on purpose. AUTO still has to resolve to a real language before a
     * request is built, so [active] keeps meaning "the language being used right now" and this
     * answers "how was that decided". Kept apart, a probe that fails leaves a usable language
     * behind; merged, there would be a third value everything downstream had to know about.
     */
    fun mode(): String {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maLanguageMode.get()
    }

    /**
     * Steps the badge: English, Croatian, Auto, and round.
     *
     * Auto comes last so the two he picks deliberately are one tap apart. Leaving Auto sets the
     * language to whatever Auto last resolved, so the badge never lies about what the next dictation
     * will use.
     */
    /**
     * Long press on the badge: draw the suggestion row away, or bring it back.
     *
     * On the badge rather than an X at the end of the row, which is what he asked for first and then
     * talked himself out of — an X sitting in the row costs a slot of a strip that is already narrow,
     * and it only ever does one thing. The badge is already there, already about the row, and a hold
     * is a gesture nobody performs by accident.
     */
    fun toggleSuggestions() {
        // The row is never hidden now (27.10.2026: "It should never be hidden"). A hold on the badge
        // only makes sure it is showing, for an install that had drawn it away before.
        val prefs by FlorisPreferenceStore
        scope.launch { prefs.dictate.maSuggestionsShown.set(true) }
    }

    fun cycleMode(context: Context) {
        val prefs by FlorisPreferenceStore
        // Two states. AUTO is gone entirely: detection was never reliable enough to be trusted
        // with a whole dictation, and a wrong language returns confident nonsense rather than an
        // error he can see. Manual is slower and always right.
        val next = if (mode() == MODE_EN) MODE_HR else MODE_EN
        // Written synchronously, for the same reason the probe is.
        //
        // A tap on the badge that is followed immediately by a send — which is exactly how he uses
        // it, switch then talk — must have landed before the send reads it. Launched on Main from
        // Main, it would not have.
        runBlocking {
            prefs.dictate.maLanguageMode.set(next)
            // Always written now: with no auto mode, the mode IS the language.
            prefs.dictate.activeInputLanguage.set(next)
        }
    }

    fun toggle(context: Context) {
        set(context, if (active() == HR) EN else HR)
    }
}
