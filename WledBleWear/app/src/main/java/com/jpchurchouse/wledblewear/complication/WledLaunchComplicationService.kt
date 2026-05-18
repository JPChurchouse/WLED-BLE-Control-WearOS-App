package com.jpchurchouse.wledblewear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.EXTRA_TILE_COMMAND
import com.jpchurchouse.wledblewear.MainActivity
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.tile.WledTileService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Complication #2 — Icon + device name (SHORT_TEXT, SMALL_IMAGE fallback).
 *
 * Displays the last-connected device name next to the WLED icon.
 * Falls back to "WLED BLE Wear" if no device has been configured.
 * Tapping launches MainActivity with the "connect_lcd" command.
 *
 * AndroidManifest.xml entry required:
 *
 *   <service
 *       android:name=".complication.WledLaunchComplicationService"
 *       android:label="@string/complication_launch_label"
 *       android:exported="true"
 *       android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
 *       <intent-filter>
 *           <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/>
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.support.wearable.complications.SUPPORTED_TYPES"
 *           android:value="SHORT_TEXT,SMALL_IMAGE"/>
 *       <meta-data
 *           android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
 *           android:value="0"/>
 *   </service>
 */
class WledLaunchComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val prefs      = runBlocking { applicationContext.wledDataStore.data.first() }
        val deviceName = prefs[WledPreferences.DEVICE_NAME] ?: FALLBACK_NAME
        val tapIntent  = connectLcdPendingIntent()

        val data: ComplicationData? = when (request.complicationType) {
            ComplicationType.SHORT_TEXT   -> buildShortText(deviceName, tapIntent)
            ComplicationType.SMALL_IMAGE  -> buildSmallImage(tapIntent)
            else                          -> null
        }
        listener.onComplicationData(data)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT  -> buildShortText(FALLBACK_NAME, null)
        ComplicationType.SMALL_IMAGE -> buildSmallImage(null)
        else                         -> null
    }

    private fun buildShortText(deviceName: String, tap: PendingIntent?): ShortTextComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(deviceName).build(),
            contentDescription = PlainComplicationText.Builder("WLED: $deviceName").build(),
        )
        .setMonochromaticImage(
            MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_wled_complication)
            ).build()
        )
        .apply { if (tap != null) setTapAction(tap) }
        .build()

    private fun buildSmallImage(tap: PendingIntent?): SmallImageComplicationData =
        SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_wled_complication),
                SmallImageType.ICON,
            ).build(),
            contentDescription = PlainComplicationText.Builder("WLED BLE").build(),
        )
        .apply { if (tap != null) setTapAction(tap) }
        .build()

    private fun connectLcdPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_TILE_COMMAND, WledTileService.CMD_CONNECT_LCD)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val REQUEST_CODE  = 1002
        private const val FALLBACK_NAME = "WLED BLE Wear"
    }
}
