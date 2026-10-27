package com.jpchurchouse.wledblewear

import android.app.Application
import android.content.ComponentName
import androidx.datastore.preferences.core.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.jpchurchouse.wledblewear.action.ActionService
import com.jpchurchouse.wledblewear.ble.BleManager
import com.jpchurchouse.wledblewear.complication.WledBrightnessComplicationService
import com.jpchurchouse.wledblewear.complication.WledLaunchComplicationService
import com.jpchurchouse.wledblewear.complication.WledNextComplicationService
import com.jpchurchouse.wledblewear.complication.WledNightlightComplicationService
import com.jpchurchouse.wledblewear.complication.WledPresetComplicationService
import com.jpchurchouse.wledblewear.complication.WledStatusComplicationService
import com.jpchurchouse.wledblewear.complication.WledToggleComplicationService
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectProblem
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.tile.WledTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the BleManager singleton and the app-scoped coroutine that mirrors BLE state into
 * DataStore for the tile and complications, which run without any Activity.
 */
class WledApplication : Application() {

    lateinit var bleManager: BleManager
        private set

    val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(applicationContext)
        ActionService.ensureChannel(this)
        startStateSync()
    }

    /** The subset of state the tile and complications show. */
    private data class Snapshot(
        val address: String?,
        val connected: Boolean,
        val powerOn: Boolean,
        val brightness: Int,
        val presetName: String?,
        val nightlightEndMs: Long,
        val problem: ConnectProblem?,
    )

    /**
     * Mirrors BLE state into DataStore. Last-known values are kept when the link drops,
     * because tiles and complications connect only briefly; they are cleared only when a
     * different device becomes current (rememberDevice / forgetDevice).
     */
    private fun startStateSync() {
        appScope.launch {
            bleManager.uiState
                .map {
                    Snapshot(
                        address = it.connectedAddress,
                        connected = it.connectionState == ConnectionState.Connected,
                        powerOn = it.isPowerOn,
                        brightness = it.brightness,
                        presetName = it.activePreset?.name,
                        // Round so the countdown's own drift doesn't count as a change.
                        nightlightEndMs = it.nightlightEndMs / 10_000 * 10_000,
                        problem = it.problem,
                    )
                }
                .distinctUntilChanged()
                .collect { s ->
                    val current = wledDataStore.data.first()[WledPreferences.DEVICE_ADDRESS]
                    if (s.address == null || s.address != current) return@collect
                    wledDataStore.edit { prefs ->
                        prefs[WledPreferences.IS_CONNECTED] = s.connected
                        if (s.connected) {
                            prefs[WledPreferences.IS_POWER_ON] = s.powerOn
                            prefs[WledPreferences.BRIGHTNESS] = s.brightness
                            if (s.presetName != null) prefs[WledPreferences.ACTIVE_PRESET_NAME] = s.presetName
                            else prefs.remove(WledPreferences.ACTIVE_PRESET_NAME)
                            if (s.nightlightEndMs > 0) prefs[WledPreferences.NIGHTLIGHT_END_MS] = s.nightlightEndMs
                            else prefs.remove(WledPreferences.NIGHTLIGHT_END_MS)
                            prefs[WledPreferences.STATE_UPDATED_MS] = System.currentTimeMillis()
                            prefs.remove(WledPreferences.CONNECT_FAILED)
                            prefs.remove(WledPreferences.NEEDS_PAIRING)
                        }
                        when (s.problem) {
                            ConnectProblem.PAIRING_WINDOW_CLOSED,
                            ConnectProblem.PAIRING_FAILED,
                            ConnectProblem.BOND_LOST -> prefs[WledPreferences.NEEDS_PAIRING] = s.problem.name
                            else -> Unit
                        }
                    }
                    refreshSurfaces()
                }
        }
    }

    /** Ask the system to redraw the tile and every complication. */
    fun refreshSurfaces() {
        TileService.getUpdater(this).requestUpdate(WledTileService::class.java)
        listOf(
            WledLaunchComplicationService::class.java,
            WledToggleComplicationService::class.java,
            WledStatusComplicationService::class.java,
            WledPresetComplicationService::class.java,
            WledNextComplicationService::class.java,
            WledNightlightComplicationService::class.java,
            WledBrightnessComplicationService::class.java,
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
}
