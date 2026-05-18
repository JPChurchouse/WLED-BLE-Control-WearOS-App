package com.jpchurchouse.wledblewear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.EXTRA_TILE_COMMAND
import com.jpchurchouse.wledblewear.MainActivity
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.tile.WledTileService

/**
 * Complication #1 — Icon only (SMALL_IMAGE).
 *
 * Displays the WLED app icon.  Tapping launches MainActivity with the
 * "connect_lcd" command: the app will attempt to reconnect to the last
 * connected device and navigate to the control screen.
 *
 * AndroidManifest.xml entry required:
 *
 *   <service
 *       android:name=".complication.WledIconComplicationService"
 *       android:label="@string/complication_icon_label"
 *       android:exported="true"
 *       android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
 *       <intent-filter>
 *           <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/>
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.support.wearable.complications.SUPPORTED_TYPES"
 *           android:value="SMALL_IMAGE"/>
 *       <meta-data
 *           android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
 *           android:value="0"/>
 *   </service>
 *
 * Resource required:
 *   res/drawable/ic_wled_complication.xml  — monochromatic vector icon
 *   (ambient-safe: single-colour, no gradients)
 */
class WledIconComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        if (request.complicationType != ComplicationType.SMALL_IMAGE) {
            listener.onComplicationData(null)
            return
        }
        listener.onComplicationData(buildData())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SMALL_IMAGE) return null
        return buildData()
    }

    private fun buildData(): SmallImageComplicationData =
        SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithResource(this, R.drawable.ic_wled_complication),
                type  = SmallImageType.ICON,
            ).build(),
            contentDescription = PlainComplicationText.Builder("WLED BLE").build(),
        )
        .setTapAction(connectLcdPendingIntent())
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
        private const val REQUEST_CODE = 1001
    }
}
