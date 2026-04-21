package com.example.wledble.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import com.example.wledble.ble.BleManager
import com.example.wledble.model.WledUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin layer between BleManager and Composables.
 * AndroidViewModel gives us Application context without leaking Activity.
 */
class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)

    val uiState: StateFlow<WledUiState> = bleManager.state

    fun startScan()               = bleManager.startScan()
    fun connect(device: BluetoothDevice) = bleManager.connect(device)
    fun disconnect()              = bleManager.disconnect()
    fun togglePower()             = bleManager.writePower(!uiState.value.isPowered)
    fun activatePreset(id: Int)   = bleManager.writeActivePreset(id)

    override fun onCleared() {
        super.onCleared()
        bleManager.cleanup()
    }
}