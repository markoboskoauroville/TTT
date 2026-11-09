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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.jvm.Volatile

/**
 * THE RE MAGIC SKILL: HOW MARKO WRITES, READ FRESH EVERY TIME, AND TAUGHT BY HIS CORRECTIONS.
 *
 * Marko, 9.11.2026: "Every time I correct something, the skill is automatically updated ... so over
 * time the RE Magic Button works flawlessly, reading the skill file, which is also readable in the
 * same Documents folder where the TTT stuff is." The skill is NOT shipped with the app: it lives on
 * the Mac (~/Downloads/API/re_ttt_skill.md) and is pushed to `Documents/TTTmini/re_ttt_skill.md`
 * over Wi-Fi (`re-skill-sync push`). The app only reads it, edits it (settings, RE Magic) and appends
 * the rules it learns to its last section.
 *
 * ### The learning loop (decided 9.11.2026; the reasoning is in DEVELOPMENT.md)
 *
 * 1. RE writes a message: [onComposed] keeps the draft (app, field, his instruction, the text).
 * 2. He edits it and sends it in the other app. Two ways the keyboard finds out, both READ-ONLY:
 *    - **A.** The accessibility service sees a click on a button that is called Send (or Pošalji,
 *      Submit, ...), and reads the focused field's text at that moment: [onSendClicked]. It never
 *      clicks anything. His send is never delayed or pressed for him.
 *    - **B.** The keyboard watches the field: the last text it saw, and then the field going empty
 *      in one step ([noteFieldText]). A field emptied that way is a sent message (or, rarely, a cut
 *      or a delete of everything; the model is told to answer NONE when the final text is not the
 *      same message).
 *    Only fields where an RE draft was used are compared, only the newest draft, only within
 *    [DRAFT_TTL_MS]. Whichever way comes first settles it; the draft is consumed.
 * 3. [settle]: same text = "accepted" (counted, nothing sent anywhere). Different = logged to
 *    `re_corrections.jsonl` and a background prompt (the model RE uses; a small one if that fails)
 *    turns the pair into ONE short rule, merged into section 7 of the skill (deduped, capped).
 * 4. Nothing leaves the phone but that prompt, to the API he already uses. The switch is in
 *    Settings, RE Magic. Off: nothing is kept, nothing compared.
 */
object MaReSkill {

    const val NAME = "RE Magic"
    const val SKILL_FILE = "re_ttt_skill.md"
    const val CORRECTIONS_FILE = "re_corrections.jsonl"

    /** The section the app writes in. The Mac file carries the same heading. */
    const val RULES_HEADING = "## 7. Corrections log → rules"

    /** A draft waits this long for its message to be sent. */
    const val DRAFT_TTL_MS = 30 * 60_000L

    /** At most this many learned rules; over it the model is asked to merge them. */
    const val RULES_MAX = 40

    /** The skill is sent in full up to this size; beyond it the middle is left out. */
    const val PROMPT_MAX_CHARS = 14_000

    /** The words of a send button, any language he uses. Matched on its label or description. */
    val SEND_WORDS = Regex(
        "^(send|sent|pošalji|posalji|pošalji poruku|send message|submit|post|reply|odgovori|objavi|" +
            "pošalji odgovor|enviar|senden|envoyer|invia)$",
        RegexOption.IGNORE_CASE,
    )

    /** Used only when no skill file is on the phone yet, so RE never writes without a voice. */
    const val FALLBACK_SKILL =
        "Write as Marko, on his phone: short, plain, spoken language, the fact first and then the ask, " +
            "English words left English, no dashes, no bullet points, no exclamation marks, at most one 🙏 " +
            "and only on WhatsApp. Never invent facts. The language of the request is the language of the message."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    /** Called once from the input method service. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    // ---------------------------------------------------------------- the files

    private fun privateDir(): File? = appContext?.filesDir?.let { File(it, "re") }

    /** `Documents/TTTmini/re_ttt_skill.md`, whether or not it exists. */
    fun skillFile(): File = File(MaVault.dir(), SKILL_FILE)

