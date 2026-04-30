package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.layout.*
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
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel

// Explicit import to avoid ambiguity with the same-named class in compose.material
// (lesson #2 from the briefing)
@Suppress("unused")
private val autoCenter = AutoCenteringParams::class

@Composable
fun ScanScreen(
    viewModel: WledViewModel,
    onScanStart: () -> Unit,
    onDeviceSelected: (ScannedDevice) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val isScanning  = state.connectionState == ConnectionState.Scanning
    val isConnecting = state.connectionState == ConnectionState.Connecting

    Scaffold(
        timeText    = { TimeText() },
        vignette    = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier            = Modifier.fillMaxSize(),
            state               = listState,
            autoCentering       = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
        ) {
            // Header
            item {
                Text(
                    text      = "Scan for WLED",
                    style     = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(bottom = 8.dp),
                )
            }

            // Scan / stop button
            item {
                Button(
                    onClick  = {
                        if (isScanning) viewModel.stopScan()
                        else onScanStart()
                    },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors   = ButtonDefaults.buttonColors(
                        backgroundColor = if (isScanning)
                            MaterialTheme.colors.error
                        else
                            MaterialTheme.colors.primary
                    ),
                ) {
                    Text(if (isScanning) "Stop" else "Scan")
                }
            }

            // Progress indicator while scanning
            if (isScanning || isConnecting) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(top = 8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = if (isConnecting) "Connecting…" else "Scanning…",
                            style = MaterialTheme.typography.caption2,
                        )
                    }
                }
            }

            // Empty state
            if (state.scannedDevices.isEmpty() && !isScanning) {
                item {
                    Text(
                        text      = "No devices found",
                        style     = MaterialTheme.typography.caption2,
                        color     = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(top = 12.dp),
                    )
                }
            }

            // Device list
            items(state.scannedDevices) { device ->
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick  = { onDeviceSelected(device) },
                    enabled  = !isConnecting,
                    label    = { Text(device.name, maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            text  = device.address,
                            style = MaterialTheme.typography.caption2,
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.surface,
                    ),
                    border = ChipDefaults.chipBorder(),
                )
            }
        }
    }
}
