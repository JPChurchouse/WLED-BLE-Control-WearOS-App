package com.jpchurchouse.wledblewear.ble

import com.jpchurchouse.wledblewear.model.Preset
import java.util.UUID

/**
 * WLED BLE Control protocol v1 — the client side of
 * WLED-BLE-Control-Usermod/docs/PROTOCOL.md. Keep the two in step.
 *
 * All multi-byte integers are little endian; strings are u8-length-prefixed UTF-8.
 */
object Protocol {
    private fun uuid(n: Int) = UUID.fromString("8e59%04x-14dc-405e-9f47-e363750a861a".format(n))

    val SERVICE: UUID     = uuid(1)
    val INFO: UUID        = uuid(2)
    val STATE: UUID       = uuid(3)
    val COMMAND: UUID     = uuid(4)
    val PRESET_LIST: UUID = uuid(5)
    val CCCD: UUID        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val VERSION = 1
    const val MTU = 517

    // Command opcodes
    const val OP_POWER           = 0x01
    const val OP_BRIGHTNESS_SET  = 0x02
    const val OP_BRIGHTNESS_STEP = 0x03
    const val OP_PRESET_APPLY    = 0x04
    const val OP_PRESET_NEXT     = 0x05
    const val OP_PRESET_PREV     = 0x06
    const val OP_NIGHTLIGHT      = 0x07
    const val OP_OFF_DISCONNECT  = 0x08

    // Command result status codes
    const val ST_OK          = 0x00
    const val ST_UNKNOWN_OP  = 0x80
    const val ST_BAD_LENGTH  = 0x81
    const val ST_INVALID     = 0x82
    const val ST_BUSY        = 0x83

    // ── Commands ─────────────────────────────────────────────────────────────

    object Cmd {
        fun power(on: Boolean)        = byteArrayOf(OP_POWER.toByte(), if (on) 1 else 0)
        fun powerToggle()             = byteArrayOf(OP_POWER.toByte(), 2)
        fun brightness(value: Int)    = byteArrayOf(OP_BRIGHTNESS_SET.toByte(), value.coerceIn(1, 255).toByte())
        fun brightnessStep(delta: Int) = byteArrayOf(OP_BRIGHTNESS_STEP.toByte(), delta.coerceIn(-128, 127).toByte())
        fun preset(id: Int)           = byteArrayOf(OP_PRESET_APPLY.toByte(), id.toByte())
        fun next(favourites: Boolean = false) = byteArrayOf(OP_PRESET_NEXT.toByte(), if (favourites) 1 else 0)
        fun prev(favourites: Boolean = false) = byteArrayOf(OP_PRESET_PREV.toByte(), if (favourites) 1 else 0)
        fun nightlight(minutes: Int)  = byteArrayOf(OP_NIGHTLIGHT.toByte(), minutes.coerceIn(0, 255).toByte())
        fun offAndDisconnect()        = byteArrayOf(OP_OFF_DISCONNECT.toByte())
    }

    fun statusText(status: Int) = when (status) {
        ST_OK         -> "OK"
        ST_UNKNOWN_OP -> "Not supported by this device"
        ST_BAD_LENGTH -> "Bad command"
        ST_INVALID    -> "Not available"
        ST_BUSY       -> "Device busy, try again"
        else          -> "Error 0x%02x".format(status)
    }

    // ── Decoding ─────────────────────────────────────────────────────────────

    data class Info(
        val protocolVersion: Int,
        val features: Int,
        val pairingOpen: Boolean,
        val name: String,
        val wledVersion: String,
    )

    data class State(
        val powerOn: Boolean,
        val nightlightActive: Boolean,
        val brightness: Int,
        val presetId: Int,       // 0 = none
        val playlistId: Int,     // 0 = none
        val presetRevision: Int,
        val nightlightRemainingSec: Int,
    )

    data class PresetPage(
        val revision: Int,
        val nextStartId: Int,    // 0 = last page
        val presets: List<Preset>,
    )

    private class Reader(private val b: ByteArray) {
        var pos = 0
        fun u8(): Int = b[pos++].toInt() and 0xFF
        fun u16(): Int = u8() or (u8() shl 8)
        fun str(): String {
            val n = u8()
            val s = String(b, pos, n, Charsets.UTF_8)
            pos += n
            return s
        }
    }

    fun decodeInfo(b: ByteArray): Info? = runCatching {
        val r = Reader(b)
        val version = r.u8()
        val features = r.u16()
        val status = r.u8()
        Info(version, features, status and 0x01 != 0, r.str(), r.str())
    }.getOrNull()

    fun decodeState(b: ByteArray): State? {
        if (b.size < 8) return null
        val r = Reader(b)
        val flags = r.u8()
        return State(
            powerOn = flags and 0x01 != 0,
            nightlightActive = flags and 0x02 != 0,
            brightness = r.u8(),
            presetId = r.u8(),
            playlistId = r.u8(),
            presetRevision = r.u16(),
            nightlightRemainingSec = r.u16(),
        )
    }

    fun decodePresetPage(b: ByteArray): PresetPage? = runCatching {
        val r = Reader(b)
        val rev = r.u16()
        val next = r.u8()
        val count = r.u8()
        val list = List(count) {
            val id = r.u8()
            val flags = r.u8()
            val name = r.str()
            val ql = r.str()
            Preset(id = id, name = name, quickLoad = ql,
                playlist = flags and 0x01 != 0, favourite = flags and 0x02 != 0)
        }
        PresetPage(rev, next, list)
    }.getOrNull()
}
