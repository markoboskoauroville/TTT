/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.engines

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the H and E keys show, live (Marko, 9.11.2026): the engine chosen for each key, and, while a
 * fallback is happening, the engine that is really answering. Never a silent switch.
 */
object EngineLive {
    data class State(val selected: Map<String, String> = emptyMap(), val active: Map<String, String> = emptyMap())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(c: Context) {
        _state.value = _state.value.copy(selected = mapOf("hr" to EngineStore.selected(c, "hr"), "en" to EngineStore.selected(c, "en")))
    }

    fun select(c: Context, lang: String, id: String) {
        EngineStore.setSelected(c, lang, id)
        load(c)
    }

    /** The engine really working on [lang] right now; null when none is (or the chosen one is). */
    fun setActive(lang: String, id: String?) {
        val m = _state.value.active.toMutableMap()
        if (id == null) m.remove(lang) else m[lang] = id
        _state.value = _state.value.copy(active = m)
    }
}
