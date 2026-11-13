/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/**
 * Where the settings were left, so the gear key can put him back there.
 *
 * Reaching a setting is the expensive part on a phone driven by voice and read with difficulty.
 * Changing it once you are looking at it is not. So the gear key does not open the settings home and
 * leave him to find his way down again: it opens the screen he was last on, scrolled to where he was
 * on it.
 *
 * **Both halves or it is not worth having.** The right screen at the top of itself still costs a
 * scroll on a page he was at the bottom of, and the settings home when he was three levels into the
 * key manager is the same as remembering nothing at all.
 *
 * ### Why SharedPreferences rather than the datastore
 *
 * This is written from the keyboard process and read from the settings activity, and it is written
 * on every scroll settle. It is scratch state about where somebody was standing, not a preference
 * anybody chose, and it has no business in the exported settings the datastore holds.
 *
 * ### The offset is stored against the route it was measured on
 *
 * A scroll position means nothing on a different page. Four hundred pixels down the provider list is
 * the middle of it and four hundred pixels down a short screen is the bottom or nowhere, so an
 * offset applied to the wrong route does not look like a bug, it looks like the app jumped. Both
 * values are written together and the offset is only ever handed back when the route matches.
 */
object MaSettingsResume {

    private const val FILE = "ma_settings_resume"
    private const val K_ROUTE = "route"
    private const val K_SCROLL_ROUTE = "scroll_route"
    private const val K_SCROLL = "scroll"
    private const val K_ARMED = "armed"

    /** The settings home, used when nothing has been recorded yet. */
    const val DEFAULT_PATH = "settings/home"

    /**
     * Route key (the class's serial name, as the nav graph knows it) to deep-link path.
     *
     * Filled in by `composableWithDeepLink` as the graph is built, because that function is the one
     * place that holds both halves at once. Deriving it anywhere else means keeping a second list in
     * step with the first, and the second list is the one that rots.
     */
    private val pathsByRoute = HashMap<String, String>()

    /** Route class to path (13.11.2026): matched with hasRoute, which does not depend on how the route is named. */
    private val pathsByClass = LinkedHashMap<KClass<*>, String>()

    fun register(routeKey: String?, path: String) {
        if (routeKey != null) pathsByRoute[routeKey] = path
    }

    fun register(kClass: KClass<*>, path: String) {
        pathsByClass[kClass] = path
        register(kClass.qualifiedName, path)
    }

    fun pathFor(routeKey: String?): String? = pathsByRoute[routeKey]

    /**
     * The deep-link path of a destination. By class first (Marko, 13.11.2026: the gear kept opening the settings home;
     * looking a screen up by its route STRING can miss when the serial name differs from the class name, and then
     * nothing deeper than the home was ever recorded).
     */
    fun pathOf(destination: NavDestination?): String? {
        if (destination == null) return null
        for ((k, path) in pathsByClass) {
            @Suppress("UNCHECKED_CAST")
            if (runCatching { destination.hasRoute(k as KClass<Any>) }.getOrDefault(false)) return path
        }
        return pathFor(destination.route)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Remembers the screen he is looking at. Called as the settings nav graph changes destination. */
    fun rememberRoute(context: Context, path: String) {
        android.util.Log.i("TTT-SETTINGS", "remember $path")
        prefs(context).edit().putString(K_ROUTE, path).apply()
    }

    /** Remembers how far down that screen he had scrolled. */
    fun rememberScroll(context: Context, path: String, offset: Int) {
        prefs(context).edit()
            .putString(K_SCROLL_ROUTE, path)
            .putInt(K_SCROLL, offset)
            .apply()
    }

    fun lastPath(context: Context): String =
        prefs(context).getString(K_ROUTE, null) ?: DEFAULT_PATH

    /**
     * The stored offset for [path], or null.
     *
     * Consumed once: it is handed back only when the gear key armed it, and taking it clears the
     * arming. Ordinary navigation into a screen therefore starts at the top, which is what anybody
     * expects from tapping through a settings list. Restoring a scroll position on every visit would
     * mean a list that never starts where it is looked at.
     */
    fun consumeScroll(context: Context, path: String): Int? {
        val p = prefs(context)
        if (!p.getBoolean(K_ARMED, false)) return null
        p.edit().putBoolean(K_ARMED, false).apply()
        if (p.getString(K_SCROLL_ROUTE, null) != path) return null
        val offset = p.getInt(K_SCROLL, 0)
        return if (offset > 0) offset else null
    }

    /**
     * Opens settings where they were left.
     *
     * BROWSABLE is required: FlorisAppActivity.onNewIntent only routes a VIEW intent to the nav
     * graph's deep-link handler when it carries this category, and without it the intent is treated
     * as an extension import and lands on the wrong screen entirely.
     */
    /**
     * Opens one particular settings screen, rather than wherever the user left off.
     *
     * Used by keys that lead somewhere specific — holding the wand goes to the wand's own list, not
     * to whatever page was last read. Deliberately does not disturb the remembered route: the gear
     * key should still return to where he was, and a detour through the wand's list is not "where
     * he was".
     */
    fun open(context: Context, path: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/$path"))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun open(context: Context) {
        val path = lastPath(context)
        android.util.Log.i("TTT-SETTINGS", "gear opens $path")
        prefs(context).edit().putBoolean(K_ARMED, true).apply()
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/$path"))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            // A deep link that no longer resolves must not leave the gear key doing nothing at all.
            // The stored route can outlive the screen it names across an update, so fall back to the
            // home screen and forget the route rather than keep failing on it.
            prefs(context).edit().remove(K_ROUTE).putBoolean(K_ARMED, false).apply()
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/$DEFAULT_PATH"))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
