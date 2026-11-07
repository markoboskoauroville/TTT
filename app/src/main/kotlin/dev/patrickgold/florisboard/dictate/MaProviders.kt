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
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.audio.MaResample
import java.io.File
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionApi
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog

/**
 * Which provider does which job, and on which path.
 *
 * ### Why this is its own file
 *
 * `DictateController` was 3,821 lines holding recording, transcription, history, prompts, audio
 * routing and this. Splitting it is worth doing carefully rather than quickly: most of that file is
 * coupled to private state — `_state`, the output sink, the transcribe path — and moving those
 * elsewhere would mean making the controller's internals public purely to relocate code, which
 * trades one problem for a worse one.
 *
 * **This block was chosen because it reads nothing but preferences.** No state, no sink, no
 * coroutine scope. It answers questions — which account, which preset, which model, may this take
 * the fast path — and answers them from settings alone, which is exactly the shape that belongs
 * outside a controller.
 *
 * ### What it must never become
 *
 * A place where anything is *decided by side effect*. Everything here is a pure question about
 * configuration. The moment something in this file writes a preference or touches the UI, it has
 * stopped being resolution and should go back.
 */
object MaProviders {

    /** 20 Hz is responsive for a voice indicator while avoiding a display-rate UI loop. */
    /**
     * The only languages allowed on the Sync path.
     *
     * An allow-list on purpose. Sync's fast model is strong on English and returns confident nonsense
     * on Croatian — fluent-sounding sentences that are the wrong words — so the cost of guessing
     * wrong is a transcript nobody can tell is broken. A language stays slow until its Sync output
     * has actually been read by somebody. Slow costs seconds; wrong costs the sentence.
     */
    private val SYNC_SAFE_LANGUAGES = setOf("en")

    // AssemblyAI Sync bounds, both sides. The endpoint rejects anything under 80 ms as too short, so half
    // a second is a comfortable floor for something that is meant to be speech at all. The margin keeps a
    // calculated duration from arguing with the service's own measurement at the two minute ceiling.
    private const val MIN_SYNC_SECONDS = 0.5
    private const val SYNC_SECONDS_MARGIN = 2.0

    /** The same store the controller reads. Resolution answers from settings and nothing else. */
    private val prefs by FlorisPreferenceStore



    /** The active transcription provider's stored credentials (keyring). */

    /** The three choices of Settings → Transcription model (Marko, 7.11.2026); on the phone = Parakeet. */
    const val ENGINE_ASSEMBLYAI = "assemblyai"
    const val ENGINE_LOCAL = "local"
    const val ENGINE_FALLBACK = "fallback"

    /** Set once by FlorisApplication, for the install check above (settings resolution has no context). */
    @Volatile
    var appContext: Context? = null

    fun transcriptionEngine(): String = prefs.dictate.maTranscriptionEngine.get()

    fun transcriptionAccount(): ProviderAccount {
        val accounts = prefs.dictate.providerAccounts.get()
        // On the phone chosen: every transcription runs on Parakeet, AssemblyAI is not called at all.
        // Fallback with no AssemblyAI key at all: nothing to try first, so Parakeet straight away.
        val engine = transcriptionEngine()
        val localInstalled = appContext?.let { LocalModelManager.installedIds(it).isNotEmpty() } == true
        if (engine == ENGINE_LOCAL ||
            (engine == ENGINE_FALLBACK && localInstalled && accounts.getOrEmpty(MaRoles.TRANSCRIPTION).apiKey.isBlank())
        ) {
            return localTranscriptionAccount()
        }
        // Resolved, not read. The stored id is a fallback for a setup with no AssemblyAI key; it is
        // never allowed to point transcription at the language provider. See MaRoles.
        val id = MaRoles.transcription(accounts, prefs.dictate.transcriptionProviderId.get())
        return accounts.getOrEmpty(id)
    }

    /**
     * The on-device provider's stored account (issue #228): the selected local model lives in its
     * [ProviderAccount.transcriptionModel]. Used by the long-press "send with local model" shortcut.
     */
    fun localTranscriptionAccount(): ProviderAccount =
        prefs.dictate.providerAccounts.get().getOrEmpty(ProviderRegistry.LOCAL.id)

