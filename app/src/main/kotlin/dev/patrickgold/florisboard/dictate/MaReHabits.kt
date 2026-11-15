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

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How Marko uses RE, one line per event (Marko, 15.11.2026: "we need to build the skills and learn of my usage habit,
 * which we don't know"). Kept in re/HABITS_LOG.md (synced to RE_MAGIC_DATABASE with the rest of re/), never in the prompt
 * of a single answer. RE's learn step reads the tail and keeps notes/usage-habits.md (when, where, to whom, how long his
 * instructions are, how often he reprompts or edits, which try he keeps, whether he waits or closes and lets RE work in
 * the background). Only counts and names of apps and people, never the message text.
 */
object MaReHabits {
    const val FILE = "HABITS_LOG.md"
    private const val KEEP = 600

    private fun f() = File(MaReChat.dir(), FILE)

    @Synchronized
    fun note(event: String, detail: String = "") {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm EEE", Locale.ROOT).format(Date())
            val where = listOfNotNull(MaReChat.pkg?.substringAfterLast('.'), MaReChat.person).joinToString(" · ").ifEmpty { "-" }
            val line = "- $stamp · $where · $event" + (if (detail.isNotBlank()) " · $detail" else "")
            val file = f()
            val old = if (file.exists()) file.readLines().filter { it.startsWith("- ") } else emptyList()
            val head = "# RE usage habits (raw log, newest last)\n\nOne line per event; RE's memory step turns it into notes/usage-habits.md.\n\n"
            file.writeText(head + (old + line).takeLast(KEEP).joinToString("\n") + "\n")
            MaReChat.synced()
        }
    }

    /** The newest [n] lines, for RE's learn step. */
    fun tail(n: Int = 80): String = runCatching { f().readLines().filter { it.startsWith("- ") }.takeLast(n).joinToString("\n") }.getOrDefault("")
}
