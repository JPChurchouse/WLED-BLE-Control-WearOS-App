package com.example.wledble.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.wledble.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.util.UUID

private const val TAG = "BleManager"

/**
 * All BLE logic: scan → connect → GATT setup chain → notify handling → reconnect.
 *
 * Caller contract: the Activity/ViewModel must obtain required BLE permissions
 * (BLUETOOTH_SCAN + BLUETOOTH_CONNECT on API 31+, ACCESS_FINE_LOCATION on API <31)
 * before calling [startScan] or [connect].
 *
 * Thread safety: GATT callbacks arrive on a Bluetooth binder thread.
 * [_state].update() is atomic (CAS under the hood); all other mutable state
 * is accessed only from the callback thread in the strictly sequential
 * GATT setup chain, so no additional locking is required there.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    // ── Bluetooth handles ───────────────────────────────────────────────────
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter
        get() = bluetoothManager.adapter

    // ── Coroutine scope for reconnect scheduling ────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── State exposed to ViewModel ──────────────────────────────────────────
    private val _state = MutableStateFlow(WledUiState())
    val state: StateFlow<WledUiState> = _state.asStateFlow()

    // ── GATT handle — written/read only on the BT callback thread ──────────
    @Volatile private var gatt: BluetoothGatt? = null

    // ── Cached characteristic references set during service discovery ───────
    @Volatile private var powerChar       : BluetoothGattCharacteristic? = null
    @Volatile private var presetsChar     : BluetoothGattCharacteristic? = null
    @Volatile private var activePresetChar: BluetoothGattCharacteristic? = null

    // ── Reconnect tracking ──────────────────────────────────────────────────
    /** Non-null only while we still want to (re)connect to this device. */
    @Volatile private var targetDevice: BluetoothDevice? = null
    private var reconnectJob: Job? = null
    @Volatile private var reconnectAttempts = 0

    /**
     * Explicit setup-phase enum prevents executing a step twice if a
     * callback fires unexpectedly (e.g. device reconnects mid-setup).
     */
    private enum class SetupPhase {
        IDLE,
        MTU_REQUESTED,
        READING_PRESETS,
        ENABLING_POWER_NOTIFY,
        ENABLING_PRESET_NOTIFY,
        READING_INITIAL_POWER,
        COMPLETE
    }
    @Volatile private var setupPhase = SetupPhase.IDLE

    // ── lenient JSON parser — tolerates extra fields from future firmware ───
    private val json = Json { ignoreUnknownKeys = true }

    // ────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Start BLE scan filtered by the WLED service UUID.
     * Only devices that include [BleConstants.SERVICE_UUID] in their
     * advertising payload will surface — ensure the NimBLE usermod calls
     * NimBLEAdvertising::addServiceUUID() before advertising.
     */
    fun startScan() {
        stopScan()
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            updateError("BLE not available / Bluetooth off")
            return
        }
        _state.update { it.copy(connectionState = ConnectionState.Scanning, scanResults = emptyList()) }

        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    /**
     * Connect to [device]. Cancels any in-progress reconnect loop and resets
     * the reconnect counter so backoff starts fresh.
     */
    fun connect(device: BluetoothDevice) {
        stopScan()
        reconnectJob?.cancel()
        reconnectJob = null
        targetDevice = device
        reconnectAttempts = 0
        _state.update { it.copy(connectionState = ConnectionState.Connecting) }
        doConnect(device)
    }

    /**
     * Explicit user-initiated disconnect. Clears [targetDevice] to suppress
     * the automatic reconnect that would otherwise trigger from the callback.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        targetDevice = null          // prevents reconnect logic from firing
        gatt?.disconnect()           // → onConnectionStateChange(DISCONNECTED)
    }

    /** Toggle LED power. Fire-and-forget — state update arrives via notify. */
    fun writePower(on: Boolean) {
        val g = gatt ?: return
        val c = powerChar ?: return
        writeCharacteristic(g, c, byteArrayOf(if (on) 0x01 else 0x00))
    }

    /**
     * Activate preset by ID. Fire-and-forget — active preset notify
     * confirms the change. [id] must be 1–250.
     */
    fun writeActivePreset(id: Int) {
        require(id in 1..250) { "Preset id out of range: $id" }
        val g = gatt ?: return
        val c = activePresetChar ?: return
        writeCharacteristic(g, c, byteArrayOf(id.toByte()))
    }

    /** Call from ViewModel.onCleared() to release all resources. */
    fun cleanup() {
        scope.cancel()
        gatt?.close()
        gatt = null
    }

    // ────────────────────────────────────────────────────────────────────────
    // Scan callback
    // ────────────────────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val existing = _state.value.scanResults
            if (existing.any { it.address == address }) return   // de-duplicate

            val displayName = result.device.name
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown (…${address.takeLast(5)})"

            val scanned = ScannedDevice(
                name    = displayName,
                address = address,
                device  = result.device
            )
            _state.update { it.copy(scanResults = existing + scanned) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: errorCode=$errorCode")
            updateError("Scan failed (code $errorCode). Is Bluetooth on?")
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // GATT callback — implements the sequential setup chain
    // ────────────────────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, discovering services…")
                    reconnectAttempts = 0
                    setupPhase = SetupPhase.IDLE
                    _state.update { it.copy(connectionState = ConnectionState.Connecting) }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected (status=$status)")
                    clearCharacteristicCache()
                    _state.update {
                        it.copy(
                            connectionState  = ConnectionState.Disconnected,
                            isPowered        = false,
                            presets          = emptyList(),
                            activePresetId   = null,
                            isLoadingPresets = false
                        )
                    }
                    gatt.close()
                    this@BleManager.gatt = null
                    // Only auto-reconnect if the user hasn't explicitly disconnected
                    targetDevice?.let { scheduleReconnect(it) }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                gatt.disconnect(); return
            }
            val service = gatt.getService(BleConstants.SERVICE_UUID) ?: run {
                Log.e(TAG, "WLED service not found — check UUID and firmware advertising")
                updateError("WLED BLE service not found")
                gatt.disconnect(); return
            }

            fun getChar(uuid: UUID, label: String): BluetoothGattCharacteristic? =
                service.getCharacteristic(uuid).also {
                    if (it == null) Log.e(TAG, "Missing characteristic: $label ($uuid)")
                }

            powerChar        = getChar(BleConstants.POWER_CHAR_UUID,        "Power")
            presetsChar      = getChar(BleConstants.PRESETS_CHAR_UUID,      "Presets")
            activePresetChar = getChar(BleConstants.ACTIVE_PRESET_CHAR_UUID, "ActivePreset")

            if (powerChar == null || presetsChar == null || activePresetChar == null) {
                updateError("Required BLE characteristics missing")
                gatt.disconnect(); return
            }

            // Negotiate MTU to support preset JSON larger than default 23-byte ATT payload.
            // Android's BLE stack handles ATT fragmentation (Long Read / Read Blob) automatically,
            // so onCharacteristicRead always delivers the complete value regardless of size.
            setupPhase = SetupPhase.MTU_REQUESTED
            gatt.requestMtu(BleConstants.REQUESTED_MTU)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU negotiated to $mtu (status=$status)")
            // Proceed regardless of status — fallback MTU is still usable.
            if (setupPhase == SetupPhase.MTU_REQUESTED) {
                setupPhase = SetupPhase.READING_PRESETS
                _state.update { it.copy(isLoadingPresets = true) }
                gatt.readCharacteristic(presetsChar!!)
            }
        }

        // ── Read callbacks — handle both API variants ──────────────────────

        /**
         * Called on API < 33 (Tiramisu). On API 33+ this is NOT called;
         * the four-parameter override below is called instead.
         */
        @Deprecated("Use the 4-param override on API 33+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handleCharacteristicRead(
                gatt, characteristic,
                characteristic.value ?: byteArrayOf(),
                status
            )
        }

        /** Called on API 33+ (Tiramisu) exclusively. */
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(gatt, characteristic, value, status)
        }

        // ── Descriptor write callback (CCCD enables) ──────────────────────

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed: $status on ${descriptor.characteristic.uuid}")
                return
            }
            when (descriptor.characteristic.uuid) {
                BleConstants.POWER_CHAR_UUID -> {
                    if (setupPhase == SetupPhase.ENABLING_POWER_NOTIFY) {
                        Log.d(TAG, "Power notifications enabled, enabling preset notifications…")
                        setupPhase = SetupPhase.ENABLING_PRESET_NOTIFY
                        enableNotifications(gatt, activePresetChar!!)
                    }
                }
                BleConstants.ACTIVE_PRESET_CHAR_UUID -> {
                    if (setupPhase == SetupPhase.ENABLING_PRESET_NOTIFY) {
                        Log.d(TAG, "ActivePreset notifications enabled, reading initial power…")
                        setupPhase = SetupPhase.READING_INITIAL_POWER
                        gatt.readCharacteristic(powerChar!!)
                    }
                }
            }
        }

        // ── Notify callbacks — handle both API variants ───────────────────

        @Deprecated("Use the 3-param override on API 33+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicChanged(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }
    } // end gattCallback

    // ────────────────────────────────────────────────────────────────────────
    // Shared handlers (called from both API-version overrides above)
    // ────────────────────────────────────────────────────────────────────────

    private fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Read failed: status=$status uuid=${characteristic.uuid}")
            return
        }
        when (characteristic.uuid) {
            BleConstants.PRESETS_CHAR_UUID -> {
                val jsonText = value.toString(Charsets.UTF_8)
                Log.d(TAG, "Presets JSON (${value.size} bytes): $jsonText")
                val presets = try {
                    json.decodeFromString<List<com.example.wledble.model.Preset>>(jsonText)
                } catch (e: Exception) {
                    Log.e(TAG, "Preset JSON parse error: ${e.message}")
                    emptyList()
                }
                _state.update { it.copy(presets = presets, isLoadingPresets = false) }

                // Next: enable notifications on Power characteristic
                if (setupPhase == SetupPhase.READING_PRESETS) {
                    setupPhase = SetupPhase.ENABLING_POWER_NOTIFY
                    enableNotifications(gatt, powerChar!!)
                }
            }
            BleConstants.POWER_CHAR_UUID -> {
                val powered = value.firstOrNull() == 0x01.toByte()
                _state.update { it.copy(isPowered = powered) }

                // Final step — mark setup complete and surface Connected state
                if (setupPhase == SetupPhase.READING_INITIAL_POWER) {
                    setupPhase = SetupPhase.COMPLETE
                    Log.i(TAG, "BLE setup complete — app ready")
                    _state.update { it.copy(connectionState = ConnectionState.Connected) }
                }
            }
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        when (uuid) {
            BleConstants.POWER_CHAR_UUID -> {
                val powered = value.firstOrNull() == 0x01.toByte()
                Log.d(TAG, "Power notify: $powered")
                _state.update { it.copy(isPowered = powered) }
            }
            BleConstants.ACTIVE_PRESET_CHAR_UUID -> {
                val raw = value.firstOrNull()?.toInt()?.and(0xFF)
                val presetId = if (raw == null || raw == BleConstants.NO_ACTIVE_PRESET) null else raw
                Log.d(TAG, "ActivePreset notify: $presetId")
                _state.update { it.copy(activePresetId = presetId) }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ────────────────────────────────────────────────────────────────────────

    private fun doConnect(device: BluetoothDevice) {
        gatt?.close()           // close any stale handle from previous attempt
        gatt = device.connectGatt(
            context,
            false,              // autoConnect=false → direct connect (faster, avoids ghost connects)
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
        this.gatt = gatt
    }

    private fun scheduleReconnect(device: BluetoothDevice) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = minOf(
                BleConstants.RECONNECT_BASE_MS * (1L shl reconnectAttempts.coerceAtMost(4)),
                BleConstants.RECONNECT_MAX_MS
            )
            val attempt = reconnectAttempts + 1
            Log.i(TAG, "Reconnect attempt $attempt in ${delayMs / 1000}s")
            _state.update {
                it.copy(connectionState = ConnectionState.Reconnecting(attempt))
            }
            delay(delayMs)
            reconnectAttempts++
            if (isActive && targetDevice != null) doConnect(device)
        }
    }

    /**
     * Write a characteristic value using the correct API for the running SDK.
     * The deprecated path sets [BluetoothGattCharacteristic.value] and calls
     * the old single-arg overload; API 33+ has an atomic write that avoids
     * the shared-value race condition.
     */
    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                char, value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            char.value     = value
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(char)
        }
    }

    /**
     * Enable ATT notifications by writing ENABLE_NOTIFICATION_VALUE (0x01, 0x00)
     * to the CCCD descriptor. Must call [BluetoothGatt.setCharacteristicNotification]
     * first — that's a local flag only; the descriptor write is what actually
     * instructs the remote server to start sending notifications.
     */
    @Suppress("DEPRECATION")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic
    ) {
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(BleConstants.CCCD_UUID) ?: run {
            Log.e(TAG, "No CCCD descriptor on ${char.uuid} — firmware missing descriptor?")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
        }
    }

    private fun clearCharacteristicCache() {
        powerChar        = null
        presetsChar      = null
        activePresetChar = null
        setupPhase       = SetupPhase.IDLE
    }

    private fun updateError(msg: String) {
        Log.e(TAG, msg)
        _state.update { it.copy(connectionState = ConnectionState.Error(msg)) }
    }
}