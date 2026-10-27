package com.jpchurchouse.wledblewear.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.jpchurchouse.wledblewear.data.loadPresetCache
import com.jpchurchouse.wledblewear.data.rememberDevice
import com.jpchurchouse.wledblewear.data.savePresetCache
import com.jpchurchouse.wledblewear.model.ConnectProblem
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.Preset
import com.jpchurchouse.wledblewear.model.PresetCache
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.model.WledUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Single source of truth for all BLE operations (protocol: Protocol.kt).
 *
 * Lifecycle: created once in WledApplication and held for the lifetime of the process,
 * so the tile, complications and background actions share one connection with the app.
 *
 * Threading: everything runs on Dispatchers.Main; GATT callbacks complete deferreds that
 * suspended coroutines await. One GATT operation is in flight at a time (gattMutex).
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val tag = "BleManager"

    private val _uiState = MutableStateFlow(WledUiState())
    val uiState: StateFlow<WledUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private val leScanner get() = bluetoothAdapter.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null

    /** Device the app wants to stay connected to; null after a user disconnect. */
    private var sessionDevice: ScannedDevice? = null
    /** True when the connection should be re-established after a drop. */
    private var autoReconnect = false
    private var reconnectJob: Job? = null
    private var setupJob: Job? = null
    private var presetFetchJob: Job? = null
    /** Set while we expect the device to drop the link (OFF_AND_DISCONNECT). */
    private var expectDisconnect = false

    private var presetRevision: Int? = null

    // ── GATT operation serialisation ──────────────────────────────────────────

    private class GattResult(val status: Int, val value: ByteArray?) {
        val ok get() = status == BluetoothGatt.GATT_SUCCESS
    }

    private val gattMutex = Mutex()
    @Volatile private var pending: CompletableDeferred<GattResult>? = null

    private fun complete(status: Int, value: ByteArray? = null) {
        pending?.complete(GattResult(status, value))
    }

    // ── Bonding ─────────────────────────────────────────────────────────────────

    @Volatile private var bondResult: CompletableDeferred<Int>? = null

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (device?.address != bluetoothGatt?.device?.address) return
            val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            Log.d(tag, "Bond state → $bond")
            if (bond == BluetoothDevice.BOND_BONDED || bond == BluetoothDevice.BOND_NONE) {
                bondResult?.complete(bond)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    fun startScan() {
        if (scanCallback != null) return
        _uiState.update {
            it.copy(connectionState = ConnectionState.Scanning, scannedDevices = emptyList(), problem = null)
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(Protocol.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName
                    ?: result.device.name
                    ?: "WLED (${result.device.address.takeLast(5)})"
                val found = ScannedDevice(address = result.device.address, name = name)
                _uiState.update { state ->
                    val others = state.scannedDevices.filter { it.address != found.address }
                    // Keep order stable; replace an unnamed entry once the scan response arrives.
                    val index = state.scannedDevices.indexOfFirst { it.address == found.address }
                    if (index < 0) state.copy(scannedDevices = others + found)
                    else state.copy(scannedDevices = state.scannedDevices.toMutableList().also { it[index] = found })
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "Scan failed: errorCode=$errorCode")
                scanCallback = null
                _uiState.update { it.copy(connectionState = ConnectionState.Idle) }
            }
        }
        leScanner?.startScan(listOf(filter), settings, scanCallback!!)
        // Scanning drains the battery; stop on our own after a while.
        scope.launch {
            delay(SCAN_DURATION_MS)
            if (_uiState.value.connectionState == ConnectionState.Scanning) stopScan()
        }
    }

    fun stopScan() {
        scanCallback?.let { runCatching { leScanner?.stopScan(it) } }
        scanCallback = null
        _uiState.update {
            if (it.connectionState == ConnectionState.Scanning) it.copy(connectionState = ConnectionState.Idle) else it
        }
    }

    // ── Connect / disconnect ──────────────────────────────────────────────────

    /**
     * Connect to [device]. With [keepConnected] (the app's control screen), drops are
     * followed by an exponential-backoff reconnect until [disconnect] is called.
     */
    fun connect(device: ScannedDevice, keepConnected: Boolean = true) {
        stopScan()
        reconnectJob?.cancel()
        sessionDevice = device
        autoReconnect = keepConnected
        if (isConnectedTo(device.address)) return
        _uiState.update {
            it.copy(
                connectedAddress = device.address,
                connectedDeviceName = device.name,
                problem = null,
                lastError = null,
            )
        }
        doConnect(device.address)
    }

    /** User-initiated disconnect: closes the link and stops any reconnect. */
    fun disconnect() {
        reconnectJob?.cancel()
        setupJob?.cancel()
        presetFetchJob?.cancel()
        sessionDevice = null
        autoReconnect = false
        pending?.complete(GattResult(GATT_ABORTED, null))
        bondResult?.complete(BluetoothDevice.BOND_NONE)
        bluetoothGatt?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
        bluetoothGatt = null
        _uiState.update { it.copy(connectionState = ConnectionState.Idle) }
        Log.d(tag, "Disconnected by user request")
    }

    fun isConnectedTo(address: String) =
        _uiState.value.connectionState == ConnectionState.Connected &&
            _uiState.value.connectedAddress == address

    /**
     * For background actions: make sure we're connected to [device], connecting if needed.
     * Returns true once Connected, false on a problem or timeout (the connection attempt is
     * then abandoned unless the app itself keeps a session open).
     */
    suspend fun ensureConnected(device: ScannedDevice, timeoutMs: Long): Boolean {
        if (isConnectedTo(device.address)) return true
        val keep = sessionDevice?.address == device.address && autoReconnect
        connect(device, keepConnected = keep)
        val result = withTimeoutOrNull(timeoutMs) {
            uiState.first {
                it.connectionState == ConnectionState.Connected ||
                    it.problem != null ||
                    it.connectionState == ConnectionState.Idle
            }
        }
        val ok = result?.connectionState == ConnectionState.Connected
        if (!ok && !keep) disconnect()
        return ok
    }

    /** Removes the watch's bond with [address] so the device can be paired again. */
    fun forgetBond(address: String) {
        val device = runCatching { bluetoothAdapter.getRemoteDevice(address) }.getOrNull() ?: return
        if (device.bondState == BluetoothDevice.BOND_NONE) return
        // BluetoothDevice.removeBond() is hidden API, but the standard way to do this.
        runCatching { device.javaClass.getMethod("removeBond").invoke(device) }
            .onFailure { Log.w(tag, "removeBond failed: ${it.message}") }
    }

    /** Shut down (process teardown only). */
    fun cleanup() {
        disconnect()
        runCatching { context.unregisterReceiver(bondReceiver) }
        scope.cancel()
    }

    private fun doConnect(address: String) {
        _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
        val device = bluetoothAdapter.getRemoteDevice(address)
        bluetoothGatt?.close()
        expectDisconnect = false
        presetRevision = null
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(tag, "connectGatt → $address")
    }

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "onConnectionStateChange: newState=$newState status=$status")
            scope.launch {
                if (gatt != bluetoothGatt) return@launch  // stale callback from a closed GATT
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Give a bonded link time to re-encrypt before discovery.
                    if (gatt.device.bondState == BluetoothDevice.BOND_BONDED) delay(300)
                    gatt.discoverServices()
                } else {
                    pending?.complete(GattResult(status.takeIf { it != 0 } ?: GATT_ABORTED, null))
                    handleDisconnect(gatt, status)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tag, "onServicesDiscovered: status=$status")
            scope.launch {
                if (gatt != bluetoothGatt) return@launch
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    setupJob?.cancel()
                    setupJob = scope.launch { runSetupChain(gatt) }
                } else {
                    gatt.disconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(tag, "MTU → $mtu (status=$status)")
            complete(status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                complete(status, if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                complete(status, if (status == BluetoothGatt.GATT_SUCCESS) value else null)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            complete(status)

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            complete(status)

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                scope.launch { dispatchNotify(characteristic.uuid, value) }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                scope.launch { dispatchNotify(characteristic.uuid, value) }
            }
        }
    }

    // ── GATT setup chain ──────────────────────────────────────────────────────

    private suspend fun runSetupChain(gatt: BluetoothGatt) {
        val service = gatt.getService(Protocol.SERVICE)
            ?: return fail(gatt, ConnectProblem.NOT_WLED)
        val infoChar   = service.getCharacteristic(Protocol.INFO)
        val stateChar  = service.getCharacteristic(Protocol.STATE)
        val cmdChar    = service.getCharacteristic(Protocol.COMMAND)
        if (infoChar == null || stateChar == null || cmdChar == null) return fail(gatt, ConnectProblem.NOT_WLED)

        gattRequestMtu(gatt, Protocol.MTU)

        val info = gattRead(gatt, infoChar).value?.let(Protocol::decodeInfo)
            ?: return fail(gatt, ConnectProblem.UNREACHABLE)
        if (info.protocolVersion > Protocol.VERSION) return fail(gatt, ConnectProblem.UNSUPPORTED_VERSION)
        _uiState.update { it.copy(connectedDeviceName = info.name, wledVersion = info.wledVersion) }

        // Bond first: everything but Info needs an authenticated, bonded link.
        val device = gatt.device
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            if (!info.pairingOpen) return fail(gatt, ConnectProblem.PAIRING_WINDOW_CLOSED)
            _uiState.update { it.copy(connectionState = ConnectionState.Pairing) }
            val result = CompletableDeferred<Int>().also { bondResult = it }
            if (!device.createBond()) return fail(gatt, ConnectProblem.PAIRING_FAILED)
            val bond = withTimeoutOrNull(BOND_TIMEOUT_MS) { result.await() }
            bondResult = null
            if (bond != BluetoothDevice.BOND_BONDED) return fail(gatt, ConnectProblem.PAIRING_FAILED)
            _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
        }

        val stateRead = gattRead(gatt, stateChar)
        if (!stateRead.ok) {
            val problem = if (stateRead.status in AUTH_FAILURES) ConnectProblem.BOND_LOST else ConnectProblem.UNREACHABLE
            return fail(gatt, problem)
        }
        stateRead.value?.let(Protocol::decodeState)?.let(::applyState)

        enableNotify(gatt, stateChar)
        enableNotify(gatt, cmdChar)

        val address = device.address
        context.rememberDevice(ScannedDevice(address, info.name))

        // Presets: from cache when the revision matches, else fetch.
        val rev = stateRead.value?.let(Protocol::decodeState)?.presetRevision
        val cache = context.loadPresetCache(address)
        if (cache != null && cache.revision == rev) {
            presetRevision = rev
            _uiState.update { it.copy(presets = cache.presets) }
        } else {
            _uiState.update { it.copy(presets = cache?.presets ?: emptyList()) }
            fetchPresets(gatt)
        }

        _uiState.update { it.copy(connectionState = ConnectionState.Connected, problem = null) }
        Log.d(tag, "Setup chain complete — Connected to ${info.name}")
    }

    private suspend fun fetchPresets(gatt: BluetoothGatt) {
        val char = gatt.getService(Protocol.SERVICE)?.getCharacteristic(Protocol.PRESET_LIST) ?: return
        repeat(3) { attempt ->
            val all = mutableListOf<Preset>()
            var start = 1
            var firstRev: Int? = null
            while (true) {
                if (!gattWrite(gatt, char, byteArrayOf(start.toByte())).ok) return
                val page = gattRead(gatt, char).value?.let(Protocol::decodePresetPage) ?: return
                if (firstRev == null) firstRev = page.revision
                else if (page.revision != firstRev) break  // list changed mid-fetch: restart
                all += page.presets
                if (page.nextStartId == 0) {
                    presetRevision = firstRev
                    _uiState.update { it.copy(presets = all.toList()) }
                    context.savePresetCache(PresetCache(gatt.device.address, firstRev, all.toList()))
                    Log.d(tag, "Fetched ${all.size} presets, rev ${"%04x".format(firstRev)}")
                    return
                }
                start = page.nextStartId
            }
            Log.d(tag, "Preset list changed during fetch (attempt ${attempt + 1})")
        }
    }

    private suspend fun fail(gatt: BluetoothGatt, problem: ConnectProblem) {
        Log.w(tag, "Connection problem: $problem")
        bondResult = null
        autoReconnect = false
        _uiState.update { it.copy(problem = problem, connectionState = ConnectionState.Idle) }
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        if (bluetoothGatt == gatt) bluetoothGatt = null
    }

    // ── Primitive GATT operations ─────────────────────────────────────────────

    private suspend fun <T> gattOp(block: () -> Boolean, timeoutMs: Long = GATT_OP_TIMEOUT_MS, map: (GattResult) -> T, fallback: T): T =
        gattMutex.withLock {
            val deferred = CompletableDeferred<GattResult>().also { pending = it }
            val started = runCatching(block).getOrDefault(false)
            val result = if (started) withTimeoutOrNull(timeoutMs) { deferred.await() } else null
            pending = null
            result?.let(map) ?: fallback
        }

    private suspend fun gattRequestMtu(gatt: BluetoothGatt, mtu: Int) {
        gattOp({ gatt.requestMtu(mtu) }, map = { }, fallback = Unit)
    }

    private suspend fun gattRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic): GattResult =
        gattOp({
            @Suppress("DEPRECATION")
            gatt.readCharacteristic(char)
        }, map = { it }, fallback = GattResult(GATT_ABORTED, null))

    private suspend fun gattWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray): GattResult =
        gattOp({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                char.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }, map = { it }, fallback = GattResult(GATT_ABORTED, null))

    private suspend fun enableNotify(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(Protocol.CCCD) ?: return
        gattOp({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }, map = { }, fallback = Unit)
    }

    // ── Commands ───────────────────────────────────────────────────────────────

    /** Sends a command (Protocol.Cmd). Returns true if the device accepted the write. */
    suspend fun sendCommand(command: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        if (_uiState.value.connectionState != ConnectionState.Connected) return false
        val char = gatt.getService(Protocol.SERVICE)?.getCharacteristic(Protocol.COMMAND) ?: return false
        if (command.firstOrNull()?.toInt() == Protocol.OP_OFF_DISCONNECT) {
            expectDisconnect = true
        }
        val ok = gattWrite(gatt, char, command).ok
        if (!ok) Log.w(tag, "Command write failed: ${command.joinToString(" ") { "%02x".format(it) }}")
        return ok
    }

    fun command(command: ByteArray) {
        scope.launch { sendCommand(command) }
    }

    // ── Notifications ───────────────────────────────────────────────────────────

    private fun dispatchNotify(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            Protocol.STATE -> Protocol.decodeState(value)?.let(::applyState)
            Protocol.COMMAND -> if (value.size >= 2) {
                val status = value[1].toInt() and 0xFF
                if (status != Protocol.ST_OK) showError(Protocol.statusText(status))
            }
        }
    }

    private fun applyState(s: Protocol.State) {
        _uiState.update {
            it.copy(
                isPowerOn = s.powerOn,
                brightness = s.brightness,
                activePresetId = s.presetId.takeIf { id -> id != 0 },
                activePlaylistId = s.playlistId.takeIf { id -> id != 0 },
                nightlightActive = s.nightlightActive,
                nightlightEndMs = if (s.nightlightActive) System.currentTimeMillis() + s.nightlightRemainingSec * 1000L else 0L,
            )
        }
        val known = presetRevision
        if (known != null && known != s.presetRevision && presetFetchJob?.isActive != true) {
            val gatt = bluetoothGatt ?: return
            presetFetchJob = scope.launch { fetchPresets(gatt) }
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(lastError = message) }
        scope.launch {
            delay(3_000)
            _uiState.update { if (it.lastError == message) it.copy(lastError = null) else it }
        }
    }

    // ── Disconnect / reconnect ────────────────────────────────────────────────

    private fun handleDisconnect(gatt: BluetoothGatt, status: Int) {
        val wasConnected = _uiState.value.connectionState == ConnectionState.Connected
        val bondedButRejected = !wasConnected &&
            gatt.device.bondState == BluetoothDevice.BOND_BONDED &&
            status in AUTH_DISCONNECTS
        runCatching { gatt.close() }
        if (bluetoothGatt == gatt) bluetoothGatt = null
        setupJob?.cancel()

        if (bondedButRejected) {
            autoReconnect = false
            _uiState.update { it.copy(connectionState = ConnectionState.Idle, problem = ConnectProblem.BOND_LOST) }
            return
        }
        if (expectDisconnect) {
            expectDisconnect = false
            autoReconnect = false
            sessionDevice = null
            _uiState.update { it.copy(connectionState = ConnectionState.Idle) }
            return
        }
        if (_uiState.value.problem != null) return  // fail() already set the terminal state

        if (autoReconnect && sessionDevice != null) {
            _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
            scheduleReconnect()
        } else {
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.Idle,
                    problem = if (!wasConnected) ConnectProblem.UNREACHABLE else null,
                )
            }
        }
    }

    private fun scheduleReconnect() {
        val device = sessionDevice ?: return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var delayMs = RECONNECT_INITIAL_DELAY_MS
            while (isActive && sessionDevice != null && autoReconnect) {
                Log.d(tag, "Reconnect in ${delayMs}ms → ${device.address}")
                delay(delayMs)
                doConnect(device.address)
                val s = uiState.first {
                    it.connectionState == ConnectionState.Connected ||
                        it.connectionState == ConnectionState.Disconnected ||
                        it.connectionState == ConnectionState.Idle
                }
                if (s.connectionState != ConnectionState.Disconnected) break
                delayMs = min(delayMs * 2, RECONNECT_MAX_DELAY_MS)
            }
        }
    }

    companion object {
        private const val GATT_ABORTED = 0x101  // local marker, not a real GATT status
        private const val GATT_OP_TIMEOUT_MS = 5_000L
        private const val BOND_TIMEOUT_MS = 90_000L   // room to read and type the passkey
        private const val SCAN_DURATION_MS = 20_000L
        private const val RECONNECT_INITIAL_DELAY_MS = 2_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L

        /** GATT statuses meaning the link isn't (properly) authenticated. */
        private val AUTH_FAILURES = setOf(0x05, 0x08, 0x0F, 0x89)
        /** HCI disconnect reasons seen when the peer rejects our stored keys. */
        private val AUTH_DISCONNECTS = setOf(0x05, 0x06, 0x3D)
    }
}
