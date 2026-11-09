/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.dictate.provider.MaClaudeModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import dev.patrickgold.florisboard.app.settings.theme.ColorPreferenceSerializer
import dev.patrickgold.florisboard.app.settings.theme.DisplayKbdAfterDialogs
import dev.patrickgold.florisboard.app.settings.theme.SnyggLevel
import dev.patrickgold.florisboard.app.setup.NotificationPermissionState
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonDesign
import dev.patrickgold.florisboard.dictate.llm.MaPhoneModels
import dev.patrickgold.florisboard.dictate.DictateLongformMode
import dev.patrickgold.florisboard.dictate.MaNumericSecondary
import dev.patrickgold.florisboard.dictate.MaPopups
import dev.patrickgold.florisboard.dictate.MaFeatureOrder
import dev.patrickgold.florisboard.dictate.audio.DictateAudioSource
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonSize
import dev.patrickgold.florisboard.dictate.DictateLegacyLayout
import dev.patrickgold.florisboard.dictate.DictatePromptsLayout
import dev.patrickgold.florisboard.dictate.DictateRecordingAnimation
import dev.patrickgold.florisboard.dictate.DictateReasoningEffort
import dev.patrickgold.florisboard.dictate.data.mappings.DictateMappings
import dev.patrickgold.florisboard.dictate.gif.GifContentFilter
import dev.patrickgold.florisboard.dictate.gif.GifHistory
import dev.patrickgold.florisboard.dictate.provider.DictateProxyType
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.input.HapticVibrationMode
import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHairStyle
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSkinTone
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.ime.nlp.SpellingLanguageMode
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.ExtendedActionsPlacement
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionJsonConfig
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyHintConfiguration
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.florisboard.ime.window.ImeWindowConfig
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.util.VersionName
import dev.patrickgold.jetpref.datastore.annotations.Preferences
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.model.LocalTime
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.model.PreferenceType
import dev.patrickgold.jetpref.material.ui.ColorRepresentation
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.isOrientationPortrait

val FlorisPreferenceStore = jetprefDataStoreOf(FlorisPreferenceModel::class)

@Preferences
abstract class FlorisPreferenceModel : PreferenceModel() {
    companion object {
        const val NAME = "florisboard-app-prefs"
    }

    val clipboard = Clipboard()
    inner class Clipboard {
        val useInternalClipboard = boolean(
            key = "clipboard__use_internal_clipboard",
            default = false,
        )
        val syncToFloris = enum(
            key = "clipboard__sync_to_floris",
            default = ClipboardSyncBehavior.ALL_EVENTS,
        )
        val syncToSystem = enum(
            key = "clipboard__sync_to_system",
            default = ClipboardSyncBehavior.NO_EVENTS,
        )
        // The clipboard suggestion preferences are gone with the feature they switched. It put the
        // newest copy into the word suggestion strip, which is what the copy buckets do now, in the
        // same strip the bucket legend draws in.
        // On by default in TTT, where it was off in FlorisBoard.
        //
        // This is not a preference here, it is the feature. The C1 to C10 keys read this history,
        // and with recording off history.all is permanently empty, so every one of those keys sees
        // null and does nothing at all — which is exactly how they shipped and exactly what it
        // looked like: ten keys that swallow a tap.
        val historyEnabled = boolean(
            key = "clipboard__history_enabled",
            default = true,
        )
        val historyNumGridColumnsPortrait = int(
            key = "clipboard__history_num_grid_columns_portrait",
            default = CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO,
        )
        val historyNumGridColumnsLandscape = int(
            key = "clipboard__history_num_grid_columns_landscape",
            default = CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO,
        )
        @Composable
        fun historyNumGridColumns(): PreferenceData<Int> {
            val configuration = LocalConfiguration.current
            return if (configuration.isOrientationPortrait()) {
                historyNumGridColumnsPortrait
            } else {
                historyNumGridColumnsLandscape
            }
        }
        val historyAutoCleanOldEnabled = boolean(
            key = "clipboard__history_auto_clean_old_enabled",
            default = false,
        )
        val historyAutoCleanOldAfter = int(
            key = "clipboard__history_auto_clean_old_after",
            default = 20,
        )
        val historyAutoCleanSensitiveEnabled = boolean(
            key = "clipboard__history_auto_clean_sensitive_enabled",
            default = false,
        )
        val historyAutoCleanSensitiveAfter = int(
            key = "clipboard__history_auto_clean_sensitive_after",
            default = 20,
        )
        val historySizeLimitEnabled = boolean(
            key = "clipboard__history_size_limit_enabled",
            default = true,
        )
        val historySizeLimit = int(
            key = "clipboard__history_size_limit",
            default = 20,
        )
        val historyHideOnPaste = boolean(
            key = "clipboard__history_hide_on_paste",
            default = false,
        )
        val historyHideOnNextTextField = boolean(
            key = "clipboard__history_hide_on_next_text_field",
            default = true,
        )
        val clearPrimaryClipAffectsHistoryIfUnpinned = boolean(
            key = "clipboard__clear_primary_clip_affects_history_if_unpinned",
            default = true,
        )
    }

    val correction = Correction()
    inner class Correction {
        val autoCapitalization = boolean(
            key = "correction__auto_capitalization",
            default = false, // AUTO: nothing changes unless switched on (Marko, 9.11.2026)
        )
        val autoSpacePunctuation = boolean(
            key = "correction__auto_space_punctuation",
            default = false,
        )
        val doubleSpacePeriod = boolean(
            key = "correction__double_space_period",
            default = false,
        )
        val rememberCapsLockState = boolean(
            key = "correction__remember_caps_lock_state",
            default = false,
        )
    }

    val devtools = Devtools()
    inner class Devtools {
        val enabled = boolean(
            key = "devtools__enabled",
            default = false,
        )
        val showPrimaryClip = boolean(
            key = "devtools__show_primary_clip",
            default = false,
        )
        val showInputStateOverlay = boolean(
            key = "devtools__show_input_state_overlay",
            default = false,
        )
        val showSpellingOverlay = boolean(
            key = "devtools__show_spelling_overlay",
            default = false,
        )
        val showInlineAutofillOverlay = boolean(
            key = "devtools__show_inline_autofill_overlay",
            default = false,
        )
        val showKeyTouchBoundaries = boolean(
            key = "devtools__show_touch_boundaries",
            default = false,
        )
        val showDragAndDropHelpers = boolean(
            key = "devtools__show_drag_and_drop_helpers",
            default = false,
        )
        val showWindowResizeHandleBoundaries = boolean(
            key = "devtools__show_window_resize_handle_boundaries",
            default = false,
        )
    }

