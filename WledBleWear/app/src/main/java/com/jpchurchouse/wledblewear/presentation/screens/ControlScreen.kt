package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.BorderStroke             // ← for chipBorder fix
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams    // ← explicit: foundation.lazy
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.WledUiState
import com.jpchurchouse.wledblewear.presentation.theme.WledActiveChipBg
import com.jpchurchouse.wledblewear.presentation.theme.WledCyan

@Composable
fun ControlScreen(
    uiState: WledUiState,
    onTogglePower: () -> Unit,
    onActivatePreset: (Int) -> Unit,
    onDisconnect: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText          = { TimeText() },
        vignette          = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier            = Modifier.fillMaxSize(),
            state               = listState,
            autoCentering       = AutoCenteringParams(itemIndex = 0),
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            item {
                val (dotColor, statusText) = when (val cs = uiState.connectionState) {
                    is ConnectionState.Connected    -> Color(0xFF4CAF50) to "Connected"
                    is ConnectionState.Reconnecting -> Color(0xFFFFB300) to "Reconnecting… (${cs.attempt})"
                    else                            -> Color(0xFFCF6679) to "Disconnected"
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(dotColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, style = MaterialTheme.typography.caption1)
                }
            }

            item {
                ToggleChip(
                    checked         = uiState.isPowered,
                    onCheckedChange = { onTogglePower() },
                    label           = {
                        Text(
                            if (uiState.isPowered) stringResource(R.string.power_on)
                            else stringResource(R.string.power_off)
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector        = ToggleChipDefaults.switchIcon(uiState.isPowered),
                            contentDescription = if (uiState.isPowered) "On" else "Off"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                when {
                    uiState.isLoadingPresets -> {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.loading_presets),
                                style = MaterialTheme.typography.caption1
                            )
                        }
                    }
                    uiState.presets.isNotEmpty() -> {
                        Text(
                            text      = stringResource(R.string.presets_header),
                            style     = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                    !uiState.isLoadingPresets
                            && uiState.connectionState is ConnectionState.Connected -> {
                        Text(
                            text      = stringResource(R.string.no_presets),
                            style     = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            items(uiState.presets, key = { it.id }) { preset ->
                val isActive = preset.id == uiState.activePresetId
                Chip(
                    label = {
                        Text(
                            text     = preset.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = if (isActive) WledCyan else MaterialTheme.colors.onSurface
                        )
                    },
                    onClick  = { onActivatePreset(preset.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isActive) {
                        ChipDefaults.chipColors(backgroundColor = WledActiveChipBg)
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    // chipBorder() in 1.4.x takes a BorderStroke, not named color/width params
                    border = if (isActive) {
                        ChipDefaults.chipBorder(
                            borderStroke = BorderStroke(1.5.dp, WledCyan)
                        )
                    } else {
                        ChipDefaults.chipBorder()
                    }
                )
            }

            item {
                Chip(
                    label    = { Text(stringResource(R.string.disconnect)) },
                    onClick  = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}