    fun correctionsFile(): File = File(MaVault.dir(), CORRECTIONS_FILE)

    /**
     * The skill as it is now, read fresh from disk: Documents first (where the Mac pushes it and
     * where he can read it), the private copy if Documents cannot be read, else the fallback.
     */
    fun read(): String {
        val docs = runCatching { skillFile().takeIf { it.length() > 0L }?.readText() }.getOrNull()
        if (!docs.isNullOrBlank()) return docs
        val own = runCatching { privateDir()?.let { File(it, SKILL_FILE) }?.takeIf { it.length() > 0L }?.readText() }.getOrNull()
        return own?.takeIf { it.isNotBlank() } ?: FALLBACK_SKILL
    }

    /** Whether a real skill file is there (the editor says so when it is not). */
    fun exists(): Boolean =
        runCatching { skillFile().length() > 0L }.getOrDefault(false) ||
            runCatching { privateDir()?.let { File(it, SKILL_FILE).length() > 0L } == true }.getOrDefault(false)

    /** Writes the skill to Documents and to the private copy. True when at least one landed. */
    @Synchronized
    fun save(text: String): Boolean {
        var ok = false
        runCatching {
            val f = skillFile()
            f.parentFile?.mkdirs()
            f.writeText(text)
            ok = true
        }
        runCatching {
            privateDir()?.let { d -> d.mkdirs(); File(d, SKILL_FILE).writeText(text); ok = true }
        }
        return ok
    }

    /** The skill as it goes into a prompt: whole, or with the oldest learned rules left out when huge. */
    fun preamble(): String {
        val skill = read().let { if (it.length <= PROMPT_MAX_CHARS) it else it.take(PROMPT_MAX_CHARS / 2) + "\n...\n" + it.takeLast(PROMPT_MAX_CHARS / 2) }
        return "Before the task, this is the skill that says how Marko writes. Follow it exactly; its last " +
            "section (his corrections) outranks everything above it.\n<skill>\n" + skill.trim() + "\n</skill>\n\n"
    }

    // ---------------------------------------------------------------- pure helpers (tested)

