package com.jpchurchouse.wledblewear.tile

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.jpchurchouse.wledblewear.EXTRA_TILE_COMMAND
import com.jpchurchouse.wledblewear.MainActivity
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * WledTileService — glanceable WLED control surface.
 *
 * Extends TileService (Java base class); returns ListenableFuture via ResolvableFuture
 * from androidx.concurrent:concurrent-futures.  onTileRequest runs on TileService's own
 * background executor — runBlocking on a single DataStore read is safe here.
 *
 * All interactions use LaunchAction → MainActivity with EXTRA_TILE_COMMAND.
 * The tile never touches BLE; it reads DataStore for display state.
 *
 * Layout (round 192dp screen):
 *   PrimaryLayout
 *     ├── primaryLabel : "● DeviceName"  (green)
 *     ├── Column of CompactChips : [PWR] [P1] [P2] [P3]
 *     └── bottom chip  : "Open App"
 *
 * CompactChip is used for all interactive elements — it has a stable, simple API in
 * protolayout-material 1.2.x.  Button / ButtonDefaults are avoided because their
 * constant names differ across minor versions.
 */
class WledTileService : TileService() {

    // ── TileService overrides ─────────────────────────────────────────────────

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()
        try {
            future.set(buildTile())
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
        return future
    }

    // ── Tile builder ──────────────────────────────────────────────────────────

    private fun buildTile(): TileBuilders.Tile {
        val prefs = runBlocking { applicationContext.wledDataStore.data.first() }

        val deviceName    = prefs[WledPreferences.DEVICE_NAME]
        val deviceAddress = prefs[WledPreferences.DEVICE_ADDRESS]
        val recentPresets = prefs[WledPreferences.RECENT_PRESETS]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.take(3)
            ?: emptyList()

        val layout = if (deviceAddress == null) buildNoDeviceLayout()
                     else buildControlLayout(deviceName ?: "WLED", recentPresets)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    // ── Layout builders ───────────────────────────────────────────────────────

    private fun buildNoDeviceLayout(): LayoutElement {
        val params = defaultDeviceParams()
        return PrimaryLayout.Builder(params)
            .setContent(
                Text.Builder(this, "Open app\nto connect")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFFAAAAAA.toInt()))
                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                    .build()
            )
            .setPrimaryChipContent(
                CompactChip.Builder(
                    this, "Open App",
                    clickable(openAppAction()),
                    params,
                ).build()
            )
            .build()
    }

    private fun buildControlLayout(
        deviceName: String,
        recentPresets: List<Int>,
    ): LayoutElement {
        val params = defaultDeviceParams()

        // Build a column of compact chips: Power + up to 3 preset shortcuts
        val columnBuilder = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                CompactChip.Builder(
                    this, "Power",
                    clickable(tileCommandAction("pwr")),
                    params,
                ).build()
            )

        recentPresets.forEach { id ->
            columnBuilder.addContent(
                CompactChip.Builder(
                    this, "Preset $id",
                    clickable(tileCommandAction("pre:$id")),
                    params,
                ).build()
            )
        }

        return PrimaryLayout.Builder(params)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "● $deviceName")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFF4CAF50.toInt()))
                    .build()
            )
            .setContent(columnBuilder.build())
            .setPrimaryChipContent(
                CompactChip.Builder(
                    this, "Open App",
                    clickable(openAppAction()),
                    params,
                ).build()
            )
            .build()
    }

    // ── Action / Clickable helpers ────────────────────────────────────────────

    /**
     * Wraps an Action in a Clickable.
     * CompactChip.Builder (and Button.Builder) take Clickable as their second arg,
     * not Action directly.
     */
    private fun clickable(action: ActionBuilders.Action): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setOnClick(action)
            .build()

    private fun tileCommandAction(cmd: String): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        EXTRA_TILE_COMMAND,
                        ActionBuilders.AndroidStringExtra.Builder().setValue(cmd).build()
                    )
                    .build()
            )
            .build()

    private fun openAppAction(): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()

    // ── Device parameters ─────────────────────────────────────────────────────

    /**
     * Static 192×192 round defaults — TileRequest.deviceParameters is nullable in
     * protolayout 1.2.x so we use fixed values rather than risk a null crash.
     */
    private fun defaultDeviceParams() =
        androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2.0f)
            .setScreenShape(
                androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
            )
            .build()
}
