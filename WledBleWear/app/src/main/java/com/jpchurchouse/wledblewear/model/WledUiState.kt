package com.jpchurchouse.wledblewear.model

sealed class ConnectionState {
    object Idle         : ConnectionState()
    object Scanning     : ConnectionState()
    object Connecting   : ConnectionState()
    /** Bonding in progress: the watch is showing the passkey dialog. */
    object Pairing      : ConnectionState()
    object Connected    : ConnectionState()
    object Disconnected : ConnectionState()  // involuntary drop; reconnect backoff running
}

/** Why the last connection attempt stopped short of Connected. */
enum class ConnectProblem {
    /** Not bonded and the device's pairing window is closed. */
    PAIRING_WINDOW_CLOSED,
    /** Pairing was attempted and failed (wrong passkey, cancelled, window closed mid-way). */
    PAIRING_FAILED,
    /** The watch holds a bond the device no longer recognises. */
    BOND_LOST,
    /** The device speaks a newer protocol than this app. */
    UNSUPPORTED_VERSION,
    /** The device doesn't expose the WLED BLE Control service. */
    NOT_WLED,
    /** Couldn't reach the device. */
    UNREACHABLE,
}

data class WledUiState(
    val connectionState:     ConnectionState   = ConnectionState.Idle,
    val connectedAddress:    String?           = null,
    val connectedDeviceName: String?           = null,
    val problem:             ConnectProblem?   = null,
    val scannedDevices:      List<ScannedDevice> = emptyList(),
    val presets:             List<Preset>      = emptyList(),
    val isPowerOn:           Boolean           = false,
    val brightness:          Int               = 128,
    val activePresetId:      Int?              = null,   // null = none
    val activePlaylistId:    Int?              = null,
    val nightlightActive:    Boolean           = false,
    /** Wall-clock time the nightlight ends, or 0. */
    val nightlightEndMs:     Long              = 0L,
    val wledVersion:         String?           = null,
    /** Transient message from the last command (e.g. "Not available"); null when fine. */
    val lastError:           String?           = null,
) {
    val activePreset: Preset? get() = presets.find { it.id == activePresetId }
}
