package com.jpchurchouse.wledblewear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.action.Actions

/** One-tap "turn off and disconnect" (SHORT_TEXT / SMALL_IMAGE). */
class WledOffComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) =
        listener.onComplicationData(build(request.complicationType))

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    private fun build(type: ComplicationType): ComplicationData? {
        val tap = Actions.pendingAction(this, Actions.OFF_DISCONNECT, REQUEST_CODE)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text("Off"), text("Turn WLED off and disconnect"))
                    .setMonochromaticImage(icon(R.drawable.ic_wled_power_off))
                    .setTapAction(tap)
                    .build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_wled_power_off), SmallImageType.ICON).build(),
                    text("Turn WLED off and disconnect"),
                ).setTapAction(tap).build()
            else -> null
        }
    }

    companion object {
        private const val REQUEST_CODE = 1007
    }
}
