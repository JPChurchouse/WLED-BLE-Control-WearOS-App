# Google Play Release Guide: WLED BLE Wear
### Updating the live app (v1 → v2.0.0)

The app already exists in Play Console (package `com.jpchurchouse.wledblewear`). This guide covers
shipping an **update**: a new signed AAB plus a refreshed store listing. You don't need to create the
app, set up Play app signing or fill in the content rating again.

---

## Key facts

| Item | Value |
|---|---|
| Package | `com.jpchurchouse.wledblewear` |
| Project | `C:\Users\Jamie\Git\WLED-BLE-Control-WearOS-App\WledBleWear` |
| Upload keystore | `C:\Users\Jamie\Git\.keystore\wled-ble-wear-release.keystore`, alias `wled-ble-wear` |
| Signing credentials | `WledBleWear\local.properties` (gitignored, never commit) |
| Upload key SHA-256 | `3C:02:E5:EB:40:E2:6A:5E:0D:70:9C:D9:18:DC:82:E6:30:BD:0A:04:1A:50:C2:A0:DA:32:C0:63:02:AE:8D:B6` |
| Live version | 1.x (`Exports\20260518.aab`) |
| This release | `versionName 2.0.0`, `versionCode 6`, `targetSdk 35` |
| keytool | `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe` |

> Back up the keystore and `local.properties` somewhere off this PC. If you lose the upload key you
> can still request a key reset from Play Console, but it takes days.

**Play target API (2026):** from 31 Aug 2026, Wear OS updates must target API 35 or higher (phones need 36).
`targetSdk 35` passes. Wear OS apps must also support 64-bit devices (from 15 Sep 2026). The bundle includes
`arm64-v8a`, so that passes too. Check the requirement again before each release, because it rises every August.

---

## Part 1: Before release

1. **Merge `v2` into `main`** so the code, README and the privacy policy link all match what ships.
2. **Tile picker (fixed in 2.0.0).** One UI Watch only lists tiles that declare a preview image
   (`androidx.wear.tiles.PREVIEW` → `res/drawable-nodpi/tile_preview.png`). Keep that metadata, and
   regenerate the image if the tile's look changes.
3. **Update the version** in `app/build.gradle.kts` if anything changes after this guide.
   `versionCode` must be higher than every code Play has ever seen. This release is 6.

---

## Part 2: Build and verify the AAB

```powershell
cd C:\Users\Jamie\Git\WLED-BLE-Control-WearOS-App\WledBleWear
./gradlew clean bundleRelease
```

