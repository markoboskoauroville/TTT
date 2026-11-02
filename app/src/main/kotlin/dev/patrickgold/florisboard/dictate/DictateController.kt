/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.media.MediaRecorder
import android.os.SystemClock
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.audio.AudioConcat
import dev.patrickgold.florisboard.dictate.audio.AudioDecode
import dev.patrickgold.florisboard.dictate.audio.AudioLevelSmoother
import dev.patrickgold.florisboard.dictate.audio.BluetoothMicRouter
import dev.patrickgold.florisboard.dictate.audio.LiveSpeechSplitter
import dev.patrickgold.florisboard.dictate.audio.SmartTurnModel
import dev.patrickgold.florisboard.dictate.audio.Pcm16Resampler
import dev.patrickgold.florisboard.dictate.audio.RecordingController
import dev.patrickgold.florisboard.dictate.audio.SpeechGate
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistorySource
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.dictate.provider.ChatRequest
import dev.patrickgold.florisboard.dictate.llm.MaLocalLlm
import dev.patrickgold.florisboard.dictate.llm.MaPhoneAnswer
import dev.patrickgold.florisboard.dictate.llm.MaPhoneModels
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.LocalRealtimeSession
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.RealtimeApi
import dev.patrickgold.florisboard.dictate.provider.RealtimeCallbacks
import dev.patrickgold.florisboard.dictate.provider.RealtimeClient
import dev.patrickgold.florisboard.dictate.provider.RealtimeSession
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionApi
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import dev.patrickgold.florisboard.dictate.provider.TranscriptionResult
import dev.patrickgold.florisboard.dictate.overlay.AccessibilitySink
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.recognition.RecognitionSink
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.AppVersionUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import dev.patrickgold.florisboard.dictate.audio.MaEncoder
import dev.patrickgold.florisboard.dictate.audio.MaResample
import dev.patrickgold.florisboard.dictate.nlp.MaDictationCorrections
import dev.patrickgold.florisboard.dictate.ui.MaMessage
import dev.patrickgold.florisboard.dictate.provider.MaKeyRing
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

/**
 * Orchestrates the dictation flow that fuses the recording, the provider layer and the editor: tap
 * to record, tap again to transcribe the audio and commit the result into the focused text field.
 *
 * Provider, API key and model are read from the unified JetPref store (`prefs.dictate`), which is
 * seeded once from the legacy Dictate settings on first run (see [dev.patrickgold.florisboard.
 * dictate.data.prefs.DictateLegacyMigrator]) and is editable in the in-app Dictate settings screen.
 *
 * Ported comfort features (all toggleable in the Dictate settings): pause/resume, cancel, audio
 * focus (pause other apps while recording), optional Bluetooth-SCO mic and a transcription retry
 * with a visible indicator.
 *
 * Rewording (GPT) is wired in: [applyPrompt] runs a prompt on the selection/cursor, [startLivePrompt]
 * sends a spoken instruction to the model, and every transcription runs through [postProcessTranscript]
 * (auto-formatting + auto-apply prompts). The prompt chips that drive these come later (UI phase).
 *
 * Not yet ported from the legacy service (later refinement): usage tracking.
 */
object DictateController {

    private const val LATENCY_LOG_TAG = "DictateLatency"
    private val latencyFlowIds = AtomicLong()

    /** Correlates privacy-safe phase timings for one batch transcription without logging its contents. */
    private data class BatchLatencyTrace(
        val id: Long = latencyFlowIds.incrementAndGet(),
        val startedNanos: Long = SystemClock.elapsedRealtimeNanos(),
    )

    private fun logLatency(trace: BatchLatencyTrace, phase: String, phaseStartedNanos: Long? = null) {
        val now = SystemClock.elapsedRealtimeNanos()
        val phaseMs = phaseStartedNanos?.let { TimeUnit.NANOSECONDS.toMillis(now - it) }
        Log.i(
            LATENCY_LOG_TAG,
            buildString {
                append("flow=").append(trace.id)
                append(" phase=").append(phase)
                phaseMs?.let { append(" phaseMs=").append(it) }
                append(" totalMs=")
                    .append(TimeUnit.NANOSECONDS.toMillis(now - trace.startedNanos))
            },
        )
    }

    sealed interface UiState {
        data object Idle : UiState
        data class Recording(
            /** [SystemClock.elapsedRealtime] when the current (running) segment started. */
            val startedAtMs: Long,
            /** Elapsed time accumulated across previous, already-finished segments (before pauses). */
            val accumulatedMs: Long = 0L,
            val paused: Boolean = false,
        ) : UiState
        /** [attempt] is 1 for the first try, 2/3/… while retrying after a transient failure. */
        /**
         * A transcription is in flight, or [held].
         *
         * HELD means: the request was thrown away and the audio kept. He tapped the middle of the
         * sending line to stop it, and the next tap sends the same audio again in whatever language
         * the badge says by then.
         *
         * A flag on this state rather than a state of its own, because everything that asks "is the
         * recorder busy" must keep answering yes: the audio is still here, still unsent, and still
         * his. A fourth state would have meant auditing every `when` in the app for a case that
         * means exactly what Transcribing already means.
         */
        data class Transcribing(val attempt: Int = 1, val held: Boolean = false) : UiState
        /** A rewording/GPT request is in flight (manual prompt, auto-apply, auto-format or live). */
        data class Rewording(val label: String) : UiState
        /**
         * A failed transcription/rewording (roadmap 1.12). [message] is the short, localized headline
         * (derived from [kind]); [detail] is the raw provider text shown when the user taps the chip;
         * [action] is the contextual button offered (resend the kept audio, open settings, or none).
         */
        data class Error(
            val message: String,
            val kind: DictateApiException.Kind? = null,
            val action: ErrorAction = ErrorAction.NONE,
            val detail: String? = null,
            /** Informational (not a failure), e.g. "no speech detected" — rendered neutral, not red. */
            val neutral: Boolean = false,
        ) : UiState
        /**
         * Offer to send a recording that was interrupted because the keyboard closed mid-recording: the
         * audio was finalized and persisted, so on the next keyboard open this neutral (non-error) chip
         * offers to transcribe it or discard it. [seconds] is the captured length, shown for context.
         */
        data class Interrupted(val seconds: Long) : UiState
        /**
         * A one-time Smartbar nudge (roadmap 9.7/9.8). [message] overrides the kind's static text for
         * nudges whose text is dynamic (the [PromoKind.MILESTONE] celebration, issue #142).
         */
        data class Promo(val kind: PromoKind, val message: String? = null) : UiState
    }

    /** Why an audio file is being kept for a one-tap re-send (drives the unified resend chip copy/tint). */
    enum class RetainReason {
        /** A transcription/rewording failed; the kept audio can be retried (in-memory, cache file). */
        FAILED,

        /** The keyboard closed mid-recording; the finalized audio was persisted to survive process death. */
        INTERRUPTED,
    }

    /** The contextual action a [UiState.Error] offers (see roadmap 1.12 keyboard design). */
    enum class ErrorAction {
        /** No action; the chip auto-clears after a moment. */
        NONE,

        /** Retry the same kept audio (transient failures with retained audio, roadmap 10.3). */
        RESEND,

        /** Open the Dictate provider settings (fixable errors like an invalid/missing API key). */
        OPEN_SETTINGS,

        /**
         * Export the kept recording to Downloads (issue #144): offered when transcription fails for a
         * reason that resending can't fix (too large / unsupported format) so a long recording isn't lost.
         */
        SAVE_AUDIO,
    }

    /**
     * Which one-time nudge is being shown. RATE/DONATE are usage-gated (see [maybePromptForReview]);
     * CHANGELOG is shown right after an app update (see [maybePromptChangelog]) and opens the in-app
     * "What's new" dialog instead of a web page.
     */
    enum class PromoKind { RATE, DONATE, CHANGELOG, FLOATING_BUTTON, MILESTONE }

    /**
     * Where the active dictation's output goes: the keyboard editor ([OutputTarget.IME]) or the
     * accessibility-injected field of the floating button ([OutputTarget.OVERLAY], issue #88). Set when a
     * dictation starts (the mic-tap entry points carry their source); the two never drive concurrently.
     */
    enum class OutputTarget { IME, OVERLAY, RECOGNITION_SERVICE }

    /**
     * Temporary debug switch to preview the "Dictate was updated" Smartbar nudge. When true, the nudge
     * is offered on every keyboard open (the real version gate never triggers on debug builds, whose
     * version name carries an unparseable suffix). MUST be false for any committed/shipped build.
     */
    private const val DEBUG_FORCE_CHANGELOG_NUDGE = false

    /** Forces the floating-button spotlight regardless of gates (testing only). MUST be false for shipped builds. */
    private const val DEBUG_FORCE_FB_SPOTLIGHT = false

    private val prefs by FlorisPreferenceStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Press Send once this dictation has landed in the field.
     *
     * Set by volume down while a recording is running, and consumed exactly once. The whole feature
     * is one gesture: volume up to start, volume down to stop, transcribe and send, with nothing to
     * wait for and nothing else to press.
     *
     * `@Volatile` because it is written from the key handler on the main thread and read from the
     * transcription coroutine, and a stale read here would either drop the send or fire an old one.
     */
    @Volatile
    var sendAfterCommit: Boolean = false

    /**
     * How long to wait after the words land before pressing Send on screen.
     *
     * See the note at the call site: the pause is for the *other* app, not for this one.
     */
    private const val SEND_SETTLE_MS = 333L

    /**
     * Whether the last dictation actually reached a field.
     *
     * Set by the commit, read when the history entry is built. The realtime path delivers through a
     * different function and leaves this true, which is correct: it only runs while a field is open.
     */
    @Volatile
    private var deliveredToField: Boolean = true

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Voice Type: a plain sentence describing what the request is doing right now.
     *
     * The spinner alone says "something is happening", which is not the same as "your first key was
     * out of quota and the second one is uploading 240 kB". Everything that used to be invisible
     * behind the spinner is written here and shown under it.
     */
    private val _maStatus = MutableStateFlow("")
    val maStatus: StateFlow<String> = _maStatus.asStateFlow()

    private val _prompts = MutableStateFlow<List<PromptModel>>(emptyList())
    /** The user's saved prompts (shared `prompts.db`), refreshed via [refreshPrompts]; drives the Smartbar prompt chips. */
    val prompts: StateFlow<List<PromptModel>> = _prompts.asStateFlow()

    private val _pendingPrompts = MutableStateFlow<List<PromptModel>>(emptyList())
    /**
     * Prompts queued by tapping the always-on prompt row while recording (ROW layout). They are applied
     * in tap order to the finished transcript before it is committed (see [applyPendingPrompts]); the UI
     * highlights every queued prompt in the accent color. Empty whenever no recording queue is active.
     */
    val pendingPrompts: StateFlow<List<PromptModel>> = _pendingPrompts.asStateFlow()

    private val _livePromptActive = MutableStateFlow(false)
    /**
     * True while a *live-prompt* recording is in progress, so the live-prompt chip can show the same
     * accent highlight the queued prompt chips use. Tapping the chip again stops the recording (toggle),
     * which clears this. Set/cleared alongside the recording lifecycle.
     */
    val livePromptActive: StateFlow<Boolean> = _livePromptActive.asStateFlow()

    // --- Real-time streaming transcription (issue #128) -----------------------------------------
    private val _interimText = MutableStateFlow("")
    /**
     * Live transcript while a real-time recording runs: finalized segments plus the current partial. The
     * Smartbar shows this as a live caption; the field only receives the finished (reworded) text on stop.
     * Empty outside a realtime recording.
     */
    val interimText: StateFlow<String> = _interimText.asStateFlow()

    private var realtimeSession: RealtimeSession? = null
    private val realtimeFinal = StringBuilder()      // accumulated finalized segments
    @Volatile private var realtimeFailed = false     // stream errored → fall back to batch on stop
    private var realtimeClosed: CompletableDeferred<Unit>? = null
    private var realtimeContext: Context? = null     // app context to edit the field's provisional text
    private val realtimeShown = StringBuilder()       // text currently committed to the field this session
    @Volatile private var realtimeCancelled = false   // block late stream callbacks from re-adding text

    // --- Long-form segmented dictation (issue #170) ---------------------------------------------
    // Segmented mode transcribes cut segments in the background while recording continues, appending raw
    // text to the field as a live preview (reusing [realtimeShown] as the shown-text buffer, since
    // segmented and realtime are mutually exclusive). All formatting/rewording runs ONCE at the end via
    // finalizeAndCommit(finalizeViaComposing=true), which replaces the preview with the finished text.
    private var segmentedActive = false
    private var segmentNextIndex = 0          // next index to assign to a cut segment (cut order)
    private var segmentCommitIndex = 0        // next index expected by the ordered commit drain
    private val segmentResults = HashMap<Int, String>()  // index -> raw text, buffered until in-order
    private val segmentJobs = mutableSetOf<Job>()
    private val segmentMutex = Mutex()        // orders index assignment + rotate + the commit drain
    private var segmentInFlightCount = 0      // segments cut but not yet committed
    private var segmentStopped = false        // stop requested; finish once the queue drains
    private var segmentRecordedSeconds = 0L
    private var segmentVad: LiveSpeechSplitter? = null  // live VAD auto-split, when enabled (Phase 2)
    private val segmentAudioFiles = HashMap<Int, File>()  // kept segment WAVs (index -> file) for history merge
    private var segmentKeepAudio = false                  // whether to keep + merge segment audio (retention on)
    private val _segmentFlushCount = MutableStateFlow(0)
    /** Monotonic count of segment cuts — the recording bar flashes the Next button on each change (#170). */
    val segmentFlushCount: StateFlow<Int> = _segmentFlushCount.asStateFlow()
    private val _segmentedRecording = MutableStateFlow(false)
    /** True while a long-form segmented recording is active — drives the "Next segment" button (#170). */
    val segmentedRecording: StateFlow<Boolean> = _segmentedRecording.asStateFlow()
    private val _segmentsInFlight = MutableStateFlow(0)
    /** How many cut segments are transcribing in the background — drives the recording-bar badge (#170). */
    val segmentsInFlight: StateFlow<Int> = _segmentsInFlight.asStateFlow()

    private var recorder: RecordingController? = null
    private var startJob: Job? = null

    // --- Push-to-talk (issue #235) ---------------------------------------------------------------

    private fun setPushToTalk(
        phase: PushToTalkPhase = _pushToTalkVisuals.value.phase,
        lockFlash: Boolean = _pushToTalkVisuals.value.lockFlash,
        discarding: Boolean = _pushToTalkVisuals.value.discarding,
    ) {
        _pushToTalkVisuals.value = PushToTalkVisuals(phase, lockFlash, discarding)
        _pushToTalkPhase.value = phase
    }

    private val _pushToTalkPhase = MutableStateFlow(PushToTalkPhase.NONE)
    /**
     * Where a hold-to-record gesture currently stands, so the recording bar can show the matching
     * affordance ("slide to cancel", the armed-cancel state, or the ordinary bar once locked).
     */
    val pushToTalkPhase: StateFlow<PushToTalkPhase> = _pushToTalkPhase.asStateFlow()

    private val _cancelSlideProgress = MutableStateFlow(0f)
    /** How far the finger has slid towards discarding, 0..1 — the bar slides its content with it. */
    val cancelSlideProgress: StateFlow<Float> = _cancelSlideProgress.asStateFlow()

    private val _lockSlideProgress = MutableStateFlow(0f)
    /** How far the finger has slid towards the lock, 0..1 — the lock target fills with it. */
    val lockSlideProgress: StateFlow<Float> = _lockSlideProgress.asStateFlow()


    /**
     * Set when the finger is lifted before [startRecording]'s job has produced a recorder. Starting is
     * asynchronous (audio focus, and Bluetooth SCO can take seconds), so a short press-and-release
     * regularly outruns it; the job honours this once there is actually something to stop.
     */
    @Volatile private var pttStopPending = false

    private val _audioLevel = MutableStateFlow(0f)
    /**
     * Shared, noise-gated microphone level for lightweight recording visuals. Sampling once here keeps
     * the Smartbar and floating overlay from competing for [RecordingController.maxAmplitude]'s
     * read-and-reset peak. Values are smoothed and normalized to 0..1 at 20 Hz.
     */
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    private var audioLevelJob: Job? = null

    // While a recording is active we listen for the screen turning off (device locked / display timeout):
    // that is the reliable "the user has left" signal that finalizes and keeps the recording and releases
    // the mic, instead of depending on IME teardown callbacks that are not always delivered (issue #147).
    // Long recordings stay possible — this never fires while the screen is on. Held at process scope
    // (alongside the recorder) with the context used to register it, so any stop path can unregister.
    private var screenOffReceiver: BroadcastReceiver? = null
    private var screenOffContext: Context? = null

    /**
     * Application context, latched the first time a recording starts. Teardown paths run from places
     * that have no context to hand, and releasing the microphone service is not something to skip
     * because the caller happened not to be holding one.
     */
    private var maAppContext: Context? = null

    /** Releases the microphone foreground service, from anywhere, as often as needed. */
    private fun maReleaseMic() {
        maAppContext?.let { MaRecordingService.stop(it) }
    }

    /** The in-flight transcription coroutine, cancellable via the stop button (see [cancelTranscription]). */
    private var transcribeJob: Job? = null

    // The in-flight manual rewording coroutine (a prompt chip / "Send"), so the stop button can abort it
    // mid-generation (issue #192). The post-transcription rewording chain instead runs inside
    // [transcribeJob]; [cancelRewording] cancels whichever is active.
    private var rewordJob: Job? = null

    /** Held only while recording, so the music can be given back the moment it ends. */
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var btRouter: BluetoothMicRouter? = null

    /** When true, the next finished recording is fed to the rewording model instead of committed. */
    private var livePromptArmed = false

    /** Field text before a live prompt rewrote it, kept so the archive can offer the original back. */
    private var livePromptOriginal = ""

    /** True when the live-prompt answer should replace the whole field rather than insert at the cursor. */
    private var livePromptReplacesField = false

    /** Output destination of the in-flight dictation; see [OutputTarget]. Reset to IME when idle. */
    private var outputTarget = OutputTarget.IME

    // Haptic feedback (#166) fires on dictation state transitions. Started lazily on the first dictation
    // (so we have an application context for the vibrator), then it observes for the whole process life.
    private var hapticObserverStarted = false
    private fun ensureHapticObserver(context: Context) {
        if (hapticObserverStarted) return
        hapticObserverStarted = true
        val appContext = context.applicationContext
        // Capture the current state synchronously (the caller is about to change it): the launched
        // collector otherwise reads _state.value only after the change and would miss the first transition.
        val initial = _state.value
        scope.launch {
            var prev: UiState = initial
            _state.collect { new ->
                when {
                    // Record started — skipped for the floating button when its own tap already buzzed
                    // (no double buzz); a resend enters at Transcribing so it never matches here.
                    new is UiState.Recording && prev !is UiState.Recording -> {
                        val buttonAlreadyBuzzed = outputTarget == OutputTarget.OVERLAY &&
                            prefs.dictate.floatingButtonHaptic.get()
                        if (!buttonAlreadyBuzzed) DictateHaptics.short(appContext)
                    }
                    // Record stopped → transcribing (a resend is Idle→Transcribing and is ignored).
                    prev is UiState.Recording && new is UiState.Transcribing -> DictateHaptics.short(appContext)
                    // Transcription ready (heading to commit/idle or on to a rewording pass) — not on failure.
                    prev is UiState.Transcribing && (new is UiState.Idle || new is UiState.Rewording) ->
                        DictateHaptics.double(appContext)
                    // Rewording / LLM prompt applied.
                    prev is UiState.Rewording && new is UiState.Idle -> DictateHaptics.medium(appContext)
                }
                prev = new
            }
        }
    }

    /**
     * A single audio file kept for a one-tap re-send, used by both the error-resend chip and the
     * interrupted-recording chip (unified resend path). [reason] distinguishes a failed transcription
     * (kept in cache, in-memory only) from a recording interrupted by the keyboard closing (finalized
     * and persisted to filesDir, mirrored by the `interruptedAudio*` prefs so it survives process death).
     */
    private data class RetainedAudio(
        val file: File,
        val reason: RetainReason,
        val wasLive: Boolean,
        val seconds: Long,
    )

    /** The currently kept audio (failed or interrupted), or null when there is nothing to re-send. */
    private var retained: RetainedAudio? = null

    /**
     * Metadata threaded from a transcription into [finalizeAndCommit] so the finished dictation can be
     * logged to the history store (issue #140). [audioFile] is the (still-present) recorded WAV to retain
     * when audio retention is on. [isReplay] marks a re-transcription of already-counted audio so stats
     * aren't double-counted, and [replayHistoryId] (when set) updates that existing entry's text in place
     * instead of inserting a new row.
     */
    private data class HistoryCapture(
        val audioFile: File?,
        val providerId: String,
        val providerName: String,
        val model: String,
        val language: String,
        val source: String,
        val isReplay: Boolean = false,
        val replayHistoryId: Long? = null,
    )

    /**
     * A previously captured audio segment to prepend to the next finished recording, set when the user
     * chooses to *continue* an interrupted recording (see [continueInterruptedRecording]). The new
     * segment is recorded normally and the two are merged ([AudioConcat]) before transcription. Null
     * unless a continuation is in progress.
     */
    private var carryOverAudio: File? = null

    /** Recorded seconds of [carryOverAudio], so the continued recording's total length stays correct. */
    private var carryOverSeconds = 0L

    /** Cache file name for the merged audio when a continued interrupted recording is stitched together. */
    private const val MERGED_AUDIO_NAME = "dictate_merged.wav"
    // Silence trimming (issue #232): cache file for the trimmed upload, plus the gap thresholds — a silence
    // gap longer than TRIM_MAX_SILENCE_MS is collapsed down to TRIM_KEEP_SILENCE_MS (a short pad on each
    // side of the cut); shorter, natural pauses are left untouched.
    private const val TRIMMED_AUDIO_NAME = "dictate_trimmed.wav"
    /**
     * HOW LONG A PAUSE HAS TO BE BEFORE IT IS CUT, AND HOW MUCH OF IT SURVIVES.
     *
     * These two numbers are the whole cost of dictation. Async speech-to-text bills by the second of
     * audio, and dictation is roughly half silence — thinking, breathing, reading the screen. **The
     * seconds not uploaded are free, and they cost nothing in quality, because they carried no
     * words.**
     *
     * They were 2000/400: only pauses longer than TWO SECONDS were touched. That left every ordinary
     * mid-sentence pause — the half-second and the second-and-a-half, which is most of them — paid
     * for in full. Tightened to 700/250 for the money.
     *
     * 700ms is above the longest gap inside fluent speech and below the shortest deliberate pause. A
     * gap that long is somebody thinking, not somebody speaking.
     *
     * 250ms is kept rather than 0 for two reasons that both cost more than the silence saves: a hard
     * cut mid-breath sounds like a splice and makes the recogniser guess at the join, and a sentence
     * with every pause removed reads back as one breathless run. It is also the floor for not
     * clipping the first syllable after the pause — **a clipped word costs a re-record, which costs
     * more than the silence did.**
     */
    private const val TRIM_MAX_SILENCE_MS = 700
    private const val TRIM_KEEP_SILENCE_MS = 250
    // Realtime (#128): after finish(), how long to wait for the provider to flush the last words before we
    // commit the already-streamed text. Short — the text is already on screen; we only wait for the tail.
    private const val REALTIME_FINALIZE_TIMEOUT_MS = 1_200L

