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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * THE RE ENGINE COMPARISON (Marko, 13.11.2026): "over ADB we run a comparison of Queen and Anthropic corrections and any
 * possible scenario when I am dictating and asking for corrections. Just invent some person and some chat."
 *
 * Invented people and chats only (Ana Kovač in Croatian, Tom Becker in English); the real memory files are READ (so the
 * comparison is what RE really does with his lessons and skills) but nothing is written: no chat, no lesson, no person
 * file, no pref (the engine is set through [MaReEngine.override] for the run and cleared after). Each scenario goes
 * through each engine with the exact prompt the chat uses; every answer, its time and simple checks land in
 * files/re_compare.json for the comparison over adb. Started only by the adb broadcast dev.ttt.engines.RE_COMPARE.
 */
object MaReCompare {

    data class Scenario(
        val id: String,
        val ctx: MaReChat.Ctx,
        /** The chat so far, his turns and RE's; the last one is his. */
        val turns: List<Pair<Boolean, String>>,
        val wantDraft: Boolean,
        /** "hr" or "en": the language the answer should be in. */
        val lang: String,
        val note: String,
    )

    private val ANA_SCREEN = MaScreenRead.Screen(
        "[center] Danas\n" +
            "[left] Ana Kovač: Bok! Jesi za kavu sutra oko 10 u Kvartu?\n" +
            "[right] Može, samo da vidim kad završavam sastanak\n" +
            "[left] Ana Kovač: Super. Usput, jesi poslao onaj ponudu za radionicu?\n" +
            "[left] Ana Kovač: Klijent pita do petka",
        "invented",
    )
    private const val ANA_NOTES = "# Ana Kovač\n\n## Style with Ana Kovač\n- Croatian, informal (ti), short messages, 'Bok' and 'Pozdrav', sometimes a smiley.\n\n" +
        "## Conversations\n- 10.11.2026 workshop offer for her client, Marko promised it this week.\n\n## Flow\n- coffee tomorrow at 10 in Kvart; the offer is still open."

    private val TOM_SCREEN = MaScreenRead.Screen(
        "[center] Today\n" +
            "[left] Tom Becker: Hi Marko, are we still on for the mixing session on Friday?\n" +
            "[left] Tom Becker: I can do any time after lunch\n" +
            "[right] Let me check the studio\n" +
            "[left] Tom Becker: Also, did you get the stems I sent?",
        "invented",
    )
    private const val TOM_NOTES = "# Tom Becker\n\n## Style with Tom Becker\n- English, friendly and direct, first names, no emojis.\n\n" +
        "## Conversations\n- 8.11.2026 Santoshi concert mix, Tom sends stems.\n\n## Flow\n- Friday mixing session to confirm; stems arrived?"

    private val ANA = MaReChat.Ctx("com.whatsapp", "Ana Kovač", ANA_SCREEN, ANA_NOTES, older = false)
    private val TOM = MaReChat.Ctx("com.whatsapp", "Tom Becker", TOM_SCREEN, TOM_NOTES, older = false)

