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

package dev.patrickgold.florisboard.dictate.nlp

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.MaLog
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the personal n-gram model: its file, when it learns, and when it is written out.
 *
 * One instance for the process. The model is small enough to hold in memory and too small to be
 * worth a database, so it lives as one file read once at start and written back on a delay.
 */
object MaNgram {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * ONE MODEL PER LANGUAGE, AND THEY NEVER MEET.
     *
     * There was one model for everything he wrote. It learned Croatian and English into the same
     * counts and offered whichever was commoner, so typing Croatian produced English words with a
     * matching prefix and the reverse — and every AI word he accepted went into the same undivided
     * store.
     *
     * The EN/HR key already moves the transcription language, the keyboard subtype and the shipped
     * dictionary. **It moves the personal model now too, and that was the only part still deaf to
     * it.** What he wants is exactly this: a sentence in English, the badge, a sentence in Croatian,
     * and each one finished from words of its own language.
     *
     * Isolated, not weighted. A shared model with a language column would still let one language's
     * counts decide the ranking of the other's, which is the same failure wearing a schema.
     */
    private val models = mutableMapOf<String, MaNgramModel>()

    private val files = mutableMapOf<String, File>()

    private val modelsLock = Any()

    /** The language being written right now: the badge, which is the one control he touches. */
    private fun active(): String = MaLanguage.active()

    /** Whether [language]'s personal model knows [word] (MaTypingLanguage reads this). */
    fun knowsIn(language: String, word: String): Boolean = modelFor(language).knows(word)

    private fun modelFor(language: String): MaNgramModel = synchronized(modelsLock) {
        models.getOrPut(language) { MaNgramModel() }
    }

    // His corrections and when he last wrote each word, one of each per language like the models
    // (MaCorrections.kt: what he corrects is learned as a typo; recent words outrank old ones).
    private val corrections = mutableMapOf<String, MaCorrectionStore>()
    private val recency = mutableMapOf<String, MaRecency>()
    private val watcher = MaCorrectionWatcher()

    private fun correctionsFor(language: String): MaCorrectionStore = synchronized(modelsLock) {
        corrections.getOrPut(language) { MaCorrectionStore() }
    }

    private fun recencyFor(language: String): MaRecency = synchronized(modelsLock) {
        recency.getOrPut(language) { MaRecency() }
    }

    /** What Haiku taught, per language (MaTeacherMemory). */
    private val teachers = mutableMapOf<String, MaTeacherMemory>()

    private fun teacherFor(language: String): MaTeacherMemory = synchronized(modelsLock) {
        teachers.getOrPut(language) { MaTeacherMemory() }
    }

    /** The word being typed and the text before it, as [predict] reads them. */
    private fun splitWord(textBeforeCursor: String, currentWord: String): Pair<String, String> {
        val trailing = textBeforeCursor.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
        val derived = currentWord.isBlank() && trailing.isNotEmpty()
        val word = if (derived) trailing else currentWord
        val context = if (derived) textBeforeCursor.dropLast(trailing.length) else textBeforeCursor
        return word to context
    }

    /**
     * Haiku's answer for this spot, kept for the local engine (MaTeacherMemory, 4.11.2026: "Haiku
     * should improve local prediction as we go"). Not in incognito, not with learning off.
     */
    fun rememberTeacher(textBeforeCursor: String, currentWord: String, words: List<String>, isIncognito: Boolean) {
        if (isIncognito || words.isEmpty()) return
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return
        val (_, context) = splitWord(textBeforeCursor, currentWord)
        val language = MaTypingLanguage.of(textBeforeCursor)
        val (two, one) = modelFor(language).contextOf(context)
        teacherFor(language).remember(two, one, words)
        scheduleSave()
    }

    /** What Haiku already taught for this spot: when there are enough, Haiku is not asked again. */
    fun teacherWords(textBeforeCursor: String, currentWord: String): List<String> {
        val (word, context) = splitWord(textBeforeCursor, currentWord)
        val language = MaTypingLanguage.of(textBeforeCursor)
        val (two, one) = modelFor(language).contextOf(context)
        return teacherFor(language).words(two, one, word)
    }

    private fun today(): Int = MaRecency.today(System.currentTimeMillis())

