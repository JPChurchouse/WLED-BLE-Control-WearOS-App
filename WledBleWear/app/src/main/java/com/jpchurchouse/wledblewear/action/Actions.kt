package com.jpchurchouse.wledblewear.action

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jpchurchouse.wledblewear.ble.Protocol

/**
 * One-tap actions run by ActionService for tiles and complications. Encoded as strings so
 * they fit in intent extras and ProtoLayout LaunchAction extras.
 */
object Actions {
    const val EXTRA_ACTION = "wled_action"

    const val TOGGLE         = "toggle"
    const val NEXT           = "next"
    const val PREV           = "prev"
    const val NEXT_FAVOURITE = "next_fav"
    const val OFF_DISCONNECT = "off_disconnect"
    private const val PRESET_PREFIX = "preset:"

    fun preset(id: Int) = "$PRESET_PREFIX$id"

    fun command(action: String): ByteArray? = when {
        action == TOGGLE         -> Protocol.Cmd.powerToggle()
        action == NEXT           -> Protocol.Cmd.next()
        action == PREV           -> Protocol.Cmd.prev()
        action == NEXT_FAVOURITE -> Protocol.Cmd.next(favourites = true)
        action == OFF_DISCONNECT -> Protocol.Cmd.offAndDisconnect()
        action.startsWith(PRESET_PREFIX) ->
            action.removePrefix(PRESET_PREFIX).toIntOrNull()?.let(Protocol.Cmd::preset)
        else -> null
    }

    /** Short label shown on the tile while the action runs. */
    fun label(action: String): String = when {
        action == TOGGLE         -> "Power"
        action == NEXT || action == NEXT_FAVOURITE -> "Next"
        action == PREV           -> "Previous"
        action == OFF_DISCONNECT -> "Turning off"
        action.startsWith(PRESET_PREFIX) -> "Preset"
        else -> "Working"
    }

    /**
     * PendingIntent for complication tap actions. It launches ActionActivity rather than
     * broadcasting: since Android 12 a receiver in the background may not start a
     * foreground service, but a user-launched activity may.
     */
    fun pendingAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, ActionActivity::class.java)
                .putExtra(EXTRA_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
