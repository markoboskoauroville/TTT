/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.nlp

import android.content.Context
import android.text.InputType
import android.view.inputmethod.ExtractedTextRequest
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.MaLog
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.jvm.Volatile

/**
 * Watches the last dictation in the field, learns what he corrects in it, and keeps the rules.
 *
 * The decisions are all in [MaCorrectionRules]; this is where they meet the keyboard: the dictation
 * as it went in, the field as it is later, the file the rules live in, and the personal model that
 * must stop offering the wrong word.
 *
 * Nothing leaves the phone and nothing costs a request. The rules are one small file in the app's
 * own folder; only how many were learned is logged, never the words, which are his writing.
 */
object MaDictationCorrections {

    private const val FILE = "ma_dictation_corrections.tsv"

    /** How far past the dictation the field is read: room for a longer word put in its place. */
    private const val SLACK = 80

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var file: File? = null

    /** This phone's own rules (the .tsv): what it learned and what he typed in here. */
    private val _rules = MutableStateFlow<List<MaCorrectionRules.Rule>>(emptyList())

    /**
     * What is applied and listed: the union of this phone's rules and the other devices' (MANTRA_CORRECTIONS, 21.11.2026,
     * [MaCorrectionsSync]). Without a token or before the first sync it is just this phone's rules.
     */
    private val _effective = MutableStateFlow<List<MaCorrectionRules.Rule>>(emptyList())

    /** Every rule, learned and his own, from every device. The settings screen draws from this. */
    val rules: StateFlow<List<MaCorrectionRules.Rule>> = _effective.asStateFlow()

    private fun recompute() {
        val local = _rules.value
        scope.launch { _effective.value = runCatching { MaCorrectionsSync.effective(local) }.getOrDefault(local) }
    }

    /** A dictation as it went into the field, and where. */
    private class Pending(
        val text: String,
        val start: Int,
        val before: String,
        val language: String,
        val field: String,
        val clip: Pair<String, String>? = null,
    )

    @Volatile
    private var appCtx: Context? = null

    private fun appContextOrNull(): Context? = appCtx

    @Volatile
    private var pending: Pending? = null

    fun initialize(context: Context) {
        appCtx = context.applicationContext
        val f = File(context.filesDir, FILE)
        file = f
        MaCorrectionsSync.attach(context)
        MaCorrectionsSync.onRemoteChanged = { recompute() }
        scope.launch {
            _rules.value = MaCorrectionRules.parse(runCatching { f.readText() }.getOrNull())
            _effective.value = _rules.value
            // The first time: every rule this phone already learned goes to the shared file (one line each).
            runCatching { MaCorrectionsSync.recordLocal(_rules.value) }
            recompute()
            MaCorrectionsSync.maybePull()
        }
    }

    private fun enabled(): Boolean {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maCorrectionsEnabled.get()
    }

    private fun fieldOf(info: FlorisEditorInfo): String =
        "${info.packageName}:${info.base.fieldId}:${info.base.inputType}"

