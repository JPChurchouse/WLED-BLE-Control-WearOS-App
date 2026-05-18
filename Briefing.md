# WledBleWear — Fresh Thread Briefing

## Developer Profile
Qualified mechatronics/embedded systems engineer, qualified EMT. Comfortable with low-level concepts, firmware, BLE protocol details, and general programming. New to WearOS/Android development. Setup instructions should be thorough but can assume programming competence.

---

## Project Identity
- **Project name:** `WledBleWear`
- **Namespace / applicationId:** `com.jpchurchouse.wledblewear`
- **Language:** Kotlin
- **Target platform:** WearOS, target API 35, minimum API 26
- **Android SDK location (Windows):** `C:\Users\Jamie\AppData\Local\Android\Sdk`
  - In `local.properties` this must appear as: `sdk.dir=C\:\\Users\\Jamie\\AppData\\Local\\Android\\Sdk`
- **Build system:** Gradle with Kotlin DSL (`.kts` files throughout)
- **Signing keystore:** `C:\Users\Jamie\Git\.keystore\wled-ble-release.keystore`, alias `wled-ble`

---

## Hardware Context
- **BLE peripheral:** ESP32 WROOM-32 (ESP32-D0WDQ5) running WLED 0.15.4 with a custom NimBLE-based usermod
- **BLE service:** Custom, not a standard GATT profile
- **NimBLE advertising:** The firmware must call `NimBLEAdvertising::addServiceUUID()` before advertising so the scan filter works
- **MTU:** Firmware negotiates up to 517 bytes; NimBLE handles ATT fragmentation server-side. The client must perform a full GATT Read (not assume single-packet delivery)

---

## BLE Specification

| Characteristic | UUID | Properties | Format |
|---|---|---|---|
| Service | `4fafc201-1fb5-459e-8fcc-c5c9c3319100` | — | — |
| Power | `4fafc202-1fb5-459e-8fcc-c5c9c3319100` | Read / Write / Notify | 1 byte: `0x00`=off, `0x01`=on |
| Available Presets | `4fafc203-1fb5-459e-8fcc-c5c9c3319100` | Read | UTF-8 JSON: `[{"id":1,"n":"Name"},…]` |
| Active Preset | `4fafc204-1fb5-459e-8fcc-c5c9c3319100` | Read / Write / Notify | 1 byte: `1`–`250`=preset id, `0xFF`=none |

- Device advertises as `WLED-BLE` by default, but name may vary
- CCCD UUID (standard): `00002902-0000-1000-8000-00805f9b34fb`

---

## Application Requirements

### Core App
- Scan for BLE devices filtered by service UUID `4fafc201...`
- Display scan results in a scrollable list; tap to connect
- On connect, run a sequential GATT setup chain:
  1. Request MTU 517
  2. Read Available Presets characteristic (full GATT Read — may span multiple ATT packets)
  3. Enable notify on Power characteristic (write CCCD)
  4. Enable notify on Active Preset characteristic (write CCCD)
  5. Read initial Power state
  6. Mark UI as Connected
- Power toggle: write `0x01`/`0x00`; state updates arrive via notify without polling
- Preset list: scrollable, highlights active preset; tapping writes preset ID as single byte
- Graceful reconnection on drop: exponential backoff (2s → 4s → 8s → 16s → 30s cap)
- Navigation: scan screen → control screen; swipe-right to go back (WearOS native gesture)
- Single-activity architecture using `ComponentActivity` (not `FragmentActivity`)

### WearOS Tile (Quick Access)
- The tile provides a glanceable control surface without opening the full app
- **One-time setup flow:** When the tile is first added, it detects no saved device and displays a prompt to open the app. The user opens the app, scans, and connects normally. On successful connection, the ViewModel persists the device MAC address and display name to DataStore. The tile then automatically uses this saved device on all future interactions — no repeat scanning required.
- **Tile layout (round screen):**
  - Primary label: connection dot (●/○) + device name
  - Button grid: Power toggle button + up to 3 preset buttons (most-recently-active presets)
  - Bottom chip: "Open App" → launches MainActivity
