package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.presentation.theme.WledGreen
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel

@Composable
fun ControlScreen(
    viewModel: WledViewModel,
) {
    val state     by viewModel.uiState.collectAsStateWithLifecycle()
    val listState  = rememberScalingLazyListState()

    val isConnected    = state.connectionState == ConnectionState.Connected
    val isReconnecting = state.connectionState == ConnectionState.Disconnected ||
                         state.connectionState == ConnectionState.Connecting

    Scaffold(
        timeText          = { TimeText() },
        vignette          = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier            = Modifier.fillMaxSize(),
            state               = listState,
            autoCentering       = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
        ) {
            // ── Device name + status dot ──────────────────────────────────────
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                ) {
                    val dotColor = when {
                        isConnected    -> WledGreen
                        isReconnecting -> MaterialTheme.colors.primaryVariant
                        else           -> MaterialTheme.colors.error
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(width = 1.dp, color = dotColor, shape = CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = state.connectedDeviceName ?: "—",
                        style = MaterialTheme.typography.title3,
                    )
                }
            }

            // Reconnecting banner
            if (isReconnecting) {
                item {
                    Text(
                        text      = "Reconnecting…",
                        style     = MaterialTheme.typography.caption2,
                        color     = MaterialTheme.colors.primaryVariant,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // ── Power toggle ──────────────────────────────────────────────────
            item {
                ToggleChip(
                    modifier        = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    checked         = state.isPowerOn,
                    onCheckedChange = { viewModel.togglePower() },
                    enabled         = isConnected,
                    label           = { Text("Power") },
                    // switchIcon (lowercase) is the correct API in Wear Compose 1.4.x
                    toggleControl   = {
                        ToggleChipDefaults.switchIcon(checked = state.isPowerOn)
                    },
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.32f),
                        checkedEndBackgroundColor   = MaterialTheme.colors.primary.copy(alpha = 0.32f),
                        checkedToggleControlColor   = MaterialTheme.colors.primary,
                    ),
                )
            }

            // ── Preset list header ────────────────────────────────────────────
            if (state.presets.isNotEmpty()) {
                item {
                    Text(
                        text      = "Presets",
                        style     = MaterialTheme.typography.caption1,
                        color     = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
            }

            // ── Preset chips ──────────────────────────────────────────────────
            items(state.presets) { preset ->
                val isActive = preset.id == state.activePresetId
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick  = { viewModel.activatePreset(preset.id) },
                    enabled  = isConnected,
                    label    = { Text(preset.name, maxLines = 1) },
                    secondaryLabel = if (isActive) {
                        { Text("Active", style = MaterialTheme.typography.caption2) }
                    } else null,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (isActive)
                            MaterialTheme.colors.primary.copy(alpha = 0.28f)
                        else
                            MaterialTheme.colors.surface,
                    ),
                    border = ChipDefaults.chipBorder(),
                )
            }

            // Empty preset state
            if (state.presets.isEmpty() && isConnected) {
                item {
                    Text(
                        text      = "No presets found",
                        style     = MaterialTheme.typography.caption2,
                        color     = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
