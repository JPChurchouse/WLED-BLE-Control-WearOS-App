package com.jpchurchouse.wledblewear.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.data.PreferencesKeys
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.Preset
import com.jpchurchouse.wledblewear.model.WledUiState
import com.jpchurchouse.wledblewear.tile.WledTileService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val app        = application as WledApplication
    private val bleManager = app.bleManager
    private val dataStore  = application.wledDataStore
    private val json       = Json { ignoreUnknownKeys = true }

    val uiState: StateFlow<WledUiState> = bleManager.state

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.IS_POWERED] = state.isPowered
                    state.activePresetId?.let { prefs[PreferencesKeys.ACTIVE_PRESET] = it }
                    if (state.presets.isNotEmpty()) {
                        prefs[PreferencesKeys.PRESETS_JSON] =
                            json.encodeToString<List<Preset>>(state.presets)
                    }
                }
                TileService.getUpdater(app).requestUpdate(WledTileService::class.java)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.DEVICE_MAC]  = device.address
                prefs[PreferencesKeys.DEVICE_NAME] = device.name ?: "WLED Device"
            }
        }
        bleManager.connect(device)
    }

    fun startScan()             = bleManager.startScan()
    fun disconnect()            = bleManager.disconnect()
    fun togglePower()           = bleManager.writePower(!uiState.value.isPowered)
    fun activatePreset(id: Int) = bleManager.writeActivePreset(id)

    override fun onCleared() {
        super.onCleared()
        // Do NOT call bleManager.cleanup() — tile needs the connection to outlive the ViewModel
    }
}