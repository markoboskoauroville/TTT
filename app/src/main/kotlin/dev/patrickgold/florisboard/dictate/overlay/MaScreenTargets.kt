/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

import android.graphics.Path
import android.accessibilityservice.GestureDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import dev.patrickgold.florisboard.dictate.MaLog
import dev.patrickgold.florisboard.dictate.MaScreenText
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Finds a control on screen by what it says, and presses it.
 *
 * ### Why this rather than recognising the picture of a button
 *
 * Marko asked for template matching: crop the orange send button, look for that shape on screen,
 * tap it. That would work, and it is the harder and weaker of the two ways.
 *
 * Reading pixels needs `MediaProjection`, which throws a system consent dialog every session and
 * cannot be held open by a keyboard. Then the match has to survive a theme change, a font change, a
 * different screen density, the button being half scrolled off, and the same button drawn a shade
 * darker while it is pressed. Every one of those is a miss, and a miss looks exactly like the key
 * being broken.
 *
 * Android already hands us something better for free. Every control carries the label a screen
 * reader would announce, plus its exact rectangle on screen. `Add URL`, `Generate Images` and a send
 * arrow are all findable by that label with no pixels at all, and the answer is right whatever the
 * theme, the density or the scroll position. This is the same information a blind user's phone uses
 * to describe the screen, so it is maintained by the app author rather than guessed at by us.
 *
 * ### One key, several buttons
 *
 * The targets are a list and the first one found on screen wins. Marko's three buttons are never on
 * screen at the same time, so one key presses whichever is showing: the magic button.
 */
object MaScreenTargets {

    /** Labels that look like a target and are not. Checked first. */
    private val NEVER = listOf(
        "send feedback",
        "send to",
        "resend",
        "send later",
        "add urls to",
    )

    /**
     * Learning a button by watching it be pressed.
     *
     * ### Why this replaces reading a list
     *
     * The list was the wrong shape of help. A screen holds dozens of pressable things, the labels
     * are written for a screen reader rather than for a person choosing from a list, and Marko had
     * to work out which of thirty entries was the orange circle he actually wanted. He asked for the
     * opposite: touch the thing, and be told what it is called.
     *
     * A keyboard cannot watch a finger land on another app — the touch belongs to that app. But the
     * accessibility service is told, after the fact, when a control has been clicked, and the event
     * carries the control. So he presses the button for real, it does what it always does, and its
     * name arrives here as a side effect. Nothing is intercepted and nothing behaves differently.
     *
     * ### Why it is armed rather than always on
     *
     * Recording every click anybody makes would be both useless and unpleasant. It is armed by a
     * long press, listens for one click, and disarms — by success, by the timeout, or by being armed
     * again.
     */
    /**
     * The whole accessibility tree of what is on screen, as text, with nothing filtered out.
     *
     * ### Why a dump rather than a cleverer filter
     *
     * Three attempts at guessing which nodes matter have now failed on Marko's phone: everything
     * clickable was too much, page-only missed things, furniture lists caught the wrong ones. The
     * label a button carries is often nothing like the word on its face, and no rule written in
     * this file can know that.
     *
     * So this stops guessing. He copies the tree, sends it with a screenshot, and is told exactly
     * which name to use. The intelligence moves out of the heuristics and into the conversation,
     * which is where it was working all along.
     *
     * Every node is included, clickable or not, named or not. A node with no label is exactly the
     * kind of thing that turns out to be the button, and a dump that had already decided what
     * mattered would be the same mistake in a new place.
     */
    fun dumpTree(service: AccessibilityService): String {
        val sb = StringBuilder()
        var count = 0
        for (root in appWindowRoots(service)) {
            try {
                sb.append("=== ").append(root.packageName ?: "?").append(" ===\n")
                count += dumpNode(root, sb, 0, count)
            } finally {
                runCatching { root.recycle() }
            }
        }
        if (count >= MAX_DUMP_NODES) {
            sb.append("\n… stopped at ").append(MAX_DUMP_NODES)
                .append(" nodes. Scroll to what you want and dump again.\n")
        }
        return if (sb.isEmpty()) "Nothing on screen. Is the accessibility service on?" else sb.toString()
    }

    /**
     * A ceiling, because a dump has to survive being pasted into a chat.
     *
     * Not filtering, which is the thing being avoided — this takes the first N in tree order and
     * says so, rather than deciding which N deserve to be there.
     */
    private const val MAX_DUMP_NODES = 500

