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
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TRAINING MODE: Opus supervises Qwen on everything RE does (Marko, 13.11.2026): *"We can call it training mod. And
 * flip this switch on ... set up Claude Opus to be supervisor of Groq ... First, Opus is reading and then asking the
 * question to Grog Queen to do the work. Then it looks to the output, assess the output and says to the Grog this is
 * not successful, you need to improve your skills. And then it gives the skills ... Let's put all TTTs in all of my
 * phones in training mod while using RE."*
 *
 * **On by default** on every phone (a switch in the RE key's long-press panel). Needs RE's Qwen key; without one RE
 * works as before with its chosen engine.
 *
 * One RE request in training:
 * 1. **Opus reads** the request (with the whole context RE has) and writes Qwen a short brief: what is asked, what a
 *    good answer must have, the traps.
 * 2. **Qwen works**: its system prompt, its skills file (re/QWEN_SKILLS.md, read on every Qwen call, also outside
 *    training) and the brief.
 * 3. **Opus checks** the answer: passes, or says what is wrong and writes ONE new skill for Qwen. The skill goes into
 *    QWEN_SKILLS.md at once (synced to GitHub with RE's memory).
 * 4. Not passed: **Qwen tries again** with the critique and the skill, and Opus checks once more. Still not passed:
 *    Marko gets **Opus's own corrected answer**, so a weak answer never reaches him.
 *
 * Every round is a line in re/TRAINING_LOG.md (date, scores, the skill), so it is visible whether Qwen improves.
 */
object MaReTraining {
    const val SKILLS = "QWEN_SKILLS.md"
    const val LOG = "TRAINING_LOG.md"
    private const val PREFS = "ma_re_training"
    private const val MAX_SKILLS_CHARS = 6_000

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** On unless he switched it off (13.11.2026: "flip this switch on ... all TTTs in all of my phones"). */
    fun on(c: Context): Boolean = sp(c).getBoolean("on", true)
    fun setOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("on", v).apply()

    /** Training runs RE's work when it is on and Qwen has a key. */
    fun active(c: Context): Boolean = on(c) && MaReEngine.hasQwenKey(c)

    private fun file(name: String) = File(MaReChat.dir(), name)

    /** Qwen's skills, given to every Qwen call (empty until Opus writes the first). */
    fun skills(): String = runCatching { file(SKILLS).readText() }.getOrDefault("").trim()

    @Volatile var lastSummary: String = ""
        private set

    private fun addSkill(skill: String) {
        val s = skill.trim().removePrefix("-").trim()
        if (s.length < 8) return
        val f = file(SKILLS)
        val cur = runCatching { f.readText() }.getOrDefault("# Qwen skills\n\nWritten by Opus while supervising Qwen (training mode). Qwen reads these on every answer.\n")
        if (cur.lowercase().contains(s.lowercase().take(60))) return
        var next = cur.trimEnd() + "\n- " + SimpleDateFormat("d.M.yyyy", Locale.ROOT).format(Date()) + " " + s + "\n"
        // Kept short: the oldest skills go first when it grows past the limit (Opus repeats what still matters).
        while (next.length > MAX_SKILLS_CHARS) {
            val lines = next.lines().toMutableList()
            val first = lines.indexOfFirst { it.startsWith("- ") }
            if (first < 0) break
            lines.removeAt(first)
            next = lines.joinToString("\n")
        }
        runCatching { f.writeText(next) }
        MaReChat.synced()
    }

    private fun log(task: String, s1: Int, s2: Int?, skill: String, outcome: String) {
        val line = "- " + SimpleDateFormat("d.M.yyyy HH:mm", Locale.ROOT).format(Date()) +
            " · first $s1/10" + (s2?.let { " · second $it/10" } ?: "") + " · $outcome" +
            (if (skill.isNotBlank()) " · skill: " + skill.take(160) else "") + " · task: " + task.replace("\n", " ").take(80) + "\n"
        runCatching {
            val f = file(LOG)
            if (!f.exists()) f.writeText("# RE training log\n\nOpus supervising Qwen: one line per request.\n\n")
            f.appendText(line)
        }
        lastSummary = line.trim()
        MaReChat.synced()
    }

    /** True while Opus is being asked as supervisor: RE's Qwen routing must let these calls through to Opus. */
    @Volatile var supervising = false
        private set

    private suspend fun opus(c: Context, prompt: String): String {
        supervising = true
        try { return DictateController.reChatRaw(c, prompt, MaReEngine.OPUS_MODEL).trim() } finally { supervising = false }
    }

    private data class Verdict(val ok: Boolean, val score: Int, val problems: String, val skill: String, val better: String)

    private suspend fun assess(c: Context, task: String, brief: String, answer: String): Verdict {
        val raw = opus(
            c,
            "You supervise Qwen, a smaller model doing the work of RE, a writing assistant in Marko's phone keyboard. You are " +
                "training it. Below: the task (with all its rules and context), your brief, and Qwen's answer.\n" +
                "Judge the answer against the task's rules: right language, a <draft> only when a message to send is wanted, " +
                "only facts given, the person's style, short and plain. A GitHub look-up tag (<github_search> / <github_read>) " +
                "as the whole answer is a valid step when the task needs repository facts.\n" +
                "Answer ONLY with JSON: {\"ok\": true|false, \"score\": 0-10, \"problems\": \"what is wrong, one or two sentences\", " +
                "\"skill\": \"ONE general rule that would have prevented this, written to Qwen, or empty when ok\", " +
                "\"better\": \"when not ok: the full corrected answer exactly as RE should give it (same format), else empty\"}\n\n" +
                "<task>\n" + task.takeLast(12_000) + "\n</task>\n<brief>\n" + brief + "\n</brief>\n<qwen_answer>\n" + answer + "\n</qwen_answer>",
        )
        val j = runCatching { JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)) }.getOrNull()
            ?: return Verdict(true, 7, "", "", "")   // an unreadable verdict never blocks his answer
        return Verdict(
            j.optBoolean("ok", true), j.optInt("score", 0), j.optString("problems"), j.optString("skill"), j.optString("better"),
        )
    }

    /**
     * One RE request in training: Opus briefs, Qwen works, Opus checks and teaches; returns the answer for Marko.
     * [system] and [task] are what RE would give Qwen anyway.
     */
    suspend fun run(c: Context, system: String, task: String, status: (String) -> Unit): String {
        status("RE · training · Opus reads the request")
        val brief = runCatching {
            opus(
                c,
                "You supervise Qwen, a smaller model that does the work of RE (a writing assistant in Marko's keyboard). Read the " +
                    "task below and write Qwen a brief of at most 100 words: what exactly is asked, what a good answer must " +
                    "contain, which of the task's rules matter most here, and the traps. Plain text, no answer to the task itself.\n\n" +
                    "<task>\n" + task.takeLast(12_000) + "\n</task>",
            )
        }.getOrDefault("")
        val sys = system + (if (brief.isNotBlank()) "\n\nYour supervisor's brief for this request:\n$brief" else "")

        status("RE · training · Qwen works")
        val first = MaReEngine.groqAsk(c, sys, task, status)
        status("RE · training · Opus checks")
        val v1 = runCatching { assess(c, task, brief, first) }.getOrElse { Verdict(true, 0, "", "", "") }
        if (v1.ok) { log(task, v1.score, null, "", "passed"); return first }
        addSkill(v1.skill)

        status("RE · training · Qwen improves")
        val second = runCatching {
            MaReEngine.groqAsk(
                c,
                sys + "\n\nYour first answer was not good enough. Supervisor: " + v1.problems +
                    (if (v1.skill.isNotBlank()) "\nNew skill: " + v1.skill else "") +
                    "\nYour first answer was:\n" + first + "\nWrite the whole answer again, better.",
                task, status,
            )
        }.getOrNull()
        if (second != null) {
            status("RE · training · Opus checks again")
            val v2 = runCatching { assess(c, task, brief, second) }.getOrElse { Verdict(true, 0, "", "", "") }
            if (v2.ok) { log(task, v1.score, v2.score, v1.skill, "passed on the second try"); return second }
            addSkill(v2.skill)
            log(task, v1.score, v2.score, listOf(v1.skill, v2.skill).filter { it.isNotBlank() }.joinToString(" | "), "Opus answered")
            return v2.better.ifBlank { v1.better }.ifBlank { second }
        }
        log(task, v1.score, null, v1.skill, "Opus answered")
        return v1.better.ifBlank { first }
    }
}
