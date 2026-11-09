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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * The engine keys, Keystore-encrypted (AES-GCM, key never leaves the Keystore), never printed or
 * logged. Keys come only from files (Marko, 9.11.2026): the picker on Voice transcription buttons, or a file
 * pushed to `Android/data/<app>/files/keys/`, both through [KeyImporter].
 * AssemblyAI keeps using the app's existing key ring when no key was given here.
 */
object EngineKeys {
    private const val ALIAS = "ttt_engine_keys"
    private const val PREFS = "ttt_engine_keys"

    fun keyId(engineId: String) = if (engineId.startsWith("groq")) "groq" else engineId

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return g.generateKey()
    }

    fun save(c: Context, engineId: String, value: String): Boolean = runCatching {
        val t = value.trim()
        if (t.isEmpty()) return false
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.ENCRYPT_MODE, secret())
        val enc = ci.doFinal(t.toByteArray())
        prefs(c).edit().putString(keyId(engineId), Base64.encodeToString(ci.iv + enc, Base64.NO_WRAP)).apply()
        true
    }.getOrDefault(false)

    fun delete(c: Context, engineId: String) {
        prefs(c).edit().remove(keyId(engineId)).apply()
    }

    /** The lines stored here (the ring), without the legacy fallback. */
    fun ownLines(c: Context, engineId: String): List<String> = runCatching {
        val raw = Base64.decode(prefs(c).getString(keyId(engineId), null) ?: return@runCatching emptyList<String>(), Base64.NO_WRAP)
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(ci.doFinal(raw.copyOfRange(12, raw.size))).lines().filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** The ring: every stored key, one per line (Cloudflare holds account id then token; a service account is one JSON line). */
    fun lines(c: Context, engineId: String): List<String> {
        val own = ownLines(c, engineId)
        if (own.isNotEmpty()) return own
        // The existing key ring (AssemblyAI is already set up in the keyboard).
        if (engineId == "assemblyai") {
            val k = runCatching {
                val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
                val acc = prefs.dictate.providerAccounts.get().getOrEmpty("assemblyai")
                dev.patrickgold.florisboard.dictate.MaKeyRingStore.currentKey("assemblyai", acc.apiKey)
            }.getOrDefault("")
            if (k.isNotBlank()) return listOf(k)
        }
        return emptyList()
    }

    /** Replaces the ring (empty deletes it). */
    fun setLines(c: Context, engineId: String, l: List<String>) {
        if (l.isEmpty()) delete(c, engineId) else save(c, engineId, l.joinToString("\n"))
    }

    /** Adds the keys that are not already in the ring; returns how many were new. */
    fun merge(c: Context, engineId: String, add: List<String>): Int {
        val cur = ownLines(c, engineId)
        val fresh = add.map { it.trim() }.filter { it.isNotEmpty() && it !in cur }.distinct()
        if (fresh.isNotEmpty()) setLines(c, engineId, cur + fresh)
        return fresh.size
    }

    /** "5 keys" for the provider cards; null when none. Cloudflare and a service account count as accounts. */
    fun countLabel(c: Context, engineId: String): String? {
        val l = lines(c, engineId)
        if (l.isEmpty()) return null
        return when (keyId(engineId)) {
            "cloudflare" -> if (l.size >= 2) "1 account" else null
            "google_chirp" -> if (l.size == 1) "1 account" else "${l.size} accounts"
            else -> if (l.size == 1) "1 key" else "${l.size} keys"
        }
    }

    fun has(c: Context, engineId: String): Boolean {
        val spec = EngineCatalog.byId(engineId) ?: return false
        if (!spec.needsKey) return true
        if (engineId == "cloudflare") return lines(c, engineId).size >= 2
        return lines(c, engineId).isNotEmpty()
    }

    /** Size only, never the value. */
    fun describe(c: Context, engineId: String): String {
        val l = lines(c, engineId)
        return if (l.isEmpty()) "no key" else "key set (" + l.size + (if (l.size == 1) " line)" else " lines)")
    }

    fun pushedDir(c: Context): File? = c.getExternalFilesDir("keys")

    /**
     * Keys enter only through files. A file pushed into the app's keys folder (adb, tests) goes the same
     * way as one chosen with the picker: [KeyImporter] finds the keys, tests them, keeps the working
     * ones, and the file is deleted. Returns the summary lines, never a key.
     */
    suspend fun importPushed(c: Context): List<String> {
        val dir = pushedDir(c) ?: return listOf("no keys folder")
        val files = dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        if (files.isEmpty()) return listOf("no files in the keys folder")
        val texts = files.map { it.name to runCatching { it.readText() }.getOrDefault("") }
        val out = KeyImporter.importTexts(c, texts) { }
        files.forEach { it.delete() }
        return out.summary.lines()
    }
}