    /**
     * The text before the cursor after every change in the field, from the editor. Sees a word
     * typed, cut back into and typed again differently; such a pair is a typo and its correction.
     */
    fun observeText(textBefore: String, isIncognito: Boolean) {
        if (isIncognito) return
        val found = watcher.observe(textBefore) ?: return
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return
        val language = active()
        scope.launch {
            correctionsFor(language).record(found.typo, found.fixed)
            modelFor(language).unlearn(found.typo)
            MaLog.add("ngram", "learned a correction: ${found.typo} → ${found.fixed}")
            scheduleSave()
        }
    }

    /** A character deleted before the cursor: it is not part of the sentence being collected. */
    fun onDelete() {
        synchronized(pendingLock) { if (pending.isNotEmpty()) pending.setLength(pending.length - 1) }
    }

    /**
     * The model for the language being written.
     *
     * Kept as a property because the settings screen reads `MaNgram.model.vocabularySize` to report
     * how much has been learned. It now reports the ACTIVE language's model, which is the honest
     * answer to "how many words does it know" asked while writing in one of them.
     */
    val model: MaNgramModel
        get() = modelFor(active())

    @Volatile
    private var loaded = false

    /**
     * The application context, kept for reading the shipped dictionary asset.
     *
     * The application context and never an Activity or the keyboard service: this object outlives
     * both, and holding either would leak a whole view hierarchy for the sake of opening a file.
     */
    @Volatile
    private var appContext: Context? = null

    private var saveJob: Job? = null

    /**
     * Reads the model from disk, once.
     *
     * Safe to call repeatedly; the second call does nothing. Failure is silent by design: an
     * unreadable model is an empty model, which costs the user their learned words but not their
     * keyboard.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        loaded = true
        // Both, at startup, rather than the active one on demand. He switches language mid-sentence
        // and a load on first use would put a disk read in the middle of typing.
        for (language in LANGUAGES) {
            val target = File(context.filesDir, "ma_ngram_$language.tsv")
            synchronized(modelsLock) { files[language] = target }
            scope.launch { modelFor(language).load(target) }
            scope.launch {
                runCatching { File(context.filesDir, "ma_corrections_$language.tsv").takeIf { it.exists() }?.readText() }
                    .getOrNull()?.let { correctionsFor(language).parse(it) }
                runCatching { File(context.filesDir, "ma_recency_$language.tsv").takeIf { it.exists() }?.readText() }
                    .getOrNull()?.let { recencyFor(language).parse(it) }
                runCatching { File(context.filesDir, "ma_teacher_$language.tsv").takeIf { it.exists() }?.readText() }
                    .getOrNull()?.let { teacherFor(language).parse(it) }
            }
        }
        // The old single mixed file, removed once.
        //
        // It cannot be split: nothing in it records which language each count came from, and
        // guessing per word is exactly the mixing this change exists to end. It is deleted and the
        // backfill below rebuilds both models from the dictation history, which DOES carry a
        // language per entry. Nothing of value is lost that the history cannot give back.
        scope.launch {
            runCatching {
                val legacy = File(context.filesDir, LEGACY_FILE_NAME)
                if (legacy.exists()) {
                    legacy.delete()
                    val prefs by FlorisPreferenceStore
                    prefs.dictate.maNgramBackfilled.set(false)
                    MaLog.add("ngram", "mixed model removed; rebuilding one per language")
                }
            }
        }
    }

    /**
     * The sentence being written, assembled from single-character commits.
     *
     * Typing arrives one character at a time, so learning each commit as it came would fill the
     * model with single letters and nothing else. Characters are collected here and handed to the
     * model as whole sentences, which is the unit the counts are actually about. Dictation does not
     * pass through here: a transcript arrives complete and is learned as it stands.
     */
    private val pending = StringBuilder()

    private val pendingLock = Any()

