package com.jpchurchouse.wledblewear.model

import kotlinx.serialization.Serializable

/** A WLED device found by scanning or saved for quick access. */
@Serializable
data class ScannedDevice(
    val address: String,
    val name: String,
)
