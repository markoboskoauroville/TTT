package dev.patrickgold.florisboard.dictate.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The Mac's microphone as a recording source (Marko, 14.11.2026: "I opened my Nothing Phone in Screen Copy Mantra, and I
 * tried to transcribe some of my comments with TTT, and the sound is not heard" -> "build our custom audio link with TTT").
 *
 * Contract with Screen Copy Mantra (fixed): Screen Copy runs `adb reverse tcp:8879 tcp:8879`, so 127.0.0.1:8879 on the
 * phone reaches the Mac. We send `TTT-MIC 1 <rate>\n`, read one line `OK <rate> 1 s16le\n`, then raw mono PCM16 LE
 * until we close the socket. `ERR <reason>\n` (or no answer in 300 ms) means: use the phone microphone.
 */
object MacMicLink {
    private const val TAG = "TTT-MACMIC"
    const val HOST = "127.0.0.1"
    const val PORT = 8879
    const val TIMEOUT_MS = 300

    private val _active = MutableStateFlow(false)
    /** True while a take is being recorded from the Mac; the keyboard shows "MAC MIC". */
    val active: StateFlow<Boolean> = _active
    fun setActive(on: Boolean) { _active.value = on }

    /** Last reason the Mac mic was refused, shown briefly; null when it was simply not there. */
    @Volatile var lastError: String? = null

    class Link(val socket: Socket, val input: InputStream, val rate: Int)

    /** Connects and shakes hands within [TIMEOUT_MS] in total. Null = fall back to the phone mic. */
    fun open(rate: Int): Link? {
        lastError = null
        var result: Link? = null
        val t = Thread {
            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
                s.soTimeout = TIMEOUT_MS
                s.getOutputStream().apply { write("TTT-MIC 1 $rate\n".toByteArray()); flush() }
                val inp = s.getInputStream()
                val sb = StringBuilder()
                while (sb.length < 120) {
                    val b = inp.read()
                    if (b < 0 || b == '\n'.code) break
                    sb.append(b.toChar())
                }
                val line = sb.toString().trim()
                val p = line.split(' ')
                if (p.size >= 4 && p[0] == "OK" && p[2] == "1" && p[3] == "s16le") {
                    val r = p[1].toIntOrNull() ?: rate
                    s.soTimeout = 0
                    result = Link(s, inp, r)
                    Log.i(TAG, "linked: $line")
                    return@Thread
                }
                if (line.startsWith("ERR")) lastError = line.removePrefix("ERR").trim().ifEmpty { "refused" }
                Log.i(TAG, "not used: '$line'")
            } catch (e: Exception) {
                Log.i(TAG, "no Mac link: ${e.javaClass.simpleName}")
            }
            runCatching { s.close() }
        }
        t.start()
        t.join((TIMEOUT_MS + 150).toLong())
        val r = result
        if (r == null && t.isAlive) runCatching { t.interrupt() }
        return r
    }
}
