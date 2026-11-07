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

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateOf
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.provider.MaClaudeModels
import dev.patrickgold.florisboard.dictate.ui.MaMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * WA: WHATSAPP, CONTROLLED BY CLAUDE SONNET, NEVER SENDING.
 *
 * Marko, 7.11.2026: *"a WhatsApp control ... commands are going to Sonnet ... I can ask Sonnet, for
 * example, find me this and that person and write this and that message, or take screenshot of past
 * conversation and continue conversation ... a special button that WA is on the button and looks similar
 * as RE button, and it puts similar mechanism, just different engine to control WhatsApp when it's opened.
 * Like I'm controlling it, searching for contact, and then write the message and never send the message
 * automatically."*
 *
 * ### The mechanism
 *
 * The WA key works like RE: a press records the instruction (in English), a second press sends it. The
 * transcript comes here instead of to the field. Sonnet then works WhatsApp step by step: it is shown the
 * screen as TTT's accessibility service reads it (the chat list, names, the messages of an open chat, the
 * buttons and fields, each with a number) and answers with one action at a time: tap, type, scroll, back.
 * After each action it sees the screen again. It stops with a short report, shown in the message line.
 *
 * ### Why the accessibility service and not wireless debugging
 *
 * He said "through wireless debugging". The phone can drive itself that way only after pairing with its
 * own adb, which Android drops on every Wi-Fi change. TTT's accessibility service (the one the magic
 * finger and the floating button already use) reads and operates the app in front with no pairing, so
 * the WA key works the moment it is pressed. The past conversation is read as text from the screen,
 * which is what a screenshot of it would have been used for.
 *
 * ### Never sends
 *
 * There is no send action at all. A tap on anything that sends or calls (Send, the voice message
 * button, voice or video call, "Pošalji") is refused here in code, whatever Sonnet asks, and typing is
 * only into a text field. The message is left in WhatsApp's box for him.
 */
object MaWhatsApp {

    const val NAME = "WhatsApp control"

    /** WhatsApp and WhatsApp Business. */
    val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Sonnet, the newest one (resolved against Anthropic's model list). */
    const val MODEL = "claude-sonnet-latest"

    private const val MAX_STEPS = 15
    private const val MAX_NODES = 220
    private const val SETTLE_MS = 900L

    /** Anything that sends or calls: never tapped, whatever is asked. */
    private val FORBIDDEN = Regex(
        """(?i)(^|\b|_)(send|pošalji|posalji|voice[ _]?(note|message|call)|glasovn\w*|video[ _]?call|videopoziv|call|poziv|nazovi|mic|record)(\b|_|$)""",
    )

    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    /** True while Sonnet is working WhatsApp, for the key's ring. */
    val busy = mutableStateOf(false)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /** Stops a running task (a long press on the key). */
    fun stop() {
        job?.cancel()
        job = null
        busy.value = false
        MaMessage.show("WhatsApp: stopped")
    }

    /** Runs [instruction] on WhatsApp. One task at a time; a new one replaces a running one. */
    fun run(context: Context, instruction: String) {
        val words = instruction.trim()
        if (words.isEmpty()) return
        val service = DictateAccessibilityService.gestureService()
        if (service == null) {
            MaMessage.show("WhatsApp: switch on TTT in Android settings → Accessibility first")
            return
        }
        val key = apiKey()
        if (key.isBlank()) {
            MaMessage.show("WhatsApp: no Anthropic key loaded in TTT")
            return
        }
        job?.cancel()
        busy.value = true
        MaMessage.show("WhatsApp: working on it")
        job = scope.launch {
            val report = runCatching { agent(context.applicationContext, service, key, words) }
                .getOrElse { e -> "stopped (${e.javaClass.simpleName})" }
            busy.value = false
            MaMessage.show("WhatsApp: $report")
        }
    }

    private fun apiKey(): String {
        val account = prefs.dictate.providerAccounts.get().getOrEmpty("anthropic")
        return MaKeyRingStore.currentKey("anthropic", account.apiKey)
    }

    // --- The screen as Sonnet sees it -----------------------------------------------------------------

    private class Screen(val packageName: String?, val text: String, val nodes: List<AccessibilityNodeInfo>)

    private fun readScreen(service: AccessibilityService): Screen {
        val root = service.rootInActiveWindow ?: return Screen(null, "(nothing readable on screen)", emptyList())
        val pkg = root.packageName?.toString()
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder()
        sb.append("App in front: ").append(pkg ?: "?").append('\n')
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= MAX_NODES || depth > 40) return
            val text = n.text?.toString()?.trim().orEmpty()
            val desc = n.contentDescription?.toString()?.trim().orEmpty()
            val id = n.viewIdResourceName?.substringAfter(":id/").orEmpty()
            val actionable = n.isClickable || n.isEditable || n.isScrollable || n.isLongClickable
            if (n.isVisibleToUser && (text.isNotEmpty() || desc.isNotEmpty() || (actionable && id.isNotEmpty()))) {
                val i = nodes.size
                nodes.add(n)
                val r = Rect().also { n.getBoundsInScreen(it) }
                sb.append('[').append(i).append("] ")
                sb.append(n.className?.toString()?.substringAfterLast('.') ?: "View")
                if (id.isNotEmpty()) sb.append(" id=").append(id)
                if (text.isNotEmpty()) sb.append(" \"").append(text.take(160).replace('\n', ' ')).append('"')
                if (desc.isNotEmpty() && desc != text) sb.append(" desc=\"").append(desc.take(100)).append('"')
                val flags = buildString {
                    if (n.isClickable) append(" tap")
                    if (n.isEditable) append(" editable")
                    if (n.isScrollable) append(" scroll")
                }
                sb.append(flags).append(" y=").append(r.top).append('\n')
            }
            for (c in 0 until n.childCount) n.getChild(c)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return Screen(pkg, sb.toString().take(14_000), nodes)
    }