The output is `app\build\outputs\bundle\release\app-release.aab`. Check the signature:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -printcert -jarfile app\build\outputs\bundle\release\app-release.aab
```

The **SHA256** must equal the upload key SHA-256 in the table above. If it doesn't, or it says "not a
signed jar file", stop: the wrong key, or no key, was used.

Archive the build alongside the earlier ones:

```powershell
Copy-Item app\build\outputs\bundle\release\app-release.aab ..\Exports\YYYYMMDD.aab
```

---

## Part 3: Update the store listing

Play Console → select the app → **Grow users → Store presence → Main store listing**.

Text comes from `play_store_listing.md`:

| Field | Action |
|---|---|
| App name | Leave as is (max 30 chars) |
| Short description | Replace with the new one (75/80 chars) |
| Full description | Replace with everything under *Full description* (~3,200/4,000 chars) |

Graphics:

| Asset | File | Action |
|---|---|---|
| App icon | `icons/play_store_icon_512.png` | Replace (new icon background) |
| Feature graphic | `icons/feature_graphic_1024x500.png` | Replace |
| **Wear OS** screenshots | `images/screenshots/v2/*.png` | **Delete all the v1 screenshots**, then upload up to 8 |

> ⚠️ Upload watch screenshots under the **Wear OS** tab. The Phone tab is a separate section.

Suggested screenshot order (8 max; leave out `Pairing_Passkey.png`, which shows the system
dialog rather than the app):

1. `ControlScreen_Power.png`
2. `ControlScreen_Presets.png`
3. `Tile.png`
4. `Complications.png`
5. `ControlScreen_BrightnessTimer.png`
6. `ControlScreen_SleepTimer.png`
7. `DevicesScreen.png`
8. `ScanScreen_Scanning.png`

Click **Save**. Listing changes go through review and may go live before or after the release.
To publish both together, turn on **Publishing overview → Managed publishing** before saving, then
publish everything at once in Part 6.

---

## Part 4: Check App content

**Policy → App content.** Nothing in v2 should need changes, but check each item:

| Section | v2 status |
|---|---|
| Privacy policy | Currently points at the **old repo** (`WLED-BLE-Plugin-WearOS-App`). It still loads, but update it to `https://raw.githubusercontent.com/JPChurchouse/WLED-BLE-Control-WearOS-App/main/PRIVACY_POLICY.md` and change "WLED BLE plugin" in the policy to "WLED BLE Control usermod" |
| Data safety | Unchanged: no data collected or shared |
| Foreground service permissions | Unchanged: `connectedDevice`, same as v1 (BLE commands from tiles and complications) |
| Permissions | Same set as v1. Nothing new to declare |
| Ads, App access, Content rating, Target audience | Unchanged |

If Play shows a banner asking you to review a declaration, complete it before submitting.

---

## Part 5: Create the release

1. In the left sidebar go to **Test and release → Production**. Check the form-factor selector at the
   top shows **Wear OS** and not Phone.
2. Click **Create new release**.
3. Upload `app-release.aab`. Check it shows **6 (2.0.0)** and that Play reports no errors. A warning
   about debug symbols or a deobfuscation file is fine to ignore.
4. **Release name:** `2.0.0`
5. **Release notes** (en-GB): use the *Release notes (v2.0.0)* section of `play_store_listing.md`
   (500 char limit; it's ~375).
6. Click **Next**.
7. **Staged rollout (recommended):** v2 needs the new usermod and **won't work with v1 firmware**.
   Start at about 20% and check for reviews or crashes (**Monitor and improve → Android vitals**), then
   raise it to 100%.
8. **Save → Send for review** (or *Start rollout to Production*).

---

## Part 6: After submission

| When | What happens |
|---|---|
| Immediately | "In review" in **Publishing overview** |
| Hours to ~3 days | Review completes. Updates are usually faster than a first release |
| After approval | Goes live (or waits for you to click **Publish** if Managed publishing is on). Allow a few hours to reach watches |

Check it on the watch: Play Store on the watch → *Manage apps* → update, or search "WLED".

If you have a **debug build** (`com.jpchurchouse.wledblewear.debug`) on the watch, it installs
alongside the Play version and adds a second set of tiles and complications with the same names.
Uninstall it so you're testing the Play build:
`adb uninstall com.jpchurchouse.wledblewear.debug`.

---

## Future updates

1. Bump `versionCode` (always higher) and `versionName` in `app/build.gradle.kts`.
2. Run Part 2: build, check the SHA-256, archive to `Exports\`.
3. Run Part 5. Update the listing (Part 3) only if features or screenshots changed.

---

## Common errors

| Error | Cause | Fix |
|---|---|---|
| "Your Android App Bundle is signed with the wrong key" | Built with a different keystore | Check `keystore.path` in `local.properties`. The SHA-256 must match the table above |
| "Version code 6 has already been used" | That code was uploaded before, even if the release was discarded | Increase `versionCode` |
| "Your app currently targets API level XX…" | Play's annual target-API rise | Raise `targetSdk` (and `compileSdk`), retest, rebuild |
| Release created on the wrong form factor / "requires android.hardware.type.watch" | Phone track selected | Switch the form-factor selector to Wear OS |
| `keystore.path missing from local.properties` during build | Fresh clone without `local.properties` | Copy it back from your backup |
