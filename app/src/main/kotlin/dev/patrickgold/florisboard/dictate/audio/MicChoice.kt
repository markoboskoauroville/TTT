package dev.patrickgold.florisboard.dictate.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.util.Log
import dev.patrickgold.florisboard.dictate.MaLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which microphone TTT records from (Marko, 14.11.2026: "I connected my Sony camera to my phone and it took away our audio
 * device ... it should be persistent on the microphone from the phone. If I connect any sound card or anything it takes
 * audio from the phone microphone. But anything what comes new it is registered when I long press and then I can change").
 *
 * One setting for all languages (H and E alike). Default = the phone's own built-in microphone, pinned on every AudioRecord
 * with [AudioRecord.setPreferredDevice], so a USB camera / sound card / headset plugged in later never takes the recording.
 * A chosen device is remembered by type + product name; while it is away the phone microphone is used, and when it is
 * plugged in again it is used again without asking. The Mac microphone (Screen Copy, [MacMicLink]) is a separate source
 * decided before this and is not touched here.
 */
object MicChoice {
    private const val TAG = "TTT-MIC"
    private const val PREFS = "ttt_mic_choice"
    private const val K_KEY = "key"
    private const val K_LABEL = "label"
    const val BUILTIN = "builtin"

    /** One row in the long-press panel. [type] is the AudioDeviceInfo type, [key] what is stored. */
    data class Option(val key: String, val type: Int, val label: String, val device: AudioDeviceInfo?)

    private val _choice = MutableStateFlow<String?>(null)
    /** The stored key (BUILTIN by default); collected by the panel so a pick lights at once. */
    fun choiceFlow(context: Context): StateFlow<String?> {
        if (_choice.value == null) _choice.value = storedKey(context)
        return _choice
    }

    private val _activeShort = MutableStateFlow<String?>(null)
    /** Short name of the microphone the live take is routed to, null while it is the phone's own (the readout shows nothing then). */
    val activeShort: StateFlow<String?> = _activeShort

    fun storedKey(context: Context): String =
        prefs(context).getString(K_KEY, null) ?: BUILTIN

    fun storedLabel(context: Context): String? = prefs(context).getString(K_LABEL, null)

    fun set(context: Context, option: Option) {
        prefs(context).edit().putString(K_KEY, option.key).putString(K_LABEL, option.label).apply()
        _choice.value = option.key
        MaLog.add("mic", "chosen: ${option.label}")
        Log.i(TAG, "chosen ${option.key} (${option.label})")
    }

    /** Every input a recording can use right now, the phone microphone first. */
    fun options(context: Context): List<Option> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() }.getOrDefault(emptyList())
        val out = ArrayList<Option>()
        out += Option(BUILTIN, AudioDeviceInfo.TYPE_BUILTIN_MIC, "Phone microphone", builtin(devices))
        val seen = HashSet<String>()
        for (d in devices) {
            if (!shown(d.type)) continue
            val key = keyOf(d)
            if (!seen.add(key)) continue
            out += Option(key, d.type, labelOf(d), d)
        }
        return out
    }

    /**
     * The device the next AudioRecord must be pinned to: the stored choice when it is plugged in, otherwise the phone's
     * built-in microphone (the choice stays stored, so it comes back with the device). Null only if the phone reports
     * no built-in microphone at all.
     */
    fun preferredDevice(context: Context): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() }.getOrDefault(emptyList())
        val key = storedKey(context)
        if (key != BUILTIN) {
            devices.firstOrNull { shown(it.type) && keyOf(it) == key }?.let { return it }
            MaLog.add("mic", "${storedLabel(context) ?: key} not connected, phone microphone")
        }
        return builtin(devices)
    }

    /** True when the stored choice is a Bluetooth headset that is connected now (it needs SCO switched on). */
    fun wantsBluetoothSco(context: Context): Boolean =
        preferredDevice(context)?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

    /** Pins [rec] to [device]; logged under the recorder line. */
    fun apply(rec: AudioRecord, device: AudioDeviceInfo?) {
        if (device == null) { MaLog.add("mic", "no built-in microphone reported, system routing"); return }
        val ok = runCatching { rec.setPreferredDevice(device) }.getOrDefault(false)
        Log.i(TAG, "preferred ${describe(device)} -> $ok")
        if (!ok) MaLog.add("mic", "setPreferredDevice refused for ${describe(device)}")
    }

    /** Called once the take is running: logs where Android really routed it and sets the readout. */
    fun noteRouted(wanted: AudioDeviceInfo?, routed: AudioDeviceInfo?) {
        val line = "recording from ${routed?.let { describe(it) } ?: "unknown"}" +
            if (wanted != null && routed != null && routed.id != wanted.id) " (wanted ${describe(wanted)})" else ""
        Log.i(TAG, line)
        MaLog.add("mic", line)
        _activeShort.value = when (routed?.type) {
            null, AudioDeviceInfo.TYPE_BUILTIN_MIC -> null
            else -> shortOf(routed.type)
        }
    }

    fun clearActive() { _activeShort.value = null }

    // ---- naming ----

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun builtin(devices: List<AudioDeviceInfo>): AudioDeviceInfo? {
        val mics = devices.filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return mics.firstOrNull { addr(it).equals("bottom", ignoreCase = true) } ?: mics.firstOrNull()
    }

    private fun shown(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_DOCK,
        AudioDeviceInfo.TYPE_HDMI, TYPE_BLE_HEADSET -> true
        else -> false // built-in (one row of its own), telephony, remote submix, tuners, echo reference
    }

    private fun product(d: AudioDeviceInfo): String {
        val p = d.productName?.toString()?.trim().orEmpty()
        return if (p.isEmpty() || p.equals(Build.MODEL, ignoreCase = true)) "" else p
    }

    private fun keyOf(d: AudioDeviceInfo): String = "${d.type}:${product(d)}"

    private fun labelOf(d: AudioDeviceInfo): String {
        val base = when (d.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB microphone"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET -> "Bluetooth headset"
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line in"
            AudioDeviceInfo.TYPE_DOCK -> "Dock"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            else -> "Microphone"
        }
        val p = product(d)
        return if (p.isEmpty()) base else "$base ($p)"
    }

    private fun shortOf(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB MIC"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET -> "BT MIC"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED MIC"
        else -> "EXT MIC"
    }

    private fun describe(d: AudioDeviceInfo): String =
        "type ${d.type} '${d.productName}' id ${d.id}" + (addr(d).takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")

    private fun addr(d: AudioDeviceInfo): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) d.address.orEmpty() else ""

    /** AudioDeviceInfo.TYPE_BLE_HEADSET (API 31), as a literal so minSdk 26 compiles. */
    private const val TYPE_BLE_HEADSET = 26
}
