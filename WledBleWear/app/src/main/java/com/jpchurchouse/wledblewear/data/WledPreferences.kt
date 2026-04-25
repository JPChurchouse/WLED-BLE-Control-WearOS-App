package com.jpchurchouse.wledblewear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

val Context.wledDataStore: DataStore<Preferences> by preferencesDataStore(name = "wled_prefs")

object PreferencesKeys {
    val DEVICE_MAC    = stringPreferencesKey("device_mac")
    val DEVICE_NAME   = stringPreferencesKey("device_name")
    val IS_POWERED    = booleanPreferencesKey("is_powered")
    val PRESETS_JSON  = stringPreferencesKey("presets_json")
    val ACTIVE_PRESET = intPreferencesKey("active_preset_id")
}