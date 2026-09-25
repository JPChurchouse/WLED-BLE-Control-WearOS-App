package com.jpchurchouse.wledblewear.action

import android.app.Activity

/**
 * Invisible trampoline for tile buttons and complication taps. Since Android 12, a
 * foreground service may only be started while the app is in the foreground (a broadcast
 * receiver in the background is refused), and ProtoLayout can only launch activities
 * anyway. This translucent activity starts ActionService once resumed, then finishes.
 */
class ActionActivity : Activity() {
    override fun onResume() {
        super.onResume()
        intent.getStringExtra(Actions.EXTRA_ACTION)?.let { ActionService.start(this, it) }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