    val SCENARIOS = listOf(
        Scenario("hr_late", ANA, listOf(true to "odgovori ani da kasnim deset minuta oprosti"), true, "hr", "short Croatian reply, informal"),
        Scenario("hr_offer", ANA, listOf(true to "napiši ani da ću ponudu poslati sutra do podne i da sutra kava u 10 može"), true, "hr", "two facts, both from his words"),
        Scenario("hr_fix", ANA, listOf(true to "ispravi moju poruku: sutra cu doci u pola sedam i donjet cu kolac za sve"), true, "hr", "spelling fix only: ću, doći, donijet ću"),
        Scenario("hr_formal", ANA, listOf(true to "napiši joj formalnije da ne mogu doći na sastanak u četvrtak"), true, "hr", "formal register (Vi), same fact"),
        Scenario(
            "hr_shorter", ANA,
            listOf(
                true to "odgovori ani da ću ponudu poslati sutra jer sam danas cijeli dan u studiju i nisam stigao",
                false to "<draft>Bok Ana! Ponudu ti šaljem sutra, danas sam cijeli dan u studiju pa nisam stigao. Vidimo se na kavi!</draft>",
                true to "kraće",
            ),
            true, "hr", "follow-up: shorter, keep the facts",
        ),
        Scenario("en_friday", TOM, listOf(true to "reply to tom that friday works but only after 3pm and yes i got the stems"), true, "en", "two facts"),
        Scenario("en_fix", TOM, listOf(true to "correct my message: i will send you the invoce tommorow morning befor ten"), true, "en", "spelling fix only"),
        Scenario("en_dictation", TOM, listOf(true to "tell tom the creation version of the song is ready and vesper transcribed the lyrics"), true, "en", "dictation slips: creation=Croatian, Vesper=Whisper"),
        Scenario("en_noinvent", TOM, listOf(true to "send tom the address of the studio"), false, "en", "address not known: must ask, not invent"),
        Scenario("en_question", TOM, listOf(true to "what did tom ask me last"), false, "en", "a question about the screen, no draft"),
        Scenario("en_apology", TOM, listOf(true to "tom is upset that i missed the session last week without telling him, write a sincere apology, not too long, offer friday"), true, "en", "delicate, Auto should pick Opus"),
        Scenario("hr_translate", ANA, listOf(true to "prevedi na engleski: Hvala na pozivu, javit ću se sutra ujutro."), true, "en", "translation to English"),
        Scenario(
            "en_casual", TOM,
            listOf(
                true to "reply to tom that friday works",
                false to "<draft>Dear Tom, I am pleased to confirm that Friday is suitable for our mixing session.</draft>",
                true to "too formal, more casual",
            ),
            true, "en", "correction: casual tone",
        ),
    )

    private val HR_WORDS = Regex("\\b(je|sam|ću|ćeš|sutra|može|hvala|bok|da|ne|i|na|za|se|ti|vam|kava|ponudu)\\b", RegexOption.IGNORE_CASE)
    private val EN_WORDS = Regex("\\b(the|and|is|you|i|to|on|for|will|friday|thanks|got)\\b", RegexOption.IGNORE_CASE)

    fun langOf(t: String): String {
        val hr = HR_WORDS.findAll(t).count() + Regex("[čćžšđČĆŽŠĐ]").findAll(t).count()
        val en = EN_WORDS.findAll(t).count()
        return if (hr > en) "hr" else "en"
    }

    suspend fun run(c: Context, engines: List<String>, only: Set<String>?, out: (String) -> Unit): File {
        val file = File(c.getExternalFilesDir(null), "re_compare.json")
        val all = JSONArray()
        val scenarios = SCENARIOS.filter { only == null || it.id in only }
        try {
            for (sc in scenarios) for (e in engines) {
                MaReEngine.override = e
                MaReEngine.lastChoice = ""
                val msgs = sc.turns.mapIndexed { i, (me, t) -> MaReChat.Msg(me, t, i.toLong()) }
                val t0 = System.currentTimeMillis()
                val r = runCatching { MaReChat.ask(c, msgs, sc.ctx) { } }
                val ms = System.currentTimeMillis() - t0
                val ans = r.getOrNull().orEmpty()
                val m = MaReChat.Msg(false, ans)
                val draft = m.draft
                val checked = draft ?: m.shown
                val o = JSONObject()
                    .put("scenario", sc.id).put("engine", e).put("model", if (e == MaReEngine.QWEN) "qwen" else MaReEngine.lastChoice.ifEmpty { e })
                    .put("ms", ms).put("ok", r.isSuccess).put("error", r.exceptionOrNull()?.message ?: "")
                    .put("answer", ans).put("draft", draft ?: JSONObject.NULL)
                    .put("wantDraft", sc.wantDraft).put("hasDraft", draft != null)
                    .put("lang", langOf(checked)).put("wantLang", sc.lang)
                    .put("think", ans.contains("<think", true)).put("markdown", Regex("(^|\\n)\\s*([-*#]|\\d+\\.)\\s").containsMatchIn(ans) || ans.contains("**"))
                    .put("drafts", Regex("<draft>", RegexOption.IGNORE_CASE).findAll(ans).count())
                    .put("note", sc.note)
                all.put(o)
                out("${sc.id} $e ${ms}ms ok=${r.isSuccess} draft=${draft != null}/${sc.wantDraft} lang=${langOf(checked)}/${sc.lang}")
                file.writeText(all.toString(1))
            }
        } finally {
            MaReEngine.override = null
        }
        return file
    }
}
