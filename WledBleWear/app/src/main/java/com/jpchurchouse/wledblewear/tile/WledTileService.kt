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
 * Layout states (in priority order):
 *
 *   1. NO_DEVICE      — DataStore has no DEVICE_ADDRESS.
 *                       Shows "Open app to connect."
 *
 *   2. CONNECT_FAILED — A recent tile-initiated connect attempt timed out.
 *                       Shows device name in red + "Connection failed" + "Try Again" chip.
 *                       Clears on next successful connect or next attempt.
 *
 *   3. CONNECTED      — IS_CONNECTED = true.
 *                       Shows device name in green, live power state, active preset name.
 *                       "Open App" chip brings the running app to the foreground.
 *
 *   4. STALE          — IS_CONNECTED = false, but IS_POWER_ON / ACTIVE_PRESET_NAME exist
 *                       (involuntary drop; app was killed with device live).
 *                       Shows device name in amber, "Last known:" prefix, dimmed state.
 *                       "Open App" chip triggers a reconnect attempt via MainActivity.
 *
 *   5. DISCONNECTED   — IS_CONNECTED = false and no stale power data
 *                       (user explicitly disconnected).
 *                       Shows device name only + "Open App" chip.
 *
 * All interactive chips use LaunchAction → MainActivity.  The tile never touches BLE.
 * CONNECT_FAILED is written by MainActivity after a timed-out reconnect attempt.
 * WledApplication.startStateSync() clears it on the next successful connect.
 *
 * runBlocking on a single DataStore .data.first() is safe on TileService's background
 * executor — the same pattern used by the original tile.
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

        val deviceAddress   = prefs[WledPreferences.DEVICE_ADDRESS]
        val deviceName      = prefs[WledPreferences.DEVICE_NAME]
        val isConnected     = prefs[WledPreferences.IS_CONNECTED] ?: false
        val isPowerOn       = prefs[WledPreferences.IS_POWER_ON]        // null = no stale data
        val activePreset    = prefs[WledPreferences.ACTIVE_PRESET_NAME] // null = no stale data
        val connectFailed   = prefs[WledPreferences.CONNECT_FAILED] ?: false

        val layout = when {
            deviceAddress == null -> buildNoDeviceLayout()
            connectFailed        -> buildFailedLayout(deviceName ?: "WLED")
            isConnected          -> buildConnectedLayout(deviceName ?: "WLED", isPowerOn ?: false, activePreset)
            isPowerOn != null    -> buildStaleLayout(deviceName ?: "WLED", isPowerOn, activePreset)
            else                 -> buildDisconnectedLayout(deviceName ?: "WLED")
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    // ── Layout builders ───────────────────────────────────────────────────────

    /** State 1: no device has ever been configured. */
    private fun buildNoDeviceLayout(): LayoutElement {
        val params = defaultDeviceParams()
        return PrimaryLayout.Builder(params)
            .setContent(
                Text.Builder(this, "Open app\nto connect")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_DIM))
                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                    .build()
            )
            .setPrimaryChipContent(
                CompactChip.Builder(this, "Open App", clickable(openAppAction()), params).build()
            )
            .build()
    }

    /**
     * State 2: a tile-initiated connect attempt timed out.
     * Shows the device name in red + a "Try Again" chip that re-attempts.
     * CONNECT_FAILED is cleared by WledApplication when connection next succeeds.
     */
    private fun buildFailedLayout(deviceName: String): LayoutElement {
        val params = defaultDeviceParams()
        return PrimaryLayout.Builder(params)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "✕ $deviceName")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_ERROR))
                    .build()
            )
            .setContent(
                Text.Builder(this, "Connection\nfailed")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_DIM))
                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                    .build()
            )
            .setPrimaryChipContent(
                CompactChip.Builder(this, "Try Again", clickable(connectLcdAction()), params).build()
            )
            .build()
    }

    /**
     * State 3: BLE reports Connected.
     * Shows live power and preset state.  "Open App" brings the activity to the foreground.
     */
    private fun buildConnectedLayout(
        deviceName: String,
        isPowerOn: Boolean,
        activePreset: String?,
    ): LayoutElement {
        val params = defaultDeviceParams()
        val col = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, if (isPowerOn) "Power: On" else "Power: Off")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(if (isPowerOn) COLOR_GREEN else COLOR_DIM))
                    .build()
            )

        if (activePreset != null) {
            col.addContent(
                Text.Builder(this, activePreset)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_LIGHT))
                    .build()
            )
        }

        return PrimaryLayout.Builder(params)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "● $deviceName")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_GREEN))
                    .build()
            )
            .setContent(col.build())
            .setPrimaryChipContent(
                CompactChip.Builder(this, "Open App", clickable(connectLcdAction()), params).build()
            )
            .build()
    }

    /**
     * State 4: disconnected but stale IS_POWER_ON data exists (involuntary drop).
     * Shows device name in amber with "Last known:" prefix on content to signal staleness.
     * "Open App" sends connect_lcd so MainActivity will attempt reconnect.
     */
    private fun buildStaleLayout(
        deviceName: String,
        isPowerOn: Boolean,
        activePreset: String?,
    ): LayoutElement {
        val params = defaultDeviceParams()
        val col = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "Last known:")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_DIM))
                    .build()
            )
            .addContent(
                Text.Builder(this, if (isPowerOn) "Power: On" else "Power: Off")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(if (isPowerOn) COLOR_GREEN_DIM else COLOR_DIM))
                    .build()
            )

        if (activePreset != null) {
            col.addContent(
                Text.Builder(this, activePreset)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_DIM))
                    .build()
            )
        }

        return PrimaryLayout.Builder(params)
            .setPrimaryLabelTextContent(
                // Amber circle signals "was connected, now stale"
                Text.Builder(this, "◌ $deviceName")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_AMBER))
                    .build()
            )
            .setContent(col.build())
            .setPrimaryChipContent(
                CompactChip.Builder(this, "Open App", clickable(connectLcdAction()), params).build()
            )
            .build()
    }

    /**
     * State 5: user explicitly disconnected — no stale data.
     * Shows device name only; "Open App" starts the normal scan → connect flow.
     */
    private fun buildDisconnectedLayout(deviceName: String): LayoutElement {
        val params = defaultDeviceParams()
        return PrimaryLayout.Builder(params)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "○ $deviceName")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_DIM))
                    .build()
            )
            .setContent(
                Text.Builder(this, "Not connected")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_DIM))
                    .build()
            )
            .setPrimaryChipContent(
                CompactChip.Builder(this, "Open App", clickable(openAppAction()), params).build()
            )
            .build()
    }

    // ── Action / Clickable helpers ────────────────────────────────────────────

    private fun clickable(action: ActionBuilders.Action): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setOnClick(action)
            .build()

    /**
     * Launches MainActivity with EXTRA_TILE_COMMAND = "connect_lcd".
     * MainActivity will attempt to reconnect to the last known device (3 attempts / 10s),
     * navigate to ControlScreen on success, or write CONNECT_FAILED on timeout.
     */
    private fun connectLcdAction(): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        EXTRA_TILE_COMMAND,
                        ActionBuilders.AndroidStringExtra.Builder()
                            .setValue(CMD_CONNECT_LCD)
                            .build()
                    )
                    .build()
            )
            .build()

    /** Launches MainActivity with no command — surfaces the running app. */
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

    private fun defaultDeviceParams() =
        androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2.0f)
            .setScreenShape(
                androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
            )
            .build()

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val CMD_CONNECT_LCD = "connect_lcd"

        // Tile colour palette (ARGB int literals)
        private val COLOR_GREEN     = 0xFF4CAF50.toInt()   // connected / power on
        private val COLOR_GREEN_DIM = 0xFF2E7D32.toInt()   // stale power-on
        private val COLOR_AMBER     = 0xFFFFC107.toInt()   // stale device name
        private val COLOR_ERROR     = 0xFFCF6679.toInt()   // connection failed
        private val COLOR_LIGHT     = 0xFFE0E0E0.toInt()   // general content text
        private val COLOR_DIM       = 0xFFAAAAAA.toInt()   // secondary / disabled text
    }
}
