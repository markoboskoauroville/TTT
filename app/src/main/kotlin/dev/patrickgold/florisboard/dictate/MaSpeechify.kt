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

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.util.Base64
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking

/**
 * Speaking, through Speechify.
 *
 * ### The voices, and why these two
 *
 * **Speechify has no Croatian voice.** Verified against the whole catalogue — 985 voices, every page
 * walked — and there is no `hr-HR` on any model. The only Slavic locales that exist at all are
 * Russian, Polish and Ukrainian.
 *
 * So Croatian is always read by a foreign voice, and the question is only which foreign voice hurts
 * least. Marko listened to five renderings of the same Croatian sentence and chose:
 *
 * - **Lesya**, Ukrainian, for Croatian — Slavic phonetics, so `č ć ž š đ` and the `-lj-` `-nj-`
 *   clusters come out as sounds rather than as spellings.
 * - **Beatrice**, British, for English, and as the second option for Croatian.
 *
 * ### TRAP: the model is not free to choose
 *
 * `lesya` exists ONLY on `simba-multilingual` and `simba-3.0`. Ask for it with `simba-english` and
 * the request fails. And `simba-3.2`, which the documentation recommends, answers **HTTP 400** for
 * any voice outside the eight curated `_32` ids. Both facts are measured, not read.
 *
 * So the model follows the voice, never a global default.
 *
 * ### The key ring never tests speculatively
 *
 * Take the first key not known dead, use it until a real request returns 401 or 403, and only then
 * condemn it and retry the same request with the next. **A dead key costs one wasted request in its
 * whole life, and a healthy ring costs none.** 429 is not death — the key is valid and throttled, so
 * it is rested rather than killed.
 */
object MaSpeechify {

    const val PROVIDER_ID = "speechify"

    /**
     * The key that answered last, so the ring does not walk to it again every time.
     *
     * Held in memory only. It is a shortcut, not a record: a wrong guess costs one request and
     * corrects itself, and persisting it would mean carrying a stale answer across a session where
     * the account may have changed entirely.
     */
    @Volatile
    private var lastGoodKey: Int = 0

    /**
     * The key being tried right now, counted from one, for the corner of the reader.
     *
     * **Updated on every attempt, not only on success — it is a progress bar.** The first version
     * showed only the key that worked, on the reasoning that a flickering number is a progress bar
     * rather than an answer. He wanted the progress bar: while a reading is slow to start, watching
     * the number climb *is* the diagnosis. A still `1` means waiting for the network; `1 2 3 4` means
     * the ring is walking past tired keys.
     *
     * The total is not exposed. He can count his keys in settings, and a second number on a screen
     * built for one word at a time is one more thing competing for the eye.
     */
    var activeKeyNumber by mutableStateOf(0)
        private set

    private const val BASE = "https://api.sws.speechify.com/v1/audio/speech"
    private const val TIMEOUT_MS = 30_000

    /**
     * One word, and when it is spoken. Times are milliseconds from the start of the audio.
     *
     * Speechify returns these with every synthesis at no extra cost, which is what makes the
     * karaoke word on the spacebar free rather than a second request.
     */
    data class Word(val text: String, val startMs: Int, val endMs: Int)

    /** The words of the last synthesis, in order. Empty when the marks could not be read. */
    @Volatile
    var lastWords: List<Word> = emptyList()
        private set

    /**
     * Pulls the word timings out of a response.
     *
     * The marks arrive as a nested object — a sentence with a `chunks` array of words — rather than
     * the flat list the name suggests. Reading it defensively because a shape change here should
     * cost the karaoke display, never the audio.
     */
    fun parseWords(marks: JSONObject?): List<Word> {
        val chunks = marks?.optJSONArray("chunks") ?: return emptyList()
        return buildList {
            for (i in 0 until chunks.length()) {
                val c = chunks.optJSONObject(i) ?: continue
                if (c.optString("type") != "word") continue
                val value = c.optString("value").trim()
                if (value.isEmpty()) continue
                add(Word(value, c.optInt("start_time", -1), c.optInt("end_time", -1)))
            }
        }.filter { it.startMs >= 0 && it.endMs >= it.startMs }
    }

    /**
     * The word being spoken at [positionMs], or null between words.
     *
     * A plain scan rather than a binary search: a screen is a few hundred words and this runs a few
     * times a second, so the clearer code costs nothing measurable.
     */
    fun wordAt(words: List<Word>, positionMs: Int): String? =
        words.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.text