    private fun dumpNode(
        node: AccessibilityNodeInfo,
        sb: StringBuilder,
        depth: Int,
        soFar: Int,
    ): Int {
        if (soFar >= MAX_DUMP_NODES || depth > 30) return 0
        var written = 1
        val r = Rect().also { node.getBoundsInScreen(it) }
        sb.append("  ".repeat(depth.coerceAtMost(12)))
        sb.append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.substringAfterLast('/')?.let { sb.append(" id=").append(it) }
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            sb.append(" txt=\"").append(it.replace('\n', ' ').take(60)).append('"')
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            sb.append(" desc=\"").append(it.replace('\n', ' ').take(60)).append('"')
        }
        if (node.isClickable) sb.append(" CLICK")
        if (!node.isVisibleToUser) sb.append(" hidden")
        sb.append(" @").append(r.left).append(',').append(r.top)
            .append('-').append(r.right).append(',').append(r.bottom)
        sb.append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            written += dumpNode(child, sb, depth + 1, soFar + written)
            runCatching { child.recycle() }
        }
        return written
    }

    object Learn {

        /** What the wand is doing, for the bar at the top of the keyboard to show. */
        sealed interface State {
            /** Nothing happening. The bar is not drawn. */
            data object Idle : State

            /** Waiting for him to press a button in another app. */
            data object Waiting : State

            /** A button was pressed and is offered for storing. */
            data class Caught(val label: String, val appPackage: String?, val appName: String) : State
        }

        private val _state = MutableStateFlow<State>(State.Idle)

        /**
         * Observed by the feature row.
         *
         * A flow rather than a value read when the keyboard is next drawn, which is what this
         * replaced and why it appeared to do nothing at all: he presses the button in another app
         * while the keyboard is still up, so nothing of ours is redrawn and a value waiting to be
         * collected is never collected. The bar has to be told, not asked.
         */
        val state: StateFlow<State> = _state.asStateFlow()

        @Volatile
        private var armedUntil = 0L

        /**
         * Long enough to switch apps and find the button, short enough that a forgotten arming has
         * expired before the next unrelated tap.
         */
        private const val WINDOW_MS = 30_000L

        fun arm() {
            armedUntil = android.os.SystemClock.elapsedRealtime() + WINDOW_MS
            _state.value = State.Waiting
        }

        fun cancel() {
            armedUntil = 0L
            _state.value = State.Idle
        }

        fun isArmed(): Boolean = android.os.SystemClock.elapsedRealtime() < armedUntil

        /** Called once he has answered the bar, either way. */
        fun clear() {
            _state.value = State.Idle
        }

        /**
         * Offered every click while armed.
         *
         * A click on something with no name is ignored and the arming stands: an unnamed control
         * cannot be found again by name, so storing it would produce a term that never matches, and
         * staying armed lets him simply try a different button.
         */
        fun onClicked(node: AccessibilityNodeInfo, ownPackage: String, appName: String) {
            if (!isArmed()) return
            val pkg = node.packageName?.toString()
            // Our own keys are clicks too, and the long press that armed this is one of them.
            // Catching that would teach the wand the name of the wand.
            if (pkg == null || pkg == ownPackage) return
            val label = labelOf(node) ?: childLabel(node) ?: return
            armedUntil = 0L
            _state.value = State.Caught(label, pkg, appName)
        }
    }

    /**
     * Everything on screen that can be pressed, with the label that would find it.
     *
     * The answer to "how would I know its name". Claude's send button carries no visible text — it
     * is an orange circle with an arrow — so there is nothing to type into the term list, and until
     * now the only way to find its label was to guess. This reads the labels straight out of the
     * screen and lets him pick one.
     *
     * Only genuinely pressable things are listed. A screen holds hundreds of nodes and almost all of
     * them are layout: offering those would bury the four buttons that matter under a wall of
     * containers, and picking a container adds a term that matches something nobody can press.
     *
     * Deduplicated, because one button is often several nested nodes carrying the same label, and
     * three identical rows in the picker look like a fault rather than a choice.
     */
    /**
     * @param everything skip the page and furniture filters and return all of it
     *
     * The filters are right nearly always and wrong occasionally: a button in a browser's own
     * toolbar, or one whose id happens to sit on the furniture list. Rather than argue about the
     * edge cases, the search box in the picker asks for the unfiltered set and lets him look
     * through it himself. A filter you can switch off is a filter you can trust.
     */
    fun scanClickable(service: AccessibilityService, everything: Boolean = false): List<String> {
        val out = LinkedHashSet<String>()
        for (root in appWindowRoots(service)) {
            try {
                // The page, not the browser around it.
                //
                // In a browser the address bar, the tab counter, the menu and the back button are
                // all clickable and all irrelevant: Marko is looking for a button on the site, and
                // offering him "New tab" and "Bookmarks" alongside it is the noise he asked to be
                // rid of.
                //
                // The two are cleanly separable. Web content is rendered by the engine and appears
                // under a node of class android.webkit.WebView; the browser's own furniture is
                // ordinary Android views outside it. So when a page is present, only the page is
                // read. When there is none — a native app, where everything on screen is the app —
                // the whole window is read as before.
                val contents = if (everything) emptyList() else webContents(root)
                if (contents.isEmpty()) {
                    collectClickable(root, out, 0, everything)
                } else {
                    for (content in contents) {
                        try {
                            collectClickable(content, out, 0, everything)
                        } finally {
                            runCatching { content.recycle() }
                        }
                    }
                }
            } finally {
                runCatching { root.recycle() }
            }
        }
        return out.toList()
    }

    /**
     * The web page roots inside a window, or empty when the window holds none.
     *
     * Nested WebViews are not descended into twice: the outer one already contains the inner, and
     * collecting both would offer every button on the page a second time.
     */
    private fun webContents(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        collectWebViews(root, found, 0)
        return found
    }

    private fun collectWebViews(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int,
    ) {
        if (depth > 28) return
        if (node.isVisibleToUser && isWebRoot(node)) {
            out.add(AccessibilityNodeInfo.obtain(node))
            // Stop here: everything below is this page, and it will be walked when the page is read.
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectWebViews(child, out, depth + 1)
            runCatching { child.recycle() }
        }
    }

    /**
     * Whether this node is the top of a rendered web page.
     *
     * Two ways of telling, because one browser is not enough. Chromium and the system WebView both
     * report the class below, and Marko uses Chrome. Firefox renders through GeckoView, which does
     * not always report that class, so the view id is checked as well — the ids below are the
     * containers each browser puts its page inside.
     *
     * Getting this wrong is safe in one direction only, which is why both checks are narrow: failing
     * to find a page means the whole window is scanned, which is merely the old noisy behaviour.
     * Matching something that is not a page would hide the buttons that are actually there.
     */
    private fun isWebRoot(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() == WEB_VIEW_CLASS) return true
        val id = node.viewIdResourceName?.substringAfterLast('/') ?: return false
        return id in WEB_ROOT_IDS
    }

    /** The class Chromium and the system WebView both report for a rendered page. */
    private const val WEB_VIEW_CLASS = "android.webkit.WebView"

    /** Page containers, by view id: Chromium first, then GeckoView as Firefox builds it. */
    private val WEB_ROOT_IDS = setOf(
        "compositor_view_holder",
        "webview",
        "engine_view",
        "gecko_view",
        "geckoview",
    )

    private fun collectClickable(
        node: AccessibilityNodeInfo,
        out: MutableSet<String>,
        depth: Int,
        everything: Boolean = false,
    ) {
        if (depth > 28) return
        // Only things that are themselves pressable. This used to accept any node sitting inside a
        // clickable parent, which on a chat screen means every line of the conversation: Marko's
        // list came back holding "Claude is AI and can make mistakes" and the whole message body.
        // A screen is mostly text, and a picker full of text is a picker nobody can find a button in.
        if (node.isVisibleToUser && node.isClickable && node.isEnabled &&
            (everything || !isChrome(node))
        ) {
            // The label may be on the button or on the icon inside it, so look down as well as at
            // the node itself — but only for the button's own label, not for everything under it.
            (labelOf(node) ?: childLabel(node))?.let { out.add(it) }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickable(child, out, depth + 1, everything)
            runCatching { child.recycle() }
        }
    }

    /**
     * Whether this is the app's own furniture rather than something in the content.
     *
     * Only matters for apps with no web page in them, where the whole window is scanned and the
     * toolbar comes with it. A chat app's back arrow, hamburger, overflow and new-conversation
     * button are clickable, are never what Marko is reaching for, and sit at the top of every list
     * because they sit at the top of every screen.
     *
     * Matched on the view id rather than the label, because the label is what he might legitimately
     * search for: a page could contain a button honestly called "Menu". An id belongs to the app's
     * own layout and is not something a web page produces at all.
     *
     * Deliberately short and it should stay short. This is a list of things certain to be furniture,
     * not a general tidy-up: anything wrongly on it is a button he cannot reach, and that failure is
     * silent.
     */
    private fun isChrome(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase() ?: return false
        return CHROME_IDS.any { id == it }
    }

    /** Browser and app furniture, by view id. Exact matches only. */
    private val CHROME_IDS = setOf(
        // Chromium's toolbar
        "url_bar", "search_box_text", "tab_switcher_button", "menu_button", "home_button",
        "back_button", "forward_button", "refresh_button", "bookmark_button",
        // GeckoView's toolbar, as Firefox builds it
        "mozac_browser_toolbar_url_view", "mozac_browser_toolbar_menu", "counter_box",
        "mozac_browser_toolbar_navigation_actions",
        // Android's own
        "navigation_bar_item_icon_view",
    )

    /** The first short label among a button's immediate children, for an icon button. */
    private fun childLabel(node: AccessibilityNodeInfo): String? {
        for (i in 0 until minOf(node.childCount, 6)) {
            val child = node.getChild(i) ?: continue
            val label = labelOf(child)
            runCatching { child.recycle() }
            if (label != null) return label
        }
        return null
    }

    /**
     * A short, typeable label for a node, or null when it has nothing worth matching.
     *
     * Long text is dropped rather than truncated. A paragraph that happens to be tappable is not a
     * button anybody names, and a truncated term would match the wrong thing later.
     */
    private fun labelOf(node: AccessibilityNodeInfo): String? {
        val raw = node.contentDescription?.toString()?.trim()
            ?: node.text?.toString()?.trim()
            ?: node.viewIdResourceName?.substringAfterLast('/')?.replace('_', ' ')
        val label = raw?.replace('\n', ' ')?.trim().orEmpty()
        // Short enough to be a button's name. A tappable paragraph is not a button anybody
        // names, and a truncated term would match the wrong control later.
        if (label.isBlank() || label.length > 32) return null
        return label
    }

    /**
     * Scrolls the page under the keyboard by [pages]. Negative scrolls up. Returns false if nothing
     * on screen could be scrolled.
     *
     * Uses the scroll action rather than a swipe gesture. A swipe has to guess where the list is,
     * how far a page is on this screen, and how fast to move so it reads as a drag rather than a
     * fling — three guesses, each wrong on some screen. The action asks the view itself to advance
     * one page, which is the same thing a hardware page-down does and is exactly one page every
     * time.
     */
    suspend fun scrollBy(service: AccessibilityService, pages: Int): Boolean {
        if (pages == 0) return true
        val action = if (pages > 0) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        var moved = false
        repeat(kotlin.math.abs(pages)) { step ->
            val target = findScrollable(service) ?: return moved
            val ok = try {
                // Jump if the app will let us, animate only if it will not.
                //
                // ACTION_SCROLL_FORWARD is a REQUEST, and a list answers it with its own smooth
                // scroll — the animation belongs to the app, not to us, and cannot be turned off
                // from here. ACTION_SCROLL_TO_POSITION is different: a RecyclerView answers it with
                // `scrollToPosition`, which moves in one frame with nothing to watch.
                //
                // It only works where the list publishes its row count, so this tries the jump and
                // falls back. On a screen that does not expose a collection, the animation stays
                // and is not ours to remove.
                jumpForward(target, pages > 0) || target.performAction(action)
            } finally {
                runCatching { target.recycle() }
            }
            if (!ok) return moved
            moved = true
            // The node is found again for each page rather than held, because scrolling replaces the
            // rows inside it and a node captured before the move can be stale by the time the next
            // one is asked for. The pause lets the scroll settle so the second page starts from
            // where the first finished rather than racing it.
            if (step < kotlin.math.abs(pages) - 1) delay(SCROLL_SETTLE_MS)
        }
        return moved
    }

    /**
     * Moves a list by one screenful with no animation, or false when it cannot.
     *
     * Works out how many rows are on screen from the ones currently attached, then asks for the row
     * one screenful further on. A list that publishes neither a collection nor any item rows cannot
     * be jumped, and says so rather than guessing at a position.
     */
    private fun jumpForward(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val collection = node.collectionInfo ?: return false
        val total = collection.rowCount
        if (total <= 0) return false
        val rows = visibleRowRange(node) ?: return false
        val span = (rows.last - rows.first + 1).coerceAtLeast(1)
        val target = if (forward) rows.last + span else rows.first - span
        if (target < 0 || target >= total) return false
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, target)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
        }
        return node.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id,
            args,
        )
    }

    /** The first and last row indices currently attached, or null when the list publishes none. */
    private fun visibleRowRange(node: AccessibilityNodeInfo): IntRange? {
        var lowest = Int.MAX_VALUE
        var highest = Int.MIN_VALUE
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val row = child.collectionItemInfo?.rowIndex
            runCatching { child.recycle() }
            if (row == null || row < 0) continue
            if (row < lowest) lowest = row
            if (row > highest) highest = row
        }
        return if (highest >= lowest) lowest..highest else null
    }

    /**
     * The biggest scrollable thing on screen, which is the page rather than a side list.
     *
     * Screens often hold several: a horizontal strip of chips, a small menu, and the actual content.
     * Largest by area is the content in every case worth handling, and it is a far better rule than
     * first-found, which tends to be whichever toolbar happens to sit earliest in the tree.
     */
    private fun findScrollable(service: AccessibilityService): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        for (root in appWindowRoots(service)) {
            try {
                val found = mutableListOf<AccessibilityNodeInfo>()
                collectScrollable(root, found, 0)
                for (node in found) {
                    val r = Rect().also { node.getBoundsInScreen(it) }
                    val area = r.width() * r.height()
                    if (area > bestArea) {
                        runCatching { best?.recycle() }
                        best = node
                        bestArea = area
                    } else {
                        runCatching { node.recycle() }
                    }
                }
            } finally {
                runCatching { root.recycle() }
            }
        }
        return best
    }

    private fun collectScrollable(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int,
    ) {
        if (depth > 28) return
        if (node.isVisibleToUser && node.isScrollable) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollable(child, out, depth + 1)
            runCatching { child.recycle() }
        }
    }

    /** Long enough for one page to land before the next is asked for. */
    private const val SCROLL_SETTLE_MS = 220L

    /**
     * Presses the first target found. Returns the label pressed, or null when nothing matched.
     *
     * The label is returned rather than a boolean so the key can say which button it pressed. On a
     * screen with several possible targets that is the difference between trusting the key and
     * wondering what it just did.
     */
    fun pressFirstMatch(service: AccessibilityService, targets: List<String>): String? =
        pressMatch(service, targets, rank = 0)

    /**
     * Presses the match [rank] places **up** the screen from the bottom-most one.
     *
     * Rank 0 is the lowest, which is the newest answer in a chat. Rank 1 is the one above it. That
     * is what the automatic bucket counts through: press, press, press, and it walks up the page
     * taking one code block each time.
     *
     * Returns null when there is no match that far up, which is how the caller learns it has reached
     * the top of what the screen currently holds.
     */
    fun pressMatch(
        service: AccessibilityService,
        targets: List<String>,
        rank: Int,
        visibleOnly: Boolean = false,
    ): String? {
        for (root in appWindowRoots(service)) {
            try {
                val hit = findIn(service, root, targets, rank, visibleOnly = visibleOnly)
                if (hit != null) return hit
            } finally {
                runCatching { root.recycle() }
            }
        }
        return null
    }

    /**
     * Scrolls the match at [rank] into view without pressing it. True when one was found.
     *
     * ### What this is for
     *
     * After the automatic bucket copies a block, it reveals the **next** one up. The list scrolls to
     * bring that into view, which pushes the block just copied down towards the bottom of the
     * screen — so the last thing he collected is the last thing he sees, and he can read down the
     * page to check what went into the buckets without counting.
     *
     * ### And it is what makes a long chat reachable at all
     *
     * A list destroys the rows far from the viewport and rebuilds them on demand, so the ones he has
     * not scrolled near **do not exist** to be found (§58). Revealing the next block is what causes
     * the rows above it to be built, which is why the ladder can keep climbing instead of stopping
     * at whatever happened to be in memory when he started.
     */
    fun revealMatch(service: AccessibilityService, targets: List<String>, rank: Int): Boolean {
        for (root in appWindowRoots(service)) {
            try {
                if (findIn(service, root, targets, rank, clickIt = false) != null) return true
            } finally {
                runCatching { root.recycle() }
            }
        }
        return false
    }

    /**
     * The roots of the application windows, front-most first, never the keyboard's own.
     *
     * `rootInActiveWindow` alone is not enough and is the likeliest reason a press finds nothing:
     * while the keyboard is up, the active window can be the input method's, so the search walks the
     * keyboard's own tree — where there is no send button, because the send button is in the app
     * underneath.
     *
     * Windows are ordered by layer, so the highest is the dialog on top rather than the page behind
     * it. That matters for `Add URL`: the dialog and the page beneath it are both present, and the
     * button worth pressing is the one the user can see.
     */

    /**
     * How many matches are on the screen right now.
     *
     * Counted rather than pressed, so the key can say "three code blocks in view" before touching
     * anything, and so a loop over them knows where to stop.
     */
    fun countMatchesInView(service: AccessibilityService, targets: List<String>): Int {
        for (root in appWindowRoots(service)) {
            try {
                val found = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
                collect(root, targets, found, 0)
                val onScreen = found.count { (node, _) -> isOnScreen(service, node) }
                if (onScreen > 0) return onScreen
            } finally {
                runCatching { root.recycle() }
            }
        }
        return 0
    }

    /**
     * Whether this node is inside the frame, and not a zero-sized one.
     *
     * `isVisibleToUser` alone is not enough: a node scrolled just past the fold can still answer
     * true in some hierarchies, and a node of no size answers true while occupying nothing. The
     * bounds have to intersect the display as well, which is the check that matches what he can
     * point at.
     */
    private fun isOnScreen(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (!runCatching { node.isVisibleToUser }.getOrDefault(false)) return false
        val r = Rect().also { runCatching { node.getBoundsInScreen(it) } }
        if (r.width() <= 0 || r.height() <= 0) return false
        val metrics = service.resources.displayMetrics
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        return Rect.intersects(screen, r)
    }

    internal fun appWindowRoots(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val own = service.packageName
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val roots = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .mapNotNull { w -> runCatching { w.root }.getOrNull() }
            .filter { it.packageName?.toString() != own }
        // The active window as a fallback, for the case where the window list is empty because no
        // service in the system has requested interactive windows yet.
        return roots.ifEmpty { listOfNotNull(service.rootInActiveWindow) }
    }

    private fun findIn(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        targets: List<String>,
        rank: Int = 0,
        clickIt: Boolean = true,
        /**
         * Only what is on the screen right now.
         *
         * **This is the one place a visibility filter is the feature rather than caution.** Every
         * other filter of this kind in this file was a bug — `isVisibleToUser` was added to the field
         * walk as a safety check and made TAB refuse exactly the fields hardest to reach, and the
         * same filter quietly crippled the magic finger for months, because off screen is a reason
         * to scroll to something and not a reason to decide it does not exist.
         *
         * The automatic bucket is different, and Marko is the one who found out why: collecting is
         * not linear. He scrolls up, takes a block, scrolls down, takes another. A key that walked
         * the whole document by a counter was answering a question he had stopped asking. **What he
         * means by "this one" is what he is looking at**, so here the frame is the question.
         */
        visibleOnly: Boolean = false,
    ): String? {
        val found = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        collect(root, targets, found, 0)
        if (visibleOnly) {
            found.retainAll { (node, _) -> isOnScreen(service, node) }
        }
        if (found.isEmpty()) return null

        // Bottom-most wins, and now that means the LAST one rather than the last one on screen.
        //
        // Two reasons, and they turn out to be the same reason. A page can carry the word "send" in
        // its text and a send button in the composer below it; the control is always the lower of
        // the two. And a chat carries a copy button under every answer ever given, where the one
        // worth pressing is under the newest — which is the lowest.
        //
        // So the ordering is not a tiebreak, it is the whole rule: scan the tree, take the lowest.
        // Since a node scrolled below the fold has a larger bottom than anything on screen, this
        // reaches the newest answer even when he has scrolled away from it, instead of handing him
        // a copy button from last month.
        // Ordered lowest first, then counted into. Rank 0 is the bottom-most, which is what every
        // caller but the automatic bucket wants; rank 1 is the one above it, and so on.
        //
        // Sorting the whole list rather than taking a maximum is what makes "the next one up" a
        // question this can answer at all. On a chat that is the answer before last, the one before
        // that, and so on up the page — which is the order somebody actually collecting code blocks
        // wants them in.
        val ordered = found.sortedWith(
            // Announced names first, then lowest. A wrapper matching only through its view id can
            // never outrank the button that says its own name, however the boxes happen to sit.
            compareByDescending<Pair<AccessibilityNodeInfo, String>> { (node, _) ->
                isAnnounced(node, targets)
            }.thenByDescending { (node, _) ->
                Rect().also { node.getBoundsInScreen(it) }.bottom
            },
        )
        val best = ordered.getOrNull(rank)
        var pressed: String? = null
        if (best != null) {
            // The label often sits on an icon inside the button rather than on the button, so walk
            // up until something is clickable. Bounded: an unbounded walk ends at the window root,
            // and clicking that does whatever the app does on a stray tap.
            var target: AccessibilityNodeInfo? = best.first
            var hops = 0
            while (target != null && !(target.isClickable && target.isEnabled) && hops < 5) {
                target = target.parent
                hops++
            }
            if (target != null && target.isClickable && target.isEnabled) {
                // Bring it into the frame first. A control below the fold can be clicked perfectly
                // well without this, but the app then scrolls to wherever it acted, and a press
                // whose effect happens somewhere unseen is indistinguishable from a press that did
                // nothing. Reached through AccessibilityAction and its id because this one never
                // had a plain int constant on AccessibilityNodeInfo.
                runCatching {
                    target.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id,
                    )
                }
                pressed = if (clickIt) {
                    // The ordinary path: bring it into the frame, then press it.
                    //
                    // If ACTION_CLICK is refused, tap the pixels instead. Compose builds its
                    // accessibility nodes by hand, and a node that reports `isClickable` does not
                    // always accept the action — Gemini's send button is one: the tree shows a
                    // clickable View wrapping a "Send" label, the walk finds it, and the action
                    // returns false with nothing happening.
                    //
                    // A dispatched tap at the centre of its bounds is what a finger does, and it
                    // works on anything that is genuinely on screen. Second, not first: ACTION_CLICK
                    // is precise, survives an element moving between the look and the press, and
                    // does not care what is drawn on top. **The gesture is the fallback because it
                    // is the blunter instrument, not because it is worse.**
                    if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        best.second
                    } else if (tapNode(service, target)) {
                        MaLog.add("keys", "click refused, tapped '${best.second}' instead")
                        best.second
                    } else {
                        null
                    }
                } else {
                    // Reveal only. The scroll above has already happened; this reports that the
                    // match exists and was brought into view, without touching it.
                    best.second
                }
            }
        }
        found.forEach { runCatching { it.first.recycle() } }
        return pressed
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        targets: List<String>,
        out: MutableList<Pair<AccessibilityNodeInfo, String>>,
        depth: Int,
    ) {
        // Deep enough for a real screen, bounded so a pathological layout cannot turn one key press
        // into thousands of calls across a process boundary.
        if (depth > 28) return
        matchOf(node, targets)?.let { out.add(AccessibilityNodeInfo.obtain(node) to it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, targets, out, depth + 1)
            runCatching { child.recycle() }
        }
    }

    private fun matchOf(node: AccessibilityNodeInfo, targets: List<String>): String? {
        // Visibility is deliberately NOT a condition.
        //
        // It was, and it made the finger refuse the presses worth most. A button five screens down
        // a long list is reported as not visible to the user, so the one press that saves the most
        // scrolling was the one press that could not be made — the finger only ever reached what
        // was already under his thumb.
        //
        // Off screen is a reason to scroll to a control, not a reason to decide it does not exist.
        // The press step asks for it to be brought into the frame first.
        //
        // What replaces the check is the ordering in findIn and the NEVER list: a match still has
        // to carry the label, still has to be clickable or sit under something clickable, and the
        // lowest match on screen still wins.
        // Description, text and the view's id all count. A button drawn as an icon carries only a
        // description; one drawn as a word carries only text; and an unlabelled button sometimes
        // still has a telling id such as send_button.
        val label = buildString {
            node.contentDescription?.let { append(it).append(' ') }
            node.text?.let { append(it).append(' ') }
            node.viewIdResourceName?.let { append(it.substringAfterLast('/')) }
        }.lowercase(Locale.ROOT).replace('_', ' ')
        if (label.isBlank()) return null
        if (NEVER.any { label.contains(it) }) return null
        return targets.firstOrNull { t ->
            val target = t.lowercase(Locale.ROOT)
            if (target.contains(' ')) {
                // A phrase matches as a phrase, so "generate image" still finds "Generate Images 2"
                // however the coin count beside it changes.
                label.contains(target)
            } else {
                // A single word matches whole, so "send" does not fire on "resend" or "sendbird".
                label.split(' ', '/', ':', '.', '-', ',').any { it == target }
            }
        }
    }

    /**
     * Everything on screen worth reading aloud, in reading order.
     *
     * Walks the same windows the finger uses, keeps what [MaScreenText.isWorthReading] allows, and
     * hands the rest to [MaScreenText.assemble] to be ordered and joined.
     *
     * Only what is materialised, which is the viewport and roughly a screen either side — Android
     * destroys rows far from view, so they do not exist to be read (§59). That is the whole reason
     * this reads the visible screen rather than promising a document.
     */
    fun readableText(service: AccessibilityService): String {
        val lines = mutableListOf<MaScreenText.Line>()
        var windowCount = 0
        for (root in appWindowRoots(service)) {
            windowCount++
            try {
                collectText(root, lines, 0)
            } finally {
                runCatching { root.recycle() }
            }
        }
        val text = MaScreenText.assemble(lines).take(MaScreenText.MAX_CHARS)
        // What was found, and from how many windows, because "the reader does nothing in this app"
        // has three different causes that look identical: no window reachable, a window with no
        // text worth reading, or text found and the voice failing afterwards. One line separates
        // them and saves another round of guessing across a chat window.
        MaLog.add("read", "screen: $windowCount windows, ${lines.size} lines, ${text.length} chars")
        return text
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<MaScreenText.Line>, depth: Int) {
        if (depth > 30 || out.size > 400) return
        val text = node.text?.toString()
        if (MaScreenText.isWorthReading(text, node.isClickable)) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            out.add(MaScreenText.Line(text!!.trim(), r.top, r.left))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out, depth + 1)
            runCatching { child.recycle() }
        }
    }

    /**
     * Whether the match came from a name the app *announces*, rather than from its view id.
     *
     * This is the difference between the send button and its wrapper in Gemini. The button carries
     * `contentDescription="Send"`; the ComposeView around it carries no name at all and matches only
     * because its id happens to read `..._input_send_button_compose`. Both are called "send" by
     * [matchOf], and the wrapper's box is a few pixels taller — so bottom-most chose the wrapper,
     * whose nearest clickable ancestor is the whole input sheet. The finger tapped the text field
     * and nothing was ever sent.
     *
     * A description or a text is what a person means by a button's name. An id is a programmer's
     * spelling that leaked into the tree. So an announced name outranks an id match always, and
     * position only decides between equals.
     */
    private fun isAnnounced(node: AccessibilityNodeInfo, targets: List<String>): Boolean {
        val spoken = buildString {
            node.contentDescription?.let { append(it).append(' ') }
            node.text?.let { append(it) }
        }.lowercase(Locale.ROOT)
        if (spoken.isBlank()) return false
        return targets.any { t ->
            val target = t.lowercase(Locale.ROOT)
            if (target.contains(' ')) {
                spoken.contains(target)
            } else {
                spoken.split(' ', '/', ':', '.', '-', ',').any { it == target }
            }
        }
    }
}

/**
 * Taps the centre of a node's bounds, as a finger would.
 *
 * The last resort when a node claims to be clickable and then refuses the click. Fifty milliseconds
 * down and up at the middle of what is on screen: long enough to register everywhere, short enough
 * that nothing reads it as a long press.
 *
 * Returns whether the gesture was accepted for dispatch, not whether the app did anything with it —
 * nothing can tell us that, and a tap on the right pixels is the whole of what a finger promises.
 */
private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    if (bounds.isEmpty) return false
    val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
}
