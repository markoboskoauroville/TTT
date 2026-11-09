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

package dev.patrickgold.florisboard

import dev.patrickgold.florisboard.dictate.MaFlow
import dev.patrickgold.florisboard.dictate.MaReprompt
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.res.Configuration
import android.inputmethodservice.ExtractEditText
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import android.view.WindowManager
import android.media.AudioManager
import android.widget.Toast
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaLog
import dev.patrickgold.florisboard.ime.text.keyboard.MaCursorPad
import dev.patrickgold.florisboard.dictate.MaMagicTargets
import dev.patrickgold.florisboard.dictate.MaVolumeKeys
import dev.patrickgold.florisboard.dictate.nlp.MaDictationCorrections
import dev.patrickgold.florisboard.dictate.nlp.MaTypingLanguage
import dev.patrickgold.florisboard.dictate.nlp.MaNgram
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.input.InputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.isFullscreenInputRequired
import dev.patrickgold.florisboard.ime.landscapeinput.ExtractedInputRootView
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.lifecycle.LifecycleInputMethodService
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.ime.theme.WallpaperChangeReceiver
import dev.patrickgold.florisboard.ime.window.ImeRootView
import dev.patrickgold.florisboard.ime.window.ImeWindowController
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.debugSummarize
import dev.patrickgold.florisboard.lib.util.launchActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidInternalR
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.kotlin.collectLatestIn
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisImeService] class. This is needed as certain actions (request hide, switch to
 * another input method, getting the editor instance / input connection, etc.) can only be performed by an IME
 * service class and no context-bound managers. This reference is exclusively used by the companion helper methods
 * of [FlorisImeService], which provide a safe and memory-leak-free way of performing certain actions on the Floris
 * input method service instance.
 */
private var FlorisImeServiceReference = WeakReference<FlorisImeService?>(null)

/**
 * Core class responsible for linking together all managers and UI composables to provide an IME service. Sets
 * up the window and context to be lifecycle-aware, so LiveData and Jetpack Compose can be used without issues.
 */
class FlorisImeService : LifecycleInputMethodService() {
    companion object {
        private val InlineSuggestionUiSmallestSize = Size(0, 0)
        private val InlineSuggestionUiBiggestSize = Size(Int.MAX_VALUE, Int.MAX_VALUE)

        fun currentInputConnection(): InputConnection? {
            return FlorisImeServiceReference.get()?.currentInputConnection
        }

        /**
         * Whether the keyboard is on screen and taking the volume keys itself.
         *
         * The accessibility service can hear those keys everywhere, including here, so without this
         * a single press would be handled twice — once by each — and a hold would start a recording
         * and immediately stop it. The rule is simply that the nearer handler wins: if the keyboard
         * is up it owns the keys, and the global one stands down.
         */
        fun ownsVolumeKeys(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return ims.isInputViewShown
        }

        fun inputFeedbackController(): InputFeedbackController? {
            return FlorisImeServiceReference.get()?.inputFeedbackController
        }

        /**
         * Hides the IME and launches [FlorisAppActivity]. When [deepLinkPath] is given (e.g.
         * `"settings/dictate/prompts"`), the activity opens directly on that screen via the same
         * `ui://florisboard/...` deep-link mechanism used for external links.
         */
        fun launchSettings(deepLinkPath: String? = null) {
            val ims = FlorisImeServiceReference.get() ?: return
            ims.requestHideSelf(0)
            ims.launchActivity(FlorisAppActivity::class) {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (deepLinkPath != null) {
                    it.action = Intent.ACTION_VIEW
                    it.addCategory(Intent.CATEGORY_BROWSABLE)
                    it.data = Uri.parse("ui://florisboard/$deepLinkPath")
                }
            }
        }

        fun showUi() {
            val ims = FlorisImeServiceReference.get() ?: return
            ims.showUi()
        }

        fun hideUi() {
            val ims = FlorisImeServiceReference.get() ?: return
            // The user asked for this one. The pin's re-show must not undo a deliberate dismissal —
            // a keyboard that will not go away when told is a far worse fault than one that goes
            // away too eagerly.
            ims.maUserRequestedHide = true
            ims.hideUi()
        }

        fun switchToPrevInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return ims.switchToPrevInputMethod()
        }

