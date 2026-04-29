package com.jpchurchouse.wledblewear.model

/** All UI-visible connection states. */
sealed class ConnectionState {
    object Disconnected                        : ConnectionState()
    object Scanning                            : ConnectionState()
    object Connecting                          : ConnectionState()
    data class Reconnecting(val attempt: Int)  : ConnectionState()
    object Connected                           : ConnectionState()
    data class Error(val message: String)      : ConnectionState()
}

/**
 * Single source of truth for the entire app UI.
 * Produced by [BleManager], consumed by ViewModel → Composables.
 */
data class WledUiState(
    val connectionState : ConnectionState      = ConnectionState.Disconnected,
    val isPowered       : Boolean              = false,
    val presets         : List<Preset>         = emptyList(),
    val activePresetId  : Int?                 = null,   // null = none active
    val scanResults     : List<ScannedDevice>  = emptyList(),
    val isLoadingPresets: Boolean              = false
)