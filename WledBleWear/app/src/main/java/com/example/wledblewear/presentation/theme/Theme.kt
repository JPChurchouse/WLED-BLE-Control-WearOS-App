package com.jpchurchouse.wledble.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val WledColors = Colors(
    primary         = WledCyan,
    primaryVariant  = WledCyanDim,
    secondary       = WledAmber,
    background      = WledBackground,
    surface         = WledSurface,
    error           = WledError,
    onPrimary       = WledOnCyan,
    onSecondary     = WledOnDark,
    onBackground    = WledOnDark,
    onSurface       = WledOnDark,
    onError         = WledOnDark
)

@Composable
fun WledBleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors  = WledColors,
        content = content
    )
}