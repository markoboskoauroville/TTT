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
import kotlin.test.assertTrue

class KeyScanTest {
    private val groq1 = "gsk_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4"
    private val groq2 = "gsk_" + "Z9y8X7w6V5u4T3s2R1q0P9o8N7m6"
    private val google = "AIza" + "SyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q"
    private val hex32 = "0123456789abcdef0123456789abcdef"
    private val hex40 = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `several groq keys in one file are all found and deduplicated`() {
        val c = KeyScan.scan("groq-api.txt", "GROQ_API_KEY=$groq1\n$groq2\n$groq1\n")
        assertEquals(2, c.size)
        assertTrue(c.all { it.kinds == listOf("groq") })
    }

    @Test
    fun `google key and prefixes`() {
        val c = KeyScan.scan("x.txt", "$google\nsk-ant-api03-ABCDEFGHIJKLMNOPQRSTUVWX\n")
        assertEquals(listOf("google"), c[0].kinds)
        assertEquals(listOf("anthropic"), c[1].kinds)
    }

    @Test
    fun `hex shapes and labels`() {
        val c = KeyScan.scan("keys.txt", "Deepgram\n$hex40\nassemblyai: $hex32\n")
        assertEquals(listOf("deepgram"), c.first { it.value == hex40 }.kinds)
        assertEquals("assemblyai", c.first { it.value == hex32 }.kinds.first())
    }

    @Test
    fun `sk_ is ambiguous between speechify and elevenlabs and the label decides`() {
        val k = "sk_" + "a1b2c3d4e5f6a7b8c9d0e1f2"
        assertEquals(listOf("elevenlabs", "speechify"), KeyScan.scan("x.txt", k)[0].kinds)
        assertEquals("speechify", KeyScan.scan("speechify_api.txt", k)[0].kinds.first())
    }

    @Test
    fun `cloudflare account id and token are paired, the id is not an assemblyai key`() {
        val token = "cfat_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4"
        val c = KeyScan.scan("Cloudflare.api.txt", "Account ID $hex32\nToken $token\n")
        assertEquals(1, c.size)
        assertEquals(listOf("cloudflare"), c[0].kinds)
        assertEquals(hex32 + "\n" + token, c[0].value)
    }

    @Test
    fun `words and urls are not keys`() {
        assertTrue(KeyScan.scan("notes.txt", "hello world https://example.com/path\nghp_" + "a".repeat(30)).isEmpty())
    }
}
