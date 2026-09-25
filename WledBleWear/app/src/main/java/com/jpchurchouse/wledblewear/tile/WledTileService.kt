package com.jpchurchouse.wledblewear.tile

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.jpchurchouse.wledblewear.EXTRA_OPEN_CURRENT
import com.jpchurchouse.wledblewear.MainActivity
import com.jpchurchouse.wledblewear.R
import com.jpchurchouse.wledblewear.action.ActionActivity
import com.jpchurchouse.wledblewear.action.Actions
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.currentFavourites
import com.jpchurchouse.wledblewear.data.wledDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * WLED tile: status of the current device plus one-tap buttons (power, previous, next and
 * up to two favourite presets). Buttons launch ActionActivity → ActionService, which does
 * the BLE work in the background; the tile never touches BLE and reads only DataStore.
 */
class WledTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()
        try {
            future.set(buildTile(requestParams.deviceConfiguration))
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        ICONS.forEach { (id, res) ->
            resources.addIdToImageMapping(id,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build()
                    )
                    .build())
        }
        future.set(resources.build())
        return future
    }

    private fun buildTile(device: DeviceParameters): TileBuilders.Tile {
        // runBlocking on one DataStore read is fine on the tile's background executor.
        val prefs = runBlocking { applicationContext.wledDataStore.data.first() }
        val name = prefs[WledPreferences.DEVICE_NAME] ?: "WLED"

        val layout = when {
            prefs[WledPreferences.DEVICE_ADDRESS] == null -> messageLayout(device, null, "Open the app\nto add a device")
            prefs[WledPreferences.NEEDS_PAIRING] != null ->
                messageLayout(device, "✕ $name", "Needs pairing.\nOpen the app.", COLOR_ERROR)
            else -> controlLayout(device, name, prefs)
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    // ── Layouts ───────────────────────────────────────────────────────────────

    private fun messageLayout(device: DeviceParameters, title: String?, message: String, titleColor: Int = COLOR_DIM): LayoutElement {
        val builder = PrimaryLayout.Builder(device)
            .setContent(
                Text.Builder(this, message)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_DIM))
                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                    .setMaxLines(3)
                    .build()
            )
            .setPrimaryChipContent(openAppChip(device))
        if (title != null) {
            builder.setPrimaryLabelTextContent(
                Text.Builder(this, title).setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(titleColor)).build()
            )
        }
        return builder.build()
    }

    private fun controlLayout(
        device: DeviceParameters,
        name: String,
        prefs: androidx.datastore.preferences.core.Preferences,
    ): LayoutElement {
        val connected = prefs[WledPreferences.IS_CONNECTED] ?: false
        val powerOn = prefs[WledPreferences.IS_POWER_ON]
        val preset = prefs[WledPreferences.ACTIVE_PRESET_NAME]
        val busy = prefs[WledPreferences.ACTION_IN_PROGRESS]
        val failed = prefs[WledPreferences.CONNECT_FAILED] ?: false

        val status = when {
            busy != null -> "$busy…"
            failed -> "Couldn't connect"
            powerOn == null -> "Tap a button to connect"
            else -> buildString {
                if (!connected) append("Last: ")
                append(if (powerOn) "On" else "Off")
                if (powerOn && preset != null) append(" · ").append(preset)
            }
        }
        val statusColor = when {
            failed && busy == null -> COLOR_ERROR
            connected -> COLOR_LIGHT
            else -> COLOR_DIM
        }

        val buttons = MultiButtonLayout.Builder()
            .addButtonContent(
                actionButton(Actions.TOGGLE, "Power",
                    if (powerOn == true) ButtonDefaults.PRIMARY_COLORS else ButtonDefaults.SECONDARY_COLORS) {
                    setIconContent(ICON_POWER)
                }
            )
            .addButtonContent(actionButton(Actions.PREV, "Previous preset") { setIconContent(ICON_PREV) })
            .addButtonContent(actionButton(Actions.NEXT, "Next preset") { setIconContent(ICON_NEXT) })
        prefs.currentFavourites().take(2).forEach { fav ->
            buttons.addButtonContent(actionButton(Actions.preset(fav.id), fav.name) { setTextContent(fav.shortLabel.take(3)) })
        }

        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "${if (connected) "●" else "○"} $name")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(if (connected) COLOR_GREEN else COLOR_LIGHT))
                    .build()
            )
            .setContent(buttons.build())
            .setSecondaryLabelTextContent(
                Text.Builder(this, status)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(statusColor))
                    .build()
            )
            .setPrimaryChipContent(openAppChip(device))
            .build()
    }

    // ── Elements ──────────────────────────────────────────────────────────────

    private fun actionButton(
        action: String,
        description: String,
        colors: androidx.wear.protolayout.material.ButtonColors = ButtonDefaults.SECONDARY_COLORS,
        content: Button.Builder.() -> Unit,
    ): LayoutElement =
        Button.Builder(this, clickable(action, launch(ActionActivity::class.java.name) {
            addKeyToExtraMapping(Actions.EXTRA_ACTION,
                ActionBuilders.AndroidStringExtra.Builder().setValue(action).build())
        }))
            .apply(content)
            .setContentDescription(description)
            .setButtonColors(colors)
            .build()

    private fun openAppChip(device: DeviceParameters) =
        CompactChip.Builder(
            this, "Open",
            clickable("open", launch(MainActivity::class.java.name) {
                addKeyToExtraMapping(EXTRA_OPEN_CURRENT,
                    ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build())
            }),
            device,
        ).build()

    private fun launch(className: String, extras: ActionBuilders.AndroidActivity.Builder.() -> Unit): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(className)
                    .apply(extras)
                    .build()
            )
            .build()

    private fun clickable(id: String, action: ActionBuilders.Action) =
        ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(action).build()

    companion object {
        private const val RESOURCES_VERSION = "3"
        private const val ICON_POWER = "power"
        private const val ICON_PREV = "prev"
        private const val ICON_NEXT = "next"
        private val ICONS = mapOf(
            ICON_POWER to R.drawable.ic_wled_power_on,
            ICON_PREV to R.drawable.ic_wled_prev,
            ICON_NEXT to R.drawable.ic_wled_next,
        )
        private val COLOR_GREEN = 0xFF4CAF50.toInt()
        private val COLOR_ERROR = 0xFFCF6679.toInt()
        private val COLOR_LIGHT = 0xFFE0E0E0.toInt()
        private val COLOR_DIM   = 0xFFAAAAAA.toInt()
    }
}