    private const val AUDIO_LEVEL_SAMPLE_MS = 50L

    /** Cumulative recorded audio (seconds) after which the rate / donate nudges appear (roadmap 9.7/9.8). */

    /** How long the discarded mic takes to reach the bin; the bar outlives the recording by this much. */
    const val PUSH_TO_TALK_FLIGHT_MS = 950L

    /** How long the key shows a lock after latching, before dissolving into its ordinary icon. */
    const val PUSH_TO_TALK_LOCK_FLASH_MS = 600L

    private const val RATE_THRESHOLD_SECONDS = 180L   // 3 min
    private const val DONATE_THRESHOLD_SECONDS = 300L // 5 min (user choice; legacy used 10 min)

    /**
     * Single entry point for the mic button: starts recording, or stops and transcribes. [target]
     * selects where the finished text goes — the keyboard editor for the in-keyboard mic (default), or
     * the accessibility-injected field for the floating button (issue #88). It is latched when a fresh
     * recording starts, so the stop tap from the same source uses the same destination.
     */
    /**
     * Hold-to-record (issue #235), the walkie-talkie alternative to the tap-toggle [onMicClick]. The
     * gesture layer calls [onPushToTalkDown] on press, [onPushToTalkSlide] as the finger moves,
     * [lockPushToTalk] when it is slid far enough up, and [onPushToTalkUp] on release.
     *
     * Works for plain and real-time recordings alike — both are a start/stop pair, and real-time even
     * suits it better because text appears while the finger is still down. Long-form segmented is
     * excluded by the caller: holding a finger down for a ten-minute dictation defeats the point.
     */
    fun onPushToTalkDown(context: Context, target: OutputTarget = OutputTarget.IME) {
        // Idempotent: the gesture layer can be torn down and restarted mid-press (a recomposition with a
        // new pointerInput key), and re-entering here would otherwise restart the clock — making a long
        // hold look like a tap — and start a second recording. The window is widest with real-time on,
        // where opening the socket keeps the state Idle for longer.
        if (_pushToTalkPhase.value != PushToTalkPhase.NONE) return
        if (!canStartRecording()) return
        pttStopPending = false
        // A new hold always starts where the key is. Latching ends the gesture through lockPushToTalk,
        // which never cleared these, so the next mic was drawn at the height the last one was let go at
        // until the first movement corrected it.
        _cancelSlideProgress.value = 0f
        _lockSlideProgress.value = 0f
        setPushToTalk(phase = PushToTalkPhase.HOLDING)
        outputTarget = target
        startRecording(context)
    }

    /**
     * Reports how far the finger has slid towards the cancel target: [progress] 0..1, where 1 means it
     * reached it. Crossing it discards the recording immediately rather than waiting for the release —
     * that is what voice-message UIs do, and waiting would leave the user holding a recording they have
     * already thrown away. Returns true once the gesture is over so the caller can stop tracking.
     */
    fun onPushToTalkSlide(progress: Float): Boolean {
        if (!_pushToTalkPhase.value.isHolding) return _pushToTalkPhase.value != PushToTalkPhase.LOCKED
        _cancelSlideProgress.value = progress.coerceIn(0f, 1f)
        if (progress >= 1f) {
                        setPushToTalk(phase = PushToTalkPhase.CANCEL_ARMED, discarding = true)
            cancelRecording(keepBarForMs = PUSH_TO_TALK_FLIGHT_MS + 120L)
            return true
        }
        return false
    }

    /** Slide-down progress towards the lock, 0..1 — drives how far the lock target fills. */
    fun onPushToTalkLockSlide(progress: Float) {
        if (_pushToTalkPhase.value.isHolding) _lockSlideProgress.value = progress.coerceIn(0f, 1f)
    }

    /** Aborts a held recording outright — the gesture was taken over (bubble drag) or the system cancelled it. */
    fun cancelPushToTalk() {
        if (_pushToTalkPhase.value == PushToTalkPhase.NONE) return
        cancelRecording()
    }

    /** Latches the recording so it keeps running after the finger lifts (slide down into the lock). */
    fun lockPushToTalk() {
        if (_pushToTalkPhase.value == PushToTalkPhase.HOLDING) {
            // Latched *and* flashing in the same emission: as two flows the key was drawn once with
            // its ordinary icon in between, which is exactly the frame this replaces.
            setPushToTalk(phase = PushToTalkPhase.LOCKED, lockFlash = true)
            scope.launch {
                delay(PUSH_TO_TALK_LOCK_FLASH_MS)
                setPushToTalk(lockFlash = false)
            }
        }
    }

    private val _pushToTalkVisuals = MutableStateFlow(PushToTalkVisuals())
    /** Phase, lock confirmation and discard flight as one value — see [PushToTalkVisuals]. */
    val pushToTalkVisuals: StateFlow<PushToTalkVisuals> = _pushToTalkVisuals.asStateFlow()

    /** Finger lifted: send, or silently drop a press too short to be speech. */
    fun onPushToTalkUp(context: Context) {
        val phase = _pushToTalkPhase.value
        // Locked: the recording carries on and is ended by the stop button, exactly like tap-toggle.
        if (phase == PushToTalkPhase.LOCKED || phase == PushToTalkPhase.NONE) return
        // Released on the discard target: go straight there without passing through NONE, which would be
        // one emission in which the mic is neither held nor flying — and therefore not on screen.
        if (phase == PushToTalkPhase.CANCEL_ARMED) {
            setPushToTalk(phase = PushToTalkPhase.CANCEL_ARMED, discarding = true)
            cancelRecording(keepBarForMs = PUSH_TO_TALK_FLIGHT_MS + 120L)
            return
        }
        setPushToTalk(phase = PushToTalkPhase.NONE)
        _cancelSlideProgress.value = 0f
        _lockSlideProgress.value = 0f
        // Releases arrive from the window's own touch stream now (see DictateHoldTouch), so a short one is
        // a short one. This used to latch anything under 400 ms, because real-time holds were being ended
        // by a release nobody made about 100 ms in — which also meant a deliberately brief hold latched
        // instead of sending.
        if (_state.value is UiState.Recording) {
            stopAndTranscribe(context)
            return
        }
        // Still starting up — let the start job stop it the moment the recorder exists.
        if (startJob?.isActive == true) pttStopPending = true else cancelRecording()
    }

    /**
     * True when the mic should behave as hold-to-record: the user enabled it and the upcoming recording
     * is not long-form segmented, which cannot sensibly be held down for its whole duration.
     */
    fun isPushToTalkActive(context: Context): Boolean =
        prefs.dictate.pushToTalk.get() && !isSegmentedMode(context.applicationContext)

    /**
     * True when a tap on the mic would start a fresh recording — everything except a recording already
     * running or a request in flight. Notably that includes the interrupted-recording chip (issue #111):
     * it is a resting state with an offer on it, so holding the mic there has to work exactly as it does
     * on a plain idle keyboard, which it did not while this was an `is UiState.Idle` check.
     */
    fun canStartRecording(): Boolean = when {
        discardingBar -> false
        else -> when (_state.value) {
            is UiState.Recording, is UiState.Transcribing, is UiState.Rewording -> false
            else -> true
        }
    }

    /**
     * True while the recording bar is only still on screen so a discarded mic has somewhere to land.
     * Nothing is being captured any more, so every entry point has to step aside rather than act on a
     * state that looks like a live recording but is not one.
     */
    @Volatile private var discardingBar = false

    /**
     * True while the recording in hand was started by the E>H key: transcribed as English, then
     * translated into Croatian in his style (MaTranslate) before it is written. Set at every start,
     * so any other way of recording clears it.
     */
    @Volatile
    private var translateRecording = false

    /** Whether the recording in hand was started by E>H, for its key's ring. */
    fun isTranslating(): Boolean = translateRecording

    fun onMicClick(context: Context, target: OutputTarget = OutputTarget.IME, translate: Boolean = false) {
        // The bar is a leftover from a discard that is still animating — acting on it would stop a
        // recording that no longer exists.
        if (discardingBar) return
        when (_state.value) {
            is UiState.Recording -> stopAndTranscribe(context)
            // Tapping the mic while transcribing or rewording aborts it (the button shows a stop icon,
            // see the ComputingEvaluator) — e.g. after accidentally sending a prompt (issue #192).
            is UiState.Transcribing -> cancelTranscription()
            is UiState.Rewording -> cancelRewording()
            else -> {
                outputTarget = target
                translateRecording = translate
                startRecording(context)
            }
        }
    }

    /**
     * Toggles a prompt in the recording-time queue (ROW layout): while a recording/transcription is in
     * flight, tapping a prompt chip enqueues it (or removes it if already queued) instead of applying it
     * immediately. The queue is applied in tap order to the finished transcript (see [applyPendingPrompts]).
     * No-op outside the recording/transcribing states or for non-persisted prompts.
     */
    fun togglePendingPrompt(prompt: PromptModel) {
        if (_state.value !is UiState.Recording && _state.value !is UiState.Transcribing) return
        if (!prompt.isPersisted()) return
        val current = _pendingPrompts.value
        _pendingPrompts.value = if (current.any { it.id == prompt.id }) {
            current.filterNot { it.id == prompt.id }
        } else {
            current + prompt
        }
    }

    /** Toggles pause/resume of the in-progress recording. No-op outside the recording state. */
    fun togglePause() {
        val current = _state.value as? UiState.Recording ?: return
        val rec = recorder ?: return
        if (current.paused) {
            rec.resume()
            _state.value = current.copy(startedAtMs = SystemClock.elapsedRealtime(), paused = false)
        } else {
            rec.pause()
            val segment = SystemClock.elapsedRealtime() - current.startedAtMs
            _state.value = current.copy(accumulatedMs = current.accumulatedMs + segment, paused = true)
        }
    }

    /** The active dictation language (defaults to auto-detect); read live from the JetPref store. */
    fun activeLanguage(): DictateLanguage = DictateLanguages.of(prefs.dictate.activeInputLanguage.get())

    /** Advances the active language to the next entry in the user's selected subset (no-op if ≤1). */
    fun cycleLanguage() {
        val selection = DictateLanguages.parseSelection(prefs.dictate.inputLanguages.get())
        if (selection.size <= 1) return
        val currentCode = prefs.dictate.activeInputLanguage.get()
        val idx = selection.indexOfFirst { it.code == currentCode }
        val next = selection[(idx + 1) % selection.size] // idx == -1 (unknown) → starts at index 0
        scope.launch { prefs.dictate.activeInputLanguage.set(next.code) }
    }

    /** Sets the active dictation language explicitly (from the recording bar's language picker). */
    fun setLanguage(code: String) {
        scope.launch { prefs.dictate.activeInputLanguage.set(code) }
    }

    /**
     * Snaps [activeInputLanguage] back into the current [inputLanguages] selection. A stale active
     * code — most importantly "detect" left over after the user disabled auto-detect — would otherwise
     * keep the transcription request on auto-detect (language = null, so e.g. Portuguese gets detected
     * as English) and show a phantom globe on the recording bar. Falls back to the first selected
     * language and persists the correction. No-op when the active code is already selected.
     */
    private suspend fun reconcileActiveLanguage() {
        // A stored "detect" is repaired here rather than tolerated. It cannot be reached any more —
        // no key sets it and no screen offers it — but an install that has been running since before
        // auto-detect was removed still holds it, and this is the one place that runs before every
        // recording. MaLanguage.active() reads it as Croatian either way; this makes the stored value
        // agree with what the app is actually doing, so the badge and the request cannot disagree.
        if (prefs.dictate.activeInputLanguage.get() == DictateLanguages.DETECT) {
            prefs.dictate.activeInputLanguage.set(MaLanguage.HR)
        }
        val selection = DictateLanguages.parseSelection(prefs.dictate.inputLanguages.get())
            .filter { it.code != DictateLanguages.DETECT }
        if (selection.isEmpty()) return
        val current = prefs.dictate.activeInputLanguage.get()
        if (selection.none { it.code == current }) {
            prefs.dictate.activeInputLanguage.set(selection.first().code)
        }
    }

