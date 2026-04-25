package com.jpchurchouse.wledblewear.model

import android.bluetooth.BluetoothDevice

/**
 * Lightweight wrapper around a raw scan result, kept in UI state.
 * We hold the [BluetoothDevice] reference so we can connect to it later,
 * but we extract the display strings immediately to avoid repeated
 * permission-gated calls in Composables.
 */
data class ScannedDevice(
    val name: String,          // "WLED-BLE" or "Unknown (xx:yy)"
    val address: String,       // MAC address string for de-duplication
    val device: BluetoothDevice
)