package com.jpchurchouse.wledblewear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R

/** Current preset (SHORT_TEXT / LONG_TEXT), last known. Tap opens the app. */
class WledPresetComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        val prefs = prefsNow()
        listener.onComplicationData(build(request.complicationType, prefs.deviceName(), stateText(prefs)))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "Lounge", "Rainbow")

    private fun build(type: ComplicationType, device: String, preset: String): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(preset), text("$device: $preset"))
                    .setMonochromaticImage(icon(R.drawable.ic_wled_complication))
                    .setTapAction(openAppIntent(REQUEST_CODE))
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(preset), text("$device: $preset"))
                    .setTitle(text(device))
                    .setMonochromaticImage(icon(R.drawable.ic_wled_complication))
                    .setTapAction(openAppIntent(REQUEST_CODE))
                    .build()
            else -> null
        }

    companion object {
        private const val REQUEST_CODE = 1005
    }
}