        fun switchToNextInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return ims.switchToNextInputMethod()
        }

        /**
         * Switches to a named input method.
         *
         * Only the running IME can do this: the platform call wants the service's own window token,
         * which nothing outside the service has. Android 28 and later expose a token-free overload;
         * below that the token is taken from the service window. Returns false if neither path is
         * available, so the caller can fall back to the picker instead of a tap that does nothing.
         */
        fun switchToInputMethod(imeId: String): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return runCatching {
                if (AndroidVersion.ATLEAST_API28_P) {
                    ims.switchInputMethod(imeId)
                    true
                } else {
                    val imm = ims.systemServiceOrNull(InputMethodManager::class)
                    val token = ims.window.window?.attributes?.token ?: return@runCatching false
                    @Suppress("DEPRECATION")
                    imm?.setInputMethod(token, imeId)
                    true
                }
            }.getOrDefault(false)
        }

        fun showImePicker(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return InputMethodUtils.showImePicker(ims)
        }

        fun windowControllerOrNull(): ImeWindowController? {
            val ims = FlorisImeServiceReference.get() ?: return null
            return ims.windowController
        }
    }

    fun hideUi() {
        requestHideSelf(0)
    }

    /**
     * Show the Ime UI
     *
     * Note: This function can be replaced with a `requestShowSelf(0)`
     * call once we've set the minApiLevel to 28 (Android 9)
     */
    fun showUi() {
        if (AndroidVersion.ATLEAST_API28_P) {
            requestShowSelf(0)
        } else {
            @Suppress("DEPRECATION")
            systemServiceOrNull(InputMethodManager::class)
                ?.showSoftInputFromInputMethod(currentInputBinding.connectionToken, 0)
        }
    }


    /**
     * Switch to previous input method
     *
     * Note: This function can be replaced with a `switchToPreviousInputMethod()`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToPrevInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToPreviousInputMethod()
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToLastInputMethod(window.attributes.token) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the previous IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to next input method
     *
     * Note: This function can be replaced with a `switchToNextInputMethod(false)`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToNextInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToNextInputMethod(false)
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToNextInputMethod(window.attributes.token, false) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the next IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }


    private val prefs by FlorisPreferenceStore
    val editorInstance by editorInstance()
    private val keyboardManager by keyboardManager()
    private val nlpManager by nlpManager()
    private val subtypeManager by subtypeManager()
    private val themeManager by themeManager()

    val windowController = ImeWindowController(prefs, lifecycleScope)

    /** Set when the user dismissed the keyboard themselves, so the pin does not undo it. */
    @Volatile
    private var maUserRequestedHide = false


    /** When the pin last pushed the keyboard back up, for the governor in [maReshowIfPinned]. */
    private val maPinReshowAt = ArrayDeque<Long>()

    private val activeState get() = keyboardManager.activeState
    val inputFeedbackController by lazy { InputFeedbackController.new(this) }
    private val systemLocalesFlow = MutableStateFlow(LocaleList())
    var resourcesContext by mutableStateOf(this as Context)
        private set

    private val wallpaperChangeReceiver = WallpaperChangeReceiver()

    init {
        setTheme(R.style.FlorisImeTheme)
    }

    /**
     * Suggestions come back the moment the trackpad closes.
     *
     * Registered once here rather than at each of the two places the pad is drawn, so the two
     * cannot end up restoring different things.
     */
    private fun maWireCursorPad() {
        MaCursorPad.onClosed = {
            runCatching { keyboardManager.resetSuggestions(editorInstance.activeContent) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dev.patrickgold.florisboard.dictate.MaReSkill.attach(this)
        dev.patrickgold.florisboard.dictate.MaSpelling.attach(this)
        dev.patrickgold.florisboard.dictate.MaRemoteDb.attach(this)
        dev.patrickgold.florisboard.dictate.MaRemoteDb.maybePull()
        maWireCursorPad()
        FlorisImeServiceReference = WeakReference(this)
        systemLocalesFlow.value = resources.configuration.locales

        WindowCompat.setDecorFitsSystemWindows(window.window!!, false)
        windowController.onConfigurationChanged(resources.configuration)
        windowController.activeWindowConfig.collectLatestIn(lifecycleScope) {
            keyboardManager.updateActiveEvaluators() // TODO: wacky solution, but works for now
        }

        combine(
            systemLocalesFlow,
            subtypeManager.activeSubtypeFlow,
            prefs.localization.displayKeyboardLabelsInSubtypeLanguage.asFlow(),
        ) { systemLocales, subtype, shouldUseSubtypeLanguage ->
            systemLocales to (if (shouldUseSubtypeLanguage) subtype.primaryLocale else null)
        }.collectIn(lifecycleScope) { (systemLocales, subtypeLocale) ->
            val config = Configuration().apply {
                setToDefaults()
                if (subtypeLocale != null) {
                    setLocale(subtypeLocale.base)
                } else {
                    setLocales(systemLocales)
                }
            }
            resourcesContext = createConfigurationContext(config)
        }

        prefs.physicalKeyboard.showOnScreenKeyboard.asFlow().collectIn(lifecycleScope) {
            updateInputViewShown()
        }

        // Keep the screen on for the whole dictation (issue #231), applied to the IME window itself.
        // This used to live in the Smartbar's recording UI, but that composable is not composed while a
        // panel (prompts / history / GIF) is open — so the flag silently dropped mid-dictation, and with
        // no recent touch input the screen then turned off almost immediately, killing the recording.
        // Driving it from the controller state at window level makes it independent of what UI is shown.
        // Also held through transcribing/rewording so the finished text can't be lost to a sleeping screen.
        combine(
            DictateController.state,
            prefs.dictate.keepScreenAwake.asFlow(),
        ) { state, keepAwake ->
            keepAwake && (
                state is DictateController.UiState.Recording ||
                    state is DictateController.UiState.Transcribing ||
                    state is DictateController.UiState.Rewording
                )
        }.collectIn(lifecycleScope) { keepOn ->
            val imeWindow = window?.window ?: return@collectIn
            if (keepOn) {
                imeWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                imeWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        @Suppress("DEPRECATION") // We do not retrieve the wallpaper but only listen to changes
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    override fun onCreateInputView(): View? {
        super.installViewTreeOwners()
        val content = window.window!!.findViewById<ViewGroup>(android.R.id.content)
        content.addView(ImeRootView(this))
        // Disable the default input view placement
        return null
    }

    override fun onCreateCandidatesView(): View? {
        // Disable the default candidates view
        return null
    }

    override fun onCreateExtractTextView(): View {
        super.installViewTreeOwners()
        // Consider adding a fallback to the default extract edit layout if user reports come
        // that this causes a crash, especially if the device manufacturer of the user device
        // is a known one to break AOSP standards...
        val defaultExtractView = super.onCreateExtractTextView()
        if (defaultExtractView == null || defaultExtractView !is ViewGroup) {
            return ExtractedInputRootView(this, null)
        }
        val extractEditText = defaultExtractView.findViewById<ExtractEditText>(android.R.id.inputExtractEditText)
        (extractEditText?.parent as? ViewGroup)?.removeView(extractEditText)
        defaultExtractView.let {
            it.removeAllViews()
            it.addView(ExtractedInputRootView(this, extractEditText))
        }
        return defaultExtractView
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemLocalesFlow.value = newConfig.locales
        windowController.onConfigurationChanged(newConfig)
        themeManager.configurationChangeCounter.update { it + 1 }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the service is torn down mid-recording, finalize and keep the audio and release the mic
        // instead of leaking the (process-scoped) recorder (issue #147). No-op when not recording.
        dev.patrickgold.florisboard.dictate.DictateController.stashRecordingOnHide(this)
        unregisterReceiver(wallpaperChangeReceiver)
        FlorisImeServiceReference = WeakReference(null)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
        super.onStartInput(info, restarting)
        if (info == null) return
        val editorInfo = FlorisEditorInfo.wrap(info)
        // Another field of the same app: the one being left is "closed" for the address book, so an email
        // or number typed there is saved (live test 7.11.2026: moving from Email to Text saved nothing,
        // because a web page switches fields by RESTARTING the input). But a web page also restarts the
        // input in the middle of typing, and counting those saved halves ("+385915"): a restart counts as
        // leaving only when the new field's text does not continue the old one.
        runCatching {
            val c = editorInstance.activeContent
            // Same field when the restart keeps the field's type and id (live test 7.11.2026: comparing the
            // texts was unreliable in a WebView; Email → Text changes the type, typing in one box does not).
            val oldInfo = editorInstance.activeInfo.base
            val sameField = restarting && oldInfo.inputType == info.inputType && oldInfo.fieldId == info.fieldId
            if (!sameField) dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.observe(
                c.textBeforeSelection, c.textAfterSelection,
                keyboardManager.activeState.isIncognitoMode, fieldClosing = true,
            )
        }
        editorInstance.handleStartInput(editorInfo)
        // A new field starts from the badge, not from the language of the last field (MaTypingLanguage).
        if (!restarting) MaTypingLanguage.reset()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
        super.onStartInputView(info, restarting)
        if (info == null) return
        val editorInfo = FlorisEditorInfo.wrap(info)
        // REPROMPT (MaReprompt): the field the key was pressed in is back from the instruction
        // window, so its text is rewritten now. A moment later, so the editor has read the field.
        MaReprompt.takeFor(info.packageName, info.fieldId)?.let { words ->
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Empty field: write the message he asks for, at the cursor. Text: reshape it.
                val content = editorInstance.activeContent
                val write = content.text.isBlank() && content.selectedText.isBlank()
                lifecycleScope.launch {
                    // The typed window can say it too: "take a screenshot and reply" (MaScreenRead).
                    if (dev.patrickgold.florisboard.dictate.MaSkills.wants(words)) {
                        dev.patrickgold.florisboard.dictate.MaReResult.note(
                            this@FlorisImeService, dev.patrickgold.florisboard.dictate.MaSkills.updateNow(),
                        )
                        return@launch
                    }
                    var screen: dev.patrickgold.florisboard.dictate.MaScreenRead.Screen? = null
                    if (dev.patrickgold.florisboard.dictate.MaScreenRead.wants(words)) {
                        screen = dev.patrickgold.florisboard.dictate.MaScreenRead.read()
                        if (screen == null) {
                            dev.patrickgold.florisboard.dictate.MaReResult.note(
                                this@FlorisImeService, "Could not read the screen: the app showed no readable chat text (the accessibility tree was empty). Nothing was written.",
                            )
                            return@launch
                        }
                    }
                    DictateController.applyPrompt(
                        this@FlorisImeService, MaReprompt.prompt(words, write, screen), model = MaFlow.model(), allowGrowth = true, bare = true,
                    )
                }
            }, 400)
        }
        // The RE pop-up has closed (Close, Cancel, OK, Back, outside): the keyboard comes back at once.
        if (dev.patrickgold.florisboard.dictate.MaReResult.consumeClosed()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { requestShowSelf(0) } }, 250)
        }
        // The pop-up's OK (MaReResult): the accepted draft goes into the field it was written for.
        dev.patrickgold.florisboard.dictate.MaReResult.takeInsert(info.packageName, info.fieldId)?.let { r ->
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                DictateController.insertReResult(this, r)
            }, 400)
        }
        // The cursor pad never survives a new field.
        //
        // It is a mode drawn over the keyboard, and it trapped him once already by having only one
        // way out. A second, unconditional one: opening any field closes it. So even if every
        // gesture failed, switching app or tapping another box returns a working keyboard.
        MaCursorPad.close()
        // THE READER SURVIVES A NEW FIELD. It used to stop here, and that was wrong.
        //
        // `onStartInputView` fires for far more than "he walked away": a notification arriving, a
        // dialog opening, switching between the typing and transcription views, pinning the
        // keyboard — every one of them reopens the input view, and every one of them killed a
        // reading mid-sentence. He described it exactly: something pops up and it just stops.
        //
        // A reading is a task he started deliberately and can end with one press. It is not a mode
        // that traps him, which is what the cursor pad above is and why that one still closes
        // unconditionally. **Interrupting a task because a notification arrived is not caution, it
        // is losing his place for him.**
        //
        // Stopping still happens where it means something: he presses the key, the passage ends, or
        // the reader itself finds nothing left to read.
        // And with it the selection mode it may have turned on, for the same reason: a flag left
        // set would make arrows select on a field that never saw the pad.
        activeState.isManualSelectionMode = false
        activeState.batchEdit {
            // A new editor field invalidates any in-progress emoji search (issue #110); drop it so we
            // don't reappear on an unrelated field. imeUiMode is reset to TEXT just below anyway.
            keyboardManager.closeEmojiSearch(returnToMedia = false)
            if (activeState.imeUiMode != ImeUiMode.CLIPBOARD || prefs.clipboard.historyHideOnNextTextField.get()) {
                // Sticky panel: come back to whichever view was last in use rather than always to the
                // typing keyboard. Only TEXT and TRANSCRIBE are ever restored; the clipboard, emoji,
                // GIF and history panels are transient by nature and would be wrong to reopen on an
                // unrelated field.
                activeState.imeUiMode = maRestoredUiMode()
            }
            activeState.isSelectionMode = editorInfo.initialSelection.isSelectionMode
            editorInstance.handleStartInputView(editorInfo, isRestart = restarting)
        }

        // File transcription: if the user picked a file via the long-press mic trampoline, transcribe
        // it now that we are back on the field. Skips instant-recording when it kicks in.
        val startedFileTranscription =
            dev.patrickgold.florisboard.dictate.DictateController.consumePendingFileTranscription(this)

        val instantRecordingOn = prefs.dictate.instantRecording.get()

        // Don't auto-start on number-only fields (number/phone/PIN/date-time), where dictation rarely
        // makes sense, when the user opted to skip them (issue #146).
        val isNumericField = editorInfo.inputAttributes.type in setOf(
            dev.patrickgold.florisboard.ime.editor.InputAttributes.Type.NUMBER,
            dev.patrickgold.florisboard.ime.editor.InputAttributes.Type.PHONE,
            dev.patrickgold.florisboard.ime.editor.InputAttributes.Type.DATETIME,
        )
        val skipInstantForNumeric = isNumericField && prefs.dictate.instantRecordingSkipNumeric.get()

        // Interrupted recording: if a recording was finalized because the keyboard closed mid-recording,
        // offer to send it now. This recovery feature is mutually exclusive with instant recording: when
        // instant recording is on we never offer (and never stash — see stashRecordingOnHide), so opening
        // the keyboard always starts a fresh recording instead of being blocked (issue #120). The user is
        // told about this trade-off when enabling instant recording.
        val offeredInterrupted = !instantRecordingOn &&
            dev.patrickgold.florisboard.dictate.DictateController.maybeOfferInterruptedRecording(this)

        // Instant recording: optionally start dictation as soon as the keyboard opens on a field.
        if (!startedFileTranscription &&
            !offeredInterrupted &&
            !restarting &&
            instantRecordingOn &&
            !skipInstantForNumeric &&
            dev.patrickgold.florisboard.dictate.DictateController.state.value is
                dev.patrickgold.florisboard.dictate.DictateController.UiState.Idle &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            dev.patrickgold.florisboard.dictate.DictateController.onMicClick(this)
        }

        // "Dictate was updated" nudge (roadmap 11.9): shown in the Smartbar after an app update so users
        // who rarely open the settings still discover the changelog. Checked first so it takes priority
        // over the rate/donate nudge; both no-op unless idle, so neither interrupts a recording above.
        dev.patrickgold.florisboard.dictate.DictateController.maybePromptChangelog(this)
        // Floating-button spotlight: one-time nudge for users who have not enabled it yet. After the
        // changelog nudge so it does not compete on the same update; all no-op unless idle.
        dev.patrickgold.florisboard.dictate.DictateController.maybePromptFloatingButton(this)
        // Rate/donate nudge (roadmap 9.7/9.8): shown in the Smartbar once enough audio was dictated.
        // Guarded internally to no-op unless idle, so it never interrupts a recording started above.
        dev.patrickgold.florisboard.dictate.DictateController.maybePromptForReview()
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val config = resources.configuration
        // Pinned means shown whenever this IME is running, including the cases the system would
        // otherwise collapse: a hardware keyboard attached, or a configuration where the on-screen
        // view is treated as optional. This is the honest half of the pin.
        return prefs.dictate.maKeyboardPinned.get()
            || super.onEvaluateInputViewShown()
            || config.keyboard == Configuration.KEYBOARD_NOKEYS
            || prefs.physicalKeyboard.showOnScreenKeyboard.get()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        flogInfo { "old={start=$oldSelStart,end=$oldSelEnd} new={start=$newSelStart,end=$newSelEnd} composing={start=$candidatesStart,end=$candidatesEnd}" }
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        activeState.batchEdit {
            activeState.isSelectionMode = (newSelEnd - newSelStart) != 0
            editorInstance.handleSelectionUpdate(
                oldSelection = EditorRange.normalized(oldSelStart, oldSelEnd),
                newSelection = EditorRange.normalized(newSelStart, newSelEnd),
                composing = EditorRange.normalized(candidatesStart, candidatesEnd),
            )
        }
        // RE Magic (MaReSkill), method B: while an RE draft waits to be sent, remember what the field
        // holds, so a field emptied in one step is a sent message. Looks at nothing otherwise.
        if (dev.patrickgold.florisboard.dictate.MaReSkill.pending()) {
            runCatching {
                val info = editorInstance.activeInfo
                val c = editorInstance.activeContent
                dev.patrickgold.florisboard.dictate.MaReSkill.noteFieldText(
                    info.packageName, info.base.fieldId, c.textBeforeSelection + c.selectedText + c.textAfterSelection,
                )
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flogInfo { "finishing=$finishingInput" }
        super.onFinishInputView(finishingInput)
        editorInstance.handleFinishInputView()
    }

    override fun onFinishInput() {
        flogInfo { "(no args)" }
        // His corrections to the last dictation are read now, while this field is still the one
        // connected. Not when only the keyboard hides: he may bring it back to fix the same text.
        MaDictationCorrections.settle(editorInstance.activeInfo)
        // The field closes: every complete email address in it is saved whole (MaAddressBook, 7.11.2026).
        runCatching {
            val c = editorInstance.activeContent
            dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.observe(
                c.textBeforeSelection, c.textAfterSelection,
                keyboardManager.activeState.isIncognitoMode, fieldClosing = true,
            )
        }
        super.onFinishInput()
        editorInstance.handleFinishInput()
        NlpInlineAutofill.clearInlineSuggestions()
        // The half-written sentence goes to the personal model now, and the model goes to disk. An
        // input method is killed without warning, so anything still only in memory when the field
        // closes is likely never to be written at all.
        MaNgram.flushPending(keyboardManager.activeState.isIncognitoMode)
        MaNgram.flush()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (windowController.onWindowShown()) {
            flogInfo(LogTopic.IMS_EVENTS)
            inputFeedbackController.updateSystemPrefsState()
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already shown)" }
        }
        // Fallback for the file-transcription handoff: depending on how the user returns from the
        // picker the keyboard may reappear without a fresh onStartInputView, so also check here. The
        // claim mechanism makes this idempotent with the onStartInputView call.
        dev.patrickgold.florisboard.dictate.DictateController.consumePendingFileTranscription(this)
    }

    /**
     * Records which panel was showing when the keyboard closed, so the next open can return to it.
     * Only the two views worth returning to are stored; anything else is remembered as the keyboard.
     */
    private fun maRememberUiMode(mode: ImeUiMode) {
        // Only ever the keyboard now. Nothing reads this to reopen the recording view, so recording
        // which view was showing would be bookkeeping with no reader.
        val keep = ImeUiMode.TEXT
        runCatching {
            lifecycleScope.launch { prefs.dictate.maLastImeUiMode.set(keep.name) }
        }
    }

    /**
     * The view a fresh editor field opens in.
     *
     * Settings, Mantra, Opening view. **Defaults to the typing keyboard** at build 156, replacing a
     * rule that always reopened the last view used. That rule existed to stop a view being lost, and
     * it does, but it also makes the keyboard that appears depend on something done in another app an
     * hour ago, and a text field that greets you with a live microphone is a surprise every time.
     */
    private fun maRestoredUiMode(): ImeUiMode = when (prefs.dictate.maOpeningView.get()) {
        // "dictation" is back, pinned from the dictation view itself.
        //
        // It was removed on the argument that this screen is always secondary and that opening onto
        // it means opening onto a live microphone. The second half is not true — the view shows a
        // mic key, it does not press it, and recording on open is a separate setting he can leave
        // off. The first half was mine to assume and his to decide: he dictates far more than he
        // types, so for him the keyboard is the secondary screen.
        //
        // The pin on that view is the only thing that sets this, so it can always be unset from the
        // same place it was set — which is what the earlier removal was really protecting against.
        "dictation" -> ImeUiMode.TRANSCRIBE
        // Pinned from inside the clipboard panel. Tapping an entry pastes it, which makes the panel
        // a second route to the C keys' job with no ten-slot ceiling and the text visible rather
        // than a number to remember.
        "clipboard" -> ImeUiMode.CLIPBOARD
        // The old behaviour, kept for anyone who wants it: reopen whichever view was last used. Only
        // the two main views are remembered; clipboard, emoji and history are transient and would be
        // wrong to reopen on an unrelated field.
        // "last" no longer reopens the recording view either. It was the same override by a second
        // route: end a session in the recording view and every later open began there, which is the
        // behaviour that has just been removed from the setting. There is nothing else worth
        // remembering — the only two candidates were the keyboard and this — so "last" is now the
        // keyboard, and is kept only so a stored value still resolves.
        "last" -> ImeUiMode.TEXT
        // "keyboard", and anything unrecognised. An unreadable preference must open the typing
        // keyboard rather than the dictation view: one is a surprise, the other is a live microphone.
        else -> ImeUiMode.TEXT
    }

    /**
     * Pushes the keyboard back up after something dismissed it, while the pin is on.
     *
     * Tapping the middle of the screen makes the app drop focus and ask the system to hide the
     * keyboard. That request goes to the system, not here, and an input method cannot refuse it —
     * onWindowHidden is a notification that it already happened. Asking to be shown again is the
     * only move available, and it is a request rather than a guarantee: with focus genuinely gone it
     * will simply fail, which is the case this cannot fix.
     *
     * ### The governor, and why it is not optional
     *
     * An app that hides the keyboard on a rule of its own will hide it again the moment it returns,
     * and re-showing without a limit turns that into a loop: hide, show, hide, show, visible as
     * flicker and leaving no way to dismiss the keyboard at all. So re-shows are counted, and after
     * [MA_PIN_MAX_RESHOWS] inside [MA_PIN_WINDOW_MS] this stops trying until the window passes.
     * Losing the pin's benefit in a stubborn app is a small cost; a keyboard the user cannot put
     * away is not.
     *
     * A dismissal the user asked for is never undone.
     */
    private fun maReshowIfPinned() {
        if (!prefs.dictate.maKeyboardPinned.get()) return
        if (maUserRequestedHide) {
            maUserRequestedHide = false
            return
        }
        val now = SystemClock.elapsedRealtime()
        while (maPinReshowAt.isNotEmpty() && now - maPinReshowAt.first() > MA_PIN_WINDOW_MS) {
            maPinReshowAt.removeFirst()
        }
        if (maPinReshowAt.size >= MA_PIN_MAX_RESHOWS) {
            flogWarning(LogTopic.IMS_EVENTS) { "Pin: giving up re-showing, the app keeps closing it" }
            return
        }
        maPinReshowAt.addLast(now)
        lifecycleScope.launch {
            // A beat, so the request does not race the hide it is answering and get swallowed.
            delay(MA_PIN_RESHOW_DELAY_MS)
            if (prefs.dictate.maKeyboardPinned.get()) showUi()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // Collapsing the keyboard during a recording finalizes and keeps the audio so far (instead of
        // discarding it): the next keyboard open then offers to send the interrupted recording. Outside
        // an active recording this is the normal teardown.
        dev.patrickgold.florisboard.dictate.DictateController.stashRecordingOnHide(this)
        if (windowController.onWindowHidden()) {
            flogInfo(LogTopic.IMS_EVENTS)
            maRememberUiMode(activeState.imeUiMode)
            activeState.batchEdit {
                activeState.imeUiMode = ImeUiMode.TEXT
                activeState.isActionsOverflowVisible = false
                activeState.isActionsEditorVisible = false
            }
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already hidden)" }
        }
        // Last, after the teardown above. A recording is stashed and the view reset first, so if the
        // keyboard does come back it comes back in a known state rather than mid-collapse.
        maReshowIfPinned()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val config = resources.configuration
        // Pinned refuses fullscreen extract mode outright. In that mode the app's field is replaced
        // by the system's own full-height editor and the keyboard's rows go with it, which is the
        // most complete version of the disappearing Marko is pinning against.
        if (prefs.dictate.maKeyboardPinned.get()) {
            return false
        }
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        return when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onEvaluateFullscreenMode()
            LandscapeInputUiMode.NEVER_SHOW -> false
            LandscapeInputUiMode.ALWAYS_SHOW -> true
        }
    }

    override fun onUpdateExtractingVisibility(info: EditorInfo?) {
        if (info != null) {
            editorInstance.handleStartInputView(FlorisEditorInfo.wrap(info), isRestart = true)
        }
        when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onUpdateExtractingVisibility(info)
            LandscapeInputUiMode.NEVER_SHOW -> isExtractViewShown = false
            LandscapeInputUiMode.ALWAYS_SHOW -> isExtractViewShown = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!prefs.smartbar.enabled.get() || !prefs.suggestion.api30InlineSuggestionsEnabled.get()) {
            flogInfo(LogTopic.IMS_EVENTS) {
                "Ignoring inline suggestions request because Smartbar and/or inline suggestions are disabled."
            }
            return null
        }

        flogInfo(LogTopic.IMS_EVENTS) { "Creating inline suggestions request" }
        val stylesBundle = themeManager.createInlineSuggestionUiStyleBundle(this)
        if (stylesBundle == null) {
            flogWarning(LogTopic.IMS_EVENTS) { "Failed to retrieve inline suggestions style bundle" }
            return null
        }
        val spec = InlinePresentationSpec.Builder(
            InlineSuggestionUiSmallestSize,
            InlineSuggestionUiBiggestSize,
        ).run {
            setStyle(stylesBundle)
            build()
        }

        return InlineSuggestionsRequest.Builder(listOf(spec)).run {
            setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val inlineSuggestions = response.inlineSuggestions
        flogInfo(LogTopic.IMS_EVENTS) {
            "Received inline suggestions response with ${inlineSuggestions.size} suggestion(s) provided."
        }
        return NlpInlineAutofill.showInlineSuggestions(this, inlineSuggestions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        if (outInsets == null) return
        val state = keyboardManager.activeState.snapshot()
        windowController.onComputeInsets(outInsets, state.isFullscreenInputRequired())
    }

    override fun getTextForImeAction(imeOptions: Int): String? {
        return try {
            when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_NONE -> null
                EditorInfo.IME_ACTION_GO -> resourcesContext.getString(AndroidInternalR.string.ime_action_go)
                EditorInfo.IME_ACTION_SEARCH -> resourcesContext.getString(AndroidInternalR.string.ime_action_search)
                EditorInfo.IME_ACTION_SEND -> resourcesContext.getString(AndroidInternalR.string.ime_action_send)
                EditorInfo.IME_ACTION_NEXT -> resourcesContext.getString(AndroidInternalR.string.ime_action_next)
                EditorInfo.IME_ACTION_DONE -> resourcesContext.getString(AndroidInternalR.string.ime_action_done)
                EditorInfo.IME_ACTION_PREVIOUS -> resourcesContext.getString(AndroidInternalR.string.ime_action_previous)
                else -> resourcesContext.getString(AndroidInternalR.string.ime_action_default)
            }
        } catch (_: Throwable) {
            super.getTextForImeAction(imeOptions)?.toString()
        }
    }




    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // The volume keys, as a module with no setting. See MaVolumeKeys and VOLUME_KEYS.md.
        if (MaVolumeKeys.onDown(this, keyCode, event)) return true
        return keyboardManager.onHardwareKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // The release is where the decision is made, so it does the work rather than being
        // swallowed. A quick tap acts; a press that already became the volume does nothing more.
        if (MaVolumeKeys.onUp(this, keyCode)) return true
        return keyboardManager.onHardwareKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

}



/** How long the pin's re-show attempts are counted over. */
private const val MA_PIN_WINDOW_MS = 6_000L

/** How many times the pin will push back inside that window before it stops fighting. */
private const val MA_PIN_MAX_RESHOWS = 3

/** A beat before asking, so the request does not race the hide it is answering. */
private const val MA_PIN_RESHOW_DELAY_MS = 120L
