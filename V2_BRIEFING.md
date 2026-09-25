# WledBleWear v2: briefing

Hand-off from the firmware side (`C:\Users\Jamie\Git\WLED-BLE-Control-Usermod`). This file is the brief for building **v2** of this app on top of the v1 code in this repo. The v1 notes in `Briefing.md` and README ("Known Quirks") still apply: Gradle/JBR setup, Wear Compose and ProtoLayout API gotchas, signing.

## Goal and constraints

- **Same Play listing.** Keep `applicationId = "com.jpchurchouse.wledblewear"` and the same upload key. Bump `versionCode` above 5 and set `versionName` to `2.0.0`.
- **Play target API.** Check Play's current target-API requirement before release. v1 targets API 35, and Play's minimum rises each year.
- **v2 speaks only the new protocol.** It is not compatible with v1 firmware. The listing text, release notes and README must tell users to flash the new usermod (`WLED-BLE-Control-Usermod`, WLED 16+).
- **Signing.** `WledBleWear/local.properties` is gitignored and is not in this clone. Copy it from the old local folder `C:\Users\Jamie\Git\WLED-BLE-Plugin-WearOS-App\WledBleWear\local.properties`.

## The protocol

**Read `C:\Users\Jamie\Git\WLED-BLE-Control-Usermod\docs\PROTOCOL.md` in full. It's the contract.** Summary:

| Characteristic | UUID `8e59xxxx-14dc-405e-9f47-e363750a861a` | Use |
|---|---|---|
| Service | `…0001` | scan filter |
| Info | `…0002` read, **open** | protocol version, features, pairing-window flag, name, WLED version |
| State | `…0003` read/notify, **bonded** | 8 bytes: flags (power, nightlight), brightness, preset, playlist, preset-list revision, nightlight seconds left |
| Command | `…0004` write/notify, **bonded** | `[opcode][arg]`: power on/off/toggle, brightness set/step, preset apply/next/prev (all or favourites), nightlight N min, off-and-disconnect. The result comes back as a notification `[opcode][status]` |
| Preset list | `…0005` write start ID, then read a page, **bonded** | paged records `[id][flags][name][quick-load label]`. Cache it by device address + revision |

A reference client in Python (`tools/ble_client.py` in the usermod repo) implements every flow and is the easiest thing to check behaviour against.

## Security UX (new in v2)

- Everything except Info needs an encrypted, **MITM-authenticated bond** (LE Secure Connections, passkey entry). The first read of State triggers Android's system pairing dialog, where the user types the 6-digit passkey. The passkey is shown in WLED → Config → Usermods → BLEControl.
- Pairing only works while the device's **pairing window** is open: WLED → Info → "BLE pairing: Enable", 120 s. Info's status bit0 says whether it's open.
- **Connect flow:**
  1. Connect.
  2. Read Info. If not bonded and the window is closed, show: "On your WLED device: Info → BLE pairing → Enable, then retry".
  3. Otherwise read State. Android pairs; the user enters the PIN.
  4. On `Insufficient Authentication`/`Encryption` errors, show the same guidance.
- After bonding, tiles and complications reconnect silently using the stored keys, with no window needed.
- Handle a bond that the device has forgotten ("Forget all devices" in WLED). Encryption fails and the device ignores the old keys. Offer "Forget and re-pair": remove the bond on the watch (`BluetoothDevice.removeBond` via reflection, or send the user to Settings), then pair again.
- The device allows **one connection at a time** and **up to 3 bonded watches**.

## Features

**App**
- Scan (filter by service UUID; the name is in the scan response).
- Saved devices list. Store the address, name and last-known state. Connect directly by address, no scan needed.
- Control screen:
  - device name
  - power toggle, updated live from notifications
  - brightness (rotary input / bezel is a natural fit, using BRIGHTNESS_STEP)
  - preset list with the active one highlighted, playlists marked, favourites (presets with a WLED quick-load label) first or marked
  - nightlight / sleep timer (e.g. 15/30/60 min) with a countdown from `nl_remaining`
  - disconnect button
- "Active preset" follows WLED's meaning: any manual change (brightness, power) resets it to 0. Show "no preset" rather than keeping the old highlight.

**Tiles and complications.** Short-lived: connect by saved address → write the command → optionally read State → disconnect. The device answers in about 1–2 s end to end. Always show clearly when a device can't be reached or needs re-pairing. Keep the cached state in DataStore and mark it stale when it's old.
- Tile: device name + connection/last-known state, power toggle, next/previous preset, 2–4 favourite preset buttons (use their quick-load labels as button text), "Off & disconnect", and "Open app".
- Complications, extending v1's four:
  - power toggle (SHORT_TEXT / SMALL_IMAGE)
  - current preset (SHORT_TEXT / LONG_TEXT)
  - connection status with device name
  - one-tap off-and-disconnect (opcode `0x08`)
  - next preset
  - nightlight remaining (RANGED_VALUE)
- Reading complications update from State notifications while the app holds a connection. Otherwise they show the last-known values. Don't hold a permanent connection for them (battery).

## Reuse from v1

- **Keep:** the Gradle setup, theme, `WledApplication`-owned `BleManager` singleton, the DataStore patterns, the tile and complication services and the foreground-service toggle pattern (`WledToggleForegroundService`), and the operation serialisation (`Mutex` + timeouts).
- **Replace:**
  - UUIDs and data formats in `BleConstants.kt`
  - the GATT setup chain (MTU 517 → Info → State → CCCDs → preset pages when the revision changed)
  - v1's JSON preset parsing, replaced by the paged binary format
- **Add:** bonding/pairing state handling, the saved-devices list, brightness and nightlight UI, and handling of Command results.

## Testing

- A real device runs the new firmware: Gledopto ESP32 at `192.168.1.91`, BLE address `D4:E9:F4:7C:4F:76`, name `WLED-Gledopto`. Read the passkey from `http://192.168.1.91/json/cfg` (`um.BLEControl.passkey`), and open the pairing window with `curl -X POST -H "Content-Type: application/json" -d '{"ble":{"pair":true}}' http://192.168.1.91/json/state`.
- The WearOS emulator can't reach real BLE peripherals. Use a physical watch over Wi-Fi ADB (see v1 notes).
- The dev PC is currently bonded to the device. The device holds up to 3 bonds, so the watch will use the second slot.
