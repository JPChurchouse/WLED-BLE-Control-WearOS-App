# WledBleWear

A WearOS app for controlling [WLED](https://kno.wled.ge/) LED controllers over Bluetooth Low Energy (BLE), directly from your wrist — no phone, no Wi-Fi required.

> Tested on a Pixel Watch 2 running WearOS 4 (API 34), against an ESP32 running WLED with a custom BLE GATT server firmware.

---

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [BLE Firmware](#ble-firmware)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Building](#building)
- [Tile](#tile)
- [Complications](#complications)
- [Known Quirks & API Notes](#known-quirks--api-notes)
- [Roadmap](#roadmap)
- [License](#license)

---

## Features

- **Scan & connect** to any WLED device advertising the custom BLE service UUID
- **Power toggle** with clear on/off switch indicator
- **Preset list** — shows all presets stored on the device; tap to activate
- **Auto-reconnect** with exponential backoff (2 → 4 → 8 → 16 → 30 s cap) on involuntary disconnection
- **Explicit disconnect** button at the bottom of the control screen
- **Tile** — glanceable control panel with live connection state, power status, and active preset; tapping reconnects automatically
- **Four watch face complications** ranging from a simple icon launcher to a read-only status display and a background power toggle
- State persisted to DataStore so the tile and complications remain useful when the app is not in the foreground

---

## Screenshots

> _Add screenshots here once the app is running on device._

| Scan screen | Control screen | Tile (connected) | Tile (stale) |
|:-----------:|:--------------:|:----------------:|:------------:|
| _(todo)_ | _(todo)_ | _(todo)_ | _(todo)_ |

---

## Requirements

| Component | Version |
|---|---|
| WearOS | API 26 (Android 8.0) minimum, API 35 target |
| Android Gradle Plugin | 8.6.1 |
| Kotlin | 2.0.21 |
| Wear Compose (Material 2) | 1.4.1 |
| Wear Tiles | 1.4.1 |
| ProtoLayout | 1.2.1 |
| Watchface Complications | 1.2.1 |
| Compose BOM | 2024.09.03 |

The watch must support BLE. No companion phone app is required.

---

## BLE Firmware

WledBleWear expects an ESP32 running WLED with a custom GATT server that exposes the following service and characteristics. The [WLED BLE server firmware](https://github.com/your-repo/wled-ble-firmware) _(link TBD)_ is a separate project.

### Service

| Field | Value |
|---|---|
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c3319100` |

### Characteristics

| Name | UUID suffix | Properties | Format |
|---|---|---|---|
| Power | `...202` | Read / Write / Notify | 1 byte — `0x00` off, `0x01` on |
| Available Presets | `...203` | Read | UTF-8 JSON `[{"id":1,"n":"Name"},…]` |
| Active Preset | `...204` | Read / Write / Notify | 1 byte preset ID, or `0xFF` for none |

### GATT setup sequence

On connection the app executes this chain before marking the device as connected:

1. Request MTU 517
2. Read Available Presets (full characteristic read; Android reassembles fragmented ATT packets)
3. Enable notify on Power characteristic
4. Enable notify on Active Preset characteristic
5. Read initial Power state
6. Emit `ConnectionState.Connected`

All GATT operations are serialised through a `Mutex` with a 5 s per-operation timeout.

---

## Architecture

```
WledApplication
│   Owns BleManager singleton (process lifetime)
│   Runs appScope coroutine — mirrors BLE state → DataStore
│   Requests tile & complication redraws on every BLE state change
│
├── BleManager
│     Single source of truth: StateFlow<WledUiState>
│     All GATT ops on Dispatchers.Main (required by Android BLE stack)
│     Exponential-backoff reconnect loop (cancelled on user disconnect)
│
├── WledViewModel  (AndroidViewModel)
│     Mirrors BleManager.uiState to UI
│     Exposes: connect(), connectToLastDevice(), disconnect(),
│              togglePower(), activatePreset(), startScan(), stopScan()
│
├── MainActivity  (ComponentActivity, singleTop)
│     SwipeDismissableNavHost: "scan" → "control"
│     Auto-navigates on ConnectionState changes
│     Handles tile intent extras (connect_lcd, pwr, pre:<id>)
│     Manages timed connect-LCD attempt (10 s window, 3 attempts)
│
├── WledTileService  (TileService / Java base)
│     Reads DataStore via runBlocking (safe on tile executor)
│     Returns ResolvableFuture<Tile> from concurrent-futures
│     Five layout states: no device, connect failed, connected,
│                         stale (involuntary drop), disconnected
│
└── complication/
      WledIconComplicationService      (#1 — SMALL_IMAGE, icon launcher)
      WledLaunchComplicationService    (#2 — SHORT_TEXT, name + icon launcher)
      WledToggleComplicationService    (#3 — SHORT_TEXT, power state + tap-to-toggle)
      WledStatusComplicationService    (#4 — LONG_TEXT, read-only status)
      WledToggleBroadcastReceiver      (receives tap from #3, starts foreground service)
      WledToggleForegroundService      (connects to LCD + toggles power, no UI)
```

### DataStore keys (`wled_prefs`)

| Key | Type | Written by | Purpose |
|---|---|---|---|
| `device_address` | String | ViewModel on connect | Last connected device MAC |
| `device_name` | String | ViewModel on connect | Last connected device display name |
| `recent_presets` | String | ViewModel on activatePreset | Comma-separated recent preset IDs (max 3) |
| `is_connected` | Boolean | WledApplication.startStateSync | Live connection state for tile/complications |
| `is_power_on` | Boolean | WledApplication.startStateSync | Last known power state |
| `active_preset_name` | String | WledApplication.startStateSync | Last known active preset display name |
| `connect_failed` | Boolean | MainActivity.handleConnectLcd | Set on tile connect timeout; cleared on next success |

**State preservation semantics:**

- **`ConnectionState.Connected`** → all keys written
- **`ConnectionState.Disconnected`** (involuntary drop) → `is_connected` set false; `is_power_on` and `active_preset_name` preserved so tile can show "last known" stale state
- **`ConnectionState.Idle`** (user-initiated disconnect) → `is_connected` set false; `is_power_on` and `active_preset_name` removed entirely

---

## Project Structure

```
app/src/main/
├── java/com/jpchurchouse/wledblewear/
│   ├── ble/
│   │   ├── BleConstants.kt          # UUID constants, timeout values
│   │   └── BleManager.kt            # All GATT logic, StateFlow source of truth
│   ├── complication/
│   │   ├── WledIconComplicationService.kt
│   │   ├── WledLaunchComplicationService.kt
│   │   ├── WledStatusComplicationService.kt
│   │   ├── WledToggleBroadcastReceiver.kt
│   │   ├── WledToggleComplicationService.kt
│   │   └── WledToggleForegroundService.kt
│   ├── data/
│   │   └── WledPreferences.kt       # DataStore keys + wledDataStore extension
│   ├── model/
│   │   ├── ConnectionState.kt       # Sealed class: Idle/Scanning/Connecting/Connected/Disconnected
│   │   ├── Preset.kt                # @Serializable data class {id, name}
│   │   ├── ScannedDevice.kt         # {address, name}
│   │   └── WledUiState.kt           # Snapshot of all observable BLE state
│   ├── presentation/
│   │   ├── screens/
│   │   │   ├── ControlScreen.kt     # Power toggle, preset list, disconnect button
│   │   │   └── ScanScreen.kt        # BLE scan + device list
│   │   └── theme/
│   │       ├── Color.kt             # WledOrange, WledGreen, WledSurface, etc.
│   │       └── Theme.kt             # WledTheme (Wear Compose Material 2)
│   ├── tile/
│   │   └── WledTileService.kt       # ProtoLayout tile, five display states
│   ├── viewmodel/
│   │   └── WledViewModel.kt
│   ├── MainActivity.kt
│   └── WledApplication.kt           # BleManager singleton + appScope state sync
├── res/
│   ├── drawable/
│   │   ├── ic_wled_complication.xml  # Lightbulb outline (neutral)
│   │   ├── ic_wled_power_on.xml      # Filled power button
│   │   └── ic_wled_power_off.xml     # Outline power button
│   └── values/
│       └── strings.xml
└── AndroidManifest.xml
```

---

## Building

### Prerequisites

- Android Studio Ladybug or newer
- A physical WearOS device or emulator (BLE scanning does not work on the emulator)
- An ESP32 flashed with the WLED BLE server firmware

### Debug build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release build

Create a `local.properties` file in the project root (not committed to source control):

```properties
keystore.path=/absolute/path/to/your.keystore
keystore.password=yourKeystorePassword
keystore.alias=yourKeyAlias
keystore.aliasPassword=yourKeyPassword
```

Then:

```bash
./gradlew bundleRelease
```

The signed AAB will be at `app/build/outputs/bundle/release/app-release.aab`.

---

## Tile

The tile provides a glanceable WLED control surface without opening the app. Add it via the WearOS tile carousel (long-press the watch face → swipe to tiles → add).

### Display states

| State | Trigger | Display |
|---|---|---|
| **No device** | No device has ever been connected | "Open app to connect" |
| **Connected** | `IS_CONNECTED = true` in DataStore | Device name (green ●), live power state, active preset name |
| **Stale** | Involuntary disconnect with cached state | Device name (amber ◌), "Last known:" prefix, dimmed state |
| **Disconnected** | User explicitly disconnected | Device name (dim ○), "Not connected" |
| **Connect failed** | Tile-initiated connect timed out | Device name (red ✕), "Connection failed", "Try Again" chip |

### Tap behaviour

All interactive chips send a `LaunchAction` to `MainActivity` via `EXTRA_TILE_COMMAND`. The `connect_lcd` command triggers a 10-second connection window (covering up to 3 backoff attempts at t=0 s, t+2 s, t+6 s). On success the app navigates to the control screen; on timeout it writes `CONNECT_FAILED` and requests a tile redraw to show the failure state.

---

## Complications

Four watch face complications are available. Add them by long-pressing your watch face and selecting the complication slot.

### #1 — WLED Icon (`SMALL_IMAGE`)

A compact lightbulb icon. Fits any small complication slot. Tap to connect to the last device and open the control screen.

### #2 — WLED Device (`SHORT_TEXT`, `SMALL_IMAGE`)

Shows the last-connected device name next to the icon. Falls back to "WLED BLE Wear" if no device has been configured. Same tap behaviour as #1.

### #3 — WLED Power (`SHORT_TEXT`)

Displays "On" or "Off" with a filled/outline power icon reflecting the cached state. Tap to toggle power in the background — the app connects silently via a foreground service if needed, with no UI shown. On timeout the service disconnects cleanly and the complication reverts to its last state.

### #4 — WLED Status (`LONG_TEXT`)

Read-only. Title shows the device name; body shows the active preset name when on, "Power Off" when connected but off, "Last: On/Off" for stale state, or "Disconnected". No tap action — purely informational. Ideal for persistent at-a-glance placement on a watch face.

### Complication update behaviour

All complications are updated automatically via `ComplicationDataSourceUpdateRequester.requestUpdateAll()` called from `WledApplication.startStateSync()` on every BLE state change. `UPDATE_PERIOD_SECONDS` is set to `0` in the manifest for all complications — polling is never used.

---

## Known Quirks & API Notes

These are non-obvious constraints discovered during development. They are recorded here to save future pain.

| Area | Quirk |
|---|---|
| `build.gradle.kts` | `kotlin { compilerOptions { jvmTarget } }` must be at **top level**, not inside `android {}`. Placing it inside `android {}` causes an unresolved-reference cascade in AGP 8.6.x / Kotlin 2.x. |
| Wear Compose | `AutoCenteringParams` must be imported from `androidx.wear.compose.foundation.lazy`, not `compose.material`. |
| Wear Compose | `ChipDefaults.chipBorder()` takes **no arguments** in Wear Compose 1.4.x. |
| Wear Compose | `ToggleChipDefaults.switchIcon(checked)` is lowercase `s` in 1.4.x. |
| Wear Compose | `ToggleChip` switch thumb is invisible if `checkedToggleControlColor` matches the chip background. Use `Color.White` for the thumb colour. |
| Tiles | `WledTileService.onResourcesRequest` must return `androidx.wear.tiles.ResourceBuilders.Resources` (the **tiles** package), not the protolayout one. |
| ProtoLayout | `CompactChip.Builder` takes a `ModifiersBuilders.Clickable` as its second argument, not an `ActionBuilders.Action` directly. Wrap via `ModifiersBuilders.Clickable.Builder().setOnClick(action).build()`. |
| ProtoLayout | `ButtonDefaults.PRIMARY_BUTTON_COLORS` and `EXTRA_SMALL_SIZE` do not exist in protolayout-material 1.2.x. Use `CompactChip` throughout the tile instead. |
| Tiles | `TileService` (Java base class) requires `ListenableFuture` returns. Use `ResolvableFuture.create<T>()` from `androidx.concurrent:concurrent-futures`. |
| BLE | Do **not** call `BleManager.cleanup()` from `ViewModel.onCleared()`. The GATT connection must outlive the ViewModel so the tile and complications can operate without re-connecting. |
| BLE | `disconnect()` must set `savedDevice = null` before emitting `Idle`; otherwise `handleDisconnect()` immediately re-schedules the reconnect backoff. |
| Complications | `ComplicationDataSourceUpdateRequester` must be called from the application scope (`appScope`), not from a ViewModel scope, so tile/complication refreshes continue when MainActivity is destroyed. |
| Foreground service | `foregroundServiceType="connectedDevice"` is required on API 34+ for BLE GATT operations in a foreground service. Declare `FOREGROUND_SERVICE_CONNECTED_DEVICE` in the manifest alongside `FOREGROUND_SERVICE`. |

---

## Roadmap

- [ ] Brightness slider in the control screen
- [ ] Colour picker (RGB) via a custom BLE characteristic
- [ ] Preset name display in complications (currently shows cached name from last connection)
- [ ] Multiple saved devices
- [ ] WearOS health-services integration (e.g. auto-toggle on workout start)

---

## License

```
MIT License

Copyright (c) 2025 J. P. Churchouse

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
