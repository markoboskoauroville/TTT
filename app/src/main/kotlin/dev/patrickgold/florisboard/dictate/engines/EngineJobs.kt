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
