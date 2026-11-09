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

import android.content.Context
import android.speech.tts.TextToSpeech
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.overlay.MaScreenChat
import java.util.Locale
import kotlin.jvm.Volatile

/**
 * RE MAGIC AS AN ASSISTANT, SLICE 1 (Marko, 9.11.2026): "It's like a Siri for WhatsApp, or email, or whatever is in front."
 * (RE_ASSISTANT_PLAN.md)
 *
 * **Trigger when no text field is focused:** the Quick Settings tile "RE" ([MaAssistantTile]) and the accessibility
 * button of the TTT service. A press starts the RE recorder (AssemblyAI only), a second press stops it; the transcript
 * comes to [handle]. The keyboard's RE key stays the way in when a field is focused.
 *
 * **What it does with the words**, over whatever app is in front, from the accessibility tree ([MaScreenRead]):
 * - a reply ("reply to the latest message saying ...", "odgovori ...", "answer ..."): a draft in the pop-up; OK inserts
 *   it into the field behind; sending is Marko's own tap, never RE;
 * - anything else ("read me the last messages", "pročitaj zadnje poruke", "who wrote last", "what's on the screen",
 *   "summarise this chat", "read this email"): the screen goes to the cheap model with his words, and the answer is shown
 *   in the pop-up AND spoken with the phone's text to speech. Read only.
 *
 * **Safety.** RE never sends, buys, deletes or changes a setting here: the only things it does are read the screen,
 * speak, and show a pop-up. Password fields are never read ([MaScreenChat] skips them). Banking apps are refused.
 */
object MaAssistant {

    /** Armed by the tile or the button: the next RE transcript is an assistant command. */
    @Volatile
    private var armed = false

    fun arm() { armed = true }

    /** True once if armed: the transcript belongs to the assistant. */
    fun take(): Boolean { val a = armed; armed = false; return a }

    private val REPLY = Regex("^(please )?(reply|answer|respond)\\b|\\bodgovori\\b|\\bnapi[sš]i (mu|joj|im|odgovor)\\b", RegexOption.IGNORE_CASE)

    /** Apps RE will not read: banking and payments. */
    private val REFUSED = setOf(
        "com.revolut.revolut", "hr.erstebank.mobile", "hr.pbz.mobile", "com.google.android.apps.walletnfcrel",
        "com.paypal.android.p2pmobile", "com.zaba.mbanking", "hr.addiko.mobile",
    )

    /** True while the last command came from the assistant (the result is inserted without the keyboard). */
    @Volatile
    var viaOverlay = false
        private set

    /** The keyboard's RE key was used: results go back through the keyboard field. */
    fun markKeyboard() { viaOverlay = false }

    /** The tile and the accessibility button: start listening, or stop and run the command. */
    fun toggle(context: Context) {
        MaLanguage.set(context, MaLanguage.EN)
        arm()
        DictateController.onMicClick(
            context, target = DictateController.OutputTarget.OVERLAY, reprompt = true, engine = MaProviders.ENGINE_ASSEMBLYAI,
        )
    }

    fun isReply(words: String) = REPLY.containsMatchIn(words.trim())

    /** Handles one spoken assistant command. Always ends in a pop-up; never throws. */
    suspend fun handle(context: Context, words: String) {
        val app = context.applicationContext
        MaReprompt.noteField(DictateAccessibilityService.frontPackage(), 0)
        viaOverlay = true
        val pkg = MaReprompt.lastPkg
        if (pkg != null && (pkg in REFUSED || pkg.contains("bank", true))) {
            MaReResult.note(app, "RE does not read banking or payment apps.")
            return
        }
        val screen = MaScreenRead.read()
        if (screen == null) {
            MaReResult.note(app, "Could not read the screen: the app in front showed no readable text. Nothing was done.")
            return
        }
        if (isReply(words)) {
            // A draft: the usual RE path, over the accessibility sink, with the screen as context.
            DictateController.applyPrompt(
                app, MaReprompt.prompt(words, write = true, screen = screen),
                target = DictateController.OutputTarget.OVERLAY, model = MaFlow.model(), allowGrowth = true, bare = true,
            )
            return
        }
        val answer = kotlinx.coroutines.withTimeoutOrNull(MaReprompt.RE_TIMEOUT_MS) {
            DictateController.askModel(askPrompt(words, screen), "RE assistant")
        }
        if (answer.isNullOrBlank()) {
            MaReResult.note(app, "The model did not answer. Nothing was done.")
            return
        }
        val clean = answer.trim()
        MaReResult.note(app, clean)
        speak(app, clean)
    }

    /** What the model is asked: answer his request from the screen, short, in his language, fit to be spoken. */
    fun askPrompt(words: String, screen: MaScreenRead.Screen): String =
        "You are RE, Marko's voice assistant on his phone. Answer his request using only the screen below, read just now " +
            "(${screen.how}). Each line starts with where it sits: [left] is the other person, [right] is Marko, [center] a " +
            "date or notice; top to bottom, the newest is last. Answer in the language he used, plainly, for listening: " +
            "no lists, no symbols, no markdown, short. To read messages aloud say who wrote and what, oldest of the last " +
            "few first. Never invent anything that is not on the screen; if it is not there, say so in one sentence. " +
            "Never offer to send, buy or delete.\n\n<request>\n${words.trim()}\n</request>\n\n<screen>\n${screen.text}\n</screen>"

    // ---------------------------------------------------------------- speaking

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    private fun speak(context: Context, text: String) {
        val lang = MaRewordPrompt.languageOf(text)
        val locale = if (lang != null && lang.startsWith("Croatian", true)) Locale("hr", "HR") else Locale.getDefault()
        val say = { t: TextToSpeech ->
            runCatching { t.language = locale }
            t.speak(text.take(1500), TextToSpeech.QUEUE_FLUSH, null, "re-assistant")
        }
        val cur = tts
        if (cur != null && ttsReady) { say(cur); return }
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) { ttsReady = true; created?.let { say(it) } }
        }
        tts = created
    }
}