    private fun isForbidden(n: AccessibilityNodeInfo): Boolean {
        val probe = listOfNotNull(
            n.viewIdResourceName?.substringAfter(":id/"),
            n.contentDescription?.toString(),
            n.text?.toString()?.takeIf { it.length <= 20 },
        )
        return probe.any { FORBIDDEN.containsMatchIn(it) }
    }

    // --- Actions -------------------------------------------------------------------------------------

    private suspend fun act(context: Context, service: AccessibilityService, screen: Screen, name: String, input: JsonObject): String {
        fun node(): AccessibilityNodeInfo? = input["id"]?.jsonPrimitive?.intOrNull?.let { screen.nodes.getOrNull(it) }
        return withContext(Dispatchers.Main) {
            when (name) {
                "open_whatsapp" -> {
                    val intent = PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
                        ?: return@withContext "WhatsApp is not installed"
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "opened WhatsApp"
                }
                "tap" -> {
                    if (screen.packageName !in PACKAGES) return@withContext "refused: WhatsApp is not in front"
                    var n = node() ?: return@withContext "no element with that number"
                    if (isForbidden(n)) return@withContext "refused: TTT never sends or calls; leave the message for Marko"
                    var hops = 0
                    while (!n.isClickable && hops < 6) {
                        n = n.parent ?: break
                        if (isForbidden(n)) return@withContext "refused: TTT never sends or calls"
                        hops++
                    }
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "tapped" else "tap did nothing"
                }
                "type" -> {
                    if (screen.packageName !in PACKAGES) return@withContext "refused: WhatsApp is not in front"
                    val n = node() ?: return@withContext "no element with that number"
                    if (!n.isEditable) return@withContext "refused: that element is not a text field"
                    val text = input["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "typed (not sent)" else "typing failed"
                }
                "scroll" -> {
                    val n = node() ?: return@withContext "no element with that number"
                    val up = input["direction"]?.jsonPrimitive?.contentOrNull == "up"
                    val action = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    if (n.performAction(action)) "scrolled" else "could not scroll that"
                }
                "back" -> if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) "went back" else "back failed"
                else -> "unknown action"
            }
        }
    }

    // --- Sonnet --------------------------------------------------------------------------------------

    private const val SYSTEM = """You operate WhatsApp on Marko's Android phone for him, one action at a time.
You see the screen as a numbered list of elements read by the phone's accessibility service ([n] class id "text" desc flags y=top).
Use the tools to act: tap, type, scroll, back, open_whatsapp. After every action you see the screen again.
To find a person: use the search (magnifier) in the chats list, type the name, tap the matching chat.
To read the past conversation: open the chat; the messages are the text elements; scroll up for older ones.
When asked to write or continue a conversation, write the message in Marko's own style and language (Croatian or English, as the chat is), and put it in the message field with type.
NEVER send anything and never call: there is no send action, and taps on send, voice and call buttons are refused. The message is left in the box for Marko to send himself.
When done, or if you cannot do it, call finish with one short sentence for Marko (in English)."""

