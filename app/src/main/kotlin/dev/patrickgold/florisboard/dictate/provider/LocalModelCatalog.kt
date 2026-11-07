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

/**
 * One downloadable file of an on-device model. [destName] is the fixed name it is stored under (so the
 * runtime stays variant-agnostic — see [LocalTranscriptionProvider]); [sizeBytes] and [sha256] are
 * verified after download to guarantee integrity.
 */
data class LocalModelFile(
    val url: String,
    val destName: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

/**
 * A selectable on-device model (issue #104). [id] doubles as the install directory name and the value
 * stored in [ProviderAccount.transcriptionModel] for the local provider.
 */
data class LocalModelSpec(
    val id: String,
    val displayName: String,
    /** Short note for the picker, e.g. languages / accuracy/speed trade-off. */
    val description: String,
    val files: List<LocalModelFile>,
    /**
     * True for a *streaming* model (issue #233): it transcribes while the user is still speaking, so it
     * can drive the live/real-time path via [LocalRealtimeSession]. Offline models (Whisper, Parakeet)
     * only produce text once the whole utterance is in.
     *
     * This flag — not the presence of `joiner.onnx` — is what tells the two runtimes apart, because a
     * streaming transducer and an offline NeMo transducer both ship a joiner.
     */
    val isStreaming: Boolean = false,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * The fixed catalog of on-device models offered for download: one-shot recognizers (Whisper, NeMo
 * Parakeet). Streaming models were removed; since 7.11.2026 this list is Whisper Turbo only. All int8-quantised
 * sherpa-onnx builds.
 *
 * **Attribution / licensing:** every model here comes from an upstream project under a license that
 * permits redistribution (see each entry, and NOTICE). The files are mirrored on the project's own
 * GitHub release ([REL]) for a stable, project-controlled source instead of depending on a third party
 * at runtime. To re-point hosting, change [REL] only. The runtime never fetches this list — it is
 * shipped in the app.
 */
object LocalModelCatalog {

    /** Project-hosted mirror of the model files (GitHub release assets). Single re-point for hosting. */
    private const val REL = "https://github.com/DevEmperor/DictateKeyboard/releases/download/whisper-models-v1"

    /**
     * Silero VAD model, downloaded into every model dir so [LocalTranscriptionProvider] can segment
     * long audio at speech pauses (Whisper itself only handles ~30 s per pass). Same file for all models.
     */
    private val VAD_FILE = LocalModelFile(
        "$REL/silero_vad.onnx", LocalTranscriptionProvider.VAD, 643_854,
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )

    /** Hugging Face home of the sherpa-onnx export of Whisper large-v3-turbo (int8). */
    private const val HF_TURBO = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main"

    /** Install-dir id of Whisper Turbo; also the value stored as the local provider's model. */
    const val WHISPER_TURBO_ID = "whisper-turbo"

    /**
     * ~1 GB. OpenAI Whisper large-v3-turbo, int8, exported to ONNX by the sherpa-onnx project
     * (Marko, 7.11.2026: "use offline transcription services using Whisper Turbo Model ... remove the old
     * model"). It replaces Parakeet TDT 0.6B v3 (25 European languages, ~670 MB). Licensing: the Whisper
     * weights are MIT (OpenAI), the sherpa-onnx export is Apache-2.0; both allow redistribution.
     * Sizes and SHA-256 are the Hugging Face LFS values, verified after download.
     */
    val WHISPER_TURBO = LocalModelSpec(
        id = WHISPER_TURBO_ID,
        displayName = "Whisper Turbo",
        description = "Whisper large-v3-turbo · 99 languages, Croatian included · ~1 GB",
        files = listOf(
            LocalModelFile("$HF_TURBO/turbo-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 674_716_297, "b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b"),
            LocalModelFile("$HF_TURBO/turbo-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 361_080_764, "20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547"),
            LocalModelFile("$HF_TURBO/turbo-tokens.txt", LocalTranscriptionProvider.TOKENS, 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
            VAD_FILE,
        ),
    )

    /** Install-dir ids of models no longer offered, deleted from the phone on start (they were ~670 MB). */
    val RETIRED_IDS = listOf("parakeet-tdt-0.6b-v3")

    /** Install-dir id of the on-device Smart Turn v3 classifier (issue #191). */
    const val SMART_TURN_ID = "smart-turn-v3"

    /**
     * The Smart Turn v3.2 semantic turn-completion model for long-form auto-split (issue #191). Kept out
     * of [all] because it is not an STT model (it never appears in the transcription-model picker); it is
     * downloaded on demand from the Smart Turn checkbox in the long-form settings. Single derived model
     * file (Pipecat classifier + Whisper feature graph), verified after download.
     */
    val SMART_TURN = LocalModelSpec(
        id = SMART_TURN_ID,
        displayName = "Smart Turn v3",
        description = "On-device thought-completion model for long-form auto-split.",
        files = listOf(
            LocalModelFile(
                "$REL/smart-turn-v3.2-cpu.onnx", "smart-turn.onnx", 8_840_701,
                "7e7bfa1924cf89bd12ca9ba8f6d9165e3154884c377944911926ed9fda2f6bab",
            ),
        ),
    )

    /**
     * All catalog models in display order: Whisper Turbo only (7.11.2026). The streaming (Kroko) family
     * and Parakeet are gone; the
     * accessors below still exist because the picker and the provider code branch on them, and an
     * empty streaming list is the honest way to say "no live models" without touching that code:
     * [LocalModelSection] relies on this order to know where that heading goes.
     */
    val all: List<LocalModelSpec> = listOf(
        WHISPER_TURBO,
    )

    fun byId(id: String): LocalModelSpec? = all.firstOrNull { it.id == id }

    /** The streaming models, in display order — the "Live" group of the on-device picker (#233). */
    val streaming: List<LocalModelSpec> get() = all.filter { it.isStreaming }

    /** The classic one-shot models — everything that is not [streaming]. */
    val batchOnly: List<LocalModelSpec> get() = all.filter { !it.isStreaming }

    /** True if [id] names a streaming model. Unknown ids (e.g. a leftover pref) count as non-streaming. */
    fun isStreaming(id: String): Boolean = byId(id)?.isStreaming == true
}
