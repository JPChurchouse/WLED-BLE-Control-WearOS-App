package com.jpchurchouse.wledblewear.complication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WledToggleForegroundService — background power toggle for complication #3.
 *
 * Lifecycle:
 *   1. Started by WledToggleBroadcastReceiver on complication tap.
 *   2. Immediately posts a minimal foreground notification (required within 5 s on API 26+).
 *   3. If BleManager is already connected → togglePower() → stop.
 *   4. If not connected → connect to LCD (up to CONNECT_TIMEOUT_MS) → togglePower() → stop.
 *   5. On timeout → stop silently (BleManager.connect() backoff is NOT left running;
 *      we call disconnect() so the app stays in a clean Idle state).
 *
 * The service does NOT launch any Activity or show a persistent notification.
 * The "Toggling…" notification is transient and dismissed when the service stops.
 *
 * AndroidManifest.xml entries required:
 *
 *   <service
 *       android:name=".complication.WledToggleForegroundService"
 *       android:exported="false"
 *       android:foregroundServiceType="connectedDevice"/>
 *
 * Permissions required in manifest:
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
 *
 * Notification channel CHANNEL_ID is created in WledApplication.onCreate().
 */
class WledToggleForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch {
            doConnectAndToggle()
            stopSelf()
        }
        return START_NOT_STICKY  // don't restart automatically if killed
    }

    private suspend fun doConnectAndToggle() {
        val app        = application as WledApplication
        val bleManager = app.bleManager
        val prefs      = applicationContext.wledDataStore.data.first()

        val address = prefs[WledPreferences.DEVICE_ADDRESS] ?: return
        val name    = prefs[WledPreferences.DEVICE_NAME] ?: "WLED"

        // If already connected, toggle immediately
        if (bleManager.uiState.value.connectionState == ConnectionState.Connected) {
            bleManager.togglePower()
            return
        }

        // Attempt to connect within the timeout window
        bleManager.connect(ScannedDevice(address, name))

        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            bleManager.uiState.first { it.connectionState == ConnectionState.Connected }
        }

        if (connected != null) {
            bleManager.togglePower()
        } else {
            // Timed out — cancel the backoff loop so the app stays in a clean Idle state.
            // The WledApplication state sync will remove stale DataStore keys and refresh
            // the complications automatically when it receives the Idle emission.
            bleManager.disconnect()
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "WLED Background Actions",
                NotificationManager.IMPORTANCE_LOW,  // no sound; no heads-up
            ).apply {
                description = "Silent notifications for background WLED BLE operations"
            }
        )
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("WLED BLE Wear")
            .setContentText("Toggling power…")
            .setSmallIcon(R.drawable.ic_wled_complication)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        /** Must match WledApplication.TOGGLE_SERVICE_CHANNEL. */
        const val CHANNEL_ID       = "wled_bg_actions"
        private const val NOTIFICATION_ID    = 2001
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
