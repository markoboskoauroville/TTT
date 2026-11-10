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
import org.json.JSONObject
import java.io.File

/**
 * Marko's custom UI as the app's default (Marko, 10.11.2026: "export my configuration ... and distribute it as default
 * configuration with the app itself ... custom UI, that is it"). ONLY layout and look: feature rows, buttons and their
 * order, widths (Fit / percent), key colours, spacer width, copy row, macro slots (the labels on M keys), the font scale
 * and the Smartbar switch. Never engines, languages, behaviour, keys or history. A fresh install starts with it; an
 * update applies a newer shipped version again, keeping keys and history.
 */
object MaUiConfig {
    private const val ASSET = "default_config.json"

    private val prefs by FlorisPreferenceStore

    /** Name in the file to the preference that holds it. Strings, ints and booleans only. */
    private fun strings() = mapOf(
        "rows" to prefs.dictate.maRows,
        "key_widths" to prefs.dictate.maKeyWidths,
        "key_colors" to prefs.dictate.maKeyColors,
        "copy_row" to prefs.dictate.maCopyRow,
        "macro_slots" to prefs.dictate.maMacroSlots,
    )
    private fun ints() = mapOf(
        "spacer_tenths" to prefs.dictate.maSpacerTenths,
        "font_portrait" to prefs.keyboard.fontSizeMultiplierPortrait,
        "font_landscape" to prefs.keyboard.fontSizeMultiplierLandscape,
    )
    private fun bools() = mapOf(
        "feature_row_shown" to prefs.dictate.maFeatureRowShown,
        "zone_keyboard" to prefs.dictate.maZoneKeyboard,
        "smartbar_enabled" to prefs.smartbar.enabled,
    )

    /** The current UI as JSON (no secrets: only the allow-listed layout preferences). */
    fun export(version: Int): String {
        val o = JSONObject()
        o.put("version", version)
        strings().forEach { (k, p) -> o.put(k, p.get()) }
        ints().forEach { (k, p) -> o.put(k, p.get()) }
        bools().forEach { (k, p) -> o.put(k, p.get()) }
        return o.toString(2)
    }

    fun exportTo(c: Context, version: Int = 1): File {
        val f = File(c.applicationContext.getExternalFilesDir(null), "ui_config.json")
        f.writeText(export(version))
        return f
    }

    /** Apply the shipped default when its version is newer than the one applied here. Returns true when applied. */
    suspend fun applyShipped(c: Context): Boolean {
        val app = c.applicationContext
        val text = runCatching { app.assets.open(ASSET).bufferedReader().use { it.readText() } }.getOrNull() ?: return false
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return false
        val v = o.optInt("version", 0)
        val sp = app.getSharedPreferences("ttt_misc", Context.MODE_PRIVATE)
        if (v <= sp.getInt("ui_default_version", 0)) return false
        strings().forEach { (k, p) -> if (o.has(k)) p.set(o.getString(k)) }
        ints().forEach { (k, p) -> if (o.has(k)) p.set(o.getInt(k)) }
        bools().forEach { (k, p) -> if (o.has(k)) p.set(o.getBoolean(k)) }
        sp.edit().putInt("ui_default_version", v).putBoolean("widths_migrated_v1", true).apply()
        return true
    }
}