    /**
     * The transcription model to run for [account], resolving the on-device special case: the local
     * provider holds two picks (#233), and if the user only installed a streaming model, batch paths —
     * real-time off, long-form, the floating button — must use that one instead of failing with
     * "no model downloaded".
     */
    fun transcriptionModelFor(
        context: Context,
        account: ProviderAccount,
        preset: ProviderPreset,
        fallback: String = "",
    ): String {
        val chosen = account.transcriptionModel.takeIf { it.isNotBlank() }
            ?: preset.defaultTranscriptionModel
            ?: fallback
        if (preset.transcriptionApi != TranscriptionApi.LOCAL_ONDEVICE) return chosen
        if (chosen.isNotBlank() && LocalModelManager.isInstalled(context, chosen)) return chosen
        return account.realtimeModel.takeIf {
            it.isNotBlank() && LocalModelManager.isInstalled(context, it)
        }
            // A stored pick that is gone (an old Whisper id): whatever one-shot model is installed.
            ?: LocalModelManager.installedIds(context).firstOrNull { !LocalModelCatalog.isStreaming(it) && LocalModelCatalog.byId(it) != null }
            ?: LocalModelCatalog.PARAKEET_TDT_V3.id
    }

    /** The active rewording provider's stored credentials (keyring). */
    fun rewordingAccount(): ProviderAccount {
        val accounts = prefs.dictate.providerAccounts.get()
        val id = MaRoles.rewording(accounts, prefs.dictate.rewordingProviderId.get())
        return accounts.getOrEmpty(id)
    }

    /** Effective rewording key: the rewording account's, falling back to the transcription account's. */
    fun rewordingApiKey(): String =
        rewordingAccount().apiKey.ifBlank { transcriptionAccount().apiKey }

    /** Resolves the registry preset (base URL, defaults, headers) backing [account]. */
    fun presetFor(account: ProviderAccount): ProviderPreset = when {
        account.isCustom -> ProviderRegistry.custom(account.customBaseUrl)
        else -> ProviderRegistry.byId(account.providerId) ?: ProviderRegistry.OPENAI
    }

    /**
     * Whether this particular recording goes up the AssemblyAI Sync path.
     *
     * Every condition here is a reason to say no, and that asymmetry is deliberate. Fast is a
     * preference; arriving is not. A recording that cannot take the fast path takes the slow one and
     * nobody is told, because from where Marko sits the only difference is how long the words take,
     * and the long recording, the one that took the most effort to speak, must never be the one that
     * fails.
     *
     * [file] must already be the resampled WAV, so the length and the size below are measured rather
     * than assumed.
     */

