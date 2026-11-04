/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.nlp

/**
 * WHAT HAIKU TAUGHT THE LOCAL ENGINE: AFTER THESE WORDS, THESE WORDS.
 *
 * Marko, 4.11.2026: "Haiku should improve local prediction as we go, using its database to better
 * predict words itself."
 *
 * Every answer Haiku gives while he types (MaAiPredict.live) is kept here under the two words before
 * the cursor, per language, on the phone. The local engine then offers those words itself, from
 * here, after his own words and before the shipped dictionary ([MaNgram.predict]); and where it
 * already has enough of them, Haiku is not asked again ([MaNgram.teacherWords]), which is the saving.
 *
 * A soft layer, never his counts: a word Haiku guessed is not a word he wrote. Only what he actually
 * writes or picks goes into the n-gram model, as before. Capped at [MAX_CONTEXTS], the least recently
 * used dropped first. Pure: walked by `MaTeacherMemoryTest`.
 */
class MaTeacherMemory {

    /** Most recently used last (LinkedHashMap in access order). */
    private val map = object : LinkedHashMap<String, List<String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > MAX_CONTEXTS
    }

    var isDirty = false
        private set

    @Synchronized fun remember(previousTwo: String?, previousOne: String?, words: List<String>) {
        val clean = words.map { it.trim() }.filter { it.isNotEmpty() && !it.contains(' ') }
        if (clean.isEmpty()) return
        val k = key(previousTwo, previousOne)
        val merged = (clean + (map[k] ?: emptyList()))
            .distinctBy { it.lowercase() }
            .take(PER_CONTEXT)
        map[k] = merged
        isDirty = true
    }

    /** The words kept for this context that continue [prefix], in the order Haiku gave them. */
    @Synchronized fun words(previousTwo: String?, previousOne: String?, prefix: String): List<String> {
        val p = prefix.lowercase()
        val list = map[key(previousTwo, previousOne)] ?: return emptyList()
        return list.filter { it.lowercase().startsWith(p) && !it.equals(prefix, ignoreCase = true) }
    }

    @Synchronized fun size(): Int = map.size

    @Synchronized fun clear() { map.clear(); isDirty = true }

    @Synchronized fun serialize(): String {
        isDirty = false
        return map.entries.joinToString("\n") { (k, v) -> k + "\t" + v.joinToString(" ") }
    }

    @Synchronized fun parse(text: String) {
        map.clear()
        text.split('\n').forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val words = line.substring(tab + 1).split(' ').filter { it.isNotEmpty() }
            if (words.isNotEmpty()) map[line.substring(0, tab)] = words.take(PER_CONTEXT)
        }
        isDirty = false
    }

    companion object {
        /** How many words are kept for one context. */
        const val PER_CONTEXT = 6

        /** How many contexts are kept; the least recently used goes first. */
        const val MAX_CONTEXTS = 3000

        /** The two words before the cursor, lowercased; a missing one is empty. */
        fun key(previousTwo: String?, previousOne: String?): String =
            (previousTwo.orEmpty().lowercase()) + "\u001F" + (previousOne.orEmpty().lowercase())
    }
}
