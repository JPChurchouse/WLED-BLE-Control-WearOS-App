package com.jpchurchouse.wledblewear.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

object WledPreferences {
    // ── Persistent device identity (written on connect) ───────────────────────
    val DEVICE_ADDRESS     = stringPreferencesKey("device_address")
    val DEVICE_NAME        = stringPreferencesKey("device_name")
    /**
     * Comma-separated, newest-first list of up to 3 recently-activated preset IDs.
     * Used by the legacy tile chip path; prefer ACTIVE_PRESET_NAME for display.
     */
    val RECENT_PRESETS     = stringPreferencesKey("recent_presets")

    // ── Live / stale BLE state (written by WledApplication.startStateSync) ───
    /**
     * True while BLE reports Connected.  Set false on Disconnected / Idle.
     * The tile uses this to decide whether stale data is available.
     */
    val IS_CONNECTED       = booleanPreferencesKey("is_connected")
    /**
     * Last-known power state.  Written on Connected, removed on user Disconnect
     * (Idle), preserved on involuntary Disconnected so the tile can show it.
     * Null ↔ key absent ↔ no stale data available.
     */
    val IS_POWER_ON        = booleanPreferencesKey("is_power_on")
    /**
     * Display name of the last-active preset, e.g. "Solid".
     * Written on Connected when activePresetId resolves to a known preset.
     * Removed on user Disconnect; preserved on involuntary Disconnected.
     */
    val ACTIVE_PRESET_NAME = stringPreferencesKey("active_preset_name")
    /**
     * Set true by WledViewModel.connectToLastDevice() when a timed-out
     * connection attempt fails; cleared on the next successful connect or
     * on the next connection attempt.
     */
    val CONNECT_FAILED     = booleanPreferencesKey("connect_failed")
}

val Context.wledDataStore by preferencesDataStore(name = "wled_prefs")