    /**
     * Whether this dictation goes down the Sync path, decided by language first of all.
     *
     * ### The failure this exists to prevent
     *
     * Sync's fast model transcribes Croatian badly, and badly in the worst possible way: it returns
     * fluent Croatian that is the wrong words. Not garbled, not empty, not obviously broken —
     * plausible sentences nobody would question without knowing what was said. A wrong answer that
     * looks right is worse than an error, because there is nothing to notice.
     *
     * So the language decides the path, and speed is no longer something the user sets. Croatian is
     * always async. English may use Sync while the clip fits its window, and the length check below
     * turns that into "fast until about two minutes, then slow" without any timer of its own.
     */
    /**
     * WHICH FAST PATH: the Dictation API, or Sync.
     *
     * Called only when [maUseSyncPath] has already said yes, so every guard it applies is already
     * true — under two minutes, under the byte cap, a WAV, not chat audio, an AssemblyAI account.
     * **This function adds exactly one question**, and adding it anywhere else would mean restating
     * the other five.
     *
     * The question is the language. The Dictation endpoint accepts 32 codes and **Croatian is not
     * among them** — a code outside the set is rejected with `400`. So: English goes to Dictation,
     * everything else stays on Sync.
     *
     * Checked against the language he CHOSE, not a guess: the record key decided it before the audio
     * existed. There is no detector in this path and there must not be — a detector that says
     * "English" about a Croatian sentence turns a good transcription into a 400.
     *
     * Over two minutes never reaches here at all: [maUseSyncPath] has already sent it to the async
     * path, which is his rule — **over two minutes, we go the old way, always.**
     */
    fun maUseDictationPath(language: String): Boolean {
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maDictationApi.get()) return false
        return language == MaLanguage.EN
    }

    fun maUseSyncPath(preset: ProviderPreset, chatAudio: Boolean, file: File): Boolean {
        // MaLanguage.active() and not the raw preference, deliberately. It answers hr or en and
        // nothing else, collapsing anything left over from an older install — including the auto
        // detect this app no longer offers — to Croatian. The gate therefore has two cases rather
        // than three, and an install that still has "detect" stored gets the safe one.
        val lang = MaLanguage.active()

        // An allow-list, not a deny-list, and that direction is the point. Sync's fast model returns
        // fluent Croatian that is the wrong words, so being wrong here costs the sentence and there
        // is nothing in the result to notice. Only a language whose Sync output has actually been
        // read belongs on this list.
        if (lang !in SYNC_SAFE_LANGUAGES) return false

        // Deliberately not gated on any speed preference, and this is not an oversight.
        //
        // There was one. It defaulted to SLOW and held whatever the old FAST/SLOW key was last left
        // on, so reading it meant English silently never taking the fast path on any install that
        // had tapped that key. A control that has been removed must not keep voting — and once
        // nothing read it, the preference and its enum were deleted rather than left waiting.
        //
        // The language decides, and nothing else does.
        // Sync belongs to AssemblyAI and to no other account. Anything else keeps its own path.
        if (preset.transcriptionApi != TranscriptionApi.ASSEMBLYAI_ASYNC) return false
        // Chat-audio transcribes and formats in one chat request; Sync does transcription only.
        if (chatAudio) return false
        if (file.length() > OpenAiCompatibleClient.SYNC_MAX_BYTES) return false
        // Not knowing how long the audio is counts as too long. A header that will not parse is not a
        // thing to gamble a dictation on.
        val seconds = MaResample.durationSeconds(file) ?: return false
        // A margin under the real limit: the service rejects at 120 s and this reading is a
        // calculation, so the last two seconds are left as room for the two to disagree.
        return seconds >= MIN_SYNC_SECONDS && seconds <= OpenAiCompatibleClient.SYNC_MAX_SECONDS - SYNC_SECONDS_MARGIN
    }

    /**
     * The account's own base URL, when it has one: custom endpoints always, and base-URL-editable
     * built-ins like Ollama (issue #136). Null → the preset's default base URL is used.
     */
    fun baseUrlOverrideFor(account: ProviderAccount): String? =
        if (account.isCustom || presetFor(account).allowsCustomBaseUrl) {
            account.customBaseUrl.takeIf { it.isNotBlank() }
        } else {
            null
        }

    /** Whether [account] needs an API key: built-in cloud providers do; custom/local servers may not. */
    fun requiresKey(account: ProviderAccount): Boolean =
        !account.isCustom && presetFor(account).apiKeyUrl != null

    /**
     * The on-device provider to retry [error] on as an offline fallback (#104), or null when it doesn't
     * apply: the fallback is disabled, the failure isn't a connectivity one, the active provider is
     * already local, or no local model is downloaded.
     */
    fun localFallbackProvider(
        context: Context,
        activePreset: ProviderPreset,
        error: DictateApiException,
    ): LocalTranscriptionProvider? {
        // "as a fallback if AssemblyAI gets rejected or is not happening for some reason" (Marko,
        // 7.11.2026; the on-device engine is Parakeet): with that choice ANY failure of the cloud call falls back, a refused key and a server
        // error as much as no network. The older switch keeps its narrower meaning (offline only).
        val engine = transcriptionEngine()
        val anyFailure = engine == ENGINE_FALLBACK
        if (!anyFailure && !prefs.dictate.localFallbackEnabled.get()) return null
        if (engine == ENGINE_ASSEMBLYAI) return null
        if (activePreset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) return null
        if (!anyFailure &&
            error.kind != DictateApiException.Kind.NETWORK &&
            error.kind != DictateApiException.Kind.TIMEOUT
        ) return null
        val localAccount = prefs.dictate.providerAccounts.get().getOrEmpty(ProviderRegistry.LOCAL.id)
        val localModel = transcriptionModelFor(context, localAccount, ProviderRegistry.LOCAL)
            .takeIf { it.isNotBlank() } ?: return null
        if (!LocalModelManager.isInstalled(context, localModel)) return null
        return LocalTranscriptionProvider(LocalTranscriptionProvider.modelDir(context, localModel))
    }
}