    /**
     * Everything committed to the editor passes through here.
     *
     * A commit longer than one character is already a unit worth learning: a transcript, or a
     * suggestion accepted whole. A single character is a keystroke, and keystrokes are collected
     * until a sentence ends.
     *
     * A known limitation, and an accepted one: text typed and then deleted is still learned. Undoing
     * a count needs an edit history this has no access to, and the cost of the odd wrong word is one
     * count among thousands, which the ranking absorbs.
     */
    fun onCommit(text: String, isIncognito: Boolean) {
        if (isIncognito || text.isEmpty()) return
        // One word committed whole (the composing word when it is finished, or a suggestion picked) joins
        // the sentence being collected, like typed letters do (7.11.2026: learned alone, typing never taught
        // which word follows which: no bigrams, no names after names). A transcript stays a unit of its own.
        val oneWord = text.length in 2..40 && text.trimEnd().none { it.isWhitespace() }
        if (text.length > 1 && !oneWord) {
            learn(text, isIncognito)
            return
        }
        val ch = text.last()
        val ready: String? = synchronized(pendingLock) {
            pending.append(text)
            when {
                ch in SENTENCE_ENDS || pending.length >= MAX_PENDING -> {
                    val out = pending.toString()
                    pending.setLength(0)
                    out
                }
                else -> null
            }
        }
        if (ready != null) learn(ready, isIncognito)
    }

    /** A picked suggestion: [typed] (the letters so far, already collected) becomes [picked] in the sentence. */
    fun onReplaceWord(typed: String, picked: String, isIncognito: Boolean) {
        if (isIncognito || picked.isEmpty()) return
        synchronized(pendingLock) {
            if (typed.isNotEmpty() && pending.endsWith(typed)) pending.setLength(pending.length - typed.length)
            pending.append(picked)
        }
    }

    /** Hands over whatever sentence is half-written, for when the keyboard is closing. */
    fun flushPending(isIncognito: Boolean) {
        val ready: String? = synchronized(pendingLock) {
            if (pending.isEmpty()) null else pending.toString().also { pending.setLength(0) }
        }
        if (ready != null) learn(ready, isIncognito)
    }

    /**
     * Folds [text] into the model.
     *
     * Refused in incognito mode, which is the whole promise of incognito mode; refused when the
     * feature is off; and refused for anything long enough to be a paste rather than a sentence,
     * because a pasted document would swamp the counts with somebody else's vocabulary.
     */
    fun learn(text: String, isIncognito: Boolean) {
        if (isIncognito || text.isBlank()) return
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return
        if (text.length > MAX_LEARN_LENGTH) return
        // The language at the moment of writing, captured here rather than inside the coroutine: he
        // may press the badge between the commit and the save, and the sentence belongs to the
        // language it was written in.
        // The language the sentence is written in (MaTypingLanguage: detected from its words when that
        // is on, else the badge). Named `badge` still, because below it plays the badge's part.
        val badge = MaTypingLanguage.ofSentence(text)
        scope.launch {
            // EVERY WORD GOES THROUGH THE GATE, AND ALMOST NONE OF THEM COST ANYTHING.
            //
            // The badge is right about a sentence and not always about a word: he writes an English
            // word inside a Croatian sentence and back again, and a word filed under the badge alone
            // is offered in the wrong language for good.
            //
            // So each word is checked against what the models already know. **A word already in a
            // model is already in the right place** — it was filed once, correctly, and asking again
            // would pay to be told what is on disk. Words carrying č ć ž š đ are Croatian with no
            // help from anybody. Only a word that is new AND unmarked is queued to be asked about,
            // once, ever.
            //
            // The sentence is still learned WHOLE into the badge's model, because an n-gram learns
            // sequences and a sentence chopped into per-word destinations would teach neither model
            // how the words follow each other. The gate decides where a NEW WORD belongs; the queue
            // fixes it afterwards if the badge was wrong about it.
            for (raw in text.split(Regex("\\s+"))) {
                val word = MaWordLanguage.normalise(raw)
                if (word.isBlank()) continue
                when (
                    val answer = MaWordLanguage.decide(
                        word = word,
                        badge = badge,
                        knownEn = modelFor(MaWordLanguage.EN).knows(word),
                        knownHr = modelFor(MaWordLanguage.HR).knows(word),
                    )
                ) {
                    is MaWordLanguage.Answer.Known -> Unit
                    is MaWordLanguage.Answer.Certain ->
                        if (answer.language != badge) modelFor(answer.language).learn(word)
                    is MaWordLanguage.Answer.Ask -> queueForAsking(word)
                }
            }
            modelFor(badge).learn(text)
            val day = today()
            MaNgramModel.tokenize(text).forEach { recencyFor(badge).touch(it, day) }
            scheduleSave()
        }
    }

