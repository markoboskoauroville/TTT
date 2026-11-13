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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.patrickgold.florisboard.dictate.engines.EngineKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * THE RE CHAT WITH A CLAUDE CODE SESSION (Marko, 13.11.2026, through MAC_SES3_13-NOV): *"let's copy what we built in
 * TTT. It's an iMessage chat window, and we chat through that ... You send a message, I send a message ... I will send
 * when I press Send. If there is an error (like in TTT), then it's sent, not automatically."*
 *
 * The RE chat window gets partners beside RE itself: "Claude Code · <session name>", one thread each
 * (re/claude/<slug>.json, on the phone only). Only TEXT travels.
 *
 * - **Out**: what he sends goes to the Mac's relay (VOICE_ENGINE_ANDROID/mac/phonereply.py), `POST /reply` with
 *   {to, to_id, text, at, id} and the header X-Mantra-Token. The token comes only from a key file
 *   (Download/Api/mantra-relay-token.txt, Import keys from file), stored encrypted. Not reachable: the message waits in
 *   the outbox (re/claude/outbox.json) and is tried again (every 20 s, at most 2 minutes apart), each with its id, so the
 *   relay delivers it once.
 * - **In**: the Mac sends by adb (broadcast dev.ttt.remote.CLAUDE_SAY, from + text, see EngineJobs). The message joins
 *   that session's thread, the open chat shows it and speaks it when voice mode is on; a closed chat gets a notification
 *   that opens it on that thread, and the Reader speaks it if voice mode is on.
 */
object MaClaudeLink {
    const val RING = "mantra_relay"
    const val DEFAULT_URL = "http://100.79.251.19:8875/reply"
    private const val PREFS = "ma_claude_link"
    private const val CHANNEL = "ttt_claude_code"
    const val EXTRA_PARTNER = "claude_partner"

