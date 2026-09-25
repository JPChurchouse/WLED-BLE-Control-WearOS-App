package com.jpchurchouse.wledblewear.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.ble.Protocol
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.forgetDevice
import com.jpchurchouse.wledblewear.data.savedDevices
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.model.WledUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WledApplication
    private val ble = app.bleManager
    private val dataStore = application.wledDataStore

    val uiState: StateFlow<WledUiState> = ble.uiState

    val savedDevices: StateFlow<List<ScannedDevice>> = dataStore.data
        .map { it.savedDevices() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Scan / connect ────────────────────────────────────────────────────────

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()

    /** The device is saved (and becomes the tile's device) once the connection succeeds. */
    fun connect(device: ScannedDevice) = ble.connect(device, keepConnected = true)

    /** Reconnect to the tile's current device, e.g. from the tile's "Open app". */
    fun connectToCurrentDevice(onNone: () -> Unit = {}) {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val address = prefs[WledPreferences.DEVICE_ADDRESS] ?: return@launch onNone()
            connect(ScannedDevice(address, prefs[WledPreferences.DEVICE_NAME] ?: "WLED"))
        }
    }

    fun retry() {
        val s = uiState.value
        val address = s.connectedAddress ?: return
        connect(ScannedDevice(address, s.connectedDeviceName ?: "WLED"))
    }

    /** Drops the watch's stale bond so the device can be paired again, then retries. */
    fun forgetPairingAndRetry() {
        uiState.value.connectedAddress?.let(ble::forgetBond)
        viewModelScope.launch {
            delay(1_000)  // let the stack finish removing the bond
            retry()
        }
    }

    fun disconnect() = ble.disconnect()

    /** Unpairs and removes the device from the saved list. */
    fun forgetDevice(address: String) {
        if (uiState.value.connectedAddress == address) ble.disconnect()
        ble.forgetBond(address)
        viewModelScope.launch {
            app.forgetDevice(address)
            app.refreshSurfaces()
        }
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    fun setPower(on: Boolean) = ble.command(Protocol.Cmd.power(on))
    fun activatePreset(id: Int) = ble.command(Protocol.Cmd.preset(id))
    fun nextPreset() = ble.command(Protocol.Cmd.next())
    fun previousPreset() = ble.command(Protocol.Cmd.prev())
    fun startNightlight(minutes: Int) = ble.command(Protocol.Cmd.nightlight(minutes))
    fun cancelNightlight() = ble.command(Protocol.Cmd.nightlight(0))
    fun offAndDisconnect() = ble.command(Protocol.Cmd.offAndDisconnect())

    private var brightnessJob: Job? = null
    private var pendingBrightness: Int? = null

    /** Sends at most one brightness command per 150 ms, always ending on the latest value. */
    fun setBrightness(value: Int) {
        pendingBrightness = value
        if (brightnessJob?.isActive == true) return
        brightnessJob = viewModelScope.launch {
            while (true) {
                val v = pendingBrightness ?: break
                pendingBrightness = null
                ble.sendCommand(Protocol.Cmd.brightness(v))
                delay(150)
            }
        }
    }
}
