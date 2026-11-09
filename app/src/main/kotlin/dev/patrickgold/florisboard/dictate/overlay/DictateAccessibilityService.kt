/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.Context
import android.graphics.Rect
import dev.patrickgold.florisboard.dictate.MaLog
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional accessibility service that powers the floating dictation button (issue #88). It does two
 * things the keyboard cannot do from outside an active IME:
 *
 *  1. **Detect** when an editable text field holds input focus in *any* app, so the floating button can
 *     appear only when there is somewhere to dictate into ([editableFocused]).
 *  2. **Inject** the transcribed text into that focused field ([injectText]) — the equivalent of the
 *     IME's `commitText`, but driven from the overlay where no InputConnection exists.
 *
 * The service is entirely opt-in: it does nothing until the user enables both the floating-button
 * feature and this service in the system accessibility settings. It only ever reads the *focused*
 * field (to know it is editable and to place text at the cursor); it does not collect screen content.
 *
 * It also owns the floating bubble ([DictateBubbleController]) and promotes itself to a microphone
 * foreground service while a bubble-driven dictation records, so background mic capture is allowed.
 */
class DictateAccessibilityService : AccessibilityService() {


    private var bubble: DictateBubbleController? = null

    /** One line at the bottom of the screen while recording unseen. */
    private var recordingLine: MaRecordingLine? = null

    /**
     * Its own scope, cancelled with the service.
     *
     * The line is a window this service owns; a collector that outlived it would try to add one to a
     * WindowManager that no longer has a service behind it.
     */
    private val lineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var isForeground = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Coalesce selection re-checks. Selection-changed events can arrive on every keystroke, but the
    // expensive part of updateEditableFocus() — fetching the focused node's full AccessibilityNodeInfo
    // over IPC — only needs to run once a burst settles: the editable-focus state does not change while
    // typing in the same field. Debouncing only this noisy event removes the per-keystroke IPC flood
    // without delaying the bubble after a real focus or window change (#222).
    private val focusUpdateRunnable = Runnable { updateEditableFocus() }

    private fun scheduleFocusUpdate() {
        mainHandler.removeCallbacks(focusUpdateRunnable)
        mainHandler.postDelayed(focusUpdateRunnable, FOCUS_UPDATE_DEBOUNCE_MS)
    }

    /**
     * Runs a focus check as soon as Android tells us that the input target or window changed. Any pending
     * selection debounce is stale at that point, so cancel it rather than letting an old callback delay or
     * overwrite this state. These event types are not emitted for every typed character, unlike selection
     * changes, so the immediate IPC is both safe and necessary for a responsive overlay.
     */
    private fun updateEditableFocusImmediately() {
        mainHandler.removeCallbacks(focusUpdateRunnable)
        updateEditableFocus()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // The single most useful line in the log.
        //
        // Everything this app does beyond typing runs through this service, and when it will not
        // turn on there is otherwise no way to tell "Android refused it" from "it started and then
        // died". The capabilities are printed with it because a service can connect with fewer
        // than it asked for, which is silent and looks exactly like a bug in the app.
        MaLog.add(
            "a11y",
            "service connected, flags=0x${serviceInfo?.flags?.toString(16) ?: "?"}, " +
                "capabilities=0x${serviceInfo?.capabilities?.toString(16) ?: "?"}",
        )
        flogDebug { "DictateAccessibilityService connected" }
        createNotificationChannel()
        bubble = DictateBubbleController(this).also { it.start() }
        // The recording line: visible only while recording with no keyboard on screen.
        //
        // Driven from the two facts that decide it rather than from an event, so it cannot be left
        // showing by a path nobody thought of — every change to either one re-answers the question.
        recordingLine = MaRecordingLine(this)
        lineScope.launch {
            combine(
                DictateController.state,
                _imeVisible,
            ) { state, imeUp ->
                state is DictateController.UiState.Recording && !imeUp
            }.distinctUntilChanged().collect { show ->
                recordingLine?.show(show)
            }
        }
        updateEditableFocus()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        MaLog.add("a11y", "service unbound")
        clearInstance()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        MaLog.add("a11y", "service destroyed")
        mainHandler.removeCallbacks(focusUpdateRunnable)
        clearInstance()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // No ongoing feedback to interrupt.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            // Note: TYPE_WINDOW_CONTENT_CHANGED is intentionally absent (and not subscribed in the
            // service config): it fires on every keystroke and made updateEditableFocus() re-fetch the
            // whole focused AccessibilityNodeInfo per character — a per-keystroke IPC flood that caused
            // typing jank. Focus/editability only change on the events below, so we lose nothing.
            // A focus/click or window transition is exactly when the bubble should appear or disappear.
            // Do not route these through the typing-oriented debounce: it used to add 150 ms to every
            // transition on top of the accessibility framework's notification timeout (#222).
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                // The app switcher rides on the events the bubble already needs, so tracking which
                // app is in front costs nothing extra: no new subscription, no new permission, and
                // no polling. Only a window state change tells us an app came forward.
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    MaAppSwitcher.onWindowPackage(this, packageName, event.packageName?.toString())
                }
                // Learning a button by watching it be pressed. Only while armed, and the event is
                // the one the service already receives — nothing is intercepted, and the button
                // does exactly what it always does.
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                    MaScreenTargets.Learn.isArmed()
                ) {
                    event.source?.let { node ->
                        try {
                            // The app's readable name, resolved here where the package manager is
                            // to hand. "Claude" rather than com.anthropic.claude: the bar is asking
                            // him a question and it should ask it in words he uses.
                            val label = node.packageName?.toString()?.let { pkg ->
                                runCatching {
                                    val pm = packageManager
                                    pm.getApplicationLabel(
                                        pm.getApplicationInfo(pkg, 0),
                                    ).toString()
                                }.getOrNull() ?: pkg.substringAfterLast('.')
                            }.orEmpty()
                            MaScreenTargets.Learn.onClicked(node, packageName, label)
                        } finally {
                            runCatching { node.recycle() }
                        }
                    }
                }
                // RE MAGIC (MaReSkill), method A: a click on a button called Send while an RE draft waits.
                // READ ONLY: the label is read and the focused field's text is read; nothing is clicked,
                // nothing is pressed for him, nothing is delayed. His send goes through untouched.
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                    dev.patrickgold.florisboard.dictate.MaReSkill.pending()
                ) {
                    runCatching { noteSendClickForSkill(event) }
                }
                updateEditableFocusImmediately()
            }
            // This is the only subscribed event which can arrive for every keystroke. Keep it coalesced
            // so caret moves and text selection do not cause a focused-node IPC round trip per character.
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> scheduleFocusUpdate()
        }
    }

    /** RE Magic, method A (see the call above): is this click a send, and if so hand the field's text over. */
    private fun noteSendClickForSkill(event: AccessibilityEvent) {
        val node = event.source
        try {
            val isSend = dev.patrickgold.florisboard.dictate.MaReSkill.isSendLabel(
                node?.text, node?.contentDescription, event.contentDescription,
                event.text.takeIf { it.isNotEmpty() }?.joinToString(" "),
                node?.viewIdResourceName?.substringAfterLast('/')?.split('_', '.')?.firstOrNull { it.equals("send", true) },
            )
            if (!isSend) return
            dev.patrickgold.florisboard.dictate.MaReSkill.onSendClicked(event.packageName?.toString(), fullTextOfFocused())
        } finally {
            runCatching { node?.recycle() }
        }
    }

    /** The currently input-focused node if it is an editable text field, else null. */
    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (node.isLikelyEditable()) return node
        // findFocus sometimes returns a container that merely *holds* the editable view (common in
        // wrapped/cross-platform UIs); descend to the first editable descendant.
        return findEditableDescendant(node, 0)
    }

    /** Depth-first search under [node] for the first editable descendant, bounded to avoid deep trees. */
    private fun findEditableDescendant(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth >= MAX_EDITABLE_SEARCH_DEPTH) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isLikelyEditable()) return child
            findEditableDescendant(child, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Moves input focus to the next editable field on screen, wrapping to the first.
     *
     * ### Why this exists rather than a Tab key
     *
     * A real `KEYCODE_TAB` is already reachable through the macro syntax, and in Suno it does
     * nothing useful — it moves the caret inside the field it is already in. That is not a bug in
     * the app: Tab moves focus on Android only between views marked focusable in touch mode, and
     * almost nothing is, because until recently nobody had a Tab key on a phone.
     *
     * Reading the node tree sidesteps the question entirely. It does not matter whether the app
     * handles Tab, whether it is native or a web view, or whether anyone ever considered a keyboard:
     * the fields are in the tree because the tree is what screen readers use, and every app has to
     * provide it.
     *
     * ### Order
     *
     * Depth-first through the tree, which is the order the fields were declared and, in practice,
     * the order they appear down the screen. That matches what the eye expects and what Tab does on
     * a desktop.
     *
     * ### Wrapping
     *
     * From the last field it returns to the first, so the key always does something. On a screen
     * with two fields — lyrics and style — that makes one key a toggle between them, which is the
     * shape of the actual problem.
     */
    private fun focusNextEditableField(backwards: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val fields = ArrayList<AccessibilityNodeInfo>(8)
        collectEditable(root, 0, fields)
        if (fields.isEmpty()) return false
        // Where we are now. A field that reports focus is the anchor; with none, start at the top.
        val currentIndex = fields.indexOfFirst { runCatching { it.isFocused }.getOrDefault(false) }
        val direction = if (backwards) -1 else 1
        // Try each field in turn, wrapping. A field can refuse focus — it may be disabled — and
        // stopping at the first refusal would make the key look broken when the next one along
        // would have worked.
        for (step in 1..fields.size) {
            val at = (currentIndex + direction * step) % fields.size
            val candidate = fields[if (at < 0) at + fields.size else at]
            if (currentIndex >= 0 && candidate == fields[currentIndex]) continue
            // Scroll it into view first. A field below the fold can be focused perfectly well and
            // then be somewhere the user cannot see, which reads as the key having done nothing —
            // the caveat he reported. This is the request that says "bring this into the frame",
            // and the container that can scroll is the one that answers it.
            //
            // Reached through AccessibilityAction and its id, because unlike ACTION_FOCUS this one
            // never had a plain int constant on AccessibilityNodeInfo — it arrived in API 23 as an
            // AccessibilityAction only.
            runCatching {
                candidate.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id,
                )
            }
            val ok = runCatching {
                candidate.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** Depth-first collection of every editable field, capped so a deep tree cannot stall a keypress. */
    private fun collectEditable(
        node: AccessibilityNodeInfo?,
        depth: Int,
        out: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null || depth > MA_FIELD_MAX_DEPTH || out.size >= MA_FIELD_MAX_COUNT) return
        // Visibility is deliberately NOT a condition.
        //
        // It was, and it was wrong: a form taller than the screen has its later fields reported as
        // not visible to the user, so the key refused to reach exactly the fields that are hardest
        // to reach by hand. Off-screen is a reason to scroll to a field, not a reason to pretend it
        // does not exist.
        if (node.isLikelyEditable()) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            collectEditable(runCatching { node.getChild(i) }.getOrNull(), depth + 1, out)
        }
    }

    /**
     * A node we should treat as a dictation target. [isEditable] is the canonical flag, but several apps
     * never set it on otherwise-editable fields; fall back to the EditText class hierarchy and to the
     * field advertising the text-editing actions, so detection is not limited to the few well-behaved apps.
     */
    private fun AccessibilityNodeInfo.isLikelyEditable(): Boolean {
        if (isEditable) return true
        if (className?.toString()?.contains("EditText") == true) return true
        val actions = actionList
        return actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT) &&
            actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION)
    }

    /**
     * Whether a soft keyboard (any IME) is currently shown on screen. This is the most reliable proxy for
     * "a keyboard would normally be extended here", independent of whether the focused field reports itself
     * as editable, so the bubble appears in the same situations a keyboard does. Requires
     * `flagRetrieveInteractiveWindows`, which the service config sets.
     */
    private fun isImeWindowShown(): Boolean = runCatching {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }.getOrDefault(false)

    private fun updateEditableFocus() {
        // Show the bubble whenever there is somewhere to dictate: either an editable field holds focus, or a
        // soft keyboard is physically out (covers apps whose fields don't report an accessible editable focus).
        val imeShown = isImeWindowShown()
        val focused = focusedEditableNode() != null || imeShown
        if (_editableFocused.value != focused) {
            _editableFocused.value = focused
            flogDebug { "editable field focused = $focused" }
        }
        if (_imeVisible.value != imeShown) {
            _imeVisible.value = imeShown
            flogDebug { "IME window visible = $imeShown" }
        }
        val dictateKeyboard = isDictateKeyboardActive()
        if (_dictateKeyboardActive.value != dictateKeyboard) {
            _dictateKeyboardActive.value = dictateKeyboard
            flogDebug { "Dictate keyboard active = $dictateKeyboard" }
        }
        val pkg = currentAppPackage()
        if (!pkg.isNullOrEmpty() && pkg != packageName && _foregroundPackage.value != pkg) {
            _foregroundPackage.value = pkg
            flogDebug { "foreground app = $pkg" }
        }
    }

    /**
     * The package of the foreground *application* window (ignoring IME/system windows), for per-app bubble
     * positioning. Reading it from the focused application window avoids the churn of TYPE_WINDOW_STATE_CHANGED
     * events that fire for the keyboard and transient popups with their own package names.
     */
    private fun currentAppPackage(): String? = runCatching {
        val fromAppWindow = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.isFocused }
            .firstOrNull()
            ?.root?.packageName?.toString()
        fromAppWindow ?: rootInActiveWindow?.packageName?.toString()
    }.getOrNull()

    /** Whether the Dictate keyboard itself is the currently selected input method (handles .debug). */
    private fun isDictateKeyboardActive(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return false
        // DEFAULT_INPUT_METHOD is "<package>/<service-class>"; the package is our applicationId.
        return current.substringBefore('/') == packageName
    }

    /**
     * Inserts [text] into the focused editable field at the cursor, replacing the active selection
     * (matching the IME `commitText` semantics) and placing the cursor right after the inserted text.
     * Falls back to appending at the end when the field reports no usable selection. Returns true when
     * the field accepted the change — some custom/legacy views do not support `ACTION_SET_TEXT`.
     */
    private fun commitTextIntoFocused(text: String): Boolean {
        if (text.isEmpty()) return true // silence: nothing to insert — a no-op is a success, not a failure.

        // Insert exactly like a normal keyboard: through the accessibility input connection's commitText,
        // straight into the field at the cursor — no clipboard, no toast, and it never prepends a shown
        // placeholder (e.g. WhatsApp's "Message"). The stability trick is to first resolve the field the
        // user actually SEES (fresh from the live window, so a recreated/stale field can't swallow the
        // text) and, if it isn't already focused, give it input focus. Focusing the visible field points
        // the input connection at THAT field instead of an editor the app discarded — the root of the old
        // "green check, no text" flakiness. A short retry covers the instant right after a send when the
        // host app is still rebuilding its field. Node ACTION_SET_TEXT / clipboard paste stay only as
        // fallbacks for the rare fields that accept no input connection (old OS, some WebView/custom views).
        repeat(COMMIT_ATTEMPTS) { attempt ->
            val target = activeWindowEditable()
            if (target != null && !target.isFocused) {
                runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
                runCatching { target.refresh() }
                // Let the input connection rebind to the field we just focused before we commit into it.
                SystemClock.sleep(FOCUS_SETTLE_MS)
            }

            // 1. The keyboard-style path: commit through the input connection (no clipboard, no toast).
            if (commitViaInputConnection(text)) {
                flogDebug { "commit via inputConnection len=${text.length}" }
                return true
            }
            // 2. Fallback: write straight into the visible node (older OS without the a11y input method, or
            //    fields that expose no editor connection). Placeholder-safe via editableText().
            if (target != null && setTextOnFocused(target, text)) {
                flogDebug { "commit via setText len=${text.length}" }
                return true
            }
            // 3. Last resort only: clipboard paste (WebView/custom inputs that ignore both). The paste
            //    toast is therefore the rare exception, never the normal case.
            if (target != null && pasteIntoFocused(target, text)) {
                flogDebug { "commit via paste len=${text.length}" }
                return true
            }
            if (attempt < COMMIT_ATTEMPTS - 1) SystemClock.sleep(COMMIT_RETRY_DELAY_MS)
        }
        flogDebug { "commit FAILED after $COMMIT_ATTEMPTS attempts len=${text.length}" }
        return false
    }

    /**
     * The editable field in the currently active (visible) window, located fresh from the live node tree
     * via [rootInActiveWindow]. Used when the cached input focus is stale — e.g. the host app recreated
     * its input after sending a message — so dictation lands in the field the user actually sees rather
     * than a detached one (#132 follow-up).
     */
    private fun activeWindowEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isLikelyEditable()) return focused
            findEditableDescendant(focused, 0)?.let { return it }
        }
        if (root.isLikelyEditable()) return root
        // Nothing has focus: take the LOWEST field on the screen and focus it ourselves.
        //
        // This is the case where he never tapped the box. He opens an app, holds volume up, speaks,
        // and expects the words to land in the obvious place — without first tapping a field to
        // raise a keyboard he is not going to type on.
        //
        // Lowest rather than first, and the difference matters: depth-first from the root returns
        // whatever is declared earliest, which is a search box at the top of a chat far more often
        // than the composer at the bottom. The box worth writing into is the one nearest the
        // thumb, and on every messaging screen ever built that is the last one down the page.
        //
        // `collectEditable` is used rather than `findEditableDescendant` because the latter stops
        // at depth 6, and a composer inside a modern nested layout sits deeper than that.
        val fields = ArrayList<AccessibilityNodeInfo>(8)
        collectEditable(root, 0, fields)
        val lowest = fields.maxByOrNull { node ->
            Rect().also { node.getBoundsInScreen(it) }.bottom
        }
        if (lowest != null) {
            // Focus it, so the input connection points at this field rather than at nothing. Shown
            // on screen first for the same reason as everywhere else: a field written into while
            // out of view looks like a press that did nothing.
            runCatching {
                lowest.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            }
            runCatching { lowest.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            return lowest
        }
        return findEditableDescendant(root, 0)
    }

    /**
     * Commits [text] through the accessibility [android.accessibilityservice.InputMethod] input connection
     * (API 33+). Requires the `flagInputMethodEditor` accessibility flag. Returns false when unavailable
     * (older OS, or no editor currently bound), so the caller falls back to the node-based methods.
     */
    private fun commitViaInputConnection(text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val connection = inputMethod?.currentInputConnection ?: return false
        return runCatching {
            connection.commitText(text, 1, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Inserts [text] via [AccessibilityNodeInfo.ACTION_SET_TEXT], reconstructing the field content around the
     * cursor/selection. A shown placeholder is treated as empty (see [editableText]) so it is not prepended.
     * Returns false when the field does not accept the action, so the caller can fall back to pasting.
     */
    private fun setTextOnFocused(node: AccessibilityNodeInfo, text: String): Boolean {
        val existing = node.editableText()
        val from = node.textSelectionStart.coerceForText(existing)
        val to = node.textSelectionEnd.coerceForText(existing)
        val start = minOf(from, to)
        val end = maxOf(from, to)
        val newText = existing.substring(0, start) + text + existing.substring(end)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        if (ok) {
            val cursor = start + text.length
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }
        return ok
    }

    /**
     * Inserts [text] by putting it on the clipboard and performing [AccessibilityNodeInfo.ACTION_PASTE] on
     * the focused field, then restoring the user's previous clipboard shortly after. Returns false when the
     * field does not advertise the paste action, so the caller can fall back to ACTION_SET_TEXT.
     *
     * Pasting inserts into the field's real content (so a shown placeholder is never prepended) and works in
     * WebView/browser inputs that ignore ACTION_SET_TEXT. We cannot verify the write by reading the clipboard
     * back (a background app's clipboard read is blocked on Android 10+ and returns null), so we trust the
     * write; if it were blocked the field simply would not receive our text and the user would re-try.
     */
    private fun pasteIntoFocused(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE)) return false
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        val previous = runCatching { clipboard.primaryClip }.getOrNull()
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("dictate", text)) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (ok) {
            // Restore the previous clipboard once the target app has consumed the paste, so we do not
            // clobber whatever the user had copied.
            mainHandler.postDelayed({
                runCatching {
                    clipboard.setPrimaryClip(previous ?: ClipData.newPlainText("", ""))
                }
            }, CLIPBOARD_RESTORE_DELAY_MS)
        }
        return ok
    }

    /**
     * Removes the last inserted [text] from the focused field again (undo, issue #133). Prefers the
     * accessibility input connection (API 33+) when the characters right before the cursor are exactly
     * [text]; otherwise removes the matching region — the window ending at the cursor if it matches,
     * else the last occurrence — via [AccessibilityNodeInfo.ACTION_SET_TEXT]. Returns true on success.
     */
    private fun deleteLastTextFromFocused(text: String): Boolean {
        if (text.isEmpty()) return false
        // Reconstruct the field without the inserted text via ACTION_SET_TEXT (works pre-API-33 too and
        // lets us verify the match first, so the user's own edits are never eaten).
        val node = focusedEditableNode() ?: return false
        node.refresh()
        val existing = node.editableText()
        val cursor = node.textSelectionEnd.coerceForText(existing)
        val start = when {
            cursor >= text.length && existing.regionMatches(cursor - text.length, text, 0, text.length) ->
                cursor - text.length
            existing.contains(text) -> existing.lastIndexOf(text)
            else -> return false
        }
        val newText = existing.removeRange(start, start + text.length)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) return false
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, start)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        flogDebug { "deleteLastText via setText len=${text.length}" }
        return true
    }

    // --- Real-time dictation preview via the overlay (issue #128) ---------------------------------
    // Live streaming into another app's field over AccessibilityService. Each accessibility write costs a
    // node fetch + set, so preview updates are throttled and applied as a minimal diff (delete the changed
    // tail, insert the new tail) rather than re-setting the whole field. [previewShown] tracks exactly what
    // we have injected so the diff stays correct even when throttling skips intermediate updates.
    private var previewShown = ""
    private var lastPreviewMs = 0L

    private fun applyPreviewDiff(old: String, new: String) {
        if (old == new) return
        val cp = old.commonPrefixWith(new).length
        if (cp < old.length) deleteLastTextFromFocused(old.substring(cp))
        if (cp < new.length) commitTextIntoFocused(new.substring(cp))
    }

    private fun setPreviewThrottled(full: String) {
        if (full == previewShown) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewMs < PREVIEW_THROTTLE_MS) return   // skip; a later update catches up via the diff
        applyPreviewDiff(previewShown, full)
        previewShown = full
        lastPreviewMs = now
    }

    private fun commitPreviewFinalOnFocused(finalText: String) {
        applyPreviewDiff(previewShown, finalText)   // no throttle — final result always lands
        previewShown = ""
        lastPreviewMs = 0L
    }

    private fun clearPreviewOnFocused() {
        if (previewShown.isNotEmpty()) applyPreviewDiff(previewShown, "")
        previewShown = ""
        lastPreviewMs = 0L
    }

    /** The selected text in the focused editable field, or empty when nothing is selected. */
    private fun selectedTextOfFocused(): String {
        val node = focusedEditableNode() ?: return ""
        node.refresh()
        val text = node.editableText()
        if (text.isEmpty()) return ""
        val from = node.textSelectionStart
        val to = node.textSelectionEnd
        if (from < 0 || to < 0 || from == to) return ""
        val start = minOf(from, to).coerceIn(0, text.length)
        val end = maxOf(from, to).coerceIn(0, text.length)
        return text.substring(start, end)
    }

    /** The full text of the focused editable field, or empty when there is none. */
    private fun fullTextOfFocused(): String {
        val node = focusedEditableNode() ?: return ""
        node.refresh()
        return node.editableText()
    }

    /** Selects the whole field so a subsequent inject replaces it. Returns true on success. */
    private fun selectAllInFocused(): Boolean {
        val node = focusedEditableNode() ?: return false
        node.refresh()
        val len = node.editableText().length
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    /**
     * The field's real text, treating a shown hint/placeholder (e.g. WhatsApp's "Message") as empty so
     * the injected text never gets prepended to the placeholder.
     *
     * [AccessibilityNodeInfo.getText] returns the hint verbatim for an empty field. The documented way to
     * tell them apart is [isShowingHintText], but it is not reliable across apps — WhatsApp's compose field
     * returns its "Message" placeholder as `text` *without* setting the flag. So we additionally treat the
     * text as empty when it is identical to the node's declared [getHintText]; the (self-correcting) cost
     * is that a field whose real content exactly equals its placeholder is seen as empty.
     */
    private fun AccessibilityNodeInfo.editableText(): String {
        if (isShowingHintText) return ""
        val raw = text?.toString() ?: ""
        if (raw.isEmpty()) return ""
        // Treat the text as empty when it merely echoes the declared hint/placeholder (some apps return the
        // placeholder as text without setting isShowingHintText). Only hintText is used here — matching
        // against contentDescription is unsafe because some apps mirror the real content there, which would
        // make us drop existing text when appending.
        val hint = hintText?.toString()?.trim()
        if (!hint.isNullOrEmpty() && hint == raw.trim()) return ""
        return raw
    }

    /**
     * Presses the editor action / Enter on the focused field (auto-enter). Uses the proper IME-enter
     * action on Android 11+; on older releases there is no editor-action equivalent, so it falls back
     * to inserting a newline.
     */
    private fun performEnterOnFocused(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val node = focusedEditableNode() ?: return false
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else {
            commitTextIntoFocused("\n")
        }
    }

    /** Clamps a selection index into [0, length]; a missing index (-1) maps to the end (append). */
    private fun Int.coerceForText(text: String): Int =
        if (this in 0..text.length) this else text.length

    private fun clearInstance() {
        if (instance === this) {
            instance = null
            _editableFocused.value = false
            _dictateKeyboardActive.value = false
            _imeVisible.value = false
            bubble?.destroy()
            bubble = null
            recordingLine?.show(false)
            recordingLine = null
            lineScope.coroutineContext.cancelChildren()
            mainHandler.removeCallbacksAndMessages(null)
            stopMicForeground()
            flogDebug { "DictateAccessibilityService disconnected" }
        }
    }

    // --- Microphone foreground (while-in-background recording, Android 14+) ----------------------

    /**
     * Promotes the (already running, system-bound) service to a microphone foreground service so the
     * recording started from the floating button is allowed while the app is in the background. Promoting
     * an existing service sidesteps the "start a foreground service from the background" restriction.
     */
    fun startMicForeground() {
        if (isForeground) return
        val notification = buildNotification(getString(R.string.dictate__overlay_notification_recording))
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            isForeground = true
        }
    }

    /** Drops the microphone foreground state once the dictation has finished. */
    fun stopMicForeground() {
        if (!isForeground) return
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        isForeground = false
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_dictate_overlay_mic)
            .setContentTitle(getString(R.string.floris_app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIF_CHANNEL) != null) return
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            getString(R.string.dictate__overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIF_ID = 0xD1C7
        private const val NOTIF_CHANNEL = "dictate_overlay_recording"
        private const val CLIPBOARD_RESTORE_DELAY_MS = 400L
        private const val MAX_EDITABLE_SEARCH_DEPTH = 6

        // The next-field walk goes deeper than the dictation-target search, because it has to find
        // every field rather than the nearest one, and modern layouts nest hard. Both caps exist so
        // a pathological tree cannot turn one keypress into a long walk.
        private const val MA_FIELD_MAX_DEPTH = 40
        private const val MA_FIELD_MAX_COUNT = 40
        // Floating-button commit reliability (#161): resolve + focus the field the user sees so the input
        // connection binds to it, retry briefly while the host app rebuilds its field right after a send.
        private const val COMMIT_ATTEMPTS = 2
        private const val COMMIT_RETRY_DELAY_MS = 60L
        private const val FOCUS_SETTLE_MS = 40L
        // Debounce window for focus re-checks so a typing burst triggers at most one focused-node fetch.
        private const val FOCUS_UPDATE_DEBOUNCE_MS = 150L
        // Real-time overlay preview (#128): min gap between accessibility writes while streaming, so live
        // typing into another app doesn't flood the accessibility channel. 0 = apply every update (tested
        // to work smoothly in practice; raise if a target app can't keep up).
        private const val PREVIEW_THROTTLE_MS = 0L

        @Volatile
        private var instance: DictateAccessibilityService? = null

        /** Whether the service is connected and able to detect focus / inject text. */
        val isRunning: Boolean
            get() = instance != null

        /**
         * The running service, for dispatching pointer clicks, or null when it is not enabled.
         *
         * Handed out rather than wrapping every gesture call, because MaClickPlayer needs the
         * service itself: dispatchGesture is an instance method and the screen size it converts
         * against comes from the same instance's resources. Returning null rather than throwing is
         * the point — the service is switched on by hand in the system settings and may simply not
         * be, and every caller has to say so out loud instead of clicking nothing.
         */
        fun gestureService(): AccessibilityService? = instance

        /**
         * Presses the send button of the app in front. False when the service is off or nothing
         * on screen says it sends.
         *
         * The root is fetched here rather than held: a window that has changed since the last event
         * leaves a stale root pointing at a screen the user is no longer looking at, and clicking
         * inside that does something invisible in an app they cannot see.
         */
        /**
         * Presses the first of [targets] found on screen. Returns the label pressed, or null.
         *
         * Null covers two different situations and the caller has to tell them apart, which is why
         * [isRunning] exists beside this: the service being switched off is a thing the user can
         * fix, and nothing on screen matching is a thing they can only be told about.
         */
        /** Everything pressable on the screen in front, for the wand's picker. */
        /**
         * The package of the app in front, so a term can be learned and used for that app alone.
         *
         * Taken from the window itself rather than from what the switcher last observed: the
         * switcher deliberately ignores the launcher and the shade, and a term picked while one of
         * those was in front would otherwise be filed under whatever came before it.
         */
        fun foregroundPackage(): String? {
            val ims = instance ?: return null
            return runCatching {
                ims.windows
                    .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                    // Never this app. A term captured while the keyboard's own window answered was
                    // filed under tttlight and could then only ever fire inside TTT — which is
                    // nowhere, because the keyboard is not an app anybody presses buttons in. It
                    // showed up in Marko's list as "New chat, tttlight" for a button in Claude.
                    .filter { w ->
                        runCatching { w.root?.packageName?.toString() }.getOrNull() != ims.packageName
                    }
                    .maxByOrNull { it.layer }
                    ?.root
                    ?.let { root ->
                        val pkg = root.packageName?.toString()
                        runCatching { root.recycle() }
                        pkg
                    }
            }.getOrNull()
                ?: ims.rootInActiveWindow?.packageName?.toString()?.takeIf { it != ims.packageName }
        }

        /** Arms learning: the next button pressed in another app is remembered. */
        fun armLearn(): Boolean {
            if (instance == null) return false
            MaScreenTargets.Learn.arm()
            return true
        }

        /** The whole tree of what is on screen, as text, for copying out. */
        fun dumpScreen(): String {
            val ims = instance ?: return "The accessibility service is off."
            return MaScreenTargets.dumpTree(ims)
        }

        /** What the wand is doing, for the bar at the top of the keyboard. */
        val learnState get() = MaScreenTargets.Learn.state

        fun scanScreenTargets(everything: Boolean = false): List<String> {
            val ims = instance ?: return emptyList()
            return MaScreenTargets.scanClickable(ims, everything)
        }

        fun pressScreenTarget(targets: List<String>): String? {
            val ims = instance ?: return null
            return MaScreenTargets.pressFirstMatch(ims, targets)
        }

        /**
         * The screen's readable text, or empty when the service is not running.
         *
         * Wrapped here rather than exposing `instance`, so the service stays the only thing that
         * knows whether it is alive and callers cannot hold a reference to a dead one.
         */
        /** Scrolls the screen down one page. False when nothing scrolled. */
        suspend fun scrollScreenDown(): Boolean {
            val ims = instance ?: return false
            return MaScreenTargets.scrollBy(ims, 1)
        }

        fun readableScreenText(): String {
            val ims = instance ?: return ""
            return MaScreenTargets.readableText(ims)
        }

        /** Presses the match [rank] places up from the bottom-most. Null when there is no such match. */
        fun pressScreenTargetAt(targets: List<String>, rank: Int): String? {
            val ims = instance ?: return null
            return MaScreenTargets.pressMatch(ims, targets, rank)
        }

        /**
         * Presses the match [rank] places up from the bottom-most **of what is on screen**.
         *
         * The automatic bucket's whole world. Rank 0 is the lowest code block in the frame, rank 1
         * the one above it, and null means there is no such block in view — not that the document
         * has run out.
         */
        fun pressScreenTargetInView(targets: List<String>, rank: Int): String? {
            val ims = instance ?: return null
            return MaScreenTargets.pressMatch(ims, targets, rank, visibleOnly = true)
        }

        /** How many matches are in the frame right now. */
        fun countScreenTargetsInView(targets: List<String>): Int {
            val ims = instance ?: return 0
            return MaScreenTargets.countMatchesInView(ims, targets)
        }

        /**
         * COPY WHAT IS SELECTED, THROUGH ACCESSIBILITY, WHEN THE INPUT CONNECTION CANNOT.
         *
         * ### The failure this exists for
         *
         * Selecting text in another app makes Android collapse the keyboard, and the input
         * connection goes with it. Pinning the keyboard keeps it on screen but does not give the
         * connection back — so the copy key reports *"Failed to retrieve selected text requested to
         * copy: either selection state is invalid or an error occurred within the input
         * connection"*, which is true and useless.
         *
         * The selection is still there. It is on the SCREEN, in another app's view, and the
         * accessibility service can see it and act on it. **The keyboard lost its handle on the
         * text; the text did not go anywhere.**
         *
         * ### Why the node's own action rather than reading the text
         *
         * `ACTION_COPY` is performed BY the view that owns the selection, so it copies exactly what
         * that view thinks is selected — including a partial selection inside a formatted document,
         * which reading `text` and slicing by index gets wrong the moment the view's idea of an
         * index is not a character offset.
         *
         * Returns false when there is no focused node, no selection, or the action is refused, so
         * the caller can fall back rather than believing a copy happened.
         */
        fun copySelectionOnScreen(): Boolean {
            val ims = instance ?: return false
            return runCatching {
                val node = ims.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: ims.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: return false
                // A selection of zero length is a cursor, and copying a cursor is a no-op that would
                // look like success and leave the old clipboard in place.
                val start = node.textSelectionStart
                val end = node.textSelectionEnd
                if (start < 0 || end < 0 || start == end) return false
                node.performAction(AccessibilityNodeInfo.ACTION_COPY)
            }.getOrDefault(false)
        }

        /** The same, for cut. */
        fun cutSelectionOnScreen(): Boolean {
            val ims = instance ?: return false
            return runCatching {
                val node = ims.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: ims.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: return false
                val start = node.textSelectionStart
                val end = node.textSelectionEnd
                if (start < 0 || end < 0 || start == end) return false
                node.performAction(AccessibilityNodeInfo.ACTION_CUT)
            }.getOrDefault(false)
        }

        /** Scrolls the match at [rank] into view without pressing it. */
        fun revealScreenTargetAt(targets: List<String>, rank: Int): Boolean {
            val ims = instance ?: return false
            return MaScreenTargets.revealMatch(ims, targets, rank)
        }

        /** Moves focus to the next editable field. False when the service is off or nothing took it. */
        fun focusNextField(backwards: Boolean = false): Boolean {
            val ims = instance ?: return false
            return runCatching { ims.focusNextEditableField(backwards) }.getOrDefault(false)
        }

        private val _editableFocused = MutableStateFlow(false)

        /** Whether an editable text field currently holds input focus anywhere on screen. */
        val editableFocused: StateFlow<Boolean> = _editableFocused.asStateFlow()

        private val _dictateKeyboardActive = MutableStateFlow(false)

        /** Whether the Dictate keyboard is the currently selected input method. */
        val dictateKeyboardActive: StateFlow<Boolean> = _dictateKeyboardActive.asStateFlow()

        private val _imeVisible = MutableStateFlow(false)

        /** Whether a soft-keyboard (IME) window is currently shown on screen. */
        val imeVisible: StateFlow<Boolean> = _imeVisible.asStateFlow()

        private val _foregroundPackage = MutableStateFlow<String?>(null)

        /** Package name of the current foreground app, for per-app bubble positioning. */
        val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

        /**
         * Inserts [text] into the focused editable field via the running service, returning true on
         * success. Returns false when the service is not running or no editable field is focused.
         */
        fun injectText(text: String): Boolean = instance?.commitTextIntoFocused(text) ?: false

        /** The selection in the focused field, or empty when the service is unavailable. */
        fun selectedText(): String = instance?.selectedTextOfFocused() ?: ""

        /** The full text of the focused field, or empty when the service is unavailable. */
        fun fullText(): String = instance?.fullTextOfFocused() ?: ""

        /** Selects the whole focused field; false when the service is unavailable. */
        fun selectAll(): Boolean = instance?.selectAllInFocused() ?: false

        /** Presses Enter / the editor action on the focused field; false when unavailable. */
        fun performEnter(): Boolean = instance?.performEnterOnFocused() ?: false

        /** Removes the last inserted [text] from the focused field (undo, #133); false when unavailable. */
        fun deleteLastText(text: String): Boolean = instance?.deleteLastTextFromFocused(text) ?: false

        /** Real-time overlay preview (#128): throttled live update of the streamed text into the field. */
        fun setPreview(full: String) { instance?.setPreviewThrottled(full) }

        /** Replace the live preview with the finished/reworded [finalText] (unthrottled). */
        fun commitPreviewFinal(finalText: String) { instance?.commitPreviewFinalOnFocused(finalText) }

        /** Remove the live preview entirely (recording cancelled). */
        fun clearPreview() { instance?.clearPreviewOnFocused() }
    }
}