    /**
     * Reads every past transcription into the model, once.
     *
     * ### Why this is the single best thing that can be done for prediction
     *
     * The model only ever learned from words committed **through this keyboard**, which meant a
     * fresh install knew nothing and had to be taught his vocabulary again by hand, one sentence at
     * a time, over weeks. Meanwhile every dictation he has ever made was sitting in the history
     * database — thousands of his own words, in his own phrasing, about his own subjects.
     *
     * That is a better corpus for predicting his writing than any general model, because it is not
     * a sample of how people write. It is a record of how *he* writes.
     *
     * ### Once, and marked
     *
     * Guarded by a preference rather than by "is the model empty", because a model that has learned
     * a little is still worth backfilling, and a second pass would double every count and skew the
     * ranking towards whatever happened to be in history.
     *
     * ### Newest first, and capped
     *
     * [BACKFILL_MAX] entries, taken from the most recent, because the far past is the least like
     * what he is writing today and reading all of it on a first run would stall the app behind a
     * database it does not need yet. The work happens on the io scope like every other write here.
     */
    fun backfillFromHistory(context: Context) {
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return
        if (prefs.dictate.maNgramBackfilled.get()) return
        scope.launch {
            runCatching {
                val entries = DictateHistoryStore.getAll(context.applicationContext)
                    .take(BACKFILL_MAX)
                var learned = 0
                for (entry in entries) {
                    val text = entry.text
                    if (text.isBlank() || text.length > MAX_LEARN_LENGTH) continue
                    // Routed by the language the entry was DICTATED in, which the history has
                    // recorded all along. This is why the mixed file could be thrown away without
                    // losing anything: the evidence for the split was already on disk.
                    val language = if (entry.language.substringBefore('-').lowercase() == MaLanguage.HR) {
                        MaLanguage.HR
                    } else {
                        MaLanguage.EN
                    }
                    modelFor(language).learn(text)
                    learned++
                }
                prefs.dictate.maNgramBackfilled.set(true)
                if (learned > 0) {
                    scheduleSave()
                    MaLog.add("ngram", "learned $learned past dictations into ${LANGUAGES.size} models")
                }
            }.onFailure { MaLog.add("ngram", "backfill failed: ${it.message}") }
        }
    }

