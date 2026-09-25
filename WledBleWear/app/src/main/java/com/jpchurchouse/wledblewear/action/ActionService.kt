package com.jpchurchouse.wledblewear.action

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectProblem
import com.jpchurchouse.wledblewear.model.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs a one-tap action (Actions.kt) against the current device without showing any UI:
 * connect if needed → send the command → let the State notification land → disconnect,
 * unless the app itself holds the connection.
 *
 * A foreground service because Wear OS doesn't keep background BLE work alive otherwise.
 * Started by ActionActivity (tile buttons and complication taps).
 */
class ActionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var running = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Sending command…")
            .setSmallIcon(R.drawable.ic_wled_complication)
            .setOngoing(true)
            .build()
        // Without the Bluetooth permission (app never opened), neither the foreground service
        // type nor BLE is allowed: record the failure for the tile instead of crashing.
        val started = hasBluetoothPermission() && runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "startForeground failed: ${it.message}") }.isSuccess
        if (!started) {
            serviceScope.launch {
                applicationContext.wledDataStore.edit { it[WledPreferences.CONNECT_FAILED] = true }
                (application as WledApplication).refreshSurfaces()
                if (running == 0) stopSelf()
            }
            return START_NOT_STICKY
        }

        val action = intent?.getStringExtra(Actions.EXTRA_ACTION)
        running++
        serviceScope.launch {
            try {
                if (action != null) runAction(action)
            } finally {
                if (--running == 0) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runAction(action: String) = actionMutex.withLock {
        val app = application as WledApplication
        val ble = app.bleManager
        val store = applicationContext.wledDataStore
        val command = Actions.command(action) ?: return
        val prefs = store.data.first()
        val address = prefs[WledPreferences.DEVICE_ADDRESS] ?: return
        val name = prefs[WledPreferences.DEVICE_NAME] ?: "WLED"

        store.edit { it[WledPreferences.ACTION_IN_PROGRESS] = Actions.label(action) }
        app.refreshSurfaces()

        val appOwnsConnection = ble.isConnectedTo(address)
        val connected = ble.ensureConnected(ScannedDevice(address, name), CONNECT_TIMEOUT_MS)
        var sent = false
        if (connected) {
            sent = ble.sendCommand(command)
            delay(SETTLE_MS)  // let the resulting State notification update the cache
            if (!appOwnsConnection && ble.isConnectedTo(address)) ble.disconnect()
        }
        Log.d(TAG, "action $action: connected=$connected sent=$sent")

        val problem = ble.uiState.value.problem
        store.edit {
            it.remove(WledPreferences.ACTION_IN_PROGRESS)
            if (sent) {
                it.remove(WledPreferences.CONNECT_FAILED)
                it.remove(WledPreferences.NEEDS_PAIRING)
            } else if (problem in PAIRING_PROBLEMS) {
                it[WledPreferences.NEEDS_PAIRING] = problem!!.name
            } else {
                it[WledPreferences.CONNECT_FAILED] = true
            }
        }
        app.refreshSurfaces()
    }

    private fun hasBluetoothPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "ActionService"
        const val CHANNEL_ID = "wled_bg_actions"
        private const val NOTIFICATION_ID = 2001
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val SETTLE_MS = 600L
        private val actionMutex = Mutex()
        private val PAIRING_PROBLEMS = setOf(
            ConnectProblem.PAIRING_WINDOW_CLOSED,
            ConnectProblem.PAIRING_FAILED,
            ConnectProblem.BOND_LOST,
        )

        /** Returns false if Android refused to start the service (e.g. from the background). */
        fun start(context: Context, action: String): Boolean = runCatching {
            context.startForegroundService(
                Intent(context, ActionService::class.java).putExtra(Actions.EXTRA_ACTION, action)
            )
        }.onFailure { Log.w(TAG, "Couldn't start ActionService: ${it.message}") }.isSuccess

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "WLED background actions", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown briefly while a tile or complication action runs" }
            )
        }
    }
}