- **Tile interactions:** All buttons use `LaunchAction` → `MainActivity` with an Intent extra (`tile_cmd`). `MainActivity.onNewIntent()` reads this extra and executes the BLE command via the shared `BleManager` singleton
- **State persistence:** `BleManager` is held in `WledApplication` as a singleton, shared between the app and the tile service so an active GATT connection survives ViewModel lifecycle
- **Tile refresh:** Triggered by `TileService.getUpdater().requestUpdate()` from the ViewModel whenever BLE state changes
- **No DataStore state on fresh install:** Tile shows "Open app to connect" prompt gracefully

### Tile Intent Commands (passed as `EXTRA_TILE_COMMAND` string):
- `"pwr"` → toggle power
- `"pre:N"` → activate preset with ID N

---

## Architecture

```
WledApplication          (Application subclass — holds BleManager singleton)
├── BleManager           (all BLE: scan, connect, GATT chain, notify, reconnect)
├── WledViewModel        (thin layer; mirrors BLE state to DataStore; persists device MAC)
├── MainActivity         (single activity; handles tile Intent extras; permission requests)
├── WledTileService      (SuspendingTileService; reads DataStore; builds ProtoLayout)
└── data/WledPreferences (DataStore keys and Context extension property)
```

**Key architectural rules:**
- `BleManager.cleanup()` must NOT be called from `ViewModel.onCleared()` — the GATT connection must outlive the ViewModel so the tile can operate
- `BleManager` is the single source of truth for connection state via `StateFlow<WledUiState>`
- DataStore (`wled_prefs`) is the persistence layer for tile state and device pairing info
- The tile never touches BLE directly; it reads DataStore for display and sends commands via LaunchAction→MainActivity

---

## Library Stack

| Library | Version | Purpose |
|---|---|---|
| AGP | 8.6.1 | Android Gradle Plugin |
| Kotlin | 2.0.21 | Language |
| Compose BOM | 2024.09.03 | Compose version alignment |
| Wear Compose | 1.4.1 | WearOS UI (Material 2) |
| Activity Compose | 1.9.3 | `ComponentActivity.setContent` |
| Lifecycle | 2.8.7 | ViewModel, runtime-compose |
| kotlinx.serialization | 1.7.3 | JSON parsing for preset list |
| kotlinx.coroutines | 1.9.0 | Async/Flow |
| Core Splashscreen | 1.0.1 | WearOS splash screen |
| Wear Tiles | 1.4.1 | Tile service |
| ProtoLayout | 1.2.1 | Tile layout (M2 — matches Wear Compose M2) |
| DataStore Preferences | 1.1.1 | Persistent tile/pairing state |

**Critical:** Do NOT mix Wear Compose Material 2 (`compose-material`) with ProtoLayout Material 3 (`protolayout-material3`). Use `protolayout-material` (M2) to match.

---

## File Tree

```
WledBleWear/
├── settings.gradle.kts
├── build.gradle.kts                          (root — plugin declarations only)
├── gradle.properties                         (useAndroidX, JAVA_HOME, perf flags)
├── local.properties                          (sdk.dir, keystore credentials — NOT in git)
├── gradle/
│   ├── libs.versions.toml                    (version catalog)
│   └── wrapper/
│       ├── gradle-wrapper.jar                (must exist — generate via AS sync)
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts                      (app module — signing, deps)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml
        └── java/com/jpchurchouse/wledblewear/
            ├── MainActivity.kt               (handles tile Intent extras + navigation)
            ├── WledApplication.kt            (Application subclass, BleManager singleton)
            ├── ble/
            │   ├── BleConstants.kt           (UUIDs, MTU, reconnect timing)
            │   └── BleManager.kt             (all BLE logic)
            ├── data/
            │   └── WledPreferences.kt        (DataStore keys + Context extension)
            ├── model/
            │   ├── Preset.kt                 (@Serializable data class)
            │   ├── ScannedDevice.kt          (scan result wrapper)
            │   └── WledUiState.kt            (ConnectionState sealed class + UiState)
            ├── viewmodel/
            │   └── WledViewModel.kt          (mirrors BLE→DataStore, persists MAC)
            ├── tile/
            │   └── WledTileService.kt        (SuspendingTileService, LaunchAction pattern)
            └── presentation/
                ├── theme/
                │   ├── Color.kt
                │   └── Theme.kt
                └── screens/
                    ├── ScanScreen.kt
                    └── ControlScreen.kt
```

