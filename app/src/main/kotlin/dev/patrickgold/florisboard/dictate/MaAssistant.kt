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
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.overlay.MaScreenChat
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

    /** One line about the app in front, so RE adapts: chat messages, mail, or his own prompts to Claude. */
    fun appHint(): String {
        val p = MaReprompt.lastPkg ?: return ""
        val what = when {
            p.startsWith("com.whatsapp") -> "WhatsApp: the screen holds chat messages"
            p == "com.google.android.gm" -> "Gmail: the screen holds email (sender, subject, text)"
            p == "com.anthropic.claude" -> "the Claude app: the text is Marko's own prompts to Claude"
            p.contains("telegram") || p.contains("messenger") || p.contains("signal") -> "a messenger: the screen holds chat messages"
            else -> "an app ($p)"
        }
        return "The app in front is $what.\n"
    }

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

    /** Commands the keyboard's RE key hears as assistant questions (read-only), besides the reply flow. */
    private val ASK = Regex(
        "read (me )?(the )?(last|latest|recent|new|unread) (messages|emails|mails)|read (me )?(this|the) (email|mail|chat|conversation)|" +
            "who (wrote|messaged|sent)( last| the last)?|what'?s on (the |my )?screen|what is on (the |my )?screen|summari[sz]e (this|the) (chat|conversation|email|mail|screen)|" +
            "pro[cč]itaj (mi )?(zadnje|posljednje|nove|ovu) (poruke|mailove|e-?mailove|poruku|e-?mail)|tko je (zadnji )?pisao|[sš]to je na ekranu|sa[zž]mi (ovaj )?(chat|razgovor)",
        RegexOption.IGNORE_CASE,
    )

    fun wantsKeyboard(words: String) = ASK.containsMatchIn(words)

    fun isReply(words: String) = REPLY.containsMatchIn(words.trim())

    /** Handles one spoken assistant command. Always ends in a pop-up; never throws. */
    suspend fun handle(context: Context, words: String, overlay: Boolean = true) {
        val app = context.applicationContext
        if (overlay) {
            MaReprompt.noteField(DictateAccessibilityService.frontPackage(), 0)
            viaOverlay = true
        }
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
        MaReResult.note(app, clean)   // the note is spoken by MaVoice (Reader voice, Voice Engine subtitles)
    }

    /** What the model is asked: answer his request from the screen, short, in his language, fit to be spoken. */
    fun askPrompt(words: String, screen: MaScreenRead.Screen): String =
        "You are RE, Marko's voice assistant on his phone. Answer his request using only the screen below, read just now " +
            "(${screen.how}). Each line starts with where it sits: [left] is the other person, [right] is Marko, [center] a " +
            "date or notice; top to bottom, the newest is last. Answer in the language he used, plainly, for listening: " +
            "no lists, no symbols, no markdown, short. To read messages aloud say who wrote and what, oldest of the last " +
            "few first. Never invent anything that is not on the screen; if it is not there, say so in one sentence. " +
            "Never offer to send, buy or delete.\n\n<request>\n${words.trim()}\n</request>\n\n<screen>\n${screen.text}\n</screen>"
}
