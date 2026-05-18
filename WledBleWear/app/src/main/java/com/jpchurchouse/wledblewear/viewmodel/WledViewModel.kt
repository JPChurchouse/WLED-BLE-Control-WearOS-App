package com.jpchurchouse.wledblewear.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.model.WledUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = (application as WledApplication).bleManager
    private val dataStore  = application.wledDataStore

    val uiState: StateFlow<WledUiState> = bleManager.uiState

    // NOTE: state sync (DataStore writes, tile + complication refresh) has been
    // moved to WledApplication.startStateSync(), which runs in appScope for the
    // entire process lifetime — not just while MainActivity exists.

    // ── Scan ──────────────────────────────────────────────────────────────────

    fun startScan() = bleManager.startScan()
    fun stopScan()  = bleManager.stopScan()

    // ── Connect ───────────────────────────────────────────────────────────────

    fun connect(device: ScannedDevice) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[WledPreferences.DEVICE_ADDRESS] = device.address
                prefs[WledPreferences.DEVICE_NAME]    = device.name
            }
        }
        bleManager.connect(device)
    }

    /**
     * Read the last-connected device from DataStore and attempt to reconnect.
     * Used by MainActivity when a tile "connect_lcd" intent is received.
     *
     * Note: BleManager.connect() starts the exponential-backoff reconnect loop.
     * MainActivity is responsible for imposing a timeout and calling disconnect()
     * if the connection does not complete within the allowed window.
     */
    fun connectToLastDevice() {
        viewModelScope.launch {
            val prefs   = dataStore.data.first()
            val address = prefs[WledPreferences.DEVICE_ADDRESS] ?: return@launch
            val name    = prefs[WledPreferences.DEVICE_NAME] ?: "WLED"
            bleManager.connect(ScannedDevice(address, name))
        }
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    fun togglePower() = bleManager.togglePower()

    /**
     * Activate a preset and maintain the RECENT_PRESETS ordered list in DataStore
     * (newest first, capped at 3).  ACTIVE_PRESET_NAME is written by
     * WledApplication.startStateSync() once the BLE notification round-trips back.
     */
    fun activatePreset(presetId: Int) {
        bleManager.activatePreset(presetId)
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val existing = prefs[WledPreferences.RECENT_PRESETS]
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?: emptyList()
                val updated = (listOf(presetId) + existing.filter { it != presetId }).take(3)
                prefs[WledPreferences.RECENT_PRESETS] = updated.joinToString(",")
            }
        }
    }

    /**
     * User-initiated disconnect.
     *
     * Requires BleManager.disconnect() to:
     *   1. Cancel any pending reconnect-backoff coroutine.
     *   2. Call gatt?.disconnect() + gatt?.close().
     *   3. Emit ConnectionState.Idle to uiState.
     *
     * The Idle emission triggers WledApplication.startStateSync() to remove all
     * stale DataStore keys (IS_POWER_ON, ACTIVE_PRESET_NAME), so the tile and
     * complications revert to the "no live data" display.
     */
    fun disconnect() = bleManager.disconnect()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // Intentionally empty — BleManager lifecycle is owned by WledApplication.
        // The GATT connection must survive ViewModel teardown.
    }
}