    /**
     * The next-word predictions for the text before the cursor.
     *
     * Returns nothing when the feature is off, in incognito mode, or before the model has read
     * enough to be worth trusting. That last one matters: a model with a hundred words in it will
     * answer every query, and every answer will be noise, which teaches the user to ignore the strip
     * before the model is ever good enough to be worth reading.
     */
    fun predict(textBeforeCursor: String, currentWord: String, isIncognito: Boolean): List<MaNgramSuggestion> {
        if (isIncognito) return emptyList()
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return emptyList()
        // The personal model, when it has seen enough to be worth asking. Below that threshold it
        // is not silent because it is broken; it is silent because a handful of words produces
        // confident nonsense.
        // The word being typed, worked out from the text when the editor will not say.
        //
        // `currentWordText` comes from the composing region, and many apps never set one — a web
        // view, a dialog, anything drawing its own input. In those the prefix arrived empty, and an
        // empty prefix means something entirely different downstream: it stops being "complete this
        // word" and becomes "what word comes next". That is why typing `other` offered `things` and
        // `parts`. They were not wrong answers to the question asked; the wrong question was asked.
        //
        // So when the editor does not tell us, read it off the end of the text. The trailing run of
        // letters before the cursor IS the word in progress, apostrophes and hyphens included so
        // `don't` and `well-known` are not cut in half.
        val trailing = textBeforeCursor.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
        val derived = currentWord.isBlank() && trailing.isNotEmpty()
        val word = if (derived) trailing else currentWord
        // And when the word was derived, the context has to lose it too. `contextOf` reads the last
        // complete words before the cursor, and it would otherwise count the half-typed word as the
        // previous one — predicting what follows `other` while he is still writing `other`.
        val context = if (derived) textBeforeCursor.dropLast(trailing.length) else textBeforeCursor
        // Only this language's model. Never the other one, not even to fill an empty row: an English
        // word offered while writing Croatian is not a weaker suggestion, it is a wrong one. The
        // language is the one he is TYPING (MaTypingLanguage), not only the badge (27.10.2026).
        val language = MaTypingLanguage.of(textBeforeCursor)
        val active = modelFor(language)
        val fixes = correctionsFor(language)
        val recent = recencyFor(language)
        val day = today()
        val predicted = if (active.totalWords >= MIN_WORDS_BEFORE_PREDICTING) {
            val (two, one) = active.contextOf(context)
            active.predict(
                previousTwo = two,
                previousOne = one,
                prefix = word,
                weight = { recent.weight(it, day) },
                skip = { fixes.isTypo(it) },
            )
        } else {
            // NAMES BEFORE THE GATE (7.11.2026, SwiftKey parity: "TTT must nicely save all email addresses
            // and names"). Below MIN_WORDS_BEFORE_PREDICTING the model stays silent, so on a new phone a name
            // typed twice was never offered. A capitalised word neither shipped dictionary knows, seen at least
            // twice, is offered from the start: from its first letters, and after the word it follows
            // ("Zvjezdana " → "Kovačević").
            // A name has no language: a name committed on its own is filed by the word gate, often under
            // the other language (live test: "Ximenka" went to the Croatian model while "we saw Xim" asked
            // the English one), so both models are asked.
            val (two, one) = active.contextOf(context)
            LANGUAGES.flatMap { lang ->
                modelFor(lang).predict(
                    previousTwo = two,
                    previousOne = one,
                    prefix = word,
                    weight = { recent.weight(it, day) },
                    skip = { w ->
                        fixes.isTypo(w) || w.firstOrNull()?.isUpperCase() != true || MaShippedWords.isKnown(w)
                    },
                )
            }.filter { it.count >= 2 }.distinctBy { it.word.lowercase() }.sortedByDescending { it.count }.take(PREDICT_LIMIT)
        }
        // A word he has corrected before: what he meant comes first, in the case he is typing.
        // Ahead of the totalWords gate, because one correction is already certain knowledge.
        val fix = if (word.isNotEmpty()) fixes.fixFor(word) else null
        val personal = if (fix != null) {
            val shown = MaCorrectionStore.matchCase(word, fix)
            listOf(MaNgramSuggestion(word = shown, count = 1, tier = MaNgramModel.TIER_TRIGRAM + 1)) +
                predicted.filter { !it.word.equals(shown, ignoreCase = true) }.take(PREDICT_LIMIT - 1)
        } else {
            predicted
        }.let { own ->
            // WHAT HAIKU TAUGHT (MaTeacherMemory): after his own words, before the shipped list.
            if (own.size >= PREDICT_LIMIT) return@let own
            val (two, one) = active.contextOf(context)
            val have = own.mapTo(HashSet()) { it.word.lowercase() }
            own + teacherFor(language).words(two, one, word)
                .filter { it.lowercase() !in have && !fixes.isTypo(it) }
                .take(PREDICT_LIMIT - own.size)
                .map { MaNgramSuggestion(word = MaCorrectionStore.matchCase(word, it), count = 1, tier = 0) }
        }
        if (personal.size >= PREDICT_LIMIT) return personal
        // The shipped Croatian list fills what is left, and only then.
        //
        // Three conditions, each of them the point rather than caution. A prefix, because a word
        // list with no prefix is the commonest word in the language rather than a prediction.
        // Croatian, because English already has a dictionary from upstream and offering both would
        // put Croatian words under an English sentence. And last, always, so a word he has actually
        // written outranks a word the language merely contains.
        //
        // Deliberately outside the totalWords gate: an empty personal model is exactly the state
        // this exists for. A fresh install should suggest Croatian on the first word, not after
        // three hundred.
        if (word.isEmpty()) return personal
        if (language != MaLanguage.HR) return personal
        // Since 7.11.2026 the Croatian dictionary ships in the APK and the shipped engine ranks it by the
        // previous word as well (LatinLanguageProvider, NlpManager falls back to it under the HR badge).
        // This frequency-only list would fill the strip before that engine speaks, so it stands aside.
        if ("hr" in dev.patrickgold.florisboard.ime.nlp.latin.GlideDictionaryCatalog.BUNDLED) return personal
        val appCtx = appContext ?: return personal
        val already = personal.mapTo(HashSet()) { it.word.lowercase() }
        return personal + MaBaseDictionary.suggest(
            context = appCtx,
            prefix = word,
            limit = PREDICT_LIMIT - personal.size,
            exclude = already,
        )
    }

