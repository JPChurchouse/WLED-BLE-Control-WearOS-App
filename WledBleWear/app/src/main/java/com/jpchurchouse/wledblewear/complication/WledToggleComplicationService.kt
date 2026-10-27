package com.jpchurchouse.wledblewear.complication

import androidx.datastore.preferences.core.Preferences
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.action.Actions
import com.jpchurchouse.wledblewear.data.WledPreferences

/**
 * Power toggle (SHORT_TEXT / LONG_TEXT): the device name and what's showing (preset name,
 * or On/Off). A tap toggles power in the background via ActionService.
 */
class WledToggleComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        val prefs = prefsNow()
        listener.onComplicationData(build(request.complicationType, prefs.deviceName(),
            stateText(prefs), stateText(prefs, stalePrefix = true), prefs[WledPreferences.IS_POWER_ON]))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, "Lounge", "Rainbow", "Rainbow", true)

    private fun build(type: ComplicationType, device: String, short: String, long: String, powerOn: Boolean?): ComplicationData? {
        val iconRes = when (powerOn) {
            true -> R.drawable.ic_wled_power_on
            false -> R.drawable.ic_wled_power_off
            null -> R.drawable.ic_wled_complication
        }
        val description = text("$device: $long. Tap to toggle power")
        val tap = Actions.pendingAction(this, Actions.TOGGLE, REQUEST_CODE)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(short), description)
                    .setTitle(text(device))
                    .setMonochromaticImage(icon(iconRes))
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(long), description)
                    .setTitle(text(device))
                    .setMonochromaticImage(icon(iconRes))
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    companion object {
        private const val REQUEST_CODE = 1003
    }
}
