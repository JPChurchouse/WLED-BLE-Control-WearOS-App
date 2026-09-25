package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// Explicit: compose.material has a same-named class (v1 lesson).
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel

@Composable
fun ScanScreen(
    viewModel: WledViewModel,
    onRescan: () -> Unit,
    onDeviceSelected: (ScannedDevice) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val isScanning = state.connectionState == ConnectionState.Scanning

    DisposableEffect(Unit) { onDispose { viewModel.stopScan() } }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isScanning) "Scanning…" else "Nearby devices",
                        style = MaterialTheme.typography.title3,
                    )
                }
            }

            if (state.scannedDevices.isEmpty() && !isScanning) {
                item {
                    Text(
                        text = "No WLED devices found.\nIs the BLE usermod installed?",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(state.scannedDevices) { device ->
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    onClick = { onDeviceSelected(device) },
                    label = { Text(device.name, maxLines = 1) },
                    secondaryLabel = { Text(device.address, style = MaterialTheme.typography.caption2) },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }

            if (!isScanning) {
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        onClick = onRescan,
                        label = { Text("Scan again") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}