    /**
     * Writes the model out after a pause in typing.
     *
     * Debounced rather than written per word: learning happens on every commit, and a file write per
     * keystroke would be both wasteful and a good way to be killed mid-write. The delay is long
     * enough to batch a sentence and short enough that little is lost if the process dies.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            // Every model, not the active one. He switches language mid-sentence, so the one that
            // just learned something may not be the one in front of him by the time this runs.
            for (language in LANGUAGES) {
                saveExtras(language)
                val m = modelFor(language)
                if (!m.isDirty) continue
                m.prune()
                synchronized(modelsLock) { files[language] }?.let { m.save(it) }
            }
        }
    }

    /** The corrections and recency files, beside the model's, when either has changed. */
    private fun saveExtras(language: String) {
        val dir = synchronized(modelsLock) { files[language] }?.parentFile ?: return
        runCatching {
            correctionsFor(language).takeIf { it.isDirty }?.let { File(dir, "ma_corrections_$language.tsv").writeText(it.serialize()) }
            recencyFor(language).takeIf { it.isDirty }?.let { File(dir, "ma_recency_$language.tsv").writeText(it.serialize()) }
            teacherFor(language).takeIf { it.isDirty }?.let { File(dir, "ma_teacher_$language.tsv").writeText(it.serialize()) }
        }
    }

    /** Writes immediately, for when the keyboard is going away and the delay would not finish. */
    fun flush() {
        saveJob?.cancel()
        scope.launch {
            for (language in LANGUAGES) {
                saveExtras(language)
                val m = modelFor(language)
                if (m.isDirty) synchronized(modelsLock) { files[language] }?.let { m.save(it) }
            }
        }
    }

    /** Forgets everything and removes the file. */
    fun forgetEverything() {
        scope.launch {
            // Everything means everything. "Forget what you have learned" asked while writing
            // English cannot sensibly leave the Croatian model standing — he would clear it, see the
            // count go to zero, and still be offered his own words the moment he pressed the badge.
            for (language in LANGUAGES) {
                modelFor(language).clear()
                correctionsFor(language).clear()
                recencyFor(language).clear()
                teacherFor(language).clear()
                synchronized(modelsLock) { files[language] }?.delete()
                saveExtras(language)
            }
        }
    }

    /** The two, and the only two. The badge has two states and so has this. */
    private val LANGUAGES = listOf(MaLanguage.EN, MaLanguage.HR)

    /** The single mixed model, deleted on the first run after the split. */
    private const val LEGACY_FILE_NAME = "ma_ngram.tsv"

    /** Long enough to batch a sentence, short enough to lose little if the process is killed. */
    private const val SAVE_DELAY_MS = 4_000L

    /** Beyond this a commit is a paste, not writing, and learning it would swamp the counts. */
    private const val MAX_LEARN_LENGTH = 2_000

    /** Below this the model answers everything and is right about nothing. */
    /** How many suggestions the row asks for. Matches MaNgramModel.predict's own default. */
    private const val PREDICT_LIMIT = 3

    /** How many past dictations the first run reads. Newest first; the far past predicts least. */
    private const val BACKFILL_MAX = 2000

    private const val MIN_WORDS_BEFORE_PREDICTING = 300L

    /** What ends a sentence for the purpose of handing it over. */
    private val SENTENCE_ENDS = charArrayOf('.', '!', '?', '\n', ':', ';')

    /** A sentence this long without punctuation is handed over anyway. */
    private const val MAX_PENDING = 400

    // ---------------------------------------------------------------------------------------------
    // The queue of words nobody has classified yet
    // ---------------------------------------------------------------------------------------------

    /**
     * Words seen once, carrying no Croatian letters, and known to neither model.
     *
     * They are already learned under the badge, so nothing is lost and nothing waits. This queue is
     * only for CORRECTING that guess, later, in the background, in one batch.
     *
     * Persisted, because the correction is worth having tomorrow and the phone will be killed
     * tonight. Capped: past a few hundred the list is not a queue, it is a leak, and the words at
     * the front are the ones he actually uses.
     */
    // A lock of its own. `pendingLock` already guards the commit buffer above and the two protect
    // different things; sharing one would make a word waiting to be classified block a keystroke
    // waiting to be learned, which is the wrong way round for the only one of them he can feel.
    private val askLock = Any()