    val dictate = Dictate()
    inner class Dictate {
        // --- Provider keyring (multi-provider, roadmap section 4.x) ------------------------------
        // Per-provider credentials (API key + chosen models + custom base URL), keyed by provider id.
        // This is the source of truth for keys/models; transcriptionProviderId / rewordingProviderId
        // below are just the *active* pointers into this keyring. See ProviderAccounts.
        val providerAccounts = custom(
            key = "dictate__provider_accounts",
            default = ProviderAccounts.Empty,
            serializer = ProviderAccounts.Serializer,
        )
        // Guard so the one-time import of the legacy flat prefs (apiKey/transcriptionModel/… below)
        // into the keyring runs exactly once. See DictateProviderMigrator.
        val providerAccountsMigrated = boolean(
            key = "dictate__provider_accounts_migrated",
            default = false,
        )

        // Active transcription provider id, matching a ProviderRegistry id that supports speech-to-text
        // ("openai", "groq") or a "custom:<uuid>" endpoint. The actual key/model live in the keyring.
        val transcriptionProviderId = string(
            key = "dictate__transcription_provider_id",
            // Voice Type: AssemblyAI out of the box. The upstream default was openai, a provider
            // this build does not offer, so a fresh install pointed at nothing.
            default = "assemblyai",
        )


        // On-device offline fallback (issue #104): when the active provider is a cloud one and its call
        // fails because the device is offline (after the normal retries), retry once on-device using the
        // local provider's downloaded model. No effect if the local model isn't installed, or if the
        // active provider is already the local one.
        val localFallbackEnabled = boolean(
            key = "dictate__local_fallback_enabled",
            default = false,
        )

        // Transcription model (Marko, 7.11.2026): "assemblyai" = AssemblyAI only, "local" = Parakeet on the phone
        // only, "fallback" = AssemblyAI, and Parakeet whenever AssemblyAI is rejected or does not answer (no key,
        // refused key, no network, timeout, server error). Parakeet needs its ~670 MB download first; until then
        // "fallback" behaves like "assemblyai". See MaProviders.
        val maTranscriptionEngine = string(
            key = "dictate__ma_transcription_engine",
            default = "fallback",
        )


        // --- Network proxy (roadmap 5.6) ---------------------------------------------------------
        // Optional proxy applied to *every* provider API call (transcription, rewording, model
        // listing, connection test). Disabled by default; built into a ProxyConfig via ProxyConfig.of
        // and forwarded to OkHttp. HTTP proxies support user/password; SOCKS5 credentials are not
        // forwarded (JVM limitation). See dictateProxyConfig().
        val proxyEnabled = boolean(
            key = "dictate__proxy_enabled",
            default = false,
        )
        val proxyType = enum(
            key = "dictate__proxy_type",
            default = DictateProxyType.HTTP,
        )
        val proxyHost = string(
            key = "dictate__proxy_host",
            default = "",
        )
        val proxyPort = string(
            key = "dictate__proxy_port",
            default = "8080",
        )
        val proxyUsername = string(
            key = "dictate__proxy_username",
            default = "",
        )
        val proxyPassword = string(
            key = "dictate__proxy_password",
            default = "",
        )
        val trustUserCertificates = boolean(
            key = "dictate__trust_user_certificates",
            default = false,
        )

        // --- DEPRECATED flat credential prefs (migration source only) ----------------------------
        // Kept solely so DictateProviderMigrator can copy them into the keyring once. Do not read these
        // for live calls anymore – use providerAccounts[transcriptionProviderId].
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val apiKey = string(
            key = "dictate__api_key",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val transcriptionModel = string(
            key = "dictate__transcription_model",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val customBaseUrl = string(
            key = "dictate__custom_base_url",
            default = "",
        )
        // Route recording through a connected Bluetooth (SCO) microphone when available.
        val useBluetoothMic = boolean(
            key = "dictate__use_bluetooth_mic",
            default = false,
        )
        // Which audio source the recorder captures from (issue #62). Default keeps the device MIC
        // (current behavior); VOICE_RECOGNITION/UNPROCESSED skip phone audio processing that can hurt
        // transcription. Ignored on the Bluetooth-SCO path (always VOICE_COMMUNICATION).
        val audioInputSource = enum(
            key = "dictate__audio_input_source",
            default = DictateAudioSource.DEFAULT,
        )
        // Keep the screen awake while a recording is in progress (default on).
        val keepScreenAwake = boolean(
            key = "dictate__keep_screen_awake",
            default = true,
        )
        // How the recording indicator moves while dictating — the Smartbar's red dot and the classic
        // layout's record button (issue #238). Defaults to LEVEL (mic-reactive), which doubles as
        // feedback that the microphone is hearing something; PULSE restores the pre-rewrite look and
        // STATIC removes the movement entirely for anyone who finds it distracting while speaking.
        val recordingAnimation = enum(
            key = "dictate__recording_animation",
            default = DictateRecordingAnimation.LEVEL,
        )
        // Skip transcription when a local Silero VAD finds no speech in the recording, so silent clips
        // don't produce "ghost text" hallucinations or waste API credits (issue #93). Default on.
        val skipSilentRecordings = boolean(
            key = "dictate__skip_silent_recordings",
            default = true,
        )
        // Trim long internal pauses (> ~2 s of silence) out of a recording before it's uploaded, using the
        // same local Silero VAD as the silence gate (issue #232). Every spoken segment is kept in full;
        // only the dead time between them is collapsed, so a dictation with big gaps sends less audio (less
        // cost/latency) without losing a word. Default on. Ignored while long-form dictation is active — it
        // does its own segment-cutting.
        val trimSilentGaps = boolean(
            key = "dictate__trim_silent_gaps",
            default = true,
        )
        // Break long *plain* transcripts into paragraphs (issue #225): once at least this many words have
        // accumulated, the next sentence end starts a new paragraph. 0 = off (default). Deterministic and
        // only applied to a pure transcript — never to reworded / auto-formatted output, which already
        // carries its own paragraphing.
        val paragraphSplitWords = int(
            key = "dictate__paragraph_split_words",
            default = 0,
        )
        // Long-press the send (mic) button while recording to transcribe with the on-device model instead
        // of the configured cloud provider (issue #228), for a quick offline one-off without digging
        // through the provider settings. Toggled from the on-device model dialog. Off by default. Only
        // applies to a plain recording — in long-form / streaming there is no plain send button to hold.
        val longPressSendLocalModel = boolean(
            key = "dictate__long_press_send_local_model",
            default = false,
        )
        // Hold-to-record instead of tap-to-start/tap-to-stop (issue #235): press and hold the mic, speak,
        // release to send — slide left to discard, slide up to latch. Off by default because it replaces
        // the mic's long-press shortcuts (file transcription, send-with-local-model) with the hold
        // itself. Long-form segmented ignores it: a ten-minute dictation cannot be held down.
        val pushToTalk = boolean(
            key = "dictate__push_to_talk",
            default = false,
        )
        // Minutes the on-device model may sit idle before it is unloaded from RAM to free memory (models
        // are ~100 MB up to ~700 MB). It is always also freed immediately on an Android memory-pressure
        // signal; this timer additionally covers the "keyboard alive but not dictating" window. 0 = only
        // on memory pressure (no idle timer). Default 5 minutes.
        val localModelUnloadMinutes = int(
            key = "dictate__local_model_unload_minutes",
            default = 5,
        )
        // Haptic feedback on dictation state changes (issue #166): a short buzz on record start/stop, a
        // double on transcription done, a longer one when a rewording/LLM prompt finished — so the user
        // knows blindly when to look back at the screen. Off by default. Amplitude honours the system
        // haptic intensity.
        val hapticFeedback = boolean(
            key = "dictate__haptic_feedback",
            default = false,
        )
        // Start recording immediately whenever the keyboard opens on a text field (default off).
        val instantRecording = boolean(
            key = "dictate__instant_recording",
            default = false,
        )
        // When instant recording is on, still don't auto-start on number-only fields (number, phone, PIN,
        // date/time), where dictation rarely makes sense (issue #146). Default on.
        val instantRecordingSkipNumeric = boolean(
            key = "dictate__instant_recording_skip_numeric",
            default = true,
        )
        // Floating dictation button (issue #88): the in-app master toggle. The bubble only shows when
        // this is on AND the DictateAccessibilityService is enabled in the system accessibility settings
        // (the latter is the actual permission; this lets the user hide the bubble without digging into
        // system settings). Default off — opt-in feature.
        val floatingButtonEnabled = boolean(
            key = "dictate__floating_button_enabled",
            default = false,
        )
        // Whether the floating button also shows while the Dictate keyboard itself is the active input
        // method. Default off: when our own keyboard is up it already has a mic key, so the bubble would
        // be redundant; turning this on shows it everywhere regardless of the active keyboard.
        val floatingButtonShowWithDictateKeyboard = boolean(
            key = "dictate__floating_button_show_with_dictate_keyboard",
            default = false,
        )
        // Visual style of the floating button: a compact ring (RING) or a bubble that expands into a pill
        // with a timer + live waveform while active (PILL). See DictateFloatingButtonDesign.
        val floatingButtonDesign = enum(
            key = "dictate__floating_button_design",
            default = DictateFloatingButtonDesign.PILL,
        )
        // Overall size of the floating button (scales the skin dimensions).
        val floatingButtonSize = enum(
            key = "dictate__floating_button_size",
            default = DictateFloatingButtonSize.MEDIUM,
        )
        // Whether the floating button snaps to the nearest screen edge after being dragged. Default on;
        // turn off to leave it wherever it is dropped (still kept within the screen bounds).
        val floatingButtonSnapToEdge = boolean(
            key = "dictate__floating_button_snap_to_edge",
            default = true,
        )
        // Accent color of the floating button (idle/transcribing visuals). Defaults to the Dictate light blue.
        val floatingButtonColor = custom(
            key = "dictate__floating_button_color",
            default = Color(0xFF30B7E6),
            serializer = ColorPreferenceSerializer,
        )
        // Fade + shrink the button to a small dot after a few seconds of inactivity; tap to restore.
        val floatingButtonAutoDim = boolean(
            key = "dictate__floating_button_auto_dim",
            default = true,
        )
        // Remember the button's position separately per app.
        val floatingButtonRememberPosition = boolean(
            key = "dictate__floating_button_remember_position",
            default = true,
        )
        // Vibrate briefly when the button is tapped.
        val floatingButtonHaptic = boolean(
            key = "dictate__floating_button_haptic",
            default = true,
        )
        // Optional undo control on the floating button (issue #133): when on, an undo button appears
        // next to the bubble after a dictation and removes the last inserted text in one tap. Off by
        // default to keep the overlay minimal; needs "remember last dictation" to have something to undo.
        val floatingButtonUndoEnabled = boolean(
            key = "dictate__floating_button_undo_enabled",
            default = false,
        )
        // Safety net (issue #214): unconditionally copy every floating-button dictation to the system
        // clipboard, so nothing is lost if the accessibility insert is silently swallowed (the known
        // "green check but no text" failure) — the user can then just paste it manually. Off by default
        // because it overwrites the clipboard on every dictation (and shows a system clipboard toast on
        // some OEMs like Samsung).
        val floatingButtonCopyToClipboard = boolean(
            key = "dictate__floating_button_copy_to_clipboard",
            default = false,
        )
        // Whether the user has opened the floating-button screen at least once (clears the "New" badge).
        val floatingButtonHintSeen = boolean(
            key = "dictate__floating_button_hint_seen",
            default = false,
        )
        // App version whose one-time floating-button Smartbar spotlight has already been shown.
        val floatingButtonSpotlightVersion = string(
            key = "dictate__floating_button_spotlight_version",
            default = "",
        )
        // --- Output behavior (roadmap section 10) ------------------------------------------------
        // Press Enter / trigger the editor action automatically after committing a transcription.
        val autoEnter = boolean(
            key = "dictate__auto_enter",
            default = false,
        )
        // Commit the transcription all at once (true) or "type" it out character by character (false).
        val instantOutput = boolean(
            key = "dictate__instant_output",
            default = true,
        )
        // Real-time (streaming) transcription (issue #128): show text live while speaking, for providers
        // that support it (OpenAI realtime, Soniox, Deepgram, …). Global switch; falls back to batch when
        // the selected provider has no realtime support. Default off.
        val realtimeTranscription = boolean(
            key = "dictate__realtime_transcription",
            default = false,
        )
        // --- Long-form segmented dictation (issue #170) ------------------------------------------
        // Transcribe long dictations segment-by-segment in the background while you keep talking, so you
        // don't wait for one big upload at the end. OFF by default; MANUAL shows the "Next" button, AUTO
        // additionally uses Silero VAD + Smart Turn v3 at speech pauses. Keyboard-only, not for realtime /
        // live-prompt / multimodal.
        val longformMode = enum(
            key = "dictate__longform_mode",
            default = DictateLongformMode.OFF,
        )
        // Maximum silence (Pipecat Smart Turn stop_secs fallback) before AUTO mode cuts even when the
        // semantic classifier says the current thought may be incomplete.
        val longformAutoSplitSeconds = int(
            key = "dictate__longform_auto_split_seconds",
            default = 3,
        )
        // Opt-in semantic auto-segmentation: when on (and the model is downloaded), AUTO mode uses the
        // on-device Smart Turn v3 classifier to cut at completed thoughts instead of only on silence.
        // Off by default; the ~8 MB model is downloaded on demand, not bundled.
        val smartTurnEnabled = boolean(
            key = "dictate__smart_turn_enabled",
            default = false,
        )
        // Speed of the typewriter animation when instantOutput is off (1 = slow … 10 = fast).
        val outputSpeed = int(
            key = "dictate__output_speed",
            default = 5,
        )
        // Show a resend button when a recording failed to transcribe/reword, to retry the same audio.
        val resendButton = boolean(
            key = "dictate__resend_button",
            default = true,
        )
        // Safety net (issue #111): keep the last successful dictation around so it can be re-inserted
        // via the "Re-insert last dictation" Smartbar action after the field is cleared (rotation,
        // context switch, host app refreshing its state). When off, nothing is cached and the action
        // stays disabled. The text is stored locally (see lastDictation) until the next dictation.
        val rememberLastDictation = boolean(
            key = "dictate__remember_last_dictation",
            default = true,
        )
        // The last successfully committed dictation text, persisted so it survives the IME process being
        // killed. Overwritten by the next successful dictation; never shown directly in the UI. Empty
        // means there is nothing to re-insert. Only populated while rememberLastDictation is on.
        val lastDictation = string(
            key = "dictate__last_dictation",
            default = "",
        )
        // Interrupted recording (keyboard closed mid-recording): the audio is finalized and moved to a
        // file in filesDir so it survives the recorder/process being destroyed; these prefs are the
        // persisted marker + metadata so the "recording interrupted — send it?" offer can be restored on
        // the next keyboard open. pending=true means an interrupted-audio file is waiting.
        val interruptedAudioPending = boolean(
            key = "dictate__interrupted_audio_pending",
            default = false,
        )
        // Recorded seconds of the interrupted audio, re-credited towards the rate/donate nudges on send.
        val interruptedAudioSeconds = long(
            key = "dictate__interrupted_audio_seconds",
            default = 0L,
        )
        // Whether the interrupted recording was a live-prompt session, so sending it repeats that mode.
        val interruptedAudioLive = boolean(
            key = "dictate__interrupted_audio_live",
            default = false,
        )
        // --- Transcription history / activity log (issue #140) -----------------------------------
        // Keep a rolling, browsable log of finished dictations (transcript + metadata) so they can be
        // re-inserted, re-transcribed or reviewed later. Supersedes the single lastDictation slot for the
        // history UI; when off, nothing is logged. Never captured in incognito/password fields.
        val historyEnabled = boolean(
            key = "dictate__history_enabled",
            default = true,
        )
        // Additionally keep the source audio (WAV) of each logged dictation so a flaky transcription can be
        // replayed and re-transcribed. Off by default (privacy + disk: ~1.9 MB per recorded minute); the
        // audio lives in the app's private storage and is pruned by the byte budget below.
        // On by default. Keeping the transcript without its audio makes re-transcribing impossible,
        // which is most of what the archive is for: the same recording through a different model or
        // upload format, compared side by side. The size caps below still bound what it costs.
        val historyAudioRetention = boolean(
            key = "dictate__history_audio_retention",
            default = true,
        )
        // Cap: how many entries to keep (oldest dropped first).
        val historyMaxEntries = int(
            key = "dictate__history_max_entries",
            default = 50,
        )
        // Cap: entries older than this many days are dropped (0 = no age limit).
        val historyMaxAgeDays = int(
            key = "dictate__history_max_age_days",
            default = 30,
        )
        // Cap: total megabytes of retained audio; once exceeded, the oldest recordings' audio is dropped
        // (their transcript text is kept).
        val historyAudioBudgetMb = int(
            key = "dictate__history_audio_budget_mb",
            default = 200,
        )
        // --- Lifetime dictation statistics (issue #142) ------------------------------------------
        // Never auto-reset (unlike totalAudioSeconds below, which the rate nudge clears); only the user
        // can reset them from the stats screen. Updated centrally after each successful dictation.
        val statsDictations = long(key = "dictate__stats_dictations", default = 0L)
        val statsWords = long(key = "dictate__stats_words", default = 0L)
        val statsChars = long(key = "dictate__stats_chars", default = 0L)
        val statsSpokenSeconds = long(key = "dictate__stats_spoken_seconds", default = 0L)
        val statsRewordings = long(key = "dictate__stats_rewordings", default = 0L)
        // Epoch millis of the first ever dictation (0 = none yet), for the "tracking since" line.
        val statsFirstUseEpochMs = long(key = "dictate__stats_first_use_epoch_ms", default = 0L)
        // Day-streak bookkeeping: last active day (epoch day) plus current/best consecutive-day runs.
        val statsLastDayEpoch = long(key = "dictate__stats_last_day_epoch", default = 0L)
        val statsStreakCurrent = int(key = "dictate__stats_streak_current", default = 0)
        val statsStreakBest = int(key = "dictate__stats_streak_best", default = 0)
        // Compact rolling per-day word counts for the 7-day chart: "epochDay:words;epochDay:words;…".
        val statsDaily = string(key = "dictate__stats_daily", default = "")
        // One-time milestone celebrations (issue #142). Only saved-time and dictation-count milestones,
        // shown once each in the app (never on the keyboard). Toggle lives on the stats screen.
        val statsMilestonesEnabled = boolean(key = "dictate__stats_milestones_enabled", default = true)
        val statsMilestoneTimeShown = long(key = "dictate__stats_milestone_time_shown", default = 0L)
        val statsMilestoneCountShown = long(key = "dictate__stats_milestone_count_shown", default = 0L)
        // A crossed-but-not-yet-shown milestone, consumed on next app open: "time:<min>" | "count:<n>".
        val statsPendingMilestone = string(key = "dictate__stats_pending_milestone", default = "")

        // --- Rate / Donate nudges (roadmap 9.7/9.8) ----------------------------------------------
        // Cumulative seconds of successfully transcribed *recorded* audio, used to gate the one-time
        // rate/donate prompts. Replaces the legacy usage DB (which was dropped); only this counter
        // remains. Incremented after each successful mic transcription.
        val totalAudioSeconds = long(
            key = "dictate__total_audio_seconds",
            default = 0L,
        )
        // Set once the user has acted on the rate prompt (accepted or declined), so it never reappears.
        val hasRated = boolean(
            key = "dictate__has_rated",
            default = false,
        )
        // Set once the user has acted on the donate prompt; accepting/declining donate also sets
        // hasRated, so a donor is never asked to rate afterwards (mirrors the legacy behavior).
        val hasDonated = boolean(
            key = "dictate__has_donated",
            default = false,
        )
        // The app version whose "Dictate was updated" changelog nudge has already been shown on the
        // keyboard (Smartbar). Set when the user taps or dismisses that nudge, so it appears only once
        // per update. Empty until the first post-update nudge. Independent of the in-app dialog's
        // versionLastChangelog bookkeeping, so the two surfaces never suppress each other.
        val changelogNudgeVersion = string(
            key = "dictate__changelog_nudge_version",
            default = "",
        )
        // Comma-separated dictation language codes the user cycles through on the recording bar
        // (see DictateLanguages; "detect" = auto-detect). Default mirrors the legacy app.
        val inputLanguages = string(
            key = "dictate__input_languages",
            // The two Marko speaks, and no detect entry: the language is chosen by the HR/ENG key
            // rather than guessed from the audio.
            default = "hr,en",
        )
        // The currently active dictation language code; persists across sessions and is switched
        // from the recording bar's language chip.
        /**
         * The dictation language: `hr` or `en`, and nothing else.
         *
         * Auto-detect is gone. It defaulted here, and it meant the service guessed the language from
         * the audio — which sounds harmless and was not: a Croatian sentence guessed wrong comes back
         * as fluent Croatian that is the wrong words, and there is nothing in the result to notice.
         * Marko speaks two languages and says which one with a key, so there is nothing to guess.
         *
         * Croatian is the default rather than English because it is the one that suffers when the
         * app is wrong. English transcribed as Croatian is obviously broken; Croatian transcribed by
         * the English model is quietly broken.
         */
        val activeInputLanguage = string(
            key = "dictate__active_input_language",
            default = "en",
        )
        // Guard so the one-time seeding of the device/system dictation language (added on top of the
        // default detect,en) runs only once on a fresh install. See
        // DictateLegacyMigrator.seedDeviceLanguageIfNeeded.
        val inputLanguagesSeeded = boolean(
            key = "dictate__input_languages_seeded",
            default = false,
        )
        // Guard so the one-time import from the legacy Dictate SharedPreferences runs only once.
        val legacyImported = boolean(
            key = "dictate__legacy_imported",
            default = false,
        )
        // Guard for the one-time injection of the live-prompt Smartbar action into arrangements that
        // were saved before the action existed (otherwise upgrading users never see it).
        val livePromptActionMigrated = boolean(
            key = "dictate__live_prompt_action_migrated",
            default = false,
        )
        // Same one-time injection for the AI prompt-panel Smartbar action (DICTATE_PROMPTS).
        val promptsActionMigrated = boolean(
            key = "dictate__prompts_action_migrated",
            default = false,
        )
        // Guard for the one-time *removal* of the live-prompt Smartbar action: the live prompt is now a
        // chip inside the prompt panel/row, so it no longer ships as a separate Smartbar button. Strips
        // any previously-injected DICTATE_LIVE_PROMPT action from saved arrangements exactly once.
        val livePromptActionRemoved = boolean(
            key = "dictate__live_prompt_action_removed",
            default = false,
        )
        // Guard for the one-time switch of existing users to the always-on prompt ROW layout (the new
        // default). Fires once so users who were on PANEL land on ROW after the update; they can switch
        // back any time. See DictateLegacyMigrator.migratePromptsLayoutToRowIfNeeded.
        val promptsLayoutRowMigrated = boolean(
            key = "dictate__prompts_layout_row_migrated",
            default = false,
        )
        // Guard for the one-time re-engagement reset shipped with the 4.0.0 relaunch: existing users
        // (who had already rated/donated, or whose audio counter was long past the thresholds) are
        // given the rate & donate nudges one more time so they can react to the new app. Clears
        // hasRated/hasDonated and resets totalAudioSeconds exactly once. See
        // DictateLegacyMigrator.reofferRateAndDonateIfNeeded.
        val promoReengagementDone = boolean(
            key = "dictate__promo_reengagement_done",
            default = false,
        )

        // --- Rewording / GPT (roadmap section 4) -------------------------------------------------
        // Master switch for the rewording feature (prompt chips, auto-apply, live prompt). Default
        // on, mirroring the legacy app.
        val rewordingEnabled = boolean(
            key = "dictate__rewording_enabled",
            default = true,
        )
        // Reasoning effort sent as OpenAI-compatible `reasoning_effort` on rewording chat calls for
        // reasoning models (issue #141). OFF omits the field, so non-reasoning models are unaffected.
        val rewordingReasoningEffort = enum(
            key = "dictate__rewording_reasoning_effort",
            default = DictateReasoningEffort.OFF,
        )
        // The wire value sent as `reasoning_effort` when the setting is CUSTOM (issue #186), e.g. a value
        // a specific provider expects. Blank → the field is omitted.
        val rewordingReasoningEffortCustom = string(
            key = "dictate__rewording_reasoning_effort_custom",
            default = "",
        )
        // How the rewording prompt chips are surfaced: a dedicated panel (PANEL) opened from the
        // Smartbar, or an always-on extra row pinned above the Smartbar (ROW). See DictatePromptsLayout.
        // Defaults to ROW so the prompts are immediately visible; existing users are moved to ROW once via
        // DictateLegacyMigrator.migratePromptsLayoutToRowIfNeeded.
        val promptsLayout = enum(
            key = "dictate__prompts_layout",
            default = DictatePromptsLayout.ROW,
        )
        // Classic keyboard-less "legacy" dictation layout (issue #125): OFF = modern keyboard (default);
        // LOCKED = only the legacy record-first UI; SWIPE = legacy UI as home, horizontal swipe flips to
        // the modern typing keyboard and back. See DictateLegacyLayout / LegacyDictateLayout.
        val legacyLayout = enum(
            key = "dictate__legacy_layout",
            default = DictateLegacyLayout.OFF,
        )
        // Configurable legacy action row (#183/#194): comma-separated LegacyEditAction names, arranged by
        // the user via drag-and-drop. Default reproduces the original fixed row.
        val legacyActionRow = string(
            key = "dictate__legacy_action_row",
            // Emoji is not in the default row: this is a dictation keyboard and the emoji panel is
            // one of the things the fork does not need. History takes its place, since past
            // dictations are worth reaching from the view where dictating happens rather than from
            // a settings screen two levels away.
            // Grouped by what the keys actually do rather than by history. Undo and redo are the two
            // "take that back" keys and sit together on the left; select all belongs with cut, copy
            // and paste because selecting is the first move of all three, and separating it from
            // them was the reason the row read as a jumble.
            // Paste sits directly after select all, ahead of cut and copy, because select all then
            // paste is the one pair pressed together often enough to be a single motion: it is how
            // a field is replaced. Cut and copy read out of the field rather than into it, so they
            // follow.
            // Undo and redo left the row. Undo is on the number row's editing set and was the
            // least reached for of the eight here; redo even less. Their places go to the two things
            // that had nowhere to live: the clipboard panel, and the replace-everything macro.
            default = "SELECT_ALL,PASTE,CUT,COPY,HISTORY,CLIPBOARD_HISTORY,ALL_PASTE,KEYBOARD",
        )
        // Sticky panel: when on, whichever of the two main views was last used comes back the next
        // time the keyboard opens, instead of always landing on the typing keyboard. Marko is in the
        // transcribe view far more than the keyboard, so being returned to the keyboard every time
        // Dedicated cursor row above the keyboard: line start, four arrows, line end, all repeating
        // when held. Off by default so the stock keyboard is unchanged; switched on in Settings under
        // Gestures. See MaCursorRow.
        // The extra row above the keyboard. Off by default; the two Smartbar actions turn it on and
        // swap its contents. Croatian diacritics are the other thing constantly reached for on this
        // phone, and hunting them through long-press popups mid-sentence is slower than a row.
        // Volume keys as dictation controls while the keyboard is up: volume up starts and then
        // sends, volume down swaps view. On by default because it was asked for, and switchable        // Case forced on whatever comes back: "none", "lower" or "upper". A recogniser returns
        // sentence case with a full stop because it is guessing at prose; when the words are going
        // into a search box, a filename or a command line, that guess is wrong every time and has to
        // be undone by hand.
        // Nothing, by default: a transcript arrives exactly as the service wrote it, and case is a
        // decision taken afterwards with the four buttons, looking at the result. The automatic and
        // forced modes remain under Typing, Correction for anyone who would rather not decide each
        // time, but they are no longer what happens unasked.
        val maTextCase = string(
            key = "dictate__ma_text_case",
            default = "none",
        )
        // Sections of the keyboard that can be hidden. Screen height is the scarcest thing here, and
        // a row that is never used costs the same height as one used constantly.
        val maShowPrompts = boolean(
            key = "dictate__ma_show_prompts",
            default = true,
        )
        val maShowQuickRow = boolean(
            key = "dictate__ma_show_quick_row",
            default = true,
        )
        val maExtraRow = boolean(
            key = "dictate__ma_extra_row",
            default = false,
        )
        /** "digits", "diacritics", "symbols", "arrows" or "editing". */
        val maExtraRowMode = string(
            key = "dictate__ma_extra_row_mode",
            default = "digits",
        )
        // What each digit on the number row types when it is held. Ten slots joined by a unit
        // separator; see MaNumericSecondary. Underscore on all ten by default, because it costs
        // three layout switches to reach otherwise and appears constantly in scripts and paths.
        val maNumericSecondary = string(
            key = "dictate__ma_numeric_secondary",
            default = MaNumericSecondary.DEFAULT,
        )
        // A colour per key, G815 style: `k97=#C0392B n0=#2E86DE`. See MaKeyColors. Empty means every
        // key wears the theme's colour.
        val maKeyColors = string(
            key = "dictate__ma_key_colors",
            default = "",
        )
        // What a long press on each letter offers, `c\tč\tć` per line. See MaPopups. The default is
        // the five Croatian letters; every other letter starts with none.
        val maPopups = string(
            key = "dictate__ma_popups",
            default = MaPopups.DEFAULT,
        )
        // Personal word prediction: counts of what follows what, learned from this phone only and
        // never sent anywhere. On, because the words it exists to learn are the ones no shipped
        // dictionary contains, and it cannot learn them while switched off.
        val maNgramEnabled = boolean(
            key = "dictate__ma_ngram_enabled",
            default = true,
        )
        // AUTO (Marko, 9.11.2026: "I want no automatic replacement of anything"): the one-time reset that
        // switched every automatic text change off on installs that already existed, and the two switches
        // that only the AUTO screen holds. See MaAutoScreen and DictateLegacyMigrator.applyAutoOffV1IfNeeded.
        val maAutoOffV2Applied = boolean(
            key = "dictate__ma_auto_off_v2_applied",
            default = false,
        )
        val maAutoOffV1Applied = boolean(
            key = "dictate__ma_auto_off_v1_applied",
            default = false,
        )
        // Spoken fix-ups of a dictation: Croatian spelling and diacritics, name spelling, a dictated
        // address or time put together. Off: the transcript is written as the engine returned it.
        // Dictation features he asked for by name keep their state; the typing ones are what AUTO turned off.
        val maSpokenFixups = boolean(
            key = "dictate__ma_spoken_fixups",
            default = false,
        )
        // His find-and-replace list (Settings, Replacements) applied to dictation.
        val maTextReplacements = boolean(
            key = "dictate__ma_text_replacements",
            default = false,
        )
        // Learn from my corrections (MaDictationCorrections): a word he fixes in a dictation, fixed the same way
        // twice, is fixed for him from then on. On: it is local, free, and every rule can be switched
        // off on its own screen.
        val maCorrectionsEnabled = boolean(
            key = "dictate__ma_corrections_enabled",
            default = false, // AUTO v2 (9.11.2026)
        )
        // AUTO PREDICTION (Settings → Auto prediction): the AI key at the end of the suggestion row,
        // and the Claude model it asks. Stored as a tier alias, so the newest of the tier is used
        // without an update; the Anthropic key in the app pays for it. Haiku: the cheapest, and a
        // list of five words gains nothing from a bigger model.
        // Auto prediction (27.10.2026): the row stays on screen, dark when it has nothing to say, unless
        // he asks for the old behaviour of coming and going with its content.
        val maPredictRowAutoHide = boolean(
            key = "dictate__ma_predict_row_auto_hide",
            default = false,
        )
        // Suggestions in the language he is typing, English or not English (MaTypingLanguage).
        val maDetectTypingLanguage = boolean(
            key = "dictate__ma_detect_typing_language",
            default = true,
        )
        /** Reprompt (MaReprompt): his last instructions, newest first, on this phone only. */
        val maRepromptRecent = string(key = "dictate__ma_reprompt_recent", default = "")
        // RE Magic skill (MaReSkill, 9.11.2026): learn from the corrections he makes before sending.
        val maReLearn = boolean(key = "dictate__ma_re_learn", default = true)
        // Spelling (MaSpelling, 9.11.2026): the names his speech-to-text mishears. On by default: he asked for it.
        val maReSpeaks = boolean(key = "dictate__ma_re_speaks", default = true)
        val maReSkills = boolean(key = "dictate__ma_re_skills", default = true)
        /** Screenshot + OCR fallback for apps whose accessibility tree is empty (off by default; never for WhatsApp). */
        val maReScreenshot = boolean(key = "dictate__ma_re_screenshot", default = false)
        val maReScreen = boolean(key = "dictate__ma_re_screen", default = true)
        val maSpelling = boolean(key = "dictate__ma_spelling", default = true)
        val maSpellingTerms = boolean(key = "dictate__ma_spelling_terms", default = true)
        /** Pair ids switched off on the Spelling page, comma separated. */
        val maSpellingOff = string(key = "dictate__ma_spelling_off", default = "")
        /** The Reprompt key put on his rows, once. */
        val maRepromptKeyAdded = boolean(key = "dictate__ma_reprompt_key_added", default = false)
        /** The WA key placed on his rows once, after RE (7.11.2026). */
        val maWhatsAppKeyAdded = boolean(key = "dictate__ma_whatsapp_key_added", default = false)
        val maEngineKeysAdded = boolean(key = "dictate__ma_engine_keys_added", default = false)
        // AI words while typing (4.11.2026): Claude Haiku asked in parallel with the local engine, after
        // a short pause in typing, its words shown beside the local ones. On, as he asked ("always").
        val maAiLivePredict = boolean(
            key = "dictate__ma_ai_live_predict",
            default = true,
        )
        val maAiPredictKey = boolean(
            key = "dictate__ma_ai_predict_key",
            default = true,
        )
        val maPredictModel = string(
            key = "dictate__ma_predict_model",
            default = MaClaudeModels.DEFAULT,
        )
        // The copy and paste row, shared by both views. On, because it is the row that made the
        // transcribe view worth using and the keyboard view had no equivalent worth keeping.
        val maEditRow = boolean(
            key = "dictate__ma_edit_row",
            default = false,
        )
        // The feature row at the very bottom. Collapsible from its own left-hand key, and remembered,
        // because a row folded away should stay folded until it is asked back rather than returning
        // every time the keyboard opens.
        val maFeatureRowShown = boolean(
            key = "dictate__ma_feature_row_shown",
            default = true,
        )
        /**
         * The feature row's key order, as comma separated ids. Rearranged in Settings, Mantra,
         * Feature row.
         *
         * Stored as a string of ids rather than as indices so that it survives a key being added or
         * renamed. [MaFeatureOrder.parse] repairs anything unexpected and always returns all nine,
         * which matters because this row is the one that survives when every other row is folded
         * away: it is the only route to backspace, to enter and to the microphone.
         */
        /**
         * Which view the keyboard shows when it appears: `keyboard`, `dictation`, or `last`.
         *
         * Default `keyboard`, on Marko's instruction, replacing a rule that always reopened whichever
         * view was last used. That rule was written to stop a view being lost, and it does, but it
         * also means the keyboard that appears depends on something done in a different app an hour
         * ago. A keyboard is opened to type far more often than to dictate, and a text field that
         * greets you with a microphone is a surprise every time it happens.
         *
         * `last` keeps the old behaviour for anyone who wants it, which is why this is a setting and
         * not a deletion.
         */
        val maOpeningView = string(
            key = "dictate__ma_opening_view",
            default = "keyboard",
        )
        /** Auto prediction moved beside Prompts in the stored order, once (27.10.2026). */
        val maPredictionsMovedV1 = boolean(
            key = "dictate__ma_predictions_moved_v1",
            default = false,
        )
        /** Voice transcription buttons moved to position 2 of the stored order, once (build 572, 9.11.2026). */
        val maEnginesSecondV1 = boolean(
            key = "dictate__ma_engines_second_v1",
            default = false,
        )
        /** Settings entries the user has opened (comma separated ids); an entry not in it shows the NEW badge. */
        val maSettingsSeen = string(
            key = "dictate__ma_settings_seen",
            default = "",
        )
        /** The seen set was seeded once with every entry that existed before NEW badges (build 572). */
        val maSettingsSeenSeededV1 = boolean(
            key = "dictate__ma_settings_seen_seeded_v1",
            default = false,
        )
        /** The Mantra settings list order, as comma separated ids. See MaSettingsOrder. */
        val maSettingsOrder = string(
            key = "dictate__ma_settings_order",
            default = "cloud_reader,feature_row,reader,switchboard,prompts,settings_order,magic,recording,history,buckets,mappings,output,permissions,recovered,vocabulary,predictions,voice_commands,shortcuts,voice_format,copy_row,profiles",
        )
        // Zone two: the keyboard itself, everything from the number row down to the bottom row,
        // switched as one from the feature row. Zone one is the edit strip and rides on maEditRow,
        // which already existed, so there is one switch for it rather than two that could disagree.
        //
        // Kept separate from maExtraRow rather than overwriting it. Folding the keyboard away for a
        // minute must not silently erase somebody's decision about whether they want a number row
        // when it comes back.
        val maZoneKeyboard = boolean(
            key = "dictate__ma_zone_keyboard",
            default = false,
        )
        val maCursorRow = boolean(
            key = "dictate__ma_cursor_row",
            // On: the bars are the point, not an option to go looking for.
            default = true,
        )
        // One-shot: switch an existing install over to Sunrise. Changing the default only affects a
        // fresh install, and every phone that already ran this app has floris_night written to disk,
        // so without this the theme would never actually arrive on the device that asked for it.
        // Runs once and never again, so a later manual choice of another theme sticks.
        // Second one-shot pass. maSunriseApplied has already run on every phone that has this
        // build's predecessor, so reusing it would silently skip everything added after it. Each
        // batch of "change something a user may already have written" needs its own flag.
        val maRowV2Applied = boolean(
            key = "dictate__ma_row_v13_applied",
            default = false,
        )
        val maSunriseApplied = boolean(
            key = "dictate__ma_sunrise_applied",
            default = false,
        )
        // The Menu Macro switches added after v13. Its own flag, not a bump of the row one: bumping
        // that key re-runs the whole of that pass, and a phone set up by hand since would lose those
        // choices to it.
        val maPanelV14Applied = boolean(
            key = "dictate__ma_panel_v14_applied",
            default = false,
        )
        // One-handed mode and the floating window, out of an arrangement already written.
        val maRemovalV15Applied = boolean(
            key = "dictate__ma_removal_v15_applied",
            default = false,
        )
        // Croatian and English on the keyboard, once, without being asked.
        val maLanguagesV16Applied = boolean(
            key = "dictate__ma_languages_v17_applied",
            default = false,
        )
        // The row sets to the front of a panel already written.
        val maDashboardV18Applied = boolean(
            key = "dictate__ma_dashboard_v18_applied",
            default = false,
        )
        // Paste ahead of cut and copy in a row already written.
        val maActionRowV19Applied = boolean(
            key = "dictate__ma_action_row_v19_applied",
            default = false,
        )
        // Clipboard panel and the replace-all macro in place of undo and redo.
        val maActionRowV20Applied = boolean(
            key = "dictate__ma_action_row_v20_applied",
            default = false,
        )
        // Every row above the keyboard, built-in keys and macros together, serialized by MaRows.
        //
        // Empty means "not migrated yet" rather than "no rows": MaRowsMigration fills it once from
        // the two preferences below plus the old feature row order, and after that this is the only
        // one that is read. The old three are left in place and unread rather than deleted, because
        // a migration that runs against an empty source produces an empty keyboard, and that is the
        // shape of bug that only shows up on the one install that mattered.

        val maRows = string(
            key = "dictate__ma_rows",
            default =
                // The row he actually uses, exported 21.8.2026: the three zone keys, the mic, the
                // reader, the switchboard and settings.
                "1~default row\u001Cb\u001Dzone1\u001D1\u001Fb\u001Dzone2\u001D1\u001Fb\u001Dzone3\u001D1\u001Fb\u001Dhistory\u001D1\u001Fb\u001Drecorden\u001D1\u001Fb\u001Dcase\u001D1\u001Fb\u001Drow_2\u001D1\u001Fb\u001Dsettings\u001D1\u001E1~switch\u001Cb\u001Dundo\u001D1\u001Fb\u001Darrow_left\u001D1\u001Fb\u001Darrow_right\u001D1\u001Fb\u001Darrow_down\u001D1\u001Fb\u001Darrow_up\u001D1\u001Fb\u001Dspace\u001D1\u001Fb\u001Denter\u001D1\u001Fb\u001Dbackspace\u001D1\u001Fb\u001Dsend\u001D1\u001E0~buckets\u001Cb\u001Dcclear\u001D1\u001Fc\u001D1\u001D1\u001Fc\u001D2\u001D1\u001Fc\u001D3\u001D1\u001Fb\u001Dautobucket\u001D1\u001Fb\u001Dtab\u001D1\u001E0~TTS\u001Cb\u001Dsubtitle\u001D1\u001Fb\u001Dread_faster\u001D1\u001Fb\u001Dread_slower\u001D1\u001Fb\u001Dread_watch\u001D1\u001Fb\u001Dread_stop\u001D1\u001Fb\u001Dread_next\u001D1\u001Fb\u001Dread_prev\u001D1\u001Fb\u001Dread_play\u001D1\u001Fb\u001Dreader\u001D1\u001E0\u001Cb\u001Darrow_left\u001D1\u001Fb\u001Darrow_right\u001D1\u001Fb\u001Darrow_up\u001D1\u001Fb\u001Darrow_down\u001D1\u001Fb\u001Dclip_history\u001D1\u001Fb\u001Dcopy\u001D1\u001Fb\u001Dbackspace\u001D1\u001Fb\u001Dpin\u001D1\u001E0\u001C",
        )

        /**
         * Whether a clipboard history key replaces the field or inserts into it.
         *
         * On by default, because replacing is what the CH row is for. These keys are AP with a
         * chosen entry rather than the current one: select all, delete, paste. The field Marko is
         * filling is nearly always a search box, a prompt or a single-line field he is putting one
         * value into, and inserting beside whatever is already there is the wrong answer often
         * enough that it has to be the exception.
         *
         * Off makes them insert at the cursor and touch nothing else, for writing prose where the
         * clipboard is a source rather than the whole answer.
         */
        /**
         * Whether the clipboard expansion is open.
         *
         * Off at rest, and that is the point of it: the nine slots are a second row of keys, and a
         * keyboard that is permanently a row taller costs screen on every app whether or not the
         * clipboard is being used. The CH badge on the feature row opens it, uses it, and closes it.
         *
         * Not remembered as "open" across a restart on purpose — see the badge. Coming back to a
         * keyboard that is unexpectedly a row taller is the kind of thing that gets blamed on the
         * app being broken.
         */
        /**
         * What the ten macro buttons do, serialized by MaMacroSlots.
         *
         * Apart from the rows on purpose: a row stores a reference to a slot, so the same macro can
         * sit in two rows without being written twice, and moving a button cannot lose what was
         * attached to it.
         */
        val maMacroSlots = string(
            key = "dictate__ma_macro_slots",
            default = "pst\u001D {Ctrl+V}\u001EM2\u001D\u001EM3\u001D\u001EM4\u001D\u001EM5\u001D\u001EM6\u001D\u001EM7\u001D\u001EM8\u001D\u001EM9\u001D\u001EM10\u001D",
        )

        /**
         * What the ten copy buckets hold, in order. See MaClipCapture.
         *
         * A copy goes into the lowest empty bucket and stays there until it is pasted, which pours
         * that bucket out and frees it for the next copy. The keys were once a window onto the
         * history sorted newest first, which meant every key changed meaning on every copy and a key
         * pressed from memory pasted whatever had since moved under it.
         */
        /**
         * Keeps the keyboard up: the pin in the top-left corner.
         *
         * What it does and does not do is worth being exact about, because the name promises more
         * than Android allows. Pinned, the input view is shown whenever this IME is running — with a
         * hardware keyboard attached, in landscape, and in the cases where the system would collapse
         * the on-screen view to a strip — fullscreen extract mode is refused, and the keyboard asks
         * to be shown again the moment a field is focused rather than waiting to be invited.
         *
         * It cannot hold the keyboard open across an app that has no text field focused. The system
         * owns that window and tears it down when nothing is taking input; no flag an IME can set
         * overrides it. What the pin removes is the collapsing and re-raising in between, which is
         * the part that was actually costing Marko time.
         */
        val maKeyboardPinned = boolean(
            key = "dictate__ma_keyboard_pinned",
            default = false,
        )

        /**
         * What the magic key looks for: the terms, their order, and which are switched on.
         *
         * Text rather than a picture of a button. Every control on Android carries the label a
         * screen reader announces plus its exact rectangle, so a button is findable by what it says
         * whatever the theme, the density or the scroll position — where matching pixels breaks on
         * all three, and breaks looking exactly like the key is faulty.
         *
         * Editable so a new site costs a line here rather than a build. Empty falls back to
         * MaMagicTargets.defaults(), so clearing it cannot leave a key that does nothing.
         */
        /**
         * Whether the magic row is on the keyboard.
         *
         * Its own row rather than a key on the feature rows, because its contents are not a fixed
         * set of keys: it is one button per term, and the terms change as he learns them. A row that
         * rewrites itself does not belong in an editor where every other row is arranged by hand.
         *
         * On, it cannot be removed from the keyboard except by coming back here and turning it off.
         * That is the point of a switch rather than a key: the row is either part of the keyboard or
         * it is not, and it does not drift out of an arrangement by accident.
         */
        val maMagicRowShown = boolean(
            key = "dictate__ma_magic_row_shown",
            default = false,
        )

        val maMagicTargets = string(
            key = "dictate__ma_magic_targets",
            default = "1\u001D_\u001D\u001D\u001E1\u001D_\u001D\u001D\u001E1\u001DSend\u001D\u001Dsend\u001E1\u001D__\u001D\u001D\u001E1\u001D__\u001D\u001D\u001E1\u001DUse Image URL\u001D\u001DUse Image URL",
        )

        /**
         * Which crop of built-in magic defaults this install has already been shown.
         *
         * Zero means never — including every install that predates this preference, which is the
         * point: those are exactly the lists that missed everything shipped so far.
         */
        val maMagicDefaultsVersion = int(
            key = "dictate__ma_magic_defaults_version",
            default = 0,
        )

        /**
         * Whether a transcription reading "press something" presses instead of being typed.
         *
         * On by default, because it was asked for and because it cannot lose anything: a command
         * whose button is not on screen falls through and is written into the field exactly as it
         * would have been. The switch exists for the case the rule is simply not wanted — somebody
         * who dictates about pressing things would rather have their words than a keyboard second
         * guessing them, and that person should be able to say so once and be left alone.
         */
        /**
         * The magic finger term that a long press on volume down presses.
         *
         * A term rather than an action, so anything he has taught the finger can live on the key
         * without new code — and so the key and the row cannot disagree about what a word means.
         * Empty leaves the key as a plain volume key.
         */
        /**
         * Whether the word model has already read the dictation history.
         *
         * A flag rather than a check for an empty model: a model that has learned a little is still
         * worth backfilling, and running the pass twice would double every count and tilt the
         * ranking towards whatever happened to be in history.
         */
        /**
         * His own wording for Ctrl+F, or empty to use the one that ships.
         *
         * Empty rather than a copy of the built-in text, so the default can be improved later
         * without silently overwriting what he wrote — and so "reset" means clearing a field rather
         * than remembering what the original said.
         */
        val maFlowPrompt = string(
            key = "dictate__ma_flow_prompt",
            default = "",
        )

        /** The same, for Ctrl+P. */
        val maProofreadPrompt = string(
            key = "dictate__ma_proofread_prompt",
            default = "",
        )

        /**
         * His wording of the language line added after every Ctrl+P and Ctrl+F instruction, or
         * empty for the shipped one. `{language}` is replaced by Croatian or English. See
         * MaRewordPrompt.
         */
        val maLanguageRule = string(
            key = "dictate__ma_language_rule",
            default = "",
        )

        /** The same line for text whose language could not be told, or empty for the shipped one. */
        val maLanguageRuleUnsure = string(
            key = "dictate__ma_language_rule_unsure",
            default = "",
        )

        /**
         * Croatian spell check after every Croatian dictation (MaSpellcheck). On by default: he
         * called it mandatory, because AssemblyAI invents Croatian words that do not exist.
         */
        val maSpellcheckEnabled = boolean(
            key = "dictate__ma_spellcheck_enabled",
            default = false, // AI spell check rewrites the dictation: off until switched on (AUTO v2)
        )

        /** Its wordings, the same named-set shape as Ctrl+P and Ctrl+F (MaPrompts). */
        val maSpellcheckPrompt = string(
            key = "dictate__ma_spellcheck_prompt",
            default = "",
        )

        /**
         * The model each prompt runs on: a tier alias such as `claude-sonnet-latest`, a pinned id, or
         * a model on the phone (`phone:<id>`, MaPhoneModels). One each, so proofreading can run on
         * Sonnet while reflow stays on Haiku. Always a name: there is no "Default" choice any more
         * (§191); an empty value left by an old build is shown and replaced by the model it meant.
         */
        val maProofreadModel = string(key = "dictate__ma_proofread_model", default = "")
        val maFlowModel = string(key = "dictate__ma_flow_model", default = "")
        val maSpellcheckModel = string(key = "dictate__ma_spellcheck_model", default = "")

        /** E>H (MaTranslate, 2.11.2026): its wordings, as a named set, and its model. */
        val maTranslatePrompt = string(key = "dictate__ma_translate_prompt", default = "")
        val maTranslateModel = string(key = "dictate__ma_translate_model", default = "")
        /** The E>H key put next to the E key on his rows, once. */
        val maTranslateKeyAdded = boolean(key = "dictate__ma_translate_key_added", default = false)

        /**
         * Style after every dictation, the Mac's rules (MaPhoneStyle, §191). On, and inert until its
         * model is downloaded: he asked for it, and a switch that does nothing until then costs nothing.
         */
        val maPhoneStyleEnabled = boolean(key = "dictate__ma_phone_style_enabled", default = false)
        val maPhoneStylePrompt = string(key = "dictate__ma_phone_style_prompt", default = "")
        val maPhoneStyleModel = string(key = "dictate__ma_phone_style_model", default = MaPhoneModels.QWEN3_4B.choice)

        /**
         * The Anthropic credit he last read in the Console, in dollars, and when he entered it. The
         * API cannot be asked for it (MaClaudeCost); the app subtracts every logged request since.
         */
        val maClaudeBalanceUsd = string(key = "dictate__ma_claude_balance_usd", default = "")
        val maClaudeBalanceAt = long(key = "dictate__ma_claude_balance_at", default = 0L)

        /**
         * Spoken formatting marks he has switched OFF, comma separated.
         *
         * The ones OFF rather than the ones on, so a mark added in a later version is live by
         * default instead of invisible to anybody whose saved list predates it. Empty means all of
         * them work, which is also the right behaviour on a first run.
         */
        /**
         * Which language the badge is set to: "en", "hr", or "auto".
         *
         * Defaults to "hr" rather than "auto" so nothing changes for anybody upgrading: auto is a
         * thing he turns on, and a probe that started running unasked would spend a key he may not
         * have configured.
         */
        /**
         * Whether the suggestion row is allowed on screen at all.
         *
         * Its own switch rather than the existing suggestion setting, because that one governs
         * whether suggestions are COMPUTED. This is a curtain he draws over the row when it is in
         * the way, and drawing it should not throw away the word model's work or need anything
         * restarted to undo.
         */
        val maSuggestionsShown = boolean(
            key = "dictate__ma_suggestions_shown",
            default = true,
        )

        val maLanguageMode = string(
            key = "dictate__ma_language_mode",
            default = "hr",
        )

        /** The voice that reads Croatian. Empty means the shipped default, Lesya. */
        /**
         * How fast the reader speaks, as a multiplier of the natural rate.
         *
         * Applied to PLAYBACK rather than sent to Speechify, so changing it costs nothing and takes
         * effect on the next press instead of the next synthesis. The word timings stay correct
         * because they describe the audio and the playhead is scaled by the same number.
         *
         * Stored in TENTHS as an integer. Every other numeric preference here is an int, and a lone
         * float type would be the first of its kind for a number with one decimal place — the
         * stepper moves in tenths anyway, so nothing is lost.
         */
        /**
         * Where the reading is shown: "subtitle", "spacebar" or "off".
         *
         * The subtitle row is the default because it does not depend on the keyboard being the
         * thing on screen — the spacebar version borrows a key that only exists while the letters
         * are shown, and vanishes with them.
         */
        // maCopyRowOnKeyboard and maCopyRowOnDictate are gone.
        //
        // The first switched a second, appended copy of the copy row onto the typing keyboard while
        // `maEditRow` switched a different row entirely; the second switched nothing, because the
        // transcription view is fixed by design. The copy row now has one switch, `maEditRow`, and
        // it is the one the copy-row key on the feature row presses.
        //
        // Removing a declaration leaves whatever these hold in his preference file untouched and
        // unread, which is the harmless direction: nothing looks for them.

        /**
         * The copy row, stored apart from the three feature rows.
         *
         * Separate because `MaRows.parse` pads and truncates to exactly three — a fourth row in
         * that preference is silently dropped, and the source would look right while the keyboard
         * never showed it. Empty means the shipped default.
         */
        /**
         * Which profile is in force, by name. Empty when none has been chosen.
         *
         * A name rather than a file path, because the folder is the truth and a path would break
         * the moment a profile was renamed or the phone changed. A name that no longer exists
         * simply shows nothing as active, which is the right answer.
         */
        val maActiveProfile = string(
            key = "dictate__ma_active_profile",
            default = "",
        )

        /** The bucket row's arrangement. Its own row, like the copy row. */
        /** The reader row's arrangement. Its own row, like the copy row and the bucket row. */
        val maReaderRow = string(
            key = "dictate__ma_reader_row",
            default = "",
        )

        /**
         * Whether the reader row is showing.
         *
         * Off by default. It is the controls for a thing he is doing, not a thing he is always
         * doing, and a row of transport keys on a keyboard that is not reading is six dead keys.
         */
        val maReaderRowShown = boolean(
            key = "dictate__ma_reader_row_shown",
            default = false,
        )

        val maBucketRow = string(
            key = "dictate__ma_bucket_row",
            default = "",
        )

        /**
         * Whether the bucket row is showing.
         *
         * Off by default: it is a mode he turns on when he is collecting, and a row that appears
         * uninvited on every keyboard is a row he has to switch off before he can type.
         */
        val maBucketRowShown = boolean(
            key = "dictate__ma_bucket_row_shown",
            default = false,
        )

        val maCopyRow = string(
            key = "dictate__ma_copy_row",
            default = "",
        )

        val maReaderDisplay = string(
            key = "dictate__ma_reader_display",
            default = "subtitle",
        )

        /**
         * Whether tapping a voice speaks a sample.
         *
         * On by default, because a name and a nationality do not tell him what a voice sounds like
         * and hearing it is the entire basis for choosing. Switchable because a sample is a
         * synthesis: it costs characters, and once he has chosen he does not want to pay to change
         * his mind about the order of a list.
         */
        val maReaderPreviewVoices = boolean(
            key = "dictate__ma_reader_preview_voices",
            default = false,
        )

        /**
         * The colour of the word being spoken: `"yellow"` or `"white"`.
         *
         * Two, not a picker. A colour picker on this would be a thousand answers to a question with
         * two good ones — it has to carry against a near-black box and it has to be distinct from
         * the words around it, and everything else is either those two or worse than both.
         */
        /**
         * How the reading is drawn: `highlight`, `typewriter`, `karaoke`, `spotlight`, `void`.
         *
         * `oneword` was removed: it was the void with a smaller word and a box around it, which is
         * not a second effect, it is the same one done less well. A stored `oneword` falls through
         * to `highlight`, which is the safe direction — a page he can read rather than one word he
         * did not choose.
         *
         * Five, and every one of them obeys the rule that nothing moves. Each marks the spoken word
         * differently while the words around it stay exactly where they were, because a caption
         * that re-flows as it reads is a caption the eye chases instead of reads.
         */
        /**
         * Subtitle text size in sp. His eyes, his number.
         *
         * The same size in the small box and full screen. Full screen used to enlarge it as well,
         * which meant one gesture changed two things and he could not have the big view without the
         * big type — see §102.
         */
        val maReaderFontSize = int(
            key = "dictate__ma_reader_font_size",
            default = 17,
        )

        /**
         * The switchboard's own order, as ids separated by commas.
         *
         * Empty means the order it ships in. Stored like every other arrangement in this app,
         * because the switchboard is a list he uses constantly and the one he uses most should be
         * at the top — his rule, and the same one that governs the settings list and the rows.
         */
        val maSwitchboardOrder = string(
            key = "dictate__ma_switchboard_order",
            default = "",
        )

        val maReaderStyle = string(
            key = "dictate__ma_reader_style",
            default = "void",
        )

        /**
         * Whether the subtitle box fills the keyboard.
         *
         * A whole page at a time instead of three lines. Kept as a preference rather than as
         * transient state so it survives the keyboard being folded away mid-reading, which is
         * exactly when he would fold it away.
         */
        val maReaderFullscreen = boolean(
            key = "dictate__ma_reader_fullscreen",
            default = false,
        )

        /**
         * The highlight colour as `#RRGGBB`, chosen on the wheel.
         *
         * Empty means fall back to `maReaderHighlightColor` — the yellow-or-white choice that came
         * first. Kept rather than replaced so nobody's existing setting changes the day the wheel
         * arrives, and so the two words still mean something to somebody reading the file.
         */
        val maReaderHighlightHex = string(
            key = "dictate__ma_reader_highlight_hex",
            default = "",
        )

        val maReaderHighlightColor = string(
            key = "dictate__ma_reader_highlight_color",
            default = "yellow",
        )

        /**
         * Whether the spoken word is drawn bold.
         *
         * Off by default, and the reason is worth keeping: bold changes the WIDTH of a word, so the
         * line re-flows as the highlight passes through it and the text appears to breathe. That is
         * why weight was taken out when the highlight was first fixed. It is a choice rather than a
         * ban because he asked for it, and on a short page the movement is small.
         */
        val maReaderHighlightBold = boolean(
            key = "dictate__ma_reader_highlight_bold",
            default = false,
        )

        /**
         * Whether the spoken word is underlined.
         *
         * The one mark that changes nothing about the layout: an underline occupies space the line
         * already reserves for descenders, so it can be on with a white highlight on white text and
         * still be the only thing moving.
         */
        val maReaderHighlightUnderline = boolean(
            key = "dictate__ma_reader_highlight_underline",
            default = false,
        )

        val maReaderSpeed = int(
            key = "dictate__ma_reader_speed_tenths",
            default = 12,
        )

        val maReaderVoiceHr = string(
            key = "dictate__ma_reader_voice_hr",
            default = "lesya",
        )

        /** The voice that reads English. */
        /**
         * The one reading voice, by id. Empty means the first in [MaSpeechify.ALL_VOICES].
         *
         * Replaces the per-language pair, which are left declared and unread so a phone that stored
         * them reads back harmlessly rather than tripping a missing preference.
         */
        val maReaderVoice = string(
            key = "dictate__ma_reader_voice",
            default = "beatrice_32",
        )

        val maReaderVoiceEn = string(
            key = "dictate__ma_reader_voice_en",
            default = "beatrice_32",
        )

        val maVoiceFormatOff = string(
            key = "dictate__ma_voice_format_off",
            default = "",
        )

        val maNgramBackfilled = boolean(
            key = "dictate__ma_ngram_backfilled",
            default = false,
        )

        val maVolumeDownTerm = string(
            key = "dictate__ma_volume_down_term",
            default = "Send",
        )

        val maVoiceCommands = boolean(
            key = "dictate__ma_voice_commands",
            default = true,
        )

        // maBucketsEnabled is gone. The buckets are switched by whether C keys are on a row:
        // present means live, absent means nothing to capture into. It defaulted OFF, which is
        // how a fresh install shipped with grey C keys that caught nothing and an A key that
        // appeared broken, with the cure three screens away in a list.
        //
        // Whatever the preference file already holds is left there, unread.

        /**
         * Whether the volume keys drive recording and reading, or are just the volume.
         *
         * Changed ONLY by the volume-keys key on the feature row — deliberately not in the
         * switchboard, not on a settings screen, and not in the gestures list. See MaVolumeKeys.live
         * and VOLUME_KEYS.md: the switch that failed before failed because it was invisible and
         * sat among a dozen others that had just become draggable.
         */
        val maVolumeKeysLive = boolean(
            key = "dictate__ma_volume_keys_live",
            default = false,
        )

        /**
         * What he calls each key, learned from what he typed before picking it.
         *
         * Written only by the key picker, read only by it. See MaKeySearch: this is the layer that
         * makes the model unnecessary over time rather than a permanent dependency.
         */
        val maKeySearchMemory = string(
            key = "dictate__ma_key_search_memory",
            default = "",
        )

        /** Words seen once, unmarked, and unknown to both models. Waiting to be classified. */
        /**
         * Where the reading sits in its window: "top", "middle" or "bottom".
         *
         * Top by default, because the line he is listening to should be where the eye lands first
         * and because it leaves the space below for what is coming.
         */
        /**
         * Whether the reader keeps watching for new text after the screen runs out.
         *
         * On by default: he reads live chats, and a reader that stops at the bottom of a growing
         * answer has stopped in the middle of the thing he asked it to read.
         */
        /**
         * Whether the cloud reader keeps a log of what it read.
         *
         * Off by default. It writes files to internal storage without being asked, and a feature
         * that accumulates his conversations on disk should be one he switched on.
         */
        /**
         * Whether English dictations under two minutes use AssemblyAI's Dictation API.
         *
         * On by default: it is the same key and the same provider, it returns cleaned-up text in the
         * same request, and it is the endpoint built for exactly this job. Croatian and anything
         * over two minutes ignore it entirely.
         */
        /**
         * Corrected text, or exactly what he said.
         *
         * On: filler removed, punctuation and capitals applied, self-corrections resolved to what he
         * landed on. Off: the words as spoken, unpunctuated and lowercase.
         *
         * On by default, because that is what makes a dictation sendable — but it is a real choice
         * and it is worth having, because **corrected text is a model deciding what he meant.** Say
         * "meet at three, no, four" and it writes four. Usually right, and when it is wrong there is
         * nothing on screen to show that anything was changed.
         *
         * Only the Dictation API returns both texts; every other provider ignores this.
         */
        val maCorrectedDictation = boolean(
            key = "dictate__ma_corrected_dictation",
            default = false,
        )

        val maDictationApi = boolean(
            key = "dictate__ma_dictation_api",
            default = true,
        )

        val maCloudLogEnabled = boolean(
            key = "dictate__ma_cloud_log",
            default = true,
        )

        val maReaderWatch = boolean(
            key = "dictate__ma_reader_watch",
            default = false,
        )

        val maReaderAlign = string(
            key = "dictate__ma_reader_align",
            default = "top",
        )

        /**
         * What the last trim saved, as a percentage of the recording, or -1 before the first one.
         *
         * Shown under the Trim silent gaps switch. The switch has existed all along and told him
         * nothing about whether it was worth having — which is how it sat at a threshold that saved
         * nothing for months without anybody noticing. **A setting that cannot be evaluated is a
         * setting nobody can decide about**, and he asked to be able to experiment.
         */
        val trimLastSavedPercent = int(
            key = "dictate__trim_last_saved_percent",
            default = -1,
        )

        /**
         * Which of the two writing styles the reflow produces: "marko" or "yshai".
         *
         * MANTRA_MANIFEST/modules/prose-voice.md defines ONE voice, taken from Yshai Afterman's
         * letters, with exactly one deliberate deviation: Marko's prose uses normal sentence
         * capitalisation and correct apostrophes where Yshai writes his most personal letters in
         * lowercase and drops them.
         *
         * So these are not two voices. They are the same voice with that one deviation on or off,
         * and the setting says so in those words rather than offering two names as if they were
         * different characters. **A choice between two things that differ in one respect should
         * name the respect.**
         */
        val maProseStyle = string(
            key = "dictate__ma_prose_style",
            default = "marko",
        )

        /**
         * Models that have been retired and what replaced them, as `old=new` pairs.
         *
         * Written by MaModelHealing when a request fails because of the model and the provider's own
         * list offers a successor. Read before every request, so the repair happens once in the life
         * of the model rather than once a day.
         */
        val maModelRewires = string(
            key = "dictate__ma_model_rewires",
            default = "",
        )

        val maNgramPending = string(
            key = "dictate__ma_ngram_pending",
            default = "",
        )

        val maClipDelaySelect = int(
            key = "dictate__ma_clip_delay_select",
            default = 0,
        )
        val maClipDelayDelete = int(
            key = "dictate__ma_clip_delay_delete",
            default = 500,
        )
        val maClipDelayPaste = int(
            key = "dictate__ma_clip_delay_paste",
            default = 0,
        )

        /**
         * How wide one spacer is, as a fraction of an ordinary key, in tenths.
         *
         * Ten is exactly one key. Adjustable because a row pushed right by one key is often too
         * little and by two is too much, and the difference is a thumb's width rather than
         * something anybody can reason about in advance — so it is his to set by eye.
         */
        val maSpacerTenths = int(
            key = "dictate__ma_spacer_tenths",
            default = 10,
        )

        val maScrollPages = int(
            key = "dictate__ma_scroll_pages",
            default = 4,
        )

        val maClipCaptured = string(
            key = "dictate__ma_clip_captured",
            default = "",
        )

        // The pinned view, set by the pin in each view's top-left corner. "TEXT" or "TRANSCRIBE"
        // means always open there; empty means nothing is pinned and the last-used view wins, which
        // is the behaviour that existed before the pin and is still a reasonable default.
        // Seconds the last transcription took, and the format it used, shown under the meter and
        // stored with the history entry so the comparison survives being forgotten.
        val maLastSendMs = long(
            key = "dictate__ma_last_send_ms",
            default = 0L,
        )
        // Fast or slow, the two AssemblyAI paths. Default SLOW: fast is three times the price per hour,
        // so it is a thing to turn on deliberately rather than a thing to discover on a bill. It applies
        // only to AssemblyAI and only under two minutes; everything else falls back on its own.
        val maLastSendFormat = string(
            key = "dictate__ma_last_send_format",
            default = "",
        )
        // The transcribe view is the main one now and the typing keyboard accompanies it, so that is
        // where a fresh install opens. Still just a default: the pin overrides it, and unpinning
        // returns to whichever view was last used.
        // Instructions spoken to the little man, newest first, so they can be re-run by tapping
        // instead of said again. See MaLivePrompts.
        val maLivePromptHistory = string(
            key = "dictate__ma_live_prompt_history",
            default = "",
        )
        // Third pin state, the green one: hold the keyboard open. An app asking for it to close is
        // refused; only the system's own down-arrow puts it away. See FlorisImeService.onFinishInput
        // Which panel was showing when the keyboard was last closed. Written by FlorisImeService,
        // read back on the next open. Stored as the enum name so a
        // future panel that no longer exists simply fails to resolve and falls back to the keyboard.
        val maLastImeUiMode = string(
            key = "dictate__ma_last_ime_ui_mode",
            default = "TEXT",
        )
        // How many rows of prompt/revision buttons the legacy prompt strip shows (1 or 2, issue #194/#8).
        val legacyPromptRows = int(
            key = "dictate__legacy_prompt_rows",
            default = 1,
        )
        // Characters offered by the classic layout's Enter-key long-press popup (#196): hold Enter, swipe
        // left/right to pick one, release to insert. Up to 8 individual characters (whitespace ignored);
        // empty disables the popup so Enter just inserts a newline as usual.
        val enterLongPressChars = string(
            key = "dictate__enter_long_press_chars",
            default = ".,?!:;-…",
        )
        // Chat (rewording) provider id – any chat-capable ProviderRegistry id ("openai", "groq",
        // "openrouter", … or "custom"). Independent from the transcription provider.
        val rewordingProviderId = string(
            key = "dictate__rewording_provider_id",
            // Voice Type: Claude for rewording, out of the box.
            default = "anthropic",
        )
        // --- DEPRECATED flat rewording credential prefs (migration source only) ------------------
        // Kept solely for the one-time keyring import; live calls read providerAccounts instead.
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val rewordingApiKey = string(
            key = "dictate__rewording_api_key",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val rewordingModel = string(
            key = "dictate__rewording_model",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val rewordingCustomBaseUrl = string(
            key = "dictate__rewording_custom_base_url",
            default = "",
        )
        // System prompt appended to every rewording request: 0 = none, 1 = predefined (be-precise),
        // 2 = custom. See DictatePromptDefaults.SELECTION_*.
        val systemPromptSelection = int(
            key = "dictate__system_prompt_selection",
            default = 1,
        )
        val systemPromptCustom = string(
            key = "dictate__system_prompt_custom",
            default = "",
        )
        // Style prompt biasing the transcription model (roadmap 2.4): 0 = none, 1 = predefined
        // per-language punctuation/capitalization sentence, 2 = custom.
        val stylePromptSelection = int(
            key = "dictate__style_prompt_selection",
            default = 1,
        )
        val stylePromptCustom = string(
            key = "dictate__style_prompt_custom",
            default = "",
        )
        // Custom vocabulary (roadmap 11.12): names/jargon appended to the transcription prompt so the
        // speech model spells them correctly. Comma- or newline-separated; empty = unused. Applied on
        // top of whatever style prompt (none/predefined/custom) is active.
        val customWords = string(
            key = "dictate__custom_words",
            default = "",
        )
        // Custom mappings (issue #129): deterministic find-and-replace applied to the finished transcript
        // before it is inserted — exact and token-free, unlike the prompt-hint customWords above.
        val customMappings = custom(
            key = "dictate__custom_mappings",
            default = DictateMappings.Empty,
            serializer = DictateMappings.Serializer,
        )
        // Run the spoken-formatting-cues → Markdown pass automatically on every transcript.
        val autoFormattingEnabled = boolean(
            key = "dictate__auto_formatting_enabled",
            default = false,
        )
    }

    val dictionary = Dictionary()
    inner class Dictionary {
        val enableSystemUserDictionary = boolean(
            key = "suggestion__enable_system_user_dictionary",
            default = true,
        )
        val enableFlorisUserDictionary = boolean(
            key = "suggestion__enable_floris_user_dictionary",
            default = true,
        )
    }

    val emoji = Emoji()
    inner class Emoji {
        val preferredSkinTone = enum(
            key = "emoji__preferred_skin_tone",
            default = EmojiSkinTone.DEFAULT,
        )
        val preferredHairStyle = enum(
            key = "emoji__preferred_hair_style",
            default = EmojiHairStyle.DEFAULT,
        )
        val historyEnabled = boolean(
            key = "emoji__history_enabled",
            default = true,
        )
        val historyData = custom(
            key = "emoji__history_data",
            default = EmojiHistory.Empty,
            serializer = EmojiHistory.Serializer,
        )
        val historyPinnedUpdateStrategy = enum(
            key = "emoji__history_pinned_update_strategy",
            default = EmojiHistory.UpdateStrategy.MANUAL_SORT_PREPEND,
        )
        val historyPinnedMaxSize = int(
            key = "emoji__history_pinned_max_size",
            default = EmojiHistory.MaxSizeUnlimited,
        )
        val historyRecentUpdateStrategy = enum(
            key = "emoji__history_recent_update_strategy",
            default = EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND,
        )
        val historyRecentMaxSize = int(
            key = "emoji__history_recent_max_size",
            default = 90,
        )
        val suggestionEnabled = boolean(
            key = "emoji__suggestion_enabled",
            default = true,
        )
        val suggestionType = enum(
            key = "emoji__suggestion_type",
            default = EmojiSuggestionType.LEADING_COLON,
        )
        val suggestionUpdateHistory = boolean(
            key = "emoji__suggestion_update_history",
            default = true,
        )
        val suggestionCandidateShowName = boolean(
            key = "emoji__suggestion_candidate_show_name",
            default = false,
        )
        val suggestionQueryMinLength = int(
            key = "emoji__suggestion_query_min_length",
            default = 3,
        )
        val suggestionCandidateMaxCount = int(
            key = "emoji__suggestion_candidate_max_count",
            default = 5,
        )
    }

    val gif = Gif()
    inner class Gif {
        val enabled = boolean(
            key = "gif__enabled",
            default = false,
        )
        // Bring-your-own KLIPY API key (see KlipyGifProvider). Empty = GIF search disabled.
        val klipyApiKey = string(
            key = "gif__klipy_api_key",
            default = "",
        )
        val contentFilter = enum(
            key = "gif__content_filter",
            default = GifContentFilter.HIGH,
        )
        // Stable per-install id sent to KLIPY for relevance/localization (generated on first use).
        val customerId = string(
            key = "gif__customer_id",
            default = "",
        )
        // Recently searched terms + recently inserted GIFs, for quick re-access.
        val history = custom(
            key = "gif__history",
            default = GifHistory.Empty,
            serializer = GifHistory.Serializer,
        )
    }

    val gestures = Gestures()
    inner class Gestures {
        /**
         * Talk to Type: swipe toward a symbol printed on a key to type it, rather than long
         * pressing and choosing from the popup. Off by default so nobody's muscle memory changes
         * without asking.
         */
        val maSwipeToSymbol = boolean(
            key = "gestures__ma_swipe_to_symbol",
            default = false,
        )


        val swipeUp = enum(
            key = "gestures__swipe_up",
            default = SwipeAction.SHIFT,
        )
        val swipeDown = enum(
            key = "gestures__swipe_down",
            default = SwipeAction.HIDE_KEYBOARD,
        )
        val swipeLeft = enum(
            key = "gestures__swipe_left",
            default = SwipeAction.SWITCH_TO_NEXT_SUBTYPE,
        )
        val swipeRight = enum(
            key = "gestures__swipe_right",
            default = SwipeAction.SWITCH_TO_PREV_SUBTYPE,
        )
        val spaceBarSwipeUp = enum(
            key = "gestures__space_bar_swipe_up",
            default = SwipeAction.NO_ACTION,
        )
        val spaceBarSwipeLeft = enum(
            key = "gestures__space_bar_swipe_left",
            default = SwipeAction.MOVE_CURSOR_LEFT,
        )
        val spaceBarSwipeRight = enum(
            key = "gestures__space_bar_swipe_right",
            default = SwipeAction.MOVE_CURSOR_RIGHT,
        )
        // Long press on the space bar picks the keyboard layout, from the ones enabled in settings.
        // It briefly swapped the dictate view instead, which was my misreading: swapping view now
        // belongs to the microphone key, and the space bar is where a layout is chosen on every
        // other keyboard. Subtype picker rather than the system input method picker, so it offers
        // Croatian and English rather than every keyboard installed on the phone.
        val spaceBarLongPress = enum(
            key = "gestures__space_bar_long_press",
            default = SwipeAction.SHOW_SUBTYPE_PICKER,
        )
        val deleteKeySwipeLeft = enum(
            key = "gestures__delete_key_swipe_left",
            default = SwipeAction.DELETE_CHARACTERS_PRECISELY,
        )
        val deleteKeyLongPress = enum(
            key = "gestures__delete_key_long_press",
            default = SwipeAction.DELETE_CHARACTER,
        )
        val swipeDistanceThreshold = int(
            key = "gestures__swipe_distance_threshold",
            default = 32,
        )
        val swipeVelocityThreshold = int(
            key = "gestures__swipe_velocity_threshold",
            default = 1900,
        )
    }

    val glide = Glide()
    inner class Glide {
        // Off, and no longer exposed. Glide typing is for typing, and this is a keyboard whose
        // words arrive by voice; the setting was a section of a screen nobody used.
        val enabled = boolean(
            key = "glide__enabled",
            default = false,
        )
        val showTrail = boolean(
            key = "glide__show_trail",
            default = true,
        )
        val trailDuration = int(
            key = "glide__trail_fade_duration",
            default = 200,
        )
        val showPreview = boolean(
            key = "glide__show_preview",
            default = true,
        )
        val previewRefreshDelay = int(
            key = "glide__preview_refresh_delay",
            default = 150,
        )
        val immediateBackspaceDeletesWord = boolean(
            key = "glide__immediate_backspace_deletes_word",
            default = true,
        )
    }

    val inputFeedback = InputFeedback()
    inner class InputFeedback {
        val audioEnabled = boolean(
            key = "input_feedback__audio_enabled",
            default = true,
        )
        val audioActivationMode = enum(
            key = "input_feedback__audio_activation_mode",
            default = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
        )
        val audioVolume = int(
            key = "input_feedback__audio_volume",
            default = 50,
        )
        val audioFeatKeyPress = boolean(
            key = "input_feedback__audio_feat_key_press",
            default = true,
        )
        val audioFeatKeyLongPress = boolean(
            key = "input_feedback__audio_feat_key_long_press",
            default = false,
        )
        val audioFeatKeyRepeatedAction = boolean(
            key = "input_feedback__audio_feat_key_repeated_action",
            default = false,
        )
        val audioFeatGestureSwipe = boolean(
            key = "input_feedback__audio_feat_gesture_swipe",
            default = false,
        )
        val audioFeatGestureMovingSwipe = boolean(
            key = "input_feedback__audio_feat_gesture_moving_swipe",
            default = false,
        )

        val hapticEnabled = boolean(
            key = "input_feedback__haptic_enabled",
            default = true,
        )
        val hapticActivationMode = enum(
            key = "input_feedback__haptic_activation_mode",
            default = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
        )
        val hapticVibrationMode = enum(
            key = "input_feedback__haptic_vibration_mode",
            default = HapticVibrationMode.USE_VIBRATOR_DIRECTLY,
        )
        val hapticVibrationDuration = int(
            key = "input_feedback__haptic_vibration_duration",
            default = 10,
        )
        val hapticVibrationStrength = int(
            key = "input_feedback__haptic_vibration_strength",
            default = 5,
        )
        val hapticFeatKeyPress = boolean(
            key = "input_feedback__haptic_feat_key_press",
            default = true,
        )
        val hapticFeatKeyLongPress = boolean(
            key = "input_feedback__haptic_feat_key_long_press",
            default = false,
        )
        val hapticFeatKeyRepeatedAction = boolean(
            key = "input_feedback__haptic_feat_key_repeated_action",
            default = true,
        )
        val hapticFeatGestureSwipe = boolean(
            key = "input_feedback__haptic_feat_gesture_swipe",
            default = false,
        )
        val hapticFeatGestureMovingSwipe = boolean(
            key = "input_feedback__haptic_feat_gesture_moving_swipe",
            default = true,
        )
    }

    val internal = Internal()
    inner class Internal {
        val homeIsBetaToolboxCollapsed = boolean(
            key = "internal__home_is_beta_toolbox_collapsed_040a01",
            default = false,
        )
        val isImeSetUp = boolean(
            key = "internal__is_ime_set_up",
            default = false,
        )
        // One-shot signal set by the onboarding's optional floating-button step: completing setup flips
        // [isImeSetUp], which rebuilds the nav graph and resets the back stack to Home; this flag lets
        // FlorisAppActivity then navigate on to the floating-button settings once that reset has settled.
        val openFloatingButtonAfterSetup = boolean(
            key = "internal__open_floating_button_after_setup",
            default = false,
        )
        // Newline-separated most-recent settings-search queries (newest first), for the search screen's
        // recent-search chips (issue #187).
        val settingsSearchHistory = string(
            key = "internal__settings_search_history",
            default = "",
        )
        val versionOnInstall = string(
            key = "internal__version_on_install",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastUse = string(
            key = "internal__version_last_use",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastChangelog = string(
            key = "internal__version_last_changelog",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastWhatsNew = string(
            key = "internal__version_last_whats_new",
            default = VersionName.DEFAULT_RAW,
        )
        val notificationPermissionState = enum(
            key = "internal__notification_permission_state",
            default = NotificationPermissionState.NOT_SET,
        )
    }

    val keyboard = Keyboard()
    inner class Keyboard {
        val windowConfig = custom(
            key = "keyboard__window_config",
            default = emptyMap(),
            serializer = ImeWindowConfig.ByTypeSerializer,
        )
        val numberRow = boolean(
            key = "keyboard__number_row",
            default = false,
        )
        val hintedNumberRowEnabled = boolean(
            key = "keyboard__hinted_number_row_enabled",
            default = true,
        )
        val hintedNumberRowMode = enum(
            key = "keyboard__hinted_number_row_mode",
            default = KeyHintMode.SMART_PRIORITY,
        )
        val hintedSymbolsEnabled = boolean(
            key = "keyboard__hinted_symbols_enabled",
            default = true,
        )
        val hintedSymbolsMode = enum(
            key = "keyboard__hinted_symbols_mode",
            default = KeyHintMode.SMART_PRIORITY,
        )
        // Off. This is the smiley beside the space bar, and an emoji panel is one of the things a
        // voice keyboard does not need; the key it was occupying is worth more than the panel.
        val utilityKeyEnabled = boolean(
            key = "keyboard__utility_key_enabled",
            default = false,
        )
        val utilityKeyAction = enum(
            key = "keyboard__utility_key_action",
            default = UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
        )
        val spaceBarMode = enum(
            key = "keyboard__space_bar_display_mode",
            default = SpaceBarMode.CURRENT_LANGUAGE,
        )
        val capitalizationBehavior = enum(
            key = "keyboard__capitalization_behavior",
            default = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
        )
        val fontSizeMultiplierPortrait = int(
            key = "keyboard__font_size_multiplier_portrait",
            default = 100,
        )
        val fontSizeMultiplierLandscape = int(
            key = "keyboard__font_size_multiplier_landscape",
            default = 100,
        )
        val landscapeInputUiMode = enum(
            key = "keyboard__landscape_input_ui_mode",
            default = LandscapeInputUiMode.DYNAMICALLY_SHOW,
        )
        val keySpacingVertical = int(
            key = "keyboard__key_spacing_vertical",
            default = 100,
        )
        val keySpacingHorizontal = int(
            key = "keyboard__key_spacing_horizontal",
            default = 100,
        )
        val popupEnabled = boolean(
            key = "keyboard__popup_enabled",
            default = true,
        )
        val mergeHintPopupsEnabled = boolean(
            key = "keyboard__merge_hint_popups_enabled",
            default = false,
        )
        val longPressDelay = int(
            key = "keyboard__long_press_delay",
            default = 300,
        )
        val spaceBarSwitchesToCharacters = boolean(
            key = "keyboard__space_bar_switches_to_characters",
            default = true,
        )
        val incognitoDisplayMode = enum(
            key = "keyboard__incognito_indicator",
            default = IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD,
        )

        fun keyHintConfiguration(): KeyHintConfiguration {
            return KeyHintConfiguration(
                numberHintMode = when {
                    hintedNumberRowEnabled.get() -> hintedNumberRowMode.get()
                    else -> KeyHintMode.DISABLED
                },
                symbolHintMode = when {
                    hintedSymbolsEnabled.get() -> hintedSymbolsMode.get()
                    else -> KeyHintMode.DISABLED
                },
                mergeHintPopups = mergeHintPopupsEnabled.get(),
            )
        }
    }

    val localization = Localization()
    inner class Localization {
        val displayLanguageNamesIn = enum(
            key = "localization__display_language_names_in",
            default = DisplayLanguageNamesIn.SYSTEM_LOCALE,
        )
        val displayKeyboardLabelsInSubtypeLanguage = boolean(
            key = "localization__display_keyboard_labels_in_subtype_language",
            default = false,
        )
        val activeSubtypeId = long(
            key = "localization__active_subtype_id",
            default = Subtype.DEFAULT.id,
        )
        val subtypes = string(
            key = "localization__subtypes",
            default = "[{\"id\":1786452815014,\"primaryLocale\":\"en-US\",\"secondaryLocales\":[],\"nlpProviders\":{\"spelling\":\"org.florisboard.nlp.providers.latin\",\"suggestion\":\"org.florisboard.nlp.providers.latin\"},\"composer\":\"org.florisboard.composers:appender\",\"currencySet\":\"org.florisboard.currencysets:dollar\",\"punctuationRule\":\"org.florisboard.localization:default\",\"popupMapping\":\"org.florisboard.localization:en\",\"layoutMap\":{\"characters\":\"org.florisboard.layouts:qwerty\",\"symbols\":\"org.florisboard.layouts:western\",\"symbols2\":\"org.florisboard.layouts:western\",\"numeric\":\"org.florisboard.layouts:western_arabic\",\"numericAdvanced\":\"org.florisboard.layouts:western_arabic\",\"numericRow\":\"org.florisboard.layouts:western_arabic\",\"phone\":\"org.florisboard.layouts:telpad\",\"phone2\":\"org.florisboard.layouts:telpad\"}}]",
        )
    }

    val other = Other()
    inner class Other {
        val settingsTheme = enum(
            key = "other__settings_theme",
            default = AppTheme.AUTO,
        )
        val accentColor = custom(
            key = "other__accent_color",
            default = Color(0xFF30B7E6), // Dictate light blue
            serializer = ColorPreferenceSerializer,
        )
        val settingsLanguage = string(
            key = "other__settings_language",
            default = "auto",
        )
        val showAppIcon = boolean(
            key = "other__show_app_icon",
            default = true,
        )
    }

    val physicalKeyboard = PhysicalKeyboard()
    inner class PhysicalKeyboard {
        val showOnScreenKeyboard = boolean(
            key = "physical_keyboard__show_on_screen_keyboard",
            default = false,
        )
    }

    val smartbar = Smartbar()
    inner class Smartbar {
        val enabled = boolean(
            key = "smartbar__enabled",
            default = true,
        )
        val layout = enum(
            key = "smartbar__layout",
            default = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
        )
        val actionArrangement = custom(
            key = "smartbar__action_arrangement",
            default = QuickActionArrangement.Default,
            serializer = QuickActionArrangement.Serializer,
        )
        val flipToggles = boolean(
            key = "smartbar__flip_toggles",
            default = false,
        )
        val sharedActionsExpanded = boolean(
            key = "smartbar__shared_actions_expanded",
            default = true,
        )
        @Deprecated("Always enabled due to UX issues")
        val sharedActionsAutoExpandCollapse = boolean(
            key = "smartbar__shared_actions_auto_expand_collapse",
            default = true,
        )
        val sharedActionsExpandWithAnimation = boolean(
            key = "smartbar__shared_actions_expand_with_animation",
            default = true,
        )
        val extendedActionsExpanded = boolean(
            key = "smartbar__extended_actions_expanded",
            default = false,
        )
        val extendedActionsPlacement = enum(
            key = "smartbar__extended_actions_placement",
            default = ExtendedActionsPlacement.ABOVE_CANDIDATES,
        )
    }

    val spelling = Spelling()
    inner class Spelling {
        val languageMode = enum(
            key = "spelling__language_mode",
            default = SpellingLanguageMode.USE_KEYBOARD_SUBTYPES,
        )
        val useContacts = boolean(
            key = "spelling__use_contacts",
            default = true,
        )
        val useUdmEntries = boolean(
            key = "spelling__use_udm_entries",
            default = true,
        )
    }

    val suggestion = Suggestion()
    inner class Suggestion {
        val api30InlineSuggestionsEnabled = boolean(
            key = "suggestion__api30_inline_suggestions_enabled",
            default = true,
        )
        val enabled = boolean(
            key = "suggestion__enabled",
            default = false,
        )
        // Autocorrect the typed word on space/punctuation when it looks like a typo (issue #127). Gated by
        // [enabled]; on by default like other keyboards, with its own switch so suggestions can stay on
        // without autocorrect.
        val autoCorrect = boolean(
            key = "suggestion__auto_correct",
            default = false, // AUTO: off until he switches it on
        )
        // Multilingual typing (issue #190): accept words from every configured keyboard language, not just
        // the active one, so a bilingual's second-language words aren't flagged as typos or autocorrected
        // away. Opt-in; leaves single-language behavior unchanged when off.
        val multilingualTyping = boolean(
            key = "suggestion__multilingual_typing",
            default = false,
        )
        // Next-word prediction from the bigram tables (issue #245). Only ever offers words once a previous
        // word exists — never on an empty field, so opening the keyboard still shows the quick actions.
        val nextWordPrediction = boolean(
            key = "suggestion__next_word_prediction",
            default = true,
        )
        val displayMode = enum(
            key = "suggestion__display_mode",
            default = CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
        )
        val incognitoMode = enum(
            key = "suggestion__incognito_mode",
            default = IncognitoMode.DYNAMIC_ON_OFF,
        )
        // Internal pref
        val forceIncognitoModeFromDynamic = boolean(
            key = "suggestion__force_incognito_mode_from_dynamic",
            default = false,
        )
    }

    val theme = Theme()
    inner class Theme {
        val mode = enum(
            key = "theme__mode",
            default = ThemeMode.FOLLOW_SYSTEM,
        )
        // Sunrise is the default now, day and night both, so it is what a fresh install looks like
        // rather than something to be found in a list. The original author's themes are all still
        // there and still selectable, which was always the deal.
        val dayThemeId = custom(
            key = "theme__day_theme_id",
            default = extCoreTheme("sunrise"),
            serializer = ExtensionComponentName.Serializer,
        )
        val nightThemeId = custom(
            key = "theme__night_theme_id",
            default = extCoreTheme("sunrise"),
            serializer = ExtensionComponentName.Serializer,
        )
        // Gold, not amber. This preference, not the stylesheet, is what actually paints the enter
        // key and the accent chips, which is why they stayed orange through several passes of
        // editing the theme: the stylesheet was being overridden by a colour stored here.
        val accentColor = custom(
            key = "theme__accent_color",
            default = Color(0xFFE8B15C),
            serializer = ColorPreferenceSerializer,
        )
        val sunriseTime = localTime(
            key = "theme__sunrise_time",
            default = LocalTime(6, 0),
        )
        val sunsetTime = localTime(
            key = "theme__sunset_time",
            default = LocalTime(18, 0),
        )
        val editorColorRepresentation = enum(
            key = "theme__editor_color_representation",
            default = ColorRepresentation.HEX,
        )
        val editorDisplayKbdAfterDialogs = enum(
            key = "theme__editor_display_kbd_after_dialogs",
            default = DisplayKbdAfterDialogs.REMEMBER,
        )
        val editorLevel = enum(
            key = "theme__editor_level",
            default = SnyggLevel.ADVANCED,
        )
    }

    override fun migrate(entry: PreferenceMigrationEntry): PreferenceMigrationEntry {
        return when (entry.key) {

            // Migrate media prefs to emoji prefs
            // Keep migration rule until: 0.6 dev cycle
            "media__emoji_recently_used" -> {
                val emojiValues = entry.rawValue.split(";")
                val recent = emojiValues.map {
                    dev.patrickgold.florisboard.ime.media.emoji.Emoji(it, "", emptyList())
                }
                val data = EmojiHistory(emptyList(), recent)
                entry.transform(key = "emoji__history_data", rawValue = Json.encodeToString(data))
            }
            "media__emoji_recently_used_max_size" -> {
                entry.transform(key = "emoji__history_recent_max_size")
            }

            // Migrate advanced prefs to other prefs
            // Keep migration rules until: 0.7 dev cycle
            "advanced__settings_theme" -> {
                entry.transform(key = "other__settings_theme")
            }
            "advanced__accent_color" -> {
                entry.transform(key = "other__accent_color")
            }
            "advanced__settings_language" -> {
                entry.transform(key = "other__settings_language")
            }
            "advanced__show_app_icon" -> {
                entry.transform(key = "other__show_app_icon")
            }
            "advanced__incognito_mode" -> {
                entry.transform(key = "suggestion__incognito_mode")
            }
            "advanced__force_incognito_mode_from_dynamic" -> {
                entry.transform(key = "suggestion__force_incognito_mode_from_dynamic")
            }
            // The clipboard suggestion feature is gone, so these two are dropped rather than
            // migrated: transforming them would rename an old key into a new one that no longer
            // exists, leaving rubbish in the store for a setting nothing can read.
            "suggestion__clipboard_content_enabled",
            "suggestion__clipboard_content_timeout" -> entry.reset()

            //Migrate one hand mode prefs keep until: 0.7 dev cycle
            "keyboard__one_handed_mode" -> {
                if (entry.rawValue == "OFF") {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "smartbar__action_arrangement" -> {
                // The one-handed migration went with the feature; there is nothing left to migrate
                // those actions into.
                val arrangement = QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(entry.rawValue)
                var newArrangement = arrangement
                if (QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.FORWARD_DELETE) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.FORWARD_DELETE))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.IME_HIDE_UI) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.IME_HIDE_UI))
                    )
                }
                // The upstream pass that put the floating window button back on every migration is
                // gone with the feature. This is why "Floating" kept reappearing in the panel after
                // being retired: one migration was removing it and another was adding it again.
                if (QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE))
                    )
                }
                val json = QuickActionJsonConfig.encodeToString(newArrangement.distinct())
                entry.transform(rawValue = json)
            }

            // Migrate theme editor fine-tuning
            // Keep migration rule until: 0.6 dev cycle
            "theme__editor_display_colors_as" -> {
                val colorRepresentation = when (entry.rawValue) {
                    "RGBA" -> ColorRepresentation.RGB
                    else -> ColorRepresentation.HEX
                }
                entry.transform(
                    key = "theme__editor_color_representation",
                    rawValue = colorRepresentation.name,
                )
            }

            // Migrate clipboard history pref names
            // Keep migration rules until: 0.7 dev cycle
            "clipboard__sync_to_floris", "clipboard__sync_to_system" -> {
                entry.transform(
                    type = PreferenceType.string(),
                    rawValue = when (entry.rawValue) {
                        "true" -> ClipboardSyncBehavior.ALL_EVENTS.name
                        "false" -> ClipboardSyncBehavior.NO_EVENTS.name
                        else -> entry.rawValue
                    },
                )
            }
            "clipboard__num_history_grid_columns_portrait" -> {
                entry.transform(key = "clipboard__history_num_grid_columns_portrait")
            }
            "clipboard__num_history_grid_columns_landscape" -> {
                entry.transform(key = "clipboard__history_num_grid_columns_landscape")
            }
            "clipboard__clean_up_old" -> {
                entry.transform(key = "clipboard__history_auto_clean_old_enabled")
            }
            "clipboard__clean_up_after" -> {
                entry.transform(key = "clipboard__history_auto_clean_old_after")
            }
            "clipboard__auto_clean_sensitive" -> {
                entry.transform(key = "clipboard__history_auto_clean_sensitive_enabled")
            }
            "clipboard__auto_clean_sensitive_after" -> {
                entry.transform(key = "clipboard__history_auto_clean_sensitive_after")
            }
            "clipboard__limit_history_size" -> {
                entry.transform(key = "clipboard__history_size_limit_enabled")
            }
            "clipboard__max_history_size" -> {
                entry.transform(key = "clipboard__history_size_limit")
            }
            "clipboard__clear_primary_clip_deletes_last_item" -> {
                entry.transform(key = "clipboard__clear_primary_clip_affects_history_if_unpinned")
            }

            // Migrate key spacing rules
            // Keep migration rules until: 0.8 dev cycle
            "keyboard__key_spacing_horizontal" -> {
                if (entry.type.isFloat()) {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__key_spacing_vertical" -> {
                if (entry.type.isFloat()) {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }

            // Default: keep entry
            else -> entry.keepAsIs()
        }
    }
}
