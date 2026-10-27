package com.jpchurchouse.wledblewear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.action.Actions

/**
 * Next preset (SHORT_TEXT / LONG_TEXT / SMALL_IMAGE). A tap switches to the next preset in
 * the background; the long form also shows the current preset.
 */
class WledNextComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) =
        listener.onComplicationData(build(request.complicationType, stateText(prefsNow())))

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "Rainbow")

    private fun build(type: ComplicationType, current: String): ComplicationData? {
        val tap = Actions.pendingAction(this, Actions.NEXT, REQUEST_CODE)
        val description = text("Next preset. Now: $current")
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text("Next"), description)
                    .setMonochromaticImage(icon(R.drawable.ic_wled_next))
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(current), description)
                    .setTitle(text("Next preset"))
                    .setMonochromaticImage(icon(R.drawable.ic_wled_next))
                    .setTapAction(tap)
                    .build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_wled_next), SmallImageType.ICON).build(),
                    description,
                ).setTapAction(tap).build()
            else -> null
        }
    }

    companion object {
        private const val REQUEST_CODE = 1006
    }
}
