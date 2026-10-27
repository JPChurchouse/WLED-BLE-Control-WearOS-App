package com.jpchurchouse.wledblewear.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val id: Int,
    val name: String,
    /** WLED's quick-load label (often an emoji); presets with one are favourites. */
    val quickLoad: String = "",
    val playlist: Boolean = false,
    val favourite: Boolean = false,
) {
    /** Short label for tile buttons: the quick-load label, else the name's initials. */
    val shortLabel: String
        get() = quickLoad.ifBlank {
            name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
        }
}

/** Cached preset list for one device, keyed by the device's list revision. */
@Serializable
data class PresetCache(
    val address: String,
    val revision: Int,
    val presets: List<Preset>,
)