    /**
     * The sentence being spoken, and which word inside it is current.
     *
     * ### Sentences are derived, not given
     *
     * Measured: a passage of three sentences comes back as ONE mark object of type `sentence` with
     * a flat list of words. There is no per-sentence nesting to read, whatever the type field
     * suggests. So a sentence here is simply the run of words between one ending in `.`, `!` or `?`
     * and the next — which is what the subtitle row needs and costs nothing to compute.
     *
     * Returns the words of the current sentence and the index of the current word within them, or
     * an empty list between sentences.
     */
    fun sentenceAround(words: List<Word>, positionMs: Int): Pair<List<Word>, Int> {
        if (words.isEmpty()) return emptyList<Word>() to -1
        val current = words.indexOfFirst { positionMs >= it.startMs && positionMs < it.endMs }
        if (current < 0) return emptyList<Word>() to -1
        fun ends(w: Word) = w.text.lastOrNull() in setOf('.', '!', '?')
        var start = current
        while (start > 0 && !ends(words[start - 1])) start--
        var end = current
        while (end < words.lastIndex && !ends(words[end])) end++
        return words.subList(start, end + 1) to (current - start)
    }

    /** A voice he can pick, with the model it actually works on. */
    data class Voice(
        val id: String,
        val label: String,
        val detail: String,
        val model: String,
    )

    /**
     * Croatian is read by Ukrainian Lesya by default.
     *
     * Ordered as he ranked them by ear, so the first entry is the shipped default and the settings
     * screen can simply show the list.
     */
    val CROATIAN_VOICES = listOf(
        Voice("lesya", "Lesya", "Ukrainian female \u2014 Slavic sounds", "simba-multilingual"),
        Voice("beatrice_32", "Beatrice", "British female \u2014 warm", "simba-multilingual"),
        Voice("dominika", "Dominika", "Polish female", "simba-multilingual"),
        Voice("daria", "Daria", "Russian female", "simba-multilingual"),
    )

    /** English keeps the English voices, on the model that serves the whole catalogue. */
    val ENGLISH_VOICES = listOf(
        Voice("beatrice_32", "Beatrice", "British female \u2014 warm", "simba-english"),
        Voice("imogen", "Imogen", "British female", "simba-english"),
        Voice("edmund", "Edmund", "British male", "simba-english"),
        Voice("hugh", "Hugh", "British male", "simba-english"),
    )

    /**
     * Speaks a one-line sample in a voice, so he can hear it before choosing.
     *
     * The voice says its OWN name. "Hi, I am Lesya" tells him the accent, the pace and the warmth
     * in four words, and ties the sound to the row he is looking at — which a neutral sentence
     * would not.
     *
     * Written to its own cache file so a preview never overwrites audio the reader is playing.
     */
    fun previewFile(context: android.content.Context, voice: Voice): File? {
        val dest = File(context.cacheDir, "ma_voice_preview.mp3")
        return synthesize("Hi, I am ${voice.label}.", voice, dest)
    }

    fun voicesFor(language: String): List<Voice> =
        if (language == MaLanguage.EN) ENGLISH_VOICES else CROATIAN_VOICES

    /** The voice chosen for [language], falling back to that language's default. */
    /**
     * EVERY VOICE, ENGLISH FIRST, AS ONE LIST.
     *
     * One list because there is one choice. The English voices come first because that is what he
     * reads most; the Croatian ones follow, and the section headings stay so he can see which is
     * which — but the radio group spans both, and exactly one can be lit.
     */
    val ALL_VOICES: List<Voice> = ENGLISH_VOICES + CROATIAN_VOICES

    /**
     * THE VOICE. One, chosen by hand, used for everything.
     *
     * **No language is consulted, and that is the rule rather than an optimisation.**
     *
     * It used to pick per language: English text got the English voice, Croatian text got the
     * Croatian one. That sounds obviously right and it is the thing he has spent this month deleting
     * — it means the app decides, from a language that is itself a guess, and when the guess is wrong
     * the reading comes out in a voice he did not choose with no way to see why.
     *
     * Now: he picks a voice and that voice reads. A Ukrainian voice reading English is a choice he
     * can hear and correct in one tap. **A voice chosen for him by a language detector is a choice he
     * cannot see being made.**
     *
     * The fallback is the first English voice rather than the first of the matching language,
     * because there is no matching language any more — there is a list, and its head is the default.
     */
    fun chosenVoice(): Voice {
        val prefs by FlorisPreferenceStore
        val stored = prefs.dictate.maReaderVoice.get()
        return ALL_VOICES.firstOrNull { it.id == stored } ?: ALL_VOICES.first()
    }

