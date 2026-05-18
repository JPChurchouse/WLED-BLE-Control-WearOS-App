package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

            // ── Reconnecting banner ───────────────────────────────────────────
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
            //
            // FIX: the previous version set checkedToggleControlColor = primary (orange),
            // making the switch thumb invisible against the orange chip background.
            //
            // Fix: set checkedToggleControlColor = Color.White so the thumb contrasts
            // clearly against the chip.  uncheckedToggleControlColor uses a dim onSurface
            // so the off-state thumb is still visible on the dark surface.
            //
            // The secondaryLabel ("On" / "Off") provides an unambiguous text indicator
            // alongside the sliding switch, making the toggle's state obvious at a glance.
            item {
                ToggleChip(
                    modifier        = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    checked         = state.isPowerOn,
                    onCheckedChange = { viewModel.togglePower() },
                    enabled         = isConnected,
                    label           = { Text("Power") },
                    secondaryLabel  = {
                        Text(
                            text  = if (state.isPowerOn) "On" else "Off",
                            style = MaterialTheme.typography.caption2,
                            color = if (state.isPowerOn)
                                WledGreen
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        )
                    },
                    toggleControl = {
                        // switchIcon (lowercase s) is correct in Wear Compose 1.4.x
                        ToggleChipDefaults.switchIcon(checked = state.isPowerOn)
                    },
                    colors = ToggleChipDefaults.toggleChipColors(
                        // Checked background: a gentle orange tint
                        checkedStartBackgroundColor   = MaterialTheme.colors.primary.copy(alpha = 0.25f),
                        checkedEndBackgroundColor     = MaterialTheme.colors.primary.copy(alpha = 0.25f),
                        // White thumb: clearly visible on any background
                        checkedToggleControlColor     = Color.White,
                        // Unchecked background: neutral dark surface
                        uncheckedStartBackgroundColor = MaterialTheme.colors.surface,
                        uncheckedEndBackgroundColor   = MaterialTheme.colors.surface,
                        // Dim thumb: visible but not prominent in off state
                        uncheckedToggleControlColor   = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
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

            // ── Preset items ──────────────────────────────────────────────────
            //
            // Visual differentiation from the power toggle and disconnect button:
            //  • Narrower horizontal padding gives a "list row" feel vs a "button" feel.
            //  • A small coloured bullet in the icon slot acts as a list marker and
            //    doubles as the active-state indicator (orange when active, dim otherwise).
            //  • No border on inactive items, a subtle border on the active one.
            items(state.presets) { preset ->
                val isActive = preset.id == state.activePresetId
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    onClick  = { viewModel.activatePreset(preset.id) },
                    enabled  = isConnected,
                    label    = { Text(preset.name, maxLines = 1) },
                    secondaryLabel = if (isActive) {
                        { Text("Active", style = MaterialTheme.typography.caption2) }
                    } else null,
                    icon = {
                        // Bullet marker: orange when active, dim grey otherwise
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isActive)
                                        MaterialTheme.colors.primary
                                    else
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.30f),
                                    shape = CircleShape,
                                )
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (isActive)
                            MaterialTheme.colors.primary.copy(alpha = 0.18f)
                        else
                            Color.Transparent,
                    ),
                    border = if (isActive) ChipDefaults.chipBorder() else ChipDefaults.chipBorder(),
                )
            }

            // ── Empty preset state ────────────────────────────────────────────
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

            // ── Disconnect button ─────────────────────────────────────────────
            //
            // Placed below the entire preset list so it requires deliberate scrolling to
            // reach — reducing the risk of accidental taps.  Error colour signals that
            // this is a destructive / terminal action.
            item {
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    onClick  = { viewModel.disconnect() },
                    enabled  = true,    // always tappable; backoff-reconnecting user may still want to cancel
                    label    = {
                        Text(
                            text      = "Disconnect",
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.22f),
                        contentColor    = MaterialTheme.colors.error,
                    ),
                    border = ChipDefaults.chipBorder(),
                )
            }
        }
    }
}