    /**
     * Reloads the prompt list from the shared `prompts.db` into [prompts]. Cheap and idempotent;
     * called when the keyboard (re-)appears so the chip strip reflects edits made in the settings.
     */
    fun refreshPrompts(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            _prompts.value = withContext(Dispatchers.IO) { promptsDb(appContext).getAll() }
        }
    }

    /** Clears a transient error back to idle (the Smartbar UI calls this after showing it briefly). */
    fun clearError() {
        if (_state.value is UiState.Error) _state.value = UiState.Idle
    }

    /**
     * Opens the Dictate provider settings from the keyboard, used by the "fixable" errors (e.g. an
     * invalid or missing API key, roadmap 1.12). Launched as a new task since an IME has no activity of
     * its own; clears the error afterwards so the Smartbar returns to normal.
     */
    fun openProviderSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/settings/dictate/providers"))
                    // BROWSABLE is required: FlorisAppActivity.onNewIntent only routes a VIEW intent to the
                    // nav-graph deep-link handler when it carries this category, otherwise it treats the
                    // intent as an extension-import and lands on the wrong screen.
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        clearError()
    }

    /** Localized one-line headline for an API error [kind] (roadmap 1.12 specific error messages). */
    private fun errorMessageRes(kind: DictateApiException.Kind): Int = when (kind) {
        DictateApiException.Kind.INVALID_API_KEY -> R.string.dictate__error_invalid_api_key
        DictateApiException.Kind.QUOTA_EXCEEDED -> R.string.dictate__error_quota_exceeded
        DictateApiException.Kind.RATE_LIMITED -> R.string.dictate__error_rate_limited
        DictateApiException.Kind.CONTENT_SIZE_LIMIT -> R.string.dictate__error_content_size_limit
        DictateApiException.Kind.FORMAT_NOT_SUPPORTED -> R.string.dictate__error_format_not_supported
        DictateApiException.Kind.TIMEOUT -> R.string.dictate__error_timeout
        DictateApiException.Kind.NETWORK -> R.string.dictate__error_network
        DictateApiException.Kind.SERVER_ERROR -> R.string.dictate__error_server
        DictateApiException.Kind.UNKNOWN -> R.string.dictate__error_unknown
    }

    /**
     * Builds an [UiState.Error] from an API exception: a localized headline (per [DictateApiException.Kind]),
     * the raw provider text kept as the tappable detail, and the contextual action — resend the kept audio
     * for retryable failures, open settings for a bad/missing key, otherwise none.
     */
    /** Failures where resending is pointless but the recording is worth saving instead (issue #144). */
    private val EXPORTABLE_ERROR_KINDS = setOf(
        DictateApiException.Kind.CONTENT_SIZE_LIMIT,
        DictateApiException.Kind.FORMAT_NOT_SUPPORTED,
    )

    /** Voice Type: clears the verbose line once nothing is in flight. */
    private fun maClearStatus() {
        _maStatus.value = ""
    }

    private fun apiError(e: DictateApiException, context: Context, canResend: Boolean): UiState.Error {
        maClearStatus()
        val action = when {
            canResend && e.kind in EXPORTABLE_ERROR_KINDS -> ErrorAction.SAVE_AUDIO
            canResend && e.kind.isRetryable -> ErrorAction.RESEND
            e.kind == DictateApiException.Kind.INVALID_API_KEY -> ErrorAction.OPEN_SETTINGS
            else -> ErrorAction.NONE
        }
        return UiState.Error(
            message = context.getString(errorMessageRes(e.kind)),
            kind = e.kind,
            action = action,
            detail = e.message?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Declines the kept audio (whether from a failed transcription or an interrupted recording): drops
     * the audio and returns the Smartbar to idle. Shared dismiss (✗) for both resend chips.
     */
    fun dismissRetainedAudio() {
        discardRetainedAudio()
        if (_state.value is UiState.Error || _state.value is UiState.Interrupted) {
            _state.value = UiState.Idle
        }
    }

    /**
     * System voice input entry points (issue #67), driven by [DictateRecognitionService]. They record via
     * the normal pipeline but latch [OutputTarget.RECOGNITION_SERVICE], so the finished text is handed back
     * to the calling app through the recognition callback instead of being written into a field. Always
     * plain batch (no realtime/segmented) — see [openRealtimeSession] / [isSegmentedMode].
     */
    fun startRecognition(context: Context) {
        // Busy with another dictation → ignore; the service will time out and report an error.
        if (_state.value is UiState.Recording ||
            _state.value is UiState.Transcribing ||
            _state.value is UiState.Rewording
        ) return
        outputTarget = OutputTarget.RECOGNITION_SERVICE
        startRecording(context)
    }

    /** Stops the recognition recording and transcribes it; the result flows to the recognition callback. */
    fun stopRecognition(context: Context) {
        if (_state.value is UiState.Recording) stopAndTranscribe(context)
    }

    /** Aborts a recognition recording without transcribing (the caller cancelled). */
    fun cancelRecognition() {
        cancelRecording()
    }

    /** Aborts an in-progress recording and returns to idle (cancel button / leaving the keyboard). */
    fun cancelRecording(keepBarForMs: Long = 0L) {
        // A cancelled recording disarms the send. Otherwise the arming survives, and the NEXT
        // dictation — an ordinary one, started with the mic key — would send itself. A stray send
        // is worse than a missed one: it puts words in front of somebody.
        sendAfterCommit = false

        maReleaseMic()
        pttStopPending = false
        // A discard in flight keeps its flag; any other teardown clears everything.
        setPushToTalk(
            phase = PushToTalkPhase.NONE,
            lockFlash = false,
            discarding = keepBarForMs > 0L,
        )
        _cancelSlideProgress.value = 0f
        _lockSlideProgress.value = 0f
        startJob?.cancel()
        startJob = null
        recorder?.cancel()
        recorder = null
        // Long-form segmented (#170): abort the background segment transcriptions; the realtime cleanup
        // below removes the progressively-shown preview text (segmented reuses realtimeShown/Context).
        if (segmentedActive) {
            segmentJobs.forEach { it.cancel() }
            segmentJobs.clear()
            segmentAudioFiles.values.forEach { runCatching { it.delete() } }
            resetSegmentedState()
        }
        // Tear down any realtime stream (#128) and remove the live provisional text from the field. Set the
        // cancelled flag first so any stream callback still queued on the main thread can't re-add the text.
        realtimeCancelled = true
        realtimeSession?.cancel()
        realtimeSession = null
        realtimeClosed = null
        _interimText.value = ""
        realtimeContext?.let { ctx -> runCatching { sink(ctx).clearDictationPreview(realtimeShown.toString()) } }
        realtimeShown.setLength(0)
        realtimeContext = null
        unregisterScreenOffReceiver()
        cleanupAudioRouting()
        livePromptArmed = false
        _livePromptActive.value = false
        _pendingPrompts.value = emptyList()
        // Cancelling a continued recording also throws away the carried-over interrupted segment.
        discardCarryOver()
        if (_state.value is UiState.Recording) {
            if (keepBarForMs > 0L) {
                // Capture has already stopped; only the bar stays, so the mic being thrown has a bin to
                // land in. Dropping straight to Idle made the target vanish mid-flight.
                discardingBar = true
                scope.launch {
                    delay(keepBarForMs)
                    discardingBar = false
                    setPushToTalk(discarding = false)
                    if (_state.value is UiState.Recording) _state.value = UiState.Idle
                }
            } else {
                discardingBar = false
                setPushToTalk(discarding = false)
                _state.value = UiState.Idle
            }
        }
    }

    /** Kept for the legacy in-keyboard panel; identical to [cancelRecording]. */
    fun abortRecording() = cancelRecording()

    /**
     * Starts listening for [Intent.ACTION_SCREEN_OFF] while a recording is in progress (issue #147). When
     * the screen turns off we treat it exactly like the keyboard being hidden ([stashRecordingOnHide]):
     * the audio is finalized and kept, and the mic is released. This is the dependable catch-all for
     * recordings that would otherwise be orphaned when no IME teardown callback is delivered (abrupt app
     * switch, IME switch, lock). It never fires while the screen is on, so long recordings are unaffected.
     */
    private fun registerScreenOffReceiver(appContext: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    stashRecordingOnHide(appContext)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        }.isSuccess
        if (registered) {
            screenOffReceiver = receiver
            screenOffContext = appContext
        }
    }

    /** Stops listening for screen-off. Called from every path that stops a recording. Idempotent. */
    private fun unregisterScreenOffReceiver() {
        val receiver = screenOffReceiver ?: return
        val ctx = screenOffContext
        screenOffReceiver = null
        screenOffContext = null
        if (ctx != null) runCatching { ctx.unregisterReceiver(receiver) }
    }

    /**
     * Aborts an in-flight transcription (stop button shown on the mic while transcribing). Cancels the
     * network coroutine, drops the audio (handled in the job's finally) and returns to idle. No-op
     * outside the transcribing state, so a tap can never interrupt a rewording request.
     */
    /** What is being transcribed right now, so the same audio can be sent again in another language. */
    private data class InFlight(
        val audioFile: File,
        val recordedSeconds: Long,
        val gate: Boolean,
        val forceLocal: Boolean,
        val isReplay: Boolean,
        val source: String,
        val replayHistoryId: Long?,
        /**
         * The language THIS REQUEST WAS SENT IN, captured when it left.
         *
         * The sending line used to show `maLanguageMode` — the current SETTING — so the moment the
         * setting changed for the next dictation, the line relabelled a request already on the wire.
         * He reported it as "sending English, status says Croatian", and it was: the request was
         * English and the label was reading a preference that had moved on.
         *
         * **A label on a thing in flight must come from the thing, not from the setting that
         * started it.**
         */
        val language: String,
    )

    private var inFlight: InFlight? = null

    /**
     * The language badge was tapped while a transcription was in flight: throw it away and send the
     * same audio again in the new language.
     *
     * ### Why cancelling is the only honest answer
     *
     * The language is settled before the upload — it is a field in the request and, on AUTO, the
     * result of a probe that has already run. There is no way to change it mid-flight, so a badge
     * that appeared to change the language of a request already on the wire would be lying about the
     * one thing he tapped it to control.
     *
     * ### Why he wants this at all
     *
     * He starts a Croatian dictation with the badge on ENG, sees it in the status line, and knows
     * before the result arrives that it will come back wrong. Until now the only cure was waiting
     * for the wrong answer and then re-transcribing from the history. **Seeing a mistake and being
     * unable to act on it is worse than not seeing it**, which is why the badge and this belong in
     * the same build: showing the language without letting him change it would have been half a
     * feature and the frustrating half.
     *
     * The audio is untouched by any of this — it is the same file, sent again — so nothing is
     * re-recorded and nothing is lost.
     */
    /**
     * The middle of the sending line was tapped: hold it, or send it again.
     *
     * ### Hold is a cancel that keeps the tape
     *
     * There is no pausing a request that is already on the wire — it is thrown away. What makes this
     * a hold rather than a cancel is what is NOT thrown away: the audio, and the fact that he means
     * to send it. **The distinction the user cares about is not whether the socket closed, it is
     * whether the recording is still his.**
     *
     * So the state stays `Transcribing(held = true)`, the line stays on screen saying so, and
     * nothing else in the app has to learn a new state to know the recorder is busy.
     *
     * ### Why he wants it
     *
     * Watching the spinner he realises the badge is wrong. Tapping the badge alone would resend
     * immediately in the next language, which is one language further on than the one he wants when
     * there are two to cycle through. Hold gives him the moment in between: stop, set the language,
     * send. Two taps around whatever he needs to do in the middle.
     */
    fun toggleTranscriptionHold(context: Context) {
        val current = inFlight ?: return
        val state = _state.value as? UiState.Transcribing ?: return
        if (state.held) {
            if (!current.audioFile.exists() || current.audioFile.length() == 0L) {
                // The audio went while it was held — a cache clear, or the system reclaiming space.
                // Better to end honestly than to send nothing and blame the provider.
                _state.value = UiState.Idle
                inFlight = null
                return
            }
            MaLog.add("stt", "hold released, sending as ${MaLanguage.active()}")
            _state.value = UiState.Idle
            transcribe(
                context,
                current.audioFile,
                current.recordedSeconds,
                // NO GATE on a release, exactly as the history replay does it.
                //
                // The gate decides whether the audio contains speech at all and trims the silence
                // out of it. This audio has already been through it once — that is what "held"
                // means. Running it again on the trimmed result is how a perfectly good recording
                // gets judged silent and thrown away with an error, which from where he is standing
                // is a tap that cancelled instead of sending.
                gate = false,
                forceLocal = current.forceLocal,
                isReplay = current.isReplay,
                source = current.source,
                replayHistoryId = current.replayHistoryId,
            )
        } else {
            // The audio is COPIED before the request is dropped.
            //
            // The gate trims silence out of the recording in place while the first send is being
            // prepared, so the file `inFlight` names can be rewritten or replaced under it. Holding
            // and then sending "the same file" was therefore sending whatever the trimmer had left,
            // which is how a tap that should have resent looked like a cancel.
            //
            // The history replay has done exactly this since it was written — copy to a stable file
            // first — and this is the same problem wearing a different hat.
            val kept = File(context.cacheDir, "dictate_held_send.wav")
            val ok = runCatching { current.audioFile.copyTo(kept, overwrite = true) }.isSuccess
            if (ok) inFlight = current.copy(audioFile = kept)
            MaLog.add("stt", "send held, audio kept" + if (ok) "" else " (copy failed, using original)")
            transcribeJob?.cancel()
            transcribeJob = null
            _state.value = UiState.Transcribing(held = true)
        }
    }

    fun retranscribeInLanguage(context: Context, language: String) {
        val current = inFlight ?: return
        val state = _state.value as? UiState.Transcribing ?: return
        if (!current.audioFile.exists() || current.audioFile.length() == 0L) return
        MaLanguage.set(context, language)
        // Held: set the language and stay held. He stopped it in order to choose, and sending the
        // moment he chooses would take the decision away at the exact moment he was making it.
        if (state.held) {
            MaLog.add("stt", "language set to $language while held")
            return
        }
        MaLog.add("stt", "language changed mid-flight, sending again as $language")
        // Cancelled rather than left to land: the first answer would arrive in the old language and
        // overwrite the one he asked for, and which of the two won would depend on the network.
        transcribeJob?.cancel()
        transcribeJob = null
        _state.value = UiState.Idle
        transcribe(
            context,
            current.audioFile,
            current.recordedSeconds,
            gate = current.gate,
            forceLocal = current.forceLocal,
            isReplay = current.isReplay,
            source = current.source,
            replayHistoryId = current.replayHistoryId,
        )
    }

    /**
     * THE LANGUAGE THIS REQUEST IS USING, and now genuinely Compose state.
     *
     * The comment here read "State, not a plain read, because the line showing it is a
     * composable" — and the code beneath it was a plain `get()` over an ordinary field. Compose
     * could not see it change, so **the line showed whatever had been true at the last
     * recomposition**: usually the language of the previous request. That is exactly what he
     * reported — sending English, status line saying Croatian.
     *
     * A comment describing the fix rather than the code is worse than no comment, because it
     * stops the next reader looking.
     */
    var inFlightLanguage: String by mutableStateOf(MaLanguage.active())
        private set

    fun cancelTranscription() {
        // Same reason as cancelRecording: an abandoned transcription must not leave a send armed
        // for whatever is dictated next.
        sendAfterCommit = false

        if (_state.value !is UiState.Transcribing) return
        transcribeJob?.cancel()
        transcribeJob = null
        _pendingPrompts.value = emptyList()
        _state.value = UiState.Idle
    }

    /**
     * Aborts an in-flight rewording (the stop button shown on the mic while rewording, issue #192).
     * Covers both a manually applied prompt ([rewordJob]) and the post-transcription / live-prompt
     * rewording chain that runs inside [transcribeJob]. Cancelling before the model answer is committed
     * leaves the field untouched — for a "reword the selection" prompt the original text stays selected
     * and intact. No-op outside the rewording state.
     */
    fun cancelRewording() {
        if (_state.value !is UiState.Rewording) return
        rewordJob?.cancel()
        rewordJob = null
        transcribeJob?.cancel()
        transcribeJob = null
        _pendingPrompts.value = emptyList()
        _state.value = UiState.Idle
    }

    /**
     * Starts a recording. [seedAccumulatedMs] pre-fills the elapsed timer (and the credited length) with
     * already-captured audio when continuing an interrupted recording, so the bar shows the running
     * total; it is 0 for a normal recording.
     */
    private fun startRecording(context: Context, seedAccumulatedMs: Long = 0L) {
        if (_state.value is UiState.Recording) return

        // Refuse before the microphone opens rather than after the upload fails.
        //
        // Without this he speaks a whole sentence, waits for it to go up, and only then learns there
        // was no key — with the recording spent and a re-transcribe needed to recover it. The check
        // costs one read of the stored account and turns the worst kind of failure, where he did
        // everything right and lost the work anyway, into a message before any work is done.
        //
        // The error carries OPEN_SETTINGS, which already exists for exactly this and lands on the
        // provider screen rather than the top of the settings.
        // No key AND no local model. Both are checked because forceLocal is decided when he stops
        // rather than when he starts — holding send picks the on-device engine — so refusing on a
        // missing key alone would block a recording he intended to transcribe offline.
        val noKey = MaProviders.transcriptionAccount().apiKey.isBlank()
        val noLocalModel = LocalModelManager.installedIds(context.applicationContext).isEmpty()
        if (noKey && noLocalModel) {
            _state.value = UiState.Error(
                message = context.getString(R.string.dictate__error_no_key_before_recording),
                action = ErrorAction.OPEN_SETTINGS,
            )
            return
        }
        // Starting a fresh recording supersedes any kept audio (a failed retry or an interrupted
        // recording the user chose not to send), so drop it instead of leaving a stale offer behind.
        // A continuation keeps its carry-over (seeded above), so only drop it for a normal start.
        if (seedAccumulatedMs == 0L) {
            discardRetainedAudio()
            discardCarryOver()
        }
        val appContext = context.applicationContext
        ensureHapticObserver(appContext)
        startJob = scope.launch {
            try {
                // Correct any stale active language (e.g. leftover "detect" after auto-detect was
                // disabled) before the realtime session / request reads it.
                reconcileActiveLanguage()
                // Ask everything else to stop before the microphone opens, not after.
                requestExclusiveAudio(appContext)
                val audioSource = setupBluetoothIfEnabled(appContext)
                // Long-form segmented dictation (#170): transcribe cut segments in the background while
                // recording continues. Off for realtime / live-prompt / overlay / multimodal (see the gate).
                val segmented = isSegmentedMode(appContext)
                // Auto-split: Silero VAD finds candidate pauses, then Smart Turn v3 decides whether the
                // thought is complete; the configured pause remains the Pipecat-style safety fallback.
                segmentVad?.release()
                segmentVad = if (segmented && prefs.dictate.longformMode.get() == DictateLongformMode.AUTO) {
                    LiveSpeechSplitter(
                        appContext,
                        prefs.dictate.longformAutoSplitSeconds.get() * 1000,
                        useSmartTurn = prefs.dictate.smartTurnEnabled.get() &&
                            SmartTurnModel.isModelAvailable(appContext),
                    ) { flushSegment(appContext, splitterAlreadyReset = true) }.also { it.start() }
                } else null
                // The mic PCM tap: the realtime session (batch mode), the VAD splitter (auto-split), or none.
                val pcmSink: ((ByteArray, Int) -> Unit)? = when {
                    // Off the main thread: opening the stream builds an HTTP client and a WebSocket, and
                    // doing that inline stalled the UI thread long enough for Android to cancel the
                    // in-flight touch — which killed push-to-talk ~90 ms into a hold (#235).
                    !segmented -> withContext(Dispatchers.IO) { openRealtimeSession(appContext) }
                    segmentVad != null -> { val v = segmentVad!!; { pcm, len -> v.feed(pcm, len) } }
                    else -> null
                }
                recorder = RecordingController(appContext).also { it.start(audioSource, pcmSink) }
                // Take the microphone foreground before anything else can take the screen. From here
                // the recording belongs to a service rather than to the keyboard window, so switching
                // apps, locking, or anything else coming to the front no longer ends it.
                maAppContext = appContext
                MaRecordingService.start(appContext)
                if (prefs.dictate.skipSilentRecordings.get()) {
                    // Hide the one-time native VAD/session setup behind the user's recording time.
                    scope.launch { SpeechGate.prewarm(appContext) }
                }
                _state.value = UiState.Recording(SystemClock.elapsedRealtime(), accumulatedMs = seedAccumulatedMs)
                startAudioLevelSampling()
                // Highlight the live-prompt chip for the duration of a live-prompt recording.
                _livePromptActive.value = livePromptArmed
                if (segmented) initSegmented(appContext)
                registerScreenOffReceiver(appContext)
                // Push-to-talk (#235): the finger came back up while this job was still acquiring audio
                // focus / Bluetooth SCO. Now that a recorder exists, honour that release.
                if (pttStopPending) {
                    pttStopPending = false
                    stopAndTranscribe(appContext)
                }
            } catch (t: Throwable) {
                recorder = null
                segmentVad?.release()
                segmentVad = null
                _livePromptActive.value = false
                cleanupAudioRouting()
                _state.value = UiState.Error(
                    // Most common cause is the missing RECORD_AUDIO permission (granted in onboarding).
                    appContext.getString(R.string.dictate__error_recording_failed, t.message ?: ""),
                )
            }
        }
    }

    /** Samples the recorder once for all UI consumers; the job ends itself with the recording state. */
    private fun startAudioLevelSampling() {
        audioLevelJob?.cancel()
        val smoother = AudioLevelSmoother()
        audioLevelJob = scope.launch {
            while (_state.value is UiState.Recording) {
                val recording = _state.value as UiState.Recording
                _audioLevel.value = if (recording.paused) {
                    smoother.reset()
                } else {
                    smoother.update(recorder?.maxAmplitude() ?: 0)
                }
                delay(AUDIO_LEVEL_SAMPLE_MS)
            }
            _audioLevel.value = smoother.reset()
        }
    }

    /**
     * Long-press "send with the local model" (#228): stop the current plain recording and transcribe it
     * on-device instead of the configured cloud provider. No-op unless a plain recording is active — in
     * long-form / realtime there is no plain send button to hold, so the shortcut doesn't apply.
     */
    fun stopAndTranscribeLocal(context: Context) {
        maReleaseMic()
        if (!canLongPressSendLocal()) return
        stopAndTranscribe(context, forceLocal = true)
    }

    /**
     * True while a plain (non-segmented, non-realtime) recording is in progress — the state where the mic
     * doubles as a "send" button, so its long-press can force a local-model transcription (#228). If no
     * on-device model is downloaded yet, the shortcut still fires and [transcribe] surfaces the "model not
     * installed → open settings" feedback (it never crashes), which is friendlier than silently ignoring.
     */
    fun canLongPressSendLocal(): Boolean =
        _state.value is UiState.Recording && !segmentedActive && realtimeSession == null

    private fun stopAndTranscribe(context: Context, forceLocal: Boolean = false) {
        maReleaseMic()
        setPushToTalk(phase = PushToTalkPhase.NONE)
        // Long-form segmented (#170): finish the segment queue instead of uploading one big file.
        if (segmentedActive) {
            stopSegmentedAndFinalize(context)
            return
        }
        // Real-time recording (#128): finalize the stream instead of uploading the whole file.
        if (realtimeSession != null) {
            stopRealtimeAndFinalize(context)
            return
        }
        val latencyTrace = BatchLatencyTrace()
        logLatency(latencyTrace, "stopTapped")
        val activeRecorder = recorder
        recorder = null
        _livePromptActive.value = false
        unregisterScreenOffReceiver()
        // Capture the recorded length before leaving the Recording state, to credit the usage counter
        // that gates the rate/donate nudges (roadmap 9.7/9.8). Includes any carried-over seconds.
        val recordedSeconds = recordedSecondsOf(_state.value)
        val recorderStopStartedNanos = SystemClock.elapsedRealtimeNanos()
        val audioFile = activeRecorder?.stop()
        logLatency(latencyTrace, "recorderStopped", recorderStopStartedNanos)
        val routingCleanupStartedNanos = SystemClock.elapsedRealtimeNanos()
        cleanupAudioRouting()
        logLatency(latencyTrace, "audioRoutingCleaned", routingCleanupStartedNanos)
        val carry = carryOverAudio
        carryOverAudio = null
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            // The new segment is unusable. If we were continuing an interrupted recording, fall back to
            // transcribing the carried-over segment alone rather than losing it.
            if (carry != null && carry.exists() && carry.length() > 0L) {
                scope.launch { clearInterruptedAudioPref() }
                transcribe(context, carry, carryOverSeconds, forceLocal = forceLocal, latencyTrace = latencyTrace)
            } else {
                carry?.delete()
                _state.value = UiState.Error(context.getString(R.string.dictate__error_no_audio))
            }
            return
        }
        if (carry == null) {
            transcribe(context, audioFile, recordedSeconds, forceLocal = forceLocal, latencyTrace = latencyTrace)
            return
        }
        // Continuation: stitch the carried-over segment and the new one into a single audio so the whole
        // dictation is transcribed as one. The interrupted marker was already claimed when continuing.
        scope.launch { clearInterruptedAudioPref() }
        val merged = File(context.applicationContext.cacheDir, MERGED_AUDIO_NAME)
        val ok = AudioConcat.concat(listOf(carry, audioFile), merged)
        carry.delete()
        if (ok && merged.exists() && merged.length() > 0L) {
            audioFile.delete()
            transcribe(context, merged, recordedSeconds, forceLocal = forceLocal, latencyTrace = latencyTrace)
        } else {
            // Merge failed (rare): transcribe at least the newly recorded segment.
            merged.delete()
            transcribe(context, audioFile, recordedSeconds, forceLocal = forceLocal, latencyTrace = latencyTrace)
        }
    }

    /** Elapsed recorded seconds of a [UiState.Recording] (running + accumulated), else 0. */
    private fun recordedSecondsOf(state: UiState): Long {
        val rec = state as? UiState.Recording ?: return 0L
        val running = if (rec.paused) 0L else SystemClock.elapsedRealtime() - rec.startedAtMs
        return ((rec.accumulatedMs + running) / 1000L).coerceAtLeast(0L)
    }

    /**
     * Long-press entry point for the mic: hands off to [FileTranscriptionActivity] so the user can
     * pick an existing audio/video file to transcribe instead of recording. The activity stashes the
     * picked file and a pref; [consumePendingFileTranscription] finishes the job once the keyboard
     * regains focus. No-op unless we are idle.
     */
    fun startFileTranscription(context: Context) {
        if (_state.value !is UiState.Idle) return
        val intent = Intent(context, FileTranscriptionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Cache directory where [FileTranscriptionActivity] drops a picked file for the IME to pick up.
     * A dedicated directory keeps the handoff file-based (survives the IME process being killed while
     * the file picker is foreground) and unambiguous.
     */
    fun pendingTranscriptionDir(context: Context): File = File(context.cacheDir, "dictate_pending")

    /**
     * Called by the IME when the keyboard (re-)appears on a field: if [FileTranscriptionActivity]
     * stashed a picked file, transcribe it now and commit into the focused field. Returns true if a
     * transcription was started, so the caller can skip instant-recording.
     *
     * Safe to call from multiple lifecycle hooks: the pending file is *claimed* (moved out of the
     * pending dir) before transcription starts, so a second call finds nothing and is a no-op.
     */
    fun consumePendingFileTranscription(context: Context): Boolean {
        if (_state.value !is UiState.Idle) return false
        val pending = pendingTranscriptionDir(context).listFiles()?.firstOrNull { it.isFile && it.length() > 0L }
            ?: return false
        // Claim it: move out of the pending dir so it cannot be picked up twice, then clean the dir.
        val claimed = File(context.cacheDir, "dictate_import_${pending.name}")
        claimed.delete()
        if (!pending.renameTo(claimed)) {
            pending.copyTo(claimed, overwrite = true)
            pending.delete()
        }
        pendingTranscriptionDir(context).deleteRecursively()
        if (!claimed.exists() || claimed.length() == 0L) return false
        // A deliberately picked file is transcribed as-is (no silence gate — see issue #93).
        transcribe(context, claimed, gate = false, source = DictateHistorySource.IMPORT)
        return true
    }

    /**
     * Shared transcription path for both recorded and picked audio: resolves provider/key/model,
     * uploads [audioFile], commits the result and deletes the file afterwards.
     */
    private fun transcribe(
        context: Context,
        audioFile: File,
        recordedSeconds: Long = 0L,
        gate: Boolean = true,
        // Long-press "send with local model" (#228): force this one transcription onto the on-device
        // provider regardless of the configured active provider.
        forceLocal: Boolean = false,
        // History (issue #140): [isReplay] re-transcribes already-counted audio (skip stats),
        // [replayHistoryId] updates that stored entry's text in place, [source] tags the origin.
        isReplay: Boolean = false,
        source: String = DictateHistorySource.KEYBOARD,
        replayHistoryId: Long? = null,
        latencyTrace: BatchLatencyTrace = BatchLatencyTrace(),
    ) {
        logLatency(latencyTrace, "transcribeEntered")
        // Everything needed to send this exact audio again, kept for as long as it is in flight.
        //
        // He can change the language badge while the spinner is running, and the only honest answer
        // to that is to abandon the request and make it again — the language is decided before the
        // upload and there is no way to change it mid-flight. So the file and its metadata are held
        // here rather than being reconstructed later from the history, which would only have them
        // after the request finished, which is exactly when they are no longer wanted.
        inFlight = InFlight(
            audioFile, recordedSeconds, gate, forceLocal, isReplay, source, replayHistoryId,
            language = MaLanguage.active(),
        )
        // Written where the request BEGINS, so a language changed afterwards cannot relabel a send
        // already on the wire.
        inFlightLanguage = MaLanguage.active()
        val account = if (forceLocal) MaProviders.localTranscriptionAccount() else MaProviders.transcriptionAccount()
        val apiKey = account.apiKey
        val preset = MaProviders.presetFor(account)
        val appContext = context.applicationContext
        val model = MaProviders.transcriptionModelFor(appContext, account, preset, "gpt-4o-mini-transcribe")
        // History metadata (issue #140), resolved once so the success (capture) and EVERY failure path —
        // including the early returns below (no key / model not downloaded) — log the same info.
        val historyProviderName = account.displayName.ifBlank { preset.displayName }
        // The language, settled here and once, before anything is sent.
        //
        // On AUTO the first 30 seconds go to Groq's Whisper, which answers in about a second and
        // reports what it heard. That answer is clamped to his two languages by the only question
        // worth asking — is this English — because Croatian is routinely heard as Slovenian,
        // Serbian, Czech or Polish while English is almost never mistaken for anything.
        //
        // Everything downstream reads this one value: the request's language hint, the sync-or-async
        // decision, and the history row. A probe that failed, timed out, or found no Groq key leaves
        // the language exactly as it was set by hand, so AUTO can only ever improve on the manual
        // setting and never break it.
        //
        // `transcribe` runs on the Main dispatcher, so the probe is pushed to IO and waited for:
        // a network call left on Main would freeze the keyboard for its whole duration, and on a
        // modern Android it does not freeze at all, it throws NetworkOnMainThreadException. The
        // wait is deliberate — the answer is needed before the request below is built — and it is
        // bounded by the probe's own 12 second timeout.
        // No detection. The language is whatever the badge says, and the badge is his.
        //
        // Groq's probe is gone: it was a second network call before every send, it was wrong often
        // enough to matter, and a wrong language does not fail visibly — it returns fluent nonsense
        // in the other language. Manual is slower by one tap and right every time.
        // Always a real code: there is no detect case left, so history never records a blank language.
        val historyLanguage = MaLanguage.active()
        val historySource = if (outputTarget == OutputTarget.OVERLAY) DictateHistorySource.OVERLAY else source
        // Logs the failed dictation with its audio (safety net, issue #140) unless this is a replay, then
        // drops the cache file. Async so the early-return callers don't block.
        fun logFailureAndDrop() {
            if (replayHistoryId != null) {
                audioFile.delete()
                return
            }
            scope.launch {
                recordFailedHistory(appContext, audioFile, account.providerId, historyProviderName, model, historyLanguage, recordedSeconds, historySource)
                if (audioFile.exists()) audioFile.delete()
            }
        }

        if (apiKey.isBlank() && MaProviders.requiresKey(account)) {
            _state.value = UiState.Error(
                message = context.getString(R.string.dictate__error_no_api_key),
                kind = DictateApiException.Kind.INVALID_API_KEY,
                action = ErrorAction.OPEN_SETTINGS,
            )
            logFailureAndDrop()
            return
        }

        // On-device (#104): guide the user to download a model instead of failing mid-transcription.
        if (preset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE &&
            !LocalModelManager.isInstalled(appContext, model)
        ) {
            _state.value = UiState.Error(
                message = context.getString(R.string.dictate__local_model_not_installed_error),
                kind = DictateApiException.Kind.UNKNOWN,
                action = ErrorAction.OPEN_SETTINGS,
            )
            logFailureAndDrop()
            return
        }

        ensureHapticObserver(appContext)
        _state.value = UiState.Transcribing()
        // Live prompt is consumed by this transcription only (the next recording is normal again).
        val live = livePromptArmed
        livePromptArmed = false
        val coroutineScheduledNanos = SystemClock.elapsedRealtimeNanos()
        transcribeJob = scope.launch {
            var keepAudio = false
            var outcome = "failed"
            // The file actually uploaded. Normally the original recording; the silence trimmer (#232) may
            // swap in a shorter copy, while history/retention/cleanup keep referencing the original audioFile.
            var uploadFile = audioFile
            try {
                logLatency(latencyTrace, "coroutineStarted", coroutineScheduledNanos)
                reconcileActiveLanguage() // correct a stale active language before it's read for the request
                // Local Silero VAD pass before spending an upload. Two purposes, both skipped for picked
                // files / resends (gate=false) and while long-form dictation runs its own segment-cutting:
                //   • Silence gate (#93): a recording with no speech is dropped so silent clips can't produce
                //     "ghost text" hallucinations or waste API credits.
                //   • Silence trimming (#232): long internal pauses are cut out so a gappy dictation uploads
                //     less audio (less cost/latency) without losing a word.
                // Both fail open (treated as speech, left untrimmed) if the check can't run.
                val skipSilent = prefs.dictate.skipSilentRecordings.get()
                val trimGaps = prefs.dictate.trimSilentGaps.get()
                val longform = prefs.dictate.longformMode.get().isEnabled
                if (gate && !longform && (skipSilent || trimGaps)) {
                    val gateStartedNanos = SystemClock.elapsedRealtimeNanos()
                    if (trimGaps) {
                        // One VAD pass yields both the speech decision and the segment map for trimming.
                        val analysis = SpeechGate.analyze(appContext, audioFile)
                        logLatency(latencyTrace, "speechGateCompleted", gateStartedNanos)
                        if (skipSilent && analysis != null && !analysis.hasSpeech) {
                            outcome = "noSpeech"
                            _state.value = UiState.Error(
                                message = appContext.getString(R.string.dictate__no_speech_detected),
                                action = ErrorAction.NONE,
                                neutral = true, // informational, not a failure → white/themed, not red
                            )
                            return@launch // audio is dropped by the finally block
                        }
                        if (analysis != null && analysis.hasSpeech) {
                            val before = audioFile.length()
                            SpeechGate.writeTrimmedWav(
                                analysis,
                                File(appContext.cacheDir, TRIMMED_AUDIO_NAME),
                                TRIM_MAX_SILENCE_MS,
                                TRIM_KEEP_SILENCE_MS,
                            )?.let {
                                uploadFile = it
                                // WHAT THE TRIM SAVED, IN THE LOG, EVERY TIME.
                                //
                                // He is watching the bill. **A saving nobody can see is a saving
                                // nobody can trust** — and if these thresholds are ever wrong, this
                                // line is how it gets noticed, either because the figure is
                                // suspiciously large or because it is stuck at zero.
                                //
                                // Bytes rather than seconds because both files are the same format
                                // and the ratio is what matters. Guarded against a zero length: a
                                // division by zero in a log line is a crash in the send path.
                                val after = it.length()
                                if (before > 0L) {
                                    val cut = (100 - (after * 100 / before)).toInt().coerceIn(0, 100)
                                    MaLog.add("stt", "trimmed $cut% of the audio before upload")
                                    // And where he can see it without opening the log: under the
                                    // switch that did it.
                                    scope.launch { prefs.dictate.trimLastSavedPercent.set(cut) }
                                }
                            }
                        }
                    } else {
                        // Gate only: the cheaper early-exit check (returns as soon as the first speech closes).
                        val hasSpeech = SpeechGate.hasSpeech(appContext, audioFile)
                        logLatency(latencyTrace, "speechGateCompleted", gateStartedNanos)
                        if (!hasSpeech) {
                            outcome = "noSpeech"
                            _state.value = UiState.Error(
                                message = appContext.getString(R.string.dictate__no_speech_detected),
                                action = ErrorAction.NONE,
                                neutral = true, // informational, not a failure → white/themed, not red
                            )
                            return@launch // audio is dropped by the finally block
                        }
                    }
                }
                // Single-call multimodal (issue #130): one chat/completions+input_audio request transcribes
                // and formats together (cloud chat models only, never the on-device engine).
                val chatAudio = account.transcriptionViaChat &&
                    preset.transcriptionApi != TranscriptionApi.LOCAL_ONDEVICE
                // One pipeline, three steps, each deleting what it replaces:
                //
                //   1. the recording, captured at whatever rate this phone offers, typically 48 kHz
                //   2. a 16 kHz mono WAV, low-pass filtered first so nothing folds back into the
                //      speech band. Recognisers want 16 kHz and something has to do the reduction;
                //      doing it here with a real filter is better than letting the microphone
                //      driver decimate on the way in
                //   3. AAC, which is what is sent and what the archive keeps
                //
                // The intermediate files go as soon as they are consumed, so the two large ones
                // never sit on disk together and a long dictation costs one small file at rest.
                //
                // The on-device engine is skipped: it is handed the audio locally and decodes it
                // itself, so encoding would only give it something to undo.
                var maEncoded = false
                // Fast or slow, decided here rather than earlier, because here the resample has already
                // happened and the length and the size are facts about a file instead of intentions.
                var maFast = false
                if (preset.transcriptionApi != TranscriptionApi.LOCAL_ONDEVICE) {
                    val resampled = MaResample.toTargetRate(uploadFile)
                    if (resampled != null) {
                        if (uploadFile !== audioFile) runCatching { uploadFile.delete() }
                        uploadFile = resampled
                    }
                    maFast = MaProviders.maUseSyncPath(preset, chatAudio, uploadFile)
                    // The AAC encode is for the async path only: Sync accepts WAV and PCM and nothing
                    // else, so encoding first would guarantee a rejection.
                    if (!maFast) {
                        MaEncoder.encode(uploadFile)?.let { aac ->
                            if (uploadFile !== audioFile) runCatching { uploadFile.delete() }
                            uploadFile = aac
                            maEncoded = true
                        }
                    }
                }
                var maFormatTag = if (maEncoded) MaEncoder.TAG else "wav"
                // Timed across encoding, upload, the service's queue and the answer, because that
                // whole span is what "slow" means to someone waiting for their words to appear.
                val maSendStartedAt = System.currentTimeMillis()
                val request = TranscriptionRequest(
                    audioFile = uploadFile,
                    model = model,
                    // His switch: corrected text, or exactly what he said.
                    preferCorrected = prefs.dictate.maCorrectedDictation.get(),
                    // Always an explicit language, never null. Null told the provider to detect, and
                    // detection is what this app stopped doing: the HR/ENG key says which language it
                    // is, so there is nothing left to guess. MaLanguage.active() answers hr or en and
                    // collapses anything older to Croatian, so an install still holding "detect"
                    // sends hr rather than falling back to the guess.
                    //
                    // Chat-audio is the exception and always was: there the language goes into the
                    // written instruction as a readable name instead of as a field.
                    language = if (chatAudio) null else MaLanguage.active(),
                    // Empty, not a shortlist. Candidates only mean anything to a provider that is
                    // detecting, and this one is being told.
                    languageCandidates = emptyList(),
                    // Non-chat: style/punctuation prompt biases recognition (roadmap 2.4 / 4.11).
                    // Chat-audio: the full instruction (language + style + all auto-formatting) in one go.
                    prompt = if (chatAudio) buildChatAudioInstruction(appContext) else transcriptionStylePrompt(),
                )
                val providerStartedNanos = SystemClock.elapsedRealtimeNanos()
                val result = if (preset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) {
                    // On-device (issue #104): no HTTP client, no key; transcribe locally via sherpa-onnx.
                    // Tell the recognizer cache how long it may stay resident once idle (RAM unload).
                    LocalTranscriptionProvider.setIdleUnloadMillis(
                        prefs.dictate.localModelUnloadMinutes.get() * 60_000L,
                    )
                    LocalTranscriptionProvider(LocalTranscriptionProvider.modelDir(appContext, model))
                        .transcribe(request)
                } else {
                    // One send, against whichever preset and model the path calls for. Written once and
                    // called twice, because the fast path may hand the same audio to the slow one.
                    suspend fun maSend(
                        sendPreset: ProviderPreset,
                        sendModel: String,
                        sendFile: File,
                        fast: Boolean,
                    ): TranscriptionResult {
                        // MA TWIST: the key field may hold several keys, one per line. A rejected or
                        // exhausted key rolls to the next one; anything else fails straight away.
                        // In RING ORDER: the key that worked last time first, the ones the service
                        // has refused skipped entirely. A keyring whose first few keys are dead used
                        // to pay those failed round trips before every single recording.
                        val maKeys = MaKeyRingStore.keys(account.providerId, apiKey)
                        // How long was spoken, and how much is going up. The raw WAV size is gone:
                        // it was there to prove compression was happening, which is settled now, and
                        // it crowded out the one number that means something while waiting, which is
                        // how much of the recording there is.
                        val maSentKb = sendFile.length() / 1024L
                        val maLen = "%d:%02d".format(recordedSeconds / 60, recordedSeconds % 60)
                        // The path is in the line because it is the thing being judged: fast is three
                        // times the price and only worth it if the number beside it is smaller.
                        val maPath = if (fast) "fast" else "slow"
                        val maSize = "$maLen, $maSentKb kB $maFormatTag $maPath"
                        // "K1_6" rather than "key 1 of 6". The status line is one narrow strip above a
                        // keyboard and every word spent on wording is a word not spent on the numbers.
                        _maStatus.value = if (maKeys.size > 1) {
                            "sending $maSize, K1_${maKeys.size}"
                        } else {
                            "sending $maSize"
                        }
                        val ring = MaKeyRingStore.load(account.providerId)
                        var rolled = 0
                        val outcome = try {
                            MaKeyRing.run(maKeys, ring) { maKey ->
                                rolled++
                                if (rolled > 1) {
                                    _maStatus.value = "K${rolled - 1}_${maKeys.size} failed, K$rolled"
                                }
                                OpenAiCompatibleClient.from(
                                    sendPreset, maKey,
                                    // Sync has its own host and is never user-editable, so an override
                                    // belonging to the account would point it at the wrong one.
                                    baseUrlOverride = if (fast) null else MaProviders.baseUrlOverrideFor(account),
                                    proxy = prefs.dictate.dictateProxyConfig(),
                                    // Single-call multimodal (issue #130): route audio through chat/completions.
                                    useChatAudio = chatAudio,
                                    trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                                ).transcribe(
                                    request.copy(audioFile = sendFile, model = sendModel),
                                    onRetry = { attempt ->
                                        // Named as waiting rather than as retrying, because that is what
                                        // is actually happening for the next several seconds and a line
                                        // that says "retrying" while nothing moves reads as a hang.
                                        _maStatus.value = "no answer, waiting to retry, attempt $attempt"
                                        _state.value = UiState.Transcribing(attempt)
                                    },
                                )
                            }
                        } catch (e: MaKeyRing.NoKeyLeft) {
                            // Every key was tried. Persist what the walk learned before failing, or
                            // the very next recording pays to discover the same thing again.
                            MaKeyRingStore.save(account.providerId, e.ring)
                            throw e.last
                                ?: DictateApiException(DictateApiException.Kind.INVALID_API_KEY, "No API key set")
                        }
                        MaKeyRingStore.save(account.providerId, outcome.second)
                        return outcome.first
                    }

                    try {
                        try {
                            if (maFast) {
                                // English under two minutes goes to the Dictation endpoint, which
                                // returns cleaned text in the same request. Everything else that
                                // got this far goes to Sync. Over two minutes never gets here.
                                if (MaProviders.maUseDictationPath(MaLanguage.active())) {
                                    maSend(ProviderRegistry.ASSEMBLYAI_DICTATION, "", uploadFile, true)
                                } else {
                                    maSend(ProviderRegistry.ASSEMBLYAI_SYNC, OpenAiCompatibleClient.SYNC_MODEL, uploadFile, true)
                                }
                            } else {
                                maSend(preset, model, uploadFile, false)
                            }
                        } catch (e: DictateApiException) {
                            // The fast path did not answer. The audio is still here and the slow path is
                            // the same provider and the same key, so hand it over rather than hand back
                            // an error: a dictation that arrives late is worth incomparably more than one
                            // that has to be spoken again. Said out loud in the status line rather than
                            // done silently, because otherwise "fast" would look simply slow.
                            if (!maFast) throw e
                            maFast = false
                            _maStatus.value = "fast did not answer, sending slow"
                            _state.value = UiState.Transcribing()
                            MaEncoder.encode(uploadFile)?.let { aac ->
                                if (uploadFile !== audioFile) runCatching { uploadFile.delete() }
                                uploadFile = aac
                                maEncoded = true
                                maFormatTag = MaEncoder.TAG
                            }
                            maSend(preset, model, uploadFile, false)
                        }
                    } catch (e: DictateApiException) {
                        // Offline fallback (#104): the cloud call failed because we're offline (after its
                        // retries) — transcribe on-device with the downloaded model instead of erroring.
                        val fallback = MaProviders.localFallbackProvider(appContext, preset, e) ?: throw e
                        _state.value = UiState.Transcribing()
                        LocalTranscriptionProvider.setIdleUnloadMillis(
                            prefs.dictate.localModelUnloadMinutes.get() * 60_000L,
                        )
                        // The current file, not the one the request was built with: a fast path that
                        // handed over to the slow one re-encoded and deleted its WAV on the way.
                        fallback.transcribe(request.copy(audioFile = uploadFile))
                    }
                }
                logLatency(latencyTrace, "providerCompleted", providerStartedNanos)
                // The number the whole format experiment turns on: how long the words took to come
                // back, measured across encoding, upload, queue and answer. Stored rather than only
                // shown, so a comparison run over an evening survives being forgotten.
                val maSendMs = System.currentTimeMillis() - maSendStartedAt
                prefs.dictate.maLastSendMs.set(maSendMs)
                prefs.dictate.maLastSendFormat.set(maFormatTag)
                _maStatus.value = "%.1fs \u00b7 %s".format(maSendMs / 1000.0, maFormatTag.uppercase())
                // Prompt-echo guard (issue #77): on silent/unclear audio, Whisper-style models echo the
                // transcription style prompt back verbatim (the old default was infamously returned as
                // "This sentence has capitalization and punctuation."). If the result is just that prompt
                // echoed, treat it as no speech and drop it instead of dumping the prompt into the field.
                // Skipped for the chat-audio path, whose prompt is an instruction, not a Whisper style hint.
                if (!chatAudio && DictatePromptDefaults.looksLikeStylePromptEcho(result.text, transcriptionStyleBasePrompt())) {
                    outcome = "promptEcho"
                    _state.value = UiState.Error(
                        message = appContext.getString(R.string.dictate__no_speech_detected),
                        action = ErrorAction.NONE,
                        neutral = true,
                    )
                    return@launch // audio is dropped by the finally block
                }
                // Shared finalize: rewording/formatting + mappings + commit + stats. Reused by the
                // realtime path (issue #128), which supplies its own already-streamed transcript.
                val capture = HistoryCapture(
                    audioFile = audioFile,
                    providerId = account.providerId,
                    providerName = historyProviderName,
                    model = model,
                    language = historyLanguage,
                    source = historySource,
                    isReplay = isReplay,
                    replayHistoryId = replayHistoryId,
                )
                val finalizeStartedNanos = SystemClock.elapsedRealtimeNanos()
                finalizeAndCommit(
                    appContext,
                    result.text,
                    recordedSeconds,
                    live,
                    alreadyFormatted = chatAudio,
                    capture = capture,
                    latencyTrace = latencyTrace,
                )
                logLatency(latencyTrace, "finalizeCompleted", finalizeStartedNanos)
                outcome = "success"
            } catch (c: CancellationException) {
                // User aborted via the stop button: discard quietly (state set by cancelTranscription),
                // never show an error. The audio is dropped in the finally block.
                outcome = "cancelled"
                throw c
            } catch (e: DictateApiException) {
                outcome = "apiError"
                _pendingPrompts.value = emptyList()
                // Exportable failures (too large / bad format) keep the audio regardless of the resend
                // pref, so it can be saved instead of lost (issue #144).
                keepAudio = retainFailedAudio(audioFile, live, recordedSeconds, force = e.kind in EXPORTABLE_ERROR_KINDS)
                // Safety net (issue #140): log the failed dictation with its audio so it can be recovered
                // later; not for replays (the entry already exists).
                if (replayHistoryId == null) {
                    recordFailedHistory(appContext, audioFile, account.providerId, historyProviderName, model, historyLanguage, recordedSeconds, historySource)
                }
                _state.value = apiError(e, appContext, canResend = keepAudio)
            } catch (t: Throwable) {
                outcome = "unexpectedError"
                _pendingPrompts.value = emptyList()
                keepAudio = retainFailedAudio(audioFile, live, recordedSeconds)
                if (replayHistoryId == null) {
                    recordFailedHistory(appContext, audioFile, account.providerId, historyProviderName, model, historyLanguage, recordedSeconds, historySource)
                }
                _state.value = UiState.Error(
                    message = appContext.getString(R.string.dictate__error_unknown),
                    kind = DictateApiException.Kind.UNKNOWN,
                    action = if (keepAudio) ErrorAction.RESEND else ErrorAction.NONE,
                    detail = t.message?.takeIf { it.isNotBlank() },
                )
            } finally {
                if (!keepAudio) audioFile.delete()
                // Drop the trimmed upload copy (#232); the original audioFile is the one history keeps.
                if (uploadFile !== audioFile) runCatching { uploadFile.delete() }
                // System voice input (#67): hand the terminal outcome back to the RecognitionService so it
                // delivers results / an error to the calling app. One hook covers every path (success,
                // no-speech, prompt-echo, API/unexpected error) since `outcome` is set before each return.
                if (outputTarget == OutputTarget.RECOGNITION_SERVICE) {
                    dev.patrickgold.florisboard.dictate.recognition.RecognitionBridge.completeOutcome(outcome)
                }
                logLatency(latencyTrace, "terminal:$outcome")
            }
        }
    }

    /**
     * Shared finalize step for a produced transcript, used by both the batch [transcribe] path and the
     * realtime path (issue #128): runs live-prompt rewording or the auto-formatting/auto-apply/pending
     * prompt chain (unless [alreadyFormatted]), applies the deterministic mappings, commits, and records
     * stats. [rawText] is the transcript to process; [live] routes it as a live-prompt instruction.
     */
    private suspend fun finalizeAndCommit(
        appContext: Context,
        rawText: String,
        recordedSeconds: Long,
        live: Boolean,
        alreadyFormatted: Boolean,
        finalizeViaComposing: Boolean = false,
        capture: HistoryCapture? = null,
        latencyTrace: BatchLatencyTrace? = null,
    ) {
        // The transcript after the Croatian spell check: a correction, not a rewrite, so paragraph
        // splitting still treats it as the pure transcript. The raw one stays the history's original.
        var spellchecked = rawText
        val finalText = if (live) {
            // The spoken transcript is an instruction; send it to GPT (optionally operating on the current
            // selection) and insert the answer instead of the transcript.
            _pendingPrompts.value = emptyList() // a live prompt ignores any queued prompts
            _state.value = UiState.Rewording(appContext.getString(R.string.dictate__status_rewording))
            // The text the instruction is about. Selection first, because selecting something is an
            // explicit "this bit"; otherwise the whole field, because that is what the user is
            // looking at and plainly means.
            //
            // This is the bug behind the model answering that it cannot see any text. Only the
            // selection was ever sent, so speaking an instruction with nothing highlighted handed
            // the model an instruction about nothing, and it said so.
            val outSink = sink(appContext)
            val selected = outSink.selectedText().takeIf { it.isNotBlank() }
            val whole = outSink.fullText().takeIf { it.isNotBlank() }
            val subject = selected ?: whole
            // Remembered for the history entry: asking the model to rewrite something should not be
            // the moment the original becomes unrecoverable, so the pre-prompt text is stored as the
            // entry's "original" and can be re-inserted from the archive.
            livePromptOriginal = subject.orEmpty()
            // Whether the answer replaces the field or lands at the cursor. A selection is already
            // replaced by commitText; a whole-field subject is not, and that was the bug: asking for
            // the letters to be rewritten as numbers appended a second copy underneath the first
            // instead of rewriting anything. Selecting the field first makes the commit a
            // replacement, which is what "rewrite this" plainly means.
            livePromptReplacesField = selected == null && whole != null
            // Kept for the long-press list: the same few instructions get spoken constantly, and
            // saying one aloud costs a recording, an upload and a wait every time.
            MaLivePrompts.remember(rawText)
            requestReword(rawText, subject)
        } else {
            // Normal dictation: auto-formatting + auto-apply prompts, then the prompts the user queued by
            // tapping the prompt row while recording, in tap order; then commit. [alreadyFormatted] skips
            // the rewording pass (single-call multimodal #130 already returns finished text).
            // CROATIAN SPELL CHECK FIRST (MaSpellcheck): AssemblyAI invents Croatian words, and every
            // later step would build on them. Not on single-call audio, which never went through
            // AssemblyAI, and never on English.
            // THE MAC'S STYLE PASS (MaPhoneStyle, §191) before it, when it is on and can run: it fixes
            // the same misheard words and spelling, so a dictation it styled skips the spell check.
            // E>H (MaTranslate): the English transcript becomes his Croatian message in one request,
            // which replaces the style pass and the spell check rather than running under them.
            val translated = if (translateRecording && !alreadyFormatted) translateToCroatian(rawText) else null
            val checked = when {
                translated != null -> translated
                !alreadyFormatted -> phoneStyle(rawText) ?: croatianSpellcheck(appContext, rawText)
                else -> rawText
            }
            spellchecked = checked
            val processed = if (alreadyFormatted) checked else postProcessTranscript(appContext, checked)
            applyPendingPrompts(appContext, processed)
        }
        // Paragraph splitting (issue #225): break a long *pure* transcript into paragraphs at sentence
        // boundaries. Only when nothing reworded/auto-formatted the text (a live prompt, single-call
        // multimodal, or an auto-format/prompt pass that actually changed it) — that output already carries
        // its own paragraphing and must not be second-guessed.
        val splitWords = prefs.dictate.paragraphSplitWords.get()
        val isPureTranscript = !live && !alreadyFormatted && finalText == spellchecked
        // Keep the raw transcript for the history when a prompt actually rewrote it (issue #240), so the
        // original wording stays recoverable without re-running (and paying for) the transcription. Only
        // the prompt chain counts: the deterministic steps below (paragraph splitting, custom mappings)
        // would otherwise store a near-identical copy differing in little more than line breaks.
        // For a live prompt the interesting "original" is the text that was rewritten, not the
        // spoken instruction: a rewrite that cannot be undone is a rewrite nobody dares run twice.
        // The archive keeps it so the previous version can be re-inserted.
        val originalForHistory = when {
            live && livePromptOriginal.isNotBlank() -> livePromptOriginal
            finalText != rawText -> rawText
            else -> ""
        }
        val paragraphed = if (isPureTranscript && splitWords > 0) {
            TranscriptParagraphs.split(finalText, splitWords)
        } else {
            finalText
        }
        // Deterministic find-and-replace dictionary (issue #129), applied right before insert.
        val mapped = prefs.dictate.customMappings.get().apply(paragraphed)
        // Case forced last, after every other transformation, so nothing downstream can put a capital
        // back. Only for plain transcripts: a rewrite asked for in words is allowed to choose its own
        // capitals, and flattening a prompt's answer would undo the very thing it was asked to do.
        val outputText = if (isPureTranscript) maApplyCase(mapped) else mapped
        if (finalizeViaComposing) {
            // Realtime only runs while a field is open — it streams into one. So this path always
            // delivered, and saying so explicitly is what makes the flag trustworthy: it was set in
            // the other branch only, and left at its previous value here, so a realtime dictation
            // inherited whatever the last one happened to be. **A flag written on one path and read
            // on both is not a flag, it is a memory of somewhere else.**
            deliveredToField = true
            // Realtime (#128): replace the live-streamed preview with the finished (reworded) result via the
            // minimal diff, then honor auto-enter — instead of committing on top of the preview.
            val outSink = sink(appContext)
            outSink.commitDictationFinal(outputText, realtimeShown.toString())
            realtimeShown.setLength(0)
            if (prefs.dictate.autoEnter.get() && outputText.isNotEmpty()) outSink.performEnter()
        } else {
            // Safety net (#214): for the floating button, optionally copy every dictation to the system
            // clipboard so nothing is lost if the accessibility insert is silently swallowed (the known
            // "green check but no text" case). Done BEFORE the commit on purpose: the paste-fallback captures
            // the current clipboard as "previous" and restores it 400 ms later, so with our text already on
            // the clipboard that restore becomes a no-op instead of wiping the copy.
            if (outputTarget == OutputTarget.OVERLAY && outputText.isNotEmpty() &&
                prefs.dictate.floatingButtonCopyToClipboard.get()
            ) {
                copyToSystemClipboard(appContext, outputText)
            }
            val committed = commitOutput(appContext, outputText)
            deliveredToField = committed
            if (committed) latencyTrace?.let { logLatency(it, "outputCommitted") }
            // Floating button (#156): the accessibility insert can be silently swallowed by some app fields
            // (Gemini's Compose box, WebViews). Don't flash a false green check — stash the text so the
            // user can recover it via Reinsert, and surface an error instead of "success".
            if (!committed && outputTarget == OutputTarget.OVERLAY && outputText.isNotEmpty()) {
                rememberLastDictation(outputText)
                if (capture?.isReplay != true) {
                    if (recordedSeconds > 0L) creditAudioSeconds(recordedSeconds)
                }
                recordHistory(appContext, outputText, originalForHistory, recordedSeconds, capture, reworded = live)
                discardRetainedAudio()
                _state.value = UiState.Error(
                    message = appContext.getString(R.string.dictate__error_overlay_insert_failed),
                )
                return
            }
        }
        // Send, now that the words are in the field.
        //
        // Placed after BOTH delivery branches, not inside one of them. The realtime path commits
        // through `commitDictationFinal` and never touches `committed`, so a send consumed inside
        // the other branch would simply never fire for anyone using realtime — and it would fail
        // silently, which is the worst way for this to be wrong.
        //
        // The ORDER is the feature. A send that fires before the text lands sends an empty message
        // and nobody finds out until they read the other end. Everything above this line has
        // already delivered the text; nothing below it does.
        //
        // Consumed unconditionally, so a send armed by one recording can never fire on the next.
        if (sendAfterCommit) {
            sendAfterCommit = false
            if (outputText.isNotEmpty()) {
                // A beat before pressing. 333 ms, his number.
                //
                // Committing the text and pressing Send are two events in another app's life, and
                // that app needs a moment between them. Claude accepts the press immediately;
                // Gemini sometimes does not — its send button enables itself once the field reports
                // content, and a press that lands in the same frame as the text arrives at a button
                // that is still disabled. Nothing throws. The message simply sits there.
                //
                // **The delay is not a fix for a race in this app; it is politeness toward one in
                // theirs**, and there is nothing here that can observe the other side becoming
                // ready. A third of a second is under the threshold where he would call it slow and
                // well over the frame or two the other app needs.
                delay(SEND_SETTLE_MS)
                MaMagicTargets.pressSend()
            } else {
                MaLog.add("keys", "send skipped, there was nothing to send")
            }
        }

        // Re-insert safety net (issue #111) + lifetime stats (issue #142) + history log (issue #140).
        rememberLastDictation(outputText)
        if (capture?.isReplay != true) {
            if (recordedSeconds > 0L) creditAudioSeconds(recordedSeconds)
        }
        // DID THE WORDS LAND ANYWHERE?
        //
        // He dictates with no text field open — the volume keys record whether or not a keyboard is
        // up — and the transcript then went to the history looking exactly like every other entry: a
        // first line, a timestamp, and nothing saying it was never delivered.
        //
        // **A dictation with nowhere to go is a note, and a different kind of thing.** Its own
        // source, its own colour in the list, and a title, because he comes back to it hours later
        // and a wall of first lines is not something anybody can search by eye.
        val landed = deliveredToField
        val historyCapture = if (landed) {
            capture
        } else {
            capture?.copy(source = DictateHistorySource.OFFLINE)
        }
        val noteTitle = if (landed) "" else titleFor(outputText)
        if (!landed) MaLog.add("dictate", "kept as an offline note: \"$noteTitle\"")
        recordHistory(
            appContext, outputText, originalForHistory, recordedSeconds, historyCapture,
            reworded = live, title = noteTitle,
        )
        discardRetainedAudio()
        _state.value = UiState.Idle
        if (outputTarget != OutputTarget.IME || !showMilestoneNudge(appContext)) {
            maybePromptForReview()
        }
    }

    /** Copies [text] to the system clipboard — the floating-button always-copy safety net (issue #214). */
    private fun copyToSystemClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("TTT", text)) }
    }

    // --- Real-time streaming (issue #128) -------------------------------------------------------

    /** The realtime wire API to use for the active transcription account, or null if realtime shouldn't run. */
    private fun realtimeApiForActiveAccount(): RealtimeApi? {
        if (!prefs.dictate.realtimeTranscription.get()) return null
        val account = MaProviders.transcriptionAccount()
        // The raw field on purpose: this only asks whether any key exists at all, and the ring can
        // legitimately have flagged every one of them without that meaning realtime is unavailable.
        if (account.apiKey.isBlank()) return null
        val preset = MaProviders.presetFor(account)
        return if (preset.supportsRealtime) preset.realtimeApi else null
    }

    /**
     * The installed on-device **streaming** model to run live (issue #233), or null if local live
     * transcription doesn't apply. Checked before [realtimeApiForActiveAccount] because that one bails
     * out on a blank API key, which the on-device provider always has.
     */
    private fun localStreamingModelDir(context: Context): File? {
        if (!prefs.dictate.realtimeTranscription.get()) return null
        val account = MaProviders.transcriptionAccount()
        if (MaProviders.presetFor(account).transcriptionApi != TranscriptionApi.LOCAL_ONDEVICE) return null
        // The live model has its own slot on the local account (#233) — `realtimeModel`, which for this
        // provider means the streaming model rather than a remote model id. The one-shot slot is also
        // accepted as a source: before the two slots existed a streaming model could only be picked
        // there, and such a setup should keep working instead of silently dropping back to batch.
        val modelId = account.realtimeModel.takeIf { LocalModelCatalog.isStreaming(it) }
            ?: account.transcriptionModel.takeIf { LocalModelCatalog.isStreaming(it) }
            ?: return null
        if (!LocalModelManager.isInstalled(context, modelId)) return null
        return LocalTranscriptionProvider.modelDir(context, modelId)
    }

    /**
     * True if the next recording should stream in real time: the global toggle is on and either the cloud
     * provider supports realtime or (with [context]) an on-device streaming model is installed. Without a
     * context only the cloud case can be answered, since the local check has to look at the filesystem.
     */
    fun isRealtimeActive(context: Context? = null): Boolean =
        realtimeApiForActiveAccount() != null ||
            (context != null && localStreamingModelDir(context.applicationContext) != null)

    /** True while a real-time streaming recording is actually in progress (a session is open). */
    fun isRealtimeRecording(): Boolean = realtimeSession != null

    /**
     * Opens a realtime session for the active account and returns a PCM sink to hand [RecordingController]
     * (which feeds captured 16 kHz frames, resampled per provider). Returns null when realtime does not
     * apply or the session can't be created — the caller then records normally (batch).
     */
    private fun openRealtimeSession(appContext: Context): ((ByteArray, Int) -> Unit)? {
        // System voice input (#67) always records in plain batch mode — the RecognitionService callback
        // returns one final result, so there's no realtime streaming/composing to wire up here.
        if (outputTarget == OutputTarget.RECOGNITION_SERVICE) return null
        // On-device live model (#233) wins over the cloud lookup: the local provider has no API key, so
        // realtimeApiForActiveAccount() would reject it before ever getting here.
        val localModelDir = localStreamingModelDir(appContext)
        val api = if (localModelDir != null) null else realtimeApiForActiveAccount() ?: return null
        val account = MaProviders.transcriptionAccount()
        val preset = MaProviders.presetFor(account)
        val model = if (localModelDir != null) {
            localModelDir.name
        } else {
            account.realtimeModel.takeIf { it.isNotBlank() } ?: preset.defaultRealtimeModel ?: return null
        }
        // Realtime is told the language too. Streaming had the same detect hole as the batch path.
        val language = MaLanguage.active()
        realtimeFinal.setLength(0)
        realtimeFailed = false
        realtimeCancelled = false
        _interimText.value = ""
        realtimeContext = appContext
        realtimeShown.setLength(0)
        val closed = CompletableDeferred<Unit>()
        realtimeClosed = closed
        // Type the growing transcript live into the field, applying only the minimal diff each time (#128).
        fun showLive(full: String) {
            if (realtimeCancelled) return   // a late callback must not re-add text after a cancel
            _interimText.value = full
            runCatching { sink(appContext).setDictationPreview(full, realtimeShown.toString()) }
            realtimeShown.setLength(0)
            realtimeShown.append(full)
        }
        val callbacks = object : RealtimeCallbacks {
            override fun onPartial(text: String) {
                scope.launch {
                    val head = realtimeFinal.toString()
                    showLive((if (head.isEmpty()) text else "$head $text").trim())
                }
            }
            override fun onFinalSegment(text: String) {
                scope.launch {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        if (realtimeFinal.isNotEmpty()) realtimeFinal.append(' ')
                        realtimeFinal.append(t)
                    }
                    showLive(realtimeFinal.toString())
                }
            }
            override fun onError(t: Throwable) { realtimeFailed = true }
            override fun onClosed() { closed.complete(Unit) }
        }
        val session = runCatching {
            if (localModelDir != null) {
                // Same idle-unload budget the batch path uses, so a live model doesn't sit in RAM either.
                LocalTranscriptionProvider.setIdleUnloadMillis(
                    prefs.dictate.localModelUnloadMinutes.get() * 60_000L,
                )
                // The model is language-specific, so the input-language pref is irrelevant here.
                LocalRealtimeSession(localModelDir, callbacks)
            } else {
                RealtimeClient.open(
                    api!!,
                    MaKeyRingStore.currentKey(account.providerId, account.apiKey),
                    model,
                    language,
                    callbacks,
                )
            }
        }.getOrElse { realtimeFailed = true; null } ?: return null
        realtimeSession = session
        // On-device runs at the recorder's native rate, so no resampling step is needed.
        val targetRate = if (api == null) AudioDecode.TARGET_SAMPLE_RATE else RealtimeClient.sampleRateFor(api)
        if (targetRate == AudioDecode.TARGET_SAMPLE_RATE) {
            return { pcm, len ->
                runCatching { session.sendAudio(pcm, len) }
            }
        }
        return { pcm, len ->
            val out = Pcm16Resampler.resample(pcm, len, AudioDecode.TARGET_SAMPLE_RATE, targetRate)
            runCatching { session.sendAudio(out, out.size) }
        }
    }

    /**
     * Stops a realtime recording: finalizes the stream, then commits the accumulated transcript through the
     * shared [finalizeAndCommit]. Keeps the recorded WAV so any stream failure (or an empty transcript)
     * falls back to a normal batch [transcribe] of the audio — the user never loses their dictation.
     */
    private fun stopRealtimeAndFinalize(context: Context) {
        val session = realtimeSession
        realtimeSession = null
        realtimeContext = null
        val activeRecorder = recorder
        recorder = null
        _livePromptActive.value = false
        unregisterScreenOffReceiver()
        val recordedSeconds = recordedSecondsOf(_state.value)
        val wavFile = activeRecorder?.stop()
        cleanupAudioRouting()
        val live = livePromptArmed
        livePromptArmed = false
        val closed = realtimeClosed
        realtimeClosed = null
        _state.value = UiState.Transcribing()
        val appContext = context.applicationContext
        transcribeJob = scope.launch {
            try {
                runCatching { session?.finish() }
                // Wait briefly for the provider to flush the last words (ends early if it closes), then
                // force-close the socket — several providers keep it open after finish, which otherwise
                // stalls us until the timeout and later trips a ping/pong failure.
                withTimeoutOrNull(REALTIME_FINALIZE_TIMEOUT_MS) { closed?.await() }
                runCatching { session?.cancel() }
                // The transcript is what we already streamed into the field (finals + last partial); fall
                // back to the finalized-segments buffer only if nothing was shown.
                val transcript = realtimeShown.toString().trim().ifEmpty { realtimeFinal.toString().trim() }
                _interimText.value = ""
                if (realtimeFailed || transcript.isEmpty()) {
                    // Drop the live provisional text; the batch path commits fresh from the WAV.
                    runCatching { sink(appContext).clearDictationPreview(realtimeShown.toString()) }
                    realtimeShown.setLength(0)
                    if (wavFile != null && wavFile.exists() && wavFile.length() > 0L) {
                        livePromptArmed = live
                        transcribe(context, wavFile, recordedSeconds, gate = false)
                    } else {
                        _state.value = UiState.Error(appContext.getString(R.string.dictate__error_no_audio))
                    }
                    return@launch
                }
                // History (issue #140): capture the metadata + WAV before deleting the cache file, so
                // audio retention (if on) can copy it in during finalize; then drop the cache original.
                val rtAccount = MaProviders.transcriptionAccount()
                val rtPreset = MaProviders.presetFor(rtAccount)
                val rtModel = rtAccount.realtimeModel.takeIf { it.isNotBlank() }
                    ?: rtPreset.defaultRealtimeModel ?: ""
                val rtCapture = HistoryCapture(
                    audioFile = wavFile?.takeIf { it.exists() && it.length() > 0L },
                    providerId = rtAccount.providerId,
                    providerName = rtAccount.displayName.ifBlank { rtPreset.displayName },
                    model = rtModel,
                    language = MaLanguage.active(),
                    source = DictateHistorySource.REALTIME,
                )
                finalizeAndCommit(appContext, transcript, recordedSeconds, live, alreadyFormatted = false, finalizeViaComposing = true, capture = rtCapture)
                wavFile?.delete()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _interimText.value = ""
                runCatching { sink(appContext).clearDictationPreview(realtimeShown.toString()) }
                realtimeShown.setLength(0)
                if (wavFile != null && wavFile.exists() && wavFile.length() > 0L) {
                    livePromptArmed = live
                    transcribe(context, wavFile, recordedSeconds, gate = false)
                } else {
                    _state.value = UiState.Error(appContext.getString(R.string.dictate__error_unknown))
                }
            }
        }
    }

    // --- Long-form segmented dictation (issue #170) ---------------------------------------------

    /**
     * Whether the next recording should run in segmented mode: the feature is on, output goes to the
     * keyboard (not the accessibility overlay), it's not a live-prompt recording, realtime streaming is
     * not active, and the provider isn't in single-call multimodal mode (which would format per segment).
     */
    private fun isSegmentedMode(context: Context): Boolean =
        prefs.dictate.longformMode.get().isEnabled &&
            outputTarget == OutputTarget.IME &&
            !livePromptArmed &&
            !isRealtimeActive(context) &&
            !MaProviders.transcriptionAccount().transcriptionViaChat

    private fun initSegmented(appContext: Context) {
        segmentedActive = true
        segmentNextIndex = 0
        segmentCommitIndex = 0
        segmentResults.clear()
        segmentAudioFiles.clear()
        segmentInFlightCount = 0
        segmentStopped = false
        segmentRecordedSeconds = 0L
        _segmentFlushCount.value = 0
        // Keep + merge the segment audio only when the history feature would actually store it.
        segmentKeepAudio = prefs.dictate.historyEnabled.get() && prefs.dictate.historyAudioRetention.get()
        realtimeShown.setLength(0)
        // Reuse the realtime shown-text context so the existing cancel/interrupt cleanup clears the preview.
        realtimeContext = appContext
        realtimeCancelled = false
        _segmentsInFlight.value = 0
        _segmentedRecording.value = true
    }

    private fun resetSegmentedState() {
        segmentedActive = false
        segmentNextIndex = 0
        segmentCommitIndex = 0
        segmentResults.clear()
        segmentAudioFiles.clear() // files themselves are deleted by finalize/cancel, not here
        segmentInFlightCount = 0
        segmentStopped = false
        segmentVad?.release()
        segmentVad = null
        _segmentsInFlight.value = 0
        _segmentedRecording.value = false
    }

    /**
     * Cuts the current segment and keeps recording (the "Next segment" button, issue #170). The cut audio
     * is transcribed in the background and its raw text appended to the field in order. No-op unless a
     * segmented recording is actually in progress.
     */
    fun flushSegment(context: Context) {
        flushSegment(context, splitterAlreadyReset = false)
    }

    private fun flushSegment(context: Context, splitterAlreadyReset: Boolean) {
        if (!segmentedActive || _state.value !is UiState.Recording) return
        val appContext = context.applicationContext
        // Manual cuts reset the analyzer at call time so audio queued after this point belongs to the next
        // turn. Automatic cuts already reset atomically inside LiveSpeechSplitter before invoking us.
        if (!splitterAlreadyReset) segmentVad?.notifyCut()
        scope.launch {
            val assigned = segmentMutex.withLock {
                if (!segmentedActive || _state.value !is UiState.Recording) return@withLock null
                val i = segmentNextIndex++
                val w = withContext(Dispatchers.IO) { recorder?.rotate() }
                if (segmentKeepAudio && w != null && w.exists() && w.length() > 0L) segmentAudioFiles[i] = w
                segmentInFlightCount++
                _segmentsInFlight.value = segmentInFlightCount
                _segmentFlushCount.value = _segmentFlushCount.value + 1
                i to w
            } ?: return@launch
            val (idx, wav) = assigned
            if (wav != null && wav.exists() && wav.length() > 0L) {
                launchSegmentTranscription(appContext, idx, wav)
            } else {
                // Nothing captured since the last cut (e.g. a double tap): keep the index sequence
                // contiguous so the ordered drain never stalls.
                onSegmentResult(appContext, idx, "")
            }
        }
    }

    /**
     * Cancel-button behaviour: in long-form mode, tapping the trash button ends the recording but keeps
     * everything transcribed so far — the already-committed segments are finalized and saved to history as
     * usual; only the current, not-yet-cut chunk is thrown away instead of being transcribed (#183).
     * Outside long-form it aborts the whole recording ([cancelRecording]). Used by both the Smartbar and
     * the legacy layout so the trash button behaves consistently.
     */
    fun cancelOrDiscardSegment(context: Context) {
        if (segmentedActive && _state.value is UiState.Recording) {
            stopSegmentedAndFinalize(context, discardFinal = true)
        } else {
            cancelRecording()
        }
    }

    /**
     * Stops a segmented recording: cuts the final open segment, then finishes once every queued segment
     * has transcribed and been committed in order — at which point the whole assembled transcript runs
     * through the normal post-processing once (auto-format + prompts + mappings) and replaces the preview.
     *
     * [discardFinal] (the cancel button, #183) throws the final open chunk away instead of transcribing it,
     * so the session still finalizes + saves to history with everything captured up to the last cut, but
     * the current unfinished utterance is dropped.
     */
    private fun stopSegmentedAndFinalize(context: Context, discardFinal: Boolean = false) {
        val appContext = context.applicationContext
        _livePromptActive.value = false
        unregisterScreenOffReceiver()
        segmentRecordedSeconds = recordedSecondsOf(_state.value)
        _segmentedRecording.value = false
        _state.value = UiState.Transcribing()
        scope.launch {
            val assigned = segmentMutex.withLock {
                val i = segmentNextIndex++
                val activeRecorder = recorder
                recorder = null
                val w = withContext(Dispatchers.IO) { activeRecorder?.stop() }
                cleanupAudioRouting()
                if (discardFinal) {
                    // Deleted chunk: drop its audio so it lands in neither the transcript nor the history WAV.
                    withContext(Dispatchers.IO) { runCatching { w?.delete() } }
                } else if (segmentKeepAudio && w != null && w.exists() && w.length() > 0L) {
                    segmentAudioFiles[i] = w
                }
                segmentStopped = true
                segmentInFlightCount++
                _segmentsInFlight.value = segmentInFlightCount
                i to w
            }
            val (idx, wav) = assigned
            if (!discardFinal && wav != null && wav.exists() && wav.length() > 0L) {
                launchSegmentTranscription(appContext, idx, wav)
            } else {
                onSegmentResult(appContext, idx, "")
            }
        }
    }

    private fun launchSegmentTranscription(appContext: Context, idx: Int, wav: File) {
        val job = scope.launch {
            // Best-effort cross-segment continuity: bias the recognizer with what's committed so far.
            val continuity = realtimeShown.toString()
            // One retry before giving up; a still-failed segment leaves a gap (no placeholder), but its
            // audio is preserved in the merged history WAV so nothing is truly lost.
            val text = transcribeSegmentRaw(appContext, wav, continuity)
                ?: transcribeSegmentRaw(appContext, wav, continuity)
            // Keep the WAV when it will be merged into the history audio; otherwise drop it now.
            if (!segmentKeepAudio) withContext(Dispatchers.IO) { runCatching { wav.delete() } }
            onSegmentResult(appContext, idx, text ?: "")
        }
        segmentJobs.add(job)
        job.invokeOnCompletion { segmentJobs.remove(job) }
    }

    /**
     * Buffers a finished segment's raw text and drains the ordered commit queue: appends every now-in-order
     * segment to the field's live preview. When the last segment lands after a stop, runs the end finalize.
     */
    private suspend fun onSegmentResult(appContext: Context, idx: Int, text: String) {
        val shouldFinish = segmentMutex.withLock {
            segmentResults[idx] = text
            while (segmentResults.containsKey(segmentCommitIndex)) {
                val raw = segmentResults.remove(segmentCommitIndex)!!.trim()
                segmentCommitIndex++
                if (raw.isNotEmpty()) {
                    val prev = realtimeShown.toString()
                    val full = if (prev.isEmpty()) raw else "$prev $raw"
                    runCatching { sink(appContext).setDictationPreview(full, prev) }
                    realtimeShown.setLength(0)
                    realtimeShown.append(full)
                }
            }
            segmentInFlightCount--
            _segmentsInFlight.value = segmentInFlightCount.coerceAtLeast(0)
            segmentStopped && segmentInFlightCount <= 0
        }
        if (shouldFinish) finalizeSegmentedEnd(appContext)
    }

    /**
     * All segments are in and committed as a raw preview; run the whole dictation through the normal
     * post-processing once and replace the preview with the finished (formatted/reworded) text.
     */
    private suspend fun finalizeSegmentedEnd(appContext: Context) {
        val account = MaProviders.transcriptionAccount()
        val preset = MaProviders.presetFor(account)
        val model = MaProviders.transcriptionModelFor(appContext, account, preset)
        val assembled = realtimeShown.toString().trim()
        val recordedSeconds = segmentRecordedSeconds
        // Snapshot the kept segment files (in cut order) before resetting; merge them into one WAV so the
        // whole dictation has a single retained-audio file in the history (issue #170 / #140 reuse).
        val keepAudio = segmentKeepAudio
        val audioFiles = segmentAudioFiles.toSortedMap().values.filter { it.exists() && it.length() > 0L }
        resetSegmentedState()
        val mergedWav = if (keepAudio && audioFiles.isNotEmpty()) {
            val merged = File(appContext.cacheDir, "dictate_seg_merged.wav")
            merged.delete()
            if (withContext(Dispatchers.IO) { AudioConcat.concat(audioFiles, merged) } && merged.exists() && merged.length() > 0L) merged else null
        } else null
        withContext(Dispatchers.IO) { audioFiles.forEach { runCatching { it.delete() } } }
        if (assembled.isEmpty()) {
            runCatching { sink(appContext).clearDictationPreview(realtimeShown.toString()) }
            realtimeShown.setLength(0)
            mergedWav?.delete()
            _state.value = UiState.Idle
            return
        }
        val capture = HistoryCapture(
            audioFile = mergedWav, // the merged segment audio, or null when retention is off
            providerId = account.providerId,
            providerName = account.displayName.ifBlank { preset.displayName },
            model = model,
            language = MaLanguage.active(),
            source = DictateHistorySource.KEYBOARD,
        )
        finalizeAndCommit(
            appContext, assembled, recordedSeconds, live = false,
            alreadyFormatted = false, finalizeViaComposing = true, capture = capture,
        )
        mergedWav?.delete() // history already copied it during finalize
    }

    /**
     * Transcribes one cut segment to RAW text (no formatting/rewording — that runs once at the end). Uses
     * the dedicated STT endpoint (never the multimodal chat path, which would format), with the offline
     * on-device fallback. Returns null on failure (the segment is dropped, keeping the sequence contiguous).
     */
    private suspend fun transcribeSegmentRaw(appContext: Context, wav: File, continuity: String): String? {
        // Silence gate (issue #93): skip a segment that is just silence — e.g. the trailing pause before a
        // cut/stop — so the model can't hallucinate ghost text ("Vielen Dank" / "Thanks for watching")
        // from it. Fails open (transcribes) if the check can't run, so real speech is never dropped.
        if (prefs.dictate.skipSilentRecordings.get() && !SpeechGate.hasSpeech(appContext, wav)) return null
        val account = MaProviders.transcriptionAccount()
        // ONE key, chosen by the ring. This field holds every key separated by newlines, so passing
        // it straight through sent all of them as a single credential and the service answered with
        // a complaint about a line break in the header. That worked for as long as there was only
        // ever one key in it.
        val apiKey = MaKeyRingStore.currentKey(account.providerId, account.apiKey)
        val preset = MaProviders.presetFor(account)
        val model = MaProviders.transcriptionModelFor(appContext, account, preset, "gpt-4o-mini-transcribe")
        val language = MaLanguage.active()
        val style = transcriptionStylePrompt()
        val prompt = continuity.takeLast(200).trim().let { if (it.isEmpty()) style else "$it $style".trim() }
        val request = TranscriptionRequest(audioFile = wav, model = model, language = language, prompt = prompt)
        return try {
            val result = if (preset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) {
                if (!LocalModelManager.isInstalled(appContext, model)) return null
                withContext(Dispatchers.IO) {
                    LocalTranscriptionProvider(LocalTranscriptionProvider.modelDir(appContext, model)).transcribe(request)
                }
            } else {
                if (apiKey.isBlank() && MaProviders.requiresKey(account)) return null
                try {
                    OpenAiCompatibleClient.from(
                        preset, apiKey,
                        baseUrlOverride = MaProviders.baseUrlOverrideFor(account),
                        proxy = prefs.dictate.dictateProxyConfig(),
                        useChatAudio = false,
                        trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                    ).transcribe(request)
                } catch (e: DictateApiException) {
                    val fallback = MaProviders.localFallbackProvider(appContext, preset, e) ?: throw e
                    fallback.transcribe(request)
                }
            }
            result.text
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            null
        }
    }

    // --- Output behavior + resend (roadmap section 10) ------------------------------------------

    /**
     * Resolves the output sink for the current dictation: where the finished text is written and how the
     * focused field is read. The keyboard's own editor ([ImeDictationSink]) for in-keyboard dictation, or
     * an accessibility-backed sink ([dev.patrickgold.florisboard.dictate.overlay.AccessibilitySink]) when
     * the dictation was started from the floating button (issue #88) and must inject into another app.
     * This single seam keeps the rest of the engine editor-agnostic.
     */
    private fun editor(context: Context) = context.applicationContext.editorInstance().value

    /** Learns from the last dictation in the keyboard's field (MaDictationCorrections.settle). */
    private fun settleCorrections(context: Context) {
        runCatching { MaDictationCorrections.settle(editor(context).activeInfo) }
    }

    private fun sink(context: Context): DictationSink = when (outputTarget) {
        OutputTarget.IME -> ImeDictationSink(context)
        OutputTarget.OVERLAY -> AccessibilitySink()
        // System voice input (#67): the finished text is handed back to the OS via the RecognitionService
        // callback (the calling app/keyboard inserts it), not written into a field ourselves.
        OutputTarget.RECOGNITION_SERVICE -> RecognitionSink()
    }

    /**
     * Commits [text] into the focused field honoring the output prefs: either all at once
     * ([prefs.dictate.instantOutput]) or "typed" character by character at the configured speed, then
     * an optional auto-enter (10.1). Runs on the caller's (Main) coroutine, so the typewriter delay
     * suspends rather than blocks.
     */
    private suspend fun commitOutput(context: Context, text: String): Boolean {
        // Length rather than content: the log is pasted into a chat, and a transcription is his
        // writing. Knowing that 47 characters arrived answers "did it transcribe" without putting
        // his words somewhere he did not choose to put them.
        MaLog.add("dictate", "commit ${text.length} chars, target=$outputTarget")
        // Empty result (e.g. silence): nothing to insert — a no-op is a success, not a failed write.
        if (text.isEmpty()) return true
        // A voice command, before anything is written. "press send" said on its own is an
        // instruction rather than a sentence, and this is the one place every finished
        // transcription passes through on its way to a field, whichever sink it is bound for.
        //
        // A command may also END a dictation — say the message, pause, say "press send" — in which
        // case the words in front of it are text and are written first. The press happens after
        // the writing, always, because a send that fired before the words were in the field would
        // send an empty one.
        //
        // It only wins if the press actually lands. pressScreenTarget returns the term it found, or
        // null when the screen has nothing by that name — and on null the words are written exactly
        // as they would have been. So a misheard command costs nothing, which is the whole reason
        // the rule can be allowed to fire without asking first.
        var pendingPress: List<String>? = null
        // Spoken formatting first: "the word parenthesis" becomes "the (word)".
        //
        // Before the press-send check, because a command word is removed here and the check reads
        // the last two words. Doing it the other way round would leave "dot" sitting between his
        // sentence and the word "press", and neither rule would see what it was looking for.
        var body = MaSpokenFormat.apply(text)
        if (prefs.dictate.maVoiceCommands.get() && DictateAccessibilityService.isRunning) {
            val split = MaVoiceCommand.splitTrailing(body)
            if (split != null) {
                val targets = MaMagicTargets.parse(prefs.dictate.maMagicTargets.get())
                    .ifEmpty { MaMagicTargets.defaults() }
                val candidates = MaVoiceCommand.candidatesFor(split.target, targets)
                if (split.text.isEmpty()) {
                    // Nothing to write: the whole dictation was the command. If the press finds
                    // nothing, fall through and type the words rather than swallowing them.
                    if (DictateAccessibilityService.pressScreenTarget(candidates) != null) return true
                } else {
                    body = split.text
                    pendingPress = candidates
                }
            }
        }
        // HIS CORRECTIONS (MaDictationCorrections): first, whatever he fixed in the last dictation is learned,
        // before this one moves the field on; then the fixes he has taught are made in this one.
        if (outputTarget == OutputTarget.IME) settleCorrections(context)
        body = MaDictationCorrections.apply(body)
        val sink = sink(context)
        // A live prompt that worked on the whole field replaces it. commitText already replaces a
        // selection, so selecting everything first turns the insert into the rewrite that was asked
        // for. Reset immediately: this is true for exactly one commit.
        if (livePromptReplacesField) {
            livePromptReplacesField = false
            sink.selectAll()
        }
        // A SPACE BETWEEN HIS TEXT AND THE DICTATION (phone test, build 398): "C2:" + a dictation gave
        // "C2:Do you…". Only when the field ends on a word or punctuation and the dictation starts
        // with a letter or digit; never after a space, a line break or an opening bracket.
        if (MaDictationSpace.needsLeadingSpace(sink.charBeforeCursor(), body.firstOrNull())) body = " $body"
        // Where it starts, read before it is written: after, the field's copy may not have caught up.
        val watchStart = if (outputTarget == OutputTarget.IME) editor(context).activeContent.selection.start else -1
        val watchBefore = if (outputTarget == OutputTarget.IME) editor(context).activeContent.textBeforeSelection else ""
        var committed: Boolean
        // System voice input (#67) returns the whole result at once — no typewriter animation, which only
        // makes sense when we're the one typing into a visible field.
        if (prefs.dictate.instantOutput.get() || outputTarget == OutputTarget.RECOGNITION_SERVICE) {
            committed = sink.commitText(body)
        } else {
            val perChar = perCharDelayMs(prefs.dictate.outputSpeed.get())
            committed = true
            body.forEach { ch ->
                if (!sink.commitText(ch.toString())) committed = false
                delay(perChar)
            }
        }
        if (committed && watchStart >= 0) {
            val state = context.keyboardManager().value.activeState
            MaDictationCorrections.noteDictation(body, watchStart, watchBefore, editor(context).activeInfo, state.isIncognitoMode)
        }
        // No auto-enter on an empty result (e.g. silence): don't fire a stray newline into the field (#124).
        if (prefs.dictate.autoEnter.get()) {
            sink.performEnter()
        }
        // The trailing command, last of all: the words are in the field, so a send now sends them.
        // Deliberately after the auto-enter too, since both are endings and the order between them
        // is the order they were asked for — write, finish, then press.
        //
        // A press that finds nothing is not an error here. The words were written either way, which
        // is the outcome he would have had before this feature existed.
        pendingPress?.let { DictateAccessibilityService.pressScreenTarget(it) }
        return committed
    }

    /** Per-character delay for the typewriter output: speed 1 → 100 ms … 5 → 20 ms … 10 → 10 ms (legacy mapping). */
    private fun perCharDelayMs(speed: Int): Long = (100L / speed.coerceIn(1, 10)).coerceAtLeast(1L)

    // --- Retained audio + unified resend (failed transcription / interrupted recording) ---------

    /**
     * Retains [audioFile] after a failed transcription so the error chip's resend button can retry it,
     * if the resend button is enabled and the file is usable; returns true when kept. The file stays in
     * the cache (a transient failure does not need to survive process death). Any previously kept audio
     * is discarded first.
     */
    private fun retainFailedAudio(
        audioFile: File,
        wasLive: Boolean,
        recordedSeconds: Long,
        // Keep even when the resend button is off — used for exportable failures so the recording can be
        // saved (issue #144); otherwise retention is gated on the resend-button preference.
        force: Boolean = false,
    ): Boolean {
        if (!force && !prefs.dictate.resendButton.get()) return false
        if (!audioFile.exists() || audioFile.length() == 0L) return false
        if (retained?.file != audioFile) discardRetainedAudio()
        retained = RetainedAudio(audioFile, RetainReason.FAILED, wasLive, recordedSeconds)
        return true
    }

    /** Deletes the kept audio (if any), forgets it, and clears the persisted interrupted-audio marker. */
    fun discardRetainedAudio() {
        retained?.file?.takeIf { it.exists() }?.delete()
        retained = null
        scope.launch { clearInterruptedAudioPref() }
    }

    /**
     * Re-sends the currently kept audio — used by *both* the error-resend chip and the interrupted-
     * recording chip (unified path). Repeats the original mode (a kept live-prompt resends as a live
     * prompt) and re-credits the recorded seconds towards the nudges. No-op unless we are idle/showing
     * one of the resend chips and a usable file exists. Interrupted audio is claimed (its persisted
     * marker cleared) up front, so a crash mid-transcription cannot re-offer the same recording.
     */
    fun sendRetainedAudio(context: Context) {
        if (_state.value !is UiState.Error && _state.value !is UiState.Interrupted &&
            _state.value !is UiState.Idle
        ) return
        val r = retained
        if (r == null || !r.file.exists() || r.file.length() == 0L) {
            discardRetainedAudio()
            _state.value = UiState.Idle
            return
        }
        if (r.reason == RetainReason.INTERRUPTED) scope.launch { clearInterruptedAudioPref() }
        livePromptArmed = r.wasLive
        // A user-initiated resend of already-captured audio is sent as-is (no silence gate — issue #93).
        transcribe(context, r.file, r.seconds, gate = false)
    }

    /**
     * Exports the kept recording to the public Downloads folder (issue #144), so a dictation that failed
     * for a non-retryable reason (too large / unsupported format) can be recovered instead of lost. On
     * success the audio is dropped and a toast confirms the file name; on failure it is kept so the user
     * can try again. No-op unless a usable kept file exists.
     */
    fun saveRetainedAudio(context: Context) {
        val r = retained
        if (r == null || !r.file.exists() || r.file.length() == 0L) {
            discardRetainedAudio()
            _state.value = UiState.Idle
            return
        }
        val appContext = context.applicationContext
        val src = r.file
        scope.launch {
            val savedName = withContext(Dispatchers.IO) { exportAudioToDownloads(appContext, src) }
            val message = if (savedName != null) {
                appContext.getString(R.string.dictate__audio_saved, savedName)
            } else {
                appContext.getString(R.string.dictate__audio_save_failed)
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            // Keep the audio on failure so the user can retry the export; drop it once safely saved.
            if (savedName != null) discardRetainedAudio()
            _state.value = UiState.Idle
        }
    }

    /** Copies [src] into Downloads/Dictate as a WAV via MediaStore (API 29+) or the public dir. Returns the file name, or null on failure. */
    private fun exportAudioToDownloads(context: Context, src: File): String? = runCatching {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "dictate-recording-$stamp.wav"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TTT")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return null
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TTT")
            dir.mkdirs()
            src.inputStream().use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
        }
        name
    }.getOrNull()

    /**
     * Continues an interrupted recording instead of sending it: the kept audio becomes a carry-over
     * segment and a fresh recording starts, with the timer seeded so it shows the running total. When the
     * user finally stops, the carry-over and the new segment are merged into one audio and transcribed
     * together (see [stopAndTranscribe]); if the keyboard closes again first, both are merged back into
     * the persisted interrupted file (see [stashRecordingOnHide]). No-op unless the interrupted chip is
     * showing with a usable file.
     */
    fun continueInterruptedRecording(context: Context) {
        if (_state.value !is UiState.Interrupted) return
        val r = retained
        if (r == null || r.reason != RetainReason.INTERRUPTED || !r.file.exists() || r.file.length() == 0L) {
            discardRetainedAudio()
            _state.value = UiState.Idle
            return
        }
        // Claim the interrupted audio as the carry-over and clear the offer/marker, then record on top.
        retained = null
        carryOverAudio = r.file
        carryOverSeconds = r.seconds
        scope.launch { clearInterruptedAudioPref() }
        _state.value = UiState.Idle
        livePromptArmed = r.wasLive
        // coerceAtLeast(1) keeps the "continuation" path (non-zero seed) so the carry-over is not dropped
        // by the normal-start cleanup, even for a sub-second carried-over segment.
        startRecording(context, seedAccumulatedMs = (r.seconds * 1000L).coerceAtLeast(1L))
    }

    /** Deletes the carry-over recording segment (if any) and forgets it. */
    private fun discardCarryOver() {
        carryOverAudio?.takeIf { it.exists() }?.delete()
        carryOverAudio = null
        carryOverSeconds = 0L
    }

    // --- Interrupted recording (keyboard closed mid-recording) ----------------------------------

    /** Stable on-disk location for an interrupted recording: in filesDir so it survives the cache wipe. */
    private fun interruptedAudioFile(context: Context): File =
        File(context.applicationContext.filesDir, "dictate_interrupted.wav")

    /**
     * Called when the keyboard window is hidden (see [FlorisImeService.onWindowHidden]). If a recording
     * is in progress it is *finalized and kept* instead of discarded: the audio is stopped cleanly (so
     * the WAV is valid even if the recorder/process is destroyed afterwards) and moved to [interruptedAudioFile],
     * with its metadata mirrored to prefs. The next keyboard open then offers to send it (see
     * [maybeOfferInterruptedRecording]). Outside the recording state this falls back to the normal
     * teardown ([cancelRecording]).
     */
    /**
     * Ends everything, now, from anywhere.
     *
     * Reachable from the notification's Stop action and from the X beside the record button. A
     * recording that keeps running while out of sight needs a way to be ended that does not involve
     * finding the keyboard again, and a way out if it ever gets stuck.
     */
    /**
     * Ends the recording now and throws the audio away.
     *
     * What the red trash button does. Distinct from [cancelOrDiscardSegment], which in long-form
     * drops only the segment being recorded and keeps going: this stops everything, whatever mode is
     * running and whatever state the UI thinks it is in, and nothing is transcribed or kept.
     *
     * Also the way out when something is stuck, which is why it releases the microphone first rather
     * than relying on the normal teardown to get there.
     */
    /** Bytes of audio captured so far, or 0 when nothing is recording. For the size readout. */
    /**
     * Forces the case of a finished transcript and leaves exactly one trailing space.
     *
     * A recogniser returns sentence case ending in a full stop, because it assumes prose. Dictating
     * into a search box, a filename or a command line makes that assumption wrong every time, and
     * undoing it by hand costs more than the dictation saved.
     *
     * The trailing space is unconditional: the next dictation then starts a word rather than gluing
     * itself onto the last one, which is the single most common annoyance when speaking in bursts.
     */
    /**
     * Case and trailing punctuation, decided by how much was said.
     *
     * A single sentence is almost never prose. It is a search box, a filename, a name, a reply, an
     * instruction, and a recogniser trained on prose hands all of those back capitalised with a full
     * stop that then has to be deleted by hand. Two sentences or more is writing, and keeps what it
     * was given.
     *
     * So the rule is sentence count, which the recogniser has already decided by where it put
     * the full stops. One sentence comes back lowercase without the invented full stop; two or more
     * are prose and are left exactly as written.
     *
     * An explicit setting of "lower" or "upper" still wins outright, because someone who asked for
     * every dictation in one case meant it whatever the length.
     */
    private fun maApplyCase(text: String): String {
        val mode = prefs.dictate.maTextCase.get()
        // An explicit case wins outright, for text arriving as well as text already there, and the
        // transformation is the one the buttons use so the two can never disagree.
        if (mode == MaCase.LOWER || mode == MaCase.UPPER ||
            mode == MaCase.SENTENCE || mode == MaCase.TITLE
        ) {
            val forced = MaCase.transform(text, mode)
            // Only the flat cases drop the recogniser's full stop. Sentence and title case are about
            // writing prose properly, and prose keeps its punctuation.
            return if (mode == MaCase.LOWER || mode == MaCase.UPPER) {
                forced.trimEnd().trimEnd('.', ',', '!', '?', ';', ':') + " "
            } else {
                forced
            }
        }
        if (mode != MaCase.NONE && mode != MaCase.AUTO) return text
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text
        // One sentence or several: that is the whole test, and it is one the recogniser answers for
        // us. A sentence terminator anywhere but the very end means it decided there was more than
        // one thought here, and prose keeps the capitals and punctuation it was given.
        //
        // Word count was the wrong measure. A single spoken instruction can run to a dozen words and
        // is still one fragment dropped into a field; two words can be two sentences.
        val inner = trimmed.dropLast(1)
        if (inner.any { it == '.' || it == '!' || it == '?' || it == '\n' }) return text
        return trimmed.lowercase().trimEnd('.', ',', '!', '?', ';', ':') + " "
    }

    /**
     * Rewrites the text already in the field into [mode].
     *
     * Works on the selection when there is one and the whole field otherwise, which is the same rule
     * the live prompt follows, so the two behave alike. Selecting the field before committing turns
     * the insert into a replacement rather than an append.
     *
     * Silent when there is nothing to change: pressing a case button in an empty field should set
     * the rule for what comes next and do nothing visible, not report an error.
     */
    fun recaseField(context: Context, mode: String) {
        val appContext = context.applicationContext
        maAppContext = appContext
        scope.launch {
            runCatching {
                val sink = sink(appContext)
                val selected = sink.selectedText().takeIf { it.isNotBlank() }
                val whole = if (selected == null) sink.fullText().takeIf { it.isNotBlank() } else null
                val subject = selected ?: whole ?: return@runCatching
                val recased = MaCase.transform(subject, mode)
                if (recased == subject) return@runCatching
                if (whole != null) sink.selectAll()
                sink.commitText(recased)
            }
        }
    }

    fun currentRecordingBytes(): Long = recorder?.bytesWritten ?: 0L

    fun discardRecording(context: Context) {
        maReleaseMic()
        runCatching { recorder?.stop() }
        cancelRecording()
    }

    fun forceStop(context: Context) {
        maReleaseMic()
        if (_state.value is UiState.Recording) {
            stopAndTranscribe(context)
        } else {
            cancelRecording()
        }
    }

    fun stashRecordingOnHide(context: Context) {
        // The keyboard going away is no longer the end of a recording. While the microphone
        // foreground service holds the mic, the audio keeps arriving whatever is on screen, so
        // switching apps mid-sentence simply does not interrupt anything and there is nothing here
        // to salvage.
        //
        // This still runs when the mic is not held: on the screen-off path, and on anything that
        // tore the service down. Then the old behaviour is exactly right and the audio so far is
        // finalised and kept rather than dropped.
        if (_state.value is UiState.Recording && MaRecordingService.isHoldingMic) return
        val current = _state.value
        val activeRecorder = recorder
        if (current !is UiState.Recording || activeRecorder == null) {
            // Not actively recording (e.g. a start that never got going): use the normal teardown.
            cancelRecording()
            return
        }
        recorder = null
        _livePromptActive.value = false
        // Realtime (#128): drop the stream; the WAV is stashed below and recoverable via batch as usual.
        realtimeCancelled = true
        realtimeSession?.cancel()
        realtimeSession = null
        realtimeClosed = null
        _interimText.value = ""
        realtimeContext?.let { ctx -> runCatching { sink(ctx).clearDictationPreview(realtimeShown.toString()) } }
        realtimeShown.setLength(0)
        realtimeContext = null
        unregisterScreenOffReceiver()
        val seconds = recordedSecondsOf(current)
        val wasLive = livePromptArmed
        val audioFile = activeRecorder.stop()
        // Into the ring of ten before anything else decides this audio's fate. Upstream keeps exactly
        // one interrupted file at a fixed path, so a second interruption overwrote the first and it
        // was gone. Everything below may still discard or move the file; this copy survives either way.
        MaRecordingBuffer.add(context, audioFile, seconds)
        cleanupAudioRouting()
        livePromptArmed = false
        _pendingPrompts.value = emptyList()
        _state.value = UiState.Idle

        // Interrupted-recording recovery is disabled while instant recording is on (issue #120): every
        // keyboard open auto-starts a recording, so a stashed segment would only block the next open.
        // Discard the finalized audio instead of keeping it (the user is told about this trade-off when
        // enabling instant recording, and the whole feature only applies with instant recording off).
        if (prefs.dictate.instantRecording.get()) {
            audioFile?.takeIf { it.exists() }?.delete()
            discardCarryOver()
            scope.launch { clearInterruptedAudioPref() }
            return
        }

        val carry = carryOverAudio
        carryOverAudio = null
        val dest = interruptedAudioFile(context)
        val newValid = audioFile != null && audioFile.exists() && audioFile.length() > 0L
        // Resolve the audio to keep (and its length). dest == carry for a continuation, so when both
        // segments are present they are merged into a cache temp first and then moved onto dest.
        val keptSeconds: Long? = when {
            carry != null && newValid -> {
                val merged = File(context.applicationContext.cacheDir, MERGED_AUDIO_NAME)
                val ok = AudioConcat.concat(listOf(carry, audioFile!!), merged)
                if (ok && merged.exists() && merged.length() > 0L) {
                    carry.delete()
                    audioFile.delete()
                    runCatching {
                        dest.delete()
                        if (!merged.renameTo(dest)) {
                            merged.copyTo(dest, overwrite = true)
                            merged.delete()
                        }
                    }
                    if (dest.exists() && dest.length() > 0L) seconds else null
                } else {
                    // Merge failed (rare): keep the carry-over (already at dest), drop the new segment.
                    merged.delete()
                    audioFile.delete()
                    if (carry.exists() && carry.length() > 0L) carryOverSeconds else null
                }
            }
            // Continuation, but the new segment is unusable: keep the carried-over segment alone.
            carry != null -> if (carry.exists() && carry.length() > 0L) carryOverSeconds else null
            // Plain recording interrupted: move the finalized segment out of the cache into filesDir.
            newValid -> {
                runCatching {
                    dest.parentFile?.mkdirs()
                    dest.delete()
                    if (!audioFile!!.renameTo(dest)) {
                        audioFile.copyTo(dest, overwrite = true)
                        audioFile.delete()
                    }
                }
                if (dest.exists() && dest.length() > 0L) seconds else null
            }
            else -> null
        }
        carryOverSeconds = 0L
        if (keptSeconds == null) {
            // Nothing usable was kept; make sure no stale offer remains.
            scope.launch { clearInterruptedAudioPref() }
            return
        }
        // Persist the marker + metadata so the offer can be restored even after a process death.
        scope.launch {
            prefs.dictate.interruptedAudioSeconds.set(keptSeconds)
            prefs.dictate.interruptedAudioLive.set(wasLive)
            prefs.dictate.interruptedAudioPending.set(true)
        }
    }

    /**
     * On keyboard open, restores the "recording interrupted — send it?" offer if an interrupted audio
     * file is waiting. Returns true when the offer is now shown, so the caller can skip instant-recording.
     * No-op unless idle. A stale marker without a usable file is cleared.
     */
    fun maybeOfferInterruptedRecording(context: Context): Boolean {
        if (_state.value !is UiState.Idle) return false
        if (!prefs.dictate.interruptedAudioPending.get()) return false
        val file = interruptedAudioFile(context)
        if (!file.exists() || file.length() == 0L) {
            scope.launch { clearInterruptedAudioPref() }
            return false
        }
        val seconds = prefs.dictate.interruptedAudioSeconds.get()
        retained = RetainedAudio(file, RetainReason.INTERRUPTED, prefs.dictate.interruptedAudioLive.get(), seconds)
        _state.value = UiState.Interrupted(seconds)
        return true
    }

    /** Clears the persisted interrupted-audio marker (best-effort; the file itself is handled separately). */
    private suspend fun clearInterruptedAudioPref() {
        if (prefs.dictate.interruptedAudioPending.get()) {
            prefs.dictate.interruptedAudioPending.set(false)
        }
    }

    // --- Re-insert last dictation (issue #111) --------------------------------------------------

    /**
     * Persists [text] as the last successful dictation so the "Re-insert last dictation" Smartbar action
     * can recover it after the field is cleared (rotation, context switch, host app refreshing its
     * state). No-op when the feature is off or the text is blank. Held until the next successful
     * dictation overwrites it; stored to a pref so it survives the IME process being killed.
     */
    private suspend fun rememberLastDictation(text: String) {
        if (!prefs.dictate.rememberLastDictation.get() || text.isBlank()) return
        prefs.dictate.lastDictation.set(text)
    }

    /**
     * Whether a re-insertable last dictation exists (feature enabled and a non-empty cache). Read
     * synchronously by the Smartbar action's enabled-state evaluation, so the button greys out when
     * there is nothing to re-insert.
     */
    fun hasLastDictation(): Boolean =
        prefs.dictate.rememberLastDictation.get() && prefs.dictate.lastDictation.get().isNotEmpty()

    /** Whether the history feature is enabled — gates the history Smartbar button's enabled state (#140). */
    fun isHistoryEnabled(): Boolean = prefs.dictate.historyEnabled.get()

    /**
     * Re-inserts the last successful dictation into the focused field (issue #111). The cached text is
     * committed verbatim (no auto-formatting/auto-enter, which already ran on the original) and is kept,
     * so it can be re-inserted repeatedly until the next dictation replaces it. No-op while a
     * recording/transcription/rewording is in flight, or when there is nothing cached.
     */
    fun reinsertLastDictation(context: Context) {
        if (_state.value is UiState.Recording || _state.value is UiState.Transcribing ||
            _state.value is UiState.Rewording
        ) return
        if (!prefs.dictate.rememberLastDictation.get()) return
        val text = prefs.dictate.lastDictation.get()
        if (text.isEmpty()) return
        sink(context).commitText(text)
        clearError()
    }

    /**
     * Undo (issue #133): removes the last successful dictation from the focused field again — the
     * inverse of [reinsertLastDictation]. Used by the floating button's optional undo control, so it
     * outputs through the overlay sink. No-op while a recording/transcription/rewording is in flight,
     * or when nothing is cached. On success the cache is cleared so a second tap can't delete unrelated
     * text. Returns true when the field accepted the removal.
     */
    fun undoLastDictation(context: Context): Boolean {
        if (_state.value is UiState.Recording || _state.value is UiState.Transcribing ||
            _state.value is UiState.Rewording
        ) return false
        if (!prefs.dictate.rememberLastDictation.get()) return false
        val text = prefs.dictate.lastDictation.get()
        if (text.isEmpty()) return false
        outputTarget = OutputTarget.OVERLAY
        if (!sink(context).deleteLastText(text)) return false
        scope.launch { prefs.dictate.lastDictation.set("") }
        clearError()
        return true
    }

    // --- Transcription history (issue #140) -----------------------------------------------------

    /**
     * Logs a finished dictation to the history store, honoring the opt-in and the privacy gate. Called
     * from [finalizeAndCommit] with the final committed text and the threaded [capture] metadata. A replay
     * with a known entry id overwrites that entry's text in place; otherwise a new row is inserted (and the
     * WAV copied in when audio retention is on). No-op when history is off, the field is sensitive, or
     * there is no capture context (e.g. a plain re-insert).
     */
    private suspend fun recordHistory(
        appContext: Context,
        text: String,
        originalText: String,
        recordedSeconds: Long,
        capture: HistoryCapture?,
        reworded: Boolean,
        title: String = "",
    ) {
        if (capture == null || text.isBlank()) return
        if (!prefs.dictate.historyEnabled.get()) return
        if (isSensitiveDictationField(appContext)) return
        capture.replayHistoryId?.let { id ->
            DictateHistoryStore.updateText(appContext, id, text, originalText)
            return
        }
        DictateHistoryStore.record(
            context = appContext,
            prefs = prefs,
            text = text,
            originalText = originalText,
            providerId = capture.providerId,
            providerName = capture.providerName,
            model = capture.model,
            language = capture.language,
            durationSecs = recordedSeconds,
            source = capture.source,
            reworded = reworded,
            audioFile = capture.audioFile,
            // Read back from prefs rather than threaded through HistoryCapture: the realtime path
            // finishes here too and never went through the upload timer, so it correctly records
            // whatever the last measured send was rather than inventing a number.
            sendMs = prefs.dictate.maLastSendMs.get(),
            sendFormat = prefs.dictate.maLastSendFormat.get(),
            title = title,
        )
    }

    /**
     * Logs a *failed* dictation to the history (issue #140 safety net) so its audio can be recovered and
     * re-transcribed later — the resend chip is transient (it disappears; see issue #114). Only when
     * history + audio retention are on (a failure with no kept audio is not recoverable), and never in a
     * sensitive field. The entry carries a placeholder text and `failed=true`.
     */
    private suspend fun recordFailedHistory(
        appContext: Context,
        audioFile: File,
        providerId: String,
        providerName: String,
        model: String,
        language: String,
        recordedSeconds: Long,
        source: String,
    ) {
        // Gated only on the master history switch: a failed dictation has no text, so its audio is the ONLY
        // recovery path — we keep it even when "keep audio" (which governs successful dictations) is off.
        if (!prefs.dictate.historyEnabled.get()) return
        if (isSensitiveDictationField(appContext)) return
        if (!audioFile.exists() || audioFile.length() == 0L) return
        DictateHistoryStore.record(
            context = appContext,
            prefs = prefs,
            text = appContext.getString(R.string.dictate__history_failed),
            providerId = providerId,
            providerName = providerName,
            model = model,
            language = language,
            durationSecs = recordedSeconds,
            source = source,
            reworded = false,
            audioFile = audioFile,
            failed = true,
            forceAudio = true,
        )
    }

    /** Exports a history entry's retained audio to Downloads/Dictate (issue #140), toasting the result. */
    fun exportHistoryAudio(context: Context, entry: DictateHistoryEntry) {
        val path = entry.audioPath ?: return
        val src = File(path)
        if (!src.exists() || src.length() == 0L) return
        val appContext = context.applicationContext
        scope.launch {
            val savedName = withContext(Dispatchers.IO) { exportAudioToDownloads(appContext, src) }
            val message = if (savedName != null) {
                appContext.getString(R.string.dictate__audio_saved, savedName)
            } else {
                appContext.getString(R.string.dictate__audio_save_failed)
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * True when the active in-keyboard field is a password field or in incognito mode, so a dictation into
     * it must not be logged. Only meaningful for the IME target — the floating button injects into
     * arbitrary apps via accessibility with no reliable field-sensitivity signal, so it is never gated here.
     */
    private fun isSensitiveDictationField(context: Context): Boolean {
        if (outputTarget != OutputTarget.IME) return false
        return runCatching {
            val state = context.keyboardManager().value.activeState
            state.isIncognitoMode || state.keyVariation == KeyVariation.PASSWORD
        }.getOrDefault(false)
    }

    /**
     * Commits a stored history entry's text into the focused field (issue #140), verbatim like
     * [reinsertLastDictation] — no auto-formatting/auto-enter (those already ran). Used by the in-keyboard
     * history panel's per-row insert. No-op while a recording/transcription/rewording is in flight.
     */
    fun insertHistoryText(context: Context, text: String) {
        if (_state.value is UiState.Recording || _state.value is UiState.Transcribing ||
            _state.value is UiState.Rewording
        ) return
        if (text.isEmpty()) return
        outputTarget = OutputTarget.IME
        sink(context).commitText(text)
        clearError()
    }

    /**
     * Re-transcribes a stored entry's retained audio (issue #140) and commits the fresh result into the
     * field, then overwrites the entry's text in place. The history-owned WAV is copied to a cache temp
     * first so the shared transcribe path's finally-delete can't remove it. Marked as a replay so stats
     * aren't double-counted. No-op when the entry has no retained audio or a dictation is in flight.
     */
    fun retranscribeHistoryEntry(context: Context, entry: DictateHistoryEntry) {
        if (_state.value is UiState.Recording || _state.value is UiState.Transcribing ||
            _state.value is UiState.Rewording
        ) return
        val path = entry.audioPath ?: return
        val src = File(path)
        if (!src.exists() || src.length() == 0L) return
        val temp = File(context.cacheDir, "dictate_history_replay.wav")
        runCatching { src.copyTo(temp, overwrite = true) }.getOrElse { return }
        outputTarget = OutputTarget.IME
        clearError()
        // A failed entry's first successful re-transcribe SHOULD count stats (it was never counted); an
        // already-successful entry's re-transcribe must not double-count → isReplay only when not failed.
        transcribe(
            context, temp, entry.durationSecs, gate = false,
            isReplay = !entry.failed, source = entry.source, replayHistoryId = entry.id,
        )
    }

    // --- Rate / Donate nudges (roadmap 9.7/9.8) -------------------------------------------------

    /**
     * Shows a one-time rate or donate nudge in the Smartbar once the user has accumulated enough
     * transcribed audio, mirroring the legacy app: rate after [RATE_THRESHOLD_SECONDS], donate after
     * [DONATE_THRESHOLD_SECONDS]. Each is shown until acted on (a flag is then set); accepting or
     * declining donate also marks rate as done, so a donor is never asked to rate. No-op unless idle.
     * Called when the keyboard appears so it never interrupts an in-flight recording/transcription.
     */
    fun maybePromptForReview() {
        // Nothing. This is a fork; the upstream author's rate and donate nudges are not ours to
        // show, and asking for money on his behalf from a build he does not maintain would be
        // wrong even if it were wanted. Nothing raises a promo any more.
    }

    /**
     * If a saved-time / dictation-count milestone is pending (issue #142), shows it as a one-time Smartbar
     * celebration and returns true (consuming the pending marker). Returns false — leaving anything pending
     * intact — when idle-state is not held or nothing is pending / celebrations are off.
     */
    private suspend fun showMilestoneNudge(context: Context): Boolean {
        // Milestone celebrations are gone with the rest of the banners. The pending marker is still
        // consumed so it cannot pile up and fire later if this ever comes back.
        return false
    }

    /** Short, single-line celebration text for the milestone nudge (kept compact for the Smartbar). */

    /**
     * Shows a one-time "Dictate was updated" nudge in the Smartbar right after an app update, so users
     * who rarely open the settings still learn about new versions and can jump straight to the changelog.
     * Tapping it opens the app, where the "What's new" dialog appears (it shares the same
     * [AppVersionUtils.shouldShowChangelog] gate). A dedicated per-version flag
     * ([dev.patrickgold.florisboard.app.AppPrefs.Dictate.changelogNudgeVersion]) keeps the keyboard nudge
     * from reappearing without suppressing the in-app dialog, and vice versa. No-op unless idle.
     */
    fun maybePromptChangelog(context: Context) {
        // Also gone. An update banner is still a banner, and the only pop-up worth interrupting
        // someone for is one that says something has actually gone wrong.
    }

    /**
     * Shows a one-time Smartbar spotlight for the floating dictation button to users who have not enabled
     * it yet, so existing users discover the feature. Tapping it deep-links straight to the floating-button
     * settings screen (where the accessibility opt-in + disclosure live — it is never auto-enabled).
     * Gated by a per-version flag; skipped once the user has enabled it or opened its screen. No-op unless idle.
     */
    fun maybePromptFloatingButton(context: Context) {
        // The floating bubble was removed from this build; there is nothing to advertise.
    }

    /**
     * Acts on the active promo and marks it done: RATE/DONATE open the Play Store / PayPal page,
     * CHANGELOG opens the app (which then shows the "What's new" dialog), FLOATING_BUTTON deep-links to its
     * settings screen. No-op otherwise.
     */
    fun acceptPromo(context: Context) {
        val kind = (_state.value as? UiState.Promo)?.kind ?: return
        runCatching {
            val intent = when (kind) {
                PromoKind.RATE -> Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/markoboskoauroville/TTT/releases"))
                PromoKind.DONATE -> Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/DevEmperor"))
                PromoKind.CHANGELOG -> Intent(context, FlorisAppActivity::class.java)
                PromoKind.FLOATING_BUTTON -> Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("ui://florisboard/settings/dictate/floating-button"),
                    context,
                    FlorisAppActivity::class.java,
                ).addCategory(Intent.CATEGORY_BROWSABLE)
                PromoKind.MILESTONE -> Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("ui://florisboard/settings/dictate/stats"),
                    context,
                    FlorisAppActivity::class.java,
                ).addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        markPromoDone(kind)
        _state.value = UiState.Idle
    }

    /** Dismisses the active promo without opening anything, but still marks it done. No-op otherwise. */
    fun declinePromo() {
        val kind = (_state.value as? UiState.Promo)?.kind ?: return
        markPromoDone(kind)
        _state.value = UiState.Idle
    }

    /** Persists the "handled" flags: declining/accepting donate also marks rate as done. */
    private fun markPromoDone(kind: PromoKind) {
        scope.launch {
            when (kind) {
                PromoKind.RATE -> prefs.dictate.hasRated.set(true)
                PromoKind.DONATE -> {
                    prefs.dictate.hasDonated.set(true)
                    prefs.dictate.hasRated.set(true)
                }
                // Remember this version so the keyboard nudge shows only once per update. The in-app
                // dialog stays governed by versionLastChangelog, so tapping/dismissing here never hides it.
                PromoKind.CHANGELOG -> prefs.dictate.changelogNudgeVersion.set(BuildConfig.VERSION_NAME)
                // Show the floating-button spotlight only once per version.
                PromoKind.FLOATING_BUTTON -> prefs.dictate.floatingButtonSpotlightVersion.set(BuildConfig.VERSION_NAME)
                // The milestone was already consumed when shown; nothing further to persist.
                PromoKind.MILESTONE -> Unit
            }
        }
    }

    /**
     * Adds [seconds] to the cumulative audio counter that gates the nudges. Suspends until the new value
     * is written to the in-memory cache, so a [maybePromptForReview] called right after sees the update.
     */
    private suspend fun creditAudioSeconds(seconds: Long) {
        prefs.dictate.totalAudioSeconds.set(prefs.dictate.totalAudioSeconds.get() + seconds)
    }

    // --- Rewording / GPT engine (roadmap section 4) ---------------------------------------------

    /**
     * Applies a rewording [prompt] and commits the result. Used by the prompt chips (Phase 3):
     *  - a `[snippet]` prompt (text wrapped in brackets) is inserted literally, no API call;
     *  - a `requiresSelection` prompt operates on [selectionOverride] (or the current selection) and
     *    replaces it with the reworded result;
     *  - a free prompt generates from the instruction alone and inserts at the cursor.
     * No-op unless idle (or recovering from a transient error).
     */
    /**
     * Runs an instruction that was spoken before, against whatever is in the field now.
     *
     * The recalled path skips the microphone entirely and joins the live-prompt flow at the point
     * where the words already exist, so a re-run behaves exactly like speaking the same sentence
     * again: selection first if there is one, otherwise the whole field, and the result replaces
     * rather than appends.
     */
    fun applyRememberedPrompt(context: Context, instruction: String) {
        if (_state.value !is UiState.Idle && _state.value !is UiState.Error) return
        val appContext = context.applicationContext
        maAppContext = appContext
        scope.launch {
            runCatching {
                finalizeAndCommit(
                    appContext = appContext,
                    rawText = instruction,
                    recordedSeconds = 0L,
                    live = true,
                    alreadyFormatted = false,
                )
            }.onFailure {
                _state.value = UiState.Error(
                    message = appContext.getString(R.string.dictate__error_unknown),
                    action = ErrorAction.NONE,
                )
            }
        }
    }

    fun applyPrompt(
        context: Context,
        prompt: PromptModel,
        selectionOverride: String? = null,
        target: OutputTarget? = null,
        /** The model for this prompt (Settings → Prompts), or null for the rewording account's own. */
        model: String? = null,
    ) {
        if (_state.value !is UiState.Idle && _state.value !is UiState.Error) return
        // The floating overlay passes OVERLAY so the result is injected into the focused field via the
        // accessibility sink rather than the keyboard's editor.
        if (target != null) outputTarget = target
        val appContext = context.applicationContext
        val sink = sink(appContext)
        val raw = prompt.prompt.orEmpty()

        // Snippet shortcut: text wrapped in [...] is inserted literally (no network call).
        if (raw.length >= 2 && raw.startsWith("[") && raw.endsWith("]")) {
            sink.commitText(raw.substring(1, raw.length - 1))
            return
        }

        val input: String? = when {
            selectionOverride != null -> selectionOverride
            !prompt.requiresSelection -> null
            else -> {
                val selected = sink.selectedText()
                if (selected.isNotEmpty()) {
                    selected
                } else {
                    // Nothing selected: select the whole field (so the user sees what gets reworded)
                    // and operate on its full text. The reworded result then replaces the now-selected
                    // content via commitText. Matches the "tap a prompt with no selection" flow.
                    val whole = sink.fullText()
                    if (whole.isBlank()) return // empty field – nothing to operate on
                    sink.selectAll()
                    whole
                }
            }
        }

        if (prompt.requiresSelection && input.isNullOrBlank()) {
            // The first failure in his screenshots: the model replied "I don't see any text to
            // edit", which is what it says when it is handed an instruction and nothing else.
            //
            // The whole-field branch below already refuses a blank field, but the selection branch
            // did not — a selection can be reported as present and come back empty when the field
            // is one the accessibility path cannot read. Guarded here so both routes end the same
            // way: say so, spend nothing, and leave the field alone.
            _state.value = UiState.Error(
                message = "Nothing to correct — no text in this field.",
                kind = DictateApiException.Kind.UNKNOWN,
            )
            return
        }

        if (MaProviders.rewordingApiKey().isBlank()) {
            _state.value = UiState.Error(
                message = appContext.getString(R.string.dictate__error_no_api_key),
                kind = DictateApiException.Kind.INVALID_API_KEY,
                action = ErrorAction.OPEN_SETTINGS,
            )
            return
        }

        // What he corrected by hand is learned now; what the prompt is about to change is not his.
        settleCorrections(appContext)
        MaDictationCorrections.drop()
        _state.value = UiState.Rewording(prompt.name ?: appContext.getString(R.string.dictate__status_rewording))
        // Store the job so the stop button can abort this reword mid-generation (issue #192).
        rewordJob = scope.launch {
            try {
                val text = requestReword(
                    raw, input, prompt.reasoningEffort, prompt.reasoningEffortCustom,
                    model = model, purpose = prompt.name ?: "Prompt",
                )
                // A reply that is not his text back does not go into his text.
                //
                // Ctrl+P and Ctrl+F replace the field with whatever returns, and a model that
                // answers conversationally instead of obeying — "I need you to provide the text
                // you'd like me to rewrite", "I've reviewed the text you provided" — had that
                // pasted straight into the message he was writing to somebody. The instruction
                // forbids preamble and the model ignored it, which is a thing models do and no
                // amount of rewording the prompt makes impossible.
                //
                // The tell is length. Proofreading and reflowing return roughly what they were
                // given: shorter, or a little different, never several times longer. Commentary
                // about a short phrase is always far longer than the phrase. So a reply that has
                // grown out of all proportion is refused and shown instead of written, and his
                // words are left exactly as they were.
                //
                // Generous on purpose — three times plus eighty characters. A real correction never
                // comes close to that, and the cost of guessing wrong in this direction is one
                // visible message rather than a ruined one already sent.
                val grew = text.length > input.orEmpty().length * 3 + 80
                if (grew && !input.isNullOrBlank()) {
                    MaLog.add("reword", "refused a reply of ${text.length} chars for ${input.length} in")
                    _state.value = UiState.Error(
                        message = "The model answered instead of correcting. Your text is unchanged.",
                        kind = DictateApiException.Kind.UNKNOWN,
                        detail = text.take(300),
                    )
                    return@launch
                }
                // commitText replaces the active selection if any, else inserts at the cursor.
                sink.commitText(text)
                _state.value = UiState.Idle
            } catch (e: CancellationException) {
                throw e // stop button pressed: cancelRewording already reset the state, leave the field as-is
            } catch (e: DictateApiException) {
                _state.value = apiError(e, appContext, canResend = false)
            } catch (t: Throwable) {
                _state.value = UiState.Error(
                    message = appContext.getString(R.string.dictate__error_rewording_failed),
                    kind = DictateApiException.Kind.UNKNOWN,
                    detail = t.message?.takeIf { it.isNotBlank() },
                )
            } finally {
                rewordJob = null
            }
        }
    }

    /**
     * Starts (or stops) a *live prompt* recording: the spoken transcript is sent to the rewording
     * model as an instruction instead of being inserted verbatim. Toggles like the mic button.
     */
    fun startLivePrompt(context: Context, target: OutputTarget = OutputTarget.IME) {
        when (_state.value) {
            is UiState.Recording -> {
                livePromptArmed = true
                stopAndTranscribe(context)
            }
            is UiState.Transcribing, is UiState.Rewording -> Unit
            else -> {
                // Latch where the reworded result goes — the keyboard editor, or the accessibility-injected
                // field for the floating button's freeform voice command (issue #230). Same as onMicClick.
                outputTarget = target
                livePromptArmed = true
                startRecording(context)
            }
        }
    }

    /**
     * The Croatian spell check, or [text] unchanged when it does not apply or does not succeed.
     *
     * Runs only for a Croatian recording, only when switched on, only with a rewording key. Its own
     * switch, not the rewording switch: he called it mandatory, and it must not vanish because
     * auto-formatting was turned off. A failure or a reply that fails [MaSpellcheck.accept] keeps the
     * transcript as it came — a dictation is never lost to its own correction.
     */
    private suspend fun croatianSpellcheck(context: Context, text: String): String {
        if (text.isBlank() || !MaSpellcheck.enabled()) return text
        if (MaLanguage.active() != MaLanguage.HR) return text
        val model = MaSpellcheck.model()
        if (!MaPhoneModels.isPhone(model) && MaProviders.rewordingApiKey().isBlank()) return text
        _state.value = UiState.Rewording(MaSpellcheck.NAME)
        val corrected = runCatching {
            requestReword(MaSpellcheck.instruction(), text, model = model, purpose = MaSpellcheck.NAME)
        }.getOrElse {
            MaLog.add("dictate", "spell check failed: ${it.javaClass.simpleName}")
            return text
        }
        return if (MaSpellcheck.accept(text, corrected)) corrected else {
            MaLog.add("dictate", "spell check reply refused: ${corrected.length} chars for ${text.length}")
            text
        }
    }

    /**
     * [text], dictated in English, as his Croatian message (MaTranslate), or null when it cannot run
     * or the reply is not a translation. Null keeps the English, so a dictation is never lost; the
     * message says so, because English landing in a Croatian chat is a surprise worth naming.
     */
    private suspend fun translateToCroatian(text: String): String? {
        if (text.isBlank()) return null
        val model = MaTranslate.model()
        if (!MaPhoneModels.isPhone(model) && MaProviders.rewordingApiKey().isBlank()) {
            MaMessage.show("E>H needs an Anthropic key: the English was written instead")
            return null
        }
        _state.value = UiState.Rewording(MaTranslate.NAME)
        val translated = runCatching {
            requestReword(MaTranslate.instruction(), MaPhoneStyle.correct(text), model = model, purpose = MaTranslate.NAME, answerLanguage = "Croatian")
        }.getOrElse {
            if (it is CancellationException) throw it
            MaLog.add("dictate", "E>H failed: ${it.message ?: it.javaClass.simpleName}")
            MaMessage.show("E>H failed: the English was written instead")
            return null
        }
        return if (MaTranslate.accept(text, translated)) MaPhoneStyle.correct(translated.trim()) else {
            MaLog.add("dictate", "E>H reply refused: ${translated.length} chars for ${text.length}")
            MaMessage.show("E>H answer refused: the English was written instead")
            null
        }
    }

    /**
     * The Mac's style pass on [text] (MaPhoneStyle), or null when it is off, cannot run, or fails.
     *
     * Null rather than [text] so the caller can tell "styled" from "not styled" and fall back to the
     * Croatian spell check. Cannot run means: the model on the phone is not downloaded, or a Claude
     * model was chosen and there is no key.
     */
    private suspend fun phoneStyle(text: String): String? {
        if (text.isBlank() || !MaPhoneStyle.enabled() || !MaPhoneStyle.worthStyling(text)) return null
        val model = MaPhoneStyle.model()
        val phone = MaPhoneModels.byChoice(model)
        val context = maAppContext
        if (phone != null) {
            if (context == null || !MaPhoneModels.isInstalled(context, phone)) return null
            if (!MaPhoneStyle.fitsPhone(text)) {
                MaLog.add("dictate", "style pass skipped: longer than ${MaPhoneStyle.PHONE_MAX_WORDS} words")
                return null
            }
        } else if (MaProviders.rewordingApiKey().isBlank()) {
            return null
        }
        _state.value = UiState.Rewording(if (phone != null) "${MaPhoneStyle.NAME} \u00B7 ${phone.name}" else MaPhoneStyle.NAME)
        val input = MaPhoneStyle.correct(text)
        val styled = runCatching {
            MaPhoneStyle.correct(requestReword(MaPhoneStyle.instruction(), input, model = model, purpose = MaPhoneStyle.NAME))
        }.getOrElse {
            if (it is CancellationException) throw it
            MaLog.add("dictate", "style pass failed: ${it.message ?: it.javaClass.simpleName}")
            return null
        }
        return if (MaPhoneStyle.accept(text, styled)) styled else {
            MaLog.add("dictate", "style pass reply refused: ${styled.length} chars for ${text.length}")
            null
        }
    }

    /**
     * Runs the post-transcription rewording chain on [transcript]: optional auto-formatting, then the
     * user's auto-apply prompts in order. Each step is best-effort – a failing step keeps the text so
     * far so the user never loses their dictation. Returns the text to commit.
     */
    private suspend fun postProcessTranscript(context: Context, transcript: String): String {
        if (!prefs.dictate.rewordingEnabled.get() || transcript.isBlank()) return transcript
        // No rewording key (not even a shared transcription one) → nothing here can run; return the raw
        // transcript instead of flashing "Formatting…" and looping through doomed throw/catch calls.
        if (MaProviders.rewordingApiKey().isBlank()) return transcript
        var text = transcript

        // 1) Auto-formatting (spoken cues → Markdown). Low-level prompt, no be-precise suffix.
        if (prefs.dictate.autoFormattingEnabled.get()) {
            _state.value = UiState.Rewording(context.getString(R.string.dictate__status_formatting))
            // Hint the model with the readable language name ("German"), or "unknown" for auto-detect.
            val languageName = DictateLanguages.englishNameFor(prefs.dictate.activeInputLanguage.get())
            val formatPrompt = DictatePromptDefaults.buildAutoFormattingPrompt(languageName, text)
            val formatted = runCatching { requestRewordRaw(formatPrompt) }.getOrDefault(text)
            // Safety net (#124): on (near-)empty input the model sometimes echoes the formatting prompt
            // itself instead of returning nothing. If the result looks like that prompt, discard it and keep
            // the original text — the master prompt must never land in the field.
            text = if (formatted.isBlank() || DictatePromptDefaults.looksLikeAutoFormattingPrompt(formatted)) {
                text
            } else {
                formatted
            }
        }

        // 2) Auto-apply prompts, in POS order; each operates on the running text if it needs input.
        val autoApply = withContext(Dispatchers.IO) {
            promptsDb(context).getAll().filter { it.autoApply }
        }
        for (p in autoApply) {
            val instruction = p.prompt.orEmpty()
            if (instruction.isBlank()) continue
            _state.value = UiState.Rewording(p.name ?: context.getString(R.string.dictate__status_rewording))
            text = runCatching {
                requestReword(instruction, if (p.requiresSelection) text else null, p.reasoningEffort, p.reasoningEffortCustom)
            }.getOrDefault(text)
        }
        return text
    }

    /**
     * Applies the prompts the user queued by tapping the always-on prompt row while recording (ROW
     * layout), in tap order, to the finished [text]. Each step is best-effort (a failing prompt keeps
     * the text so far). `[snippet]` prompts are appended literally; everything else runs through the
     * rewording model (operating on the running text when the prompt requires a selection). Clears the
     * queue (so the highlights disappear) regardless of outcome.
     */
    private suspend fun applyPendingPrompts(context: Context, text: String): String {
        val queued = _pendingPrompts.value
        _pendingPrompts.value = emptyList()
        if (queued.isEmpty()) return text
        if (MaProviders.rewordingApiKey().isBlank()) return text
        var result = text
        for (p in queued) {
            val raw = p.prompt.orEmpty()
            if (raw.isBlank()) continue
            // Snippet shortcut: text wrapped in [...] is appended literally (no network call).
            if (raw.length >= 2 && raw.startsWith("[") && raw.endsWith("]")) {
                result += raw.substring(1, raw.length - 1)
                continue
            }
            _state.value = UiState.Rewording(p.name ?: context.getString(R.string.dictate__status_rewording))
            result = runCatching {
                requestReword(raw, if (p.requiresSelection) result else null, p.reasoningEffort, p.reasoningEffortCustom)
            }.getOrDefault(result)
        }
        return result
    }

    /**
     * High-level rewording call: builds the user message as `instruction [+ system prompt] [+ input]`
     * (exactly as the legacy app did – the be-precise prompt is tuned for this position) and returns
     * the trimmed model output.
     */
    private suspend fun requestReword(
        instruction: String,
        input: String?,
        reasoning: DictateReasoningEffort? = null,
        reasoningCustom: String? = null,
        model: String? = null,
        purpose: String = "Rewording",
        /** Set for a translation: the language of the answer, instead of the text's own. */
        answerLanguage: String? = null,
    ): String {
        // THE LANGUAGE RULE GOES LAST, AND IT NAMES THE LANGUAGE.
        //
        // He reported reflow silently TRANSLATING his Croatian into English. The language is
        // detected here from the text and NAMED, immediately before his text, after every
        // English style rule. Applied to every rewording prompt, not only reflow.
        //
        // The whole message is built by MaRewordPrompt, the same function the Prompts screen
        // previews, so what he reads there is what is sent. See there for the be-precise sentence
        // that used to contradict this rule, and for the passage detection that replaced the
        // recording key as the guess.
        if (MaPhoneModels.isPhone(model)) return requestPhoneModel(instruction, input, model.orEmpty(), answerLanguage)
        val content = MaRewordPrompt.forSending(instruction, input, answerLanguage)
        return requestRewordRaw(content, reasoning, reasoningCustom, modelOverride = model, purpose = purpose)
    }

    /**
     * The same request, answered by the model on the phone (§191) instead of Claude: no key, no
     * network, nothing leaves the phone. The message is the one [MaRewordPrompt] builds for Claude,
     * split into its rules and his text, and recorded the same way for the Prompts screen.
     */
    private suspend fun requestPhoneModel(instruction: String, input: String?, model: String, answerLanguage: String? = null): String {
        val m = MaPhoneModels.byChoice(model)
            ?: throw DictateApiException(DictateApiException.Kind.UNKNOWN, "No model on the phone called $model")
        val (rules, user) = if (input.isNullOrBlank()) {
            "" to MaRewordPrompt.forSending(instruction, null)
        } else {
            MaRewordPrompt.forSendingSplit(instruction, input, answerLanguage)
        }
        // The phone model does not stop by itself the way Claude does (phone test A4, build 398): it
        // is told to stop at the end of the rewrite, and given about the text's own length to do it.
        val system = if (input.isNullOrBlank()) rules else "$rules\n\n${MaPhoneAnswer.RULES}".trim()
        val budget = MaPhoneAnswer.maxTokens(input ?: user)
        MaRewordPrompt.recordSent(model, if (system.isBlank()) user else "$system\n\n$user")
        val answer = try {
            MaLocalLlm.complete(m, MaPhoneModels.chatPrompt(m, system, user), budget)
        } catch (e: MaLocalLlm.LocalModelException) {
            throw DictateApiException(DictateApiException.Kind.UNKNOWN, e.message, e)
        }
        return MaPhoneAnswer.fit(MaPhoneModels.clean(answer.text), hitCap = answer.generatedTokens >= budget)
    }

    /**
     * Low-level rewording call: sends [userContent] verbatim as a single user message. [reasoning] is
     * the per-prompt reasoning-effort override (issue #155); null falls back to the global setting.
     */
    /**
     * A short title for a note that had nowhere to go.
     *
     * Only for offline notes. Every other entry was watched arriving in a field and needs no name;
     * paying for a model call on all of them would be spending his money to label things he already
     * knows.
     *
     * **Falls back to the first few words, and never fails the save.** A note without a title is a
     * note; a note lost because the titling call timed out is not. The whole thing is wrapped and a
     * failure returns empty, which the list renders as the first line exactly as before.
     */
    private suspend fun titleFor(text: String): String {
        if (text.length < 40) return ""
        return runCatching {
            requestRewordRaw(
                "Give this a title of at most six words. Reply with the title alone, no quotes, no " +
                    "full stop, in the language the text is written in.\n\n" + text.take(1200),
            ).trim().removeSurrounding("\"").take(60)
        }.getOrElse {
            MaLog.add("dictate", "title failed: ${it.javaClass.simpleName}")
            ""
        }
    }

    /**
     * One cheap question, for the key picker's `(AI)` suggestion.
     *
     * Goes through `requestRewordRaw` rather than opening a second path to the same providers: the
     * key ring, the roll to the next key when one is refused, the proxy and the certificate setting
     * are all already there and all already tested. **A second HTTP path is a second set of bugs.**
     *
     * `cheapest = true` pins the provider preset's default model, which is the mini/flash/haiku one
     * for every provider in the registry. He asked for the cheapest possible and this is the
     * cheapest possible *with the key he already has*, which is better than demanding a particular
     * provider for one suggestion in a settings screen.
     *
     * Returns null rather than throwing. Nothing here is worth an error on a picker: no network, no
     * key, a refusal — all of them mean the same thing to him, which is that the suggestion did not
     * come, and the list he was already looking at is still there.
     */
    suspend fun askCheapModel(prompt: String): String? = runCatching {
        requestRewordRaw(prompt, cheapest = true)
    }.getOrNull()

    /**
     * Word prediction (the AI key, and sorting new words into a language): the model chosen under
     * Settings → Auto prediction, with the Anthropic key loaded in the app. Any other rewording
     * provider still gets its smallest model, as [askCheapModel] does.
     */
    suspend fun askPredictionModel(prompt: String): String? = runCatching {
        requestRewordRaw(
            prompt,
            cheapest = true,
            modelOverride = prefs.dictate.maPredictModel.get().ifBlank { null },
            purpose = "Suggestion",
        )
    }.getOrNull()

    private suspend fun requestRewordRaw(
        userContent: String,
        reasoning: DictateReasoningEffort? = null,
        reasoningCustom: String? = null,
        cheapest: Boolean = false,
        /** A model chosen for this one job; used only when the rewording provider is Anthropic. */
        modelOverride: String? = null,
        /** What the cost log says this request was for. */
        purpose: String = if (cheapest) "Suggestion" else "Rewording",
    ): String {
        val account = MaProviders.rewordingAccount()
        // Blank rewording key falls back to the transcription account's key (legacy "reuse" behavior).
        // Through the ring in both cases, and under the id of whichever account the key came from,
        // so a key borrowed from transcription is flagged where its owner will see it.
        val ringId = if (account.apiKey.isNotBlank()) account.providerId else MaProviders.transcriptionAccount().providerId
        val stored = account.apiKey.ifBlank { MaProviders.transcriptionAccount().apiKey }
        val apiKey = MaKeyRingStore.currentKey(ringId, stored)
        if (apiKey.isBlank() && MaProviders.requiresKey(account)) {
            throw DictateApiException(DictateApiException.Kind.INVALID_API_KEY, "No API key set")
        }
        val preset = MaProviders.presetFor(account)
        // HAIKU IS THE RULE, AND IT IS A HARD ONE.
        //
        // Every language job in this keyboard — proofread, reflow, restyle, and the prediction the
        // local n-gram cannot make — goes to the smallest model the provider has. He set that as a
        // rule rather than a default, and a rule is enforced rather than suggested.
        //
        // The old code trusted `preset.defaultChatModel` to be the cheap one, with a comment saying
        // it is haiku, mini or flash "for every provider in the registry". **That was true when it
        // was written and nothing keeps it true.** A preset edited next year, or a provider that
        // renames its default, silently promotes every rewording to an expensive model — and the
        // only symptom is the bill.
        //
        // So the small model is NAMED, per provider, and the preset default is the fallback rather
        // than the source. Anthropic gets haiku by name.
        // A model chosen for the job wins, for Anthropic only: the choosers offer Claude models.
        val model = if (modelOverride != null && account.providerId == MaRoles.REWORDING) {
            modelOverride
        } else if (cheapest) {
            MaSmallModel.forProvider(account.providerId)
                ?: preset.defaultChatModel
                ?: account.chatModel.ifBlank { "gpt-4o-mini" }
        } else {
            account.chatModel.ifBlank { preset.defaultChatModel ?: "gpt-4o-mini" }
        }
        // Built per key inside the ring walk below, because the key is what it is built with. Kept as
        // a lambda rather than a value so a roll to the next key really does mean a new client.
        val clientFor: (String) -> OpenAiCompatibleClient = { key ->
            OpenAiCompatibleClient.from(
                preset, key,
                baseUrlOverride = MaProviders.baseUrlOverrideFor(account),
                proxy = prefs.dictate.dictateProxyConfig(),
                trustUserCerts = prefs.dictate.trustUserCertificates.get(),
            )
        }
        // Reasoning effort for reasoning models (issue #141); a per-prompt override wins over the global
        // setting (#155). OFF → null → field omitted. CUSTOM (#186) uses a user-entered wire value —
        // the per-prompt one when the override itself is CUSTOM, else the global custom value.
        val effort = reasoning ?: prefs.dictate.rewordingReasoningEffort.get()
        val reasoningWire = if (effort == DictateReasoningEffort.CUSTOM) {
            val custom = if (reasoning == DictateReasoningEffort.CUSTOM) {
                reasoningCustom
            } else {
                prefs.dictate.rewordingReasoningEffortCustom.get()
            }
            custom?.trim()?.ifBlank { null }
        } else {
            effort.wire
        }
        // Rewording gets the same ring as everything else. It used to take whatever was in the key
        // field and fail outright if that key was refused, which on a keyring of ten was a strange
        // thing to do while dictation happily rolled to the next one.
        val ring = MaKeyRingStore.load(ringId)
        // Kept so Settings → Prompts can show the last message exactly as it left, and to whom.
        MaRewordPrompt.recordSent(model, userContent)
        MaClaudeCost.purpose = purpose
        val outcome = try {
            MaKeyRing.run(MaKeyRingStore.keys(ringId, stored).ifEmpty { listOf(apiKey) }, ring) { key ->
                clientFor(key).complete(
                    ChatRequest.ofUser(model, userContent, reasoningEffort = reasoningWire),
                ).text.trim()
            }
        } catch (e: MaKeyRing.NoKeyLeft) {
            MaKeyRingStore.save(ringId, e.ring)
            throw e.last ?: DictateApiException(DictateApiException.Kind.INVALID_API_KEY, "No API key set")
        }
        MaKeyRingStore.save(ringId, outcome.second)
        // Lifetime statistics (issue #142): every rewording/prompt pass funnels through here.
        return outcome.first
    }

    /**
     * Style/punctuation prompt sent with the transcription request (independent of rewording). The
     * user's custom words (roadmap 11.12) are appended on top of whichever style prompt is active, so
     * names/jargon are spelled correctly even with the predefined punctuation prompt or with none.
     */
    private fun transcriptionStylePrompt(): String? =
        DictatePromptDefaults.appendCustomWords(
            transcriptionStyleBasePrompt(),
            // His own words, then the right forms of the corrections he taught (MaDictationCorrections).
            (listOf(prefs.dictate.customWords.get()) + MaDictationCorrections.glossary()).filter { it.isNotBlank() }.joinToString(", "),
        )

    /**
     * The style prompt WITHOUT the appended custom-words glossary — the predefined per-language sentence or
     * the user's custom style prompt. This is the part a Whisper-style model echoes on silence, so the
     * prompt-echo guard (#77) compares against it rather than the full prompt (whose trailing glossary
     * would otherwise throw off the overlap check).
     */
    private fun transcriptionStyleBasePrompt(): String? = when (prefs.dictate.stylePromptSelection.get()) {
        DictatePromptDefaults.SELECTION_PREDEFINED ->
            DictatePromptDefaults.punctuationPromptFor(prefs.dictate.activeInputLanguage.get())
        DictatePromptDefaults.SELECTION_CUSTOM ->
            prefs.dictate.stylePromptCustom.get().takeIf { it.isNotBlank() }
        else -> null
    }

    /**
     * The instruction sent alongside the audio in the single-call multimodal path (issue #130). Folds
     * everything the two-call flow would otherwise do into one prompt: the spoken language (readable
     * name), the style/punctuation prompt + custom words, and — when rewording is enabled — the
     * auto-formatting rules and the user's auto-apply prompts. The client prepends a "transcribe, return
     * only the text" preamble.
     */
    private suspend fun buildChatAudioInstruction(context: Context): String {
        val parts = mutableListOf<String>()
        // Source-language hint only (not an output directive) so it never fights a "translate to X"
        // rewording prompt — the weaker models otherwise just echo the spoken language.
        DictateLanguages.englishNameFor(MaLanguage.active())?.takeIf { it.isNotBlank() }
            ?.let { parts.add("The audio is spoken in $it.") }
        transcriptionStylePrompt()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        // Formatting/rewording is folded in only when the user has rewording enabled (mirrors
        // postProcessTranscript's gating), so single-call output matches the two-call output.
        if (prefs.dictate.rewordingEnabled.get()) {
            if (prefs.dictate.autoFormattingEnabled.get()) {
                parts.add(DictatePromptDefaults.AUTO_FORMATTING_PROMPT)
            }
            val autoApply = withContext(Dispatchers.IO) {
                promptsDb(context).getAll().filter { it.autoApply }
            }
            autoApply.forEach { p -> p.prompt?.takeIf { it.isNotBlank() }?.let { parts.add(it) } }
        }
        return parts.joinToString("\n\n")
    }

    private fun promptsDb(context: Context) = PromptsDatabaseHelper.getInstance(context)

    // Audio focus was requested here while recording, and it is deliberately gone.
    //
    // It took AUDIOFOCUS_GAIN_TRANSIENT, which asks every other player on the phone to duck or
    // pause, and its listener paused the recording whenever something else took focus back. With a
    // reader app playing in the background that produced exactly the tangle Marko described: two
    // programs each politely stopping for the other, and the result reading as backwards.
    //
    // Nothing replaces it. Recording no longer touches what anything else is playing, and nothing
    // anything else plays touches the recording. Whatever is playing keeps playing, the way it does
    // in a browser, which is what was asked for and is also the honest default: this app records
    // through the microphone and has no business holding the phone's audio.

    /**
     * Asks everything else playing to stop, for the length of the recording.
     *
     * ### This was removed once, at his instruction, and is back at his instruction
     *
     * §29 took audio focus out entirely and said not to rebuild it. That was right for what existed:
     * the old version also **listened** for focus loss and paused HIS recording whenever another app
     * took focus back. With a reader playing in the background the two rules met each other — each
     * program politely stopping for the other — which is why it behaved backwards.
     *
     * **The listening is what was wrong, not the asking.** This asks and does not listen. Nothing
     * another app does can now interrupt a recording; the only thing that stops it is him.
     *
     * ### Exclusive rather than transient
     *
     * `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` tells other players to **pause** rather than duck, and
     * not to resume until focus is abandoned. Ducking would leave his bhajan playing quietly into
     * the microphone, which is the thing being avoided — a quieter recording of the wrong sound is
     * not an improvement on a loud one.
     *
     * Released in [cleanupAudioRouting], which every path out of a recording already goes through,
     * so the music comes back whether he sent it, cancelled it, or it failed.
     */
    private fun requestExclusiveAudio(context: Context) {
        runCatching {
            val am = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audioManager = it }
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                // A listener is required to build the request, and this one deliberately does
                // nothing. Reacting to focus changes is exactly the behaviour that made the old
                // version fight with his reader app.
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
            MaLog.add("audio", "asked other apps to pause")
        }
    }

    private suspend fun setupBluetoothIfEnabled(context: Context): Int {
        // Non-Bluetooth path uses the user's chosen audio source (issue #62); Bluetooth SCO always needs
        // VOICE_COMMUNICATION. If BT is requested but can't be activated, fall back to the chosen source.
        val localSource = prefs.dictate.audioInputSource.get().resolve(context)
        if (!prefs.dictate.useBluetoothMic.get()) return localSource
        val router = BluetoothMicRouter(context).also { btRouter = it }
        return if (router.activate()) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            localSource
        }
    }

    private fun cleanupAudioRouting() {
        // Give the music back. Every exit from a recording passes through here.
        focusRequest?.let { request ->
            runCatching { audioManager?.abandonAudioFocusRequest(request) }
            MaLog.add("audio", "released, other apps may resume")
        }
        focusRequest = null
        audioManager = null
        btRouter?.deactivate()
        btRouter = null
    }
}
