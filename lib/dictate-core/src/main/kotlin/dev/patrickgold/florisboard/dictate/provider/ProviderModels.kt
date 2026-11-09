/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import java.io.File

/** What a provider can do. Most OpenAI-compatible endpoints do chat; only some do transcription. */
data class ProviderCapabilities(
    val chat: Boolean,
    val transcription: Boolean,
    /**
     * Text to speech, the reader's side of the app. Defaulted so every existing preset compiles
     * unchanged: only Speechify sets it, and nothing else here speaks.
     */
    val tts: Boolean = false,
)

/** A selectable model, as offered by a provider (statically or via [LlmProvider.listModels]). */
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    /**
     * Input modalities the model accepts (e.g. "text", "image", "audio"), when the provider's catalog
     * reports them (OpenRouter does, via `architecture.input_modalities`). Empty when unknown. A model
     * that accepts "audio" input is transcription-capable regardless of its name (issue #132).
     */
    val inputModalities: List<String> = emptyList(),
    /**
     * Output modalities the model produces, when reported. A dedicated speech-to-text model outputs
     * `transcription` (not `text`), which is how it's told apart from an audio-input chat model (#157).
     */
    val outputModalities: List<String> = emptyList(),
) {
    /**
     * True when the catalog says this model accepts audio input **as a chat model** (text output) → it
     * can transcribe via the single-call multimodal chat path (issue #130).
     */
    val acceptsAudioInput: Boolean get() = inputModalities.any { it.equals("audio", ignoreCase = true) }

    /**
     * True for a **dedicated** speech-to-text model (audio in → `transcription` out, e.g. OpenRouter's
     * MAI-Transcribe / Whisper / Parakeet). Served via the transcription endpoint, not the chat-audio
     * path — so it belongs in the transcription picker but must stay out of [acceptsAudioInput] (#157).
     */
    val isTranscriptionModel: Boolean
        get() = outputModalities.any { it.equals("transcription", ignoreCase = true) }
}

enum class ChatRole(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    /**
     * OpenAI-compatible `reasoning_effort` (e.g. `minimal`/`low`/`medium`/`high`) for reasoning models
     * (issue #141). Null omits the field entirely — the provider default is used and non-reasoning models
     * are unaffected.
     */
    val reasoningEffort: String? = null,
) {
    companion object {
        /** Convenience for the common single-user-message rewording case. */
        fun ofUser(model: String, prompt: String, reasoningEffort: String? = null) =
            ChatRequest(model, listOf(ChatMessage(ChatRole.USER, prompt)), reasoningEffort = reasoningEffort)
    }
}

data class TokenUsage(
    val promptTokens: Long,
    val completionTokens: Long,
)

data class ChatResult(
    val text: String,
    val usage: TokenUsage?,
)

data class TranscriptionRequest(
    val audioFile: File,
    val model: String,
    /** ISO language code, or null / "detect" for auto-detection. */
    val language: String? = null,
    /** Optional style/punctuation prompt to bias recognition. */
    val prompt: String? = null,
    /**
     * VERBATIM OR CORRECTED, for providers that return both.
     *
     * Only the Dictation API answers with two texts: `text` exactly as spoken, and `llm_response`
     * with filler removed, punctuation and capitals applied, and self-corrections resolved to what
     * the speaker landed on.
     *
     * **Which one he wants is a preference, not a fallback**, and it was neither until now: the code
     * preferred the corrected text and fell back to the verbatim one, so a rewrite that failed
     * looked exactly like a setting he had chosen. Making it explicit means the fallback can say it
     * happened.
     *
     * A provider that returns one text ignores this.
     */
    val preferCorrected: Boolean = true,
    /**
     * When [language] is auto-detect, the only languages detection may choose between.
     *
     * Unconstrained detection is not what "auto" means to someone who speaks exactly two languages:
     * a Croatian sentence with an English brand name in it can be read as Spanish, and the whole
     * transcript then comes back wrong. Empty means no constraint, for providers that cannot take a
     * shortlist and for users who really do want any language.
     */
    val languageCandidates: List<String> = emptyList(),
    /**
     * Names and terms the engine should favour (AssemblyAI `keyterms_prompt`: Universal-2 200 terms,
     * Universal-3.5 Pro 1000, sync and dictation 100 terms / 8000 characters). Empty sends nothing.
     * Only the AssemblyAI paths read it. A 400 from the engine retries once without it.
     */
    val keyterms: List<String> = emptyList(),
    /**
     * AssemblyAI `custom_spelling` (pre-recorded path only): misheard forms and the one-word spelling
     * they become. The engine's own rule: `to` is one word, `from` at most five words.
     */
    val customSpelling: List<Pair<List<String>, String>> = emptyList(),
)

data class TranscriptionResult(
    val text: String,
)
