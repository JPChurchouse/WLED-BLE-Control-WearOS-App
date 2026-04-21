package com.jpchurchouse.wledble.ble

import java.util.UUID

object BleConstants {
    /** Custom WLED NimBLE service */
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c3319100")

    /** Power – Read/Write/Notify. 0x00 = off, 0x01 = on. */
    val POWER_CHAR_UUID: UUID = UUID.fromString("4fafc202-1fb5-459e-8fcc-c5c9c3319100")

    /** Available Presets – Read. UTF-8 JSON: [{"id":1,"n":"Name"},…] */
    val PRESETS_CHAR_UUID: UUID = UUID.fromString("4fafc203-1fb5-459e-8fcc-c5c9c3319100")

    /**
     * Active Preset – Read/Write/Notify.
     * Write: 1–250 = preset id byte.
     * Notify: 1–250 = active id, 0xFF = none active.
     */
    val ACTIVE_PRESET_CHAR_UUID: UUID = UUID.fromString("4fafc204-1fb5-459e-8fcc-c5c9c3319100")

    /** GATT Client Characteristic Configuration Descriptor – standard UUID */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val REQUESTED_MTU = 517           // NimBLE server supports up to 517
    const val NO_ACTIVE_PRESET = 0xFF       // Sentinel from firmware

    // Exponential backoff: 2 s → 4 s → 8 s → 16 s → 30 s (capped)
    const val RECONNECT_BASE_MS = 2_000L
    const val RECONNECT_MAX_MS  = 30_000L
}