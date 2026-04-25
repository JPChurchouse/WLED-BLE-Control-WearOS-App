package com.jpchurchouse.wledblewear

import android.app.Application
import com.jpchurchouse.wledblewear.ble.BleManager

class WledApplication : Application() {
    val bleManager: BleManager by lazy { BleManager(this) }
}