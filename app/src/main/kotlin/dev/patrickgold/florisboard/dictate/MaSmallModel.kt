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

import dev.patrickgold.florisboard.dictate.provider.MaClaudeModels

/**
 * THE SMALL MODEL, NAMED, PER PROVIDER. HAIKU IS THE RULE.
 *
 * Every language job in this keyboard is small: proofread a sentence, reflow a paragraph, restyle a
 * line, guess the next word when the local n-gram cannot. **None of them is improved by a larger
 * model, and all of them are billed by it.**
 *
 * He set this as a rule rather than a preference: never an advanced model here, whatever is selected
 * for anything else. So the names are written down and enforced at the one place every such request
 * passes through, instead of being trusted to a default somebody else maintains.
 *
 * ### Why not `preset.defaultChatModel`
 *
 * That is what the code did, under a comment asserting the default is haiku, mini or flash "for
 * every provider in the registry". It was true when written and **nothing keeps it true**. A preset
 * edited next year, or a provider that renames its default, silently promotes every rewording to an
 * expensive model — and the only symptom is the bill, arriving a month later with nothing to point
 * at.
 *
 * ### Why a family and not an exact id
 *
 * Model ids carry version numbers that get retired — that is what `model-self-repair.md` exists for,
 * and this app already heals a dead id by asking the provider what it offers and stepping the number.
 * The names here are the CURRENT small model per provider; when one retires, the healer replaces it
 * within its family, and a family step never crosses from small to large.
 *
 * ### The fallback, and its honesty
 *
 * A provider not in this table falls through to the preset default, which is the old behaviour. That
 * is deliberate: a provider nobody has thought about should keep working rather than fail, and this
 * table is a list of promises kept rather than a gate.
 */
object MaSmallModel {

    /**
     * The small model for [providerId], or null if this provider has not been decided.
     *
     * Matched on a contains, because provider ids in the registry are not perfectly uniform —
     * "anthropic", "anthropic_claude" and similar have all appeared, and a lookup that misses
     * silently returns to the expensive path.
     */
    fun forProvider(providerId: String): String? {
        val id = providerId.lowercase()
        return when {
            // The newest Haiku, whatever its number. Resolved against Anthropic's model list.
            id.contains("anthropic") || id.contains("claude") -> MaClaudeModels.DEFAULT
            id.contains("groq") -> "openai/gpt-oss-20b"
            id.contains("gemini") || id.contains("google") -> "gemini-2.0-flash"
            id.contains("openai") -> "gpt-4o-mini"
            else -> null
        }
    }

    /** Every small model this app will use. For the test, and for anybody auditing the bill. */
    val ALL = listOf(
        MaClaudeModels.DEFAULT,
        "openai/gpt-oss-20b",
        "gemini-2.0-flash",
        "gpt-4o-mini",
    )

    /**
     * Words that appear in the name of a model too big for this app's jobs.
     *
     * Used by the test to assert nothing in [ALL] is one. **A list of things to refuse is worse than
     * a list of things to use** — this one exists only to check the other, and never to choose.
     */
    val TOO_BIG = listOf("opus", "sonnet", "gpt-4o", "gpt-5", "70b", "405b", "ultra", "pro")
}
