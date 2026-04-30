package com.jpchurchouse.wledblewear.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    @SerialName("id") val id: Int,
    @SerialName("n")  val name: String,
)
