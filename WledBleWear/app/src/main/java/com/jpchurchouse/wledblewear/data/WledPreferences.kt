package com.jpchurchouse.wledblewear.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

object WledPreferences {
    /** MAC address of the last successfully connected device. */
    val DEVICE_ADDRESS  = stringPreferencesKey("device_address")
    /** Display name of the last successfully connected device. */
    val DEVICE_NAME     = stringPreferencesKey("device_name")
    /**
     * Comma-separated list of the most-recently-activated preset IDs (max 3),
     * newest first.  Used by the tile to show quick-access preset buttons.
     */
    val RECENT_PRESETS  = stringPreferencesKey("recent_presets")
}

val Context.wledDataStore by preferencesDataStore(name = "wled_prefs")
