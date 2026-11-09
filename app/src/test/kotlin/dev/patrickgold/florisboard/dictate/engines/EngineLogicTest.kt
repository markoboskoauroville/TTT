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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class EngineLogicTest {

    private fun ok(id: String, name: String, text: String) = Candidate(id, name, null, false) { text }
    private fun fail(id: String, name: String, k: FailKind, local: Boolean = false) =
        Candidate(id, name, null, local) { throw EngineFailure(k, "x") }

    // ---- WER

    @Test
    fun `identical text has no errors`() {
        val s = Wer.score("Send it to Svemir.", "send it to svemir", emptyList())
        assertEquals(0, s.errors); assertEquals(4, s.words)
    }

    @Test
    fun `one substitution and one deletion`() {
        val s = Wer.score("one two three four", "one too three", emptyList())
        assertEquals(2, s.errors); assertEquals(4, s.words)
    }

    @Test
    fun `croatian letters count as letters`() {
        assertEquals(listOf("čaša", "šećer"), Wer.norm("Čaša, šećer!"))
        assertEquals(1, Wer.score("čaša šećer", "casa šećer", emptyList()).errors)
    }

    @Test
    fun `name errors count only names in the reference`() {
        val names = listOf("Svemir", "Tajana", "Damjan")
        val s = Wer.score("Send it to Svemir and Tajana", "send it to Svemira and Tajana", names)
        assertEquals(2, s.names); assertEquals(1, s.nameErrors)
    }

    // ---- fallback

    @Test
    fun `first engine answers and the status says so`() = runBlocking {
        val st = ArrayList<String>()
        val r = FallbackRunner.run(listOf(ok("a", "Groq", "hello"), ok("b", "Deepgram", "x")), 1000, { st.add(it) })
        assertEquals("a", r.engineId); assertEquals("hello", r.text)
        assertEquals("Sending to Groq…", st.first()); assertTrue(st.last().startsWith("done (Groq,"))
    }

    @Test
    fun `quota answer switches to the next engine and says so`() = runBlocking {
        val st = ArrayList<String>()
        val r = FallbackRunner.run(listOf(fail("a", "Groq", FailKind.QUOTA), ok("b", "Deepgram", "hi")), 1000, { st.add(it) })
        assertEquals("b", r.engineId)
        assertTrue(st.contains("Groq out of quota → switching to Deepgram"))
        assertEquals(listOf(FailKind.QUOTA, null), r.attempts.map { it.kind })
    }

    @Test
    fun `an exhausted quota is skipped without a call`() = runBlocking {
        var called = false
        val spent = Candidate("a", "Groq", 0L, false) { called = true; "no" }
        val r = FallbackRunner.run(listOf(spent, ok("b", "Cloudflare", "ok")), 1000, { })
        assertEquals("b", r.engineId); assertEquals(false, called)
    }

    @Test
    fun `a slow engine is cut off and the next one starts`() = runBlocking {
        val slow = Candidate("a", "Slow", null, false) { delay(5000); "late" }
        val t0 = System.currentTimeMillis()
        val st = ArrayList<String>()
        val r = FallbackRunner.run(listOf(slow, ok("b", "Fast", "now")), 150, { st.add(it) })
        assertEquals("b", r.engineId)
        assertTrue(System.currentTimeMillis() - t0 < 1000)
        assertTrue(st.contains("Slow timed out → switching to Fast"))
    }

    @Test
    fun `offline goes to the on-device engine`() = runBlocking {
        val st = ArrayList<String>()
        val net = Candidate("a", "Groq", null, false) { throw java.net.UnknownHostException("x") }
        FallbackRunner.run(listOf(net, Candidate("p", "Parakeet", null, true) { "text" }), 1000, { st.add(it) })
        assertTrue(st.contains("offline → on-device"))
    }

    @Test
    fun `an unknown error also falls through`() = runBlocking {
        val boom = Candidate("a", "A", null, false) { throw IllegalStateException("boom") }
        assertEquals("b", FallbackRunner.run(listOf(boom, ok("b", "B", "t")), 1000, { }).engineId)
    }

    @Test
    fun `when every engine fails it says so`() {
        assertFailsWith<AllEnginesFailed> {
            runBlocking { FallbackRunner.run(listOf(fail("a", "A", FailKind.QUOTA), fail("b", "B", FailKind.AUTH)), 1000, { }) }
        }
    }

    // ---- quota

    private val groq = EngineCatalog.byId("groq_turbo")!!
    private val day1 = 1_794_182_400_000L // 9.11.2026 00:00 UTC

    @Test
    fun `daily quota resets at midnight UTC`() {
        val u = Quota.add(groq, Quota.Usage(), 1000.0, day1 + 3_600_000)
        assertEquals(27_800L, Quota.remainingSeconds(groq, u, day1 + 7_200_000))
        assertEquals(28_800L, Quota.remainingSeconds(groq, u, day1 + 86_400_000 + 1))
        assertEquals(day1 + 86_400_000, Quota.nextReset(QuotaReset.DAILY, day1 + 5))
    }

    @Test
    fun `benchmark never leaves the free quota and paid needs consent`() {
        val u = Quota.add(groq, Quota.Usage(), 28_795.0, day1 + 10)
        assertEquals(false, Quota.canAfford(groq, u, 10.0, day1 + 20, true))
        assertEquals(true, Quota.canAfford(groq, u, 4.0, day1 + 20, true))
        val soniox = EngineCatalog.byId("soniox")!!
        assertEquals(false, Quota.canAfford(soniox, Quota.Usage(), 5.0, day1, false))
        assertEquals(true, Quota.canAfford(soniox, Quota.Usage(), 5.0, day1, true))
    }

    @Test
    fun `cost counts only what is beyond the free quota`() {
        val u = Quota.add(groq, Quota.Usage(), 28_800.0 + 3600.0, day1)
        assertEquals(0.04, u.costUsd, 1e-9)
    }

    // ---- chains, benchmark, keys

    @Test
    fun `stored order is made whole`() {
        val o = EngineCatalog.complete(listOf("deepgram", "nonsense", "deepgram", "groq_v3"), "hr")
        assertEquals(listOf("deepgram", "groq_v3"), o.take(2))
        assertEquals(EngineCatalog.ALL.map { it.id }.toSet(), o.toSet())
    }

    @Test
    fun `best order is lowest WER first and unscored last`() {
        fun row(id: String, e: Int) = BenchMath.Row(id, "hr", 10, e, 100, 0, 0, 500, 0.0, 0)
        val best = BenchMath.bestOrder(listOf(row("a", 20), row("b", 5), row("c", 12)), listOf("x", "a", "b", "c"))
        assertEquals(listOf("b", "c", "a", "x"), best)
    }

    @Test
    fun `keys are picked out of a file`() {
        assertEquals("gsk_aaaaaaaaaaaaaaaaaaaaaaaa", KeyText.forEngine("groq_v3", "label\ngsk_aaaaaaaaaaaaaaaaaaaaaaaa\n"))
        val acct = "0123456789abcdef0123456789abcdef"
        val cf = KeyText.cloudflare("https://dash.cloudflare.com/$acct/workers/ai\n\ncfut_abcdefghijklmnopqrstuvwxyz0123456789ABCD")
        assertEquals(acct + "\ncfut_abcdefghijklmnopqrstuvwxyz0123456789ABCD", cf)
    }
}
