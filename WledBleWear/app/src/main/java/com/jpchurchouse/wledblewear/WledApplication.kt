package com.jpchurchouse.wledblewear

import android.app.Application
import com.jpchurchouse.wledblewear.ble.BleManager

/**
 * Application subclass — owns the BleManager singleton.
 *
 * The singleton must outlive any individual ViewModel so that:
 *  - an active GATT connection persists across config changes, and
 *  - the tile service can interact with BLE state without launching the full activity.
 */
class WledApplication : Application() {

    lateinit var bleManager: BleManager
        private set

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        bleManager.cleanup()
    }
}
