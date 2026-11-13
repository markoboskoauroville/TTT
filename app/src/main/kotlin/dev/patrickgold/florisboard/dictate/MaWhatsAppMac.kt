/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * THE WHATSAPP TAB OF RE MAGIC (Marko, 13.11.2026): *"I want that this ReMagic is able to connect to the Groq or Queen
 * to this computer and do the things in WhatsApp, what I'm telling you to do ... I am going to use this chat to ask
 * through my phone and TTT on any device to work with WhatsApp on my computer inside Firefox it will be the second tab
 * in re."*
 *
 * What he says or types in the tab goes over Tailscale (any network) to the Mac's WhatsApp door
 * (claude-code/tools/wa_door.py, port 8877, tailnet identity, no code): Queen turns the words into one action on
 * WhatsApp Web in his Firefox (show latest, open a chat, scroll, draft, a draft with the Santoshi live picture). What
 * comes back: a short text, a screenshot of the WhatsApp page (taken by the browser itself, so it works with Firefox
 * behind other windows), the draft read back, and sometimes a list of chats to pick from.
 *
 * **Never sent by itself.** A draft stays a draft in the box on the Mac. Only "Send it" under a draft calls /whatsapp/send
 * with the exact approved text and the chat shown; the Mac refuses if the open chat or the text differs.
 */
object MaWhatsAppMac {
    /** The pseudo-partner that stands for this tab in MaClaudeLink's partner field (thread file claude/whatsapp.json). */
    const val TAB = "WhatsApp"
    const val DEFAULT_URL = "http://100.79.251.19:8877"
    private const val PREFS = "ma_whatsapp"
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(240, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun url(c: Context): String = sp(c).getString("url", DEFAULT_URL) ?: DEFAULT_URL
    fun setUrl(c: Context, u: String) = sp(c).edit().putString("url", u.trim().trimEnd('/')).apply()

    /** What the last answer left waiting: the chat of the draft and whether a picture goes with it. */
    fun pendingChat(c: Context): String = sp(c).getString("chat", "") ?: ""
    fun pendingPhoto(c: Context): Boolean = sp(c).getBoolean("photo", false)

    data class Reply(val ok: Boolean, val text: String, val image: String?, val draft: String?, val choices: List<String>, val chat: String)

    private fun post(c: Context, path: String, body: JSONObject): Reply = runCatching {
        http.newCall(Request.Builder().url(url(c) + path).post(body.toString().toRequestBody(JSON)).build()).execute().use { r ->
            val raw = r.body.string()
            val j = runCatching { JSONObject(raw) }.getOrNull()
                ?: return Reply(false, "The Mac answered HTTP ${r.code}: ${raw.take(120)}", null, null, emptyList(), "")
            val img = j.optString("image_b64").takeIf { it.isNotBlank() }?.let { keep(c, it) }
            val ch = j.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val draft = j.optString("draft", "").takeIf { it.isNotBlank() }
            val chat = j.optString("chat", "")
            val photo = j.optBoolean("photo", false)
            if (j.optBoolean("ok")) sp(c).edit().putString("chat", if (draft != null) chat else "").putBoolean("photo", draft != null && photo).apply()
            Reply(j.optBoolean("ok"), j.optString("say", j.optString("error", "(no text)")), img, draft, ch, chat)
        }
    }.getOrElse {
        Reply(false, "The Mac is not reachable over Tailscale: " + (it.message ?: it.javaClass.simpleName).take(100), null, null, emptyList(), "")
    }

    private fun keep(c: Context, b64: String): String? = runCatching {
        val dir = File(MaClaudeLink.dir(), "img").apply { mkdirs() }
        val f = File(dir, "wa_${System.currentTimeMillis()}.jpg")
        f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(40)?.forEach { it.delete() }
        f.path
    }.getOrNull()

    /** Words in, one action on the Mac. */
    suspend fun ask(c: Context, text: String): Reply = withContext(Dispatchers.IO) {
        post(c, "/whatsapp", JSONObject().put("text", text).put("from", "TTT RE Magic WhatsApp tab"))
    }

    /** A chat picked from the list the Mac offered. */
    suspend fun pick(c: Context, title: String): Reply = withContext(Dispatchers.IO) {
        post(c, "/whatsapp", JSONObject().put("pick", title))
    }

    /** "Send it": the exact approved text, the chat shown; the Mac checks both and refuses on any difference. */
    suspend fun sendApproved(c: Context, chat: String, text: String, photo: Boolean): Reply = withContext(Dispatchers.IO) {
        post(c, "/whatsapp/send", JSONObject().put("chat", chat).put("text", text).put("photo", photo))
    }
}
