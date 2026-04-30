package com.jpchurchouse.wledblewear.ble

object BleConstants {
    const val SERVICE_UUID            = "4fafc201-1fb5-459e-8fcc-c5c9c3319100"
    const val CHAR_POWER_UUID         = "4fafc202-1fb5-459e-8fcc-c5c9c3319100"
    const val CHAR_PRESETS_UUID       = "4fafc203-1fb5-459e-8fcc-c5c9c3319100"
    const val CHAR_ACTIVE_PRESET_UUID = "4fafc204-1fb5-459e-8fcc-c5c9c3319100"
    const val CCCD_UUID               = "00002902-0000-1000-8000-00805f9b34fb"
    const val MTU                     = 517

    // Exponential-backoff reconnect schedule: 2 → 4 → 8 → 16 → 30s (cap)
    const val RECONNECT_INITIAL_DELAY_MS = 2_000L
    const val RECONNECT_MAX_DELAY_MS     = 30_000L
    const val GATT_OP_TIMEOUT_MS         = 5_000L
}