    /** The rules in section 7: each line that starts with "- ". */
    fun rulesOf(skill: String): List<String> {
        val i = skill.indexOf(RULES_HEADING)
        if (i < 0) return emptyList()
        return skill.substring(i + RULES_HEADING.length).lines()
            .map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }
            .filter { it.isNotEmpty() }
    }

    /** [skill] with section 7 holding exactly [rules] (the heading and its intro paragraph are kept). */
    fun withRules(skill: String, rules: List<String>): String {
        val i = skill.indexOf(RULES_HEADING)
        val head = if (i < 0) skill.trimEnd() + "\n\n$RULES_HEADING\n\nThe app appends one short rule per correction here, newest last.\n" else {
            val after = skill.substring(i + RULES_HEADING.length)
            // Keep everything in the section that is not a rule line (the intro paragraph).
            val intro = after.lines().filter { !it.trim().startsWith("- ") }.joinToString("\n").trimEnd()
            skill.substring(0, i) + RULES_HEADING + intro + "\n"
        }
        return head.trimEnd() + "\n\n" + rules.joinToString("\n") { "- $it" } + "\n"
    }

    /** [rule] normalised for comparing: lower case, letters and digits only. */
    fun key(rule: String): String = rule.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** [rules] plus [rule] unless it is already there (or already contained), newest last, capped. */
    fun mergeRule(rules: List<String>, rule: String): List<String> {
        val r = rule.trim().removePrefix("- ").trim()
        if (r.isEmpty() || r.equals("NONE", ignoreCase = true)) return rules
        val k = key(r)
        if (rules.any { key(it) == k || (k.length > 20 && key(it).contains(k)) }) return rules
        return rules + r
    }

    /** True for a click on something that sends: its label or description is a send word. */
    fun isSendLabel(vararg labels: CharSequence?): Boolean =
        labels.any { l -> l != null && SEND_WORDS.matches(l.toString().trim()) }

    /** Same text for our purpose: trimmed, whitespace collapsed. */
    fun same(a: String, b: String): Boolean = norm(a) == norm(b)

    private fun norm(s: String) = s.trim().replace(Regex("\\s+"), " ")

    // ---------------------------------------------------------------- the draft

    data class Draft(val pkg: String, val fieldId: Int, val words: String, val text: String, val at: Long)

    @Volatile
    var draft: Draft? = null
        private set

    /** What the field held when we last looked, for fallback B. */
    @Volatile
    private var seen: Triple<String, Int, String>? = null

    fun learningOn(): Boolean = runCatching {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maReLearn.get()
    }.getOrDefault(true)

    /** A draft is waiting: the accessibility service and the keyboard only look when this is true. */
    fun pending(now: Long = System.currentTimeMillis()): Boolean {
        val d = draft ?: return false
        if (now - d.at > DRAFT_TTL_MS) { draft = null; return false }
        return learningOn()
    }

    /** RE has just written [text] into the field ([pkg], [fieldId]) for the instruction [words]. */
    fun onComposed(pkg: String?, fieldId: Int, words: String, text: String) {
        if (pkg == null || pkg == appContext?.packageName || !learningOn() || text.isBlank()) return
        draft = Draft(pkg, fieldId, words, text, System.currentTimeMillis())
        seen = Triple(pkg, fieldId, text)
        MaLog.add("re", "draft kept for $pkg (${text.length} chars)")
    }

    /**
     * Fallback B, from the keyboard: the field ([pkg], [fieldId]) now holds [text]. A field that
     * held a long text and is now empty in one step has been sent.
     */
    fun noteFieldText(pkg: String?, fieldId: Int, text: String) {
        if (pkg == null || !pending()) return
        val d = draft ?: return
        if (d.pkg != pkg) return
        val last = seen
        if (text.isBlank() && last != null && last.first == pkg && last.second == fieldId && last.third.length >= MIN_CLEARED) {
            settle(pkg, last.third, "cleared")
            return
        }
        if (text.isNotBlank()) seen = Triple(pkg, fieldId, text)
    }

    /** Fallback B needs at least this much text to call an emptied field a sent message. */
    const val MIN_CLEARED = 12

    /**
     * Method A, from the accessibility service: a click on a send button in [pkg]; [fieldText] is
     * the focused field read at that instant (read only, nothing is clicked or changed).
     */
    fun onSendClicked(pkg: String?, fieldText: String?) {
        if (pkg == null || !pending()) return
        val d = draft ?: return
        if (d.pkg != pkg) return
        val text = fieldText?.takeIf { it.isNotBlank() } ?: seen?.takeIf { it.first == pkg }?.third ?: return
        settle(pkg, text, "send-click")
    }

    // ---------------------------------------------------------------- compare, log, learn

    @Synchronized
    private fun settle(pkg: String, finalText: String, how: String) {
        val d = draft ?: return
        if (d.pkg != pkg) return
        draft = null
        seen = null
        val changed = !same(d.text, finalText)
        scope.launch {
            runCatching { logLine(d, finalText, how, changed) }
            // A word he changes the same way twice becomes a learned spelling pair (MaSpelling).
            if (changed) runCatching { MaSpelling.noteEdit(d.text, finalText) }
            if (changed) runCatching { learn(d, finalText) }
        }
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(Date())

    private fun logLine(d: Draft, finalText: String, how: String, changed: Boolean) {
        val o = JSONObject()
            .put("at", stamp()).put("app", d.pkg).put("how", how).put("changed", changed)
            .put("words", d.words).put("draft", d.text).put("final", finalText)
        val f = correctionsFile()
        runCatching { f.parentFile?.mkdirs(); f.appendText(o.toString() + "\n") }
        runCatching { privateDir()?.let { dir -> dir.mkdirs(); File(dir, CORRECTIONS_FILE).appendText(o.toString() + "\n") } }
    }

    /** The newest [n] log lines as (changed, draft, final, when), newest first. */
    fun recent(n: Int = 8): List<JSONObject> {
        val lines = runCatching { correctionsFile().takeIf { it.exists() }?.readLines() }.getOrNull()
            ?: runCatching { privateDir()?.let { File(it, CORRECTIONS_FILE) }?.takeIf { it.exists() }?.readLines() }.getOrNull()
            ?: return emptyList()
        return lines.takeLast(n).reversed().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    }

    fun counts(): Pair<Int, Int> {
        val lines = runCatching { correctionsFile().takeIf { it.exists() }?.readLines() }.getOrNull() ?: return 0 to 0
        var changed = 0
        var kept = 0
        for (l in lines) {
            val o = runCatching { JSONObject(l) }.getOrNull() ?: continue
            if (o.optBoolean("changed")) changed++ else kept++
        }
        return changed to kept
    }

    /** What the model is asked about one correction. */
    fun learnPrompt(d: Draft, finalText: String, rules: List<String>): String =
        "You maintain the writing skill of Marko's keyboard. A message was written for him from his " +
            "spoken instruction, he corrected it, and sent it. Work out what the correction teaches.\n\n" +
            "<instruction>\n${d.words}\n</instruction>\n\n<written_by_the_app>\n${d.text}\n</written_by_the_app>\n\n" +
            "<sent_by_marko>\n$finalText\n</sent_by_marko>\n\n" +
            "<rules_so_far>\n" + rules.joinToString("\n") { "- $it" } + "\n</rules_so_far>\n\n" +
            "Reply with ONE new rule, one line, at most 25 words, in English, general enough to apply to " +
            "future messages (a word he prefers, a phrase he cuts, his length, his punctuation, his " +
            "greeting or sign-off, a fact he fixed such as a spelling of a name). Quote the exact " +
            "words when it is about a word. If the correction is only a change of content for this one " +
            "message, or the sent text is not the same message as the written one, or a rule already " +
            "covers it, reply exactly NONE. No preamble, no quotes around the rule, no dash at the start."

    private fun mergePrompt(rules: List<String>): String =
        "These are the learned rules of Marko's writing skill. Merge duplicates and near duplicates, " +
            "keep every distinct lesson, keep exact quoted words, keep the order of time (newest last), " +
            "and return at most 30 lines, one rule per line, each starting with \"- \". Nothing else.\n\n" +
            rules.joinToString("\n") { "- $it" }

    private suspend fun learn(d: Draft, finalText: String) {
        val skill = read()
        val rules = rulesOf(skill)
        val answer = DictateController.askModel(learnPrompt(d, finalText, rules), "RE learning") ?: run {
            MaLog.add("re", "learning skipped: no answer")
            return
        }
        var updated = mergeRule(rules, answer.lines().firstOrNull { it.isNotBlank() }.orEmpty())
        if (updated.size > RULES_MAX) {
            val merged = DictateController.askModel(mergePrompt(updated), "RE merge")
                ?.lines()?.map { it.trim() }?.filter { it.startsWith("- ") }?.map { it.removePrefix("- ").trim() }
                ?.takeIf { it.isNotEmpty() && it.size < updated.size }
            updated = merged ?: updated.takeLast(RULES_MAX)
        }
        if (updated != rules) {
            // Read again: the Mac may have pushed a new file while the model was thinking.
            val fresh = read()
            val now = rulesOf(fresh)
            val final = if (now == rules) updated else mergeRule(now, updated.last())
            save(withRules(fresh, final))
            MaLog.add("re", "skill learned a rule (${final.size} in all)")
        }
    }

    /** Forget every learned rule; the rest of the skill stays. */
    fun forgetRules(): Boolean = save(withRules(read(), emptyList()))
}