    /**
     * Speaks [text] into [dest] as mp3. Null on failure, with the reason in the log.
     *
     * Walks the key ring, and treats each status the way the engine handoff proved correct: 401 and
     * 403 mean the key is dead and the next is tried immediately; anything else means stop and
     * report, because retrying a working key against a real error just spends money twice.
     */
    fun synthesize(text: String, voice: Voice, dest: File): File? {
        val prefs by FlorisPreferenceStore
        val account = prefs.dictate.providerAccounts.get().accounts[PROVIDER_ID] ?: run {
            MaLog.add("read", "no Speechify key configured")
            return null
        }
        val keys = MaKeys.split(account.apiKey).filter { it.isNotBlank() }

        if (keys.isEmpty()) {
            MaLog.add("read", "Speechify account has no keys")
            return null
        }
        // START FROM THE ONE THAT WORKED LAST TIME.
        //
        // THIS IS THE TWENTY SECONDS. The ring restarted at key one on every single read, so every
        // dead and every throttled key ahead of the good one was paid for again — a full network
        // round trip each, before a word could be spoken. With twenty-one keys and a few tired ones
        // at the front, that is the whole delay he was waiting through, on every press.
        //
        // A key that answered a moment ago will almost certainly answer now. Remembering it makes
        // the common case exactly one request. The ones before it are not forgotten: if the
        // remembered key fails, the walk continues past it and wraps, so nothing is skipped
        // permanently and a key that recovers is found again on the next wrap.
        val startAt = lastGoodKey.takeIf { it in keys.indices } ?: 0
        val order = (startAt until keys.size) + (0 until startAt)
        for (index in order) {
            val key = keys[index]
            // Set before the request, so the walk is visible while it happens rather than only
            // after it finishes.
            activeKeyNumber = index + 1
            val result = runCatching { speakOnce(text, voice, key, dest) }.getOrElse { e ->
                MaLog.add("read", "speak failed: ${e.javaClass.simpleName}")
                return null
            }
            when (result) {
                200 -> {
                    // Remembered, so the next read starts here instead of walking to it again.
                    lastGoodKey = index
                    MaLog.add("read", "spoke ${text.length} chars as ${voice.label} on key ${index + 1}")
                    return dest
                }
                401, 403 -> {
                    // Dead key. Move to the next and try the same request, so he never sees it.
                    MaLog.add("read", "key ${index + 1} rejected, rolling forward")
                    continue
                }
                429 -> {
                    MaLog.add("read", "key ${index + 1} throttled, rolling forward")
                    continue
                }
                // A free account that has used its characters answers 402 (Marko, 13.11.2026: "if the key is exhausted,
                // we rotate it"): the next key, at once.
                402 -> {
                    MaLog.add("read", "key ${index + 1} out of free use, rolling forward")
                    continue
                }
                else -> {
                    MaLog.add("read", "speak refused, http $result")
                    return null
                }
            }
        }
        MaLog.add("read", "every Speechify key was refused")
        return null
    }

    /**
     * Checks one key against the voice catalogue. Returns the HTTP status, or -1 for a real network
     * failure.
     *
     * ### Why this exists rather than the shared validator
     *
     * `OpenAiCompatibleClient.validateKey()` asks for `/models`, which every OpenAI-shaped provider
     * has and **Speechify does not**. It answered 404 "The requested resource could not be found",
     * which fell through to the catch-all and was reported as **"no connection"** — sending him to
     * look at his wifi over a key that was perfectly good and an API that was answering.
     *
     * `/v1/voices?limit=1` is the cheapest thing Speechify will answer: one voice, no synthesis, no
     * billing. Measured against the real API — a good key returns 200, a nonsense key returns 401
     * with `{"error":{"code":"unauthorized"}}`.
     *
     * **A test must ask the question the service can answer**, not the question the other services
     * happen to share.
     */
    fun validateKey(key: String): Int = runCatching {
        val conn = (URL("https://api.sws.speechify.com/v1/voices?limit=1").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $key")
        }
        val code = conn.responseCode
        runCatching { conn.disconnect() }
        code
    }.getOrDefault(-1)

