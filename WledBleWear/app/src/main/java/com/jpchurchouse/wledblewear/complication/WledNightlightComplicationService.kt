package com.jpchurchouse.wledblewear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.data.WledPreferences
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Sleep timer (SHORT_TEXT). While WLED's nightlight runs, a countdown the watch face
 * advances by itself ("23m"); otherwise "—". Tap opens the app.
 */
class WledNightlightComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return listener.onComplicationData(null)
        val end = prefsNow()[WledPreferences.NIGHTLIGHT_END_MS]?.takeIf { it > System.currentTimeMillis() }
        listener.onComplicationData(build(end))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build(System.currentTimeMillis() + 23 * 60_000L) else null

    private fun build(endMs: Long?): ShortTextComplicationData {
        val body = if (endMs == null) text("—") else
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                CountDownTimeReference(Instant.ofEpochMilli(endMs)),
            ).setMinimumTimeUnit(TimeUnit.MINUTES).build()
        return ShortTextComplicationData.Builder(body, text("WLED sleep timer"))
            .setMonochromaticImage(icon(R.drawable.ic_wled_timer))
            .setTapAction(openAppIntent(REQUEST_CODE))
            .build()
    }

    companion object {
        private const val REQUEST_CODE = 1008
    }
}
