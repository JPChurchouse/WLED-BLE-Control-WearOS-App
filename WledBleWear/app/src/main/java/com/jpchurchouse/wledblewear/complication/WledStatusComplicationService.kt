package com.jpchurchouse.wledblewear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Complication #4 — Read-only status display (LONG_TEXT).
 *
 * Title: last-connected device name (or "WLED BLE" if none).
 * Text:  the active preset name when connected and a preset is active,
 *        "Power Off" when connected but power is off,
 *        "Last known: On/Off" when disconnected with stale data,
 *        "Disconnected" when no live or stale data exists.
 *
 * No tap action — this is a purely informational glance.
 *
 * Data refreshed by WledApplication.startStateSync() on every BLE state change
 * via ComplicationDataSourceUpdateRequester.requestUpdateAll().
 *
 * AndroidManifest.xml entry required:
 *
 *   <service
 *       android:name=".complication.WledStatusComplicationService"
 *       android:label="@string/complication_status_label"
 *       android:exported="true"
 *       android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
 *       <intent-filter>
 *           <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/>
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.support.wearable.complications.SUPPORTED_TYPES"
 *           android:value="LONG_TEXT"/>
 *       <meta-data
 *           android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
 *           android:value="0"/>
 *   </service>
 */
class WledStatusComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        if (request.complicationType != ComplicationType.LONG_TEXT) {
            listener.onComplicationData(null)
            return
        }

        val prefs       = runBlocking { applicationContext.wledDataStore.data.first() }
        val deviceName  = prefs[WledPreferences.DEVICE_NAME] ?: FALLBACK_NAME
        val isConnected = prefs[WledPreferences.IS_CONNECTED] ?: false
        val isPowerOn   = prefs[WledPreferences.IS_POWER_ON]
        val presetName  = prefs[WledPreferences.ACTIVE_PRESET_NAME]

        listener.onComplicationData(buildData(deviceName, isConnected, isPowerOn, presetName))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.LONG_TEXT) return null
        return buildData(
            deviceName  = FALLBACK_NAME,
            isConnected = true,
            isPowerOn   = true,
            presetName  = "Solid",
        )
    }

    private fun buildData(
        deviceName:  String,
        isConnected: Boolean,
        isPowerOn:   Boolean?,
        presetName:  String?,
    ): LongTextComplicationData {
        val statusText = when {
            // Connected and a preset is active
            isConnected && presetName != null && isPowerOn == true ->
                presetName

            // Connected, power on, but no preset name (shouldn't normally happen)
            isConnected && isPowerOn == true ->
                "Power On"

            // Connected but power is off
            isConnected && isPowerOn == false ->
                "Power Off"

            // Disconnected with stale power data
            !isConnected && isPowerOn != null ->
                "Last: ${if (isPowerOn) "On" else "Off"}"

            // No data at all
            else ->
                "Disconnected"
        }

        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(statusText).build(),
            contentDescription = PlainComplicationText.Builder(
                "WLED $deviceName: $statusText"
            ).build(),
        )
        .setTitle(PlainComplicationText.Builder(deviceName).build())
        .build()
    }

    companion object {
        private const val FALLBACK_NAME = "WLED BLE"
    }
}
