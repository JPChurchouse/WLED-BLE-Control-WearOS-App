package com.jpchurchouse.wledblewear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

private val WledColors = Colors(
    primary          = WledOrange,
    primaryVariant   = WledOrangeDim,
    secondary        = WledGreen,
    secondaryVariant = WledGreen,
    background       = WledSurface,
    surface          = WledSurface,
    error            = WledError,
    onPrimary        = WledOnSurface,
    onSecondary      = WledSurface,
    onBackground     = WledOnSurface,
    onSurface        = WledOnSurface,
    onError          = WledOnSurface,
)

@Composable
fun WledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors  = WledColors,
        content = content,
    )
}