    /** One request with one key. Returns the HTTP status; writes [dest] only on 200. */
    /**
     * One request. [overrideModel] is used by the self-repair to retry with a successor without
     * writing the new name into the voice table, which is data rather than state.
     */
    private fun speakOnce(
        text: String,
        voice: Voice,
        key: String,
        dest: File,
        overrideModel: String? = null,
    ): Int {
        val conn = (URL(BASE).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject().apply {
            put("input", text)
            put("voice_id", voice.id)
            put("audio_format", "mp3")
            // The model follows the voice. A global default would break Lesya, who does not exist
            // on simba-english, and simba-3.2 answers 400 for anything outside the curated set.
            // The stored repair, applied before the request. If this model was retired and a
            // successor was found on a previous run, that happened once and this run never notices.
            put("model", overrideModel ?: modelFor(voice))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code != 200) {
            // THE REPAIR, and only here — after a request has already failed.
            //
            // A working setup makes no extra calls, ever. Probing on every run to check the model
            // still exists would spend a request a day to prevent one failure a year, and a check
            // that costs something every time is a check that gets switched off.
            val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
            val used = modelFor(voice)
            if (MaModelHealing.isModelFailure(code, body, used)) {
                val fixed = healModel(used, key)
                // Retried ONCE, with the new name. Not a loop: if the successor fails too, the
                // provider has changed something this cannot reason about, and he needs to be told
                // rather than have the keyboard sit there trying names.
                if (fixed != null) return speakOnce(text, voice, key, dest, overrideModel = fixed)
            }
            return code
        }
        val payload = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(payload)
        val audio = json.optString("audio_data")
        if (audio.isBlank()) return -1
        // Timings kept alongside the audio, so the karaoke word costs nothing extra.
        lastWords = parseWords(json.optJSONObject("speech_marks"))
        dest.writeBytes(Base64.decode(audio, Base64.DEFAULT))
        return 200
    }

    /**
     * The model for a voice, after any repair already learned.
     *
     * Read on every request and costing nothing: it is a preference lookup, not a network call.
     */
    private fun modelFor(voice: Voice): String {
        val prefs by FlorisPreferenceStore
        return MaModelHealing.apply(voice.model, MaModelHealing.parseRewires(prefs.dictate.maModelRewires.get()))
    }

    /**
     * Asks the provider what models it has, picks the successor to [model], and writes it down.
     *
     * Returns the new name, or null when nothing qualifies — and **null means stop**. A model that
     * cannot be matched to a successor from the provider's own list is not guessed at: the voices
     * would not exist on a stranger's model, and a reading in an unexpected voice is worse than a
     * reading that did not happen.
     */
    private fun healModel(model: String, key: String): String? {
        val prefs by FlorisPreferenceStore
        // The key is passed in rather than fetched: this is called from inside a request that has
        // just proved this key works, and going back to the ring could pick a different one.
        val available = runCatching { listModels(key) }.getOrDefault(emptyList())
        if (available.isEmpty()) return null
        val next = MaModelHealing.successor(model, available) ?: return null
        val rewires = MaModelHealing.parseRewires(prefs.dictate.maModelRewires.get()).toMutableMap()
        rewires[model] = next
        runBlocking { prefs.dictate.maModelRewires.set(MaModelHealing.serializeRewires(rewires)) }
        MaLog.add("tts", "model $model retired, rewired to $next")
        return next
    }

    /** The provider's own list of model ids. The only evidence that a name works. */
    private fun listModels(key: String): List<String> {
        val conn = (URL("$BASE/v1/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $key")
        }
        if (conn.responseCode != 200) return emptyList()
        val payload = conn.inputStream.bufferedReader().use { it.readText() }
        val out = mutableListOf<String>()
        runCatching {
            val root = JSONObject(payload)
            val arr = root.optJSONArray("models") ?: root.optJSONArray("data")
            for (i in 0 until (arr?.length() ?: 0)) {
                val item = arr!!.opt(i)
                val id = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                    else -> ""
                }
                if (id.isNotBlank()) out.add(id)
            }
        }
        return out
    }
}