/** Switches, orders, usage and benchmark results, in one small preferences file. */
object EngineStore {
    private const val PREFS = "ttt_engines"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun chainOn(c: Context) = p(c).getBoolean("chain_on", true)
    fun setChainOn(c: Context, v: Boolean) = p(c).edit().putBoolean("chain_on", v).apply()
    fun clipsOn(c: Context) = p(c).getBoolean("clips_on", true)
    fun setClipsOn(c: Context, v: Boolean) = p(c).edit().putBoolean("clips_on", v).apply()
    fun weeklyOn(c: Context) = p(c).getBoolean("weekly_on", false)
    fun setWeeklyOn(c: Context, v: Boolean) = p(c).edit().putBoolean("weekly_on", v).apply()
    fun fakeOn(c: Context) = p(c).getBoolean("fake_on", false)
    fun setFakeOn(c: Context, v: Boolean) = p(c).edit().putBoolean("fake_on", v).apply()

    /** Free engines are on by default; engines with no free quota only when he turns them on. */
    fun enabled(c: Context, id: String): Boolean {
        val spec = EngineCatalog.byId(id) ?: return false
        return p(c).getBoolean("en_$id", !spec.paid)
    }
    fun setEnabled(c: Context, id: String, v: Boolean) = p(c).edit().putBoolean("en_$id", v).apply()

    fun order(c: Context, lang: String): List<String> {
        val raw = p(c).getString("order_$lang", null)
        val stored = runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
        return EngineCatalog.complete(stored, lang)
    }
    fun setOrder(c: Context, lang: String, ids: List<String>) =
        p(c).edit().putString("order_$lang", JSONArray(ids).toString()).apply()

    fun usage(c: Context, id: String): Quota.Usage {
        val o = runCatching { JSONObject(p(c).getString("use_$id", "{}")!!) }.getOrDefault(JSONObject())
        return Quota.Usage(o.optLong("w"), o.optDouble("s", 0.0), o.optDouble("c", 0.0))
    }
    fun addUsage(c: Context, id: String, seconds: Double) {
        val spec = EngineCatalog.byId(id) ?: return
        val u = Quota.add(spec, usage(c, id), seconds, System.currentTimeMillis())
        p(c).edit().putString("use_$id", JSONObject().put("w", u.windowStart).put("s", u.seconds).put("c", u.costUsd).toString()).apply()
    }

    /**
     * The engine chosen for the H or E key (Marko, 9.11.2026). Until he chooses, it is what the keyboard
     * already used: AssemblyAI, or Parakeet when the Transcription model setting says on the phone. The
     * benchmark never changes it by itself; only "Use best per language" does.
     */
    fun selected(c: Context, lang: String): String {
        val l = if (lang == "en") "en" else "hr"
        p(c).getString("sel_$l", null)?.let { if (EngineCatalog.byId(it) != null) return it }
        return runCatching {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            if (prefs.dictate.maTranscriptionEngine.get() == dev.patrickgold.florisboard.dictate.MaProviders.ENGINE_LOCAL) "parakeet" else "assemblyai"
        }.getOrDefault("assemblyai")
    }
    fun setSelected(c: Context, lang: String, id: String) = p(c).edit().putString("sel_" + (if (lang == "en") "en" else "hr"), id).apply()

    /** The last key import or re-test summary (counts only, never a key). */
    fun lastKeys(c: Context): String? = p(c).getString("keys_last", null)
    fun setLastKeys(c: Context, text: String) = p(c).edit().putString("keys_last", text).apply()

    fun lastBenchmark(c: Context): String? = p(c).getString("bench_last", null)
    fun setLastBenchmark(c: Context, json: String) = p(c).edit().putString("bench_last", json).apply()
}
