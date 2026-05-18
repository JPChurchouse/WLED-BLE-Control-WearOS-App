package com.jpchurchouse.wledblewear.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.Preset
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.model.WledUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.min

/**
 * Single source of truth for all BLE operations.
 *
 * Lifecycle: created once in WledApplication and held for the lifetime of the process.
 * Do NOT call cleanup() from ViewModel.onCleared() — the tile interacts with BLE state
 * independently of any ViewModel lifecycle.
 *
 * Threading: all GATT operations are dispatched on Dispatchers.Main (required by the
 * Android BLE stack).  Callbacks arrive on the main thread; coroutines suspend without
 * blocking it.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val tag = "BleManager"

    // ── Public state ──────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(WledUiState())
    val uiState: StateFlow<WledUiState> = _uiState.asStateFlow()

    // ── Internals ─────────────────────────────────────────────────────────────

    /** All coroutines dispatched on Main so connectGatt / scan calls are safe. */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private val leScanner       get() = bluetoothAdapter.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null

    /** Device we last asked to connect to; drives automatic reconnection. */
    private var savedDevice: ScannedDevice? = null

    /** True while the reconnect loop is running; prevents re-entrant scheduling. */
    @Volatile private var isReconnecting = false
    private var reconnectJob: Job? = null

    // ── GATT operation serialisation ──────────────────────────────────────────

    /**
     * Mutex ensures only one GATT operation is in-flight at a time.
     * Within each withLock block we set [pendingDeferred] before triggering
     * the BLE call so the callback always sees a non-null target.
     */
    private val gattMutex = Mutex()

    /**
     * Completed by the appropriate [BluetoothGattCallback] override.
     * Null result means the operation failed or timed out.
     */
    @Volatile private var pendingDeferred: CompletableDeferred<ByteArray?>? = null

    // ── Scan ─────────────────────────────────────────────────────────────────

    fun startScan() {
        _uiState.update { it.copy(connectionState = ConnectionState.Scanning, scannedDevices = emptyList()) }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(BleConstants.SERVICE_UUID)))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName
                    ?: result.device.name
                    ?: "Unknown (${result.device.address.takeLast(5)})"
                val found = ScannedDevice(address = result.device.address, name = name)
                _uiState.update { state ->
                    if (state.scannedDevices.any { it.address == found.address }) state
                    else state.copy(scannedDevices = state.scannedDevices + found)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "Scan failed: errorCode=$errorCode")
                _uiState.update { it.copy(connectionState = ConnectionState.Idle) }
            }
        }
        leScanner.startScan(listOf(filter), settings, scanCallback!!)
        Log.d(tag, "Scan started")
    }

    fun stopScan() {
        scanCallback?.let { leScanner.stopScan(it) }
        scanCallback = null
        Log.d(tag, "Scan stopped")
    }

    // ── Connect / disconnect ──────────────────────────────────────────────────

    fun connect(device: ScannedDevice) {
        stopScan()
        savedDevice = device
        reconnectJob?.cancel()
        isReconnecting = false
        _uiState.update { it.copy(connectedDeviceName = device.name) }
        doConnect(device.address)
    }

    /** Close current GATT and stop any pending reconnect. */
    fun disconnect() {
        reconnectJob?.cancel()
        savedDevice = null
        isReconnecting = false
        pendingDeferred?.complete(null)
        bluetoothGatt?.close()
        bluetoothGatt = null
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Idle,
                connectedDeviceName = null,
            )
        }
        Log.d(tag, "Disconnected by user request")
    }

    /** Called from WledApplication.onTerminate() only. */
    fun cleanup() {
        disconnect()
        scope.cancel()
    }

    private fun doConnect(address: String) {
        _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
        val device = bluetoothAdapter.getRemoteDevice(address)
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(
            context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
        )
        Log.d(tag, "connectGatt → $address")
    }

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "onConnectionStateChange: newState=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else {
                // Unblock any in-flight GATT op so the coroutine doesn't hang
                pendingDeferred?.complete(null)
                handleDisconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tag, "onServicesDiscovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                scope.launch { runSetupChain(gatt) }
            } else {
                handleDisconnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(tag, "MTU → $mtu (status=$status)")
            pendingDeferred?.complete(byteArrayOf(status.toByte()))
        }

        // ── Read (API < 33) ──────────────────────────────────────────────────
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val value = if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null
                pendingDeferred?.complete(value)
            }
        }

        // ── Read (API 33+) ───────────────────────────────────────────────────
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pendingDeferred?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingDeferred?.complete(byteArrayOf(status.toByte()))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            pendingDeferred?.complete(byteArrayOf(status.toByte()))
        }

        // ── Notify (API < 33) ────────────────────────────────────────────────
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                dispatchNotify(characteristic.uuid, characteristic.value ?: return)
            }
        }

        // ── Notify (API 33+) ─────────────────────────────────────────────────
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                dispatchNotify(characteristic.uuid, value)
            }
        }
    }

    // ── GATT setup chain ──────────────────────────────────────────────────────

    private suspend fun runSetupChain(gatt: BluetoothGatt) {
        val service = gatt.getService(UUID.fromString(BleConstants.SERVICE_UUID))
        if (service == null) {
            Log.e(tag, "Service UUID not found after discovery — disconnecting")
            handleDisconnect()
            return
        }

        val powerChar       = service.getCharacteristic(UUID.fromString(BleConstants.CHAR_POWER_UUID))
        val presetsChar     = service.getCharacteristic(UUID.fromString(BleConstants.CHAR_PRESETS_UUID))
        val activePresetChar = service.getCharacteristic(UUID.fromString(BleConstants.CHAR_ACTIVE_PRESET_UUID))

        // 1. Request MTU — proceed regardless of result; NimBLE negotiates from its end
        gattRequestMtu(gatt, BleConstants.MTU)

        // 2. Read Available Presets (full GATT read — firmware may fragment across ATT packets,
        //    but Android's stack reassembles them before delivering to onCharacteristicRead)
        val presetsBytes = gattRead(gatt, presetsChar)
        val presets: List<Preset> = presetsBytes
            ?.let { bytes ->
                runCatching {
                    Json.decodeFromString<List<Preset>>(bytes.toString(Charsets.UTF_8))
                }.onFailure { Log.w(tag, "Preset JSON parse failed: ${it.message}") }.getOrNull()
            }
            ?: emptyList()
        _uiState.update { it.copy(presets = presets) }

        // 3. Enable notify on Power
        enableNotify(gatt, powerChar)

        // 4. Enable notify on Active Preset
        enableNotify(gatt, activePresetChar)

        // 5. Read initial Power state
        val powerBytes = gattRead(gatt, powerChar)
        val powerOn = powerBytes?.firstOrNull()?.toInt()?.and(0xFF) == 0x01
        _uiState.update { it.copy(isPowerOn = powerOn) }

        // 6. Mark connected — UI unlocks from here
        _uiState.update { it.copy(connectionState = ConnectionState.Connected) }
        Log.d(tag, "Setup chain complete — Connected")
    }

    // ── Primitive GATT operations ─────────────────────────────────────────────

    private suspend fun gattRequestMtu(gatt: BluetoothGatt, mtu: Int) {
        gattMutex.withLock {
            pendingDeferred = CompletableDeferred()
            gatt.requestMtu(mtu)
            withTimeoutOrNull(BleConstants.GATT_OP_TIMEOUT_MS) { pendingDeferred!!.await() }
        }
    }

    private suspend fun gattRead(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic?,
    ): ByteArray? {
        char ?: return null
        return gattMutex.withLock {
            pendingDeferred = CompletableDeferred()
            @Suppress("DEPRECATION")
            gatt.readCharacteristic(char)
            withTimeoutOrNull(BleConstants.GATT_OP_TIMEOUT_MS) { pendingDeferred!!.await() }
        }
    }

    private suspend fun enableNotify(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic?,
    ) {
        char ?: return
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID)) ?: return
        gattMutex.withLock {
            pendingDeferred = CompletableDeferred()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
            withTimeoutOrNull(BleConstants.GATT_OP_TIMEOUT_MS) { pendingDeferred!!.await() }
        }
    }

    private suspend fun gattWrite(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        gattMutex.withLock {
            pendingDeferred = CompletableDeferred()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
            withTimeoutOrNull(BleConstants.GATT_OP_TIMEOUT_MS) { pendingDeferred!!.await() }
        }
    }

    // ── BLE commands (called from ViewModel / tile intent handler) ────────────

    fun togglePower() {
        val gatt    = bluetoothGatt ?: return
        val service = gatt.getService(UUID.fromString(BleConstants.SERVICE_UUID)) ?: return
        val char    = service.getCharacteristic(UUID.fromString(BleConstants.CHAR_POWER_UUID)) ?: return
        val newVal  = if (_uiState.value.isPowerOn) 0x00.toByte() else 0x01.toByte()
        scope.launch { gattWrite(gatt, char, byteArrayOf(newVal)) }
    }

    fun activatePreset(presetId: Int) {
        val gatt    = bluetoothGatt ?: return
        val service = gatt.getService(UUID.fromString(BleConstants.SERVICE_UUID)) ?: return
        val char    = service.getCharacteristic(UUID.fromString(BleConstants.CHAR_ACTIVE_PRESET_UUID)) ?: return
        scope.launch { gattWrite(gatt, char, byteArrayOf(presetId.toByte())) }
    }

    // ── Notify dispatch ───────────────────────────────────────────────────────

    private fun dispatchNotify(uuid: UUID, value: ByteArray) {
        when (uuid.toString().lowercase()) {
            BleConstants.CHAR_POWER_UUID.lowercase() -> {
                val on = value.firstOrNull()?.toInt()?.and(0xFF) == 0x01
                _uiState.update { it.copy(isPowerOn = on) }
            }
            BleConstants.CHAR_ACTIVE_PRESET_UUID.lowercase() -> {
                val raw = value.firstOrNull()?.toInt()?.and(0xFF) ?: 0xFF
                _uiState.update { it.copy(activePresetId = if (raw == 0xFF) null else raw) }
            }
        }
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private fun handleDisconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }

        if (isReconnecting || savedDevice == null) return  // already looping or user disconnected
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val device = savedDevice ?: return
        isReconnecting = true
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var delayMs = BleConstants.RECONNECT_INITIAL_DELAY_MS
            try {
                while (isActive && savedDevice != null) {
                    Log.d(tag, "Reconnect in ${delayMs}ms → ${device.address}")
                    delay(delayMs)

                    doConnect(device.address)

                    // Wait until the connection either succeeds or definitively fails.
                    // doConnect() sets state → Connecting; we wait for the next terminal state.
                    uiState.first {
                        it.connectionState == ConnectionState.Connected ||
                        it.connectionState == ConnectionState.Disconnected
                    }

                    if (uiState.value.connectionState == ConnectionState.Connected) break
                    delayMs = min(delayMs * 2, BleConstants.RECONNECT_MAX_DELAY_MS)
                }
            } finally {
                isReconnecting = false
            }
        }
    }
}
