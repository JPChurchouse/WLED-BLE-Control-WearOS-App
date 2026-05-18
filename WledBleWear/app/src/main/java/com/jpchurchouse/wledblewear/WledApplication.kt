package com.jpchurchouse.wledblewear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import androidx.datastore.preferences.core.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.jpchurchouse.wledblewear.ble.BleManager
import com.jpchurchouse.wledblewear.complication.WledLaunchComplicationService
import com.jpchurchouse.wledblewear.complication.WledStatusComplicationService
import com.jpchurchouse.wledblewear.complication.WledToggleComplicationService
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.tile.WledTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application subclass — owns the BleManager singleton and the app-scoped
 * coroutine that mirrors BLE state to DataStore.
 *
 * WHY appScope / startStateSync lives here (not in ViewModel):
 *   ViewModel is destroyed with MainActivity.  The tile and complication services
 *   run in the same process but independently of any Activity.  Moving the
 *   DataStore-write + tile/complication-refresh loop to Application ensures it
 *   is always running while the process is alive — even when the watch face is
 *   showing and no Activity exists.
 */
class WledApplication : Application() {

    lateinit var bleManager: BleManager
        private set

    /**
     * Application-lifetime coroutine scope.
     * Dispatchers.Main — required for GATT ops inside BleManager; safe here
     * because we only do DataStore writes and intent broadcasts (both fast).
     */
    val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(applicationContext)
        createNotificationChannels()
        startStateSync()
    }

    /**
     * Collects every BLE state emission and:
     *  1. Writes the relevant subset to DataStore (tile / complication source of truth).
     *  2. Requests a tile redraw.
     *  3. Requests a complication redraw on all registered instances.
     *
     * DataStore write semantics:
     *  - Connected   → write IS_CONNECTED, IS_POWER_ON, ACTIVE_PRESET_NAME; clear CONNECT_FAILED
     *  - Disconnected → write IS_CONNECTED=false; preserve IS_POWER_ON / ACTIVE_PRESET_NAME
     *                   so the tile can show "last known" stale state
     *  - Idle        → write IS_CONNECTED=false; REMOVE IS_POWER_ON / ACTIVE_PRESET_NAME
     *                   because the user explicitly disconnected — no stale data to show
     */
    private fun createNotificationChannels() {
        // Channel for WledToggleForegroundService (complication #3 background BLE toggle)
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(TOGGLE_SERVICE_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    TOGGLE_SERVICE_CHANNEL,
                    "WLED Background Actions",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Silent notifications for background WLED BLE operations"
                }
            )
        }
    }

    private fun startStateSync() {
        appScope.launch {
            bleManager.uiState.collect { state ->
                wledDataStore.edit { prefs ->
                    when (state.connectionState) {
                        is ConnectionState.Connected -> {
                            prefs[WledPreferences.IS_CONNECTED]   = true
                            prefs[WledPreferences.IS_POWER_ON]    = state.isPowerOn
                            prefs.remove(WledPreferences.CONNECT_FAILED)

                            val presetName = state.presets
                                .find { it.id == state.activePresetId }?.name
                            if (presetName != null) {
                                prefs[WledPreferences.ACTIVE_PRESET_NAME] = presetName
                            } else {
                                prefs.remove(WledPreferences.ACTIVE_PRESET_NAME)
                            }
                        }

                        is ConnectionState.Disconnected -> {
                            // Involuntary drop — keep IS_POWER_ON / ACTIVE_PRESET_NAME
                            // so the tile can show them as "last known".
                            prefs[WledPreferences.IS_CONNECTED] = false
                        }

                        is ConnectionState.Idle -> {
                            // User-initiated disconnect — wipe stale data entirely.
                            prefs[WledPreferences.IS_CONNECTED] = false
                            prefs.remove(WledPreferences.IS_POWER_ON)
                            prefs.remove(WledPreferences.ACTIVE_PRESET_NAME)
                        }

                        else -> Unit  // Scanning / Connecting — no DataStore update
                    }
                }

                // Refresh tile and all registered complication instances
                TileService.getUpdater(this@WledApplication)
                    .requestUpdate(WledTileService::class.java)
                refreshComplications()
            }
        }
    }

    private fun refreshComplications() {
        listOf(
            WledLaunchComplicationService::class.java,
            WledToggleComplicationService::class.java,
            WledStatusComplicationService::class.java,
        ).forEach { cls ->
            ComplicationDataSourceUpdateRequester
                .create(this, ComponentName(this, cls))
                .requestUpdateAll()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        bleManager.cleanup()
    }

    companion object {
        /** Shared with WledToggleForegroundService — must match. */
        const val TOGGLE_SERVICE_CHANNEL = "wled_bg_actions"
    }
}