---

## Known Issues / Lessons Learned From Prior Session

These problems have already been solved — the fresh implementation must bake in the fixes:

1. **`FragmentActivity` must not be used.** Use `ComponentActivity`. No fragment dependency is needed.
2. **`AutoCenteringParams` import must be explicit:** `androidx.wear.compose.foundation.lazy.AutoCenteringParams` — the `material` package has a same-named class that causes type mismatch.
3. **`ChipDefaults.chipBorder` in Wear Compose 1.4.x** takes a `BorderStroke` object, not named `borderColor`/`borderWidth` parameters.
4. **`kotlinOptions { jvmTarget }` is deprecated** in Kotlin 2.x. Use `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.
5. **`DynamicDataValue` requires a type argument** in protolayout 1.2.x — avoid it entirely. The tile uses `LaunchAction` + Intent extras instead of `LoadAction` + tile state.
6. **`SuspendingTileService` overrides** (`onTileRequest`, `onResourcesRequest`, `onTileEnterEvent`) must all use correct parameter types from `androidx.wear.tiles.*`.
7. **`gradle.properties` must exist** at project root with `android.useAndroidX=true`. Without it, every `androidx.*` dependency fails to resolve.
8. **Global `~/.gradle/gradle.properties`** on this machine may contain legacy `android.useAndroidX=false` — the project-level file must override it, or the global file must be cleaned.
9. **`JAVA_HOME`** must point to `C:\Program Files\Android\Android Studio\jbr` (the AS-bundled JBR). The system may have a stale environment variable pointing at a non-existent path.
10. **`org.gradle.java.home`** in project `gradle.properties` must also point to the JBR to prevent Gradle 9 from attempting to download JDK 21 from foojay.io.
11. **All `.kt` files must use package `com.jpchurchouse.wledblewear`** (and subpackages). Mixed package names (`com.example.wledble`, `com.jpchurchouse.wledble`) have caused repeated `R` class and cross-reference failures.
12. **`gradle-wrapper.jar` must be generated** via File → Sync Project in Android Studio. It cannot be delivered as source — it's a binary.
13. **`local.properties` path format:** Use forward slashes for keystore path (`C:/Users/...`). The `sdk.dir` entry uses `C\:\\...` (Android Studio writes it that way automatically).
14. **Release signing:** The `signingConfigs` block should use `check()` assertions so a missing/wrong keystore path fails loudly rather than silently producing an unsigned AAB.
15. **WearOS emulator cannot test BLE scanning to external peripherals.** Use the emulator for UI layout only. All BLE testing requires a physical WearOS device connected via WiFi ADB.

---

## Project Setup Instructions (To Be Included in Fresh Thread Response)

The response should include step-by-step instructions covering:
1. Creating the project in Android Studio as a **WearOS blank project** (this generates `gradle-wrapper.jar` and correct skeleton files automatically)
2. Replacing the generated skeleton files with the provided source
3. SDK Manager setup (API 35 compile target, Wear OS system images)
4. `gradle.properties` contents (both project-level and note about global file)
5. `local.properties` contents and keystore setup
6. `JAVA_HOME` environment variable setup on Windows
7. Physical device ADB over WiFi pairing steps
8. Release AAB build and Play Store upload process
9. Tile installation on watch