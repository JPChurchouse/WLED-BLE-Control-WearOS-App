package com.jpchurchouse.wledblewear.model

sealed class ConnectionState {
    object Idle         : ConnectionState()
    object Scanning     : ConnectionState()
    object Connecting   : ConnectionState()
    object Connected    : ConnectionState()
    object Disconnected : ConnectionState()  // transient; triggers reconnect backoff
}

data class WledUiState(
    val connectionState:    ConnectionState = ConnectionState.Idle,
    val connectedDeviceName: String?        = null,
    val scannedDevices:     List<ScannedDevice> = emptyList(),
    val presets:            List<Preset>    = emptyList(),
    val isPowerOn:          Boolean         = false,
    val activePresetId:     Int?            = null,   // null == 0xFF == none
)