    private fun isSecret(info: FlorisEditorInfo): Boolean {
        val t = info.base.inputType
        val variation = t and InputType.TYPE_MASK_VARIATION
        return when (t and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * Remembers [text], just written into the field by dictation from [start] (absolute), with
     * [textBefore] the field's text before it, both read just before the commit.
     */
    fun noteDictation(text: String, start: Int, textBefore: String, info: FlorisEditorInfo, incognito: Boolean) {
        pending = null
        // The clip buffer's reference for the newest clip: what really went into the field.
        runCatching {
            val ctx = appContextOrNull()
            if (ctx != null) {
                if (incognito || isSecret(info)) dev.patrickgold.florisboard.dictate.engines.ClipBuffer.discardLast(ctx)
                else dev.patrickgold.florisboard.dictate.engines.ClipBuffer.setLastReference(ctx, text)
            }
        }
        if (!enabled() || incognito || isSecret(info) || text.isBlank()) return
        if (start < 0) return
        pending = Pending(text, start, textBefore.takeLast(24), MaLanguage.active(), fieldOf(info), dev.patrickgold.florisboard.dictate.engines.ClipBuffer.lastRef())
    }

    /** Forgets the dictation being watched, without learning from it: Ctrl+P, Ctrl+F and the like rewrote it. */
    fun drop() {
        pending = null
    }

    /**
     * Reads the field again where the last dictation went in, and learns whatever he corrected there.
     * Called when the next dictation is about to be written, before a prompt rewrites the text, and
     * when the field closes. Once per dictation: what is watched is let go here, learned or not.
     */
    fun settle(info: FlorisEditorInfo) {
        val p = pending ?: return
        pending = null
        if (!enabled() || fieldOf(info) != p.field) return
        val now = runCatching { readRegion(p) }.getOrNull() ?: return
        val swaps = MaCorrectionRules.diff(p.text, now)
        // His final text becomes the clip's reference, so the benchmark scores against what he sent.
        if (p.clip != null) runCatching {
            appContextOrNull()?.let { dev.patrickgold.florisboard.dictate.engines.ClipBuffer.setReference(it, p.clip.first, p.clip.second, now.trim()) }
        }
        if (swaps.isEmpty()) return
        var next = _rules.value
        for (s in swaps) next = MaCorrectionRules.record(next, s, p.language)
        _rules.value = next
        save()
        // The typing side learns it too: the wrong words leave the personal model, and a one-word
        // fix is offered first when he types the wrong word (MaNgram.learnFix).
        for (s in swaps) MaNgram.learnFix(s.wrong, s.right, p.language)
        MaLog.add("corrections", "learned ${swaps.size} from the last dictation")
    }

    /** The stretch of the field that began where [p] was written, or null when it cannot be found. */
    private fun readRegion(p: Pending): String? {
        val ic = FlorisImeService.currentInputConnection() ?: return null
        val et = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 20000 }, 0) ?: return null
        val all = et.text?.toString() ?: return null
        var at = p.start - et.startOffset
        // Text written or deleted above the dictation moves it; the words just before it say where.
        if (at < 0 || at > all.length || !all.substring(0, at).endsWith(p.before)) {
            if (p.before.isEmpty()) return null
            val from = (at - 400).coerceAtLeast(0)
            val found = all.indexOf(p.before, from)
            if (found < 0) return null
            at = found + p.before.length
        }
        return all.substring(at, (at + p.text.length + SLACK).coerceAtMost(all.length))
    }

    /** [text] with his corrections applied, in the language being written. */
    fun apply(text: String): String {
        if (!enabled()) return text
        return MaCorrectionRules.apply(text, _effective.value, MaLanguage.active())
    }

    /** The right forms, for the speech model's word list. */
    fun glossary(): List<String> =
        if (enabled()) MaCorrectionRules.glossary(_effective.value, MaLanguage.active()) else emptyList()

    // ---------------------------------------------------------------- edited from the settings screen

    /** A rule from another device that this phone does not have yet is copied here with count 0, so the switch is his. */
    fun setEnabled(rule: MaCorrectionRules.Rule, on: Boolean) = update { list ->
        if (list.any { it.key() == rule.key() }) list.map { if (it.key() == rule.key()) it.copy(enabled = on) else it }
        else list + rule.copy(count = 0, enabled = on)
    }

    /**
     * Deleted here = switched off on every device (the shared file keeps a line with enabled false; the newest decides).
     * A rule only another device had is kept here switched off, so the deletion travels.
     */
    fun delete(rule: MaCorrectionRules.Rule) = update { list ->
        if (list.any { it.key() == rule.key() }) list.filter { it.key() != rule.key() }
        else list + rule.copy(count = 0, enabled = false)
    }

    /** Adds a rule of his own, applied at once, for every language. */
    fun add(wrong: String, right: String) {
        val w = wrong.trim()
        val r = right.trim()
        if (w.isEmpty() || r.isEmpty() || w == r) return
        val rule = MaCorrectionRules.Rule(w, r, MaCorrectionRules.ANY, 0, true, MaCorrectionRules.Source.MINE)
        update { list -> list.filter { it.key() != rule.key() } + rule }
    }

    fun addSeeds() = update { MaCorrectionRules.withSeeds(it) }

    fun deleteAll() = update { emptyList() }

    private fun update(change: (List<MaCorrectionRules.Rule>) -> List<MaCorrectionRules.Rule>) {
        _rules.value = change(_rules.value)
        save()
    }

    private fun save() {
        val f = file ?: return
        val snapshot = _rules.value
        val text = MaCorrectionRules.serialize(snapshot)
        scope.launch {
            // Shared corrections: the change becomes lines of this phone's file and is sent in a few seconds.
            runCatching { MaCorrectionsSync.recordLocal(snapshot) }
            _effective.value = runCatching { MaCorrectionsSync.effective(snapshot) }.getOrDefault(snapshot)
            runCatching {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(f)) {
                    f.writeText(text)
                    tmp.delete()
                }
            }
        }
    }
}
