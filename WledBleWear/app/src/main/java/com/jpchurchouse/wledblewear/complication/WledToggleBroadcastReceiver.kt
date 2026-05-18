package com.jpchurchouse.wledblewear.complication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the complication #3 tap broadcast and starts WledToggleForegroundService.
 *
 * The receiver must be registered in AndroidManifest.xml:
 *
 *   <receiver
 *       android:name=".complication.WledToggleBroadcastReceiver"
 *       android:exported="false"/>
 *
 * We use a BroadcastReceiver → startForegroundService() indirection because
 * complication tap actions can only fire PendingIntents, and a foreground service
 * must be started before BLE work begins (WearOS background BLE is unreliable).
 */
class WledToggleBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(
            Intent(context, WledToggleForegroundService::class.java)
        )
    }
}
