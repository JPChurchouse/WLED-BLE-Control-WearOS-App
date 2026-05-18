package com.jpchurchouse.wledblewear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Complication #3 — Power toggle (SHORT_TEXT).
 *
 * Displays the last-connected device name as the title and the cached power state
 * ("On" / "Off" / "?") as the main text.  Tapping fires a broadcast to
 * WledToggleBroadcastReceiver, which starts WledToggleForegroundService to connect
 * to the LCD (if not already connected) and toggle power — no UI shown.
 *
 * The icon is coloured to reflect the cached power state in supporting watch faces:
 *   • Power On  → R.drawable.ic_wled_power_on   (e.g. bright / filled)
 *   • Power Off → R.drawable.ic_wled_power_off  (e.g. dim / outline)
 *   • Unknown   → R.drawable.ic_wled_complication (neutral)
 *
 * AndroidManifest.xml entry required:
 *
 *   <service
 *       android:name=".complication.WledToggleComplicationService"
 *       android:label="@string/complication_toggle_label"
 *       android:exported="true"
 *       android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
 *       <intent-filter>
 *           <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/>
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.support.wearable.complications.SUPPORTED_TYPES"
 *           android:value="SHORT_TEXT"/>
 *       <meta-data
 *           android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
 *           android:value="0"/>
 *   </service>
 *
 * Resources required (monochromatic vectors, ambient-safe):
 *   res/drawable/ic_wled_power_on.xml
 *   res/drawable/ic_wled_power_off.xml
 *   res/drawable/ic_wled_complication.xml
 */
class WledToggleComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            listener.onComplicationData(null)
            return
        }

        val prefs      = runBlocking { applicationContext.wledDataStore.data.first() }
        val deviceName = prefs[WledPreferences.DEVICE_NAME] ?: "WLED"
        val isPowerOn  = prefs[WledPreferences.IS_POWER_ON]   // null = unknown

        listener.onComplicationData(buildData(deviceName, isPowerOn))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return buildData(deviceName = "WLED", isPowerOn = true)
    }

    private fun buildData(deviceName: String, isPowerOn: Boolean?): ShortTextComplicationData {
        val powerText = when (isPowerOn) {
            true  -> "On"
            false -> "Off"
            null  -> "?"
        }
        val iconRes = when (isPowerOn) {
            true  -> R.drawable.ic_wled_power_on
            false -> R.drawable.ic_wled_power_off
            null  -> R.drawable.ic_wled_complication
        }

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(powerText).build(),
            contentDescription = PlainComplicationText.Builder(
                "WLED $deviceName power: $powerText — tap to toggle"
            ).build(),
        )
        .setTitle(PlainComplicationText.Builder(deviceName).build())
        .setMonochromaticImage(
            MonochromaticImage.Builder(
                Icon.createWithResource(this, iconRes)
            ).build()
        )
        .setTapAction(togglePendingIntent())
        .build()
    }

    /**
     * Broadcast that starts WledToggleForegroundService.
     * FLAG_IMMUTABLE is required on API 31+.
     */
    private fun togglePendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            REQUEST_CODE,
            Intent(this, WledToggleBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val REQUEST_CODE = 1003
    }
}
