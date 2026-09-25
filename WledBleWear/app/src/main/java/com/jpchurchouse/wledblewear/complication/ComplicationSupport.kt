package com.jpchurchouse.wledblewear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.datastore.preferences.core.Preferences
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import com.jpchurchouse.wledblewear.EXTRA_OPEN_CURRENT
import com.jpchurchouse.wledblewear.MainActivity
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal fun text(s: String) = PlainComplicationText.Builder(s).build()

internal fun Context.icon(res: Int) = MonochromaticImage.Builder(Icon.createWithResource(this, res)).build()

/** Complication requests arrive on a background thread; one blocking DataStore read is fine. */
internal fun Context.prefsNow(): Preferences = runBlocking { wledDataStore.data.first() }

/**
 * What the lights are doing, for complication text: the preset name where known, else
 * "On"/"Off". With [stalePrefix], last-known (not live) state is prefixed "Last: ".
 */
internal fun stateText(prefs: Preferences, stalePrefix: Boolean = false): String {
    if (prefs[WledPreferences.DEVICE_ADDRESS] == null) return "No device"
    if (prefs[WledPreferences.NEEDS_PAIRING] != null) return "Pair in app"
    prefs[WledPreferences.ACTION_IN_PROGRESS]?.let { return "$it…" }
    val power = prefs[WledPreferences.IS_POWER_ON] ?: return "—"
    val state = if (power) prefs[WledPreferences.ACTIVE_PRESET_NAME] ?: "On" else "Off"
    val live = prefs[WledPreferences.IS_CONNECTED] ?: false
    return if (stalePrefix && !live) "Last: $state" else state
}

internal fun Preferences.deviceName() = this[WledPreferences.DEVICE_NAME] ?: "WLED"

/** Opens the app on the current device's control screen. */
internal fun Context.openAppIntent(requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CURRENT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
