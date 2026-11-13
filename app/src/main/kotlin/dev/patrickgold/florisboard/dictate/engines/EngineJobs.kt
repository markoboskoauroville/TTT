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

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.patrickgold.florisboard.dictate.DictateController
import java.io.File
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The weekly benchmark: Wi-Fi (unmetered) and charging, once a week, only while the switch is on. */
class EngineBenchmarkJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(p: JobParameters): Boolean {
        val c = applicationContext
        if (!EngineStore.weeklyOn(c)) return false
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { EngineBenchmark.run(c) { } }
            jobFinished(p, false)
        }
        return true
    }

    override fun onStopJob(p: JobParameters): Boolean { job?.cancel(); return true }

    companion object {
        private const val ID = 6310

        fun schedule(c: Context, on: Boolean) {
            val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (!on) { js.cancel(ID); return }
            js.schedule(
                JobInfo.Builder(ID, ComponentName(c, EngineBenchmarkJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresCharging(true)
                    .setPersisted(true)
                    .setPeriodic(TimeUnit.DAYS.toMillis(7))
                    .build(),
            )
        }
    }
}

/**
 * Debug hooks for testing over adb (the shell holds android.permission.DUMP, which the manifest
 * requires of the sender, so no other app can call this):
 *   engines_keys            encrypt the key files pushed to Android/data/<app>/files/keys, delete them
 *   engines_seed            import clips from files/seed (<lang>_<n>.wav + .txt reference)
 *   engines_fake on|off     the always-429 test engine in front of the chain
 *   engines_demo            run the chain on the newest clip, showing every status for a few seconds
 *   engines_ping <engine>   one tiny call; status only
 *   engines_bench           run the benchmark
 *   engines_report          print state (counts, quota left, key present yes/no)
 * Output goes to files/engines_log.txt (never a key).
 */
/** The remote API's answer for one command: files/remote/<id>.json (id from the extra "id", else "last"). */
internal fun remoteOut(app: Context, i: Intent, o: JSONObject) {
    val dir = File(app.getExternalFilesDir(null), "remote").apply { mkdirs() }
    runCatching { File(dir, (i.getStringExtra("id") ?: "last").replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json").writeText(o.put("at", System.currentTimeMillis()).toString(1)) }
}

class EngineDebugReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        val log = File(app.getExternalFilesDir(null), "engines_log.txt")
        fun out(s: String) { runCatching { log.appendText(s + "\n") } }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (i.action) {
                    "dev.ttt.engines.KEYS" -> EngineKeys.importPushed(app).forEach { out("keys " + it) }
                    "dev.ttt.engines.RETEST" -> KeyImporter.retest(app) { }.summary.lines().forEach { out("retest " + it) }
                    "dev.ttt.engines.ENABLE" -> { EngineStore.setEnabled(app, i.getStringExtra("e") ?: "", i.getStringExtra("v") != "off"); out("enable " + i.getStringExtra("e")) }
                    "dev.ttt.engines.SEED" -> {
                        val d = File(app.getExternalFilesDir(null), "seed")
                        var n = 0
                        var t = System.currentTimeMillis() - 100_000
                        for (w in d.listFiles().orEmpty().filter { it.name.endsWith(".wav") }.sortedBy { it.name }) {
                            val lang = if (w.name.startsWith("en")) "en" else "hr"
                            val ref = File(d, w.name.removeSuffix(".wav") + ".txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                            if (ClipBuffer.add(app, w, lang, ref, "seed", t) != null) n++
                            t += 1000
                        }
                        out("seed: $n clips (hr ${ClipBuffer.count(app, "hr")}, en ${ClipBuffer.count(app, "en")})")
                    }
                    "dev.ttt.engines.FAKE" -> { EngineStore.setFakeOn(app, i.getStringExtra("v") == "on"); out("fake engine " + (i.getStringExtra("v") == "on")) }
                    "dev.ttt.engines.DEMO" -> {
                        val lang = if (i.getStringExtra("lang") == "en") "en" else "hr"
                        val clip = ClipBuffer.latest(app, lang, 1).firstOrNull()
                        if (clip == null) { out("demo: no clip") } else {
                            val chain = EngineChain.usableIds(app, lang).let { if (EngineCatalog.FAKE in it) it else listOf(EngineCatalog.FAKE) + it }
                                .map { EngineChain.candidate(app, it, clip.wav, lang, record = false) }
                            val hold = i.getLongExtra("hold", 4000L)
                            try {
                                val r = FallbackRunner.run(chain, EngineChain.timeoutMs(clip.seconds, false), { s ->
                                    out("status: $s"); DictateController.showEngineStatus(s)
                                })
                                out("demo result: ${r.engineName} ${r.ms} ms, ${r.text.length} chars")
                            } catch (e: AllEnginesFailed) { out("demo: all failed " + e.attempts.map { it.id + ":" + it.kind }) }
                            delay(hold)
                            DictateController.showEngineStatus("")
                        }
                    }
                    "dev.ttt.engines.EXPORT_UI" -> {
                        val f = dev.patrickgold.florisboard.dictate.MaUiConfig.exportTo(app, i.getIntExtra("v", 1))
                        out("ui exported " + f.length() + " bytes")
                    }
                    "dev.ttt.engines.SIMNET" -> {
                        KeyRing.simulateNetFailures = i.getIntExtra("n", 3)
                        out("simulating " + KeyRing.simulateNetFailures + " network failures")
                    }
                    // ---- The remote API (Marko, 13.11.2026: "make Python API so you can do anything from the distance").
                    // Only adb can send these (the receiver needs android.permission.DUMP). Answers go to
                    // files/remote/<id>.json, which tools/ttt_remote.py pulls. Never a key value.
                    "dev.ttt.remote.TYPE" -> {
                        val t = i.getStringExtra("t").orEmpty()
                        kotlinx.coroutines.withContext(Dispatchers.Main) { DictateController.insertHistoryText(app, t) }
                        remoteOut(app, i, JSONObject().put("ok", true).put("typed", t.length))
                    }
                    "dev.ttt.remote.STATUS" -> {
                        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
                        val v = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode }.getOrDefault(0L)
                        val rings = JSONObject()
                        for (r in listOf("groq", "gemini", "google_v1", "deepgram", "assemblyai", "speechmatics", "soniox", "elevenlabs",
                            "speechify", "anthropic", "cloudflare", "google_chirp", dev.patrickgold.florisboard.dictate.MaReEngine.RING,
                            dev.patrickgold.florisboard.dictate.MaReGithub.RING)) rings.put(r, EngineKeys.ownLines(app, r).size)
                        remoteOut(app, i, JSONObject().put("versionCode", v)
                            .put("reEngine", dev.patrickgold.florisboard.dictate.MaReEngine.engine())
                            .put("reChat", dev.patrickgold.florisboard.dictate.MaReChat.chatOn(app))
                            .put("readerVoice", prefs.dictate.maReaderVoice.get())
                            .put("language", dev.patrickgold.florisboard.dictate.MaLanguage.active())
                            .put("state", DictateController.state.value.javaClass.simpleName)
                            .put("keysPerRing", rings)
                            .put("githubDb", dev.patrickgold.florisboard.dictate.MaRemoteDb.hasToken())
                            .put("hH", EngineStore.selected(app, "hr")).put("hE", EngineStore.selected(app, "en")))
                    }
                    "dev.ttt.remote.CLAUDE_SAY" -> {
                        // A Claude Code session on the Mac writes to Marko (MaClaudeLink, 13.11.2026), by text over adb:
                        // am broadcast -a dev.ttt.remote.CLAUDE_SAY -n <pkg>/<receiver> --es from "<session>" --es text "<message>"
                        val from = i.getStringExtra("from").orEmpty()
                        val text = i.getStringExtra("text").orEmpty()
                        val ok = text.isNotBlank()
                        if (ok) kotlinx.coroutines.withContext(Dispatchers.Main) { dev.patrickgold.florisboard.dictate.MaClaudeLink.receive(app, from, text) }
                        remoteOut(app, i, JSONObject().put("ok", ok).put("from", from).put("chars", text.length)
                            .put("chatOpen", dev.patrickgold.florisboard.dictate.MaClaudeLink.chatOpen)
                            .put("waitingToMac", dev.patrickgold.florisboard.dictate.MaClaudeLink.waiting()))
                    }
                    "dev.ttt.remote.CLAUDE_URL" -> {
                        // The relay's address, when the Mac's Tailscale address changes (default MaClaudeLink.DEFAULT_URL).
                        val u = i.getStringExtra("url").orEmpty()
                        if (u.startsWith("http")) dev.patrickgold.florisboard.dictate.MaClaudeLink.setUrl(app, u)
                        dev.patrickgold.florisboard.dictate.MaClaudeLink.flush(app)
                        remoteOut(app, i, JSONObject().put("ok", true).put("url", dev.patrickgold.florisboard.dictate.MaClaudeLink.url(app))
                            .put("token", dev.patrickgold.florisboard.dictate.MaClaudeLink.hasToken(app)))
                    }
                    "dev.ttt.remote.RE_ENGINE" -> {
                        val e = i.getStringExtra("e").orEmpty()
                        val ok = e in listOf("anthropic_auto", "opus", "sonnet", "qwen")
                        if (ok) { val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore; prefs.dictate.maReEngine.set(e) }
                        remoteOut(app, i, JSONObject().put("ok", ok).put("reEngine", dev.patrickgold.florisboard.dictate.MaReEngine.engine()))
                    }
                    "dev.ttt.remote.RE_ASK" -> {
                        // One RE answer with the live context (the app and screen RE last saw), engine = his choice or e=...
                        val t = i.getStringExtra("t").orEmpty()
                        val e = i.getStringExtra("e")
                        dev.patrickgold.florisboard.dictate.MaReChat.scope.launch {
                            if (e != null) dev.patrickgold.florisboard.dictate.MaReEngine.override = e
                            val t0 = System.currentTimeMillis()
                            val r = runCatching {
                                dev.patrickgold.florisboard.dictate.MaReChat.ask(app, listOf(dev.patrickgold.florisboard.dictate.MaReChat.Msg(true, t))) { out("re status: " + it) }
                            }
                            dev.patrickgold.florisboard.dictate.MaReEngine.override = null
                            remoteOut(app, i, JSONObject().put("ok", r.isSuccess).put("answer", r.getOrNull().orEmpty())
                                .put("error", r.exceptionOrNull()?.message.orEmpty()).put("ms", System.currentTimeMillis() - t0)
                                .put("model", dev.patrickgold.florisboard.dictate.MaReEngine.lastChoice))
                        }
                    }
                    "dev.ttt.engines.SAY" -> {
                        // Debugging with Marko away from the screen (13.11.2026): the Reader voice (Beatrice, Speechify,
                        // his key in TTT) speaks the update, and Voice Engine shows it as subtitles.
                        val t = i.getStringExtra("t").orEmpty()
                        kotlinx.coroutines.withContext(Dispatchers.Main) { dev.patrickgold.florisboard.dictate.MaVoice.say(app, t, force = true) }
                        out("say " + t.length + " chars")
                    }
                    "dev.ttt.engines.RE_COMPARE" -> {
                        // The engine comparison (13.11.2026): invented chats through Opus, Sonnet, Auto and Qwen; results in re_compare.json.
                        val engines = (i.getStringExtra("e") ?: "opus,sonnet,anthropic_auto,qwen").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        val only = i.getStringExtra("only")?.split(',')?.map { it.trim() }?.toSet()
                        // Its own scope: a run takes minutes and must not hold the broadcast (that was an ANR on 13.11.2026).
                        dev.patrickgold.florisboard.dictate.MaReChat.scope.launch {
                            val f = dev.patrickgold.florisboard.dictate.MaReCompare.run(app, engines, only) { out("compare " + it) }
                            out("compare done: " + f.absolutePath)
                        }
                    }
                    "dev.ttt.engines.RE" -> {
                        // Debug: one RE rewrite with the chosen RE engine, the result's first words logged (a test sentence, no key).
                        val t = i.getStringExtra("t") ?: "Make this shorter: the weather today is very very nice and sunny."
                        val t0 = System.currentTimeMillis()
                        val r = runCatching {
                            if (dev.patrickgold.florisboard.dictate.MaReEngine.isQwen())
                                dev.patrickgold.florisboard.dictate.MaReEngine.groq(app, t) { out("status: " + it) }
                            else dev.patrickgold.florisboard.dictate.DictateController.askModel(t, "debug RE") ?: "(no answer)"
                        }
                        out("re " + dev.patrickgold.florisboard.dictate.MaReEngine.label() + " " + (System.currentTimeMillis() - t0) + " ms: " +
                            (r.getOrNull()?.take(100) ?: ("failed " + r.exceptionOrNull()?.message)))
                    }
                    "dev.ttt.engines.PING" -> {
                        val id = i.getStringExtra("e") ?: "groq_turbo"
                        val clip = ClipBuffer.latest(app, "en", 1).firstOrNull() ?: ClipBuffer.latest(app, "hr", 1).firstOrNull()
                        out("ping $id: " + if (clip == null) "no clip" else EngineAdapters.ping(app, id, clip.wav))
                    }
                    "dev.ttt.engines.BENCH" -> {
                        // Long: release the broadcast at once (no ANR); the run needs the app in the foreground,
                        // because Android freezes a cached process. The Settings button is the normal way.
                        pending.finish()
                        val r = EngineBenchmark.run(app) { }
                        out("bench saved: " + r.saved)
                        r.json.lines().forEach { out("json " + it) }
                        out("HR\n" + EngineBenchmark.table(r.rows, "hr")); out("EN\n" + EngineBenchmark.table(r.rows, "en"))
                        r.skipped.forEach { out("note: $it") }
                    }
                    "dev.ttt.engines.REPORT" -> {
                        for (s in EngineCatalog.ALL) {
                            val left = EngineChain.quotaLeft(app, s)
                            out("${s.id}: key=${EngineKeys.has(app, s.id)} available=${EngineChain.available(app, s.id)} enabled=${EngineStore.enabled(app, s.id)} quotaLeft=${left ?: "n/a"}")
                        }
                        out("clips hr=${ClipBuffer.count(app, "hr")} en=${ClipBuffer.count(app, "en")}")
                        for (l in listOf("hr", "en")) ClipBuffer.list(app, l).take(8).forEach { out("clip $l ${it.id} engine=${it.engine} sec=${"%.1f".format(it.seconds)} refwords=${it.reference.trim().split(Regex("\\s+")).size}") }
                    }
                }
            } catch (t: Throwable) { out("error: " + t.javaClass.simpleName) } finally { runCatching { pending.finish() } }
        }
    }
}
