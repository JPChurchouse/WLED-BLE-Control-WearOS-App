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
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel

/** Start screen: saved devices for one-tap connect, plus scanning for new ones. */
@Composable
fun DevicesScreen(
    viewModel: WledViewModel,
    onDeviceSelected: (ScannedDevice) -> Unit,
    onScan: () -> Unit,
) {
    val devices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

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
                Text(
                    text = "WLED",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            if (devices.isEmpty()) {
                item {
                    Text(
                        text = "No devices yet.\nScan to find your WLED.",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            items(devices) { device ->
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    onClick = { onDeviceSelected(device) },
                    label = { Text(device.name, maxLines = 1) },
                    secondaryLabel = {
                        Text(device.address, style = MaterialTheme.typography.caption2, maxLines = 1)
                    },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    onClick = onScan,
                    label = { Text("Scan for devices") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}