    data class Incoming(val from: String, val msg: MaReChat.Msg)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val outboxLock = Mutex()
    private var flushJob: Job? = null

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 16)
    val incoming = _incoming.asSharedFlow()

    /** True while the chat window is open on screen (it shows incoming messages itself). */
    @Volatile var chatOpen = false

    @Volatile var status: String = ""
        private set

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The partner the chat talks to: null = RE (the AI), else a Claude Code session name. */
    fun partner(c: Context): String? = sp(c).getString("partner", null)?.takeIf { it.isNotBlank() }
    fun setPartner(c: Context, name: String?) = sp(c).edit().putString("partner", name ?: "").apply()

    fun url(c: Context): String = sp(c).getString("url", DEFAULT_URL) ?: DEFAULT_URL
    fun setUrl(c: Context, u: String) = sp(c).edit().putString("url", u.trim()).apply()

    fun hasToken(c: Context) = EngineKeys.ownLines(c, RING).isNotEmpty()

    /** The key file picked right from the chat's message (13.11.2026: "message itself is file picker"). */
    fun importToken(c: Context, text: String): String {
        val tok = text.lines().map { it.trim() }.firstOrNull { Regex("[A-Za-z0-9_-]{16,}").matches(it) }
            ?: return "No key found in that file. Tap here and pick mantra-relay-token.txt."
        EngineKeys.setLines(c, RING, listOf(tok))
        status = ""
        flush(c)
        return "Mac relay key stored. Sending what was waiting…"
    }

    fun slug(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").take(60).ifBlank { "session" }

    fun dir(): File = File(MaReChat.dir(), "claude").apply { mkdirs() }

    /** Thread file name relative to re/ for MaReChat.load/save. */
    fun threadName(name: String) = "claude/" + slug(name) + ".json"

    /** Every session he has a thread with, newest first, by display name. */
    fun sessions(c: Context): List<String> {
        val names = sp(c).getStringSet("names", emptySet()).orEmpty()
        return names.sortedByDescending { File(dir(), slug(it) + ".json").lastModified() }
    }

    private fun remember(c: Context, name: String) {
        val cur = sp(c).getStringSet("names", emptySet()).orEmpty()
        if (name !in cur) sp(c).edit().putStringSet("names", cur + name).apply()
    }

    fun forget(c: Context, name: String) {
        val cur = sp(c).getStringSet("names", emptySet()).orEmpty()
        sp(c).edit().putStringSet("names", cur - name).apply()
        runCatching { File(dir(), slug(name) + ".json").delete() }
        if (partner(c) == name) setPartner(c, null)
    }

    // ---------------------------------------------------------------- in

    /** A message from a Claude Code session (adb broadcast). Joins its thread; shown, spoken or notified. */
    fun receive(c: Context, from: String, text: String, image: String? = null) {
        val app = c.applicationContext
        val name = from.trim().ifBlank { "Claude Code" }
        remember(app, name)
        val m = MaReChat.Msg(false, text.trim(), image = image?.let { keepImage(app, it) })
        val file = threadName(name)
        synchronized(this) { MaReChat.save(MaReChat.load(file) + m, file) }
        if (chatOpen) {
            _incoming.tryEmit(Incoming(name, m))
            return
        }
        // Closed chat: open it on this thread when he taps; speak now when voice mode is on.
        notify(app, name, m.shown.ifBlank { "Picture" })
        if (m.shown.isNotBlank() && MaReChat.voiceMode(app) && !MaReChat.muted(app)) {
            scope.launch(Dispatchers.Main) { runCatching { MaReader.speakText(app, m.shown) { } } }
        }
    }

    /**
     * A picture the Mac pushed by adb into TTT's own files (Android/data/<pkg>/files/claude_inbox/…, [rel] relative to that
     * folder): copied into the chat's folder so the thread keeps it. Nothing outside that folder is read.
     */
    private fun keepImage(c: Context, rel: String): String? = runCatching {
        val base = c.getExternalFilesDir(null)!!.canonicalFile
        val src = File(base, rel.removePrefix("/")).canonicalFile
        if (!src.path.startsWith(base.path + File.separator) || !src.isFile) return null
        val out = File(File(dir(), "img").apply { mkdirs() }, "${System.currentTimeMillis()}_${src.name}")
        src.copyTo(out, overwrite = true)
        src.delete()
        out.path
    }.getOrNull()

    private fun notify(c: Context, from: String, text: String) = runCatching {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Claude Code messages", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = Intent(c, MaReChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_PARTNER, from)
        val pi = PendingIntent.getActivity(c, slug(from).hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Claude Code · $from")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(slug(from).hashCode(), n)
    }

    // ---------------------------------------------------------------- out

    private fun outboxFile() = File(dir(), "outbox.json")

    private fun readOutbox(): JSONArray = runCatching { JSONArray(outboxFile().readText()) }.getOrDefault(JSONArray())

    private fun writeOutbox(a: JSONArray) = runCatching { outboxFile().writeText(a.toString()) }

    fun waiting(): Int = readOutbox().length()

    /** He pressed SEND: the message is queued (with its id) and sent now; unreachable, it waits and is tried again. */
    fun send(c: Context, to: String, text: String) {
        val app = c.applicationContext
        remember(app, to)
        scope.launch {
            outboxLock.withLock {
                val a = readOutbox()
                a.put(
                    JSONObject().put("id", UUID.randomUUID().toString()).put("to", to).put("text", text)
                        .put("at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.ROOT).format(java.util.Date())),
                )
                writeOutbox(a)
            }
            flush(app)
        }
    }

    /** Sends what waits, oldest first; keeps what could not go and tries again later. */
    fun flush(c: Context) {
        val app = c.applicationContext
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            var wait = 20_000L
            while (true) {
                val left = outboxLock.withLock { sendAll(app) }
                if (left == 0) { status = ""; break }
                delay(wait)
                wait = (wait * 2).coerceAtMost(120_000L)
            }
        }
    }

    private fun sendAll(c: Context): Int {
        val a = readOutbox()
        if (a.length() == 0) return 0
        // The relay trusts his tailnet (tailscale whois, 13.11.2026: "use the tail scale"); a token is sent only if one exists.
        val tok = EngineKeys.ownLines(c, RING).firstOrNull()
        val keep = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val body = JSONObject().put("to", o.getString("to")).put("to_id", "").put("text", o.getString("text"))
                .put("at", o.optString("at")).put("id", o.getString("id")).put("from", "Marko, TTT chat")
            val ok = runCatching {
                http.newCall(
                    Request.Builder().url(url(c)).apply { if (tok != null) header("X-Mantra-Token", tok) }
                        .post(body.toString().toRequestBody(JSON)).build(),
                ).execute().use { r ->
                    if (r.code == 401) status = "The Mac relay asks for its key. Tap here and pick mantra-relay-token.txt."
                    r.isSuccessful
                }
            }.getOrElse { status = "The Mac is not reachable: " + (it.message ?: it.javaClass.simpleName).take(80); false }
            if (!ok) keep.put(o)
        }
        writeOutbox(keep)
        if (keep.length() > 0 && status.isBlank()) status = "${keep.length()} message(s) waiting for the Mac"
        return keep.length()
    }
}
