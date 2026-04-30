package com.jpchurchouse.wledblewear.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.model.WledUiState
import com.jpchurchouse.wledblewear.tile.WledTileService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = (application as WledApplication).bleManager
    private val dataStore  = application.wledDataStore

    val uiState: StateFlow<WledUiState> = bleManager.uiState

    init {
        // Mirror connection events to DataStore so the tile always has fresh info,
        // and request a tile refresh on every BLE state change.
        viewModelScope.launch {
            bleManager.uiState.collect { state ->
                // Refresh tile on any state change
                TileService.getUpdater(application)
                    .requestUpdate(WledTileService::class.java)
            }
        }
    }

    // ── Scan ──────────────────────────────────────────────────────────────────

    fun startScan() = bleManager.startScan()
    fun stopScan()  = bleManager.stopScan()

    // ── Connect ───────────────────────────────────────────────────────────────

    fun connect(device: ScannedDevice) {
        // Persist device info before connecting — tile reads this from DataStore
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[WledPreferences.DEVICE_ADDRESS] = device.address
                prefs[WledPreferences.DEVICE_NAME]    = device.name
            }
        }
        bleManager.connect(device)
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    fun togglePower() = bleManager.togglePower()

    fun activatePreset(presetId: Int) {
        bleManager.activatePreset(presetId)
        // Persist recent preset order for tile quick-access buttons
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // Intentionally empty — BleManager lifecycle is owned by WledApplication,
        // not by this ViewModel.  The GATT connection must survive ViewModel teardown
        // so the tile can operate without re-connecting.
    }
}
