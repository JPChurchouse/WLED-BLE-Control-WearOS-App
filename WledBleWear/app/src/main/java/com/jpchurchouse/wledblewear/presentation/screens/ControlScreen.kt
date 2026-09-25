package com.jpchurchouse.wledblewear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.jpchurchouse.wledblewear.model.ConnectProblem
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.Preset
import com.jpchurchouse.wledblewear.model.WledUiState
import com.jpchurchouse.wledblewear.presentation.theme.WledGreen
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ControlScreen(viewModel: WledViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            item { Header(state) }

            val problem = state.problem
            when {
                problem != null -> problemItems(problem, viewModel)
                state.connectionState == ConnectionState.Pairing -> item { PairingHint() }
                state.connectionState == ConnectionState.Connected -> controlItems(state, viewModel)
                state.connectionState == ConnectionState.Disconnected -> {
                    item { Status("Reconnecting…") }
                    // Keep the last-known controls visible but disabled.
                    controlItems(state, viewModel, enabled = false)
                }
                else -> item { Status("Connecting…", spinner = true) }
            }

            if (problem == null) {
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        onClick = { viewModel.disconnect() },
                        label = { Text("Disconnect", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.22f),
                            contentColor = MaterialTheme.colors.error,
                        ),
                    )
                }
            }
            state.connectedAddress?.let { address ->
                item {
                    CompactChip(
                        modifier = Modifier.padding(top = 6.dp),
                        onClick = { viewModel.forgetDevice(address) },
                        label = { Text("Forget device") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}

// ── Header / status ───────────────────────────────────────────────────────────

@Composable
private fun Header(state: WledUiState) {
    val dotColor = when (state.connectionState) {
        ConnectionState.Connected -> WledGreen
        ConnectionState.Connecting, ConnectionState.Pairing, ConnectionState.Disconnected ->
            MaterialTheme.colors.primaryVariant
        else -> MaterialTheme.colors.error
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.connectedDeviceName ?: "WLED",
                style = MaterialTheme.typography.title3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.lastError?.let {
            Text(it, style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.error)
        }
    }
}

@Composable
private fun Status(text: String, spinner: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        if (spinner) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.primaryVariant)
    }
}

@Composable
private fun Explain(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun PairingHint() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Status("Pairing…", spinner = true)
        Explain("Enter the passkey from WLED:\nConfig → Usermods → BLEControl")
    }
}

// ── Problems ──────────────────────────────────────────────────────────────────

