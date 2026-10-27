package com.jpchurchouse.wledblewear.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jpchurchouse.wledblewear.model.Preset
import com.jpchurchouse.wledblewear.model.PresetCache
import com.jpchurchouse.wledblewear.model.ScannedDevice
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataStore keys. The app, the tile and the complications all read from here;
 * WledApplication.startStateSync() writes the live state.
 */
object WledPreferences {
    // ── Devices ───────────────────────────────────────────────────────────────
    /** The device tiles and complications act on: the last one connected. */
    val DEVICE_ADDRESS     = stringPreferencesKey("device_address")
    val DEVICE_NAME        = stringPreferencesKey("device_name")
    /** JSON list of ScannedDevice, most recently used first. */
    val SAVED_DEVICES      = stringPreferencesKey("saved_devices")
    /** JSON list of PresetCache, one per device. */
    val PRESET_CACHES      = stringPreferencesKey("preset_caches")

    // ── Live / last-known state of DEVICE_ADDRESS ─────────────────────────────
    val IS_CONNECTED       = booleanPreferencesKey("is_connected")
    /** Absent = nothing known (never connected, or the user disconnected). */
    val IS_POWER_ON        = booleanPreferencesKey("is_power_on")
    val BRIGHTNESS         = intPreferencesKey("brightness")
    val ACTIVE_PRESET_NAME = stringPreferencesKey("active_preset_name")
    /** Wall-clock end of the nightlight, or absent. */
    val NIGHTLIGHT_END_MS  = longPreferencesKey("nightlight_end_ms")
    /** When the state above was last confirmed by the device. */
    val STATE_UPDATED_MS   = longPreferencesKey("state_updated_ms")

    // ── Background action feedback ────────────────────────────────────────────
    /** Set when a tile/complication action couldn't reach the device; cleared on success. */
    val CONNECT_FAILED     = booleanPreferencesKey("connect_failed")
    /** ConnectProblem name when the device needs (re-)pairing in the app. */
    val NEEDS_PAIRING      = stringPreferencesKey("needs_pairing")
    /** Short label of a background action in progress, e.g. "Power…". */
    val ACTION_IN_PROGRESS = stringPreferencesKey("action_in_progress")
}

val Context.wledDataStore by preferencesDataStore(name = "wled_prefs")

private val json = Json { ignoreUnknownKeys = true }

fun Preferences.savedDevices(): List<ScannedDevice> =
    this[WledPreferences.SAVED_DEVICES]
        ?.let { runCatching { json.decodeFromString<List<ScannedDevice>>(it) }.getOrNull() }
        ?: emptyList()

fun Preferences.presetCache(address: String): PresetCache? =
    this[WledPreferences.PRESET_CACHES]
        ?.let { runCatching { json.decodeFromString<List<PresetCache>>(it) }.getOrNull() }
        ?.find { it.address == address }

/** Makes [device] the current one and moves it to the top of the saved list. */
suspend fun Context.rememberDevice(device: ScannedDevice) {
    wledDataStore.edit { prefs ->
        val previous = prefs[WledPreferences.DEVICE_ADDRESS]
        prefs[WledPreferences.DEVICE_ADDRESS] = device.address
        prefs[WledPreferences.DEVICE_NAME] = device.name
        prefs[WledPreferences.SAVED_DEVICES] = json.encodeToString(
            listOf(device) + prefs.savedDevices().filter { it.address != device.address }
        )
        if (previous != device.address) clearLiveState(prefs)
    }
}

suspend fun Context.forgetDevice(address: String) {
    wledDataStore.edit { prefs ->
        val remaining = prefs.savedDevices().filter { it.address != address }
        prefs[WledPreferences.SAVED_DEVICES] = json.encodeToString(remaining)
        val caches = prefs[WledPreferences.PRESET_CACHES]
            ?.let { runCatching { json.decodeFromString<List<PresetCache>>(it) }.getOrNull() }
            ?: emptyList()
        prefs[WledPreferences.PRESET_CACHES] = json.encodeToString(caches.filter { it.address != address })
        if (prefs[WledPreferences.DEVICE_ADDRESS] == address) {
            val next = remaining.firstOrNull()
            if (next != null) {
                prefs[WledPreferences.DEVICE_ADDRESS] = next.address
                prefs[WledPreferences.DEVICE_NAME] = next.name
            } else {
                prefs.remove(WledPreferences.DEVICE_ADDRESS)
                prefs.remove(WledPreferences.DEVICE_NAME)
            }
            clearLiveState(prefs)
        }
    }
}

suspend fun Context.savePresetCache(cache: PresetCache) {
    wledDataStore.edit { prefs ->
        val caches = prefs[WledPreferences.PRESET_CACHES]
            ?.let { runCatching { json.decodeFromString<List<PresetCache>>(it) }.getOrNull() }
            ?: emptyList()
        prefs[WledPreferences.PRESET_CACHES] =
            json.encodeToString(listOf(cache) + caches.filter { it.address != cache.address })
    }
}

suspend fun Context.loadPresetCache(address: String): PresetCache? =
    wledDataStore.data.first().presetCache(address)

/** Favourite presets of the current device, for tile buttons. */
fun Preferences.currentFavourites(): List<Preset> {
    val address = this[WledPreferences.DEVICE_ADDRESS] ?: return emptyList()
    return presetCache(address)?.presets?.filter { it.favourite } ?: emptyList()
}

private fun clearLiveState(prefs: androidx.datastore.preferences.core.MutablePreferences) {
    prefs[WledPreferences.IS_CONNECTED] = false
    prefs.remove(WledPreferences.IS_POWER_ON)
    prefs.remove(WledPreferences.BRIGHTNESS)
    prefs.remove(WledPreferences.ACTIVE_PRESET_NAME)
    prefs.remove(WledPreferences.NIGHTLIGHT_END_MS)
    prefs.remove(WledPreferences.STATE_UPDATED_MS)
    prefs.remove(WledPreferences.CONNECT_FAILED)
    prefs.remove(WledPreferences.NEEDS_PAIRING)
}