    private fun tools(): JsonArray = buildJsonArray {
        fun tool(name: String, description: String, props: JsonObject, required: List<String>) = add(
            buildJsonObject {
                put("name", name)
                put("description", description)
                putJsonObject("input_schema") {
                    put("type", "object")
                    put("properties", props)
                    putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                }
            },
        )
        val id = buildJsonObject { putJsonObject("id") { put("type", "integer"); put("description", "the element's number") } }
        tool("tap", "Tap an element by its number.", id, listOf("id"))
        tool(
            "type",
            "Put text into a text field (replaces what is in it). Never sends.",
            buildJsonObject {
                putJsonObject("id") { put("type", "integer") }
                putJsonObject("text") { put("type", "string") }
            },
            listOf("id", "text"),
        )
        tool(
            "scroll",
            "Scroll a scrollable element up (older) or down (newer).",
            buildJsonObject {
                putJsonObject("id") { put("type", "integer") }
                putJsonObject("direction") { put("type", "string"); putJsonArray("enum") { add(kotlinx.serialization.json.JsonPrimitive("up")); add(kotlinx.serialization.json.JsonPrimitive("down")) } }
            },
            listOf("id", "direction"),
        )
        tool("back", "Press the system back button.", buildJsonObject { }, emptyList())
        tool("open_whatsapp", "Open WhatsApp if it is not in front.", buildJsonObject { }, emptyList())
        tool(
            "finish",
            "Stop, with one short sentence for Marko.",
            buildJsonObject { putJsonObject("summary") { put("type", "string") } },
            listOf("summary"),
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun post(key: String, body: JsonObject): JsonObject {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private fun resolveModel(key: String): String = runCatching {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models?limit=100")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .build()
        http.newCall(request).execute().use { r ->
            val ids = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["data"]?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.orEmpty()
            MaClaudeModels.resolve(MODEL, ids)
        }
    }.getOrElse { MaClaudeModels.resolve(MODEL, emptyList()) }

    /**
     * The loop: screen → Sonnet → one action → screen … Older screens are dropped from what is sent
     * (only the newest is shown in full), so a long task does not resend fifteen screens every step.
     */
    private suspend fun agent(context: Context, service: AccessibilityService, key: String, instruction: String): String {
        val model = resolveModel(key)
        var screen = readScreen(service)
        if (screen.packageName !in PACKAGES) {
            act(context, service, screen, "open_whatsapp", JsonObject(emptyMap()))
            delay(1_500)
            screen = readScreen(service)
        }
        val turns = ArrayList<JsonObject>()
        turns.add(
            buildJsonObject {
                put("role", "user")
                put("content", "Marko's instruction: $instruction\n\nThe screen now:\n${screen.text}")
            },
        )
        for (step in 1..MAX_STEPS) {
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", 1024)
                put("system", SYSTEM)
                put("tools", tools())
                put("messages", JsonArray(withOnlyNewestScreen(turns)))
            }
            val reply = post(key, body)
            val content = reply["content"]?.jsonArray ?: return "no answer"
            turns.add(buildJsonObject { put("role", "assistant"); put("content", content) })
            val uses = content.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            if (uses.isEmpty()) {
                return content.firstNotNullOfOrNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.take(160) ?: "done"
            }
            val results = buildJsonArray {
                for (use in uses) {
                    val o = use.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val input = o["input"]?.jsonObject ?: JsonObject(emptyMap())
                    if (name == "finish") {
                        return input["summary"]?.jsonPrimitive?.contentOrNull?.take(160) ?: "done"
                    }
                    MaMessage.show("WhatsApp: $name (step $step)")
                    val outcome = act(context, service, screen, name, input)
                    add(
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", o["id"]?.jsonPrimitive?.contentOrNull.orEmpty())
                            put("content", outcome)
                        },
                    )
                }
            }
            delay(SETTLE_MS)
            screen = readScreen(service)
            turns.add(
                buildJsonObject {
                    put("role", "user")
                    put(
                        "content",
                        buildJsonArray {
                            results.forEach { add(it) }
                            add(buildJsonObject { put("type", "text"); put("text", "SCREEN:\n${screen.text}") })
                        },
                    )
                },
            )
        }
        return "stopped after $MAX_STEPS steps; the message, if written, is in the box"
    }

    /** The turns with every screen but the newest replaced by a short placeholder. */
    private fun withOnlyNewestScreen(turns: List<JsonObject>): List<JsonElement> {
        val lastUser = turns.indexOfLast { it["role"]?.jsonPrimitive?.contentOrNull == "user" }
        return turns.mapIndexed { i, t ->
            if (i == lastUser || i == 0 || t["role"]?.jsonPrimitive?.contentOrNull != "user") return@mapIndexed t
            val content = t["content"] as? JsonArray ?: return@mapIndexed t
            buildJsonObject {
                put("role", "user")
                put(
                    "content",
                    buildJsonArray {
                        content.forEach { c ->
                            val o = c.jsonObject
                            if (o["type"]?.jsonPrimitive?.contentOrNull == "text") {
                                add(buildJsonObject { put("type", "text"); put("text", "(older screen omitted)") })
                            } else add(c)
                        }
                    },
                )
            }
        }
    }
}
