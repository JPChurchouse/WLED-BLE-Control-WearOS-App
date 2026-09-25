package com.jpchurchouse.wledblewear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.data.WledPreferences
import kotlin.math.roundToInt

/** Brightness gauge (RANGED_VALUE / SHORT_TEXT), 0% while off. Tap opens the app. */
class WledBrightnessComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        val prefs = prefsNow()
        val power = prefs[WledPreferences.IS_POWER_ON]
        val percent = when (power) {
            null -> null
            false -> 0
            true -> ((prefs[WledPreferences.BRIGHTNESS] ?: 0) / 255f * 100).roundToInt()
        }
        listener.onComplicationData(build(request.complicationType, prefs.deviceName(), percent))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "Lounge", 60)

    private fun build(type: ComplicationType, device: String, percent: Int?): ComplicationData? {
        val label = percent?.let { "$it%" } ?: "—"
        val description = text("$device brightness $label")
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder((percent ?: 0).toFloat(), 0f, 100f, description)
                    .setText(text(label))
                    .setMonochromaticImage(icon(R.drawable.ic_wled_brightness))
                    .setTapAction(openAppIntent(REQUEST_CODE))
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(label), description)
                    .setMonochromaticImage(icon(R.drawable.ic_wled_brightness))
                    .setTapAction(openAppIntent(REQUEST_CODE))
                    .build()
            else -> null
        }
    }

    companion object {
        private const val REQUEST_CODE = 1009
    }
}
