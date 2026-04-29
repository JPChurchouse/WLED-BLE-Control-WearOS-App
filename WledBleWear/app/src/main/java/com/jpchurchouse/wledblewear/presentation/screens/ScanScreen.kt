package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams     // ← explicit: foundation.lazy
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.ScannedDevice
import com.jpchurchouse.wledblewear.model.WledUiState

@Composable
fun ScanScreen(
    uiState: WledUiState,
    onStartScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit
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
                Text(
                    text      = stringResource(R.string.scan_title),
                    style     = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }

            item {
                when (val cs = uiState.connectionState) {
                    is ConnectionState.Scanning -> {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.scanning),
                                style = MaterialTheme.typography.caption1
                            )
                        }
                    }
                    is ConnectionState.Connecting -> {
                        Text(
                            text      = stringResource(R.string.connecting),
                            style     = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                    is ConnectionState.Error -> {
                        Text(
                            text      = cs.message,
                            color     = MaterialTheme.colors.error,
                            style     = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
            }

            items(uiState.scanResults, key = { it.address }) { device ->
                Chip(
                    label = {
                        Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(device.address, style = MaterialTheme.typography.caption2)
                    },
                    onClick  = { onConnect(device) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ChipDefaults.primaryChipColors()
                )
            }

            item {
                val isScanning = uiState.connectionState is ConnectionState.Scanning
                        || uiState.connectionState is ConnectionState.Connecting
                if (!isScanning) {
                    Chip(
                        label    = { Text(stringResource(R.string.scan_again)) },
                        onClick  = onStartScan,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}