private fun ScalingLazyListScope.problemItems(problem: ConnectProblem, viewModel: WledViewModel) {
    val text = when (problem) {
        ConnectProblem.PAIRING_WINDOW_CLOSED ->
            "Not paired yet.\nIn WLED open Info and press\nBLE pairing: Enable, then retry."
        ConnectProblem.PAIRING_FAILED ->
            "Pairing failed.\nCheck the passkey and that the\npairing window is still open."
        ConnectProblem.BOND_LOST ->
            "The device no longer knows\nthis watch. Pair it again."
        ConnectProblem.UNSUPPORTED_VERSION ->
            "This device needs a newer\nversion of the app."
        ConnectProblem.NOT_WLED ->
            "No WLED BLE Control service.\nIs the usermod installed?"
        ConnectProblem.UNREACHABLE ->
            "Can't reach the device.\nIs it on and nearby?"
    }
    item { Explain(text) }
    item {
        Chip(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (problem == ConnectProblem.BOND_LOST) viewModel.forgetPairingAndRetry() else viewModel.retry()
            },
            label = { Text(if (problem == ConnectProblem.BOND_LOST) "Pair again" else "Retry") },
            colors = ChipDefaults.primaryChipColors(),
        )
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────

private fun ScalingLazyListScope.controlItems(state: WledUiState, viewModel: WledViewModel, enabled: Boolean = true) {
    item {
        ToggleChip(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            checked = state.isPowerOn,
            onCheckedChange = { viewModel.setPower(it) },
            enabled = enabled,
            label = { Text("Power") },
            secondaryLabel = {
                Text(
                    text = if (state.isPowerOn) "On" else "Off",
                    color = if (state.isPowerOn) WledGreen else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            },
            toggleControl = { ToggleChipDefaults.switchIcon(checked = state.isPowerOn) },
            colors = ToggleChipDefaults.toggleChipColors(
                checkedStartBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.25f),
                checkedEndBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.25f),
                checkedToggleControlColor = Color.White,  // v1 lesson: keep the thumb visible
                uncheckedToggleControlColor = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            ),
        )
    }

    item { BrightnessSlider(state.brightness, enabled) { viewModel.setBrightness(it) } }

    item { NightlightControls(state, enabled, viewModel) }

    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        ) {
            CompactButton(onClick = { viewModel.previousPreset() }, enabled = enabled && state.presets.isNotEmpty()) {
                Text("◀")
            }
            Text("Presets", style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            CompactButton(onClick = { viewModel.nextPreset() }, enabled = enabled && state.presets.isNotEmpty()) {
                Text("▶")
            }
        }
    }

    if (state.presets.isEmpty()) {
        item { Explain(if (state.connectionState == ConnectionState.Connected) "No presets saved in WLED" else "") }
    }

    // Favourites (presets with a quick-load label) first.
    val sorted = state.presets.sortedByDescending { it.favourite }
    items(sorted, key = { it.id }) { preset ->
        PresetChip(preset, active = preset.id == state.activePresetId || preset.id == state.activePlaylistId,
            enabled = enabled) { viewModel.activatePreset(preset.id) }
    }
}

@Composable
private fun BrightnessSlider(brightness: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    // Local value so the slider moves immediately; the device's notification then confirms it.
    var local by remember { mutableFloatStateOf(brightness.toFloat()) }
    LaunchedEffect(brightness) { local = brightness.toFloat() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            "Brightness ${(local / 255f * 100).roundToInt()}%",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        InlineSlider(
            value = local,
            onValueChange = { v ->
                local = v
                onChange(v.roundToInt().coerceIn(1, 255))
            },
            increaseIcon = { Icon(InlineSliderDefaults.Increase, "Brighter") },
            decreaseIcon = { Icon(InlineSliderDefaults.Decrease, "Dimmer") },
            valueRange = 1f..255f,
            steps = 15,
            segmented = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NightlightControls(state: WledUiState, enabled: Boolean, viewModel: WledViewModel) {
    // Tick so the countdown stays current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.nightlightActive) {
        while (state.nightlightActive) { now = System.currentTimeMillis(); delay(15_000) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
        if (state.nightlightActive) {
            val minutes = ((state.nightlightEndMs - now).coerceAtLeast(0) + 59_999) / 60_000
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.cancelNightlight() },
                enabled = enabled,
                label = { Text("Sleep timer: $minutes min") },
                secondaryLabel = { Text("Tap to cancel") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        } else {
            Text("Sleep timer", style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    CompactChip(
                        onClick = { viewModel.startNightlight(m) },
                        enabled = enabled,
                        label = { Text("${m}m") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(preset: Preset, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tags = buildList {
        if (active) add("Active")
        if (preset.playlist) add("Playlist")
        if (preset.favourite) add("★")
    }.joinToString(" · ")
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        onClick = onClick,
        enabled = enabled,
        label = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (tags.isNotEmpty()) {
            { Text(tags, style = MaterialTheme.typography.caption2) }
        } else null,
        icon = {
            if (preset.quickLoad.isNotBlank()) {
                Text(preset.quickLoad, maxLines = 1)
            } else {
                Box(
                    Modifier.size(8.dp).background(
                        if (active) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                        CircleShape,
                    )
                )
            }
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = if (active) MaterialTheme.colors.primary.copy(alpha = 0.22f) else MaterialTheme.colors.surface,
        ),
    )
}
