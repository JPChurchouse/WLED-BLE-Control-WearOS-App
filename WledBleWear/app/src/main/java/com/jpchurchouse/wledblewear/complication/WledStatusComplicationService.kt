package com.jpchurchouse.wledblewear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.data.WledPreferences

/**
 * Connection status (LONG_TEXT / SHORT_TEXT): title is the device name with ● when
 * connected, text is the preset name or On/Off ("Last: …" when not live). Tap opens the app.
 */
class WledStatusComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        val prefs = prefsNow()
        val live = prefs[WledPreferences.IS_CONNECTED] ?: false
        listener.onComplicationData(build(request.complicationType,
            (if (live) "● " else "") + prefs.deviceName(), stateText(prefs, stalePrefix = true), stateText(prefs)))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, "● Lounge", "Rainbow", "Rainbow")

    private fun build(type: ComplicationType, title: String, long: String, short: String): ComplicationData? {
        val description = text("$title: $long")
        return when (type) {
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(long), description)
                    .setTitle(text(title))
                    .setMonochromaticImage(icon(R.drawable.ic_wled_complication))
                    .setTapAction(openAppIntent(REQUEST_CODE))
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(short), description)
                    .setTitle(text(title))
                    .setTapAction(openAppIntent(REQUEST_CODE))
                    .build()
            else -> null
        }
    }

    companion object {
        private const val REQUEST_CODE = 1004
    }
}