    /**
     * Adds [word] to the queue.
     *
     * The lock computes the new list; the WRITE happens outside it.
     *
     * `set` on a preference is a suspend function, and suspending inside `synchronized` is not
     * allowed — Kotlin refuses it outright, which is the right refusal: a coroutine that suspends
     * while holding a monitor can resume on a different thread and try to release a lock it does not
     * own. So the critical section decides what the list should be and hands it out, and the write
     * is an ordinary suspending call in the coroutine that called this.
     */
    private suspend fun queueForAsking(word: String) {
        val prefs by FlorisPreferenceStore
        val next = synchronized(askLock) {
            val current = prefs.dictate.maNgramPending.get()
                .split(' ').filter { it.isNotBlank() }.toMutableList()
            if (word in current) return
            current += word
            while (current.size > PENDING_CAP) current.removeAt(0)
            current.joinToString(" ")
        }
        prefs.dictate.maNgramPending.set(next)
    }

    /** How many words are waiting to be classified. Shown on the settings screen. */
    fun pendingCount(): Int {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maNgramPending.get().split(' ').count { it.isNotBlank() }
    }

    /**
     * Asks the model about the queue, in one batch, and files the answers.
     *
     * Never on the typing path and never automatic. It is a button he presses, because it costs
     * money and takes a second and there is no moment while typing when either is acceptable.
     *
     * A word answered `both` is learned into both models: "radio", "auto", "student", "film" are
     * ordinary in both languages, and forcing a choice would delete a suggestion he had earned to
     * satisfy a schema.
     *
     * Words that come back unclassified are dropped from the queue rather than retried. They stay
     * where the badge put them, which was never worse than a guess, and a retry loop over a word
     * nobody can classify is a bill with no end.
     */
    suspend fun classifyPending(onMessage: (String) -> Unit) {
        val prefs by FlorisPreferenceStore
        val words = synchronized(askLock) {
            prefs.dictate.maNgramPending.get().split(' ').filter { it.isNotBlank() }
        }.take(BATCH)
        if (words.isEmpty()) {
            onMessage("Nothing waiting")
            return
        }
        val reply = DictateController.askPredictionModel(MaWordLanguage.prompt(words))
        if (reply == null) {
            onMessage("No answer \u2014 check the key and the network")
            return
        }
        val answers = MaWordLanguage.readAnswer(reply, words.toSet())
        for ((word, langs) in answers) {
            for (language in langs) modelFor(language).learn(word)
        }
        // Same shape as above, and for the same reason: decide inside the lock, write outside it.
        val rest = synchronized(askLock) {
            prefs.dictate.maNgramPending.get()
                .split(' ').filter { it.isNotBlank() && it !in words }
                .joinToString(" ")
        }
        prefs.dictate.maNgramPending.set(rest)
        scheduleSave()
        onMessage("Filed ${answers.size} of ${words.size}")
    }

    /** How many words go in one request. Enough to be worth the round trip, small enough to read. */
    private const val BATCH = 60

    /** Past this the queue is a leak rather than a queue. */
    private const val PENDING_CAP = 400

    /** What each model knows, for the settings screen. */
    fun vocabularyOf(language: String): Int = modelFor(language).vocabularySize

    /** How many words that model has read in total. */
    fun totalOf(language: String): Long = modelFor(language).totalWords

    /** Forgets one language only, leaving the other standing. */
    fun forget(language: String) {
        modelFor(language).clear()
        correctionsFor(language).clear()
        recencyFor(language).clear()
        teacherFor(language).clear()
        synchronized(modelsLock) { files[language] }?.delete()
        saveExtras(language)
    }

    /**
     * A fix he made in a dictation (MaDictationCorrections), handed to the typing side as well.
     *
     * One word for one word goes into the same correction store as a typo retyped on the keys
     * ([observeText]): typing the wrong word offers the right one first, and the wrong one is no
     * longer suggested. Longer swaps only take the wrong words out of the counts, except words that
     * are also part of the right form ("Mantra production" to "Mantra Productions" keeps "Mantra").
     */
    fun learnFix(wrong: String, right: String, language: String) {
        val wrongWords = wrong.split(' ').filter { it.isNotBlank() }
        val rightWords = right.split(' ').filter { it.isNotBlank() }
        if (wrongWords.isEmpty() || rightWords.isEmpty()) return
        scope.launch {
            if (wrongWords.size == 1 && rightWords.size == 1) {
                correctionsFor(language).record(wrongWords[0], rightWords[0])
            }
            val keep = rightWords.map { it.lowercase() }.toSet()
            for (w in wrongWords) if (w.lowercase() !in keep) modelFor(language).unlearn(w)
            scheduleSave()
        }
    }
}
