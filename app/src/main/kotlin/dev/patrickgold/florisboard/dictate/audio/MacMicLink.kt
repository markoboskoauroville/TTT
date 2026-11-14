package dev.patrickgold.florisboard.dictate.audio

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
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
 * until we close the socket. `ERR <reason>\n` (or no answer in [HANDSHAKE_MS]) means: use the phone microphone.
 *
 * 14.11.2026: the answer travels over adb reverse, so on Tailscale through a relay (Nothing on mobile data, DERP
 * Frankfurt, 80-120 ms ping) it took 220-280 ms at rest; the old 300 ms limit for the whole handshake made every H
 * press fall back to the phone mic. Connect stays 300 ms (no reverse = refused at once), the answer may take 1.5 s.
 */
object MacMicLink {
    private const val TAG = "TTT-MACMIC"
    const val HOST = "127.0.0.1"
    const val PORT = 8879
    const val TIMEOUT_MS = 300
    const val HANDSHAKE_MS = 1500

    private val _active = MutableStateFlow(false)
    /** True while a take is being recorded from the Mac; the keyboard shows "MAC MIC". */
    val active: StateFlow<Boolean> = _active
    fun setActive(on: Boolean) { _active.value = on }

    /*
     * Who pressed (Marko, 14.11.2026: "If I'm clicking on my Pixel phone to transcribe a message on my TTT, it should detect
     * that I clicked on the phone, and it should take the microphone from the phone, not from the computer.").
     * A finger on this glass arrives from the real touchscreen: a device id > 0 whose InputDevice is not virtual and is a
     * touchscreen. Screen Copy (scrcpy) and `adb shell input` inject with device id -1, the "Virtual" device (seen 14.11.2026). Every keyboard touch-down is noted here
     * (ImeRootView.dispatchTouchEvent); the Mac link is asked only when the last one, under 3 s ago, was NOT a finger.
     * Anything unknown (no touch seen, a volume key, a broadcast) = phone microphone.
     */
    @Volatile private var lastDownAt = 0L
    @Volatile private var lastDownRemote = false

    fun noteTouch(e: MotionEvent) {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return
        val dev = runCatching { InputDevice.getDevice(e.deviceId) }.getOrNull()
        val finger = e.deviceId > 0 && dev != null && !dev.isVirtual && dev.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)
        lastDownAt = SystemClock.uptimeMillis()
        lastDownRemote = !finger
        Log.i(TAG, "touch down: device ${e.deviceId} source 0x${Integer.toHexString(e.source)} virtual ${dev?.isVirtual} " +
            "name '${dev?.name}' -> ${if (finger) "FINGER (phone mic)" else "REMOTE (may ask the Mac)"}")
    }

    /** True only when the last keyboard touch-down (under 3 s ago) was injected, i.e. came through Screen Copy. */
    fun pressCameFromMac(): Boolean {
        val r = lastDownRemote && SystemClock.uptimeMillis() - lastDownAt < 3000
        Log.i(TAG, "press from the Mac: $r")
        return r
    }

    /** Last reason the Mac mic was refused, shown briefly; null when it was simply not there. */
    @Volatile var lastError: String? = null

    class Link(val socket: Socket, val input: InputStream, val rate: Int)

    /** Connects within [TIMEOUT_MS] and shakes hands within [HANDSHAKE_MS]. Null = fall back to the phone mic. */
    fun open(rate: Int): Link? {
        lastError = null
        var result: Link? = null
        val t = Thread {
            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
                s.soTimeout = HANDSHAKE_MS
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
                // "NO <reason>" (Screen Copy v27): he is not driving this phone from the Mac -> phone mic, no toast
                if (line.startsWith("ERR")) lastError = line.removePrefix("ERR").trim().ifEmpty { "refused" }
                Log.i(TAG, "not used: '$line'")
            } catch (e: Exception) {
                Log.i(TAG, "no Mac link: ${e.javaClass.simpleName}")
            }
            runCatching { s.close() }
        }
        t.start()
        t.join((TIMEOUT_MS + HANDSHAKE_MS + 150).toLong())
        val r = result
        if (r == null && t.isAlive) runCatching { t.interrupt() }
        return r
    }
}
