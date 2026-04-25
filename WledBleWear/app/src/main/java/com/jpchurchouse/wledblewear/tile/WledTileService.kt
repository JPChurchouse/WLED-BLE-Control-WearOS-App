package com.jpchurchouse.wledblewear.tile

import androidx.datastore.preferences.core.Preferences
import androidx.wear.protolayout.ActionBuilders.*
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.*
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.SuspendingTileService
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.jpchurchouse.wledblewear.MainActivity
import com.jpchurchouse.wledblewear.WledApplication
import com.jpchurchouse.wledblewear.data.PreferencesKeys
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.model.Preset
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Tile buttons use [LaunchAction] → [MainActivity] with an Intent extra.
 * This avoids the protolayout DynamicDataValue type-argument complexity
 * while being a fully supported WearOS pattern. MainActivity reads the
 * extra, executes the BLE command via the shared BleManager, and proceeds
 * to the control screen as normal.
 */
class WledTileService : SuspendingTileService() {

    private val bleManager get() = (application as WledApplication).bleManager
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }

    override suspend fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): Tile {
        val prefs = applicationContext.wledDataStore.data.first()

        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                Timeline.Builder()
                    .addTimelineEntry(
                        TimelineEntry.Builder()
                            .setLayout(
                                androidx.wear.protolayout.LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildLayout(prefs, requestParams.deviceConfiguration))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override suspend fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources =
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        TileService.getUpdater(this).requestUpdate(WledTileService::class.java)
    }

    private fun buildLayout(
        prefs: Preferences,
        deviceParams: DeviceParametersBuilders.DeviceParameters
    ): LayoutElement {
        val deviceName      = prefs[PreferencesKeys.DEVICE_NAME] ?: "WLED BLE"
        val isPowered       = prefs[PreferencesKeys.IS_POWERED]  ?: false
        val activePreset    = prefs[PreferencesKeys.ACTIVE_PRESET]
        val presets         = parsePresets(prefs[PreferencesKeys.PRESETS_JSON])
        val hasPairedDevice = prefs[PreferencesKeys.DEVICE_MAC] != null
        val isConnected     = bleManager.state.value.connectionState is ConnectionState.Connected

        val openAppChip = CompactChip.Builder(
            this, "Open App",
            buildLaunchClickable("open_app", null),
            deviceParams
        ).build()

        if (!hasPairedDevice) {
            return PrimaryLayout.Builder(deviceParams)
                .setPrimaryLabelTextContent(
                    Text.Builder(this, "WLED BLE")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(argb(0xFFFFFFFF.toInt()))
                        .build()
                )
                .setContent(
                    Text.Builder(this, "Open app to connect")
                        .setTypography(Typography.TYPOGRAPHY_BODY2)
                        .setColor(argb(0xFFAAAAAA.toInt()))
                        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                        .build()
                )
                .setPrimaryChipContent(openAppChip)
                .build()
        }

        val dotColor = if (isConnected) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
        val titleLabel = Text.Builder(this, "${if (isConnected) "●" else "○"} $deviceName")
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(dotColor))
            .build()

        val multiButton = MultiButtonLayout.Builder()

        multiButton.addButtonContent(
            Button.Builder(this, buildLaunchClickable("pwr", MainActivity.CMD_TOGGLE_POWER))
                .setTextContent(if (isPowered) "ON" else "OFF")
                .setButtonColors(
                    if (isPowered)
                        ButtonColors(argb(0xFF00E5FF.toInt()), argb(0xFF000000.toInt()))
                    else
                        ButtonColors(argb(0xFF2C2C2C.toInt()), argb(0xFFFFFFFF.toInt()))
                )
                .build()
        )

        presets.take(3).forEach { preset ->
            val isActive = preset.id == activePreset
            multiButton.addButtonContent(
                Button.Builder(
                    this,
                    buildLaunchClickable(
                        "pre_${preset.id}",
                        "${MainActivity.CMD_PRESET_PREFIX}${preset.id}"
                    )
                )
                    .setTextContent(preset.name.take(4))
                    .setButtonColors(
                        if (isActive)
                            ButtonColors(argb(0xFFFF8F00.toInt()), argb(0xFF000000.toInt()))
                        else
                            ButtonColors(argb(0xFF2C2C2C.toInt()), argb(0xFFFFFFFF.toInt()))
                    )
                    .build()
            )
        }

        return PrimaryLayout.Builder(deviceParams)
            .setPrimaryLabelTextContent(titleLabel)
            .setContent(multiButton.build())
            .setPrimaryChipContent(openAppChip)
            .build()
    }

    private fun buildLaunchClickable(id: String, cmd: String?): Clickable {
        val activityBuilder = AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName("$packageName.MainActivity")

        if (cmd != null) {
            activityBuilder.addKeyToExtraMapping(
                MainActivity.EXTRA_TILE_COMMAND,
                AndroidIntentExtra.Builder().setStringValue(cmd).build()
            )
        }

        return Clickable.Builder()
            .setId(id)
            .setOnClick(
                LaunchAction.Builder()
                    .setAndroidActivity(activityBuilder.build())
                    .build()
            )
            .build()
    }

    private fun parsePresets(jsonStr: String?): List<Preset> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try { json.decodeFromString<List<Preset>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
}