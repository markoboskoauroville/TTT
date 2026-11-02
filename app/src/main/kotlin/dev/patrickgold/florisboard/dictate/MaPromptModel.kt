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
import dev.patrickgold.florisboard.dictate.llm.MaPhoneModels
import dev.patrickgold.florisboard.dictate.provider.MaClaudeModels

/**
 * WHICH MODEL A PROMPT RUNS ON, BY NAME. §191.
 *
 * Each prompt offered "Default" beside Haiku, Sonnet and Opus, and "Default" meant "whatever the
 * rewording account is set to", which the screen never said. He called it confusing and asked for
 * it to go: he wants to see which model is in use, not a mask over it.
 *
 * So a prompt's model is always a name. [nameBlankModels] turns an empty value an old build left
 * into the model it stood for, once, at start; [effective] does the same on the way to a request
 * in case one slips through; [describe] is what the screen prints under the choice.
 */
object MaPromptModel {

    /** The rewording account's model: what an empty per-prompt model used to mean. */
    fun accountModel(): String {
        val account = MaProviders.rewordingAccount()
        val preset = MaProviders.presetFor(account)
        return MaClaudeModels.upgradeOldDefault(account.chatModel.ifBlank { preset.defaultChatModel.orEmpty() })
            .ifBlank { MaClaudeModels.DEFAULT }
    }

    /** [stored], or the account's model when it is empty. Never blank. */
    fun effective(stored: String): String = stored.trim().ifBlank { accountModel() }

    /** Names every empty per-prompt model. Does nothing once they all have one. */
    suspend fun nameBlankModels() {
        val prefs by FlorisPreferenceStore
        val d = prefs.dictate
        for (pref in listOf(d.maProofreadModel, d.maFlowModel, d.maSpellcheckModel, d.maTranslateModel)) {
            if (pref.get().isBlank()) pref.set(accountModel())
        }
        if (d.maPhoneStyleModel.get().isBlank()) d.maPhoneStyleModel.set(MaPhoneModels.QWEN3_4B.choice)
    }

    /**
     * The exact model, as he should read it: "claude-haiku-4-5-20251001 (newest Haiku)", or
     * "Qwen3 4B Instruct 2507 on this phone, downloaded".
     */
    fun describe(context: Context, model: String): String {
        MaPhoneModels.byChoice(model)?.let { m ->
            val state = if (MaPhoneModels.isInstalled(context, m)) "downloaded" else "not downloaded yet"
            return "${m.name} on this phone, $state"
        }
        val tier = MaClaudeModels.tierOfAlias(model)
        val resolved = MaClaudeModels.resolve(model, MaProviders.rewordingAccount().cachedModels)
        return if (tier == null) resolved else "$resolved (newest ${tier.replaceFirstChar { it.uppercase() }})"
    }
}
