package com.example.wledble.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON schema returned by the Available Presets characteristic:
 *   [{"id":1,"n":"Rainbow"},{"id":2,"n":"Fire"},…]
 */
@Serializable
data class Preset(
    val id: Int,
    @SerialName("n") val name: String
)